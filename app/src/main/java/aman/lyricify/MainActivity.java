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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;

import aman.lyricify.Utilities.Constants;
import aman.taglib.TagLib;

public class MainActivity extends AppCompatActivity {

    FloatingActionButton youlyPlayerFab;

    private TextInputEditText searchEditText;
    private RecyclerView songReclyclerView;
    private ProgressBar songLoading;
    private CardView nowPlayingCard;
    private ImageView nowPlayingArtwork;
    private TextView nowPlayingTitle, nowPlayingArtist, nowPlayingFilePath;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View menuButton;

    private List<MediaStoreHelper.LocalSong> allLocalSongs = new ArrayList<>();
    private List<MediaStoreHelper.LocalSong> filteredLocalSongs = new ArrayList<>();
    private LocalSongAdapter localAdapter;

    private MediaSessionHandler mediaSessionHandler;
    private NowPlayingManager nowPlayingManager;
    private PermissionManager permissionManager;

    private boolean isShowingSheet = false;

    private int currentSortCriteria = R.id.rbTitle;
    private int currentSortOrder = R.id.rbAscending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean useBottomUi = prefs.getBoolean(Constants.KEY_BOTTOM_UI, false);

        if (useBottomUi) {
            setContentView(R.layout.activity_main_bottom);
        } else {
            setContentView(R.layout.activity_main);
        }

