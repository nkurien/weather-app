package com.nathankurien.dotter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/**
 * Open-Meteo JSON turned into hours and days, a port of processData() and the
 * sky palette in index.html so the widget's colours match the app's. Colours
 * are float[3] RGB in 0..255.
 */
final class Forecast {
    static final String HOURLY = "temperature_2m,precipitation_probability,precipitation,weather_code,cloud_cover,visibility,wind_speed_10m,wind_direction_10m";
    static final String DAILY = "sunrise,sunset";

    static final class Hour {
        String time;          // local, e.g. 2026-10-28T23:00
        int day;              // index into days
        double clock;         // local hours since midnight
        double temp, mm, wind, wx;
        Double pop;           // null when the model gives no probability
        int code;
        double cloud, fog, storm, rain, snow;
        double alt;           // sun altitude, -1..1
        boolean morning;
        float[] top, bot;
        double sunX, sunY, sunB;
        double moonX, moonY, moonB, moonF, moonVx, moonVy;
        String label;
    }

    static final class Day {
        String key;
        int start, n;
        double hi, lo;
    }

    final List<Hour> hours = new ArrayList<Hour>();
    final List<Day> days = new ArrayList<Day>();
    int nowIdx;

    // ---- small maths
    static double clamp(double v, double a, double b) { return v < a ? a : v > b ? b : v; }
    static double clamp(double v) { return clamp(v, 0, 1); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }
    static float[] mix(float[] p, float[] q, double t) {
        return new float[] { (float) (p[0] + (q[0] - p[0]) * t), (float) (p[1] + (q[1] - p[1]) * t), (float) (p[2] + (q[2] - p[2]) * t) };
    }
    static double luma(float[] c) { return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255; }
    static float[] toLuma(float[] c, double L) {
        double l = Math.max(luma(c), 0.004), k = L / l;
        return new float[] { (float) Math.min(255, c[0] * k), (float) Math.min(255, c[1] * k), (float) Math.min(255, c[2] * k) };
    }
    static float[] greyOf(float[] c) { float g = (float) (luma(c) * 255); return new float[] { g, g, g }; }
    static int rgb(float[] c) {
        return android.graphics.Color.rgb(Math.round(c[0]), Math.round(c[1]), Math.round(c[2]));
    }

    // ---- sky palette: alt, then [top, bottom] for morning and for evening
    private static final float[][][] PAL_TOP = {
        { {6, 10, 26}, {20, 27, 61} }, { {18, 24, 64}, {72, 60, 112} }, { {58, 72, 128}, {226, 150, 128} },
        { {112, 150, 208}, {246, 200, 160} }, { {102, 162, 224}, {244, 222, 194} }, { {86, 160, 230}, {208, 232, 248} },
        { {78, 152, 232}, {196, 226, 250} } };
    private static final float[][][] PAL_EVE = {
        { {6, 10, 26}, {20, 27, 61} }, { {18, 22, 58}, {84, 52, 100} }, { {52, 50, 110}, {214, 106, 98} },
        { {96, 128, 196}, {244, 168, 98} }, { {96, 152, 220}, {243, 214, 168} }, { {86, 160, 230}, {208, 232, 248} },
        { {78, 152, 232}, {196, 226, 250} } };
    private static final double[] PAL_ALT = { -1.00, -0.50, -0.12, 0.10, 0.40, 0.75, 1.00 };

    static float[][] basePalette(double alt, boolean morning) {
        int i = 0;
        while (i < PAL_ALT.length - 2 && alt > PAL_ALT[i + 1]) i++;
        double t = smooth(0, 1, clamp((alt - PAL_ALT[i]) / (PAL_ALT[i + 1] - PAL_ALT[i])));
        float[][][] p = morning ? PAL_TOP : PAL_EVE;
        return new float[][] { mix(p[i][0], p[i + 1][0], t), mix(p[i][1], p[i + 1][1], t) };
    }

