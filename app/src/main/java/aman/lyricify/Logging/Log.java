package aman.lyricify;


import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

/**
>>>ADD THIS IN MANIFEST

<queries>
        <provider android:authorities="aman.loghub.provider" />
    </queries>
**/








/**
 * ============================================================================
 * LogHub - Centralized Logging System
 * ============================================================================
 * 
 * A single-file drop-in replacement for android.util.Log that automatically
 * sends all logs to a centralized LogHub app for monitoring multiple apps
 * in one place. Also captures crashes automatically.
 * 
 * ============================================================================
 * FEATURES
 * ============================================================================
 * ✓ Drop-in replacement for android.util.Log (same API)
 * ✓ Logs to both logcat AND LogHub app simultaneously
 * ✓ Automatic crash capture and reporting
 * ✓ Auto-detects app name from manifest
 * ✓ Thread-safe background logging
 * ✓ Fail-safe (won't crash your app if LogHub is unavailable)
 * ✓ Single file solution - just copy and use
 * ✓ Zero runtime dependencies
 * 
 * ============================================================================
 * SETUP INSTRUCTIONS
 * ============================================================================
 * 
 * Step 1: Copy this file to your project
 * --------
 * Just copy this Log.java file to your project and change the package name
 * at the top to match your app's package.
 * 
 * Step 2: Update AndroidManifest.xml
 * --------
 * Add two things to your AndroidManifest.xml:
 * 
 * a) Add queries declaration (required for Android 11+):
 *    <manifest>
 *        <queries>
 *            <provider android:authorities="aman.loghub.provider" />
 *        </queries>
 *        ...
 *    </manifest>
 * 
 * b) Set this class as your Application:
 *    <application
 *        android:name=".Log"
 *        android:icon="@mipmap/ic_launcher"
 *        android:label="@string/app_name"
 *        ...>
 *        
 *        <!-- Your activities here -->
 *        
 *    </application>
 * 
 * Step 3: That's it! Just start using it.
 * --------
 * No initialization needed. Just use Log.d(), Log.i(), etc. anywhere.
 * 
 * ============================================================================
 * USAGE EXAMPLES
 * ============================================================================
 * 
 * Basic logging:
 * --------------
 * Log.d("MyTag", "Debug message");
 * Log.i("MyTag", "Info message");
 * Log.w("MyTag", "Warning message");
 * Log.e("MyTag", "Error message");
 * Log.v("MyTag", "Verbose message");
 * 
 * Logging with exceptions:
 * ------------------------
 * try {
 *     // Some code
 * } catch (Exception e) {
 *     Log.e("MyTag", "Something went wrong", e);
 * }
 * 
 * Critical failures:
 * ------------------
 * if (userId == null) {
 *     Log.wtf("MyTag", "User ID should never be null here!");
 * }
 * 
 * Custom app name (optional):
 * ---------------------------
 * // By default, app name is auto-detected from manifest
 * // But you can override it if needed:
 * Log.setAppName("MyCustomAppName");
 * 
 * ============================================================================
 * LOG LEVELS EXPLAINED
 * ============================================================================
 * 
 * VERBOSE (v): Detailed diagnostic information for debugging specific issues
 *              Example: "Entering method calculateTotal()"
 *              Use when: You need extremely detailed flow information
 * 
 * DEBUG (d):   General debugging information useful during development
 *              Example: "User clicked login button"
 *              Use when: Tracking application flow and state changes
 * 
 * INFO (i):    Important runtime events and milestones
 *              Example: "User logged in successfully"
 *              Use when: Recording significant application events
 * 
 * WARN (w):    Potentially harmful situations that don't stop execution
 *              Example: "Network request took 5 seconds (slow connection)"
 *              Use when: Something unusual happened but app can continue
 * 
 * ERROR (e):   Error events that might still allow the app to continue
 *              Example: "Failed to load user profile image"
 *              Use when: An operation failed but app remains functional
 * 
 * WTF (wtf):   "What a Terrible Failure" - should never happen conditions
 *              Example: "Database is null after initialization"
 *              Use when: Impossible/critical bugs that violate core assumptions
 *              Note: On debug builds, this can crash your app immediately
 * 
 * ============================================================================
 * HOW IT WORKS
 * ============================================================================
 * 
 * 1. Extends Application class to auto-initialize on app startup
 * 2. Sets up crash handler to capture uncaught exceptions
 * 3. Auto-detects app name from AndroidManifest.xml
 * 4. All log methods forward to android.util.Log (for logcat)
 * 5. Simultaneously sends logs to LogHub via ContentProvider in background
 * 6. Uses background threads so logging never blocks your UI
 * 7. Silently fails if LogHub app is not installed (graceful degradation)
 * 
 * ============================================================================
 * CRASH CAPTURE
 * ============================================================================
 * 
 * Crashes are automatically captured and logged to LogHub with:
 * - Exception class name
 * - Error message
 * - Thread name where crash occurred
 * - Complete stack trace
 * - Tagged as "CRASH" in LogHub for easy filtering
 * 
 * The app will still crash normally (this doesn't prevent crashes),
 * but you'll have a record of it in LogHub for debugging.
 * 
 * ============================================================================
 * REQUIREMENTS
 * ============================================================================
 * 
 * - Android API 14+ (Ice Cream Sandwich and above)
 * - LogHub app installed on the same device
 * - Queries permission in manifest (for Android 11+)
 * 
 * ============================================================================
 * ARCHITECTURE
 * ============================================================================
 * 
 * This class serves dual purposes:
 * 
 * 1. Application Class:
 *    - Extends android.app.Application
 *    - Auto-initializes when app starts
 *    - Sets up crash handler
 *    
 * 2. Static Logging API:
 *    - Provides static methods (Log.d, Log.i, etc.)
 *    - Matches android.util.Log API exactly
 *    - Routes logs to both logcat and LogHub
 * 
 * ============================================================================
 * THREAD SAFETY
 * ============================================================================
 * 
 * All log methods are thread-safe. LogHub communication happens on background
 * threads so your UI thread is never blocked. Crash handler works on any thread.
 * 
 * ============================================================================
 * PERFORMANCE
 * ============================================================================
 * 
 * - Minimal overhead: Just creates a background thread for each log
 * - Non-blocking: Never waits for LogHub response
 * - Fail-fast: If LogHub unavailable, silently continues
 * - Memory efficient: No log buffering or caching
 * 
 * ============================================================================
 * TROUBLESHOOTING
 * ============================================================================
 * 
 * Logs not appearing in LogHub?
 * - Check LogHub app is installed
 * - Verify <queries> tag is in your manifest
 * - Confirm android:name=".Log" is set in <application> tag
 * - Check logcat for any errors
 * - Ensure both apps are on the same device
 * 
 * Crashes not being captured?
 * - Crashes ARE captured, but app still crashes (this is intentional)
 * - Open LogHub app to see crash logs
 * - Look for entries tagged "CRASH"
 * 
 * App name showing as package name?
 * - Set android:label in <application> tag in manifest
 * - Or manually call Log.setAppName("YourAppName")
 * 
 * ============================================================================
 * LICENSE
 * ============================================================================
 * 
 * Free to use, modify, and distribute. No attribution required.
 * Use at your own risk. No warranty provided.
 * 
 * ============================================================================
 * VERSION HISTORY
 * ============================================================================
 * 
 * v1.0 - Initial release
 *        - Basic logging functionality
 *        - Crash capture
 *        - Auto-initialization
 * 
 * ============================================================================
 * AUTHOR NOTES
 * ============================================================================
 * 
 * This class intentionally shadows android.util.Log to provide a drop-in
 * replacement. Make sure to import YOUR Log class (not android.util.Log)
 * in your code files.
 * 
 * The class name "Log" might seem unusual for an Application class, but
 * this design allows it to serve both purposes elegantly while maintaining
 * a familiar API for developers.
 * 
 * ============================================================================
 */
