package com.portal.isstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/** Full-width footer ticker: the next orbital launch with a live T- countdown. */
class LaunchBar extends View {

    private static final int NASA_BLUE = 0xFF0B3D91;
    private static final int NASA_RED  = 0xFFFC3D21;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int LABEL     = 0xFFAFC6FF;

    private final Paint bg = new Paint();
    private final Paint rule = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint count = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private IssApi.LaunchInfo launch;
    private String status = "loading…";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            postDelayed(this, 1000);   // live countdown
        }
    };

    LaunchBar(Context ctx) {
        super(ctx);
        bg.setColor(NASA_BLUE);
        rule.setColor(NASA_RED);
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        label.setColor(NASA_RED); label.setTypeface(mono); label.setTextSize(sp(13)); label.setLetterSpacing(0.12f);
        count.setColor(WHITE); count.setTypeface(mono); count.setTextSize(sp(20));
        detail.setColor(LABEL); detail.setTypeface(mono); detail.setTextSize(sp(15));
    }

    void setLaunch(IssApi.LaunchInfo li) { this.launch = li; invalidate(); }
    void setStatus(String s) { this.status = s; invalidate(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(tick); }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawRect(0, 0, getWidth(), dp(2), rule);   // NASA-red top rule

        float y = getHeight() / 2f + sp(7);
        float x = dp(16);
        c.drawText("NEXT LAUNCH", x, y, label);
        x += label.measureText("NEXT LAUNCH") + dp(20);

        if (launch == null) {
            c.drawText(status, x, y, detail);
            return;
        }

        String cd = countdown(launch.netMs);
        c.drawText(cd, x, y, count);
        x += count.measureText(cd) + dp(20);

        StringBuilder d = new StringBuilder();
        append(d, launch.name);
        append(d, launch.provider);
        append(d, launch.pad);
        if (!launch.status.isEmpty()) append(d, "[" + launch.status + "]");
        float avail = Math.max(0, getWidth() - x - dp(16));
        CharSequence clipped = TextUtils.ellipsize(d.toString(), detail, avail, TextUtils.TruncateAt.END);
        c.drawText(clipped, 0, clipped.length(), x, y, detail);
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