    private static final float[] SLATE = {88, 112, 122}, FOG = {204, 199, 191}, SNOWC = {190, 200, 214};

    static float[][] skyFor(float[] top, float[] bot, double cloud, double rain, double fog, double snow, double storm, double alt) {
        double day = smooth(-0.25, 0.25, alt);
        float[] t = top.clone(), b = bot.clone();
        double flat = clamp(cloud * 0.45 + fog * 0.7 + snow * 0.3);
        b = mix(b, t, flat * 0.8);
        double d = clamp(cloud * 0.5 + rain * 0.25 + storm * 0.2, 0, 0.8);
        t = mix(t, greyOf(t), d); b = mix(b, greyOf(b), d);
        double sl = clamp(rain * 0.28 + storm * 0.3);
        if (sl > 0) { t = mix(t, toLuma(SLATE, luma(t)), sl); b = mix(b, toLuma(SLATE, luma(b)), sl); }
        if (fog > 0) {
            float[] tf = toLuma(FOG, lerp(luma(t), 0.12 + 0.5 * day, 0.55)), bf = toLuma(FOG, lerp(luma(b), 0.12 + 0.5 * day, 0.55));
            t = mix(t, tf, fog * 0.75); b = mix(b, bf, fog * 0.75);
        }
        if (snow > 0) {
            float[] ts = toLuma(SNOWC, lerp(luma(t), 0.2 + 0.55 * day, 0.5)), bs = toLuma(SNOWC, lerp(luma(b), 0.2 + 0.55 * day, 0.5));
            t = mix(t, ts, snow * 0.55); b = mix(b, bs, snow * 0.55);
        }
        if (storm > 0) { float m = (float) (1 - storm * 0.28); t = new float[] { t[0] * m, t[1] * m, t[2] * m }; b = new float[] { b[0] * m, b[1] * m, b[2] * m }; }
        return new float[][] { t, b };
    }

    /** Text colour: light on dark skies, dark on light ones, blended through the middle. */
    static float[] inkFor(float[] top, float[] bot) {
        double L = luma(mix(top, bot, 0.45));
        return mix(new float[] {247, 244, 236}, new float[] {22, 24, 28}, smooth(0.34, 0.5, L));
    }

