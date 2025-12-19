package aman.lyricify;

import java.util.List;

/**
 * Calculates word progress for karaoke mode
 */
class WordProgressCalculator {
    
    static class WordProgress {
        int wordIndex;
        float progress;
        
        WordProgress(int wordIndex, float progress) {
            this.wordIndex = wordIndex;
            this.progress = progress;
        }
    }
    
    static WordProgress calculate(List<KaraokeWord> words, long positionMs) {
        int currentWord = -1;
        float wordProgress = 0f;
        
        for (int i = 0; i < words.size(); i++) {
            KaraokeWord word = words.get(i);
            long wordStart = word.timestamp;
            long wordEnd = (i + 1 < words.size()) ? 
                words.get(i + 1).timestamp : 
                (wordStart + 1000);
            
            if (positionMs >= wordStart && positionMs < wordEnd) {
                currentWord = i;
                long wordDuration = wordEnd - wordStart;
                long elapsed = positionMs - wordStart;
                wordProgress = wordDuration > 0 ? (float) elapsed / wordDuration : 1.0f;
                break;
            } else if (positionMs >= wordEnd) {
                currentWord = i;
                wordProgress = 1.0f;
            }
        }
        
        return new WordProgress(currentWord, wordProgress);
    }
}