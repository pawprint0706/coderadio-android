package io.github.pawprint0706.coderadio;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

@OptIn(markerClass = UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    public static final SessionCommand START = new SessionCommand("coderadio.START", Bundle.EMPTY);
    private static final SessionCommand STOP = new SessionCommand("coderadio.STOP", Bundle.EMPTY);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService metadataWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
    private final PlaybackIntent intent = new PlaybackIntent();
    private ExoPlayer player;
    private MediaSession session;
    private Settings settings;
    private StationSnapshot snapshot;
    private byte[] fallbackArt, artwork;
    private String activeUrl = "", artworkKey = "", status = "";
    private boolean fetching, destroyed, metadataStale, retryPending, stopped;
    private int retryAttempt;
    private long artGeneration;
    private Runnable retry;
    private ConnectivityManager connectivity;
    private boolean networkRegistered;
    private final Runnable poll = this::fetchMetadata;
    private final Runnable stablePlayback = () -> retryAttempt = 0;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        if (destroyed || player == null) return;
        if ("bitrate".equals(key) && intent.requested() && snapshot != null && snapshot.online) connectStream();
        if ("artwork".equals(key)) {
            artworkKey = "";
            updateArtwork();
            updateMediaMetadata();
        }
        publishState();
    };
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network network) {
            main.post(() -> {
                if (!destroyed && !stopped && intent.requested()) {
                    fetchMetadata();
                    if (player.getPlayerError() != null || retryPending) scheduleRetry(500);
                }
            });
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        settings = new Settings(this);
        fallbackArt = Artwork.fallback(this);
        artwork = fallbackArt;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(RadioConfig.USER_AGENT)
                .setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.setVolume(1.0f);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                main.removeCallbacks(stablePlayback);
                if (isPlaying) {
                    status = getString(R.string.live);
                    main.postDelayed(stablePlayback, 10_000);
                }
                publishState();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (intent.requested() && state == Player.STATE_ENDED) scheduleRetry(-1);
                if (intent.requested() && state == Player.STATE_BUFFERING) status = getString(R.string.buffering);
                publishState();
            }
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                if (intent.requested()) scheduleRetry(-1);
            }
            @Override public void onPlayWhenReadyChanged(boolean ready, int reason) {
                // Headset disconnect / permanent audio focus loss must not trigger a network restart.
                if (!ready && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                        || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)) {
                    pausePlayback();
                }
            }
        });
        Intent activity = new Intent(this, MainActivity.class).putExtra("from_notification", true);
        PendingIntent openApp = PendingIntent.getActivity(this, 0, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        session = new MediaSession.Builder(this, new RadioPlayer(player))
                .setSessionActivity(openApp)
                .setCallback(new MediaSession.Callback() {
                    @NonNull @Override public MediaSession.ConnectionResult onConnect(
                            @NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller) {
                        MediaSession.ConnectionResult defaults = MediaSession.Callback.super.onConnect(mediaSession, controller);
                        Player.Commands transport = defaults.availablePlayerCommands.buildUpon()
                                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                                .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                                .remove(Player.COMMAND_SEEK_TO_NEXT)
                                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_BACK)
                                .remove(Player.COMMAND_SEEK_FORWARD)
                                .remove(Player.COMMAND_SET_VOLUME).build();
                        return MediaSession.ConnectionResult.accept(
                                defaults.availableSessionCommands.buildUpon().add(START).add(STOP).build(), transport);
                    }
                    @NonNull @Override public ListenableFuture<SessionResult> onCustomCommand(
                            @NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller,
                            @NonNull SessionCommand command, @NonNull Bundle args) {
                        if (START.customAction.equals(command.customAction)) {
                            requestPlay();
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        if (STOP.customAction.equals(command.customAction)) {
                            stopPlayback();
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
                    }
                }).build();
        session.setCustomLayout(Collections.singletonList(new CommandButton.Builder()
                .setDisplayName(getString(R.string.stop_short)).setIconResId(R.drawable.ic_stop)
                .setSessionCommand(STOP).build()));
        DefaultMediaNotificationProvider notification = new DefaultMediaNotificationProvider.Builder(this).build();
        notification.setSmallIcon(R.drawable.ic_radio);
        setMediaNotificationProvider(notification);
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener);
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback);
            networkRegistered = true;
        } catch (RuntimeException ignored) { /* Timed retry remains available. */ }
        status = getString(R.string.ready);
        publishState();
        fetchMetadata();
    }

    private void requestPlay() {
        if (destroyed) return;
        if (intent.requested() && (player.isPlaying() || player.getPlaybackState() == Player.STATE_BUFFERING)) return;
        stopped = false;
        intent.play();
        cancelRetry();
        retryAttempt = 0;
        if (snapshot != null && snapshot.online && !snapshot.stream(settings.bitrate()).isEmpty()) {
            connectStream();
        } else {
            status = getString(R.string.connecting);
        }
        fetchMetadata();
        publishState();
    }

    private void pausePlayback() {
        intent.pause();
        cancelRetry();
        player.pause();
        player.stop(); // Drop old live buffers. The next play opens the current broadcast.
        activeUrl = "";
        status = getString(R.string.paused);
        publishState();
    }

    private void stopPlayback() {
        pausePlayback();
        stopped = true;
        main.removeCallbacks(poll);
        player.clearMediaItems(); // Media3 removes its foreground notification.
        status = getString(R.string.stopped);
        publishState();
        stopSelf();
    }

    private void connectStream() {
        if (destroyed || !intent.requested() || snapshot == null || !snapshot.online) return;
        String url = snapshot.stream(settings.bitrate());
        if (url.isEmpty()) {
            status = getString(R.string.no_stream);
            publishState();
            return;
        }
        cancelRetry();
        activeUrl = url;
        player.setMediaItem(new MediaItem.Builder().setMediaId("coderadio").setUri(url)
                .setMediaMetadata(mediaMetadata()).build());
        player.prepare();
        player.play();
        status = getString(R.string.buffering);
        publishState();
    }

    private void fetchMetadata() {
        main.removeCallbacks(poll);
        if (destroyed || stopped || fetching) return;
        fetching = true;
        metadataWorker.execute(() -> {
            try {
                StationSnapshot result = RadioHttp.snapshot();
                main.post(() -> {
                    if (destroyed || stopped) { fetching = false; return; }
                    boolean wasOffline = snapshot != null && !snapshot.online;
                    snapshot = result;
                    metadataStale = false;
                    updateArtwork();
                    updateMediaMetadata();
                    if (!result.online) {
                        cancelRetry();
                        player.pause();
                        player.stop();
                        activeUrl = "";
                        status = getString(R.string.station_offline);
                    } else if (intent.requested() && (activeUrl.isEmpty() || wasOffline
                            || !result.stream(settings.bitrate()).equals(activeUrl))) {
                        connectStream();
                    }
                    metadataFinished();
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (destroyed || stopped) { fetching = false; return; }
                    metadataStale = true;
                    if (snapshot == null && intent.requested()) status = getString(R.string.metadata_unavailable);
                    metadataFinished();
                });
            }
        });
    }

    private void metadataFinished() {
        fetching = false;
        publishState();
        // Continue while playing; poll more slowly while paused to reduce radio / battery use.
        main.postDelayed(poll, intent.requested() ? RadioConfig.POLL_MS : 60_000);
    }

    private void updateArtwork() {
        if (snapshot == null) return;
        String key = snapshot.trackKey() + settings.artwork();
        if (key.equals(artworkKey)) return;
        artworkKey = key;
        long generation = ++artGeneration;
        artwork = fallbackArt; // Never show the previous song's artwork for a new song.
        if (!settings.artwork() || snapshot.artwork.isEmpty()) return;
        String url = snapshot.artwork;
        artworkWorker.execute(() -> {
            try {
                byte[] result = Artwork.download(url);
                main.post(() -> {
                    if (destroyed || generation != artGeneration || !settings.artwork()) return;
                    artwork = result;
                    updateMediaMetadata();
                });
            } catch (Exception ignored) {
                main.post(() -> {
                    // Retry on the next metadata poll; failure never stops audio.
                    if (!destroyed && generation == artGeneration) artworkKey = "";
                });
            }
        });
    }

    private MediaMetadata mediaMetadata() {
        return new MediaMetadata.Builder()
                .setTitle(snapshot == null ? "Code Radio" : snapshot.title)
                .setArtist(snapshot == null || snapshot.artist.isEmpty() ? "freeCodeCamp" : snapshot.artist)
                .setAlbumTitle(snapshot == null ? "" : snapshot.album)
                .setStation("Code Radio")
                .setIsPlayable(true)
                .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build();
    }

    private void updateMediaMetadata() {
        if (player.getMediaItemCount() == 0) {
            // Let the UI show a track before first playback via session extras.
            publishState();
            return;
        }
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) {
            // Only metadata changes: Media3 keeps the same media source / audio connection.
            player.replaceMediaItem(0, current.buildUpon().setMediaMetadata(mediaMetadata()).build());
        }
        publishState();
    }

    private void scheduleRetry(long delayOverride) {
        if (destroyed || !intent.requested()) return;
        cancelRetry();
        long token = intent.generation();
        long delay = delayOverride >= 0 ? delayOverride : RadioRules.retryDelay(retryAttempt++);
        retryPending = true;
        status = getString(R.string.reconnecting, Math.max(1, delay / 1000));
        retry = () -> {
            retryPending = false;
            if (!destroyed && intent.mayRetry(token)) {
                connectStream();
                fetchMetadata();
            }
        };
        main.postDelayed(retry, delay);
        publishState();
    }

    private void cancelRetry() {
        if (retry != null) main.removeCallbacks(retry);
        main.removeCallbacks(stablePlayback);
        retry = null;
        retryPending = false;
    }

    private void publishState() {
        if (session == null || destroyed) return;
        Bundle state = new Bundle();
        state.putString("status", status);
        state.putBoolean("requested", intent.requested());
        state.putBoolean("metadata_stale", metadataStale);
        state.putInt("listeners", snapshot == null ? -1 : snapshot.listeners);
        state.putBoolean("low_available", snapshot != null && !snapshot.low.isEmpty());
        state.putString("title", snapshot == null ? "Code Radio" : snapshot.title);
        state.putString("artist", snapshot == null ? "freeCodeCamp" : snapshot.artist);
        state.putString("album", snapshot == null ? "" : snapshot.album);
        session.setSessionExtras(state);
    }

    @Nullable @Override public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (!isPlaybackOngoing()) stopPlayback();
    }

    @Override public void onDestroy() {
        destroyed = true;
        intent.pause();
        main.removeCallbacksAndMessages(null);
        metadataWorker.shutdownNow();
        artworkWorker.shutdownNow();
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener);
        if (networkRegistered) connectivity.unregisterNetworkCallback(networkCallback);
        if (session != null) session.release();
        if (player != null) player.release();
        super.onDestroy();
    }

    private final class RadioPlayer extends ForwardingPlayer {
        RadioPlayer(Player delegate) { super(delegate); }
        @Override public void play() { requestPlay(); }
        @Override public void pause() { pausePlayback(); }
        @Override public void setPlayWhenReady(boolean ready) { if (ready) requestPlay(); else pausePlayback(); }
        @Override public void stop() { stopPlayback(); }
        @Override public void prepare() {
            // Metadata provides the real mount URLs; requestPlay prepares when they are available.
            if (player.getMediaItemCount() > 0) player.prepare();
        }
    }
}
