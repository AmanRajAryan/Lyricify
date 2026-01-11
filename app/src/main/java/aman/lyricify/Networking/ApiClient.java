package aman.lyricify;

import okhttp3.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String USER_AGENT = "LYRICIFY";

    // Global Singleton Client
    public static final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(
                            chain -> {
                                Request original = chain.request();
                                Request request =
                                        original.newBuilder()
                                                .header("User-Agent", USER_AGENT)
                                                .build();
                                return chain.proceed(request);
                            })
                    .build();

    public static class LyricsResponse implements Serializable {
        public String plain;
        public String lrc;
        public String lrcMultiPerson;
        public String elrc;
        public String elrcMultiPerson;
        public String ttml;

        public String trackUrl;

        // Metadata
        public List<String> songwriters;
        public List<String> genreNames;
        public String audioLocale;
        public String genre;
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
            boolean hasPlain = plain != null && !plain.isEmpty();
            boolean hasTtml = ttml != null && !ttml.isEmpty();
            return hasPlain || hasTtml;
        }
    }

    // --- SHARED CACHE MECHANISM ---
    private static LyricsResponse globalCache;
    private static final List<CacheListener> listeners = new ArrayList<>();

    public interface CacheListener {
        void onCacheUpdated(LyricsResponse response);
    }

    public static void updateCache(LyricsResponse response) {
        globalCache = response;
        synchronized (listeners) {
            for (CacheListener listener : listeners) {
                listener.onCacheUpdated(response);
            }
        }
    }

    public static void registerCacheListener(CacheListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }
        if (globalCache != null) listener.onCacheUpdated(globalCache);
    }

    public static void unregisterCacheListener(CacheListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    // --- INTERFACES ---
    public interface SearchCallback {
        void onSuccess(ArrayList<Song> songs);

        void onFailure(String error);
    }

    public interface LyricsCallback {
        void onSuccess(LyricsResponse lyrics);

        void onFailure(String error);
    }

    // --- PUBLIC METHODS ---

    //
    public static void searchSongs(String query, SearchCallback callback) {
        String TAG = "ApiClient";

        // PRIORITY 1: Direct Apple Music Search
        Log.d(TAG, "🚀 Initiating Priority 1: Direct Apple Search for '" + query + "'");

        DirectAppleSearchRepository.search(
                client,
                query,
                new SearchCallback() {
                    @Override
                    public void onSuccess(ArrayList<Song> songs) {
                        Log.i(
                                TAG,
                                "✅ Direct Search SUCCESS. Returning " + songs.size() + " items.");
                        callback.onSuccess(songs);
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "⚠️ Direct Search FAILED: " + error);
                        Log.d(TAG, "🔄 Falling back to Priority 2: Worker Search...");

                        // PRIORITY 2: Original Worker Search (Fallback)
                        SearchRepository.search(
                                client,
                                query,
                                new SearchCallback() {
                                    @Override
                                    public void onSuccess(ArrayList<Song> songs) {
                                        Log.i(TAG, "✅ Worker Fallback SUCCESS.");
                                        callback.onSuccess(songs);
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Log.e(TAG, "❌ Worker Fallback FAILED: " + error);
                                        callback.onFailure(error);
                                    }
                                });
                    }
                });
    }
    
    
    
    
    
    
  
    public static void getLyrics(String songId, LyricsCallback callback) {
        getLyrics(songId, false, callback);
    }

   
    public static void getLyrics(String songId, boolean forceRefresh, LyricsCallback callback) {
        LyricsRepository.getLyrics(client, songId, forceRefresh, callback);
    }

    
    
    
    
    
    



    public static void getLyricsByTitleAndArtist(
            String title, String artist, LyricsCallback callback) {
        searchSongs(
                title + " " + artist,
                new SearchCallback() {
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





   