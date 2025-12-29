package aman.lyricify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FolderAdapter adapter;
    private List<String> folderPaths;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "LyricifyPrefs";
    private static final String KEY_FOLDERS = "scan_folders";

    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        folderPaths = new ArrayList<>(prefs.getStringSet(KEY_FOLDERS, new HashSet<>()));

        recyclerView = findViewById(R.id.foldersRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderAdapter(folderPaths, this::removeFolder);
        recyclerView.setAdapter(adapter);

        MaterialButton btnAdd = findViewById(R.id.btnAddFolder);
        btnAdd.setOnClickListener(v -> openFolderPicker());

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            // Persist permission for future file saving operations
                            try {
                                getContentResolver().takePersistableUriPermission(treeUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            } catch (Exception ignored) {}

                            String path = FileSaver.getPathFromUri(this, treeUri);
                            if (path == null) {
                                // Fallback: try to guess or use raw path from URI for display
                                path = treeUri.getPath(); 
                                // Clean up typical Android path weirdness if possible
                                if(path != null && path.contains(":")) {
                                    path = "/storage/emulated/0/" + path.split(":")[1]; 
                                }
                            }
                            
                            if (path != null && !folderPaths.contains(path)) {
                                folderPaths.add(path);
                                saveFolders();
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
        );
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    private void removeFolder(int position) {
        folderPaths.remove(position);
        saveFolders();
        adapter.notifyItemRemoved(position);
    }

    private void saveFolders() {
        prefs.edit().putStringSet(KEY_FOLDERS, new HashSet<>(folderPaths)).apply();
    }

    // --- Adapter Class ---
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.pathText.setText(paths.get(position));
            holder.btnRemove.setOnClickListener(v -> listener.onRemove(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() { return paths.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView pathText;
            ImageButton btnRemove;

            ViewHolder(View itemView) {
                super(itemView);
                pathText = itemView.findViewById(R.id.folderPathText);
                btnRemove = itemView.findViewById(R.id.btnRemoveFolder);
            }
        }
    }
}