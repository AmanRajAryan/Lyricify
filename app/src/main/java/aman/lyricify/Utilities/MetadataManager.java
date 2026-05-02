package aman.lyricify;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import aman.taglib.TagLib;

public class MetadataManager {

    private static final String TAG = "MetadataManager";
    private final WeakReference<Context> contextRef;

    public MetadataManager(Context context) {
        this.contextRef = new WeakReference<>(context);
    }

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

        if (artworks != null && artworks.length > 0) {
            addArtworkToLayout(context, layout, artworks);
        } else {
            TextView noArtText = new TextView(context);
            noArtText.setText("No artwork embedded in this file");
            noArtText.setPadding(0, 0, 0, 20);
            layout.addView(noArtText);
        }

        HorizontalScrollView hScrollView = new HorizontalScrollView(context);
        TextView metadataText = new TextView(context);
        metadataText.setTextIsSelectable(true);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (String key : metadataMap.keySet()) {
            int start = ssb.length();
            ssb.append(key);
            int end = ssb.length();
            ssb.setSpan(new ForegroundColorSpan(Color.CYAN), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(": ").append(metadataMap.get(key)).append("\n");
        }
        
        metadataText.setText(ssb);
        hScrollView.addView(metadataText);
        layout.addView(hScrollView);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);

        new AlertDialog.Builder(context)
                .setTitle("Metadata & Artwork")
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .show();
    }

    private void addArtworkToLayout(Context context, LinearLayout layout, TagLib.Artwork[] artworks) {
        for (int i = 0; i < artworks.length; i++) {
            ImageView imageView = new ImageView(context);
            imageView.setAdjustViewBounds(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 400
            );
            params.setMargins(0, 0, 0, 10);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            boolean imageLoaded = loadAnimatedImage(imageView, artworks[i].data);
            
            if (imageLoaded) {
                layout.addView(imageView);

                String dimensions = getDimensions(artworks[i].data);
                TextView infoText = new TextView(context);
                infoText.setText(
                        "Artwork " + (i + 1) + ": " +
                                dimensions +
                                " - " + artworks[i].mimeType
                );
                infoText.setPadding(0, 0, 0, 20);
                layout.addView(infoText);
            }
        }
    }

    private boolean loadAnimatedImage(ImageView imageView, byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return false;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.Source source = 
                    android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(imageData));
                Drawable drawable = android.graphics.ImageDecoder.decodeDrawable(source);
                imageView.setImageDrawable(drawable);
                
                if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                    Log.d(TAG, "Started animated image playback");
                }
                return true;
            } else {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading animated image, trying fallback: " + e.getMessage());
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return true;
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback also failed: " + fallbackError.getMessage());
            }
        }
        return false;
    }

    private String getDimensions(byte[] imageData) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.Source source = 
                    android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(imageData));
                Bitmap bitmap = android.graphics.ImageDecoder.decodeBitmap(source);
                return bitmap.getWidth() + "x" + bitmap.getHeight();
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
                return options.outWidth + "x" + options.outHeight;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting dimensions: " + e.getMessage());
            return "unknown";
        }
    }

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
