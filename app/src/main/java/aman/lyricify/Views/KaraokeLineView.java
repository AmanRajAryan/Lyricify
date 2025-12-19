package aman.lyricify;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for karaoke line with individual word views
 * Each word animates itself for its calculated duration
 */
public class KaraokeLineView extends LinearLayout {
    
    private LyricLineModels.KaraokeLine line;
    private List<KaraokeWordView> wordViews = new ArrayList<>();
    private Typeface typeface;
    private int highlightTextSize;
    private int currentWordIndex = -1;
    
    public KaraokeLineView(Context context, LyricLineModels.KaraokeLine line, 
                          Typeface typeface, int highlightTextSize) {
        super(context);
        this.line = line;
        this.typeface = typeface;
        this.highlightTextSize = highlightTextSize;
        
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
        setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setPadding(24, 20, 24, 20);
        
        createWordViews();
    }
    
    private void createWordViews() {
        removeAllViews();
        wordViews.clear();
        
        float textSize = highlightTextSize * getResources().getDisplayMetrics().scaledDensity;
        
        for (int i = 0; i < line.words.size(); i++) {
            LyricLineModels.KaraokeWord karaokeWord = line.words.get(i);
            
            KaraokeWordView wordView = new KaraokeWordView(getContext());
            wordView.setWord(karaokeWord.text);
            wordView.setWordTextSize(textSize);
            wordView.setWordTypeface(Typeface.create(typeface, Typeface.BOLD));
            wordView.setColors(Color.GRAY, Color.WHITE);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(4, 0, 4, 0);
            wordView.setLayoutParams(params);
            
            addView(wordView);
            wordViews.add(wordView);
        }
    }
    
    /**
     * Trigger animation for specific word based on current playback position
     * @param positionMs Current playback position in milliseconds
     */
    public void updatePosition(long positionMs) {
        // Find which word should be animating
        int newWordIndex = -1;
        
        for (int i = 0; i < line.words.size(); i++) {
            LyricLineModels.KaraokeWord word = line.words.get(i);
            long wordStart = word.timestamp;
            long wordEnd = (i + 1 < line.words.size()) ? 
                line.words.get(i + 1).timestamp : (wordStart + 1000);
            
            if (positionMs >= wordStart && positionMs < wordEnd) {
                newWordIndex = i;
                break;
            } else if (positionMs >= wordEnd && i < line.words.size() - 1) {
                // Word is complete
                continue;
            } else if (positionMs >= wordEnd && i == line.words.size() - 1) {
                // Last word is complete
                newWordIndex = i;
                break;
            }
        }
        
        // If word changed, start new animation
        if (newWordIndex != currentWordIndex && newWordIndex >= 0) {
            currentWordIndex = newWordIndex;
            
            // Calculate word duration
            LyricLineModels.KaraokeWord currentWord = line.words.get(newWordIndex);
            long wordStart = currentWord.timestamp;
            long wordEnd = (newWordIndex + 1 < line.words.size()) ? 
                line.words.get(newWordIndex + 1).timestamp : (wordStart + 1000);
            long wordDuration = wordEnd - wordStart;
            
            // Start animation for current word
            wordViews.get(newWordIndex).startAnimation(wordDuration);
            
            // Complete all previous words immediately
            for (int i = 0; i < newWordIndex; i++) {
                wordViews.get(i).completeImmediately();
            }
            
            // Reset all future words
            for (int i = newWordIndex + 1; i < wordViews.size(); i++) {
                wordViews.get(i).reset();
            }
        }
    }
    
    /**
     * Alternative: Manual progress update (for backward compatibility)
     */
    public void updateProgress(float progress, int wordIndex) {
        // This method is kept for compatibility but not recommended
        // The updatePosition() method should be used instead
    }
    
    public void updateTypeface(Typeface typeface) {
        this.typeface = typeface;
        
        float textSize = highlightTextSize * getResources().getDisplayMetrics().scaledDensity;
        
        for (KaraokeWordView wordView : wordViews) {
            wordView.setWordTypeface(Typeface.create(typeface, Typeface.BOLD));
            wordView.setWordTextSize(textSize);
        }
        
        requestLayout();
        invalidate();
    }
    
    public void resetAnimation() {
        for (KaraokeWordView wordView : wordViews) {
            wordView.reset();
        }
        currentWordIndex = -1;
    }
    
    public void setColors(int baseColor, int fillColor) {
        for (KaraokeWordView wordView : wordViews) {
            wordView.setColors(baseColor, fillColor);
        }
    }
}