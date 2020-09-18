package com.example.rajpathrecalls;

public interface RadioUpdateCallback {
    void onRadioConnectionUpdate(int connection_status);
    void onRadioPausePlay(boolean isPaused);
    void onRadioSyncUpdate(boolean isSyncSuccess);
}
