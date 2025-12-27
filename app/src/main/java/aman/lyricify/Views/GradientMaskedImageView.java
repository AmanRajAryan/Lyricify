package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class GradientMaskedImageView extends AppCompatImageView {

    private Paint maskPaint;
    private LinearGradient gradient;

    public GradientMaskedImageView(Context context) {
        super(context);
        init();
    }

    public GradientMaskedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GradientMaskedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Create gradient: opaque (top) -> transparent (middle) -> opaque (bottom)
        // More blur coverage at top with stronger effect
        
        gradient = new LinearGradient(
                0, 0,           // start X, Y
                0, h,           // end X, Y
                new int[]{
                        0xFFFFFFFF,  // Fully opaque at top (position 0f)
                        0xFFFFFFFF,  // Stay opaque longer (position 0.15f) 
                        0x00FFFFFF,  // Transparent starts (position 0.4f)
                        0x00FFFFFF,  // Transparent ends (position 0.75f)
                        0xFFFFFFFF   // Opaque at bottom (position 1f)
                },
                new float[]{0f, 0.1f, 0.2f, 0.85f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the image normally first
        super.onDraw(canvas);
        
        // Apply the gradient mask
        if (gradient != null) {
            maskPaint.setShader(gradient);
            canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        }
    }
}