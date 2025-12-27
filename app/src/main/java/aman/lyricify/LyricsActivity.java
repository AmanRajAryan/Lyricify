package aman.lyricify;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import aman.youly.LyricsSharedEngine;

public class LyricsActivity extends AppCompatActivity {

    // UI Components
    private ImageView songArtwork;
    private ImageView immersiveBackground;
    private TextView songTitle;
    private TextView songArtist;
    private TextView songFilePath;
    private TextView lyricsTextView;
    private HorizontalScrollView lyricsHorizontalScrollView;
    private ProgressBar lyricsLoading;

    // Control Panel Components
    private LinearLayout formatSelectorButton;
    private TextView currentFormatText;
    private ImageView formatDropdownArrow;
    private TextView hasElrcIndicator;
    private View statusDot;

    // Buttons
    private ImageButton showMetadataButton, saveLrcButton, copyLyricsButton;
    private ImageButton embedLyricsButton, syncedLyricsButton;
    private ExtendedFloatingActionButton editTagsButton;

    // Data
    private String songId;
    private String title;
    private String artist;
    private String artworkUrl;
    private String filePath;
    private String currentFormat = "Plain";
    private List<String> availableFormats = new ArrayList<>();

    // Cached Data
    private ApiClient.LyricsResponse currentLyricsResponse;

    // Managers
    private SongInfoDisplay songInfoDisplay;
    private LyricsFetcher lyricsFetcher;
    private MetadataManager metadataManager;
    private EmbeddingManager embeddingManager;

