package aman.lyricify;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnticipateInterpolator;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;

import aman.taglib.TagLib; //

public class MainActivity extends AppCompatActivity {

    FloatingActionButton youlyPlayerFab;

    // UI Components
    private TextInputEditText searchEditText;
    private ListView songListView;
    private ProgressBar songLoading;
    private CardView nowPlayingCard;
    private ImageView nowPlayingArtwork;
    private TextView nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath;

    // Drawer Components
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View menuButton;

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

    // Preference Keys
    private static final String PREFS_NAME = "LyricifyPrefs";
    private static final String KEY_SORT_CRITERIA = "sort_criteria";
    private static final String KEY_SORT_ORDER = "sort_order";
    
    // Folder Filtering Keys
    private static final String KEY_FOLDERS = "music_folders";
    private static final String KEY_BLACKLIST = "blacklist_folders";
    private static final String KEY_SCAN_ALL = "scan_all_folders";
    private static final String KEY_BLACKLIST_ENABLED = "blacklist_enabled";
    
    // New Feature Keys
    private static final String KEY_BOTTOM_UI = "bottom_ui_enabled";
    private static final String KEY_HIDE_LYRICS = "hide_lyrics_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        // 1. INSTALL SPLASH SCREEN
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        
        // CHECK UI PREFERENCE
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean useBottomUi = prefs.getBoolean(KEY_BOTTOM_UI, false);

        if (useBottomUi) {
            setContentView(R.layout.activity_main_bottom);
        } else {
            setContentView(R.layout.activity_main);
        }

        // 2. SETUP EXIT ANIMATION
        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            try {
                View iconView = splashScreenView.getIconView();
                if (iconView == null) {
                    splashScreenView.remove();
                    return;
                }
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0f);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(splashScreenView.getView(), View.ALPHA, 1f, 0f);
                
