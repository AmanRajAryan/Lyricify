package aman.lyricify.glide;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder; // Added
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat; // Added
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions; // Added
import java.io.InputStream;

@GlideModule
public class LyricifyGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Register our custom loader
        registry.append(AudioFileCover.class, InputStream.class, new AudioCoverLoader.Factory());
    }
    
    // --- ADD THIS METHOD ---
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // This allows Glide to use higher quality images and Hardware Bitmaps (saves RAM)
        builder.setDefaultRequestOptions(
            new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888)
        );
    }
    
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
