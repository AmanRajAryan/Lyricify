package aman.lyricify;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import aman.taglib.TagLib;

/**
 * Manages metadata reading and display operations
 */
public class MetadataManager {
    
    private final WeakReference<Context> contextRef;
    
    public MetadataManager(Context context) {
        this.contextRef = new WeakReference<>(context);
    }
    
    /**
     * Show metadata dialog with artwork and tags
     */
    public void showMetadataDialog(String filePath) {
        Context context = contextRef.get();
        if (context == null) {
            return;
        }
        
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(context, "No file available to read metadata", Toast.LENGTH_SHORT).show();
            return;
        }
        
        TagLib tagLib = new TagLib();
        HashMap<String, String> metadataMap = tagLib.getMetadata(filePath);
        TagLib.Artwork[] artworks = tagLib.getArtwork(filePath);
        
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        
        // Add artwork if available
        if (artworks != null && artworks.length > 0) {
            addArtworkToLayout(context, layout, artworks);
        } else {
            TextView noArtText = new TextView(context);
            noArtText.setText("No artwork embedded in this file");
            noArtText.setPadding(0, 0, 0, 20);
            layout.addView(noArtText);
        }
        
        // Add metadata text
        TextView metadataText = new TextView(context);
        StringBuilder sb = new StringBuilder();
        for (String key : metadataMap.keySet()) {
            sb.append(key).append(": ").append(metadataMap.get(key)).append("\n");
        }
        metadataText.setText(sb.toString());
        layout.addView(metadataText);
        
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        
        new AlertDialog.Builder(context)
                .setTitle("Metadata & Artwork")
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .show();
    }
    
    /**
     * Add artwork images to layout
     */
    private void addArtworkToLayout(Context context, LinearLayout layout, TagLib.Artwork[] artworks) {
        for (int i = 0; i < artworks.length; i++) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                artworks[i].data, 0, artworks[i].data.length
            );
            
            if (bitmap != null) {
                ImageView imageView = new ImageView(context);
                imageView.setImageBitmap(bitmap);
                imageView.setAdjustViewBounds(true);
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 400
                );
                params.setMargins(0, 0, 0, 10);
                imageView.setLayoutParams(params);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                layout.addView(imageView);
                
                TextView infoText = new TextView(context);
                infoText.setText(
                    "Artwork " + (i + 1) + ": " + 
                    bitmap.getWidth() + "x" + bitmap.getHeight() + 
                    " - " + artworks[i].mimeType
                );
                infoText.setPadding(0, 0, 0, 20);
                layout.addView(infoText);
            }
        }
    }
    
    /**
     * Show file path dialog with copy option
     */
    public void showFilePathDialog(String path) {
        Context context = contextRef.get();
        if (context == null) return;
        
        new AlertDialog.Builder(context)
                .setTitle("File Location")
                .setMessage(path)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) 
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("File Path", path));
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}