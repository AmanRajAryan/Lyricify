package aman.lyricify;

import android.content.Context;

import java.io.*;
import java.util.HashMap;
import aman.taglib.TagLib;

public class LyricsCacheManager {

    private static LyricsCacheManager instance;
    private static final String CACHE_FILE = "lyrics_cache.dat";
    
    private HashMap<String, CacheEntry> cache;
    private boolean isDirty = false;

    private static class CacheEntry implements Serializable {
        long lastModified;
        boolean hasLyrics;
        private static final long serialVersionUID = 1L;

        CacheEntry(long lastModified, boolean hasLyrics) {
            this.lastModified = lastModified;
            this.hasLyrics = hasLyrics;
        }
    }

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
            cache = new HashMap<>();
        }
    }

    public boolean hasLyrics(String filePath) {
        if (filePath == null) return false;
        
        File file = new File(filePath);
        if (!file.exists()) return false;

        long currentModified = file.lastModified();
        CacheEntry entry = cache.get(filePath);

        if (entry != null && entry.lastModified == currentModified) {
            return entry.hasLyrics;
        }

        boolean hasLyrics = checkFileWithTagLib(filePath);
        
        cache.put(filePath, new CacheEntry(currentModified, hasLyrics));
        isDirty = true;
        
        return hasLyrics;
    }

    private boolean checkFileWithTagLib(String filePath) {
        try {
            TagLib tagLib = new TagLib();
            HashMap<String, String> metadata = tagLib.getMetadata(filePath);
            
            if (metadata != null) {
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

    public void saveCache(Context context) {
        if (!isDirty) return;

        File file = new File(context.getCacheDir(), CACHE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(cache);
            isDirty = false;
        } catch (IOException e) {
            Log.e("LyricsCache", "Error saving cache", e);
        }
    }
}
