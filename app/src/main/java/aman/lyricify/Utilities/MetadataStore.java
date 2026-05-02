package aman.lyricify;

import java.util.ArrayList;
import java.util.List;

public class MetadataStore {
    private static ApiClient.LyricsResponse cachedResponse;
    private static boolean isFetching = false;
    private static final List<OnMetadataListener> listeners = new ArrayList<>();

    public interface OnMetadataListener {
        void onMetadataReady(ApiClient.LyricsResponse response);
    }

    public static void update(ApiClient.LyricsResponse response) {
        cachedResponse = response;
        isFetching = false;
        notifyListeners();
    }

    public static void setFetching(boolean fetching) {
        isFetching = fetching;
        if (fetching) cachedResponse = null; 
    }

    public static ApiClient.LyricsResponse get() {
        return cachedResponse;
    }

    public static boolean isFetching() {
        return isFetching;
    }

    public static void addListener(OnMetadataListener listener) {
        listeners.add(listener);
        if (cachedResponse != null) {
            listener.onMetadataReady(cachedResponse);
        }
    }

    public static void removeListener(OnMetadataListener listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        if (cachedResponse != null) {
            for (OnMetadataListener listener : new ArrayList<>(listeners)) {
                listener.onMetadataReady(cachedResponse);
            }
            listeners.clear();
        }
    }
    
    public static void clear() {
        cachedResponse = null;
        isFetching = false;
        listeners.clear();
    }
}