    // ---- the moon: phase, position, tilt from the date alone
    static double[] moonAt(long ms, double lat, double lon) {
        final double R = Math.PI / 180;
        double d = ms / 86400000.0 + 2440587.5 - 2451545, e = R * 23.4397;
        double L = R * (218.316 + 13.176396 * d), M = R * (134.963 + 13.064993 * d), F = R * (93.272 + 13.22935 * d);
        double l = L + R * 6.289 * Math.sin(M), b = R * 5.128 * Math.sin(F), mdist = 385001 - 20905 * Math.cos(M);
        double ra = Math.atan2(Math.sin(l) * Math.cos(e) - Math.tan(b) * Math.sin(e), Math.cos(l));
        double dec = Math.asin(Math.sin(b) * Math.cos(e) + Math.cos(b) * Math.sin(e) * Math.sin(l));
        double sM = R * (357.5291 + 0.98560028 * d);
        double sl = sM + R * (1.9148 * Math.sin(sM) + 0.02 * Math.sin(2 * sM) + 0.0003 * Math.sin(3 * sM)) + R * 102.9372 + Math.PI;
        double sra = Math.atan2(Math.sin(sl) * Math.cos(e), Math.cos(sl)), sdec = Math.asin(Math.sin(sl) * Math.sin(e)), sdist = 149598000;
        double phi = Math.acos(clamp(Math.sin(sdec) * Math.sin(dec) + Math.cos(sdec) * Math.cos(dec) * Math.cos(sra - ra), -1, 1));
        double inc = Math.atan2(sdist * Math.sin(phi), mdist - sdist * Math.cos(phi));
        double ang = Math.atan2(Math.cos(sdec) * Math.sin(sra - ra), Math.sin(sdec) * Math.cos(dec) - Math.cos(sdec) * Math.sin(dec) * Math.cos(sra - ra));
        double H = R * (280.16 + 360.9856235 * d) + R * lon - ra, la = R * lat;
        double alt = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(H));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(la) - Math.tan(dec) * Math.cos(la));
        double tilt = ang - Math.atan2(Math.sin(H), Math.tan(la) * Math.cos(dec) - Math.sin(dec) * Math.cos(H));
        return new double[] { 0.5 + 0.5 * inc * (ang < 0 ? -1 : 1) / Math.PI, (1 + Math.cos(inc)) / 2, alt, az, tilt };
    }

    // ---- parsing
    private static double decimal(String s) { return Integer.parseInt(s.substring(11, 13)) + Integer.parseInt(s.substring(14, 16)) / 60.0; }

    private static double[] grab(JSONArray a, int n, double fallback) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (a == null || i >= a.length() || a.isNull(i)) ? fallback : a.optDouble(i, fallback);
        return out;
    }

    static String condLabel(int code, double alt) {
        switch (code) {
            case 45: return "Fog"; case 48: return "Freezing fog"; case 51: return "Light drizzle"; case 53: return "Drizzle";
            case 55: return "Heavy drizzle"; case 56: case 57: return "Freezing drizzle"; case 61: return "Light rain";
            case 63: return "Rain"; case 65: return "Heavy rain"; case 66: case 67: return "Freezing rain";
            case 71: return "Light snow"; case 73: return "Snow"; case 75: return "Heavy snow"; case 77: return "Snow grains";
            case 80: return "Light showers"; case 81: return "Showers"; case 82: return "Heavy showers";
            case 85: return "Snow showers"; case 86: return "Heavy snow showers"; case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            case 0: return alt > 0 ? "Sunny" : "Clear";
            case 1: return alt > 0 ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            default: return "";
        }
    }

    private static double rainCode(int c) {
        switch (c) { case 51: return .12; case 53: return .2; case 55: return .3; case 56: return .15; case 57: return .3;
            case 61: return .25; case 63: return .5; case 65: return .85; case 66: return .3; case 67: return .6;
            case 80: return .3; case 81: return .55; case 82: return .9; case 95: return .6; case 96: return .75; case 99: return .9;
            default: return 0; }
    }

    private static double snowCode(int c) {
        switch (c) { case 71: return .3; case 73: return .55; case 75: return .9; case 77: return .3; case 85: return .4; case 86: return .7;
            default: return 0; }
    }

    static Forecast parse(JSONObject j, double lat, double lon) throws Exception {
        Forecast f = new Forecast();
        JSONObject H = j.getJSONObject("hourly"), Dl = j.getJSONObject("daily");
        String tzId = j.optString("timezone", "UTC");
        TimeZone tz = TimeZone.getTimeZone(tzId);
        if ("GMT".equals(tz.getID()) && !"GMT".equals(tzId) && !"UTC".equals(tzId)) tz = new SimpleTimeZone(j.optInt("utc_offset_seconds", 0) * 1000, "local");
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        parser.setTimeZone(tz);

        JSONArray times = H.getJSONArray("time"), dTimes = Dl.getJSONArray("time");
        int n = times.length();
        double[] temp = grab(H.optJSONArray("temperature_2m"), n, 0), mmA = grab(H.optJSONArray("precipitation"), n, 0),
            cloudA = grab(H.optJSONArray("cloud_cover"), n, 0), visA = grab(H.optJSONArray("visibility"), n, 24000),
            codeA = grab(H.optJSONArray("weather_code"), n, 0), windA = grab(H.optJSONArray("wind_speed_10m"), n, 0),
            wdirA = grab(H.optJSONArray("wind_direction_10m"), n, 0);
        JSONArray popA = H.optJSONArray("precipitation_probability");
        double[] sr = new double[dTimes.length()], ss = new double[dTimes.length()];
        for (int i = 0; i < dTimes.length(); i++) { sr[i] = decimal(Dl.getJSONArray("sunrise").getString(i)); ss[i] = decimal(Dl.getJSONArray("sunset").getString(i)); }

        String lastKey = "";
        for (int i = 0; i < n; i++) {
            String t = times.getString(i), key = t.substring(0, 10);
            if (!key.equals(lastKey)) {
                Day d = new Day(); d.key = key; d.start = i; d.hi = -1e9; d.lo = 1e9;
                f.days.add(d); lastKey = key;
            }
            int dy = f.days.size() - 1;
            Day D = f.days.get(dy);
            D.n++;
            int di = 0;
            for (int q = 0; q < dTimes.length(); q++) if (dTimes.getString(q).equals(key)) { di = q; break; }
            double dsr = sr[di], dss = ss[di];

            Hour h = new Hour();
            h.time = t; h.day = dy; h.clock = decimal(t);
            h.temp = temp[i]; h.mm = Math.max(0, mmA[i]); h.code = (int) codeA[i];
            h.pop = (popA == null || i >= popA.length() || popA.isNull(i)) ? null : popA.optDouble(i);
            h.cloud = clamp(cloudA[i] / 100);
            double snowI = snowCode(h.code);
            h.snow = snowI;
            h.rain = snowI > 0 ? 0 : clamp(Math.max(Math.pow(h.mm / 3, 0.6), rainCode(h.code)));
            h.fog = clamp(Math.max((h.code == 45 || h.code == 48) ? 1 : 0, clamp((4000 - visA[i]) / 3500) * 0.9));
            h.storm = (h.code == 95 || h.code == 96 || h.code == 99) ? 1 : 0;
            h.wind = windA[i];
            h.wx = -Math.sin(wdirA[i] * Math.PI / 180);
            double c = h.clock, df = (c - dsr) / (dss - dsr);
            if (c >= dsr && c <= dss) { h.alt = Math.sin(Math.PI * df); h.morning = df < 0.5; }
            else { double dh = c < dsr ? dsr - c : c - dss; h.alt = -Math.min(1, dh / 1.5); h.morning = c < dsr; }
            long utc;
            try { utc = parser.parse(t).getTime(); } catch (Exception e) { utc = 0; }
            double[] mo = moonAt(utc, lat, lon);
            double mh = mo[2] * 180 / Math.PI;
            float[][] bp = basePalette(h.alt, h.morning);
            float[][] sky = skyFor(bp[0], bp[1], h.cloud, h.rain, h.fog, h.snow, h.storm, h.alt);
            h.top = sky[0]; h.bot = sky[1];
            h.sunX = 0.12 + 0.76 * clamp(df, -0.15, 1.15);
            h.sunY = h.alt >= 0 ? 0.64 - 0.5 * h.alt : 0.64 + 0.25 * clamp(-h.alt);
            h.sunB = smooth(-0.2, 0.05, h.alt);
            h.moonX = 0.5 + 0.38 * clamp(mo[3] / (Math.PI / 2), -1.15, 1.15);
            h.moonY = 0.64 - 0.5 * clamp(mh / 65, -0.5, 1);
            h.moonB = smooth(0, 6, mh) * (1 - 0.65 * smooth(-0.25, 0.25, h.alt));
            h.moonF = mo[1];
            h.moonVx = -Math.sin(mo[4]); h.moonVy = -Math.cos(mo[4]);
            h.label = condLabel(h.code, h.alt);
            D.hi = Math.max(D.hi, h.temp); D.lo = Math.min(D.lo, h.temp);
            f.hours.add(h);
        }
        String nowKey = j.has("current") ? j.getJSONObject("current").optString("time", "").substring(0, Math.min(13, j.getJSONObject("current").optString("time", "").length())) : "";
        for (int q = 0; q < f.hours.size(); q++) if (f.hours.get(q).time.startsWith(nowKey) && nowKey.length() == 13) { f.nowIdx = q; break; }
        return f;
    }
}
