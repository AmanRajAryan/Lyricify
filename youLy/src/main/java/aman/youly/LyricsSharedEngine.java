package aman.youly;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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

    public static LyricsSharedEngine getInstance(Context context) {
        if (instance == null) {
            instance = new LyricsSharedEngine(context.getApplicationContext());
        }
        
        Log.init(context);
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
                Log.d("YouLyJS", cm.message() + " -- (" + cm.lineNumber() + ")");
                return true;
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/lyrics_engine/index.html");
    }

    public WebView getWebView() { return webView; }

    public void setListener(LyricsWebViewFragment.LyricsListener listener) {
        this.activeListener = listener;
    }

    private void runJs(String js) {
        mainHandler.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    // --- SHARED BRIDGE ---
    private class SharedBridge {
        
        // CHANGED: Use String and parse it to be safe against JS number types
        @JavascriptInterface
        public void seekTo(String timeMsStr) {
            try {
                long timeMs = Long.parseLong(timeMsStr);
                Log.d("YouLyNative", "Seek request received: " + timeMs);
                
                mainHandler.post(() -> {
                    if (activeListener != null) {
                        activeListener.onSeekRequest(timeMs);
                    } else {
                        Log.w("YouLyNative", "No active listener for seek!");
                    }
                });
            } catch (Exception e) {
                Log.e("YouLyNative", "Failed to parse seek time: " + timeMsStr);
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
