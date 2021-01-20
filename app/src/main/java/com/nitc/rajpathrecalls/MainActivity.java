package com.nitc.rajpathrecalls;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    Fragment current_fragment;
    RadioPlayerService radioPlayerService;
    private BottomNavigationView navBar;
    private boolean isConnectingSnackbarActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        navBar = findViewById(R.id.bottom_nav_bar);
        navBar.setOnNavigationItemSelectedListener(this);

        Intent playerIntent = new Intent(this, RadioPlayerService.class);
        if (savedInstanceState == null) {
            startService(playerIntent);     //will only create service if not running.
            // so even if activity is closed and reopened, service is not recreated.
            // only onStartCommand() will be called
            goToFragment(new ListenFragment());
        }
        bindService(playerIntent, serviceConnection, BIND_AUTO_CREATE);

        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference().child("ActiveUsers").push();
        Map<String, String> new_child = new HashMap<>();
        new_child.put("platform", "app v" + BuildConfig.VERSION_NAME);
        databaseRef.setValue(new_child);
        databaseRef.onDisconnect().removeValue();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            RadioPlayerService.LocalBinder binder = (RadioPlayerService.LocalBinder) service;
            radioPlayerService = binder.getService();
            if (current_fragment instanceof ListenFragment)
                ((ListenFragment) current_fragment).updateViewsOnResume();
            else if (current_fragment instanceof MoreFragment)
                ((MoreFragment) current_fragment).updateViewsOnResume();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private final BroadcastReceiver connectionUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            RadioPlayerService.ConnectionStatus connection_status =
                    (RadioPlayerService.ConnectionStatus) intent.getSerializableExtra(RadioPlayerService.CONNECTION_BROADCAST);

            if (connection_status == RadioPlayerService.ConnectionStatus.FAILED) {
                showSnackbar(getString(R.string.connection_failed_text), Snackbar.LENGTH_INDEFINITE,
                        getString(R.string.retry_text), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                radioPlayerService.connectToRadio();
                            }
                        });

            } else if (connection_status == RadioPlayerService.ConnectionStatus.SUCCESS) {
                showConnectedSnackbar();
            }
        }
    };

    private final BroadcastReceiver syncUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int sync_state = intent.getIntExtra(RadioPlayerService.SYNC_BROADCAST, -1);

            if (current_fragment instanceof ListenFragment) {
                ((ListenFragment) current_fragment).onSyncUpdate(sync_state);
            }

            if (sync_state == 2) {
                showSnackbar(getString(R.string.synced_confirmation_msg), Snackbar.LENGTH_SHORT, null, null);
            }
        }
    };

    void showConnectingSnackbar() {
        isConnectingSnackbarActive = true;
        showSnackbar(getString(R.string.connecting_text), Snackbar.LENGTH_INDEFINITE, null, null);
    }

    private void showConnectedSnackbar() {
        isConnectingSnackbarActive = false;
        showSnackbar(getString(R.string.connection_established_text), Snackbar.LENGTH_SHORT, null, null);
    }

    private void showSnackbar(String message, int duration, String action_message, View.OnClickListener action_listener) {
        Snackbar bar = Snackbar.make(navBar, message, duration);
        if (action_message != null)
            bar.setAction(action_message, action_listener);
        bar.setAnchorView(navBar);
        bar.setBackgroundTint(0x80808080);
        bar.setActionTextColor(getResources().getColor(R.color.colorAccent));
        bar.getView().setElevation(0);
        bar.show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selected;

        int itemId = item.getItemId();
        if (itemId == R.id.nav_chat) {
            selected = new ChatFragment();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else if (itemId == R.id.nav_more) {
            selected = new MoreFragment();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {        //default
            selected = new ListenFragment();
        }

        if (!selected.getClass().equals(current_fragment.getClass())) {
            goToFragment(selected);
        }
        return true;
    }

    void goToFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (current_fragment instanceof ListenFragment)
            super.onBackPressed();
        else
            navBar.setSelectedItemId(R.id.nav_listen);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionUpdateReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncUpdateReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionUpdateReceiver,
                new IntentFilter(RadioPlayerService.CONNECTION_BROADCAST));
        LocalBroadcastManager.getInstance(this).registerReceiver(syncUpdateReceiver,
                new IntentFilter(RadioPlayerService.SYNC_BROADCAST));

        if (isConnectingSnackbarActive && radioPlayerService.getConnectionState() == RadioPlayerService.ConnectionStatus.SUCCESS) {
            showConnectedSnackbar();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

}