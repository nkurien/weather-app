package com.nathankurien.dotter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The widget's sky: the same gradient, sun and moon glow, clouds, rain and
 * snow as paintSky() in index.html, drawn once (a widget cannot animate).
 * Sizes are in dp; the caller scales the canvas.
 */
final class SkyPainter {
    private SkyPainter() {}

    private static Bitmap cloudLight, cloudDark;

    private static double seed = 7;
    private static double rnd() { seed = (seed * 16807) % 2147483647; return seed / 2147483647; }

    /** A cloud sprite: seven soft puffs, as makeSprite() in the page. */
    private static Bitmap sprite(int r, int g, int b) {
        Bitmap bm = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float[][] puffs = { {110, 160, 90}, {190, 115, 115}, {290, 125, 125}, {370, 150, 95}, {250, 170, 130}, {150, 175, 85}, {345, 175, 90} };
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float[] q : puffs) {
            float x = q[0] / 2, y = q[1] / 2, rad = q[2] / 2;
            p.setShader(new RadialGradient(x, y, rad, new int[] { Color.argb(140, r, g, b), Color.argb(64, r, g, b), Color.argb(0, r, g, b) },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        return bm;
    }

    /** Draws the sky for hour h into the rectangle (0,0,w,h); nowMin shifts the clouds a little on each refresh. */
    static void draw(Canvas cv, float w, float h, Forecast.Hour hr, long nowMin) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, Forecast.rgb(hr.top), Forecast.rgb(hr.bot), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        double dim = (1 - hr.cloud * 0.85) * (1 - hr.fog * 0.6) * (1 - hr.rain * 0.5) * (1 - hr.storm * 0.5);
        float m = Math.min(w, h), big = Math.max(w, h);

        // the sun lives in the right-hand part of the sky and the upper part, clear of the text
        double a = hr.sunB * dim;
        if (a > 0.01) {
            float x = (float) (w * (0.34 + 0.6 * Forecast.clamp((hr.sunX - 0.12) / 0.76))), y = (float) (h * (0.14 + 0.5 * Forecast.clamp((hr.sunY - 0.14) / 0.5, 0, 1.2)));
            float R = big * 0.7f;
            p.setShader(new RadialGradient(x, y, R, new int[] { Color.argb((int) (255 * 0.55 * a), 255, 226, 170), Color.argb((int) (255 * 0.28 * a), 255, 206, 140), Color.argb(0, 255, 200, 130) },
                new float[] { 0f, 0.18f, 1f }, Shader.TileMode.CLAMP));
            cv.drawCircle(x, y, R, p);
            p.setShader(null);
            p.setColor(Color.argb((int) (255 * 0.8 * a), 255, 247, 226));
            cv.drawCircle(x, y, Math.max(5, m * 0.05f), p);
        }

        // the moon: a glow that scales with how much is lit, then the disc at its real phase and tilt
        a = hr.moonB * (1 - hr.cloud * 0.9) * (1 - hr.fog * 0.5) * (1 - hr.rain * 0.4);
        if (a > 0.01) {
            float x = (float) (w * (0.34 + 0.6 * Forecast.clamp((hr.moonX - 0.12) / 0.76))), y = (float) (h * (0.14 + 0.5 * Forecast.clamp((hr.moonY - 0.14) / 0.5, 0, 1.2)));
            float R = big * 0.5f * (float) (0.55 + 0.45 * hr.moonF);
            p.setShader(new RadialGradient(x, y, R, Color.argb((int) (255 * 0.3 * a * (0.3 + 0.7 * hr.moonF)), 205, 218, 255), Color.argb(0, 205, 218, 255), Shader.TileMode.CLAMP));
            cv.drawCircle(x, y, R, p);
            p.setShader(null);
            moon(cv, x, y, Math.max(7, Math.min(14, m * 0.075f)), hr, a);
        }

        if (hr.cloud > 0.01) clouds(cv, w, h, hr, nowMin);
        float[] ink = Forecast.inkFor(hr.top, hr.bot);
        if (hr.rain > 0.02) rain(cv, w, h, hr, ink);
        if (hr.snow > 0.02) snow(cv, w, h, hr);
    }

    private static void moon(Canvas cv, float x, float y, float r, Forecast.Hour hr, double a) {
        double t = r * (1 - 2 * hr.moonF);
        float theta = (float) Math.toDegrees(Math.atan2(hr.moonVy, hr.moonVx));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        cv.save();
        cv.translate(x, y);
        cv.rotate(theta);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (255 * 0.14 * a), 180, 195, 230));
        cv.drawCircle(0, 0, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f);
        p.setColor(Color.argb((int) (255 * 0.3 * a), 205, 218, 255));
        cv.drawCircle(0, 0, r, p);
        Path path = new Path();
        path.arcTo(new RectF(-r, -r, r, r), -90, 180);
        float tw = (float) Math.max(0.01, Math.abs(t));
        path.arcTo(new RectF(-tw, -r, tw, r), 90, t > 0 ? -180 : 180);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb((int) (255 * 0.9 * a), 240, 244, 255));
        cv.drawPath(path, p);
        cv.restore();
    }

    private static void clouds(Canvas cv, float w, float h, Forecast.Hour hr, long nowMin) {
        if (cloudLight == null) { cloudLight = sprite(250, 248, 244); cloudDark = sprite(52, 62, 80); }
        seed = 7;
        double count = hr.cloud * 14, dayK = 0.3 + 0.7 * Forecast.smooth(-0.25, 0.2, hr.alt);
        double dark = Forecast.clamp(hr.rain * 0.7 + hr.storm * 0.3), base = 0.3 * (0.6 + 0.4 * hr.cloud);
        double sgn = hr.wx;
        if (Math.abs(sgn) < 0.25) sgn = sgn < 0 ? -0.25 : 0.25;
        float sw = Math.max(120, w * 0.9f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < 14; i++) {
            double x0 = rnd(), y = 0.03 + rnd() * 0.62, sc = 0.7 + rnd() * 1.1, sp = 0.6 + rnd() * 0.8, al = 0.5 + rnd() * 0.5;
            double ai = Forecast.clamp(count - i);
            if (ai <= 0.01) continue;
            float cw = (float) (sw * sc), ch = cw / 2, span = w + cw;
            double drift = nowMin * 0.05 * (4 + hr.wind * 0.9) * sp * sgn;     // moves a little with each refresh
            float px = (float) ((((x0 * span + drift) % span) + span) % span) - cw;
            float py = (float) (y * h) - ch * 0.5f;
            RectF dst = new RectF(px, py, px + cw, py + ch);
            double alpha = ai * al * base;
            if (dark < 0.99) { p.setAlpha((int) (255 * Forecast.clamp(alpha * (1 - dark) * dayK))); cv.drawBitmap(cloudLight, null, dst, p); }
            if (dark > 0.01) { p.setAlpha((int) (255 * Forecast.clamp(alpha * dark))); cv.drawBitmap(cloudDark, null, dst, p); }
        }
    }

    private static void rain(Canvas cv, float w, float h, Forecast.Hour hr, float[] ink) {
        seed = 11;
        int n = (int) (hr.rain * 0.0016 * w * h);
        double slant = Forecast.clamp(hr.wind / 55, 0, 0.55) * (hr.wx < 0 ? -1 : 1) * Math.min(1, Math.abs(hr.wx) * 1.6 + 0.2);
        double nl = Math.sqrt(slant * slant + 1), dx = slant / nl, dy = 1 / nl;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int layer = 0; layer < 2; layer++) {
            Path path = new Path();
            for (int j = 0; j < n; j++) {
                float x = (float) (rnd() * (w + 40) - 20), y = (float) (rnd() * h), len = (float) (8 + rnd() * 12);
                boolean front = rnd() > 0.5;
                if (front != (layer == 1)) continue;
                float k = layer == 1 ? 1.3f : 1f;
                path.moveTo(x, y);
                path.lineTo((float) (x - dx * len * k), (float) (y - dy * len * k));
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(layer == 1 ? 1.1f : 0.8f);
            p.setColor(Color.argb((int) (255 * (layer == 1 ? 0.16 + 0.2 * hr.rain : 0.08 + 0.14 * hr.rain)), Math.round(ink[0]), Math.round(ink[1]), Math.round(ink[2])));
            cv.drawPath(path, p);
        }
    }

    private static void snow(Canvas cv, float w, float h, Forecast.Hour hr) {
        seed = 13;
        int n = (int) (hr.snow * 0.0009 * w * h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(217, 255, 255, 255));
        for (int i = 0; i < n; i++) cv.drawCircle((float) (rnd() * w), (float) (rnd() * h), (float) (0.8 + rnd() * 1.4), p);
    }
}
