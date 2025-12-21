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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LyricLine parsedLine = parseLine(line);
                if (parsedLine != null) lines.add(parsedLine);
            }
            
            // Sort by startTime, maintaining order for same timestamps
            Collections.sort(lines, (a, b) -> {
                int timeCompare = Long.compare(a.startTime, b.startTime);
                if (timeCompare != 0) return timeCompare;
                return Integer.compare(a.vocalType, b.vocalType);
            });

            // Calculate end times - handle overlapping lines
            for (int i = 0; i < lines.size(); i++) {
                LyricLine curr = lines.get(i);
                if (curr.endTime == 0) {
                    // Find next line with DIFFERENT start time
                    long nextDifferentStart = -1;
                    for (int j = i + 1; j < lines.size(); j++) {
                        if (lines.get(j).startTime > curr.startTime) {
                            nextDifferentStart = lines.get(j).startTime;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static LyricLine parseLine(String line) {
        Pattern linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
        Matcher lineMatcher = linePattern.matcher(line);
        if (lineMatcher.find()) {
            int min = Integer.parseInt(lineMatcher.group(1));
            int sec = Integer.parseInt(lineMatcher.group(2));
            String msStr = lineMatcher.group(3);
            int ms = Integer.parseInt(msStr) * (msStr.length() == 2 ? 10 : 1);
            long startTime = (min * 60L + sec) * 1000L + ms;
            String content = lineMatcher.group(4);

            LyricLine lyricLine = new LyricLine(startTime);

            // Detect and strip vocal type prefix
            if (content != null) {
                String trimmed = content.trim();
                if (trimmed.startsWith("v2:")) {
                    lyricLine.vocalType = 2;
                    content = content.replaceFirst("v2:", "");
                } else if (trimmed.startsWith("v1:")) {
                    lyricLine.vocalType = 1;
                    content = content.replaceFirst("v1:", "");
                } else {
                    lyricLine.vocalType = 1; // default
                    // Still remove any other prefix like "main:" etc
                    content = content.replaceFirst("^[^<]*:", "");
                }
            }

            Pattern wordPattern = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)");
            Matcher wordMatcher = wordPattern.matcher(content);

            while (wordMatcher.find()) {
                int wMin = Integer.parseInt(wordMatcher.group(1));
                int wSec = Integer.parseInt(wordMatcher.group(2));
                String wMsStr = wordMatcher.group(3);
                int wMs = Integer.parseInt(wMsStr) * (wMsStr.length() == 2 ? 10 : 1);
                long wordTime = (wMin * 60L + wSec) * 1000L + wMs;

                String text = wordMatcher.group(4);
                lyricLine.words.add(new LyricWord(wordTime, text));
            }

            if (!lyricLine.words.isEmpty()) {
                LyricWord lastEntry = lyricLine.words.get(lyricLine.words.size() - 1);
                if (lastEntry.text == null || lastEntry.text.trim().isEmpty()) {
                    lyricLine.endTime = lastEntry.time;
                    lyricLine.words.remove(lyricLine.words.size() - 1);
                }
                if (!lyricLine.words.isEmpty()) return lyricLine;
            }
        }
        return null;
    }
}