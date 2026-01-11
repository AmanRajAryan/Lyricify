package aman.lyricify;

import android.content.Intent;
import android.graphics.Bitmap;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import aman.taglib.TagLib;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagEditorActivity extends AppCompatActivity implements ApiClient.CacheListener {

    // UI Components
    ImageView artworkImageView;
    LinearLayout artworkDimensionsContainer;
    TextView artworkDimensionsText;
    TextView fileNameText;
    TextInputEditText titleEditText, artistEditText, albumEditText, albumArtistEditText;
    TextInputEditText genreEditText, yearEditText, releaseDateEditText;
    TextInputEditText trackNumberEditText, discNumberEditText;
    TextInputEditText audioLocaleEditText, languageEditText;
    TextInputEditText composerEditText, songwriterEditText;
    TextInputEditText commentEditText;
    TextInputEditText unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText;
    LinearLayout formatSwapperContainer;
    TextView labelElrc, labelTtml;
    LinearLayout lyricsHeader, lyricsContainer;
    ImageView lyricsArrow;
    LinearLayout tagFieldsContainer, extendedTagsHeader, extendedTagsContainer;
    ImageView extendedTagsArrow;
    FrameLayout loadingOverlay;
    TextView loadingText;

    private boolean isLyricsVisible = false;
    private boolean isExtendedTagsVisible = false;

    private MaterialToolbar toolbar;
    private MaterialButton changeArtworkButton,
            resetArtworkButton,
            fetchFromApiButton,
            restoreTagsButton;
    private ExtendedFloatingActionButton saveButton, addFieldButton;

    // Logic & Data vars
    String filePath;
    private TagLib tagLib;
    private HashMap<String, String> originalMetadata;

    private List<CustomField> customFields = new ArrayList<>();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;
    private ActivityResultLauncher<Intent> companionAppLauncher; // NEW: Launcher for Companion App

    private TagEditorUIManager uiManager;
    private TagEditorDataManager dataManager;

    // Helpers
    private TagEditorArtworkHelper artworkHelper;
    private TagEditorLyricsHelper lyricsHelper;
    private TagEditorMetadataHelper metadataHelper;

    private static final String WAITING_MESSAGE = "No cached metadata available, waiting!";

    public static class CustomField {
        public String tag;
        public String value;
        public TextInputEditText editText;
        public TextInputLayout layout;
    }

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

        artworkHelper =
                new TagEditorArtworkHelper(
                        this,
                        artworkImageView,
                        artworkDimensionsContainer,
                        artworkDimensionsText,
                        resetArtworkButton,
                        this::updateRestoreButtonState);
        lyricsHelper =
                new TagEditorLyricsHelper(
                        lyricsMultiEditText, labelElrc, labelTtml, this::updateRestoreButtonState);
        metadataHelper =
                new TagEditorMetadataHelper(
                        this,
                        uiManager,
                        artworkHelper,
                        lyricsHelper,
                        this::updateRestoreButtonState);

        setupToolbar();
        setupImagePickerLauncher();
        setupDirectoryPickerLauncher();
        setupCompanionAppLauncher(); // NEW
        extractIntentData();
        loadCurrentTags();
        setupListeners();

        if (metadataHelper.getIntentSongId() != null
                || metadataHelper.getCachedMetadata() != null) {
            ApiClient.registerCacheListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiClient.unregisterCacheListener(this);

        // 1. Clear Memory Cache
        motionCache = null;

        // 2. Clear File Cache (Delete temporary preview videos)
        new Thread(
                        () -> {
                            try {
                                File cacheDir = new File(getCacheDir(), "motion_cache");
                                if (cacheDir.exists()) {
                                    File[] files = cacheDir.listFiles();
                                    if (files != null) {
                                        for (File f : files) f.delete();
                                    }
                                    cacheDir.delete();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                .start();
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
                    metadataHelper.setCachedMetadata(response);
                    if (loadingOverlay.getVisibility() == View.VISIBLE
                            && loadingText.getText().toString().equals(WAITING_MESSAGE)) {
                        metadataHelper.populateFieldsFromCachedData();
                        if (metadataHelper.getIntentArtworkUrl() == null) hideLoading();
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
                                Uri uri = result.getData().getData();
                                // CHECK MIME TYPE
                                String mime = getContentResolver().getType(uri);
                                if (mime != null && mime.startsWith("video/")) {
                                    launchCompanionApp(uri); // Video -> Companion App
                                } else {
                                    artworkHelper.handleImageResult(uri); // Image -> Normal flow
                                }
                            }
                        });
    }

    // NEW: Launcher for receiving the converted image from Companion App
    private void setupCompanionAppLauncher() {
        companionAppLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                Uri convertedImageUri = result.getData().getData();
                                if (convertedImageUri != null) {
                                    artworkHelper.handleImageResult(convertedImageUri);
                                    Toast.makeText(
                                                    this,
                                                    "Video converted successfully!",
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            }
                        });
    }

    public void launchCompanionApp(Uri videoUri) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_STREAM, videoUri);

            // Pass the mode flag as requested
            intent.putExtra("mode_lyricify", true);

            // Target the specific standalone app package
            intent.setPackage("aman.lyricifycompanion");

            companionAppLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Lyricify Companion App not installed!", Toast.LENGTH_LONG).show();
        }
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
        ApiClient.LyricsResponse cached = null;
        if (getIntent().hasExtra("CACHED_METADATA")) {
            cached = (ApiClient.LyricsResponse) getIntent().getSerializableExtra("CACHED_METADATA");
        }
        metadataHelper.setIntentData(
                getIntent().getStringExtra("SONG_TITLE"),
                getIntent().getStringExtra("SONG_ARTIST"),
                getIntent().getStringExtra("SONG_ALBUM"),
                getIntent().getStringExtra("ARTWORK_URL"),
                getIntent().getStringExtra("SONG_ID"),
                cached);
        if (filePath != null) fileNameText.setText(new File(filePath).getName());
    }

    // In TagEditorActivity.java inside loadCurrentTags() call

    private void loadCurrentTags() {
        dataManager.loadCurrentTags(
                filePath,
                metadataHelper.getIntentArtworkUrl(),
                this::showLoading,
                this::hideLoading,
                this::updateRestoreButtonState,
                // UPDATED CALLBACK:
                (metadata, artworkBitmap, rawBytes, mimeType) -> {
                    this.originalMetadata = metadata;

                    // NEW: Pass raw bytes directly!
                    if (rawBytes != null && rawBytes.length > 0) {
                        artworkHelper.setOriginalArtwork(
                                artworkBitmap,
                                rawBytes, // The real animated bytes (GIF/WebP)
                                mimeType // The real mime type
                                );
                    } else {
                        // Fallback for no artwork
                        artworkHelper.setOriginalArtwork(null, null, null);
                    }

                    Map<String, String> normalized = new HashMap<>();
                    if (metadata != null) {
                        for (Map.Entry<String, String> entry : metadata.entrySet())
                            normalized.put(entry.getKey().toUpperCase(), entry.getValue());
                    }
                    lyricsHelper.setOriginalLyrics(normalized.getOrDefault("LYRICS", ""));
                });
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> saveTags());

        // CHANGED: Allow picking Video OR Image
        // In TagEditorActivity.java inside setupListeners()

        changeArtworkButton.setOnClickListener(
                v -> {
                    // FIX: Use ACTION_GET_CONTENT to open the Media/Gallery picker
                    // instead of ACTION_OPEN_DOCUMENT (which forces the File Manager).
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
                    imagePickerLauncher.launch(Intent.createChooser(intent, "Select Artwork"));
                });

        resetArtworkButton.setOnClickListener(v -> artworkHelper.resetArtwork());

        fetchFromApiButton.setOnClickListener(
                v -> {
                    if (metadataHelper.getCachedMetadata() != null) {
                        if (!metadataHelper.hasAppliedMetadata()) {
                            metadataHelper.populateFieldsFromCachedData();
                            Toast.makeText(this, "Cached metadata applied!", Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            metadataHelper.showMetadataConflictDialog();
                        }
                    } else if (metadataHelper.getIntentSongId() != null) {
                        showLoading(WAITING_MESSAGE);
                        Toast.makeText(this, WAITING_MESSAGE, Toast.LENGTH_LONG).show();
                    } else {
                        metadataHelper.showIdentifySongDialog();
                    }
                });

        restoreTagsButton.setOnClickListener(v -> showRestoreConfirmation());
        addFieldButton.setOnClickListener(
                v ->
                        uiManager.showAddCustomFieldDialog(
                                customFields, this::updateRestoreButtonState));
        extendedTagsHeader.setOnClickListener(v -> toggleExtendedTags());
        lyricsHeader.setOnClickListener(v -> toggleLyrics());
        formatSwapperContainer.setOnClickListener(v -> lyricsHelper.toggleLyricsMode());
        // In TagEditorActivity.java inside setupListeners()
        artworkDimensionsContainer.setOnClickListener(
                v -> {
                    // OLD: artworkHelper.showArtworkOptionsDialog(...)

                    // NEW:
                    String trackUrl = null;
                    if (metadataHelper.getCachedMetadata() != null) {
                        trackUrl =
                                metadataHelper.getCachedMetadata()
                                        .trackUrl; // Ensure you added this field to LyricsResponse!
                    }

                    ArtworkBottomSheetFragment bottomSheet =
                            ArtworkBottomSheetFragment.newInstance(
                                    trackUrl, metadataHelper.getIntentArtworkUrl());
                    bottomSheet.show(getSupportFragmentManager(), "ArtworkSheet");
                });
        TextWatcher changeWatcher =
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    public void afterTextChanged(Editable s) {
                        updateRestoreButtonState();
                    }
                };

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
        boolean lyricsChanged =
                !currentVisibleText.equals(lyricsHelper.getOriginalLyricsTagContent());

        boolean otherFieldsChanged =
                dataManager.hasUnsavedChanges(
                        originalMetadata,
                        artworkHelper.isArtworkChanged(),
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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

        lyricsHelper.setOriginalLyrics(lyricsHelper.getOriginalLyricsTagContent());
        artworkHelper.resetArtwork();
        restoreTagsButton.setEnabled(false);
        Toast.makeText(this, "Restored to original", Toast.LENGTH_SHORT).show();
    }

    private void saveTags() {
        lyricsHelper.updateCurrentContentFromView();

        dataManager.saveTagsWithArtworkBytes(
                filePath,
                customFields,
                artworkHelper.isArtworkChanged(),
                artworkHelper.getSelectedArtwork(),
                artworkHelper.getSelectedArtworkBytes(),
                artworkHelper.getSelectedArtworkMimeType(),
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

    public TagEditorArtworkHelper getTagEditorArtworkHelper() {
        return this.artworkHelper;
    }

    private List<MotionRepository.MotionOption> motionCache = null;

    public List<MotionRepository.MotionOption> getMotionCache() {
        return motionCache;
    }

    public void setMotionCache(List<MotionRepository.MotionOption> cache) {
        this.motionCache = cache;
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
