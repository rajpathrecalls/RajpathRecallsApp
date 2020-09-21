package com.example.rajpathrecalls;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadioPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener, SimpleExoPlayer.EventListener {

    private boolean isPaused = true, isInForeground = false;
    private long player_offset = 0, player_offset_start = -1;
    private SimpleExoPlayer mediaPlayer, temp_switch = null;
    private int connection_state = CONNECTION_FAILED;
    private MediaSessionCompat mediaSession;

    private final String CHANNEL_ID = "com.rajpathrecalls.notifications",
            PLAY_PAUSE_ACTION = "com.rajpathrecalls.playpause";        //public broadcasts action, so need unique

    static final int CONNECTION_SUCCESS = 1, CONNECTION_TRYING = 2, CONNECTION_FAILED = 3;
    static final String PLAY_PAUSE_BROADCAST = "playpause", CONNECTION_BROADCAST = "connectionupdate",
            SYNC_BROADCAST = "sync_update";     //local broadcast actions

    public class LocalBinder extends Binder {
        public RadioPlayerService getService() {
            return RadioPlayerService.this;
        }
    }

    private final IBinder iBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new SimpleExoPlayer.Builder(this).build();

//        mediaPlayer.setAudioAttributes(
//                new AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_MEDIA)
//                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .build()
//        );

        mediaSession = new MediaSessionCompat(this, CHANNEL_ID);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent keyEvent = (KeyEvent) mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    togglePlayer(!isPaused);
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        });

        connectToRadio();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            CharSequence name = getResources().getString(R.string.app_name);// The user-visible name of the channel.
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(mChannel);
        }

        registerReceiver(notifActionReceiver, new IntentFilter(PLAY_PAUSE_ACTION));
    }

    void togglePlayer(boolean shouldPause) {
        if (connection_state != CONNECTION_SUCCESS || isPaused == shouldPause)
            return;

        if (isPaused) {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).
                        requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(this)
                        .build());
            } else {
                result = ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).
                        requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaSession.setActive(true);
                mediaPlayer.play();
                updatePlayerOffset();
                registerReceiver(pauseForOutputChange, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                setPaused(false);
            }

        } else {
            mediaPlayer.pause();
            player_offset_start = System.currentTimeMillis();
            unregisterReceiver(pauseForOutputChange);
            setPaused(true);
        }
        makeNotification();
    }

    void syncToRadio() {
        temp_switch = mediaPlayer;
        mediaPlayer = new SimpleExoPlayer.Builder(this).build();
        connectToRadio();
    }

    int getConnectionState() {
        return connection_state;
    }

    boolean isPaused() {
        return isPaused;
    }

    int getPlayerOffset() {
        return (int) player_offset;
    }

    private void updatePlayerOffset() {
        if (player_offset_start != -1)
            player_offset += (System.currentTimeMillis() - player_offset_start) / 1000;
    }

    private void makeNotification() {
        final int NOTIFICATION_ID = 6;
        Bitmap pic = BitmapFactory.decodeResource(getResources(), R.drawable.notif_album_art);

        Intent notifButtonIntent = new Intent(PLAY_PAUSE_ACTION);   //implicit intent
        PendingIntent actionIntent = PendingIntent.getBroadcast(this, 0, notifButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notifClickIntent = new Intent(this, MainActivity.class); //explicit intent
        notifClickIntent.setAction(Intent.ACTION_MAIN);
        notifClickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notifClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Lorem Ipsum")
                .setContentText("Lorem ipsum dolor sit amet")
                .setLargeIcon(pic)
                .setOngoing(!isPaused)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .addAction(isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                        PLAY_PAUSE_ACTION, actionIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if(isInForeground){
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
        } else {
            startForeground(NOTIFICATION_ID, mBuilder.build());
            isInForeground = true;
        }
    }

    private void sendLocalBroadcast(Intent intent) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void setPaused(boolean is_paused) {
        isPaused = is_paused;
        Intent intent = new Intent(PLAY_PAUSE_BROADCAST);
        intent.putExtra(PLAY_PAUSE_BROADCAST, isPaused);
        sendLocalBroadcast(intent);
    }

    private void updateConnectionState(int new_state, boolean shouldCallback) {
        connection_state = new_state;
        if (!shouldCallback)
            return;

        Intent intent = new Intent(CONNECTION_BROADCAST);
        intent.putExtra(CONNECTION_BROADCAST, connection_state);
        sendLocalBroadcast(intent);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            togglePlayer(true);
            mediaSession.setActive(false);
        }
    }

    private void setSynced(boolean isSyncSuccess) {
        if (isSyncSuccess) {
            player_offset = 0;  //synced
            player_offset_start = -1;
        }
        Intent intent = new Intent(SYNC_BROADCAST);
        intent.putExtra(SYNC_BROADCAST, isSyncSuccess);
        sendLocalBroadcast(intent);
    }

    void connectToRadio() {
        //get redirect url
        new Thread(new Runnable() {
            @Override
            public void run() {
                String myurl;
                updateConnectionState(CONNECTION_TRYING, false);
                try {
                    HttpURLConnection ucon = (HttpURLConnection)
                            new URL("https://stream.zeno.fm/2ce1nx83g2zuv").openConnection();
                    myurl = ucon.getHeaderField("Location");
                    if (!"https".equals(myurl.substring(0, 5)))
                        myurl = "https" + myurl.substring(4);
                } catch (IOException | NullPointerException e) {
                    myurl = "connection.failed";
                }
                connectToURL(myurl);
            }
        }).start();
    }

    private void connectToURL(final String url) {
        //run on ui thread
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
                        RadioPlayerService.this, "exoplayer-codelab");
                MediaSource source = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(url));

                mediaPlayer.setMediaSource(source);
                mediaPlayer.addListener(RadioPlayerService.this);
                mediaPlayer.prepare();
            }
        });
    }

    //exoplayer event listener
    @Override
    public void onPlaybackStateChanged(int state) {

        if (state == SimpleExoPlayer.STATE_READY) {
            if (temp_switch != null) {      //sync op
                temp_switch.release();
                temp_switch = null;
                if (isPaused)
                    togglePlayer(false); //need to update notification if paused
                else
                    mediaPlayer.play();        //no need to update notification here

                setSynced(true);
                updateConnectionState(CONNECTION_SUCCESS, false);

            } else {
                updateConnectionState(CONNECTION_SUCCESS, true);
            }
        }
    }

    //exoplayer event listener
    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.i("mylog", "Radio Connection Failed: " + error.getMessage());
        error.printStackTrace();

        if (temp_switch != null) {      //sync failed. go back to old media player
            mediaPlayer.release();
            mediaPlayer = temp_switch;
            temp_switch = null;
            updateConnectionState(CONNECTION_SUCCESS, false); //old player is connected
            setSynced(false);
        } else {
            updateConnectionState(CONNECTION_FAILED, true);
        }
    }

    //pause when listening
    private BroadcastReceiver pauseForOutputChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                togglePlayer(true);
        }
    };

    //notification action receiver
    private BroadcastReceiver notifActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            togglePlayer(!isPaused);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Handle application closing
        if (!isPaused) {
            unregisterReceiver(pauseForOutputChange);
        }
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(this);
        unregisterReceiver(notifActionReceiver);
        mediaPlayer.release();
        mediaSession.release();
        NotificationManagerCompat.from(this).cancelAll();

        // Destroy the service
        stopSelf();
    }
}
