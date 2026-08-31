package aman.lyricify;

import aman.lyricify.Song;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import okhttp3.*;

public class SearchRepository {
    private static final String SEARCH_URL = "https://itunes.apple.com/search?entity=song&term=";

    public static void search(OkHttpClient client, String query, ApiClient.SearchCallback callback) {
        Request request = new Request.Builder()
                .url(SEARCH_URL + query)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("HTTP Error: " + response.code());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject root = new JSONObject(responseData);
                    JSONArray jsonArray = root.getJSONArray("results");
                    ArrayList<Song> songs = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject item = jsonArray.getJSONObject(i);
                        String id = item.optString("trackId");
                        String name = item.optString("trackName");
                        String artist = item.optString("artistName");
                        String album = item.optString("collectionName");
                        
                        String cover = item.optString("artworkUrl100");
                        if (cover != null && cover.contains("100x100bb.jpg")) {
                            cover = cover.replace("100x100bb.jpg", "1000x1000bb.jpg");
                        }
                        
                        String durationStr = String.valueOf(item.optInt("trackTimeMillis", 0));
                        String explicitness = item.optString("trackExplicitness", "");
                        String contentRating = "explicit".equalsIgnoreCase(explicitness) ? "EXPLICIT" : "NONE";
                        String releaseDate = item.optString("releaseDate", "");

                        Song song = new Song(id, name, artist, album, cover, releaseDate, durationStr, contentRating, true, true);
                        songs.add(song);
                    }
                    callback.onSuccess(songs);

                } catch (Exception e) {
                    callback.onFailure("JSON Parsing error: " + e.getMessage());
                }
            }
        });
    }
}
