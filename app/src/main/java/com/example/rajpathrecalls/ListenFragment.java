package com.example.rajpathrecalls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

public class ListenFragment extends Fragment {

    private ImageView play_pause_btn;
    private TextView offset_text;
    private Button sync_button;
    private ProgressBar sync_progress;
    private TextSwitcher now_song_view, now_artist_view;
    private boolean nowPlayingActive = false;

    private MediaPlayer bg_player = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View fragmentView = inflater.inflate(R.layout.fragment_listen, container, false);
        ((MainActivity) getContext()).current_fragment = this;

        final VideoView background_video = fragmentView.findViewById(R.id.videoView);
        background_video.setVideoURI(Uri.parse("android.resource://" + getContext().getPackageName()
                + "/" + R.raw.bg_video));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            background_video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        }
        background_video.start();
        background_video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                bg_player = mp;
                bg_player.setLooping(true);
                bg_player.setVolume(0, 0);
                bg_player.start();
            }
        });

        background_video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                background_video.setVisibility(View.GONE);
                bg_player = null;
                return true;
            }
        });

        now_song_view = fragmentView.findViewById(R.id.song_name_view);
        now_song_view.setInAnimation(getContext(), android.R.anim.slide_in_left);
        now_song_view.setOutAnimation(getContext(), android.R.anim.slide_out_right);

        now_artist_view = fragmentView.findViewById(R.id.artist_name_view);
        now_artist_view.setInAnimation(getContext(), android.R.anim.slide_in_left);
        now_artist_view.setOutAnimation(getContext(), android.R.anim.slide_out_right);

        play_pause_btn = fragmentView.findViewById(R.id.play_pause_view);
        offset_text = fragmentView.findViewById(R.id.offset_text);
        sync_button = fragmentView.findViewById(R.id.sync_button);
        sync_progress = fragmentView.findViewById(R.id.sync_progress);

        play_pause_btn.setOnClickListener(onPlayPauseClick);
        sync_button.setOnClickListener(onSyncClick);

        updatePlayPauseView(true);

        return fragmentView;
    }

    private View.OnClickListener onPlayPauseClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!getRadioPlayer().isPrepared()) {
                getRadioPlayer().connectToRadio();
                ((MainActivity) getContext()).showSnackbar(getString(R.string.connecting_text),
                        Snackbar.LENGTH_INDEFINITE, null, null);
            }

            if (getRadioPlayer().getConnectionState() == RadioPlayerService.CONNECTION_SUCCESS) {
                getRadioPlayer().togglePlayer(!getRadioPlayer().isPaused());

            }
        }
    };

    private View.OnClickListener onSyncClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            getRadioPlayer().syncToRadio();
            updateSyncViews(1);
        }
    };

    private BroadcastReceiver playPauseUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPaused = intent.getBooleanExtra(RadioPlayerService.PLAY_PAUSE_BROADCAST, true);

            updatePlayPauseView(getRadioPlayer().isPaused());
            if (!isPaused && getRadioPlayer().getPlayerOffset() != 0) {
                updateSyncViews(0);
            }
        }
    };

    private BroadcastReceiver nowPlayingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String song_name = intent.getStringExtra("song"),
                    artist_name = intent.getStringExtra("artist");

            if (song_name != null && artist_name != null) {
                if (nowPlayingActive) {
                    updateNowPlayingViews(song_name, artist_name);
                } else {
                    startNowPlayingViews(song_name, artist_name);
                    nowPlayingActive = true;
                }
            }
        }
    };

    //called from activity
    void onSyncUpdate(boolean isSyncSuccess) {
        updateSyncViews(isSyncSuccess ? 2 : 0);
    }


    private void updatePlayPauseView(boolean isPaused) {
        //TODO animate view change
        play_pause_btn.setImageResource(isPaused ? R.drawable.ic_play : R.drawable.ic_pause);
    }

    private void updateSyncViews(int sync_state) {
        TransitionManager.beginDelayedTransition((ConstraintLayout) sync_button.getParent());

        if (sync_state == 0) {    //sync button
            int offset_in_sec = getRadioPlayer().getPlayerOffset();
            int elapsed_min = offset_in_sec / 60, elapsed_sec = offset_in_sec % 60;

            String elapsed_time;
            if (elapsed_min == 0) {
                elapsed_time = getString(R.string.elapsed_seconds).replace("0", "" + elapsed_sec);
            } else {
                String time = elapsed_min + ":" + (elapsed_sec < 10 ? "0" : "") + elapsed_sec;
                elapsed_time = getString(R.string.elapsed_minutes).replace("0:00", time);
            }

            offset_text.setText(elapsed_time);
            offset_text.setVisibility(View.VISIBLE);
            sync_button.setEnabled(true);
            sync_button.setText(R.string.sync_text);
            sync_button.setVisibility(View.VISIBLE);
            sync_progress.setVisibility(View.GONE);

        } else if (sync_state == 1) {      //sync progress
            offset_text.setVisibility(View.GONE);
            sync_button.setText(R.string.syncing_text);
            sync_button.setEnabled(false);
            sync_progress.setVisibility(View.VISIBLE);

        } else if (sync_state == 2) {     //sync operation completed
            offset_text.setVisibility(View.GONE);
            sync_button.setVisibility(View.GONE);
            sync_progress.setVisibility(View.GONE);
        }
    }

    private void startNowPlayingViews(String song, String artist) {
        final int animation_duration = 450;
        now_song_view.setCurrentText(song);
        now_artist_view.setCurrentText(artist);
        TextView now_title = ((ViewGroup)now_song_view.getParent()).findViewById(R.id.now_playing_title);
        now_song_view.setX(-now_song_view.getWidth());
        now_artist_view.setX(-now_artist_view.getWidth());
        now_title.setX(-now_title.getWidth());

        now_title.animate().alpha(1f).translationX(0).setDuration(animation_duration);
        now_song_view.animate().alpha(1f).translationX(0).setStartDelay(animation_duration).
                setDuration(animation_duration);
        now_artist_view.animate().alpha(1f).translationX(0).setStartDelay(animation_duration + animation_duration).
                setDuration(animation_duration);
    }

    private void updateNowPlayingViews(String song, String artist) {
        now_song_view.setText(song);
        now_artist_view.setText(artist);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getContext());
        manager.unregisterReceiver(playPauseUpdateReceiver);
        manager.unregisterReceiver(nowPlayingUpdateReceiver);
    }

    //reload views on resume
    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getContext());
        manager.registerReceiver(playPauseUpdateReceiver, new IntentFilter(RadioPlayerService.PLAY_PAUSE_BROADCAST));
        manager.registerReceiver(nowPlayingUpdateReceiver, new IntentFilter(RadioPlayerService.NOW_PLAYING_BROADCAST));

        if (getRadioPlayer() != null) {
            //update any view changes that might have happened while fragment paused
            updateViewsOnResume();
        }
    }

    //called after service is bound
    void updateViewsOnResume() {
        RadioPlayerService player = getRadioPlayer();
        updatePlayPauseView(player.isPaused());
        if (player.getPlayerOffset() != 0)
            updateSyncViews(0);  //show sync views
        else
            updateSyncViews(2);  //disable sync views

        updateNowPlayingViews(player.getNowPlaying()[0], player.getNowPlaying()[1]);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bg_player != null) {
            bg_player.release();
            bg_player = null;
        }
    }

    private RadioPlayerService getRadioPlayer() {
        return ((MainActivity) getContext()).radioPlayerService;
    }
}