public class Log extends Application {
    
    // ========================================================================
    // CONSTANTS
    // ========================================================================
    
    /**
     * Content URI for the LogHub ContentProvider.
     * This is where all logs are sent for centralized storage.
     */
    private static final Uri LOGHUB_URI = Uri.parse("content://aman.loghub.provider/logs");
    
    // ========================================================================
    // STATIC FIELDS
    // ========================================================================
    
    /**
     * Application context used for ContentResolver operations.
     * Initialized in onCreate() and used for all LogHub communications.
     */
    private static Context appContext;
    
    /**
     * Name of the application sending logs.
     * Auto-detected from manifest or can be set manually via setAppName().
     */
    private static String appName;
    
    // ========================================================================
    // APPLICATION LIFECYCLE
    // ========================================================================
    
    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects have been created.
     * 
     * This method:
     * 1. Stores the application context for later use
     * 2. Auto-detects the app name from the manifest
     * 3. Sets up the global crash handler
     * 
     * This runs before any of your activities, so crash capture is active
     * from the very beginning of your app's lifecycle.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Store context for static methods
        appContext = getApplicationContext();
        
        // Auto-detect app name from manifest's android:label
        try {
            int stringId = getApplicationInfo().labelRes;
            appName = stringId == 0 ? getPackageName() : getString(stringId);
        } catch (Exception e) {
            // Fallback to package name if label is not available
            appName = getPackageName();
        }
        
