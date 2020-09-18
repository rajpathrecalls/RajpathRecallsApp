package com.example.rajpathrecalls;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity implements RadioUpdateCallback, BottomNavigationView.OnNavigationItemSelectedListener {

    static RadioPlayer radioPlayer;
    Fragment current_fragment;
    boolean isActive;
    private BottomNavigationView navBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navBar = findViewById(R.id.bottom_nav_bar);
        navBar.setOnNavigationItemSelectedListener(this);

        if(savedInstanceState == null) {
            radioPlayer = new RadioPlayer(this);
            radioPlayer.prepare();
            startService(new Intent(getBaseContext(), AppCloseService.class));
            goToFragment(new ListenFragment());
        }
        else {   //config change
            radioPlayer.updateContext(this);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isChangingConfigurations()) {
            radioPlayer.releaseContextVars();
        }
    }

    @Override
    public void onRadioConnectionUpdate(int connection_status) {
        if(connection_status == RadioPlayer.CONNECTION_FAILED){
            showSnackbar("Connection Failed", Snackbar.LENGTH_INDEFINITE,
                    "RETRY", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    radioPlayer.connectToRadio();
                }
            });
        } else if(connection_status == RadioPlayer.CONNECTION_SUCCESS){
            showSnackbar("Connection established",Snackbar.LENGTH_SHORT, null, null);
        }
    }

    @Override
    public void onRadioPausePlay(boolean isPaused) {
        if(current_fragment instanceof ListenFragment){
            ((ListenFragment) current_fragment).onRadioPausePlay(isPaused);
        }
    }

    @Override
    public void onRadioSyncUpdate(boolean isSyncSuccess) {
        if(current_fragment instanceof ListenFragment){
            ((ListenFragment) current_fragment).onRadioSyncUpdate(isSyncSuccess);
        }
        if(!isSyncSuccess){
            showSnackbar("Sync Failed", Snackbar.LENGTH_SHORT, null, null);
        } else {
            showSnackbar("Synced to livestream", Snackbar.LENGTH_SHORT, null, null);
        }
    }

    void showSnackbar(String message, int duration, String action_message, View.OnClickListener action_listener){
        if(!isActive)
            return;
        Snackbar bar = Snackbar.make(navBar, message, duration);
        if(action_message != null)
            bar.setAction(action_message, action_listener);
        bar.setAnchorView(navBar);
        bar.setBackgroundTint(0x80808080);
        bar.setActionTextColor(getResources().getColor(R.color.colorAccent));
        bar.getView().setElevation(0);
        bar.show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selected = null;

        switch(item.getItemId()){
            case R.id.nav_listen:
                selected = new ListenFragment();
                break;
            case R.id.nav_schedule:
                selected = new ScheduleFragment();
                break;
            case R.id.nav_settings:
                selected = new SettingsFragment();
                break;
        }
        goToFragment(selected);
        return true;
    }

    void goToFragment(Fragment fragment){
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                fragment).commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
    }
}