    private AlertDialog progressDialog;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics);

        // 1. WARM UP THE ENGINE & PREPARE FOR BACKGROUND SEARCH
        LyricsSharedEngine.getInstance(this);

        setupDirectoryPickerLauncher();
        initializeViews();
        initializeManagers();
        extractIntentData();

        displaySongInfo();

        // Initial state
        availableFormats.add("Plain");
        updateSelectorState(false);

        fetchLyrics();
        setupButtonListeners();

        // 2. TRIGGER BACKGROUND SEARCH (Phase 1)
        triggerBackgroundSearch();
    }

    /**
     * SPLIT ARCHITECTURE: PHASE 1 Silently triggers the JS engine to search for this song. The
     * result is cached in memory, ready for SyncedLyricsActivity.
     */
    private void triggerBackgroundSearch() {
        if (title == null || title.isEmpty() || artist == null || artist.isEmpty()) {
            Log.w("Lyricify", "Cannot trigger background search: Missing title or artist");
            return;
        }

        WebView webView = LyricsSharedEngine.getInstance(this).getWebView();
        if (webView != null) {
            String safeTitle = escapeSingleQuotes(title);
            String safeArtist = escapeSingleQuotes(artist);
            String safeAlbum = escapeSingleQuotes(getIntent().getStringExtra("SONG_ALBUM"));

            long durationMs = getIntent().getLongExtra("SONG_DURATION", 0);
            long durationSeconds = durationMs / 1000;

            Log.d("Lyricify", "🔍 Background Search: " + safeTitle + " by " + safeArtist);

            String js =
                    String.format(
                            "if(window.AndroidAPI) window.AndroidAPI.searchSong('%s', '%s', '%s', %d);",
                            safeTitle, safeArtist, safeAlbum, durationSeconds);

            webView.post(() -> webView.evaluateJavascript(js, null));
        }
    }

    private String escapeSingleQuotes(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'");
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
                                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    Toast.makeText(this, "✓ Access granted!", Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });
    }

    private void initializeViews() {
        songArtwork = findViewById(R.id.songArtwork);
        immersiveBackground = findViewById(R.id.immersiveBackground);
        songTitle = findViewById(R.id.songTitle);
        songTitle.setSelected(true);
        songArtist = findViewById(R.id.songArtist);
        songFilePath = findViewById(R.id.songFilePath);

        lyricsTextView = findViewById(R.id.lyricsTextView);
        lyricsHorizontalScrollView = findViewById(R.id.lyricsHorizontalScrollView);
        lyricsLoading = findViewById(R.id.lyricsLoading);

        // Control Panel
        formatSelectorButton = findViewById(R.id.formatSelectorButton);
        currentFormatText = findViewById(R.id.currentFormatText);
        formatDropdownArrow = findViewById(R.id.formatDropdownArrow);
        hasElrcIndicator = findViewById(R.id.hasElrcIndicator);
        statusDot = findViewById(R.id.statusDot);

        showMetadataButton = findViewById(R.id.showMetadataButton);
        saveLrcButton = findViewById(R.id.saveLrcButton);
        copyLyricsButton = findViewById(R.id.copyLyricsButton);
        embedLyricsButton = findViewById(R.id.embbedLyricsButton);
        syncedLyricsButton = findViewById(R.id.syncedLyricsButton);
        editTagsButton = findViewById(R.id.editTagsButton);
    }

    private void initializeManagers() {
        songInfoDisplay =
                new SongInfoDisplay(
                        this,
                        songArtwork,
                        immersiveBackground,
                        songTitle,
                        songArtist,
                        songFilePath);

        lyricsFetcher = new LyricsFetcher(lyricsTextView, lyricsLoading);
        lyricsFetcher.setCallback(
                new LyricsFetcher.LyricsCallback() {
                    @Override
                    public void onLyricsLoaded(ApiClient.LyricsResponse lyricsResponse) {
                        currentLyricsResponse = lyricsResponse;
                        ApiClient.updateCache(lyricsResponse);
                        runOnUiThread(() -> updateFormatAvailability(lyricsResponse));
                    }

                    @Override
                    public void onLyricsError(String error) {
                        // Handle error
                    }
                });

        metadataManager = new MetadataManager(this);
        embeddingManager = new EmbeddingManager(this);
        embeddingManager.setCallback(
                new EmbeddingManager.EmbeddingCallback() {
                    @Override
                    public void onProgressUpdate(String message) {
                        updateProgressDialog(message);
                    }

                    @Override
                    public void onEmbedSuccess(String successMessage) {
                        runOnUiThread(
                                () -> {
                                    dismissProgressDialog();
                                    showSuccessDialog(successMessage);
                                });
                    }

                    @Override
                    public void onEmbedError(String errorMessage) {
                        runOnUiThread(
                                () -> {
                                    dismissProgressDialog();
                                    showErrorDialog(errorMessage);
                                });
                    }

                    @Override
                    public void onNeedPermission(String folderPath) {
                        runOnUiThread(
                                () -> {
                                    dismissProgressDialog();
                                    showPermissionDialog(folderPath);
                                });
                    }

                    @Override
                    public void onShowMetadata(String filePath) {
                        runOnUiThread(() -> metadataManager.showMetadataDialog(filePath));
                    }
                });
    }

    private void updateFormatAvailability(ApiClient.LyricsResponse response) {
        availableFormats.clear();
        availableFormats.add("Plain"); // Always available

        if (response == null || !response.hasLyrics()) {
            updateSelectorState(false);
            hasElrcIndicator.setText("None");
            hasElrcIndicator.setTextColor(Color.parseColor("#80FFFFFF"));
            statusDot.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FF5252"))); // Red
            return;
        }

        boolean hasLrc = false;
        boolean hasElrc = false;

        if (response.lrc != null && !response.lrc.isEmpty() && !response.lrc.equals("null")) {
            availableFormats.add("LRC");
            hasLrc = true;
        }

        if ((response.elrc != null && !response.elrc.isEmpty() && !response.elrc.equals("null"))
                || (response.elrcMultiPerson != null
                        && !response.elrcMultiPerson.isEmpty()
                        && !response.elrcMultiPerson.equals("null"))) {

            if (response.elrc != null) availableFormats.add("ELRC");
            if (response.elrcMultiPerson != null) availableFormats.add("ELRC Multi-Person");
            hasElrc = true;
        }

        if (hasElrc) {
            hasElrcIndicator.setText("ELRC");
            hasElrcIndicator.setTextColor(Color.WHITE);
            statusDot.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
        } else if (hasLrc) {
            hasElrcIndicator.setText("LRC");
            hasElrcIndicator.setTextColor(Color.WHITE);
            statusDot.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FFC107"))); // Amber
        } else {
            hasElrcIndicator.setText("Plain");
            hasElrcIndicator.setTextColor(Color.LTGRAY);
            statusDot.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }

        updateSelectorState(true);
        updateSyncedLyricsButtonState();
    }

    private void updateSelectorState(boolean enabled) {
        formatSelectorButton.setEnabled(enabled);
        formatSelectorButton.setAlpha(enabled ? 1.0f : 0.5f);
    }

    /**
     * Updates the TextView properties based on the selected format.
     * Plain -> Wrap enabled, Center aligned, No Horizontal Scroll
     * Others -> Wrap disabled, Left aligned, Horizontal Scroll enabled
     */
    private void applyTextLayoutLogic(String format) {
        if (lyricsTextView == null || lyricsHorizontalScrollView == null) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lyricsTextView.getLayoutParams();

        if ("Plain".equals(format)) {
            // 1. ENABLE WRAPPING: Force width to match parent
            params.width = FrameLayout.LayoutParams.MATCH_PARENT;
            
            // 2. CENTER JUSTIFY
            lyricsTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            
            // Allow text to wrap naturally
            lyricsTextView.setHorizontallyScrolling(false);
            
            // 3. EFFECTIVELY DISABLE HORIZONTAL SCROLLING 
            // (Since width is MATCH_PARENT, it won't overflow)
        } else {
            // 1. DISABLE WRAPPING: Allow width to grow beyond screen
            params.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            
            // 2. LEFT JUSTIFY
            lyricsTextView.setGravity(Gravity.START);
            params.gravity = Gravity.START;

            // Force text to NOT wrap even if it hits screen edge
            lyricsTextView.setHorizontallyScrolling(true);
            
            // 3. ENABLE HORIZONTAL SCROLLING
            // (Since width is WRAP_CONTENT, HorizontalScrollView will handle overflow)
        }

        lyricsTextView.setLayoutParams(params);
        
        // Reset scroll position to start
        lyricsHorizontalScrollView.scrollTo(0, 0);
    }

    private void showFormatSelectionSheet() {
        if (availableFormats.size() <= 1) {
            Toast.makeText(this, "Only Plain format available", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_formats, null);
        LinearLayout container = sheetView.findViewById(R.id.sheetListContainer);
        bottomSheetDialog.setContentView(sheetView);

        bottomSheetDialog.setOnShowListener(
                dialog -> {
                    BottomSheetDialog d = (BottomSheetDialog) dialog;
                    View bottomSheet =
                            d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                    if (bottomSheet != null) {
                        bottomSheet.setBackgroundResource(android.R.color.transparent);
                    }
                });

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        int selectableBackgroundId = outValue.resourceId;

        for (String format : availableFormats) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(60, 32, 60, 32);
            itemLayout.setBackgroundResource(selectableBackgroundId);

            ImageView checkIcon = new ImageView(this);
            checkIcon.setImageResource(R.drawable.ic_check_circle);
            checkIcon.setColorFilter(Color.parseColor("#4CAF50")); // Green
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(50, 50);
            checkParams.setMarginEnd(32);
            checkIcon.setLayoutParams(checkParams);

            if (format.equals(currentFormat)) {
                checkIcon.setVisibility(View.VISIBLE);
            } else {
                checkIcon.setVisibility(View.INVISIBLE);
            }

            TextView optionText = new TextView(this);
            optionText.setText(format);
            optionText.setTextSize(16);
            optionText.setTextColor(Color.WHITE);

            if (format.equals(currentFormat)) {
                optionText.setTypeface(null, Typeface.BOLD);
                optionText.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                optionText.setTypeface(null, Typeface.NORMAL);
            }

            itemLayout.addView(checkIcon);
            itemLayout.addView(optionText);

            itemLayout.setOnClickListener(
                    v -> {
                        currentFormat = format;
                        currentFormatText.setText(format);
                        
                        // Update Content
                        lyricsFetcher.displayFormat(format);
                        
                        // Update Layout Logic (Wrap vs Scroll)
                        applyTextLayoutLogic(format);
                        
                        updateSyncedLyricsButtonState();
                        bottomSheetDialog.dismiss();
                    });

            container.addView(itemLayout);
        }

        bottomSheetDialog.show();
    }

    private void setupButtonListeners() {
        formatSelectorButton.setOnClickListener(v -> showFormatSelectionSheet());

        showMetadataButton.setOnClickListener(v -> metadataManager.showMetadataDialog(filePath));
        songFilePath.setOnClickListener(v -> metadataManager.showFilePathDialog(filePath));
        copyLyricsButton.setOnClickListener(v -> copyLyricsToClipboard());
        embedLyricsButton.setOnClickListener(v -> handleEmbedLyrics());
        saveLrcButton.setOnClickListener(v -> handleSaveLrc());
        syncedLyricsButton.setOnClickListener(v -> openSyncedLyricsView());

        editTagsButton.setOnClickListener(
                v -> {
                    Intent intent = new Intent(LyricsActivity.this, TagEditorActivity.class);
                    intent.putExtra("FILE_PATH", filePath);
                    intent.putExtra("SONG_TITLE", title);
                    intent.putExtra("SONG_ARTIST", artist);
                    intent.putExtra("ARTWORK_URL", artworkUrl);
                    intent.putExtra("SONG_ID", songId);
                    if (currentLyricsResponse != null) {
                        intent.putExtra("CACHED_METADATA", currentLyricsResponse);
                    }
                    startActivity(intent);
                });
    }

    private void extractIntentData() {
        songId = getIntent().getStringExtra("SONG_ID");
        title = getIntent().getStringExtra("SONG_TITLE");
        artist = getIntent().getStringExtra("SONG_ARTIST");
        artworkUrl = getIntent().getStringExtra("SONG_ARTWORK");
        filePath = getIntent().getStringExtra("SONG_FILE_PATH");
    }

    private void displaySongInfo() {
        songInfoDisplay.displaySongInfo(title, artist, artworkUrl, filePath);
    }

    private void fetchLyrics() {
        if (songId != null && !songId.isEmpty()) {
            lyricsFetcher.fetchBySongId(songId);
        } else if (title != null && artist != null) {
            lyricsFetcher.fetchByTitleAndArtist(title, artist);
        } else {
            lyricsTextView.setText("No song information provided");
            lyricsLoading.setVisibility(View.GONE);
        }
    }

    private void updateSyncedLyricsButtonState() {
        boolean isPlainFormat = currentFormat.equals("Plain");
        syncedLyricsButton.setEnabled(!isPlainFormat);
        syncedLyricsButton.setAlpha(isPlainFormat ? 0.4f : 1.0f);
    }

    private void copyLyricsToClipboard() {
        String lyrics = lyricsFetcher.getCurrentLyrics();
        if (lyrics == null || lyrics.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Lyrics", lyrics);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Lyrics copied", Toast.LENGTH_SHORT).show();
    }

    private void handleSaveLrc() {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No file path", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!lyricsFetcher.hasValidLyrics()) {
            Toast.makeText(this, "No lyrics", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = embeddingManager.extractFileName(filePath);
        String lrcFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".lrc";

        new AlertDialog.Builder(this)
                .setTitle("Save LRC")
                .setMessage("Save as: " + lrcFileName)
                .setPositiveButton(
                        "Save",
                        (d, w) -> {
                            showProgressDialog("Saving...", "Processing");
                            embeddingManager.saveLrcFile(
                                    filePath, lyricsFetcher.getCurrentLyrics());
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleEmbedLyrics() {
        if (!embeddingManager.canEmbed(filePath)) return;
        if (!lyricsFetcher.hasValidLyrics()) return;
        if (embeddingManager.isFileLocked(filePath)) {
            embeddingManager.showFileLockedWarning(
                    this, filePath, this::showEmbedLyricsConfirmation);
            return;
        }
        showEmbedLyricsConfirmation();
    }

    private void showEmbedLyricsConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Embed Lyrics")
                .setMessage(
                        "File: "
                                + embeddingManager.extractFileName(filePath)
                                + "\nFormat: "
                                + currentFormat)
                .setPositiveButton(
                        "Yes",
                        (d, w) -> {
                            showProgressDialog("Embedding...", "Processing");
                            embeddingManager.embedLyrics(
                                    filePath, lyricsFetcher.getCurrentLyrics());
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openSyncedLyricsView() {
        if (!lyricsFetcher.hasValidLyrics()) return;
        String currentLyrics = lyricsFetcher.getCurrentLyrics();
        Intent intent = new Intent(this, SyncedLyricsActivity.class);
        intent.putExtra("SONG_TITLE", title);
        intent.putExtra("SONG_ARTIST", artist);
        intent.putExtra("LYRICS", currentLyrics);
        intent.putExtra("LYRICS_FORMAT", currentFormat);
        intent.putExtra("ARTWORK_URL", artworkUrl);
        startActivity(intent);
    }

    private void showProgressDialog(String title, String message) {
        dismissProgressDialog();
        progressDialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .create();
        progressDialog.show();
    }

    private void updateProgressDialog(String message) {
        runOnUiThread(
                () -> {
                    if (progressDialog != null && progressDialog.isShowing())
                        progressDialog.setMessage(message);
                });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    }

    private void showSuccessDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("✓ Success!")
                .setMessage(message)
                .setPositiveButton(
                        "Metadata", (d, w) -> metadataManager.showMetadataDialog(filePath))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showErrorDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(errorMessage)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPermissionDialog(String folderPath) {
        new AlertDialog.Builder(this)
                .setTitle("Permission")
                .setMessage("Grant access to: " + folderPath)
                .setPositiveButton(
                        "Grant",
                        (d, w) -> {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            Uri initialUri = FileSaver.getFolderUriForPath(folderPath);
                            if (initialUri != null)
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
                            directoryPickerLauncher.launch(intent);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
