package aman.lyricify;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import aman.taglib.TagLib;

/**
 * Manages embedding lyrics and artwork into audio files
 */
public class EmbeddingManager {
    
    private final WeakReference<Context> contextRef;
    private EmbeddingCallback callback;
    
    public interface EmbeddingCallback {
        void onProgressUpdate(String message);
        void onEmbedSuccess(String successMessage);
        void onEmbedError(String errorMessage);
        void onNeedPermission(String folderPath);
        void onShowMetadata(String filePath);
    }
    
    private LrcSaver lrcSaver;
    
    public EmbeddingManager(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.lrcSaver = new LrcSaver(context);
    }
    
    public void setCallback(EmbeddingCallback callback) {
        this.callback = callback;
        
        // Setup LRC saver callback
        lrcSaver.setCallback(new LrcSaver.SaveCallback() {
            @Override
            public void onProgress(String message) {
                notifyProgress(message);
            }
            
            @Override
            public void onSuccess(String filePath) {
                notifySuccess("LRC file saved: " + extractFileName(filePath));
            }
            
            @Override
            public void onError(String errorMessage) {
                notifyError(errorMessage);
            }
            
            @Override
            public void onNeedPermission(String folderPath) {
                if (EmbeddingManager.this.callback != null) {
                    EmbeddingManager.this.callback.onNeedPermission(folderPath);
                }
            }
        });
    }
    
    /**
     * Save lyrics as .lrc file
     */
    public void saveLrcFile(String audioFilePath, String lyrics) {
        Context context = contextRef.get();
        if (context == null) return;
        
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            Toast.makeText(context, "No audio file path available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (lyrics == null || lyrics.isEmpty()) {
            Toast.makeText(context, "No lyrics to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        lrcSaver.saveLrcFile(audioFilePath, lyrics);
    }
    
    /**
     * Check if embedding is possible
     */
    public boolean canEmbed(String filePath) {
        Context context = contextRef.get();
        if (context == null) return false;
        
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(context, "No file available", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    
    /**
     * Check if file is locked
     */
    public boolean isFileLocked(String filePath) {
        try {
            FileInputStream fis = new FileInputStream(new File(filePath));
            fis.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }
    
    /**
     * Show file locked warning
     */
    public void showFileLockedWarning(Context context, String filePath, Runnable onContinue) {
        new AlertDialog.Builder(context)
                .setTitle("⚠ File In Use")
                .setMessage("File may be locked. Stop playback first.")
                .setPositiveButton("Try Anyway", (d, w) -> onContinue.run())
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Embed lyrics into audio file
     */
    public void embedLyrics(String filePath, String lyrics) {
        Context context = contextRef.get();
        if (context == null) return;
        
        if (!canEmbed(filePath)) return;
        
        new Thread(() -> {
            File tempFile = null;
            try {
                File originalFile = new File(filePath);
                if (!originalFile.exists()) {
                    notifyError("File doesn't exist");
                    return;
                }
                
                notifyProgress("Copying to cache...");
                tempFile = new File(
                    context.getCacheDir(),
                    "temp_" + System.currentTimeMillis() + "_" + originalFile.getName()
                );
                copyFile(originalFile, tempFile);
                
                notifyProgress("Embedding lyrics...");
                TagLib tagLib = new TagLib();
                HashMap<String, String> metadataMap = new HashMap<>();
                metadataMap.put("LYRICS", lyrics);
                metadataMap.put("UNSYNCEDLYRICS", lyrics);
                boolean success = tagLib.setMetadata(tempFile.getAbsolutePath(), metadataMap);
                
                if (!success) {
                    if (tempFile.exists()) tempFile.delete();
                    notifyError("Failed to embed lyrics");
                    return;
                }
                
                // Save file
                final File finalTempFile = tempFile;
                saveFile(filePath, finalTempFile, "Lyrics embedded successfully!");
                
            } catch (Exception e) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                notifyError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Embed artwork into audio file
     */
    public void embedArtwork(String filePath, Bitmap artwork) {
        Context context = contextRef.get();
        if (context == null) return;
        
        if (!canEmbed(filePath)) return;
        
        if (artwork == null) {
            Toast.makeText(context, "No artwork available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new Thread(() -> {
            File tempFile = null;
            try {
                File originalFile = new File(filePath);
                if (!originalFile.exists()) {
                    notifyError("File doesn't exist");
                    return;
                }
                
                notifyProgress("Converting image...");
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                artwork.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                byte[] imageData = stream.toByteArray();
                
                notifyProgress("Copying to cache...");
                tempFile = new File(
                    context.getCacheDir(),
                    "temp_" + System.currentTimeMillis() + "_" + originalFile.getName()
                );
                copyFile(originalFile, tempFile);
                
                notifyProgress("Embedding artwork...");
                TagLib tagLib = new TagLib();
                boolean success = tagLib.setArtwork(
                    tempFile.getAbsolutePath(),
                    imageData,
                    "image/jpeg",
                    "Cover (front)"
                );
                
                if (!success) {
                    if (tempFile.exists()) tempFile.delete();
                    notifyError("Failed to embed artwork");
                    return;
                }
                
                // Save file
                final File finalTempFile = tempFile;
                saveFile(filePath, finalTempFile, "Artwork embedded successfully!");
                
            } catch (Exception e) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                notifyError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Save modified file back to original location
     */
    private void saveFile(String originalPath, File tempFile, String successMessage) {
        Context context = contextRef.get();
        if (context == null) return;
        
        FileSaver fileSaver = new FileSaver(context);
        fileSaver.saveFile(originalPath, tempFile, new FileSaver.SaveCallback() {
            @Override
            public void onProgress(String message) {
                notifyProgress(message);
            }
            
            @Override
            public void onSuccess() {
                notifySuccess(successMessage);
            }
            
            @Override
            public void onError(String errorMessage) {
                notifyError(errorMessage);
            }
            
            @Override
            public void onNeedPermission(String folderPath) {
                if (callback != null) {
                    callback.onNeedPermission(folderPath);
                }
            }
        });
    }
    
    /**
     * Copy file from source to destination
     */
    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
        }
    }
    
    /**
     * Extract filename from path
     */
    public String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = filePath.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash < filePath.length() - 1)
                ? filePath.substring(lastSlash + 1)
                : filePath;
    }
    
    // Notification methods
    private void notifyProgress(String message) {
        if (callback != null) {
            callback.onProgressUpdate(message);
        }
    }
    
    private void notifySuccess(String message) {
        if (callback != null) {
            callback.onEmbedSuccess(message);
        }
    }
    
    private void notifyError(String message) {
        if (callback != null) {
            callback.onEmbedError(message);
        }
    }
}
