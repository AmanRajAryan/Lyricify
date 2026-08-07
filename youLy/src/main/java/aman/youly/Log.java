package aman.youly;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public class Log {
    
    // URI for your LogHub
    private static final Uri LOGHUB_URI = Uri.parse("content://aman.loghub.provider/logs");
    private static Context context;
    private static String appName = "YouLy-Module";

    // Call this from your Fragment's onAttach or onCreate
    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
    }

    // --- LOGGING METHODS ---

    public static int d(String tag, String msg) {
        sendToLogHub("DEBUG", tag, msg);
        return android.util.Log.d(tag, msg);
    }

    public static int i(String tag, String msg) {
        sendToLogHub("INFO", tag, msg);
        return android.util.Log.i(tag, msg);
    }

    public static int w(String tag, String msg) {
        sendToLogHub("WARN", tag, msg);
        return android.util.Log.w(tag, msg);
    }

    public static int e(String tag, String msg) {
        sendToLogHub("ERROR", tag, msg);
        return android.util.Log.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        String fullMsg = msg + "\n" + android.util.Log.getStackTraceString(tr);
        sendToLogHub("ERROR", tag, fullMsg);
        return android.util.Log.e(tag, msg, tr);
    }

    // --- HELPER ---

    private static void sendToLogHub(String level, String tag, String msg) {
        if (context == null) return;
        
        new Thread(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put("app_name", appName);
                v.put("tag", tag);
                v.put("message", msg);
                v.put("level", level);
                v.put("timestamp", System.currentTimeMillis());
                context.getContentResolver().insert(LOGHUB_URI, v);
            } catch (Exception e) {
                // Silently fail if LogHub is not installed
            }
        }).start();
    }
}
