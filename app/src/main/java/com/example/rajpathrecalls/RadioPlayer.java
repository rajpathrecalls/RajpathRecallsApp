package com.example.rajpathrecalls;

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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class RadioPlayer {
    private WeakReference<Context> context;
    private boolean isPaused = true;
    private long player_offset = 0, player_offset_start = -1;
    private MediaPlayer mediaPlayer, temp_switch;
    private AudioManager audioManager;
    private int connection_state = CONNECTION_FAILED;
    private NotificationManagerCompat notificationManager;
    private MediaSessionCompat mediaSession;

    private final String CHANNEL_ID = "rajpathrecalls";// The id of the channel.
    static final int CONNECTION_SUCCESS = 1, CONNECTION_TRYING = 0, CONNECTION_FAILED = -1;

    RadioPlayer(Context ctx) {
        context = new WeakReference<>(ctx);
    }

    void prepare() {
        notificationManager = NotificationManagerCompat.from(context.get());
        audioManager = (AudioManager) context.get().getSystemService(Context.AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
        mediaSession = new MediaSessionCompat(context.get(), "tag");
        //TODO mediabutton receive

        connectToRadio();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager = (NotificationManager) context.get().getSystemService(Context.NOTIFICATION_SERVICE);
            CharSequence name = context.get().getResources().getString(R.string.app_name);// The user-visible name of the channel.
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    void updateContext(Context ctx) {
        context = new WeakReference<>(ctx);
        if (!isPaused)
            context.get().registerReceiver(pauseForOutputChange, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    void togglePlayer() {
        if (connection_state != CONNECTION_SUCCESS)
            return;
        if (isPaused) {
            int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                updatePlayerOffset();
                context.get().registerReceiver(pauseForOutputChange, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                setPaused(false);
            }
        } else {
            mediaPlayer.pause();
            player_offset_start = System.currentTimeMillis();
            context.get().unregisterReceiver(pauseForOutputChange);
            setPaused(true);
        }
        makeNotification();
    }

    void releaseContextVars() {
        if (!isPaused) {
            context.get().unregisterReceiver(pauseForOutputChange);
        }
    }

    void finish() {
        releaseContextVars();
        mediaPlayer.release();
        mediaSession.release();
        notificationManager.cancelAll();
    }

    void syncToRadio() {
        temp_switch = mediaPlayer;
        mediaPlayer = new MediaPlayer();
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
        Bitmap pic = BitmapFactory.decodeResource(context.get().getResources(), R.drawable.notif_album_art);

        Intent notifButtonIntent = new Intent(context.get(), NotificationActionReceiver.class).setAction("playpause");
        PendingIntent actionIntent = PendingIntent.getBroadcast(context.get(), 0, notifButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notifClickIntent = new Intent(context.get(), MainActivity.class);
        notifClickIntent.setAction(Intent.ACTION_MAIN);
        notifClickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(context.get(), 0, notifClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context.get(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Lorem Ipsum")
                .setContentText("Lorem ipsum dolor sit amet")
                .setLargeIcon(pic)
                .setOngoing(!isPaused)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .addAction(isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                        "playpause", actionIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        notificationManager.notify(1, mBuilder.build());
    }



    private void setPaused(boolean is_paused) {
        isPaused = is_paused;
        postCallback(new Runnable() {
            @Override
            public void run() {
                ((RadioUpdateCallback) context.get()).onRadioPausePlay(isPaused);
            }
        });
    }

    private void updateConnectionState(int new_state, boolean shouldCallback) {
        connection_state = new_state;
        if(!shouldCallback)
            return;

        //dont want value to change before runnable is run
        final int current_val = connection_state;
        postCallback(new Runnable() {
            @Override
            public void run() {
                ((RadioUpdateCallback) context.get()).onRadioConnectionUpdate(current_val);
            }
        });
    }

    private void postCallback(Runnable r) {
        //run on ui thread
        new Handler(Looper.getMainLooper()).post(r);
    }

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (!isPaused && (focusChange == AudioManager.AUDIOFOCUS_LOSS))
                togglePlayer();
        }
    };

    private void setSynced(boolean isSyncSuccess){
        if(isSyncSuccess) {
            player_offset = 0;  //synced
            player_offset_start = -1;
            postCallback(new Runnable() {
                @Override
                public void run() {
                    ((RadioUpdateCallback) context.get()).onRadioSyncUpdate(true);
                }
            });
        } else {
            postCallback(new Runnable() {
                @Override
                public void run() {
                    ((RadioUpdateCallback) context.get()).onRadioSyncUpdate(false);
                }
            });
        }
    }

    private Runnable connectionRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                updateConnectionState(CONNECTION_TRYING, false);
                URL url = new URL("https://stream.zeno.fm/2ce1nx83g2zuv");
                HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                String myurl = ucon.getHeaderField("Location");
                if (!"https".equals(myurl.substring(0, 5)))
                    myurl = "https" + myurl.substring(4);
                mediaPlayer.setDataSource(myurl);
                mediaPlayer.prepare();

                if (temp_switch != null) {      //sync op
                    temp_switch.release();
                    temp_switch = null;
                    if (isPaused)
                        togglePlayer(); //update notification too
                    else
                        mediaPlayer.start();
                    setSynced(true);
                } else {
                    updateConnectionState(CONNECTION_SUCCESS, true);
                }

            } catch (IOException | NullPointerException e) {
                //nullpointerexception if connection redirect fails from substring call
                Log.i("mylog", "Radio Connection Failed: " + e.getMessage());

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
        }
    };

    void connectToRadio() {
        new Thread(connectionRunnable).start();
    }

//    private MediaButtonReceiver mediaButtonReceiver = new MediaButtonReceiver(){
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Log.i("mylog", "received media click");
//            togglePlayer();
//        }
//    };

    //pause when listening
    private BroadcastReceiver pauseForOutputChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                togglePlayer();
        }
    };

}
