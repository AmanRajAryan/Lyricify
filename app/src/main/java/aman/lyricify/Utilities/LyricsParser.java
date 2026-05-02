package aman.lyricify;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class LyricsParser {

    public static String convertToLRC(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return "No lyrics available";
        }

        try {
            JSONObject root = new JSONObject(jsonResponse);

            if (!root.has("content")) {
                return "No lyrics found";
            }

            String type = root.optString("type", "Line");

            if ("Syllable".equals(type) || "Word".equals(type)) {
                return convertToKaraokeLRC(root);
            } else {
                return convertToNormalLRC(root);
            }

        } catch (JSONException e) {
            return "Error parsing lyrics";
        }
    }

    private static String convertToNormalLRC(JSONObject root) throws JSONException {
        JSONArray content = root.getJSONArray("content");
        StringBuilder lrcBuilder = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            JSONObject line = content.getJSONObject(i);
            long timestamp = line.getLong("timestamp");
            String timeTag = formatTimestamp(timestamp);

            StringBuilder lineText = new StringBuilder();
            JSONArray textArray = line.getJSONArray("text");
            for (int j = 0; j < textArray.length(); j++) {
                JSONObject word = textArray.getJSONObject(j);
                lineText.append(word.getString("text"));
                if (j < textArray.length() - 1) lineText.append(" ");
            }

            lrcBuilder.append(timeTag)
                      .append(lineText.toString().trim())
                      .append("\n");
        }

        return lrcBuilder.toString().trim();
    }

    private static String convertToKaraokeLRC(JSONObject root) throws JSONException {
        JSONArray content = root.getJSONArray("content");
        StringBuilder lrcBuilder = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            JSONObject line = content.getJSONObject(i);

            long lineTimestamp = line.getLong("timestamp");
            long lineEndtime = line.optLong("endtime", lineTimestamp);

            boolean oppositeTurn = line.optBoolean("oppositeTurn", false);
            String voice = oppositeTurn ? "v2" : "v1";

            JSONArray textArray = line.getJSONArray("text");

            StringBuilder karaokeLine = new StringBuilder();
            karaokeLine.append(formatTimestamp(lineTimestamp))
                       .append(voice)
                       .append(":");

            for (int j = 0; j < textArray.length(); j++) {
                JSONObject word = textArray.getJSONObject(j);
                long wordStart = word.getLong("timestamp");

                karaokeLine.append("<")
                           .append(formatTimestampShort(wordStart))
                           .append(">")
                           .append(word.getString("text"));
                if ((j < textArray.length() - 1) && !word.optBoolean("part" , false)) karaokeLine.append(" ");
            }

            karaokeLine.append(" <").append(formatTimestampShort(lineEndtime)).append(">");

            lrcBuilder.append(karaokeLine).append("\n");
        }

        return lrcBuilder.toString().trim();
    }

    private static String formatTimestamp(long milliseconds) {
        int totalSeconds = (int) (milliseconds / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int millis = (int) (milliseconds % 1000);
        return String.format(Locale.US, "[%02d:%02d.%03d]", minutes, seconds, millis);
    }

    private static String formatTimestampShort(long milliseconds) {
        int totalSeconds = (int) (milliseconds / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int millis = (int) (milliseconds % 1000);
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
