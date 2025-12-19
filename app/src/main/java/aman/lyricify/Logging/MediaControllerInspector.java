package aman.lyricify;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;

public class MediaControllerInspector {

    private MediaController controller;

    public MediaControllerInspector(MediaController controller) {
        this.controller = controller;
    }

    // Returns all metadata, playback state, and extras as a Map
    public Map<String, Object> getAllInfo() {
        Map<String, Object> info = new HashMap<>();

        if (controller == null) return info;

        info.put("PackageName", controller.getPackageName());

        MediaMetadata metadata = controller.getMetadata();
        if (metadata != null) {
            Map<String, Object> metaMap = new HashMap<>();
            for (String key : metadata.keySet()) {
                Object value = metadata.getText(key);
                if (value == null) value = metadata.getLong(key);
                if (value == null) value = metadata.getBitmap(key);
                metaMap.put(key, value);
            }
            info.put("Metadata", metaMap);
        }

        PlaybackState state = controller.getPlaybackState();
        if (state != null) {
            Map<String, Object> stateMap = new HashMap<>();
            stateMap.put("State", state.getState());
            stateMap.put("Position", state.getPosition());
            stateMap.put("Speed", state.getPlaybackSpeed());
            stateMap.put("Actions", state.getActions());
            info.put("PlaybackState", stateMap);
        }

        Bundle extras = controller.getExtras();
        if (extras != null) {
            Map<String, Object> extrasMap = new HashMap<>();
            for (String key : extras.keySet()) {
                extrasMap.put(key, extras.get(key));
            }
            info.put("Extras", extrasMap);
        }

        return info;
    }

    // Optional helper: return a debug string
    public String getDebugString() {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> allInfo = getAllInfo();

        for (String key : allInfo.keySet()) {
            sb.append("=== ").append(key).append(" ===\n");
            Object value = allInfo.get(key);
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            } else {
                sb.append(value).append("\n");
            }
        }

        return sb.toString();
    }

}