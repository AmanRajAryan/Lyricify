package aman.lyricify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.view.ViewAnimationUtils;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
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

    // UI Groups for Immersive Mode
    private MaterialCardView headerCard;
    private LinearLayout controlsLayout;
    private View controlsScrim;

    private FloatingActionButton playPauseButton;
    private FloatingActionButton immersiveButton;
    private MaterialButton prevButton, nextButton;
    private MaterialButton playerChangerButton, fontSwitchButton;

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
    private long timingOffset = 0;

    // Player mode: 0 = Native, 1 = YouLy (Web), 2 = Accompanist (Karaoke)
    private int currentPlayerMode = 0;

    // Immersive State
    private boolean isImmersiveMode = false;

    // Current Song Data (Live State)
    private String title;
    private String artist;
    private String lyrics;

    // --- NEW: Original Entry State ---
    private String originalTitle = "";
    private String originalArtist = "";
    private String originalArtworkUrl;
    private boolean isShowingOriginalArt = true;

    // Flag to capture the initial session as "The Anchor"
    private boolean isFirstMetadataUpdate = true;

    // Player Components
    private LyricsWebViewFragment lyricsWebViewFragment;
    private KaraokeLyricsFragment karaokeLyricsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         // --- KEEP SCREEN ON ---
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        hideSystemUI();
        setContentView(R.layout.activity_synced_lyrics);

        initializeViews();
        extractIntentData(); // Populates UI only

        setupYouLyFragment();
        setupKaraokeFragment();
        setupMediaSession();
        fetchAndDisplayNativeLyrics();

        setupControls();

        updateHandler = new Handler(Looper.getMainLooper());
        startPositionUpdates();
    }

    private void seekToPosition(long timeMs) {
        runOnUiThread(
                () -> {
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

        // Immersive Groups
        headerCard = findViewById(R.id.headerCard);
        controlsLayout = findViewById(R.id.controlsLayout);
        controlsScrim = findViewById(R.id.controlsScrim);

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

        immersiveButton = findViewById(R.id.immersiveButton);

        // Apply Native Blur (Only affects API 31+)
        applyBlurEffect();
    }

    private void applyBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Native RenderEffect for Android 12+ (Safe on A15)
            RenderEffect blurEffect =
                    RenderEffect.createBlurEffect(250f, 150f, Shader.TileMode.CLAMP);

            if (immersiveBackground != null) {
                immersiveBackground.setRenderEffect(blurEffect);
            }
            if (immersiveBackgroundOverlay != null) {
                immersiveBackgroundOverlay.setRenderEffect(blurEffect);
            }
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
                    animateButton(v);
                    if (mediaController != null)
                        mediaController.getTransportControls().skipToPrevious();
                });

        nextButton.setOnClickListener(
                v -> {
                    animateButton(v);
                    if (mediaController != null)
                        mediaController.getTransportControls().skipToNext();
                });

        playerChangerButton.setOnClickListener(v -> togglePlayerView());

        fontSwitchButton.setOnClickListener(
                v -> {
                    if (currentPlayerMode == 0) {
                        String fontName = syncedLyricsView.cycleFont();
                        Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                    } else if (currentPlayerMode == 2 && karaokeLyricsFragment != null) {
                        String fontName = karaokeLyricsFragment.cycleFont();
                        Toast.makeText(this, "Font: " + fontName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(
                                        this,
                                        "Settings available only in Native and Karaoke views",
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        progressSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && mediaController != null)
                            mediaController.getTransportControls().seekTo(progress);
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

        immersiveButton.setOnClickListener(v -> enableImmersiveMode(!isImmersiveMode));
    }

    private void enableImmersiveMode(boolean enable) {
        isImmersiveMode = enable;

        if (enable) {
            headerCard
                    .animate()
                    .alpha(0f)
                    .translationY(-50)
                    .setDuration(300)
                    .withEndAction(() -> headerCard.setVisibility(View.GONE));
            controlsLayout
                    .animate()
                    .alpha(0f)
                    .translationY(50)
                    .setDuration(300)
                    .withEndAction(() -> controlsLayout.setVisibility(View.GONE));
            controlsScrim
                    .animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> controlsScrim.setVisibility(View.GONE));
            immersiveBackgroundOverlay.animate().alpha(0f).setDuration(500);

            animateImmersiveButtonMargin(40);
            immersiveButton.setImageResource(R.drawable.ic_close);
            Toast.makeText(this, "Immersive Mode On", Toast.LENGTH_SHORT).show();

        } else {
            headerCard.setVisibility(View.VISIBLE);
            controlsLayout.setVisibility(View.VISIBLE);
            controlsScrim.setVisibility(View.VISIBLE);

            headerCard.animate().alpha(1f).translationY(0).setDuration(300);
            controlsLayout.animate().alpha(1f).translationY(0).setDuration(300);
            controlsScrim.animate().alpha(1f).setDuration(300);
            immersiveBackgroundOverlay.animate().alpha(1f).setDuration(500);

            animateImmersiveButtonMargin(180);
            immersiveButton.setImageResource(R.drawable.ic_fullscreen);
        }
    }

    private void animateImmersiveButtonMargin(int targetMarginDp) {
        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) immersiveButton.getLayoutParams();

        int currentMargin = params.bottomMargin;
        float density = getResources().getDisplayMetrics().density;
        int targetMargin = (int) (targetMarginDp * density);

        ValueAnimator animator = ValueAnimator.ofInt(currentMargin, targetMargin);
        animator.setDuration(400);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                animation -> {
                    params.bottomMargin = (int) animation.getAnimatedValue();
                    immersiveButton.setLayoutParams(params);
                });
        animator.start();
    }

    // --- METADATA UPDATE & ANCHOR LOGIC ---
    private void updateMetadata(MediaMetadata metadata) {
        if (metadata == null) return;

        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (duration > 0) {
            runOnUiThread(() -> progressSeekBar.setMax((int) duration));
        }

        String newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);

        if (newTitle == null) newTitle = "";
        if (newArtist == null) newArtist = "";

        // --- FIRST RUN: CAPTURE ANCHOR ---
        if (isFirstMetadataUpdate) {
            originalTitle = newTitle;
            originalArtist = newArtist;

            // Sync current state tracking
            title = newTitle;
            artist = newArtist;

            isFirstMetadataUpdate = false;

            // Ensure Player Changer is visible (we are on the correct song)
            runOnUiThread(() -> playerChangerButton.setVisibility(View.VISIBLE));

            return;
        }

        // --- SUBSEQUENT RUNS: CHECK FOR CHANGES ---
        if (!newTitle.equals(title)) {
            title = newTitle;
            artist = newArtist;

            // Song changed: Update UI Text now
            runOnUiThread(
                    () -> {
                        songTitleText.setText(title);
                        songArtistText.setText(artist);
                    });

            // Compare against ANCHOR (the one we started with)
            boolean isOriginalSong =
                    title.trim().equalsIgnoreCase(originalTitle.trim())
                            && artist.trim().equalsIgnoreCase(originalArtist.trim());

            if (isOriginalSong) {
                // --- MATCH (Back to Original) ---
                runOnUiThread(
                        () -> {
                            playerChangerButton.setVisibility(View.VISIBLE);

                            if (!isShowingOriginalArt) {
                                loadArtwork(originalArtworkUrl);
                                isShowingOriginalArt = true;
                            }
                        });

                // Reload Original Lyrics into WebView
                if (lyricsWebViewFragment != null) {
                    long durSeconds = duration / 1000;
                    lyricsWebViewFragment.loadLyrics(title, artist, newAlbum, durSeconds);
                }

            } else {
                // --- MISMATCH (New Song) ---
                Bitmap notifArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (notifArt == null) {
                    notifArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                }
                final Bitmap finalArt = notifArt;

                runOnUiThread(
                        () -> {
                            // Use INVISIBLE to preserve layout spacing
                            playerChangerButton.setVisibility(View.INVISIBLE);

                            if (finalArt != null) {
                                updateArtwork(finalArt);
                                isShowingOriginalArt = false;
                            }

                            if (currentPlayerMode != 1) {
                                switchToWebMode();
                            }
                        });

                if (lyricsWebViewFragment != null) {
                    long durSeconds = duration / 1000;
                    lyricsWebViewFragment.loadLyrics(title, artist, newAlbum, durSeconds);
                }
            }
        }
    }

    private void switchToWebMode() {
        if (currentPlayerMode == 1) return;

        View oldView;
        if (currentPlayerMode == 2) oldView = karaokeContainer;
        else oldView = syncedLyricsView;

        currentPlayerMode = 1;

        immersiveButton.show();
        prevButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.VISIBLE);
        fontSwitchButton.setVisibility(View.INVISIBLE);
        playerChangerButton.setIconResource(R.drawable.ic_layers);

        if (lyricsWebViewFragment != null) {
            lyricsWebViewFragment.displayLyrics();
            lyricsWebViewFragment.setPlaying(isPlaying);
        }
        animateReveal(webViewContainer, oldView);
    }

    private void togglePlayerView() {
        View oldView;
        switch (currentPlayerMode) {
            case 0:
                oldView = syncedLyricsView;
                break;
            case 1:
                oldView = webViewContainer;
                break;
            case 2:
                oldView = karaokeContainer;
                break;
            default:
                oldView = syncedLyricsView;
                break;
        }

        long currentPosition = 0;
        if (mediaController != null) {
            PlaybackState state = mediaController.getPlaybackState();
            if (state != null) currentPosition = state.getPosition() + timingOffset;
        }

        currentPlayerMode = (currentPlayerMode + 1) % 3;

        if (currentPlayerMode == 1) {
            immersiveButton.show();
            prevButton.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);
            playerChangerButton.setIconResource(R.drawable.ic_layers);
            fontSwitchButton.setVisibility(View.INVISIBLE);
            Toast.makeText(this, "YouLy+ Engine", Toast.LENGTH_SHORT).show();
        } else {
            immersiveButton.hide();
            prevButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);
            fontSwitchButton.setVisibility(View.VISIBLE);

            if (isImmersiveMode) enableImmersiveMode(false);

            if (currentPlayerMode == 0) {
                playerChangerButton.setIconResource(R.drawable.ic_swap_horiz);
                Toast.makeText(this, "Native Engine", Toast.LENGTH_SHORT).show();
            } else {
                playerChangerButton.setIconResource(R.drawable.ic_music_note);
                Toast.makeText(this, "Accompanist Engine", Toast.LENGTH_SHORT).show();
            }
        }

        View newView;
        switch (currentPlayerMode) {
            case 0:
                newView = syncedLyricsView;
                syncedLyricsView.updateTime(currentPosition);
                break;
            case 1:
                newView = webViewContainer;
                if (lyricsWebViewFragment != null) {
                    lyricsWebViewFragment.displayLyrics();
                    lyricsWebViewFragment.updateTime(currentPosition);
                    lyricsWebViewFragment.setPlaying(isPlaying);
                }
                break;
            case 2:
                newView = karaokeContainer;
                if (karaokeLyricsFragment != null) {
                    karaokeLyricsFragment.setPlaying(isPlaying);
                    karaokeLyricsFragment.updateTime(currentPosition);
                }
                break;
            default:
                newView = syncedLyricsView;
                break;
        }

        animateReveal(newView, oldView);
    }

    private void animateReveal(View viewToShow, View viewToHide) {
        if (!viewToShow.isAttachedToWindow()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            viewToShow.setVisibility(View.VISIBLE);
            viewToHide.setVisibility(View.GONE);
            return;
        }
        int[] buttonLocation = new int[2];
        int[] viewLocation = new int[2];
        playerChangerButton.getLocationInWindow(buttonLocation);
        viewToShow.getLocationInWindow(viewLocation);
        int cx = buttonLocation[0] - viewLocation[0] + (playerChangerButton.getWidth() / 2);
        int cy = buttonLocation[1] - viewLocation[1] + (playerChangerButton.getHeight() / 2);
        float finalRadius = (float) Math.hypot(viewToShow.getWidth(), viewToShow.getHeight());
        viewToHide.animate().alpha(0f).setDuration(700).start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewToHide.setRenderEffect(
                    RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP));
        }
        Animator anim =
                ViewAnimationUtils.createCircularReveal(viewToShow, cx, cy, 0f, finalRadius);
        anim.setDuration(1000);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        viewToShow.setVisibility(View.VISIBLE);
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        viewToHide.setVisibility(View.GONE);
                        viewToHide.setAlpha(1.0f);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            viewToHide.setRenderEffect(null);
                    }
                });
        anim.start();
    }

    private void extractIntentData() {
        title = getIntent().getStringExtra("SONG_TITLE");
        artist = getIntent().getStringExtra("SONG_ARTIST");
        lyrics = getIntent().getStringExtra("LYRICS");
        originalArtworkUrl = getIntent().getStringExtra("ARTWORK_URL");

        songTitleText.setText(title != null ? title : "Unknown Song");
        songArtistText.setText(artist != null ? artist : "Unknown Artist");

        loadArtwork(originalArtworkUrl);
    }

    // --- UPDATED: Fallback Logic for Artwork Loading ---
    private void loadArtwork(String url) {
        if (url != null && !url.isEmpty()) {
            String formattedUrl =
                    url.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg");

            // Standard load into Header
            Glide.with(this).asBitmap().load(formattedUrl).into(headerArtwork);

            // BLUR LOGIC:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: Just load, RenderEffect handles the blur
                Glide.with(this).load(formattedUrl).into(immersiveBackground);
                Glide.with(this).load(formattedUrl).into(immersiveBackgroundOverlay);
            } else {
                // API 29-30: Use Glide Transformation (Fallback)
                Glide.with(this)
                        .load(formattedUrl)
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 12)))
                        .into(immersiveBackground);
                Glide.with(this)
                        .load(formattedUrl)
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 17)))
                        .into(immersiveBackgroundOverlay);
            }

        } else {
            setPlaceholderArtwork();
        }
    }

    // --- UPDATED: Fallback Logic for Bitmap Updates ---
    private void updateArtwork(Bitmap bitmap) {
        if (bitmap != null) {
            Glide.with(this).asBitmap().load(bitmap).into(headerArtwork);

            // BLUR LOGIC:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: Just load
                Glide.with(this).load(bitmap).into(immersiveBackground);
                Glide.with(this).load(bitmap).into(immersiveBackgroundOverlay);
            } else {
                // API 29-30: Use Glide Transformation (Fallback)
                Glide.with(this)
                        .load(bitmap)
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 12)))
                        .into(immersiveBackground);
                Glide.with(this)
                        .load(bitmap)
                        .apply(RequestOptions.bitmapTransform(new BlurTransformation(25, 17)))
                        .into(immersiveBackgroundOverlay);
            }
        } else {
            setPlaceholderArtwork();
        }
    }

    private void setPlaceholderArtwork() {
        headerArtwork.setImageResource(R.drawable.ic_music_note);
        immersiveBackground.setImageResource(R.drawable.ic_music_note);
        immersiveBackgroundOverlay.setImageResource(R.drawable.ic_music_note);
    }

    private void setupYouLyFragment() {
        lyricsWebViewFragment = new LyricsWebViewFragment();
        lyricsWebViewFragment.setLyricsListener(this::seekToPosition);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.webViewContainer, lyricsWebViewFragment);
        transaction.commit();
    }

    private void setupKaraokeFragment() {
        karaokeLyricsFragment = new KaraokeLyricsFragment();
        karaokeLyricsFragment.setSeekListener(this::seekToPosition);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.karaokeContainer, karaokeLyricsFragment);
        transaction.commit();
    }

    private void fetchAndDisplayNativeLyrics() {
        if (lyrics != null && !lyrics.isEmpty()) {
            syncedLyricsView.setLyrics(lyrics);
            syncedLyricsView.setSeekListener(this::seekToPosition);
            karaokeLyricsFragment.setLyrics(lyrics);
        }
    }

    private void setupMediaSession() {
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        try {
            List<MediaController> controllers =
                    mediaSessionManager.getActiveSessions(
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
                if (playbackState == PlaybackState.STATE_PLAYING
                        || playbackState == PlaybackState.STATE_PAUSED) return controller;
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
        boolean isNowPlaying = (state.getState() == PlaybackState.STATE_PLAYING);
        isPlaying = isNowPlaying;
        if (karaokeLyricsFragment != null) karaokeLyricsFragment.setPlaying(isPlaying);
        if (lyricsWebViewFragment != null) lyricsWebViewFragment.setPlaying(isPlaying);

        runOnUiThread(
                () -> {
                    if (isNowPlaying != lastPlayingState) {
                        int drawableId =
                                isNowPlaying
                                        ? R.drawable.avd_play_to_pause
                                        : R.drawable.avd_pause_to_play;
                        playPauseButton.setImageResource(drawableId);
                        Drawable drawable = playPauseButton.getDrawable();
                        if (drawable instanceof Animatable) ((Animatable) drawable).start();
                        lastPlayingState = isNowPlaying;
                    } else {
                        playPauseButton.setImageResource(
                                isNowPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                    }
                    if (isNowPlaying) progressSeekBar.startAnimation();
                    else progressSeekBar.stopAnimation();
                });
    }

    private void animateButton(View view) {
        view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
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
                                switch (currentPlayerMode) {
                                    case 0:
                                        syncedLyricsView.updateTime(adjustedPosition);
                                        break;
                                    case 1:
                                        if (lyricsWebViewFragment != null)
                                            lyricsWebViewFragment.updateTime(adjustedPosition);
                                        break;
                                    case 2:
                                        if (karaokeLyricsFragment != null)
                                            karaokeLyricsFragment.updateTime(adjustedPosition);
                                        break;
                                }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
        if (mediaController != null && mediaControllerCallback != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
    }
}
