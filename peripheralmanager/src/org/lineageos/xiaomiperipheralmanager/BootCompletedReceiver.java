/*
 * Copyright (C) 2023-2025 The LineageOS Project
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
 * BroadcastReceiver that initializes peripheral managers on boot
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiPeripheralManager";
    private static final boolean DEBUG = SystemProperties.getBoolean("persist.xiaomi.peripherals.debug", false);

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            return;
        }
        
        logInfo("Device boot completed, initializing peripheral services");
        
        try {
            KeyboardUtils.setup(context);
            logInfo("Keyboard service initialized");
        } catch (Exception e) {
            logError("Failed to initialize keyboard service: " + e.getMessage());
        }
        
        try {
            PenUtils.setup(context);
            logInfo("Pen service initialized");
        } catch (Exception e) {
            logError("Failed to initialize pen service: " + e.getMessage());
        }
    }
    
    private void logDebug(String message) {
        if (DEBUG) Log.d(TAG, getTimestamp() + message);
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
