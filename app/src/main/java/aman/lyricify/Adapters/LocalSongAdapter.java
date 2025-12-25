package aman.lyricify;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class LocalSongAdapter extends ArrayAdapter<MediaStoreHelper.LocalSong> implements SectionIndexer {

    private HashMap<String, Integer> mapIndex;
    private String[] sections;
    
    // Sort modes: 0=Title, 1=DateAdded, 2=Artist
    private int currentSortMode = 0; 

    public LocalSongAdapter(Context context, List<MediaStoreHelper.LocalSong> songs) {
        super(context, 0, songs);
        calculateSections(songs);
    }
    
    public void setSortMode(int mode) {
        this.currentSortMode = mode;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        List<MediaStoreHelper.LocalSong> items = new ArrayList<>();
        for (int i = 0; i < getCount(); i++) {
            items.add(getItem(i));
        }
        calculateSections(items);
    }

    private void calculateSections(List<MediaStoreHelper.LocalSong> songs) {
        mapIndex = new LinkedHashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.US);

        for (int i = 0; i < songs.size(); i++) {
            String sectionHeader;
            MediaStoreHelper.LocalSong song = songs.get(i);

            if (currentSortMode == 1) { 
                // DATE ADDED MODE
                Date date = new Date(song.dateAdded * 1000L);
                sectionHeader = dateFormat.format(date);
            } else if (currentSortMode == 2) {
                // ARTIST MODE (A-Z)
                String artist = song.artist;
                if (artist == null || artist.isEmpty()) {
                    sectionHeader = "#";
                } else {
                    String ch = artist.trim().substring(0, 1).toUpperCase(Locale.US);
                    if (ch.matches("[A-Z]")) {
                        sectionHeader = ch;
                    } else {
                        sectionHeader = "#";
                    }
                }
            } else {
                // TITLE MODE (A-Z) - Default
                String title = song.title;
                if (title == null || title.isEmpty()) {
                    sectionHeader = "#";
                } else {
                    String ch = title.trim().substring(0, 1).toUpperCase(Locale.US);
                    if (ch.matches("[A-Z]")) {
                        sectionHeader = ch;
                    } else {
                        sectionHeader = "#";
                    }
                }
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

        // Iterate backwards through sections to find the one containing this position
        for (int i = sections.length - 1; i >= 0; i--) {
            Integer sectionStartPos = mapIndex.get(sections[i]);
            if (sectionStartPos != null && position >= sectionStartPos) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MediaStoreHelper.LocalSong song = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_local_song, parent, false);
        }

        ImageView artworkImageView = convertView.findViewById(R.id.localArtwork);
        TextView titleTextView = convertView.findViewById(R.id.localTitle);
        TextView artistTextView = convertView.findViewById(R.id.localArtist);

        titleTextView.setText(song.title);
        artistTextView.setText(song.artist);

        Uri artworkUri = MediaStoreHelper.getAlbumArtUri(song.albumId);

        Glide.with(getContext())
                .load(artworkUri)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .dontAnimate()
                .into(artworkImageView);

        return convertView;
    }
}
