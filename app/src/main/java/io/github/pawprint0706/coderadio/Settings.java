package io.github.pawprint0706.coderadio;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    public final SharedPreferences prefs;
    public Settings(Context context) { prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE); }
    public int bitrate() { return prefs.getInt("bitrate", 128) == 64 ? 64 : 128; }
    public int volume() { return Math.max(0, Math.min(100, prefs.getInt("volume", 70))); }
    public boolean autoplay() { return prefs.getBoolean("autoplay", true); }
    public boolean artwork() { return prefs.getBoolean("artwork", true); }
    public boolean listeners() { return prefs.getBoolean("listeners", true); }
}