        // Install crash handler to capture uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this, appName));
    }
    
    // ========================================================================
    // PUBLIC API - CONFIGURATION
    // ========================================================================
    
    /**
     * Sets a custom application name for logging.
     * 
     * By default, the app name is auto-detected from your AndroidManifest.xml
     * android:label attribute. Use this method if you want to override it.
     * 
     * @param name The custom name to display in LogHub for this app's logs
     * 
     * Example:
     *   Log.setAppName("MyApp-Debug");
     *   Log.setAppName("Production Build");
     */
    public static void setAppName(String name) {
        appName = name;
    }
    
    // ========================================================================
    // PUBLIC API - DEBUG LEVEL
    // ========================================================================
    
    /**
     * Send a DEBUG log message.
     * 
     * Debug logs are used for detailed diagnostic information useful during
     * development and debugging. These logs help track application flow and
     * state changes.
     * 
     * @param tag Used to identify the source of a log message. It usually
     *            identifies the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.d("MainActivity", "onCreate called");
     *   Log.d("NetworkManager", "Starting API request to /users");
     */
    public static int d(String tag, String msg) {
        int result = android.util.Log.d(tag, msg);
        sendToLogHub("DEBUG", tag, msg);
        return result;
    }
    
    /**
     * Send a DEBUG log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.d("Parser", "Parsing completed with warnings", exception);
     */
    public static int d(String tag, String msg, Throwable tr) {
        int result = android.util.Log.d(tag, msg, tr);
        sendToLogHub("DEBUG", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - INFO LEVEL
    // ========================================================================
    
    /**
     * Send an INFO log message.
     * 
     * Info logs are used for important runtime events and milestones that
     * are informational but not problematic. These help understand what
     * the application is doing at a high level.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.i("LoginActivity", "User logged in successfully");
     *   Log.i("PaymentService", "Payment processed: $49.99");
     */
    public static int i(String tag, String msg) {
        int result = android.util.Log.i(tag, msg);
        sendToLogHub("INFO", tag, msg);
        return result;
    }
    
    /**
     * Send an INFO log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.i("Sync", "Sync completed with some items skipped", exception);
     */
    public static int i(String tag, String msg, Throwable tr) {
        int result = android.util.Log.i(tag, msg, tr);
        sendToLogHub("INFO", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - WARNING LEVEL
    // ========================================================================
    
    /**
     * Send a WARN log message.
     * 
     * Warning logs are used for potentially harmful situations that don't
     * prevent the application from continuing. These indicate something
     * unexpected happened, but the app can recover.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.w("ImageLoader", "Image quality reduced due to low memory");
     *   Log.w("NetworkManager", "Request took longer than expected: 5000ms");
     */
    public static int w(String tag, String msg) {
        int result = android.util.Log.w(tag, msg);
        sendToLogHub("WARN", tag, msg);
        return result;
    }
    
    /**
     * Send a WARN log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.w("Cache", "Failed to write to cache", exception);
     */
    public static int w(String tag, String msg, Throwable tr) {
        int result = android.util.Log.w(tag, msg, tr);
        sendToLogHub("WARN", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    /**
     * Send a WARN log message and log the exception.
     * Shorthand for when you only want to log the exception without a message.
     * 
     * @param tag Used to identify the source of a log message.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.w("Parser", exception);
     */
    public static int w(String tag, Throwable tr) {
        int result = android.util.Log.w(tag, tr);
        sendToLogHub("WARN", tag, android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - ERROR LEVEL
    // ========================================================================
    
    /**
     * Send an ERROR log message.
     * 
     * Error logs are used for error events that might still allow the
     * application to continue running. These indicate that something went
     * wrong, but the app can continue functioning (perhaps with degraded
     * functionality).
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.e("DatabaseHelper", "Failed to insert row into database");
     *   Log.e("ApiClient", "HTTP 500 error from server");
     */
    public static int e(String tag, String msg) {
        int result = android.util.Log.e(tag, msg);
        sendToLogHub("ERROR", tag, msg);
        return result;
    }
    
    /**
     * Send an ERROR log message and log the exception.
     * 
     * This is the most commonly used error logging method as it includes
     * both a descriptive message and the exception details.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   try {
     *       processPayment();
     *   } catch (Exception e) {
     *       Log.e("PaymentService", "Failed to process payment", e);
     *   }
     */
    public static int e(String tag, String msg, Throwable tr) {
        int result = android.util.Log.e(tag, msg, tr);
        sendToLogHub("ERROR", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - VERBOSE LEVEL
    // ========================================================================
    
    /**
     * Send a VERBOSE log message.
     * 
     * Verbose logs are for extremely detailed diagnostic information. These
     * are typically only used during development for tracing specific issues
     * and are usually filtered out in production builds.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   Log.v("Renderer", "Drawing frame 1234");
     *   Log.v("Animation", "Interpolated value: 0.456");
     */
    public static int v(String tag, String msg) {
        int result = android.util.Log.v(tag, msg);
        sendToLogHub("DEBUG", tag, msg);
        return result;
    }
    
    /**
     * Send a VERBOSE log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     */
    public static int v(String tag, String msg, Throwable tr) {
        int result = android.util.Log.v(tag, msg, tr);
        sendToLogHub("DEBUG", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - WTF LEVEL (What a Terrible Failure)
    // ========================================================================
    
    /**
     * Send a WTF (What a Terrible Failure) log message.
     * 
     * WTF logs are for conditions that should never happen. These represent
     * critical bugs or impossible states that violate fundamental assumptions
     * in your code. On debug builds, these can crash your app immediately.
     * 
     * Use this sparingly for truly impossible conditions, not just errors.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     * 
     * Example:
     *   if (user == null) {
     *       Log.wtf("LoginActivity", "User is null after successful login!");
     *   }
     *   
     *   if (databaseConnection == null) {
     *       Log.wtf("App", "Database connection is null - app cannot function!");
     *   }
     */
    public static int wtf(String tag, String msg) {
        int result = android.util.Log.wtf(tag, msg);
        sendToLogHub("ERROR", tag, "WTF: " + msg);
        return result;
    }
    
    /**
     * Send a WTF log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     */
    public static int wtf(String tag, Throwable tr) {
        int result = android.util.Log.wtf(tag, tr);
        sendToLogHub("ERROR", tag, "WTF: " + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    /**
     * Send a WTF log message and log the exception.
     * 
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log (stack trace will be included)
     * @return The number of bytes written (from android.util.Log)
     */
    public static int wtf(String tag, String msg, Throwable tr) {
        int result = android.util.Log.wtf(tag, msg, tr);
        sendToLogHub("ERROR", tag, "WTF: " + msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }
    
    // ========================================================================
    // PUBLIC API - HELPER METHODS
    // ========================================================================
    
    /**
     * Handy function to get a loggable stack trace from a Throwable.
     * 
     * This is a passthrough to android.util.Log.getStackTraceString() for
     * API compatibility.
     * 
     * @param tr An exception to log
     * @return A string representation of the stack trace
     */
    public static String getStackTraceString(Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }
    
    /**
     * Low-level logging call.
     * 
     * Allows you to specify the priority/level as an integer constant.
     * Most code should use the named methods (d, i, w, e) instead.
     * 
     * @param priority The priority/type of this log message (Log.VERBOSE, etc.)
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @return The number of bytes written (from android.util.Log)
     */
    public static int println(int priority, String tag, String msg) {
        int result = android.util.Log.println(priority, tag, msg);
        String level = priorityToLevel(priority);
        sendToLogHub(level, tag, msg);
        return result;
    }
    
    /**
     * Checks to see whether or not a log for the specified tag is loggable
     * at the specified level.
     * 
     * This is a passthrough to android.util.Log.isLoggable() for API
     * compatibility.
     * 
     * @param tag The tag to check.
     * @param level The level to check.
     * @return Whether or not that this is allowed to be logged.
     */
    public static boolean isLoggable(String tag, int level) {
        return android.util.Log.isLoggable(tag, level);
    }
    
    // ========================================================================
    // PRIVATE IMPLEMENTATION
    // ========================================================================
    
    /**
     * Sends a log message to LogHub in the background.
     * 
     * This method:
     * 1. Checks if the system is initialized
     * 2. Creates a background thread to avoid blocking the caller
     * 3. Packages the log data into ContentValues
     * 4. Sends it to LogHub via ContentProvider
     * 5. Silently fails if LogHub is unavailable (graceful degradation)
     * 
     * @param level The log level (DEBUG, INFO, WARN, ERROR)
     * @param tag The tag identifying the source
     * @param msg The log message (may include stack traces)
     */
    private static void sendToLogHub(String level, String tag, String msg) {
        // Don't try to log if we're not initialized
        if (appContext == null) return;
        
        // Send to LogHub in background thread to avoid blocking
        new Thread(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put("app_name", appName != null ? appName : "Unknown");
                v.put("tag", tag);
                v.put("message", msg);
                v.put("level", level);
                v.put("timestamp", System.currentTimeMillis());
                appContext.getContentResolver().insert(LOGHUB_URI, v);
            } catch (Exception e) {
                // Silently fail - we don't want logging to crash the app
                // The log still went to logcat, just not to LogHub
            }
        }).start();
    }
    
    /**
     * Converts android.util.Log priority constants to LogHub level strings.
     * 
     * @param priority The priority constant from android.util.Log
     * @return The corresponding level string for LogHub
     */
    private static String priorityToLevel(int priority) {
        switch (priority) {
            case android.util.Log.VERBOSE:
            case android.util.Log.DEBUG:
                return "DEBUG";
            case android.util.Log.INFO:
                return "INFO";
            case android.util.Log.WARN:
                return "WARN";
            case android.util.Log.ERROR:
            case android.util.Log.ASSERT:
                return "ERROR";
            default:
                return "DEBUG";
        }
    }
    
    // ========================================================================
    // CRASH HANDLER
    // ========================================================================
    
    /**
     * Custom uncaught exception handler that logs crashes to LogHub.
     * 
     * This handler:
     * 1. Intercepts uncaught exceptions before the app crashes
     * 2. Formats crash information (exception type, message, stack trace)
     * 3. Synchronously writes to LogHub (since app is crashing anyway)
     * 4. Calls the original handler to let the app crash normally
     * 
     * The app still crashes (this doesn't prevent crashes), but now you
     * have a record of the crash in LogHub for debugging.
     */
    private static class CrashHandler implements Thread.UncaughtExceptionHandler {
        
        private Context context;
        private Thread.UncaughtExceptionHandler defaultHandler;
        private String appName;
        
        /**
         * Creates a new crash handler.
         * 
         * @param context Application context for ContentResolver access
         * @param appName Name of the app (for crash log identification)
         */
        public CrashHandler(Context context, String appName) {
            this.context = context.getApplicationContext();
            this.appName = appName;
            // Save the original handler so we can call it after logging
            this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        }
        
        /**
         * Called when an uncaught exception occurs.
         * 
         * @param thread The thread where the exception occurred
         * @param throwable The uncaught exception
         */
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            try {
                // Format crash information
                String crashLog = getCrashLog(thread, throwable);
                
                // Save crash to LogHub synchronously
                // We don't use a background thread here because:
                // 1. The app is crashing anyway, so blocking is acceptable
                // 2. We need to ensure the log is written before the process dies
                ContentValues v = new ContentValues();
                v.put("app_name", appName);
                v.put("tag", "CRASH");
                v.put("message", crashLog);
                v.put("level", "ERROR");
                v.put("timestamp", System.currentTimeMillis());
                context.getContentResolver().insert(LOGHUB_URI, v);
                
                // Small delay to ensure the write completes
                // This is a best-effort attempt; the system may kill us anyway
                Thread.sleep(100);
                
            } catch (Exception e) {
                // If crash logging fails, at least try to log to logcat
                android.util.Log.e("LogHub", "Failed to log crash", e);
            } finally {
                // Always call the default handler to let the app crash normally
                // This ensures the system handles the crash properly (showing
                // the crash dialog, etc.)
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        }
        
        /**
         * Formats crash information into a readable string.
         * 
         * @param thread The thread where the crash occurred
         * @param throwable The exception that caused the crash
         * @return Formatted crash information
         */
        private String getCrashLog(Thread thread, Throwable throwable) {
            StringBuilder sb = new StringBuilder();
            sb.append("CRASH: ").append(throwable.getClass().getName()).append("\n");
            sb.append("Message: ").append(throwable.getMessage()).append("\n");
            sb.append("Thread: ").append(thread.getName()).append("\n\n");
            sb.append("Stack Trace:\n");
            sb.append(android.util.Log.getStackTraceString(throwable));
            return sb.toString();
        }
    }
}
