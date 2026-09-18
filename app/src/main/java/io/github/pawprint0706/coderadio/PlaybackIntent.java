package io.github.pawprint0706.coderadio;

/** User intent is separate from transient buffering / end-of-stream / network state. */
public final class PlaybackIntent {
    private boolean requested;
    private long generation;
    public void play() { requested = true; generation++; }
    public void pause() { requested = false; generation++; }
    public boolean requested() { return requested; }
    public long generation() { return generation; }
    public boolean mayRetry(long token) { return requested && token == generation; }
}
