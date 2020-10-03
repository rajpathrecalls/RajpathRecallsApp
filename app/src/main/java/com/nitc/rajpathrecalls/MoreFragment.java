package com.nitc.rajpathrecalls;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MoreFragment extends Fragment {

    TextView timer_text;
    SwitchMaterial sleep_switch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ((MainActivity) getContext()).current_fragment = this;
        View root = inflater.inflate(R.layout.fragment_more, container, false);
        final SharedPreferences sp = getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE);
        SwitchMaterial background_switch = root.findViewById(R.id.background_switch);

        sleep_switch = root.findViewById(R.id.timer_switch);
        timer_text = root.findViewById(R.id.timer_text);

        background_switch.setChecked(sp.getBoolean("background_video_on", true));
        background_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean("background_video_on", isChecked).apply();
            }
        });

        sleep_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                RadioPlayerService service = ((MainActivity) getContext()).radioPlayerService;
                if (isChecked && !service.isSleepTimer()) {
                    showSleepDialog();

                } else if(!isChecked && service.isSleepTimer()){
                    service.stopSleepTimer();
                    timer_text.animate().alpha(0f).setDuration(300);
                }
            }
        });

        root.findViewById(R.id.instagram_button).setOnClickListener(social_listener);
        root.findViewById(R.id.facebook_button).setOnClickListener(social_listener);
        root.findViewById(R.id.github_button).setOnClickListener(social_listener);
        root.findViewById(R.id.mail_button).setOnClickListener(social_listener);

        TextView version_text = root.findViewById(R.id.version_text);
        version_text.setText(BuildConfig.VERSION_NAME);
        return root;
    }

    private View.OnClickListener social_listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String link = null;
            switch (v.getId()) {
                case R.id.instagram_button:
                    link = "https://www.instagram.com/rajpath.recalls_nitc/";
                    break;
                case R.id.facebook_button:
                    link = "https://facebook.com/rajpath.recalls/";
                    break;
                case R.id.github_button:
                    link = "https://github.com/rajpathrecalls/";
                    break;
                case R.id.mail_button:
                    link = "mailto:rajpathrecalls@gmail.com";
                    break;
            }
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            startActivity(launchIntent);
        }
    };

    private void updateTimerText() {
        long end_time = ((MainActivity) getContext()).radioPlayerService.getSleepEndTime();
        int minutes_remaining = (int) ((end_time - System.currentTimeMillis()) / 60000);
        String time_text = getString(R.string.sleep_timer_remaining_time).
                replace("0", "" + minutes_remaining);
        timer_text.setText(time_text);
    }

    private void showSleepDialog(){

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialog_layout = inflater.inflate(R.layout.sleep_dialog, null ,false);
        final TextView title = dialog_layout.findViewById(R.id.dialog_title);
        final Slider slider = dialog_layout.findViewById(R.id.slider);

        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                String text = "Stop playback in " + (int)(value) + " minute" + (value > 2f ? "s":"");
                title.setText(text);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogTheme)
                .setView(dialog_layout)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if(!((MainActivity)getContext()).radioPlayerService.isSleepTimer())
                            sleep_switch.setChecked(false);
                    }
                })
                .show();

        dialog_layout.findViewById(R.id.okay_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getContext()).radioPlayerService.startSleepTimer((int)slider.getValue());
                updateTimerText();
                timer_text.animate().alpha(1f).setDuration(300);
                dialog.cancel();
            }
        });

        dialog_layout.findViewById(R.id.cancel_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }

    void updateViewsOnResume(){
        RadioPlayerService rad = ((MainActivity) getContext()).radioPlayerService;
        if (rad == null)
            return;

        SwitchMaterial sleep_switch = ((ViewGroup)timer_text.getParent()).findViewById(R.id.timer_switch);
        sleep_switch.setChecked(rad.isSleepTimer());
        if (rad.isSleepTimer()) {
            updateTimerText();
            timer_text.setAlpha(1f);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateViewsOnResume();
    }
}
