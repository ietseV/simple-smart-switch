package nl.simplesmartswitch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Grote schuifschakelaar UIT / AAN. Tikken of schuiven. */
public class ToggleView extends View {

    public interface Listener { void onToggle(boolean on); }

    private static final int TRACK_OFF = Color.rgb(58, 64, 82);
    private static final int TRACK_ON  = Color.rgb(242, 184, 7);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private float pos = 0f;              // 0 = links (uit), 1 = rechts (aan)
    private boolean on = false;
    private Listener listener;
    private ValueAnimator anim;
    private float downX, downPos;
    private boolean dragging;

    public ToggleView(Context c) {
        super(c);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }
    public boolean isOn() { return on; }

    /** Zet de stand zonder de listener aan te roepen. */
    public void setOn(boolean value, boolean animate) {
        on = value;
        animateTo(on ? 1f : 0f, animate);
    }

    private void animateTo(float target, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate) { pos = target; invalidate(); return; }
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration(320);
        anim.setInterpolator(new OvershootInterpolator(1.6f));
        anim.addUpdateListener(a -> { pos = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.45f);
    }

    private float pad() { return getHeight() * 0.09f; }
    private float thumbR() { return getHeight() / 2f - pad(); }
    private float travel() { return getWidth() - 2 * (pad() + thumbR()); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downPos = pos; dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                if (Math.abs(dx) > getHeight() * 0.15f) dragging = true;
                if (dragging && travel() > 0) {
                    pos = Math.max(0f, Math.min(1f, downPos + dx / travel()));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean newOn = dragging ? pos > 0.5f : !on;
                commit(newOn);
                return true;
            case MotionEvent.ACTION_CANCEL:
                animateTo(on ? 1f : 0f, true);
                return true;
        }
        return false;
    }

    private void commit(boolean newOn) {
        boolean changed = newOn != on;
        on = newOn;
        animateTo(on ? 1f : 0f, true);
        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (listener != null) listener.onToggle(on);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight(), p = pad(), tr = thumbR();
        float vis = Math.max(0f, Math.min(1f, pos));

        // Baan
        paint.setColor((int) argb.evaluate(vis, TRACK_OFF, TRACK_ON));
        c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, paint);

        // Tekst in de baan
        paint.setTextSize(h * 0.32f);
        float ty = h / 2 - (paint.descent() + paint.ascent()) / 2;
        paint.setColor(Color.argb((int) (255 * (1 - vis)), 200, 205, 220));
        c.drawText("UIT", w - p - tr, ty, paint);
        paint.setColor(Color.argb((int) (255 * vis), 60, 40, 0));
        c.drawText("AAN", p + tr, ty, paint);

        // Knop
        float tx = p + tr + pos * travel();
        paint.setColor(Color.argb(60, 0, 0, 0));
        c.drawCircle(tx, h / 2 + h * 0.04f, tr, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(tx, h / 2, tr, paint);
        // klein lampje in de knop
        paint.setColor((int) argb.evaluate(vis, Color.rgb(150, 156, 170), TRACK_ON));
        c.drawCircle(tx, h / 2 - tr * 0.12f, tr * 0.32f, paint);
        paint.setColor(Color.rgb(150, 156, 170));
        c.drawRoundRect(new RectF(tx - tr * 0.16f, h / 2 + tr * 0.18f, tx + tr * 0.16f, h / 2 + tr * 0.42f),
                tr * 0.06f, tr * 0.06f, paint);
    }
}
