package com.nathankurien.weather;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Shown when a widget is added (and from "reconfigure" on Android 9+): pick
 * one of the site's twelve cities, or search any place with Open-Meteo's
 * geocoding. Each widget keeps its own place.
 */
public class WeatherWidgetConfigActivity extends Activity {
    private static class Hit {
        final String name, sub; final double lat, lon;
        Hit(String name, String sub, double lat, double lon) { this.name = name; this.sub = sub; this.lat = lat; this.lon = lon; }
    }

    /** Same twelve cities as PLACES in index.html. */
    private static final Hit[] PRESETS = {
        new Hit("London", "", 51.5074, -0.1278), new Hit("Paris", "", 48.8566, 2.3522),
        new Hit("Cape Town", "", -33.9249, 18.4241), new Hit("Cairo", "", 30.0444, 31.2357),
        new Hit("Dubai", "", 25.2048, 55.2708), new Hit("Mumbai", "", 19.0760, 72.8777),
        new Hit("Singapore", "", 1.3521, 103.8198), new Hit("Tokyo", "", 35.6762, 139.6503),
        new Hit("Sydney", "", -33.8688, 151.2093), new Hit("Mexico City", "", 19.4326, -99.1332),
        new Hit("New York", "", 40.7128, -74.0060), new Hit("São Paulo", "", -23.5505, -46.6333)
    };

    private static final int BG = Color.rgb(11, 16, 38), INK = Color.rgb(246, 243, 235), SOFT = Color.argb(150, 246, 243, 235);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Hit> shown = new ArrayList<Hit>();
    private BaseAdapter adapter;
    private TextView message;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int seq = 0;
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        if (extras != null) widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                int p = dp(20);
                v.setPadding(p + in.getSystemWindowInsetLeft(), p + in.getSystemWindowInsetTop(),
                    p + in.getSystemWindowInsetRight(), p + in.getSystemWindowInsetBottom());
                return in;
            }
        });

        TextView title = new TextView(this);
        title.setText("Choose a place");
        title.setTextColor(INK);
        title.setTypeface(android.graphics.Typeface.SERIF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        root.addView(title);

        EditText q = new EditText(this);
        q.setHint("Search any city");
        q.setHintTextColor(SOFT);
        q.setTextColor(INK);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.topMargin = dp(12);
        root.addView(q, qp);

        message = new TextView(this);
        message.setTextColor(SOFT);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(message);

        ListView list = new ListView(this);
        list.setDivider(null);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                LinearLayout row = new LinearLayout(WeatherWidgetConfigActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(12), 0, dp(12));
                Hit h = shown.get(i);
                TextView n = new TextView(WeatherWidgetConfigActivity.this);
                n.setText(h.name);
                n.setTextColor(INK);
                n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                row.addView(n);
                if (h.sub.length() > 0) {
                    TextView sub = new TextView(WeatherWidgetConfigActivity.this);
                    sub.setText(h.sub);
                    sub.setTextColor(SOFT);
                    sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    row.addView(sub);
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int i, long id) { pick(shown.get(i)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        showPresets();
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) { onQuery(e.toString().trim()); }
        });
    }

    private void showPresets() {
        seq++;
        shown.clear();
        for (Hit h : PRESETS) shown.add(h);
        message.setText("");
        adapter.notifyDataSetChanged();
    }

    private void onQuery(final String v) {
        if (pendingSearch != null) ui.removeCallbacks(pendingSearch);
        if (v.length() < 2) { showPresets(); return; }
        final int my = ++seq;
        pendingSearch = new Runnable() {
            @Override public void run() { search(v, my); }
        };
        ui.postDelayed(pendingSearch, 300);
    }

    private void search(final String v, final int my) {
        message.setText("Searching…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Hit> hits = new ArrayList<Hit>();
                boolean ok = true;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL("https://geocoding-api.open-meteo.com/v1/search?name="
                        + URLEncoder.encode(v, "UTF-8") + "&count=8&language=en&format=json").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    in.close();
                    JSONArray r = new JSONObject(sb.toString()).optJSONArray("results");
                    for (int i = 0; r != null && i < r.length(); i++) {
                        JSONObject o = r.getJSONObject(i);
                        String name = o.getString("name"), cc = o.optString("country_code", "");
                        String admin = o.optString("admin1", ""), country = o.optString("country", "");
                        StringBuilder sub = new StringBuilder();
                        if (admin.length() > 0 && !admin.equals(name)) sub.append(admin);
                        if (country.length() > 0) sub.append(sub.length() > 0 ? " · " : "").append(country);
                        hits.add(new Hit(name + (cc.length() > 0 && !cc.equals("GB") ? ", " + cc : ""), sub.toString(),
                            o.getDouble("latitude"), o.getDouble("longitude")));
                    }
                } catch (Exception e) {
                    ok = false;
                } finally {
                    if (c != null) c.disconnect();
                }
                final boolean fine = ok;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (my != seq) return;
                        shown.clear();
                        shown.addAll(hits);
                        message.setText(!fine ? "Search unavailable right now." : hits.isEmpty() ? "No places found." : "");
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void pick(Hit h) {
        WeatherWidgetProvider.savePlace(this, widgetId, h.name, h.lat, h.lon);
        Intent update = new Intent(this, WeatherWidgetProvider.class);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { widgetId });
        sendBroadcast(update);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
