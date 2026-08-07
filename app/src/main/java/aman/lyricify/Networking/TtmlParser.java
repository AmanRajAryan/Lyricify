package aman.lyricify;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class TtmlParser {

    public static class LyricUnit {
        public String text;
        public long startTime; // -1 if untimed
        public long endTime;   // -1 if untimed
        public boolean isBg;   // Background Vocal Flag

        public LyricUnit(String text, long startTime, long endTime, boolean isBg) {
            this.text = text;
            this.startTime = startTime;
            this.endTime = endTime;
            this.isBg = isBg;
        }

        public boolean isTimed() {
            return startTime != -1;
        }
    }

    public static class LyricLine {
        public long startTime;
        public long endTime;
        public String singerId; 
        public String sectionName; 
        public List<LyricUnit> units = new ArrayList<>();
        public String fullText; 
    }

    public static class TtmlNode {
        public List<LyricLine> lines = new ArrayList<>();
    }

    public static TtmlNode parse(String ttmlContent) {
        TtmlNode node = new TtmlNode();
        if (ttmlContent == null || ttmlContent.isEmpty()) return node;

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(ttmlContent));
            int eventType = parser.getEventType();

            LyricLine currentLine = null;
            
            // State tracking
            boolean insideSpan = false;
            String currentSection = null; 
            
            // Logic to track nested Background Spans
            int spanDepth = 0;
            int bgSpanDepth = -1; // -1 means not currently in a BG span
            
            long spanStart = -1;
            long spanEnd = -1;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("div".equals(tagName)) {
                            currentSection = getAttributeValueRobust(parser, "songPart");
                        }
                        else if ("p".equals(tagName)) {
                            currentLine = new LyricLine();
                            currentLine.startTime = parseTime(parser.getAttributeValue(null, "begin"));
                            currentLine.endTime = parseTime(parser.getAttributeValue(null, "end"));
                            currentLine.singerId = getAttributeValueRobust(parser, "agent");
                            currentLine.sectionName = currentSection;
                            
                            // Reset BG state on new line
                            bgSpanDepth = -1;
                            spanDepth = 0;
                        } 
                        else if ("span".equals(tagName)) {
                            spanDepth++;
                            
                            String role = getAttributeValueRobust(parser, "role");
                            // If we encounter a BG tag and we aren't already inside one, mark the depth
                            if ("x-bg".equals(role) && bgSpanDepth == -1) {
                                bgSpanDepth = spanDepth; 
                            }
                            
                            String begin = parser.getAttributeValue(null, "begin");
                            if (begin != null) {
                                insideSpan = true;
                                spanStart = parseTime(begin);
                                spanEnd = parseTime(parser.getAttributeValue(null, "end"));
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (currentLine != null) {
                            String text = parser.getText();
                            if (text != null) {
                                String normalizedText = text.replaceAll("\\s+", " ");

                                if (!currentLine.units.isEmpty()) {
                                    LyricUnit lastUnit = currentLine.units.get(currentLine.units.size() - 1);
                                    if (lastUnit.text.endsWith("-") && normalizedText.startsWith(" ")) {
                                        normalizedText = normalizedText.substring(1); 
                                    }
                                }

                                if (!normalizedText.isEmpty()) {
                                    // explicit check: are we inside a BG span depth?
                                    boolean isCurrentBg = (bgSpanDepth != -1);
                                    
                                    if (insideSpan) {
                                        currentLine.units.add(new LyricUnit(normalizedText, spanStart, spanEnd, isCurrentBg));
                                    } else {
                                        currentLine.units.add(new LyricUnit(normalizedText, -1, -1, isCurrentBg));
                                    }
                                }
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("div".equals(tagName)) {
                            currentSection = null;
                        }
                        else if ("span".equals(tagName)) {
                            if (insideSpan) insideSpan = false;
                            
                            // If we are closing the span that started the BG context
                            if (spanDepth == bgSpanDepth) {
                                bgSpanDepth = -1;
                            }
                            spanDepth--;
                        } 
                        else if ("p".equals(tagName)) {
                            if (currentLine != null) {
                                StringBuilder sb = new StringBuilder();
                                for (LyricUnit unit : currentLine.units) {
                                    sb.append(unit.text);
                                }
                                currentLine.fullText = sb.toString().trim();
                                if (!currentLine.fullText.isEmpty()) {
                                    node.lines.add(currentLine);
                                }
                                currentLine = null;
                            }
                        }
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return node;
    }

    private static String getAttributeValueRobust(XmlPullParser parser, String attributeNameSuffix) {
        String value = parser.getAttributeValue(null, attributeNameSuffix);
        if (value != null) return value;
        
        value = parser.getAttributeValue(null, "ttm:" + attributeNameSuffix);
        if (value != null) return value;
        value = parser.getAttributeValue(null, "itunes:" + attributeNameSuffix);
        if (value != null) return value;

        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (name.endsWith(":" + attributeNameSuffix) || name.equals(attributeNameSuffix)) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        try {
            timeStr = timeStr.replace("s", "");
            String[] parts = timeStr.split(":");
            double totalSeconds = 0;
            if (parts.length == 3) {
                totalSeconds += Double.parseDouble(parts[0]) * 3600;
                totalSeconds += Double.parseDouble(parts[1]) * 60;
                totalSeconds += Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                totalSeconds += Double.parseDouble(parts[0]) * 60;
                totalSeconds += Double.parseDouble(parts[1]);
            } else {
                totalSeconds = Double.parseDouble(parts[0]);
            }
            return (long) (totalSeconds * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
