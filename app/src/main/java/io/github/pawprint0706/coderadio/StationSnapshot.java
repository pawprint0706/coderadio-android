package io.github.pawprint0706.coderadio;

public final class StationSnapshot {
    public final boolean online;
    public final String title, artist, album, artwork, high, low, listen;
    public final int listeners;
    public StationSnapshot(boolean online, String title, String artist, String album,
                           String artwork, String high, String low, String listen, int listeners) {
        this.online = online;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artwork = artwork;
        this.high = high;
        this.low = low;
        this.listen = listen;
        this.listeners = Math.max(0, listeners);
    }
    public String stream(int bitrate) {
        if (bitrate == 64 && !low.isEmpty()) return low;
        return high.isEmpty() ? listen : high;
    }
    public String trackKey() { return title + "\n" + artist + "\n" + album + "\n" + artwork; }
}
