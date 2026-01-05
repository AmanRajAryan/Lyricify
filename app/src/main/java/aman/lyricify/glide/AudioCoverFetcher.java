package aman.lyricify.glide;

import androidx.annotation.NonNull;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import aman.taglib.TagLib;

public class AudioCoverFetcher implements DataFetcher<InputStream> {
    private final AudioFileCover model;
    
    public AudioCoverFetcher(AudioFileCover model) {
        this.model = model;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        // This runs on a background thread automatically managed by Glide
        try {
            TagLib tagLib = new TagLib();
            TagLib.Artwork[] artworks = tagLib.getArtwork(model.filePath);
            
            if (artworks != null && artworks.length > 0 && artworks[0].data != null) {
                // Success! Pass the raw bytes. Glide will auto-detect GIF/WebP/PNG.
                callback.onDataReady(new ByteArrayInputStream(artworks[0].data));
            } else {
                callback.onLoadFailed(new Exception("No artwork found"));
            }
        } catch (Exception e) {
            callback.onLoadFailed(e);
        }
    }

    @Override public void cleanup() { /* InputStreams are auto-closed by Glide usually, but safe to ignore here for ByteArray */ }
    @Override public void cancel() { /* Native calls are hard to cancel */ }
    @NonNull @Override public Class<InputStream> getDataClass() { return InputStream.class; }
    @NonNull @Override public DataSource getDataSource() { return DataSource.LOCAL; }
}
