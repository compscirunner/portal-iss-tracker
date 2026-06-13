package com.portal.isstracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live ISS feed from Sen (sen.com), which runs real 4K cameras on the ISS
 * (SpaceTV-1) and publishes the stream on YouTube with embedding allowed —
 * unlike NASA, which blocks embeds (YouTube error 150).
 *
 * Sen's live video id changes whenever they restart the broadcast, so we
 * resolve the current one at startup by scraping youtube.com/@Sen/live, falling
 * back to a known id if that fails.
 *
 * Embedded via the Portal's WebView, which needs three device-specific tricks:
 *   - a desktop Chrome UA, or YouTube's mobile player throws a non-recoverable
 *     error in this WebView fork;
 *   - the embed served from a tiny localhost HTTP server, so the iframe has a
 *     real http://127.0.0.1 origin + Referer (a data:-URL iframe has a null
 *     origin → error 152; a top-level load sends no referer → error 150).
 */
@SuppressLint("SetJavaScriptEnabled")
class FeedView extends WebView {

    private static final String TAG = "ISSFEED";
    private static final String LIVE_PAGE = "https://www.youtube.com/@Sen/live";
    private static final String FALLBACK_ID = "fO9e9jnhYK8";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ServerSocket server;

    FeedView(Context ctx) {
        super(ctx);
        setBackgroundColor(0xFF000000);
        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(DESKTOP_UA);

        setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "console: " + m.message());
                return true;
            }
        });
        setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                Log.d(TAG, "error " + err.getErrorCode() + " " + err.getDescription() + " @ " + req.getUrl());
            }
        });

        // Resolve the live id + start the local server off the main thread.
        new Thread(() -> {
            String id = resolveLiveId();
            int port = startServer(id);
            ui.post(() -> {
                if (port > 0) loadUrl("http://127.0.0.1:" + port + "/");
                else loadData("<body style='background:#000'></body>", "text/html", "utf-8");
            });
        }, "iss-feed-init").start();
    }

    /** Scrape Sen's current live video id; fall back to a known id. */
    private String resolveLiveId() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(LIVE_PAGE).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", DESKTOP_UA);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                Pattern p = Pattern.compile("\"videoId\":\"([0-9A-Za-z_-]{11})\"");
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        Log.d(TAG, "resolved live id: " + m.group(1));
                        return m.group(1);
                    }
                    if (sb.length() > 1_500_000) break;   // safety cap
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "live-id scrape failed: " + e + " — using fallback");
        } finally {
            if (c != null) c.disconnect();
        }
        return FALLBACK_ID;
    }

    /** Serve one static page (the YouTube iframe) over localhost so the embed
     *  gets a valid origin + Referer. Returns the bound port, or -1 on failure. */
    private int startServer(String videoId) {
        try {
            server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            final int port = server.getLocalPort();
            final byte[] body = page(videoId, port).getBytes(StandardCharsets.UTF_8);
            Thread t = new Thread(() -> {
                while (server != null && !server.isClosed()) {
                    try (Socket c = server.accept()) {
                        InputStream in = c.getInputStream();
                        in.read(new byte[2048]);   // drain the request
                        OutputStream out = c.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8));
                        out.write(body);
                        out.flush();
                    } catch (Exception e) {
                        if (server == null || server.isClosed()) break;
                    }
                }
            }, "iss-feed-httpd");
            t.setDaemon(true);
            t.start();
            return port;
        } catch (Exception e) {
            Log.d(TAG, "local server failed: " + e);
            return -1;
        }
    }

    private static String page(String videoId, int port) {
        String origin = "http://127.0.0.1:" + port;
        return "<!DOCTYPE html><html><head>"
             + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
             + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
             + "iframe{border:0;width:100%;height:100%}</style></head><body>"
             + "<iframe src='https://www.youtube.com/embed/" + videoId
             + "?autoplay=1&mute=1&playsinline=1&rel=0&modestbranding=1&origin=" + origin + "' "
             + "allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe>"
             + "</body></html>";
    }

    void resume()  { onResume(); }
    void pause()   { onPause(); }
    void release() {
        destroy();
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        server = null;
    }
}
