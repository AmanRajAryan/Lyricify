package aman.lyricify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentTransaction;

import aman.youly.LyricsWebViewFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import jp.wasabeef.glide.transformations.BlurTransformation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class YoulyPlayerActivity extends AppCompatActivity {

    // UI Components
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

    private void seekToPosition(long timeMs) {
        runOnUiThread(() -> {
            if (mediaController != null) {
                mediaController.getTransportControls().seekTo(timeMs);
            }
        });
    }

    private void initializeViews() {
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
                if (fromUser && mediaController != null) mediaController.getTransportControls().seekTo(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { isTracking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { isTracking = false; }
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
            // --- ENTERING IMMERSIVE (HIDE UI) ---
            
            // Fade out Header
            headerCard.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction(() -> headerCard.setVisibility(View.GONE))
                    .start();

            // Fade out Controls
            controlsLayout.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction(() -> controlsLayout.setVisibility(View.GONE))
                    .start();

            // Fade out Scrim
            controlsScrim.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction(() -> controlsScrim.setVisibility(View.GONE))
                    .start();
            
            // Fade out Dimmer
            immersiveBackgroundOverlay.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .start();

            Toast.makeText(this, "Immersive Mode On (Tap Back to Exit)", Toast.LENGTH_SHORT).show();

        } else {
            // --- EXITING IMMERSIVE (SHOW UI) ---
            
            // 1. Prepare Header
            if (headerCard.getVisibility() != View.VISIBLE) {
                headerCard.setAlpha(0f); // Reset alpha to 0 so we can animate it
                headerCard.setVisibility(View.VISIBLE); // Make it visible immediately
            }
            headerCard.animate().alpha(1f).setDuration(duration).setListener(null).withEndAction(null).start();

            // 2. Prepare Controls
            if (controlsLayout.getVisibility() != View.VISIBLE) {
                controlsLayout.setAlpha(0f);
                controlsLayout.setVisibility(View.VISIBLE);
            }
            controlsLayout.animate().alpha(1f).setDuration(duration).setListener(null).withEndAction(null).start();

            // 3. Prepare Scrim
            if (controlsScrim.getVisibility() != View.VISIBLE) {
                controlsScrim.setAlpha(0f);
                controlsScrim.setVisibility(View.VISIBLE);
            }
            controlsScrim.animate().alpha(1f).setDuration(duration).setListener(null).withEndAction(null).start();
            
            // 4. Fade in Dimmer
            immersiveBackgroundOverlay.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .start();
        }
    }
    
    @Override
    public void onBackPressed() {
        if (isImmersiveMode) {
            toggleImmersiveMode();
        } else {
            super.onBackPressed();
        }
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
                if (playbackState == PlaybackState.STATE_PLAYING || playbackState == PlaybackState.STATE_PAUSED) return controller;
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
    }

    private void updateMetadata(MediaMetadata metadata) {
        if (metadata == null) return;
        String newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (newTitle == null) newTitle = "";
        if (newArtist == null) newArtist = "";

        if (!newTitle.equals(currentTitle) || !newArtist.equals(currentArtist)) {
            currentTitle = newTitle;
            currentArtist = newArtist;
            currentAlbum = newAlbum;
            currentDuration = duration;

            runOnUiThread(() -> {
                songTitleText.setText(currentTitle);
                songArtistText.setText(currentArtist);
                if (duration > 0) progressSeekBar.setMax((int) duration);
            });

            Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            final Bitmap finalArt = art;
            runOnUiThread(() -> updateArtwork(finalArt));

            final String fTitle = newTitle;
            final String fArtist = newArtist;
            final String fAlbum = newAlbum;
            webViewContainer.post(() -> {
                if (lyricsWebViewFragment != null && lyricsWebViewFragment.isAdded()) {
                    long durSeconds = duration / 1000;
                    Toast.makeText(this, "Fetching: " + fTitle, Toast.LENGTH_SHORT).show();
                    lyricsWebViewFragment.loadLyrics(fTitle, fArtist, fAlbum, durSeconds);
                    lyricsWebViewFragment.displayLyrics();
                    lyricsWebViewFragment.setPlaying(isPlaying);
                }
            });
        }
    }

    private void updateArtwork(Bitmap bitmap) {
        if (bitmap != null) {
            Glide.with(this).asBitmap().load(bitmap).into(headerArtwork);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Glide.with(this).load(bitmap).into(immersiveBackground);
                Glide.with(this).load(bitmap).into(immersiveBackgroundOverlay);
            } else {
                Glide.with(this).load(bitmap).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 12))).into(immersiveBackground);
                Glide.with(this).load(bitmap).apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 17))).into(immersiveBackgroundOverlay);
            }
        } else {
            headerArtwork.setImageResource(R.drawable.ic_music_note);
            immersiveBackground.setImageResource(R.drawable.ic_music_note);
            immersiveBackgroundOverlay.setImageResource(R.drawable.ic_music_note);
        }
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) return;
        boolean isNowPlaying = (state.getState() == PlaybackState.STATE_PLAYING);
        isPlaying = isNowPlaying;
        if (lyricsWebViewFragment != null) lyricsWebViewFragment.setPlaying(isPlaying);

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
            @Override
            public void run() {
                if (mediaController != null) {
                    PlaybackState state = mediaController.getPlaybackState();
                    if (state != null) {
                        long position = state.getPosition();
                        if (lyricsWebViewFragment != null) lyricsWebViewFragment.updateTime(position);
                        if (!isTracking) progressSeekBar.setProgress((int) position);
                        positionText.setText(formatTime(position));
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
