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

        // 1. Try to find Line Timestamp [mm:ss.xx]
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
            parseTextAndVocals(lyricLine, text);
            return lyricLine;

        } else {
            // 2. Plain Text (Treat as Simple Line, but start -1)
            LyricLine lyricLine = new LyricLine(-1);
            lyricLine.isWordSynced = false;
            // Split by space for wrapping
            String[] words = lineContent.split(" ");
            for (String w : words) {
                 if(!w.isEmpty()) lyricLine.words.add(new LyricWord(-1, w + " "));
            }
            // If empty (just spaces), add original
            if (lyricLine.words.isEmpty()) lyricLine.words.add(new LyricWord(-1, lineContent));
            
            return lyricLine;
        }
    }

    private static void parseTextAndVocals(LyricLine lyricLine, String content) {
        if (content == null) return;

        String trimmed = content.trim();
        if (trimmed.startsWith("v2:")) {
            lyricLine.vocalType = 2;
            content = content.replaceFirst("v2:", "");
        } else if (trimmed.startsWith("v1:")) {
            lyricLine.vocalType = 1; 
            content = content.replaceFirst("v1:", "");
        } else {
            lyricLine.vocalType = 1;
            content = content.replaceFirst("^[^<]*:", "");
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
            // --- FIX FOR WRAPPING ---
            // Split simple LRC content into individual words by whitespace
            // This allows the SyncedLyricsView wrapping logic to break lines correctly.
            lyricLine.isWordSynced = false;
            String[] words = content.split(" ");
            for (String w : words) {
                // Add space back to word for spacing, unless empty
                if (!w.isEmpty()) {
                    lyricLine.words.add(new LyricWord(lyricLine.startTime, w + " "));
                }
            }
            // Fallback for lines without spaces
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
