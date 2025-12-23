package aman.lyricify;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import androidx.cardview.widget.CardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Main activity - handles UI and coordinates between managers
 */
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
    private Song currentPlayingSong;
    private String lastSearchQuery = "";

    // Managers
    private MediaSessionHandler mediaSessionHandler;
    private NowPlayingManager nowPlayingManager;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeManagers();
        setupListeners();
        

        android.util.Log.d("test" , "test logwire"); 
        
        permissionManager.checkAndRequestStoragePermission();
        mediaSessionHandler.initialize();
        nowPlayingManager.register();
        
        // Initial media check with delay
        nowPlayingCard.postDelayed(() -> mediaSessionHandler.checkActiveSessions(), 200);
    }

    /**
     * Initialize all views
     */
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

        // Set up adapter
        adapter = new SongAdapter(this, songs);
        songListView.setAdapter(adapter);
    }

    /**
     * Initialize all managers
     */
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
            public void onMetadataChanged() {
                // Metadata changed - will trigger onMediaFound
            }
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
            public void onFileFound(String filePath, Uri fileUri) {
                // File found - stored in manager
            }
        });

        // Permission manager
        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(new PermissionManager.PermissionCallback() {
            @Override
            public void onStoragePermissionGranted() {
                if (nowPlayingManager.hasActiveMedia()) {
                    // Retry file search if we have active media
                    String title = nowPlayingManager.getCurrentTitle();
                    String artist = nowPlayingManager.getCurrentArtist();
                    if (title != null && artist != null) {
                        nowPlayingManager.prepareUpdate(title, artist, nowPlayingManager.getCurrentArtwork());
                    }
                }
            }

            @Override
            public void onStoragePermissionDenied() {
                // Permission denied - already shown toast
            }
        });
    }

    /**
     * Setup UI listeners
     */
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
    protected void onResume() {
        super.onResume();
        if (!nowPlayingManager.hasActiveMedia() || nowPlayingManager.getCurrentArtwork() == null) {
            nowPlayingCard.postDelayed(() -> mediaSessionHandler.checkActiveSessions(), 100);
        }
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

    /**
     * Search for lyrics by title and artist
     */
    private void searchLyricsByTitleAndArtist(String title, String artist) {
        searchEditText.setText(title + " " + artist);
        searchSongs(title + " " + artist);
    }

    /**
     * Open lyrics activity for a song
     */
    private void openLyricsActivity(Song song) {
        Intent intent = new Intent(this, LyricsActivity.class);
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("SONG_TITLE", song.getSongName());
        intent.putExtra("SONG_ARTIST", song.getArtistName());
        intent.putExtra("SONG_ARTWORK", song.getArtwork());

        // Pass the current file path if available
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

    /**
     * Search for songs via API
     */
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
                // Calculate match scores for all songs
                for (Song song : results) {
                    song.calculateMatchScore(lastSearchQuery);
                }
                
                // Sort by match score (highest first)
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