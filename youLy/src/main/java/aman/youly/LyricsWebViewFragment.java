package aman.youly;

import android.os.Bundle;
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
    //  API CALLS (Delegated to Engine for Buffering/Safety)
    // =========================================================================

    public void searchLyrics(String title, String artist, String album, long durationSeconds) {
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(requireContext()).searchLyrics(title, artist, album, durationSeconds);
        }
    }

    public void displayLyrics() {
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(requireContext()).displayLyrics();
        }
    }

    public void updateTime(long positionMs) {
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(requireContext()).updateTime(positionMs);
        }
    }

    public void setPlaying(boolean isPlaying) {
        if (getContext() != null) {
            // This now hits the buffered method in Engine
            LyricsSharedEngine.getInstance(requireContext()).setPlaying(isPlaying);
        }
    }
    
    public void loadLyrics(String title, String artist, String album, long durationSeconds) {
        if (getContext() != null) {
            LyricsSharedEngine.getInstance(requireContext()).loadLyrics(title, artist, album, durationSeconds);
        }
    }
}
