package com.nathankurien.dotter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Home-screen widget. Fetches Open-Meteo itself (no WebView), keeps the last
 * good response per widget so it still shows something offline, and draws the
 * whole widget as one bitmap sized to what the launcher gives it
 * (see WidgetArt for the layouts). Tapping opens the app. Each widget keeps
 * its own place, chosen in WeatherWidgetConfigActivity.
 */
public class WeatherWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "wx-widget";
    private static final String DEFAULT_NAME = "London";
    private static final double DEFAULT_LAT = 51.5074, DEFAULT_LON = -0.1278;
    private static final String URL_TEMPLATE =
        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&hourly=" + Forecast.HOURLY
        + "&daily=" + Forecast.DAILY + "&current=temperature_2m&timezone=auto&forecast_days=7&wind_speed_unit=mph";

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
                        String json = fetch(lat(prefs, id), lon(prefs, id));
                        if (json != null) prefs.edit().putString("last_" + id, json).apply();
                        else json = prefs.getString("last_" + id, null);
                        show(context, mgr, id, json);
                    }
                } finally {
                    pending.finish();
                }
            }
        }).start();
    }

    /** The widget was resized: redraw from the saved forecast, no network needed. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle options) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("last_" + id, null);
        if (json != null) show(context, mgr, id, json);
    }

    private static double lat(SharedPreferences p, int id) { return parse(p.getString("lat_" + id, null), DEFAULT_LAT); }
    private static double lon(SharedPreferences p, int id) { return parse(p.getString("lon_" + id, null), DEFAULT_LON); }
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

    private static void show(Context context, AppWidgetManager mgr, int id, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String place = prefs.getString("name_" + id, DEFAULT_NAME);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_weather);
        if (json != null) {
            try {
                Forecast f = Forecast.parse(new JSONObject(json), lat(prefs, id), lon(prefs, id));
                int[] size = sizeDp(context, mgr, id);
                Bitmap bm = WidgetArt.render(f, place, size[0], size[1], context.getResources().getDisplayMetrics().density);
                v.setImageViewBitmap(R.id.widget_sky, bm);
                v.setContentDescription(R.id.widget_sky, WidgetArt.describe(f, place));
            } catch (Exception e) {
                // keep the plain background until a good forecast arrives
            }
        }
        Intent open = new Intent(context, MainActivity.class);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        mgr.updateAppWidget(id, v);
    }

    /** The widget's size in dp as the launcher reports it, for the current orientation. */
    private static int[] sizeDp(Context context, AppWidgetManager mgr, int id) {
        Bundle o = mgr.getAppWidgetOptions(id);
        boolean land = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int w = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        return new int[] { w > 0 ? w : 150, h > 0 ? h : 150 };
    }
}
