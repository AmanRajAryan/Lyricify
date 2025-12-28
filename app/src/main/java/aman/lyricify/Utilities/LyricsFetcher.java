package aman.lyricify;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/** Handles fetching and displaying lyrics */
public class LyricsFetcher {

    private final WeakReference<TextView> lyricsTextViewRef;
    private final WeakReference<TextView> lyricsTextViewPlainRef; // Add this
    private final WeakReference<ProgressBar> loadingRef;
    private LyricsCallback callback;
    private ApiClient.LyricsResponse currentLyricsResponse;

    public interface LyricsCallback {
        void onLyricsLoaded(ApiClient.LyricsResponse lyricsResponse);

        void onLyricsError(String error);
    }

    public LyricsFetcher(
            TextView lyricsTextView, TextView lyricsTextViewPlain, ProgressBar loading) {
        this.lyricsTextViewRef = new WeakReference<>(lyricsTextView);
        this.lyricsTextViewPlainRef = new WeakReference<>(lyricsTextViewPlain); // Add this
        this.loadingRef = new WeakReference<>(loading);
    }

    public void setCallback(LyricsCallback callback) {
        this.callback = callback;
    }

    /** Fetch lyrics by song ID */
    public void fetchBySongId(String songId) {
        showLoading();

        ApiClient.getLyrics(
                songId,
                new ApiClient.LyricsCallback() {
                    @Override
                    public void onSuccess(ApiClient.LyricsResponse lyrics) {
                        currentLyricsResponse = lyrics;
                        displayLyrics(lyrics.plain);
                        if (callback != null) {
                            callback.onLyricsLoaded(lyrics);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        displayError(error);
                        if (callback != null) {
                            callback.onLyricsError(error);
                        }
                    }
                });
    }

    /** Fetch lyrics by title and artist */
    public void fetchByTitleAndArtist(String title, String artist) {
        showLoading();

        ApiClient.getLyricsByTitleAndArtist(
                title,
                artist,
                new ApiClient.LyricsCallback() {
                    @Override
                    public void onSuccess(ApiClient.LyricsResponse lyrics) {
                        currentLyricsResponse = lyrics;
                        displayLyrics(lyrics.plain);
                        if (callback != null) {
                            callback.onLyricsLoaded(lyrics);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        displayError(error);
                        if (callback != null) {
                            callback.onLyricsError(error);
                        }
                    }
                });
    }

    /** Show loading state */
    private void showLoading() {
        TextView lyricsTextView = lyricsTextViewRef.get();
        TextView lyricsTextViewPlain = lyricsTextViewPlainRef.get();
        ProgressBar loading = loadingRef.get();

        if (loading != null) {
            loading.setVisibility(View.VISIBLE);
        }
        if (lyricsTextView != null) {
            lyricsTextView.setText("Loading lyrics...");
        }
        if (lyricsTextViewPlain != null) {
            lyricsTextViewPlain.setText("Loading lyrics...");
        }
    }

    /** Display fetched lyrics - updates both TextViews */
    private void displayLyrics(String lyrics) {
        TextView lyricsTextView = lyricsTextViewRef.get();
        TextView lyricsTextViewPlain = lyricsTextViewPlainRef.get();
        ProgressBar loading = loadingRef.get();

        if (lyricsTextView != null) {
            lyricsTextView.post(() -> lyricsTextView.setText(lyrics));
        }
        if (lyricsTextViewPlain != null) {
            lyricsTextViewPlain.post(() -> lyricsTextViewPlain.setText(lyrics));
        }
        if (loading != null) {
            loading.post(() -> loading.setVisibility(View.GONE));
        }
    }

    /** Display error message */
    private void displayError(String error) {
        TextView lyricsTextView = lyricsTextViewRef.get();
        TextView lyricsTextViewPlain = lyricsTextViewPlainRef.get();
        ProgressBar loading = loadingRef.get();

        if (lyricsTextView != null) {
            lyricsTextView.post(() -> lyricsTextView.setText(error));
        }
        if (lyricsTextViewPlain != null) {
            lyricsTextViewPlain.post(() -> lyricsTextViewPlain.setText(error));
        }
        if (loading != null) {
            loading.post(() -> loading.setVisibility(View.GONE));
        }
    }

    /** Get current lyrics text based on which TextView is visible */
    public String getCurrentLyrics() {
        TextView lyricsTextViewPlain = lyricsTextViewPlainRef.get();
        TextView lyricsTextView = lyricsTextViewRef.get();

        // Check plain view first (if visible)
        if (lyricsTextViewPlain != null && lyricsTextViewPlain.getVisibility() == View.VISIBLE) {
            return lyricsTextViewPlain.getText().toString();
        }

        // Fall back to formatted view
        if (lyricsTextView != null) {
            return lyricsTextView.getText().toString();
        }

        return null;
    }

    /** Get current lyrics response with all formats */
    public ApiClient.LyricsResponse getCurrentLyricsResponse() {
        return currentLyricsResponse;
    }

    /** Check if lyrics are valid for embedding */
    public boolean hasValidLyrics() {
        String lyrics = getCurrentLyrics();
        if (lyrics == null || lyrics.isEmpty()) {
            return false;
        }

        // Check for invalid states
        return !lyrics.equals("Loading lyrics...")
                && !lyrics.startsWith("No song")
                && !lyrics.startsWith("Failed")
                && !lyrics.startsWith("Error:");
    }

    /** Update displayed lyrics format */
    public void displayFormat(String format) {
        if (currentLyricsResponse == null) {
            return;
        }

        String lyricsToDisplay = "";
        switch (format) {
            case "Plain":
                lyricsToDisplay = currentLyricsResponse.plain;
                break;
            case "LRC":
                lyricsToDisplay = currentLyricsResponse.lrc;
                break;
            case "ELRC":
                lyricsToDisplay = currentLyricsResponse.elrc;
                break;
            case "ELRC Multi-Person":
                lyricsToDisplay = currentLyricsResponse.elrcMultiPerson;
                break;
            case "TTML":
                lyricsToDisplay = currentLyricsResponse.ttml;
                break;
        }

        String finalLyrics =
                lyricsToDisplay != null && !lyricsToDisplay.isEmpty()
                        ? lyricsToDisplay
                        : "Format not available";

        displayLyrics(finalLyrics);
    }
}
