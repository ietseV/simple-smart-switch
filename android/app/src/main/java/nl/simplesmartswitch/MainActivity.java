package nl.simplesmartswitch;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.animation.OvershootInterpolator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Smart Switch: bedient een DFRobot Bluno Beetle via Bluetooth Low Energy.
 * AAN stuurt 'U', UIT stuurt 'D', TESTEN stuurt 'V' (de Bluno antwoordt "OK").
 */
@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    // Bluno: service DFB0, seriele data DFB1, AT-commando's DFB2.
    private static final UUID SERVICE = UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL  = UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private static final UUID COMMAND = UUID.fromString("0000dfb2-0000-1000-8000-00805f9b34fb");
    private static final int CHUNK = 17;          // AT-commando's in stukjes van max. 17 bytes
    private static final long SCAN_MS = 12000;    // zo lang zoeken
    private static final long LOCK_MS = 1200;     // knoppen even blokkeren terwijl de servo beweegt

    private final Handler ui = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private boolean scanning = false;
    private BluetoothGatt gatt;
    private BluetoothDevice chosen;
    private BluetoothGattCharacteristic serial, command;
    private boolean retried = false;
    private boolean ready = false;

    // Schrijfwachtrij: steeds pas het volgende stukje als het vorige bevestigd is.
    private static class Job { BluetoothGattCharacteristic ch; byte[] data; boolean noResponse; }
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private boolean writing = false;

    private final Map<String, ScanResult> found = new LinkedHashMap<>();
    private long lastListUpdate = 0;
    private volatile String filterAddr = "";

    // Kleuren
    private static final int BG_OFF = Color.rgb(20, 24, 36);
    private static final int BG_ON  = Color.rgb(46, 38, 20);
    private static final int CARD   = Color.rgb(31, 36, 51);
    private static final int FIELD  = Color.rgb(44, 50, 68);
    private static final int TEXT   = Color.rgb(232, 234, 240);
    private static final int MUTED  = Color.rgb(154, 161, 178);
    private static final int ACCENT = Color.rgb(76, 130, 240);
    private static final int GREEN  = Color.rgb(52, 199, 110);

    private TextView status, last, log, connectedText, detailsToggle;
    private Button scanBtn, testBtn;
    private LinearLayout list, connectCard, connectedBar, lampCard;
    private ScrollView scroll;
    private EditText addrField;
    private SharedPreferences prefs;
    private LampView lamp;
    private ToggleView toggle;
    private boolean busy = false;
    private boolean autoReconnect = true;   // false zodra de gebruiker zelf op Verbreken drukt
    private int autoTries = 0;              // hooguit 3 automatische pogingen achter elkaar
    private int bgColor = BG_OFF;
    private ValueAnimator bgAnim;

    // ---------- scherm ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        getWindow().setStatusBarColor(BG_OFF);
        buildUi();
        askPermissions();
        if (hasPermissions()) ui.postDelayed(this::autoConnect, 500);
    }

    /** Automatisch verbinden met het opgeslagen adres (bij openen en na wegvallen). */
    private void autoConnect() {
        if (gatt != null || scanning || !autoReconnect) return;
        if (normalizeAddr(addrField.getText().toString()).isEmpty()) return;
        if (adapter == null || !adapter.isEnabled() || !hasPermissions()) return;
        if (autoTries >= 3) { logLine("Automatisch verbinden gestopt na 3 pogingen"); return; }
        autoTries++;
        logLine("Automatisch verbinden met opgeslagen adres (poging " + autoTries + ")");
        startScan();   // zoekt alleen naar het adres en verbindt zodra het gevonden is
    }

    private void scheduleReconnect() {
        if (autoReconnect) ui.postDelayed(this::autoConnect, 2500);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button button(String text, int color, int textSize) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(textSize);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(rounded(color, 14));
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        return btn;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 22));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        return c;
    }

    private LinearLayout.LayoutParams full(int heightDp, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightDp == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : dp(heightDp));
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_OFF);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(28));
        scroll.addView(root);

        root.addView(text("Smart Switch", 28, TEXT, true));
        root.addView(text("Bluno Beetle met servo", 15, MUTED, false), full(0, 2));

        // --- Balk als we verbonden zijn ---
        connectedBar = new LinearLayout(this);
        connectedBar.setOrientation(LinearLayout.HORIZONTAL);
        connectedBar.setGravity(Gravity.CENTER_VERTICAL);
        connectedBar.setBackground(rounded(CARD, 18));
        connectedBar.setPadding(dp(16), dp(10), dp(10), dp(10));
        TextView dot = text("●", 18, GREEN, true);
        connectedBar.addView(dot);
        connectedText = text("Verbonden", 16, TEXT, true);
        connectedText.setPadding(dp(10), 0, dp(10), 0);
        connectedBar.addView(connectedText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button disc = button("Verbreken", FIELD, 14);
        disc.setOnClickListener(v -> { autoReconnect = false; disconnect(); });
        connectedBar.addView(disc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
        connectedBar.setVisibility(View.GONE);
        root.addView(connectedBar, full(0, 18));

        // --- Verbinden ---
        connectCard = card();
        connectCard.addView(text("Verbinden met de Bluno", 19, TEXT, true));
        status = text("Niet verbonden", 15, MUTED, false);
        connectCard.addView(status, full(0, 4));

        connectCard.addView(text("Adres (optioneel), opvragen via USB met AT+MAC=?. "
                + "Na de eerste verbinding onthoudt de app het adres en verbindt hij voortaan vanzelf.", 13, MUTED, false), full(0, 14));
        prefs = getSharedPreferences("smartswitch", MODE_PRIVATE);
        addrField = new EditText(this);
        addrField.setSingleLine(true);
        addrField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addrField.setHint("bijv. 0xC4BE84123456 (leeg = alles tonen)");
        addrField.setHintTextColor(Color.rgb(110, 116, 132));
        addrField.setTextColor(TEXT);
        addrField.setText(prefs.getString("addr", ""));
        addrField.setBackground(rounded(FIELD, 12));
        addrField.setPadding(dp(14), dp(12), dp(14), dp(12));
        connectCard.addView(addrField, full(0, 6));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        scanBtn = button("Zoek Bluno", ACCENT, 16);
        scanBtn.setOnClickListener(v -> { autoTries = 0; startScan(); });
        Button directBtn = button("Direct op adres", FIELD, 15);
        directBtn.setOnClickListener(v -> connectDirect());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(52), 1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        l2.leftMargin = dp(10);
        btnRow.addView(scanBtn, l1);
        btnRow.addView(directBtn, l2);
        connectCard.addView(btnRow, full(0, 12));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        connectCard.addView(list, full(0, 8));
        root.addView(connectCard, full(0, 18));

        // --- Lamp ---
        lampCard = card();
        lamp = new LampView(this);
        lamp.setOnClickListener(v -> { if (toggle.isEnabled()) flip(!toggle.isOn()); else nudge(); });
        lampCard.addView(lamp, full(270, 0));
        toggle = new ToggleView(this);
        toggle.setListener(this::onToggle);
        LinearLayout.LayoutParams tl = full(76, 6);
        tl.leftMargin = tl.rightMargin = dp(18);
        lampCard.addView(toggle, tl);
        last = text("Verbind eerst met de Bluno", 15, MUTED, false);
        last.setGravity(Gravity.CENTER);
        lampCard.addView(last, full(0, 14));
        testBtn = button("Verbinding testen (servo blijft stil)", FIELD, 14);
        testBtn.setOnClickListener(v -> send("V", "Test"));
        lampCard.addView(testBtn, full(44, 14));
        root.addView(lampCard, full(0, 18));

        // --- Technische details ---
        detailsToggle = text("Technische details  ▸", 14, MUTED, true);
        detailsToggle.setPadding(dp(4), dp(14), dp(4), dp(8));
        detailsToggle.setOnClickListener(v -> {
            boolean show = log.getVisibility() != View.VISIBLE;
            log.setVisibility(show ? View.VISIBLE : View.GONE);
            detailsToggle.setText(show ? "Technische details  ▾" : "Technische details  ▸");
        });
        root.addView(detailsToggle, full(0, 14));
        log = text("", 12, MUTED, false);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setVisibility(View.GONE);
        root.addView(log, full(0, 2));

        setContentView(scroll);
        setReady(false);
    }

    /** Schakelaar omgezet door de gebruiker. */
    private void onToggle(boolean on) {
        if (!ready || busy) {
            toggle.setOn(!on, true);   // terugzetten
            nudge();
            return;
        }
        applyLamp(on);
        send(on ? "U" : "D", on ? "Aan" : "Uit");
    }

    /** Lamp aangetikt: schakelaar meebewegen. */
    private void flip(boolean on) {
        if (!ready || busy) { nudge(); return; }
        toggle.setOn(on, true);
        toggle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        onToggle(on);
    }

    private void applyLamp(boolean on) {
        lamp.setOn(on);
        int target = on ? BG_ON : BG_OFF;
        if (bgAnim != null) bgAnim.cancel();
        bgAnim = ValueAnimator.ofObject(new ArgbEvaluator(), bgColor, target);
        bgAnim.setDuration(600);
        bgAnim.addUpdateListener(a -> {
            bgColor = (int) a.getAnimatedValue();
            scroll.setBackgroundColor(bgColor);
            getWindow().setStatusBarColor(bgColor);
        });
        bgAnim.start();
        // even huppen
        lamp.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120)
                .withEndAction(() -> lamp.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new OvershootInterpolator(3f)).start()).start();
    }

    /** Schudden: "eerst verbinden!" */
    private void nudge() {
        View target = ready ? lampCard : connectCard;
        ObjectAnimator.ofFloat(target, "translationX", 0, dp(12), -dp(10), dp(8), -dp(5), 0)
                .setDuration(400).start();
        if (!ready) last.setText("Verbind eerst met de Bluno");
    }

    private void setReady(boolean ok) {
        boolean was = ready;
        ready = ok;
        updateControls();
        if (ok == was && connectCard.getVisibility() == (ok ? View.GONE : View.VISIBLE)) return;
        if (ok) {
            String name = chosen != null && chosen.getName() != null ? chosen.getName() : "Bluno";
            connectedText.setText("Verbonden met " + name);
            fadeOut(connectCard);
            fadeIn(connectedBar);
            last.setText("Schuif de schakelaar of tik op de lamp");
        } else {
            fadeOut(connectedBar);
            fadeIn(connectCard);
            last.setText("Verbind eerst met de Bluno");
        }
    }

    private void updateControls() {
        boolean en = ready && !busy;
        toggle.setEnabled(en);
        testBtn.setEnabled(en);
        testBtn.setAlpha(en ? 1f : 0.45f);
    }

    private void fadeIn(View v) {
        if (v.getVisibility() == View.VISIBLE) return;
        v.setAlpha(0f);
        v.setTranslationY(-dp(12));
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).translationY(0).setDuration(260).start();
    }

    private void fadeOut(View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        v.animate().alpha(0f).translationY(-dp(12)).setDuration(200)
                .withEndAction(() -> { v.setVisibility(View.GONE); v.setTranslationY(0); }).start();
    }

    private void logLine(String msg) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        ui.post(() -> log.setText(t + "  " + msg + "\n" + log.getText()));
    }

    private void setStatus(String s) { ui.post(() -> status.setText(s)); }

    // ---------- rechten ----------

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPermissions() {
        for (String p : neededPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void askPermissions() {
        if (!hasPermissions()) requestPermissions(neededPermissions(), 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (hasPermissions()) { logLine("Rechten gekregen"); ui.postDelayed(this::autoConnect, 500); }
        else logLine("Geen toestemming voor Bluetooth. Geef die via Instellingen > Apps > Smart Switch > Machtigingen.");
    }

    // ---------- stap 1: zoeken ----------

    private static boolean looksLikeBluno(String name) {
        if (name == null || name.equals("(geen naam)")) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("bluno") || n.contains("groep");
    }

    /** Maakt van "0xC4BE84123456" of "c4-be-84-12-34-56" een net adres "C4:BE:84:12:34:56". */
    private static String normalizeAddr(String in) {
        if (in == null) return "";
        String hex = in.toUpperCase(Locale.ROOT).replaceFirst("^0X", "").replaceAll("[^0-9A-F]", "");
        if (hex.length() != 12) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) { if (i > 0) sb.append(':'); sb.append(hex, i, i + 2); }
        return sb.toString();
    }

    private String wantedAddr() {
        String a = normalizeAddr(addrField.getText().toString());
        prefs.edit().putString("addr", addrField.getText().toString().trim()).apply();
        return a;
    }

    private void connectDirect() {
        autoReconnect = true;
        autoTries = 0;
        String a = wantedAddr();
        if (a.isEmpty()) { setStatus("Vul eerst een geldig adres in (12 tekens, 0-9 en A-F)"); return; }
        if (adapter == null || !adapter.isEnabled()) { setStatus("Zet Bluetooth aan en probeer opnieuw"); return; }
        if (!hasPermissions()) { askPermissions(); return; }
        disconnect();
        addrField.setText(a);
        logLine("Stap 1: direct verbinden met adres " + a);
        connect(adapter.getRemoteDevice(a), a);
    }

    private void startScan() {
        autoReconnect = true;
        if (adapter == null) { setStatus("Deze telefoon heeft geen Bluetooth"); return; }
        if (!hasPermissions()) { askPermissions(); setStatus("Geef eerst toestemming voor Bluetooth"); return; }
        if (!adapter.isEnabled()) { setStatus("Zet Bluetooth aan en probeer opnieuw"); return; }
        if (scanning) return;
        disconnect();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { setStatus("Zet Bluetooth aan en probeer opnieuw"); return; }
        found.clear();
        list.removeAllViews();
        filterAddr = wantedAddr();
        if (!filterAddr.isEmpty()) logLine("Stap 1: alleen zoeken naar adres " + filterAddr);
        scanning = true;
        scanBtn.setText("Zoeken…");
        ObjectAnimator spin = ObjectAnimator.ofFloat(scanBtn, "alpha", 1f, 0.55f, 1f);
        spin.setDuration(900);
        spin.setRepeatCount((int) (SCAN_MS / 900));
        spin.start();
        setStatus("Zoeken naar Bluno…");
        logLine("Stap 1: zoeken gestart");
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(null, settings, scanCallback);
        ui.postDelayed(this::stopScan, SCAN_MS);
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try { scanner.stopScan(scanCallback); } catch (Exception ignored) { }
        scanBtn.setText("Zoek opnieuw");
        if (gatt == null) {
            setStatus(!filterAddr.isEmpty()
                    ? "Adres " + filterAddr + " niet gevonden. Staat de Bluno aan? Klopt het adres?"
                    : found.isEmpty()
                    ? "Niets gevonden. Staat de Bluno aan? Staat Locatie aan?"
                    : "Tik op jullie Bluno in de lijst");
        }
        showList();
        logLine("Stap 1: zoeken klaar, " + found.size() + " apparaten gevonden");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int type, ScanResult r) {
            String name = r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null;
            if (name == null) name = r.getDevice().getName();
            String want = filterAddr;
            if (!want.isEmpty()) {
                if (!r.getDevice().getAddress().equalsIgnoreCase(want)) return;   // alleen ons adres
                logLine("Stap 1: adres " + want + " gevonden (" + name + "), verbinden…");
                ui.post(() -> { if (scanning) connect(r.getDevice(), nameOf(r)); });
                return;
            }
            boolean isNew = !found.containsKey(r.getDevice().getAddress());
            found.put(r.getDevice().getAddress(), r);
            if (isNew && looksLikeBluno(name)) {
                logLine("Stap 1: gevonden " + name + " (" + r.getDevice().getAddress() + ")");
            }
            long now = System.currentTimeMillis();
            if (isNew || now - lastListUpdate > 1000) {   // lijst hooguit 1x per seconde verversen
                lastListUpdate = now;
                ui.post(MainActivity.this::showList);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            logLine("Stap 1 mislukt: zoeken gaf fout " + errorCode);
            setStatus("Zoeken mislukt (fout " + errorCode + ")");
            ui.post(MainActivity.this::stopScan);
        }
    };

    private void showList() {
        list.removeAllViews();
        List<ScanResult> blunos = new ArrayList<>(), others = new ArrayList<>();
        for (ScanResult r : found.values()) {
            (looksLikeBluno(nameOf(r)) ? blunos : others).add(r);
        }
        // Sterkste signaal bovenaan: leg de telefoon naast de Bluno.
        others.sort((a, b2) -> Integer.compare(b2.getRssi(), a.getRssi()));
        if (others.size() > 25) others = new ArrayList<>(others.subList(0, 25));
        for (ScanResult r : blunos) addRow(r, true);
        if (!others.isEmpty()) {
            TextView t = new TextView(this);
            t.setTextColor(MUTED);
            t.setText(blunos.isEmpty()
                    ? "Geen Bluno of GROEP op naam gevonden. Leg de telefoon naast de Bluno: "
                      + "het apparaat met het sterkste signaal (bovenaan) is waarschijnlijk de Bluno."
                    : "Andere apparaten (sterkste signaal bovenaan):");
            t.setTextColor(MUTED);
            list.addView(t, full(0, 10));
            for (ScanResult r : others) addRow(r, false);
        }
    }

    private String nameOf(ScanResult r) {
        String name = r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null;
        if (name == null) name = r.getDevice().getName();
        return (name == null || name.trim().isEmpty()) ? "(geen naam)" : name;
    }

    private void addRow(ScanResult r, boolean bluno) {
        Button row = new Button(this);
        row.setAllCaps(false);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setText((bluno ? "★ " : "") + nameOf(r) + "\n" + r.getDevice().getAddress() + "   signaal " + r.getRssi() + " dBm");
        row.setTextSize(bluno ? 17 : 14);
        row.setTextColor(TEXT);
        row.setBackground(rounded(bluno ? Color.rgb(44, 70, 130) : FIELD, 12));
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setOnClickListener(v -> connect(r.getDevice(), nameOf(r)));
        list.addView(row, full(0, 6));
    }

    // ---------- stap 2: verbinden ----------

    private void connect(BluetoothDevice device, String name) {
        stopScan();
        list.removeAllViews();
        chosen = device;
        retried = false;
        setStatus("Verbinden met " + name + "…");
        logLine("Stap 2: verbinden met " + name + " (" + device.getAddress() + ")");
        openGatt();
    }

    private void openGatt() {
        gatt = chosen.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        ui.post(() -> setReady(false));
    }

    private void disconnect() {
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) { }
            gatt = null;
            logLine("Verbinding verbroken");
        }
        serial = command = null;
        queue.clear();
        writing = false;
        setStatus("Niet verbonden");
        ui.post(() -> setReady(false));
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            if (st != BluetoothGatt.GATT_SUCCESS) {
                logLine("Stap 2: verbinding mislukt, GATT-status " + st);
                g.close();
                if (g == gatt) gatt = null;
                if (!retried) {
                    // Status 133: oude verbinding sluiten en één keer opnieuw proberen.
                    retried = true;
                    logLine("Stap 2: één keer opnieuw proberen…");
                    ui.postDelayed(MainActivity.this::openGatt, 800);
                } else {
                    setStatus("Verbinden mislukt (status " + st + "). Probeer opnieuw.");
                    ui.post(MainActivity.this::scheduleReconnect);
                    ui.post(() -> setReady(false));
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logLine("Stap 2: verbonden, diensten opvragen…");
                ui.postDelayed(() -> { if (gatt != null) gatt.discoverServices(); }, 300);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logLine("Verbinding weggevallen");
                g.close();
                boolean ours = g == gatt;
                if (ours) gatt = null;
                setStatus("Niet verbonden");
                ui.post(() -> setReady(false));
                if (ours) ui.post(MainActivity.this::scheduleReconnect);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int st) {
            BluetoothGattService s = g.getService(SERVICE);
            if (st != BluetoothGatt.GATT_SUCCESS || s == null) {
                logLine("Stap 2 mislukt: dienst DFB0 niet gevonden (status " + st + "). Is dit wel een Bluno?");
                setStatus("Dit apparaat is geen Bluno");
                return;
            }
            serial = s.getCharacteristic(SERIAL);
            command = s.getCharacteristic(COMMAND);
            if (serial == null) {
                logLine("Stap 2 mislukt: DFB1 niet gevonden");
                setStatus("Dit apparaat is geen Bluno");
                return;
            }
            logLine("Stap 2: DFB1" + (command != null ? " en DFB2" : "") + " gevonden");

            // Stap 4: meldingen alleen op de telefoon aanzetten (geen CCCD-schrijfactie naar de Bluno).
            boolean notif = g.setCharacteristicNotification(serial, true);
            logLine("Stap 4: meldingen DFB1 " + (notif ? "aan" : "niet gelukt"));

            // Stap 3: wachtwoord en snelheid naar DFB2, in stukjes.
            ui.post(() -> {
                if (command != null) {
                    queueText(command, "AT+PASSWOR=DFRobot\r\n", false);
                    queueText(command, "AT+CURRUART=115200\r\n", false);
                    logLine("Stap 3: wachtwoord en snelheid versturen…");
                }
                queue.add(new Job());   // markering: klaar met instellen
                pump();
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int st) {
            if (st != BluetoothGatt.GATT_SUCCESS) {
                logLine((ch.getUuid().equals(COMMAND) ? "Stap 3" : "Versturen") + " mislukt: schrijfstatus " + st);
            }
            writing = false;
            ui.post(MainActivity.this::pump);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            received(ch.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            received(value);
        }
    };

    private void received(byte[] value) {
        if (value == null) return;
        String text = new String(value, StandardCharsets.UTF_8);
        logLine("Ontvangen: \"" + text.replace("\r", "\\r").replace("\n", "\\n") + "\"");
        if (text.contains("OK")) ui.post(() -> {
            last.setText("Test gelukt: de Bluno antwoordt  👍");
            testBtn.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120)
                    .withEndAction(() -> testBtn.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        });
    }

    // ---------- schrijven ----------

    private void queueText(BluetoothGattCharacteristic ch, String text, boolean noResponse) {
        byte[] all = text.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < all.length; i += CHUNK) {
            Job j = new Job();
            j.ch = ch;
            j.noResponse = noResponse;
            j.data = java.util.Arrays.copyOfRange(all, i, Math.min(all.length, i + CHUNK));
            queue.add(j);
        }
    }

    @SuppressWarnings("deprecation")
    private void pump() {
        if (writing || gatt == null) return;
        Job j = queue.poll();
        if (j == null) return;
        if (j.ch == null) {   // klaar met instellen
            logLine("Klaar: verbonden en klaar voor gebruik");
            setStatus("Verbonden met " + (chosen.getName() != null ? chosen.getName() : "Bluno"));
            ui.post(() -> {
                setReady(true);
                autoTries = 0;
                // Adres onthouden: de volgende keer verbindt de app vanzelf.
                String a = chosen.getAddress();
                addrField.setText(a);
                prefs.edit().putString("addr", a).apply();
            });
            pump();
            return;
        }
        boolean canNoResp = (j.ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
        j.ch.setWriteType(j.noResponse && canNoResp
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        j.ch.setValue(j.data);
        writing = gatt.writeCharacteristic(j.ch);
        if (!writing) {
            logLine("Schrijven mislukt, opnieuw over 200 ms");
            queue.addFirst(j);
            ui.postDelayed(this::pump, 200);
        }
    }

    private void send(String cmd, String label) {
        if (!ready || serial == null || busy) return;
        queueText(serial, cmd, true);
        pump();
        logLine("Verstuurd: " + cmd + " (" + (int) cmd.charAt(0) + ")");
        last.setText(cmd.equals("U") ? "Lamp aan  ✨  servo gaat omhoog"
                : cmd.equals("D") ? "Lamp uit  💤  servo gaat omlaag"
                : "Test verstuurd…");
        busy = true;
        updateControls();
        ui.postDelayed(() -> { busy = false; updateControls(); },
                cmd.equals("V") ? 400 : LOCK_MS);
    }

    @Override
    protected void onDestroy() {
        stopScan();
        disconnect();
        super.onDestroy();
    }
}
