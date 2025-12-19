package aman.lyricify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC and Karaoke format lyrics
 */
public class SyncedLyricsParser {
    
    /**
     * Parse standard LRC format lyrics
     * Format: [mm:ss.xxx]lyric text
     */
    public static List<LyricLineModels.LyricLine> parseLrcLyrics(String lrcText) {
        List<LyricLineModels.LyricLine> lines = new ArrayList<>();
        String[] textLines = lrcText.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
        
        for (String line : textLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    int minutes = Integer.parseInt(matcher.group(1));
                    int seconds = Integer.parseInt(matcher.group(2));
                    String millisStr = matcher.group(3);
                    
                    int millis = millisStr.length() == 2 ? 
                        Integer.parseInt(millisStr) * 10 : 
                        Integer.parseInt(millisStr);
                    
                    long timestamp = (minutes * 60 * 1000) + (seconds * 1000) + millis;
                    String text = matcher.group(4).trim();
                    
                    if (!text.isEmpty()) {
                        lines.add(new LyricLineModels.LyricLine(timestamp, text));
                    }
                } catch (NumberFormatException e) {
                    // Skip malformed lines
                }
            }
        }
        
        // Sort by timestamp
        lines.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        return lines;
    }
    
    /**
     * Parse karaoke format lyrics (word-synced with v1:/v2: tags)
     * Format: [mm:ss.xxx]v1:<mm:ss.xxx>word<mm:ss.xxx>word
     */
    public static List<LyricLineModels.LyricLine> parseKaraokeLyrics(String lrcText) {
        List<LyricLineModels.LyricLine> lines = new ArrayList<>();
        String[] textLines = lrcText.split("\n");
        Pattern linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](v\\d+:)?(.*)");
        
        for (String line : textLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            Matcher lineMatcher = linePattern.matcher(line);
            if (lineMatcher.find()) {
                try {
                    int minutes = Integer.parseInt(lineMatcher.group(1));
                    int seconds = Integer.parseInt(lineMatcher.group(2));
                    String millisStr = lineMatcher.group(3);
                    
                    int millis = millisStr.length() == 2 ? 
                        Integer.parseInt(millisStr) * 10 : 
                        Integer.parseInt(millisStr);
                    
                    long lineTimestamp = (minutes * 60 * 1000L) + (seconds * 1000L) + millis;
                    String voice = lineMatcher.group(4) != null ? lineMatcher.group(4) : "v1:";
                    String content = lineMatcher.group(5);
                    
                    List<LyricLineModels.KaraokeWord> words = parseKaraokeWords(content);
                    
                    if (!words.isEmpty()) {
                        lines.add(new LyricLineModels.KaraokeLine(lineTimestamp, voice, words));
                    }
                } catch (NumberFormatException e) {
                    // Skip malformed lines
                }
            }
        }
        
        return lines;
    }
    
    /**
     * Parse words with timestamps from karaoke content
     * Format: <mm:ss.xxx>word<mm:ss.xxx>word
     */
    private static List<LyricLineModels.KaraokeWord> parseKaraokeWords(String content) {
        List<LyricLineModels.KaraokeWord> words = new ArrayList<>();
        Pattern wordPattern = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]+)");
        Matcher matcher = wordPattern.matcher(content);
        
        while (matcher.find()) {
            try {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                String millisStr = matcher.group(3);
                
                int millis = millisStr.length() == 2 ? 
                    Integer.parseInt(millisStr) * 10 : 
                    Integer.parseInt(millisStr);
                
                long timestamp = (minutes * 60 * 1000L) + (seconds * 1000L) + millis;
                String text = matcher.group(4);
                
                words.add(new LyricLineModels.KaraokeWord(timestamp, text));
            } catch (NumberFormatException e) {
                // Skip malformed words
            }
        }
        
        return words;
    }
    
    /**
     * Check if lyrics are in karaoke format
     */
    public static boolean isKaraokeFormat(String lrcText) {
        return lrcText != null && 
               (lrcText.contains("v1:") || lrcText.contains("v2:") || lrcText.contains("<"));
    }
}