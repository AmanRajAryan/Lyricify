package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.lang.ref.WeakReference;

/**
 * Handles displaying song information (artwork, title, artist, file path)
 */
public class SongInfoDisplay {
    
    private final WeakReference<Context> contextRef;
    private final ImageView artworkView;
    private final TextView titleView;
    private final TextView artistView;
    private final TextView filePathView;
    
    private Bitmap currentArtwork;
    private ArtworkLoadedCallback artworkCallback;
    
    public interface ArtworkLoadedCallback {
        void onArtworkLoaded(Bitmap bitmap);
    }
    
    public SongInfoDisplay(Context context, ImageView artwork, TextView title, 
                          TextView artist, TextView filePath) {
        this.contextRef = new WeakReference<>(context);
        this.artworkView = artwork;
        this.titleView = title;
        this.artistView = artist;
        this.filePathView = filePath;
    }
    
    public void setArtworkCallback(ArtworkLoadedCallback callback) {
        this.artworkCallback = callback;
    }
    
    /**
     * Display song information
     */
    public void displaySongInfo(String title, String artist, String artworkUrl, String filePath) {
        // Set title
        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        } else {
            titleView.setText("Unknown Song");
        }
        
        // Set artist
        if (artist != null && !artist.isEmpty()) {
            artistView.setText(artist);
        } else {
            artistView.setText("Unknown Artist");
        }
        
        // Set file path
        displayFilePath(filePath);
        
        // Load artwork
        loadArtwork(artworkUrl);
    }
    
    /**
     * Display file path with click listener
     */
    private void displayFilePath(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            String fileName = extractFileName(filePath);
            filePathView.setText(fileName);
            filePathView.setVisibility(View.VISIBLE);
        } else {
            filePathView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Load and display artwork
     */
    private void loadArtwork(String artworkUrl) {
        Context context = contextRef.get();
        if (context == null || artworkUrl == null || artworkUrl.isEmpty()) {
            artworkView.setImageResource(R.drawable.ic_music_note);
            return;
        }
        
        String formattedUrl = artworkUrl
            .replace("{w}", "1000")
            .replace("{h}", "1000")
            .replace("{f}", "jpg");
        
        Glide.with(context)
            .asBitmap()
            .load(formattedUrl)
            .placeholder(R.drawable.ic_music_note)
            .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, 
                                          @Nullable Transition<? super Bitmap> transition) {
                    artworkView.setImageBitmap(resource);
                    currentArtwork = resource;
                    if (artworkCallback != null) {
                        artworkCallback.onArtworkLoaded(resource);
                    }
                }
                
                @Override
                public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    // Optional: handle cleanup
                }
            });
    }
    
    /**
     * Get current artwork bitmap
     */
    public Bitmap getCurrentArtwork() {
        return currentArtwork;
    }
    
    /**
     * Set file path click listener
     */
    public void setFilePathClickListener(View.OnClickListener listener) {
        filePathView.setOnClickListener(listener);
    }
    
    /**
     * Extract filename from path
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash < filePath.length() - 1)
                ? filePath.substring(lastSlash + 1)
                : filePath;
    }
}