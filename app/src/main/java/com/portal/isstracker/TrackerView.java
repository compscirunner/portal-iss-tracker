package com.portal.isstracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * NASA-branded ISS display: an equirectangular world map letterboxed to fit, the
 * ISS plotted at its ground point with a visibility-footprint circle and the
 * predicted orbital ground-track path, under a NASA-blue title bar (meatball + name)
 * and a flight telemetry strip.
 */
class TrackerView extends View {

    // NASA palette.
    private static final int NASA_BLUE = 0xFF0B3D91;
    private static final int NASA_RED  = 0xFFFC3D21;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int LABEL     = 0xFFAFC6FF;   // light NASA blue
    private static final int PANEL     = 0xCC061A3A;   // translucent navy

    private Bitmap world, nasa;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final Rect nasaSrc = new Rect();
    private final RectF nasaDst = new RectF();

    private final Paint mapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bg = new Paint();
    private final Paint barPaint = new Paint();
    private final Paint trackPast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackFuture = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footprint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private IssApi.Position pos;
    private String status = "Acquiring ISS…";
    private Double prevLat;
    private boolean northbound = true;

    private float mapL, mapT, mapW, mapH;
    private float barH;

    TrackerView(Context ctx) {
        super(ctx);
        world = loadAsset(ctx, "world.jpg");
        nasa = loadAsset(ctx, "nasa.png");
        if (world != null) srcRect.set(0, 0, world.getWidth(), world.getHeight());
        if (nasa != null) nasaSrc.set(0, 0, nasa.getWidth(), nasa.getHeight());

        bg.setColor(0xFF000610);
        barPaint.setColor(NASA_BLUE);

        trackPast.setStyle(Paint.Style.STROKE);
        trackPast.setColor(0xFFFFFFFF);
        trackPast.setStrokeWidth(dp(2));
        trackFuture.setStyle(Paint.Style.STROKE);
        trackFuture.setColor(NASA_RED);
        trackFuture.setStrokeWidth(dp(2.5f));
        trackFuture.setPathEffect(new DashPathEffect(new float[]{dp(10), dp(8)}, 0));

        footprint.setStyle(Paint.Style.STROKE);
        footprint.setColor(0x55FFFFFF);
        footprint.setStrokeWidth(dp(1.5f));
        markerFill.setColor(NASA_RED);
        markerRing.setStyle(Paint.Style.STROKE);
        markerRing.setColor(WHITE);
        markerRing.setStrokeWidth(dp(2.5f));
        panelPaint.setColor(PANEL);

        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        Typeface sans = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        label.setColor(LABEL); label.setTypeface(mono); label.setTextSize(sp(13));
        value.setColor(WHITE); value.setTypeface(mono); value.setTextSize(sp(20));
        titlePaint.setColor(WHITE); titlePaint.setTypeface(sans);
        titlePaint.setTextSize(sp(22)); titlePaint.setLetterSpacing(0.12f);
        subPaint.setColor(NASA_RED); subPaint.setTypeface(mono); subPaint.setTextSize(sp(13));
        subPaint.setLetterSpacing(0.18f);
    }

    private Bitmap loadAsset(Context ctx, String name) {
        try (InputStream in = ctx.getAssets().open(name)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) { return null; }
    }

    void setPosition(IssApi.Position p) {
        if (prevLat != null && p.lat != prevLat) northbound = p.lat > prevLat;
        prevLat = p.lat;
        this.pos = p;
        invalidate();
    }

    void setStatus(String s) { this.status = s; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        barH = dp(56);
        if (world == null) { mapL = 0; mapT = 0; mapW = w; mapH = h; }
        else {
            float ar = (float) world.getWidth() / world.getHeight();
            if ((float) w / h > ar) { mapH = h; mapW = h * ar; }
            else { mapW = w; mapH = w / ar; }
            mapL = (w - mapW) / 2f;
            mapT = (h - mapH) / 2f;
            dstRect.set(mapL, mapT, mapL + mapW, mapT + mapH);
        }
        if (nasa != null) {
            float lh = dp(40), lw = lh * nasa.getWidth() / nasa.getHeight();
            float ly = (barH - lh) / 2f;
            nasaDst.set(dp(14), ly, dp(14) + lw, ly + lh);
        }
    }

