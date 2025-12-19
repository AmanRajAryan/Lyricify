package aman.lyricify;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class FileSaver {
    
    private static final String TAG = "FileSaver";
    
    public interface SaveCallback {
        void onProgress(String message);
        void onSuccess();
        void onError(String errorMessage);
        void onNeedPermission(String folderPath);
    }

    private final Context context;

    public FileSaver(Context context) {
        this.context = context;
    }

    public void saveFile(String originalFilePath, File tempFile, SaveCallback callback) {
        new Thread(() -> {
            File finalTempFile = tempFile;
            
            try {
                File originalFile = new File(originalFilePath);
                
                if (!originalFile.exists()) {
                    callback.onError("Original file doesn't exist");
                    return;
                }

                if (!finalTempFile.exists()) {
                    callback.onError("Temp file doesn't exist");
                    return;
                }

                callback.onProgress("Finding file via SAF...");
                
                // Get SAF Uri for the file
                Uri fileUri = getUriViaSAF(originalFilePath);
                
                if (fileUri == null) {
                    // Need permission - figure out which folder to request
                    String folderPath = originalFile.getParent();
                    Log.e(TAG, "No permission found for: " + folderPath);
                    callback.onNeedPermission(folderPath);
                    return;
                }

                // Write using SAF
                callback.onProgress("Writing file via SAF...");
                try {
                    OutputStream os = context.getContentResolver().openOutputStream(fileUri, "wt");
                    if (os != null) {
                        FileInputStream fis = new FileInputStream(finalTempFile);
                        byte[] buffer = new byte[16384];
                        int bytesRead;
                        long totalWritten = 0;
                        
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                            totalWritten += bytesRead;
                        }
                        
                        fis.close();
                        os.flush();
                        os.close();
                        
                        // Verify write
                        if (totalWritten > 0 && totalWritten == finalTempFile.length()) {
                            Log.d(TAG, "Write successful: " + totalWritten + " bytes");
                            if (finalTempFile.exists()) {
                                finalTempFile.delete();
                            }
                            callback.onSuccess();
                        } else {
                            callback.onError("Write incomplete: " + totalWritten + " of " + finalTempFile.length() + " bytes");
                        }
                    } else {
                        callback.onError("Cannot open file for writing");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Write failed", e);
                    callback.onError("Write failed: " + e.getMessage());
                }

            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                callback.onError("Unexpected error: " + e.getMessage());
            } finally {
                // Always clean up temp file
                if (finalTempFile != null && finalTempFile.exists()) {
                    finalTempFile.delete();
                }
            }
        }).start();
    }

    private Uri getUriViaSAF(String filePath) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            String folderPath = file.getParent();
            
            if (folderPath == null) {
                Log.e(TAG, "No parent folder for: " + filePath);
                return null;
            }
            
            Log.d(TAG, "========================================");
            Log.d(TAG, "Looking for file: " + filePath);
            Log.d(TAG, "File name: " + fileName);
            Log.d(TAG, "Folder path: " + folderPath);
            
            // Determine which storage this file is on
            String storageId = extractStorageId(filePath);
            if (storageId == null) {
                Log.e(TAG, "Cannot determine storage ID for: " + filePath);
                return null;
            }
            Log.d(TAG, "Storage ID: " + storageId);
            
            // Get all persisted permissions
            var permissions = context.getContentResolver().getPersistedUriPermissions();
            if (permissions.isEmpty()) {
                Log.d(TAG, "No persisted permissions");
                return null;
            }
            
            Log.d(TAG, "Found " + permissions.size() + " persisted permissions");

            // Try to find permission that covers this file's folder
            for (var permission : permissions) {
                if (!permission.isWritePermission()) {
                    Log.d(TAG, "Skipping read-only permission");
                    continue;
                }
                
                Uri treeUri = permission.getUri();
                Log.d(TAG, "----------------------------------------");
                Log.d(TAG, "Checking permission: " + treeUri);
                
                // Extract tree info with proper decoding
                TreeInfo treeInfo = extractTreeInfo(treeUri);
                if (treeInfo == null) {
                    Log.d(TAG, "Failed to extract tree info");
                    continue;
                }
                
                Log.d(TAG, "Tree storage ID: " + treeInfo.storageId);
                Log.d(TAG, "Tree folder: " + treeInfo.folder);
                
                // Check if this permission is for the correct storage
                if (!treeInfo.storageId.equals(storageId)) {
                    Log.d(TAG, "Wrong storage, skipping");
                    continue;
                }
                
                // Check if this permission covers the file's folder
                if (!isPathUnderTree(folderPath, treeInfo, storageId)) {
                    Log.d(TAG, "Path not under tree, skipping");
                    continue;
                }
                
                Log.d(TAG, "✓ Permission matches! Attempting to build URI...");

                // Build the document URI
                Uri documentUri = buildDocumentUri(treeUri, filePath, treeInfo, storageId);
                if (documentUri == null) {
                    Log.d(TAG, "Failed to build document URI");
                    continue;
                }
                
                Log.d(TAG, "Built document URI: " + documentUri);
                
                // Test if this Uri works
                try {
                    OutputStream test = context.getContentResolver().openOutputStream(documentUri);
                    if (test != null) {
                        test.close();
                        Log.d(TAG, "✓✓✓ SUCCESS! URI is valid and writable");
                        return documentUri;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "URI test failed: " + e.getMessage());
                }
                
                // Fallback: Search through tree
                Log.d(TAG, "Trying tree search as fallback...");
                DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
                if (tree != null) {
                    DocumentFile found = findFileInTree(tree, file);
                    if (found != null) {
                        Log.d(TAG, "✓✓✓ Found via tree search!");
                        return found.getUri();
                    }
                }
            }
            
            Log.d(TAG, "No matching permission found");
            Log.d(TAG, "========================================");
        } catch (Exception e) {
            Log.e(TAG, "Error in getUriViaSAF", e);
        }
        return null;
    }
    
    // Helper class to store tree info
    private static class TreeInfo {
        String storageId;
        String folder;
        
        TreeInfo(String storageId, String folder) {
            this.storageId = storageId;
            this.folder = folder;
        }
    }
    
    /**
     * Extract storage ID and folder from tree URI with proper URL decoding
     */
    private TreeInfo extractTreeInfo(Uri treeUri) {
        try {
            String treeUriString = treeUri.toString();
            Log.d(TAG, "Raw tree URI: " + treeUriString);
            
            if (!treeUriString.contains("tree/")) return null;
            
            String[] parts = treeUriString.split("tree/");
            if (parts.length <= 1) return null;
            
            String encoded = parts[1];
            Log.d(TAG, "Encoded part: " + encoded);
            
            // Decode URL encoding (%3A -> :, %20 -> space, etc.)
            String decoded = Uri.decode(encoded);
            Log.d(TAG, "Decoded part: " + decoded);
            
            if (!decoded.contains(":")) return null;
            
            String[] treeParts = decoded.split(":", 2);
            String storageId = treeParts[0];
            String folder = treeParts.length > 1 ? treeParts[1] : "";
            
            Log.d(TAG, "Extracted storage: " + storageId + ", folder: " + folder);
            
            return new TreeInfo(storageId, folder);
        } catch (Exception e) {
            Log.e(TAG, "Error extracting tree info", e);
            return null;
        }
    }
    
    /**
     * Check if a file path is under a granted tree
     */
    private boolean isPathUnderTree(String folderPath, TreeInfo treeInfo, String storageId) {
        try {
            // Build the absolute path of the granted tree folder
            String storagePath = storageId.equals("primary") ? 
                    "/storage/emulated/0/" : 
                    "/storage/" + storageId + "/";
            
            String treeFolderAbsolute;
            if (treeInfo.folder.isEmpty()) {
                // Permission is for storage root
                treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1); // Remove trailing /
            } else {
                treeFolderAbsolute = storagePath + treeInfo.folder;
            }
            
            Log.d(TAG, "Checking if '" + folderPath + "' is under '" + treeFolderAbsolute + "'");
            
            // The file's folder must start with the tree folder path
            // Handle both exact match and subfolder cases
            boolean isUnder = folderPath.equals(treeFolderAbsolute) || 
                            folderPath.startsWith(treeFolderAbsolute + "/");
            
            Log.d(TAG, "Result: " + isUnder);
            return isUnder;
        } catch (Exception e) {
            Log.e(TAG, "Error in isPathUnderTree", e);
            return false;
        }
    }
    
    /**
     * Build document URI from tree URI and file path
     */
    private Uri buildDocumentUri(Uri treeUri, String filePath, TreeInfo treeInfo, String storageId) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            String fileParentPath = file.getParent();
            
            // Get base tree document ID
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Log.d(TAG, "Tree document ID: " + treeDocId);
            
            // Build relative path from tree folder to file
            String storagePath = storageId.equals("primary") ? 
                    "/storage/emulated/0/" : 
                    "/storage/" + storageId + "/";
            
            // Handle empty tree folder (root permission case)
            String treeFolderAbsolute;
            if (treeInfo.folder.isEmpty()) {
                // Permission is for storage root (e.g., primary: or 853A-5808:)
                treeFolderAbsolute = storagePath.substring(0, storagePath.length() - 1); // Remove trailing /
                Log.d(TAG, "Tree is at storage root");
            } else {
                treeFolderAbsolute = storagePath + treeInfo.folder;
                Log.d(TAG, "Tree folder absolute: " + treeFolderAbsolute);
            }
            
            String relativePath = "";
            
            if (fileParentPath.equals(treeFolderAbsolute)) {
                // File is directly in the granted folder
                relativePath = fileName;
                Log.d(TAG, "File is in granted folder directly");
            } else if (fileParentPath.startsWith(treeFolderAbsolute + "/")) {
                // File is in a subfolder
                String subPath = fileParentPath.substring((treeFolderAbsolute + "/").length());
                relativePath = subPath + "/" + fileName;
                Log.d(TAG, "File is in subfolder: " + subPath);
            } else {
                Log.d(TAG, "File path doesn't match tree structure");
                Log.d(TAG, "Expected under: " + treeFolderAbsolute);
                Log.d(TAG, "Actual path: " + fileParentPath);
                return null;
            }
            
            Log.d(TAG, "Relative path to file: " + relativePath);
            
            // Build final document ID
            String documentId;
            if (treeDocId.endsWith(":") && !relativePath.isEmpty()) {
                // Tree doc ID is like "primary:" or "853A-5808:", don't add extra /
                documentId = treeDocId + relativePath;
            } else if (relativePath.isEmpty()) {
                // File is at the exact tree root
                documentId = treeDocId;
            } else {
                // Normal case with folder
                documentId = treeDocId + "/" + relativePath;
            }
            
            Log.d(TAG, "Final document ID: " + documentId);
            
            // Build document URI
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            return documentUri;
            
        } catch (Exception e) {
            Log.e(TAG, "Error building document URI", e);
            return null;
        }
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

    private DocumentFile findFileInTree(DocumentFile tree, File targetFile) {
        String targetName = targetFile.getName();
        for (DocumentFile file : tree.listFiles()) {
            if (file.isFile() && targetName.equals(file.getName())) {
                return file;
            } else if (file.isDirectory()) {
                DocumentFile found = findFileInTree(file, targetFile);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Gets the appropriate folder Uri to request for a given file path
     */
    public static Uri getFolderUriForPath(String filePath) {
        File file = new File(filePath);
        String parent = file.getParent();
        
        if (parent == null) return null;
        
        // For internal storage
        if (parent.startsWith("/storage/emulated/0/")) {
            String relativePath = parent.substring("/storage/emulated/0/".length());
            return Uri.parse("content://com.android.externalstorage.documents/document/primary:" + 
                    Uri.encode(relativePath));
        }
        
        // For SD card
        if (parent.startsWith("/storage/") && !parent.startsWith("/storage/emulated")) {
            String[] parts = parent.split("/");
            if (parts.length >= 3) {
                String sdCardId = parts[2];
                String pathPrefix = "/storage/" + sdCardId + "/";
                
                // Check if there's a path after the SD card root
                if (parent.length() > pathPrefix.length()) {
                    String relativePath = parent.substring(pathPrefix.length());
                    return Uri.parse("content://com.android.externalstorage.documents/document/" + 
                            sdCardId + ":" + Uri.encode(relativePath));
                } else {
                    // Parent is the SD card root
                    return Uri.parse("content://com.android.externalstorage.documents/document/" + 
                            sdCardId + ":");
                }
            }
        }
        
        return null;
    }
}
