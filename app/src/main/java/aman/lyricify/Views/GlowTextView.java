package aman.lyricify.Views;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import android.view.animation.LinearInterpolator;

public class GlowTextView extends AppCompatTextView {
    private Paint basePaint;
    private Paint glowPaint;
    private Paint bgGlowPaint;
    
    // OPTIMIZATION: Define Path and Matrix objects here to avoid 'new' in onDraw
    private final Path bgPath = new Path();
    private final Rect textBounds = new Rect();

    private float progress = 0f;
    private int viewWidth;
    private ValueAnimator animator;
    private boolean isAnimating = false;

    public GlowTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(Color.GRAY);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setTextAlign(Paint.Align.LEFT);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setTextAlign(Paint.Align.LEFT);
        glowPaint.setMaskFilter(new BlurMaskFilter(20, BlurMaskFilter.Blur.NORMAL));

        bgGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgGlowPaint.setStyle(Paint.Style.FILL);
        bgGlowPaint.setMaskFilter(new BlurMaskFilter(25, BlurMaskFilter.Blur.NORMAL));
        bgGlowPaint.setAlpha(100);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        String text = getText().toString();

        // 1. Setup Paints
        float textSize = getTextSize();
        basePaint.setTextSize(textSize);
        glowPaint.setTextSize(textSize);
        basePaint.setTypeface(getTypeface());
        glowPaint.setTypeface(getTypeface());

        Paint.FontMetrics fm = basePaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float y = (getHeight() + textHeight) / 2 - fm.descent;
        float textWidth = basePaint.measureText(text);
        float textX = (getWidth() - textWidth) / 2;

        // 2. Draw Gray Base
        basePaint.setColor(Color.GRAY);
        basePaint.setShader(null);
        canvas.drawText(text, textX, y, basePaint);

        // --- 3. BACKGROUND GLOW LOGIC ---

        // A. Handle Fading
        float fadeStart = 0.55f; 
        int baseAlpha = 100;
        int currentAlpha = baseAlpha;
        if (progress > fadeStart) {
            float fadeProgress = (progress - fadeStart) / (1f - fadeStart);
            currentAlpha = (int) (baseAlpha * (1 - fadeProgress));
        }
        bgGlowPaint.setAlpha(currentAlpha);

        // B. Calculate WIPE Position
        float bgEdgeWidth = 300f; 
        float startCutoff = textX;
        float endCutoff = textX + textWidth + bgEdgeWidth;
        float totalTravel = endCutoff - startCutoff;
        float currentX = startCutoff + (progress * totalTravel);

        // C. Shaders
        // Note: Creating Shaders in onDraw is expensive but unavoidable for dynamic gradients 
        // unless you use a Matrix to translate a single static Shader (Advanced Optimization).
        // For now, the Path optimization below is the most critical one.
        
        LinearGradient staticGradient = new LinearGradient(
                textX, 0,
                textX + textWidth, 0,
                new int[]{Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT},
                new float[]{0.0f, 0.1f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP
        );

        LinearGradient wipeGradient = new LinearGradient(
                currentX - bgEdgeWidth, 0,
                currentX, 0,
                new int[]{Color.WHITE, Color.TRANSPARENT},
                null, 
                Shader.TileMode.CLAMP
        );

        ComposeShader combinedShader = new ComposeShader(staticGradient, wipeGradient, PorterDuff.Mode.MULTIPLY);
        bgGlowPaint.setShader(combinedShader);

        // D. Draw FULL Octagon Path (OPTIMIZED)
        if (progress > 0 && currentAlpha > 0) {
            // OPTIMIZATION: Reset the existing path instead of creating a new one
            bgPath.reset();

            float padTop = -5f;    
            float padBottom = 10f; 
            float padH = 20f;
            float verticalShift = 8f;

            // Reuse the class-level Rect object
            basePaint.getTextBounds(text, 0, text.length(), textBounds);

            float L = textX - padH;
            float T = y + textBounds.top + padTop + verticalShift; 
            float R = textX + textWidth + padH;
            float B = y + textBounds.bottom + padBottom + verticalShift; 

            float cut = 50f; 
            float effectiveCut = Math.min(cut, (R - L) / 2);

            bgPath.moveTo(L, T + effectiveCut);
            bgPath.lineTo(L + effectiveCut, T);
            bgPath.lineTo(R - effectiveCut, T);
            bgPath.lineTo(R, T + effectiveCut);
            bgPath.lineTo(R, B - effectiveCut);
            bgPath.lineTo(R - effectiveCut, B);
            bgPath.lineTo(L + effectiveCut, B);
            bgPath.lineTo(L, B - effectiveCut);
            bgPath.close();

            canvas.drawPath(bgPath, bgGlowPaint);
        }

        // ----------------------------------------

        // 4. Text Fill Logic
        float textEdgeWidth = 120f;
        float textEndCutoff = textX + textWidth + textEdgeWidth;
        float textTotalTravel = textEndCutoff - startCutoff;
        float textCurrentX = startCutoff + (progress * textTotalTravel);

        LinearGradient textFillGradient = new LinearGradient(
                textCurrentX - textEdgeWidth, 0,
                textCurrentX, 0,
                new int[]{Color.WHITE, Color.TRANSPARENT},
                null,
                Shader.TileMode.CLAMP
        );

        // 5. Draw White Text Fill
        Paint fillPaint = new Paint(basePaint);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShader(textFillGradient);
        canvas.drawText(text, textX, y, fillPaint);

        // 6. Draw Text Bloom (Foreground)
        glowPaint.setShader(textFillGradient);
        canvas.drawText(text, textX, y, glowPaint);
    }
    
    // ... startSweep, resetAndRestart, blendColor, etc. remain the same ...
    private void startSweep() {
        if (viewWidth <= 0) return;
        if (isAnimating) return;
        isAnimating = true;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(
                a -> {
                    progress = (float) a.getAnimatedValue();
                    invalidate();
                });

        animator.addListener(
                new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        postDelayed(() -> resetAndRestart(), 1000);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });

        animator.start();
    }

    private void resetAndRestart() {
        ValueAnimator fadeBack = ValueAnimator.ofFloat(1f, 0f);
        fadeBack.setDuration(1000);
        fadeBack.setInterpolator(new LinearInterpolator());
        fadeBack.addUpdateListener(
                a -> {
                    float fadeProgress = (float) a.getAnimatedValue();
                    basePaint.setColor(blendColor(Color.GRAY, Color.WHITE, fadeProgress));
                    invalidate();
                });
        fadeBack.addListener(
                new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progress = 0f;
                        isAnimating = false;
                        startSweep();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });
        fadeBack.start();
    }

    private int blendColor(int startColor, int endColor, float ratio) {
        int alpha = (int) (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * ratio);
        int red = (int) (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * ratio);
        int green = (int) (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * ratio);
        int blue = (int) (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::startSweep);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
