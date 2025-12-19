package aman.lyricify;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import aman.taglib.TagLib;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagEditorActivity extends AppCompatActivity implements ApiClient.CacheListener {

    // UI Components
    private ImageView artworkImageView;
    private TextView fileNameText;
    private TextInputEditText titleEditText, artistEditText, albumEditText;
    private TextInputEditText genreEditText, yearEditText, releaseDateEditText;
    private TextInputEditText trackNumberEditText, discNumberEditText;
    private TextInputEditText audioLocaleEditText, languageEditText;
    private TextInputEditText composerEditText, songwriterEditText;
    private TextInputEditText commentEditText;

    // Lyrics Fields
    private TextInputEditText unsyncedLyricsEditText,
            lrcEditText,
            elrcEditText,
            lyricsMultiEditText;
    private LinearLayout lyricsHeader, lyricsContainer;
    private ImageView lyricsArrow;
    private boolean isLyricsVisible = false;

    private MaterialToolbar toolbar;
    private MaterialButton changeArtworkButton,
            resetArtworkButton,
            fetchFromApiButton,
            restoreTagsButton;
    private ExtendedFloatingActionButton saveButton, addFieldButton;
    private LinearLayout tagFieldsContainer;

    // Extended Tags UI
    private LinearLayout extendedTagsHeader, extendedTagsContainer;
    private ImageView extendedTagsArrow;
    private boolean isExtendedTagsVisible = false;

    private FrameLayout loadingOverlay;
    private TextView loadingText;

    // Logic & Data vars
    private String filePath;
    private TagLib tagLib;
    private Bitmap selectedArtwork;
    private Bitmap originalArtwork;
    private boolean artworkChanged = false;
    private HashMap<String, String> originalMetadata;

    // Song data passed via Intent
    private String intentTitle;
    private String intentArtist;
    private String intentAlbum;
    private String intentArtworkUrl;
    private String intentSongId;

    // Cached metadata
    private ApiClient.LyricsResponse cachedMetadata;
    private static final String WAITING_MESSAGE = "No cached metadata available, waiting!";

    // Custom fields list
    private List<CustomField> customFields = new ArrayList<>();

    // Launcher for image picker
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    // Delegates
    private TagEditorUIManager uiManager;
    private TagEditorDataManager dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_editor);

        tagLib = new TagLib();
        
        initializeViews();
        
        // Initialize delegates
        uiManager = new TagEditorUIManager(this);
        dataManager = new TagEditorDataManager(this, tagLib);
        
        setupToolbar();
        setupImagePickerLauncher();
        extractIntentData();
        loadCurrentTags();
        setupListeners();

        // Register to listen for background updates
        ApiClient.registerCacheListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister to prevent leaks
        ApiClient.unregisterCacheListener(this);
    }

    // --- Cache Listener Interface ---
    @Override
    public void onCacheUpdated(ApiClient.LyricsResponse response) {
        runOnUiThread(
                () -> {
                    this.cachedMetadata = response;

                    // Check if we were actively waiting for this data (loading screen visible with
                    // specific text)
                    if (loadingOverlay.getVisibility() == View.VISIBLE
                            && loadingText.getText().toString().equals(WAITING_MESSAGE)) {

                        populateFieldsFromCachedData();
                        // hideLoading() is called inside populateFieldsFromCachedData's error
                        // handling
                        // but usually handled by image loader callback.
                        // We should ensure it closes if no image URL is involved.
                        if (intentArtworkUrl == null) {
                            hideLoading();
                        }
                        Toast.makeText(this, "Metadata received!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        artworkImageView = findViewById(R.id.artworkImageView);
        fileNameText = findViewById(R.id.fileNameText);

        titleEditText = findViewById(R.id.titleEditText);
        artistEditText = findViewById(R.id.artistEditText);
        albumEditText = findViewById(R.id.albumEditText);

        genreEditText = findViewById(R.id.genreEditText);
        languageEditText = findViewById(R.id.languageEditText);

        trackNumberEditText = findViewById(R.id.trackNumberEditText);
        discNumberEditText = findViewById(R.id.discNumberEditText);

        composerEditText = findViewById(R.id.composerEditText);
        songwriterEditText = findViewById(R.id.songwriterEditText);
        commentEditText = findViewById(R.id.commentEditText);

        releaseDateEditText = findViewById(R.id.releaseDateEditText);
        audioLocaleEditText = findViewById(R.id.audioLocaleEditText);
        yearEditText = findViewById(R.id.yearEditText);

        // Lyrics Fields
        unsyncedLyricsEditText = findViewById(R.id.unsyncedLyricsEditText);
        lrcEditText = findViewById(R.id.lrcEditText);
        elrcEditText = findViewById(R.id.elrcEditText);
        lyricsMultiEditText = findViewById(R.id.lyricsMultiEditText);
        lyricsHeader = findViewById(R.id.lyricsHeader);
        lyricsContainer = findViewById(R.id.lyricsContainer);
        lyricsArrow = findViewById(R.id.lyricsArrow);

        changeArtworkButton = findViewById(R.id.changeArtworkButton);
        resetArtworkButton = findViewById(R.id.resetArtworkButton);
        resetArtworkButton.setEnabled(false);

        fetchFromApiButton = findViewById(R.id.fetchFromApiButton);
        restoreTagsButton = findViewById(R.id.restoreTagsButton);
        restoreTagsButton.setEnabled(false);

        saveButton = findViewById(R.id.saveButton);
        addFieldButton = findViewById(R.id.addFieldButton);

        tagFieldsContainer = findViewById(R.id.tagFieldsContainer);

        // Extended Tags
        extendedTagsHeader = findViewById(R.id.extendedTagsHeader);
        extendedTagsContainer = findViewById(R.id.extendedTagsContainer);
        extendedTagsArrow = findViewById(R.id.extendedTagsArrow);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setOnMenuItemClickListener(
                item -> {
                    if (item.getItemId() == R.id.action_save) {
                        saveTags();
                        return true;
                    }
                    return false;
                });
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Uri imageUri = result.getData().getData();
                                if (imageUri != null) {
                                    loadArtworkFromUri(imageUri);
                                }
                            }
                        });
    }

    private void extractIntentData() {
        filePath = getIntent().getStringExtra("FILE_PATH");
        intentTitle = getIntent().getStringExtra("SONG_TITLE");
        intentArtist = getIntent().getStringExtra("SONG_ARTIST");
        intentAlbum = getIntent().getStringExtra("SONG_ALBUM");
        intentArtworkUrl = getIntent().getStringExtra("ARTWORK_URL");
        intentSongId = getIntent().getStringExtra("SONG_ID");

        if (getIntent().hasExtra("CACHED_METADATA")) {
            cachedMetadata =
                    (ApiClient.LyricsResponse) getIntent().getSerializableExtra("CACHED_METADATA");
        }

        if (filePath != null) {
            File file = new File(filePath);
            fileNameText.setText(file.getName());
        }
    }

    private void loadCurrentTags() {
        dataManager.loadCurrentTags(
            filePath,
            intentArtworkUrl,
            this::showLoading,
            this::hideLoading,
            this::updateRestoreButtonState,
            (metadata, artwork) -> {
                this.originalMetadata = metadata;
                this.originalArtwork = artwork;
            }
        );
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> saveTags());
        changeArtworkButton.setOnClickListener(v -> selectArtwork());
        resetArtworkButton.setOnClickListener(v -> resetArtwork());

        // MODIFIED: Only checks cache, effectively waits for background process
        fetchFromApiButton.setOnClickListener(
                v -> {
                    if (cachedMetadata != null) {
                        populateFieldsFromCachedData();
                    } else {
                        showLoading(WAITING_MESSAGE);
                        Toast.makeText(this, WAITING_MESSAGE, Toast.LENGTH_LONG).show();
                    }
                });

        restoreTagsButton.setOnClickListener(v -> showRestoreConfirmation());
        addFieldButton.setOnClickListener(v -> uiManager.showAddCustomFieldDialog(customFields, this::updateRestoreButtonState));

        extendedTagsHeader.setOnClickListener(v -> toggleExtendedTags());
        lyricsHeader.setOnClickListener(v -> toggleLyrics());

        TextWatcher changeWatcher =
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateRestoreButtonState();
                    }
                };

        titleEditText.addTextChangedListener(changeWatcher);
        artistEditText.addTextChangedListener(changeWatcher);
        albumEditText.addTextChangedListener(changeWatcher);
        genreEditText.addTextChangedListener(changeWatcher);
        audioLocaleEditText.addTextChangedListener(changeWatcher);
        languageEditText.addTextChangedListener(changeWatcher);
        yearEditText.addTextChangedListener(changeWatcher);
        releaseDateEditText.addTextChangedListener(changeWatcher);
        trackNumberEditText.addTextChangedListener(changeWatcher);
        discNumberEditText.addTextChangedListener(changeWatcher);
        composerEditText.addTextChangedListener(changeWatcher);
        songwriterEditText.addTextChangedListener(changeWatcher);
        commentEditText.addTextChangedListener(changeWatcher);

        // Lyrics watchers
        unsyncedLyricsEditText.addTextChangedListener(changeWatcher);
        lrcEditText.addTextChangedListener(changeWatcher);
        elrcEditText.addTextChangedListener(changeWatcher);
        lyricsMultiEditText.addTextChangedListener(changeWatcher);
    }

    private void toggleExtendedTags() {
        isExtendedTagsVisible = !isExtendedTagsVisible;
        extendedTagsContainer.setVisibility(isExtendedTagsVisible ? View.VISIBLE : View.GONE);
        extendedTagsArrow.setRotation(isExtendedTagsVisible ? 180f : 0f);
    }

    private void toggleLyrics() {
        isLyricsVisible = !isLyricsVisible;
        lyricsContainer.setVisibility(isLyricsVisible ? View.VISIBLE : View.GONE);
        lyricsArrow.setRotation(isLyricsVisible ? 180f : 0f);
    }

    private void updateRestoreButtonState() {
        boolean hasChanges = dataManager.hasUnsavedChanges(
            originalMetadata,
            artworkChanged,
            customFields,
            titleEditText, artistEditText, albumEditText, genreEditText, yearEditText,
            trackNumberEditText, discNumberEditText, composerEditText, songwriterEditText,
            commentEditText, releaseDateEditText, audioLocaleEditText, languageEditText,
            unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText
        );
        restoreTagsButton.setEnabled(hasChanges);
    }

    private void showRestoreConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Restore to Original")
                .setMessage("Revert all changes to how they were when you opened this file?")
                .setPositiveButton("Restore", (dialog, which) -> restoreOriginalTags())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreOriginalTags() {
        dataManager.restoreOriginalTags(
            originalMetadata,
            customFields,
            extendedTagsContainer,
            tagFieldsContainer,
            titleEditText, artistEditText, albumEditText, genreEditText, yearEditText,
            trackNumberEditText, discNumberEditText, composerEditText, songwriterEditText,
            commentEditText, releaseDateEditText, audioLocaleEditText, languageEditText,
            unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText
        );

        resetArtwork();
        restoreTagsButton.setEnabled(false);
        Toast.makeText(this, "Restored to original", Toast.LENGTH_SHORT).show();
    }

    private void populateFieldsFromCachedData() {
        showLoading("Applying cached metadata...");

        runOnUiThread(
                () -> {
                    if (titleEditText.getText().toString().isEmpty())
                        titleEditText.setText(intentTitle);
                    if (artistEditText.getText().toString().isEmpty())
                        artistEditText.setText(intentArtist);

                    if (cachedMetadata != null) {
                        if (cachedMetadata.genreNames != null
                                && !cachedMetadata.genreNames.isEmpty()) {
                            genreEditText.setText(String.join(", ", cachedMetadata.genreNames));
                        } else if (cachedMetadata.genre != null
                                && !cachedMetadata.genre.isEmpty()) {
                            genreEditText.setText(cachedMetadata.genre);
                        }

                        if (cachedMetadata.audioLocale != null
                                && !cachedMetadata.audioLocale.isEmpty()) {
                            audioLocaleEditText.setText(cachedMetadata.audioLocale);
                        }

                        if (cachedMetadata.releaseDate != null
                                && !cachedMetadata.releaseDate.isEmpty()) {
                            releaseDateEditText.setText(cachedMetadata.releaseDate);
                            if (cachedMetadata.releaseDate.length() >= 4) {
                                yearEditText.setText(cachedMetadata.releaseDate.substring(0, 4));
                            }
                        }

                        if (cachedMetadata.trackNumber != null
                                && !cachedMetadata.trackNumber.isEmpty())
                            trackNumberEditText.setText(cachedMetadata.trackNumber);
                        if (cachedMetadata.discNumber != null
                                && !cachedMetadata.discNumber.isEmpty())
                            discNumberEditText.setText(cachedMetadata.discNumber);

                        if (cachedMetadata.composerName != null
                                && !cachedMetadata.composerName.isEmpty()) {
                            composerEditText.setText(cachedMetadata.composerName);
                        }

                        if (cachedMetadata.songwriters != null
                                && !cachedMetadata.songwriters.isEmpty()) {
                            songwriterEditText.setText(
                                    String.join(", ", cachedMetadata.songwriters));
                        }

                        if (cachedMetadata.contentRating != null
                                && !cachedMetadata.contentRating.isEmpty())
                            uiManager.addOrUpdateCustomField("CONTENTRATING", cachedMetadata.contentRating, customFields, extendedTagsContainer, this::updateRestoreButtonState);
                        if (cachedMetadata.isrc != null && !cachedMetadata.isrc.isEmpty())
                            uiManager.addOrUpdateCustomField("ISRC", cachedMetadata.isrc, customFields, extendedTagsContainer, this::updateRestoreButtonState);

                        // Populate Lyrics
                        if (cachedMetadata.plain != null && !cachedMetadata.plain.isEmpty())
                            unsyncedLyricsEditText.setText(cachedMetadata.plain);
                        if (cachedMetadata.lrc != null && !cachedMetadata.lrc.isEmpty())
                            lrcEditText.setText(cachedMetadata.lrc);
                        if (cachedMetadata.elrc != null && !cachedMetadata.elrc.isEmpty())
                            elrcEditText.setText(cachedMetadata.elrc);
                        if (cachedMetadata.elrcMultiPerson != null
                                && !cachedMetadata.elrcMultiPerson.isEmpty())
                            lyricsMultiEditText.setText(cachedMetadata.elrcMultiPerson);
                    }

                    if (intentArtworkUrl != null) {
                        String artworkUrl =
                                intentArtworkUrl
                                        .replace("{w}", "600")
                                        .replace("{h}", "600")
                                        .replace("{f}", "jpg");
                        loadArtworkWithGlide(artworkUrl);
                    } else {
                        hideLoading();
                        Toast.makeText(this, "Metadata applied!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadArtworkWithGlide(String url) {
        Glide.with(this)
                .asBitmap()
                .load(url)
                .into(
                        new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(
                                    Bitmap resource,
                                    com.bumptech.glide.request.transition.Transition<? super Bitmap>
                                            t) {
                                selectedArtwork = resource;
                                artworkImageView.setImageBitmap(resource);
                                artworkChanged = true;
                                resetArtworkButton.setEnabled(true);
                                updateRestoreButtonState();
                                hideLoading();
                                Toast.makeText(
                                                TagEditorActivity.this,
                                                "Metadata fetched!",
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }

                            @Override
                            public void onLoadCleared(android.graphics.drawable.Drawable p) {
                                hideLoading();
                            }
                        });
    }

    private void selectArtwork() {
        imagePickerLauncher.launch(new Intent(Intent.ACTION_PICK).setType("image/*"));
    }

    private void loadArtworkFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            selectedArtwork = BitmapFactory.decodeStream(is);
            artworkImageView.setImageBitmap(selectedArtwork);
            artworkChanged = true;
            resetArtworkButton.setEnabled(true);
            updateRestoreButtonState();
            is.close();
            Toast.makeText(this, "Artwork updated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
        }
    }

    private void resetArtwork() {
        if (originalArtwork != null) {
            artworkImageView.setImageBitmap(originalArtwork);
        } else {
            artworkImageView.setImageResource(R.drawable.ic_music_note);
        }
        selectedArtwork = null;
        artworkChanged = false;
        resetArtworkButton.setEnabled(false);
        updateRestoreButtonState();
    }

    private void loadArtworkFromUrl(String u) {
        Glide.with(this)
                .asBitmap()
                .load(u.replace("{w}", "600").replace("{h}", "600").replace("{f}", "jpg"))
                .into(
                        new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(
                                    Bitmap r,
                                    com.bumptech.glide.request.transition.Transition<? super Bitmap>
                                            t) {
                                artworkImageView.setImageBitmap(r);
                            }

                            @Override
                            public void onLoadCleared(android.graphics.drawable.Drawable p) {}
                        });
    }

    private void saveTags() {
        dataManager.saveTags(
            filePath,
            customFields,
            artworkChanged,
            selectedArtwork,
            originalMetadata,
            this::showLoading,
            this::hideLoading,
            titleEditText, artistEditText, albumEditText, genreEditText, yearEditText,
            trackNumberEditText, discNumberEditText, composerEditText, songwriterEditText,
            commentEditText, releaseDateEditText, audioLocaleEditText, languageEditText,
            unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText
        );
    }

    void showLoading(String m) {
        runOnUiThread(
                () -> {
                    loadingText.setText(m);
                    loadingOverlay.setVisibility(View.VISIBLE);
                });
    }

    void hideLoading() {
        runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
    }

    @Override
    public void onBackPressed() {
        boolean hasChanges = dataManager.hasUnsavedChanges(
            originalMetadata,
            artworkChanged,
            customFields,
            titleEditText, artistEditText, albumEditText, genreEditText, yearEditText,
            trackNumberEditText, discNumberEditText, composerEditText, songwriterEditText,
            commentEditText, releaseDateEditText, audioLocaleEditText, languageEditText,
            unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText
        );
        
        if (hasChanges)
            new AlertDialog.Builder(this)
                    .setTitle("Discard?")
                    .setPositiveButton("Discard", (d, w) -> super.onBackPressed())
                    .setNegativeButton("Cancel", null)
                    .show();
        else super.onBackPressed();
    }

    // Getters for delegates
    public ImageView getArtworkImageView() { return artworkImageView; }
    public LinearLayout getExtendedTagsContainer() { return extendedTagsContainer; }
    public LinearLayout getTagFieldsContainer() { return tagFieldsContainer; }
    public TextView getLoadingText() { return loadingText; }
    
    static class CustomField {
        String tag;
        String value;
        TextInputEditText editText;
        TextInputLayout layout;
    }

// Add this inside TagEditorActivity.java
public List<CustomField> getCustomFields() { 
    return customFields; 
}

}