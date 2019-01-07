package com.kaltura.kalturadeviceinfo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionEventListener;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PlayerActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private PlayerView mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = this::hide;
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener mDelayHideTouchListener = (view, motionEvent) -> {
        if (AUTO_HIDE) {
            delayedHide(AUTO_HIDE_DELAY_MILLIS);
        }
        return false;
    };
    private SimpleExoPlayer player;
    private FileWriter logWriter;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_player);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);

        try {
            setupPlayer();
        } catch (UnsupportedDrmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(view -> toggle());

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mContentView.setPlayer(null);
        player.release();

        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void log(String message) {
        try {
            logWriter.append(String.valueOf(SystemClock.elapsedRealtime() - startTime)).append(" ").append(message).append('\n').flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupPlayer() throws com.google.android.exoplayer2.drm.UnsupportedDrmException, IOException {

        final Intent intent = getIntent();
        final String dataString = intent.getDataString();

        final String contentUrl;
        final String licenseUrl;

        if (dataString == null) {
            return;
        }

        switch (dataString) {
            case "Kaltura":
                contentUrl = "https://cdnapisec.kaltura.com/p/2222401/sp/2222401/playManifest/entryId/1_kvv3j1zk/tags/mobile/protocol/https/format/mpegdash/manifest.mpd";
                licenseUrl = "https://udrm.kaltura.com/cenc/widevine/license?custom_data=eyJjYV9zeXN0ZW0iOiJPVlAiLCJ1c2VyX3Rva2VuIjoiZGpKOE1qSXlNalF3TVh5bkdiU2JIQ2FOaUQxYzBYRDlfUlBvWmFxaFprTkhTUVRCZ3VHVUVNYUtFNTZKS3Q4YjM5aGZtZTU4R1N3bHBoM05Ra0pTYWVxYmJNZ2Zud3F0d3hlUkM2MW0xenFBRmFhN3h0c1NGd01YX1dJME9LMkoyQ0NZX0VOVlNkbUpiLW89IiwiYWNjb3VudF9pZCI6IjIyMjI0MDEiLCJjb250ZW50X2lkIjoiMV9rdnYzajF6ayIsImZpbGVzIjoiMV84M3dpZ3ZiYSwxX2toemh3YWs2In0%3D&signature=BoWR1xlAxXzDuP7oQM5GtDcQrpk%3D";
                break;
            case "Google":
                contentUrl = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd";
                licenseUrl = "https://proxy.uat.widevine.com/proxy?video_id=48fcc369939ac96c&provider=widevine_test";
                break;
            default:
                return;
        }



        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmmssZ", Locale.ROOT);
        String formattedDate = sdf.format(new Date());

        startTime = SystemClock.elapsedRealtime();

        final File logFile = new File(getExternalFilesDir(null), "playback-" + formattedDate + ".log");
        logWriter = new FileWriter(logFile);

        final String userAgent = Util.getUserAgent(this, "KalturaDeviceInfo");

        HttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSourceFactory(userAgent);

        final DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager =
                DefaultDrmSessionManager.newWidevineInstance(new HttpMediaDrmCallback(licenseUrl, httpDataSourceFactory), null);

        drmSessionManager.addListener(new Handler(Looper.getMainLooper()), new DefaultDrmSessionEventListener() {
            @Override
            public void onDrmKeysLoaded() {
                log("onDrmKeysLoaded");
            }

            @Override
            public void onDrmSessionManagerError(Exception error) {
                log("onDrmSessionManagerError: " + error);
            }

            @Override
            public void onDrmKeysRestored() {
                log("onDrmKeysRestored");
            }

            @Override
            public void onDrmKeysRemoved() {
                log("onDrmKeysRemoved");
            }

            @Override
            public void onDrmSessionAcquired() {
                log("onDrmSessionAcquired");
            }

            @Override
            public void onDrmSessionReleased() {
                log("onDrmSessionReleased");
            }
        });

        player = ExoPlayerFactory.newSimpleInstance(this, new DefaultRenderersFactory(this), new DefaultTrackSelector(), drmSessionManager);
        mContentView.setPlayer(player);


        player.addListener(new Player.EventListener() {
            @Override
            public void onLoadingChanged(boolean isLoading) {
                log("onLoadingChanged: " + isLoading);
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                String state;
                switch (playbackState) {
                    case Player.STATE_IDLE:
                        state = "STATE_IDLE";
                        break;
                    case Player.STATE_BUFFERING:
                        state = "STATE_BUFFERING";
                        break;
                    case Player.STATE_READY:
                        state = "STATE_READY";
                        break;
                    case Player.STATE_ENDED:
                        state = "STATE_ENDED";
                        break;
                    default:
                        state = "UNKNOWN:" + playbackState;
                }
                log("onPlayerStateChanged: " + playWhenReady + ", " + state);
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                log("onPlayerError: " + error + "\n" + Log.getStackTraceString(error));
            }

            @Override
            public void onSeekProcessed() {
                log("onSeekProcessed: " + player.getCurrentPosition()/1000f);
            }
        });


        // Produces DataSource instances through which media data is loaded.

        Uri uri = Uri.parse(contentUrl);
        final DashMediaSource mediaSource = new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(httpDataSourceFactory), httpDataSourceFactory).createMediaSource(uri);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button.
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
