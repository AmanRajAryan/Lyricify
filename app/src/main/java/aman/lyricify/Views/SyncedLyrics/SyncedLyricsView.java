package aman.lyricify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncedLyricsView extends View {

    // ==================================================================================
    // INTERFACES
    // ==================================================================================

    public interface SeekListener {
        void onSeek(long timeMs);
    }

    // ==================================================================================
    // VIEW IMPLEMENTATION
    // ==================================================================================

    private List<LyricLine> lyrics = new ArrayList<>();
    private List<WrappedLine> wrappedLines = new ArrayList<>();
    private Map<LyricLine, Float> lineCenterYMap = new HashMap<>();

    private long currentTime = 0;

    // Paints
    private Paint paintActive, paintDefault, paintPast;
    private Paint paintFps;
    
    // V1 (White) Paints
    private Paint paintFill, paintBloom;
    private LinearGradient masterGradient;
    
    // V2 (Cyan) Paints
    private Paint paintFillV2, paintBloomV2;
    private LinearGradient masterGradientV2;
    private final int COLOR_V2 = Color.parseColor("#00E5FF");

    // Optimization
    private Matrix shaderMatrix = new Matrix();

    // Metrics - LAYOUT at 1.1x, render at 0.9x/1.1x
    private float textHeight;
    private float baseTextSize;
    private static final float LAYOUT_SCALE = 1.1f; // Layout calculations use this
    private static final float INACTIVE_SCALE = 0.9f; // Non-active lines
    private static final float ACTIVE_SCALE = 1.1f; // Active line
    
    private float padding = 48;
    private float spacingBetweenWrappedLines;
    private float spacingBetweenLyrics;

    // Scrolling logic
    private float targetScrollY = 0;
    private float currentScrollY = 0;

    // Physics & Touch
    private boolean isUserScrolling = false;
    private boolean isFlinging = false;
    private float lastTouchY = 0;
    private VelocityTracker velocityTracker;
    private OverScroller scroller;
    private int minFlingVelocity;
    private int maxFlingVelocity;
    private GestureDetector gestureDetector;

    // Listeners
    private SeekListener seekListener;

    // Auto-Scroll Resume
    private Runnable resumeAutoScrollRunnable;
    private static final long AUTO_SCROLL_RESUME_DELAY = 2000;
    private static final long SCROLL_ANTICIPATION_MS = 300;

    // FPS
    private long lastFpsTime = 0;
    private int frameCount = 0;
    private int currentFps = 0;

    // Layout
    private float totalContentHeight = 0;

    // Fonts
    private int currentFontIndex = 0;
    private static final Typeface[] FONTS = {
            Typeface.DEFAULT,
            Typeface.SERIF,
            Typeface.SANS_SERIF,
            Typeface.MONOSPACE,
            Typeface.create("cursive", Typeface.NORMAL),
            Typeface.create("casual", Typeface.NORMAL)
    };
    private static final String[] FONT_NAMES = {
            "Default", "Serif", "Sans Serif", "Monospace", "Cursive", "Casual"
    };
    
    // OPTIMIZATION: Cache word widths (at LAYOUT_SCALE)
    private Map<String, Float> wordWidthCache = new HashMap<>();
    
    // Animation tracking
    private int currentFocusedLineIndex = -1;
    private int nextFocusedLineIndex = -1;

    public SyncedLyricsView(Context context) { this(context, null); }
    public SyncedLyricsView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public SyncedLyricsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().scaledDensity;
        baseTextSize = 32 * density;
        
        // Set paints to LAYOUT_SCALE size for measurements
        float layoutTextSize = baseTextSize * LAYOUT_SCALE;

        spacingBetweenWrappedLines = 10 * density;
        spacingBetweenLyrics = 60 * density;

        // Initial Paint Setup - use LAYOUT_SCALE for all measurements
        paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintActive.setTextSize(layoutTextSize);
        paintActive.setColor(Color.WHITE);
        paintActive.setFakeBoldText(true);

        paintDefault = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDefault.setTextSize(layoutTextSize);
        paintDefault.setColor(Color.argb(102, 255, 255, 255));
        paintDefault.setFakeBoldText(true);

        paintPast = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPast.setTextSize(layoutTextSize);
        paintPast.setColor(Color.argb(80, 255, 255, 255));
        paintPast.setFakeBoldText(true);

        // V1 (White)
        paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFill.setTextSize(layoutTextSize);
        paintFill.setFakeBoldText(true);

        paintBloom = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBloom.setTextSize(layoutTextSize);
        paintBloom.setFakeBoldText(true);
        paintBloom.setShadowLayer(25, 0, 0, Color.WHITE);

        // V2 (Cyan)
        paintFillV2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFillV2.setTextSize(layoutTextSize);
        paintFillV2.setFakeBoldText(true);

        paintBloomV2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBloomV2.setTextSize(layoutTextSize);
        paintBloomV2.setFakeBoldText(true);
        paintBloomV2.setShadowLayer(25, 0, 0, COLOR_V2);

        paintFps = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFps.setColor(Color.GREEN);
        paintFps.setTextSize(18 * density);
        paintFps.setFakeBoldText(true);
        paintFps.setTextAlign(Paint.Align.RIGHT);

        updateTextHeight();

        // Physics
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();

        resumeAutoScrollRunnable = () -> {
            isUserScrolling = false;
            isFlinging = false;
            postInvalidateOnAnimation();
        };

        // Click Detection
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return handleTap(e.getY());
            }
        });
    }

    private void updateTextHeight() {
        Paint.FontMetrics fm = paintActive.getFontMetrics();
        textHeight = fm.descent - fm.ascent;
    }

    // --- PUBLIC API ---

    public void setSeekListener(SeekListener listener) {
        this.seekListener = listener;
    }

    public String cycleFont() {
        currentFontIndex = (currentFontIndex + 1) % FONTS.length;
        Typeface newTypeface = FONTS[currentFontIndex];
        Typeface tf = Typeface.create(newTypeface, Typeface.BOLD);

        float layoutTextSize = baseTextSize * LAYOUT_SCALE;
        
        paintActive.setTypeface(tf);
        paintActive.setTextSize(layoutTextSize);
        paintDefault.setTypeface(tf);
        paintDefault.setTextSize(layoutTextSize);
        paintPast.setTypeface(tf);
        paintPast.setTextSize(layoutTextSize);
        paintFill.setTypeface(tf);
        paintFill.setTextSize(layoutTextSize);
        paintBloom.setTypeface(tf);
        paintBloom.setTextSize(layoutTextSize);
        paintFillV2.setTypeface(tf);
        paintFillV2.setTextSize(layoutTextSize);
        paintBloomV2.setTypeface(tf);
        paintBloomV2.setTextSize(layoutTextSize);

        updateTextHeight();
        wordWidthCache.clear();
        requestLayout();
        invalidate();

        return FONT_NAMES[currentFontIndex];
    }

    public void setLyrics(String lyricsText) {
        if (lyricsText == null || lyricsText.isEmpty()) return;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(lyricsText.getBytes(StandardCharsets.UTF_8));
        List<LyricLine> parsedLines = LrcParser.parse(inputStream);
        setLyrics(parsedLines);
    }

    public void setLyrics(List<LyricLine> lyrics) {
        this.lyrics = lyrics;
        wordWidthCache.clear();
        currentFocusedLineIndex = -1;
        nextFocusedLineIndex = -1;
        requestLayout();
        invalidate();
    }

    public void updateTime(long timeMs) {
        this.currentTime = timeMs;
        postInvalidateOnAnimation();
    }

    // --- INTERNAL LOGIC ---

    private boolean handleTap(float touchY) {
        if (seekListener == null || wrappedLines.isEmpty()) return false;

        float clickedContentY = touchY + currentScrollY;

        Paint.FontMetrics fm = paintActive.getFontMetrics();
        float ascent = fm.ascent;
        float descent = fm.descent;
        float verticalPadding = 30f;

        for (WrappedLine wl : wrappedLines) {
            float top = wl.y + ascent - verticalPadding;
            float bottom = wl.y + descent + verticalPadding;

            if (clickedContentY >= top && clickedContentY <= bottom) {
                seekListener.onSeek(wl.parentLine.startTime);
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                return true;
            }
        }
        return false;
    }

    private boolean updateScrollLogic() {
        if (isFlinging) {
            if (scroller.computeScrollOffset()) {
                currentScrollY = scroller.getCurrY();
                return true;
            } else {
                isFlinging = false;
                postDelayed(resumeAutoScrollRunnable, AUTO_SCROLL_RESUME_DELAY);
            }
        }

        if (isUserScrolling || isFlinging) {
            return false;
        }

        if (!lyrics.isEmpty()) {
            int scrollTargetIndex = -1;
            for (int i = 0; i < lyrics.size(); i++) {
                if (currentTime >= lyrics.get(i).startTime) {
                    scrollTargetIndex = i;
                } else {
                    break;
                }
            }

            int effectiveIndex = Math.max(0, scrollTargetIndex);
            currentFocusedLineIndex = effectiveIndex;
            
            LyricLine currentLine = lyrics.get(effectiveIndex);

            Float centerY = lineCenterYMap.get(currentLine);
            if (centerY == null) centerY = 0f;

            float desiredY = centerY - getHeight() / 2f;

            if (effectiveIndex + 1 < lyrics.size()) {
                LyricLine nextLine = lyrics.get(effectiveIndex + 1);
                long timeUntilNext = nextLine.startTime - currentTime;

                if (timeUntilNext < SCROLL_ANTICIPATION_MS && timeUntilNext > 0) {
                    nextFocusedLineIndex = effectiveIndex + 1;
                    float ratio = 1f - ((float) timeUntilNext / SCROLL_ANTICIPATION_MS);
                    Float nextCenterY = lineCenterYMap.get(nextLine);
                    if (nextCenterY != null) {
                        float nextTargetY = nextCenterY - getHeight() / 2f;
                        desiredY = desiredY + (nextTargetY - desiredY) * ratio;
                    }
                } else {
                    nextFocusedLineIndex = -1;
                }
            } else {
                nextFocusedLineIndex = -1;
            }

            float minScroll = -getHeight() / 2f;
            targetScrollY = Math.max(minScroll, desiredY);
        }

        float diff = targetScrollY - currentScrollY;
        if (Math.abs(diff) > 0.5f) {
            currentScrollY += diff * 0.08f;
            return true;
        } else {
            currentScrollY = targetScrollY;
            return false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0 && !lyrics.isEmpty()) wrapLines(width);
        int contentHeight = (int) (totalContentHeight + getResources().getDisplayMetrics().heightPixels / 2);
        setMeasuredDimension(width, contentHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            masterGradient = new LinearGradient(
                    0, 0, 100, 0,
                    new int[]{Color.WHITE, Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP
            );
            masterGradientV2 = new LinearGradient(
                    0, 0, 100, 0,
                    new int[]{COLOR_V2, Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP
            );
        }
    }

    // Measure at LAYOUT_SCALE (1.1x)
    private float getWordWidth(String text) {
        Float cached = wordWidthCache.get(text);
        if (cached != null) return cached;
        
        float width = paintActive.measureText(text);
        wordWidthCache.put(text, width);
        return width;
    }

    private void wrapLines(int viewWidth) {
        wrappedLines.clear();
        lineCenterYMap.clear();

        float maxWidth = viewWidth - (padding * 2);
        if (maxWidth <= 0) return;

        float currentY = 0;
        LyricLine previousParent = null;

        for (LyricLine line : lyrics) {
            List<LyricWord> currentLineWords = new ArrayList<>();
            float currentLineWidth = 0;
            long segmentStartTime = line.startTime;

            float parentStartY = -1;
            float parentLastLineY = -1;

            int i = 0;
            while (i < line.words.size()) {
                List<LyricWord> cluster = new ArrayList<>();
                float clusterWidth = 0;

                LyricWord firstPiece = line.words.get(i);
                cluster.add(firstPiece);
                clusterWidth += getWordWidth(firstPiece.text);
                i++;

                while (i < line.words.size()) {
                    LyricWord lastAdded = cluster.get(cluster.size() - 1);
                    if (lastAdded.text.endsWith(" ") || lastAdded.text.endsWith("\u3000")) {
                        break;
                    }

                    LyricWord nextPiece = line.words.get(i);
                    cluster.add(nextPiece);
                    clusterWidth += getWordWidth(nextPiece.text);
                    i++;
                }

                if (currentLineWidth + clusterWidth > maxWidth && !currentLineWords.isEmpty()) {
                    float spacing = (previousParent == line) ? spacingBetweenWrappedLines : spacingBetweenLyrics;
                    if (previousParent == null) spacing = 0;
                    currentY += spacing;

                    if (parentStartY == -1) parentStartY = currentY;

                    WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));
                    wl.y = currentY;
                    wl.startTime = segmentStartTime;
                    wrappedLines.add(wl);

                    parentLastLineY = currentY;
                    currentY += textHeight;
                    previousParent = line;

                    segmentStartTime = cluster.get(0).time;
                    currentLineWords.clear();
                    currentLineWidth = 0;
                }

                currentLineWords.addAll(cluster);
                currentLineWidth += clusterWidth;
            }

            if (!currentLineWords.isEmpty()) {
                float spacing = (previousParent == line) ? spacingBetweenWrappedLines : spacingBetweenLyrics;
                if (previousParent == null) spacing = 0;
                currentY += spacing;

                if (parentStartY == -1) parentStartY = currentY;

                WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));
                wl.y = currentY;
                wl.startTime = segmentStartTime;
                wrappedLines.add(wl);

                parentLastLineY = currentY;
                currentY += textHeight;
                previousParent = line;
            }

            if (parentStartY != -1 && parentLastLineY != -1) {
                float centerY = (parentStartY + parentLastLineY) / 2f;
                lineCenterYMap.put(line, centerY);
            }
        }
        totalContentHeight = currentY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long now = System.currentTimeMillis();
        frameCount++;
        if (now - lastFpsTime >= 1000) {
            currentFps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
        }

        if (lyrics.isEmpty() || wrappedLines.isEmpty()) return;

        boolean animatingScroll = updateScrollLogic();

        canvas.save();
        canvas.translate(0, -currentScrollY);

        boolean animatingGlow = false;
        
        float viewTop = currentScrollY - textHeight;
        float viewBottom = currentScrollY + getHeight();
        
        // Calculate transition ratio
        float transitionRatio = 0f;
        if (nextFocusedLineIndex != -1 && currentFocusedLineIndex != -1 && nextFocusedLineIndex < lyrics.size()) {
            LyricLine nextLine = lyrics.get(nextFocusedLineIndex);
            long timeUntilNext = nextLine.startTime - currentTime;
            if (timeUntilNext < SCROLL_ANTICIPATION_MS && timeUntilNext > 0) {
                transitionRatio = 1f - ((float) timeUntilNext / SCROLL_ANTICIPATION_MS);
            }
        }

        for (WrappedLine wl : wrappedLines) {
            float y = wl.y;

            if (y > viewBottom || y < viewTop) {
                continue;
            }

            boolean isActiveLine = (currentTime >= wl.parentLine.startTime && currentTime <= wl.parentLine.endTime);
            boolean isPastLine = (currentTime > wl.parentLine.endTime);
            
            int lineIndex = lyrics.indexOf(wl.parentLine);
            boolean isCurrentFocused = (lineIndex == currentFocusedLineIndex);
            boolean isNextFocused = (lineIndex == nextFocusedLineIndex);
            
            // Calculate scale: 0.9x for inactive, 1.1x for active, animate between
            float renderScale = INACTIVE_SCALE / LAYOUT_SCALE; // 0.9/1.1 = ~0.818
            if (isCurrentFocused && nextFocusedLineIndex != -1) {
                // Current line: 1.0 → 0.818 during transition
                renderScale = 1.0f - ((1.0f - INACTIVE_SCALE / LAYOUT_SCALE) * transitionRatio);
            } else if (isNextFocused && transitionRatio > 0) {
                // Next line: 0.818 → 1.0 during transition
                renderScale = (INACTIVE_SCALE / LAYOUT_SCALE) + ((1.0f - INACTIVE_SCALE / LAYOUT_SCALE) * transitionRatio);
            } else if (isCurrentFocused) {
                // Fully focused: 1.0 (since layout is at 1.1x)
                renderScale = 1.0f;
            }
            
            // Calculate color fade for past lines
            float fadeAlpha = 1.0f;
            if (isPastLine) {
                if (isCurrentFocused && nextFocusedLineIndex != -1 && transitionRatio > 0) {
                    // Current focused line that just finished: fade during transition
                    fadeAlpha = 1.0f - transitionRatio;
                } else if (!isCurrentFocused) {
                    // Already past and not focused: should be grey
                    fadeAlpha = 0.0f;
                }
            }

            float x = padding;
            
            // Scale entire line together
            canvas.save();
            canvas.scale(renderScale, renderScale, x, y);
            
            for (LyricWord word : wl.words) {
                float wordWidth = getWordWidth(word.text);

                if (isActiveLine && currentTime >= word.time) {
                    animatingGlow = true;
                    drawActiveWord(canvas, word, wl, x, y, wordWidth);
                } else if (isPastLine) {
                    // Animate color from white to grey
                    if (fadeAlpha < 1.0f) {
                        int alpha = (int) (80 + (255 - 80) * fadeAlpha);
                        paintPast.setAlpha(alpha);
                        canvas.drawText(word.text, x, y, paintPast);
                        paintPast.setAlpha(255);
                    } else {
                        // Stay white until transition starts
                        canvas.drawText(word.text, x, y, paintActive);
                    }
                } else {
                    canvas.drawText(word.text, x, y, paintDefault);
                }
                x += wordWidth;
            }
            
            canvas.restore();
        }
        canvas.restore();

        canvas.drawText(String.valueOf(currentFps), getWidth() - 50, 100, paintFps);

        if (animatingScroll || animatingGlow || transitionRatio > 0) {
            postInvalidateOnAnimation();
        }
    }

    private void drawActiveWord(Canvas canvas, LyricWord word, WrappedLine wl, float x, float y, float wordWidth) {
        boolean isV2 = (wl.parentLine.vocalType == 2);
        Paint targetFill = isV2 ? paintFillV2 : paintFill;
        Paint targetBloom = isV2 ? paintBloomV2 : paintBloom;
        LinearGradient targetGrad = isV2 ? masterGradientV2 : masterGradient;
        
        long nextWordTime = Long.MAX_VALUE;
        int wordIndex = wl.parentLine.words.indexOf(word);

        if (wordIndex < wl.parentLine.words.size() - 1) {
            nextWordTime = wl.parentLine.words.get(wordIndex + 1).time;
        } else {
            nextWordTime = wl.parentLine.endTime;
        }

        long duration = nextWordTime - word.time;
        if (duration <= 0) duration = 1;

        long elapsed = currentTime - word.time;
        float progress = Math.min(1.0f, (float) elapsed / duration);

        canvas.drawText(word.text, x, y, paintDefault);

        if (targetGrad != null) {
            float edgeWidth = 120f;
            float currentX = x + (wordWidth + edgeWidth) * progress;

            shaderMatrix.reset();
            shaderMatrix.setScale(edgeWidth / 100f, 1f);
            shaderMatrix.postTranslate(currentX - edgeWidth, 0);
            targetGrad.setLocalMatrix(shaderMatrix);

            targetFill.setShader(targetGrad);
            canvas.drawText(word.text, x, y, targetFill);

            if (progress < 1.0f) {
                float bloomAlpha = 1.0f;
                if (progress >= 0.8f) bloomAlpha = (1.0f - progress) / 0.2f;

                targetBloom.setShader(targetGrad);
                targetBloom.setAlpha((int)(255 * bloomAlpha));
                canvas.drawText(word.text, x, y, targetBloom);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isTap = gestureDetector.onTouchEvent(event);

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isUserScrolling = true;
                isFlinging = false;
                scroller.forceFinished(true);
                removeCallbacks(resumeAutoScrollRunnable);
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - lastTouchY;
                currentScrollY -= deltaY;
                float maxScroll = Math.max(0, totalContentHeight + (getHeight() / 2f));
                currentScrollY = Math.max(0, Math.min(currentScrollY, maxScroll));
                lastTouchY = event.getY();
                postInvalidateOnAnimation();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isTap) {
                    isUserScrolling = false;
                    removeCallbacks(resumeAutoScrollRunnable);
                    postInvalidateOnAnimation();
                } else {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    float velocityY = velocityTracker.getYVelocity();

                    if (Math.abs(velocityY) > minFlingVelocity) {
                        isFlinging = true;
                        scroller.fling(0, (int)currentScrollY, 0, (int)-velocityY,
                                0, 0,
                                (int)(-getHeight()/2f), (int)(totalContentHeight + getHeight()/2f));
                        postInvalidateOnAnimation();
                    } else {
                        postDelayed(resumeAutoScrollRunnable, AUTO_SCROLL_RESUME_DELAY);
                    }
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private static class WrappedLine {
        LyricLine parentLine;
        List<LyricWord> words;
        float y;
        long startTime;

        WrappedLine(LyricLine parentLine, List<LyricWord> words) {
            this.parentLine = parentLine;
            this.words = words;
        }
    }
}