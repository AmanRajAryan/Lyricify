package aman.lyricify;

import android.content.Context;
import android.graphics.BlurMaskFilter;
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

    public interface SeekListener {
        void onSeek(long timeMs);
    }

    private List<LyricLine> lyrics = new ArrayList<>();
    private List<WrappedLine> wrappedLines = new ArrayList<>();
    private Map<LyricLine, Float> lineCenterYMap = new HashMap<>();
    private Map<LyricLine, Float> lineScrollYMap = new HashMap<>();

    private long currentTime = 0;

    private Paint paintActive, paintDefault, paintPast;
    private Paint paintFps;

    private Paint paintFill, paintBloom;
    private LinearGradient masterGradient;

    private Paint paintFillV2, paintBloomV2;
    private LinearGradient masterGradientV2;
    private final int COLOR_V2 = Color.parseColor("#00E5FF");

    private Matrix shaderMatrix = new Matrix();
    private BlurMaskFilter bgBlurFilter;

    private float textHeight;
    private float baseTextSize;
    private static final float LAYOUT_SCALE = 1.1f;
    private static final float INACTIVE_SCALE = 0.9f;

    // --- BG VOCAL CONFIG ---
    private static final float BG_SCALE_SIZE = 0.85f;
    private static final float BG_HORIZONTAL_STRETCH = 1.25f;

    private float padding = 48;
    private float spacingBetweenWrappedLines;
    private float spacingBetweenLyrics;

    private float targetScrollY = 0;
    private float currentScrollY = 0;
    private float minScrollY = 0;
    private float maxScrollY = 0;

    private boolean isUserScrolling = false;
    private boolean isFlinging = false;
    private float lastTouchY = 0;
    private VelocityTracker velocityTracker;
    private OverScroller scroller;
    private int minFlingVelocity;
    private int maxFlingVelocity;
    private GestureDetector gestureDetector;

    private SeekListener seekListener;
    private Runnable resumeAutoScrollRunnable;
    private static final long AUTO_SCROLL_RESUME_DELAY = 2500;
    private static final long SCROLL_ANTICIPATION_MS = 600;
    private static final long DECAY_DURATION_MS = 400;

    private long lastFpsTime = 0;
    private int frameCount = 0;
    private int currentFps = 0;
    private float totalContentHeight = 0;

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

    private Map<String, Float> wordWidthCache = new HashMap<>();

    public SyncedLyricsView(Context context) {
        this(context, null);
    }

    public SyncedLyricsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SyncedLyricsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().scaledDensity;
        baseTextSize = 32 * density;
        float layoutTextSize = baseTextSize * LAYOUT_SCALE;

        spacingBetweenWrappedLines = 10 * density;
        spacingBetweenLyrics = 60 * density;

        // MODIFIED: Increased blur radius for "strengthy" effect (approx 3x stronger than before)
        bgBlurFilter = new BlurMaskFilter(5f * density, BlurMaskFilter.Blur.NORMAL);

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

        paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFill.setTextSize(layoutTextSize);
        paintFill.setFakeBoldText(true);

        paintBloom = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBloom.setTextSize(layoutTextSize);
        paintBloom.setFakeBoldText(true);
        paintBloom.setShadowLayer(25, 0, 0, Color.WHITE);

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

        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();

        resumeAutoScrollRunnable =
                () -> {
                    isUserScrolling = false;
                    isFlinging = false;
                    postInvalidateOnAnimation();
                };

        gestureDetector =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                return handleTap(e.getY());
                            }
                        });

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void updateTextHeight() {
        Paint.FontMetrics fm = paintActive.getFontMetrics();
        textHeight = fm.descent - fm.ascent;
    }

    public void setSeekListener(SeekListener listener) {
        this.seekListener = listener;
    }

    public String cycleFont() {
        currentFontIndex = (currentFontIndex + 1) % FONTS.length;
        Typeface tf = Typeface.create(FONTS[currentFontIndex], Typeface.BOLD);
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
        ByteArrayInputStream is =
                new ByteArrayInputStream(lyricsText.getBytes(StandardCharsets.UTF_8));
        setLyrics(LrcParser.parse(is));
    }

    public void setLyrics(List<LyricLine> lyrics) {
        this.lyrics = lyrics;
        wordWidthCache.clear();
        requestLayout();
        invalidate();
    }

    public void updateTime(long timeMs) {
        this.currentTime = timeMs;
        postInvalidateOnAnimation();
    }

    private boolean handleTap(float touchY) {
        if (seekListener == null || wrappedLines.isEmpty()) return false;
        float clickedContentY = touchY + currentScrollY;
        Paint.FontMetrics fm = paintActive.getFontMetrics();
        float verticalPadding = 30f;
        for (WrappedLine wl : wrappedLines) {
            float top = wl.y + fm.ascent - verticalPadding;
            float bottom = wl.y + fm.descent + verticalPadding;
            if (clickedContentY >= top && clickedContentY <= bottom) {
                if (wl.parentLine.startTime != -1) {
                    seekListener.onSeek(wl.parentLine.startTime);
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean updateScrollLogic() {
        if (isFlinging) {
            if (scroller.computeScrollOffset()) {
                currentScrollY = scroller.getCurrY();
                currentScrollY = Math.max(minScrollY, Math.min(currentScrollY, maxScrollY));
                return true;
            } else {
                isFlinging = false;
                postDelayed(resumeAutoScrollRunnable, AUTO_SCROLL_RESUME_DELAY);
            }
        }
        if (isUserScrolling || isFlinging) return false;

        if (!lyrics.isEmpty()) {
            int effectiveIndex = -1;
            for (int i = 0; i < lyrics.size(); i++) {
                if (currentTime >= lyrics.get(i).startTime) effectiveIndex = i;
                else break;
            }
            effectiveIndex = Math.max(0, effectiveIndex);
            LyricLine currentLine = lyrics.get(effectiveIndex);

            if (currentLine.startTime == -1) return false;

            Float preCalcTarget = lineScrollYMap.get(currentLine);
            if (preCalcTarget == null) preCalcTarget = 0f;
            float desiredY = preCalcTarget - getHeight() / 2f;

            if (effectiveIndex + 1 < lyrics.size()) {
                LyricLine nextLine = lyrics.get(effectiveIndex + 1);
                if (nextLine.startTime != -1) {
                    long timeUntilNext = nextLine.startTime - currentTime;
                    if (timeUntilNext < SCROLL_ANTICIPATION_MS && timeUntilNext > 0) {
                        float ratio = 1f - ((float) timeUntilNext / SCROLL_ANTICIPATION_MS);
                        Float nextPreCalcTarget = lineScrollYMap.get(nextLine);
                        if (nextPreCalcTarget != null) {
                            float nextTargetY = nextPreCalcTarget - getHeight() / 2f;
                            desiredY = desiredY + (nextTargetY - desiredY) * ratio;
                        }
                    }
                }
            }
            targetScrollY = Math.max(minScrollY, Math.min(desiredY, maxScrollY));
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
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (width > 0 && !lyrics.isEmpty()) {
            wrapLines(width);
            updateScrollBounds(height);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            masterGradient =
                    new LinearGradient(
                            0,
                            0,
                            100,
                            0,
                            new int[] {Color.WHITE, Color.TRANSPARENT},
                            null,
                            Shader.TileMode.CLAMP);
            masterGradientV2 =
                    new LinearGradient(
                            0,
                            0,
                            100,
                            0,
                            new int[] {COLOR_V2, Color.TRANSPARENT},
                            null,
                            Shader.TileMode.CLAMP);
        }
        updateScrollBounds(h);
    }

    private void updateScrollBounds(int viewHeight) {
        if (totalContentHeight <= 0) {
            minScrollY = 0;
            maxScrollY = 0;
            return;
        }
        minScrollY = -viewHeight / 2f;
        maxScrollY = totalContentHeight - viewHeight / 2f;
        if (maxScrollY < minScrollY) maxScrollY = minScrollY;
    }

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
        lineScrollYMap.clear();

        float maxAllowedWidth = viewWidth - (padding * 2);
        if (maxAllowedWidth <= 0) return;

        float currentY = 0;
        LyricLine previousParent = null;

        // REMOVED: float bgIndent = padding * 2;

        for (int lineIdx = 0; lineIdx < lyrics.size(); lineIdx++) {
            LyricLine line = lyrics.get(lineIdx);
            long nextStartTime = -1;
            if (lineIdx + 1 < lyrics.size()) nextStartTime = lyrics.get(lineIdx + 1).startTime;

            List<LyricWord> currentLineWords = new ArrayList<>();
            float currentLineWidth = 0;
            float parentStartY = -1;
            float parentLastLineY = -1;
            float effectiveMeasureScale =
                    line.isBackground ? (BG_SCALE_SIZE * BG_HORIZONTAL_STRETCH) : 1.0f;

            int i = 0;
            while (i < line.words.size()) {
                List<LyricWord> cluster = new ArrayList<>();
                float clusterWidth = 0;

                LyricWord firstPiece = line.words.get(i);
                cluster.add(firstPiece);
                clusterWidth += getWordWidth(firstPiece.text) * effectiveMeasureScale;
                i++;

                while (i < line.words.size()) {
                    LyricWord lastAdded = cluster.get(cluster.size() - 1);

                    // MODIFIED: Break on hyphen ("-") to fix clustering issue
                    if (lastAdded.text.endsWith(" ")
                            || lastAdded.text.endsWith("\u3000")
                            || lastAdded.text.endsWith("-")) {
                        break;
                    }

                    LyricWord nextPiece = line.words.get(i);
                    cluster.add(nextPiece);
                    clusterWidth += getWordWidth(nextPiece.text) * effectiveMeasureScale;
                    i++;
                }

                if (currentLineWidth + clusterWidth > maxAllowedWidth
                        && !currentLineWords.isEmpty()) {
                    WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));

                    // MODIFIED: Center justify background lines
                    if (line.isBackground) {
                        wl.xOffset = (viewWidth - currentLineWidth) / 2f - padding;
                    } else {
                        wl.xOffset = 0;
                    }

                    float spacing;
                    if (previousParent == null) spacing = 0;
                    else if (line.isBackground) spacing = 0;
                    else if (previousParent.isBackground) spacing = 0;
                    else
                        spacing =
                                (previousParent == line)
                                        ? spacingBetweenWrappedLines
                                        : spacingBetweenLyrics;

                    currentY += spacing;
                    if (parentStartY == -1) parentStartY = currentY;

                    wl.y = currentY;
                    wl.nextStartTime = nextStartTime;
                    wrappedLines.add(wl);

                    parentLastLineY = currentY;

                    if (line.isBackground) currentY += textHeight * BG_SCALE_SIZE;
                    else currentY += textHeight;

                    previousParent = line;
                    currentLineWords.clear();
                    currentLineWidth = 0;
                }
                currentLineWords.addAll(cluster);
                currentLineWidth += clusterWidth;
            }

            if (!currentLineWords.isEmpty()) {
                WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));

                // MODIFIED: Center justify background lines
                if (line.isBackground) {
                    wl.xOffset = (viewWidth - currentLineWidth) / 2f - padding;
                } else {
                    wl.xOffset = 0;
                }

                float spacing;
                if (previousParent == null) spacing = 0;
                else if (line.isBackground) spacing = 0;
                else if (previousParent.isBackground) spacing = 0;
                else
                    spacing =
                            (previousParent == line)
                                    ? spacingBetweenWrappedLines
                                    : spacingBetweenLyrics;

                currentY += spacing;
                if (parentStartY == -1) parentStartY = currentY;

                wl.y = currentY;
                wl.nextStartTime = nextStartTime;
                wrappedLines.add(wl);

                parentLastLineY = currentY;

                if (line.isBackground) currentY += textHeight * BG_SCALE_SIZE;
                else currentY += textHeight;

                previousParent = line;
            }

            if (parentStartY != -1 && parentLastLineY != -1) {
                float centerY = (parentStartY + parentLastLineY) / 2f;
                lineCenterYMap.put(line, centerY);
            }
        }
        totalContentHeight = currentY;

        // ... (Recalculate Scroll Map - Same as before) ...
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine current = lyrics.get(i);
            Float centerCur = lineCenterYMap.get(current);
            if (centerCur == null) centerCur = 0f;
            if (current.startTime == -1) {
                lineScrollYMap.put(current, centerCur);
                continue;
            }

            float finalTarget = centerCur;
            boolean overlapsPrev = false, overlapsPrevPrev = false;

            if (i > 0) {
                LyricLine prev = lyrics.get(i - 1);
                if (current.startTime < prev.endTime) overlapsPrev = true;
            }
            if (i > 1) {
                LyricLine prevPrev = lyrics.get(i - 2);
                if (current.startTime < prevPrev.endTime) overlapsPrevPrev = true;
            }

            if (overlapsPrevPrev) {
                Float centerMid = lineCenterYMap.get(lyrics.get(i - 1));
                if (centerMid != null) finalTarget = centerMid;
            } else if (overlapsPrev) {
                Float centerPrev = lineCenterYMap.get(lyrics.get(i - 1));
                if (centerPrev != null) finalTarget = (centerPrev + centerCur) / 2f;
            }
            lineScrollYMap.put(current, finalTarget);
        }
    }

    private float getFocusRatio(LyricLine line, long nextStartTime) {
        if (line.startTime == -1) return 1.0f;
        if (currentTime >= line.startTime && currentTime <= line.endTime) return 1.0f;
        if (currentTime < line.startTime) {
            long diff = line.startTime - currentTime;
            if (diff <= SCROLL_ANTICIPATION_MS)
                return 1.0f - ((float) diff / SCROLL_ANTICIPATION_MS);
            return 0.0f;
        }
        if (currentTime > line.endTime) {
            float decay = 0.0f, antic = 0.0f;
            long diff = currentTime - line.endTime;
            if (diff < DECAY_DURATION_MS) decay = 1.0f - ((float) diff / DECAY_DURATION_MS);
            if (nextStartTime != -1) {
                long diffNext = nextStartTime - currentTime;
                if (diffNext > SCROLL_ANTICIPATION_MS) antic = 1.0f;
                else if (diffNext > 0) antic = (float) diffNext / SCROLL_ANTICIPATION_MS;
            }
            return Math.max(decay, antic);
        }
        return 0.0f;
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
        boolean animatingGlow = false;

        canvas.save();
        canvas.translate(0, -currentScrollY);

        float viewTop = currentScrollY - textHeight;
        float viewBottom = currentScrollY + getHeight();

        // REMOVED: float viewCenterX = getWidth() / 2f; (Not needed for gradient anymore)

        for (WrappedLine wl : wrappedLines) {
            float y = wl.y;
            if (y > viewBottom || y < viewTop) continue;

            float focusRatio = getFocusRatio(wl.parentLine, wl.nextStartTime);
            focusRatio = Math.max(0f, Math.min(1f, focusRatio));

            float targetScale;

            if (wl.parentLine.isBackground) {
                targetScale = BG_SCALE_SIZE;
            } else {
                targetScale =
                        (INACTIVE_SCALE / LAYOUT_SCALE)
                                + ((1.0f - (INACTIVE_SCALE / LAYOUT_SCALE)) * focusRatio);
            }

            if (wl.parentLine.isBackground) {
                paintActive.setMaskFilter(bgBlurFilter);
                paintDefault.setMaskFilter(bgBlurFilter);
                paintFill.setMaskFilter(bgBlurFilter);
                paintFillV2.setMaskFilter(bgBlurFilter);
                paintBloom.setMaskFilter(bgBlurFilter);
                paintBloomV2.setMaskFilter(bgBlurFilter);

                paintActive.setTextScaleX(BG_HORIZONTAL_STRETCH);
                paintDefault.setTextScaleX(BG_HORIZONTAL_STRETCH);
                paintFill.setTextScaleX(BG_HORIZONTAL_STRETCH);
                paintFillV2.setTextScaleX(BG_HORIZONTAL_STRETCH);
                paintBloom.setTextScaleX(BG_HORIZONTAL_STRETCH);
                paintBloomV2.setTextScaleX(BG_HORIZONTAL_STRETCH);
            }

            boolean isPlain = (wl.parentLine.startTime == -1);
            boolean isTimeActive =
                    (currentTime >= wl.parentLine.startTime
                            && currentTime <= wl.parentLine.endTime);
            boolean isTimePast = (currentTime > wl.parentLine.endTime);
            boolean isV2 = (wl.parentLine.vocalType == 2);

            int targetAlpha = 255;
            if (!wl.parentLine.isBackground) {
                if (isTimePast && wl.parentLine.startTime != -1) {
                    if (!wl.parentLine.isWordSynced) {
                        float adjustedRatio = (float) Math.pow(focusRatio, 3);
                        targetAlpha = (int) (102 + (255 - 102) * adjustedRatio);
                    } else {
                        targetAlpha = (int) (102 + (255 - 102) * focusRatio);
                    }
                    targetAlpha = Math.max(102, Math.min(255, targetAlpha));
                }
            }

            float x = padding + wl.xOffset;

            canvas.save();
            canvas.scale(targetScale, targetScale, x, y);

            for (LyricWord word : wl.words) {
                float wordWidth = getWordWidth(word.text);
                if (wl.parentLine.isBackground) wordWidth *= BG_HORIZONTAL_STRETCH;

                int dispersedAlpha = 255;

                // MODIFIED: Removed spatial gradient logic.
                // Replaced with a static alpha (120) so it looks "grey" like main lines but visible
                // under blur.
                if (wl.parentLine.isBackground) {
                    dispersedAlpha = 120; // Fixed grey alpha

                    paintActive.setAlpha(dispersedAlpha);
                    paintDefault.setAlpha(dispersedAlpha);
                    paintFill.setAlpha(dispersedAlpha);
                    paintFillV2.setAlpha(dispersedAlpha);
                    paintBloom.setAlpha(dispersedAlpha);
                }

                if (isPlain) {
                    canvas.drawText(word.text, x, y, paintActive);
                } else if (isTimeActive && currentTime >= word.time) {
                    if (wl.parentLine.isBackground) {
                        // --- 90% FADE OUT LOGIC ---
                        float fadeOutFactor = 1.0f;
                        long lineDuration = wl.parentLine.endTime - wl.parentLine.startTime;
                        if (lineDuration > 0) {
                            long lineElapsed = currentTime - wl.parentLine.startTime;
                            float completion = (float) lineElapsed / lineDuration;
                            // Start fading at 90%
                            if (completion > 0.9f) {
                                // Map 0.9->1.0 to 1.0->0.0
                                fadeOutFactor = 1.0f - ((completion - 0.9f) / 0.1f);
                                fadeOutFactor = Math.max(0f, fadeOutFactor);
                            }
                        }

                        int fadingAlpha = (int) (dispersedAlpha * fadeOutFactor);
                        animatingGlow = true;
                        // Passing fadingAlpha ensures it fades out at the end, but stays solid grey
                        // otherwise
                        drawActiveWord(canvas, word, wl, x, y, wordWidth, fadingAlpha);
                    } else {
                        if (wl.parentLine.isWordSynced) {
                            animatingGlow = true;
                            drawActiveWord(canvas, word, wl, x, y, wordWidth, 255);
                        } else {
                            if (isV2) canvas.drawText(word.text, x, y, paintFillV2);
                            else canvas.drawText(word.text, x, y, paintActive);
                        }
                    }
                } else if (isTimeActive) {
                    canvas.drawText(word.text, x, y, paintDefault);
                } else if (isTimePast) {
                    if (focusRatio > 0.01f) {
                        Paint activeP = isV2 ? paintActive : paintActive;
                        if (isV2) activeP.setColor(COLOR_V2);
                        else activeP.setColor(Color.WHITE);

                        int finalAlpha = targetAlpha;
                        if (wl.parentLine.isBackground) {
                            // Scale alpha relative to the dispersed (fixed) alpha
                            finalAlpha =
                                    (int) ((targetAlpha / 255f) * (dispersedAlpha / 255f) * 255);
                        }

                        activeP.setAlpha(finalAlpha);
                        canvas.drawText(word.text, x, y, activeP);

                        activeP.setAlpha(255);
                        activeP.setColor(Color.WHITE);
                    } else {
                        canvas.drawText(word.text, x, y, paintDefault);
                    }
                } else {
                    if (!wl.parentLine.isWordSynced
                            && focusRatio > 0
                            && !wl.parentLine.isBackground) {
                        int futureAlpha = (int) (102 + (255 - 102) * focusRatio);
                        futureAlpha = Math.max(102, Math.min(255, futureAlpha));
                        paintActive.setAlpha(futureAlpha);
                        canvas.drawText(word.text, x, y, paintActive);
                        paintActive.setAlpha(255);
                    } else {
                        canvas.drawText(word.text, x, y, paintDefault);
                    }
                }

                if (wl.parentLine.isBackground) {
                    paintActive.setAlpha(255);
                    paintDefault.setAlpha(102);
                    paintFill.setAlpha(255);
                    paintFillV2.setAlpha(255);
                    paintBloom.setAlpha(255);
                }

                x += wordWidth;
            }
            canvas.restore();

            if (wl.parentLine.isBackground) {
                paintActive.setMaskFilter(null);
                paintDefault.setMaskFilter(null);
                paintFill.setMaskFilter(null);
                paintFillV2.setMaskFilter(null);
                paintBloom.setMaskFilter(null);
                paintBloomV2.setMaskFilter(null);

                paintActive.setTextScaleX(1.0f);
                paintDefault.setTextScaleX(1.0f);
                paintFill.setTextScaleX(1.0f);
                paintFillV2.setTextScaleX(1.0f);
                paintBloom.setTextScaleX(1.0f);
                paintBloomV2.setTextScaleX(1.0f);
            }

            if (focusRatio > 0.0f && focusRatio < 1.0f) {
                animatingGlow = true;
            }
        }
        canvas.restore();

        canvas.drawText(String.valueOf(currentFps), getWidth() - 50, 100, paintFps);

        if (animatingScroll || animatingGlow) {
            postInvalidateOnAnimation();
        }
    }

    private void drawActiveWord(
            Canvas canvas,
            LyricWord word,
            WrappedLine wl,
            float x,
            float y,
            float wordWidth,
            int alphaOverride) {
        boolean isV2 = (wl.parentLine.vocalType == 2);
        Paint targetFill = isV2 ? paintFillV2 : paintFill;
        Paint targetBloom = isV2 ? paintBloomV2 : paintBloom;
        LinearGradient targetGrad = isV2 ? masterGradientV2 : masterGradient;

        long nextWordTime = wl.parentLine.endTime;
        int wordIndex = wl.parentLine.words.indexOf(word);
        if (wordIndex < wl.parentLine.words.size() - 1) {
            nextWordTime = wl.parentLine.words.get(wordIndex + 1).time;
        }

        long duration = nextWordTime - word.time;
        if (duration <= 0) duration = 1;
        long elapsed = currentTime - word.time;
        float progress = Math.min(1.0f, (float) elapsed / duration);

        // Draw the inactive base text
        canvas.drawText(word.text, x, y, paintDefault);

        if (targetGrad != null) {
            // MODIFIED: Make edgeWidth dependent on word width
            // We use Math.min to ensure it doesn't get wider than 120f (preserving the look for
            // long words)
            // but shrinks down for short words so they fill L->R instead of fading.
            float edgeWidth = Math.min(120f, wordWidth);

            float currentX = x + (wordWidth + edgeWidth) * progress;

            shaderMatrix.reset();
            shaderMatrix.setScale(edgeWidth / 100f, 1f);
            shaderMatrix.postTranslate(currentX - edgeWidth, 0);
            targetGrad.setLocalMatrix(shaderMatrix);

            targetFill.setShader(targetGrad);
            targetFill.setAlpha(alphaOverride);
            canvas.drawText(word.text, x, y, targetFill);

            if (progress < 1.0f) {
                float bloomAlpha = 1.0f;
                if (progress >= 0.7f) {
                    bloomAlpha = (1.0f - progress) / (1.0f - 0.7f);
                    bloomAlpha = Math.max(0f, Math.min(1.0f, bloomAlpha));
                }

                int finalBloomAlpha = (int) (alphaOverride * bloomAlpha);

                int shadowColor = isV2 ? COLOR_V2 : Color.WHITE;
                int fadedShadowColor =
                        Color.argb(
                                finalBloomAlpha,
                                Color.red(shadowColor),
                                Color.green(shadowColor),
                                Color.blue(shadowColor));
                targetBloom.setShadowLayer(25, 0, 0, fadedShadowColor);

                targetBloom.setShader(targetGrad);
                targetBloom.setAlpha(finalBloomAlpha);
                canvas.drawText(word.text, x, y, targetBloom);

                targetBloom.setShadowLayer(25, 0, 0, shadowColor);
            }

            targetFill.setAlpha(255);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isTap = gestureDetector.onTouchEvent(event);
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
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
                currentScrollY = Math.max(minScrollY, Math.min(currentScrollY, maxScrollY));
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
                        scroller.fling(
                                0,
                                (int) currentScrollY,
                                0,
                                (int) -velocityY,
                                0,
                                0,
                                (int) minScrollY,
                                (int) maxScrollY);
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
        long nextStartTime = -1;
        float xOffset = 0;

        WrappedLine(LyricLine parentLine, List<LyricWord> words) {
            this.parentLine = parentLine;
            this.words = words;
        }
    }
}
