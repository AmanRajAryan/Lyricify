package aman.lyricify;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/**
 * Handles saving lyrics as .lrc files - OPTIMIZED VERSION
 */
public class LrcSaver {
    
    private static final String TAG = "LrcSaver";
    private final WeakReference<Context> contextRef;
    private SaveCallback callback;
    
    public interface SaveCallback {
        void onProgress(String message);
        void onSuccess(String filePath);
        void onError(String errorMessage);
        void onNeedPermission(String folderPath);
    }
    
    public LrcSaver(Context context) {
        this.contextRef = new WeakReference<>(context);
    }
    
    public void setCallback(SaveCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Save lyrics as .lrc file in the same folder as the audio file
     */
    public void saveLrcFile(String audioFilePath, String lyrics) {
        Context context = contextRef.get();
        if (context == null) return;
        
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            notifyError("No audio file path available");
            return;
        }
        
        if (lyrics == null || lyrics.isEmpty()) {
            notifyError("No lyrics to save");
            return;
        }
        
        new Thread(() -> {
            try {
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) {
                    notifyError("Audio file doesn't exist");
                    return;
                }
                
                // Generate .lrc filename
                String lrcFileName = generateLrcFileName(audioFile.getName());
                String lrcFilePath = audioFile.getParent() + "/" + lrcFileName;
                
                notifyProgress("Creating .lrc file...");
                
                // Try to save using SAF
                Uri lrcUri = getOrCreateLrcFileUri(lrcFilePath);
                
                if (lrcUri == null) {
                    // Need permission
                    String folderPath = audioFile.getParent();
                    notifyNeedPermission(folderPath);
                    return;
                }
                
                // Write lyrics to file
                notifyProgress("Writing lyrics...");
                try (OutputStream os = context.getContentResolver().openOutputStream(lrcUri, "wt")) {
                    if (os != null) {
                        os.write(lyrics.getBytes("UTF-8"));
                        os.flush();
                        notifySuccess(lrcFilePath);
                    } else {
                        notifyError("Cannot open file for writing");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Write failed", e);
                    notifyError("Write failed: " + e.getMessage());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                notifyError("Error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Generate .lrc filename from audio filename
     * Example: song.mp3 -> song.lrc
     */
    private String generateLrcFileName(String audioFileName) {
        int lastDot = audioFileName.lastIndexOf('.');
        if (lastDot > 0) {
            return audioFileName.substring(0, lastDot) + ".lrc";
        }
        return audioFileName + ".lrc";
    }
    
    /**
     * Get or create .lrc file URI using SAF - OPTIMIZED VERSION
     */
    private Uri getOrCreateLrcFileUri(String lrcFilePath) {
        Context context = contextRef.get();
        if (context == null) return null;
        
        try {
            File lrcFile = new File(lrcFilePath);
            String fileName = lrcFile.getName();
            String folderPath = lrcFile.getParent();
            
            if (folderPath == null) {
                Log.e(TAG, "No parent folder for: " + lrcFilePath);
                return null;
            }
            
            Log.d(TAG, "========================================");
            Log.d(TAG, "Creating LRC file: " + lrcFilePath);
            
            // Determine storage ID
            String storageId = extractStorageId(lrcFilePath);
            if (storageId == null) {
                Log.e(TAG, "Cannot determine storage ID for: " + lrcFilePath);
                return null;
            }
            Log.d(TAG, "Storage ID: " + storageId);
            
            // Get persisted permissions
            var permissions = context.getContentResolver().getPersistedUriPermissions();
            if (permissions.isEmpty()) {
                Log.d(TAG, "No persisted permissions");
                return null;
            }
            
            Log.d(TAG, "Found " + permissions.size() + " persisted permissions");
            
            // Find matching permission
            for (var permission : permissions) {
                if (!permission.isWritePermission()) {
                    Log.d(TAG, "Skipping read-only permission");
                    continue;
                }
                
                Uri treeUri = permission.getUri();
                Log.d(TAG, "----------------------------------------");
                Log.d(TAG, "Checking permission: " + treeUri);
                
                TreeInfo treeInfo = extractTreeInfo(treeUri);
                
                if (treeInfo == null) {
                    Log.d(TAG, "Failed to extract tree info");
                    continue;
                }
                
                Log.d(TAG, "Tree storage ID: " + treeInfo.storageId);
                Log.d(TAG, "Tree folder: " + treeInfo.folder);
                
                if (!treeInfo.storageId.equals(storageId)) {
                    Log.d(TAG, "Wrong storage, skipping");
                    continue;
                }
                
                if (!isPathUnderTree(folderPath, treeInfo, storageId)) {
                    Log.d(TAG, "Path not under tree, skipping");
                    continue;
                }
                
                Log.d(TAG, "✓ Permission matches!");
                
                // OPTIMIZATION 1: Try direct URI construction first (works if file exists)
                Log.d(TAG, "Attempting direct URI construction...");
                Uri documentUri = buildDocumentUri(treeUri, lrcFilePath, treeInfo, storageId);
                if (documentUri != null) {
                    Log.d(TAG, "Built document URI: " + documentUri);
                    try {
                        // Try to open for writing - if it works, file exists
                        OutputStream test = context.getContentResolver().openOutputStream(documentUri);
                        if (test != null) {
                            test.close();
                            Log.d(TAG, "✓✓✓ Direct URI works! File exists, returning URI");
                            Log.d(TAG, "========================================");
                            return documentUri;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "File doesn't exist yet: " + e.getMessage());
                    }
                }
                
                // OPTIMIZATION 2: Use DocumentFile navigation only for creating new files
                Log.d(TAG, "Using DocumentFile to create new file...");
                DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
                if (tree == null) {
                    Log.d(TAG, "Failed to get tree DocumentFile");
                    continue;
                }
                
                DocumentFile folder = navigateToFolder(tree, folderPath, treeInfo, storageId);
                if (folder == null) {
                    Log.d(TAG, "Failed to navigate to folder");
                    continue;
                }
                
                Log.d(TAG, "Navigated to folder successfully");
                
                // Check if file already exists (one findFile call)
                DocumentFile existingLrc = folder.findFile(fileName);
                if (existingLrc != null) {
                    Log.d(TAG, "✓✓✓ File already exists via findFile");
                    Log.d(TAG, "========================================");
                    return existingLrc.getUri();
                }
                
                // Create new file
                Log.d(TAG, "Creating new .lrc file...");
                DocumentFile newLrc = folder.createFile("text/lrc", fileName);
                if (newLrc != null) {
                    Log.d(TAG, "✓✓✓ Successfully created new .lrc file!");
                    Log.d(TAG, "========================================");
                    return newLrc.getUri();
                } else {
                    Log.d(TAG, "Failed to create file");
                }
            }
            
            Log.d(TAG, "No matching permission found");
            Log.d(TAG, "========================================");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in getOrCreateLrcFileUri", e);
        }
        return null;
    }
    
    /**
     * Build document URI directly (fast path for existing files)
     */
    private Uri buildDocumentUri(Uri treeUri, String filePath, TreeInfo treeInfo, String storageId) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            String fileParentPath = file.getParent();
            
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Log.d(TAG, "Tree document ID: " + treeDocId);
            
            String storagePath = storageId.equals("primary") ? 
                    "/storage/emulated/0/" : 
                    "/storage/" + storageId + "/";
            
            String treeFolderAbsolute;
            if (treeInfo.folder.isEmpty()) {
                treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1);
            } else {
                treeFolderAbsolute = storagePath + treeInfo.folder;
            }
            
            Log.d(TAG, "Tree folder absolute: " + treeFolderAbsolute);
            Log.d(TAG, "File parent path: " + fileParentPath);
            
            String relativePath = "";
            
            if (fileParentPath.equals(treeFolderAbsolute)) {
                relativePath = fileName;
                Log.d(TAG, "File is in granted folder directly");
            } else if (fileParentPath.startsWith(treeFolderAbsolute + "/")) {
                String subPath = fileParentPath.substring((treeFolderAbsolute + "/").length());
                relativePath = subPath + "/" + fileName;
                Log.d(TAG, "File is in subfolder: " + subPath);
            } else {
                Log.d(TAG, "File path doesn't match tree structure");
                return null;
            }
            
            Log.d(TAG, "Relative path: " + relativePath);
            
            String documentId;
            if (treeDocId.endsWith(":") && !relativePath.isEmpty()) {
                documentId = treeDocId + relativePath;
            } else if (relativePath.isEmpty()) {
                documentId = treeDocId;
            } else {
                documentId = treeDocId + "/" + relativePath;
            }
            
            Log.d(TAG, "Final document ID: " + documentId);
            
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error building document URI", e);
            return null;
        }
    }
    
    /**
     * Navigate to folder within tree (folder must already exist)
     */
    private DocumentFile navigateToFolder(DocumentFile tree, String targetPath, 
                                         TreeInfo treeInfo, String storageId) {
        String storagePath = storageId.equals("primary") ? 
                "/storage/emulated/0/" : "/storage/" + storageId + "/";
        
        String treeFolderAbsolute;
        if (treeInfo.folder.isEmpty()) {
            treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1);
        } else {
            treeFolderAbsolute = storagePath + treeInfo.folder;
        }
        
        // If target is the tree folder, return tree
        if (targetPath.equals(treeFolderAbsolute)) {
            return tree;
        }
        
        // Navigate to subfolder (must already exist since song file is there)
        if (targetPath.startsWith(treeFolderAbsolute + "/")) {
            String relativePath = targetPath.substring((treeFolderAbsolute + "/").length());
            String[] parts = relativePath.split("/");
            
            DocumentFile current = tree;
            for (String part : parts) {
                DocumentFile next = current.findFile(part);
                if (next == null || !next.isDirectory()) {
                    // Folder doesn't exist - this shouldn't happen since song file is there
                    Log.e(TAG, "Folder doesn't exist: " + part);
                    return null;
                }
                current = next;
            }
            return current;
        }
        
        return null;
    }
    
    // Helper classes and methods
    private static class TreeInfo {
        String storageId;
        String folder;
        
        TreeInfo(String storageId, String folder) {
            this.storageId = storageId;
            this.folder = folder;
        }
    }
    
    private TreeInfo extractTreeInfo(Uri treeUri) {
        try {
            String treeUriString = treeUri.toString();
            if (!treeUriString.contains("tree/")) return null;
            
            String[] parts = treeUriString.split("tree/");
            if (parts.length <= 1) return null;
            
            String decoded = Uri.decode(parts[1]);
            if (!decoded.contains(":")) return null;
            
            String[] treeParts = decoded.split(":", 2);
            String storageId = treeParts[0];
            String folder = treeParts.length > 1 ? treeParts[1] : "";
            
            return new TreeInfo(storageId, folder);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting tree info", e);
            return null;
        }
    }
    
    private boolean isPathUnderTree(String folderPath, TreeInfo treeInfo, String storageId) {
        String storagePath = storageId.equals("primary") ? 
                "/storage/emulated/0/" : "/storage/" + storageId + "/";
        
        String treeFolderAbsolute;
        if (treeInfo.folder.isEmpty()) {
            treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1);
        } else {
            treeFolderAbsolute = storagePath + treeInfo.folder;
        }
        
        return folderPath.equals(treeFolderAbsolute) || 
               folderPath.startsWith(treeFolderAbsolute + "/");
    }
    
    private String extractStorageId(String filePath) {
        if (filePath.startsWith("/storage/emulated/0/")) {
            return "primary";
        }
        if (filePath.startsWith("/storage/")) {
            String[] parts = filePath.split("/");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return null;
    }
    
    // Notification methods
    private void notifyProgress(String message) {
        if (callback != null) {
            callback.onProgress(message);
        }
    }
    
    private void notifySuccess(String filePath) {
        if (callback != null) {
            callback.onSuccess(filePath);
        }
    }
    
    private void notifyError(String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }
    
    private void notifyNeedPermission(String folderPath) {
        if (callback != null) {
            callback.onNeedPermission(folderPath);
        }
    }
}