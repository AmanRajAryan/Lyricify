package aman.lyricify;

import android.content.ComponentName;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import jp.wasabeef.glide.transformations.BlurTransformation;

import java.util.List;

import aman.youly.LyricsWebViewFragment;

public class SyncedLyricsActivity extends AppCompatActivity {

    // UI Components
    private SyncedLyricsView syncedLyricsView;
    private FrameLayout webViewContainer; 
    private ImageView headerArtwork;
    private ImageView immersiveBackground;
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
    private boolean isWebViewMode = false; // Toggle state

    private String title;
    private String artist;
    private String lyrics;
    private String artworkUrl;

    // YouLy Component
    private LyricsWebViewFragment lyricsWebViewFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_synced_lyrics);

        initializeViews();
        extractIntentData();
        
        // Setup players and subscribers
        setupYouLyFragment(); 
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
     * A single entry point for seeking, used by Native, Web, and future players.
     * @param timeMs Target time in milliseconds
     */
    private void seekToPosition(long timeMs) {
        runOnUiThread(() -> {
            if (mediaController != null) {
                mediaController.getTransportControls().seekTo(timeMs);
                
                // Optional: Feedback (You can comment this out if it's too noisy)
                // Toast.makeText(this, "Seek: " + formatTime(timeMs), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeViews() {
        immersiveBackground = findViewById(R.id.immersiveBackground);
        syncedLyricsView = findViewById(R.id.syncedLyricsView);
        webViewContainer = findViewById(R.id.webViewContainer); 
        
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
        // Instantiate the fragment
        lyricsWebViewFragment = new LyricsWebViewFragment();

        // SUBSCRIBE: Web Engine -> Universal Seeker
        lyricsWebViewFragment.setLyricsListener(this::seekToPosition);

        // Add to container (Hidden initially)
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.webViewContainer, lyricsWebViewFragment);
        transaction.commit();
    }

    private void fetchAndDisplayNativeLyrics() {
        if (lyrics != null && !lyrics.isEmpty()) {
            syncedLyricsView.setLyrics(lyrics);
            
            // SUBSCRIBE: Native View -> Universal Seeker
            syncedLyricsView.setSeekListener(this::seekToPosition);
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
            Glide.with(this).asBitmap().load(formattedUrl).into(headerArtwork);
            Glide.with(this)
                    .load(formattedUrl)
                    .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 3)))
                    .into(immersiveBackground);
        } else {
            headerArtwork.setImageResource(R.drawable.ic_music_note);
            immersiveBackground.setImageResource(R.drawable.ic_music_note);
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

        // SWITCH VIEW BUTTON
        playerChangerButton.setOnClickListener(v -> togglePlayerView());

        fontSwitchButton.setOnClickListener(
                v -> {
                    if (!isWebViewMode) {
                        String fontName = syncedLyricsView.cycleFont();
                        Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Settings handled by Web Player", Toast.LENGTH_SHORT).show();
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
        isWebViewMode = !isWebViewMode;

        if (isWebViewMode) {
            // SWITCH TO WEB VIEW (Phase 2: Render)
            syncedLyricsView.setVisibility(View.GONE);
            webViewContainer.setVisibility(View.VISIBLE);
            playerChangerButton.setIconResource(R.drawable.ic_layers); 
            
            // Trigger the "Show" command in JS to render cached lyrics
            if (lyricsWebViewFragment != null) {
                lyricsWebViewFragment.displayLyrics();
            }
            Toast.makeText(this, "Switched to Web Engine", Toast.LENGTH_SHORT).show();
        } else {
            // SWITCH TO NATIVE VIEW
            webViewContainer.setVisibility(View.GONE);
            syncedLyricsView.setVisibility(View.VISIBLE);
            playerChangerButton.setIconResource(R.drawable.ic_swap_horiz);
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

                                // SPLIT LOGIC: Update ONLY the active view
                                if (isWebViewMode) {
                                    if (lyricsWebViewFragment != null) {
                                        lyricsWebViewFragment.updateTime(adjustedPosition);
                                    }
                                } else {
                                    syncedLyricsView.updateTime(adjustedPosition);
                                }

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
