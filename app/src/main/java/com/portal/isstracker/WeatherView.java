package com.portal.isstracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Space-weather card: live Sun image (NASA SDO) + Kp index, solar wind, X-ray flare. */
class WeatherView extends View {

    private static final int NASA_BLUE = 0xFF0B3D91;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int LABEL     = 0xFFAFC6FF;
    private static final String SUN_URL = "https://sdo.gsfc.nasa.gov/assets/img/latest/latest_512_0193.jpg";
    private static final long SUN_REFRESH_MS = 10 * 60_000;

    private final Paint bg = new Paint();
    private final Paint barPaint = new Paint();
    private final Paint sunPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foot = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Bitmap sun;
    private IssApi.SpaceWeather sw;
    private String status = "Loading space weather…";
    private float barH;

    WeatherView(Context ctx) {
        super(ctx);
        bg.setColor(0xFF04132E);
        barPaint.setColor(NASA_BLUE);
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        Typeface sans = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        title.setColor(WHITE); title.setTypeface(sans); title.setTextSize(sp(22)); title.setLetterSpacing(0.12f);
        label.setColor(LABEL); label.setTypeface(mono); label.setTextSize(sp(13));
        value.setColor(WHITE); value.setTypeface(mono); value.setTextSize(sp(26));
        foot.setColor(LABEL); foot.setTypeface(mono); foot.setTextSize(sp(13));
        loadSun();
    }

    void setWeather(IssApi.SpaceWeather w, String stamp) {
        this.sw = w;
        this.status = "NOAA SWPC  •  " + stamp;
        invalidate();
    }
    void setStatus(String s) { this.status = s; invalidate(); }

    private void loadSun() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SUN_URL).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "PortalIssTracker/1.0");
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) ui.post(() -> { sun = b; invalidate(); });
                }
                c.disconnect();
            } catch (Exception ignored) {}
        }, "sdo-sun").start();
        ui.postDelayed(this::loadSun, SUN_REFRESH_MS);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { barH = dp(56); }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawRect(0, 0, getWidth(), barH, barPaint);
        c.drawText("SPACE WEATHER", dp(16), barH / 2f + sp(8), title);

        // Sun image, square, left side.
        float pad = dp(16);
        float side = Math.min(getWidth() * 0.42f, getHeight() - barH - 2 * pad);
        float sx = pad, sy = barH + (getHeight() - barH - side) / 2f;
        if (sun != null) {
            c.drawBitmap(sun, new Rect(0, 0, sun.getWidth(), sun.getHeight()),
                    new RectF(sx, sy, sx + side, sy + side), sunPaint);
        }
        c.drawText("SDO · 193Å", sx, sy + side + dp(18), foot);

        // Readings on the right.
        float rx = sx + side + dp(28);
        float ry = barH + dp(40);
        if (sw != null) {
            reading(c, rx, ry, "Kp INDEX", sw.kp >= 0 ? String.format(Locale.US, "%.1f", sw.kp) : "—",
                    kpColor(sw.kp), kpNote(sw.kp));
            ry += dp(86);
            reading(c, rx, ry, "SOLAR WIND",
                    sw.windKmS >= 0 ? String.format(Locale.US, "%.0f km/s", sw.windKmS) : "—", WHITE, null);
            ry += dp(86);
            reading(c, rx, ry, "X-RAY FLUX", sw.flare, flareColor(sw.flare), null);
        } else {
            c.drawText(status, rx, ry, label);
        }
        c.drawText(status, dp(16), getHeight() - dp(16), foot);
    }

    private void reading(Canvas c, float x, float y, String lab, String val, int color, String note) {
        c.drawText(lab, x, y, label);
        value.setColor(color);
        c.drawText(val, x, y + dp(34), value);
        if (note != null) {
            float w = value.measureText(val);
            c.drawText(note, x + w + dp(14), y + dp(34), foot);
        }
    }

    private static int kpColor(double kp) {
        if (kp < 0) return WHITE;
        if (kp < 4) return 0xFF34D058;
        if (kp < 5) return 0xFFE5C100;
        if (kp < 6) return 0xFFFF8C00;
        return 0xFFFC3D21;
    }
    private static String kpNote(double kp) {
        if (kp < 0) return null;
        if (kp >= 5) return "GEOMAGNETIC STORM";
        if (kp >= 4) return "ACTIVE";
        return "QUIET";
    }
    private static int flareColor(String f) {
        if (f == null || f.isEmpty()) return WHITE;
        char c = f.charAt(0);
        if (c == 'X') return 0xFFFC3D21;
        if (c == 'M') return 0xFFFF8C00;
        if (c == 'C') return 0xFFE5C100;
        return 0xFF34D058;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
