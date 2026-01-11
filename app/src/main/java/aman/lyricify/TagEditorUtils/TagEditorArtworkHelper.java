package aman.lyricify;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy; 
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class TagEditorArtworkHelper {

    private static final String TAG = "TagEditorArtworkHelper";

    private final TagEditorActivity activity;
    private final Runnable updateRestoreStateCallback;
    private final ImageView artworkImageView;
    private final View artworkDimensionsContainer;
    private final TextView artworkDimensionsText;
    private final View resetArtworkButton;

    private Bitmap selectedArtwork;
    private byte[] selectedArtworkBytes;
    private String selectedArtworkMimeType;

    // Store real dimensions separately from the Bitmap object
    private int realWidth = 0;
    private int realHeight = 0;

    private Bitmap originalArtwork;
    private byte[] originalArtworkBytes;
    private String originalArtworkMimeType;

    private boolean artworkChanged = false;
    private boolean isAutoUpdating = false;

    public TagEditorArtworkHelper(
            TagEditorActivity activity,
            ImageView artworkImageView,
            View artworkDimensionsContainer,
            TextView artworkDimensionsText,
            View resetArtworkButton,
            Runnable updateRestoreStateCallback) {
        this.activity = activity;
        this.artworkImageView = artworkImageView;
        this.artworkDimensionsContainer = artworkDimensionsContainer;
        this.artworkDimensionsText = artworkDimensionsText;
        this.resetArtworkButton = resetArtworkButton;
        this.updateRestoreStateCallback = updateRestoreStateCallback;
    }

    public void setOriginalArtwork(Bitmap artwork, byte[] bytes, String mimeType) {
        this.originalArtwork = artwork;
        this.originalArtworkBytes = bytes;
        this.originalArtworkMimeType = mimeType;

        // Default real dimensions to original
        if (artwork != null) {
            this.realWidth = artwork.getWidth();
            this.realHeight = artwork.getHeight();
        }
        updateArtworkDimensionsBadge();
    }

    public void handleImageResult(Uri imageUri) {
        if (imageUri != null) {
            Log.d(TAG, "handleImageResult: Received URI: " + imageUri.toString());
            loadArtworkFromUri(imageUri);
        } else {
            Log.e(TAG, "handleImageResult: Received NULL Uri");
        }
    }

    public void loadArtworkFromUri(Uri uri) {
        Log.d(TAG, "Loading artwork from URI: " + uri);
        try {
            // 1. Load into ImageView (Optimized display, SKIP CACHE)
            Glide.with(activity)
                    .asDrawable()
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip disk cache
                    .skipMemoryCache(true) // Skip memory cache
                    .into(artworkImageView);

            // 2. Load Bitmap for caching (Let Glide decide size, but SKIP CACHE to ensure
            // freshness)
            Glide.with(activity)
                    .asBitmap()
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip disk cache
                    .skipMemoryCache(true) // Skip memory cache
                    .into(
                            new CustomTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(
                                        Bitmap resource, Transition<? super Bitmap> t) {
                                    selectedArtwork = resource;
                                    // We wait for real dimensions calculation below
                                }

                                @Override
                                public void onLoadCleared(Drawable p) {}
                            });

            // 3. Calculate REAL dimensions separately (Lightweight, No Memory Load)
            new Thread(
                            () -> {
                                try {
                                    InputStream is =
                                            activity.getContentResolver().openInputStream(uri);
                                    BitmapFactory.Options options = new BitmapFactory.Options();
                                    options.inJustDecodeBounds = true; // THE OPTIMIZATION
                                    BitmapFactory.decodeStream(is, null, options);
                                    if (is != null) is.close();

                                    realWidth = options.outWidth;
                                    realHeight = options.outHeight;

                                    // 4. Load Bytes
                                    InputStream isBytes =
                                            activity.getContentResolver().openInputStream(uri);
                                    if (isBytes == null)
                                        throw new java.io.IOException(
                                                "InputStream is null for bytes: " + uri);

                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    byte[] buffer = new byte[8192];
                                    int read;
                                    while ((read = isBytes.read(buffer)) != -1)
                                        baos.write(buffer, 0, read);
                                    isBytes.close();

                                    selectedArtworkBytes = baos.toByteArray();
                                    selectedArtworkMimeType =
                                            detectMimeType(selectedArtworkBytes, uri.toString());

                                    Log.d(
                                            TAG,
                                            "Successfully loaded artwork bytes. Size: "
                                                    + selectedArtworkBytes.length
                                                    + ", MIME: "
                                                    + selectedArtworkMimeType);

                                    // Update UI
                                    activity.runOnUiThread(
                                            () -> {
                                                artworkChanged = true;
                                                resetArtworkButton.setEnabled(true);
                                                updateArtworkDimensionsBadge();
                                                updateRestoreStateCallback.run();
                                            });

                                } catch (Exception e) {
                                    Log.e(
                                            TAG,
                                            "Failed to load image metadata/bytes from URI: " + uri,
                                            e);
                                    e.printStackTrace();
                                }
                            })
                    .start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to load image from URI: " + uri, e);
            Toast.makeText(activity, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT)
                    .show();
            e.printStackTrace();
        }
    }

    public void loadArtworkWithGlide(String url) {
        Log.d(TAG, "Loading artwork from URL: " + url);
        // Display Image (Optimized, SKIP CACHE)
        Glide.with(activity)
                .asDrawable()
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip disk cache
                .skipMemoryCache(true) // Skip memory cache
                .listener(
                        new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException e,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean isFirstResource) {
                                Log.e(TAG, "Glide Load Failed for URL: " + url, e);
                                activity.hideLoading();
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable resource,
                                    Object model,
                                    Target<Drawable> target,
                                    DataSource dataSource,
                                    boolean isFirstResource) {
                                artworkImageView.setImageDrawable(resource);
                                loadArtworkBytes(url);
                                artworkChanged = true;
                                resetArtworkButton.setEnabled(true);
                                updateRestoreStateCallback.run();
                                activity.hideLoading();
                                Toast.makeText(activity, "Metadata fetched!", Toast.LENGTH_SHORT)
                                        .show();
                                return false;
                            }
                        })
                .into(artworkImageView);
    }

    private void loadArtworkBytes(String url) {
        // Fetch Bytes & Real Dimensions
        Glide.with(activity)
                .asBitmap()
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip disk cache
                .skipMemoryCache(true) // Skip memory cache
                .into(
                        new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(
                                    Bitmap resource, Transition<? super Bitmap> t) {
                                selectedArtwork = resource;
                                new Thread(
                                                () -> {
                                                    try {
                                                        java.net.URLConnection conn =
                                                                new java.net.URL(url)
                                                                        .openConnection();
                                                        InputStream is = conn.getInputStream();
                                                        ByteArrayOutputStream baos =
                                                                new ByteArrayOutputStream();
                                                        byte[] buffer = new byte[8192];
                                                        int read;
                                                        while ((read = is.read(buffer)) != -1)
                                                            baos.write(buffer, 0, read);
                                                        is.close();

                                                        selectedArtworkBytes = baos.toByteArray();
                                                        selectedArtworkMimeType =
                                                                detectMimeType(
                                                                        selectedArtworkBytes, url);

                                                        // Parse dimensions from bytes
                                                        BitmapFactory.Options options =
                                                                new BitmapFactory.Options();
                                                        options.inJustDecodeBounds = true;
                                                        BitmapFactory.decodeByteArray(
                                                                selectedArtworkBytes,
                                                                0,
                                                                selectedArtworkBytes.length,
                                                                options);
                                                        realWidth = options.outWidth;
                                                        realHeight = options.outHeight;

                                                        activity.runOnUiThread(
                                                                () ->
                                                                        updateArtworkDimensionsBadge());
                                                    } catch (Exception e) {
                                                        Log.e(
                                                                TAG,
                                                                "Failed to download artwork bytes from URL: "
                                                                        + url,
                                                                e);
                                                    }
                                                })
                                        .start();
                            }

                            @Override
                            public void onLoadCleared(Drawable p) {}
                        });
    }

    public void resetArtwork() {
        if (originalArtwork != null) {
            artworkImageView.setImageBitmap(originalArtwork);
            realWidth = originalArtwork.getWidth();
            realHeight = originalArtwork.getHeight();
        } else {
            artworkImageView.setImageResource(R.drawable.ic_music_note);
            realWidth = 0;
            realHeight = 0;
        }
        selectedArtwork = null;
        selectedArtworkBytes = null;
        selectedArtworkMimeType = null;
        artworkChanged = false;
        resetArtworkButton.setEnabled(false);
        updateArtworkDimensionsBadge();
        updateRestoreStateCallback.run();
    }

    public void updateArtworkDimensionsBadge() {
        int w = 0, h = 0;

        if (artworkChanged) {
            w = realWidth;
            h = realHeight;
            if (w == 0 && selectedArtwork != null) {
                w = selectedArtwork.getWidth();
                h = selectedArtwork.getHeight();
            }
        } else if (originalArtwork != null) {
            w = originalArtwork.getWidth();
            h = originalArtwork.getHeight();
        }

        if (w > 0 && h > 0) {
            artworkDimensionsContainer.setVisibility(View.VISIBLE);
            artworkDimensionsText.setText(w + " x " + h);
        } else {
            artworkDimensionsContainer.setVisibility(View.GONE);
        }
    }

    

    public String detectMimeType(byte[] imageData, String imageUrl) {
        if (imageData != null && imageData.length >= 12) {
            if (imageData[0] == (byte) 0xFF
                    && imageData[1] == (byte) 0xD8
                    && imageData[2] == (byte) 0xFF) return "image/jpeg";
            if (imageData[0] == (byte) 0x89
                    && imageData[1] == 0x50
                    && imageData[2] == 0x4E
                    && imageData[3] == 0x47) return "image/png";
            if (imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46)
                return "image/gif";
            if (imageData[0] == 0x52
                    && imageData[1] == 0x49
                    && imageData[2] == 0x46
                    && imageData[3] == 0x46
                    && imageData[8] == 0x57
                    && imageData[9] == 0x45
                    && imageData[10] == 0x42
                    && imageData[11] == 0x50) return "image/webp";
            if (imageData[4] == 0x66
                    && imageData[5] == 0x74
                    && imageData[6] == 0x79
                    && imageData[7] == 0x70
                    && imageData[8] == 0x61
                    && imageData[9] == 0x76
                    && imageData[10] == 0x69
                    && imageData[11] == 0x66) {
                return "image/avif";
            }
        }
        if (imageUrl != null) {
            String lower = imageUrl.toLowerCase();
            if (lower.endsWith(".png") || lower.contains(".png?")) return "image/png";
            if (lower.endsWith(".gif") || lower.contains(".gif?")) return "image/gif";
            if (lower.endsWith(".webp") || lower.contains(".webp?")) return "image/webp";
            if (lower.endsWith(".avif") || lower.contains(".avif?")) return "image/avif";
        }
        return "image/jpeg";
    }

    public void showArtworkOptionsDialog(String intentArtworkUrl) {
        if (selectedArtwork == null && originalArtwork == null) return;

        final int currentW =
                (artworkChanged && realWidth > 0)
                        ? realWidth
                        : (artworkChanged
                                ? selectedArtwork.getWidth()
                                : originalArtwork.getWidth());
        final int currentH =
                (artworkChanged && realHeight > 0)
                        ? realHeight
                        : (artworkChanged
                                ? selectedArtwork.getHeight()
                                : originalArtwork.getHeight());

        final Bitmap currentBmp = artworkChanged ? selectedArtwork : originalArtwork;
        final float aspectRatio = (float) currentW / currentH;

        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 40, 50, 40);

        TextView header = new TextView(activity);
        header.setText("Artwork Options");
        header.setTextSize(20);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        mainLayout.addView(header);

        View spacer1 = new View(activity);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(1, 40));
        mainLayout.addView(spacer1);

        LinearLayout resizeContainer = new LinearLayout(activity);
        resizeContainer.setOrientation(LinearLayout.HORIZONTAL);
        resizeContainer.setGravity(Gravity.CENTER_VERTICAL);
        resizeContainer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable boxBackground = new GradientDrawable();
        boxBackground.setColor(Color.parseColor("#2D2D2D"));
        boxBackground.setCornerRadius(16f);

        final EditText widthInput = new EditText(activity);
        widthInput.setHint("W");
        widthInput.setHintTextColor(Color.GRAY);
        widthInput.setTextColor(Color.WHITE);
        widthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        widthInput.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        widthInput.setGravity(Gravity.CENTER);
        widthInput.setBackground(boxBackground);
        widthInput.setPadding(0, 24, 0, 24);
        widthInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
        resizeContainer.addView(widthInput);

        TextView xLabel = new TextView(activity);
        xLabel.setText("   x   ");
        xLabel.setTextColor(Color.GRAY);
        xLabel.setTextSize(16);
        resizeContainer.addView(xLabel);

        GradientDrawable boxBackground2 = new GradientDrawable();
        boxBackground2.setColor(Color.parseColor("#2D2D2D"));
        boxBackground2.setCornerRadius(16f);

        final EditText heightInput = new EditText(activity);
        heightInput.setHint("H");
        heightInput.setHintTextColor(Color.GRAY);
        heightInput.setTextColor(Color.WHITE);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightInput.setLayoutParams(
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heightInput.setGravity(Gravity.CENTER);
        heightInput.setBackground(boxBackground2);
        heightInput.setPadding(0, 24, 0, 24);
        heightInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
        resizeContainer.addView(heightInput);

        mainLayout.addView(resizeContainer);

        boolean canResize =
                intentArtworkUrl != null
                        && (intentArtworkUrl.contains("{w}") || intentArtworkUrl.contains("{h}"));
        if (!canResize) {
            widthInput.setText("N/A");
            heightInput.setText("N/A");
            widthInput.setEnabled(false);
            heightInput.setEnabled(false);
            widthInput.setAlpha(0.5f);
            heightInput.setAlpha(0.5f);
            TextView note = new TextView(activity);
            note.setText("(Resize unavailable for local files)");
            note.setTextColor(Color.GRAY);
            note.setTextSize(12);
            note.setGravity(Gravity.CENTER_HORIZONTAL);
            note.setPadding(0, 16, 0, 0);
            mainLayout.addView(note);
        }

        View spacer2 = new View(activity);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(1, 50));
        mainLayout.addView(spacer2);

        MaterialButton saveToStorageBtn = new MaterialButton(activity);
        saveToStorageBtn.setText("Save Artwork to Gallery");
        saveToStorageBtn.setIconResource(R.drawable.ic_save);
        mainLayout.addView(saveToStorageBtn);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(activity)
                        .setView(mainLayout)
                        .setNeutralButton("Cancel", null);

        if (canResize) {
            builder.setPositiveButton(
                    "Apply",
                    (d, w) -> {
                        String wStr = widthInput.getText().toString();
                        String hStr = heightInput.getText().toString();
                        if (!wStr.isEmpty() && !hStr.isEmpty()) {
                            downloadResizedArtwork(wStr, hStr, intentArtworkUrl);
                        }
                    });
        }

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        Runnable checkValidation =
                () -> {
                    String w = widthInput.getText().toString().trim();
                    String h = heightInput.getText().toString().trim();
                    if (w.equals("N/A") || h.equals("N/A")) {
                        saveToStorageBtn.setEnabled(true);
                        saveToStorageBtn.setAlpha(1.0f);
                        Button applyBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        if (applyBtn != null) {
                            applyBtn.setEnabled(false);
                            applyBtn.setAlpha(0.5f);
                        }
                        return;
                    }
                    boolean isInvalid =
                            w.isEmpty() || h.isEmpty() || w.equals("0") || h.equals("0");
                    if (!isInvalid) {
                        try {
                            if (Integer.parseInt(w) == 0 || Integer.parseInt(h) == 0)
                                isInvalid = true;
                        } catch (NumberFormatException e) {
                            isInvalid = true;
                        }
                    }
                    saveToStorageBtn.setEnabled(!isInvalid);
                    saveToStorageBtn.setAlpha(isInvalid ? 0.5f : 1.0f);
                    Button applyBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (applyBtn != null) {
                        applyBtn.setEnabled(!isInvalid);
                        applyBtn.setAlpha(isInvalid ? 0.5f : 1.0f);
                    }
                };

        if (canResize) {
            widthInput.setText(String.valueOf(currentW));
            heightInput.setText(String.valueOf(currentH));
            TextWatcher tw =
                    new TextWatcher() {
                        public void beforeTextChanged(
                                CharSequence s, int start, int count, int after) {}

                        public void onTextChanged(
                                CharSequence s, int start, int before, int count) {}

                        public void afterTextChanged(Editable s) {
                            checkValidation.run();
                            if (isAutoUpdating) return;
                            if (s.length() == 0) {
                                isAutoUpdating = true;
                                if (widthInput.hasFocus()) heightInput.setText("");
                                else widthInput.setText("");
                                isAutoUpdating = false;
                                return;
                            }
                            try {
                                isAutoUpdating = true;
                                if (widthInput.hasFocus()) {
                                    int w = Integer.parseInt(s.toString());
                                    heightInput.setText(
                                            String.valueOf(Math.round(w / aspectRatio)));
                                } else {
                                    int h = Integer.parseInt(s.toString());
                                    widthInput.setText(String.valueOf(Math.round(h * aspectRatio)));
                                }
                                isAutoUpdating = false;
                                checkValidation.run();
                            } catch (NumberFormatException e) {
                            }
                        }
                    };
            widthInput.addTextChangedListener(tw);
            heightInput.addTextChangedListener(tw);
        }

        saveToStorageBtn.setOnClickListener(
                v -> {
                    String wStr = widthInput.getText().toString();
                    String hStr = heightInput.getText().toString();
                    if (wStr.isEmpty() || hStr.isEmpty() || wStr.equals("0") || hStr.equals("0"))
                        return;
                    if (!canResize) {
                        saveBitmapToStorage(currentBmp);
                        dialog.dismiss();
                    } else {
                        int inputW = Integer.parseInt(wStr);
                        int inputH = Integer.parseInt(hStr);
                        if (inputW == currentW && inputH == currentH) {
                            saveBitmapToStorage(currentBmp);
                            dialog.dismiss();
                        } else {
                            fetchAndSaveArtwork(inputW, inputH, intentArtworkUrl, dialog);
                        }
                    }
                });

        dialog.show();
        checkValidation.run();
    }

    public void downloadResizedArtwork(String w, String h, String intentArtworkUrl) {
        if (intentArtworkUrl == null) return;
        activity.showLoading("Downloading " + w + "x" + h + "...");
        String newUrl = intentArtworkUrl.replace("{w}", w).replace("{h}", h).replace("{f}", "jpg");
        loadArtworkWithGlide(newUrl);
    }

    private void fetchAndSaveArtwork(
            int w, int h, String intentArtworkUrl, androidx.appcompat.app.AlertDialog dialog) {
        if (intentArtworkUrl == null) return;
        dialog.setCancelable(false);
        activity.showLoading("Fetching & Saving " + w + "x" + h + "...");
        String newUrl =
                intentArtworkUrl
                        .replace("{w}", String.valueOf(w))
                        .replace("{h}", String.valueOf(h))
                        .replace("{f}", "jpg");

        Glide.with(activity)
                .asBitmap()
                .load(newUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip cache
                .skipMemoryCache(true) // Skip memory cache
                .into(
                        new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(
                                    Bitmap resource, Transition<? super Bitmap> t) {
                                saveBitmapToStorage(resource);
                                selectedArtwork = resource;
                                artworkImageView.setImageBitmap(resource);
                                artworkChanged = true;
                                resetArtworkButton.setEnabled(true);
                                updateArtworkDimensionsBadge();
                                updateRestoreStateCallback.run();
                                activity.hideLoading();
                                dialog.dismiss();
                            }

                            @Override
                            public void onLoadCleared(Drawable p) {}

                            @Override
                            public void onLoadFailed(Drawable errorDrawable) {
                                activity.hideLoading();
                                Toast.makeText(activity, "Failed to download", Toast.LENGTH_SHORT)
                                        .show();
                                dialog.setCancelable(true);
                            }
                        });
    }

    public void saveBitmapToStorage(Bitmap bitmapToSave) {
        if (bitmapToSave == null) return;
        try {
            String fileName = "Cover_" + System.currentTimeMillis() + ".jpg";
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Lyricify");
            Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream out = activity.getContentResolver().openOutputStream(uri);
                bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 100, out);
                if (out != null) out.close();
                Toast.makeText(activity, "Saved to Pictures/Lyricify", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    
    
     public void saveCurrentArtworkToGallery() {
        byte[] bytesToSave = null;
        String mimeType = "image/jpeg";
        Bitmap bitmapFallback = null;

        // 1. Check if we have modified/selected artwork
        if (artworkChanged) {
            if (selectedArtworkBytes != null) {
                bytesToSave = selectedArtworkBytes;
                mimeType = selectedArtworkMimeType;
            } else {
                bitmapFallback = selectedArtwork;
            }
        } 
        // 2. Fallback to original artwork
        else {
            if (originalArtworkBytes != null) {
                bytesToSave = originalArtworkBytes;
                mimeType = originalArtworkMimeType;
            } else {
                bitmapFallback = originalArtwork;
            }
        }

        // 3. Execute Save
        if (bytesToSave != null) {
            saveBytesToStorage(bytesToSave, mimeType);
        } else if (bitmapFallback != null) {
            saveBitmapToStorage(bitmapFallback); // Fallback for pure bitmaps
        } else {
            Toast.makeText(activity, "No artwork to save", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Helper: Writes raw bytes to storage (Preserves GIF/WebP/MP4/etc)
     */
    private void saveBytesToStorage(byte[] bytes, String mimeType) {
        try {
            if (mimeType == null) mimeType = "image/jpeg";
            String extension = getExtensionForMime(mimeType);
            String fileName = "Cover_" + System.currentTimeMillis() + extension;

            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Lyricify");

            Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream out = activity.getContentResolver().openOutputStream(uri);
                out.write(bytes);
                out.flush();
                out.close();
                Toast.makeText(activity, "Saved " + extension.toUpperCase() + " to Gallery", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private String getExtensionForMime(String mime) {
        if (mime == null) return ".jpg";
        if (mime.contains("png")) return ".png";
        if (mime.contains("gif")) return ".gif";
        if (mime.contains("webp")) return ".webp";
        if (mime.contains("avif")) return ".avif";
        if (mime.contains("video/mp4")) return ".mp4"; // Just in case
        return ".jpg";
    }

    public String detectMimeTypeFromBitmap(Bitmap bitmap) {
        return "image/png";
    }

    public Bitmap getSelectedArtwork() {
        return selectedArtwork;
    }

    public byte[] getSelectedArtworkBytes() {
        return selectedArtworkBytes;
    }

    public String getSelectedArtworkMimeType() {
        return selectedArtworkMimeType;
    }

    public boolean isArtworkChanged() {
        return artworkChanged;
    }

    public Bitmap getOriginalArtwork() {
        return originalArtwork;
    }
}



















