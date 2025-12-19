package aman.lyricify;

import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Activity for displaying synced lyrics with music playback and karaoke animation */
public class SyncedLyricsActivity extends AppCompatActivity {

    private SyncedLyricsView syncedLyricsView;
    private ImageView headerArtwork;
    private TextView songTitleText;
    private TextView songArtistText;
    private TextView positionText;
    private ImageView playPauseButton;
    private SeekBar progressSeekBar;
    private ProgressBar loadingBar;
    private LinearLayout controlsLayout;
    private Button adjustTimingButton;
    private Button fontSwitchButton;

    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaControllerCallback;

    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isTracking = false;
    private boolean isPlaying = false;

    // Timing offset in milliseconds (can be adjusted by user)
    private long timingOffset = 0;

    private String songId;
    private String title;
    private String artist;
    private String lyrics;
    private String lyricsFormat;
    private String artworkUrl;
    private android.graphics.Bitmap artworkBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synced_lyrics);

        initializeViews();
        extractIntentData();
        setupMediaSession();
        fetchAndDisplayLyrics();
        setupControls();

        updateHandler = new Handler(Looper.getMainLooper());
        startPositionUpdates();
    }

    private void initializeViews() {
        syncedLyricsView = findViewById(R.id.syncedLyricsView);
        headerArtwork = findViewById(R.id.headerArtwork);
        songTitleText = findViewById(R.id.syncedSongTitle);
        songArtistText = findViewById(R.id.syncedSongArtist);
        positionText = findViewById(R.id.positionText);
        playPauseButton = findViewById(R.id.playPauseButton);
        progressSeekBar = findViewById(R.id.progressSeekBar);
        loadingBar = findViewById(R.id.syncedLoadingBar);
        controlsLayout = findViewById(R.id.controlsLayout);
        adjustTimingButton = findViewById(R.id.adjustTimingButton);
        fontSwitchButton = findViewById(R.id.fontSwitchButton);
    }

    private void extractIntentData() {
        songId = getIntent().getStringExtra("SONG_ID");
        title = getIntent().getStringExtra("SONG_TITLE");
        artist = getIntent().getStringExtra("SONG_ARTIST");
        lyrics = getIntent().getStringExtra("LYRICS");
        lyricsFormat = getIntent().getStringExtra("LYRICS_FORMAT");
        artworkUrl = getIntent().getStringExtra("ARTWORK_URL");

        // Display song info
        songTitleText.setText(title != null ? title : "Unknown Song");
        songArtistText.setText(artist != null ? artist : "Unknown Artist");

        // Load artwork
        loadArtwork();
    }

    private void loadArtwork() {
        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            String formattedUrl =
                    artworkUrl.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg");

            com.bumptech.glide.Glide.with(this)
                    .asBitmap()
                    .load(formattedUrl)
                    .into(
                            new com.bumptech.glide.request.target.CustomTarget<
                                    android.graphics.Bitmap>() {
                                @Override
                                public void onResourceReady(
                                        android.graphics.Bitmap resource,
                                        com.bumptech.glide.request.transition.Transition<
                                                        ? super android.graphics.Bitmap>
                                                transition) {
                                    artworkBitmap = resource;
                                    headerArtwork.setImageBitmap(resource);
                                    syncedLyricsView.setArtwork(resource);
                                }

                                @Override
                                public void onLoadCleared(
                                        android.graphics.drawable.Drawable placeholder) {}
                            });
        } else {
            headerArtwork.setImageResource(R.drawable.ic_music_note);
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

        // Initial state
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
                        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                    } else {
                        playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                    }
                });
    }

    private void updateMetadata(MediaMetadata metadata) {
        if (metadata == null) return;

        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration > 0) {
            runOnUiThread(
                    () -> {
                        progressSeekBar.setMax((int) duration);
                    });
        }
    }

    private void fetchAndDisplayLyrics() {
        if (lyrics != null && !lyrics.isEmpty()) {
            // Lyrics provided via intent - use directly
            displayLyrics(lyrics);
        } else {
            // No lyrics provided
            Toast.makeText(this, "No lyrics available", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayLyrics(String lyricsText) {
        syncedLyricsView.setLyrics(lyricsText);

        // Setup seek listener
        syncedLyricsView.setSeekListener(
                positionMs -> {
                    if (mediaController != null) {
                        mediaController.getTransportControls().seekTo(positionMs);
                    }
                });

        if (!syncedLyricsView.hasLyrics()) {
            Toast.makeText(this, "No synced timestamps found", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener(
                v -> {
                    if (mediaController != null) {
                        if (isPlaying) {
                            mediaController.getTransportControls().pause();
                        } else {
                            mediaController.getTransportControls().play();
                        }
                    }
                });

        // Adjust timing button
        adjustTimingButton.setOnClickListener(
                v -> {
                    showTimingAdjustmentDialog();
                });

        // Font switch button
        fontSwitchButton.setOnClickListener(
                v -> {
                    String fontName = syncedLyricsView.cycleFont();
                    Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                });

        // SeekBar
        progressSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && mediaController != null) {
                            mediaController.getTransportControls().seekTo(progress);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        isTracking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        isTracking = false;
                    }
                });
    }

    /** Show dialog to adjust lyrics timing */
    private void showTimingAdjustmentDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Adjust Lyrics Timing");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        TextView infoText = new TextView(this);
        infoText.setText(
                "Current offset: " + timingOffset + "ms\n\nAdjust if lyrics are early or late:");
        infoText.setTextSize(16);
        layout.addView(infoText);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(2000); // -1000ms to +1000ms
        seekBar.setProgress((int) (timingOffset + 1000));
        layout.addView(seekBar);

        TextView valueText = new TextView(this);
        valueText.setText(timingOffset + "ms");
        valueText.setTextSize(18);
        valueText.setGravity(android.view.Gravity.CENTER);
        layout.addView(valueText);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        long offset = progress - 1000;
                        valueText.setText(offset + "ms");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        builder.setView(layout);
        builder.setPositiveButton(
                "Apply",
                (dialog, which) -> {
                    timingOffset = seekBar.getProgress() - 1000;
                    Toast.makeText(
                                    this,
                                    "Timing adjusted: " + timingOffset + "ms",
                                    Toast.LENGTH_SHORT)
                            .show();
                });
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton(
                "Reset",
                (dialog, which) -> {
                    timingOffset = 0;
                    Toast.makeText(this, "Timing reset", Toast.LENGTH_SHORT).show();
                });

        builder.show();
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

                                // Apply timing offset
                                long adjustedPosition = position + timingOffset;

                                // Update lyrics view with karaoke animation
                                syncedLyricsView.updatePosition(adjustedPosition);

                                // Update SeekBar
                                if (!isTracking) {
                                    progressSeekBar.setProgress((int) position);
                                }

                                // Update position text
                                positionText.setText(formatTime(position));
                            }
                        }

                        // Update every 50ms for smooth karaoke word-level animation
                        updateHandler.postDelayed(this, 50);
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

        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        if (mediaController != null && mediaControllerCallback != null) {
            try {
                mediaController.unregisterCallback(mediaControllerCallback);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}