package aman.lyricify;

import android.content.Context;
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
        LinearGradient gradient = new LinearGradient(
                0, 0,
                0, h,
                new int[]{
                        0xFFFFFFFF,
                        0xFFFFFFFF,
                        0x00FFFFFF,
                        0x00FFFFFF,
                        0xFFFFFFFF
                },
                new float[]{0f, 0.1f, 0.2f, 0.85f, 1f},
                Shader.TileMode.CLAMP
        );

        maskPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (maskPaint.getShader() != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        }
    }
}
