package aman.lyricify;

import java.util.ArrayList;
import java.util.List;

public class LyricLine {
    public long startTime;
    public long endTime;
    public List<LyricWord> words = new ArrayList<>();
    
    public int vocalType = 1; // 1 = v1 (default/white), 2 = v2 (cyan/duet)
    public boolean isWordSynced = false; // TRUE = <mm:ss>, FALSE = [mm:ss] only

    public LyricLine(long startTime) {
        this.startTime = startTime;
    }
}
