package aman.lyricify;

import aman.youly.LyricsWebViewFragment;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextInputEditText searchEditText;
    private ListView songListView;
    private ProgressBar songLoading;
    private CardView nowPlayingCard;
    private ImageView nowPlayingArtwork;
    private TextView nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath;

    // Data - Local
    private List<MediaStoreHelper.LocalSong> allLocalSongs = new ArrayList<>();
    private List<MediaStoreHelper.LocalSong> filteredLocalSongs = new ArrayList<>();
    private LocalSongAdapter localAdapter;

    // Managers
    private MediaSessionHandler mediaSessionHandler;
    private NowPlayingManager nowPlayingManager;
    private PermissionManager permissionManager;

    private boolean isShowingSheet = false;

    // Sort State
    private int currentSortCriteria = R.id.rbTitle;
    private int currentSortOrder = R.id.rbAscending;

    private static final String PREFS_NAME = "LyricifyPrefs";
    private static final String KEY_SORT_CRITERIA = "sort_criteria";
    private static final String KEY_SORT_ORDER = "sort_order";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadSortPreferences();


        initializeViews();
        initializeManagers();
        setupListeners();

        nowPlayingManager.register();
    }

    

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionAndOnboard();

        if (mediaSessionHandler.hasNotificationAccess()) {
            mediaSessionHandler.initialize();
            if (!nowPlayingManager.hasActiveMedia()) {
                nowPlayingCard.postDelayed(() -> mediaSessionHandler.checkActiveSessions(), 200);
            }
        }
    }

    private void loadSortPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentSortCriteria = prefs.getInt(KEY_SORT_CRITERIA, R.id.rbTitle);
        currentSortOrder = prefs.getInt(KEY_SORT_ORDER, R.id.rbAscending);

        if (currentSortCriteria != R.id.rbTitle
                && currentSortCriteria != R.id.rbArtist
                && currentSortCriteria != R.id.rbDateAdded) {
            currentSortCriteria = R.id.rbTitle;
        }
    }

    private void saveSortPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_SORT_CRITERIA, currentSortCriteria);
        editor.putInt(KEY_SORT_ORDER, currentSortOrder);
        editor.apply();
    }

    private void initializeViews() {
        searchEditText = findViewById(R.id.searchEditText);

        songListView = findViewById(R.id.songListView);
        songLoading = findViewById(R.id.songLoading);

        nowPlayingCard = findViewById(R.id.nowPlayingCard);
        nowPlayingArtwork = findViewById(R.id.nowPlayingArtwork);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist);
        nowPlayingFilePath = findViewById(R.id.nowPlayingFilePath);

        localAdapter = new LocalSongAdapter(this, filteredLocalSongs);
        songListView.setAdapter(localAdapter);
    }

    private void initializeManagers() {
        mediaSessionHandler = new MediaSessionHandler(this);
        mediaSessionHandler.setCallback(
                new MediaSessionHandler.MediaSessionCallback() {
                    @Override
                    public void onMediaFound(
                            String title, String artist, android.graphics.Bitmap artwork) {
                        nowPlayingManager.cancelPendingUpdate();
                        nowPlayingManager.prepareUpdate(title, artist, artwork);
                    }

                    @Override
                    public void onMediaLost() {
                        nowPlayingManager.hide();
                    }

                    @Override
                    public void onMetadataChanged() {}
                });

        nowPlayingManager =
                new NowPlayingManager(
                        this,
                        nowPlayingCard,
                        nowPlayingArtwork,
                        nowPlayingTitle,
                        nowPlayingArtist,
                        nowPlayingFilePath);
        nowPlayingManager.setCallback(
                new NowPlayingManager.NowPlayingCallback() {
                    @Override
                    public void onCardClicked(String title, String artist) {
                        Uri uri = nowPlayingManager.getCurrentFileUri();
                        String path = nowPlayingManager.getCurrentFilePath();

                        Bitmap currentArt = nowPlayingManager.getCurrentArtwork();

                        MediaStoreHelper.LocalSong tempSong =
                                new MediaStoreHelper.LocalSong(
                                        uri, path, title, artist, "", -1, 0, 0);

                        showApiMatchPopup(tempSong, currentArt);
                    }

                    @Override
                    public void onFileFound(String filePath, Uri fileUri) {}
                });

        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onStoragePermissionGranted() {
                        loadLocalSongs();
                    }

                    @Override
                    public void onStoragePermissionDenied() {}
                });
    }

    private void loadLocalSongs() {
        if (!permissionManager.hasStoragePermission()) return;

        songLoading.setVisibility(View.VISIBLE);
        new Thread(
                        () -> {
                            List<MediaStoreHelper.LocalSong> songs =
                                    MediaStoreHelper.getAllSongs(this);
                            runOnUiThread(
                                    () -> {
                                        allLocalSongs.clear();
                                        allLocalSongs.addAll(songs);
                                        applyCurrentSort();
                                        filterLocalSongs(searchEditText.getText().toString());
                                        songLoading.setVisibility(View.GONE);
                                    });
                        })
                .start();
    }

    private void filterLocalSongs(String query) {
        filteredLocalSongs.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredLocalSongs.addAll(allLocalSongs);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (MediaStoreHelper.LocalSong song : allLocalSongs) {
                boolean matchesTitle =
                        song.title != null && song.title.toLowerCase().contains(lowerQuery);
                boolean matchesArtist =
                        song.artist != null && song.artist.toLowerCase().contains(lowerQuery);
                if (matchesTitle || matchesArtist) {
                    filteredLocalSongs.add(song);
                }
            }
        }
        localAdapter.notifyDataSetChanged();
    }

    private void setupListeners() {
        searchEditText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        filterLocalSongs(s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        songListView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    MediaStoreHelper.LocalSong selectedSong = filteredLocalSongs.get(position);

                    Bitmap extractedBitmap = null;
                    try {
                        ImageView artView = view.findViewById(R.id.localArtwork);
                        extractedBitmap = getBitmapFromImageView(artView);
                    } catch (Exception ignored) {
                    }

                    showApiMatchPopup(selectedSong, extractedBitmap);
                });

        findViewById(R.id.sortButton).setOnClickListener(v -> showSortDialog());
    }

    private void showSortDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sort);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow()
                    .setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        RadioGroup criteriaGroup = dialog.findViewById(R.id.sortCriteriaGroup);
        RadioGroup orderGroup = dialog.findViewById(R.id.sortOrderGroup);
        MaterialButton btnApply = dialog.findViewById(R.id.btnApplySort);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelSort);

        criteriaGroup.check(currentSortCriteria);
        orderGroup.check(currentSortOrder);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnApply.setOnClickListener(
                v -> {
                    currentSortCriteria = criteriaGroup.getCheckedRadioButtonId();
                    currentSortOrder = orderGroup.getCheckedRadioButtonId();

                    saveSortPreferences();
                    applyCurrentSort();
                    filterLocalSongs(searchEditText.getText().toString());

                    dialog.dismiss();
                });

        dialog.show();
    }

    private void applyCurrentSort() {
        Comparator<MediaStoreHelper.LocalSong> comparator = null;

        // 0=Title (A-Z), 1=DateAdded, 2=Artist (A-Z)
        int sortMode = 0;

        if (currentSortCriteria == R.id.rbTitle) {
            comparator =
                    (s1, s2) -> {
                        String t1 = s1.title != null ? s1.title.trim() : "";
                        String t2 = s2.title != null ? s2.title.trim() : "";
                        return t1.compareToIgnoreCase(t2);
                    };
            sortMode = 0;
        } else if (currentSortCriteria == R.id.rbArtist) {
            comparator =
                    (s1, s2) -> {
                        String a1 = s1.artist != null ? s1.artist.trim() : "";
                        String a2 = s2.artist != null ? s2.artist.trim() : "";
                        return a1.compareToIgnoreCase(a2);
                    };
            // FIX: Set sortMode to 2 for Artist sorting
            sortMode = 2;
        } else if (currentSortCriteria == R.id.rbDateAdded) {
            comparator = (s1, s2) -> Long.compare(s1.dateAdded, s2.dateAdded);
            sortMode = 1;
        }

        if (localAdapter != null) {
            localAdapter.setSortMode(sortMode);
        }

        if (comparator != null) {
            if (currentSortOrder == R.id.rbDescending) {
                comparator = Collections.reverseOrder(comparator);
            }
            Collections.sort(allLocalSongs, comparator);
        }
    }

    private Bitmap getBitmapFromImageView(ImageView view) {
        if (view == null || view.getDrawable() == null) return null;
        Drawable drawable = view.getDrawable();

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        try {
            Bitmap bitmap =
                    Bitmap.createBitmap(
                            drawable.getIntrinsicWidth() <= 0 ? 100 : drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight() <= 0
                                    ? 100
                                    : drawable.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void showApiMatchPopup(MediaStoreHelper.LocalSong localSong, Bitmap overrideBitmap) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_api_search);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow()
                    .setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
        }

        ImageView artworkView = dialog.findViewById(R.id.dialogArtwork);
        TextInputEditText editTitle = dialog.findViewById(R.id.editDialogTitle);
        TextInputEditText editArtist = dialog.findViewById(R.id.editDialogArtist);
        MaterialButton btnSearch = dialog.findViewById(R.id.btnDialogSearch);
        ProgressBar loading = dialog.findViewById(R.id.dialogLoading);
        ListView apiListView = dialog.findViewById(R.id.dialogListView);
        TextView errorText = dialog.findViewById(R.id.dialogErrorText);

        editTitle.setText(localSong.title);
        editArtist.setText(localSong.artist);

        if (overrideBitmap != null) {
            Glide.with(this)
                    .load(overrideBitmap)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(artworkView);
        } else if (localSong.albumId > 0) {
            Glide.with(this)
                    .load(MediaStoreHelper.getAlbumArtUri(localSong.albumId))
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(artworkView);
        } else if (localSong.fileUri != null) {
            Glide.with(this)
                    .load(localSong.fileUri)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(artworkView);
        }

        ArrayList<Song> apiResults = new ArrayList<>();
        SongAdapter apiAdapter = new SongAdapter(this, apiResults);
        apiListView.setAdapter(apiAdapter);

        Runnable performSearch =
                () -> {
                    String title =
                            editTitle.getText() != null
                                    ? editTitle.getText().toString().trim()
                                    : "";
                    String artist =
                            editArtist.getText() != null
                                    ? editArtist.getText().toString().trim()
                                    : "";
                    String query = title + " " + artist;

                    if (query.isEmpty()) return;

                    loading.setVisibility(View.VISIBLE);
                    errorText.setVisibility(View.GONE);
                    apiResults.clear();
                    apiAdapter.notifyDataSetChanged();

                    btnSearch.setEnabled(false);
                    btnSearch.setText("Searching...");
                    btnSearch.setBackgroundTintList(
                            ColorStateList.valueOf(Color.parseColor("#555555")));
                    btnSearch.setTextColor(Color.parseColor("#AAAAAA"));

                    ApiClient.searchSongs(
                            query,
                            new ApiClient.SearchCallback() {
                                @Override
                                public void onSuccess(ArrayList<Song> results) {
                                    for (Song s : results) {
                                        s.calculateMatchScore(query);
                                    }
                                    Collections.sort(
                                            results,
                                            (s1, s2) ->
                                                    Integer.compare(
                                                            s2.getMatchScore(),
                                                            s1.getMatchScore()));

                                    runOnUiThread(
                                            () -> {
                                                apiResults.addAll(results);
                                                apiAdapter.notifyDataSetChanged();

                                                loading.setVisibility(View.GONE);
                                                if (apiResults.isEmpty()) {
                                                    errorText.setVisibility(View.VISIBLE);
                                                    errorText.setText("No matches found");
                                                }

                                                btnSearch.setEnabled(true);
                                                btnSearch.setText("SEARCH");
                                                int purple =
                                                        ContextCompat.getColor(
                                                                MainActivity.this,
                                                                R.color.primary_purple);
                                                btnSearch.setBackgroundTintList(
                                                        ColorStateList.valueOf(purple));
                                                btnSearch.setTextColor(Color.BLACK);
                                            });
                                }

                                @Override
                                public void onFailure(String error) {
                                    runOnUiThread(
                                            () -> {
                                                loading.setVisibility(View.GONE);
                                                errorText.setVisibility(View.VISIBLE);
                                                errorText.setText("Error: " + error);

                                                btnSearch.setEnabled(true);
                                                btnSearch.setText("SEARCH");
                                                int purple =
                                                        ContextCompat.getColor(
                                                                MainActivity.this,
                                                                R.color.primary_purple);
                                                btnSearch.setBackgroundTintList(
                                                        ColorStateList.valueOf(purple));
                                                btnSearch.setTextColor(Color.BLACK);
                                            });
                                }
                            });
                };

        btnSearch.setOnClickListener(v -> performSearch.run());

        apiListView.setOnItemClickListener(
                (parent, v, position, id) -> {
                    Song apiSong = apiResults.get(position);
                    dialog.dismiss();
                    openLyricsActivity(apiSong, localSong);
                });

        dialog.show();
        performSearch.run();
    }

    private void openLyricsActivity(Song apiSong, MediaStoreHelper.LocalSong localSong) {
        Intent intent = new Intent(this, LyricsActivity.class);
        intent.putExtra("SONG_ID", apiSong.getId());
        intent.putExtra("SONG_TITLE", apiSong.getSongName());
        intent.putExtra("SONG_ARTIST", apiSong.getArtistName());
        intent.putExtra("SONG_ARTWORK", apiSong.getArtwork());

        // ✅ NEW: Pass Album Name
        intent.putExtra("SONG_ALBUM", apiSong.getAlbumName());

        // ✅ NEW: Pass Duration (convert from "3:45" format to milliseconds)
        long durationMs = parseDurationToMillis(apiSong.getDuration());
        intent.putExtra("SONG_DURATION", durationMs);

        if (localSong.filePath != null) {
            intent.putExtra("SONG_FILE_PATH", localSong.filePath);
        }
        if (localSong.fileUri != null) {
            intent.putExtra("SONG_FILE_URI", localSong.fileUri);
        }

        startActivity(intent);
    }

    /** Converts duration string (e.g., "3:45" or "1:02:30") to milliseconds */
    private long parseDurationToMillis(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return 0;
        }

        try {
            // Remove "Duration: " prefix if present
            durationStr = durationStr.replace("Duration: ", "").trim();

            String[] parts = durationStr.split(":");
            long totalSeconds = 0;

            if (parts.length == 2) {
                // Format: MM:SS
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                totalSeconds = (minutes * 60) + seconds;
            } else if (parts.length == 3) {
                // Format: HH:MM:SS
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                totalSeconds = (hours * 3600) + (minutes * 60) + seconds;
            }

            return totalSeconds * 1000; // Convert to milliseconds
        } catch (NumberFormatException e) {
            Log.e("MainActivity", "Failed to parse duration: " + durationStr, e);
            return 0;
        }
    }

    private void checkPermissionAndOnboard() {
        if (isShowingSheet) return;

        if (!permissionManager.hasStoragePermission()) {
            isShowingSheet = true;
            nowPlayingCard.postDelayed(this::showStoragePermissionSheet, 500);
        } else {
            if (allLocalSongs.isEmpty()) {
                loadLocalSongs();
            }
            if (!mediaSessionHandler.hasNotificationAccess()) {
                isShowingSheet = true;
                nowPlayingCard.postDelayed(this::showNotificationPermissionSheet, 500);
            }
        }
    }

    private void showStoragePermissionSheet() {
        if (isFinishing()) return;
        BottomSheetDialog bottomSheetDialog =
                new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_permission, null);
        bottomSheetDialog.setContentView(sheetView);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog
                    .getWindow()
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
        }

        MaterialButton btnGrant = sheetView.findViewById(R.id.btnGrantAccess);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);

        btnGrant.setOnClickListener(
                v -> {
                    bottomSheetDialog.dismiss();
                    permissionManager.requestStoragePermission();
                });

        btnNotNow.setOnClickListener(
                v -> {
                    bottomSheetDialog.dismiss();
                    if (!mediaSessionHandler.hasNotificationAccess()) {
                        showNotificationPermissionSheet();
                    } else {
                        isShowingSheet = false;
                    }
                });
        bottomSheetDialog.setOnDismissListener(dialog -> isShowingSheet = false);
        bottomSheetDialog.show();
    }

    private void showNotificationPermissionSheet() {
        if (isFinishing()) return;
        BottomSheetDialog bottomSheetDialog =
                new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView =
                LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notification_access, null);
        bottomSheetDialog.setContentView(sheetView);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog
                    .getWindow()
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
        }

        MaterialButton btnConnect = sheetView.findViewById(R.id.btnConnectApps);
        TextView btnTroubleshoot = sheetView.findViewById(R.id.btnTroubleshoot);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);

        btnConnect.setOnClickListener(
                v -> {
                    bottomSheetDialog.dismiss();
                    mediaSessionHandler.requestNotificationAccess();
                });

        btnTroubleshoot.setOnClickListener(
                v -> {
                    bottomSheetDialog.dismiss();
                    mediaSessionHandler.openAppInfo();
                });

        btnNotNow.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.setOnDismissListener(dialog -> isShowingSheet = false);
        bottomSheetDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nowPlayingManager.unregister();
        mediaSessionHandler.cleanup();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults);
    }
}
