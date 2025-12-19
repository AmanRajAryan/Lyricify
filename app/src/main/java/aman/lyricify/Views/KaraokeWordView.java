package aman.lyricify;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom view for individual karaoke words with self-contained animation
 * Word animates itself for the specified duration
 */
public class KaraokeWordView extends View {
    
    private Paint basePaint;
    private Paint glowPaint;
    private LinearGradient gradient;
    private float progress = 0f;
    private int viewWidth;
    private ValueAnimator animator;
    
    private String word = "";
    private Typeface typeface = Typeface.DEFAULT_BOLD;
    private float textSize = 28;
    private int baseColor = Color.GRAY;
    private int fillColor = Color.WHITE;
    
    public KaraokeWordView(Context context) {
        super(context);
        init();
    }
    
    public KaraokeWordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(baseColor);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setTextAlign(Paint.Align.LEFT);
        basePaint.setTypeface(typeface);
        basePaint.setTextSize(textSize);
        
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setTextAlign(Paint.Align.LEFT);
        glowPaint.setTypeface(typeface);
        glowPaint.setTextSize(textSize);
        glowPaint.setMaskFilter(new BlurMaskFilter(20, BlurMaskFilter.Blur.NORMAL));
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float textWidth = basePaint.measureText(word.isEmpty() ? "A" : word);
        Paint.FontMetrics fm = basePaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        
        int width = (int) (textWidth + getPaddingLeft() + getPaddingRight() + 16);
        int height = (int) (textHeight + getPaddingTop() + getPaddingBottom() + 16);
        
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (word.isEmpty() || viewWidth <= 0) return;
        
        String text = word;
        float textWidth = basePaint.measureText(text);
        Paint.FontMetrics fm = basePaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        
        float x = (getWidth() - textWidth) / 2f;
        float y = (getHeight() + textHeight) / 2f - fm.descent;
        
        // Cutoff: how far the white progress reached
        float cutoff = progress * viewWidth;
        
        // 1. Draw gray base
        basePaint.setColor(baseColor);
        canvas.drawText(text, x, y, basePaint);
        
        // Only show effects when animating
        if (progress > 0f) {
            // 2. Draw fade gradient (white → transparent)
            float fadeStart = cutoff;
            float fadeEnd = cutoff + 80;
            LinearGradient fadeGradient = new LinearGradient(
                fadeStart,
                0,
                fadeEnd,
                0,
                new int[]{fillColor, Color.TRANSPARENT},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
            );
            Paint fadePaint = new Paint(basePaint);
            fadePaint.setShader(fadeGradient);
            canvas.drawText(text, x, y, fadePaint);
            
            // 3. Left side (already swept) — pure white
            canvas.save();
            canvas.clipRect(0, 0, cutoff, getHeight());
            basePaint.setColor(fillColor);
            canvas.drawText(text, x, y, basePaint);
            canvas.restore();
            
            // 4. White band with glow (only while animating)
            if (progress < 1f) {
                gradient = new LinearGradient(
                    cutoff - 120,
                    0,
                    cutoff + 120,
                    0,
                    new int[]{fillColor, fillColor, Color.TRANSPARENT},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
                );
                glowPaint.setShader(gradient);
                canvas.drawText(text, x, y, glowPaint);
            }
        }
    }
    
    /**
     * Start the sweep animation for this word
     * @param durationMs Duration in milliseconds for the word animation
     */
    public void startAnimation(long durationMs) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        
        if (durationMs <= 0) {
            durationMs = 500; // Default minimum duration
        }
        
        progress = 0f;
        invalidate();
        
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }
    
    /**
     * Stop animation and set to final state
     */
    public void completeImmediately() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        progress = 1.0f;
        invalidate();
    }
    
    public void setWord(String word) {
        this.word = word != null ? word : "";
        requestLayout();
        invalidate();
    }
    
    public void setWordTextSize(float size) {
        this.textSize = size;
        basePaint.setTextSize(size);
        glowPaint.setTextSize(size);
        requestLayout();
        invalidate();
    }
    
    public void setWordTypeface(Typeface typeface) {
        this.typeface = typeface;
        basePaint.setTypeface(typeface);
        glowPaint.setTypeface(typeface);
        requestLayout();
        invalidate();
    }
    
    public void setColors(int baseColor, int fillColor) {
        this.baseColor = baseColor;
        this.fillColor = fillColor;
        basePaint.setColor(baseColor);
        invalidate();
    }
    
    public void reset() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        progress = 0f;
        invalidate();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }
}