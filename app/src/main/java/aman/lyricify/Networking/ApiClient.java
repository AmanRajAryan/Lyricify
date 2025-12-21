package aman.lyricify;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String BASE_URL = "https://lyrics.paxsenix.org/";
    private static final String USER_AGENT = "LYRICIFY";

    private static final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            Request request = original.newBuilder()
                                    .header("User-Agent", USER_AGENT)
                                    .build();
                            return chain.proceed(request);
                        }
                    })
                    .build();

    // --- SHARED CACHE MECHANISM ---
    private static LyricsResponse globalCache;
    private static final List<CacheListener> listeners = new ArrayList<>();

    public interface CacheListener {
        void onCacheUpdated(LyricsResponse response);
    }

    // Call this from LyricsActivity when data arrives
    public static void updateCache(LyricsResponse response) {
        globalCache = response;
        synchronized (listeners) {
            for (CacheListener listener : listeners) {
                listener.onCacheUpdated(response);
            }
        }
    }

    // Call this from TagEditorActivity to wait for data
    public static void registerCacheListener(CacheListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
        // If we already have data, deliver it immediately
        if (globalCache != null) {
            listener.onCacheUpdated(globalCache);
        }
    }

    public static void unregisterCacheListener(CacheListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public interface SearchCallback {
        void onSuccess(ArrayList<Song> songs);
        void onFailure(String error);
    }

    public interface LyricsCallback {
        void onSuccess(LyricsResponse lyrics);
        void onFailure(String error);
    }

    // Updated LyricsResponse to hold all metadata and implement Serializable
    public static class LyricsResponse implements Serializable {
        public String plain;
        public String lrc;
        public String elrc;
        public String elrcMultiPerson;
        
        // Expanded Metadata Fields
        public List<String> songwriters;
        public List<String> genreNames; 
        public String audioLocale;      
        
        public String genre; // Primary genre
        public String releaseDate; 
        public String contentRating;
        public String isrc;
        public String trackNumber;
        public String discNumber;
        public String composerName; 
        
        public LyricsResponse() {
            songwriters = new ArrayList<>();
            genreNames = new ArrayList<>();
        }
        
        public boolean hasLyrics() {
            return plain != null && !plain.isEmpty();
        }
    }

    public static void searchSongs(String query, SearchCallback callback) {
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String url = BASE_URL + "apple-music/search?q=" + encodedQuery;

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onFailure("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onFailure("Server error: " + response.code());
                        return;
                    }

                    String json = response.body().string();

                    try {
                        JSONArray array = new JSONArray(json);
                        ArrayList<Song> songs = new ArrayList<>();

                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            
                            Song song = new Song(
                                    obj.getString("id"),
                                    obj.getString("songName"),
                                    obj.getString("artistName"),
                                    obj.getString("albumName"),
                                    obj.getString("artwork"),
                                    obj.getString("releaseDate"),
                                    obj.getString("duration"),
                                    obj.optString("contentRating", "")
                            );
                            
                            // Store full track data just in case
                            song.setFullTrackData(obj);
                            
                            songs.add(song);
                        }

                        callback.onSuccess(songs);

                    } catch (Exception e) {
                        callback.onFailure("Failed to parse data: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.onFailure("Encoding error: " + e.getMessage());
        }
    }

    public static void getLyrics(String songId, LyricsCallback callback) {
        String url = BASE_URL + "apple-music/lyrics?id=" + songId;

        Request request = new Request.Builder()
                .url(url)
                .build();

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
                    
                    LyricsResponse resp = new LyricsResponse();
                    
                    // 1. Basic Lyrics
                    resp.plain = json.optString("plain", "");
                    resp.lrc = json.optString("lrc", "");
                    resp.elrc = json.optString("elrc", "");
                    resp.elrcMultiPerson = json.optString("elrcMultiPerson", "");
                    
                    // 2. Parse Songwriters from 'metadata' object
                    if (json.has("metadata")) {
                        JSONObject metadata = json.getJSONObject("metadata");
                        if (metadata.has("songwriters")) {
                            JSONArray writersArray = metadata.getJSONArray("songwriters");
                            for (int i = 0; i < writersArray.length(); i++) {
                                resp.songwriters.add(writersArray.getString(i));
                            }
                        }
                    }
                    
                    // 3. Parse Track Info from 'track' object
                    if (json.has("track")) {
                        JSONObject track = json.getJSONObject("track");
                        
                        // Parse All Genres
                        if (track.has("genreNames")) {
                            JSONArray genres = track.getJSONArray("genreNames");
                            for (int i = 0; i < genres.length(); i++) {
                                resp.genreNames.add(genres.getString(i));
                            }
                            // Set primary genre for compatibility
                            if (!resp.genreNames.isEmpty()) {
                                resp.genre = resp.genreNames.get(0);
                            }
                        }
                        
                        // Audio Locale
                        resp.audioLocale = track.optString("audioLocale", "");
                        
                        // Full Release Date
                        resp.releaseDate = track.optString("releaseDate", "");
                        
                        // Composer Name (String fallback)
                        resp.composerName = track.optString("composerName", null);
                        
                        // Content Rating
                        resp.contentRating = track.optString("contentRating", null);
                        
                        // ISRC
                        resp.isrc = track.optString("isrc", null);
                        
                        // Numbers
                        if (track.has("trackNumber")) {
                            resp.trackNumber = String.valueOf(track.getInt("trackNumber"));
                        }
                        if (track.has("discNumber")) {
                            resp.discNumber = String.valueOf(track.getInt("discNumber"));
                        }
                    }
                    
                    callback.onSuccess(resp);
                    
                } catch (Exception e) {
                    callback.onFailure("Error parsing lyrics: " + e.getMessage());
                }
            }
        });
    }

    public static void getLyricsByTitleAndArtist(
            String title, String artist, LyricsCallback callback) {

        searchSongs(title + " " + artist, new SearchCallback() {

            @Override
            public void onSuccess(ArrayList<Song> songs) {
                if (songs.isEmpty()) {
                    callback.onFailure("Song not found");
                    return;
                }
                getLyrics(songs.get(0).getId(), callback);
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }
}