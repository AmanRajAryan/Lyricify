package aman.lyricify;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class KaraokeWordView extends View {

    private Paint basePaint;
    private Paint fillPaint;
    private Paint bloomPaint;

    private LinearGradient fillGradient;
    private Matrix shaderMatrix = new Matrix();
    
    private float progress = 0f;
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

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setTextAlign(Paint.Align.LEFT);
        fillPaint.setTypeface(typeface);
        fillPaint.setTextSize(textSize);

        bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bloomPaint.setStyle(Paint.Style.FILL);
        bloomPaint.setTextAlign(Paint.Align.LEFT);
        bloomPaint.setTypeface(typeface);
        bloomPaint.setTextSize(textSize);

        bloomPaint.setMaskFilter(new BlurMaskFilter(20, BlurMaskFilter.Blur.NORMAL));
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            fillGradient = new LinearGradient(
                    0, 0, 100, 0,
                    new int[]{fillColor, Color.TRANSPARENT},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (word.isEmpty()) {
            setMeasuredDimension(0, 0);
            return;
        }
        float textWidth = basePaint.measureText(word);
        Paint.FontMetrics fm = basePaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        int padding = 40; 
        
        int width = (int) (textWidth + getPaddingLeft() + getPaddingRight() + padding);
        int height = (int) (textHeight + getPaddingTop() + getPaddingBottom() + padding);

        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (word.isEmpty()) return;

        String text = word;
        float textWidth = basePaint.measureText(text);
        Paint.FontMetrics fm = basePaint.getFontMetrics();
        
        float x = (getWidth() - textWidth) / 2f;
        float y = (getHeight() / 2f) - ((fm.descent + fm.ascent) / 2f);

        basePaint.setColor(baseColor);
        canvas.drawText(text, x, y, basePaint);

        if (progress > 0f) {
            float edgeWidth = 120f;
            
            float totalDistance = textWidth + edgeWidth;
            float currentPos = x + (totalDistance * progress);

            if (fillGradient != null) {
                shaderMatrix.reset();
                shaderMatrix.setScale(edgeWidth / 100f, 1f); 
                shaderMatrix.postTranslate(currentPos - edgeWidth, 0);
                
                fillGradient.setLocalMatrix(shaderMatrix);
                
                fillPaint.setShader(fillGradient);
                bloomPaint.setShader(fillGradient);
            }

            canvas.drawText(text, x, y, fillPaint);

            if (progress < 1.0f) {
                float bloomAlpha = 1.0f;
                if (progress >= 0.8f) {
                    bloomAlpha = (1.0f - progress) / 0.2f; 
                }
                bloomAlpha = Math.max(0f, bloomAlpha);

                bloomPaint.setAlpha((int) (255 * bloomAlpha));
                canvas.drawText(text, x, y, bloomPaint);
            }
        }
    }

    public void setWord(String word) {
        this.word = word != null ? word : "";
        requestLayout();
        invalidate();
    }

    public void setWordTextSize(float size) {
        this.textSize = size;
        basePaint.setTextSize(size);
        fillPaint.setTextSize(size);
        bloomPaint.setTextSize(size);
        requestLayout();
        invalidate();
    }

    public void setWordTypeface(Typeface typeface) {
        this.typeface = typeface;
        basePaint.setTypeface(typeface);
        fillPaint.setTypeface(typeface);
        bloomPaint.setTypeface(typeface);
        requestLayout();
        invalidate();
    }

    public void setColors(int baseColor, int fillColor) {
        this.baseColor = baseColor;
        this.fillColor = fillColor;
        basePaint.setColor(baseColor);
        // Re-create gradient next draw/size change with new colors if needed
        onSizeChanged(getWidth(), getHeight(), 0, 0);
        invalidate();
    }
}
