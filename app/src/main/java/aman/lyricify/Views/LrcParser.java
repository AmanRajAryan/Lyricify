package aman.lyricify;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {

    public static List<LyricLine> parse(InputStream inputStream) {
        List<LyricLine> lines = new ArrayList<>();
        boolean isSynced = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LyricLine parsedLine = parseLine(line);
                if (parsedLine != null) {
                    if (parsedLine.startTime != -1) isSynced = true;
                    lines.add(parsedLine);
                }
            }

            if (isSynced) {
                Collections.sort(lines, (a, b) -> {
                    if (a.startTime == -1 && b.startTime != -1) return -1;
                    if (a.startTime != -1 && b.startTime == -1) return 1;

                    int timeCompare = Long.compare(a.startTime, b.startTime);
                    if (timeCompare != 0) return timeCompare;
                    
                    // Put background vocals visually "after" main vocals if they start at exact same ms
                    if (a.isBackground && !b.isBackground) return 1;
                    if (!a.isBackground && b.isBackground) return -1;
                    
                    return Integer.compare(a.vocalType, b.vocalType);
                });

                calculateEndTimes(lines);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static void calculateEndTimes(List<LyricLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            LyricLine curr = lines.get(i);
            if (curr.startTime == -1) continue; 

            if (curr.endTime == 0) {
                long nextDifferentStart = -1;
                for (int j = i + 1; j < lines.size(); j++) {
                    LyricLine next = lines.get(j);
                    // Don't let a background line cut short a main line, or vice versa if possible
                    // But generally simple logic is safest:
                    if (next.startTime > curr.startTime) {
                        nextDifferentStart = next.startTime;
                        break;
                    }
                }
                
                if (nextDifferentStart != -1) {
                    curr.endTime = nextDifferentStart;
                } else {
                    curr.endTime = curr.startTime + 3000;
                }
            }
        }
    }

    private static LyricLine parseLine(String lineContent) {
        if (lineContent == null || lineContent.trim().isEmpty()) return null;
        String trimmed = lineContent.trim();

        // 1. Check for Background Line [bg: ...]
        // It might not have a timestamp prefix like [mm:ss], so we check strictly
        if (trimmed.startsWith("[bg:") && trimmed.endsWith("]")) {
            // Remove [bg: and ]
            String content = trimmed.substring(4, trimmed.length() - 1);
            
            // We temporarily create a line with -1 time
            LyricLine bgLine = new LyricLine(-1);
            bgLine.isBackground = true;
            
            // Parse words. The start time will be updated to the first word's time
            parseTextAndVocals(bgLine, content);
            
            // If we found words with timestamps, update the line's start time
            if (!bgLine.words.isEmpty() && bgLine.words.get(0).time != -1) {
                bgLine.startTime = bgLine.words.get(0).time;
            } else {
                // If no timestamps found inside, this line is invalid/unsynced background
                return null; 
            }
            return bgLine;
        }

        // 2. Try to find Standard Line Timestamp [mm:ss.xx]
        Pattern linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
        Matcher lineMatcher = linePattern.matcher(lineContent);

        if (lineMatcher.find()) {
            int min = Integer.parseInt(lineMatcher.group(1));
            int sec = Integer.parseInt(lineMatcher.group(2));
            String msStr = lineMatcher.group(3);
            int ms = Integer.parseInt(msStr) * (msStr.length() == 2 ? 10 : 1);
            long startTime = (min * 60L + sec) * 1000L + ms;
            String text = lineMatcher.group(4);

            LyricLine lyricLine = new LyricLine(startTime);
            
            // Check if text starts with "bg:" inside the timestamp
            if (text != null && text.trim().startsWith("[bg:") && text.trim().endsWith("]")) {
                 lyricLine.isBackground = true;
                 text = text.trim().substring(4, text.trim().length() - 1);
            }
            
            parseTextAndVocals(lyricLine, text);
            return lyricLine;

        } else {
            // 3. Plain Text
            LyricLine lyricLine = new LyricLine(-1);
            lyricLine.isWordSynced = false;
            String[] words = lineContent.split(" ");
            for (String w : words) {
                 if(!w.isEmpty()) lyricLine.words.add(new LyricWord(-1, w + " "));
            }
            if (lyricLine.words.isEmpty()) lyricLine.words.add(new LyricWord(-1, lineContent));
            return lyricLine;
        }
    }

    private static void parseTextAndVocals(LyricLine lyricLine, String content) {
        if (content == null) return;

        // Cleanup vocal tags
        String trimmed = content.trim();
        if (trimmed.startsWith("v2:")) {
            lyricLine.vocalType = 2;
            content = content.replaceFirst("v2:", "");
        } else if (trimmed.startsWith("v1:")) {
            lyricLine.vocalType = 1; 
            content = content.replaceFirst("v1:", "");
        } else {
            // If it's NOT a BG line, remove generic prefixes. 
            // If it IS a BG line, we already stripped [bg:].
            if (!lyricLine.isBackground) {
                lyricLine.vocalType = 1;
                content = content.replaceFirst("^[^<]*:", "");
            }
        }

        Pattern wordPattern = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)");
        Matcher wordMatcher = wordPattern.matcher(content);

        boolean hasWordTimestamps = false;
        while (wordMatcher.find()) {
            hasWordTimestamps = true;
            int wMin = Integer.parseInt(wordMatcher.group(1));
            int wSec = Integer.parseInt(wordMatcher.group(2));
            String wMsStr = wordMatcher.group(3);
            int wMs = Integer.parseInt(wMsStr) * (wMsStr.length() == 2 ? 10 : 1);
            long wordTime = (wMin * 60L + wSec) * 1000L + wMs;

            String text = wordMatcher.group(4);
            lyricLine.words.add(new LyricWord(wordTime, text));
        }

        if (hasWordTimestamps) {
            lyricLine.isWordSynced = true;
        } else {
            lyricLine.isWordSynced = false;
            String[] words = content.split(" ");
            for (String w : words) {
                if (!w.isEmpty()) {
                    lyricLine.words.add(new LyricWord(lyricLine.startTime, w + " "));
                }
            }
            if (lyricLine.words.isEmpty() && !content.isEmpty()) {
                 lyricLine.words.add(new LyricWord(lyricLine.startTime, content));
            }
        }

        if (!lyricLine.words.isEmpty()) {
            LyricWord lastEntry = lyricLine.words.get(lyricLine.words.size() - 1);
            if (lyricLine.isWordSynced && (lastEntry.text == null || lastEntry.text.trim().isEmpty())) {
                lyricLine.endTime = lastEntry.time;
                lyricLine.words.remove(lyricLine.words.size() - 1);
            }
        }
    }
}
