package aman.lyricify;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import androidx.cardview.widget.CardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextInputEditText searchEditText;
    private Button searchButton;
    private ListView songListView;
    private ProgressBar songLoading;
    private CardView nowPlayingCard;
    private ImageView nowPlayingArtwork;
    private TextView nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath;

    // Data
    private ArrayList<Song> songs = new ArrayList<>();
    private SongAdapter adapter;
    private String lastSearchQuery = "";

    // Managers
    private MediaSessionHandler mediaSessionHandler;
    private NowPlayingManager nowPlayingManager;
    private PermissionManager permissionManager;

    // State to prevent double-showing sheets
    private boolean isShowingSheet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeManagers();
        setupListeners();

        // REMOVED: checkPermissionAndOnboard(); 
        // We moved this to onResume so it handles "Returning from Settings" too.

        nowPlayingManager.register();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 1. Check permissions every time the app comes to foreground
        checkPermissionAndOnboard();
        
        // 2. Initialize Media Handler if we have permission
        if (mediaSessionHandler.hasNotificationAccess()) {
            mediaSessionHandler.initialize();
            
            // Refresh Now Playing
            if (!nowPlayingManager.hasActiveMedia()) {
                nowPlayingCard.postDelayed(() -> mediaSessionHandler.checkActiveSessions(), 200);
            }
        }
    }

    /**
     * Central method to check permissions in order
     */
    private void checkPermissionAndOnboard() {
        if (isShowingSheet) return; // Don't stack sheets

        // Step 1: Storage Permission
        if (!permissionManager.hasStoragePermission()) {
            isShowingSheet = true;
            // Delay slightly to be smooth
            nowPlayingCard.postDelayed(this::showStoragePermissionSheet, 500);
        } 
        // Step 2: Notification Permission
        else if (!mediaSessionHandler.hasNotificationAccess()) {
            isShowingSheet = true;
            nowPlayingCard.postDelayed(this::showNotificationPermissionSheet, 500);
        }
    }

    private void showStoragePermissionSheet() {
        if (isFinishing()) return;
        
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_permission, null);
        bottomSheetDialog.setContentView(sheetView);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
        }

        MaterialButton btnGrant = sheetView.findViewById(R.id.btnGrantAccess);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);

        btnGrant.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            permissionManager.requestStoragePermission();
        });

        btnNotNow.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            // Proceed to check next permission immediately
            if (!mediaSessionHandler.hasNotificationAccess()) {
                showNotificationPermissionSheet();
            } else {
                isShowingSheet = false;
            }
        });

        bottomSheetDialog.setOnDismissListener(dialog -> isShowingSheet = false);
        bottomSheetDialog.setCancelable(false);
        bottomSheetDialog.show();
    }

    private void showNotificationPermissionSheet() {
        if (isFinishing()) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notification_access, null);
        bottomSheetDialog.setContentView(sheetView);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
        }

        MaterialButton btnConnect = sheetView.findViewById(R.id.btnConnectApps);
        TextView btnTroubleshoot = sheetView.findViewById(R.id.btnTroubleshoot);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);

        // Standard "Connect" button
        btnConnect.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            mediaSessionHandler.requestNotificationAccess();
            // isShowingSheet becomes false via OnDismissListener
            // When user returns, onResume runs -> checks permission -> if still missing, shows sheet again!
        });

        // "Restricted Settings" Fix Button
        btnTroubleshoot.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            mediaSessionHandler.openAppInfo();
        });

        btnNotNow.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(this, "Auto-detect disabled", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.setOnDismissListener(dialog -> isShowingSheet = false);
        bottomSheetDialog.show();
    }

    // ... (Keep existing initializeViews, initializeManagers, etc.) ...

    private void initializeViews() {
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        songListView = findViewById(R.id.songListView);
        songLoading = findViewById(R.id.songLoading);
        nowPlayingCard = findViewById(R.id.nowPlayingCard);
        nowPlayingArtwork = findViewById(R.id.nowPlayingArtwork);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist);
        nowPlayingFilePath = findViewById(R.id.nowPlayingFilePath);

        adapter = new SongAdapter(this, songs);
        songListView.setAdapter(adapter);
    }

    private void initializeManagers() {
        // Media session handler
        mediaSessionHandler = new MediaSessionHandler(this);
        mediaSessionHandler.setCallback(new MediaSessionHandler.MediaSessionCallback() {
            @Override
            public void onMediaFound(String title, String artist, android.graphics.Bitmap artwork) {
                nowPlayingManager.cancelPendingUpdate();
                nowPlayingManager.prepareUpdate(title, artist, artwork);
            }

            @Override
            public void onMediaLost() {
                nowPlayingManager.hide();
            }

            @Override
            public void onMetadataChanged() { }
        });

        // Now playing manager
        nowPlayingManager = new NowPlayingManager(
            this, nowPlayingCard, nowPlayingArtwork, 
            nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath
        );
        nowPlayingManager.setCallback(new NowPlayingManager.NowPlayingCallback() {
            @Override
            public void onCardClicked(String title, String artist) {
                searchLyricsByTitleAndArtist(title, artist);
            }

            @Override
            public void onFileFound(String filePath, Uri fileUri) { }
        });

        // Permission manager
        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(new PermissionManager.PermissionCallback() {
            @Override
            public void onStoragePermissionGranted() {
                // Storage done. The onResume check will handle the notification part next time it runs.
                // Or we can manually trigger update
                if (nowPlayingManager.hasActiveMedia()) {
                    String title = nowPlayingManager.getCurrentTitle();
                    String artist = nowPlayingManager.getCurrentArtist();
                    if (title != null && artist != null) {
                        nowPlayingManager.prepareUpdate(title, artist, nowPlayingManager.getCurrentArtwork());
                    }
                }
            }

            @Override
            public void onStoragePermissionDenied() { }
        });
    }

    private void setupListeners() {
        searchButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                searchSongs(query);
            }
        });

        songListView.setOnItemClickListener((parent, view, position, id) -> {
            Song song = songs.get(position);
            openLyricsActivity(song);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nowPlayingManager.unregister();
        mediaSessionHandler.cleanup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults);
    }

    private void searchLyricsByTitleAndArtist(String title, String artist) {
        searchEditText.setText(title + " " + artist);
        searchSongs(title + " " + artist);
    }

    private void openLyricsActivity(Song song) {
        Intent intent = new Intent(this, LyricsActivity.class);
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("SONG_TITLE", song.getSongName());
        intent.putExtra("SONG_ARTIST", song.getArtistName());
        intent.putExtra("SONG_ARTWORK", song.getArtwork());

        String filePath = nowPlayingManager.getCurrentFilePath();
        Uri fileUri = nowPlayingManager.getCurrentFileUri();
        
        if (filePath != null) {
            intent.putExtra("SONG_FILE_PATH", filePath);
        }
        if (fileUri != null) {
            intent.putExtra("SONG_FILE_URI", fileUri);
        }

        startActivity(intent);
    }

    private void searchSongs(String query) {
        lastSearchQuery = query;
        runOnUiThread(() -> {
            songLoading.setVisibility(View.VISIBLE);
            songs.clear();
            adapter.notifyDataSetChanged();
            searchButton.setEnabled(false);
        });

        ApiClient.searchSongs(query, new ApiClient.SearchCallback() {
            @Override
            public void onSuccess(ArrayList<Song> results) {
                for (Song song : results) {
                    song.calculateMatchScore(lastSearchQuery);
                }
                
                Collections.sort(results, new Comparator<Song>() {
                    @Override
                    public int compare(Song s1, Song s2) {
                        return Integer.compare(s2.getMatchScore(), s1.getMatchScore());
                    }
                });
                
                songs.clear();
                songs.addAll(results);
                
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    songLoading.setVisibility(View.GONE);
                    searchButton.setEnabled(true);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    songLoading.setVisibility(View.GONE);
                    searchButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
