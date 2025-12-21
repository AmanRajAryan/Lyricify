package aman.lyricify;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

public class LyricsRenderer {

    // Paints
    public Paint paintActive, paintDefault, paintPast, paintFps;
    public Paint paintFill, paintBloom; // V1
    public Paint paintFillV2, paintBloomV2; // V2

    // Gradients & Matrix
    private LinearGradient masterGradient, masterGradientV2;
    private Matrix shaderMatrix = new Matrix();
    
    // Config
    private final int COLOR_V2 = Color.parseColor("#00E5FF");
    private float textHeight;
    private float baseTextSize;
    
    // Scaling
    public static final float LAYOUT_SCALE = 1.1f; 
    public static final float INACTIVE_SCALE = 0.9f; 

    private int currentFontIndex = 0;
    private static final Typeface[] FONTS = {
            Typeface.DEFAULT, Typeface.SERIF, Typeface.SANS_SERIF, Typeface.MONOSPACE,
            Typeface.create("cursive", Typeface.NORMAL), Typeface.create("casual", Typeface.NORMAL)
    };
    public static final String[] FONT_NAMES = {
            "Default", "Serif", "Sans Serif", "Monospace", "Cursive", "Casual"
    };

    public LyricsRenderer(float density) {
        init(density);
    }

    private void init(float density) {
        baseTextSize = 32 * density;
        float layoutTextSize = baseTextSize * LAYOUT_SCALE;

        paintActive = createPaint(layoutTextSize, Color.WHITE, 255);
        paintDefault = createPaint(layoutTextSize, Color.WHITE, 102); // Greyish
        paintPast = createPaint(layoutTextSize, Color.WHITE, 80);

        paintFill = createPaint(layoutTextSize, Color.BLACK, 255); // Color overwritten by shader
        paintBloom = createPaint(layoutTextSize, Color.WHITE, 255);
        paintBloom.setShadowLayer(25, 0, 0, Color.WHITE);

        paintFillV2 = createPaint(layoutTextSize, Color.BLACK, 255);
        paintBloomV2 = createPaint(layoutTextSize, COLOR_V2, 255);
        paintBloomV2.setShadowLayer(25, 0, 0, COLOR_V2);

        paintFps = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFps.setColor(Color.GREEN);
        paintFps.setTextSize(18 * density);
        paintFps.setFakeBoldText(true);
        paintFps.setTextAlign(Paint.Align.RIGHT);

        updateTextHeight();
    }

    private Paint createPaint(float size, int color, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setAlpha(alpha);
        p.setFakeBoldText(true);
        return p;
    }

    private void updateTextHeight() {
        Paint.FontMetrics fm = paintActive.getFontMetrics();
        textHeight = fm.descent - fm.ascent;
    }

