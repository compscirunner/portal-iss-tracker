package com.portal.isstracker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kiosk-style ISS display for Meta Portal (no Google services needed).
 * Polls the ground position every few seconds and auto-cycles between the
 * map/telemetry view and the people-in-space roster. Tap anywhere to flip.
 */
public class MainActivity extends Activity {

    private static final long POS_INTERVAL_MS  = 5_000;     // position refresh
    private static final long CREW_INTERVAL_MS = 5 * 60_000; // crew refresh
    private static final long MAP_DWELL_MS  = 22_000;        // time on map view
    private static final long CREW_DWELL_MS = 12_000;        // time on crew view

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US);

    private TrackerView tracker;
    private CrewView crewView;
    private boolean showingCrew = false;
    private volatile boolean running = true;
    private long lastCrewFetch = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        tracker = new TrackerView(this);
        crewView = new CrewView(this);
        crewView.setVisibility(View.GONE);
        root.addView(tracker);
        root.addView(crewView);
        root.setOnClickListener(v -> { toggleView(); restartCycle(); });
        setContentView(root);

        clock.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

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
        ui.post(pollPosition);
        ui.postDelayed(cycleView, MAP_DWELL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
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
                    crewView.setStatus("Source: Open Notify  •  " + clock.format(new Date()));
                });
            } catch (Exception e) {
                ui.post(() -> crewView.setStatus("Crew feed unavailable"));
            }
        });
    }

    private final Runnable cycleView = new Runnable() {
        @Override public void run() {
            if (!running) return;
            toggleView();
            ui.postDelayed(this, showingCrew ? CREW_DWELL_MS : MAP_DWELL_MS);
        }
    };

    private void toggleView() {
        showingCrew = !showingCrew;
        crewView.setVisibility(showingCrew ? View.VISIBLE : View.GONE);
        tracker.setVisibility(showingCrew ? View.GONE : View.VISIBLE);
    }

    private void restartCycle() {
        ui.removeCallbacks(cycleView);
        ui.postDelayed(cycleView, showingCrew ? CREW_DWELL_MS : MAP_DWELL_MS);
    }
}
