package aman.lyricify;


import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.*;
import android.widget.*;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView; // Import added
import java.util.ArrayList;

public class SongAdapter extends ArrayAdapter<Song> {

    public SongAdapter(Context context, ArrayList<Song> songs) {
        super(context, 0, songs);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Song song = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.song_item, parent, false);
        }

        ImageView artworkImageView = convertView.findViewById(R.id.artworkImageView);
        TextView songNameTextView = convertView.findViewById(R.id.songNameTextView);
        TextView artistNameTextView = convertView.findViewById(R.id.artistNameTextView);
        ImageView arrowIcon = convertView.findViewById(R.id.arrowIcon);
        LinearLayout detailsLayout = convertView.findViewById(R.id.detailsLayout);
        
        TextView matchPercentageTextView = convertView.findViewById(R.id.matchPercentageTextView);
        TextView lyricsStatusTextView = convertView.findViewById(R.id.lyricsStatusTextView);
        // New: Container for the colored badge
        MaterialCardView statusBadgeCard = convertView.findViewById(R.id.statusBadgeCard); 

        TextView albumNameTextView = convertView.findViewById(R.id.albumNameTextView);
        TextView releaseDateTextView = convertView.findViewById(R.id.releaseDateTextView);
        TextView durationTextView = convertView.findViewById(R.id.durationTextView);
        TextView contentRatingTextView = convertView.findViewById(R.id.contentRatingTextView);

        songNameTextView.setText(song.getSongName());
        artistNameTextView.setText(song.getArtistName());
        albumNameTextView.setText("Album: " + song.getAlbumName());
        releaseDateTextView.setText("Release: " + song.getReleaseDate());
        durationTextView.setText("Duration: " + song.getDuration());
        contentRatingTextView.setText("Rating: " + song.getContentRating());

        // Display match percentage (Neutral styling)
        int matchScore = song.getMatchScore();
        if (matchScore > 0) {
            matchPercentageTextView.setVisibility(View.VISIBLE);
            matchPercentageTextView.setText(matchScore + "% Match");
        } else {
            matchPercentageTextView.setVisibility(View.GONE);
        }

        // --- UPDATED LYRICS STATUS INDICATOR LOGIC ---
        // Colors the CardView instead of the Text background
        if (statusBadgeCard != null && lyricsStatusTextView != null) {
            statusBadgeCard.setVisibility(View.VISIBLE);

            if (song.hasTimeSyncedLyrics()) {
                // "SYNCED" - Confirms line sync
                lyricsStatusTextView.setText("SYNCED");
                // Material Green 600
                statusBadgeCard.setCardBackgroundColor(Color.parseColor("#43A047")); 
            } else if (song.hasLyrics()) {
                // "LYRICS" - Confirms plain text only
                lyricsStatusTextView.setText("LYRICS");
                // Material Orange 800
                statusBadgeCard.setCardBackgroundColor(Color.parseColor("#EF6C00")); 
            } else {
                // "NONE" - No lyrics data found
                lyricsStatusTextView.setText("NONE");
                // Material Red 700
                statusBadgeCard.setCardBackgroundColor(Color.parseColor("#D32F2F")); 
            }
        }

        String artworkUrl = song.getArtwork().replace("{w}", "200").replace("{h}", "200").replace("{f}", "jpg");
        Glide.with(getContext()).load(artworkUrl).into(artworkImageView);

        // Animate expand/collapse
        arrowIcon.setOnClickListener(v -> {
            if (detailsLayout.getVisibility() == View.GONE) {
                expandView(detailsLayout);
                arrowIcon.animate()
                        .rotation(180)
                        .setDuration(300)
                        .setInterpolator(new OvershootInterpolator())
                        .start();
            } else {
                collapseView(detailsLayout);
                arrowIcon.animate()
                        .rotation(0)
                        .setDuration(300)
                        .setInterpolator(new AnticipateOvershootInterpolator())
                        .start();
            }
        });

        return convertView;
    }

    private void expandView(View view) {
        view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int targetHeight = view.getMeasuredHeight();

        view.getLayoutParams().height = 0;
        view.setVisibility(View.VISIBLE);

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.setDuration(250);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            view.getLayoutParams().height = value;
            view.requestLayout();
        });

        animator.start();
        view.animate().alpha(1f).setDuration(200).start();
    }

    private void collapseView(View view) {
        int initialHeight = view.getMeasuredHeight();

        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.setDuration(250);
        animator.setInterpolator(new AccelerateInterpolator());

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            view.getLayoutParams().height = value;
            view.requestLayout();
        });

        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setVisibility(View.GONE);
            }
        });

        animator.start();
        view.animate().alpha(0f).setDuration(200).start();
    }
}
