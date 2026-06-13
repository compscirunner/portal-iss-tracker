package com.portal.isstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** People-in-space roster, grouped by spacecraft, in the same flight-display look. */
class CrewView extends View {

    private static final int LABEL = 0xFF34D058;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int CRAFT = 0xFFFF2EA6;

    private final Paint bg = new Paint();
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint craft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<IssApi.CrewMember> crew = new ArrayList<>();
    private String status = "Loading crew…";

    CrewView(Context ctx) {
        super(ctx);
        bg.setColor(0xFF000610);
        Typeface mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        title.setColor(LABEL); title.setTypeface(mono); title.setTextSize(sp(22)); title.setLetterSpacing(0.12f);
        craft.setColor(CRAFT); craft.setTypeface(mono); craft.setTextSize(sp(16));
        name.setColor(VALUE);  name.setTypeface(mono);  name.setTextSize(sp(20));
        sub.setColor(LABEL);   sub.setTypeface(mono);   sub.setTextSize(sp(13));
    }

    void setCrew(List<IssApi.CrewMember> c) { this.crew = c; invalidate(); }
    void setStatus(String s) { this.status = s; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        float x = dp(40), y = dp(56);
        c.drawText("HUMANS IN SPACE — " + crew.size(), x, y, title);
        y += dp(14);

        if (crew.isEmpty()) {
            c.drawText(status, x, y + dp(40), name);
            return;
        }

        // Group by craft, preserving first-seen order.
        Map<String, List<String>> byCraft = new LinkedHashMap<>();
        for (IssApi.CrewMember m : crew) {
            String key = m.craft == null || m.craft.isEmpty() ? "Orbit" : m.craft;
            byCraft.computeIfAbsent(key, k -> new ArrayList<>()).add(m.name);
        }

        // Two columns so a full ISS+Tiangong roster fits the Portal screen.
        float colW = getWidth() / 2f;
        float colX = x, rowY = y + dp(44);
        int col = 0;
        for (Map.Entry<String, List<String>> e : byCraft.entrySet()) {
            c.drawText("▸ " + e.getKey() + "  (" + e.getValue().size() + ")", colX, rowY, craft);
            rowY += dp(34);
            for (String person : e.getValue()) {
                c.drawText("•  " + person, colX + dp(12), rowY, name);
                rowY += dp(32);
            }
            rowY += dp(20);
            if (rowY > getHeight() - dp(80) && col == 0) {   // overflow into 2nd column
                col = 1; colX = x + colW; rowY = y + dp(44);
            }
        }
        c.drawText(status, x, getHeight() - dp(24), sub);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
