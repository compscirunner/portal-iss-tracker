package com.portal.isstracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Draws an equirectangular world map letterboxed to fit, plots the ISS at its
 * ground point with a visibility-footprint circle and a trailing ground track,
 * and overlays a flight-display style telemetry HUD.
 */
class TrackerView extends View {

    // Boeing-ish flight-display palette: green labels, white values, magenta target.
    private static final int LABEL  = 0xFF34D058;
    private static final int VALUE  = 0xFFFFFFFF;
    private static final int ACCENT = 0xFFFF2EA6;
    private static final int TRAIL  = 0x9900E5FF;
    private static final int PANEL  = 0xB3000A12;

    private Bitmap world;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private final Paint mapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bg = new Paint();
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footprint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);

    private IssApi.Position pos;
    private String status = "Acquiring ISS…";
    private final Deque<double[]> trail = new ArrayDeque<>();   // {lat, lon}
    private static final int MAX_TRAIL = 240;

    // The drawn map rectangle (computed each layout); lat/lon map into it.
    private float mapL, mapT, mapW, mapH;

    TrackerView(Context ctx) {
        super(ctx);
        loadMap(ctx);

        bg.setColor(0xFF000610);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setColor(TRAIL);
        trailPaint.setStrokeWidth(dp(2));
        footprint.setStyle(Paint.Style.STROKE);
        footprint.setColor(0x55FF2EA6);
        footprint.setStrokeWidth(dp(1.5f));
        marker.setColor(ACCENT);
        crosshair.setColor(ACCENT);
        crosshair.setStyle(Paint.Style.STROKE);
        crosshair.setStrokeWidth(dp(1.5f));
        panelPaint.setColor(PANEL);

        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        label.setColor(LABEL); label.setTypeface(mono); label.setTextSize(sp(13));
        value.setColor(VALUE); value.setTypeface(mono); value.setTextSize(sp(20));
        title.setColor(LABEL); title.setTypeface(mono); title.setTextSize(sp(16));
        title.setLetterSpacing(0.15f);
    }

    private void loadMap(Context ctx) {
        try (InputStream in = ctx.getAssets().open("world.jpg")) {
            world = BitmapFactory.decodeStream(in);
            if (world != null) srcRect.set(0, 0, world.getWidth(), world.getHeight());
        } catch (Exception ignored) { /* drawn as plain dark background */ }
    }

    /** Push a new fix (called on the UI thread). */
    void setPosition(IssApi.Position p) {
        this.pos = p;
        double[] last = trail.peekLast();
        if (last == null || last[0] != p.lat || last[1] != p.lon) {
            trail.addLast(new double[]{p.lat, p.lon});
            while (trail.size() > MAX_TRAIL) trail.removeFirst();
        }
        invalidate();
    }

    void setStatus(String s) { this.status = s; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Contain the 2:1 map within the view, centered (letterbox).
        if (world == null) { mapL = 0; mapT = 0; mapW = w; mapH = h; return; }
        float ar = (float) world.getWidth() / world.getHeight();
        float vw = w, vh = h;
        if (vw / vh > ar) { mapH = vh; mapW = vh * ar; }
        else { mapW = vw; mapH = vw / ar; }
        mapL = (vw - mapW) / 2f;
        mapT = (vh - mapH) / 2f;
        dstRect.set(mapL, mapT, mapL + mapW, mapT + mapH);
    }

    private float xOf(double lon) { return mapL + (float) ((lon + 180.0) / 360.0) * mapW; }
    private float yOf(double lat) { return mapT + (float) ((90.0 - lat) / 180.0) * mapH; }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        if (world != null) c.drawBitmap(world, srcRect, dstRect, mapPaint);

        drawTrail(c);

        if (pos != null) {
            // Visibility footprint (great-circle radius -> degrees -> px on the lon axis).
            if (pos.footprintKm > 0) {
                float rPx = (float) (pos.footprintKm / 2.0 / 111.0) * (mapW / 360f);
                c.drawCircle(xOf(pos.lon), yOf(pos.lat), rPx, footprint);
            }
            float mx = xOf(pos.lon), my = yOf(pos.lat);
            c.drawCircle(mx, my, dp(6), marker);
            c.drawCircle(mx, my, dp(11), crosshair);
            c.drawLine(mx - dp(18), my, mx + dp(18), my, crosshair);
            c.drawLine(mx, my - dp(18), mx, my + dp(18), crosshair);
        }

        drawHud(c);
    }

    private void drawTrail(Canvas c) {
        if (trail.size() < 2) return;
        List<double[]> pts = new ArrayList<>(trail);
        float px = 0, py = 0;
        boolean have = false;
        for (double[] p : pts) {
            float x = xOf(p[1]), y = yOf(p[0]);
            if (have && Math.abs(p[1] - lastLon) < 180) {  // skip the date-line wrap
                c.drawLine(px, py, x, y, trailPaint);
            }
            px = x; py = y; lastLon = p[1]; have = true;
        }
    }
    private double lastLon;

    private void drawHud(Canvas c) {
        // Title strip, top-left.
        c.drawText("◉ ISS — LIVE GROUND TRACK", dp(16), mapT > dp(28) ? mapT - dp(10) : dp(22), title);

        // Telemetry panel along the bottom.
        float ph = dp(96);
        float pt = getHeight() - ph;
        c.drawRect(0, pt, getWidth(), getHeight(), panelPaint);

        float colW = getWidth() / 5f;
        float baseY = pt + dp(36);
        if (pos != null) {
            cell(c, 0, colW, baseY, "LATITUDE",  fmtDeg(pos.lat, "N", "S"));
            cell(c, 1, colW, baseY, "LONGITUDE", fmtDeg(pos.lon, "E", "W"));
            cell(c, 2, colW, baseY, "ALTITUDE",  String.format(Locale.US, "%.1f km", pos.altKm));
            cell(c, 3, colW, baseY, "VELOCITY",  String.format(Locale.US, "%,.0f km/h", pos.velKmh));
            cell(c, 4, colW, baseY, "OVER",      GeoRegion.describe(pos.lat, pos.lon));

            String vis = pos.visibility.isEmpty() ? "" : "  •  " + cap(pos.visibility);
            c.drawText(status + vis, dp(16), getHeight() - dp(14), label);
        } else {
            c.drawText(status, dp(16), pt + dp(52), value);
        }
    }

    private void cell(Canvas c, int col, float colW, float baseY, String lab, String val) {
        float x = col * colW + dp(16);
        c.drawText(lab, x, baseY, label);
        c.drawText(val, x, baseY + dp(30), value);
    }

    private static String fmtDeg(double v, String pos, String neg) {
        return String.format(Locale.US, "%.3f° %s", Math.abs(v), v >= 0 ? pos : neg);
    }
    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
