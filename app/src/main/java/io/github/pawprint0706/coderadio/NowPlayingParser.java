package io.github.pawprint0706.coderadio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

public final class NowPlayingParser {
    private NowPlayingParser() {}
    public static StationSnapshot parse(String json) throws JSONException {
        JSONObject data = new JSONObject(json);
        // Reject API error payloads instead of replacing a good snapshot with empty fields.
        if (!data.has("station")) throw new JSONException("Missing station");
        JSONObject station = object(data, "station");
        JSONObject song = object(object(data, "now_playing"), "song");
        String[] fields = RadioRules.recoverSong(string(song, "artist"), string(song, "title"), string(song, "text"));
        String listen = RadioRules.httpsUrl(string(station, "listen_url"));
        String first = "", high = "", low = "";
        JSONArray mounts = station.optJSONArray("mounts");
        if (mounts != null) for (int i = 0; i < mounts.length(); i++) {
            JSONObject mount = mounts.optJSONObject(i);
            if (mount == null) continue;
            String url = RadioRules.httpsUrl(string(mount, "url"));
            if (url.isEmpty()) continue;
            if (first.isEmpty()) first = url;
            String name = string(mount, "name").toLowerCase(Locale.ROOT);
            if (high.isEmpty() && mount.optBoolean("is_default")) high = url;
            if (low.isEmpty() && (name.contains("64") || name.contains("low") || mount.optInt("bitrate") == 64)) low = url;
        }
        if (high.isEmpty()) high = first.isEmpty() ? listen : first;
        // Missing low quality falls back to the default, never a fabricated mount URL.
        int listeners = object(data, "listeners").optInt("current", 0);
        return new StationSnapshot(data.optBoolean("is_online", true), fields[1], fields[0],
                string(song, "album"), RadioRules.artworkUrl(string(song, "art")), high, low, listen, listeners);
    }
    private static JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }
    private static String string(JSONObject parent, String key) {
        return parent.isNull(key) ? "" : RadioRules.clean(parent.optString(key, ""));
    }
}
