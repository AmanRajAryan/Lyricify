package aman.lyricify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private static final int REQUEST_CODE_WHITELIST = 101;
    private static final int REQUEST_CODE_BLACKLIST = 102;

    private static final String PREFS_NAME = "LyricifyPrefs";
    private static final String KEY_WHITELIST = "music_folders";
    private static final String KEY_BLACKLIST = "blacklist_folders";
    private static final String KEY_SCAN_ALL = "scan_all_folders";
    private static final String KEY_BLACKLIST_ENABLED = "blacklist_enabled";

    private LinearLayout whitelistContainer, blacklistContainer;
    private MaterialSwitch switchScanAll, switchBlacklist;
    
    private FolderAdapter whitelistAdapter, blacklistAdapter;
    private List<String> whitelistFolders, blacklistFolders;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: SettingsActivity started");
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        whitelistContainer = findViewById(R.id.whitelistContainer);
        blacklistContainer = findViewById(R.id.blacklistContainer);
        
        switchScanAll = findViewById(R.id.switchScanAll);
        switchBlacklist = findViewById(R.id.switchBlacklist);

        RecyclerView whitelistRecycler = findViewById(R.id.whitelistRecyclerView);
        RecyclerView blacklistRecycler = findViewById(R.id.blacklistRecyclerView);
        
        whitelistRecycler.setLayoutManager(new LinearLayoutManager(this));
        blacklistRecycler.setLayoutManager(new LinearLayoutManager(this));

        loadSettings();

        whitelistAdapter = new FolderAdapter(whitelistFolders, pos -> removeFolder(pos, true));
        blacklistAdapter = new FolderAdapter(blacklistFolders, pos -> removeFolder(pos, false));
        
        whitelistRecycler.setAdapter(whitelistAdapter);
        blacklistRecycler.setAdapter(blacklistAdapter);

        setupListeners();
        updateUiVisibility();
    }

    private void loadSettings() {
        whitelistFolders = new ArrayList<>(prefs.getStringSet(KEY_WHITELIST, new HashSet<>()));
        blacklistFolders = new ArrayList<>(prefs.getStringSet(KEY_BLACKLIST, new HashSet<>()));

        // Scan All defaults to TRUE
        boolean isScanAll = prefs.getBoolean(KEY_SCAN_ALL, true);
        boolean isBlacklist = prefs.getBoolean(KEY_BLACKLIST_ENABLED, false);

        Log.d(TAG, "loadSettings: ScanAll=" + isScanAll + ", BlacklistEnabled=" + isBlacklist);

        switchScanAll.setChecked(isScanAll);
        switchBlacklist.setChecked(isBlacklist);
    }

    private void setupListeners() {
        switchScanAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "ScanAll Switch Changed: " + isChecked);
            prefs.edit().putBoolean(KEY_SCAN_ALL, isChecked).apply();
            updateUiVisibility();
        });

        switchBlacklist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "Blacklist Switch Changed: " + isChecked);
            prefs.edit().putBoolean(KEY_BLACKLIST_ENABLED, isChecked).apply();
            updateUiVisibility();
        });

        findViewById(R.id.btnAddWhitelist).setOnClickListener(v -> openDirectoryPicker(REQUEST_CODE_WHITELIST));
        findViewById(R.id.btnAddBlacklist).setOnClickListener(v -> openDirectoryPicker(REQUEST_CODE_BLACKLIST));
    }

    private void updateUiVisibility() {
        boolean isScanAll = switchScanAll.isChecked();
        boolean isBlacklist = switchBlacklist.isChecked();

        whitelistContainer.setVisibility(isScanAll ? View.GONE : View.VISIBLE);
        blacklistContainer.setVisibility(isBlacklist ? View.VISIBLE : View.GONE);
    }

    private void openDirectoryPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String path = parsePathFromUri(uri);

            try {
                getContentResolver().takePersistableUriPermission(uri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                Log.e(TAG, "Error taking permissions", e);
            }

            if (requestCode == REQUEST_CODE_WHITELIST) {
                addFolder(path, true);
            } else if (requestCode == REQUEST_CODE_BLACKLIST) {
                addFolder(path, false);
            }
        }
    }

    private void addFolder(String path, boolean isWhitelist) {
        List<String> targetList = isWhitelist ? whitelistFolders : blacklistFolders;
        FolderAdapter targetAdapter = isWhitelist ? whitelistAdapter : blacklistAdapter;
        String prefsKey = isWhitelist ? KEY_WHITELIST : KEY_BLACKLIST;

        if (!targetList.contains(path)) {
            targetList.add(path);
            targetAdapter.notifyItemInserted(targetList.size() - 1);
            saveList(prefsKey, targetList);
        } else {
            Toast.makeText(this, "Folder already added", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeFolder(int position, boolean isWhitelist) {
        List<String> targetList = isWhitelist ? whitelistFolders : blacklistFolders;
        FolderAdapter targetAdapter = isWhitelist ? whitelistAdapter : blacklistAdapter;
        String prefsKey = isWhitelist ? KEY_WHITELIST : KEY_BLACKLIST;

        targetList.remove(position);
        targetAdapter.notifyItemRemoved(position);
        saveList(prefsKey, targetList);
    }

    private void saveList(String key, List<String> list) {
        prefs.edit().putStringSet(key, new HashSet<>(list)).apply();
    }

    private String parsePathFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "";
        String[] split = path.split(":");
        
        if (split.length > 1 && path.contains("primary")) {
            return Environment.getExternalStorageDirectory().toString() + "/" + split[1];
        } 
        else if (split.length > 1) {
            return "/storage/" + split[0].replace("/tree/", "") + "/" + split[1];
        }
        return path;
    }

    private static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
        private final List<String> paths;
        private final OnRemoveListener listener;

        interface OnRemoveListener {
            void onRemove(int position);
        }

        FolderAdapter(List<String> paths, OnRemoveListener listener) {
            this.paths = paths;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_folder, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.pathText.setText(paths.get(position));
            holder.removeBtn.setOnClickListener(v -> listener.onRemove(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return paths.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView pathText;
            ImageButton removeBtn;

            ViewHolder(View itemView) {
                super(itemView);
                pathText = itemView.findViewById(R.id.folderPathText);
                removeBtn = itemView.findViewById(R.id.btnRemoveFolder);
            }
        }
    }
}
