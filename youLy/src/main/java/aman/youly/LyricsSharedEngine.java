package aman.youly;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LyricsSharedEngine {
    @SuppressLint("StaticFieldLeak")
    private static LyricsSharedEngine instance;
    private WebView webView;
    private Context context;
    private LyricsWebViewFragment.LyricsListener activeListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient okHttpClient;

    // Buffering Logic
    private boolean isJsReady = false;
    private PendingSong pendingSong = null;

    private static class PendingSong {
        String title, artist, album;
        long duration;
        PendingSong(String t, String a, String al, long d) {
            title = t; artist = a; album = al; duration = d;
        }
    }

    public static LyricsSharedEngine getInstance(Context context) {
        if (instance == null) {
            instance = new LyricsSharedEngine(context.getApplicationContext());
        }
        // Assuming Log.init is a custom logger you have
        // Log.init(context); 
        return instance;
    }

    private LyricsSharedEngine(Context context) {
        this.context = context;
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        initWebView();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        // Add Bridge
        webView.addJavascriptInterface(new SharedBridge(), "AndroidBridge");

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                // Use standard Android Log if custom Log class is missing
                android.util.Log.d("YouLyJS", cm.message() + " -- (" + cm.lineNumber() + ")");
                return true;
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/lyrics_engine/index.html");
    }

    public WebView getWebView() { return webView; }

    public void setListener(LyricsWebViewFragment.LyricsListener listener) {
        this.activeListener = listener;
    }

    // --- NEW: Centralized Load Method with Buffering ---
    public void loadLyrics(String title, String artist, String album, long durationSeconds) {
        if (isJsReady) {
            String safeTitle = escape(title);
            String safeArtist = escape(artist);
            String safeAlbum = escape(album);
            String js = String.format(
                    "if(window.AndroidAPI) window.AndroidAPI.loadSong('%s', '%s', '%s', %d);",
                    safeTitle, safeArtist, safeAlbum, durationSeconds);
            runJs(js);
        } else {
            android.util.Log.d("YouLyEngine", "JS not ready. Buffering song: " + title);
            pendingSong = new PendingSong(title, artist, album, durationSeconds);
        }
    }

    private void runJs(String js) {
        mainHandler.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "\\'");
    }

    // --- SHARED BRIDGE ---
    private class SharedBridge {
        
        // 1. ENGINE READY SIGNAL
        @JavascriptInterface
        public void onEngineReady() {
            android.util.Log.d("YouLyNative", "Engine Ready Signal Received!");
            isJsReady = true;
            
            // Flush Buffer
            if (pendingSong != null) {
                mainHandler.post(() -> {
                    android.util.Log.d("YouLyNative", "Flushing buffered song: " + pendingSong.title);
                    loadLyrics(pendingSong.title, pendingSong.artist, pendingSong.album, pendingSong.duration);
                    pendingSong = null;
                });
            }
        }

        @JavascriptInterface
        public void seekTo(String timeMsStr) {
            try {
                long timeMs = Long.parseLong(timeMsStr);
                mainHandler.post(() -> {
                    if (activeListener != null) {
                        activeListener.onSeekRequest(timeMs);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("YouLyNative", "Failed to parse seek time: " + timeMsStr);
            }
        }

        @JavascriptInterface
        public void performNetworkRequest(String urlStr, String method, String body, String reqId) {
            new Thread(() -> {
                try {
                    Request.Builder builder = new Request.Builder()
                            .url(urlStr)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

                    if ("POST".equalsIgnoreCase(method) && body != null) {
                        builder.post(RequestBody.create(body, MediaType.parse("application/json; charset=utf-8")));
                    } else {
                        builder.get();
                    }

                    try (Response response = okHttpClient.newCall(builder.build()).execute()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        String safeResponse = escapeForJs(responseBody);
                        String callback = String.format("window.handleNativeResponse('%s', true, %d, '%s')", reqId, response.code(), safeResponse);
                        runJs(callback);
                    }
                } catch (Exception e) {
                    String callback = String.format("window.handleNativeResponse('%s', false, 0, '%s')", reqId, escapeForJs(e.getMessage()));
                    runJs(callback);
                }
            }).start();
        }

        private String escapeForJs(String data) {
            if (data == null) return "";
            return data.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        }
    }
}
