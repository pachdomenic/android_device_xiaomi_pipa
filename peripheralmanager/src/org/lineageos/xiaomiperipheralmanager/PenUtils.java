/*
 * Copyright (C) 2023 The LineageOS Project
 * Copyright (C) 2025 SheoranPranshu
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.InputDevice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PenUtils {

    private static final String TAG = "XiaomiPenUtils";
    private static final boolean DEBUG = true;

    // Xiaomi pen hardware identifiers
    private static final int penVendorId = 6421;
    private static final int penProductId = 19841;
    
    // Preference keys
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_STYLUS_KEY = "force_recognize_stylus_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private static InputManager mInputManager;
    private static SharedPreferences mPreferences;
    private static RefreshUtils mRefreshUtils;
    private static Handler mHandler;
    
    // State tracking
    private static boolean mPenModeEnabled = false;
    private static boolean mIsPenConnected = false;
    private static Context mContext;
    private static String mCurrentRefreshMode = "dynamic";

    // Set up device listeners.
    public static void setup(Context context) {
        mContext = context;
        mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mRefreshUtils = new RefreshUtils(context);
        mHandler = new Handler(Looper.getMainLooper());
        
        // Register listener for pen connection/disconnection events
        mInputManager.registerInputDeviceListener(mInputDeviceListener, mHandler);
        
        // Load saved refresh rate preference
        mCurrentRefreshMode = mPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
        
        // Log current settings state
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceRecognize = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        
        logInfo("Setup complete - Stylus mode: " + stylusModeEnabled + 
                ", Force recognize: " + forceRecognize + 
                ", Refresh mode: " + mCurrentRefreshMode);
        
        // Initial pen mode check
        refreshPenMode();
    }

    public static void enablePenMode() {
        if (mPenModeEnabled) {
            logDebug("Pen mode already enabled");
            return;
        }
        
        logInfo("Enabling pen mode");
        mPenModeEnabled = true;
        
        // Set system property for kernel/HAL
        SystemProperties.set("persist.vendor.parts.pen", "18");
        
        // Apply refresh rate constraints if stylus mode is enabled
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        if (mRefreshUtils != null && stylusModeEnabled) {
            applyRefreshRateMode(mCurrentRefreshMode);
            logInfo("Applied pen mode with refresh rate: " + mCurrentRefreshMode);
        } else {
            logInfo("Pen hardware enabled without refresh rate constraints");
        }
    }

    public static void disablePenMode() {
        if (!mPenModeEnabled) {
            logDebug("Pen mode already disabled");
            return;
        }
        
        logInfo("Disabling pen mode");
        mPenModeEnabled = false;
        
        // Clear system property
        SystemProperties.set("persist.vendor.parts.pen", "2");
        
        // Restore default refresh rates only if they were being enforced
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        if (mRefreshUtils != null && stylusModeEnabled) {
            mRefreshUtils.setDefaultRefreshRate();
            logInfo("Restored default refresh rates");
        }
    }

    private static void refreshPenMode() {
        boolean forceRecognize = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean penDetected = false;
        
        // Check if Xiaomi pen is physically connected
        for (int id : mInputManager.getInputDeviceIds()) {
            if (isDeviceXiaomiPen(id)) {
                penDetected = true;
                logDebug("Xiaomi pen detected (device ID: " + id + ")");
                break;
            }
        }
        
        mIsPenConnected = penDetected;
        
        // Enable pen mode if:
        // 1. Stylus mode is enabled AND (pen is detected OR force recognize is on)
        // 2. OR force recognize is enabled (backward compatibility)
        boolean shouldEnablePen = (stylusModeEnabled && (penDetected || forceRecognize)) || forceRecognize;
        
        if (shouldEnablePen) {
            logInfo("Pen mode should be enabled - Detected: " + penDetected + 
                   ", Stylus mode: " + stylusModeEnabled + ", Force: " + forceRecognize);
            enablePenMode();
        } else {
            logInfo("Pen mode should be disabled - Detected: " + penDetected + 
                   ", Stylus mode: " + stylusModeEnabled);
            disablePenMode();
        }
    }

     // Apply the selected refresh rate mode
    private static void applyRefreshRateMode(String mode) {
        if (mRefreshUtils == null) {
            logError("RefreshUtils not initialized");
            return;
        }

        mCurrentRefreshMode = mode;
        
        switch (mode) {
            case "60":
                mRefreshUtils.setFixedRefreshRate(60f);
                logInfo("Applied fixed 60Hz refresh rate");
                break;
            case "120":
                mRefreshUtils.setFixedRefreshRate(120f);
                logInfo("Applied fixed 120Hz refresh rate");
                break;
            case "dynamic":
            default:
                mRefreshUtils.setPenRefreshRate();
                logInfo("Applied dynamic 60-120Hz refresh rate");
                break;
        }
    }

    public static void setRefreshRateMode(String mode) {
        logInfo("Refresh rate mode changed to: " + mode);
        mCurrentRefreshMode = mode;
        
        // Only apply if pen mode is currently active
        if (mPenModeEnabled && mRefreshUtils != null) {
            applyRefreshRateMode(mode);
        }
    }

    // Checks if an input device is the Xiaomi pen based on vendor and product IDs.
    private static boolean isDeviceXiaomiPen(int id) {
        try {
            InputDevice inputDevice = mInputManager.getInputDevice(id);
            if (inputDevice == null) return false;
            
            boolean isPen = inputDevice.getVendorId() == penVendorId &&
                          inputDevice.getProductId() == penProductId;
            
            if (isPen) {
                logDebug("Found Xiaomi pen: " + inputDevice.getName());
            }
            
            return isPen;
        } catch (Exception e) {
            logError("Error checking device: " + e.getMessage());
            return false;
        }
    }

    // InputDeviceListener to handle pen connection/disconnection events.
    private static InputDeviceListener mInputDeviceListener = new InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int id) {
            logDebug("Input device added: " + id);
            // Check if it's the Xiaomi pen
            if (isDeviceXiaomiPen(id)) {
                logInfo("Xiaomi pen connected");
                // Delay to ensure device is fully initialized
                mHandler.postDelayed(() -> refreshPenMode(), 100);
            }
        }
        
        @Override
        public void onInputDeviceRemoved(int id) {
            logDebug("Input device removed: " + id);
            // Device is already removed, so check all remaining devices
            mHandler.postDelayed(() -> refreshPenMode(), 100);
        }
        
        @Override
        public void onInputDeviceChanged(int id) {
            logDebug("Input device changed: " + id);
            if (isDeviceXiaomiPen(id)) {
                logInfo("Xiaomi pen changed");
                mHandler.postDelayed(() -> refreshPenMode(), 100);
            }
        }
    };

    public static void onStylusModeChanged(boolean enabled) {
        logInfo("Stylus mode setting changed to: " + enabled);
        refreshPenMode();
    }

    public static void onForceRecognizeChanged(boolean enabled) {
        logInfo("Force recognize setting changed to: " + enabled);
        refreshPenMode();
    }

    // Physical pen connected?
    public static boolean isPenConnected() {
        return mIsPenConnected;
    }

    // Pen mode enabled?
    public static boolean isPenModeEnabled() {
        return mPenModeEnabled;
    }

    // Get current refresh rate mode
    public static String getCurrentRefreshMode() {
        return mCurrentRefreshMode;
    }

    // Cleanup method to unregister listeners.
    public static void cleanup() {
        if (mInputManager != null && mInputDeviceListener != null) {
            mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
            logInfo("Input device listener unregistered");
        }
        if (mRefreshUtils != null) {
            mRefreshUtils.cleanup();
        }
    }

    // Logging helpers with timestamps
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
