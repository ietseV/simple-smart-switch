package nl.simplesmartswitch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Een getekende gloeilamp die aan en uit kan, met gloed, stralen, vonkjes en rookwolkjes. */
public class LampView extends View {

    private static final int OFF_GLASS = Color.rgb(70, 76, 92);
    private static final int ON_GLASS  = Color.rgb(255, 214, 74);
    private static final int GLOW      = Color.rgb(255, 200, 60);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final Random rnd = new Random();
    private final Path path = new Path();

    private float level = 0f;      // 0 = uit, 1 = volledig aan
    private float pulse = 1f;      // ademende gloed
    private float rayAngle = 0f;   // draaiende stralen
    private boolean on = false;
    private ValueAnimator levelAnim, pulseAnim;

    private static class Particle {
        float x, y, vx, vy, life, max, size; int color; boolean smoke;
    }
    private final List<Particle> particles = new ArrayList<>();
    private long lastFrame = 0;

    public LampView(Context c) { super(c); }

    public boolean isOn() { return on; }

    /** Lamp aan- of uitzetten. Aan: flikkeren als een tl-buis plus vonkjes. Uit: rookwolkje. */
    public void setOn(boolean value) {
        if (value == on) return;
        on = value;
        if (levelAnim != null) levelAnim.cancel();
        if (on) {
            levelAnim = ValueAnimator.ofFloat(level, 0.7f, 0.1f, 0.85f, 0.35f, 1f);
            levelAnim.setDuration(650);
            spawnSparks();
            startPulse();
        } else {
            levelAnim = ValueAnimator.ofFloat(level, 0f);
            levelAnim.setDuration(350);
            spawnSmoke();
            if (pulseAnim != null) pulseAnim.cancel();
            pulse = 1f;
        }
        levelAnim.addUpdateListener(a -> { level = (float) a.getAnimatedValue(); invalidate(); });
        levelAnim.start();
    }

