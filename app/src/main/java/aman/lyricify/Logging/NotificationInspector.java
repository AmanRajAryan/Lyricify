package aman.lyricify;

import android.app.Notification;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;

import java.util.Set;

public class NotificationInspector {

    private static Bundle lastExtras = null;
    private static Bundle lastMetadata = null;
    private static PlaybackState lastPlayback = null;
    private static String lastPackage = "";
    public static Bitmap lastBitmap;

    static int callCount = 0;

    public static void update(
            Notification notification, String packageName, MediaController controller) {
        if (notification != null && notification.extras != null) {
            lastExtras = new Bundle(notification.extras);
        }
        lastPackage = packageName != null ? packageName : "";

        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                lastMetadata = new Bundle();
                for (String key : metadata.keySet()) {
                    Object value = null;
                    try {
                        // Try String
                        String s = metadata.getString(key);
                        if (s != null) value = s;

                        // Try Long
                        if (value == null) {
                            long l = metadata.getLong(key);
                            if (l != 0) value = l;
                        }

                        // Try Bitmap
                        if (value == null) {
                            Bitmap bm = metadata.getBitmap(key);
                            if (bm != null) {
                                lastBitmap = bm;
                                value = "[Bitmap " + bm.getWidth() + "x" + bm.getHeight() + "]";
                            }
                        }

                        // Try Rating
                        if (value == null && metadata.getRating(key) != null) {
                            value = metadata.getRating(key).toString();
                        }
                    } catch (Exception ignored) {
                    }

                    if (value != null) {
                        lastMetadata.putString(key, value.toString());
                    }
                }
            }
            lastPlayback = controller.getPlaybackState();
        }
    }

    public static Bitmap getLastArtwork() {
        if (lastBitmap != null) {
            return lastBitmap;
        } else {
            return null;
        }
    }

    public static String getRawDump() {
        callCount++;
        StringBuilder sb = new StringBuilder();

        sb.append("=== PackageName ===\n").append(lastPackage).append("\n");

        if (lastExtras != null) {
            sb.append("=== Notification Extras ===\n");
            dumpBundle(lastExtras, sb);
        }

        if (lastMetadata != null) {
            sb.append("=== Metadata ===\n");
            dumpBundle(lastMetadata, sb);
        }

        if (lastPlayback != null) {
            sb.append("=== PlaybackState ===\n");
            sb.append("State: ").append(lastPlayback.getState()).append("\n");
            sb.append("Position: ").append(lastPlayback.getPosition()).append("\n");
            sb.append("Speed: ").append(lastPlayback.getPlaybackSpeed()).append("\n");
            sb.append("Actions: ").append(lastPlayback.getActions()).append("\n");
        }

        if (lastExtras == null && lastMetadata == null && lastPlayback == null) {
            sb.append("No data captured ").append(callCount).append("\n");
        }

        return sb.toString();
    }

    private static void dumpBundle(Bundle bundle, StringBuilder sb) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object val = bundle.get(key);
            sb.append(key).append(": ").append(val != null ? val.toString() : "null").append("\n");
        }
    }
}
