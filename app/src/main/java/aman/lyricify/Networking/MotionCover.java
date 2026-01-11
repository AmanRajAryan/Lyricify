package aman.lyricify;

public class MotionCover {
    public String url;
    public String type; // e.g., "video/mp4"
    public int width;
    public int height;
    public String name; // e.g., "1080x1080 (H.264)"

    public MotionCover(String url, String type, int width, int height, String name) {
        this.url = url;
        this.type = type;
        this.width = width;
        this.height = height;
        this.name = name;
    }
    
    // Helper to display in UI later
    public String getResolution() {
        return width + "x" + height;
    }
}
