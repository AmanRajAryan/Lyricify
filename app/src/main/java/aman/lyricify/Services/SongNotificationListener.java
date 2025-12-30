package aman.lyricify;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SongNotificationListener extends NotificationListenerService {

    private String lastNotificationKey = "";
    private long lastNotificationTime = 0;
    
    private static final Set<String> KNOWN_MUSIC_APPS = new HashSet<>(Arrays.asList(
        "com.spotify.music", "com.google.android.music", "com.apple.android.music",
        "com.amazon.mp3", "deezer.android.app", "com.soundcloud.android",
        "com.pandora.android", "fm.last.android", "com.google.android.apps.youtube.music",
        "com.aspiro.tidal", "com.netease.cloudmusic", "com.tencent.qqmusic",
        "com.xiaomi.music", "com.anghami", "com.bandcamp.android",
        "com.poweramp.music", "com.maxmpz.music", "com.vlc.mobile", 
        "com.foobar2000.foobar2000", "com.aimp.player", "com.neutroncode.mp",
        "com.cloudplayer.scplayer", "com.tbig.playerpro", "com.doubleTwist.androidPlayer",
        "com.jetappfactory.jetaudio", "tunein.player", "com.iheart.radio",
        "com.slacker.radio", "com.stitcher.app", "com.google.android.apps.podcasts",
        "com.castbox.audiobook.radio.podcast", "com.jio.media.jiobeats",
        "com.gaana", "com.wynk.music", "com.hungama.myplay.activity",
        "com.bsbportal.music", "com.spotify.lite", "com.atesso.musicolet"
    ));
    
    private static final Set<String> MUSIC_KEYWORDS = new HashSet<>(Arrays.asList(
        "music", "audio", "player", "radio", "podcast", "sound", "media",
        "mp3", "beats", "tune", "song", "album", "track", "play", "stream"
    ));
    
    private static final Set<String> MEDIA_ACTION_KEYWORDS = new HashSet<>(Arrays.asList(
        "play", "pause", "next", "previous", "stop", "skip", "forward", "rewind"
    ));
    
    private final BroadcastReceiver requestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("aman.lyricify.REQUEST_EXISTING_CHECK".equals(intent.getAction())) {
                checkExistingNotifications();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(requestReceiver, new IntentFilter("aman.lyricify.REQUEST_EXISTING_CHECK"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(requestReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        processNotification(sbn, "onNotificationPosted");
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && sbn.getNotification() != null) {
            String packageName = sbn.getPackageName();
            if (KNOWN_MUSIC_APPS.contains(packageName.toLowerCase())) {
                lastNotificationKey = "";
                lastNotificationTime = 0;
            }
        }
    }

    public void checkExistingNotifications() {
        try {
            StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications == null) return;
            
            StatusBarNotification bestMediaNotification = null;
            int bestScore = 0;
            
            for (StatusBarNotification sbn : notifications) {
                if (sbn != null && sbn.getNotification() != null) {
                    int score = getNotificationScore(sbn.getPackageName(), sbn.getNotification(), true);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMediaNotification = sbn;
                    }
                }
            }
            
            if (bestMediaNotification != null && bestScore >= 100) {
                processNotification(bestMediaNotification, "checkExisting");
            }
        } catch (Exception ignored) {}
    }

    private int getNotificationScore(String packageName, Notification notification, boolean isExisting) {
        // (Logic remains same as original for brevity, it was correct)
        int score = 0;
        Bundle extras = notification.extras;
        if (extras == null) return 0;
        String lowerPackage = packageName.toLowerCase();
        
        if (KNOWN_MUSIC_APPS.contains(lowerPackage)) score += 1000;
        else if (!isExisting) score += 50;
        
        if (hasMediaStyleTemplate(notification)) score += 800;
        try { if (extras.get("android.mediaSession") != null) score += 500; } catch (Exception ignored) {}
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)) score += 300;
        if (hasMediaControls(notification)) score += 200;
        
        String title = extras.getString(Notification.EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) return 0;
        
        return score;
    }

    private boolean hasMediaStyleTemplate(Notification notification) {
        try {
            String templateClass = notification.extras.getString("android.template");
            if (templateClass != null && templateClass.contains("MediaStyle")) return true;
            return notification.extras.containsKey("android.mediaRemoteDevice");
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hasMediaControls(Notification notification) {
        if (notification.actions == null) return false;
        for (Notification.Action action : notification.actions) {
            if (action.title != null) {
                String t = action.title.toString().toLowerCase();
                for (String k : MEDIA_ACTION_KEYWORDS) if (t.contains(k)) return true;
            }
        }
        return false;
    }

    private void processNotification(StatusBarNotification sbn, String source) {
        if (sbn == null || sbn.getNotification() == null) return;

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        int score = getNotificationScore(packageName, notification, source.equals("checkExisting"));
        if (score < 100) return;

        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        String artist = text != null ? text.toString() : "";
        if (artist.isEmpty()) {
            artist = extras.getString(Notification.EXTRA_SUB_TEXT);
            if (artist == null) artist = "";
        }

        if (title == null || title.trim().isEmpty()) return;
        
        // Dedup logic
        String notificationKey = packageName + ":" + title + ":" + artist;
        long currentTime = System.currentTimeMillis();
        if (notificationKey.equals(lastNotificationKey) && (currentTime - lastNotificationTime) < 1000) return;
        
        lastNotificationKey = notificationKey;
        lastNotificationTime = currentTime;

        Bitmap artwork = extractNotificationArtwork(notification, extras);

        Intent intent = new Intent("aman.lyricify.SONG_NOTIFICATION");
        intent.putExtra("songTitle", title);
        intent.putExtra("artist", artist);
        intent.putExtra("packageName", packageName);
        intent.putExtra("source", source);
        
        // FIX: Resize bitmap if too large to prevent TransactionTooLargeException
        if (artwork != null) {
            if (artwork.getByteCount() > 500000) { // > 500KB
                float aspectRatio = (float) artwork.getWidth() / artwork.getHeight();
                int newWidth = 300; 
                int newHeight = (int) (newWidth / aspectRatio);
                if (newWidth > 0 && newHeight > 0) {
                    try {
                        Bitmap scaled = Bitmap.createScaledBitmap(artwork, newWidth, newHeight, true);
                        intent.putExtra("artwork", scaled);
                    } catch (Exception e) {
                        // If scaling fails, don't send artwork
                    }
                }
            } else {
                intent.putExtra("artwork", artwork);
            }
        }
        
        sendBroadcast(intent);
    }

    private Bitmap extractNotificationArtwork(Notification notification, Bundle extras) {
        try {
            Bitmap picture = (Bitmap) extras.getParcelable(Notification.EXTRA_PICTURE);
            if (isValidBitmap(picture)) return picture;

            Object largeIconObj = extras.get(Notification.EXTRA_LARGE_ICON);
            if (largeIconObj != null) {
                if (largeIconObj instanceof Bitmap && isValidBitmap((Bitmap) largeIconObj)) {
                    return (Bitmap) largeIconObj;
                } else if (largeIconObj instanceof Icon) {
                    Drawable d = ((Icon) largeIconObj).loadDrawable(this);
                    if (d instanceof BitmapDrawable && isValidBitmap(((BitmapDrawable) d).getBitmap())) {
                        return ((BitmapDrawable) d).getBitmap();
                    }
                }
            }
            if (notification.getLargeIcon() != null) {
                Drawable d = notification.getLargeIcon().loadDrawable(this);
                if (d instanceof BitmapDrawable && isValidBitmap(((BitmapDrawable) d).getBitmap())) {
                    return ((BitmapDrawable) d).getBitmap();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isValidBitmap(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0;
    }
}
