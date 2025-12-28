package aman.youly;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LyricsWebViewFragment extends Fragment {

    public interface LyricsListener {
        void onSeekRequest(long timeMs);
    }

    private WebView webView;
    private LyricsListener lyricsListener;
    private FrameLayout rootContainer; 
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setLyricsListener(LyricsListener listener) {
        this.lyricsListener = listener;
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(getContext()).setListener(listener);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootContainer = new FrameLayout(requireContext());
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        attachWebView();
        return rootContainer;
    }

    @Override
    public void onResume() {
        super.onResume();
        attachWebView();
    }

    private void attachWebView() {
        if (getContext() == null || rootContainer == null) return;

        LyricsSharedEngine engine = LyricsSharedEngine.getInstance(requireContext());
        engine.setListener(lyricsListener);
        webView = engine.getWebView();

        if (webView != null) {
            ViewParent parent = webView.getParent();
            
            if (parent == rootContainer) {
                webView.setVisibility(View.VISIBLE);
                webView.setAlpha(1.0f);
                return;
            }

            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }

            rootContainer.removeAllViews(); 
            rootContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            webView.setVisibility(View.VISIBLE);
            webView.setAlpha(1.0f);
            webView.setTranslationX(0);
            webView.setTranslationY(0);
            webView.setScaleX(1.0f);
            webView.setScaleY(1.0f);
            webView.onResume();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null && rootContainer != null) {
            rootContainer.removeView(webView);
        }
    }

    // =========================================================================
    //  API CALLS
    // =========================================================================

    // 1. SEARCH (Background)
    public void searchLyrics(String title, String artist, String album, long durationSeconds) {
        // Search usually doesn't need immediate rendering, but we could buffer it too if needed.
        // For now, simple runJs is fine as search implies interaction which implies load is done.
        String safeTitle = escape(title);
        String safeArtist = escape(artist);
        String safeAlbum = escape(album);
        String js = String.format(
                "if(window.AndroidAPI) window.AndroidAPI.searchSong('%s', '%s', '%s', %d);",
                safeTitle, safeArtist, safeAlbum, durationSeconds);
        runJs(js);
    }

    // 2. SHOW (Foreground)
    public void displayLyrics() {
        runJs("if(window.AndroidAPI) window.AndroidAPI.showSong();");
    }

    // 3. UPDATE TIME
    public void updateTime(long positionMs) {
        runJs("if(window.AndroidAPI) window.AndroidAPI.updateTime(" + positionMs + ")");
    }

    // 4. SET PLAYING
    public void setPlaying(boolean isPlaying) {
        runJs("if(window.AndroidAPI && window.AndroidAPI.setPlaying) window.AndroidAPI.setPlaying(" + isPlaying + ")");
    }
    
    // 5. LOAD (The Critical Fix)
    public void loadLyrics(String title, String artist, String album, long durationSeconds) {
        if (getContext() != null) {
            // Delegate to Engine to handle Buffering/Race Conditions
            LyricsSharedEngine.getInstance(requireContext()).loadLyrics(title, artist, album, durationSeconds);
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
}
