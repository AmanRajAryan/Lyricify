package aman.lyricify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SectionIndexer;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FastScroller extends View {

    private static final int HANDLE_WIDTH = 60;
    private static final int HANDLE_HEIGHT = 100;

    private static final int BUBBLE_HEIGHT = 120;
    private static final int MIN_BUBBLE_WIDTH = 120;
    private static final int BUBBLE_PADDING_HORIZONTAL = 60;

    private RecyclerView recyclerView;
    private SectionIndexer sectionIndexer;

    private final Paint handlePaint;
    private final Paint bubblePaint;
    private final Paint textPaint;

    private boolean isDragging = false;
    private String currentSectionText = "";
    private float currentY = 0;

    // Reusable rects to avoid allocation in onDraw
    private final RectF handleRect = new RectF();
    private final RectF bubbleRect = new RectF();
    private final Rect textBounds = new Rect();

    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    public FastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);

        handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#BB86FC"));
        handlePaint.setAntiAlias(true);
        handlePaint.setAlpha(180);

        bubblePaint = new Paint();
        bubblePaint.setColor(Color.parseColor("#BB86FC"));
        bubblePaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(60);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void attachToRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        if (recyclerView.getAdapter() instanceof SectionIndexer) {
            sectionIndexer = (SectionIndexer) recyclerView.getAdapter();
        }
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!isDragging) {
                    updateHandlePosition();
                }
            }
        });
    }

    private void updateHandlePosition() {
        if (recyclerView == null || recyclerView.getAdapter() == null) return;

        int verticalScrollOffset = recyclerView.computeVerticalScrollOffset();
        int verticalScrollRange = recyclerView.computeVerticalScrollRange();
        int verticalScrollExtent = recyclerView.computeVerticalScrollExtent();

        int scrollableRange = verticalScrollRange - verticalScrollExtent;
        if (scrollableRange <= 0) return;

        float ratio = (float) verticalScrollOffset / scrollableRange;
        float scrollableHeight = getHeight() - HANDLE_HEIGHT;
        currentY = ratio * scrollableHeight;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (recyclerView == null) return;

        float width = getWidth();

        int scrollRange = recyclerView.computeVerticalScrollRange();
        if (scrollRange > recyclerView.getHeight()) {

            handleRect.set(width - HANDLE_WIDTH, currentY, width, currentY + HANDLE_HEIGHT);
            canvas.drawRoundRect(handleRect, 10, 10, handlePaint);

            if (isDragging && !currentSectionText.isEmpty()) {

                textPaint.getTextBounds(currentSectionText, 0, currentSectionText.length(), textBounds);
                float textWidth = textBounds.width();

                float bubbleWidth = Math.max(MIN_BUBBLE_WIDTH, textWidth + (BUBBLE_PADDING_HORIZONTAL * 2));

                float bubbleX = width - HANDLE_WIDTH - bubbleWidth - 40;
                float bubbleCenterY = currentY + (HANDLE_HEIGHT / 2f);
                float bubbleTop = bubbleCenterY - (BUBBLE_HEIGHT / 2f);
                float bubbleBottom = bubbleTop + BUBBLE_HEIGHT;

                if (bubbleTop < 0) {
                    bubbleTop = 0;
                    bubbleBottom = BUBBLE_HEIGHT;
                }
                if (bubbleBottom > getHeight()) {
                    bubbleBottom = getHeight();
                    bubbleTop = getHeight() - BUBBLE_HEIGHT;
                }

                bubbleRect.set(bubbleX, bubbleTop, bubbleX + bubbleWidth, bubbleBottom);
                canvas.drawRoundRect(bubbleRect, BUBBLE_HEIGHT / 2f, BUBBLE_HEIGHT / 2f, bubblePaint);

                textPaint.getFontMetrics(fontMetrics);
                float textY = bubbleRect.centerY() - (fontMetrics.descent + fontMetrics.ascent) / 2;
                canvas.drawText(currentSectionText, bubbleRect.centerX(), textY, textPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getX() < getWidth() - (HANDLE_WIDTH * 2)) return false;

                isDragging = true;
                handlePaint.setAlpha(255);
                getParent().requestDisallowInterceptTouchEvent(true);
                scrollTo(event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    scrollTo(event.getY());
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                currentSectionText = "";
                handlePaint.setAlpha(180);
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void scrollTo(float y) {
        if (recyclerView == null || recyclerView.getAdapter() == null) return;

        int itemCount = recyclerView.getAdapter().getItemCount();
        if (itemCount == 0) return;

        float scrollableHeight = getHeight() - HANDLE_HEIGHT;
        float touchedY = Math.max(0, Math.min(y - (HANDLE_HEIGHT / 2f), scrollableHeight));

        currentY = touchedY;
        float ratio = currentY / scrollableHeight;

        int targetPosition = (int) (ratio * (itemCount - 1));
        targetPosition = Math.max(0, Math.min(targetPosition, itemCount - 1));

        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(targetPosition, 0);
        } else {
            recyclerView.scrollToPosition(targetPosition);
        }

        if (sectionIndexer != null) {
            int sectionIndex = sectionIndexer.getSectionForPosition(targetPosition);
            Object[] sections = sectionIndexer.getSections();

            if (sections != null && sectionIndex >= 0 && sectionIndex < sections.length) {
                currentSectionText = sections[sectionIndex].toString();
            }
        }

        invalidate();
    }
}
