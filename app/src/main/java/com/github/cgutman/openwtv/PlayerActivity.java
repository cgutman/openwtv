package com.github.cgutman.openwtv;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;

import com.github.cgutman.openwtv.protocol.ExtendConnection;
import com.github.cgutman.openwtv.utils.Dialog;
import com.github.cgutman.openwtv.utils.SpinnerDialog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class PlayerActivity extends AppCompatActivity implements View.OnSystemUiVisibilityChangeListener {
    public static final String ADDRESS_EXTRA = "com.github.cgutman.openwtv.PlayerActivity.ADDRESS";
    public static final String PORT_EXTRA = "com.github.cgutman.openwtv.PlayerActivity.PORT";
    public static final String PASSWD_EXTRA = "com.github.cgutman.openwtv.PlayerActivity.PASSWD";
    public static final String CHANNELID_EXTRA = "com.github.cgutman.openwtv.PlayerActivity.CHANNELID";

    private String addressString;
    private int portNumber;
    private String passwdString;
    private int channelId;

    private Thread loaderThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addressString = getIntent().getStringExtra(ADDRESS_EXTRA);
        portNumber = getIntent().getIntExtra(PORT_EXTRA, 0);
        passwdString = getIntent().getStringExtra(PASSWD_EXTRA);
        channelId = getIntent().getIntExtra(CHANNELID_EXTRA, -1);

        if (channelId == -1) {
            finish();
            return;
        }

        // Full-screen and don't let the display go off
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // If we're going to use immersive mode, we want to have
        // the entire screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        }

        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        setContentView(R.layout.activity_player);

        // Display a spinner dialog while transcoding
        final SpinnerDialog spinner = SpinnerDialog.displayDialog(this, "Loading Channel",
                "Connecting...", true);

        loaderThread = new Thread() {
            public void run() {
                try {
                    InetAddress address;

                    try {
                        address = InetAddress.getByName(addressString);
                    } catch (UnknownHostException e) {
                        Dialog.displayDialog(PlayerActivity.this, "Invalid Address", "The address could not be found.", false);
                        return;
                    }

                    ExtendConnection connection = ExtendConnection.establishConnection(address, portNumber, passwdString);

                    // Set the desired encoding profile
                    connection.setResolution1280x720();

                    // Start transcoding
                    connection.beginTranscode(channelId);

                    ExtendConnection.TranscodeStatus status;
                    do {
                        // Wait 500 ms before polling again
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return;
                        }

                        // Update the dialog after each poll
                        status = connection.requestTranscodeStatus();
                        spinner.setMessage("Transcoding: "+status.percentage+"%");
                    } while (!status.finishedBuffering);

                    // Close the spinner
                    spinner.dismiss();

                    final String url = connection.getPlaybackUrl(channelId);

                    PlayerActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VideoView videoView = (VideoView)findViewById(R.id.videoView);
                            videoView.setVideoURI(Uri.parse(url));
                            videoView.requestFocus();
                            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                public void onPrepared(MediaPlayer mp) {
                                    hideSystemUi(1000);
                                    mp.start();
                                }
                            });
                            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                                    Dialog.displayDialog(PlayerActivity.this, "Playback Error", "The video player has encountered an error.", true);
                                    return true;
                                }
                            });
                        }
                    });
                } catch (IOException e) {
                    Dialog.displayDialog(PlayerActivity.this, "Connection Error", e.getMessage(), true);
                }
            }
        };
        loaderThread.start();
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            // Use immersive mode on 4.4+ or standard low profile on previous builds
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                PlayerActivity.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            else {
                PlayerActivity.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    };

    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // The stream breaks if we pause since the video player closes the connection
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();

        loaderThread.interrupt();

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(2000);
        }
        // This flag is only set on 4.4+
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT &&
                (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(2000);
        }
        // This flag is only set before 4.4+
        else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT &&
                (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
            hideSystemUi(2000);
        }
    }
}
