package aman.lyricify;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class KaraokeLineView extends LinearLayout {

    private LyricLineModels.KaraokeLine line;
    private List<KaraokeWordView> wordViews = new ArrayList<>();
    private Typeface typeface;
    private int highlightTextSize;
    
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
        setPadding(16, 10, 16, 10);
        
        setClipChildren(false);
        setClipToPadding(false);
        
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
            params.setMargins(8, 0, 8, 0);
            wordView.setLayoutParams(params);
            
            addView(wordView);
            wordViews.add(wordView);
        }
    }
    
    public void updatePosition(long positionMs) {
        for (int i = 0; i < line.words.size(); i++) {
            LyricLineModels.KaraokeWord word = line.words.get(i);
            long wordStart = word.timestamp;
            
            long wordEnd;
            if (i + 1 < line.words.size()) {
                wordEnd = line.words.get(i + 1).timestamp;
            } else {
                wordEnd = wordStart + 1000;
            }
            long duration = wordEnd - wordStart;
            
            float progress;
            if (positionMs < wordStart) {
                progress = 0f;
            } else if (positionMs >= wordEnd) {
                progress = 1f;
            } else {
                progress = (float) (positionMs - wordStart) / (float) duration;
            }
            
            wordViews.get(i).setProgress(progress);
        }
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
            wordView.setProgress(0f);
        }
    }
    
    public void setColors(int baseColor, int fillColor) {
        for (KaraokeWordView wordView : wordViews) {
            wordView.setColors(baseColor, fillColor);
        }
    }
}
