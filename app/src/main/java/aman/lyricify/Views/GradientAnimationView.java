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
    
    // Config: Colors for the animation (Deep Purple -> Blue -> Cyan -> Purple)
    private final int[] colors = new int[]{
            Color.parseColor("#4A00E0"), // Deep Purple
            Color.parseColor("#8E2DE2"), // Violet
            Color.parseColor("#00E5FF"), // Cyan (V2 Match)
            Color.parseColor("#4A00E0")  // Loop back to start
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
        // Create a gradient that is larger than the screen to allow scrolling
        gradient = new LinearGradient(0, 0, w, h, colors, null, Shader.TileMode.MIRROR);
        paint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Move the gradient
        translate += 3.0f; // Speed of animation
        
        // 2. Reset if it goes too far (simple seamless loop logic depends on gradient size, 
        // but Matrix rotation/translation is easier for abstract backgrounds)
        if (translate > getWidth() * 2) {
            translate = 0;
        }

        // 3. Apply Transformation
        gradientMatrix.setTranslate(translate, translate); // Move diagonally
        // Optional: Add slow rotation for more dynamic feel
        // gradientMatrix.postRotate(translate / 10f, getWidth()/2f, getHeight()/2f); 
        
        gradient.setLocalMatrix(gradientMatrix);

        // 4. Draw
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // 5. Loop
        postInvalidateOnAnimation();
    }
}
