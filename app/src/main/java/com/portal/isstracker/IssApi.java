package com.portal.isstracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin client for the two free, key-less APIs the tracker uses:
 *   - api.wheretheiss.at  -> live ISS ground position + telemetry (HTTPS)
 *   - api.open-notify.org -> who is currently in space (HTTP only)
 * All methods block; call them from a worker thread.
 */
final class IssApi {

    private static final String POS_URL =
            "https://api.wheretheiss.at/v1/satellites/25544";
    private static final String CREW_URL =
            "http://api.open-notify.org/astros.json";

    /** A single ISS fix. Altitude in km, velocity in km/h. */
    static final class Position {
        double lat, lon, altKm, velKmh, footprintKm;
        double solarLat, solarLon;   // sub-solar point, for the day/night terminator
        String visibility = "";
        long timestamp;   // unix seconds, from the API
    }

    static final class CrewMember {
        final String name, craft;
        CrewMember(String name, String craft) { this.name = name; this.craft = craft; }
    }

    static Position fetchPosition() throws Exception {
        JSONObject o = new JSONObject(httpGet(POS_URL));
        Position p = new Position();
        p.lat = o.getDouble("latitude");
        p.lon = o.getDouble("longitude");
        p.altKm = o.getDouble("altitude");
        p.velKmh = o.getDouble("velocity");
        p.footprintKm = o.optDouble("footprint", 0);
        p.visibility = o.optString("visibility", "");
        p.solarLat = o.optDouble("solar_lat", 0);
        p.solarLon = o.optDouble("solar_lon", 0);
        p.timestamp = o.optLong("timestamp", System.currentTimeMillis() / 1000L);
        return p;
    }

    /** Next upcoming launch (The Space Devs' Launch Library 2 — free, no key). */
    static final class LaunchInfo {
        String name = "", rocket = "", provider = "", pad = "", status = "";
        long netMs;   // launch time (epoch millis), 0 if unknown
    }

    static LaunchInfo fetchNextLaunch() throws Exception {
        String json = httpGet(
                "https://ll.thespacedevs.com/2.2.0/launch/upcoming/?limit=1&hide_recent_previous=true");
        JSONArray results = new JSONObject(json).getJSONArray("results");
        if (results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);

        LaunchInfo li = new LaunchInfo();
        li.name = r.optString("name", "");
        li.status = r.optJSONObject("status") != null
                ? r.getJSONObject("status").optString("name", "") : "";
        li.provider = nested(r, "launch_service_provider", "name");
        JSONObject rocket = r.optJSONObject("rocket");
        if (rocket != null && rocket.optJSONObject("configuration") != null)
            li.rocket = rocket.getJSONObject("configuration").optString("name", "");
        JSONObject pad = r.optJSONObject("pad");
        if (pad != null) {
            String padName = pad.optString("name", "");
            String loc = pad.optJSONObject("location") != null
                    ? pad.getJSONObject("location").optString("name", "") : "";
            li.pad = loc.isEmpty() ? padName : (padName.isEmpty() ? loc : padName + ", " + loc);
        }
        li.netMs = parseIso(r.optString("net", ""));
        return li;
    }

    /** Space weather from NOAA SWPC (free, no key): planetary Kp, solar-wind
     *  speed, and the latest GOES soft X-ray flare class. */
    static final class SpaceWeather {
        double kp = -1;          // planetary K index, 0..9
        double windKmS = -1;     // solar wind bulk speed, km/s
        String flare = "—";      // soft X-ray class, e.g. "C2.3"
    }

    static SpaceWeather fetchSpaceWeather() throws Exception {
        SpaceWeather sw = new SpaceWeather();
        try {
            JSONArray k = new JSONArray(httpGet(
                    "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"));
            sw.kp = Double.parseDouble(k.getJSONArray(k.length() - 1).getString(1));
        } catch (Exception ignored) {}
        try {
            JSONArray w = new JSONArray(httpGet(
                    "https://services.swpc.noaa.gov/products/solar-wind/plasma-2-hour.json"));
            // columns: time_tag, density, speed, temperature
            for (int i = w.length() - 1; i > 0; i--) {
                String v = w.getJSONArray(i).optString(2, "");
                if (!v.isEmpty() && !"null".equals(v)) { sw.windKmS = Double.parseDouble(v); break; }
            }
        } catch (Exception ignored) {}
        try {
            JSONArray x = new JSONArray(httpGet(
                    "https://services.swpc.noaa.gov/json/goes/primary/xrays-6-hour.json"));
            double flux = -1;
            for (int i = 0; i < x.length(); i++) {
                JSONObject o = x.getJSONObject(i);
                if ("0.1-0.8nm".equals(o.optString("energy"))) flux = o.optDouble("flux", flux);
            }
            sw.flare = classify(flux);
        } catch (Exception ignored) {}
        return sw;
    }

