package aman.lyricify;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Handles all permission-related operations
 */
public class PermissionManager {
    
    public static final int STORAGE_PERMISSION_CODE = 100;
    
    private final Activity activity;
    private PermissionCallback callback;
    
    public interface PermissionCallback {
        void onStoragePermissionGranted();
        void onStoragePermissionDenied();
    }
    
    public PermissionManager(Activity activity) {
        this.activity = activity;
    }
    
    public void setCallback(PermissionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Check if storage permission is granted
     */
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Request storage permission
     */
    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                STORAGE_PERMISSION_CODE
            );
        } else {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE
            );
        }
    }
    
    /**
     * Check and request permission if needed
     */
    public void checkAndRequestStoragePermission() {
        if (!hasStoragePermission()) {
            requestStoragePermission();
        }
    }
    
    /**
     * Handle permission result
     */
    public void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Storage permission granted", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onStoragePermissionGranted();
                }
            } else {
                Toast.makeText(activity, "Storage permission denied - can't find local files", Toast.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onStoragePermissionDenied();
                }
            }
        }
    }
}