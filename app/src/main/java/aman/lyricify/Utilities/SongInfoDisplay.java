package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.lang.ref.WeakReference;

import jp.wasabeef.glide.transformations.BlurTransformation;

/**
 * Handles displaying song information (artwork, background, title, artist, file path)
 */
public class SongInfoDisplay {
    
    private final WeakReference<Context> contextRef;
    private final ImageView artworkView;
    private final ImageView backgroundView; // Added for immersive effect
    private final TextView titleView;
    private final TextView artistView;
    private final TextView filePathView;
    
    private Bitmap currentArtwork;
    private ArtworkLoadedCallback artworkCallback;
    
    public interface ArtworkLoadedCallback {
        void onArtworkLoaded(Bitmap bitmap);
    }
    
    // Updated Constructor
    public SongInfoDisplay(Context context, ImageView artwork, ImageView background,
                          TextView title, TextView artist, TextView filePath) {
        this.contextRef = new WeakReference<>(context);
        this.artworkView = artwork;
        this.backgroundView = background;
        this.titleView = title;
        this.artistView = artist;
        this.filePathView = filePath;
    }
    
    public void setArtworkCallback(ArtworkLoadedCallback callback) {
        this.artworkCallback = callback;
    }
    
    public void displaySongInfo(String title, String artist, String artworkUrl, String filePath) {
        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        } else {
            titleView.setText("Unknown Song");
        }
        
        if (artist != null && !artist.isEmpty()) {
            artistView.setText(artist);
        } else {
            artistView.setText("Unknown Artist");
        }
        
        displayFilePath(filePath);
        loadArtwork(artworkUrl);
    }
    
    private void displayFilePath(String filePath) {
        if (filePathView == null) return;
        if (filePath != null && !filePath.isEmpty()) {
            String fileName = extractFileName(filePath);
            filePathView.setText(fileName);
            filePathView.setVisibility(View.VISIBLE);
        } else {
            filePathView.setVisibility(View.GONE);
        }
    }
    
    private void loadArtwork(String artworkUrl) {
        Context context = contextRef.get();
        if (context == null) return;

        if (artworkUrl == null || artworkUrl.isEmpty()) {
            artworkView.setImageResource(R.drawable.ic_music_note);
            if (backgroundView != null) {
                backgroundView.setImageResource(android.R.color.black);
            }
            return;
        }
        
        String formattedUrl = artworkUrl
            .replace("{w}", "1000")
            .replace("{h}", "1000")
            .replace("{f}", "jpg");
        
        // 1. Load Main Artwork
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

        // 2. Load Blurred Background
        if (backgroundView != null) {
            Glide.with(context)
                .load(formattedUrl)
                .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3)))
                .into(backgroundView);
        }
    }
    
    public Bitmap getCurrentArtwork() {
        return currentArtwork;
    }
    
    public void setFilePathClickListener(View.OnClickListener listener) {
        if (filePathView != null) {
            filePathView.setOnClickListener(listener);
        }
    }
    
    private String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash < filePath.length() - 1)
                ? filePath.substring(lastSlash + 1)
                : filePath;
    }
}
