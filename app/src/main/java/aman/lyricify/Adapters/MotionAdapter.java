package aman.lyricify;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MotionAdapter extends RecyclerView.Adapter<MotionAdapter.ViewHolder> {

    private final List<MotionRepository.MotionOption> list;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onDownloadClick(MotionRepository.MotionOption item);
    }

    public MotionAdapter(List<MotionRepository.MotionOption> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_motion_cover, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MotionRepository.MotionOption item = list.get(position);

        holder.resText.setText(item.width + " x " + item.height);
        
        
        String meta = "Size : " + item.sizeText;
        if (item.bitrateText != null && !item.bitrateText.isEmpty()) {
            meta += " • " + "Bitrate : " + item.bitrateText;
        }
        holder.sizeText.setText(meta);

        holder.typeBadge.setText(item.type); // "Square" or "Tall"

        // Color code the badge
        if ("Square".equals(item.type)) {
            holder.typeBadge.setBackgroundColor(0xFF4CAF50); // Green
        } else {
            holder.typeBadge.setBackgroundColor(0xFF2196F3); // Blue
        }

        holder.downloadBtn.setOnClickListener(v -> listener.onDownloadClick(item));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView resText, sizeText, typeBadge;
        ImageView downloadBtn;

        ViewHolder(View v) {
            super(v);
            resText = v.findViewById(R.id.resText);
            sizeText = v.findViewById(R.id.sizeText);
            typeBadge = v.findViewById(R.id.typeBadge);
            downloadBtn = v.findViewById(R.id.downloadBtn);
        }
    }
}
