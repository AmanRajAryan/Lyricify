package aman.lyricify;

import android.graphics.Typeface;

/**
 * Manages font selection and cycling for lyrics display
 */
class FontManager {
    private Typeface currentTypeface = Typeface.DEFAULT;
    private int currentFontIndex = 0;
    
    private static final Typeface[] AVAILABLE_FONTS = {
        Typeface.DEFAULT,
        Typeface.SANS_SERIF,
        Typeface.SERIF,
        Typeface.MONOSPACE,
        Typeface.create("casual", Typeface.NORMAL),
        Typeface.create("cursive", Typeface.NORMAL)
    };
    
    private static final String[] FONT_NAMES = {
        "Default",
        "Sans Serif",
        "Serif",
        "Monospace",
        "Casual",
        "Cursive"
    };
    
    void setCurrentTypeface(Typeface typeface) {
        this.currentTypeface = typeface;
    }
    
    Typeface getCurrentTypeface() {
        return currentTypeface;
    }
    
    void cycleFont() {
        currentFontIndex = (currentFontIndex + 1) % AVAILABLE_FONTS.length;
        currentTypeface = AVAILABLE_FONTS[currentFontIndex];
    }
    
    String getCurrentFontName() {
        return FONT_NAMES[currentFontIndex];
    }
}