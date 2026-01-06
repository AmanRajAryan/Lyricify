package aman.lyricify;

import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class TagEditorLyricsHelper {
    
    private final TextInputEditText lyricsMultiEditText;
    private final TextView labelElrc;
    private final TextView labelTtml;
    private final Runnable updateRestoreStateCallback;

    private boolean isTtmlMode = false;
    private String currentElrcContent = "";
    private String currentTtmlContent = "";
    private String originalLyricsTagContent = "";

    public TagEditorLyricsHelper(TextInputEditText lyricsMultiEditText,
                                 TextView labelElrc,
                                 TextView labelTtml,
                                 Runnable updateRestoreStateCallback) {
        this.lyricsMultiEditText = lyricsMultiEditText;
        this.labelElrc = labelElrc;
        this.labelTtml = labelTtml;
        this.updateRestoreStateCallback = updateRestoreStateCallback;
    }

    public void setOriginalLyrics(String content) {
        this.originalLyricsTagContent = content;
        if (content.trim().startsWith("<")) {
            this.isTtmlMode = true;
            this.currentTtmlContent = content;
            this.currentElrcContent = "";
        } else {
            this.isTtmlMode = false;
            this.currentElrcContent = content;
            this.currentTtmlContent = "";
        }
        updateSwapperUI();
    }

    public void toggleLyricsMode() {
        String visibleText = lyricsMultiEditText.getText().toString();
        if (isTtmlMode) {
            currentTtmlContent = visibleText;
        } else {
            currentElrcContent = visibleText;
        }
        isTtmlMode = !isTtmlMode;
        updateSwapperUI();
    }

    public void updateSwapperUI() {
        if (isTtmlMode) {
            labelElrc.setTextColor(Color.parseColor("#80FFFFFF"));
            labelElrc.setTypeface(null, Typeface.NORMAL);
            labelTtml.setTextColor(Color.WHITE);
            labelTtml.setTypeface(null, Typeface.BOLD);
            lyricsMultiEditText.setText(currentTtmlContent);
            ((TextInputLayout) lyricsMultiEditText.getParent().getParent()).setHint("TTML");
        } else {
            labelElrc.setTextColor(Color.WHITE);
            labelElrc.setTypeface(null, Typeface.BOLD);
            labelTtml.setTextColor(Color.parseColor("#80FFFFFF"));
            labelTtml.setTypeface(null, Typeface.NORMAL);
            lyricsMultiEditText.setText(currentElrcContent);
            ((TextInputLayout) lyricsMultiEditText.getParent().getParent()).setHint("ELRC Multi-Person");
        }
        updateRestoreStateCallback.run();
    }

    public void updateCurrentContentFromView() {
        String visible = lyricsMultiEditText.getText().toString();
        if (isTtmlMode) currentTtmlContent = visible;
        else currentElrcContent = visible;
    }

    public boolean isTtmlMode() { return isTtmlMode; }
    public String getOriginalLyricsTagContent() { return originalLyricsTagContent; }
    
    public void setCurrentElrcContent(String content) { this.currentElrcContent = content; }
    public void setCurrentTtmlContent(String content) { this.currentTtmlContent = content; }
}
