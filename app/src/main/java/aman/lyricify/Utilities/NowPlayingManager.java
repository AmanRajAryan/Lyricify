package aman.lyricify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.lang.ref.WeakReference;

import aman.lyricify.glide.AudioFileCover;

/**
 * Manages the "Now Playing" card display and updates
 */
public class NowPlayingManager {
    
    private final WeakReference<Context> contextRef;
    private final Handler uiHandler;
    
    // UI Views
    private final CardView nowPlayingCard;
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
    
    public NowPlayingManager(Context context, CardView card, ImageView artwork, 
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
    
    public void register() {
        Context context = contextRef.get();
        if (context != null) {
            IntentFilter filter = new IntentFilter("aman.lyricify.SONG_NOTIFICATION");
            context.registerReceiver(notifArtworkReceiver, filter);
        }
    }
    
    public void unregister() {
        Context context = contextRef.get();
        if (context != null) {
            try {
                context.unregisterReceiver(notifArtworkReceiver);
            } catch (Exception ignored) {}
        }
    }
    
    public void prepareUpdate(String title, String artist, Bitmap artwork) {
        cancelPendingUpdate();
        
        pendingTitle = title;
        pendingArtist = artist;
        pendingArtwork = artwork;
        
        requestExistingNotificationCheck();
        
        pendingUpdateRunnable = this::commitPendingUpdate;
        uiHandler.postDelayed(pendingUpdateRunnable, UPDATE_DELAY_MS);
    }
    
    public void cancelPendingUpdate() {
        if (pendingUpdateRunnable != null) {
            uiHandler.removeCallbacks(pendingUpdateRunnable);
            pendingUpdateRunnable = null;
        }
        pendingTitle = null;
        pendingArtist = null;
        pendingArtwork = null;
    }
    
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
            
            // Initial load (Static Bitmap) - will be replaced if local file is found
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
        
        // Search for local file to enable animation
        Context context = contextRef.get();
        if (context != null) {
            searchForLocalFile(context, finalTitle, finalArtist);
        }
        
        pendingTitle = null;
        pendingArtist = null;
        pendingArtwork = null;
        pendingUpdateRunnable = null;
    }
    
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
                        
                        // NEW: Load animated artwork immediately
                        loadAnimatedArtwork(song.filePath);
                        
                        // Stop looking for notification artwork since we found the file
                        stopArtworkMonitoring();
                        isWaitingForArtwork = false;
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
     * NEW: Load artwork using Glide and AudioFileCover to support GIF/WebP animation
     */
    private void loadAnimatedArtwork(String filePath) {
        Context context = contextRef.get();
        if (context == null || filePath == null) return;
        
        long lastModified = 0;
        try {
            lastModified = new File(filePath).lastModified();
        } catch (Exception ignored) {}
        
        // Create model with timestamp signature
        AudioFileCover coverModel = new AudioFileCover(filePath, lastModified);
        
        Glide.with(context)
             .load(coverModel)
             .diskCacheStrategy(DiskCacheStrategy.DATA) // Cache raw bytes
             .placeholder(R.drawable.ic_music_note)
             .error(R.drawable.ic_music_note)
             .optionalCenterCrop() // Crop static, leave animated alone
             .listener(new RequestListener<Drawable>() {
                 @Override
                 public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                     return false;
                 }

                 @Override
                 public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                     if (resource instanceof Animatable) {
                         ((Animatable) resource).start();
                     }
                     return false;
                 }
             })
             .into(nowPlayingArtwork);
    }
    
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
            // FIX: If we found a local file (currentFilePath != null), it means we are likely
            // displaying a high-quality GIF/WebP. Do NOT overwrite it with a static bitmap
            // from the notification.
            if (currentFilePath != null) {
                 // We can update the backing field for fallback, but don't touch UI
                 if (MediaSessionHandler.isValidBitmap(artwork)) {
                     currentArtwork = artwork;
                 }
                 return;
            }

            // Normal behavior: Update static bitmap
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
    
    private void stopArtworkMonitoring() {
        if (artworkMonitoringRunnable != null) {
            uiHandler.removeCallbacks(artworkMonitoringRunnable);
            artworkMonitoringRunnable = null;
        }
    }
    
    private void requestExistingNotificationCheck() {
        Context context = contextRef.get();
        if (context != null) {
            context.sendBroadcast(new Intent("aman.lyricify.REQUEST_EXISTING_CHECK"));
        }
    }
    
    private String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }
    
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentArtist() { return currentArtist; }
    public String getCurrentFilePath() { return currentFilePath; }
    public Uri getCurrentFileUri() { return currentFileUri; }
    public boolean hasActiveMedia() { return hasActiveMedia; }
    public Bitmap getCurrentArtwork() { return currentArtwork; }
}