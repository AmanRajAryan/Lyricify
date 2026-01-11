package aman.lyricify;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import okhttp3.*;

public class ArtworkBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String TAG = "LyricifyMotion";
    private static final String CACHE_FOLDER_NAME = "motion_cache";

    private TagEditorActivity activity;
    private String trackUrl;
    private String artworkUrl;

    // UI Components
    private ViewFlipper viewFlipper;
    private VideoView motionPreviewPlayer;
    private ProgressBar playerLoading, listLoading;
    private TextView previewLabel;

    // Categorized Lists
    private RecyclerView squareList, tallList;
    private View tallHeader;
    private ImageView tallArrow;
    private boolean isTallExpanded = false;

    // Download Overlay
    private View downloadOverlay;
    private ProgressBar downloadProgressBar;
    private TextView downloadProgressText;

    // Static Tab UI
    private EditText widthInput, heightInput;
    private Button btnSaveStatic, btnApplyResize, btnPickGallery, btnReset;

    public static ArtworkBottomSheetFragment newInstance(String trackUrl, String artworkUrl) {
        ArtworkBottomSheetFragment fragment = new ArtworkBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("TRACK_URL", trackUrl);
        args.putString("ARTWORK_URL", artworkUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (TagEditorActivity) getActivity();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(
                d -> {
                    FrameLayout bottomSheet =
                            dialog.findViewById(
                                    com.google.android.material.R.id.design_bottom_sheet);
                    if (bottomSheet != null) {
                        BottomSheetBehavior.from(bottomSheet)
                                .setState(BottomSheetBehavior.STATE_EXPANDED);
                    }
                });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.artwork_bottom_sheet, container, false);

        if (getArguments() != null) {
            trackUrl = getArguments().getString("TRACK_URL");
            artworkUrl = getArguments().getString("ARTWORK_URL");
        }

        setupTabs(v);
        setupStaticTab(v);
        setupMotionTab(v);

        return v;
    }

    private void setupTabs(View v) {
        TabLayout tabLayout = v.findViewById(R.id.artworkTabs);
        viewFlipper = v.findViewById(R.id.viewFlipper);

        tabLayout.addTab(tabLayout.newTab().setText("Static"));
        tabLayout.addTab(tabLayout.newTab().setText("Motion"));

        tabLayout.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        viewFlipper.setDisplayedChild(tab.getPosition());
                        if (tab.getPosition() == 1) {
                            loadMotionData();
                        } else {
                            if (motionPreviewPlayer.isPlaying()) {
                                motionPreviewPlayer.pause();
                            }
                        }
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
    }

    private void setupStaticTab(View v) {
        widthInput = v.findViewById(R.id.widthInput);
        heightInput = v.findViewById(R.id.heightInput);
        btnSaveStatic = v.findViewById(R.id.btnSaveStatic);
        btnApplyResize = v.findViewById(R.id.btnApplyResize);
        btnPickGallery = v.findViewById(R.id.btnPickGallery);
        btnReset = v.findViewById(R.id.btnReset);

        if (activity.getTagEditorArtworkHelper() != null) {
            Bitmap bmp = activity.getTagEditorArtworkHelper().getSelectedArtwork();
            if (bmp == null) bmp = activity.getTagEditorArtworkHelper().getOriginalArtwork();
            if (bmp != null) {
                widthInput.setText(String.valueOf(bmp.getWidth()));
                heightInput.setText(String.valueOf(bmp.getHeight()));
            }
        }

        btnPickGallery.setOnClickListener(
                btn -> {
                    activity.findViewById(R.id.changeArtworkButton).performClick();
                    dismiss();
                });

        btnReset.setOnClickListener(
                btn -> {
                    activity.findViewById(R.id.resetArtworkButton).performClick();
                    dismiss();
                });

        btnApplyResize.setOnClickListener(
                btn -> {
                    String w = widthInput.getText().toString();
                    String h = heightInput.getText().toString();
                    if (!w.isEmpty() && !h.isEmpty() && artworkUrl != null) {
                        activity.getTagEditorArtworkHelper()
                                .downloadResizedArtwork(w, h, artworkUrl);
                        dismiss();
                    }
                });

        btnSaveStatic.setOnClickListener(
                btn -> {
                    activity.getTagEditorArtworkHelper().saveCurrentArtworkToGallery();
                    dismiss();
                });

        if (artworkUrl == null || !artworkUrl.contains("{w}")) {
            widthInput.setEnabled(false);
            heightInput.setEnabled(false);
            btnApplyResize.setEnabled(false);
            btnApplyResize.setText("Resize Unavailable");
        }
    }

    private void setupMotionTab(View v) {
        motionPreviewPlayer = v.findViewById(R.id.motionPreviewPlayer);
        playerLoading = v.findViewById(R.id.playerLoading);
        listLoading = v.findViewById(R.id.listLoading);
        previewLabel = v.findViewById(R.id.previewLabel);

        // Lists
        squareList = v.findViewById(R.id.squareList);
        tallList = v.findViewById(R.id.tallList);
        tallHeader = v.findViewById(R.id.tallHeader);
        tallArrow = v.findViewById(R.id.tallArrow);

        squareList.setLayoutManager(new LinearLayoutManager(getContext()));
        tallList.setLayoutManager(new LinearLayoutManager(getContext()));

        // Download Overlay
        downloadOverlay = v.findViewById(R.id.downloadOverlay);
        downloadProgressBar = v.findViewById(R.id.downloadProgressBar);
        downloadProgressText = v.findViewById(R.id.downloadProgressText);

        tallHeader.setOnClickListener(
                view -> {
                    isTallExpanded = !isTallExpanded;
                    tallList.setVisibility(isTallExpanded ? View.VISIBLE : View.GONE);
                    tallArrow.setRotation(isTallExpanded ? 180f : 0f);
                });

        motionPreviewPlayer.setOnPreparedListener(
                mp -> {
                    // Mute audio so we don't pause Spotify/Apple Music
                    mp.setVolume(0f, 0f);

                    mp.setLooping(true);
                    playerLoading.setVisibility(View.GONE);
                    mp.start();
                });

        motionPreviewPlayer.setOnErrorListener(
                (mp, what, extra) -> {
                    playerLoading.setVisibility(View.GONE);
                    return true;
                });

        motionPreviewPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
    }

    private boolean isDataLoaded = false;

    private void loadMotionData() {
        if (isDataLoaded) {
            if (!motionPreviewPlayer.isPlaying()) motionPreviewPlayer.start();
            return;
        }

        List<MotionRepository.MotionOption> cachedData = activity.getMotionCache();
        if (cachedData != null && !cachedData.isEmpty()) {
            setupMotionList(cachedData);
            return;
        }

        if (trackUrl == null || trackUrl.isEmpty()) {
            Toast.makeText(getContext(), "No Apple Music URL found.", Toast.LENGTH_LONG).show();
            return;
        }

        listLoading.setVisibility(View.VISIBLE);

        MotionRepository.fetchMotionCovers(
                ApiClient.client,
                trackUrl,
                new MotionRepository.MotionCallback() {
                    @Override
                    public void onSuccess(List<MotionRepository.MotionOption> options) {
                        if (!isAdded()) return;
                        activity.setMotionCache(options);
                        activity.runOnUiThread(
                                () -> {
                                    listLoading.setVisibility(View.GONE);
                                    setupMotionList(options);
                                });
                    }

                    @Override
                    public void onFailure(String error) {
                        if (!isAdded()) return;
                        activity.runOnUiThread(
                                () -> {
                                    listLoading.setVisibility(View.GONE);
                                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                                });
                    }
                });
    }

    private void setupMotionList(List<MotionRepository.MotionOption> options) {
        isDataLoaded = true;

        List<MotionRepository.MotionOption> squares = new ArrayList<>();
        List<MotionRepository.MotionOption> talls = new ArrayList<>();

        for (MotionRepository.MotionOption opt : options) {
            if ("Square".equals(opt.type)) squares.add(opt);
            else talls.add(opt);
        }

        squareList.setAdapter(new MotionAdapter(squares, this::downloadAndLaunchCompanion));
        tallList.setAdapter(new MotionAdapter(talls, this::downloadAndLaunchCompanion));

        tallHeader.setVisibility(talls.isEmpty() ? View.GONE : View.VISIBLE);

        // Auto-play lowest quality square
        MotionRepository.MotionOption previewCandidate = null;
        int minWidth = Integer.MAX_VALUE;
        for (MotionRepository.MotionOption opt : squares) {
            if (opt.width < minWidth) {
                minWidth = opt.width;
                previewCandidate = opt;
            }
        }
        if (previewCandidate != null) playPreview(previewCandidate);
    }

    private void playPreview(MotionRepository.MotionOption item) {
        if (item == null) return;
        playerLoading.setVisibility(View.VISIBLE);
        previewLabel.setText("Preview (" + item.type + ")");

        // Check Cache first
        File cacheDir = new File(activity.getCacheDir(), CACHE_FOLDER_NAME);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File cachedFile = new File(cacheDir, "prev_" + item.m3u8Url.hashCode() + ".mp4");

        if (cachedFile.exists() && cachedFile.length() > 0) {
            motionPreviewPlayer.setVideoPath(cachedFile.getAbsolutePath());
            return;
        }

        MotionRepository.resolveMp4Url(
                ApiClient.client,
                item.m3u8Url,
                mp4Url -> {
                    if (!isAdded()) return;
                    if (mp4Url != null) {
                        downloadVideoToCache(mp4Url, cachedFile);
                    } else {
                        activity.runOnUiThread(
                                () ->
                                        Toast.makeText(
                                                        getContext(),
                                                        "Video not available",
                                                        Toast.LENGTH_SHORT)
                                                .show());
                    }
                });
    }

    private void downloadVideoToCache(String url, File targetFile) {
        Request request = new Request.Builder().url(url).build();
        ApiClient.client
                .newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {}

                            @Override
                            public void onResponse(Call call, Response response)
                                    throws IOException {
                                if (!response.isSuccessful()) return;
                                try {
                                    InputStream is = response.body().byteStream();
                                    FileOutputStream fos = new FileOutputStream(targetFile);
                                    byte[] buffer = new byte[4096];
                                    int read;
                                    while ((read = is.read(buffer)) != -1)
                                        fos.write(buffer, 0, read);
                                    fos.flush();
                                    fos.close();
                                    is.close();

                                    if (isAdded()) {
                                        activity.runOnUiThread(
                                                () -> {
                                                    playerLoading.setVisibility(View.GONE);
                                                    motionPreviewPlayer.setVideoPath(
                                                            targetFile.getAbsolutePath());
                                                });
                                    }
                                } catch (Exception e) {
                                }
                            }
                        });
    }

    private void downloadAndLaunchCompanion(MotionRepository.MotionOption item) {
        showDownloadOverlay();

        MotionRepository.resolveMp4Url(
                ApiClient.client,
                item.m3u8Url,
                mp4Url -> {
                    if (mp4Url == null) {
                        hideDownloadOverlay();
                        activity.runOnUiThread(
                                () ->
                                        Toast.makeText(
                                                        getContext(),
                                                        "Failed to resolve video URL",
                                                        Toast.LENGTH_SHORT)
                                                .show());
                        return;
                    }
                    downloadFileWithProgress(mp4Url);
                });
    }

    private void downloadFileWithProgress(String url) {
        Request request = new Request.Builder().url(url).build();
        ApiClient.client
                .newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                hideDownloadOverlay();
                                activity.runOnUiThread(
                                        () ->
                                                Toast.makeText(
                                                                getContext(),
                                                                "Download Failed",
                                                                Toast.LENGTH_SHORT)
                                                        .show());
                            }

                            @Override
                            public void onResponse(Call call, Response response)
                                    throws IOException {
                                if (!response.isSuccessful()) {
                                    hideDownloadOverlay();
                                    return;
                                }

                                try {
                                    File tempFile =
                                            new File(
                                                    activity.getCacheDir(),
                                                    "motion_process_"
                                                            + System.currentTimeMillis()
                                                            + ".mp4");
                                    InputStream is = response.body().byteStream();
                                    FileOutputStream fos = new FileOutputStream(tempFile);

                                    long totalBytes = response.body().contentLength();
                                    long downloadedBytes = 0;
                                    byte[] buffer = new byte[8192];
                                    int read;

                                    while ((read = is.read(buffer)) != -1) {
                                        fos.write(buffer, 0, read);
                                        downloadedBytes += read;

                                        // Update Progress on UI Thread
                                        long finalDownloaded = downloadedBytes;
                                        activity.runOnUiThread(
                                                () ->
                                                        updateProgressUI(
                                                                finalDownloaded, totalBytes));
                                    }

                                    fos.flush();
                                    fos.close();
                                    is.close();

                                    hideDownloadOverlay();
                                    activity.runOnUiThread(
                                            () -> {
                                                Uri contentUri =
                                                        androidx.core.content.FileProvider
                                                                .getUriForFile(
                                                                        activity,
                                                                        "aman.lyricify.provider",
                                                                        tempFile);
                                                activity.launchCompanionApp(contentUri);
                                                dismiss();
                                            });

                                } catch (Exception e) {
                                    hideDownloadOverlay();
                                }
                            }
                        });
    }

    // --- PROGRESS UTILS ---
    private void showDownloadOverlay() {
        activity.runOnUiThread(
                () -> {
                    downloadOverlay.setVisibility(View.VISIBLE);
                    downloadProgressBar.setIndeterminate(true);
                    downloadProgressText.setText("Connecting...");
                });
    }

    private void hideDownloadOverlay() {
        if (isAdded()) {
            activity.runOnUiThread(() -> downloadOverlay.setVisibility(View.GONE));
        }
    }

    private void updateProgressUI(long current, long total) {
        if (total <= 0) {
            downloadProgressBar.setIndeterminate(true);
            downloadProgressText.setText(formatSize(current));
        } else {
            int progress = (int) ((current * 100) / total);
            downloadProgressBar.setIndeterminate(false);
            downloadProgressBar.setProgress(progress);

            // Format: 400kb/1.56MB • 21%
            String text =
                    String.format(
                            Locale.US,
                            "%s / %s • %d%%",
                            formatSize(current),
                            formatSize(total),
                            progress);
            downloadProgressText.setText(text);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
