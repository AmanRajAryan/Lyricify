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
    private TextView fileNameText;
    private TextInputEditText titleEditText, artistEditText, albumEditText, albumArtistEditText;
    private TextInputEditText genreEditText, yearEditText, releaseDateEditText;
    private TextInputEditText trackNumberEditText, discNumberEditText;
    private TextInputEditText audioLocaleEditText, languageEditText;
    private TextInputEditText composerEditText, songwriterEditText;
    private TextInputEditText commentEditText;

    // Lyrics Fields
    private TextInputEditText unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText; 

    // SWAPPER UI
    private LinearLayout formatSwapperContainer;
    private TextView labelElrc, labelTtml;
    private LinearLayout lyricsHeader, lyricsContainer;
    private ImageView lyricsArrow;
    private boolean isLyricsVisible = false;

    private MaterialToolbar toolbar;
    private MaterialButton changeArtworkButton, resetArtworkButton, fetchFromApiButton, restoreTagsButton;
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
        runOnUiThread(() -> {
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
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveTags();
                return true;
            }
            return false;
        });
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) loadArtworkFromUri(imageUri);
            }
        });
    }

    private void setupDirectoryPickerLauncher() {
        directoryPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri treeUri = result.getData().getData();
                if (treeUri != null) {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    Toast.makeText(this, "✓ Access granted! Try saving again.", Toast.LENGTH_LONG).show();
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
            cachedMetadata = (ApiClient.LyricsResponse) getIntent().getSerializableExtra("CACHED_METADATA");
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
        fetchFromApiButton.setOnClickListener(v -> {
            if (cachedMetadata != null) {
                if (!hasAppliedMetadata) {
                    // First time? Just apply it!
                    populateFieldsFromCachedData();
                    Toast.makeText(this, "Cached metadata applied!", Toast.LENGTH_SHORT).show();
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
        addFieldButton.setOnClickListener(v -> uiManager.showAddCustomFieldDialog(customFields, this::updateRestoreButtonState));
        extendedTagsHeader.setOnClickListener(v -> toggleExtendedTags());
        lyricsHeader.setOnClickListener(v -> toggleLyrics());
        formatSwapperContainer.setOnClickListener(v -> toggleLyricsMode());

        TextWatcher changeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateRestoreButtonState(); }
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

    private void showMetadataConflictDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Metadata Available")
                .setMessage("Cached metadata is available. What would you like to do?")
                .setPositiveButton("Apply Cached", (dialog, which) -> {
                    populateFieldsFromCachedData();
                    Toast.makeText(this, "Cached metadata applied!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Search Again", (dialog, which) -> {
                    showIdentifySongDialog();
                })
                .setNeutralButton("Dismiss", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showIdentifySongDialog() {
        String currentTitle = titleEditText.getText().toString();
        String currentArtist = artistEditText.getText().toString();
        
        MediaStoreHelper.LocalSong tempSong = new MediaStoreHelper.LocalSong(
                null, filePath, currentTitle, currentArtist, "", -1, 0, 0
        );
        
        IdentifySongDialog dialog = new IdentifySongDialog(this, tempSong, originalArtwork);
        dialog.setHideManualButton(true); 
        dialog.setOnSongSelectedListener(song -> {
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
        
        ApiClient.getLyrics(song.getId(), new ApiClient.LyricsCallback() {
            @Override
            public void onSuccess(ApiClient.LyricsResponse lyricsResponse) {
                cachedMetadata = lyricsResponse;
                runOnUiThread(() -> {
                    populateFieldsFromCachedData();
                    hideLoading();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    hideLoading();
                    showErrorDialog("Fetch Error", "Could not get details: " + error);
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
            ((TextInputLayout) lyricsMultiEditText.getParent().getParent()).setHint("ELRC Multi-Person");
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
        boolean otherFieldsChanged = dataManager.hasUnsavedChanges(
                originalMetadata, artworkChanged, customFields,
                titleEditText, artistEditText, albumEditText, albumArtistEditText,
                genreEditText, yearEditText, trackNumberEditText, discNumberEditText,
                composerEditText, songwriterEditText, commentEditText, releaseDateEditText,
                audioLocaleEditText, languageEditText,
                unsyncedLyricsEditText, lrcEditText, elrcEditText, 
                lyricsMultiEditText 
        );

        restoreTagsButton.setEnabled(lyricsChanged || otherFieldsChanged);
    }

    private void showRestoreConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore to Original")
                .setMessage("Revert all changes?")
                .setPositiveButton("Restore", (dialog, which) -> restoreOriginalTags())
                .setNegativeButton("Cancel", null).show();
    }

    public void showErrorDialog(String title, String message) {
        runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                .setTitle(title).setMessage(message).setPositiveButton("OK", null).show());
    }

    private void restoreOriginalTags() {
        dataManager.restoreOriginalTags(
                originalMetadata, customFields, extendedTagsContainer, tagFieldsContainer,
                titleEditText, artistEditText, albumEditText, albumArtistEditText,
                genreEditText, yearEditText, trackNumberEditText, discNumberEditText,
                composerEditText, songwriterEditText, commentEditText, releaseDateEditText,
                audioLocaleEditText, languageEditText,
                unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText);

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
        runOnUiThread(() -> {
            // --- NEW: Mark as applied ---
            hasAppliedMetadata = true;

            if (titleEditText.getText().toString().isEmpty()) titleEditText.setText(intentTitle);
            if (artistEditText.getText().toString().isEmpty()) artistEditText.setText(intentArtist);
            if (albumEditText.getText().toString().isEmpty() && intentAlbum != null) albumEditText.setText(intentAlbum);
            
            if (cachedMetadata != null) {
                if (cachedMetadata.genreNames != null && !cachedMetadata.genreNames.isEmpty()) genreEditText.setText(String.join(", ", cachedMetadata.genreNames));
                else if (cachedMetadata.genre != null) genreEditText.setText(cachedMetadata.genre);
                
                if (cachedMetadata.audioLocale != null) audioLocaleEditText.setText(cachedMetadata.audioLocale);
                if (cachedMetadata.releaseDate != null) releaseDateEditText.setText(cachedMetadata.releaseDate);
                if (cachedMetadata.trackNumber != null) trackNumberEditText.setText(cachedMetadata.trackNumber);
                if (cachedMetadata.discNumber != null) discNumberEditText.setText(cachedMetadata.discNumber);
                if (cachedMetadata.composerName != null) composerEditText.setText(cachedMetadata.composerName);
                if (cachedMetadata.songwriters != null) songwriterEditText.setText(String.join(", ", cachedMetadata.songwriters));
                
                if (cachedMetadata.contentRating != null) uiManager.addOrUpdateCustomField("CONTENTRATING", cachedMetadata.contentRating, customFields, extendedTagsContainer, this::updateRestoreButtonState);
                if (cachedMetadata.isrc != null) uiManager.addOrUpdateCustomField("ISRC", cachedMetadata.isrc, customFields, extendedTagsContainer, this::updateRestoreButtonState);

                if (cachedMetadata.plain != null) unsyncedLyricsEditText.setText(cachedMetadata.plain);
                if (cachedMetadata.lrc != null) lrcEditText.setText(cachedMetadata.lrc);
                if (cachedMetadata.elrc != null) elrcEditText.setText(cachedMetadata.elrc);

                if (cachedMetadata.elrcMultiPerson != null) currentElrcContent = cachedMetadata.elrcMultiPerson;
                if (cachedMetadata.ttml != null) currentTtmlContent = cachedMetadata.ttml;

                updateSwapperUI();
            }

            if (intentArtworkUrl != null) {
                String artworkUrl = intentArtworkUrl.replace("{w}", "600").replace("{h}", "600").replace("{f}", "jpg");
                loadArtworkWithGlide(artworkUrl);
            } else {
                hideLoading();
                Toast.makeText(this, "Metadata applied!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadArtworkWithGlide(String url) {
        Glide.with(this).asBitmap().load(url).into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            @Override public void onResourceReady(Bitmap resource, com.bumptech.glide.request.transition.Transition<? super Bitmap> t) {
                selectedArtwork = resource;
                artworkImageView.setImageBitmap(resource);
                artworkChanged = true;
                resetArtworkButton.setEnabled(true);
                updateRestoreButtonState();
                hideLoading();
                Toast.makeText(TagEditorActivity.this, "Metadata fetched!", Toast.LENGTH_SHORT).show();
            }
            @Override public void onLoadCleared(android.graphics.drawable.Drawable p) { hideLoading(); }
        });
    }

    private void selectArtwork() { imagePickerLauncher.launch(new Intent(Intent.ACTION_PICK).setType("image/*")); }
    
    private void loadArtworkFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            selectedArtwork = BitmapFactory.decodeStream(is);
            artworkImageView.setImageBitmap(selectedArtwork);
            artworkChanged = true;
            resetArtworkButton.setEnabled(true);
            updateRestoreButtonState();
            is.close();
        } catch (Exception e) {}
    }

    private void resetArtwork() {
        if (originalArtwork != null) artworkImageView.setImageBitmap(originalArtwork);
        else artworkImageView.setImageResource(R.drawable.ic_music_note);
        selectedArtwork = null;
        artworkChanged = false;
        resetArtworkButton.setEnabled(false);
        updateRestoreButtonState();
    }

    private void saveTags() {
        String visible = lyricsMultiEditText.getText().toString();
        if (isTtmlMode) currentTtmlContent = visible;
        else currentElrcContent = visible;
        
        dataManager.saveTags(
                filePath, customFields, artworkChanged, selectedArtwork, originalMetadata,
                this::showLoading, this::hideLoading,
                titleEditText, artistEditText, albumEditText, albumArtistEditText,
                genreEditText, yearEditText, trackNumberEditText, discNumberEditText,
                composerEditText, songwriterEditText, commentEditText, releaseDateEditText,
                audioLocaleEditText, languageEditText,
                unsyncedLyricsEditText, lrcEditText, elrcEditText, lyricsMultiEditText
        );
    }

    void showLoading(String m) {
        runOnUiThread(() -> {
            loadingText.setText(m);
            loadingOverlay.setVisibility(View.VISIBLE);
        });
    }

    void hideLoading() { runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE)); }

    @Override
    public void onBackPressed() {
        updateRestoreButtonState(); 
        if (restoreTagsButton.isEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Discard?")
                    .setPositiveButton("Discard", (d, w) -> super.onBackPressed())
                    .setNegativeButton("Cancel", null).show();
        } else super.onBackPressed();
    }
    
    public ImageView getArtworkImageView() { return artworkImageView; }
    public LinearLayout getExtendedTagsContainer() { return extendedTagsContainer; }
    public LinearLayout getTagFieldsContainer() { return tagFieldsContainer; }
    public TextView getLoadingText() { return loadingText; }
    public List<CustomField> getCustomFields() { return customFields; }
    static class CustomField { String tag; String value; TextInputEditText editText; TextInputLayout layout; }
    
    private void hideSystemUI() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }
}