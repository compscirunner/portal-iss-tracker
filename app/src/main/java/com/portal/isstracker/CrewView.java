package com.portal.isstracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** People-in-space roster, grouped by spacecraft, under the NASA title bar. */
class CrewView extends View {

    private static final int NASA_BLUE = 0xFF0B3D91;
    private static final int NASA_RED  = 0xFFFC3D21;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int LABEL     = 0xFFAFC6FF;

    private Bitmap nasa;
    private final Rect nasaSrc = new Rect();
    private final RectF nasaDst = new RectF();

    private final Paint bg = new Paint();
    private final Paint barPaint = new Paint();
    private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint craft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foot  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<IssApi.CrewMember> crew = new ArrayList<>();
    private String status = "Loading crew…";
    private float barH;

    CrewView(Context ctx) {
        super(ctx);
        try (InputStream in = ctx.getAssets().open("nasa.png")) {
            nasa = BitmapFactory.decodeStream(in);
            if (nasa != null) nasaSrc.set(0, 0, nasa.getWidth(), nasa.getHeight());
        } catch (Exception ignored) {}

        bg.setColor(0xFF04132E);
        barPaint.setColor(NASA_BLUE);
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        Typeface sans = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        title.setColor(WHITE); title.setTypeface(sans); title.setTextSize(sp(22)); title.setLetterSpacing(0.12f);
        sub.setColor(NASA_RED); sub.setTypeface(mono); sub.setTextSize(sp(13)); sub.setLetterSpacing(0.18f);
        craft.setColor(NASA_RED); craft.setTypeface(mono); craft.setTextSize(sp(17));
        name.setColor(WHITE); name.setTypeface(mono); name.setTextSize(sp(20));
        foot.setColor(LABEL); foot.setTypeface(mono); foot.setTextSize(sp(13));
    }

    void setCrew(List<IssApi.CrewMember> c) { this.crew = c; invalidate(); }
    void setStatus(String s) { this.status = s; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        barH = dp(56);
        if (nasa != null) {
            float lh = dp(40), lw = lh * nasa.getWidth() / nasa.getHeight();
            float ly = (barH - lh) / 2f;
            nasaDst.set(dp(14), ly, dp(14) + lw, ly + lh);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawRect(0, 0, getWidth(), barH, barPaint);
        if (nasa != null) c.drawBitmap(nasa, nasaSrc, nasaDst, logoPaint);
        float tx = nasaDst.right + dp(16);
        c.drawText("HUMANS IN SPACE — " + crew.size(), tx, barH / 2f - dp(2), title);
        c.drawText("CURRENT ON-ORBIT CREW", tx, barH / 2f + dp(18), sub);

        float x = dp(40), y = barH + dp(48);
        if (crew.isEmpty()) {
            c.drawText(status, x, y, name);
            return;
        }

        Map<String, List<String>> byCraft = new LinkedHashMap<>();
        for (IssApi.CrewMember m : crew) {
            String key = m.craft == null || m.craft.isEmpty() ? "Orbit" : m.craft;
            byCraft.computeIfAbsent(key, k -> new ArrayList<>()).add(m.name);
        }

        float rowY = y;
        for (Map.Entry<String, List<String>> e : byCraft.entrySet()) {
            c.drawText("▸ " + e.getKey() + "  (" + e.getValue().size() + ")", x, rowY, craft);
            rowY += dp(34);
            for (String person : e.getValue()) {
                c.drawText("•  " + person, x + dp(12), rowY, name);
                rowY += dp(32);
            }
            rowY += dp(20);
        }
        c.drawText(status, x, getHeight() - dp(22), foot);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