    private static String classify(double f) {
        if (f <= 0) return "—";
        char c; double s;
        if (f >= 1e-4) { c = 'X'; s = 1e-4; }
        else if (f >= 1e-5) { c = 'M'; s = 1e-5; }
        else if (f >= 1e-6) { c = 'C'; s = 1e-6; }
        else if (f >= 1e-7) { c = 'B'; s = 1e-7; }
        else { c = 'A'; s = 1e-8; }
        return String.format(Locale.US, "%c%.1f", c, f / s);
    }

    /** Deep Space Network "now": which spacecraft are talking to which antenna. */
    static String fetchDsn() throws Exception {
        String xml = httpGet("https://eyes.nasa.gov/dsn/data/dsn.xml");
        Matcher dm = Pattern.compile("<dish name=\"(DSS\\d+)\"[^>]*>(.*?)</dish>", Pattern.DOTALL).matcher(xml);
        Pattern tp = Pattern.compile("<target name=\"([^\"]+)\"");
        List<String> items = new ArrayList<>();
        while (dm.find() && items.size() < 4) {
            String dish = dm.group(1);
            Matcher tm = tp.matcher(dm.group(2));
            while (tm.find()) {
                String t = tm.group(1);
                if (isCraft(t)) { items.add(t + " ↔ " + dish + " " + dsnSite(dish)); break; }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String it : items) { if (sb.length() > 0) sb.append("    ·    "); sb.append(it); }
        return sb.toString();
    }

    private static boolean isCraft(String t) {
        if (t == null || t.isEmpty()) return false;
        if (t.matches("\\d+")) return false;
        return !t.equalsIgnoreCase("DSN") && !t.equalsIgnoreCase("TEST")
                && !t.startsWith("RFI") && !t.equalsIgnoreCase("None");
    }

    private static String dsnSite(String dss) {
        try {
            int n = Integer.parseInt(dss.substring(3));
            if (n < 30) return "Goldstone";
            if (n < 50) return "Canberra";
            return "Madrid";
        } catch (Exception e) { return ""; }
    }

    /** Next near-Earth asteroid close approach (JPL CAD API, free, no key). */
    static String fetchAsteroid() throws Exception {
        String json = httpGet("https://ssd-api.jpl.nasa.gov/cad.api?date-min=now&sort=date&limit=1");
        JSONObject o = new JSONObject(json);
        if (o.optInt("count", 0) == 0) return "";
        JSONArray fields = o.getJSONArray("fields");
        int iDes = -1, iCd = -1, iDist = -1, iV = -1;
        for (int i = 0; i < fields.length(); i++) {
            switch (fields.getString(i)) {
                case "des":   iDes = i; break;
                case "cd":    iCd = i; break;
                case "dist":  iDist = i; break;
                case "v_rel": iV = i; break;
            }
        }
        JSONArray row = o.getJSONArray("data").getJSONArray(0);
        String des = iDes >= 0 ? row.getString(iDes) : "?";
        String cd = iCd >= 0 ? row.getString(iCd) : "";
        double ld = iDist >= 0 ? Double.parseDouble(row.getString(iDist)) * 389.17 : 0;  // AU → lunar distances
        double v = iV >= 0 ? Double.parseDouble(row.getString(iV)) : 0;
        return String.format(Locale.US, "%s  ·  %s UTC  ·  %.1f LD  ·  %.1f km/s", des, cd, ld, v);
    }

    private static String nested(JSONObject o, String key, String sub) {
        JSONObject child = o.optJSONObject(key);
        return child != null ? child.optString(sub, "") : "";
    }

    /** Parse an ISO-8601 UTC timestamp like 2026-06-15T03:40:00Z to epoch millis. */
    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            // Trim a trailing 'Z' and any fractional seconds.
            String s = iso.replace("Z", "");
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            return fmt.parse(s).getTime();
        } catch (Exception e) { return 0; }
    }

    static List<CrewMember> fetchCrew() throws Exception {
        JSONObject o = new JSONObject(httpGet(CREW_URL));
        JSONArray people = o.getJSONArray("people");
        List<CrewMember> out = new ArrayList<>();
        for (int i = 0; i < people.length(); i++) {
            JSONObject m = people.getJSONObject(i);
            out.add(new CrewMember(m.getString("name"), m.optString("craft", "")));
        }
        return out;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "PortalIssTracker/1.0");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new Exception("HTTP " + code + " (no body) from " + url);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " from " + url);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private IssApi() {}
}
