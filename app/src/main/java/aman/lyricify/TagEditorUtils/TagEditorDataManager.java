package aman.lyricify;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import aman.taglib.TagLib;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



























public class TagEditorDataManager {

    private static final String TAG = "TagEditorDataManager";
    private final TagEditorActivity activity;
    private final TagLib tagLib;

    private static final Set<String> KNOWN_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "GENRE", "DATE",
                            "TRACKNUMBER", "DISCNUMBER", "COMPOSER", "LYRICIST", "WRITER",
                            "COMMENT", "RELEASEDATE", "LOCALE", "LANGUAGE", "UNSYNCEDLYRICS",
                            "LRC", "ELRC", "LYRICS"));

    public TagEditorDataManager(TagEditorActivity activity, TagLib tagLib) {
        this.activity = activity;
        this.tagLib = tagLib;
    }

    public interface LoadCallback {
    // CHANGED: Added byte[] rawData and String mimeType
    void onLoaded(HashMap<String, String> metadata, Bitmap artwork, byte[] rawData, String mimeType);
}

    public void loadCurrentTags(
            String filePath,
            String intentArtworkUrl,
            java.util.function.Consumer<String> showLoading,
            Runnable hideLoading,
            Runnable updateRestoreButton,
            LoadCallback callback) {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(activity, "No file path provided", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading.accept("Loading tags...");

        new Thread(() -> {
            try {
                HashMap<String, String> originalMetadata = tagLib.getMetadata(filePath);

                HashMap<String, String> uiMetadata = new HashMap<>();
                if (originalMetadata != null) {
                    for (Map.Entry<String, String> entry : originalMetadata.entrySet()) {
                        uiMetadata.put(entry.getKey().toUpperCase(), entry.getValue());
                    }
                }

                if (!uiMetadata.isEmpty()) {
                    activity.runOnUiThread(() -> populateUIFromMetadata(uiMetadata));
                }

                Bitmap artworkBitmap = null;
            byte[] rawArtworkBytes = null;     // NEW
            String rawArtworkMime = null;      // NEW

            TagLib.Artwork[] artworks = tagLib.getArtwork(filePath);
            
            if (artworks != null && artworks.length > 0) {
                rawArtworkBytes = artworks[0].data;       // Capture raw bytes!
                rawArtworkMime = artworks[0].mimeType;    // Capture mime type!
                
                final byte[] bytesFinal = rawArtworkBytes;
                final String mimeFinal = rawArtworkMime;

                // Load to UI
                activity.runOnUiThread(() -> {
                    loadAnimatedArtwork(bytesFinal, mimeFinal);
                });
                
                // Create static bitmap fallback
                artworkBitmap = BitmapFactory.decodeByteArray(rawArtworkBytes, 0, rawArtworkBytes.length);

            } else if (intentArtworkUrl != null && !intentArtworkUrl.isEmpty()) {
                activity.runOnUiThread(() -> loadArtworkFromUrl(intentArtworkUrl));
            }

            // Prepare final variables for lambda
            Bitmap finalBmp = artworkBitmap;
            byte[] finalBytes = rawArtworkBytes;
            String finalMime = rawArtworkMime;
            HashMap<String, String> finalMeta = originalMetadata; // Ensure this exists from your logic

            activity.runOnUiThread(() -> {
                hideLoading.run();
                updateRestoreButton.run();
                // CHANGED: Pass raw data
                callback.onLoaded(finalMeta, finalBmp, finalBytes, finalMime);
            });

            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    hideLoading.run();
                    String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                    activity.showErrorDialog("Read Error", err);
                });
            }
        }).start();
    }

    /**
     * NEW: Load artwork with animation support for GIF/WebP
     */
    private void loadAnimatedArtwork(byte[] imageData, String mimeType) {
        if (imageData == null || imageData.length == 0) {
            return;
        }

        try {
            // For Android 9+ (API 28+), use ImageDecoder to support animated images
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.Source source = 
                    android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(imageData));
                Drawable drawable = android.graphics.ImageDecoder.decodeDrawable(source);
                activity.getArtworkImageView().setImageDrawable(drawable);
                
                // Start animation if it's an AnimatedImageDrawable (GIF/WebP)
                if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                    Log.d(TAG, "Started animated artwork playback: " + mimeType);
                }
            } else {
                // Fallback for older Android versions - shows first frame only
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bitmap != null) {
                    activity.getArtworkImageView().setImageBitmap(bitmap);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading animated artwork, trying fallback: " + e.getMessage());
            // Fallback to static image on error
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bitmap != null) {
                    activity.getArtworkImageView().setImageBitmap(bitmap);
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback also failed: " + fallbackError.getMessage());
            }
        }
    }

    private void populateUIFromMetadata(HashMap<String, String> uiMetadata) {
        TextInputEditText titleEditText = activity.findViewById(R.id.titleEditText);
        TextInputEditText artistEditText = activity.findViewById(R.id.artistEditText);
        TextInputEditText albumEditText = activity.findViewById(R.id.albumEditText);
        TextInputEditText albumArtistEditText = activity.findViewById(R.id.albumArtistEditText);
        TextInputEditText genreEditText = activity.findViewById(R.id.genreEditText);
        TextInputEditText yearEditText = activity.findViewById(R.id.yearEditText);
        TextInputEditText trackNumberEditText = activity.findViewById(R.id.trackNumberEditText);
        TextInputEditText discNumberEditText = activity.findViewById(R.id.discNumberEditText);
        TextInputEditText composerEditText = activity.findViewById(R.id.composerEditText);
        TextInputEditText songwriterEditText = activity.findViewById(R.id.songwriterEditText);
        TextInputEditText commentEditText = activity.findViewById(R.id.commentEditText);
        TextInputEditText releaseDateEditText = activity.findViewById(R.id.releaseDateEditText);
        TextInputEditText audioLocaleEditText = activity.findViewById(R.id.audioLocaleEditText);
        TextInputEditText languageEditText = activity.findViewById(R.id.languageEditText);
        TextInputEditText unsyncedLyricsEditText = activity.findViewById(R.id.unsyncedLyricsEditText);
        TextInputEditText lrcEditText = activity.findViewById(R.id.lrcEditText);
        TextInputEditText elrcEditText = activity.findViewById(R.id.elrcEditText);
        TextInputEditText lyricsMultiEditText = activity.findViewById(R.id.lyricsMultiEditText);

        titleEditText.setText(uiMetadata.get("TITLE"));
        artistEditText.setText(uiMetadata.get("ARTIST"));
        albumEditText.setText(uiMetadata.get("ALBUM"));
        albumArtistEditText.setText(uiMetadata.get("ALBUMARTIST"));
        genreEditText.setText(uiMetadata.get("GENRE"));
        yearEditText.setText(uiMetadata.get("DATE"));
        trackNumberEditText.setText(uiMetadata.get("TRACKNUMBER"));
        discNumberEditText.setText(uiMetadata.getOrDefault("DISCNUMBER", ""));
        composerEditText.setText(uiMetadata.get("COMPOSER"));

        String lyricist = uiMetadata.get("LYRICIST");
        if (lyricist == null) lyricist = uiMetadata.get("WRITER");
        songwriterEditText.setText(lyricist);

        commentEditText.setText(uiMetadata.get("COMMENT"));
        releaseDateEditText.setText(uiMetadata.getOrDefault("RELEASEDATE", ""));
        audioLocaleEditText.setText(uiMetadata.getOrDefault("LOCALE", ""));
        languageEditText.setText(uiMetadata.getOrDefault("LANGUAGE", ""));

        unsyncedLyricsEditText.setText(uiMetadata.getOrDefault("UNSYNCEDLYRICS", ""));
        lrcEditText.setText(uiMetadata.getOrDefault("LRC", ""));
        elrcEditText.setText(uiMetadata.getOrDefault("ELRC", ""));
        lyricsMultiEditText.setText(uiMetadata.getOrDefault("LYRICS", ""));

        LinearLayout extendedTagsContainer = activity.getExtendedTagsContainer();
        extendedTagsContainer.removeAllViews();

        List<TagEditorActivity.CustomField> customFields = getCustomFieldsFromActivity();
        customFields.clear();

        TagEditorUIManager uiManager = new TagEditorUIManager(activity);
        for (Map.Entry<String, String> entry : uiMetadata.entrySet()) {
            String key = entry.getKey();
            if (!KNOWN_TAGS.contains(key)) {
                uiManager.addCustomField(
                        key, entry.getValue(), extendedTagsContainer, customFields, () -> {});
            }
        }
    }

    private void loadArtworkFromUrl(String url) {}

    public void restoreOriginalTags(
            HashMap<String, String> originalMetadata,
            List<TagEditorActivity.CustomField> customFields,
            LinearLayout extendedTagsContainer,
            LinearLayout tagFieldsContainer,
            TextInputEditText... views) {
        
        if (originalMetadata != null) {
            HashMap<String, String> uiMetadata = new HashMap<>();
            for (Map.Entry<String, String> entry : originalMetadata.entrySet()) {
                uiMetadata.put(entry.getKey().toUpperCase(), entry.getValue());
            }
            populateUIFromMetadata(uiMetadata);
        } else {
            for(TextInputEditText v : views) v.setText("");
            extendedTagsContainer.removeAllViews();
            customFields.clear();
        }
    }

    /**
     * NEW: Save tags with raw artwork bytes and MIME type preservation
     */
    public void saveTagsWithArtworkBytes(
            String filePath,
            List<TagEditorActivity.CustomField> customFields,
            boolean artworkChanged,
            Bitmap selectedArtwork,
            byte[] selectedArtworkBytes,
            String selectedArtworkMimeType,
            HashMap<String, String> originalMetadata,
            java.util.function.Consumer<String> showLoading,
            Runnable hideLoading,
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
            TextInputEditText albumArtistEditText,
            TextInputEditText genreEditText,
            TextInputEditText yearEditText,
            TextInputEditText trackNumberEditText,
            TextInputEditText discNumberEditText,
            TextInputEditText composerEditText,
            TextInputEditText songwriterEditText,
            TextInputEditText commentEditText,
            TextInputEditText releaseDateEditText,
            TextInputEditText audioLocaleEditText,
            TextInputEditText languageEditText,
            TextInputEditText unsyncedLyricsEditText,
            TextInputEditText lrcEditText,
            TextInputEditText elrcEditText,
            TextInputEditText lyricsMultiEditText) {
        
        if (filePath == null || filePath.isEmpty()) return;

        // READ UI VALUES
        String sTitle = titleEditText.getText().toString().trim();
        String sArtist = artistEditText.getText().toString().trim();
        String sAlbum = albumEditText.getText().toString().trim();
        String sAlbumArtist = albumArtistEditText.getText().toString().trim();
        String sGenre = genreEditText.getText().toString().trim();
        String sDate = yearEditText.getText().toString().trim();
        String sTrack = trackNumberEditText.getText().toString().trim();
        String sDisc = discNumberEditText.getText().toString().trim();
        String sComposer = composerEditText.getText().toString().trim();
        String sWriter = songwriterEditText.getText().toString().trim();
        String sComment = commentEditText.getText().toString().trim();
        String sRelease = releaseDateEditText.getText().toString().trim();
        String sLocale = audioLocaleEditText.getText().toString().trim();
        String sLang = languageEditText.getText().toString().trim();
        String sUnsynced = unsyncedLyricsEditText.getText().toString().trim();
        String sLrc = lrcEditText.getText().toString().trim();
        String sElrc = elrcEditText.getText().toString().trim();
        String sMulti = lyricsMultiEditText.getText().toString().trim();

        Map<String, String> customTagsMap = new HashMap<>();
        for (TagEditorActivity.CustomField field : customFields) {
            customTagsMap.put(field.tag, field.editText.getText().toString().trim());
        }

        showLoading.accept("Saving tags...");

        new Thread(() -> {
            try {
                HashMap<String, String> newMetadataMap = new HashMap<>();
                newMetadataMap.put("TITLE", sTitle);
                newMetadataMap.put("ARTIST", sArtist);
                newMetadataMap.put("ALBUM", sAlbum);
                newMetadataMap.put("ALBUMARTIST", sAlbumArtist);
                newMetadataMap.put("GENRE", sGenre);
                newMetadataMap.put("DATE", sDate);
                newMetadataMap.put("TRACKNUMBER", sTrack);
                newMetadataMap.put("DISCNUMBER", sDisc);
                newMetadataMap.put("COMPOSER", sComposer);
                newMetadataMap.put("LYRICIST", sWriter);
                newMetadataMap.put("COMMENT", sComment);
                newMetadataMap.put("RELEASEDATE", sRelease);
                newMetadataMap.put("LOCALE", sLocale);
                newMetadataMap.put("LANGUAGE", sLang);
                newMetadataMap.put("UNSYNCEDLYRICS", sUnsynced);
                newMetadataMap.put("LRC", sLrc);
                newMetadataMap.put("ELRC", sElrc);

                String bestLyrics = "";
                if (isValidLyrics(sMulti)) bestLyrics = sMulti;
                else if (isValidLyrics(sElrc)) bestLyrics = sElrc;
                else if (isValidLyrics(sLrc)) bestLyrics = sLrc;
                else if (isValidLyrics(sUnsynced)) bestLyrics = sUnsynced;

                newMetadataMap.put("LYRICS", bestLyrics);
                newMetadataMap.putAll(customTagsMap);

                File originalFile = new File(filePath);
                if (!originalFile.exists()) throw new Exception("File not found");
                if (!originalFile.canRead()) throw new Exception("Cannot read file");
                if (originalFile.length() == 0) throw new Exception("File is empty");

                File tempFile = new File(activity.getCacheDir(),
                        "temp_" + System.currentTimeMillis() + "_" + originalFile.getName());

                activity.runOnUiThread(() -> activity.getLoadingText().setText("Copying file..."));
                copyFile(originalFile, tempFile);

                if (originalMetadata != null) {
                    activity.runOnUiThread(() -> 
                            activity.getLoadingText().setText("Cleaning old tags..."));
                    HashMap<String, String> deleteMap = new HashMap<>();
                    for (String key : originalMetadata.keySet()) deleteMap.put(key, "");
                    for (String key : newMetadataMap.keySet()) deleteMap.put(key, "");
                    tagLib.setMetadata(tempFile.getAbsolutePath(), deleteMap);
                }

                activity.runOnUiThread(() -> 
                        activity.getLoadingText().setText("Writing new tags..."));
                boolean success = tagLib.setMetadata(tempFile.getAbsolutePath(), newMetadataMap);
                if (!success) throw new Exception("TagLib write failed");

                // NEW: Use raw bytes with MIME type if available
                if (artworkChanged) {
                    activity.runOnUiThread(() -> 
                            activity.getLoadingText().setText("Saving artwork..."));
                    
                    if (selectedArtworkBytes != null && selectedArtworkMimeType != null) {
                        tagLib.setArtwork(tempFile.getAbsolutePath(),
                                selectedArtworkBytes, selectedArtworkMimeType, "Cover (front)");
                    } else if (selectedArtwork != null) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        selectedArtwork.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                        tagLib.setArtwork(tempFile.getAbsolutePath(),
                                stream.toByteArray(), "image/jpeg", "Cover (front)");
                    }
                }

                activity.runOnUiThread(() -> 
                        activity.getLoadingText().setText("Writing to storage..."));
                FileSaver fileSaver = new FileSaver(activity);
                fileSaver.saveFile(filePath, tempFile, new FileSaver.SaveCallback() {
                    @Override
                    public void onProgress(String message) {
                        activity.runOnUiThread(() -> activity.getLoadingText().setText(message));
                    }

                    @Override
                    public void onSuccess() {
                        activity.runOnUiThread(() -> {
                            hideLoading.run();
                            Toast.makeText(activity, "Saved!", Toast.LENGTH_SHORT).show();
                            activity.setResult(activity.RESULT_OK);
                            activity.finish();
                        });
                    }

                    @Override
                    public void onError(String e) {
                        activity.runOnUiThread(() -> {
                            hideLoading.run();
                            activity.showErrorDialog("Storage Write Error", e);
                        });
                    }

                    @Override
                    public void onNeedPermission(String path) {
                        activity.runOnUiThread(() -> {
                            hideLoading.run();
                            showPermissionDialog(path, tempFile);
                        });
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                activity.runOnUiThread(() -> {
                    hideLoading.run();
                    activity.showErrorDialog("Save Error", 
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                });
            }
        }).start();
    }

    private void copyFile(File source, File dest) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) fos.write(buffer, 0, bytesRead);
        fos.flush();
        fos.close();
        fis.close();
    }

    private void showPermissionDialog(String p, File t) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission")
                .setMessage("Grant access to: " + p)
                .setPositiveButton("Grant", (d, w) -> activity.openDirectoryPicker(p))
                .setNegativeButton("Cancel", (d, w) -> { if (t.exists()) t.delete(); })
                .show();
    }

    public boolean hasUnsavedChanges(
            HashMap<String, String> originalMetadata,
            boolean artworkChanged,
            List<TagEditorActivity.CustomField> customFields,
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
            TextInputEditText albumArtistEditText,
            TextInputEditText genreEditText,
            TextInputEditText yearEditText,
            TextInputEditText trackNumberEditText,
            TextInputEditText discNumberEditText,
            TextInputEditText composerEditText,
            TextInputEditText songwriterEditText,
            TextInputEditText commentEditText,
            TextInputEditText releaseDateEditText,
            TextInputEditText audioLocaleEditText,
            TextInputEditText languageEditText,
            TextInputEditText unsyncedLyricsEditText,
            TextInputEditText lrcEditText,
            TextInputEditText elrcEditText,
            TextInputEditText lyricsMultiEditText) {

        if (artworkChanged) return true;
        if (originalMetadata == null) return false;

        Map<String, String> norm = new HashMap<>();
        for (Map.Entry<String, String> e : originalMetadata.entrySet()) {
            norm.put(e.getKey().toUpperCase(), e.getValue());
        }

        if (!equals(titleEditText.getText().toString(), norm.get("TITLE"))) return true;
        if (!equals(artistEditText.getText().toString(), norm.get("ARTIST"))) return true;
        if (!equals(albumEditText.getText().toString(), norm.get("ALBUM"))) return true;
        if (!equals(albumArtistEditText.getText().toString(), norm.get("ALBUMARTIST"))) return true;
        if (!equals(genreEditText.getText().toString(), norm.get("GENRE"))) return true;
        if (!equals(yearEditText.getText().toString(), norm.get("DATE"))) return true;
        if (!equals(trackNumberEditText.getText().toString(), norm.get("TRACKNUMBER"))) return true;
        if (!equals(discNumberEditText.getText().toString(), norm.getOrDefault("DISCNUMBER", ""))) return true;
        if (!equals(composerEditText.getText().toString(), norm.get("COMPOSER"))) return true;

        String origWriter = norm.get("LYRICIST");
        if (origWriter == null) origWriter = norm.get("WRITER");
        if (!equals(songwriterEditText.getText().toString(), origWriter)) return true;

        if (!equals(commentEditText.getText().toString(), norm.get("COMMENT"))) return true;
        if (!equals(releaseDateEditText.getText().toString(), norm.getOrDefault("RELEASEDATE", ""))) return true;
        if (!equals(audioLocaleEditText.getText().toString(), norm.getOrDefault("LOCALE", ""))) return true;
        if (!equals(languageEditText.getText().toString(), norm.getOrDefault("LANGUAGE", ""))) return true;
        if (!equals(unsyncedLyricsEditText.getText().toString(), norm.getOrDefault("UNSYNCEDLYRICS", ""))) return true;
        if (!equals(lrcEditText.getText().toString(), norm.getOrDefault("LRC", ""))) return true;
        if (!equals(elrcEditText.getText().toString(), norm.getOrDefault("ELRC", ""))) return true;
        if (!equals(lyricsMultiEditText.getText().toString(), norm.getOrDefault("LYRICS", ""))) return true;

        Map<String, String> currentExt = new HashMap<>();
        for (TagEditorActivity.CustomField f : customFields) {
            String val = f.editText.getText().toString().trim();
            if (val.equalsIgnoreCase("null")) val = "";
            currentExt.put(f.tag, val);
        }

        Map<String, String> origExt = new HashMap<>();
        for (Map.Entry<String, String> e : norm.entrySet()) {
            if (!KNOWN_TAGS.contains(e.getKey())) {
                String val = e.getValue();
                if (val == null) val = "";
                val = val.trim();
                if (val.equalsIgnoreCase("null")) val = "";
                origExt.put(e.getKey(), val);
            }
        }

        return !currentExt.equals(origExt);
    }

    private boolean equals(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        a = a.trim();
        b = b.trim();
        if (a.equalsIgnoreCase("null")) a = "";
        if (b.equalsIgnoreCase("null")) b = "";
        return a.equals(b);
    }

    private boolean isValidLyrics(String lyrics) {
        return lyrics != null && !lyrics.isEmpty() && !lyrics.equals("null");
    }

    private List<TagEditorActivity.CustomField> getCustomFieldsFromActivity() {
        return activity.getCustomFields();
    }
}