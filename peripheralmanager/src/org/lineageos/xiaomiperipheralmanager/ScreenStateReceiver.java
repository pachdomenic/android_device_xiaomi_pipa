/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiScreenState";
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            logInfo("Screen on event received");
            
            try {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                boolean stylusModeEnabled = preferences.getBoolean(STYLUS_MODE_KEY, false);
                boolean forceRecognize = preferences.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);
                
                // Re-enable pen mode if it should be active
                if (stylusModeEnabled || forceRecognize) {
                    logInfo("Re-enabling pen mode after screen on");
                    PenUtils.enablePenMode();
                } else {
                    logDebug("Pen mode not needed on screen on");
                }
            } catch (Exception e) {
                logError("Error handling screen on: " + e.getMessage());
            }
        }
    }

    // Logging helpers with timestamps
    private void logDebug(String message) {
        Log.d(TAG, getTimestamp() + message);
    }
    
    private void logInfo(String message) {
        Log.i(TAG, getTimestamp() + message);
    }
    
    private void logError(String message) {
        Log.e(TAG, getTimestamp() + message);
    }
    
    private String getTimestamp() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
