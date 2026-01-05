package aman.lyricify.glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import java.io.InputStream;

public class AudioCoverLoader implements ModelLoader<AudioFileCover, InputStream> {

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull AudioFileCover model, int width, int height, @NonNull Options options) {
        // We use the model itself as the cache key part, and its signature field for invalidation
        return new LoadData<>(new ObjectKey(model), new AudioCoverFetcher(model));
    }

    @Override
    public boolean handles(@NonNull AudioFileCover model) {
        return true;
    }

    public static class Factory implements ModelLoaderFactory<AudioFileCover, InputStream> {
        @NonNull
        @Override
        public ModelLoader<AudioFileCover, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new AudioCoverLoader();
        }

        @Override public void teardown() { }
    }
}
