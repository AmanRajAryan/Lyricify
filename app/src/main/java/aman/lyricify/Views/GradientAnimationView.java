package aman.lyricify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class GradientAnimationView extends View {

    private Paint paint;
    private LinearGradient gradient;
    private Matrix gradientMatrix;
    private float translate = 0f;

    private float translateWrapBound = 0f;

    private final int[] colors = new int[]{
            Color.parseColor("#4A00E0"),
            Color.parseColor("#8E2DE2"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#4A00E0")
    };

    public GradientAnimationView(Context context) { this(context, null); }
    public GradientAnimationView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public GradientAnimationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientMatrix = new Matrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradient = new LinearGradient(0, 0, w, h, colors, null, Shader.TileMode.MIRROR);
        paint.setShader(gradient);
        translateWrapBound = w * 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        translate += 3.0f;
        if (translate > translateWrapBound) {
            translate = 0;
        }

        gradientMatrix.setTranslate(translate, translate);
        gradient.setLocalMatrix(gradientMatrix);

        final int w = getWidth();
        final int h = getHeight();
        canvas.drawRect(0, 0, w, h, paint);

        postInvalidateOnAnimation();
    }
}
