package aman.lyricify;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;

public class LyricsRepository {

    private static final String NEW_BASE_URL = "https://lyricify.amanraj.workers.dev/";
    private static final String OLD_BASE_URL = "https://lyrics.paxsenix.org/";
    private static final String NEW_API_TOKEN = "aman-please-gimme-the-lyrics";

    public static void getLyrics(OkHttpClient client, String songId, ApiClient.LyricsCallback callback) {
        // 1. Try New API
        getLyricsPrimary(client, songId, new ApiClient.LyricsCallback() {
            @Override
            public void onSuccess(ApiClient.LyricsResponse lyrics) {
                callback.onSuccess(lyrics);
            }

            @Override
            public void onFailure(String error) {
                // 2. Fallback to Old API
                System.out.println("Primary Lyrics Failed: " + error + ". Falling back...");
                getLyricsFallback(client, songId, callback);
            }
        });
    }

    private static void getLyricsPrimary(OkHttpClient client, String songId, ApiClient.LyricsCallback callback) {
        String url = NEW_BASE_URL + "lyrics?id=" + songId;

        Request request = new Request.Builder()
                .url(url)
                .header("X-Lyricify-Token", NEW_API_TOKEN)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("HTTP " + response.code());
                    return;
                }

                String responseText = response.body().string();

                try {
                    JSONObject root = new JSONObject(responseText);
                    ApiClient.LyricsResponse resp = new ApiClient.LyricsResponse();

                    // --- 1. GET TTML CONTENT ---
                    resp.ttml = root.optString("content", "");

                    // --- 2. CONVERT TTML TO ALL FORMATS ---
                    if (!resp.ttml.isEmpty()) {
                        TtmlParser.TtmlNode ttmlNode = TtmlParser.parse(resp.ttml);

                        resp.plain = LyricsConverter.toPlain(ttmlNode);
                        resp.lrc = LyricsConverter.toLrc(ttmlNode);
                        // Added: LRC MultiPerson
                        resp.lrcMultiPerson = LyricsConverter.toLrcMultiPerson(ttmlNode);
                        resp.elrc = LyricsConverter.toElrc(ttmlNode);
                        resp.elrcMultiPerson = LyricsConverter.toElrcMultiPerson(ttmlNode);
                    }

                    // --- 3. METADATA PARSING ---
                    if (root.has("track")) {
                        parseTrackMetadata(root.getJSONObject("track"), resp);
                    }
                    
                    callback.onSuccess(resp);

                } catch (Exception e) {
                    callback.onFailure("Parsing error: " + e.getMessage());
                }
            }
        });
    }

    private static void getLyricsFallback(OkHttpClient client, String songId, ApiClient.LyricsCallback callback) {
        String url = OLD_BASE_URL + "apple-music/lyrics?id=" + songId + "&ttml=true";

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("No lyrics found (HTTP " + response.code() + ")");
                    return;
                }

                String responseText = response.body().string();
                if (responseText.isEmpty()) {
                    callback.onFailure("No lyrics found");
                    return;
                }

                try {
                    JSONObject json = new JSONObject(responseText);
                    ApiClient.LyricsResponse resp = new ApiClient.LyricsResponse();

                    // --- 1. GET TTML CONTENT ---
                    resp.ttml = json.optString("ttmlContent", "");

                    // --- 2. CONVERT TTML TO ALL FORMATS ---
                    if (!resp.ttml.isEmpty()) {
                        TtmlParser.TtmlNode ttmlNode = TtmlParser.parse(resp.ttml);
                        
                        resp.plain = LyricsConverter.toPlain(ttmlNode);
                        resp.lrc = LyricsConverter.toLrc(ttmlNode);
                        // Added: LRC MultiPerson
                        resp.lrcMultiPerson = LyricsConverter.toLrcMultiPerson(ttmlNode);
                        resp.elrc = LyricsConverter.toElrc(ttmlNode);
                        resp.elrcMultiPerson = LyricsConverter.toElrcMultiPerson(ttmlNode);
                    }

                    // --- 3. METADATA PARSING ---
                    if (json.has("metadata")) {
                        JSONObject metadata = json.getJSONObject("metadata");
                        if (metadata.has("songwriters")) {
                            JSONArray writersArray = metadata.getJSONArray("songwriters");
                            for (int i = 0; i < writersArray.length(); i++) {
                                resp.songwriters.add(writersArray.getString(i));
                            }
                        }
                    }

                    if (json.has("track")) {
                        parseTrackMetadata(json.getJSONObject("track"), resp);
                    }

                    callback.onSuccess(resp);

                } catch (Exception e) {
                    callback.onFailure("Error parsing lyrics: " + e.getMessage());
                }
            }
        });
    }

    private static void parseTrackMetadata(JSONObject track, ApiClient.LyricsResponse resp) {
        try {
            resp.audioLocale = track.optString("audioLocale", "");
            resp.releaseDate = track.optString("releaseDate", "");
            resp.composerName = track.optString("composerName", null);
            resp.contentRating = track.optString("contentRating", null);
            resp.isrc = track.optString("isrc", null);

            if (track.has("genreNames")) {
                JSONArray genres = track.getJSONArray("genreNames");
                for (int i = 0; i < genres.length(); i++) {
                    resp.genreNames.add(genres.getString(i));
                }
                if (!resp.genreNames.isEmpty()) {
                    resp.genre = resp.genreNames.get(0);
                }
            }

            if (track.has("trackNumber")) {
                resp.trackNumber = String.valueOf(track.optInt("trackNumber"));
            }
            if (track.has("discNumber")) {
                resp.discNumber = String.valueOf(track.optInt("discNumber"));
            }
        } catch (Exception e) {
            // Ignore minor metadata parsing errors
        }
    }
}
