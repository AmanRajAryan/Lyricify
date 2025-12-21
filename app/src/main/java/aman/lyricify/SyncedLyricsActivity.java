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

public class SyncedLyricsActivity extends AppCompatActivity {

    private SyncedLyricsView syncedLyricsView;
    private ImageView headerArtwork;
    private TextView songTitleText;
    private TextView songArtistText;
    private TextView positionText;
    private ImageView playPauseButton;
    private SeekBar progressSeekBar;
    private Button adjustTimingButton;
    private Button fontSwitchButton;

    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaControllerCallback;

    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isTracking = false;
    private boolean isPlaying = false;

    private long timingOffset = 0;

    private String title;
    private String artist;
    private String lyrics;
    private String artworkUrl;

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
        adjustTimingButton = findViewById(R.id.adjustTimingButton);
        fontSwitchButton = findViewById(R.id.fontSwitchButton);
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
            String formattedUrl =
                    artworkUrl.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg");

            com.bumptech.glide.Glide.with(this)
                    .asBitmap()
                    .load(formattedUrl)
                    .into(headerArtwork);
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
        updatePlaybackState(controller.getPlaybackState());
        updateMetadata(controller.getMetadata());
    }

    private void updatePlaybackState(PlaybackState state) {
        if (state == null) return;
        int playbackState = state.getState();
        isPlaying = (playbackState == PlaybackState.STATE_PLAYING);

        runOnUiThread(() -> {
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
            runOnUiThread(() -> progressSeekBar.setMax((int) duration));
        }
    }

    private void fetchAndDisplayLyrics() {
        if (lyrics != null && !lyrics.isEmpty()) {
            syncedLyricsView.setLyrics(lyrics);
            
            // --- NEW: Line Click to Seek ---
            syncedLyricsView.setSeekListener(timeMs -> {
                if (mediaController != null) {
                    mediaController.getTransportControls().seekTo(timeMs);
                    Toast.makeText(this, "Seek to " + formatTime(timeMs), Toast.LENGTH_SHORT).show();
                }
            });
            
        } else {
            Toast.makeText(this, "No lyrics available", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupControls() {
        playPauseButton.setOnClickListener(v -> {
            if (mediaController != null) {
                if (isPlaying) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
            }
        });

        adjustTimingButton.setOnClickListener(v -> showTimingAdjustmentDialog());

        // --- NEW: Font Switcher ---
        fontSwitchButton.setOnClickListener(v -> {
            String fontName = syncedLyricsView.cycleFont();
            Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
        });

        progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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

    private void showTimingAdjustmentDialog() {
        // (Keep your existing dialog code here)
        // I omitted it to keep the answer concise, but DO NOT DELETE IT from your file.
        // It's the exact same logic as before.
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Adjust Lyrics Timing");
        // ... rest of dialog logic ...
        builder.show();
    }

    private void startPositionUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaController != null) {
                    PlaybackState state = mediaController.getPlaybackState();
                    if (state != null) {
                        long position = state.getPosition();
                        long adjustedPosition = position + timingOffset;
                        
                        syncedLyricsView.updateTime(adjustedPosition);

                        if (!isTracking) {
                            progressSeekBar.setProgress((int) position);
                        }
                        positionText.setText(formatTime(position));
                    }
                }
                // 16ms ~ 60fps updates
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (mediaController != null && mediaControllerCallback != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
    }
}
