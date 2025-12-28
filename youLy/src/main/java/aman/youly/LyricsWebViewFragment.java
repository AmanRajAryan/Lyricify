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
    private FrameLayout rootContainer; // Keep reference to container
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
        // Create a dedicated container for this fragment
        rootContainer = new FrameLayout(requireContext());
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Initial attachment attempt
        attachWebView();

        return rootContainer;
    }

    /**
     * Called when the Fragment is visible again (e.g. returning from Floating Bubble).
     * We MUST check if the WebView was stolen by the Service and steal it back.
     */
    @Override
    public void onResume() {
        super.onResume();
        attachWebView();
    }

    /**
     * Logic to grab the singleton WebView and add it to this Fragment.
     * Handles detaching it from any other parent (like FloatingLyricsService).
     */
    private void attachWebView() {
        if (getContext() == null || rootContainer == null) return;

        // Get singleton engine
        LyricsSharedEngine engine = LyricsSharedEngine.getInstance(requireContext());
        engine.setListener(lyricsListener);
        webView = engine.getWebView();

        if (webView != null) {
            ViewParent parent = webView.getParent();
            
            // Optimization: If it is already attached here, do nothing
            if (parent == rootContainer) {
                // Ensure it's visible just in case
                webView.setVisibility(View.VISIBLE);
                webView.setAlpha(1.0f);
                return;
            }

            // If it is attached somewhere else (e.g. Floating Window), remove it
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }

            // Add to this fragment's container
            rootContainer.removeAllViews(); // Clear any stale views
            rootContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Reset visibility properties that might have been changed by the Service
            webView.setVisibility(View.VISIBLE);
            webView.setAlpha(1.0f);
            webView.setTranslationX(0);
            webView.setTranslationY(0);
            webView.setScaleX(1.0f);
            webView.setScaleY(1.0f);
            
            // Vital for waking up the renderer
            webView.onResume();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // We detach the WebView so it isn't "leaked" by holding onto a dead Fragment View
        if (webView != null && rootContainer != null) {
            rootContainer.removeView(webView);
        }
    }

    // =========================================================================
    //  API CALLS
    // =========================================================================

    // 1. SEARCH (Background)
    public void searchLyrics(String title, String artist, String album, long durationSeconds) {
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
    // Call this with 'false' when player pauses, 'true' when playing
    public void setPlaying(boolean isPlaying) {
        runJs("if(window.AndroidAPI && window.AndroidAPI.setPlaying) window.AndroidAPI.setPlaying(" + isPlaying + ")");
    }
    
    // 5. LEGACY/DIRECT LOAD (Immediate Render)
    // Use this for Floating Window or when you want lyrics to appear ASAP without "Split" logic.
    public void loadLyrics(String title, String artist, String album, long durationSeconds) {
        String safeTitle = escape(title);
        String safeArtist = escape(artist);
        String safeAlbum = escape(album);
        
        // Calls 'loadSong' in JS (Sets isSearchOnlyMode = false -> Renders immediately)
        String js = String.format(
                "if(window.AndroidAPI) window.AndroidAPI.loadSong('%s', '%s', '%s', %d);",
                safeTitle, safeArtist, safeAlbum, durationSeconds);
        runJs(js);
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
