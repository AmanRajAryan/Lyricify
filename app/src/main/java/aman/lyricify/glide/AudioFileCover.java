package aman.lyricify.glide;

import androidx.annotation.NonNull;
import java.util.Objects;

public class AudioFileCover {
    public final String filePath;
    public final long signature;

    public AudioFileCover(String filePath, long signature) {
        this.filePath = filePath;
        this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioFileCover that = (AudioFileCover) o;
        return signature == that.signature && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, signature);
    }
}
