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

    // --- Listener Interface for Click-to-Seek ---
    public interface LyricsListener {
        void onSeekRequest(long timeMs);
    }

    private WebView webView;
    private LyricsListener lyricsListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Set the listener to handle seek events from the WebView.
     */
    public void setLyricsListener(LyricsListener listener) {
        this.lyricsListener = listener;
        // If the engine is already running, point it to this new listener immediately
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(getContext()).setListener(listener);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 1. Get the Shared "Warm" Engine (Singleton)
        LyricsSharedEngine engine = LyricsSharedEngine.getInstance(requireContext());
        engine.setListener(lyricsListener); // Ensure clicks are sent to THIS fragment's listener
        
        webView = engine.getWebView();

        // 2. Detach from previous parent (Crucial step!)
        // Since we reuse the view, it might still be attached to an old closed fragment layout.
        if (webView != null) {
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }
        }

        // 3. Create Container
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // root.setBackgroundColor(Color.BLACK); // Optional

        // 4. Add the WebView
        if (webView != null) {
            root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 5. Cleanup: Detach WebView so it doesn't leak memory or crash on rotation
        if (webView != null) {
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }
        }
    }

    // =========================================================================
    //  NEW SPLIT API (Use these for better performance)
    // =========================================================================

    /**
     * PHASE 1: SEARCH
     * Call this in the BACKGROUND immediately when the song changes.
     * It fetches and parses lyrics but DOES NOT render them to the screen yet.
     */
    public void searchLyrics(String title, String artist, String album, long durationSeconds) {
        String safeTitle = escape(title);
        String safeArtist = escape(artist);
        String safeAlbum = escape(album);

        // Calls 'searchSong' in JS (Sets background mode)
        String js = String.format(
                "if(window.AndroidAPI) window.AndroidAPI.searchSong('%s', '%s', '%s', %d);",
                safeTitle, safeArtist, safeAlbum, durationSeconds);
        runJs(js);
    }

    /**
     * PHASE 2: DISPLAY
     * Call this when the user actually opens the Lyrics UI.
     * It renders the cached data instantly.
     */
    public void displayLyrics() {
        // Calls 'showSong' in JS (Disables background mode & Renders)
        runJs("if(window.AndroidAPI) window.AndroidAPI.showSong();");
    }

    // =========================================================================
    //  STANDARD API
    // =========================================================================

    public void updateTime(long positionMs) {
        runJs("if(window.AndroidAPI) window.AndroidAPI.updateTime(" + positionMs + ")");
    }

    /**
     * LEGACY LOAD
     * Use this if you want to load & render immediately in one step (Old behavior).
     */
    public void loadLyrics(String title, String artist, String album, long durationSeconds) {
        String safeTitle = escape(title);
        String safeArtist = escape(artist);
        String safeAlbum = escape(album);
        
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