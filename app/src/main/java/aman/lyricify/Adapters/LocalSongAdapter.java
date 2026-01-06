package aman.lyricify;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import aman.lyricify.glide.AudioFileCover;

public class LocalSongAdapter extends RecyclerView.Adapter<LocalSongAdapter.SongViewHolder> implements SectionIndexer {

    private static final String TAG = "LyricifyGlide";
    private final Context context;
    private final List<MediaStoreHelper.LocalSong> songs;
    private final LayoutInflater inflater;

    // Fling State
    private boolean isFlinging = false;

    // Section Indexing Data
    private HashMap<String, Integer> mapIndex;
    private String[] sections;
    private int currentSortMode = 0;

    // Listener for clicks
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;

    public interface OnItemClickListener {
        void onItemClick(View view, MediaStoreHelper.LocalSong song, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(View view, MediaStoreHelper.LocalSong song, int position);
    }

    public LocalSongAdapter(Context context, List<MediaStoreHelper.LocalSong> songs) {
        this.context = context;
        this.songs = songs;
        this.inflater = LayoutInflater.from(context);
        calculateSections(songs);
    }

    // --- Fling Optimization Setter ---
    public void setFlinging(boolean flinging) {
        this.isFlinging = flinging;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    public void setSortMode(int mode) {
        this.currentSortMode = mode;
        calculateSections(songs);
        notifyDataSetChanged();
    }

    public void updateData(List<MediaStoreHelper.LocalSong> newSongs) {
        calculateSections(newSongs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_local_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        MediaStoreHelper.LocalSong song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    // --- ViewHolder ---
    class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView artworkImageView;
        TextView titleTextView;
        TextView artistTextView;

        SongViewHolder(View itemView) {
            super(itemView);
            artworkImageView = itemView.findViewById(R.id.localArtwork);
            titleTextView = itemView.findViewById(R.id.localTitle);
            artistTextView = itemView.findViewById(R.id.localArtist);
        }

        void bind(MediaStoreHelper.LocalSong song, int position) {
            titleTextView.setText(song.title);
            artistTextView.setText(song.artist);

            // 1. Get File Timestamp (The Source of Truth)
            long lastModified = 0;
            if (song.filePath != null) {
                // This is fast enough for main thread
                lastModified = new File(song.filePath).lastModified();
            }

            // 2. Create Model
            AudioFileCover coverModel = new AudioFileCover(song.filePath, lastModified);

            // 3. Build Request
            RequestBuilder<Drawable> request = Glide.with(context)
                    .load(coverModel)
                    // DATA Strategy: Cache only raw bytes. 
                    // Prevents "Unable to encode AnimatedImageDrawable" crash.
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    // Optional Center Crop: Crops static images, leaves animations alone.
                    // (Your XML ImageView scaleType="centerCrop" handles the rest)
                    .optionalCenterCrop();

            // 4. Fling Optimization
            if (isFlinging) {
                // If moving fast, ONLY load if it's already in memory/disk cache.
                // Do not start TagLib extraction.
                request = request.onlyRetrieveFromCache(true);
            }

            // 5. Load
            request.listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            // Don't log expected cache misses during fling
                            if (!isFlinging) {
                                Log.e(TAG, "FAILED to load: " + song.title, e);
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            // Start animation if valid
                            if (resource instanceof Animatable) {
                                ((Animatable) resource).start();
                            }
                            return false;
                        }
                    })
                    .into(artworkImageView);

            // Click Listeners
            itemView.setOnClickListener(v -> {
                if (itemClickListener != null) itemClickListener.onItemClick(v, song, position);
            });

            itemView.setOnLongClickListener(v -> {
                if (itemLongClickListener != null) itemLongClickListener.onItemLongClick(v, song, position);
                return true;
            });
        }
    }

    // --- Section Indexer Logic ---
    private void calculateSections(List<MediaStoreHelper.LocalSong> songsList) {
        mapIndex = new LinkedHashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.US);

        List<MediaStoreHelper.LocalSong> currentList = new ArrayList<>(songsList);

        for (int i = 0; i < currentList.size(); i++) {
            String sectionHeader;
            MediaStoreHelper.LocalSong song = currentList.get(i);

            if (currentSortMode == 1) { // Date Added
                Date date = new Date(song.dateAdded * 1000L);
                sectionHeader = dateFormat.format(date);
            } else if (currentSortMode == 2) { // Artist
                String artist = song.artist;
                sectionHeader = (artist == null || artist.isEmpty()) ? "#" : artist.trim().substring(0, 1).toUpperCase(Locale.US);
                if (!sectionHeader.matches("[A-Z]")) sectionHeader = "#";
            } else { // Title (Default)
                String title = song.title;
                sectionHeader = (title == null || title.isEmpty()) ? "#" : title.trim().substring(0, 1).toUpperCase(Locale.US);
                if (!sectionHeader.matches("[A-Z]")) sectionHeader = "#";
            }

            if (!mapIndex.containsKey(sectionHeader)) {
                mapIndex.put(sectionHeader, i);
            }
        }
        ArrayList<String> sectionList = new ArrayList<>(mapIndex.keySet());
        sections = new String[sectionList.size()];
        sectionList.toArray(sections);
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sections == null || sections.length == 0) return 0;
        if (sectionIndex >= sections.length) sectionIndex = sections.length - 1;
        if (sectionIndex < 0) sectionIndex = 0;
        return mapIndex.getOrDefault(sections[sectionIndex], 0);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (sections == null || sections.length == 0) return 0;
        for (int i = sections.length - 1; i >= 0; i--) {
            Integer sectionStartPos = mapIndex.get(sections[i]);
            if (sectionStartPos != null && position >= sectionStartPos) return i;
        }
        return 0;
    }
}
