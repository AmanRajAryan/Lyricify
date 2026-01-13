package aman.lyricify;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentTransaction;

import aman.youly.LyricsWebViewFragment;

// GLIDE IMPORTS
import aman.lyricify.glide.AudioFileCover;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import jp.wasabeef.glide.transformations.BlurTransformation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.List;













import android.content.SharedPreferences;
import com.bumptech.glide.load.DecodeFormat;


















public class YoulyPlayerActivity extends AppCompatActivity {

    // UI Components
    private MotionLayout rootLayout;
    private FrameLayout webViewContainer;
    private ImageView headerArtwork;
    private ImageView immersiveBackground;
    private GradientMaskedImageView immersiveBackgroundOverlay;
    private TextView songTitleText;
    private TextView songArtistText;
    private TextView positionText;

    // UI Groups
    private MaterialCardView headerCard;
    private LinearLayout controlsLayout;
    private View controlsScrim;

    // Buttons
    private FloatingActionButton playPauseButton;
    private MaterialButton immersiveButton;
    private MaterialButton reloadButton;
    private MaterialButton prevButton, nextButton;
    private MaterialButton btnOpenLyrics; 
    
    private SquigglySeekBar progressSeekBar;

    // Logic Variables
    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaControllerCallback;

    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isTracking = false;
    private boolean isPlaying = false;
    private boolean lastPlayingState = false;

    private boolean isImmersiveMode = false;

    private String currentTitle = "DEFAULT_EMPTY_TITLE";
    private String currentArtist = "";
    private String currentAlbum = "";
    private long currentDuration = 0;
    
    private String currentFilePath = null;

    private LyricsWebViewFragment lyricsWebViewFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();
        setContentView(R.layout.activity_youly_player);

        initializeViews();
        setupYouLyFragment();
        setupControls();
        setupMediaSession();