                android.animation.AnimatorSet animatorSet = new android.animation.AnimatorSet();
                animatorSet.playTogether(scaleX, scaleY, alpha);
                animatorSet.setDuration(500); 
                animatorSet.setInterpolator(new AnticipateInterpolator());
                animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        splashScreenView.remove();
                    }
                });
                animatorSet.start();
            } catch (Exception e) {
                splashScreenView.remove();
            }
        });

        loadSortPreferences();

        initializeViews();
        initializeManagers();
        setupListeners();
        setupDrawer();

        nowPlayingManager.register();

        youlyPlayerFab = findViewById(R.id.youlyPlayerFab);
        youlyPlayerFab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, YoulyPlayerActivity.class);
            startActivity(intent);
        });
        
        new UpdateManager(this).checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (permissionManager.hasStoragePermission()) {
            loadLocalSongs();
        }
        
        checkPermissionAndOnboard(); 

        if (mediaSessionHandler.hasNotificationAccess()) {
            mediaSessionHandler.initialize();
            if (!nowPlayingManager.hasActiveMedia()) {
                nowPlayingCard.postDelayed(() -> mediaSessionHandler.checkActiveSessions(), 200);
            }
        }
        
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_library);
        }
    }

    private void loadSortPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentSortCriteria = prefs.getInt(KEY_SORT_CRITERIA, R.id.rbTitle);
        currentSortOrder = prefs.getInt(KEY_SORT_ORDER, R.id.rbAscending);
        if (currentSortCriteria != R.id.rbTitle && currentSortCriteria != R.id.rbArtist && currentSortCriteria != R.id.rbDateAdded) {
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
        
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        menuButton = findViewById(R.id.menuButtonCard);

        localAdapter = new LocalSongAdapter(this, filteredLocalSongs);
        songListView.setAdapter(localAdapter);
    }
    
    private void setupDrawer() {
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_settings) {
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }
    }

    private void initializeManagers() {
        mediaSessionHandler = new MediaSessionHandler(this);
        mediaSessionHandler.setCallback(new MediaSessionHandler.MediaSessionCallback() {
            @Override
            public void onMediaFound(String title, String artist, android.graphics.Bitmap artwork) {
                nowPlayingManager.cancelPendingUpdate();
                nowPlayingManager.prepareUpdate(title, artist, artwork);
            }
            @Override public void onMediaLost() { nowPlayingManager.hide(); }
            @Override public void onMetadataChanged() {}
        });

        nowPlayingManager = new NowPlayingManager(this, nowPlayingCard, nowPlayingArtwork, nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath);
        nowPlayingManager.setCallback(new NowPlayingManager.NowPlayingCallback() {
            @Override
            public void onCardClicked(String title, String artist) {
                Uri uri = nowPlayingManager.getCurrentFileUri();
                String path = nowPlayingManager.getCurrentFilePath();
                Bitmap currentArt = nowPlayingManager.getCurrentArtwork();
                MediaStoreHelper.LocalSong tempSong = new MediaStoreHelper.LocalSong(uri, path, title, artist, "", -1, 0, 0);
                
                new IdentifySongDialog(MainActivity.this, tempSong, currentArt).show();
            }
            @Override public void onFileFound(String filePath, Uri fileUri) {}
        });

        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(new PermissionManager.PermissionCallback() {
            @Override public void onStoragePermissionGranted() { loadLocalSongs(); }
            @Override public void onStoragePermissionDenied() {}
        });
    }

        private void loadLocalSongs() {
        if (!permissionManager.hasStoragePermission()) return;
        
        // Only show loading if we don't have songs yet (prevents flickering on refresh)
        if (allLocalSongs.isEmpty()) songLoading.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            // 1. FETCH ALL SONGS
            List<MediaStoreHelper.LocalSong> allDeviceSongs = MediaStoreHelper.getAllSongs(this);
            
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean scanAll = prefs.getBoolean(KEY_SCAN_ALL, true);
            boolean blacklistEnabled = prefs.getBoolean(KEY_BLACKLIST_ENABLED, false);
            boolean hideLyrics = prefs.getBoolean(KEY_HIDE_LYRICS, false);
            
            Set<String> whitelistPaths = prefs.getStringSet(KEY_FOLDERS, new HashSet<>());
            Set<String> blacklistPaths = prefs.getStringSet(KEY_BLACKLIST, new HashSet<>());

            // 2. FILTER BY WHITELIST (Included Folders)
            List<MediaStoreHelper.LocalSong> pendingSongs = new ArrayList<>();
            if (scanAll) {
                pendingSongs.addAll(allDeviceSongs);
            } else {
                if (!whitelistPaths.isEmpty()) {
                    for (MediaStoreHelper.LocalSong song : allDeviceSongs) {
                        if (song.filePath != null) {
                            for (String includePath : whitelistPaths) {
                                if (song.filePath.startsWith(includePath)) {
                                    pendingSongs.add(song);
                                    break; 
                                }
                            }
                        }
                    }
                }
            }

            // 3. FILTER BY BLACKLIST (Excluded Folders)
            List<MediaStoreHelper.LocalSong> folderFilteredList = new ArrayList<>();
            if (blacklistEnabled && !blacklistPaths.isEmpty()) {
                for (MediaStoreHelper.LocalSong song : pendingSongs) {
                    boolean isBlacklisted = false;
                    if (song.filePath != null) {
                        for (String blockPath : blacklistPaths) {
                            if (song.filePath.startsWith(blockPath)) {
                                isBlacklisted = true;
                                break;
                            }
                        }
                    }
                    if (!isBlacklisted) {
                        folderFilteredList.add(song);
                    }
                }
            } else {
                folderFilteredList.addAll(pendingSongs);
            }

            // 4. FILTER BY LYRICS (Cached Check)
            List<MediaStoreHelper.LocalSong> finalFilteredList = new ArrayList<>();
            if (hideLyrics) {
                LyricsCacheManager cacheManager = LyricsCacheManager.getInstance(this);

                for (MediaStoreHelper.LocalSong song : folderFilteredList) {
                    // hasLyrics uses cache:
                    // - If file date modified matches cache -> Returns instantly.
                    // - If file changed -> Scans with TagLib and updates cache.
                    if (!cacheManager.hasLyrics(song.filePath)) {
                        finalFilteredList.add(song);
                    }
                }
                
                // Save the cache to disk so next run is fast
                cacheManager.saveCache(this);
                
            } else {
                finalFilteredList.addAll(folderFilteredList);
            }

            // 5. UPDATE UI
            runOnUiThread(() -> {
                allLocalSongs.clear();
                allLocalSongs.addAll(finalFilteredList);
                applyCurrentSort();
                filterLocalSongs(searchEditText.getText().toString());
                songLoading.setVisibility(View.GONE);
            });
        }).start();
    }


    private void filterLocalSongs(String query) {
        filteredLocalSongs.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredLocalSongs.addAll(allLocalSongs);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (MediaStoreHelper.LocalSong song : allLocalSongs) {
                if ((song.title != null && song.title.toLowerCase().contains(lowerQuery)) ||
                    (song.artist != null && song.artist.toLowerCase().contains(lowerQuery))) {
                    filteredLocalSongs.add(song);
                }
            }
        }
        localAdapter.notifyDataSetChanged();
    }

    private void setupListeners() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterLocalSongs(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        songListView.setOnItemClickListener((parent, view, position, id) -> {
            MediaStoreHelper.LocalSong selectedSong = filteredLocalSongs.get(position);
            Bitmap extractedBitmap = null;
            try {
                ImageView artView = view.findViewById(R.id.localArtwork);
                extractedBitmap = getBitmapFromImageView(artView);
            } catch (Exception ignored) {}
            
            new IdentifySongDialog(MainActivity.this, selectedSong, extractedBitmap).show();
        });

        songListView.setOnItemLongClickListener((parent, view, position, id) -> {
            MediaStoreHelper.LocalSong selectedSong = filteredLocalSongs.get(position);
            Intent intent = new Intent(MainActivity.this, TagEditorActivity.class);
            intent.putExtra("FILE_PATH", selectedSong.filePath);
            intent.putExtra("SONG_TITLE", selectedSong.title);
            intent.putExtra("SONG_ARTIST", selectedSong.artist);
            startActivity(intent);
            return true;
        });

        findViewById(R.id.sortButton).setOnClickListener(v -> showSortDialog());
    }

    private void showSortDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sort);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // Views
        RadioGroup criteriaGroup = dialog.findViewById(R.id.sortCriteriaGroup);
        RadioGroup orderGroup = dialog.findViewById(R.id.sortOrderGroup);
        MaterialSwitch switchHideLyrics = dialog.findViewById(R.id.switchHideLyricsFilter);
        
        MaterialButton btnApply = dialog.findViewById(R.id.btnApplySort);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelSort);

        // Load Defaults
        criteriaGroup.check(currentSortCriteria);
        orderGroup.check(currentSortOrder);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean currentHideState = prefs.getBoolean(KEY_HIDE_LYRICS, false);
        switchHideLyrics.setChecked(currentHideState);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnApply.setOnClickListener(v -> {
            // Get new Values
            int newCriteria = criteriaGroup.getCheckedRadioButtonId();
            int newOrder = orderGroup.getCheckedRadioButtonId();
            boolean newHideState = switchHideLyrics.isChecked();

            // Save Preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_HIDE_LYRICS, newHideState);
            editor.apply(); // Key Hide Lyrics saved

            // Update local vars
            currentSortCriteria = newCriteria;
            currentSortOrder = newOrder;
            saveSortPreferences();

            // Logic: If filter changed, we MUST re-scan (loadLocalSongs). 
            // If only sort changed, we just re-sort (applyCurrentSort).
            if (newHideState != currentHideState) {
                // Filter Changed -> Full Refresh
                allLocalSongs.clear();
                filteredLocalSongs.clear();
                localAdapter.notifyDataSetChanged();
                songLoading.setVisibility(View.VISIBLE);
                loadLocalSongs();
            } else {
                // Only Sort Changed
                applyCurrentSort();
                filterLocalSongs(searchEditText.getText().toString());
            }
            
            dialog.dismiss();
        });
        dialog.show();
    }

    private void applyCurrentSort() {
        Comparator<MediaStoreHelper.LocalSong> comparator = null;
        int sortMode = 0;

        if (currentSortCriteria == R.id.rbTitle) {
            comparator = (s1, s2) -> {
                String t1 = s1.title != null ? s1.title.trim() : "";
                String t2 = s2.title != null ? s2.title.trim() : "";
                return t1.compareToIgnoreCase(t2);
            };
            sortMode = 0;
        } else if (currentSortCriteria == R.id.rbArtist) {
            comparator = (s1, s2) -> {
                String a1 = s1.artist != null ? s1.artist.trim() : "";
                String a2 = s2.artist != null ? s2.artist.trim() : "";
                return a1.compareToIgnoreCase(a2);
            };
            sortMode = 2;
        } else if (currentSortCriteria == R.id.rbDateAdded) {
            comparator = (s1, s2) -> Long.compare(s1.dateAdded, s2.dateAdded);
            sortMode = 1;
        }

        if (localAdapter != null) localAdapter.setSortMode(sortMode);
        if (comparator != null) {
            if (currentSortOrder == R.id.rbDescending) comparator = Collections.reverseOrder(comparator);
            Collections.sort(allLocalSongs, comparator);
        }
    }

    private Bitmap getBitmapFromImageView(ImageView view) {
        if (view == null || view.getDrawable() == null) return null;
        Drawable drawable = view.getDrawable();
        if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    drawable.getIntrinsicWidth() <= 0 ? 100 : drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight() <= 0 ? 100 : drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) { return null; }
    }

    public void openLyricsActivity(Song apiSong, MediaStoreHelper.LocalSong localSong) {
        Intent intent = new Intent(this, LyricsActivity.class);
        intent.putExtra("SONG_ID", apiSong.getId());
        intent.putExtra("SONG_TITLE", apiSong.getSongName());
        intent.putExtra("SONG_ARTIST", apiSong.getArtistName());
        intent.putExtra("SONG_ARTWORK", apiSong.getArtwork());
        intent.putExtra("SONG_ALBUM", apiSong.getAlbumName());
        long durationMs = parseDurationToMillis(apiSong.getDuration());
        intent.putExtra("SONG_DURATION", durationMs);

        if (localSong.filePath != null) intent.putExtra("SONG_FILE_PATH", localSong.filePath);
        if (localSong.fileUri != null) intent.putExtra("SONG_FILE_URI", localSong.fileUri);
        startActivity(intent);
    }

    private long parseDurationToMillis(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) return 0;
        try {
            durationStr = durationStr.replace("Duration: ", "").trim();
            String[] parts = durationStr.split(":");
            long totalSeconds = 0;
            if (parts.length == 2) {
                totalSeconds = (Integer.parseInt(parts[0]) * 60) + Integer.parseInt(parts[1]);
            } else if (parts.length == 3) {
                totalSeconds = (Integer.parseInt(parts[0]) * 3600) + (Integer.parseInt(parts[1]) * 60) + Integer.parseInt(parts[2]);
            }
            return totalSeconds * 1000;
        } catch (NumberFormatException e) { return 0; }
    }

    private void checkPermissionAndOnboard() {
        if (isShowingSheet) return;
        if (!permissionManager.hasStoragePermission()) {
            isShowingSheet = true;
            nowPlayingCard.postDelayed(this::showStoragePermissionSheet, 500);
        } else {
            if (allLocalSongs.isEmpty()) loadLocalSongs();
            if (!mediaSessionHandler.hasNotificationAccess()) {
                isShowingSheet = true;
                nowPlayingCard.postDelayed(this::showNotificationPermissionSheet, 500);
            }
        }
    }

    private void showStoragePermissionSheet() {
        if (isFinishing()) return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_permission, null);
        bottomSheetDialog.setContentView(sheetView);
        if (bottomSheetDialog.getWindow() != null) bottomSheetDialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet).setBackgroundResource(android.R.color.transparent);
        MaterialButton btnGrant = sheetView.findViewById(R.id.btnGrantAccess);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);
        btnGrant.setOnClickListener(v -> { bottomSheetDialog.dismiss(); permissionManager.requestStoragePermission(); });
        btnNotNow.setOnClickListener(v -> { bottomSheetDialog.dismiss(); if (!mediaSessionHandler.hasNotificationAccess()) showNotificationPermissionSheet(); else isShowingSheet = false; });
        bottomSheetDialog.setOnDismissListener(dialog -> isShowingSheet = false);
        bottomSheetDialog.show();
    }

    private void showNotificationPermissionSheet() {
        if (isFinishing()) return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_notification_access, null);
        bottomSheetDialog.setContentView(sheetView);
        if (bottomSheetDialog.getWindow() != null) bottomSheetDialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet).setBackgroundResource(android.R.color.transparent);
        MaterialButton btnConnect = sheetView.findViewById(R.id.btnConnectApps);
        TextView btnTroubleshoot = sheetView.findViewById(R.id.btnTroubleshoot);
        MaterialButton btnNotNow = sheetView.findViewById(R.id.btnNotNow);
        btnConnect.setOnClickListener(v -> { bottomSheetDialog.dismiss(); mediaSessionHandler.requestNotificationAccess(); });
        btnTroubleshoot.setOnClickListener(v -> { bottomSheetDialog.dismiss(); mediaSessionHandler.openAppInfo(); });
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults);
    }
}
