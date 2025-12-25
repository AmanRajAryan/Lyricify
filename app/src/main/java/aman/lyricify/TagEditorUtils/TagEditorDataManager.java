package aman.lyricify;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    private final TagEditorActivity activity;
    private final TagLib tagLib;

    private static final Set<String> KNOWN_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "TITLE",
                            "ARTIST",
                            "ALBUM",
                            "GENRE",
                            "DATE",
                            "TRACKNUMBER",
                            "DISCNUMBER",
                            "COMPOSER",
                            "LYRICIST",
                            "WRITER",
                            "COMMENT",
                            "RELEASEDATE",
                            "LOCALE",
                            "LANGUAGE",
                            "UNSYNCEDLYRICS",
                            "LRC",
                            "ELRC",
                            "LYRICS"));

    public TagEditorDataManager(TagEditorActivity activity, TagLib tagLib) {
        this.activity = activity;
        this.tagLib = tagLib;
    }

    public interface LoadCallback {
        void onLoaded(HashMap<String, String> metadata, Bitmap artwork);
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

        new Thread(
                        () -> {
                            try {
                                HashMap<String, String> originalMetadata =
                                        tagLib.getMetadata(filePath);

                                HashMap<String, String> uiMetadata = new HashMap<>();
                                if (originalMetadata != null) {
                                    for (Map.Entry<String, String> entry :
                                            originalMetadata.entrySet()) {
                                        uiMetadata.put(
                                                entry.getKey().toUpperCase(), entry.getValue());
                                    }
                                }

                                if (!uiMetadata.isEmpty()) {
                                    activity.runOnUiThread(
                                            () -> {
                                                populateUIFromMetadata(uiMetadata);
                                            });
                                }

                                Bitmap artwork = null;
                                TagLib.Artwork[] artworks = tagLib.getArtwork(filePath);
                                if (artworks != null && artworks.length > 0) {
                                    byte[] artworkData = artworks[0].data;
                                    Bitmap bitmap =
                                            BitmapFactory.decodeByteArray(
                                                    artworkData, 0, artworkData.length);
                                    if (bitmap != null) {
                                        artwork = bitmap;
                                        Bitmap finalArtwork = artwork;
                                        activity.runOnUiThread(
                                                () ->
                                                        activity.getArtworkImageView()
                                                                .setImageBitmap(finalArtwork));
                                    }
                                } else if (intentArtworkUrl != null
                                        && !intentArtworkUrl.isEmpty()) {
                                    activity.runOnUiThread(
                                            () -> loadArtworkFromUrl(intentArtworkUrl));
                                }

                                Bitmap finalArtwork = artwork;
                                activity.runOnUiThread(
                                        () -> {
                                            hideLoading.run();
                                            updateRestoreButton.run();
                                            callback.onLoaded(originalMetadata, finalArtwork);
                                        });

                            } catch (Exception e) {
                                activity.runOnUiThread(
                                        () -> {
                                            hideLoading.run();
                                            Toast.makeText(
                                                            activity,
                                                            "Error reading tags: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        });
                            }
                        })
                .start();
    }

    private void populateUIFromMetadata(HashMap<String, String> uiMetadata) {
        TextInputEditText titleEditText = activity.findViewById(R.id.titleEditText);
        TextInputEditText artistEditText = activity.findViewById(R.id.artistEditText);
        TextInputEditText albumEditText = activity.findViewById(R.id.albumEditText);
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
        TextInputEditText unsyncedLyricsEditText =
                activity.findViewById(R.id.unsyncedLyricsEditText);
        TextInputEditText lrcEditText = activity.findViewById(R.id.lrcEditText);
        TextInputEditText elrcEditText = activity.findViewById(R.id.elrcEditText);
        TextInputEditText lyricsMultiEditText = activity.findViewById(R.id.lyricsMultiEditText);

        titleEditText.setText(uiMetadata.get("TITLE"));
        artistEditText.setText(uiMetadata.get("ARTIST"));
        albumEditText.setText(uiMetadata.get("ALBUM"));
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

    private void loadArtworkFromUrl(String url) {
    }

    public void restoreOriginalTags(
            HashMap<String, String> originalMetadata,
            List<TagEditorActivity.CustomField> customFields,
            LinearLayout extendedTagsContainer,
            LinearLayout tagFieldsContainer,
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
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
        if (originalMetadata != null) {
            titleEditText.setText(originalMetadata.getOrDefault("TITLE", ""));
            artistEditText.setText(originalMetadata.getOrDefault("ARTIST", ""));
            albumEditText.setText(originalMetadata.getOrDefault("ALBUM", ""));
            genreEditText.setText(originalMetadata.getOrDefault("GENRE", ""));
            yearEditText.setText(originalMetadata.getOrDefault("DATE", ""));
            trackNumberEditText.setText(originalMetadata.getOrDefault("TRACKNUMBER", ""));
            discNumberEditText.setText(originalMetadata.getOrDefault("DISCNUMBER", ""));
            composerEditText.setText(originalMetadata.getOrDefault("COMPOSER", ""));
            songwriterEditText.setText(
                    originalMetadata.getOrDefault(
                            "LYRICIST", originalMetadata.getOrDefault("WRITER", "")));
            commentEditText.setText(originalMetadata.getOrDefault("COMMENT", ""));
            releaseDateEditText.setText(originalMetadata.getOrDefault("RELEASEDATE", ""));
            audioLocaleEditText.setText(originalMetadata.getOrDefault("LOCALE", ""));
            languageEditText.setText(originalMetadata.getOrDefault("LANGUAGE", ""));

            unsyncedLyricsEditText.setText(originalMetadata.getOrDefault("UNSYNCEDLYRICS", ""));
            lrcEditText.setText(originalMetadata.getOrDefault("LRC", ""));
            elrcEditText.setText(originalMetadata.getOrDefault("ELRC", ""));
            lyricsMultiEditText.setText(originalMetadata.getOrDefault("LYRICS", ""));

            extendedTagsContainer.removeAllViews();
            
            for (TagEditorActivity.CustomField f : customFields) {
                if (f.layout.getParent() == tagFieldsContainer
                        || f.layout.getParent() == extendedTagsContainer) {
                    ((ViewGroup) f.layout.getParent()).removeView(f.layout);
                }
            }
            customFields.clear();

            TagEditorUIManager uiManager = new TagEditorUIManager(activity);
            for (Map.Entry<String, String> entry : originalMetadata.entrySet()) {
                String key = entry.getKey();
                if (!KNOWN_TAGS.contains(key)) {
                    uiManager.addCustomField(
                            key, entry.getValue(), extendedTagsContainer, customFields, () -> {});
                }
            }

        } else {
            clearAllInputs(
                    titleEditText,
                    artistEditText,
                    albumEditText,
                    genreEditText,
                    yearEditText,
                    trackNumberEditText,
                    discNumberEditText,
                    composerEditText,
                    songwriterEditText,
                    commentEditText,
                    releaseDateEditText,
                    audioLocaleEditText,
                    languageEditText,
                    unsyncedLyricsEditText,
                    lrcEditText,
                    elrcEditText,
                    lyricsMultiEditText);
            extendedTagsContainer.removeAllViews();
            customFields.clear();
        }
    }

    private void clearAllInputs(
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
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
        titleEditText.setText("");
        artistEditText.setText("");
        albumEditText.setText("");
        genreEditText.setText("");
        yearEditText.setText("");
        trackNumberEditText.setText("");
        discNumberEditText.setText("");
        composerEditText.setText("");
        songwriterEditText.setText("");
        commentEditText.setText("");
        releaseDateEditText.setText("");
        audioLocaleEditText.setText("");
        languageEditText.setText("");
        unsyncedLyricsEditText.setText("");
        lrcEditText.setText("");
        elrcEditText.setText("");
        lyricsMultiEditText.setText("");
    }

    public void saveTags(
            String filePath,
            List<TagEditorActivity.CustomField> customFields,
            boolean artworkChanged,
            Bitmap selectedArtwork,
            HashMap<String, String> originalMetadata,
            java.util.function.Consumer<String> showLoading,
            Runnable hideLoading,
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
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
        showLoading.accept("Saving tags...");

        new Thread(
                        () -> {
                            try {
                                HashMap<String, String> newMetadataMap = new HashMap<>();

                                newMetadataMap.put(
                                        "TITLE", titleEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "ARTIST", artistEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "ALBUM", albumEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "GENRE", genreEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "DATE", yearEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "TRACKNUMBER",
                                        trackNumberEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "DISCNUMBER",
                                        discNumberEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "COMPOSER", composerEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "LYRICIST", songwriterEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "COMMENT", commentEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "RELEASEDATE",
                                        releaseDateEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "LOCALE", audioLocaleEditText.getText().toString().trim());
                                newMetadataMap.put(
                                        "LANGUAGE", languageEditText.getText().toString().trim());

                                String unsyncedLyrics =
                                        unsyncedLyricsEditText.getText().toString().trim();
                                String lrc = lrcEditText.getText().toString().trim();
                                String elrc = elrcEditText.getText().toString().trim();
                                String elrcMulti = lyricsMultiEditText.getText().toString().trim();

                                newMetadataMap.put("UNSYNCEDLYRICS", unsyncedLyrics);
                                newMetadataMap.put("LRC", lrc);
                                newMetadataMap.put("ELRC", elrc);

                                String bestLyrics = "";
                                if (isValidLyrics(elrcMulti)) {
                                    bestLyrics = elrcMulti;
                                } else if (isValidLyrics(elrc)) {
                                    bestLyrics = elrc;
                                } else if (isValidLyrics(lrc)) {
                                    bestLyrics = lrc;
                                } else if (isValidLyrics(unsyncedLyrics)) {
                                    bestLyrics = unsyncedLyrics;
                                }

                                newMetadataMap.put("LYRICS", bestLyrics);

                                for (TagEditorActivity.CustomField field : customFields) {
                                    newMetadataMap.put(
                                            field.tag, field.editText.getText().toString().trim());
                                }

                                File originalFile = new File(filePath);
                                File tempFile =
                                        new File(
                                                activity.getCacheDir(),
                                                "temp_"
                                                        + System.currentTimeMillis()
                                                        + "_"
                                                        + originalFile.getName());

                                activity.runOnUiThread(
                                        () -> activity.getLoadingText().setText("Copying file..."));
                                copyFile(originalFile, tempFile);

                                if (originalMetadata != null) {
                                    activity.runOnUiThread(
                                            () ->
                                                    activity.getLoadingText()
                                                            .setText("Cleaning old tags..."));

                                    HashMap<String, String> deleteMap = new HashMap<>();

                                    for (String key : originalMetadata.keySet()) {
                                        deleteMap.put(key, "");
                                    }

                                    for (String key : newMetadataMap.keySet()) {
                                        deleteMap.put(key, "");
                                    }

                                    tagLib.setMetadata(tempFile.getAbsolutePath(), deleteMap);
                                }

                                activity.runOnUiThread(
                                        () ->
                                                activity.getLoadingText()
                                                        .setText("Writing new tags..."));
                                boolean metadataSuccess =
                                        tagLib.setMetadata(
                                                tempFile.getAbsolutePath(), newMetadataMap);

                                if (!metadataSuccess) {
                                    if (tempFile.exists()) tempFile.delete();
                                    throw new Exception("Metadata write failed");
                                }

                                if (artworkChanged && selectedArtwork != null) {
                                    activity.runOnUiThread(
                                            () ->
                                                    activity.getLoadingText()
                                                            .setText("Saving artwork..."));
                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    selectedArtwork.compress(
                                            Bitmap.CompressFormat.JPEG, 90, stream);
                                    tagLib.setArtwork(
                                            tempFile.getAbsolutePath(),
                                            stream.toByteArray(),
                                            "image/jpeg",
                                            "Cover (front)");
                                }

                                activity.runOnUiThread(
                                        () ->
                                                activity.getLoadingText()
                                                        .setText("Writing to storage..."));
                                FileSaver fileSaver = new FileSaver(activity);
                                fileSaver.saveFile(
                                        filePath,
                                        tempFile,
                                        new FileSaver.SaveCallback() {
                                            @Override
                                            public void onProgress(String message) {
                                                activity.runOnUiThread(
                                                        () ->
                                                                activity.getLoadingText()
                                                                        .setText(message));
                                            }

                                            @Override
                                            public void onSuccess() {
                                                activity.runOnUiThread(
                                                        () -> {
                                                            hideLoading.run();
                                                            Toast.makeText(
                                                                            activity,
                                                                            "Saved!",
                                                                            Toast.LENGTH_SHORT)
                                                                    .show();
                                                            activity.setResult(activity.RESULT_OK);
                                                            activity.finish();
                                                        });
                                            }

                                            @Override
                                            public void onError(String e) {
                                                activity.runOnUiThread(
                                                        () -> {
                                                            hideLoading.run();
                                                            Toast.makeText(
                                                                            activity,
                                                                            "Save Error: " + e,
                                                                            Toast.LENGTH_LONG)
                                                                    .show();
                                                        });
                                            }

                                            @Override
                                            public void onNeedPermission(String path) {
                                                activity.runOnUiThread(
                                                        () -> {
                                                            hideLoading.run();
                                                            showPermissionDialog(path, tempFile);
                                                        });
                                            }
                                        });

                            } catch (Exception e) {
                                e.printStackTrace();
                                activity.runOnUiThread(
                                        () -> {
                                            hideLoading.run();
                                            Toast.makeText(
                                                            activity,
                                                            "Error: " + e.getMessage(),
                                                            Toast.LENGTH_LONG)
                                                    .show();
                                        });
                            }
                        })
                .start();
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
                .setPositiveButton(
                        "Grant",
                        (d, w) -> {
                            // CHANGED: Call the activity method to open the picker
                            activity.openDirectoryPicker(p);
                        })
                .setNegativeButton(
                        "Cancel",
                        (d, w) -> {
                            if (t.exists()) t.delete();
                        })
                .show();
    }

    public boolean hasUnsavedChanges(
            HashMap<String, String> originalMetadata,
            boolean artworkChanged,
            List<TagEditorActivity.CustomField> customFields,
            TextInputEditText titleEditText,
            TextInputEditText artistEditText,
            TextInputEditText albumEditText,
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

        Map<String, String> normalizedOriginal = new HashMap<>();
        for (Map.Entry<String, String> entry : originalMetadata.entrySet()) {
            normalizedOriginal.put(entry.getKey().toUpperCase(), entry.getValue());
        }

        if (!equals(titleEditText.getText().toString(), normalizedOriginal.get("TITLE"))) return true;
        if (!equals(artistEditText.getText().toString(), normalizedOriginal.get("ARTIST"))) return true;
        if (!equals(albumEditText.getText().toString(), normalizedOriginal.get("ALBUM"))) return true;
        if (!equals(genreEditText.getText().toString(), normalizedOriginal.get("GENRE"))) return true;
        if (!equals(yearEditText.getText().toString(), normalizedOriginal.get("DATE"))) return true;
        if (!equals(trackNumberEditText.getText().toString(), normalizedOriginal.get("TRACKNUMBER"))) return true;
        if (!equals(discNumberEditText.getText().toString(), normalizedOriginal.getOrDefault("DISCNUMBER", ""))) return true;
        if (!equals(composerEditText.getText().toString(), normalizedOriginal.get("COMPOSER"))) return true;

        String originalSongwriter = normalizedOriginal.get("LYRICIST");
        if (originalSongwriter == null) originalSongwriter = normalizedOriginal.get("WRITER");
        if (!equals(songwriterEditText.getText().toString(), originalSongwriter)) return true;

        if (!equals(commentEditText.getText().toString(), normalizedOriginal.get("COMMENT"))) return true;
        if (!equals(releaseDateEditText.getText().toString(), normalizedOriginal.getOrDefault("RELEASEDATE", ""))) return true;
        if (!equals(audioLocaleEditText.getText().toString(), normalizedOriginal.getOrDefault("LOCALE", ""))) return true;
        if (!equals(languageEditText.getText().toString(), normalizedOriginal.getOrDefault("LANGUAGE", ""))) return true;

        if (!equals(unsyncedLyricsEditText.getText().toString(), normalizedOriginal.getOrDefault("UNSYNCEDLYRICS", ""))) return true;
        if (!equals(lrcEditText.getText().toString(), normalizedOriginal.getOrDefault("LRC", ""))) return true;
        if (!equals(elrcEditText.getText().toString(), normalizedOriginal.getOrDefault("ELRC", ""))) return true;
        if (!equals(lyricsMultiEditText.getText().toString(), normalizedOriginal.getOrDefault("LYRICS", ""))) return true;

        // Custom Tags Comparison
        Map<String, String> currentExtended = new HashMap<>();
        for (TagEditorActivity.CustomField f : customFields) {
            String val = f.editText.getText().toString().trim();
            if (val.equalsIgnoreCase("null")) val = "";
            currentExtended.put(f.tag, val);
        }

        Map<String, String> originalExtended = new HashMap<>();
        for (Map.Entry<String, String> entry : normalizedOriginal.entrySet()) {
            if (!KNOWN_TAGS.contains(entry.getKey())) {
                String val = entry.getValue();
                if (val == null) val = "";
                val = val.trim();
                if (val.equalsIgnoreCase("null")) val = "";
                originalExtended.put(entry.getKey(), val);
            }
        }

        return !currentExtended.equals(originalExtended);
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