    private float xOf(double lon) { return mapL + (float) ((lon + 180.0) / 360.0) * mapW; }
    private float yOf(double lat) { return mapT + (float) ((90.0 - lat) / 180.0) * mapH; }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        if (world != null) c.drawBitmap(world, srcRect, dstRect, mapPaint);

        if (pos != null) {
            // Predicted ground track: solid for the recent past, dashed ahead.
            buildPath(OrbitTrack.predict(pos.lat, pos.lon, northbound, -28, 0, 1.0), c, trackPast);
            buildPath(OrbitTrack.predict(pos.lat, pos.lon, northbound, 0, 96, 1.0), c, trackFuture);

            if (pos.footprintKm > 0) {
                float rPx = (float) (pos.footprintKm / 2.0 / 111.0) * (mapW / 360f);
                c.drawCircle(xOf(pos.lon), yOf(pos.lat), rPx, footprint);
            }
            float mx = xOf(pos.lon), my = yOf(pos.lat);
            c.drawCircle(mx, my, dp(7), markerFill);
            c.drawCircle(mx, my, dp(11), markerRing);
        }

        drawTitleBar(c);
        drawHud(c);
    }

    /** Build a Canvas path from {lat,lon} points, breaking subpaths at the date line. */
    private void buildPath(List<double[]> pts, Canvas c, Paint paint) {
        Path path = new Path();
        boolean started = false;
        double lastLon = 0;
        for (double[] p : pts) {
            float x = xOf(p[1]), y = yOf(p[0]);
            if (!started || Math.abs(p[1] - lastLon) > 180) path.moveTo(x, y);
            else path.lineTo(x, y);
            started = true; lastLon = p[1];
        }
        c.drawPath(path, paint);
    }

    private void drawTitleBar(Canvas c) {
        c.drawRect(0, 0, getWidth(), barH, barPaint);
        if (nasa != null) c.drawBitmap(nasa, nasaSrc, nasaDst, logoPaint);
        float tx = nasaDst.right + dp(16);
        c.drawText("INTERNATIONAL SPACE STATION", tx, barH / 2f - dp(2), titlePaint);
        c.drawText("LIVE ORBITAL GROUND TRACK", tx, barH / 2f + dp(18), subPaint);
    }

    private void drawHud(Canvas c) {
        float ph = dp(92);
        float pt = getHeight() - ph;
        c.drawRect(0, pt, getWidth(), getHeight(), panelPaint);
        c.drawRect(0, pt, getWidth(), pt + dp(3), barPaint);   // NASA-blue rule

        float colW = getWidth() / 5f;
        float baseY = pt + dp(34);
        if (pos != null) {
            cell(c, 0, colW, baseY, "LATITUDE",  fmtDeg(pos.lat, "N", "S"));
            cell(c, 1, colW, baseY, "LONGITUDE", fmtDeg(pos.lon, "E", "W"));
            cell(c, 2, colW, baseY, "ALTITUDE",  String.format(Locale.US, "%.1f km", pos.altKm));
            cell(c, 3, colW, baseY, "VELOCITY",  String.format(Locale.US, "%,.0f km/h", pos.velKmh));
            cell(c, 4, colW, baseY, "OVER",      GeoRegion.describe(pos.lat, pos.lon));

            String vis = pos.visibility.isEmpty() ? "" : "  •  " + cap(pos.visibility);
            c.drawText(status + vis, dp(16), getHeight() - dp(12), label);
        } else {
            c.drawText(status, dp(16), pt + dp(52), value);
        }
    }

    private void cell(Canvas c, int col, float colW, float baseY, String lab, String val) {
        float x = col * colW + dp(16);
        c.drawText(lab, x, baseY, label);
        c.drawText(val, x, baseY + dp(28), value);
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
