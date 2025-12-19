package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for displaying synced lyrics with karaoke word animation
 */
public class SyncedLyricsView extends FrameLayout {
    
    private ImageView blurredBackground;
    private ScrollView scrollView;
    private LinearLayout lyricsContainer;
    private List<LyricLineModels.LyricLine> lyricLines;
    private int currentLineIndex = -1;
    private TextView currentHighlightedView;
    
    // Colors and sizes
    private int normalTextColor = Color.parseColor("#AAAAAA");
    private int highlightTextColor = Color.parseColor("#FFFFFF");
    private int normalTextSize = 22;
    private int highlightTextSize = 28;
    
    // Mode
    private boolean isKaraokeMode = false;
    
    // Font selection
    private int currentFontIndex = 0;
    private static final Typeface[] AVAILABLE_FONTS = {
        Typeface.DEFAULT,
        Typeface.SANS_SERIF,
        Typeface.SERIF,
        Typeface.MONOSPACE,
        Typeface.create("casual", Typeface.NORMAL),
        Typeface.create("cursive", Typeface.NORMAL)
    };
    private static final String[] FONT_NAMES = {
        "Default", "Sans Serif", "Serif", "Monospace", "Casual", "Cursive"
    };
    
    // Helpers
    private LyricsRenderer renderer;
    private SeekListener seekListener;
    
    public interface SeekListener {
        void onSeek(long positionMs);
    }
    
    public SyncedLyricsView(Context context) {
        super(context);
        init();
    }
    
