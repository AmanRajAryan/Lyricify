package aman.lyricify;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import aman.lyricify.glide.AudioFileCover;

public class IdentifySongDialog {

    private final Context context;
    private final MediaStoreHelper.LocalSong localSong;
    private final Bitmap overrideBitmap;
    private Dialog dialog;
    
    // Callback for manual mode handling
    private OnSongSelectedListener selectionListener;
    private boolean hideManualButton = false;

    public interface OnSongSelectedListener {
        void onSongSelected(Song song);
    }

    public IdentifySongDialog(Context context, MediaStoreHelper.LocalSong localSong, Bitmap overrideBitmap) {
        this.context = context;
        this.localSong = localSong;
        this.overrideBitmap = overrideBitmap;
    }

    /**
     * Set a listener to handle song selection manually (prevents opening LyricsActivity automatically).
     */
    public void setOnSongSelectedListener(OnSongSelectedListener listener) {
        this.selectionListener = listener;
    }

    /**
     * Hide the "Edit Tags Manually" button (useful when already in Tag Editor).
     */
    public void setHideManualButton(boolean hide) {
        this.hideManualButton = hide;
    }

    public void show() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_api_search);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        initializeViews();
        dialog.show();
    }

    private void initializeViews() {
        ImageView artworkView = dialog.findViewById(R.id.dialogArtwork);
        TextInputEditText editTitle = dialog.findViewById(R.id.editDialogTitle);
        TextInputEditText editArtist = dialog.findViewById(R.id.editDialogArtist);
        MaterialButton btnSearch = dialog.findViewById(R.id.btnDialogSearch);
        ExtendedFloatingActionButton btnManualEdit = dialog.findViewById(R.id.btnManualEdit);
        ProgressBar loading = dialog.findViewById(R.id.dialogLoading);
        ListView apiListView = dialog.findViewById(R.id.dialogListView);
        TextView errorText = dialog.findViewById(R.id.dialogErrorText);

        // UI Configuration
        if (hideManualButton) {
            btnManualEdit.setVisibility(View.GONE);
        }

        // Pre-fill data
        editTitle.setText(localSong.title);
        editArtist.setText(localSong.artist);

        loadArtwork(artworkView);

        // Manual Edit Listener
        btnManualEdit.setOnClickListener(v -> {
            dialog.dismiss();
            openManualTagEditor();
        });

        // Search Logic
        ArrayList<Song> apiResults = new ArrayList<>();
        SongAdapter apiAdapter = new SongAdapter(context, apiResults);
        apiListView.setAdapter(apiAdapter);

        Runnable performSearch = () -> {
            String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
            String artist = editArtist.getText() != null ? editArtist.getText().toString().trim() : "";
            String query = title + " " + artist;

            if (query.isEmpty()) return;

            loading.setVisibility(View.VISIBLE);
            errorText.setVisibility(View.GONE);
            apiResults.clear();
            apiAdapter.notifyDataSetChanged();

            btnSearch.setEnabled(false);
            btnSearch.setText("Searching...");
            btnSearch.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#555555")));

            ApiClient.searchSongs(query, new ApiClient.SearchCallback() {
                @Override
                public void onSuccess(ArrayList<Song> results) {
                    for (Song s : results) {
                        s.calculateMatchScore(query);
                    }
                    Collections.sort(results, (s1, s2) -> Integer.compare(s2.getMatchScore(), s1.getMatchScore()));

                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            apiResults.addAll(results);
                            apiAdapter.notifyDataSetChanged();
                            loading.setVisibility(View.GONE);
                            
                            if (apiResults.isEmpty()) {
                                errorText.setVisibility(View.VISIBLE);
                                errorText.setText("No matches found");
                            }

                            resetSearchButton(btnSearch);
                        });
                    }
                }

                @Override
                public void onFailure(String error) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            loading.setVisibility(View.GONE);
                            errorText.setVisibility(View.VISIBLE);
                            errorText.setText("Error: " + error);
                            resetSearchButton(btnSearch);
                        });
                    }
                }
            });
        };

        btnSearch.setOnClickListener(v -> performSearch.run());

        apiListView.setOnItemClickListener((parent, v, position, id) -> {
            Song apiSong = apiResults.get(position);
            dialog.dismiss();
            
            // LOGIC SPLIT:
            // If a listener is provided (e.g., from TagEditorActivity), use it.
            // Otherwise, do the default action (Open LyricsActivity).
            if (selectionListener != null) {
                selectionListener.onSongSelected(apiSong);
            } else {
                openLyricsActivity(apiSong);
            }
        });

        // Auto-search on open
        performSearch.run();
    }

    private void resetSearchButton(MaterialButton btnSearch) {
        btnSearch.setEnabled(true);
        btnSearch.setText("SEARCH");
        int purple = ContextCompat.getColor(context, R.color.primary_purple);
        btnSearch.setBackgroundTintList(ColorStateList.valueOf(purple));
        btnSearch.setTextColor(Color.BLACK);
    }

    private void loadArtwork(ImageView artworkView) {
        // 1. UPDATED: Prioritize File Path with Custom Loader (Supports Animation)
        if (localSong.filePath != null) {
            long lastModified = 0;
            try {
                lastModified = new File(localSong.filePath).lastModified();
            } catch (Exception ignored) {}

            AudioFileCover coverModel = new AudioFileCover(localSong.filePath, lastModified);

            Glide.with(context)
                    .load(coverModel)
                    // DATA strategy caches the raw bytes, preventing "Encoder" crash on GIFs
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    // optionalCenterCrop allows the ImageView to crop static images
                    // while leaving animated drawables intact to play correctly
                    .optionalCenterCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            // If file loading fails, try fallback to overrides
                            if (overrideBitmap != null) {
                                artworkView.post(() -> artworkView.setImageBitmap(overrideBitmap));
                            }
                            return false; 
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            // Automatically start animation if it's a GIF/WebP
                            if (resource instanceof Animatable) {
                                ((Animatable) resource).start();
                            }
                            return false;
                        }
                    })
                    .into(artworkView);
            return;
        }

        // 2. FALLBACK: Original logic for non-file based songs
        if (overrideBitmap != null) {
            Glide.with(context).load(overrideBitmap).placeholder(R.drawable.ic_music_note).centerCrop().into(artworkView);
        } else if (localSong.albumId > 0) {
            Glide.with(context).load(MediaStoreHelper.getAlbumArtUri(localSong.albumId)).placeholder(R.drawable.ic_music_note).centerCrop().into(artworkView);
        } else if (localSong.fileUri != null) {
            Glide.with(context).load(localSong.fileUri).placeholder(R.drawable.ic_music_note).centerCrop().into(artworkView);
        }
    }

    private void openManualTagEditor() {
        Intent intent = new Intent(context, TagEditorActivity.class);
        intent.putExtra("FILE_PATH", localSong.filePath);
        intent.putExtra("SONG_TITLE", localSong.title);
        intent.putExtra("SONG_ARTIST", localSong.artist);
        context.startActivity(intent);
    }

    private void openLyricsActivity(Song apiSong) {
        if (context instanceof MainActivity) {
            ((MainActivity) context).openLyricsActivity(apiSong, localSong);
        }
    }
}