    public void updateGradients(int width) {
        if (width <= 0) return;
        masterGradient = new LinearGradient(0, 0, 100, 0, new int[]{Color.WHITE, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        masterGradientV2 = new LinearGradient(0, 0, 100, 0, new int[]{COLOR_V2, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
    }

    public String cycleFont() {
        currentFontIndex = (currentFontIndex + 1) % FONTS.length;
        Typeface tf = Typeface.create(FONTS[currentFontIndex], Typeface.BOLD);
        
        paintActive.setTypeface(tf);
        paintDefault.setTypeface(tf);
        paintPast.setTypeface(tf);
        paintFill.setTypeface(tf);
        paintBloom.setTypeface(tf);
        paintFillV2.setTypeface(tf);
        paintBloomV2.setTypeface(tf);
        
        updateTextHeight();
        return FONT_NAMES[currentFontIndex];
    }

    public float getTextHeight() { return textHeight; }
    public Paint getMeasurePaint() { return paintActive; }

    // --- DRAWING LOGIC (OPTIMIZED) ---
    public boolean drawLine(Canvas canvas, LyricsLayout.WrappedLine wl, float x, float y, float focusRatio, long currentTime) {
        boolean animatingGlow = false;
        
        // Scale Logic
        float targetScale = (INACTIVE_SCALE / LAYOUT_SCALE) + ((1.0f - (INACTIVE_SCALE / LAYOUT_SCALE)) * focusRatio);
        
        // Check States
        boolean isPlain = (wl.parentLine.startTime == -1);
        boolean isTimeActive = (currentTime >= wl.parentLine.startTime && currentTime <= wl.parentLine.endTime);
        boolean isTimePast = (currentTime > wl.parentLine.endTime);
        boolean isV2 = (wl.parentLine.vocalType == 2);

        // Calculate Alpha for Past Fade (White/Color -> Grey)
        int targetAlpha = 255;
        if (isTimePast && !isPlain) {
            targetAlpha = (int) (102 + (255 - 102) * focusRatio);
            targetAlpha = Math.max(102, Math.min(255, targetAlpha));
        }

        canvas.save();
        canvas.scale(targetScale, targetScale, x, y);

        float currentX = x;
        int wordCount = wl.words.size();

        // [PERFORMANCE FIX]: Use indexed loop instead of iterator/indexOf to avoid O(N^2) complexity
        for (int i = 0; i < wordCount; i++) {
            LyricWord word = wl.words.get(i);
            float wordWidth = wl.wordWidths.get(i); // O(1) Access
            
            if (isPlain) {
                // Plain Text: Always Active White, No Animation
                canvas.drawText(word.text, currentX, y, paintActive);
            }
            else if (isTimeActive && currentTime >= word.time) {
                // 1. Karaoke / Active
                animatingGlow = true;
                drawKaraokeWord(canvas, word, wl, currentX, y, wordWidth, currentTime, isV2);
            } 
            else if (isTimeActive) {
                // 2. Future word in Active Line -> Grey
                canvas.drawText(word.text, currentX, y, paintDefault);
            } 
            else if (isTimePast) {
                // 3. Past Line -> Fade
                if (focusRatio > 0.01f) {
                    Paint targetPaint = paintActive;
                    if (isV2) targetPaint.setColor(COLOR_V2);
                    else targetPaint.setColor(Color.WHITE);
                    
                    targetPaint.setAlpha(targetAlpha);
                    canvas.drawText(word.text, currentX, y, targetPaint);
                    
                    // Reset
                    targetPaint.setAlpha(255); 
                    targetPaint.setColor(Color.WHITE);
                } else {
                    canvas.drawText(word.text, currentX, y, paintDefault);
                }
            } 
            else {
                // 4. Future Line -> Grey
                canvas.drawText(word.text, currentX, y, paintDefault);
            }
            currentX += wordWidth;
        }
        canvas.restore();
        
        return animatingGlow;
    }

    private void drawKaraokeWord(Canvas canvas, LyricWord word, LyricsLayout.WrappedLine wl, float x, float y, float width, long currentTime, boolean isV2) {
        Paint fill = isV2 ? paintFillV2 : paintFill;
        Paint bloom = isV2 ? paintBloomV2 : paintBloom;
        LinearGradient grad = isV2 ? masterGradientV2 : masterGradient;

        long nextWordTime = wl.parentLine.endTime;
        int idx = wl.parentLine.words.indexOf(word); // This is safe here as it runs only for active words (few)
        if (idx < wl.parentLine.words.size() - 1) nextWordTime = wl.parentLine.words.get(idx + 1).time;

        long duration = nextWordTime - word.time;
        if (duration <= 0) duration = 1;
        float progress = Math.min(1.0f, (float) (currentTime - word.time) / duration);

        // Base Grey
        canvas.drawText(word.text, x, y, paintDefault);

        if (grad != null) {
            float edge = 120f;
            float shaderX = x + (width + edge) * progress;
            shaderMatrix.reset();
            shaderMatrix.setScale(edge / 100f, 1f);
            shaderMatrix.postTranslate(shaderX - edge, 0);
            grad.setLocalMatrix(shaderMatrix);
            
            fill.setShader(grad);
            canvas.drawText(word.text, x, y, fill);
            
            if (progress < 1.0f) {
                float bloomAlpha = (progress >= 0.8f) ? (1f - progress) / 0.2f : 1f;
                bloom.setShader(grad);
                bloom.setAlpha((int)(255 * bloomAlpha));
                canvas.drawText(word.text, x, y, bloom);
            }
        }
    }
    
    public void drawFPS(Canvas canvas, int fps, int width) {
        canvas.drawText(String.valueOf(fps), width - 50, 100, paintFps);
    }
}