    public SyncedLyricsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SyncedLyricsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Blurred background
        blurredBackground = new ImageView(getContext());
        blurredBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurredBackground.setAlpha(0.3f);
        addView(blurredBackground, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // Scroll view
        scrollView = new ScrollView(getContext());
        scrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        scrollView.setVerticalScrollBarEnabled(false);
        addView(scrollView, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // Lyrics container
        lyricsContainer = new LinearLayout(getContext());
        lyricsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricsContainer.setPadding(32, 200, 32, 200);
        scrollView.addView(lyricsContainer, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        lyricLines = new ArrayList<>();
        renderer = new LyricsRenderer(getContext(), normalTextSize, highlightTextSize,
                                     normalTextColor, AVAILABLE_FONTS[0]);
    }
    
    public void setArtwork(Bitmap artwork) {
        if (artwork != null) {
            Bitmap blurred = BlurHelper.blur(getContext(), artwork, 25);
            blurredBackground.setImageBitmap(blurred);
        } else {
            blurredBackground.setImageResource(0);
        }
    }
    
    public void setSeekListener(SeekListener listener) {
        this.seekListener = listener;
    }
    
    public String cycleFont() {
        currentFontIndex = (currentFontIndex + 1) % AVAILABLE_FONTS.length;
        Typeface newFont = AVAILABLE_FONTS[currentFontIndex];
        renderer.setTypeface(newFont);
        
        // Update existing views
        for (LyricLineModels.LyricLine line : lyricLines) {
            if (isKaraokeMode && line instanceof LyricLineModels.KaraokeLine) {
                LyricLineModels.KaraokeLine kLine = (LyricLineModels.KaraokeLine) line;
                if (kLine.karaokeLineView != null) {
                    kLine.karaokeLineView.updateTypeface(newFont);
                }
            } else if (line.textView != null) {
                line.textView.setTypeface(newFont, Typeface.BOLD);
            }
        }
        
        return FONT_NAMES[currentFontIndex];
    }
    
    public void setLyrics(String lrcText) {
        lyricLines.clear();
        lyricsContainer.removeAllViews();
        currentLineIndex = -1;
        currentHighlightedView = null;
        
        if (lrcText == null || lrcText.isEmpty()) {
            lyricsContainer.addView(renderer.createMessageView("No lyrics available"));
            return;
        }
        
        // Parse lyrics
        isKaraokeMode = SyncedLyricsParser.isKaraokeFormat(lrcText);
        if (isKaraokeMode) {
            lyricLines.addAll(SyncedLyricsParser.parseKaraokeLyrics(lrcText));
        } else {
            lyricLines.addAll(SyncedLyricsParser.parseLrcLyrics(lrcText));
        }
        
        if (lyricLines.isEmpty()) {
            lyricsContainer.addView(renderer.createMessageView("No synced lyrics available"));
            return;
        }
        
        // Create views
        for (int i = 0; i < lyricLines.size(); i++) {
            LyricLineModels.LyricLine line = lyricLines.get(i);
            
            View.OnClickListener clickListener = v -> {
                if (seekListener != null) {
                    seekListener.onSeek(line.timestamp);
                }
            };
            
            if (isKaraokeMode && line instanceof LyricLineModels.KaraokeLine) {
                View view = renderer.createKaraokeLineView(
                    (LyricLineModels.KaraokeLine) line, clickListener
                );
                lyricsContainer.addView(view);
            } else {
                TextView view = renderer.createStandardLineView(line, clickListener);
                lyricsContainer.addView(view);
            }
        }
    }
    
    public void updatePosition(long positionMs) {
        if (lyricLines.isEmpty()) return;
        
        long adjustedPosition = positionMs + 200;
        
        if (isKaraokeMode) {
            updateKaraokePosition(adjustedPosition);
        } else {
            updateStandardPosition(adjustedPosition);
        }
    }
    
    private void updateKaraokePosition(long positionMs) {
        int newLineIndex = -1;
        for (int i = lyricLines.size() - 1; i >= 0; i--) {
            if (positionMs >= lyricLines.get(i).timestamp) {
                newLineIndex = i;
                break;
            }
        }
        
        if (newLineIndex != currentLineIndex) {
            if (currentLineIndex >= 0 && currentLineIndex < lyricLines.size() && 
                newLineIndex > currentLineIndex) {
                resetKaraokeLine((LyricLineModels.KaraokeLine) lyricLines.get(currentLineIndex));
            }
            
            if (newLineIndex >= 0) {
                scrollToLine(newLineIndex);
            }
            
            currentLineIndex = newLineIndex;
        }
        
        if (currentLineIndex >= 0 && currentLineIndex < lyricLines.size()) {
            highlightKaraokeWords((LyricLineModels.KaraokeLine) lyricLines.get(currentLineIndex), positionMs);
        }
    }
    
    private void updateStandardPosition(long positionMs) {
        int newLineIndex = -1;
        for (int i = lyricLines.size() - 1; i >= 0; i--) {
            if (positionMs >= lyricLines.get(i).timestamp) {
                newLineIndex = i;
                break;
            }
        }
        
        if (newLineIndex != currentLineIndex && newLineIndex >= 0) {
            highlightLine(newLineIndex);
            scrollToLine(newLineIndex);
            currentLineIndex = newLineIndex;
        }
    }
    
    private void highlightKaraokeWords(LyricLineModels.KaraokeLine line, long positionMs) {
        if (line.karaokeLineView == null) return;
        
        // Let the line view handle word-level animation timing
        line.karaokeLineView.updatePosition(positionMs);
    }
    
    private void resetKaraokeLine(LyricLineModels.KaraokeLine line) {
        if (line.karaokeLineView != null) {
            line.karaokeLineView.resetAnimation();
        }
    }
    
    private void highlightLine(int index) {
        if (currentHighlightedView != null) {
            currentHighlightedView.setTextColor(normalTextColor);
            currentHighlightedView.setTextSize(normalTextSize);
        }
        
        if (index >= 0 && index < lyricLines.size()) {
            TextView textView = lyricLines.get(index).textView;
            if (textView != null) {
                textView.setTextColor(highlightTextColor);
                textView.setTextSize(highlightTextSize);
                currentHighlightedView = textView;
            }
        }
    }
    
    private void scrollToLine(int index) {
        if (index < 0 || index >= lyricLines.size()) return;
        
        post(() -> {
            LyricLineModels.LyricLine line = lyricLines.get(index);
            View targetView = null;
            
            if (isKaraokeMode && line instanceof LyricLineModels.KaraokeLine) {
                targetView = ((LyricLineModels.KaraokeLine) line).karaokeLineView;
            } else {
                targetView = line.textView;
            }
            
            if (targetView != null) {
                int scrollY = targetView.getTop() - (scrollView.getHeight() / 2) + 
                            (targetView.getHeight() / 2);
                scrollView.smoothScrollTo(0, scrollY);
            }
        });
    }
    
    public void reset() {
        currentLineIndex = -1;
        
        if (isKaraokeMode) {
            for (LyricLineModels.LyricLine line : lyricLines) {
                if (line instanceof LyricLineModels.KaraokeLine) {
                    resetKaraokeLine((LyricLineModels.KaraokeLine) line);
                }
            }
        } else {
            if (currentHighlightedView != null) {
                currentHighlightedView.setTextColor(normalTextColor);
                currentHighlightedView.setTextSize(normalTextSize);
                currentHighlightedView = null;
            }
        }
        
        scrollView.smoothScrollTo(0, 0);
    }
    
    public boolean hasLyrics() {
        return !lyricLines.isEmpty();
    }
    
    public void setColors(int normalColor, int highlightColor) {
        this.normalTextColor = normalColor;
        this.highlightTextColor = highlightColor;
    }
    
    public void setTextSizes(int normalSize, int highlightSize) {
        this.normalTextSize = normalSize;
        this.highlightTextSize = highlightSize;
    }
    
    public String getCurrentFontName() {
        return FONT_NAMES[currentFontIndex];
    }
}