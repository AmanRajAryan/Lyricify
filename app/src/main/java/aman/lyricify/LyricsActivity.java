package aman.lyricify;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class LyricsActivity extends AppCompatActivity {

    // UI Components
    private ImageView songArtwork;
    private TextView songTitle;
    private TextView songArtist;
    private TextView songFilePath;
    private TextView lyricsTextView;
    private ProgressBar lyricsLoading;
    private Spinner formatSpinner;
    private ImageView formatDropdownArrow;

    private ImageButton showMetadataButton, saveLrcButton, copyLyricsButton;
    private ImageButton embedLyricsButton, syncedLyricsButton;
    private Button editTagsButton;

    // Data
    private String songId;
    private String title;
    private String artist;
    private String artworkUrl;
    private String filePath;
    private String currentFormat = "Plain";
    
    // Cached Data to pass to Tag Editor
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

        setupDirectoryPickerLauncher();
        initializeViews();
        initializeManagers();
        extractIntentData();

        displaySongInfo();
        setupFormatSpinner();
        fetchLyrics();

        setupButtonListeners();
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
                                    Toast.makeText(
                                                    this,
                                                    "✓ Access granted! Try embedding again.",
                                                    Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });
    }

    private void initializeViews() {
        songArtwork = findViewById(R.id.songArtwork);
        songTitle = findViewById(R.id.songTitle);
        songArtist = findViewById(R.id.songArtist);
        songFilePath = findViewById(R.id.songFilePath);
        lyricsTextView = findViewById(R.id.lyricsTextView);
        lyricsLoading = findViewById(R.id.lyricsLoading);
        formatSpinner = findViewById(R.id.formatSpinner);
        formatDropdownArrow = findViewById(R.id.formatDropdownArrow);

        showMetadataButton = findViewById(R.id.showMetadataButton);
        saveLrcButton = findViewById(R.id.saveLrcButton);
        copyLyricsButton = findViewById(R.id.copyLyricsButton);
        embedLyricsButton = findViewById(R.id.embbedLyricsButton);
        syncedLyricsButton = findViewById(R.id.syncedLyricsButton);
        editTagsButton = findViewById(R.id.editTagsButton);
    }

    private void initializeManagers() {
        songInfoDisplay =
                new SongInfoDisplay(this, songArtwork, songTitle, songArtist, songFilePath);

        lyricsFetcher = new LyricsFetcher(lyricsTextView, lyricsLoading);
        lyricsFetcher.setCallback(
                new LyricsFetcher.LyricsCallback() {
                    @Override
                    public void onLyricsLoaded(ApiClient.LyricsResponse lyricsResponse) {
                        // STORE THE RESPONSE FOR TAG EDITOR
                        currentLyricsResponse = lyricsResponse;
                        
                        // NEW: Push to global cache
                        ApiClient.updateCache(lyricsResponse);
                        
                        runOnUiThread(() -> updateFormatSpinnerAvailability(lyricsResponse));
                    }

                    @Override
                    public void onLyricsError(String error) {
                        // Handle error if needed
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

    private void setupFormatSpinner() {
        String[] formats = {"Plain", "LRC", "ELRC", "ELRC Multi-Person"};

        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, formats) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        TextView textView = (TextView) view;
                        textView.setTextColor(Color.WHITE);
                        textView.setPadding(0, 0, 0, 0);
                        textView.setTextSize(14);
                        textView.setTypeface(null, android.graphics.Typeface.BOLD);
                        return view;
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        View view = super.getDropDownView(position, convertView, parent);
                        TextView textView = (TextView) view;
                        textView.setTextColor(Color.WHITE);
                        textView.setPadding(32, 20, 32, 20);
                        textView.setTextSize(15);

                        if (position == formatSpinner.getSelectedItemPosition()) {
                            textView.setBackgroundColor(Color.parseColor("#3A3A3A"));
                            textView.setTypeface(null, android.graphics.Typeface.BOLD);
                        } else {
                            textView.setBackgroundColor(Color.parseColor("#2A2A2A"));
                            textView.setTypeface(null, android.graphics.Typeface.NORMAL);
                        }

                        return view;
                    }
                };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);

        formatSpinner.setOnTouchListener(
                (v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        animateArrow(true);
                    }
                    return false;
                });

        formatSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        currentFormat = formats[position];
                        lyricsFetcher.displayFormat(currentFormat);
                        updateSyncedLyricsButtonState();
                        animateArrow(false);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        animateArrow(false);
                    }
                });

        formatSpinner.setEnabled(false);
    }

    private void animateArrow(boolean isOpening) {
        formatDropdownArrow.animate().rotation(isOpening ? 180 : 0).setDuration(200).start();
    }

    private void updateSyncedLyricsButtonState() {
        boolean isPlainFormat = currentFormat.equals("Plain");
        syncedLyricsButton.setEnabled(!isPlainFormat);
        syncedLyricsButton.setAlpha(isPlainFormat ? 0.4f : 1.0f);
    }

    private void updateFormatSpinnerAvailability(ApiClient.LyricsResponse response) {
        if (response == null || !response.hasLyrics()) {
            formatSpinner.setEnabled(false);
            syncedLyricsButton.setEnabled(false);
            syncedLyricsButton.setAlpha(0.4f);
            return;
        }

        formatSpinner.setEnabled(true);
        updateSyncedLyricsButtonState();
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

    private void setupButtonListeners() {
        showMetadataButton.setOnClickListener(v -> metadataManager.showMetadataDialog(filePath));
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
                    
                    // We still pass cached metadata for redundancy, though TagEditor uses the listener now
                    if (currentLyricsResponse != null) {
                        intent.putExtra("CACHED_METADATA", currentLyricsResponse);
                    }
                    
                    startActivity(intent);
                });
    }

    private void copyLyricsToClipboard() {
        String lyrics = lyricsFetcher.getCurrentLyrics();
        if (lyrics == null || lyrics.isEmpty()) {
            Toast.makeText(this, "No lyrics to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Lyrics", lyrics);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Lyrics copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void handleSaveLrc() {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No audio file path available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!lyricsFetcher.hasValidLyrics()) {
            Toast.makeText(this, "No valid lyrics to save", Toast.LENGTH_SHORT).show();
            return;
        }
        showSaveLrcConfirmation();
    }

    private void showSaveLrcConfirmation() {
        String fileName = embeddingManager.extractFileName(filePath);
        String lrcFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".lrc";

        new AlertDialog.Builder(this)
                .setTitle("Save LRC File")
                .setMessage(
                        "Save as: " + lrcFileName + "\nFormat: " + currentFormat + "\n\nContinue?")
                .setPositiveButton("Save", (d, w) -> performSaveLrc())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performSaveLrc() {
        String lyrics = lyricsFetcher.getCurrentLyrics();
        showProgressDialog("Saving LRC File", "Processing...");
        embeddingManager.saveLrcFile(filePath, lyrics);
    }

    private void handleEmbedLyrics() {
        if (!embeddingManager.canEmbed(filePath)) return;
        if (!lyricsFetcher.hasValidLyrics()) {
            Toast.makeText(this, "No valid lyrics to embed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (embeddingManager.isFileLocked(filePath)) {
            embeddingManager.showFileLockedWarning(
                    this, filePath, this::showEmbedLyricsConfirmation);
            return;
        }
        showEmbedLyricsConfirmation();
    }

    private void showEmbedLyricsConfirmation() {
        String fileName = embeddingManager.extractFileName(filePath);
        new AlertDialog.Builder(this)
                .setTitle("Embed Lyrics")
                .setMessage("File: " + fileName + "\nFormat: " + currentFormat + "\n\nContinue?")
                .setPositiveButton("Yes", (d, w) -> performEmbedLyrics())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performEmbedLyrics() {
        String lyrics = lyricsFetcher.getCurrentLyrics();
        showProgressDialog("Embedding Lyrics", "Processing...");
        embeddingManager.embedLyrics(filePath, lyrics);
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
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.setMessage(message);
                    }
                });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showSuccessDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("✓ Success!")
                .setMessage(message)
                .setPositiveButton(
                        "View Metadata", (d, w) -> metadataManager.showMetadataDialog(filePath))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showErrorDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("⚠ Operation Failed")
                .setMessage("Error: " + errorMessage)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPermissionDialog(String folderPath) {
        new AlertDialog.Builder(this)
                .setTitle("📁 Permission Needed")
                .setMessage("Grant access to:\n" + folderPath)
                .setPositiveButton("Grant Access", (d, w) -> requestFolderAccess(folderPath))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestFolderAccess(String folderPath) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        Uri initialUri = FileSaver.getFolderUriForPath(folderPath);
        if (initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        directoryPickerLauncher.launch(intent);
    }

    private void openSyncedLyricsView() {
        if (!lyricsFetcher.hasValidLyrics()) {
            Toast.makeText(this, "No lyrics available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentFormat.equals("Plain")) {
            Toast.makeText(this, "Please select a synced format (LRC/ELRC)", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        String currentLyrics = lyricsFetcher.getCurrentLyrics();

        Intent intent = new Intent(this, SyncedLyricsActivity.class);
        intent.putExtra("SONG_TITLE", title);
        intent.putExtra("SONG_ARTIST", artist);
        intent.putExtra("LYRICS", currentLyrics);
        intent.putExtra("LYRICS_FORMAT", currentFormat);
        intent.putExtra("ARTWORK_URL", artworkUrl);
        startActivity(intent);
    }
}
