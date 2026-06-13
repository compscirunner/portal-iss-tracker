package com.portal.isstracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Live ISS feed from Sen (sen.com), which runs real 4K cameras on the ISS
 * (SpaceTV-1) and publishes the stream on YouTube with embedding allowed —
 * unlike NASA, which blocks embeds (YouTube error 150).
 *
 * Embedded via the Portal's WebView, which needs three device-specific tricks:
 *   - a desktop Chrome UA, or YouTube's mobile player throws a non-recoverable
 *     error in this WebView fork;
 *   - the embed served from a tiny localhost HTTP server, so the iframe has a
 *     real http://127.0.0.1 origin + Referer (a data:-URL iframe has a null
 *     origin → error 152; a top-level load sends no referer → error 150);
 *   - the channel "live_stream" endpoint, so it follows Sen's current broadcast.
 */
@SuppressLint("SetJavaScriptEnabled")
class FeedView extends WebView {

    private static final String TAG = "ISSFEED";
    // Sen's live ISS 4K broadcast. The channel "live_stream" endpoint doesn't
    // resolve inside an embed here, so target the live video id directly.
    private static final String VIDEO_ID = "fO9e9jnhYK8";

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
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

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

        int port = startServer();
        if (port > 0) loadUrl("http://127.0.0.1:" + port + "/");
        else loadData("<body style='background:#000'></body>", "text/html", "utf-8");
    }

    /** Serve one static page (the YouTube iframe) over localhost so the embed
     *  gets a valid origin + Referer. Returns the bound port, or -1 on failure. */
    private int startServer() {
        try {
            server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            final int port = server.getLocalPort();
            final byte[] body = page(port).getBytes(StandardCharsets.UTF_8);
            Thread t = new Thread(() -> {
                while (server != null && !server.isClosed()) {
                    try (Socket c = server.accept()) {
                        InputStream in = c.getInputStream();
                        byte[] scratch = new byte[2048];
                        in.read(scratch);   // drain the request line/headers
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

    private static String page(int port) {
        String origin = "http://127.0.0.1:" + port;
        return "<!DOCTYPE html><html><head>"
             + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
             + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
             + "iframe{border:0;width:100%;height:100%}</style></head><body>"
             + "<iframe src='https://www.youtube.com/embed/" + VIDEO_ID
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
