/*
 * Copyright (C) 2023-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles SCREEN_ON to re-apply pen mode after the display turns back on.
 */
public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiScreenState";

    private static final String STYLUS_MODE_KEY           = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;

        logInfo("Screen on received");

        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean stylusModeEnabled = prefs.getBoolean(STYLUS_MODE_KEY, false);
            boolean forceRecognize    = prefs.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);

            // Nothing to do when all stylus features are disabled.
            if (!stylusModeEnabled && !forceRecognize) {
                logDebug("Pen features off — no action on screen on");
                return;
            }

            if (!PenUtils.isSetup()) {
                // The app process was killed and restarted via this broadcast.
                // setup() rebuilds all state and internally calls refreshPenMode(),
                // which will enable pen mode if the stored preferences require it.
                logInfo("PenUtils not initialized — running full setup");
                PenUtils.setup(context);
            } else {
                // Normal path: process is alive, just re-apply pen mode
                // in case the kernel/HAL state was lost during screen-off.
                logInfo("Re-applying pen mode after screen on");
                PenUtils.enablePenMode();
            }
        } catch (Exception e) {
            logError("Error handling screen on: " + e.getMessage());
        }
    }

    private void logDebug(String message) { Log.d(TAG, ts() + message); }
    private void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
