package io.github.pawprint0706.coderadio;

import java.net.URI;

/** Platform-independent rules shared by parsing, playback and offline regression tests. */
public final class RadioRules {
    private RadioRules() {}
    public static String clean(String value) {
        if (value == null) return "";
        String text = value.trim();
        return text.equalsIgnoreCase("unknown") || text.equalsIgnoreCase("null") ? "" : text;
    }
    public static String[] recoverSong(String artist, String title, String text) {
        artist = clean(artist);
        title = clean(title);
        text = clean(text);
        for (String separator : new String[]{" — ", " – ", " - "}) {
            int split = text.indexOf(separator);
            if (split >= 0) {
                if (artist.isEmpty()) artist = clean(text.substring(0, split));
                if (title.isEmpty()) title = clean(text.substring(split + separator.length()));
                break;
            }
        }
        if (title.isEmpty()) title = text.replaceFirst("^[-—–]\\s*", "").trim();
        if (title.isEmpty()) title = "Code Radio";
        return new String[]{artist, title};
    }
    public static String httpsUrl(String raw) {
        try {
            URI url = new URI(raw == null ? "" : raw.trim());
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                    || url.getUserInfo() != null || url.getFragment() != null) return "";
            return url.toASCIIString();
        } catch (Exception ignored) { return ""; }
    }
    public static String artworkUrl(String raw) {
        if (clean(raw).isEmpty()) return "";
        try { return httpsUrl(URI.create(RadioConfig.API).resolve(raw.trim()).toString()); }
        catch (Exception ignored) { return ""; }
    }
    public static long retryDelay(int attempt) {
        return Math.min(30_000, 1_000L << Math.min(5, Math.max(0, attempt)));
    }
}
