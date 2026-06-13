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
