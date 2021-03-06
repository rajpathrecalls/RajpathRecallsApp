package com.nitc.rajpathrecalls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class ListenFragment extends Fragment {

    private ImageView playPauseBtn;
    private TextView offsetText, nowPlayingTitle;
    private Button syncButton, liveFeedButton;
    private ProgressBar syncProgress;
    private TextSwitcher nowSongView;
    private boolean nowPlayingStarted = false, liveFeedActive = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View fragmentView = inflater.inflate(R.layout.fragment_listen, container, false);
        ((MainActivity) getContext()).current_fragment = this;

        float dimension;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
            dimension = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        else
            dimension = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;

        if (dimension < 500)
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        configureBackground(fragmentView);

        nowPlayingTitle = fragmentView.findViewById(R.id.now_playing_title);
        nowSongView = fragmentView.findViewById(R.id.song_name_view);
        nowSongView.setInAnimation(getContext(), android.R.anim.slide_in_left);
        nowSongView.setOutAnimation(getContext(), android.R.anim.slide_out_right);

        playPauseBtn = fragmentView.findViewById(R.id.play_pause_view);
        offsetText = fragmentView.findViewById(R.id.offset_text);
        syncButton = fragmentView.findViewById(R.id.sync_button);
        syncProgress = fragmentView.findViewById(R.id.sync_progress);
        liveFeedButton = fragmentView.findViewById(R.id.live_feed_button);

        playPauseBtn.setOnClickListener(onPlayPauseClick);
        syncButton.setOnClickListener(onSyncClick);

        EventList e = new EventList((LinearLayout) fragmentView.findViewById(R.id.event_list));
        e.populate();

        FirebaseDatabase.getInstance().getReference().child("LiveFeed").addListenerForSingleValueEvent(feedListener);

        return fragmentView;
    }

    void configureBackground(View root) {
        final VideoView backgroundVideo = root.findViewById(R.id.videoView);
        boolean background_video_on = getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE).
                getBoolean("background_video_on", true);

        if (background_video_on) {
            backgroundVideo.setVideoURI(Uri.parse("android.resource://" + getContext().getPackageName()
                    + "/" + R.raw.bg_video));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                backgroundVideo.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
            }
            backgroundVideo.start();
            backgroundVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                    mp.setVolume(0, 0);
                    float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
                    float screenRatio = backgroundVideo.getWidth() / (float) backgroundVideo.getHeight();
                    float scaleX = videoRatio / screenRatio;
                    if (scaleX >= 1f) {
                        backgroundVideo.setScaleX(scaleX);
                    } else {
                        backgroundVideo.setScaleY(1f / scaleX);
                    }
                    mp.start();
                }
            });

            backgroundVideo.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    backgroundVideo.setVisibility(View.GONE);
                    getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().
                            putBoolean("background_video_on", false).apply();
                    Toast.makeText(getContext(), R.string.bg_video_error_toast, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

        } else {
            backgroundVideo.setVisibility(View.GONE);
            ImageView background = new ImageView(getContext());
            int image_choice = new Random().nextInt(3), imageResId;
            if (image_choice == 0)
                imageResId = R.drawable.listen_fragment_bg_1;
            else if (image_choice == 1)
                imageResId = R.drawable.listen_fragment_bg_2;
            else
                imageResId = R.drawable.listen_fragment_bg_3;

            background.setImageResource(imageResId);
            background.setZ(-1);        //send to back of everything
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            background.setLayoutParams(backgroundVideo.getLayoutParams());
            ((ConstraintLayout) root.findViewById(R.id.listen_root)).addView(background);
        }
    }

    private final ValueEventListener feedListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (snapshot.exists()) {
                liveFeedActive = true;
                final String[] pages = new String[(int) snapshot.getChildrenCount()];
                int i = 0;

                if (syncButton.getVisibility() == View.GONE)
                    liveFeedButton.setVisibility(View.VISIBLE);

                for (DataSnapshot element : snapshot.getChildren()) {
                    pages[i] = element.child("image").getValue().toString();
                    Glide.with(getActivity()).load(pages[i]).preload();
                    ++i;
                }
                liveFeedButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), FeedActivity.class);
                        intent.putExtra("pages", pages);
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
        }
    };

    private final View.OnClickListener onPlayPauseClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (getRadioPlayer().getConnectionState() == RadioPlayerService.ConnectionStatus.SUCCESS) {
                getRadioPlayer().togglePlayer(!getRadioPlayer().isPaused());

            } else if (getRadioPlayer().getConnectionState() == RadioPlayerService.ConnectionStatus.FAILED) {
                getRadioPlayer().beginRadio();
                ((MainActivity) getContext()).showConnectingSnackbar();
            }
        }
    };

    private final View.OnClickListener onSyncClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            getRadioPlayer().syncToRadio();
        }
    };

    private final BroadcastReceiver playPauseUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPaused = intent.getBooleanExtra(RadioPlayerService.PLAY_PAUSE_BROADCAST, true);
            updatePlayPauseViewWithAnim(isPaused);
        }
    };

    private final BroadcastReceiver nowPlayingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String song_name = intent.getStringExtra("song"),
                    artist_name = intent.getStringExtra("artist");

            if (!"".equals(song_name) || !"".equals(artist_name)) {
                if (nowPlayingStarted)
                    updateNowPlayingViews(song_name, artist_name);
                else if (!song_name.contains("Syncing"))
                    startNowPlayingViews(song_name, artist_name);
            }
        }
    };

    //called from activity
    void onSyncUpdate(int sync_state) {
        updateSyncViews(sync_state);
    }

    private void updatePlayPauseViewWithAnim(boolean isPaused) {

        Drawable avd = ResourcesCompat.getDrawable(getResources(), isPaused ? R.drawable.avd_pause_to_play : R.drawable.avd_play_to_pause, null);
        playPauseBtn.setImageDrawable(avd);

        if (avd instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) avd).start();
        } else if (avd instanceof AnimatedVectorDrawableCompat) {
            ((AnimatedVectorDrawableCompat) avd).start();
        }

    }

    private void updateSyncViews(int sync_state) {
        TransitionManager.beginDelayedTransition((ConstraintLayout) syncButton.getParent());

        if (sync_state == 0) {    //sync button
            int offset_in_sec = getRadioPlayer().getPlayerOffset();
            int elapsed_min = offset_in_sec / 60, elapsed_sec = offset_in_sec % 60;

            String elapsed_time;
            if (elapsed_min == 0) {
                elapsed_time = getResources().getQuantityString(R.plurals.elapsed_seconds, elapsed_sec, elapsed_sec);
            } else {
                elapsed_time = getString(R.string.elapsed_minutes, elapsed_min, elapsed_sec);
            }

            liveFeedButton.setVisibility(View.GONE);
            offsetText.setText(elapsed_time);
            offsetText.setVisibility(View.VISIBLE);
            syncButton.setEnabled(true);
            syncButton.setText(R.string.sync_text);
            syncButton.setVisibility(View.VISIBLE);
            syncProgress.setVisibility(View.GONE);
            nowPlayingTitle.setEnabled(true);     //show exclamation

        } else if (sync_state == 1) {      //sync progress
            offsetText.setVisibility(View.GONE);
            syncButton.setVisibility(View.VISIBLE);
            syncButton.setText(R.string.syncing_text);
            syncButton.setEnabled(false);
            syncProgress.setVisibility(View.VISIBLE);

        } else if (sync_state == 2) {     //sync operation completed
            offsetText.setVisibility(View.GONE);
            syncButton.setVisibility(View.GONE);
            syncProgress.setVisibility(View.GONE);
            if (liveFeedActive)
                liveFeedButton.setVisibility(View.VISIBLE);
            nowPlayingTitle.setEnabled(false);
        }
    }

    private void startNowPlayingViews(String song, String artist) {
        final int animation_duration = 450;
        nowSongView.setCurrentText(song + " - " + artist);
        nowSongView.setSelected(true);    //to start marquee

        nowSongView.setX(-nowSongView.getWidth());
        nowPlayingTitle.setX(-nowPlayingTitle.getWidth());

        nowPlayingTitle.animate().alpha(1f).translationX(0).setDuration(animation_duration);
        nowSongView.animate().alpha(1f).translationX(0).setStartDelay(animation_duration).
                setDuration(animation_duration);
        nowPlayingStarted = true;
    }

    private void updateNowPlayingViews(String song, String artist) {
        nowSongView.setText(song + " - " + artist);
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

        if (getRadioPlayer() != null) {     //if fragment loads before service is bound
            //update any view changes that might have happened while fragment paused
            updateViewsOnResume();
        }
    }

    //also called after service is bound
    void updateViewsOnResume() {
        RadioPlayerService player = getRadioPlayer();

        playPauseBtn.setImageResource(player.isPaused() ? R.drawable.ic_play : R.drawable.ic_pause);

        if (player.getPlayerOffset() != 0) {
            if (player.getConnectionState() == RadioPlayerService.ConnectionStatus.SUCCESS)
                updateSyncViews(0);     //show sync views
            else if (player.getConnectionState() == RadioPlayerService.ConnectionStatus.TRYING)
                updateSyncViews(1);     //show syncing progress
        } else {
            updateSyncViews(2);  //disable sync views
        }

        //on closing and reopening fragment
        if (nowPlayingStarted)
            updateNowPlayingViews(player.getNowPlaying()[0], player.getNowPlaying()[1]);
        else if (!"".equals(getRadioPlayer().getNowPlaying()[0]) || !"".equals(getRadioPlayer().getNowPlaying()[1]))
            startNowPlayingViews(player.getNowPlaying()[0], player.getNowPlaying()[1]);
    }

    private RadioPlayerService getRadioPlayer() {
        return ((MainActivity) getContext()).radioPlayerService;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FirebaseDatabase.getInstance().getReference().child("LiveFeed").removeEventListener(feedListener);
    }
}
