package aman.lyricify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LyricsLayout {
    
    public List<WrappedLine> wrappedLines = new ArrayList<>();
    public Map<LyricLine, Float> lineCenterYMap = new HashMap<>();
    public Map<LyricLine, Float> lineScrollYMap = new HashMap<>(); // The Magic Pre-calc map

    private float padding = 48;
    private float spacingWrapped = 0; // Set in init
    private float spacingLyrics = 0;
    private float totalContentHeight = 0;

    public void initDimensions(float density) {
        padding = 48; // Or density * 16
        spacingWrapped = 10 * density;
        spacingLyrics = 60 * density;
    }

    public void measure(List<LyricLine> lyrics, int viewWidth, LyricsRenderer renderer) {
        wrappedLines.clear();
        lineCenterYMap.clear();
        lineScrollYMap.clear();
        
        float maxWidth = viewWidth - (padding * 2);
        if (maxWidth <= 0 || lyrics.isEmpty()) return;

        float currentY = 0;
        LyricLine prevParent = null;

        // --- 1. WRAP TEXT ---
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine line = lyrics.get(i);
            long nextStart = (i + 1 < lyrics.size()) ? lyrics.get(i + 1).startTime : -1;

            List<LyricWord> lineWords = new ArrayList<>();
            List<Float> wordWidths = new ArrayList<>(); // Cache widths here
            
            float lineWidth = 0;
            float parentStartY = -1;
            float parentLastY = -1;

            int wordIdx = 0;
            while (wordIdx < line.words.size()) {
                // Group words (simple word wrapping)
                List<LyricWord> cluster = new ArrayList<>();
                List<Float> clusterWidths = new ArrayList<>();
                float clusterW = 0;

                // Always take at least one word
                LyricWord first = line.words.get(wordIdx++);
                float w1 = renderer.getMeasurePaint().measureText(first.text);
                cluster.add(first);
                clusterWidths.add(w1);
                clusterW += w1;

                // Try adding more until space/break
                while (wordIdx < line.words.size()) {
                    LyricWord last = cluster.get(cluster.size()-1);
                    if (last.text.endsWith(" ") || last.text.endsWith("\u3000")) break;
                    
                    LyricWord next = line.words.get(wordIdx);
                    float wNext = renderer.getMeasurePaint().measureText(next.text);
                    cluster.add(next);
                    clusterWidths.add(wNext);
                    clusterW += wNext;
                    wordIdx++;
                }

                if (lineWidth + clusterW > maxWidth && !lineWords.isEmpty()) {
                    // New Line
                    float space = (prevParent == line) ? spacingWrapped : spacingLyrics;
                    if (prevParent == null) space = 0;
                    currentY += space;
                    if (parentStartY == -1) parentStartY = currentY;

                    wrappedLines.add(new WrappedLine(line, new ArrayList<>(lineWords), new ArrayList<>(wordWidths), currentY, nextStart));
                    parentLastY = currentY;
                    currentY += renderer.getTextHeight();
                    
                    prevParent = line;
                    lineWords.clear();
                    wordWidths.clear();
                    lineWidth = 0;
                }
                
                lineWords.addAll(cluster);
                wordWidths.addAll(clusterWidths);
                lineWidth += clusterW;
            }

            // Add Remainder
            if (!lineWords.isEmpty()) {
                float space = (prevParent == line) ? spacingWrapped : spacingLyrics;
                if (prevParent == null) space = 0;
                currentY += space;
                if (parentStartY == -1) parentStartY = currentY;

                wrappedLines.add(new WrappedLine(line, new ArrayList<>(lineWords), new ArrayList<>(wordWidths), currentY, nextStart));
                parentLastY = currentY;
                currentY += renderer.getTextHeight();
                prevParent = line;
            }

            if (parentStartY != -1) {
                lineCenterYMap.put(line, (parentStartY + parentLastY) / 2f);
            }
        }
        totalContentHeight = currentY;

        // --- 2. PRE-CALCULATE SCROLL TARGETS (The 3-Overlap Logic) ---
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine current = lyrics.get(i);
            Float centerCur = lineCenterYMap.get(current);
            if (centerCur == null) centerCur = 0f;
            
            // Plain text (-1) just centers normally, no overlap logic needed
            if (current.startTime == -1) {
                lineScrollYMap.put(current, centerCur);
                continue;
            }

            float finalTarget = centerCur;
            boolean overlapsPrev = false, overlapsPrevPrev = false;

            if (i > 0) {
                LyricLine prev = lyrics.get(i - 1);
                if (current.startTime < prev.endTime) overlapsPrev = true;
            }
            if (i > 1) {
                LyricLine prevPrev = lyrics.get(i - 2);
                if (current.startTime < prevPrev.endTime) overlapsPrevPrev = true;
            }

            if (overlapsPrevPrev) {
                // 3-Line Overlap: Target the Middle Line
                Float centerMid = lineCenterYMap.get(lyrics.get(i - 1));
                if (centerMid != null) finalTarget = centerMid;
            } else if (overlapsPrev) {
                // 2-Line Overlap: Target Combined Center
                Float centerPrev = lineCenterYMap.get(lyrics.get(i - 1));
                if (centerPrev != null) finalTarget = (centerPrev + centerCur) / 2f;
            }
            
            lineScrollYMap.put(current, finalTarget);
        }
    }
    
    public float getContentHeight() { return totalContentHeight; }

    public static class WrappedLine {
        public LyricLine parentLine;
        public List<LyricWord> words;
        public List<Float> wordWidths; // Cache width to avoid measure in onDraw
        public float y;
        public long nextStartTime;

        public WrappedLine(LyricLine p, List<LyricWord> w, List<Float> ww, float y, long nst) {
            this.parentLine = p; this.words = w; this.wordWidths = ww; this.y = y; this.nextStartTime = nst;
        }
    }
}
