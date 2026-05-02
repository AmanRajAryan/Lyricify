package aman.lyricify;

import android.widget.TextView;
import java.util.List;

public class LyricLineModels {
    
    public static class LyricLine {
        public long timestamp;
        public String text;
        public TextView textView;
        
        public LyricLine(long timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }
    }
    
    public static class KaraokeLine extends LyricLine {
        public String voice;
        public List<KaraokeWord> words;
        public KaraokeLineView karaokeLineView;
        
        public KaraokeLine(long timestamp, String voice, List<KaraokeWord> words) {
            super(timestamp, "");
            this.voice = voice;
            this.words = words;
        }
    }
    
    public static class KaraokeWord {
        public long timestamp;
        public String text;
        
        public KaraokeWord(long timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }
    }
}
