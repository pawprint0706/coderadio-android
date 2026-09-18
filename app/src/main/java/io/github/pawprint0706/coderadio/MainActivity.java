package io.github.pawprint0706.coderadio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;

@OptIn(markerClass = UnstableApi.class)
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 17, 25);
    private static final int CARD = Color.rgb(29, 30, 43);
    private static final int TEXT = Color.rgb(245, 243, 255);
    private static final int MUTED = Color.rgb(164, 166, 187);
    private static final int ACCENT = Color.rgb(180, 161, 255);
    private Settings settings;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private TextView title, artist, album, status, listeners, volumeLabel, warning, qualityHint;
    private ImageView cover;
    private Button play, high, low;
    private SeekBar volume;
    private byte[] displayedArt;
    private boolean launchHandled, started;
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onEvents(@NonNull Player player, @NonNull Player.Events events) { render(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> render();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        launchHandled = savedInstanceState != null && savedInstanceState.getBoolean("launch_handled");
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildScreen();
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener);
        render();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();

        LinearLayout header = row();
        TextView brand = text("CODE RADIO", 15, TEXT);
        brand.setLetterSpacing(0.18f);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button options = button(getString(R.string.settings), CARD, TEXT);
        options.setOnClickListener(v -> showSettings());
        header.addView(options, new LinearLayout.LayoutParams(dp(100), dp(52)));
        content.addView(header, full());

        status = text(getString(R.string.connecting), 12, ACCENT);
        status.setGravity(Gravity.CENTER);
        status.setBackground(round(CARD, 18));
        status.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.setMargins(0, dp(22), 0, dp(24));
        content.addView(status, statusParams);

        cover = new ImageView(this);
        cover.setImageResource(R.drawable.app_icon);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setContentDescription(getString(R.string.album_art));
        cover.setBackground(round(CARD, 22));
        cover.setClipToOutline(true);
        int artSize = Math.min(dp(300), getResources().getDisplayMetrics().widthPixels - dp(48));
        content.addView(cover, new LinearLayout.LayoutParams(artSize, artSize));

        title = text("Code Radio", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(26), 0, dp(8));
        content.addView(title, full());
        artist = text("freeCodeCamp", 17, MUTED);
        artist.setGravity(Gravity.CENTER);
        content.addView(artist, full());
        album = text("", 13, MUTED);
        album.setGravity(Gravity.CENTER);
        album.setPadding(0, dp(8), 0, 0);
        content.addView(album, full());
        listeners = text("", 12, ACCENT);
        listeners.setGravity(Gravity.CENTER);
        listeners.setPadding(0, dp(16), 0, dp(18));
        content.addView(listeners, full());

        play = button(getString(R.string.play), ACCENT, BG);
        play.setTextSize(18);
        play.setOnClickListener(v -> {
            if (controller == null) { connect(); return; }
            if (controller.getSessionExtras().getBoolean("requested")) controller.pause();
            else controller.sendCustomCommand(PlaybackService.START, Bundle.EMPTY);
        });
        content.addView(play, new LinearLayout.LayoutParams(Math.min(dp(300), artSize), dp(64)));
        TextView hint = text(getString(R.string.background_hint), 12, MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(14), 0, dp(24));
        content.addView(hint, full());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), dp(18), dp(18), dp(18));
        controls.setBackground(round(CARD, 20));
        volumeLabel = text("", 13, TEXT);
        controls.addView(volumeLabel, full());
        volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(settings.volume());
        volume.setContentDescription(getString(R.string.volume));
        volume.setProgressTintList(ColorStateList.valueOf(ACCENT));
        volume.setThumbTintList(ColorStateList.valueOf(ACCENT));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                volumeLabel.setText(getString(R.string.volume_value, progress));
                if (fromUser) {
                    settings.prefs.edit().putInt("volume", progress).apply();
                    if (controller != null) controller.setVolume(RadioRules.volume(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                settings.prefs.edit().putInt("volume", bar.getProgress()).apply();
            }
        });
        controls.addView(volume, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView quality = text(getString(R.string.quality), 13, TEXT);
        quality.setPadding(0, dp(8), 0, dp(10));
        controls.addView(quality, full());
        LinearLayout qualities = row();
        high = button("128 kbps", ACCENT, BG);
        low = button("64 kbps", BG, MUTED);
        high.setOnClickListener(v -> settings.prefs.edit().putInt("bitrate", 128).apply());
        low.setOnClickListener(v -> settings.prefs.edit().putInt("bitrate", 64).apply());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(50), 1);
        half.setMarginEnd(dp(8));
        qualities.addView(high, half);
        qualities.addView(low, new LinearLayout.LayoutParams(0, dp(50), 1));
        controls.addView(qualities, full());
        qualityHint = text("", 12, MUTED);
        qualityHint.setPadding(0, dp(10), 0, 0);
        controls.addView(qualityHint, full());
        content.addView(controls, new LinearLayout.LayoutParams(Math.min(dp(480), artSize + dp(24)), -2));

        warning = text("", 12, MUTED);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, dp(16), 0, 0);
        content.addView(warning, full());
        Button stop = button(getString(R.string.stop), BG, MUTED);
        stop.setOnClickListener(v -> {
            if (controller != null) controller.stop();
            finishAndRemoveTask();
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-2, dp(52));
        stopParams.topMargin = dp(16);
        content.addView(stop, stopParams);
        TextView footer = text(getString(R.string.unofficial), 11, MUTED);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, full());
    }

    @Override protected void onStart() { super.onStart(); started = true; connect(); }

    private void connect() {
        if (!started || controllerFuture != null) return;
        play.setEnabled(false);
        status.setText(R.string.connecting);
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(this, token)
                .setListener(new MediaController.Listener() {
                    @Override public void onExtrasChanged(@NonNull MediaController mediaController, @NonNull Bundle extras) { render(); }
                    @Override public void onDisconnected(@NonNull MediaController mediaController) {
                        if (controller == mediaController) {
                            controller = null;
                            if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
                            controllerFuture = null;
                            status.setText(R.string.connection_failed);
                            play.setText(R.string.retry);
                            play.setEnabled(true);
                        }
                    }
                }).buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            if (!started || controllerFuture != future) return;
            try {
                controller = future.get();
                controller.addListener(playerListener);
                if (!launchHandled) {
                    launchHandled = true;
                    if (settings.autoplay() && !getIntent().getBooleanExtra("from_notification", false)
                            && !controller.getSessionExtras().getBoolean("requested")) {
                        controller.sendCustomCommand(PlaybackService.START, Bundle.EMPTY);
                    }
                }
                render();
            } catch (Exception error) {
                MediaController.releaseFuture(future);
                controllerFuture = null;
                status.setText(R.string.connection_failed);
                play.setText(R.string.retry);
                play.setEnabled(true);
            }
        }, this::runOnUiThread);
    }

    private void render() {
        if (play == null) return;
        Bundle state = controller == null ? Bundle.EMPTY : controller.getSessionExtras();
        MediaMetadata metadata = controller == null ? MediaMetadata.EMPTY : controller.getMediaMetadata();
        title.setText(metadata.title == null ? state.getString("title", "Code Radio") : metadata.title);
        artist.setText(metadata.artist == null ? state.getString("artist", "freeCodeCamp") : metadata.artist);
        album.setText(metadata.albumTitle == null ? state.getString("album", "") : metadata.albumTitle);
        album.setVisibility(album.length() == 0 ? View.GONE : View.VISIBLE);
        if (controller != null) status.setText(state.getString("status", getString(R.string.ready)));
        play.setText(state.getBoolean("requested") ? R.string.pause : R.string.play);
        play.setEnabled(controller != null);
        int count = state.getInt("listeners", -1);
        listeners.setText(count < 0 ? getString(R.string.listener_waiting) : getString(R.string.listeners_value, count));
        listeners.setVisibility(settings.listeners() ? View.VISIBLE : View.GONE);
        cover.setVisibility(settings.artwork() ? View.VISIBLE : View.GONE);
        byte[] art = settings.artwork() ? metadata.artworkData : null;
        if (!Arrays.equals(art, displayedArt)) {
            displayedArt = art;
            if (art == null) cover.setImageResource(R.drawable.app_icon);
            else cover.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
        }
        volumeLabel.setText(getString(R.string.volume_value, settings.volume()));
        if (!volume.isPressed()) volume.setProgress(settings.volume());
        colorQuality(high, settings.bitrate() == 128);
        colorQuality(low, settings.bitrate() == 64);
        boolean fallback = settings.bitrate() == 64 && !state.getBoolean("low_available");
        qualityHint.setText(fallback ? R.string.quality_fallback : R.string.quality_hint);
        warning.setText(state.getBoolean("metadata_stale") ? getString(R.string.metadata_stale) : "");
        warning.setVisibility(warning.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void showSettings() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(8), dp(24), dp(16));
        addSwitch(body, R.string.autoplay_setting, "autoplay", settings.autoplay());
        addSwitch(body, R.string.artwork_setting, "artwork", settings.artwork());
        addSwitch(body, R.string.listeners_setting, "listeners", settings.listeners());
        Button website = button(getString(R.string.website), CARD, TEXT);
        website.setOnClickListener(v -> openUrl(RadioConfig.SITE));
        body.addView(website, full());
        Button source = button(getString(R.string.original_source), CARD, TEXT);
        source.setOnClickListener(v -> openUrl(RadioConfig.SOURCE));
        body.addView(source, full());
        TextView notice = text(getString(R.string.about), 12, MUTED);
        notice.setPadding(0, dp(16), 0, 0);
        body.addView(notice, full());
        new AlertDialog.Builder(this).setTitle(R.string.settings).setView(body)
                .setPositiveButton(R.string.close, null).show();
    }

    private void addSwitch(LinearLayout body, int label, String key, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(15);
        toggle.setTextColor(TEXT);
        toggle.setPadding(0, dp(14), 0, dp(14));
        toggle.setMinHeight(dp(56));
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, enabled) -> settings.prefs.edit().putBoolean(key, enabled).apply());
        body.addView(toggle, full());
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException ignored) {
            new AlertDialog.Builder(this).setMessage(url).setPositiveButton(R.string.close, null).show();
        }
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("launch_handled", launchHandled);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onStop() {
        started = false;
        if (controller != null) controller.removeListener(playerListener);
        ListenableFuture<MediaController> future = controllerFuture;
        controllerFuture = null;
        controller = null;
        if (future != null) MediaController.releaseFuture(future);
        super.onStop();
    }
    @Override protected void onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setMinHeight(dp(48));
        return button;
    }
    private void colorQuality(Button button, boolean selected) {
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? ACCENT : BG));
        button.setTextColor(selected ? BG : MUTED);
        button.setSelected(selected);
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
}
