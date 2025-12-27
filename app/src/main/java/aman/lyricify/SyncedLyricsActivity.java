package aman.lyricify;

import android.content.ComponentName;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import jp.wasabeef.glide.transformations.BlurTransformation;

import java.util.List;

import aman.youly.LyricsWebViewFragment;

public class SyncedLyricsActivity extends AppCompatActivity {

    // UI Components
    private SyncedLyricsView syncedLyricsView;
    private FrameLayout webViewContainer;
    private FrameLayout karaokeContainer;
    private ImageView headerArtwork;
    private ImageView immersiveBackground;
    private GradientMaskedImageView immersiveBackgroundOverlay;
    private TextView songTitleText;
    private TextView songArtistText;
    private TextView positionText;

    private FloatingActionButton playPauseButton;
    private MaterialButton prevButton, nextButton;
    private MaterialButton playerChangerButton, fontSwitchButton;

    private SeekBar progressSeekBar;

    // Logic Variables
    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaControllerCallback;

    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isTracking = false;
    private boolean isPlaying = false;

    private long timingOffset = 0;
    
    // Player mode: 0 = Native, 1 = Web, 2 = Karaoke
    private int currentPlayerMode = 0;

    private String title;
    private String artist;
    private String lyrics;
    private String artworkUrl;

