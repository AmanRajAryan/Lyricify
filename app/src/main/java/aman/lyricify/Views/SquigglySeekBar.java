package aman.lyricify;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

public class SquigglySeekBar extends AppCompatSeekBar {

    private boolean USE_VERTICAL_BAR_THUMB = true;

    private float WAVE_AMPLITUDE = 7f;
    private float WAVE_SPEED = 0.07f;
    private float WAVE_FREQUENCY = 10f;
    private float STROKE_WIDTH = 12f;
    private float TAPER_LENGTH = 100f;
    private float FADE_LENGTH = 100f;

    private float AMPLITUDE_TRANSITION_SPEED = 0.5f;
    private float DEFAULT_AMPLITUDE = 7f;

    private float THUMB_RADIUS = 18f;
    private float BAR_WIDTH = 10f;
    private float BAR_HEIGHT = 50f;
    private float BAR_CORNER = 3f;

    private Paint activePaint;
    private Paint inactivePaint;
    private Paint thumbPaint;
    private Path activePath;

    private float phase = 0f;
    private float currentAnimAmplitude = 0f;
    private boolean isPlaying = false;

    private long lastTouchUpTime = 0;

    private final RectF barRect = new RectF();

    private float TWO_PI_WAVE_FREQ;
    private float TWO_PI_WAVE_FREQ_SLOW;

    public SquigglySeekBar(@NonNull Context context) {
        super(context);
        init();
    }

    public SquigglySeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SquigglySeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeWidth(STROKE_WIDTH);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setColor(Color.WHITE);

        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(Color.parseColor("#50FFFFFF"));
        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setStrokeWidth(STROKE_WIDTH);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setStyle(Paint.Style.FILL);

        activePath = new Path();

        SharedPreferences prefs = getContext().getSharedPreferences("LyricifyPrefs", Context.MODE_PRIVATE);
        boolean isSquigglyEnabled = prefs.getBoolean("squiggly_seekbar_enabled", true);

        if (isSquigglyEnabled) {
            WAVE_AMPLITUDE = DEFAULT_AMPLITUDE;
        } else {
            WAVE_AMPLITUDE = 0f;
        }

        updateWaveConstants();

        setThumb(null);
        setBackground(null);
        setProgressDrawable(null);
    }

    private void updateWaveConstants() {
        TWO_PI_WAVE_FREQ      = (float) (Math.PI * 2 * WAVE_FREQUENCY);
        TWO_PI_WAVE_FREQ_SLOW = (float) (Math.PI * 2 * WAVE_FREQUENCY * 0.5);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) return;
        LinearGradient fadeGradient = new LinearGradient(
                0, 0, FADE_LENGTH, 0,
                new int[]{Color.TRANSPARENT, Color.WHITE},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        activePaint.setShader(fadeGradient);
    }

    public void startAnimation() {
        if (!isPlaying) {
            isPlaying = true;
            invalidate();
        }
    }

    public void stopAnimation() {
        if (isPlaying) {
            isPlaying = false;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        boolean result = super.onTouchEvent(event);

        if (event.getAction() == android.view.MotionEvent.ACTION_UP ||
            event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
            lastTouchUpTime = System.currentTimeMillis();
        }

        return result;
    }

    @Override
    public synchronized void setProgress(int progress) {
        if (isPressed()) {
            return;
        }

        // Grace period: ignore updates right after touch up to let media player finish seeking.
        if (System.currentTimeMillis() - lastTouchUpTime < 500) {
            return;
        }

        super.setProgress(progress);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        final float width = getWidth();
        final float height = getHeight();
        final int maxProgress = getMax();
        final float centerY = height / 2f;

        float progressRatio = (maxProgress > 0) ? (float) getProgress() / maxProgress : 0f;
        float thumbX = width * progressRatio;

        float targetAmplitude = isPlaying ? WAVE_AMPLITUDE : 0f;
        if (currentAnimAmplitude < targetAmplitude) {
            currentAnimAmplitude += AMPLITUDE_TRANSITION_SPEED;
            if (currentAnimAmplitude > targetAmplitude) currentAnimAmplitude = targetAmplitude;
        } else if (currentAnimAmplitude > targetAmplitude) {
            currentAnimAmplitude -= AMPLITUDE_TRANSITION_SPEED;
            if (currentAnimAmplitude < targetAmplitude) currentAnimAmplitude = targetAmplitude;
        }

        canvas.drawLine(thumbX, centerY, width, centerY, inactivePaint);

        activePath.reset();
        boolean isFirstPoint = true;

        final float invWidth = 1f / width;

        for (float x = 0; x <= thumbX; x += 5) {
            float xRatio = x * invWidth;
            float angle1 = xRatio * TWO_PI_WAVE_FREQ + phase;
            float angle2 = xRatio * TWO_PI_WAVE_FREQ_SLOW - (phase * 0.3f);
            float combinedWave = (float) Math.sin(angle1) + 0.2f * (float) Math.sin(angle2);

            float distToKnob = thumbX - x;
            float envelope = 1.0f;
            if (distToKnob < TAPER_LENGTH) {
                float rawRatio = distToKnob / TAPER_LENGTH;
                envelope = rawRatio * rawRatio * (3 - 2 * rawRatio);
            }

            float y = centerY + (currentAnimAmplitude * envelope) * combinedWave;

            if (isFirstPoint) {
                activePath.moveTo(x, y);
                isFirstPoint = false;
            } else {
                activePath.lineTo(x, y);
            }
        }
        canvas.drawPath(activePath, activePaint);

        if (USE_VERTICAL_BAR_THUMB) {
            float barLeft = thumbX - (BAR_WIDTH / 2);
            float barRight = thumbX + (BAR_WIDTH / 2);
            float barTop = centerY - (BAR_HEIGHT / 2);
            float barBottom = centerY + (BAR_HEIGHT / 2);
            barRect.set(barLeft, barTop, barRight, barBottom);
            canvas.drawRoundRect(barRect, BAR_CORNER, BAR_CORNER, thumbPaint);
        } else {
            canvas.drawCircle(thumbX, centerY, THUMB_RADIUS, thumbPaint);
        }

        boolean isTransitioning = (Math.abs(currentAnimAmplitude - targetAmplitude) > 0.01f);
        if (isPlaying || isTransitioning) {
            phase += WAVE_SPEED;
            postInvalidateOnAnimation();
        }
    }
}
