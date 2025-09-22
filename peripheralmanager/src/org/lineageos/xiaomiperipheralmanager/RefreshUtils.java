/*
 * Copyright (C) 2023 The LineageOS Project
 * Copyright (C) 2025 SheoranPranshu
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RefreshUtils {
    private static final String TAG = "XiaomiRefreshUtils";
    private static final boolean DEBUG = true;
    
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_USER_REFRESH_RATE = "user_refresh_rate";
    private static final String KEY_PEN_MODE = "pen_mode";
    private static final String PREF_FILE_NAME = "pen_refresh_prefs";
    
    // Pen-compatible refresh rates
    private static final float PEN_MIN_RATE = 60f;
    private static final float PEN_MAX_RATE = 120f;
    
    private Context mContext;
    private SharedPreferences mSharedPrefs;
    private DisplayManager mDisplayManager;
    private DisplayManager.DisplayListener mDisplayListener;
    private Handler mHandler;
    
    // State tracking
    private volatile boolean mPenModeActive = false;
    private volatile boolean mIsEnforcingRate = false;
    private volatile boolean mListenerRegistered = false;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        
        createDisplayListener();
        logInfo("RefreshUtils initialized");
    }

     // Creates the display listener but registers when pen mode is enabled
    private void createDisplayListener() {
        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // Not needed for our use case
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                // Not needed for our use case
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // Only monitor the default display (main screen)
                if (displayId == Display.DEFAULT_DISPLAY) {
                    // Post to handler to avoid potential deadlocks
                    mHandler.post(() -> checkAndEnforceRefreshRate());
                }
            }
        };
    }

     // Register the display listener to start monitoring refresh rate changes.
    private void registerDisplayListener() {
        if (!mListenerRegistered && mDisplayManager != null && mDisplayListener != null) {
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            mListenerRegistered = true;
            logInfo("Display listener registered - active monitoring enabled");
        }
    }

     // Unregister the display listener to stop monitoring upon disabling pen mode.
    private void unregisterDisplayListener() {
        if (mListenerRegistered && mDisplayManager != null && mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
            mListenerRegistered = false;
            logInfo("Display listener unregistered - monitoring disabled");
        }
    }

    private void checkAndEnforceRefreshRate() {
        // Skip if pen mode is not active or we're already enforcing
        if (!mPenModeActive || mIsEnforcingRate) {
            return;
        }

        try {
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) {
                logError("Could not get default display");
                return;
            }

            float currentRate = display.getRefreshRate();
            logDebug("Current refresh rate: " + currentRate + "Hz");

            // Check if current rate is incompatible with pen (30Hz, 48Hz, 50Hz, etc.)
            if (!isRateCompatibleWithPen(currentRate)) {
                logInfo("Detected incompatible refresh rate: " + currentRate + "Hz - enforcing pen rate");
                
                // Set flag to prevent recursive calls
                mIsEnforcingRate = true;
                
                // Select and apply the appropriate pen-compatible rate
                float targetRate = selectBestPenRate(currentRate);
                forceRefreshRate(targetRate);
                
                // Reset flag after a delay to allow enforcement to complete
                mHandler.postDelayed(() -> mIsEnforcingRate = false, 500);
            }
        } catch (Exception e) {
            logError("Error checking refresh rate: " + e.getMessage());
            mIsEnforcingRate = false;
        }
    }

    private boolean isRateCompatibleWithPen(float rate) {
        // Allow 60Hz and 120Hz (with tolerance for floating point comparison)
        return (Math.abs(rate - 60f) < 1f) || (Math.abs(rate - 120f) < 1f);
    }

    private float selectBestPenRate(float currentRate) {
        // If current rate is below 90Hz, switch to 60Hz; otherwise switch to 120Hz
        return currentRate < 90f ? 60f : 120f;
    }

     // Force the display to a specific refresh rate by setting min, max, and user rates.
    private void forceRefreshRate(float rate) {
        try {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, rate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, rate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_USER_REFRESH_RATE, rate);
            logInfo("Forced refresh rate to: " + rate + "Hz");
        } catch (Exception e) {
            logError("Failed to force refresh rate: " + e.getMessage());
        }
    }

     // Enable pen refresh rate mode.
     // Register display listener to actively monitor and enforce rates.
    protected void setPenRefreshRate() {
        boolean penMode = mSharedPrefs.getBoolean(KEY_PEN_MODE, false);

        if (!penMode) {
            logInfo("Enabling pen mode - saving current refresh rates");
            
            // Get current refresh rate settings
            float maxRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 144f);
            float minRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 144f);
            float userRate = Settings.System.getFloat(mContext.getContentResolver(), "user_refresh_rate", 0f);

            // Save current values in SharedPreferences for restoration later
            mSharedPrefs.edit()
                    .putFloat(KEY_MIN_REFRESH_RATE, minRate)
                    .putFloat(KEY_PEAK_REFRESH_RATE, maxRate)
                    .putFloat(KEY_USER_REFRESH_RATE, userRate)
                    .putBoolean(KEY_PEN_MODE, true)
                    .apply();

            // Set fixed refresh rates for pen mode (60-120Hz only)
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, PEN_MIN_RATE);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, PEN_MAX_RATE);
            Settings.System.putFloat(mContext.getContentResolver(), "user_refresh_rate", PEN_MAX_RATE);
            
            // Enable active monitoring
            mPenModeActive = true;
            registerDisplayListener();
            
            // Force initial rate check after a short delay
            mHandler.postDelayed(() -> checkAndEnforceRefreshRate(), 200);
            
            logInfo("Pen mode enabled: " + PEN_MIN_RATE + "-" + PEN_MAX_RATE + "Hz with active enforcement");
        }
    }

    protected void setDefaultRefreshRate() {
        logInfo("Disabling pen mode - restoring original refresh rates");
        
        // Disable active monitoring first
        mPenModeActive = false;
        unregisterDisplayListener();
        
        // Get saved refresh rate values
        float defaultMinRate = mSharedPrefs.getFloat(KEY_MIN_REFRESH_RATE, 144f);
        float defaultMaxRate = mSharedPrefs.getFloat(KEY_PEAK_REFRESH_RATE, 144f);
        float defaultUserRate = mSharedPrefs.getFloat(KEY_USER_REFRESH_RATE, 0f);

        // Mark pen mode as disabled
        mSharedPrefs.edit().putBoolean(KEY_PEN_MODE, false).apply();

        // Restore original refresh rate values
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, defaultMinRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, defaultMaxRate);
        Settings.System.putFloat(mContext.getContentResolver(), "user_refresh_rate", defaultUserRate);
        
        logInfo("Default refresh rates restored: " + defaultMinRate + "-" + defaultMaxRate + "Hz");
    }

    protected void cleanup() {
        unregisterDisplayListener();
    }

    // Logging helpers with timestamps
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
