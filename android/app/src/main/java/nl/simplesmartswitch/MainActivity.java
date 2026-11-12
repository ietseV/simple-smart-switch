package nl.simplesmartswitch;

import android.Manifest;
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
import android.widget.Button;
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

    private TextView status, last, log;
    private Button scanBtn, onBtn, offBtn, testBtn, disconnectBtn;
    private LinearLayout list;

    // ---------- scherm ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        buildUi();
        askPermissions();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button bigButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(20);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        btn.setAllCaps(false);
        return btn;
    }

    private LinearLayout.LayoutParams full(int heightDp, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightDp == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : dp(heightDp));
        lp.topMargin = dp(topDp);
        return lp;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(244, 241, 234));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Simple Smart Switch");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(31, 35, 40));
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(17);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(Color.rgb(31, 35, 40));
        status.setText("Niet verbonden");
        root.addView(status, full(0, 12));

        scanBtn = bigButton("Zoek Bluno", Color.rgb(47, 111, 222));
        scanBtn.setOnClickListener(v -> startScan());
        root.addView(scanBtn, full(56, 12));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, full(0, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        onBtn = bigButton("AAN", Color.rgb(31, 157, 85));
        offBtn = bigButton("UIT", Color.rgb(192, 57, 43));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(96), 1);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(96), 1);
        half2.leftMargin = dp(12);
        row.addView(onBtn, half);
        row.addView(offBtn, half2);
        root.addView(row, full(0, 20));
        onBtn.setOnClickListener(v -> send("U", "Aan"));
        offBtn.setOnClickListener(v -> send("D", "Uit"));

        last = new TextView(this);
        last.setGravity(Gravity.CENTER);
        last.setTextSize(16);
        last.setTextColor(Color.rgb(95, 102, 112));
        root.addView(last, full(0, 10));

        testBtn = bigButton("Verbinding testen (servo blijft stil)", Color.rgb(95, 102, 112));
        testBtn.setTextSize(15);
        testBtn.setOnClickListener(v -> send("V", "Test"));
        root.addView(testBtn, full(48, 12));

        disconnectBtn = bigButton("Verbinding verbreken", Color.rgb(95, 102, 112));
        disconnectBtn.setTextSize(15);
        disconnectBtn.setOnClickListener(v -> disconnect());
        root.addView(disconnectBtn, full(48, 8));

        TextView logTitle = new TextView(this);
        logTitle.setText("Logboek");
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(Color.rgb(95, 102, 112));
        root.addView(logTitle, full(0, 24));

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        log.setTextColor(Color.rgb(60, 60, 60));
        log.setTextIsSelectable(true);
        root.addView(log, full(0, 4));

        setContentView(scroll);
        setReady(false);
    }

    private void setReady(boolean ok) {
        ready = ok;
        onBtn.setEnabled(ok);
        offBtn.setEnabled(ok);
        testBtn.setEnabled(ok);
        onBtn.setAlpha(ok ? 1f : 0.4f);
        offBtn.setAlpha(ok ? 1f : 0.4f);
        testBtn.setAlpha(ok ? 1f : 0.4f);
        disconnectBtn.setVisibility(gatt != null ? View.VISIBLE : View.GONE);
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
        if (hasPermissions()) logLine("Rechten gekregen");
        else logLine("Geen toestemming voor Bluetooth. Geef die via Instellingen > Apps > Smart Switch > Machtigingen.");
    }

    // ---------- stap 1: zoeken ----------

    private static boolean looksLikeBluno(String name) {
        if (name == null || name.equals("(geen naam)")) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("bluno") || n.contains("groep");
    }

    private void startScan() {
        if (adapter == null) { setStatus("Deze telefoon heeft geen Bluetooth"); return; }
        if (!hasPermissions()) { askPermissions(); setStatus("Geef eerst toestemming voor Bluetooth"); return; }
        if (!adapter.isEnabled()) { setStatus("Zet Bluetooth aan en probeer opnieuw"); return; }
        if (scanning) return;
        disconnect();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { setStatus("Zet Bluetooth aan en probeer opnieuw"); return; }
        found.clear();
        list.removeAllViews();
        scanning = true;
        scanBtn.setText("Zoeken…");
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
            setStatus(found.isEmpty()
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
            t.setText(blunos.isEmpty()
                    ? "Geen Bluno of GROEP op naam gevonden. Leg de telefoon naast de Bluno: "
                      + "het apparaat met het sterkste signaal (bovenaan) is waarschijnlijk de Bluno."
                    : "Andere apparaten (sterkste signaal bovenaan):");
            t.setTextColor(Color.rgb(95, 102, 112));
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
        row.setTextColor(Color.rgb(31, 35, 40));
        row.setBackgroundColor(bluno ? Color.rgb(220, 232, 252) : Color.WHITE);
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
                if (g == gatt) gatt = null;
                setStatus("Niet verbonden");
                ui.post(() -> setReady(false));
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
        if (text.contains("OK")) ui.post(() -> last.setText("Test gelukt: de Bluno antwoordt"));
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
            ui.post(() -> setReady(true));
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
        if (!ready || serial == null) return;
        queueText(serial, cmd, true);
        pump();
        logLine("Verstuurd: " + cmd + " (" + (int) cmd.charAt(0) + ")");
        last.setText(label + " verstuurd");
        setReady(false);
        ui.postDelayed(() -> { if (gatt != null && serial != null) setReady(true); },
                cmd.equals("V") ? 400 : LOCK_MS);
    }

    @Override
    protected void onDestroy() {
        stopScan();
        disconnect();
        super.onDestroy();
    }
}
