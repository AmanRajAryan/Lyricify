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

    // Set to true to show the FPS counter overlay.
    private static final boolean SHOW_FPS = false;

    private static final float GLOW_BASE_RADIUS = 25f;
    private static final float GLOW_MIN_RADIUS = 1f;
    private static final float GLOW_MAX_RADIUS = 40f;

    private static final float GLOW_SPEED_THRESHOLD_SLOW = 0.3f;
    private static final float GLOW_SPEED_THRESHOLD_FAST = 1f;

    private static final float GLOW_FADE_START_PROGRESS = 0.7f;

    private static final float GLOW_MIN_ALPHA_MULTIPLIER = 0.2f;
    private static final float GLOW_MAX_ALPHA_MULTIPLIER = 1.0f;

    private static final float GRADIENT_EDGE_MIN_WIDTH = 200f;
    private static final float GRADIENT_EDGE_MAX_WIDTH = 300f;
    private static final float GRADIENT_EDGE_WIDTH_MULTIPLIER = 0.8f;

    public interface SeekListener {
        void onSeek(long timeMs);
    }

    private static class WrappedLine {
        LyricLine parentLine;
        List<LyricWord> words;
        float y;
        long nextStartTime = -1;
        float xOffset = 0;
        int startWordIndexInParent = 0;

        WrappedLine(LyricLine parentLine, List<LyricWord> words) {
            this.parentLine = parentLine;
            this.words = words;
        }
    }

    private List<LyricLine> lyrics = new ArrayList<>();
    private List<WrappedLine> wrappedLines = new ArrayList<>();
    private Map<LyricLine, Float> lineCenterYMap = new HashMap<>();
    private Map<LyricLine, Float> lineScrollYMap = new HashMap<>();

    private long currentTime = 0;

    private Paint paintActive, paintDefault, paintPast;
    private Paint paintFill, paintBloom;
    private Paint paintFillV2, paintBloomV2;
    private Paint paintActiveBG, paintDefaultBG;
    private Paint paintFillBG, paintBloomBG;
    private Paint paintFillV2BG, paintBloomV2BG;

    private Paint paintFps;
    private LinearGradient masterGradient;
    private LinearGradient masterGradientV2;
    private final int COLOR_V2 = Color.parseColor("#00E5FF");

    private Matrix shaderMatrix = new Matrix();
    private BlurMaskFilter bgBlurFilter;

    private float textHeight;
    private float baseTextSize;
    private static final float LAYOUT_SCALE = 1.1f;
    private static final float INACTIVE_SCALE = 0.9f;

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

        spacingBetweenWrappedLines = 0.5f * density;
        spacingBetweenLyrics = 40 * density;

        bgBlurFilter = new BlurMaskFilter(5f * density, BlurMaskFilter.Blur.NORMAL);
        Typeface boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);

        paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintActive.setTypeface(boldTypeface);
        paintActive.setTextSize(layoutTextSize);
        paintActive.setColor(Color.WHITE);
        paintActive.setFakeBoldText(true);

        paintDefault = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDefault.setTypeface(boldTypeface);
        paintDefault.setTextSize(layoutTextSize);
        paintDefault.setColor(Color.argb(102, 255, 255, 255));
        paintDefault.setFakeBoldText(true);

        paintPast = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPast.setTypeface(boldTypeface);
        paintPast.setTextSize(layoutTextSize);
        paintPast.setColor(Color.argb(80, 255, 255, 255));
        paintPast.setFakeBoldText(true);

        paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFill.setTypeface(boldTypeface);
        paintFill.setTextSize(layoutTextSize);
        paintFill.setFakeBoldText(true);

        paintBloom = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBloom.setTypeface(boldTypeface);
        paintBloom.setTextSize(layoutTextSize);
        paintBloom.setFakeBoldText(true);
        paintBloom.setShadowLayer(GLOW_BASE_RADIUS, 0, 0, Color.WHITE);

        paintFillV2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFillV2.setTypeface(boldTypeface);
        paintFillV2.setTextSize(layoutTextSize);
        paintFillV2.setFakeBoldText(true);

        paintBloomV2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBloomV2.setTypeface(boldTypeface);
        paintBloomV2.setTextSize(layoutTextSize);
        paintBloomV2.setFakeBoldText(true);
        paintBloomV2.setShadowLayer(GLOW_BASE_RADIUS, 0, 0, COLOR_V2);

        paintActiveBG = new Paint(paintActive);
        paintActiveBG.setMaskFilter(bgBlurFilter);
        paintActiveBG.setTextScaleX(BG_HORIZONTAL_STRETCH);

        paintDefaultBG = new Paint(paintDefault);
        paintDefaultBG.setMaskFilter(bgBlurFilter);
        paintDefaultBG.setTextScaleX(BG_HORIZONTAL_STRETCH);

        paintFillBG = new Paint(paintFill);
        paintFillBG.setMaskFilter(bgBlurFilter);
        paintFillBG.setTextScaleX(BG_HORIZONTAL_STRETCH);

        paintBloomBG = new Paint(paintBloom);
        paintBloomBG.setMaskFilter(bgBlurFilter);
        paintBloomBG.setTextScaleX(BG_HORIZONTAL_STRETCH);

        paintFillV2BG = new Paint(paintFillV2);
        paintFillV2BG.setMaskFilter(bgBlurFilter);
        paintFillV2BG.setTextScaleX(BG_HORIZONTAL_STRETCH);

        paintBloomV2BG = new Paint(paintBloomV2);
        paintBloomV2BG.setMaskFilter(bgBlurFilter);
        paintBloomV2BG.setTextScaleX(BG_HORIZONTAL_STRETCH);

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

        Paint[] allTextPaints = {
            paintActive, paintDefault, paintPast,
            paintFill, paintBloom, paintFillV2, paintBloomV2,
            paintActiveBG, paintDefaultBG,
            paintFillBG, paintBloomBG, paintFillV2BG, paintBloomV2BG
        };
        for (Paint p : allTextPaints) {
            p.setTypeface(tf);
            p.setTextSize(layoutTextSize);
        }

        updateTextHeight();
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

    private void wrapLines(int viewWidth) {
        wrappedLines.clear();
        lineCenterYMap.clear();
        lineScrollYMap.clear();

        float maxAllowedWidth = viewWidth - (padding * 2);
        if (maxAllowedWidth <= 0) return;

        float currentY = 0;
        LyricLine previousParent = null;

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

            int currentLineStartWordIndex = 0;

            int i = 0;
            while (i < line.words.size()) {
                int clusterStartIndex = i;

                List<LyricWord> cluster = new ArrayList<>();
                float clusterWidth = 0;

                LyricWord firstPiece = line.words.get(i);
                firstPiece.width = paintActive.measureText(firstPiece.text);
                cluster.add(firstPiece);
                clusterWidth += firstPiece.width * effectiveMeasureScale;
                i++;

                while (i < line.words.size()) {
                    LyricWord lastAdded = cluster.get(cluster.size() - 1);
                    if (lastAdded.text.endsWith(" ")
                            || lastAdded.text.endsWith("\u3000")
                            || lastAdded.text.endsWith("-")) break;

                    LyricWord nextPiece = line.words.get(i);
                    nextPiece.width = paintActive.measureText(nextPiece.text);
                    cluster.add(nextPiece);
                    clusterWidth += nextPiece.width * effectiveMeasureScale;
                    i++;
                }

                if (currentLineWidth + clusterWidth > maxAllowedWidth
                        && !currentLineWords.isEmpty()) {
                    WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));
                    wl.xOffset =
                            line.isBackground ? (viewWidth - currentLineWidth) / 2f - padding : 0;
                    wl.startWordIndexInParent = currentLineStartWordIndex;

                    float spacing =
                            (previousParent == null
                                            || line.isBackground
                                            || previousParent.isBackground)
                                    ? 0
                                    : (previousParent == line)
                                            ? spacingBetweenWrappedLines
                                            : spacingBetweenLyrics;

                    currentY += spacing;
                    if (parentStartY == -1) parentStartY = currentY;
                    wl.y = currentY;
                    wl.nextStartTime = nextStartTime;
                    wrappedLines.add(wl);
                    parentLastLineY = currentY;

                    currentY += line.isBackground ? textHeight * BG_SCALE_SIZE : textHeight;
                    previousParent = line;
                    currentLineWords.clear();
                    currentLineWidth = 0;

                    currentLineStartWordIndex = clusterStartIndex;
                }
                currentLineWords.addAll(cluster);
                currentLineWidth += clusterWidth;
            }

            if (!currentLineWords.isEmpty()) {
                WrappedLine wl = new WrappedLine(line, new ArrayList<>(currentLineWords));
                wl.xOffset = line.isBackground ? (viewWidth - currentLineWidth) / 2f - padding : 0;
                wl.startWordIndexInParent = currentLineStartWordIndex;

                float spacing =
                        (previousParent == null || line.isBackground || previousParent.isBackground)
                                ? 0
                                : (previousParent == line)
                                        ? spacingBetweenWrappedLines
                                        : spacingBetweenLyrics;

                currentY += spacing;
                if (parentStartY == -1) parentStartY = currentY;
                wl.y = currentY;
                wl.nextStartTime = nextStartTime;
                wrappedLines.add(wl);
                parentLastLineY = currentY;

                currentY += line.isBackground ? textHeight * BG_SCALE_SIZE : textHeight;
                previousParent = line;
            }

            if (parentStartY != -1 && parentLastLineY != -1) {
                lineCenterYMap.put(line, (parentStartY + parentLastLineY) / 2f);
            }
        }
        totalContentHeight = currentY;

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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            int[] colors = {
                Color.WHITE,
                Color.argb(220, 255, 255, 255),
                Color.argb(100, 255, 255, 255),
                Color.TRANSPARENT
            };
            float[] positions = {0f, 0.3f, 0.7f, 1f};

            int[] colorsV2 = {
                COLOR_V2,
                Color.argb(220, 0, 229, 255),
                Color.argb(100, 0, 229, 255),
                Color.TRANSPARENT
            };

            masterGradient =
                    new LinearGradient(0, 0, 100, 0, colors, positions, Shader.TileMode.CLAMP);

            masterGradientV2 =
                    new LinearGradient(0, 0, 100, 0, colorsV2, positions, Shader.TileMode.CLAMP);
        }
        updateScrollBounds(h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (SHOW_FPS) {
            long now = System.currentTimeMillis();
            frameCount++;
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount;
                frameCount = 0;
                lastFpsTime = now;
            }
        }

        if (lyrics.isEmpty() || wrappedLines.isEmpty()) return;
        boolean animatingScroll = updateScrollLogic();
        boolean animatingGlow = false;

        canvas.save();
        canvas.translate(0, -currentScrollY);

        float buffer = textHeight * 4;
        float viewTop = currentScrollY - buffer;
        float viewBottom = currentScrollY + getHeight() + buffer;

        for (WrappedLine wl : wrappedLines) {
            float y = wl.y;
            if (y - textHeight > viewBottom || y + textHeight < viewTop) continue;

            final LyricLine parentLine = wl.parentLine;
            final boolean isBg = parentLine.isBackground;

            float focusRatio = getFocusRatio(parentLine, wl.nextStartTime);
            focusRatio = Math.max(0f, Math.min(1f, focusRatio));

            float targetScale;
            if (isBg) {
                targetScale = BG_SCALE_SIZE;
            } else {
                targetScale =
                        (INACTIVE_SCALE / LAYOUT_SCALE)
                                + ((1.0f - (INACTIVE_SCALE / LAYOUT_SCALE)) * focusRatio);
            }

            boolean isPlain = (parentLine.startTime == -1);
            boolean isTimeActive =
                    (currentTime >= parentLine.startTime && currentTime <= parentLine.endTime);
            boolean isTimePast = (currentTime > parentLine.endTime);
            boolean isV2 = (parentLine.vocalType == 2);

            Paint currentPaintActive = isBg ? paintActiveBG : paintActive;
            Paint currentPaintDefault = isBg ? paintDefaultBG : paintDefault;
            Paint currentPaintFillV2 = isBg ? paintFillV2BG : paintFillV2;

            int targetAlpha = 255;
            if (!isBg && isTimePast && parentLine.startTime != -1) {
                float ratio =
                        parentLine.isWordSynced ? focusRatio : (float) Math.pow(focusRatio, 3);
                targetAlpha = (int) (102 + (255 - 102) * ratio);
                targetAlpha = Math.max(102, Math.min(255, targetAlpha));
            }

            float x = padding + wl.xOffset;

            canvas.save();
            canvas.scale(targetScale, targetScale, x, y);

            for (int wi = 0; wi < wl.words.size(); wi++) {
                LyricWord word = wl.words.get(wi);
                int wordIndexInParent = wl.startWordIndexInParent + wi;

                float wordWidth = word.width;
                if (isBg) wordWidth *= BG_HORIZONTAL_STRETCH;

                int dispersedAlpha = 255;
                if (isBg) {
                    dispersedAlpha = 120;
                    currentPaintActive.setAlpha(dispersedAlpha);
                    currentPaintDefault.setAlpha(dispersedAlpha);
                }

                if (isPlain) {
                    canvas.drawText(word.text, x, y, currentPaintActive);
                } else if (isTimeActive && currentTime >= word.time) {
                    if (isBg) {
                        float fadeOutFactor = 1.0f;
                        long lineDuration = parentLine.endTime - parentLine.startTime;
                        if (lineDuration > 0) {
                            long lineElapsed = currentTime - parentLine.startTime;
                            float completion = (float) lineElapsed / lineDuration;
                            if (completion > 0.9f) {
                                fadeOutFactor = 1.0f - ((completion - 0.9f) / 0.1f);
                                fadeOutFactor = Math.max(0f, fadeOutFactor);
                            }
                        }
                        int fadingAlpha = (int) (dispersedAlpha * fadeOutFactor);
                        animatingGlow = true;
                        drawActiveWord(canvas, word, wl, x, y, wordWidth, fadingAlpha, wordIndexInParent);
                    } else {
                        if (parentLine.isWordSynced) {
                            animatingGlow = true;
                            drawActiveWord(canvas, word, wl, x, y, wordWidth, 255, wordIndexInParent);
                        } else {
                            if (isV2) canvas.drawText(word.text, x, y, currentPaintFillV2);
                            else canvas.drawText(word.text, x, y, currentPaintActive);
                        }
                    }
                } else if (isTimeActive) {
                    canvas.drawText(word.text, x, y, currentPaintDefault);
                } else if (isTimePast) {
                    if (focusRatio > 0.01f) {
                        Paint activeP = isV2 ? currentPaintFillV2 : currentPaintActive;
                        activeP.setShader(null); // clear any gradient shader from drawActiveWord
                        if (isV2) activeP.setColor(COLOR_V2);
                        else activeP.setColor(Color.WHITE);
                        int finalAlpha =
                                isBg
                                        ? (int) ((targetAlpha / 255f) * (dispersedAlpha / 255f) * 255)
                                        : targetAlpha;
                        activeP.setAlpha(finalAlpha);
                        canvas.drawText(word.text, x, y, activeP);
                        activeP.setAlpha(255);
                        if (isV2) activeP.setColor(COLOR_V2);
                        else activeP.setColor(Color.WHITE);
                    } else {
                        canvas.drawText(word.text, x, y, currentPaintDefault);
                    }
                } else {
                    if (!parentLine.isWordSynced && focusRatio > 0 && !isBg) {
                        int futureAlpha = (int) (102 + (255 - 102) * focusRatio);
                        futureAlpha = Math.max(102, Math.min(255, futureAlpha));
                        currentPaintActive.setAlpha(futureAlpha);
                        canvas.drawText(word.text, x, y, currentPaintActive);
                        currentPaintActive.setAlpha(255);
                    } else {
                        canvas.drawText(word.text, x, y, currentPaintDefault);
                    }
                }

                if (isBg) {
                    currentPaintActive.setAlpha(255);
                    currentPaintDefault.setAlpha(102);
                }
                x += wordWidth;
            }
            canvas.restore();
            if (focusRatio > 0.0f && focusRatio < 1.0f) animatingGlow = true;
        }
        canvas.restore();

        if (SHOW_FPS) {
            canvas.drawText(String.valueOf(currentFps), getWidth() - 50, 100, paintFps);
        }

        if (animatingScroll || animatingGlow) postInvalidateOnAnimation();
    }

    private void drawActiveWord(
            Canvas canvas,
            LyricWord word,
            WrappedLine wl,
            float x,
            float y,
            float wordWidth,
            int alphaOverride,
            int wordIndexInParent) {

        final LyricLine parentLine = wl.parentLine;
        boolean isV2 = (parentLine.vocalType == 2);
        Paint targetFill =
                isV2
                        ? (parentLine.isBackground ? paintFillV2BG : paintFillV2)
                        : (parentLine.isBackground ? paintFillBG : paintFill);
        Paint targetBloom =
                isV2
                        ? (parentLine.isBackground ? paintBloomV2BG : paintBloomV2)
                        : (parentLine.isBackground ? paintBloomBG : paintBloom);
        LinearGradient targetGrad = isV2 ? masterGradientV2 : masterGradient;
        Paint currentDefault = parentLine.isBackground ? paintDefaultBG : paintDefault;

        long nextWordTime = parentLine.endTime;
        if (wordIndexInParent < parentLine.words.size() - 1) {
            nextWordTime = parentLine.words.get(wordIndexInParent + 1).time;
        }

        long duration = nextWordTime - word.time;
        if (duration <= 0) duration = 1;
        long elapsed = currentTime - word.time;
        float progress = Math.min(1.0f, (float) elapsed / duration);

        float animationSpeed = wordWidth / (float) duration;

        float glowIntensityFactor;
        if (animationSpeed <= GLOW_SPEED_THRESHOLD_SLOW) {
            glowIntensityFactor = GLOW_MAX_ALPHA_MULTIPLIER;
        } else if (animationSpeed >= GLOW_SPEED_THRESHOLD_FAST) {
            glowIntensityFactor = GLOW_MIN_ALPHA_MULTIPLIER;
        } else {
            float speedRange = GLOW_SPEED_THRESHOLD_FAST - GLOW_SPEED_THRESHOLD_SLOW;
            float speedPosition = (animationSpeed - GLOW_SPEED_THRESHOLD_SLOW) / speedRange;
            glowIntensityFactor = GLOW_MAX_ALPHA_MULTIPLIER -
                (speedPosition * (GLOW_MAX_ALPHA_MULTIPLIER - GLOW_MIN_ALPHA_MULTIPLIER));
        }

        float glowRadius;
        if (animationSpeed <= GLOW_SPEED_THRESHOLD_SLOW) {
            glowRadius = GLOW_BASE_RADIUS;
        } else if (animationSpeed >= GLOW_SPEED_THRESHOLD_FAST) {
            glowRadius = GLOW_MIN_RADIUS;
        } else {
            float speedRange = GLOW_SPEED_THRESHOLD_FAST - GLOW_SPEED_THRESHOLD_SLOW;
            float speedPosition = (animationSpeed - GLOW_SPEED_THRESHOLD_SLOW) / speedRange;
            glowRadius = GLOW_BASE_RADIUS - (speedPosition * (GLOW_BASE_RADIUS - GLOW_MIN_RADIUS));
        }
        glowRadius = Math.max(GLOW_MIN_RADIUS, Math.min(glowRadius, GLOW_MAX_RADIUS));

        float edgeWidth;
        if (animationSpeed >= GLOW_SPEED_THRESHOLD_FAST) {
            edgeWidth = Math.min(GRADIENT_EDGE_MAX_WIDTH, wordWidth * GRADIENT_EDGE_WIDTH_MULTIPLIER);
            edgeWidth = Math.max(edgeWidth, GRADIENT_EDGE_MIN_WIDTH);
        } else if (animationSpeed <= GLOW_SPEED_THRESHOLD_SLOW) {
            edgeWidth = Math.min(GRADIENT_EDGE_MIN_WIDTH, wordWidth);
        } else {
            float speedRange = GLOW_SPEED_THRESHOLD_FAST - GLOW_SPEED_THRESHOLD_SLOW;
            float speedPosition = (animationSpeed - GLOW_SPEED_THRESHOLD_SLOW) / speedRange;

            float minEdge = Math.min(GRADIENT_EDGE_MIN_WIDTH, wordWidth);
            float maxEdge = Math.min(GRADIENT_EDGE_MAX_WIDTH, wordWidth * GRADIENT_EDGE_WIDTH_MULTIPLIER);
            maxEdge = Math.max(maxEdge, GRADIENT_EDGE_MIN_WIDTH);

            edgeWidth = minEdge + (speedPosition * (maxEdge - minEdge));
        }

        canvas.drawText(word.text, x, y, currentDefault);

        if (targetGrad != null) {
            float currentX = x + (wordWidth + edgeWidth) * progress;

            shaderMatrix.reset();
            shaderMatrix.setScale(edgeWidth / 100f, 1f);
            shaderMatrix.postTranslate(currentX - edgeWidth, 0);
            targetGrad.setLocalMatrix(shaderMatrix);

            targetFill.setShader(targetGrad);
            targetFill.setAlpha(alphaOverride);
            canvas.drawText(word.text, x, y, targetFill);

            if (progress < 1.0f) {
                float bloomAlpha = glowIntensityFactor;

                if (progress >= GLOW_FADE_START_PROGRESS) {
                    float fadeProgress = (progress - GLOW_FADE_START_PROGRESS) / (1.0f - GLOW_FADE_START_PROGRESS);
                    bloomAlpha *= (1.0f - fadeProgress);
                }

                bloomAlpha = Math.max(0f, Math.min(1.0f, bloomAlpha));
                int finalBloomAlpha = (int) (alphaOverride * bloomAlpha);

                int shadowColor = isV2 ? COLOR_V2 : Color.WHITE;
                int fadedShadowColor =
                        Color.argb(
                                finalBloomAlpha,
                                Color.red(shadowColor),
                                Color.green(shadowColor),
                                Color.blue(shadowColor));

                targetBloom.setShadowLayer(glowRadius, 0, 0, fadedShadowColor);
                targetBloom.setShader(targetGrad);
                targetBloom.setAlpha(finalBloomAlpha);
                canvas.drawText(word.text, x, y, targetBloom);

                targetBloom.setShadowLayer(GLOW_BASE_RADIUS, 0, 0, shadowColor);
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
}
