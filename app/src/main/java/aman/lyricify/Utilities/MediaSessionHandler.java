package aman.lyricify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Handles all MediaSession related operations including: - Finding active media controllers -
 * Extracting metadata and artwork - Managing controller callbacks
 */
public class MediaSessionHandler {

    private final WeakReference<Context> contextRef;
    private final MediaSessionManager mediaSessionManager;
    private MediaController currentController;
    private MediaController.Callback controllerCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionListener;
    private MediaSessionCallback callback;

    public interface MediaSessionCallback {
        void onMediaFound(String title, String artist, Bitmap artwork);

        void onMediaLost();

        void onMetadataChanged();
    }

    public MediaSessionHandler(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.mediaSessionManager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void setCallback(MediaSessionCallback callback) {
        this.callback = callback;
    }

    /** Initialize media session listener */
    public void initialize() {
        Context context = contextRef.get();
        if (context == null) return;

        try {
            sessionListener =
                    controllers -> {
                        MediaController activeController = findActiveController(controllers);
                        if (activeController != null) {
                            assignController(activeController);
                            notifyMediaFound(activeController);
                        } else {
                            notifyMediaLost();
                        }
                    };

            mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionListener, new ComponentName(context, SongNotificationListener.class));
        } catch (SecurityException e) {
            Toast.makeText(context, "Enable Notification Access for Lyricify", Toast.LENGTH_LONG)
                    .show();
        }
    }

    /** Check for currently active media sessions */
    public void checkActiveSessions() {
        Context context = contextRef.get();
        if (context == null || mediaSessionManager == null) return;

        try {
            List<MediaController> controllers =
                    mediaSessionManager.getActiveSessions(
                            new ComponentName(context, SongNotificationListener.class));

            MediaController activeController = findActiveController(controllers);
            if (activeController != null) {
                assignController(activeController);
                notifyMediaFound(activeController);
            } else {
                notifyMediaLost();
            }
        } catch (SecurityException e) {
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                Log.e("MediaSessionCheck", "Unable to open notification access settings", ex);
            }
        }
    }

    /** Find an active controller from the list */
    private MediaController findActiveController(List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                int playbackState = state.getState();
                if (playbackState == PlaybackState.STATE_PLAYING
                        || playbackState == PlaybackState.STATE_PAUSED) {

                    MediaMetadata metadata = controller.getMetadata();
                    if (metadata != null && hasValidTrackInfo(metadata)) {
                        return controller;
                    }
                }
            }
        }
        return null;
    }

    /** Check if metadata has valid track information */
    private boolean hasValidTrackInfo(MediaMetadata metadata) {
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return (title != null && !title.trim().isEmpty())
                || (artist != null && !artist.trim().isEmpty());
    }

    /** Assign a new media controller and set up callbacks */
    private void assignController(MediaController controller) {
        // Unregister old callback
        if (currentController != null && controllerCallback != null) {
            try {
                currentController.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {
            }
        }

        currentController = controller;
        controllerCallback =
                new MediaController.Callback() {
                    @Override
                    public void onMetadataChanged(MediaMetadata metadata) {
                        if (callback != null) {
                            callback.onMetadataChanged();
                        }
                        notifyMediaFound(currentController);
                    }

                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        if (state != null) {
                            int playbackState = state.getState();
                            if (playbackState == PlaybackState.STATE_PLAYING
                                    || playbackState == PlaybackState.STATE_PAUSED) {
                                notifyMediaFound(currentController);
                            } else {
                                notifyMediaLost();
                            }
                        } else {
                            notifyMediaLost();
                        }
                    }
                };

        try {
            controller.registerCallback(controllerCallback);
        } catch (Exception ignored) {
        }
    }

    /** Extract and notify about found media */
    private void notifyMediaFound(MediaController controller) {
        if (callback == null || controller == null) return;

        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();

        if (state == null || metadata == null || !hasValidTrackInfo(metadata)) {
            notifyMediaLost();
            return;
        }

        int playbackState = state.getState();
        if (playbackState != PlaybackState.STATE_PLAYING
                && playbackState != PlaybackState.STATE_PAUSED) {
            notifyMediaLost();
            return;
        }

        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        // Fallback for artist
        if (artist == null || artist.trim().isEmpty()) {
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        if (artist == null || artist.trim().isEmpty()) {
            String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            artist = album != null ? album : "Unknown Artist";
        }
        if (title == null || title.trim().isEmpty()) {
            title = "Unknown Track";
        }

        Bitmap artwork = extractArtwork(metadata);
        callback.onMediaFound(title, artist, artwork);
    }

    /** Notify that media is lost */
    private void notifyMediaLost() {
        if (callback != null) {
            callback.onMediaLost();
        }
    }

    /** Extract artwork from metadata */
    public static Bitmap extractArtwork(MediaMetadata metadata) {
        if (metadata == null) return null;

        String[] keys = {
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        };

        for (String key : keys) {
            try {
                Bitmap artwork = metadata.getBitmap(key);
                if (isValidBitmap(artwork)) {
                    return artwork;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Check if bitmap is valid */
    public static boolean isValidBitmap(Bitmap bitmap) {
        return bitmap != null
                && !bitmap.isRecycled()
                && bitmap.getWidth() > 0
                && bitmap.getHeight() > 0;
    }

    /** Clean up resources */
    public void cleanup() {
        if (currentController != null && controllerCallback != null) {
            try {
                currentController.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {
            }
        }

        if (sessionListener != null && mediaSessionManager != null) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
            } catch (Exception ignored) {
            }
        }
    }
}
