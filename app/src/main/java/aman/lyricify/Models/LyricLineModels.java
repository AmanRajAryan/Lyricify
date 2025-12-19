package aman.lyricify;

import android.widget.TextView;
import java.util.List;

/**
 * Data models for lyric lines
 */
public class LyricLineModels {
    
    /**
     * Base class for lyric line data
     */
    public static class LyricLine {
        public long timestamp;
        public String text;
        public TextView textView;
        
        public LyricLine(long timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }
    }
    
    /**
     * Karaoke line with word-level timing
     */
    public static class KaraokeLine extends LyricLine {
        public String voice;
        public List<KaraokeWord> words;
        public KaraokeLineView karaokeLineView;  // Changed from karaokeView to karaokeLineView
        
        public KaraokeLine(long timestamp, String voice, List<KaraokeWord> words) {
            super(timestamp, "");
            this.voice = voice;
            this.words = words;
        }
    }
    
    /**
     * Individual word with timestamp for karaoke mode
     */
    public static class KaraokeWord {
        public long timestamp;
        public String text;
        
        public KaraokeWord(long timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }
    }
}