package aman.lyricify;

import java.util.List;
import java.util.ArrayList;
import okhttp3.OkHttpClient;

public class MotionRepository {

    public static class MotionOption {
        public String type;
        public int width;
        public int height;
        public String m3u8Url;
        public String sizeText;
        public String bitrateText;
        
        public MotionOption(String type, int width, int height, String m3u8Url, String sizeText, String bitrateText) {
            this.type = type;
            this.width = width;
            this.height = height;
            this.m3u8Url = m3u8Url;
            this.sizeText = sizeText;
            this.bitrateText = bitrateText;
        }
    }

    public interface MotionCallback {
        void onSuccess(List<MotionOption> options);
        void onFailure(String error);
    }

    public interface UrlCallback {
        void onSuccess(String url);
    }

    public static void fetchMotionCovers(OkHttpClient client, String trackUrl, MotionCallback callback) {
        // Since motion covers require Apple Music URLs and the search API was migrated to Spotify,
        // and there is no documented endpoint for motion covers in the new API,
        // we return an empty list or error for now.
        callback.onFailure("Motion covers are currently unsupported with the new API.");
    }

    public static void resolveMp4Url(OkHttpClient client, String m3u8Url, UrlCallback callback) {
        // Dummy implementation since fetchMotionCovers will fail
        callback.onSuccess(null);
    }
}
