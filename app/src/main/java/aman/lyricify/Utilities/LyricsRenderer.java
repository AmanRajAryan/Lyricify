package aman.lyricify;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Handles rendering of lyric lines with karaoke word animation support
 */
public class LyricsRenderer {
    
    private Context context;
    private int normalTextSize;
    private int highlightTextSize;
    private int normalTextColor;
    private Typeface currentTypeface;
    
    public LyricsRenderer(Context context, int normalTextSize, int highlightTextSize, 
                         int normalTextColor, Typeface typeface) {
        this.context = context;
        this.normalTextSize = normalTextSize;
        this.highlightTextSize = highlightTextSize;
        this.normalTextColor = normalTextColor;
        this.currentTypeface = typeface;
    }
    
    /**
     * Create a karaoke line view with animated words
     */
    public View createKaraokeLineView(LyricLineModels.KaraokeLine line, 
                                      View.OnClickListener clickListener) {
        KaraokeLineView lineView = new KaraokeLineView(
            context, line, currentTypeface, highlightTextSize
        );
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 16, 0, 16);
        lineView.setLayoutParams(params);
        lineView.setClickable(true);
        lineView.setOnClickListener(clickListener);
        
        line.karaokeLineView = lineView;
        return lineView;
    }
    
    /**
     * Create a standard lyric line view
     */
    public TextView createStandardLineView(LyricLineModels.LyricLine line,
                                           View.OnClickListener clickListener) {
        TextView textView = new TextView(context);
        textView.setText(line.text);
        textView.setTextColor(normalTextColor);
        textView.setTextSize(normalTextSize);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(16, 16, 16, 16);
        textView.setTypeface(currentTypeface, Typeface.BOLD);
        textView.setMaxLines(Integer.MAX_VALUE);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        textView.setLayoutParams(params);
        textView.setClickable(true);
        textView.setOnClickListener(clickListener);
        
        line.textView = textView;
        return textView;
    }
    
    /**
     * Create a message view
     */
    public TextView createMessageView(String message) {
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextColor(Color.parseColor("#AAAAAA"));
        textView.setTextSize(18);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(32, 32, 32, 32);
        return textView;
    }
    
    /**
     * Update typeface
     */
    public void setTypeface(Typeface typeface) {
        this.currentTypeface = typeface;
    }
    
    public Typeface getTypeface() {
        return currentTypeface;
    }
}