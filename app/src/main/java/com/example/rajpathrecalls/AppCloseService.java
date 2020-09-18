package com.example.rajpathrecalls;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AppCloseService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Handle application closing
        MainActivity.radioPlayer.finish();
        MainActivity.radioPlayer = null;        //for garbage collection ig. idk can't hurt
        // Destroy the service
        stopSelf();
    }
}