    private void startPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        pulseAnim.setDuration(3000);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            pulse = 1f + 0.06f * (float) Math.sin(t);
            rayAngle = (float) Math.toDegrees(t) / 6f;
            invalidate();
        });
        pulseAnim.start();
    }

    private float bulbR() { return Math.min(getWidth(), getHeight()) * 0.22f; }
    private float cx() { return getWidth() / 2f; }
    private float cy() { return getHeight() * 0.40f; }

    private void spawnSparks() {
        int[] colors = {Color.rgb(255, 214, 74), Color.rgb(255, 160, 60), Color.WHITE, Color.rgb(255, 120, 140), Color.rgb(120, 220, 255)};
        float r = bulbR();
        for (int i = 0; i < 28; i++) {
            Particle p = new Particle();
            double a = rnd.nextDouble() * Math.PI * 2;
            float speed = r * (1.8f + rnd.nextFloat() * 2.2f);
            p.x = cx() + (float) Math.cos(a) * r * 0.9f;
            p.y = cy() + (float) Math.sin(a) * r * 0.9f;
            p.vx = (float) Math.cos(a) * speed;
            p.vy = (float) Math.sin(a) * speed;
            p.max = p.life = 0.7f + rnd.nextFloat() * 0.6f;
            p.size = r * (0.05f + rnd.nextFloat() * 0.06f);
            p.color = colors[rnd.nextInt(colors.length)];
            particles.add(p);
        }
        kick();
    }

    private void spawnSmoke() {
        float r = bulbR();
        for (int i = 0; i < 7; i++) {
            Particle p = new Particle();
            p.smoke = true;
            p.x = cx() + (rnd.nextFloat() - 0.5f) * r * 0.6f;
            p.y = cy() - r * 0.9f;
            p.vx = (rnd.nextFloat() - 0.5f) * r * 0.5f;
            p.vy = -r * (0.6f + rnd.nextFloat() * 0.5f);
            p.max = p.life = 1.2f + rnd.nextFloat() * 0.6f;
            p.size = r * (0.12f + rnd.nextFloat() * 0.1f);
            p.color = Color.rgb(160, 166, 180);
            particles.add(p);
        }
        kick();
    }

    private void kick() { lastFrame = 0; postInvalidateOnAnimation(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float r = bulbR(), x = cx(), y = cy();

        // Gloed
        if (level > 0.01f) {
            float gr = r * 2.6f * pulse;
            int a = (int) (170 * level);
            glowPaint.setShader(new RadialGradient(x, y, gr,
                    new int[]{Color.argb(a, Color.red(GLOW), Color.green(GLOW), Color.blue(GLOW)), Color.argb(0, 255, 200, 60)},
                    new float[]{0.25f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, gr, glowPaint);

            // Stralen
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(r * 0.07f);
            paint.setColor(Color.argb((int) (200 * level), 255, 220, 100));
            for (int i = 0; i < 12; i++) {
                double ang = Math.toRadians(rayAngle + i * 30);
                float r1 = r * 1.35f, r2 = r * (1.6f + 0.12f * (i % 2)) * pulse;
                c.drawLine(x + (float) Math.cos(ang) * r1, y + (float) Math.sin(ang) * r1,
                        x + (float) Math.cos(ang) * r2, y + (float) Math.sin(ang) * r2, paint);
            }
        }

        // Glas
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((int) argb.evaluate(level, OFF_GLASS, ON_GLASS));
        c.drawCircle(x, y, r, paint);
        path.reset();
        path.moveTo(x - r * 0.62f, y + r * 0.78f);
        path.lineTo(x + r * 0.62f, y + r * 0.78f);
        path.lineTo(x + r * 0.42f, y + r * 1.32f);
        path.lineTo(x - r * 0.42f, y + r * 1.32f);
        path.close();
        c.drawPath(path, paint);

        // Glimlicht op het glas
        paint.setColor(Color.argb(70 + (int) (60 * level), 255, 255, 255));
        c.drawCircle(x - r * 0.38f, y - r * 0.38f, r * 0.16f, paint);

        // Gloeidraad
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.06f);
        paint.setColor((int) argb.evaluate(level, Color.rgb(40, 44, 54), Color.rgb(255, 250, 230)));
        path.reset();
        path.moveTo(x - r * 0.3f, y + r * 0.75f);
        path.lineTo(x - r * 0.3f, y + r * 0.1f);
        for (int i = 0; i < 4; i++) {
            float sx = x - r * 0.3f + i * r * 0.15f;
            path.lineTo(sx + r * 0.075f, y - r * 0.1f);
            path.lineTo(sx + r * 0.15f, y + r * 0.1f);
        }
        path.lineTo(x + r * 0.3f, y + r * 0.75f);
        c.drawPath(path, paint);

        // Fitting
        paint.setStyle(Paint.Style.FILL);
        float top = y + r * 1.32f, w = r * 0.9f, h = r * 0.18f;
        for (int i = 0; i < 3; i++) {
            paint.setColor(i % 2 == 0 ? Color.rgb(150, 156, 170) : Color.rgb(110, 116, 130));
            float ww = w - i * r * 0.06f;
            c.drawRoundRect(new RectF(x - ww / 2, top + i * h, x + ww / 2, top + (i + 1) * h), h / 2, h / 2, paint);
        }
        paint.setColor(Color.rgb(80, 86, 100));
        c.drawRoundRect(new RectF(x - r * 0.22f, top + 3 * h, x + r * 0.22f, top + 3 * h + h * 0.9f), h / 2, h / 2, paint);

        // Deeltjes
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) { it.remove(); continue; }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.smoke) { p.size += r * 0.25f * dt; p.vx *= 0.98f; }
            else { p.vy += r * 2.5f * dt; p.vx *= 0.99f; }
            float f = p.life / p.max;
            paint.setColor(p.color);
            paint.setAlpha((int) (255 * f * (p.smoke ? 0.5f : 1f)));
            c.drawCircle(p.x, p.y, p.smoke ? p.size : p.size * (0.5f + f * 0.5f), paint);
        }
        paint.setAlpha(255);
        if (!particles.isEmpty()) postInvalidateOnAnimation();
        else lastFrame = 0;
    }
}
