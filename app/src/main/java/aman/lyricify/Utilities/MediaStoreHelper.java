package aman.lyricify;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MediaStoreHelper {
    private static final String TAG = "MediaStoreHelper";

    // Represents a local audio file
    public static class LocalSong {
        public Uri fileUri;
        public String filePath;
        public String title;
        public String artist;
        public String album;
        public long duration;
        public int matchScore;

        public LocalSong(Uri fileUri, String filePath, String title, String artist, String album, long duration) {
            this.fileUri = fileUri;
            this.filePath = filePath;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.matchScore = 0;
        }
    }

    // Callback for search results
    public interface SearchCallback {
        void onFound(LocalSong song);
        void onNotFound();
        void onError(String error);
    }

    // Main method to search for a song by title and artist
    public static void searchLocalSong(Context context, String searchTitle, String searchArtist, SearchCallback callback) {
        new Thread(() -> {
            try {
                List<LocalSong> candidates = new ArrayList<>();
                candidates.addAll(queryMediaStore(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, searchTitle, searchArtist));
                candidates.addAll(queryMediaStore(context, MediaStore.Audio.Media.INTERNAL_CONTENT_URI, searchTitle, searchArtist));

                if (candidates.isEmpty()) {
                    callback.onNotFound();
                    return;
                }

                LocalSong bestMatch = findBestMatch(candidates, searchTitle, searchArtist);

                if (bestMatch != null && bestMatch.matchScore >= 60) {
                    callback.onFound(bestMatch);
                } else {
                    callback.onNotFound();
                }

            } catch (SecurityException e) {
                callback.onError("Storage permission not granted");
            } catch (Exception e) {
                Log.e(TAG, "Search error", e);
                callback.onError("Error searching: " + e.getMessage());
            }
        }).start();
    }

    // Queries MediaStore for songs at a specific URI (internal or external)
    private static List<LocalSong> queryMediaStore(Context context, Uri contentUri, String searchTitle, String searchArtist) {
        List<LocalSong> results = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String normalizedTitle = normalize(searchTitle);
        String normalizedArtist = normalize(searchArtist);

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
        };

        try (Cursor cursor = resolver.query(contentUri, projection, selection, null, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);

                    if (isLikelyMatch(title, artist, normalizedTitle, normalizedArtist)) {
                        long id = cursor.getLong(idCol);
                        Uri fileUri = ContentUris.withAppendedId(contentUri, id);
                        String path = cursor.getString(pathCol);
                        String album = cursor.getString(albumCol);
                        long duration = cursor.getLong(durationCol);

                        results.add(new LocalSong(fileUri, path, title, artist, album, duration));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore: " + contentUri, e);
        }

        return results;
    }

    // Checks if a song is a likely match based on title and artist
    private static boolean isLikelyMatch(String title, String artist, String searchTitle, String searchArtist) {
        if (title == null) return false;

        String normTitle = normalize(title);
        String normArtist = normalize(artist);

        boolean titleMatch = normTitle.contains(searchTitle) || searchTitle.contains(normTitle);
        boolean artistMatch = normArtist.isEmpty() || searchArtist.isEmpty() ||
                normArtist.contains(searchArtist) || searchArtist.contains(normArtist);

        return titleMatch && artistMatch;
    }

    // Finds the best matching song based on calculated scores
    private static LocalSong findBestMatch(List<LocalSong> candidates, String searchTitle, String searchArtist) {
        if (candidates.isEmpty()) return null;

        String normSearchTitle = normalize(searchTitle);
        String normSearchArtist = normalize(searchArtist);

        LocalSong bestMatch = null;
        int bestScore = 0;

        for (LocalSong song : candidates) {
            int score = calculateMatchScore(song, normSearchTitle, normSearchArtist);
            song.matchScore = score;

            if (score > bestScore) {
                bestScore = score;
                bestMatch = song;
            }
        }

        return bestMatch;
    }

    // Match scoring logic
    private static int calculateMatchScore(LocalSong song, String searchTitle, String searchArtist) {
        int score = 0;

        String normTitle = normalize(song.title);
        String normArtist = normalize(song.artist);

        if (normTitle.equals(searchTitle)) {
            score += 50;
        } else if (normTitle.contains(searchTitle)) {
            score += 30;
        } else if (searchTitle.contains(normTitle)) {
            score += 25;
        } else if (areSimilar(normTitle, searchTitle)) {
            score += 15;
        }

        if (normArtist.equals(searchArtist)) {
            score += 30;
        } else if (!normArtist.isEmpty() && normArtist.contains(searchArtist)) {
            score += 20;
        } else if (!searchArtist.isEmpty() && searchArtist.contains(normArtist)) {
            score += 15;
        }

        score += countCommonWords(normTitle, searchTitle) * 5;

        return score;
    }

    // Normalize strings for comparison
    private static String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Checks if two strings are somewhat similar
    private static boolean areSimilar(String s1, String s2) {
        int shorter = Math.min(s1.length(), s2.length());
        int longer = Math.max(s1.length(), s2.length());

        if (longer == 0) return false;

        int common = 0;
        for (int i = 0; i < shorter; i++) {
            if (i < s1.length() && i < s2.length() && s1.charAt(i) == s2.charAt(i)) {
                common++;
            }
        }

        return (common * 100 / longer) >= 60;
    }

    // Counts common words between two strings
    private static int countCommonWords(String s1, String s2) {
        String[] words1 = s1.split("\\s+");
        String[] words2 = s2.split("\\s+");

        int common = 0;
        for (String w1 : words1) {
            for (String w2 : words2) {
                if (w1.equals(w2) && w1.length() > 2) {
                    common++;
                    break;
                }
            }
        }
        return common;
    }
}