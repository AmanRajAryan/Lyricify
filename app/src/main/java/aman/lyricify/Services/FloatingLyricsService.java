package aman.lyricify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator; // Springy effect
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import aman.youly.LyricsSharedEngine; 
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class FloatingLyricsService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private ImageView bubbleView;
    private View expandedContainer;
    private LinearLayout windowHeader;
    private ImageView closeButton, openAppButton;
    private FrameLayout webContainer;
    private TextView titleText;
    private WindowManager.LayoutParams params;

    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    
    private String currentTitle, currentArtist, currentUrl, currentLyrics;
    private boolean isExpanded = false;
    
    // Sizes
    private int bubbleSizePx;
    private int expandedWidthPx;
    private int expandedHeightPx;
    private int lastBubbleX, lastBubbleY;

    // Use Overshoot for the "Spring" pop effect
    private final OvershootInterpolator springInterpolator = new OvershootInterpolator(1.2f);

    @Override
    public void onCreate() {
        super.onCreate();
        initializeWindow();
        initializeMediaSession();
    }

    private void initializeWindow() {
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(this, R.style.AppTheme);
        floatingView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.service_floating_lyrics, null);
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        bubbleSizePx = (int) (60 * metrics.density);
        expandedWidthPx = (int) (320 * metrics.density);
        expandedHeightPx = (int) (450 * metrics.density);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 200;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);

        bubbleView = floatingView.findViewById(R.id.bubbleView);
        expandedContainer = floatingView.findViewById(R.id.expandedContainer);
        webContainer = floatingView.findViewById(R.id.floatingWebContainer);
        closeButton = floatingView.findViewById(R.id.btnMinimize);
        openAppButton = floatingView.findViewById(R.id.btnOpenApp);
        titleText = floatingView.findViewById(R.id.songTitleOverlay);
        windowHeader = floatingView.findViewById(R.id.windowHeader);

        setupBubbleTouch();
        if (windowHeader != null) setupHeaderDrag(windowHeader);
        
        closeButton.setOnClickListener(v -> collapseView());
        openAppButton.setOnClickListener(v -> returnToApp());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentTitle = intent.getStringExtra("SONG_TITLE");
            currentArtist = intent.getStringExtra("SONG_ARTIST");
            currentUrl = intent.getStringExtra("ARTWORK_URL");
            currentLyrics = intent.getStringExtra("LYRICS");
            
            if (currentTitle != null) titleText.setText(currentTitle);
            if (currentUrl != null) {
                Glide.with(this)
                     .load(currentUrl.replace("{w}", "200").replace("{h}", "200"))
                     .apply(RequestOptions.circleCropTransform())
                     .into(bubbleView);
            }
            loadLyricsToWebView();
        }
        return START_STICKY;
    }

    private void attachWebView() {
        WebView webView = LyricsSharedEngine.getInstance(this).getWebView();
        if (webView == null) return;
        if (webView.getParent() != null) ((ViewGroup) webView.getParent()).removeView(webView);
        webContainer.removeAllViews();
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setVisibility(View.VISIBLE);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.onResume();
    }

    private void loadLyricsToWebView() {
        String safeTitle = currentTitle != null ? currentTitle.replace("'", "\\'") : "";
        String safeArtist = currentArtist != null ? currentArtist.replace("'", "\\'") : "";
        long duration = 300; 
        if(mediaController != null && mediaController.getMetadata() != null) {
             duration = mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000;
        }
        String js = String.format("if(window.AndroidAPI) window.AndroidAPI.loadSong('%s', '%s', '', %d);", safeTitle, safeArtist, duration);
        WebView wv = LyricsSharedEngine.getInstance(this).getWebView();
        if(wv != null) wv.evaluateJavascript(js, null);
    }
    
    private void setupBubbleTouch() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception e) {}
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) isClick = false;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) expandView();
                        return true;
                }
                return false;
            }
        });
    }

    private void setupHeaderDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception e) {}
                        return true;
                }
                return false;
            }
        });
    }

    // ================= SMOOTH ANIMATION LOGIC =================

    private void expandView() {
        if (isExpanded) return;
        isExpanded = true;
        
        // 1. Save Bubble Position
        lastBubbleX = params.x;
        lastBubbleY = params.y;

        // 2. Resize Window to MAX immediately (Invisible Canvas trick)
        params.width = expandedWidthPx;
        params.height = expandedHeightPx;
        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception e) {}

        // 3. Prepare Views
        bubbleView.setVisibility(View.GONE);
        expandedContainer.setVisibility(View.VISIBLE);
        
        // Setup content
        attachWebView();
        syncWebViewState();

        // 4. Animate Content (Scale Up) - GPU Accelerated
        expandedContainer.setPivotX(0); // Pivot from top-left (or adjust based on logic)
        expandedContainer.setPivotY(0);
        expandedContainer.setScaleX(0.2f); // Start small
        expandedContainer.setScaleY(0.2f);
        expandedContainer.setAlpha(0f);    // Start transparent

        expandedContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(springInterpolator) // Springy pop
                .setListener(null) // Clear old listeners
                .start();
    }

    private void collapseView() {
        if (!isExpanded) return;
        isExpanded = false;

        // 1. Animate Content (Scale Down)
        expandedContainer.setPivotX(0);
        expandedContainer.setPivotY(0);

        expandedContainer.animate()
                .scaleX(0.2f)
                .scaleY(0.2f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 2. AFTER animation, resize Window back to Bubble
                        expandedContainer.setVisibility(View.GONE);
                        bubbleView.setVisibility(View.VISIBLE);
                        
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.x = lastBubbleX; // Restore position
                        params.y = lastBubbleY;
                        
                        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception e) {}
                    }
                })
                .start();
    }
    
    private void syncWebViewState() {
        WebView wv = LyricsSharedEngine.getInstance(this).getWebView();
        if (wv != null && mediaController != null) {
            PlaybackState state = mediaController.getPlaybackState();
            if (state != null) {
                long pos = state.getPosition();
                boolean isPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                wv.evaluateJavascript("if(window.AndroidAPI) { window.AndroidAPI.setPlaying(" + isPlaying + "); window.AndroidAPI.updateTime(" + pos + "); }", null);
            }
        }
    }

    private void returnToApp() {
        Intent intent = new Intent(this, SyncedLyricsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("SONG_TITLE", currentTitle);
        intent.putExtra("SONG_ARTIST", currentArtist);
        intent.putExtra("ARTWORK_URL", currentUrl);
        intent.putExtra("LYRICS", currentLyrics);
        startActivity(intent);
        stopSelf();
    }

    private void initializeMediaSession() {
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        startPositionUpdates();
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(new ComponentName(this, SongNotificationListener.class));
            if (!controllers.isEmpty()) registerController(controllers.get(0));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void registerController(MediaController controller) {
        mediaController = controller;
        mediaController.registerCallback(new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (state == null) return;
                boolean isPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                WebView wv = LyricsSharedEngine.getInstance(FloatingLyricsService.this).getWebView();
                if(wv != null) wv.evaluateJavascript("if(window.AndroidAPI) window.AndroidAPI.setPlaying(" + isPlaying + ")", null);
            }
        });
    }

    private void startPositionUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isExpanded && mediaController != null) {
                    PlaybackState state = mediaController.getPlaybackState();
                    if (state != null) {
                        long pos = state.getPosition();
                        WebView wv = LyricsSharedEngine.getInstance(FloatingLyricsService.this).getWebView();
                        if(wv != null) wv.evaluateJavascript("if(window.AndroidAPI) window.AndroidAPI.updateTime(" + pos + ")", null);
                    }
                }
                updateHandler.postDelayed(this, 100);
            }
        };
        updateHandler.post(updateRunnable);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        if (webContainer != null) webContainer.removeAllViews();
        if (updateHandler != null) updateHandler.removeCallbacks(updateRunnable);
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
