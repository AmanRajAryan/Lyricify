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
        "com.bsbportal.music", "com.spotify.lite"
    ));
    
    private static final Set<String> MUSIC_KEYWORDS = new HashSet<>(Arrays.asList(
        "music", "audio", "player", "radio", "podcast", "sound", "media",
        "mp3", "beats", "tune", "song", "album", "track", "play", "stream"
    ));
    
    private static final Set<String> STRICT_BLACKLIST = new HashSet<>(Arrays.asList(
        "gradle", "build", "system", "android.system", "launcher", "settings",
        "camera", "gallery", "photos", "screenshot", "screen", "recording", "recorder",
        "email", "gmail", "message", "whatsapp", "telegram", "signal",
        "chrome", "browser", "maps", "weather", "calendar", "clock"
    ));
    
    private static final Set<String> BASIC_BLACKLIST = new HashSet<>(Arrays.asList(
        "gradle", "build", "system.ui", "launcher", "settings",
        "screen", "recording", "recorder", "camera", "gallery"
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
        try {
            unregisterReceiver(requestReceiver);
        } catch (Exception ignored) {}
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

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        lastNotificationKey = "";
        lastNotificationTime = 0;
    }

    public void checkExistingNotifications() {
        try {
            StatusBarNotification[] notifications = getActiveNotifications();
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
        int score = 0;
        Bundle extras = notification.extras;
        if (extras == null) return 0;
        
        String lowerPackage = packageName.toLowerCase();
        
        if (KNOWN_MUSIC_APPS.contains(lowerPackage)) {
            score += 1000;
        } else if (!isExisting) {
            score += 50;
        }
        
        if (hasMediaStyleTemplate(notification)) {
            score += 800;
        }
        
        try {
            if (extras.get("android.mediaSession") != null) {
                score += 500;
            }
        } catch (Exception ignored) {}
        
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)) {
            score += 300;
        }
        
        if (hasMediaControls(notification)) {
            score += 200;
        }
        
        String title = extras.getString(Notification.EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) {
            return 0;
        }
        
        String lowerTitle = title.toLowerCase();
        Set<String> blacklist = isExisting ? BASIC_BLACKLIST : STRICT_BLACKLIST;
        
        for (String keyword : blacklist) {
            if (lowerPackage.contains(keyword) || lowerTitle.contains(keyword)) {
                return 0;
            }
        }
        
        if (isExisting && hasMusicKeywords(lowerPackage)) {
            score += 100;
        }
        
        if (!isExisting && !KNOWN_MUSIC_APPS.contains(lowerPackage)) {
            boolean hasStrongIndicators = hasMediaStyleTemplate(notification) ||
                                        extras.get("android.mediaSession") != null ||
                                        hasMediaControls(notification) ||
                                        Notification.CATEGORY_TRANSPORT.equals(notification.category);
            if (!hasStrongIndicators) {
                return 0;
            }
        }
        
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            score += isExisting ? 150 : 100;
        }
        
        if (hasLargeIcon(notification, extras)) {
            score += isExisting ? 100 : 50;
        }
        
        if (extras.get(Notification.EXTRA_PROGRESS) != null) {
            score += isExisting ? 75 : 50;
        }
        
        return score;
    }

    private boolean hasMediaStyleTemplate(Notification notification) {
        try {
            String templateClass = notification.extras.getString("android.template");
            if (templateClass != null && templateClass.contains("MediaStyle")) {
                return true;
            }
            
            if (notification.actions != null && notification.actions.length >= 1 && notification.actions.length <= 5) {
                int mediaActionCount = 0;
                for (Notification.Action action : notification.actions) {
                    if (isMediaAction(action)) {
                        mediaActionCount++;
                    }
                }
                return mediaActionCount >= notification.actions.length / 2;
            }
            
            return notification.extras.containsKey("android.mediaRemoteDevice") ||
                   notification.extras.containsKey("android.mediaRemoteIcon") ||
                   notification.extras.containsKey("android.mediaRemoteIntent");
            
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isMediaAction(Notification.Action action) {
        if (action.title != null) {
            String actionTitle = action.title.toString().toLowerCase();
            for (String keyword : MEDIA_ACTION_KEYWORDS) {
                if (actionTitle.contains(keyword)) {
                    return true;
                }
            }
        }
        return action.getIcon() != null;
    }

    private void processNotification(StatusBarNotification sbn, String source) {
        if (sbn == null || sbn.getNotification() == null) return;

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) return;

        int score = getNotificationScore(packageName, notification, source.equals("checkExisting"));
        int minScore = source.equals("checkExisting") ? 100 : 200;
        
        if (score < minScore) return;

        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        String artist = text != null ? text.toString() : "";

        if (artist.isEmpty()) {
            artist = extras.getString(Notification.EXTRA_SUB_TEXT);
            if (artist == null) artist = "";
        }

        if (title == null || title.trim().isEmpty()) return;
        
        String notificationKey = packageName + ":" + title + ":" + artist;
        long currentTime = System.currentTimeMillis();
        
        if (notificationKey.equals(lastNotificationKey) && (currentTime - lastNotificationTime) < 1000) {
            return;
        }
        
        lastNotificationKey = notificationKey;
        lastNotificationTime = currentTime;

        Bitmap artwork = extractNotificationArtwork(notification, extras);

        Intent intent = new Intent("aman.lyricify.SONG_NOTIFICATION");
        intent.putExtra("songTitle", title);
        intent.putExtra("artist", artist);
        intent.putExtra("packageName", packageName);
        intent.putExtra("source", source);
        intent.putExtra("score", score);
        
        if (artwork != null) {
            intent.putExtra("artwork", artwork);
        }
        
        sendBroadcast(intent);
    }

    private boolean hasMediaControls(Notification notification) {
        if (notification.actions == null || notification.actions.length == 0) {
            return false;
        }

        for (Notification.Action action : notification.actions) {
            if (action.title != null) {
                String actionTitle = action.title.toString().toLowerCase();
                for (String keyword : MEDIA_ACTION_KEYWORDS) {
                    if (actionTitle.contains(keyword)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLargeIcon(Notification notification, Bundle extras) {
        return (notification.getLargeIcon() != null) || 
               (notification.largeIcon != null) ||
               (extras.get(Notification.EXTRA_LARGE_ICON) != null) ||
               (extras.getParcelable(Notification.EXTRA_PICTURE) != null);
    }

    private boolean hasMusicKeywords(String packageName) {
        for (String keyword : MUSIC_KEYWORDS) {
            if (packageName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Bitmap extractNotificationArtwork(Notification notification, Bundle extras) {
        try {
            Bitmap picture = (Bitmap) extras.getParcelable(Notification.EXTRA_PICTURE);
            if (isValidBitmap(picture)) {
                return picture;
            }

            Object largeIconObj = extras.get(Notification.EXTRA_LARGE_ICON);
            if (largeIconObj != null) {
                Bitmap bitmap = null;
                
                if (largeIconObj instanceof Bitmap) {
                    bitmap = (Bitmap) largeIconObj;
                } else if (largeIconObj instanceof Icon) {
                    try {
                        Icon icon = (Icon) largeIconObj;
                        Drawable drawable = icon.loadDrawable(this);
                        if (drawable instanceof BitmapDrawable) {
                            bitmap = ((BitmapDrawable) drawable).getBitmap();
                        }
                    } catch (Exception ignored) {}
                }
                
                if (isValidBitmap(bitmap)) {
                    return bitmap;
                }
            }

            if (notification.getLargeIcon() != null) {
                try {
                    Drawable drawable = notification.getLargeIcon().loadDrawable(this);
                    if (drawable instanceof BitmapDrawable) {
                        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                        if (isValidBitmap(bitmap)) {
                            return bitmap;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (isValidBitmap(notification.largeIcon)) {
                return notification.largeIcon;
            }

        } catch (Exception ignored) {}

        return null;
    }

    private boolean isValidBitmap(Bitmap bitmap) {
        return bitmap != null && 
               !bitmap.isRecycled() && 
               bitmap.getWidth() > 0 && 
               bitmap.getHeight() > 0 &&
               bitmap.getConfig() != null &&
               bitmap.getByteCount() > 0;
    }
}