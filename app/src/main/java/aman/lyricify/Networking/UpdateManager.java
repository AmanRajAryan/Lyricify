package aman.lyricify;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

// Import Markwon
import io.noties.markwon.Markwon;

public class UpdateManager {

    private final Context context;
    private final String REPO_OWNER = "AmanRajAryan";
    private final String REPO_NAME = "Lyricify";

    public UpdateManager(Context context) {
        this.context = context;
    }

    public void checkForUpdates() {
        new Thread(
                        () -> {
                            try {
                                String currentVersion = getCurrentVersion();

                                URL url =
                                        new URL(
                                                "https://api.github.com/repos/"
                                                        + REPO_OWNER
                                                        + "/"
                                                        + REPO_NAME
                                                        + "/releases/latest");
                                HttpURLConnection connection =
                                        (HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("GET");
                                connection.setRequestProperty("User-Agent", "Lyricify-App");

                                if (connection.getResponseCode() == 200) {
                                    BufferedReader reader =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            connection.getInputStream()));
                                    StringBuilder response = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null)
                                        response.append(line);
                                    reader.close();

                                    JSONObject jsonResponse = new JSONObject(response.toString());

                                    String latestVersionTag =
                                            jsonResponse.optString("tag_name", "0.0");
                                    String releaseNotes = jsonResponse.optString("body", "");

                                    // CHANGE: Use html_url directly (The GitHub Page)
                                    // We no longer look into the 'assets' array.
                                    String releasePageUrl = jsonResponse.optString("html_url");

                                    // Version Check Logic
                                    if (isNewerVersion(currentVersion, latestVersionTag)) {
                                        new Handler(Looper.getMainLooper())
                                                .post(
                                                        () ->
                                                                // Pass the releasePageUrl instead
                                                                // of the direct download link
                                                                showCustomDialog(
                                                                        latestVersionTag,
                                                                        releaseNotes,
                                                                        releasePageUrl));
                                    }
                                }
                                connection.disconnect();

                            } catch (Exception e) {
                                Log.e("UpdateManager", "Check failed", e);
                            }
                        })
                .start();
    }

    private String getCurrentVersion() {
        try {
            PackageInfo pInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0";
        }
    }

    /**
     * Logic for 0.9 vs 0.10: This splits the string by "." and compares the numbers numerically. So
     * "10" is read as integer 10, which is > integer 9.
     */
    private boolean isNewerVersion(String current, String latest) {
        String s1 = current.replaceAll("[^0-9.]", "");
        String s2 = latest.replaceAll("[^0-9.]", "");

        if (s1.isEmpty() || s2.isEmpty()) return false;

        String[] v1 = s1.split("\\.");
        String[] v2 = s2.split("\\.");

        int length = Math.max(v1.length, v2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
            int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;

            if (num2 > num1) return true; // Found a newer part (e.g. 10 > 9)
            if (num2 < num1) return false; // Found an older part
        }
        return false;
    }

    private void showCustomDialog(String version, String notes, String url) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_update, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Make background transparent so our rounded CardView shows properly
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Initialize Views
        TextView titleObj = dialogView.findViewById(R.id.updateTitle);
        TextView notesObj = dialogView.findViewById(R.id.updateReleaseNotes);
        View btnUpdate = dialogView.findViewById(R.id.btnUpdate);
        View btnLater = dialogView.findViewById(R.id.btnLater);

        // Set Data
        titleObj.setText("Update Available: " + version);

        // RENDER MARKDOWN HERE
        // This makes headers big, lists bulleted, etc.
        Markwon.create(context).setMarkdown(notesObj, notes);

        // Listeners
        btnUpdate.setOnClickListener(
                v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(intent);
                    dialog.dismiss();
                });

        btnLater.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
