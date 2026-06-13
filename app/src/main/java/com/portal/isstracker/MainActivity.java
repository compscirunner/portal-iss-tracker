package com.portal.isstracker;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kiosk-style ISS dashboard for Meta Portal (no Google services needed):
 * a live ISS video feed (hero), a ground-track map with day/night terminator,
 * a rotating widget panel (humans in space / space weather), and a footer
 * ticker (next launch / Deep Space Network / asteroid close approach).
 */
public class MainActivity extends Activity {

    private static final long POS_INTERVAL_MS      = 5_000;        // position refresh
    private static final long CREW_INTERVAL_MS     = 5 * 60_000;   // crew refresh
    private static final long LAUNCH_INTERVAL_MS   = 30 * 60_000;  // next-launch refresh
    private static final long WEATHER_INTERVAL_MS  = 10 * 60_000;  // space-weather refresh
    private static final long DSN_INTERVAL_MS      = 2 * 60_000;   // DSN refresh
    private static final long ASTEROID_INTERVAL_MS = 6 * 3600_000L;// close-approach refresh
    private static final long WIDGET_DWELL_MS      = 14_000;       // rotate the widget panel

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US);

    private TrackerView tracker;
    private CrewView crewView;
    private WeatherView weatherView;
    private FeedView feedView;
    private Ticker ticker;

    private View[] widgets;
    private int widgetIdx = 0;
    private volatile boolean running = true;
    private long lastCrewFetch, lastLaunchFetch, lastWeatherFetch, lastDsnFetch, lastAsteroidFetch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tracker = new TrackerView(this);
        crewView = new CrewView(this);
        weatherView = new WeatherView(this);
        feedView = new FeedView(this);
        ticker = new Ticker(this);
        widgets = new View[]{crewView, weatherView};
        showWidget(0);

        buildLayout();
        clock.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }

    /** (Re)assemble the view tree for the current orientation, reusing the same
     *  view instances so rotating doesn't reload the live feed. Landscape puts
     *  the video beside a map/widget column; portrait stacks them. */
    private void buildLayout() {
        boolean portrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        for (View v : new View[]{feedView, tracker, crewView, weatherView, ticker}) detach(v);

        LinearLayout feedCol = new LinearLayout(this);   // label + video
        feedCol.setOrientation(LinearLayout.VERTICAL);
        feedCol.addView(feedLabel(), new LinearLayout.LayoutParams(MATCH, dp(34)));
        feedCol.addView(feedView, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        FrameLayout widgetPanel = new FrameLayout(this);   // humans ⇄ weather
        widgetPanel.addView(crewView);
        widgetPanel.addView(weatherView);
        widgetPanel.setOnClickListener(v -> { showWidget(widgetIdx + 1); restartWidgetCycle(); });

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);

        if (portrait) {
            // Video on top, map and widget panel stacked below, then the ticker.
            outer.addView(feedCol, new LinearLayout.LayoutParams(MATCH, 0, 0.42f));
            outer.addView(tracker, new LinearLayout.LayoutParams(MATCH, 0, 0.29f));
            outer.addView(widgetPanel, new LinearLayout.LayoutParams(MATCH, 0, 0.29f));
        } else {
            // Video left (~64%); map over the widget panel on the right.
            LinearLayout rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.addView(tracker, new LinearLayout.LayoutParams(MATCH, 0, 0.46f));
            rightCol.addView(widgetPanel, new LinearLayout.LayoutParams(MATCH, 0, 0.54f));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.addView(feedCol, new LinearLayout.LayoutParams(0, MATCH, 0.64f));
            content.addView(rightCol, new LinearLayout.LayoutParams(0, MATCH, 0.36f));
            outer.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        }
        outer.addView(ticker, new LinearLayout.LayoutParams(MATCH, dp(46)));
        setContentView(outer);
    }

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;

    private static void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildLayout();
        goImmersive();
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

    private void showWidget(int i) {
        widgetIdx = ((i % widgets.length) + widgets.length) % widgets.length;
        for (int k = 0; k < widgets.length; k++)
            widgets[k].setVisibility(k == widgetIdx ? View.VISIBLE : View.GONE);
    }

    private final Runnable widgetCycle = new Runnable() {
        @Override public void run() {
            if (!running) return;
            showWidget(widgetIdx + 1);
            ui.postDelayed(this, WIDGET_DWELL_MS);
        }
    };
    private void restartWidgetCycle() {
        ui.removeCallbacks(widgetCycle);
        ui.postDelayed(widgetCycle, WIDGET_DWELL_MS);
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
        ui.postDelayed(widgetCycle, WIDGET_DWELL_MS);
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
            maybeFetchLaunch();
            maybeFetchWeather();
            maybeFetchDsn();
            maybeFetchAsteroid();
            ui.postDelayed(this, POS_INTERVAL_MS);
        }
    };

    private void maybeFetchCrew() {
        if (System.currentTimeMillis() - lastCrewFetch < CREW_INTERVAL_MS) return;
        lastCrewFetch = System.currentTimeMillis();
        net.execute(() -> {
            try {
                List<IssApi.CrewMember> crew = IssApi.fetchCrew();
                ui.post(() -> {
                    crewView.setCrew(crew);
                    crewView.setStatus("Open Notify  •  " + clock.format(new Date()));
                });
            } catch (Exception e) {
                lastCrewFetch = 0;
                ui.post(() -> crewView.setStatus("Crew feed unavailable — retrying…"));
            }
        });
    }

    private void maybeFetchLaunch() {
        if (System.currentTimeMillis() - lastLaunchFetch < LAUNCH_INTERVAL_MS) return;
        lastLaunchFetch = System.currentTimeMillis();
        net.execute(() -> {
            try {
                IssApi.LaunchInfo li = IssApi.fetchNextLaunch();
                ui.post(() -> ticker.setLaunch(li));
            } catch (Exception e) { lastLaunchFetch = 0; }
        });
    }

    private void maybeFetchWeather() {
        if (System.currentTimeMillis() - lastWeatherFetch < WEATHER_INTERVAL_MS) return;
        lastWeatherFetch = System.currentTimeMillis();
        net.execute(() -> {
            try {
                IssApi.SpaceWeather sw = IssApi.fetchSpaceWeather();
                ui.post(() -> weatherView.setWeather(sw, clock.format(new Date())));
            } catch (Exception e) {
                lastWeatherFetch = 0;
                ui.post(() -> weatherView.setStatus("Space weather unavailable"));
            }
        });
    }

    private void maybeFetchDsn() {
        if (System.currentTimeMillis() - lastDsnFetch < DSN_INTERVAL_MS) return;
        lastDsnFetch = System.currentTimeMillis();
        net.execute(() -> {
            try {
                String dsn = IssApi.fetchDsn();
                ui.post(() -> ticker.setDsn(dsn));
            } catch (Exception e) { lastDsnFetch = 0; }
        });
    }

    private void maybeFetchAsteroid() {
        if (System.currentTimeMillis() - lastAsteroidFetch < ASTEROID_INTERVAL_MS) return;
        lastAsteroidFetch = System.currentTimeMillis();
        net.execute(() -> {
            try {
                String a = IssApi.fetchAsteroid();
                ui.post(() -> ticker.setAsteroid(a));
            } catch (Exception e) { lastAsteroidFetch = 0; }
        });
    }
}
