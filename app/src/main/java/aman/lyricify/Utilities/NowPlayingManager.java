package aman.lyricify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Manages the "Now Playing" card display and updates
 */
public class NowPlayingManager {
    
    private final WeakReference<Context> contextRef;
    private final Handler uiHandler;
    
    // UI Views
    private final LinearLayout nowPlayingCard;
    private final ImageView nowPlayingArtwork;
    private final TextView nowPlayingTitle;
    private final TextView nowPlayingArtist;
    private final TextView nowPlayingFilePath;
    
    // State
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentFilePath = null;
    private Uri currentFileUri = null;
    private Bitmap currentArtwork = null;
    private boolean hasActiveMedia = false;
    private boolean isWaitingForArtwork = false;
    
    // Pending update mechanism
    private String pendingTitle = null;
    private String pendingArtist = null;
    private Bitmap pendingArtwork = null;
    private Runnable pendingUpdateRunnable = null;
    private static final int UPDATE_DELAY_MS = 500;
    
    // Artwork monitoring
    private Runnable artworkMonitoringRunnable;
    private int artworkCheckAttempts = 0;
    private static final int MAX_ARTWORK_ATTEMPTS = 15;
    
    private NowPlayingCallback callback;
    
    public interface NowPlayingCallback {
        void onCardClicked(String title, String artist);
        void onFileFound(String filePath, Uri fileUri);
    }
    
