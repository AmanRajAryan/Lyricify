package aman.lyricify;

import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public class TagEditorMetadataHelper {

    private final TagEditorActivity activity;
    private final TagEditorUIManager uiManager;
    private final TagEditorArtworkHelper artworkHelper;
    private final TagEditorLyricsHelper lyricsHelper;
    private final Runnable updateRestoreStateCallback;
    
    private ApiClient.LyricsResponse cachedMetadata;
    private String intentTitle, intentArtist, intentAlbum, intentArtworkUrl, intentSongId;
    private boolean hasAppliedMetadata = false;

    public TagEditorMetadataHelper(TagEditorActivity activity, 
                                   TagEditorUIManager uiManager,
                                   TagEditorArtworkHelper artworkHelper,
                                   TagEditorLyricsHelper lyricsHelper,
                                   Runnable updateRestoreStateCallback) {
        this.activity = activity;
        this.uiManager = uiManager;
        this.artworkHelper = artworkHelper;
        this.lyricsHelper = lyricsHelper;
        this.updateRestoreStateCallback = updateRestoreStateCallback;
    }

    public void setIntentData(String title, String artist, String album, String artworkUrl, String songId, ApiClient.LyricsResponse cached) {
        this.intentTitle = title;
        this.intentArtist = artist;
        this.intentAlbum = album;
        this.intentArtworkUrl = artworkUrl;
        this.intentSongId = songId;
        this.cachedMetadata = cached;
    }

    public void setCachedMetadata(ApiClient.LyricsResponse response) {
        this.cachedMetadata = response;
    }

    public ApiClient.LyricsResponse getCachedMetadata() { return cachedMetadata; }
    public String getIntentSongId() { return intentSongId; }
    public String getIntentArtworkUrl() { return intentArtworkUrl; }
    public boolean hasAppliedMetadata() { return hasAppliedMetadata; }

    public void showMetadataConflictDialog() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Metadata Available")
                .setMessage("Cached metadata is available. What would you like to do?")
                .setPositiveButton("Apply Cached Data", (dialog, which) -> {
                    populateFieldsFromCachedData();
                    Toast.makeText(activity, "Cached metadata applied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Search Again", (dialog, which) -> showIdentifySongDialog())
                .setNeutralButton("Dismiss", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public void showIdentifySongDialog() {
        String currentTitle = activity.titleEditText.getText().toString();
        String currentArtist = activity.artistEditText.getText().toString();
        
        MediaStoreHelper.LocalSong tempSong = new MediaStoreHelper.LocalSong(
                null, activity.filePath, currentTitle, currentArtist, "", -1, 0, 0);

        IdentifySongDialog dialog = new IdentifySongDialog(activity, tempSong, null); 
        dialog.setHideManualButton(true);
        dialog.setOnSongSelectedListener(this::fetchFullMetadataForSong);
        dialog.show();
    }

    private void fetchFullMetadataForSong(Song song) {
        activity.showLoading("Fetching details for " + song.getSongName() + "...");
        this.intentTitle = song.getSongName();
        this.intentArtist = song.getArtistName();
        this.intentAlbum = song.getAlbumName();
        this.intentArtworkUrl = song.getArtwork();
        this.intentSongId = song.getId();

        ApiClient.getLyrics(song.getId(), new ApiClient.LyricsCallback() {
            @Override
            public void onSuccess(ApiClient.LyricsResponse lyricsResponse) {
                cachedMetadata = lyricsResponse;
                activity.runOnUiThread(() -> {
                    populateFieldsFromCachedData();
                    activity.hideLoading();
                });
            }

            @Override
            public void onFailure(String error) {
                activity.runOnUiThread(() -> {
                    activity.hideLoading();
                    activity.showErrorDialog("Fetch Error", "Could not get details: " + error);
                });
            }
        });
    }

    public void populateFieldsFromCachedData() {
        activity.showLoading("Applying cached metadata...");
        activity.runOnUiThread(() -> {
            hasAppliedMetadata = true;

            if (activity.titleEditText.getText().toString().isEmpty()) activity.titleEditText.setText(intentTitle);
            if (activity.artistEditText.getText().toString().isEmpty()) activity.artistEditText.setText(intentArtist);
            if (activity.albumEditText.getText().toString().isEmpty() && intentAlbum != null) activity.albumEditText.setText(intentAlbum);

            if (cachedMetadata != null) {
                if (cachedMetadata.genreNames != null && !cachedMetadata.genreNames.isEmpty())
                    activity.genreEditText.setText(String.join(", ", cachedMetadata.genreNames));
                else if (cachedMetadata.genre != null)
                    activity.genreEditText.setText(cachedMetadata.genre);

                if (cachedMetadata.audioLocale != null) activity.audioLocaleEditText.setText(cachedMetadata.audioLocale);
                if (cachedMetadata.releaseDate != null) activity.releaseDateEditText.setText(cachedMetadata.releaseDate);
                if (cachedMetadata.trackNumber != null) activity.trackNumberEditText.setText(cachedMetadata.trackNumber);
                if (cachedMetadata.discNumber != null) activity.discNumberEditText.setText(cachedMetadata.discNumber);
                if (cachedMetadata.composerName != null) activity.composerEditText.setText(cachedMetadata.composerName);
                if (cachedMetadata.songwriters != null) activity.songwriterEditText.setText(String.join(", ", cachedMetadata.songwriters));

                if (cachedMetadata.contentRating != null)
                    uiManager.addOrUpdateCustomField("CONTENTRATING", cachedMetadata.contentRating, activity.getCustomFields(), activity.extendedTagsContainer, updateRestoreStateCallback);
                if (cachedMetadata.isrc != null)
                    uiManager.addOrUpdateCustomField("ISRC", cachedMetadata.isrc, activity.getCustomFields(), activity.extendedTagsContainer, updateRestoreStateCallback);

                if (cachedMetadata.plain != null) activity.unsyncedLyricsEditText.setText(cachedMetadata.plain);
                if (cachedMetadata.lrc != null) activity.lrcEditText.setText(cachedMetadata.lrc);
                if (cachedMetadata.elrc != null) activity.elrcEditText.setText(cachedMetadata.elrc);

                if (cachedMetadata.elrcMultiPerson != null) lyricsHelper.setCurrentElrcContent(cachedMetadata.elrcMultiPerson);
                if (cachedMetadata.ttml != null) lyricsHelper.setCurrentTtmlContent(cachedMetadata.ttml);

                lyricsHelper.updateSwapperUI();
            }

            if (intentArtworkUrl != null) {
                String artworkUrl = intentArtworkUrl.replace("{w}", "600").replace("{h}", "600").replace("{f}", "jpg");
                artworkHelper.loadArtworkWithGlide(artworkUrl);
            } else {
                activity.hideLoading();
                Toast.makeText(activity, "Metadata applied!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
