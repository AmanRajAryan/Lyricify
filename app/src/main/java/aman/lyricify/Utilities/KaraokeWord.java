package aman.lyricify;

/**
 * Individual word with timestamp for karaoke mode
 */
class KaraokeWord {
    long timestamp;
    String text;
    
    KaraokeWord(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }
}