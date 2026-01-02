package aman.lyricify;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.HashMap;
import aman.taglib.TagLib; //

public class LyricsCacheManager {

    private static LyricsCacheManager instance;
    private static final String CACHE_FILE = "lyrics_cache.dat";
    
    // Map: FilePath -> CacheEntry
    private HashMap<String, CacheEntry> cache;
    private boolean isDirty = false; // Tracks if we have unsaved changes

    // Simple container for our cached data
    private static class CacheEntry implements Serializable {
        long lastModified;
        boolean hasLyrics;
        private static final long serialVersionUID = 1L;

        CacheEntry(long lastModified, boolean hasLyrics) {
            this.lastModified = lastModified;
            this.hasLyrics = hasLyrics;
        }
    }

    // Singleton pattern
    public static synchronized LyricsCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new LyricsCacheManager();
            instance.loadCache(context);
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private void loadCache(Context context) {
        File file = new File(context.getCacheDir(), CACHE_FILE);
        if (!file.exists()) {
            cache = new HashMap<>();
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            cache = (HashMap<String, CacheEntry>) ois.readObject();
        } catch (Exception e) {
            Log.e("LyricsCache", "Error loading cache", e);
            cache = new HashMap<>(); // Start fresh on corruption
        }
    }

    /**
     * Checks if a song has lyrics.
     * Uses cache if file hasn't changed. Parses file if dirty or unknown.
     */
    public boolean hasLyrics(String filePath) {
        if (filePath == null) return false;
        
        File file = new File(filePath);
        if (!file.exists()) return false;

        long currentModified = file.lastModified();
        CacheEntry entry = cache.get(filePath);

        // 1. HIT: Cache exists and file hasn't changed
        if (entry != null && entry.lastModified == currentModified) {
            return entry.hasLyrics;
        }

        // 2. MISS: File changed or new file. Scan with TagLib.
        boolean hasLyrics = checkFileWithTagLib(filePath);
        
        // Update cache
        cache.put(filePath, new CacheEntry(currentModified, hasLyrics));
        isDirty = true;
        
        return hasLyrics;
    }

    private boolean checkFileWithTagLib(String filePath) {
        try {
            TagLib tagLib = new TagLib();
            HashMap<String, String> metadata = tagLib.getMetadata(filePath);
            
            if (metadata != null) {
                // Check "LYRICS" key (case-insensitive)
                for (String key : metadata.keySet()) {
                    if (key.equalsIgnoreCase("LYRICS")) {
                        String value = metadata.get(key);
                        return value != null && !value.trim().isEmpty();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LyricsCache", "TagLib read error: " + filePath, e);
        }
        return false;
    }

    /**
     * Call this when scanning finishes to persist data to disk.
     */
    public void saveCache(Context context) {
        if (!isDirty) return; // Don't write if nothing changed

        File file = new File(context.getCacheDir(), CACHE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(cache);
            isDirty = false;
        } catch (IOException e) {
            Log.e("LyricsCache", "Error saving cache", e);
        }
    }
}
