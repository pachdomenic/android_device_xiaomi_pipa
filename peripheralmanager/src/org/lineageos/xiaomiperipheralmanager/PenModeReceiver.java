/*
 * Copyright (C) 2026 The LineageOS Project
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

public class PenModeReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiPeripheralManagerPenModeReceiver";

    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean stylusModeEnabled = prefs.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceRecognizeEnabled = prefs.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);

        if (stylusModeEnabled && forceRecognizeEnabled) {
            Log.d(TAG, "Screen ON with force-recognize stylus enabled, forcing pen mode");
            PenUtils.enablePenMode();
        }
    }
}

