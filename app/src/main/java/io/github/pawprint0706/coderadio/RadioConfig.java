package io.github.pawprint0706.coderadio;

public final class RadioConfig {
    private RadioConfig() {}
    // Identical endpoint to coderadio-on-tray 0.5.2, supplied source commit c6ab1683.
    public static final String API = "https://coderadio-admin-v2.freecodecamp.org/api/nowplaying/coderadio";
    public static final String SITE = "https://coderadio.freecodecamp.org/";
    public static final String SOURCE = "https://github.com/pawprint0706/coderadio-on-tray";
    public static final String USER_AGENT = "coderadio-android/1.0.1 (+unofficial; " + SITE + ")";
    public static final long POLL_MS = 15_000;
}
