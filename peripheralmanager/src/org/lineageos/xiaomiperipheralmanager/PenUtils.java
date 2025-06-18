/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.os.SystemProperties;
import android.util.Log;
import android.view.InputDevice;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for handling Xiaomi pen operations and mode switching
 */
public class PenUtils {

    private static final String TAG = "XiaomiPen";
    private static boolean DEBUG = SystemProperties.getBoolean("persist.xiaomi.pen.debug", false);

    // Xiaomi pen identifiers
    private static final int PEN_VENDOR_ID = 6421;
    private static final int PEN_PRODUCT_ID = 19841;

    private static InputManager mInputManager;
    private static SharedPreferences mPreferences;
    private static final String STYLUS_KEY = "stylus_switch_key";

    /**
     * Initialize the pen utilities and register for input device events
     * @param context Application context
     */
    public static void setup(Context context) {
        logInfo("Initializing Xiaomi pen framework integration");
        try {
            mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            mInputManager.registerInputDeviceListener(mInputDeviceListener, null);
            mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            refreshPenMode();
        } catch (Exception e) {
            logError("Error setting up pen utils: " + e.getMessage());
        }
    }

    /**
     * Enable pen mode by setting system property
     */
    public static void enablePenMode() {
        logInfo("Enabling pen mode");
        try {
            SystemProperties.set("persist.vendor.parts.pen", "18");
        } catch (Exception e) {
            logError("Failed to enable pen mode: " + e.getMessage());
        }
    }

    /**
     * Disable pen mode by setting system property
     */
    public static void disablePenMode() {
        logInfo("Disabling pen mode");
        try {
            SystemProperties.set("persist.vendor.parts.pen", "2");
        } catch (Exception e) {
            logError("Failed to disable pen mode: " + e.getMessage());
        }
    }

    /**
     * Check for pen presence and update mode accordingly
     */
    private static void refreshPenMode() {
        try {
            boolean penFound = false;
            
            for (int id : mInputManager.getInputDeviceIds()) {
                if (isDeviceXiaomiPen(id)) {
                    logDebug("Found Xiaomi Pen with id: " + id);
                    penFound = true;
                    break;
                }
            }
            
            // Also check preference override
            boolean preferenceEnabled = mPreferences.getBoolean(STYLUS_KEY, false);
            
            if (penFound || preferenceEnabled) {
                logInfo("Pen detected or enabled in preferences");
                enablePenMode();
            } else {
                logInfo("No pen detected and not enabled in preferences");
                disablePenMode();
            }
        } catch (Exception e) {
            logError("Error refreshing pen mode: " + e.getMessage());
        }
    }

    /**
     * Check if an input device is the Xiaomi pen
     * @param id Device ID to check
     * @return true if the device is the Xiaomi pen
     */
    private static boolean isDeviceXiaomiPen(int id) {
        try {
            InputDevice inputDevice = mInputManager.getInputDevice(id);
            if (inputDevice == null) return false;
            
            return inputDevice.getVendorId() == PEN_VENDOR_ID && 
                   inputDevice.getProductId() == PEN_PRODUCT_ID;
        } catch (Exception e) {
            logError("Error checking pen device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Input device listener for pen connection/disconnection events
     */
    private static InputDeviceListener mInputDeviceListener = new InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int id) {
            logDebug("Input device added: " + id);
            refreshPenMode();
        }
        
        @Override
        public void onInputDeviceRemoved(int id) {
            logDebug("Input device removed: " + id);
            refreshPenMode();
        }
        
        @Override
        public void onInputDeviceChanged(int id) {
            logDebug("Input device changed: " + id);
            refreshPenMode();
        }
    };
    
    // Enhanced logging helpers to match other classes
    private static void logDebug(String message) {
        if (DEBUG) Log.d(TAG, getTimestamp() + message);
    }
    
    private static void logInfo(String message) {
        Log.i(TAG, getTimestamp() + message);
    }
    
    private static void logError(String message) {
        Log.e(TAG, getTimestamp() + message);
    }
    
    private static String getTimestamp() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
