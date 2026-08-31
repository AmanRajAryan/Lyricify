package aman.lyricify;

import java.util.Locale;

public class LyricsConverter {

    // Helper: Check if any unit in the song has word-level timing
    private static boolean hasWordSync(TtmlParser.TtmlNode node) {
        for (TtmlParser.LyricLine line : node.lines) {
            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isTimed()) return true;
            }
        }
        return false;
    }

    // Helper: Check if any line has a singer ID
    private static boolean hasSingers(TtmlParser.TtmlNode node) {
        for (TtmlParser.LyricLine line : node.lines) {
            if (line.singerId != null && !line.singerId.isEmpty()) return true;
        }
        return false;
    }

    public static String toPlain(TtmlParser.TtmlNode node) {
        if (node.lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        
        String lastSection = null;

        for (TtmlParser.LyricLine line : node.lines) {
            if (line.sectionName != null && !line.sectionName.equals(lastSection)) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("[").append(line.sectionName).append("]\n");
                lastSection = line.sectionName;
            }

            boolean currentlyInBg = false;
            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isBg && !currentlyInBg) {
                    sb.append("\n"); 
                    currentlyInBg = true;
                } 
                else if (!unit.isBg && currentlyInBg) {
                    sb.append("\n");
                    currentlyInBg = false;
                }
                sb.append(unit.text);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String toLrc(TtmlParser.TtmlNode node) {
        if (node.lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        
        for (TtmlParser.LyricLine line : node.lines) {
            sb.append(formatTimeBracket(line.startTime));
            
            boolean currentlyInBg = false;

            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isBg && !currentlyInBg) {
                    sb.append("\n"); 
                    long time = (unit.startTime != -1) ? unit.startTime : line.startTime;
                    sb.append(formatTimeBracket(time)); 
                    currentlyInBg = true;
                } 
                else if (!unit.isBg && currentlyInBg) {
                    sb.append("\n");
                    long time = (unit.startTime != -1) ? unit.startTime : line.startTime;
                    sb.append(formatTimeBracket(time));
                    currentlyInBg = false;
                }
                sb.append(unit.text);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String toLrcMultiPerson(TtmlParser.TtmlNode node) {
        if (!hasSingers(node)) return null;

        StringBuilder sb = new StringBuilder();
        
        for (TtmlParser.LyricLine line : node.lines) {
            sb.append(formatTimeBracket(line.startTime));
            if (line.singerId != null && !line.singerId.isEmpty()) {
                sb.append(line.singerId).append(": ");
            }

            boolean currentlyInBg = false;

            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isBg && !currentlyInBg) {
                    sb.append("\n");
                    long time = (unit.startTime != -1) ? unit.startTime : line.startTime;
                    sb.append(formatTimeBracket(time));
                    if (line.singerId != null && !line.singerId.isEmpty()) {
                        sb.append(line.singerId).append(": ");
                    }
                    currentlyInBg = true;
                } 
                else if (!unit.isBg && currentlyInBg) {
                    sb.append("\n");
                    long time = (unit.startTime != -1) ? unit.startTime : line.startTime;
                    sb.append(formatTimeBracket(time));
                    if (line.singerId != null && !line.singerId.isEmpty()) {
                        sb.append(line.singerId).append(": ");
                    }
                    currentlyInBg = false;
                }
                sb.append(unit.text);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String toElrc(TtmlParser.TtmlNode node) {
        if (!hasWordSync(node)) return null;

        StringBuilder sb = new StringBuilder();
        
        for (TtmlParser.LyricLine line : node.lines) {
            // --- PASS 1: Main Vocals ---
            sb.append(formatTimeBracket(line.startTime));
            
            for (int i = 0; i < line.units.size(); i++) {
                TtmlParser.LyricUnit unit = line.units.get(i);
                if (unit.isBg) continue; // Skip BG in Pass 1

                if (unit.isTimed()) {
                    sb.append(formatTimeAngle(unit.startTime));
                    sb.append(unit.text);
                    appendEndTimeIfNeeded(sb, line, i, false);
                } else {
                    sb.append(unit.text);
                }
            }

            // --- PASS 2: Background Vocals ---
            boolean hasBg = false;
            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isBg) { hasBg = true; break; }
            }

            if (hasBg) {
                sb.append("\n[bg: ");
                for (int i = 0; i < line.units.size(); i++) {
                    TtmlParser.LyricUnit unit = line.units.get(i);
                    if (!unit.isBg) continue; // Skip Main in Pass 2

                    if (unit.isTimed()) {
                        sb.append(formatTimeAngle(unit.startTime));
                        sb.append(unit.text);
                        appendEndTimeIfNeeded(sb, line, i, true);
                    } else {
                        sb.append(unit.text);
                    }
                }
                sb.append("]");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public static String toElrcMultiPerson(TtmlParser.TtmlNode node) {
        if (!hasSingers(node) || !hasWordSync(node)) return null;

        StringBuilder sb = new StringBuilder();
        
        for (TtmlParser.LyricLine line : node.lines) {
            // --- PASS 1: Main Vocals ---
            sb.append(formatTimeBracket(line.startTime));

            if (line.singerId != null && !line.singerId.isEmpty()) {
                sb.append(line.singerId).append(": ");
            }
            
            for (int i = 0; i < line.units.size(); i++) {
                TtmlParser.LyricUnit unit = line.units.get(i);
                if (unit.isBg) continue; // Skip BG

                if (unit.isTimed()) {
                    sb.append(formatTimeAngle(unit.startTime));
                    sb.append(unit.text);
                    appendEndTimeIfNeeded(sb, line, i, false);
                } else {
                    sb.append(unit.text);
                }
            }

            // --- PASS 2: Background Vocals ---
            boolean hasBg = false;
            for (TtmlParser.LyricUnit unit : line.units) {
                if (unit.isBg) { hasBg = true; break; }
            }

            if (hasBg) {
                sb.append("\n[bg: ");
                for (int i = 0; i < line.units.size(); i++) {
                    TtmlParser.LyricUnit unit = line.units.get(i);
                    if (!unit.isBg) continue; // Skip Main

                    if (unit.isTimed()) {
                        sb.append(formatTimeAngle(unit.startTime));
                        sb.append(unit.text);
                        appendEndTimeIfNeeded(sb, line, i, true);
                    } else {
                        sb.append(unit.text);
                    }
                }
                sb.append("]");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // Extracted helper logic for appending end times in ELRC to avoid duplication
    private static void appendEndTimeIfNeeded(StringBuilder sb, TtmlParser.LyricLine line, int currentIndex, boolean processingBg) {
        TtmlParser.LyricUnit currentUnit = line.units.get(currentIndex);
        boolean shouldAppendEndTime = false;
        boolean isLastTimedInGroup = true;

        for (int j = currentIndex + 1; j < line.units.size(); j++) {
            TtmlParser.LyricUnit nextUnit = line.units.get(j);
            
            // Only consider units of the same type (Main vs BG) for continuity
            if (nextUnit.isBg != processingBg) continue; 

            if (nextUnit.isTimed()) {
                isLastTimedInGroup = false;
                break;
            }
        }

        if (isLastTimedInGroup) {
            sb.append(formatTimeAngle(currentUnit.endTime));
        }
    }

    private static String formatTimeBracket(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long milliseconds = millis % 1000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minutes, seconds, milliseconds);
    }
    
    private static String formatTimeAngle(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long milliseconds = millis % 1000;
        return String.format(Locale.US, "<%02d:%02d.%03d>", minutes, seconds, milliseconds);
    }
}
