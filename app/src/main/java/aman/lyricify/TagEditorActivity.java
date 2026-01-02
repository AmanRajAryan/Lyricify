package aman.lyricify;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import aman.taglib.TagLib;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagEditorActivity extends AppCompatActivity implements ApiClient.CacheListener {

    // UI Components
    private ImageView artworkImageView;
    private LinearLayout artworkDimensionsContainer;
    private TextView artworkDimensionsText;

    // future aman : Use a flag to prevent infinite loops when updating width/height
    // programmatically
    private boolean isAutoUpdating = false;

    private TextView fileNameText;
    private TextInputEditText titleEditText, artistEditText, albumEditText, albumArtistEditText;
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

    // SWAPPER UI
    private LinearLayout formatSwapperContainer;
    private TextView labelElrc, labelTtml;
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

    // Intent Data
    private String intentTitle, intentArtist, intentAlbum, intentArtworkUrl, intentSongId;
    private ApiClient.LyricsResponse cachedMetadata;
    private static final String WAITING_MESSAGE = "No cached metadata available, waiting!";

    // --- NEW: State Flag ---
    private boolean hasAppliedMetadata = false;

    private List<CustomField> customFields = new ArrayList<>();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;

    private TagEditorUIManager uiManager;
    private TagEditorDataManager dataManager;

    private boolean isTtmlMode = false;
    private String currentElrcContent = "";
    private String currentTtmlContent = "";
    private String originalLyricsTagContent = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        
        setContentView(R.layout.activity_tag_editor);
        hideSystemUI();

        tagLib = new TagLib();
        initializeViews();

        uiManager = new TagEditorUIManager(this);
        dataManager = new TagEditorDataManager(this, tagLib);

        setupToolbar();
        setupImagePickerLauncher();
        setupDirectoryPickerLauncher();
        extractIntentData();
        loadCurrentTags();
        setupListeners();

        // Register for cache only if we have an ID (Lyrics Mode)
        if (intentSongId != null || cachedMetadata != null) {
            ApiClient.registerCacheListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiClient.unregisterCacheListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    public void onCacheUpdated(ApiClient.LyricsResponse response) {
        runOnUiThread(
                () -> {
                    this.cachedMetadata = response;
                    if (loadingOverlay.getVisibility() == View.VISIBLE
                            && loadingText.getText().toString().equals(WAITING_MESSAGE)) {
                        populateFieldsFromCachedData();
                        if (intentArtworkUrl == null) hideLoading();
                        Toast.makeText(this, "Metadata received!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        artworkImageView = findViewById(R.id.artworkImageView);
        artworkDimensionsContainer = findViewById(R.id.artworkDimensionsContainer);
        artworkDimensionsText = findViewById(R.id.artworkDimensionsText);

        fileNameText = findViewById(R.id.fileNameText);
        titleEditText = findViewById(R.id.titleEditText);
        artistEditText = findViewById(R.id.artistEditText);
        albumEditText = findViewById(R.id.albumEditText);
        albumArtistEditText = findViewById(R.id.albumArtistEditText);
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

        unsyncedLyricsEditText = findViewById(R.id.unsyncedLyricsEditText);
        lrcEditText = findViewById(R.id.lrcEditText);
        elrcEditText = findViewById(R.id.elrcEditText);
        lyricsMultiEditText = findViewById(R.id.lyricsMultiEditText);

        formatSwapperContainer = findViewById(R.id.formatSwapperContainer);
        labelElrc = findViewById(R.id.labelElrc);
        labelTtml = findViewById(R.id.labelTtml);

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
                                if (imageUri != null) loadArtworkFromUri(imageUri);
                            }
                        });
    }

    private void setupDirectoryPickerLauncher() {
        directoryPickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Uri treeUri = result.getData().getData();
                                if (treeUri != null) {
                                    getContentResolver()
                                            .takePersistableUriPermission(
                                                    treeUri,
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                            | Intent
                                                                    .FLAG_GRANT_WRITE_URI_PERMISSION);
                                    Toast.makeText(
                                                    this,
                                                    "✓ Access granted! Try saving again.",
                                                    Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });
    }

    public void openDirectoryPicker(String folderPath) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        Uri initialUri = FileSaver.getFolderUriForPath(folderPath);
        if (initialUri != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        directoryPickerLauncher.launch(intent);
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
        if (filePath != null) fileNameText.setText(new File(filePath).getName());
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
                    updateArtworkDimensionsBadge();

                    Map<String, String> normalized = new HashMap<>();
                    if (metadata != null) {
                        for (Map.Entry<String, String> entry : metadata.entrySet()) {
                            normalized.put(entry.getKey().toUpperCase(), entry.getValue());
                        }
                    }

                    this.originalLyricsTagContent = normalized.getOrDefault("LYRICS", "");

                    if (this.originalLyricsTagContent.trim().startsWith("<")) {
                        this.isTtmlMode = true;
                        this.currentTtmlContent = this.originalLyricsTagContent;
                        this.currentElrcContent = "";
                    } else {
                        this.isTtmlMode = false;
                        this.currentElrcContent = this.originalLyricsTagContent;
                        this.currentTtmlContent = "";
                    }

                    updateSwapperUI();
                });
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> saveTags());
        changeArtworkButton.setOnClickListener(v -> selectArtwork());
        resetArtworkButton.setOnClickListener(v -> resetArtwork());

        // --- UPDATED LOGIC: First time apply directly, then show conflict dialog ---
        fetchFromApiButton.setOnClickListener(
                v -> {
                    if (cachedMetadata != null) {
                        if (!hasAppliedMetadata) {
                            // First time? Just apply it!
                            populateFieldsFromCachedData();
                            Toast.makeText(this, "Cached metadata applied!", Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            // Already applied? Ask user what to do.
                            showMetadataConflictDialog();
                        }
                    } else if (intentSongId != null) {
                        // Waiting for data (Lyrics Mode)
                        showLoading(WAITING_MESSAGE);
                        Toast.makeText(this, WAITING_MESSAGE, Toast.LENGTH_LONG).show();
                    } else {
                        // No data, No ID (Manual Mode) -> Search
                        showIdentifySongDialog();
                    }
                });

        restoreTagsButton.setOnClickListener(v -> showRestoreConfirmation());
        addFieldButton.setOnClickListener(
                v ->
                        uiManager.showAddCustomFieldDialog(
                                customFields, this::updateRestoreButtonState));
        extendedTagsHeader.setOnClickListener(v -> toggleExtendedTags());
        lyricsHeader.setOnClickListener(v -> toggleLyrics());
        formatSwapperContainer.setOnClickListener(v -> toggleLyricsMode());

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

        artworkDimensionsContainer.setOnClickListener(v -> showArtworkOptionsDialog());

        titleEditText.addTextChangedListener(changeWatcher);
        artistEditText.addTextChangedListener(changeWatcher);
        albumEditText.addTextChangedListener(changeWatcher);
        albumArtistEditText.addTextChangedListener(changeWatcher);
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
        unsyncedLyricsEditText.addTextChangedListener(changeWatcher);
        lrcEditText.addTextChangedListener(changeWatcher);
        elrcEditText.addTextChangedListener(changeWatcher);
        lyricsMultiEditText.addTextChangedListener(changeWatcher);
    }

    private void updateArtworkDimensionsBadge() {
        if (selectedArtwork != null) {
            artworkDimensionsContainer.setVisibility(View.VISIBLE);
            String dimen = selectedArtwork.getWidth() + " x " + selectedArtwork.getHeight();
            artworkDimensionsText.setText(dimen);
        } else if (originalArtwork != null && !artworkChanged) {
            // Fallback to original if not changed
            artworkDimensionsContainer.setVisibility(View.VISIBLE);
            String dimen = originalArtwork.getWidth() + " x " + originalArtwork.getHeight();
            artworkDimensionsText.setText(dimen);
        } else {
            artworkDimensionsContainer.setVisibility(View.GONE);
        }
    }

    private void showMetadataConflictDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Metadata Available")
                .setMessage("Cached metadata is available. What would you like to do?")
                .setPositiveButton(
                        "Apply Cached Data",
                        (dialog, which) -> {
                            populateFieldsFromCachedData();
                            Toast.makeText(this, "Cached metadata applied!", Toast.LENGTH_SHORT)
                                    .show();
                        })
                .setNegativeButton(
                        "Search Again",
                        (dialog, which) -> {
                            showIdentifySongDialog();
                        })
                .setNeutralButton("Dismiss", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showIdentifySongDialog() {
        String currentTitle = titleEditText.getText().toString();
        String currentArtist = artistEditText.getText().toString();

        MediaStoreHelper.LocalSong tempSong =
                new MediaStoreHelper.LocalSong(
                        null, filePath, currentTitle, currentArtist, "", -1, 0, 0);

        IdentifySongDialog dialog = new IdentifySongDialog(this, tempSong, originalArtwork);
        dialog.setHideManualButton(true);
        dialog.setOnSongSelectedListener(
                song -> {
                    fetchFullMetadataForSong(song);
                });
        dialog.show();
    }

    private void fetchFullMetadataForSong(Song song) {
        showLoading("Fetching details for " + song.getSongName() + "...");

        this.intentTitle = song.getSongName();
        this.intentArtist = song.getArtistName();
        this.intentAlbum = song.getAlbumName();
        this.intentArtworkUrl = song.getArtwork();
        this.intentSongId = song.getId();

        ApiClient.getLyrics(
                song.getId(),
                new ApiClient.LyricsCallback() {
                    @Override
                    public void onSuccess(ApiClient.LyricsResponse lyricsResponse) {
                        cachedMetadata = lyricsResponse;
                        runOnUiThread(
                                () -> {
                                    populateFieldsFromCachedData();
                                    hideLoading();
                                });
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(
                                () -> {
                                    hideLoading();
                                    showErrorDialog(
                                            "Fetch Error", "Could not get details: " + error);
                                });
                    }
                });
    }

    private void toggleLyricsMode() {
        String visibleText = lyricsMultiEditText.getText().toString();
        if (isTtmlMode) {
            currentTtmlContent = visibleText;
        } else {
            currentElrcContent = visibleText;
        }

        isTtmlMode = !isTtmlMode;

        updateSwapperUI();
    }

    private void updateSwapperUI() {
        if (isTtmlMode) {
            labelElrc.setTextColor(Color.parseColor("#80FFFFFF"));
            labelElrc.setTypeface(null, Typeface.NORMAL);
            labelTtml.setTextColor(Color.WHITE);
            labelTtml.setTypeface(null, Typeface.BOLD);

            lyricsMultiEditText.setText(currentTtmlContent);
            ((TextInputLayout) lyricsMultiEditText.getParent().getParent()).setHint("TTML");
        } else {
            labelElrc.setTextColor(Color.WHITE);
            labelElrc.setTypeface(null, Typeface.BOLD);
            labelTtml.setTextColor(Color.parseColor("#80FFFFFF"));
            labelTtml.setTypeface(null, Typeface.NORMAL);

            lyricsMultiEditText.setText(currentElrcContent);
            ((TextInputLayout) lyricsMultiEditText.getParent().getParent())
                    .setHint("ELRC Multi-Person");
        }
        updateRestoreButtonState();
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
        if (originalMetadata == null) {
            restoreTagsButton.setEnabled(false);
            return;
        }

        String currentVisibleText = lyricsMultiEditText.getText().toString();
        // Check for changes in Lyrics
        boolean lyricsChanged = !currentVisibleText.equals(originalLyricsTagContent);

        // Check for changes in Standard Fields
        boolean otherFieldsChanged =
                dataManager.hasUnsavedChanges(
                        originalMetadata,
                        artworkChanged,
                        customFields,
                        titleEditText,
                        artistEditText,
                        albumEditText,
                        albumArtistEditText,
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

        restoreTagsButton.setEnabled(lyricsChanged || otherFieldsChanged);
    }

    private void showRestoreConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore to Original")
                .setMessage("Revert all changes?")
                .setPositiveButton("Restore", (dialog, which) -> restoreOriginalTags())
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void showErrorDialog(String title, String message) {
        runOnUiThread(
                () ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(title)
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show());
    }

    private void restoreOriginalTags() {
        dataManager.restoreOriginalTags(
                originalMetadata,
                customFields,
                extendedTagsContainer,
                tagFieldsContainer,
                titleEditText,
                artistEditText,
                albumEditText,
                albumArtistEditText,
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

        if (originalLyricsTagContent.trim().startsWith("<")) {
            isTtmlMode = true;
            currentTtmlContent = originalLyricsTagContent;
            currentElrcContent = "";
        } else {
            isTtmlMode = false;
            currentElrcContent = originalLyricsTagContent;
            currentTtmlContent = "";
        }

        updateSwapperUI();

        resetArtwork();
        restoreTagsButton.setEnabled(false);
        Toast.makeText(this, "Restored to original", Toast.LENGTH_SHORT).show();
    }

    private void populateFieldsFromCachedData() {
        showLoading("Applying cached metadata...");
        runOnUiThread(
                () -> {
                    // --- NEW: Mark as applied ---
                    hasAppliedMetadata = true;

                    if (titleEditText.getText().toString().isEmpty())
                        titleEditText.setText(intentTitle);
                    if (artistEditText.getText().toString().isEmpty())
                        artistEditText.setText(intentArtist);
                    if (albumEditText.getText().toString().isEmpty() && intentAlbum != null)
                        albumEditText.setText(intentAlbum);

                    if (cachedMetadata != null) {
                        if (cachedMetadata.genreNames != null
                                && !cachedMetadata.genreNames.isEmpty())
                            genreEditText.setText(String.join(", ", cachedMetadata.genreNames));
                        else if (cachedMetadata.genre != null)
                            genreEditText.setText(cachedMetadata.genre);

                        if (cachedMetadata.audioLocale != null)
                            audioLocaleEditText.setText(cachedMetadata.audioLocale);
                        if (cachedMetadata.releaseDate != null)
                            releaseDateEditText.setText(cachedMetadata.releaseDate);
                        if (cachedMetadata.trackNumber != null)
                            trackNumberEditText.setText(cachedMetadata.trackNumber);
                        if (cachedMetadata.discNumber != null)
                            discNumberEditText.setText(cachedMetadata.discNumber);
                        if (cachedMetadata.composerName != null)
                            composerEditText.setText(cachedMetadata.composerName);
                        if (cachedMetadata.songwriters != null)
                            songwriterEditText.setText(
                                    String.join(", ", cachedMetadata.songwriters));

                        if (cachedMetadata.contentRating != null)
                            uiManager.addOrUpdateCustomField(
                                    "CONTENTRATING",
                                    cachedMetadata.contentRating,
                                    customFields,
                                    extendedTagsContainer,
                                    this::updateRestoreButtonState);
                        if (cachedMetadata.isrc != null)
                            uiManager.addOrUpdateCustomField(
                                    "ISRC",
                                    cachedMetadata.isrc,
                                    customFields,
                                    extendedTagsContainer,
                                    this::updateRestoreButtonState);

                        if (cachedMetadata.plain != null)
                            unsyncedLyricsEditText.setText(cachedMetadata.plain);
                        if (cachedMetadata.lrc != null) lrcEditText.setText(cachedMetadata.lrc);
                        if (cachedMetadata.elrc != null) elrcEditText.setText(cachedMetadata.elrc);

                        if (cachedMetadata.elrcMultiPerson != null)
                            currentElrcContent = cachedMetadata.elrcMultiPerson;
                        if (cachedMetadata.ttml != null) currentTtmlContent = cachedMetadata.ttml;

                        updateSwapperUI();
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

                                updateArtworkDimensionsBadge();
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
            updateArtworkDimensionsBadge();
        } catch (Exception e) {
        }
    }

    private void resetArtwork() {
        if (originalArtwork != null) artworkImageView.setImageBitmap(originalArtwork);
        else artworkImageView.setImageResource(R.drawable.ic_music_note);
        selectedArtwork = null;
        artworkChanged = false;
        resetArtworkButton.setEnabled(false);
        updateArtworkDimensionsBadge();
        updateRestoreButtonState();
    }

    private void saveTags() {
        String visible = lyricsMultiEditText.getText().toString();
        if (isTtmlMode) currentTtmlContent = visible;
        else currentElrcContent = visible;

        dataManager.saveTags(
                filePath,
                customFields,
                artworkChanged,
                selectedArtwork,
                originalMetadata,
                this::showLoading,
                this::hideLoading,
                titleEditText,
                artistEditText,
                albumEditText,
                albumArtistEditText,
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
        updateRestoreButtonState();
        if (restoreTagsButton.isEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Discard?")
                    .setPositiveButton("Discard", (d, w) -> super.onBackPressed())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else super.onBackPressed();
    }

    public ImageView getArtworkImageView() {
        return artworkImageView;
    }

    public LinearLayout getExtendedTagsContainer() {
        return extendedTagsContainer;
    }

    public LinearLayout getTagFieldsContainer() {
        return tagFieldsContainer;
    }

    public TextView getLoadingText() {
        return loadingText;
    }

    public List<CustomField> getCustomFields() {
        return customFields;
    }

    static class CustomField {
        String tag;
        String value;
        TextInputEditText editText;
        TextInputLayout layout;
    }

    private void showArtworkOptionsDialog() {
        if (selectedArtwork == null && originalArtwork == null) return;

        // Current Bitmap and Dimensions
        final Bitmap currentBmp = artworkChanged ? selectedArtwork : originalArtwork;
        final int currentW = currentBmp.getWidth();
        final int currentH = currentBmp.getHeight();
        final float aspectRatio = (float) currentW / currentH;

        // --- Layout Setup ---
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 40, 50, 40);

        // Header
        TextView header = new TextView(this);
        header.setText("Artwork Options");
        header.setTextSize(20);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        mainLayout.addView(header);

        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(1, 40));
        mainLayout.addView(spacer1);

        // --- RESIZE SECTION ---
        LinearLayout resizeContainer = new LinearLayout(this);
        resizeContainer.setOrientation(LinearLayout.HORIZONTAL);
        resizeContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        resizeContainer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        // Helper to create the Box Background
        android.graphics.drawable.GradientDrawable boxBackground =
                new android.graphics.drawable.GradientDrawable();
        boxBackground.setColor(Color.parseColor("#2D2D2D"));
        boxBackground.setCornerRadius(16f);

        // Width Input
        final EditText widthInput = new EditText(this);
        widthInput.setHint("W");
        widthInput.setHintTextColor(Color.GRAY);
        widthInput.setTextColor(Color.WHITE);
        widthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        widthInput.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        widthInput.setGravity(android.view.Gravity.CENTER);
        widthInput.setBackground(boxBackground);
        widthInput.setPadding(0, 24, 0, 24);
        widthInput.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(4)});
        resizeContainer.addView(widthInput);

        // "x" separator
        TextView xLabel = new TextView(this);
        xLabel.setText("   x   ");
        xLabel.setTextColor(Color.GRAY);
        xLabel.setTextSize(16);
        resizeContainer.addView(xLabel);

        // Height Input (New drawable instance)
        android.graphics.drawable.GradientDrawable boxBackground2 =
                new android.graphics.drawable.GradientDrawable();
        boxBackground2.setColor(Color.parseColor("#2D2D2D"));
        boxBackground2.setCornerRadius(16f);

        final EditText heightInput = new EditText(this);
        heightInput.setHint("H");
        heightInput.setHintTextColor(Color.GRAY);
        heightInput.setTextColor(Color.WHITE);
        heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        heightInput.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heightInput.setGravity(android.view.Gravity.CENTER);
        heightInput.setBackground(boxBackground2);
        heightInput.setPadding(0, 24, 0, 24);
        heightInput.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(4)});
        resizeContainer.addView(heightInput);

        mainLayout.addView(resizeContainer);

        // Check availability
        boolean canResize =
                intentArtworkUrl != null
                        && (intentArtworkUrl.contains("{w}") || intentArtworkUrl.contains("{h}"));

        if (!canResize) {
            widthInput.setText("N/A");
            heightInput.setText("N/A");
            widthInput.setEnabled(false);
            heightInput.setEnabled(false);
            widthInput.setAlpha(0.5f);
            heightInput.setAlpha(0.5f);

            TextView note = new TextView(this);
            note.setText("(Resize unavailable for local files)");
            note.setTextColor(Color.GRAY);
            note.setTextSize(12);
            note.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            note.setPadding(0, 16, 0, 0);
            mainLayout.addView(note);
        }

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(1, 50));
        mainLayout.addView(spacer2);

        // --- SAVE BUTTON ---
        MaterialButton saveToStorageBtn = new MaterialButton(this);
        saveToStorageBtn.setText("Save Artwork to Gallery");
        saveToStorageBtn.setIconResource(R.drawable.ic_save);
        mainLayout.addView(saveToStorageBtn);

        // Build Dialog
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this)
                        .setView(mainLayout)
                        .setNeutralButton("Cancel", null);

        if (canResize) {
            builder.setPositiveButton(
                    "Apply",
                    (d, w) -> {
                        String wStr = widthInput.getText().toString();
                        String hStr = heightInput.getText().toString();
                        if (!wStr.isEmpty() && !hStr.isEmpty()) {
                            downloadResizedArtwork(wStr, hStr);
                        }
                    });
        }

        // CREATE DIALOG (Needed before setting listeners so we can reference buttons)
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // --- VALIDATION HELPER ---
                // --- VALIDATION HELPER ---
        Runnable checkValidation = () -> {
            String w = widthInput.getText().toString().trim();
            String h = heightInput.getText().toString().trim();

            // FIX: If text is "N/A", we just want to enable Save (if image exists) but disable Apply
            if (w.equals("N/A") || h.equals("N/A")) {
                saveToStorageBtn.setEnabled(true);
                saveToStorageBtn.setAlpha(1.0f);
                
                Button applyBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (applyBtn != null) {
                    applyBtn.setEnabled(false); // Cannot resize N/A
                    applyBtn.setAlpha(0.5f);
                }
                return;
            }

            // Standard Validation for numbers
            boolean isInvalid = w.isEmpty() || h.isEmpty() || 
                                w.equals("0") || h.equals("0");
            
            if (!isInvalid) {
                try {
                    // Safe parsing
                    if (Integer.parseInt(w) == 0 || Integer.parseInt(h) == 0) {
                        isInvalid = true;
                    }
                } catch (NumberFormatException e) {
                    isInvalid = true;
                }
            }

            // 1. Save Button State
            saveToStorageBtn.setEnabled(!isInvalid);
            saveToStorageBtn.setAlpha(isInvalid ? 0.5f : 1.0f);

            // 2. Apply Button State (Positive Button)
            Button applyBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (applyBtn != null) {
                applyBtn.setEnabled(!isInvalid);
                applyBtn.setAlpha(isInvalid ? 0.5f : 1.0f);
            }
        };


        // --- TEXT WATCHERS ---
        if (canResize) {
            widthInput.setText(String.valueOf(currentW));
            heightInput.setText(String.valueOf(currentH));

            // Width Watcher
            widthInput.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(
                                CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(
                                CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {
                            // 1. Run validation visual check
                            checkValidation.run();

                            if (isAutoUpdating) return;

                            if (s.length() == 0) {
                                isAutoUpdating = true;
                                heightInput.setText("");
                                isAutoUpdating = false;
                                return;
                            }

                            try {
                                int w = Integer.parseInt(s.toString());
                                int h = Math.round(w / aspectRatio);
                                isAutoUpdating = true;
                                heightInput.setText(String.valueOf(h));
                                isAutoUpdating = false;

                                // Re-run validation after auto-update
                                checkValidation.run();
                            } catch (NumberFormatException e) {
                            }
                        }
                    });

            // Height Watcher
            heightInput.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(
                                CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(
                                CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {
                            checkValidation.run();

                            if (isAutoUpdating) return;

                            if (s.length() == 0) {
                                isAutoUpdating = true;
                                widthInput.setText("");
                                isAutoUpdating = false;
                                return;
                            }

                            try {
                                int h = Integer.parseInt(s.toString());
                                int w = Math.round(h * aspectRatio);
                                isAutoUpdating = true;
                                widthInput.setText(String.valueOf(w));
                                isAutoUpdating = false;
                                checkValidation.run();
                            } catch (NumberFormatException e) {
                            }
                        }
                    });
        }

        // --- SAVE LOGIC ---
        saveToStorageBtn.setOnClickListener(
                v -> {
                    String wStr = widthInput.getText().toString();
                    String hStr = heightInput.getText().toString();

                    // Double check safety
                    if (wStr.isEmpty() || hStr.isEmpty() || wStr.equals("0") || hStr.equals("0"))
                        return;

                    if (!canResize) {
                        saveBitmapToStorage(currentBmp);
                        dialog.dismiss();
                        return;
                    }

                    int inputW = Integer.parseInt(wStr);
                    int inputH = Integer.parseInt(hStr);

                    if (inputW == currentW && inputH == currentH) {
                        saveBitmapToStorage(currentBmp);
                        dialog.dismiss();
                    } else {
                        fetchAndSaveArtwork(inputW, inputH, dialog);
                    }
                });

        // Show dialog first...
        dialog.show();

        // ...then trigger initial validation state (e.g. if image loaded is valid/invalid)
        checkValidation.run();
    }

    // ACTION 1: Apply (Fetch & Update UI only)
    private void downloadResizedArtwork(String w, String h) {
        if (intentArtworkUrl == null) return;

        showLoading("Downloading " + w + "x" + h + "...");

        String newUrl = intentArtworkUrl.replace("{w}", w).replace("{h}", h).replace("{f}", "jpg");

        // loadArtworkWithGlide updates the UI and 'selectedArtwork'
        loadArtworkWithGlide(newUrl);
    }

    // ACTION 2: Save with Changes (Fetch -> Save -> Update UI)
    private void fetchAndSaveArtwork(int w, int h, androidx.appcompat.app.AlertDialog dialog) {
        if (intentArtworkUrl == null) return;

        dialog.setCancelable(false);
        showLoading("Fetching & Saving " + w + "x" + h + "...");

        String newUrl =
                intentArtworkUrl
                        .replace("{w}", String.valueOf(w))
                        .replace("{h}", String.valueOf(h))
                        .replace("{f}", "jpg");

        Glide.with(this)
                .asBitmap()
                .load(newUrl)
                .into(
                        new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(
                                    Bitmap resource,
                                    com.bumptech.glide.request.transition.Transition<? super Bitmap>
                                            t) {
                                // 1. Save
                                saveBitmapToStorage(resource);

                                // 2. Update UI
                                selectedArtwork = resource;
                                artworkImageView.setImageBitmap(resource);
                                artworkChanged = true;
                                resetArtworkButton.setEnabled(true);
                                updateArtworkDimensionsBadge();
                                updateRestoreButtonState();

                                hideLoading();
                                dialog.dismiss();
                            }

                            @Override
                            public void onLoadCleared(android.graphics.drawable.Drawable p) {}

                            @Override
                            public void onLoadFailed(
                                    android.graphics.drawable.Drawable errorDrawable) {
                                hideLoading();
                                Toast.makeText(
                                                TagEditorActivity.this,
                                                "Failed to download",
                                                Toast.LENGTH_SHORT)
                                        .show();
                                dialog.setCancelable(true);
                            }
                        });
    }

    // ACTION 3: Simple Save
    private void saveBitmapToStorage(Bitmap bitmapToSave) {
        if (bitmapToSave == null) return;

        try {
            String fileName = "Cover_" + System.currentTimeMillis() + ".jpg";

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Lyricify");

            Uri uri =
                    getContentResolver()
                            .insert(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    values);

            if (uri != null) {
                java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 100, out);
                if (out != null) out.close();
                Toast.makeText(this, "Saved to Pictures/Lyricify", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void hideSystemUI() {
        getWindow()
                .setFlags(
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }
}
