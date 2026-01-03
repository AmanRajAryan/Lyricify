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
 * Handles saving lyrics as .lrc or .ttml files - OPTIMIZED VERSION
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
     * Save lyrics as .lrc file in the same folder as the audio file (backward compatibility)
     */
    public void saveLrcFile(String audioFilePath, String lyrics) {
        saveLyricsFile(audioFilePath, lyrics, ".lrc");
    }
    
    /**
     * Save lyrics with specified extension in the same folder as the audio file
     * @param audioFilePath Path to the audio file
     * @param lyrics Lyrics content to save
     * @param extension File extension (.lrc or .ttml)
     */
    public void saveLyricsFile(String audioFilePath, String lyrics, String extension) {
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
                
                // Generate filename with provided extension
                String outputFileName = generateLyricsFileName(audioFile.getName(), extension);
                String outputFilePath = audioFile.getParent() + "/" + outputFileName;
                
                notifyProgress("Creating " + extension + " file...");
                
                // Try to save using SAF
                Uri outputUri = getOrCreateLyricsFileUri(outputFilePath, extension);
                
                if (outputUri == null) {
                    // Need permission
                    String folderPath = audioFile.getParent();
                    notifyNeedPermission(folderPath);
                    return;
                }
                
                // Write lyrics to file
                notifyProgress("Writing lyrics...");
                try (OutputStream os = context.getContentResolver().openOutputStream(outputUri, "wt")) {
                    if (os != null) {
                        os.write(lyrics.getBytes("UTF-8"));
                        os.flush();
                        notifySuccess(outputFilePath);
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
     * Generate lyrics filename from audio filename with specified extension
     * Example: song.mp3 + ".lrc" -> song.lrc
     * Example: song.mp3 + ".ttml" -> song.ttml
     */
    private String generateLyricsFileName(String audioFileName, String extension) {
        int lastDot = audioFileName.lastIndexOf('.');
        if (lastDot > 0) {
            return audioFileName.substring(0, lastDot) + extension;
        }
        return audioFileName + extension;
    }
    
    /**
     * Get or create lyrics file URI using SAF - OPTIMIZED VERSION
     * Tries direct URI construction first (fast), falls back to DocumentFile only if needed
     */
    private Uri getOrCreateLyricsFileUri(String lyricsFilePath, String extension) {
        Context context = contextRef.get();
        if (context == null) return null;
        
        try {
            File lyricsFile = new File(lyricsFilePath);
            String fileName = lyricsFile.getName();
            String folderPath = lyricsFile.getParent();
            
            if (folderPath == null) {
                Log.e(TAG, "No parent folder for: " + lyricsFilePath);
                return null;
            }
            
            Log.d(TAG, "========================================");
            Log.d(TAG, "Creating lyrics file: " + lyricsFilePath);
            
            // Determine storage ID
            String storageId = extractStorageId(lyricsFilePath);
            if (storageId == null) {
                Log.e(TAG, "Cannot determine storage ID for: " + lyricsFilePath);
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
            
            // Determine mime type based on extension
            String mimeType;
            if (extension.equals(".ttml")) {
                mimeType = "application/ttml+xml";
            } else {
                mimeType = "text/lrc";
            }
            
            // Find matching permission
            for (var permission : permissions) {
                if (!permission.isWritePermission()) {
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
                
                if (!treeInfo.storageId.equals(storageId)) {
                    Log.d(TAG, "Wrong storage, skipping");
                    continue;
                }
                
                if (!isPathUnderTree(folderPath, treeInfo, storageId)) {
                    Log.d(TAG, "Path not under tree, skipping");
                    continue;
                }
                
                Log.d(TAG, "✓ Permission matches!");
                
                // STEP 1: Try direct URI construction (works if file exists)
                Log.d(TAG, "Attempting direct URI construction...");
                Uri directUri = buildDocumentUri(treeUri, lyricsFilePath, treeInfo, storageId);
                if (directUri != null) {
                    Log.d(TAG, "Built direct URI: " + directUri);
                    try {
                        OutputStream test = context.getContentResolver().openOutputStream(directUri, "w");
                        if (test != null) {
                            test.close();
                            Log.d(TAG, "✓✓✓ Direct URI works! Returning it");
                            Log.d(TAG, "========================================");
                            return directUri;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Direct URI failed (file doesn't exist): " + e.getMessage());
                    }
                }
                
                // STEP 2: File doesn't exist, need to create it
                // Use createDocument API directly (faster than DocumentFile)
                Log.d(TAG, "Creating new file using createDocument...");
                Uri createdUri = createDocumentDirect(context, treeUri, folderPath, fileName, mimeType, treeInfo, storageId);
                if (createdUri != null) {
                    Log.d(TAG, "✓✓✓ File created successfully!");
                    Log.d(TAG, "========================================");
                    return createdUri;
                }
                
                Log.d(TAG, "createDocument failed, falling back to DocumentFile...");
                
                // STEP 3: Last resort - use DocumentFile (slow but reliable)
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
                
                // Check if file exists
                DocumentFile existingFile = folder.findFile(fileName);
                if (existingFile != null) {
                    Log.d(TAG, "✓✓✓ File found via DocumentFile");
                    Log.d(TAG, "========================================");
                    return existingFile.getUri();
                }
                
                // Create new file
                DocumentFile newFile = folder.createFile(mimeType, fileName);
                if (newFile != null) {
                    Log.d(TAG, "✓✓✓ File created via DocumentFile");
                    Log.d(TAG, "========================================");
                    return newFile.getUri();
                }
            }
            
            Log.d(TAG, "No matching permission found");
            Log.d(TAG, "========================================");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in getOrCreateLyricsFileUri", e);
        }
        return null;
    }
    
    /**
     * Create document directly using DocumentsContract (faster than DocumentFile)
     */
    private Uri createDocumentDirect(Context context, Uri treeUri, String folderPath, 
                                     String fileName, String mimeType, TreeInfo treeInfo, String storageId) {
        try {
            // Build parent folder document ID
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            String storagePath = storageId.equals("primary") ? 
                    "/storage/emulated/0/" : 
                    "/storage/" + storageId + "/";
            
            String treeFolderAbsolute;
            if (treeInfo.folder.isEmpty()) {
                treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1);
            } else {
                treeFolderAbsolute = storagePath + treeInfo.folder;
            }
            
            String parentDocumentId;
            if (folderPath.equals(treeFolderAbsolute)) {
                parentDocumentId = treeDocId;
            } else if (folderPath.startsWith(treeFolderAbsolute + "/")) {
                String relativePath = folderPath.substring((treeFolderAbsolute + "/").length());
                if (treeDocId.endsWith(":")) {
                    parentDocumentId = treeDocId + relativePath;
                } else {
                    parentDocumentId = treeDocId + "/" + relativePath;
                }
            } else {
                return null;
            }
            
            Log.d(TAG, "Parent document ID: " + parentDocumentId);
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId);
            
            // Try to create document directly
            Uri createdUri = DocumentsContract.createDocument(
                context.getContentResolver(),
                parentUri,
                mimeType,
                fileName
            );
            
            return createdUri;
            
        } catch (Exception e) {
            Log.d(TAG, "createDocument exception: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Build document URI directly (for existing files)
     */
    private Uri buildDocumentUri(Uri treeUri, String filePath, TreeInfo treeInfo, String storageId) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            String fileParentPath = file.getParent();
            
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            
            String storagePath = storageId.equals("primary") ? 
                    "/storage/emulated/0/" : 
                    "/storage/" + storageId + "/";
            
            String treeFolderAbsolute;
            if (treeInfo.folder.isEmpty()) {
                treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1);
            } else {
                treeFolderAbsolute = storagePath + treeInfo.folder;
            }
            
            String relativePath = "";
            
            if (fileParentPath.equals(treeFolderAbsolute)) {
                relativePath = fileName;
            } else if (fileParentPath.startsWith(treeFolderAbsolute + "/")) {
                String subPath = fileParentPath.substring((treeFolderAbsolute + "/").length());
                relativePath = subPath + "/" + fileName;
            } else {
                return null;
            }
            
            String documentId;
            if (treeDocId.endsWith(":") && !relativePath.isEmpty()) {
                documentId = treeDocId + relativePath;
            } else if (relativePath.isEmpty()) {
                documentId = treeDocId;
            } else {
                documentId = treeDocId + "/" + relativePath;
            }
            
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error building document URI", e);
            return null;
        }
    }
    
    /**
     * Navigate to folder within tree (only used as last resort)
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
        
        if (targetPath.equals(treeFolderAbsolute)) {
            return tree;
        }
        
        if (targetPath.startsWith(treeFolderAbsolute + "/")) {
            String relativePath = targetPath.substring((treeFolderAbsolute + "/").length());
            String[] parts = relativePath.split("/");
            
            DocumentFile current = tree;
            for (String part : parts) {
                DocumentFile next = current.findFile(part);
                if (next == null || !next.isDirectory()) {
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