    // Player Components
    private LyricsWebViewFragment lyricsWebViewFragment;
    private KaraokeLyricsFragment karaokeLyricsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_synced_lyrics);

        initializeViews();
        extractIntentData();
        
        // Setup all three players
        setupYouLyFragment(); 
        setupKaraokeFragment();
        setupMediaSession();
        fetchAndDisplayNativeLyrics();
        
        setupControls();

        updateHandler = new Handler(Looper.getMainLooper());
        startPositionUpdates();
    }

    // =========================================================
    //  UNIVERSAL SEEKING METHOD
    // =========================================================
    /**
     * A single entry point for seeking, used by Native, Web, and Karaoke players.
     * @param timeMs Target time in milliseconds
     */
    private void seekToPosition(long timeMs) {
        runOnUiThread(() -> {
            if (mediaController != null) {
                mediaController.getTransportControls().seekTo(timeMs);
            }
        });
    }

    private void initializeViews() {
        immersiveBackground = findViewById(R.id.immersiveBackground);
        immersiveBackgroundOverlay = findViewById(R.id.immersiveBackgroundOverlay);
        syncedLyricsView = findViewById(R.id.syncedLyricsView);
        webViewContainer = findViewById(R.id.webViewContainer);
        karaokeContainer = findViewById(R.id.karaokeContainer);
        
        headerArtwork = findViewById(R.id.headerArtwork);
        songTitleText = findViewById(R.id.syncedSongTitle);
        songArtistText = findViewById(R.id.syncedSongArtist);
        positionText = findViewById(R.id.positionText);

        playPauseButton = findViewById(R.id.playPauseButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);

        progressSeekBar = findViewById(R.id.progressSeekBar);
        playerChangerButton = findViewById(R.id.playerChangerButton);
        fontSwitchButton = findViewById(R.id.fontSwitchButton);
    }

    private void setupYouLyFragment() {
        // Instantiate the web fragment
        lyricsWebViewFragment = new LyricsWebViewFragment();

        // SUBSCRIBE: Web Engine -> Universal Seeker
        lyricsWebViewFragment.setLyricsListener(this::seekToPosition);

        // Add to container (Hidden initially)
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.webViewContainer, lyricsWebViewFragment);
        transaction.commit();
    }

    private void setupKaraokeFragment() {
        // Instantiate the karaoke fragment
        karaokeLyricsFragment = new KaraokeLyricsFragment();

        // SUBSCRIBE: Karaoke Engine -> Universal Seeker
        karaokeLyricsFragment.setSeekListener(this::seekToPosition);

        // Add to container (Hidden initially)
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.karaokeContainer, karaokeLyricsFragment);
        transaction.commit();
    }

    private void fetchAndDisplayNativeLyrics() {
        if (lyrics != null && !lyrics.isEmpty()) {
            // Setup Native View
            syncedLyricsView.setLyrics(lyrics);
            syncedLyricsView.setSeekListener(this::seekToPosition);
            
            // Setup Karaoke View with the same lyrics
            karaokeLyricsFragment.setLyrics(lyrics);
        }
    }

    private void extractIntentData() {
        title = getIntent().getStringExtra("SONG_TITLE");
        artist = getIntent().getStringExtra("SONG_ARTIST");
        lyrics = getIntent().getStringExtra("LYRICS");
        artworkUrl = getIntent().getStringExtra("ARTWORK_URL");

        songTitleText.setText(title != null ? title : "Unknown Song");
        songArtistText.setText(artist != null ? artist : "Unknown Artist");
        loadArtwork();
    }

    private void loadArtwork() {
        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            String formattedUrl = artworkUrl.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg");
            
            // Load normal artwork in header
            Glide.with(this).asBitmap().load(formattedUrl).into(headerArtwork);
            
            // Load blurred artwork for base background
            Glide.with(this)
                    .load(formattedUrl)
                    .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 12)))
                    .into(immersiveBackground);
            
            // Load same blurred artwork for overlay
            Glide.with(this)
                    .load(formattedUrl)
                    .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 17)))
                    .into(immersiveBackgroundOverlay);
        } else {
            headerArtwork.setImageResource(R.drawable.ic_music_note);
            immersiveBackground.setImageResource(R.drawable.ic_music_note);
            immersiveBackgroundOverlay.setImageResource(R.drawable.ic_music_note);
        }
    }

    private void setupMediaSession() {
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        try {
            List<MediaController> controllers =
                    mediaSessionManager.getActiveSessions(
                            new ComponentName(this, SongNotificationListener.class));

            MediaController activeController = findActiveController(controllers);
            if (activeController != null) {
                attachToController(activeController);
            } else {
                Toast.makeText(this, "No active music player found", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Enable notification access", Toast.LENGTH_SHORT).show();
        }
    }

    private MediaController findActiveController(List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                int playbackState = state.getState();
                if (playbackState == PlaybackState.STATE_PLAYING
                        || playbackState == PlaybackState.STATE_PAUSED) {
                    return controller;
                }
            }
        }
        return null;
    }

    private void attachToController(MediaController controller) {
        mediaController = controller;
        mediaControllerCallback =
                new MediaController.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        updatePlaybackState(state);
                    }

                    @Override
                    public void onMetadataChanged(MediaMetadata metadata) {
                        updateMetadata(metadata);
                    }
                };

        controller.registerCallback(mediaControllerCallback);
        updatePlaybackState(controller.getPlaybackState());
        updateMetadata(controller.getMetadata());
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) return;
        int playbackState = state.getState();
        isPlaying = (playbackState == PlaybackState.STATE_PLAYING);

        // Update Karaoke player's playing state
        if (karaokeLyricsFragment != null) {
            karaokeLyricsFragment.setPlaying(isPlaying);
        }

        runOnUiThread(
                () -> {
                    if (isPlaying) {
                        playPauseButton.setImageResource(R.drawable.ic_pause); 
                    } else {
                        playPauseButton.setImageResource(R.drawable.ic_play_arrow); 
                    }
                });
    }

    private void updateMetadata(MediaMetadata metadata) {
        if (metadata == null) return;
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration > 0) {
            runOnUiThread(() -> progressSeekBar.setMax((int) duration));
        }
    }

    private void setupControls() {
        playPauseButton.setOnClickListener(
                v -> {
                    if (mediaController != null) {
                        if (isPlaying) mediaController.getTransportControls().pause();
                        else mediaController.getTransportControls().play();
                    }
                });

        prevButton.setOnClickListener(
                v -> {
                    if (mediaController != null) mediaController.getTransportControls().skipToPrevious();
                });

        nextButton.setOnClickListener(
                v -> {
                    if (mediaController != null) mediaController.getTransportControls().skipToNext();
                });

        // SWITCH VIEW BUTTON - Now cycles through 3 modes
        playerChangerButton.setOnClickListener(v -> togglePlayerView());

        // FONT SWITCH BUTTON - Updated to support all modes
        fontSwitchButton.setOnClickListener(
                v -> {
                    String fontName;
                    switch (currentPlayerMode) {
                        case 0: // Native view
                            fontName = syncedLyricsView.cycleFont();
                            Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                            break;
                        case 1: // Web view
                            Toast.makeText(this, "Font settings not available in YouLy+ Engine", Toast.LENGTH_SHORT).show();
                            break;
                        case 2: // Karaoke view
                            fontName = karaokeLyricsFragment.cycleFont();
                            Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                            break;
                    }
                });

        progressSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && mediaController != null) {
                            mediaController.getTransportControls().seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) { isTracking = true; }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) { isTracking = false; }
                });
    }

    private void togglePlayerView() {
        // Store current position before switching
        long currentPosition = 0;
        if (mediaController != null) {
            PlaybackState state = mediaController.getPlaybackState();
            if (state != null) {
                currentPosition = state.getPosition() + timingOffset;
            }
        }

        // Cycle through: Native (0) -> Web (1) -> Karaoke (2) -> Native (0)
        currentPlayerMode = (currentPlayerMode + 1) % 3;

        // Hide all views first
        syncedLyricsView.setVisibility(View.GONE);
        webViewContainer.setVisibility(View.GONE);
        karaokeContainer.setVisibility(View.GONE);

        switch (currentPlayerMode) {
            case 0: // Native View
                syncedLyricsView.setVisibility(View.VISIBLE);
                // Sync to current position immediately
                syncedLyricsView.updateTime(currentPosition);
                playerChangerButton.setIconResource(R.drawable.ic_swap_horiz);
                Toast.makeText(this, "Native Engine", Toast.LENGTH_SHORT).show();
                break;
                
            case 1: // Web View
                webViewContainer.setVisibility(View.VISIBLE);
                playerChangerButton.setIconResource(R.drawable.ic_layers);
                
                // Trigger the "Show" command in JS to render cached lyrics
                if (lyricsWebViewFragment != null) {
                    lyricsWebViewFragment.displayLyrics();
                    // Sync to current position immediately
                    lyricsWebViewFragment.updateTime(currentPosition);
                }
                Toast.makeText(this, "YouLy+ Engine", Toast.LENGTH_SHORT).show();
                break;
                
            case 2: // Karaoke View
                karaokeContainer.setVisibility(View.VISIBLE);
                // Sync playback state and position immediately
                if (karaokeLyricsFragment != null) {
                    karaokeLyricsFragment.setPlaying(isPlaying);
                    karaokeLyricsFragment.updateTime(currentPosition);
                }
                playerChangerButton.setIconResource(R.drawable.ic_music_note);
                Toast.makeText(this, "Accompanist Engine", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void startPositionUpdates() {
        updateRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        if (mediaController != null) {
                            PlaybackState state = mediaController.getPlaybackState();
                            if (state != null) {
                                long position = state.getPosition();
                                long adjustedPosition = position + timingOffset;

                                // Update ONLY the active view
                                switch (currentPlayerMode) {
                                    case 0: // Native
                                        syncedLyricsView.updateTime(adjustedPosition);
                                        break;
                                    case 1: // Web
                                        if (lyricsWebViewFragment != null) {
                                            lyricsWebViewFragment.updateTime(adjustedPosition);
                                        }
                                        break;
                                    case 2: // Karaoke
                                        if (karaokeLyricsFragment != null) {
                                            karaokeLyricsFragment.updateTime(adjustedPosition);
                                        }
                                        break;
                                }

                                // Update UI controls (seekbar and time text)
                                if (!isTracking) {
                                    progressSeekBar.setProgress((int) position);
                                }
                                positionText.setText(formatTime(position));
                            }
                        }
                        updateHandler.postDelayed(this, 16); // 60 FPS loop
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (mediaController != null && mediaControllerCallback != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
    }

    private void hideSystemUI() {
        getWindow()
                .setFlags(
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }
}