        splashScreen.setOnExitAnimationListener(
                splashScreenView -> {
                    try {
                        View iconView = splashScreenView.getIconView();
                        if (iconView == null) {
                            splashScreenView.remove();
                            return;
                        }
                        ObjectAnimator scaleX =
                                ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0f);
                        ObjectAnimator scaleY =
                                ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0f);
                        ObjectAnimator alpha =
                                ObjectAnimator.ofFloat(
                                        splashScreenView.getView(), View.ALPHA, 1f, 0f);

                        android.animation.AnimatorSet animatorSet =
                                new android.animation.AnimatorSet();
                        animatorSet.playTogether(scaleX, scaleY, alpha);
                        animatorSet.setDuration(500);
                        animatorSet.setInterpolator(new AnticipateInterpolator());
                        animatorSet.addListener(
                                new android.animation.AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(
                                            android.animation.Animator animation) {
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
        youlyPlayerFab.setOnClickListener(
                v -> {
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
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        currentSortCriteria = prefs.getInt(Constants.KEY_SORT_CRITERIA, R.id.rbTitle);
        currentSortOrder = prefs.getInt(Constants.KEY_SORT_ORDER, R.id.rbAscending);
        if (currentSortCriteria != R.id.rbTitle
                && currentSortCriteria != R.id.rbArtist
                && currentSortCriteria != R.id.rbDateAdded) {
            currentSortCriteria = R.id.rbTitle;
        }
    }

    private void saveSortPreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(Constants.KEY_SORT_CRITERIA, currentSortCriteria);
        editor.putInt(Constants.KEY_SORT_ORDER, currentSortOrder);
        editor.apply();
    }

    private void initializeViews() {
        searchEditText = findViewById(R.id.searchEditText);
        songReclyclerView = findViewById(R.id.songRecyclerView);
        songReclyclerView.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(this));

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
        songReclyclerView.setAdapter(localAdapter);
        FastScroller fastScroller = findViewById(R.id.fastScroller);
        fastScroller.attachToRecyclerView(songReclyclerView);

        songReclyclerView.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(
                            @NonNull RecyclerView recyclerView, int newState) {
                        if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                            localAdapter.setFlinging(true);
                        } else {
                            if (localAdapter != null) {
                                localAdapter.setFlinging(false);

                                // CRITICAL: Force the visible rows to reload now that we've
                                // stopped.
                                localAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                });
    }

    private void setupDrawer() {
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(
                    item -> {
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

                        new IdentifySongDialog(MainActivity.this, tempSong, currentArt).show();
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

    private boolean hasLrcFile(String audioPath) {
    if (audioPath == null) return false;

    int lastDot = audioPath.lastIndexOf('.');
    if (lastDot == -1) return false;

    String basePath = audioPath.substring(0, lastDot);

    return new File(basePath + ".lrc").exists()
        || new File(basePath + ".ttml").exists();
}
    
    
    private void loadLocalSongs() {
        if (!permissionManager.hasStoragePermission()) return;

        if (allLocalSongs.isEmpty()) songLoading.setVisibility(View.VISIBLE);

        new Thread(
                        () -> {
                            List<MediaStoreHelper.LocalSong> allDeviceSongs =
                                    MediaStoreHelper.getAllSongs(this);

                            SharedPreferences prefs =
                                    getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
                            boolean scanAll = prefs.getBoolean(Constants.KEY_SCAN_ALL, true);
                            boolean blacklistEnabled =
                                    prefs.getBoolean(Constants.KEY_BLACKLIST_ENABLED, false);

                            boolean hideLyrics = prefs.getBoolean(Constants.KEY_HIDE_LYRICS, false);
                            boolean hideLrc = prefs.getBoolean(Constants.KEY_HIDE_LRC, false);

                            Set<String> whitelistPaths =
                                    prefs.getStringSet(Constants.KEY_WHITELIST, new HashSet<>());
                            Set<String> blacklistPaths =
                                    prefs.getStringSet(Constants.KEY_BLACKLIST, new HashSet<>());

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

                            List<MediaStoreHelper.LocalSong> finalFilteredList = new ArrayList<>();

                            LyricsCacheManager cacheManager = null;
                            if (hideLyrics) {
                                cacheManager = LyricsCacheManager.getInstance(this);
                            }

                            for (MediaStoreHelper.LocalSong song : folderFilteredList) {
                                boolean shouldInclude = true;

                                if (hideLyrics && cacheManager != null) {
                                    if (cacheManager.hasLyrics(song.filePath)) {
                                        shouldInclude = false;
                                    }
                                }

                                if (shouldInclude && hideLrc) {
                                    if (hasLrcFile(song.filePath)) {
                                        shouldInclude = false;
                                    }
                                }

                                if (shouldInclude) {
                                    finalFilteredList.add(song);
                                }
                            }

                            if (hideLyrics && cacheManager != null) {
                                cacheManager.saveCache(this);
                            }

                            runOnUiThread(
                                    () -> {
                                        allLocalSongs.clear();
                                        allLocalSongs.addAll(finalFilteredList);
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
                if ((song.title != null && song.title.toLowerCase().contains(lowerQuery))
                        || (song.artist != null
                                && song.artist.toLowerCase().contains(lowerQuery))) {
                    filteredLocalSongs.add(song);
                }
            }
        }

        if (localAdapter != null) {
            localAdapter.updateData(filteredLocalSongs);
        }
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

        localAdapter.setOnItemClickListener(
                (view, song, position) -> {
                    Bitmap extractedBitmap = null;
                    try {
                        ImageView artView = view.findViewById(R.id.localArtwork);
                        extractedBitmap = getBitmapFromImageView(artView);
                    } catch (Exception ignored) {
                    }

                    new IdentifySongDialog(MainActivity.this, song, extractedBitmap).show();
                });

        localAdapter.setOnItemLongClickListener(
                (view, song, position) -> {
                    Intent intent = new Intent(MainActivity.this, TagEditorActivity.class);
                    intent.putExtra("FILE_PATH", song.filePath);
                    intent.putExtra("SONG_TITLE", song.title);
                    intent.putExtra("SONG_ARTIST", song.artist);
                    startActivity(intent);
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
        MaterialSwitch switchHideLyrics = dialog.findViewById(R.id.switchHideLyricsFilter);

        MaterialButton btnApply = dialog.findViewById(R.id.btnApplySort);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancelSort);

        criteriaGroup.check(currentSortCriteria);
        orderGroup.check(currentSortOrder);

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean currentHideState = prefs.getBoolean(Constants.KEY_HIDE_LYRICS, false);
        switchHideLyrics.setChecked(currentHideState);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnApply.setOnClickListener(
                v -> {
                    int newCriteria = criteriaGroup.getCheckedRadioButtonId();
                    int newOrder = orderGroup.getCheckedRadioButtonId();
                    boolean newHideState = switchHideLyrics.isChecked();

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(Constants.KEY_HIDE_LYRICS, newHideState);
                    editor.apply();

                    currentSortCriteria = newCriteria;
                    currentSortOrder = newOrder;
                    saveSortPreferences();

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
            sortMode = 2;
        } else if (currentSortCriteria == R.id.rbDateAdded) {
            comparator = (s1, s2) -> Long.compare(s1.dateAdded, s2.dateAdded);
            sortMode = 1;
        }

        if (localAdapter != null) localAdapter.setSortMode(sortMode);
        if (comparator != null) {
            if (currentSortOrder == R.id.rbDescending)
                comparator = Collections.reverseOrder(comparator);
            Collections.sort(allLocalSongs, comparator);
        }
    }

    private Bitmap getBitmapFromImageView(ImageView view) {
        if (view == null || view.getDrawable() == null) return null;
        Drawable drawable = view.getDrawable();
        if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
        try {
            Bitmap bitmap =
                    Bitmap.createBitmap(
                            drawable.getIntrinsicWidth() <= 0 ? 100 : drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight() <= 0
                                    ? 100
                                    : drawable.getIntrinsicHeight(),
                            Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
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
                totalSeconds =
                        (Integer.parseInt(parts[0]) * 3600)
                                + (Integer.parseInt(parts[1]) * 60)
                                + Integer.parseInt(parts[2]);
            }
            return totalSeconds * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
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
        BottomSheetDialog bottomSheetDialog =
                new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_permission, null);
        bottomSheetDialog.setContentView(sheetView);
        if (bottomSheetDialog.getWindow() != null)
            bottomSheetDialog
                    .getWindow()
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
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
                    if (!mediaSessionHandler.hasNotificationAccess())
                        showNotificationPermissionSheet();
                    else isShowingSheet = false;
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
        if (bottomSheetDialog.getWindow() != null)
            bottomSheetDialog
                    .getWindow()
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet)
                    .setBackgroundResource(android.R.color.transparent);
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
