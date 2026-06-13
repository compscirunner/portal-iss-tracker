package com.portal.isstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-width footer ticker that rotates through space facts: the next launch
 * (with a live T- countdown), the Deep Space Network "now", and the next
 * near-Earth asteroid close approach. Cycles every {@link #DWELL_S} seconds and
 * advances on tap; empty items are skipped.
 */
class Ticker extends View {

    private static final int NASA_BLUE = 0xFF0B3D91;
    private static final int NASA_RED  = 0xFFFC3D21;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int LABEL     = 0xFFAFC6FF;
    private static final int DWELL_S   = 9;

    private static final int LAUNCH = 0, DSN = 1, ASTEROID = 2;

    private final Paint bg = new Paint();
    private final Paint rule = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint count = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private IssApi.LaunchInfo launch;
    private String dsn = "", asteroid = "";
    private int mode = LAUNCH;
    private int seconds = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            seconds++;
            if (seconds % DWELL_S == 0) advance();
            invalidate();
            postDelayed(this, 1000);
        }
    };

    Ticker(Context ctx) {
        super(ctx);
        bg.setColor(NASA_BLUE);
        rule.setColor(NASA_RED);
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        label.setColor(NASA_RED); label.setTypeface(mono); label.setTextSize(sp(13)); label.setLetterSpacing(0.12f);
        count.setColor(WHITE); count.setTypeface(mono); count.setTextSize(sp(20));
        detail.setColor(LABEL); detail.setTypeface(mono); detail.setTextSize(sp(15));
        setOnClickListener(v -> advance());
    }

    void setLaunch(IssApi.LaunchInfo li) { launch = li; invalidate(); }
    void setDsn(String s) { dsn = s == null ? "" : s; invalidate(); }
    void setAsteroid(String s) { asteroid = s == null ? "" : s; invalidate(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(tick); }

    private List<Integer> available() {
        List<Integer> a = new ArrayList<>();
        if (launch != null) a.add(LAUNCH);
        if (!dsn.isEmpty()) a.add(DSN);
        if (!asteroid.isEmpty()) a.add(ASTEROID);
        return a;
    }

    private void advance() {
        List<Integer> a = available();
        if (a.isEmpty()) return;
        int idx = a.indexOf(mode);
        mode = a.get((idx + 1) % a.size());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawRect(0, 0, getWidth(), dp(2), rule);

        List<Integer> a = available();
        if (!a.isEmpty() && !a.contains(mode)) mode = a.get(0);

        float y = getHeight() / 2f + sp(7);
        float x = dp(16);
        String lab = mode == DSN ? "DEEP SPACE NETWORK"
                   : mode == ASTEROID ? "CLOSE APPROACH" : "NEXT LAUNCH";
        c.drawText(lab, x, y, label);
        x += label.measureText(lab) + dp(20);

        if (mode == LAUNCH && launch != null) {
            String cd = countdown(launch.netMs);
            c.drawText(cd, x, y, count);
            x += count.measureText(cd) + dp(20);
            StringBuilder d = new StringBuilder();
            append(d, launch.name); append(d, launch.provider); append(d, launch.pad);
            if (!launch.status.isEmpty()) append(d, "[" + launch.status + "]");
            drawDetail(c, d.toString(), x, y);
        } else if (mode == DSN) {
            drawDetail(c, dsn, x, y);
        } else if (mode == ASTEROID) {
            drawDetail(c, asteroid, x, y);
        } else {
            c.drawText("acquiring telemetry…", x, y, detail);
        }
    }

    private void drawDetail(Canvas c, String text, float x, float y) {
        float avail = Math.max(0, getWidth() - x - dp(16));
        CharSequence s = TextUtils.ellipsize(text, detail, avail, TextUtils.TruncateAt.END);
        c.drawText(s, 0, s.length(), x, y, detail);
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null || part.isEmpty()) return;
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(part);
    }

    private static String countdown(long netMs) {
        if (netMs <= 0) return "T- --:--:--";
        long sec = (netMs - System.currentTimeMillis()) / 1000L;
        if (sec <= 0) return "LIFTOFF / T+";
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return d > 0
                ? String.format(Locale.US, "T- %dd %02d:%02d:%02d", d, h, m, s)
                : String.format(Locale.US, "T- %02d:%02d:%02d", h, m, s);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
