package com.portal.isstracker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kiosk-style ISS display for Meta Portal (no Google services needed).
 * Shows the live map/telemetry and the people-in-space roster side by side on
 * one screen, refreshing the ground position every few seconds.
 */
public class MainActivity extends Activity {

    private static final long POS_INTERVAL_MS  = 5_000;       // position refresh
    private static final long CREW_INTERVAL_MS = 5 * 60_000;  // crew refresh

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US);

    private TrackerView tracker;
    private CrewView crewView;
    private FeedView feedView;
    private volatile boolean running = true;
    private long lastCrewFetch = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);

        tracker = new TrackerView(this);
        crewView = new CrewView(this);
        feedView = new FeedView(this);

        // Left (hero): labelled live ISS camera, taking ~64% of the width.
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.addView(feedLabel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        leftCol.addView(feedView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Right column: map+HUD over the crew roster.
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.addView(tracker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.52f));
        rightCol.addView(crewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.48f));

        root.addView(leftCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.64f));
        root.addView(rightCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.36f));
        setContentView(root);

        clock.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    private TextView feedLabel() {
        TextView t = new TextView(this);
        t.setText("● LIVE · ISS 4K — SEN");
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        t.setTextSize(13);
        t.setLetterSpacing(0.12f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        t.setBackgroundColor(0xFF0B3D91);   // NASA blue
        return t;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    private void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        if (feedView != null) feedView.resume();
        ui.post(pollPosition);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        ui.removeCallbacksAndMessages(null);
        if (feedView != null) feedView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
        if (feedView != null) feedView.release();
    }

    private final Runnable pollPosition = new Runnable() {
        @Override public void run() {
            if (!running) return;
            net.execute(() -> {
                try {
                    IssApi.Position p = IssApi.fetchPosition();
                    ui.post(() -> {
                        tracker.setPosition(p);
                        tracker.setStatus("Updated " + clock.format(new Date()));
                    });
                } catch (Exception e) {
                    ui.post(() -> tracker.setStatus("ISS feed unavailable — retrying…"));
                }
            });
            maybeFetchCrew();
            ui.postDelayed(this, POS_INTERVAL_MS);
        }
    };

    private void maybeFetchCrew() {
        long now = System.currentTimeMillis();
        if (now - lastCrewFetch < CREW_INTERVAL_MS) return;
        lastCrewFetch = now;
        net.execute(() -> {
            try {
                List<IssApi.CrewMember> crew = IssApi.fetchCrew();
                ui.post(() -> {
                    crewView.setCrew(crew);
                    crewView.setStatus("Open Notify  •  " + clock.format(new Date()));
                });
            } catch (Exception e) {
                lastCrewFetch = 0;   // let the next poll retry instead of waiting 5 min
                ui.post(() -> crewView.setStatus("Crew feed unavailable — retrying…"));
            }
        });
    }
}
