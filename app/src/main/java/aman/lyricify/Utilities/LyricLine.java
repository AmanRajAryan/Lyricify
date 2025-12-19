package aman.lyricify;

import android.widget.TextView;

/**
 * Base class representing a single line of lyrics with timestamp
 */
class LyricLine {
    long timestamp;
    String text;
    TextView textView;
    
    LyricLine(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }
}