    // Notification artwork receiver
    private final BroadcastReceiver notifArtworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("aman.lyricify.SONG_NOTIFICATION".equals(intent.getAction())) {
                String title = intent.getStringExtra("songTitle");
                String artist = intent.getStringExtra("artist");
                Bitmap artwork = intent.getParcelableExtra("artwork");
                String source = intent.getStringExtra("source");
                
                if ((hasActiveMedia || pendingTitle != null) && 
                    (source.equals("onNotificationPosted") || isWaitingForArtwork)) {
                    updateFromNotification(title, artist, artwork);
                }
            }
        }
    };
    
    public NowPlayingManager(Context context, LinearLayout card, ImageView artwork, 
                            TextView title, TextView artist, TextView filePath) {
        this.contextRef = new WeakReference<>(context);
        this.uiHandler = new Handler(Looper.getMainLooper());
        this.nowPlayingCard = card;
        this.nowPlayingArtwork = artwork;
        this.nowPlayingTitle = title;
        this.nowPlayingArtist = artist;
        this.nowPlayingFilePath = filePath;
        
        // Set up text marquee
        nowPlayingArtist.setSelected(true);
        nowPlayingFilePath.setSelected(true);
        nowPlayingTitle.setSelected(true);
    }
    
    public void setCallback(NowPlayingCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Register broadcast receiver
     */
    public void register() {
        Context context = contextRef.get();
        if (context != null) {
            IntentFilter filter = new IntentFilter("aman.lyricify.SONG_NOTIFICATION");
            context.registerReceiver(notifArtworkReceiver, filter);
        }
    }
    
    /**
     * Unregister broadcast receiver
     */
    public void unregister() {
        Context context = contextRef.get();
        if (context != null) {
            try {
                context.unregisterReceiver(notifArtworkReceiver);
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Prepare a new song update with delay
     */
    public void prepareUpdate(String title, String artist, Bitmap artwork) {
        cancelPendingUpdate();
        
        pendingTitle = title;
        pendingArtist = artist;
        pendingArtwork = artwork;
        
        requestExistingNotificationCheck();
        
        pendingUpdateRunnable = this::commitPendingUpdate;
        uiHandler.postDelayed(pendingUpdateRunnable, UPDATE_DELAY_MS);
    }
    
    /**
     * Cancel any pending update
     */
    public void cancelPendingUpdate() {
        if (pendingUpdateRunnable != null) {
            uiHandler.removeCallbacks(pendingUpdateRunnable);
            pendingUpdateRunnable = null;
        }
        pendingTitle = null;
        pendingArtist = null;
        pendingArtwork = null;
    }
    
    /**
     * Commit the pending update to UI
     */
    private void commitPendingUpdate() {
        if (pendingTitle == null) return;
        
        final String finalTitle = pendingTitle;
        final String finalArtist = pendingArtist;
        final Bitmap finalArtwork = pendingArtwork;
        
        currentTitle = finalTitle;
        currentArtist = finalArtist;
        hasActiveMedia = true;
        currentFilePath = null;
        currentFileUri = null;
        
        uiHandler.post(() -> {
            nowPlayingCard.setVisibility(View.VISIBLE);
            nowPlayingTitle.setText(finalTitle);
            nowPlayingArtist.setText(finalArtist);
            nowPlayingFilePath.setText("Searching file...");
            nowPlayingFilePath.setVisibility(View.VISIBLE);
            
            if (MediaSessionHandler.isValidBitmap(finalArtwork)) {
                nowPlayingArtwork.setImageBitmap(finalArtwork);
                currentArtwork = finalArtwork;
                isWaitingForArtwork = false;
                stopArtworkMonitoring();
            } else {
                nowPlayingArtwork.setImageResource(R.drawable.ic_music_note);
                currentArtwork = null;
                isWaitingForArtwork = true;
                startArtworkMonitoring();
            }
            
            nowPlayingCard.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onCardClicked(finalTitle, finalArtist);
                }
            });
        });
        
        // Search for local file
        Context context = contextRef.get();
        if (context != null) {
            searchForLocalFile(context, finalTitle, finalArtist);
        }
        
        pendingTitle = null;
        pendingArtist = null;
        pendingArtwork = null;
        pendingUpdateRunnable = null;
    }
    
    /**
     * Search for local file matching the song
     */
    private void searchForLocalFile(Context context, String title, String artist) {
        MediaStoreHelper.searchLocalSong(context, title, artist, 
            new MediaStoreHelper.SearchCallback() {
                @Override
                public void onFound(MediaStoreHelper.LocalSong song) {
                    currentFilePath = song.filePath;
                    currentFileUri = song.fileUri;
                    uiHandler.post(() -> {
                        String fileName = extractFileName(song.filePath);
                        nowPlayingFilePath.setText(fileName);
                        nowPlayingFilePath.setVisibility(View.VISIBLE);
                    });
                    
                    if (callback != null) {
                        callback.onFileFound(song.filePath, song.fileUri);
                    }
                }
                
                @Override
                public void onNotFound() {
                    currentFilePath = null;
                    currentFileUri = null;
                    uiHandler.post(() -> {
                        nowPlayingFilePath.setText("Local file not found");
                        nowPlayingFilePath.setVisibility(View.VISIBLE);
                    });
                }
                
                @Override
                public void onError(String error) {
                    currentFilePath = null;
                    currentFileUri = null;
                    uiHandler.post(() -> {
                        if (error.contains("permission")) {
                            nowPlayingFilePath.setText("Storage permission needed");
                        } else {
                            nowPlayingFilePath.setText("Error finding file");
                        }
                        nowPlayingFilePath.setVisibility(View.VISIBLE);
                    });
                }
            }
        );
    }
    
    /**
     * Update from notification artwork
     */
    private void updateFromNotification(String title, String artist, Bitmap artwork) {
        if (!hasActiveMedia && pendingTitle == null) return;
        if (title == null || title.trim().isEmpty()) return;
        if (artist == null) artist = "Unknown Artist";
        
        // Update pending if matches
        if (pendingTitle != null && pendingTitle.equals(title) && 
            pendingArtist != null && pendingArtist.equals(artist)) {
            if (artwork != null && pendingArtwork == null) {
                pendingArtwork = artwork;
            }
            return;
        }
        
        // Update current if matches
        if (title.equals(currentTitle) && artist.equals(currentArtist)) {
            if (MediaSessionHandler.isValidBitmap(artwork)) {
                final Bitmap finalArtwork = artwork;
                uiHandler.post(() -> {
                    nowPlayingArtwork.setImageBitmap(finalArtwork);
                    currentArtwork = finalArtwork;
                    isWaitingForArtwork = false;
                    stopArtworkMonitoring();
                });
            }
        }
    }
    
    /**
     * Hide the now playing card
     */
    public void hide() {
        cancelPendingUpdate();
        stopArtworkMonitoring();
        
        uiHandler.post(() -> {
            nowPlayingCard.setVisibility(View.GONE);
            isWaitingForArtwork = false;
            hasActiveMedia = false;
            currentTitle = "";
            currentArtist = "";
            currentArtwork = null;
            currentFilePath = null;
            currentFileUri = null;
            nowPlayingFilePath.setVisibility(View.GONE);
        });
    }
    
    /**
     * Start monitoring for artwork updates
     */
    private void startArtworkMonitoring() {
        stopArtworkMonitoring();
        artworkCheckAttempts = 0;
        
        artworkMonitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (isWaitingForArtwork && hasActiveMedia && 
                    artworkCheckAttempts < MAX_ARTWORK_ATTEMPTS) {
                    artworkCheckAttempts++;
                    requestExistingNotificationCheck();
                    uiHandler.postDelayed(this, 2000);
                } else if (artworkCheckAttempts >= MAX_ARTWORK_ATTEMPTS) {
                    isWaitingForArtwork = false;
                }
            }
        };
        
        uiHandler.postDelayed(artworkMonitoringRunnable, 500);
    }
    
    /**
     * Stop artwork monitoring
     */
    private void stopArtworkMonitoring() {
        if (artworkMonitoringRunnable != null) {
            uiHandler.removeCallbacks(artworkMonitoringRunnable);
            artworkMonitoringRunnable = null;
        }
    }
    
    /**
     * Request notification service to check existing notifications
     */
    private void requestExistingNotificationCheck() {
        Context context = contextRef.get();
        if (context != null) {
            context.sendBroadcast(new Intent("aman.lyricify.REQUEST_EXISTING_CHECK"));
        }
    }
    
    /**
     * Extract file name from path
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }
    
    // Getters
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentArtist() { return currentArtist; }
    public String getCurrentFilePath() { return currentFilePath; }
    public Uri getCurrentFileUri() { return currentFileUri; }
    public boolean hasActiveMedia() { return hasActiveMedia; }
    public Bitmap getCurrentArtwork() { return currentArtwork; }
}