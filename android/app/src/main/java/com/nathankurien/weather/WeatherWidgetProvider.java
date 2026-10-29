package com.nathankurien.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Home-screen widget: the current temperature on the same kind of sky the
 * site draws. Fetches Open-Meteo itself (no WebView), keeps the last good
 * response so it still shows something offline, and taps through to the app.
 * Fixed to London for now.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "wx-widget";
    private static final String DEFAULT_NAME = "London";
    private static final double DEFAULT_LAT = 51.5074, DEFAULT_LON = -0.1278;
    private static final String URL_TEMPLATE =
        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
        + "&current=temperature_2m,weather_code&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min"
        + "&timezone=auto&forecast_days=1";

    /** Saves the place a widget shows (called by the configuration screen). */
    static void savePlace(Context context, int id, String name, double lat, double lon) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name_" + id, name)
            .putString("lat_" + id, Double.toString(lat))
            .putString("lon_" + id, Double.toString(lon))
            .remove("last_" + id)
            .apply();
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove("name_" + id).remove("lat_" + id).remove("lon_" + id).remove("last_" + id);
        e.apply();
    }

    @Override
    public void onUpdate(final Context context, final AppWidgetManager mgr, final int[] ids) {
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    for (int id : ids) {
                        String name = prefs.getString("name_" + id, DEFAULT_NAME);
                        double lat = parse(prefs.getString("lat_" + id, null), DEFAULT_LAT);
                        double lon = parse(prefs.getString("lon_" + id, null), DEFAULT_LON);
                        String json = fetch(lat, lon);
                        if (json != null) prefs.edit().putString("last_" + id, json).apply();
                        else json = prefs.getString("last_" + id, null);
                        mgr.updateAppWidget(id, render(context, name, json));
                    }
                } finally {
                    pending.finish();
                }
            }
        }).start();
    }

    private static double parse(String s, double fallback) {
        try { return s == null ? fallback : Double.parseDouble(s); } catch (NumberFormatException e) { return fallback; }
    }

    private static String fetch(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(String.format(Locale.US, URL_TEMPLATE, lat, lon)).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static RemoteViews render(Context context, String place, String json) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_weather);
        String temp = "--°", cond = "", range = "";
        int top = Color.rgb(8, 12, 30), bot = Color.rgb(20, 26, 56);

        if (json != null) {
            try {
                JSONObject j = new JSONObject(json);
                JSONObject cur = j.getJSONObject("current");
                JSONObject day = j.getJSONObject("daily");
                int code = cur.getInt("weather_code");
                temp = Math.round(cur.getDouble("temperature_2m")) + "°";
                cond = describe(code);
                range = "H " + Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)) + "°  L "
                    + Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
                int[] sky = sky(minutes(cur.getString("time")),
                    minutes(day.getJSONArray("sunrise").getString(0)),
                    minutes(day.getJSONArray("sunset").getString(0)), code);
                top = sky[0];
                bot = sky[1];
            } catch (Exception ignored) {
                // keep the placeholder look
            }
        }

        int ink = luminance(mix(top, bot, 0.5f)) > 0.55f ? Color.rgb(22, 26, 34) : Color.rgb(246, 243, 235);
        int soft = Color.argb(170, Color.red(ink), Color.green(ink), Color.blue(ink));
        v.setImageViewBitmap(R.id.widget_sky, skyBitmap(top, bot));
        v.setTextViewText(R.id.widget_place, place);
        v.setTextViewText(R.id.widget_temp, temp);
        v.setTextViewText(R.id.widget_condition, cond);
        v.setTextViewText(R.id.widget_range, range);
        v.setTextColor(R.id.widget_place, soft);
        v.setTextColor(R.id.widget_temp, ink);
        v.setTextColor(R.id.widget_condition, ink);
        v.setTextColor(R.id.widget_range, soft);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_root, tap);
        return v;
    }

    /** Minutes since the epoch for an Open-Meteo local time like 2026-10-28T06:46 (zone-free, only differences matter). */
    private static long minutes(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.parse(s).getTime() / 60000L;
    }

    /** Sky colours {top, bottom}: night, twilight or day by sun position, then bent by the weather. */
    static int[] sky(long now, long sunrise, long sunset, int code) {
        int nightTop = Color.rgb(8, 12, 30), nightBot = Color.rgb(20, 26, 56);
        int twiTop = Color.rgb(58, 70, 122), twiBot = Color.rgb(236, 150, 108);
        int dayTop = Color.rgb(74, 134, 206), dayBot = Color.rgb(156, 192, 230);

        float dayness; // 0 night, 1 full day
        float twilight;
        if (now >= sunrise && now <= sunset) {
            float m = Math.min(now - sunrise, sunset - now);
            dayness = smooth(m / 90f);
            twilight = 1f - dayness;
        } else {
            float m = now < sunrise ? sunrise - now : now - sunset;
            dayness = 0f;
            twilight = 1f - smooth(m / 60f);
        }
        int top = mix(mix(nightTop, twiTop, twilight), dayTop, dayness);
        int bot = mix(mix(nightBot, twiBot, twilight), dayBot, dayness);

        float cloud = cloudiness(code);
        float rain = isWet(code) ? 1f : 0f;
        top = bend(top, cloud, rain);
        bot = bend(bot, cloud, rain);
        return new int[] { top, bot };
    }

    /** Cloud flattens and greys the sky; rain pulls it to slate and darkens it. */
    private static int bend(int c, float cloud, float rain) {
        int g = Math.round(luminance(c) * 255f);
        int grey = mix(c, Color.rgb(g, g, g), 0.65f * cloud);
        int slate = mix(grey, Color.rgb(70, 82, 98), 0.3f * rain);
        return mix(Color.rgb(0, 0, 0), slate, 1f - 0.2f * rain);
    }

    private static float cloudiness(int code) {
        if (code <= 1) return 0f;
        if (code == 2) return 0.35f;
        if (code == 3) return 0.85f;
        return 0.9f;
    }

    private static boolean isWet(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code == 61 || code == 63 || code == 80 || code == 81) return "Rain";
        if (code == 65 || code == 82) return "Heavy rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private static Bitmap skyBitmap(int top, int bot) {
        int w = 400, h = 400;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, w, h), 56, 56, p);
        return b;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static float luminance(int c) {
        return (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f;
    }
}
