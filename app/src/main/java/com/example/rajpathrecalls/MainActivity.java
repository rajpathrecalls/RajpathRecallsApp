package com.example.rajpathrecalls;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity{
    private boolean isPaused = true;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int connection_state = 0;
    String CHANNEL_ID = "rajpathrecalls";// The id of the channel.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "Connecting..", Toast.LENGTH_SHORT).show();
        setContentView(R.layout.activity_main);
        findViewById(R.id.button1).setOnClickListener(pauseListener);
        mediaPlayer = new MediaPlayer();
        connectionThread.start();

        if (android.os.Build.VERSION.SDK_INT >= 26) {

            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            CharSequence name = getResources().getString(R.string.app_name);// The user-visible name of the channel.
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(mChannel);
        }
        registerReceiver(notifReceiver, new IntentFilter("playpause"));
//        startService(new Intent(this, ))

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }

    void togglePlayer(){
        if (connection_state == 1) {
            isPaused = !isPaused;
            if (isPaused) {
                mediaPlayer.pause();
                unregisterReceiver(pauseForOutputChange);
            }
            else {
                int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    mediaPlayer.start();
                    registerReceiver(pauseForOutputChange, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                } else {
                    isPaused = true;
                }
            }
            makeNotification();
        } else if (connection_state == -1) {
            connectionThread.start();
            //TODO progress bar
        }
    }

    private void makeNotification() {
        Bitmap pic = BitmapFactory.decodeResource(getResources(), R.drawable.download);
        MediaSessionCompat msess = new MediaSessionCompat(this, "tag");
        NotificationCompat.Builder mBuilder;

        Intent notificationIntent = new Intent(this, NotificationActionService.class)
                .setAction("playpause");
        PendingIntent pendingNotificationIntent = PendingIntent.getBroadcast(this, 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RajpathRecalls")
                .setLargeIcon(pic)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                        "playpause", pendingNotificationIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(msess.getSessionToken())
                        .setShowActionsInCompactView(0))
                .setAutoCancel(false)
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW);


        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, mBuilder.build());
    }

    private View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            togglePlayer();
        }
    };

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if(!isPaused && (focusChange == AudioManager.AUDIOFOCUS_LOSS))
                togglePlayer();
        }
    };

    private Thread connectionThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                connection_state = 0;
                URL url = new URL("https://stream.zeno.fm/2ce1nx83g2zuv");
                HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                ucon.setInstanceFollowRedirects(false);
                String myurl = ucon.getHeaderField("Location");
                if (!"https".equals(myurl.substring(0, 5)))
                    myurl = "https" + myurl.substring(4);
                mediaPlayer.setDataSource(myurl);
                mediaPlayer.prepare();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        connection_state = 1;
                        Toast.makeText(getBaseContext(), "Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException ignored) {
                connection_state = -1;
            }
        }
    });

    BroadcastReceiver notifReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //play pause from notif
            togglePlayer();
        }
    };

    //pause when listening
    BroadcastReceiver pauseForOutputChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                togglePlayer();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notifReceiver);
    }
}