        updateHandler = new Handler(Looper.getMainLooper());
        startPositionUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncNow();
    }

    private void syncNow() {
        if (mediaController != null) {
            PlaybackState state = mediaController.getPlaybackState();
            if (state != null) {
                long pos = state.getPosition();
                boolean playing = (state.getState() == PlaybackState.STATE_PLAYING);
                
                if (!isTracking) progressSeekBar.setProgress((int) pos);
                positionText.setText(formatTime(pos));
                
                if (lyricsWebViewFragment != null) {
                    lyricsWebViewFragment.setPlaying(playing);
                    lyricsWebViewFragment.updateTime(pos);
                }
            }
        }
    }

    private void seekToPosition(long timeMs) {
        runOnUiThread(() -> {
            if (mediaController != null) {
                mediaController.getTransportControls().seekTo(timeMs);
                if (lyricsWebViewFragment != null) lyricsWebViewFragment.updateTime(timeMs);
            }
        });
    }

    private void initializeViews() {
        rootLayout = findViewById(R.id.rootLayout);
        webViewContainer = findViewById(R.id.webViewContainer);
        immersiveBackground = findViewById(R.id.immersiveBackground);
        immersiveBackgroundOverlay = findViewById(R.id.immersiveBackgroundOverlay);

        headerCard = findViewById(R.id.headerCard);
        controlsLayout = findViewById(R.id.controlsLayout);
        controlsScrim = findViewById(R.id.controlsScrim);

        headerArtwork = findViewById(R.id.headerArtwork);
        songTitleText = findViewById(R.id.songTitleText);
        songArtistText = findViewById(R.id.songArtistText);
        positionText = findViewById(R.id.positionText);

        playPauseButton = findViewById(R.id.playPauseButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        progressSeekBar = findViewById(R.id.progressSeekBar);

        immersiveButton = findViewById(R.id.immersiveButton);
        reloadButton = findViewById(R.id.reloadButton);

        btnOpenLyrics = findViewById(R.id.btnOpenLyrics);

        applyBlurEffect();
    }

    private void setupYouLyFragment() {
        lyricsWebViewFragment = new LyricsWebViewFragment();
        lyricsWebViewFragment.setLyricsListener(this::seekToPosition);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.webViewContainer, lyricsWebViewFragment);
        transaction.commitNow();
    }

    private void applyBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect blurEffect = RenderEffect.createBlurEffect(250f, 150f, Shader.TileMode.CLAMP);
            if (immersiveBackground != null) immersiveBackground.setRenderEffect(blurEffect);
            if (immersiveBackgroundOverlay != null) immersiveBackgroundOverlay.setRenderEffect(blurEffect);
        }
    }

    private void setupControls() {
        if (btnOpenLyrics != null) {
            btnOpenLyrics.setOnClickListener(v -> {
                if (rootLayout.getCurrentState() == R.id.end) {
                    rootLayout.transitionToStart();
                } else {
                    rootLayout.transitionToEnd();
                }
            });
        }

        playPauseButton.setOnClickListener(v -> {
            if (mediaController != null) {
                if (isPlaying) mediaController.getTransportControls().pause();
                else mediaController.getTransportControls().play();
            }
        });

        prevButton.setOnClickListener(v -> {
            animateButton(v);
            if (mediaController != null) mediaController.getTransportControls().skipToPrevious();
        });

        nextButton.setOnClickListener(v -> {
            animateButton(v);
            if (mediaController != null) mediaController.getTransportControls().skipToNext();
        });

        progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    if (lyricsWebViewFragment != null) lyricsWebViewFragment.updateTime(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { isTracking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { 
                isTracking = false; 
                if (mediaController != null) mediaController.getTransportControls().seekTo(seekBar.getProgress());
            }
        });

        immersiveButton.setOnClickListener(v -> toggleImmersiveMode());

        reloadButton.setOnClickListener(v -> {
            animateButton(v);
            if (lyricsWebViewFragment != null && !currentTitle.equals("DEFAULT_EMPTY_TITLE")) {
                Toast.makeText(this, "Reloading...", Toast.LENGTH_SHORT).show();
                long durationSec = currentDuration / 1000;
                lyricsWebViewFragment.loadLyrics(currentTitle, currentArtist, currentAlbum, durationSec);
            }
        });
    }

    private void toggleImmersiveMode() {
        isImmersiveMode = !isImmersiveMode;
        int duration = 300;

        if (isImmersiveMode) {
            // HIDING UI
            headerCard.animate().alpha(0f).setDuration(duration).start();
            headerCard.setClickable(false);
            
            controlsLayout.animate().alpha(0f).setDuration(duration).start();
            setRecursiveClickable(controlsLayout, false);

            if(controlsScrim != null) controlsScrim.animate().alpha(0f).setDuration(duration).start();
            
            songTitleText.animate().alpha(0f).setDuration(duration).start();
            songArtistText.animate().alpha(0f).setDuration(duration).start();

            if(btnOpenLyrics != null) {
                btnOpenLyrics.animate().alpha(0f).setDuration(duration).start();
                btnOpenLyrics.setClickable(false);
            }
            if(reloadButton != null) {
                reloadButton.animate().alpha(0f).setDuration(duration).start();
                reloadButton.setClickable(false);
            }
            if(immersiveButton != null) {
                immersiveButton.animate().alpha(0f).setDuration(duration).start();
                immersiveButton.setClickable(false);
            }

            immersiveBackgroundOverlay.animate().alpha(0f).setDuration(500).start();
            Toast.makeText(this, "Immersive Mode On", Toast.LENGTH_SHORT).show();

        } else {
            // SHOWING UI
            headerCard.animate().alpha(1f).setDuration(duration).start();
            headerCard.setClickable(true);
            
            controlsLayout.animate().alpha(1f).setDuration(duration).start();
            setRecursiveClickable(controlsLayout, true);

            if(controlsScrim != null) controlsScrim.animate().alpha(1f).setDuration(duration).start();

            songTitleText.animate().alpha(1f).setDuration(duration).start();
            songArtistText.animate().alpha(1f).setDuration(duration).start();

            if(btnOpenLyrics != null) {
                btnOpenLyrics.animate().alpha(1f).setDuration(duration).start();
                btnOpenLyrics.setClickable(true);
            }
            if(reloadButton != null) {
                reloadButton.animate().alpha(1f).setDuration(duration).start();
                reloadButton.setClickable(true);
            }
            if(immersiveButton != null) {
                immersiveButton.animate().alpha(1f).setDuration(duration).start();
                immersiveButton.setClickable(true);
            }

            immersiveBackgroundOverlay.animate().alpha(1f).setDuration(500).start();
        }
    }
    
    private void setRecursiveClickable(View view, boolean clickable) {
        view.setClickable(clickable);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setRecursiveClickable(group.getChildAt(i), clickable);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isImmersiveMode) {
            toggleImmersiveMode();
            return;
        }
        if (rootLayout != null && rootLayout.getCurrentState() == R.id.end) {
            rootLayout.transitionToStart();
            return;
        }
        super.onBackPressed();
    }

    private void setupMediaSession() {
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(
                    new ComponentName(this, SongNotificationListener.class));
            MediaController activeController = findActiveController(controllers);
            if (activeController != null) attachToController(activeController);
            else Toast.makeText(this, "No active music player found", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Enable notification access", Toast.LENGTH_SHORT).show();
        }
    }

    private MediaController findActiveController(List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                int playbackState = state.getState();
                if (playbackState == PlaybackState.STATE_PLAYING || 
                    playbackState == PlaybackState.STATE_PAUSED || 
                    playbackState == PlaybackState.STATE_BUFFERING) return controller;
            }
        }
        return null;
    }

    private void attachToController(MediaController controller) {
        if (mediaController != null && mediaControllerCallback != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
        mediaController = controller;
        mediaControllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) { updatePlaybackState(state); }
            @Override
            public void onMetadataChanged(MediaMetadata metadata) { updateMetadata(metadata); }
        };
        controller.registerCallback(mediaControllerCallback);
        updatePlaybackState(controller.getPlaybackState());
        updateMetadata(controller.getMetadata());
        syncNow(); 
    }

    private void updateMetadata(MediaMetadata metadata) {
        if (metadata == null) return;
        String newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        
        if (newTitle == null) newTitle = "";
        if (newArtist == null) newArtist = "";

        boolean songChanged = !newTitle.equals(currentTitle) || !newArtist.equals(currentArtist);
        
        if (songChanged) {
            currentTitle = newTitle;
            currentArtist = newArtist;
            currentAlbum = newAlbum;
            currentDuration = duration;
            currentFilePath = null; 
            
            runOnUiThread(() -> {
                songTitleText.setText(currentTitle);
                songArtistText.setText(currentArtist);
                if (duration > 0) progressSeekBar.setMax((int) duration);
            });

            // 1. Get Static Bitmap from Metadata
            Bitmap newArtwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (newArtwork == null) newArtwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            
            final Bitmap finalArt = newArtwork;
            
            // 2. Load it as the "Base" image immediately
            runOnUiThread(() -> updateArtwork(finalArt));

            // 3. Search for Animated File, passing the Static Bitmap as "fallback"
            searchForLocalFile(this, newTitle, newArtist, finalArt);

            // 4. Update Lyrics
            final String fTitle = newTitle;
            final String fArtist = newArtist;
            final String fAlbum = newAlbum;
            
            if (webViewContainer != null) {
                webViewContainer.post(() -> {
                    if (lyricsWebViewFragment != null && lyricsWebViewFragment.isAdded()) {
                        long durSeconds = duration / 1000;
                        lyricsWebViewFragment.loadLyrics(fTitle, fArtist, fAlbum, durSeconds);
                        lyricsWebViewFragment.displayLyrics();
                        lyricsWebViewFragment.setPlaying(isPlaying);
                        syncNow();
                    }
                });
            }
        }
    }

    private void searchForLocalFile(Context context, String title, String artist, Bitmap staticArtwork) {
        MediaStoreHelper.searchLocalSong(context, title, artist, 
            new MediaStoreHelper.SearchCallback() {
                @Override
                public void onFound(MediaStoreHelper.LocalSong song) {
                    currentFilePath = song.filePath;
                    runOnUiThread(() -> {
                        loadAnimatedArtwork(song.filePath, staticArtwork);
                    });
                }
                
                @Override
                public void onNotFound() {
                    currentFilePath = null;
                }
                
                @Override
                public void onError(String error) {
                    currentFilePath = null;
                }
            }
        );
    }

    private void loadAnimatedArtwork(String filePath, Bitmap staticArtwork) {
    if (filePath == null) return;
    
    // 1. Check Preference
    SharedPreferences prefs = getSharedPreferences("LyricifyPrefs", MODE_PRIVATE);
    boolean useLowRam = prefs.getBoolean("low_ram_enabled", false);

    long lastModified = 0;
    try {
        lastModified = new File(filePath).lastModified();
    } catch (Exception ignored) {}
    
    AudioFileCover coverModel = new AudioFileCover(filePath, lastModified);
    Drawable currentPlaceholder = headerArtwork.getDrawable();

    // 2. Select Format based on Toggle
    DecodeFormat format = useLowRam ? DecodeFormat.PREFER_RGB_565 : DecodeFormat.PREFER_ARGB_8888;

    Glide.with(this)
         .load(coverModel)
         .apply(new RequestOptions().format(format)) // Apply selection
         .override(1000, 1000)
         .diskCacheStrategy(DiskCacheStrategy.DATA)
         .placeholder(currentPlaceholder)
         .error(staticArtwork)
         .thumbnail(Glide.with(this).load(staticArtwork)) 
         .dontAnimate() 
         .into(headerArtwork);
}

    private void updateArtwork(Bitmap bitmap) {
        if (bitmap != null) {
            // FLICKER FIX FOR HEADER:
            // Capture the OLD image currently in the view
            Drawable oldHeaderArt = headerArtwork.getDrawable();
            
            // Load NEW image, but use OLD image as placeholder
            Glide.with(this)
                 .asBitmap()
                 .load(bitmap)
                 .placeholder(oldHeaderArt) // Keeps old art on screen until new is ready
                 .dontAnimate()
                 .into(headerArtwork);
            
            // FLICKER FIX FOR BACKGROUNDS:
            Drawable currentBg = immersiveBackground.getDrawable();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Glide.with(this)
                     .load(bitmap)
                     .placeholder(currentBg) 
                     .dontAnimate()
                     .into(immersiveBackground);
                     
                Glide.with(this)
                     .load(bitmap)
                     .placeholder(immersiveBackgroundOverlay.getDrawable())
                     .dontAnimate()
                     .into(immersiveBackgroundOverlay);
            } else {
                Glide.with(this).load(bitmap)
                     .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 12)))
                     .placeholder(currentBg)
                     .dontAnimate()
                     .into(immersiveBackground);
                     
                Glide.with(this).load(bitmap)
                     .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 17)))
                     .placeholder(immersiveBackgroundOverlay.getDrawable())
                     .dontAnimate()
                     .into(immersiveBackgroundOverlay);
            }
        } else {
            // Handle null case
            headerArtwork.setImageResource(R.drawable.ic_music_note);
            immersiveBackground.setImageResource(R.drawable.ic_music_note);
            immersiveBackgroundOverlay.setImageResource(R.drawable.ic_music_note);
        }
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) return;
        boolean isNowPlaying = (state.getState() == PlaybackState.STATE_PLAYING);
        isPlaying = isNowPlaying;

        if (lyricsWebViewFragment != null) {
            lyricsWebViewFragment.setPlaying(isPlaying);
            lyricsWebViewFragment.updateTime(state.getPosition());
        }

        runOnUiThread(() -> {
            if (isNowPlaying != lastPlayingState) {
                int drawableId = isNowPlaying ? R.drawable.avd_play_to_pause : R.drawable.avd_pause_to_play;
                playPauseButton.setImageResource(drawableId);
                Drawable drawable = playPauseButton.getDrawable();
                if (drawable instanceof Animatable) ((Animatable) drawable).start();
                lastPlayingState = isNowPlaying;
            } else {
                playPauseButton.setImageResource(isNowPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            }
            if (isNowPlaying) progressSeekBar.startAnimation();
            else progressSeekBar.stopAnimation();
        });
    }

    private void animateButton(View view) {
        view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
    }

    private void startPositionUpdates() {
        updateRunnable = new Runnable() {
            private long lastSentPosition = -1;

            @Override
            public void run() {
                if (mediaController != null) {
                    PlaybackState state = mediaController.getPlaybackState();
                    if (state != null) {
                        long currentPosition = state.getPosition();
                        long delta = Math.abs(currentPosition - lastSentPosition);

                        if (delta >= 1000) {
                            if (lyricsWebViewFragment != null) {
                                lyricsWebViewFragment.updateTime(currentPosition);
                            }
                            lastSentPosition = currentPosition;
                        }

                        if (!isTracking) {
                            progressSeekBar.setProgress((int) currentPosition);
                        }
                        positionText.setText(formatTime(currentPosition));
                    }
                }
                updateHandler.postDelayed(this, 16);
            }
        };
        updateHandler.post(updateRunnable);
    }

    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void hideSystemUI() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (mediaController != null && mediaControllerCallback != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
    }
}
