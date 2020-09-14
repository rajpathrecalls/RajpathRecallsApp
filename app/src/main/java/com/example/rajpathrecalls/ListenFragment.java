package com.example.rajpathrecalls;

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

import com.google.android.material.snackbar.Snackbar;

public class ListenFragment extends Fragment {

    ImageView play_pause_btn;
    TextView offset_text;
    Button sync_button;
    ProgressBar sync_progress;
    MediaPlayer bg_player = null;
    boolean isSyncing = false, isFragmentActive;

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
                return true;
            }
        });

        play_pause_btn = fragmentView.findViewById(R.id.play_pause_view);
        offset_text = fragmentView.findViewById(R.id.offset_text);
        sync_button = fragmentView.findViewById(R.id.sync_button);
        sync_progress = fragmentView.findViewById(R.id.sync_progress);

        play_pause_btn.setOnClickListener(pauseListener);
        sync_button.setOnClickListener(syncListener);
        return fragmentView;
    }

    private View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.i("mylog", "clicked");
            int connection_state = MainActivity.radioPlayer.getConnectionState();
            if (connection_state == RadioPlayer.CONNECTION_SUCCESS) {
                MainActivity.radioPlayer.togglePlayer();
            } else if (connection_state == RadioPlayer.CONNECTION_TRYING) {
                ((MainActivity)getContext()).showSnackbar("Establishing connection...", Snackbar.LENGTH_SHORT, null, null);
            }
        }
    };

    void updatePlayPauseIcon() {
        play_pause_btn.setImageResource(MainActivity.radioPlayer.isPaused() ? R.drawable.ic_play : R.drawable.ic_pause);
    }

    private View.OnClickListener syncListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MainActivity.radioPlayer.syncToRadio();
            isSyncing = true;
            handleSyncViews();
        }
    };

    //callback
    void onRadioConnectionUpdate(int connection_status) {
        if (connection_status == RadioPlayer.CONNECTION_SUCCESS) {
            if (isSyncing) {
                if(MainActivity.radioPlayer.isPaused())
                    MainActivity.radioPlayer.togglePlayer();
                isSyncing = false;
                handleSyncViews();
            }
        }
    }

    //callback
    void onRadioPausePlay(boolean isPaused){
        Log.i("mylog", "called");
        updatePlayPauseIcon();
        if (!isPaused && MainActivity.radioPlayer.getPlayerOffset() != 0) {
            showSyncViews(MainActivity.radioPlayer.getPlayerOffset());
        }
    }

    void showSyncViews(int offset_in_sec) {
        int elapsed_min = offset_in_sec / 60, elapsed_sec = offset_in_sec % 60;

        String elapsed_time;
        if(elapsed_min == 0){
            elapsed_time = elapsed_sec + " seconds";
        } else {
            elapsed_time = elapsed_min + ":" + (elapsed_sec < 10 ? "0" : "") + elapsed_sec +
                    " minutes";
        }
        elapsed_time +=  " behind livestream";
        TransitionManager.beginDelayedTransition((ConstraintLayout)offset_text.getParent());
        offset_text.setText(elapsed_time);
        offset_text.setVisibility(View.VISIBLE);
        sync_button.setEnabled(true);
        sync_button.setText("SYNC");
        sync_button.setVisibility(View.VISIBLE);
    }

    void handleSyncViews() {
        TransitionManager.beginDelayedTransition((ConstraintLayout) sync_button.getParent());
        if (isSyncing) {
            offset_text.setVisibility(View.GONE);
            sync_button.setText("SYNCING");
            sync_button.setEnabled(false);
            sync_progress.setVisibility(View.VISIBLE);
        } else {
            sync_button.setVisibility(View.GONE);
            sync_progress.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isFragmentActive = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        isFragmentActive = true;
        updatePlayPauseIcon();
        int current_offset = MainActivity.radioPlayer.getPlayerOffset();
        if(current_offset != 0)
            showSyncViews(current_offset);
        else
            handleSyncViews();  //disable sync views
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bg_player != null) {
            bg_player.release();
            bg_player = null;
        }
    }
}
