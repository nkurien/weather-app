package com.nathankurien.dotter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Draws the whole widget as one bitmap: the sky, then type, a curve and the
 * stipple row in the app's editorial style. The layout depends on the size
 * the launcher gives the widget: tile (1 row), small (2x2), wide (4x2) and
 * large (4x4). All measurements are dp; render() scales the canvas.
 */
final class WidgetArt {
    private WidgetArt() {}

    static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    static final Typeface SERIF_ITALIC = Typeface.create(Typeface.SERIF, Typeface.ITALIC);
    static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);

    private static final int MAX_PIXELS = 1200000;

    static Bitmap render(Forecast f, String place, int wDp, int hDp, float density) {
        float scale = density;
        if ((double) wDp * hDp * scale * scale > MAX_PIXELS) scale = (float) Math.sqrt(MAX_PIXELS / ((double) wDp * hDp));
        Bitmap bm = Bitmap.createBitmap(Math.max(1, Math.round(wDp * scale)), Math.max(1, Math.round(hDp * scale)), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        cv.scale(scale, scale);
        float w = wDp, h = hDp;

        RectF all = new RectF(0, 0, w, h);
        int layer = cv.saveLayer(all, null);
        Forecast.Hour now = f.hours.get(f.nowIdx);
        SkyPainter.draw(cv, w, h, now, System.currentTimeMillis() / 60000L);
        float[] ink = Forecast.inkFor(now.top, now.bot);
        int cw = (int) Math.floor((wDp + 30) / 70.0), ch = (int) Math.floor((hDp + 30) / 70.0);
        if (hDp < 90 || ch <= 1) { if (wDp < 90) tile(cv, f, now, w, h, ink); else strip(cv, f, now, place, w, h, ink); }
        else if (cw >= 3 && ch >= 3) large(cv, f, now, place, w, h, ink);
        else if (cw >= 3) wide(cv, f, now, place, w, h, ink);
        else small(cv, f, now, place, w, h, ink);

        // round the corners with an antialiased mask
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        cv.drawRoundRect(all, 24, 24, mask);
        cv.restoreToCount(layer);
        return bm;
    }

    static String describe(Forecast f, String place) {
        Forecast.Hour n = f.hours.get(f.nowIdx);
        Forecast.Day d = f.days.get(n.day);
        return place + ", " + Math.round(n.temp) + " degrees, " + n.label + ". High " + Math.round(d.hi) + ", low " + Math.round(d.lo) + ".";
    }

    // ---- tiers

    /** 1x1: colour and a number. */
    private static void tile(Canvas cv, Forecast f, Forecast.Hour now, float w, float h, float[] ink) {
        float size = Math.min(h * 0.6f, 34);
        Paint p = paint(SERIF, size, col(ink, 1));
        String t = String.valueOf(Math.round(now.temp));
        float tw = p.measureText(t), dw = size * 0.34f;
        float x = (w - tw - dw) / 2;
        cv.drawText(t, x, h / 2 + size * 0.34f, p);
        degree(cv, x + tw, h / 2 + size * 0.34f, size, ink, 1);
    }

    /** 2x1: the number on the left, place and condition on the right. */
    private static void strip(Canvas cv, Forecast f, Forecast.Hour now, String place, float w, float h, float[] ink) {
        float pad = 14, size = Math.min(h * 0.58f, 40);
        float base = h / 2 + size * 0.34f;
        Paint p = paint(SERIF, size, col(ink, 1));
        String t = String.valueOf(Math.round(now.temp));
        cv.drawText(t, pad, base, p);
        float tw = p.measureText(t);
        degree(cv, pad + tw, base, size, ink, 1);
        float x = pad + tw + size * 0.34f + 12, room = w - x - pad;
        if (room > 36) {
            eyebrow(cv, place, x, h / 2 - 3, 8.5f, ink, room);
            Paint c = paint(SERIF_ITALIC, 12, col(ink, 1));
            drawFit(cv, now.label, x, h / 2 + 13, c, room);
        }
    }

    private static void small(Canvas cv, Forecast f, Forecast.Hour now, String place, float w, float h, float[] ink) {
        float pad = 14;
        float size = (float) Forecast.clamp(h * 0.36, 36, 64);
        float eb = pad + 8, tb = eb + 8 + size * 0.78f, cb = tb + 17, hb = cb + 14;
        eyebrow(cv, place, pad, eb, 9, ink, w - 2 * pad);
        number(cv, now, pad, tb, size, ink);
        Paint c = paint(SERIF_ITALIC, 14, col(ink, 1));
        drawFit(cv, now.label, pad, cb, c, w - 2 * pad);
        if (h >= 110) hiLo(cv, f, now, pad, hb, 10.5f, ink);
        if (h >= 135 && f.hours.size() - f.nowIdx >= 12) stipple(cv, f, f.nowIdx, 12, pad, w - pad, h - pad - 6, ink);
    }

    private static void wide(Canvas cv, Forecast f, Forecast.Hour now, String place, float w, float h, float[] ink) {
        float pad = 16, leftW = Math.min(130, w * 0.4f);
        float size = (float) Forecast.clamp(h * 0.4, 38, 66);
        float eb = pad + 8, tb = eb + 8 + size * 0.78f, cb = tb + 17, hb = cb + 14;
        eyebrow(cv, place, pad, eb, 9, ink, leftW);
        number(cv, now, pad, tb, size, ink);
        Paint c = paint(SERIF_ITALIC, 14, col(ink, 1));
        drawFit(cv, now.label, pad, cb, c, leftW);
        if (h >= 120) hiLo(cv, f, now, pad, hb, 10.5f, ink);

        float rx = pad + leftW + 14, rr = w - pad;
        int n = Math.min(24, f.hours.size() - f.nowIdx);
        float rowY = h - pad - 6, bottom = rowY - 14;
        curve(cv, f, f.nowIdx, n, rx, pad + 2, rr, bottom, 16, false, ink);
        stipple(cv, f, f.nowIdx, n, rx, rr, rowY, ink);
    }

    private static void large(Canvas cv, Forecast f, Forecast.Hour now, String place, float w, float h, float[] ink) {
        float pad = 18;
        float size = (float) Forecast.clamp(h * 0.27, 64, 96);
        eyebrow(cv, place + " · now · " + now.time.substring(11, 16), pad, pad + 8, 9.5f, ink, w - 2 * pad);
        float tb = pad + 16 + size * 0.78f;
        number(cv, now, pad, tb, size, ink);
        float cb = tb + 22;
        Paint c = paint(SERIF_ITALIC, 17, col(ink, 1));
        drawFit(cv, now.label, pad, cb, c, w * 0.55f);
        Forecast.Day d = f.days.get(now.day);
        Paint hl = paint(SANS, 11, col(ink, 0.66));
        hl.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("H " + Math.round(d.hi) + "° · L " + Math.round(d.lo) + "°", w - pad, cb, hl);

        float top = cb + 22;
        hairline(cv, pad, w - pad, top, ink);
        eyebrow(cv, "Next 24 hours", pad, top + 18, 8.5f, ink, w - 2 * pad);
        float rowY = h - pad - 8, tickY = rowY - 18, bottom = tickY - 4;
        int n = Math.min(24, f.hours.size() - f.nowIdx);
        curve(cv, f, f.nowIdx, n, pad, top + 28, w - pad, bottom, 26, true, ink);
        ticks(cv, f, f.nowIdx, n, pad, w - pad, tickY + 8, ink);
        stipple(cv, f, f.nowIdx, n, pad, w - pad, rowY, ink);
    }

    // ---- pieces

    /** The big numeral with its small raised degree sign. */
    private static void number(Canvas cv, Forecast.Hour now, float x, float base, float size, float[] ink) {
        Paint p = paint(SERIF, size, col(ink, 1));
        String t = String.valueOf(Math.round(now.temp));
        cv.drawText(t, x, base, p);
        degree(cv, x + p.measureText(t), base, size, ink, 1);
    }

    private static void degree(Canvas cv, float x, float base, float size, float[] ink, double a) {
        Paint p = paint(SERIF, size * 0.4f, col(ink, a));
        cv.drawText("°", x + size * 0.03f, base - size * 0.42f, p);
    }

    private static void hiLo(Canvas cv, Forecast f, Forecast.Hour now, float x, float base, float size, float[] ink) {
        Forecast.Day d = f.days.get(now.day);
        cv.drawText("H " + Math.round(d.hi) + "°   L " + Math.round(d.lo) + "°", x, base, paint(SANS, size, col(ink, 0.66)));
    }

    private static void eyebrow(Canvas cv, String s, float x, float base, float size, float[] ink, float maxW) {
        Paint p = paint(SANS, size, col(ink, 0.66));
        p.setLetterSpacing(0.14f);
        drawFit(cv, s.toUpperCase(Locale.getDefault()), x, base, p, maxW);
    }

    private static void hairline(Canvas cv, float x0, float x1, float y, float[] ink) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(0.6f);
        p.setColor(col(ink, 0.18));
        cv.drawLine(x0, y, x1, y, p);
    }

    /** One dot per hour: size is temperature, solid is cloudier, a tail is rain, faint is night, a cap marks now. */
    private static void stipple(Canvas cv, Forecast f, int start, int count, float left, float right, float cy, float[] ink) {
        count = Math.min(count, f.hours.size() - start);
        if (count <= 0) return;
        double gmin = 1e9, gmax = -1e9;
        for (int k = 0; k < count; k++) { double t = f.hours.get(start + k).temp; gmin = Math.min(gmin, t); gmax = Math.max(gmax, t); }
        double range = gmax - gmin < 1 ? 1 : gmax - gmin;
        float pitch = (right - left) / count, k0 = Math.min(1f, pitch / 9f);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(0.9f);
        line.setStrokeWidth(1.1f);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < count; k++) {
            Forecast.Hour hh = f.hours.get(start + k);
            float x = left + (k + 0.5f) * pitch, sz = (float) (2.5 + (hh.temp - gmin) / range * 5) * k0;
            double night = hh.alt < 0 ? 0.38 : 1;
            ring.setColor(col(ink, 0.9 * night));
            fill.setColor(col(ink, hh.cloud * night));
            cv.drawCircle(x, cy, sz / 2, fill);
            cv.drawCircle(x, cy, sz / 2, ring);
            if (hh.mm >= 0.1) {
                float len = (float) (2 + Forecast.clamp(hh.mm / 2) * 12) * Math.max(0.7f, k0);
                line.setColor(col(ink, 0.6 * night));
                cv.drawLine(x, cy + sz / 2 + 1.5f, x, cy + sz / 2 + 1.5f + len, line);
            }
            if (k == 0) {
                line.setColor(col(ink, 1));
                cv.drawLine(x - 2, cy - 8, x + 2, cy - 8, line);
            }
        }
    }

    /** The temperature as a smooth line over the next hours, with rain bars standing on the baseline. */
    private static void curve(Canvas cv, Forecast f, int start, int count, float left, float top, float right, float bottom, float barH, boolean labels, float[] ink) {
        count = Math.min(count, f.hours.size() - start);
        if (count < 2 || bottom - top < 20) return;
        double mn = 1e9, mx = -1e9;
        for (int k = 0; k < count; k++) { double t = f.hours.get(start + k).temp; mn = Math.min(mn, t); mx = Math.max(mx, t); }
        double lo = mn - 1.5, hi = mx + 1.5;
        if (hi - lo < 6) { double mid = (hi + lo) / 2; lo = mid - 3; hi = mid + 3; }
        float plotB = bottom - barH - 4, plotT = top + (labels ? 14 : 4);
        float pitch = (right - left) / count;
        float[] xs = new float[count], ys = new float[count];
        for (int k = 0; k < count; k++) {
            xs[k] = left + (k + 0.5f) * pitch;
            ys[k] = (float) (plotB - (f.hours.get(start + k).temp - lo) / (hi - lo) * (plotB - plotT));
        }
        Path line = new Path();
        line.moveTo(xs[0], ys[0]);
        for (int k = 0; k < count - 1; k++) {
            int a = Math.max(0, k - 1), b = k, c = k + 1, d = Math.min(count - 1, k + 2);
            line.cubicTo(xs[b] + (xs[c] - xs[a]) / 6, ys[b] + (ys[c] - ys[a]) / 6, xs[c] - (xs[d] - xs[b]) / 6, ys[c] - (ys[d] - ys[b]) / 6, xs[c], ys[c]);
        }
        Path area = new Path(line);
        area.lineTo(xs[count - 1], plotB + 4);
        area.lineTo(xs[0], plotB + 4);
        area.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, plotT, 0, plotB + 4, col(ink, 0.22), col(ink, 0), Shader.TileMode.CLAMP));
        cv.drawPath(area, p);
        p.setShader(null);
        hairline(cv, left, right, bottom, ink);
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < count; k++) {
            Forecast.Hour hh = f.hours.get(start + k);
            float bh = (float) (Forecast.clamp(hh.mm / 3) * barH);
            if (bh < 0.5f) continue;
            double op = hh.pop == null ? 0.8 : 0.2 + 0.8 * hh.pop / 100;
            p.setColor(col(ink, op * 0.75));
            cv.drawRoundRect(new RectF(xs[k] - pitch * 0.3f, bottom - bh, xs[k] + pitch * 0.3f, bottom), 1.5f, 1.5f, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(col(ink, 0.95));
        cv.drawPath(line, p);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(xs[0], ys[0], 3, p);
        if (labels) {
            int hiK = 0, loK = 0;
            for (int k = 1; k < count; k++) { if (f.hours.get(start + k).temp > f.hours.get(start + hiK).temp) hiK = k; if (f.hours.get(start + k).temp < f.hours.get(start + loK).temp) loK = k; }
            Paint t = paint(SANS, 9, col(ink, 0.66));
            t.setTextAlign(Paint.Align.CENTER);
            float hx = Math.max(left + 22, Math.min(right - 22, xs[hiK]));
            if (hiK > 1) cv.drawText("high " + Math.round(f.hours.get(start + hiK).temp) + "°", hx, ys[hiK] - 7, t);
            if (loK > 1 && loK != hiK) cv.drawText("low " + Math.round(f.hours.get(start + loK).temp) + "°", Math.max(left + 22, Math.min(right - 22, xs[loK])), ys[loK] + 15, t);
        }
    }

    /** 00 / 06 / 12 / 18 labels under the curve. */
    private static void ticks(Canvas cv, Forecast f, int start, int count, float left, float right, float base, float[] ink) {
        count = Math.min(count, f.hours.size() - start);
        float pitch = (right - left) / Math.max(1, count);
        Paint t = paint(SANS, 9, col(ink, 0.55));
        t.setTextAlign(Paint.Align.CENTER);
        for (int k = 1; k < count; k++) {
            String ts = f.hours.get(start + k).time;
            int hh = Integer.parseInt(ts.substring(11, 13));
            if (ts.endsWith(":00") && hh % 6 == 0) cv.drawText(ts.substring(11, 13), left + (k + 0.5f) * pitch, base, t);
        }
    }

    // ---- helpers

    private static Paint paint(Typeface tf, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    private static int col(float[] ink, double a) {
        return Color.argb((int) Math.round(255 * Forecast.clamp(a)), Math.round(ink[0]), Math.round(ink[1]), Math.round(ink[2]));
    }

    /** Draws text on one line, shrinking a little and then truncating if it does not fit. */
    private static void drawFit(Canvas cv, String s, float x, float base, Paint p, float maxW) {
        float orig = p.getTextSize();
        while (p.measureText(s) > maxW && p.getTextSize() > orig * 0.78f) p.setTextSize(p.getTextSize() - 0.5f);
        if (p.measureText(s) > maxW) {
            while (s.length() > 1 && p.measureText(s + "…") > maxW) s = s.substring(0, s.length() - 1);
            s = s + "…";
        }
        cv.drawText(s, x, base, p);
    }
}
