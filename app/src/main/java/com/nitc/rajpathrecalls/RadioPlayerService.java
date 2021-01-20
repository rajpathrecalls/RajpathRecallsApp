package com.nitc.rajpathrecalls;

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
import android.view.KeyEvent;

import androidx.annotation.NonNull;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class RadioPlayerService extends Service implements AudioManager.OnAudioFocusChangeListener, SimpleExoPlayer.EventListener {

    private boolean isPaused = true, listenForPlayerReady = false, isPrepared = false, pendingSync = false;
    private String now_playing_song = "", now_playing_artist = "", mediaLink, infoLink;
    private long player_offset = 0, player_offset_start = -1;
    private SimpleExoPlayer mediaPlayer, temp_switch = null;
    private int connection_state = CONNECTION_FAILED;
    private MediaSessionCompat mediaSession;
    private Timer scraper_timer;
    private Handler sleep_handler;
    private long sleep_end_time;

    private final String CHANNEL_ID = "com.rajpathrecalls.notifications",
            PLAY_PAUSE_ACTION = "com.rajpathrecalls.playpause",
            SYNC_ACTION = "com.rajpathrecalls.sync";        //public broadcasts action, so need unique

    static final int CONNECTION_SUCCESS = 1, CONNECTION_TRYING = 2, CONNECTION_FAILED = 3;
    static final String PLAY_PAUSE_BROADCAST = "playpause", CONNECTION_BROADCAST = "connection_update",
            SYNC_BROADCAST = "sync_update", NOW_PLAYING_BROADCAST = "now_playing";     //local broadcast actions

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

        mediaSession = new MediaSessionCompat(this, CHANNEL_ID);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    togglePlayer(!isPaused);
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            CharSequence name = getResources().getString(R.string.app_name);    // The user-visible name of the channel.
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(mChannel);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(PLAY_PAUSE_ACTION);
        filter.addAction(SYNC_ACTION);
        registerReceiver(notifActionReceiver, filter);
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
                if (pendingSync) {
                    pendingSync = false;
                    syncToRadio();
                }
            }

        } else {
            mediaPlayer.pause();
            player_offset_start = System.currentTimeMillis();
            unregisterReceiver(pauseForOutputChange);
            if (scraper_timer != null) {
                scraper_timer.cancel();
                scraper_timer = null;
            }
            setPaused(true);
        }
        makeNotification(true);
    }

    void syncToRadio() {
        temp_switch = mediaPlayer;
        mediaPlayer = new SimpleExoPlayer.Builder(this).build();
        connectToRadio();
        setSyncState(1);
        updateNowPlaying("Syncing…", "");
    }

    void startSleepTimer(int minutes_to_sleep) {
        sleep_handler = new Handler();

        sleep_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                togglePlayer(true);     //will remove from foreground
                stopSleepTimer();
            }
        }, minutes_to_sleep * 60 * 1000);

        sleep_end_time = System.currentTimeMillis() + minutes_to_sleep * 60 * 1000;
    }

    void stopSleepTimer() {
        if (sleep_handler != null) {
            sleep_handler.removeCallbacksAndMessages(null);
            sleep_handler = null;
        }
    }

    int getConnectionState() {
        return connection_state;
    }

    boolean isPaused() {
        return isPaused;
    }

    boolean isSleepTimer() {
        return sleep_handler != null;
    }

    int getPlayerOffset() {
        return (int) player_offset;
    }

    long getSleepEndTime() {
        return sleep_end_time;
    }

    String[] getNowPlaying() {
        return new String[]{now_playing_song, now_playing_artist};
    }

    private boolean isSyncing() {
        return temp_switch != null;
    }

    private boolean isOutOfSync() {
        return player_offset_start != -1;
    }

    private void updatePlayerOffset() {
        if (isOutOfSync()) {
            long calculated_offset = (System.currentTimeMillis() - player_offset_start) / 1000;
            if (calculated_offset == 0)
                calculated_offset = 1;      //quick pause play taps give one second offset
            player_offset += calculated_offset;
            setSyncState(0);    //out of sync
        }
    }

    private void makeNotification(boolean playPauseChanged) {
        final int NOTIFICATION_ID = 6;
        Bitmap pic = BitmapFactory.decodeResource(getResources(), R.drawable.notif_album_art);

        PendingIntent playPauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(PLAY_PAUSE_ACTION), PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notifClickIntent = new Intent(this, MainActivity.class); //explicit intent
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notifClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(now_playing_song)
                .setContentText(now_playing_artist)
                .setLargeIcon(pic)
                .setOngoing(!isPaused)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .addAction(isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                        PLAY_PAUSE_ACTION, playPauseIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (getPlayerOffset() != 0) {
            PendingIntent syncIntent = PendingIntent.getBroadcast(this, 0, new Intent(SYNC_ACTION), PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.addAction(R.drawable.ic_sync, SYNC_ACTION, syncIntent);
        }

        if (playPauseChanged) {
            if (isPaused) {
                //update notification and set as not foreground
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
                stopForeground(false);
            } else {
                startForeground(NOTIFICATION_ID, mBuilder.build());
            }
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
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

    private void setSyncState(int sync_state) {
        if (sync_state == 2) {    // sync success
            player_offset = 0;
            player_offset_start = -1;
        }

        Intent intent = new Intent(SYNC_BROADCAST);
        intent.putExtra(SYNC_BROADCAST, sync_state);
        sendLocalBroadcast(intent);
    }

    void beginRadio() {
        FirebaseDatabase.getInstance().getReference().child("Links").child("mediaLink").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mediaLink = (String) snapshot.getValue();

                if (isPrepared) {
                    if (!isSyncing() && !isPaused)
                        syncToRadio();
                    else
                        pendingSync = true;

                } else {
                    connectToRadio();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        FirebaseDatabase.getInstance().getReference().child("Links").child("infoLink").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                infoLink = (String) snapshot.getValue();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
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
                            new URL(mediaLink).openConnection();
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
                listenForPlayerReady = true;
                mediaPlayer.prepare();
            }
        });
    }

    //exoplayer event listener
    @Override
    public void onPlaybackStateChanged(int state) {

        if (listenForPlayerReady && state == SimpleExoPlayer.STATE_READY) {
            listenForPlayerReady = false;
            if (scraper_timer == null) {
                scraper_timer = new Timer();
                scraper_timer.scheduleAtFixedRate(createScraperTask(), 0, 30000);  //call every 30 seconds
            }

            if (isSyncing()) {
                temp_switch.release();
                temp_switch = null;

                updateConnectionState(CONNECTION_SUCCESS, false);
                setSyncState(2);
                updateNowPlaying("", "");

                if (isPaused)
                    togglePlayer(false); //need to update isPaused
                else
                    mediaPlayer.play();        //no need to update

                if (pendingSync) {        //link update in the middle of sync will be pending
                    pendingSync = false;
                    syncToRadio();
                }

            } else {
                updateConnectionState(CONNECTION_SUCCESS, true);
                if (!isPrepared) {
                    togglePlayer(false);
                    isPrepared = true;
                }
            }
        }
    }

    //exoplayer event listener
    @Override
    public void onPlayerError(ExoPlaybackException error) {
//        Log.i("mylog", "Radio Connection Failed: " + error.getMessage());

        if (isSyncing()) {      //sync failed. go back to old media player
            mediaPlayer.release();
            mediaPlayer = temp_switch;
            temp_switch = null;
            updateConnectionState(CONNECTION_SUCCESS, false); //old player is connected
            setSyncState(0);
        } else {
            updateConnectionState(CONNECTION_FAILED, true);
        }
    }

    //pause when listening
    private final BroadcastReceiver pauseForOutputChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                togglePlayer(true);
        }
    };

    //notification action receiver
    private final BroadcastReceiver notifActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PLAY_PAUSE_ACTION.equals(intent.getAction())) {
                togglePlayer(!isPaused);
            } else if (SYNC_ACTION.equals(intent.getAction()) && !isSyncing()) {
                syncToRadio();
            }
        }
    };

    private TimerTask createScraperTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    Document document = Jsoup.connect(infoLink).get();
                    Elements song_element = document.getElementsByClass("radio-song "),
                            artist_element = document.getElementsByClass("radio-artist ");

                    String song_name = song_element.size() > 0 ? song_element.get(0).text() : "",
                            artist_name = artist_element.size() > 0 ? artist_element.get(0).text() : "";


                    //pausing while the task is running will cause the notification to show up even if paused and discarded
                    //so an out of sync check is added
                    if (!isOutOfSync() && !(song_name.equals(now_playing_song) && artist_name.equals(now_playing_artist))) {
                        updateNowPlaying(song_name, artist_name);
                    }
                } catch (IOException ignored) {
                }
            }
        };
    }

    private void updateNowPlaying(@NonNull final String song, @NonNull final String artist) {
        now_playing_song = song;
        now_playing_artist = artist;
        makeNotification(false);
        Intent intent = new Intent(NOW_PLAYING_BROADCAST);
        intent.putExtra("song", song);
        intent.putExtra("artist", artist);
        sendLocalBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        if (!isPaused) {
            unregisterReceiver(pauseForOutputChange);
        }

        stopSleepTimer();
        ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(this);
        unregisterReceiver(notifActionReceiver);
        if (isSyncing())
            temp_switch.release();
        mediaPlayer.release();
        mediaSession.release();

        if (scraper_timer != null)
            scraper_timer.cancel();

        super.onDestroy();
    }
}
