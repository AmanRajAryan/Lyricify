package aman.lyricify;


import android.app.Application;
import android.content.Intent;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {
                    handleUncaughtException(throwable);
                });
    }

    private void handleUncaughtException(Throwable e) {
        String stackTrace = getStackTrace(e);
        Intent intent = new Intent(this, CrashActivity.class);
        intent.putExtra("stack_trace", stackTrace);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    private String getStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true); // Set autoFlush to true
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        pw.close();
        return stackTrace;
    }
}
