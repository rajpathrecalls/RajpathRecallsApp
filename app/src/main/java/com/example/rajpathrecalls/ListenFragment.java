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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

public class ListenFragment extends Fragment {

    ImageView play_pause_btn;
    TextView offset_text;
    Button sync_button;
    ProgressBar sync_progress;

    MediaPlayer bg_player = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View fragmentView = inflater.inflate(R.layout.fragment_listen, container, false);
        ((MainActivity)getContext()).current_fragment = this;

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
            int connection_state = getRadioPlayer().getConnectionState();

            if (connection_state == RadioPlayerService.CONNECTION_SUCCESS) {
                getRadioPlayer().togglePlayer(!getRadioPlayer().isPaused());

            } else if (connection_state == RadioPlayerService.CONNECTION_TRYING) {
                ((MainActivity)getContext()).showSnackbar(getString(R.string.connecting_text), Snackbar.LENGTH_SHORT, null, null);

            }// else if(connection_state == RadioPlayerService.CONNECTION_FAILED) {
               // ((MainActivity)getContext()).onRadioConnectionUpdate(RadioPlayerService.CONNECTION_FAILED);    //show snackbar again
            //}
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

        if(sync_state == 0){    //sync button
            int offset_in_sec = getRadioPlayer().getPlayerOffset();
            int elapsed_min = offset_in_sec / 60, elapsed_sec = offset_in_sec % 60;

            String elapsed_time;
            if(elapsed_min == 0) {
                elapsed_time = getString(R.string.elapsed_seconds).replace("0", ""+elapsed_sec);
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

        } else if(sync_state == 2){     //sync operation completed
            offset_text.setVisibility(View.GONE);
            sync_button.setVisibility(View.GONE);
            sync_progress.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(playPauseUpdateReceiver);
    }

    //reload views on resume
    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(playPauseUpdateReceiver,
                new IntentFilter(RadioPlayerService.PLAY_PAUSE_BROADCAST));

        if(getRadioPlayer() != null) {
            //update any view changes that might have happened while fragment paused
            updateViewsOnResume();
        }
    }

    void updateViewsOnResume(){
        updatePlayPauseView(getRadioPlayer().isPaused());
        if (getRadioPlayer().getPlayerOffset() != 0)
            updateSyncViews(0);  //show sync views
        else
            updateSyncViews(2);  //disable sync views
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bg_player != null) {
            bg_player.release();
            bg_player = null;
        }
    }

    private RadioPlayerService getRadioPlayer(){
        return ((MainActivity)getContext()).radioPlayerService;
    }
}
