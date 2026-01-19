/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Broadcast receiver that handles keyboard mode changes from settings
 * and controls the native keyboard service via system properties
 */
public class KeyboardPropertyHandler extends BroadcastReceiver {

    private static final String TAG = "XiaomiKeyboardProperty";
    private static final String ACTION_KEYBOARD_MODE_CHANGED = 
        "org.lineageos.xiaomiperipheralmanager.KEYBOARD_MODE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_KEYBOARD_MODE_CHANGED.equals(intent.getAction())) {
            return;
        }

        boolean enabled = intent.getBooleanExtra("enabled", false);
        logInfo("Keyboard mode change requested: " + enabled);

        try {
            // Set system property to trigger init.rc service start/stop
            String propertyValue = enabled ? "1" : "0";
            SystemProperties.set("persist.vendor.parts.keyboard", propertyValue);
            
            logInfo("Set persist.vendor.parts.keyboard=" + propertyValue);
            
            if (enabled) {
                logInfo("Keyboard service will start via init.rc trigger");
            } else {
                logInfo("Keyboard service will stop via init.rc trigger");
            }
        } catch (Exception e) {
            logError("Failed to set keyboard property: " + e.getMessage());
        }
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
