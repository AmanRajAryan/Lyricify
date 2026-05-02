package aman.lyricify.glide;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import java.io.InputStream;

@GlideModule
public class LyricifyGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.append(AudioFileCover.class, InputStream.class, new AudioCoverLoader.Factory());
    }

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDefaultRequestOptions(
            new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888)
        );
    }
    
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
