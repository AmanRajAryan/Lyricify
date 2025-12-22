package aman.lyricify;

import java.util.ArrayList;
import java.util.List;

public class LyricLine {
    public long startTime;
    public long endTime;
    public List<LyricWord> words = new ArrayList<>();
    
    public int vocalType = 1; // 1 = v1, 2 = v2
    public boolean isWordSynced = false; 
    public boolean isBackground = false; // NEW: Flag for Background Vocals

    public LyricLine(long startTime) {
        this.startTime = startTime;
    }
}
