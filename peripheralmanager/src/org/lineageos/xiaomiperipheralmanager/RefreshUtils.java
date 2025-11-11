/*
 * Copyright (C) 2023-2025 The LineageOS Project
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
    private volatile float mTargetMinRate = PEN_MIN_RATE;
    private volatile float mTargetMaxRate = PEN_MAX_RATE;

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

            // Check if current rate is outside our target range
            if (!isRateInTargetRange(currentRate)) {
                logInfo("Detected rate outside target range: " + currentRate + 
                       "Hz - enforcing " + mTargetMinRate + "-" + mTargetMaxRate + "Hz");
                
                // Set flag to prevent recursive calls
                mIsEnforcingRate = true;
                
                // Select and apply the appropriate rate
                float targetRate = selectBestRate(currentRate);
                forceRefreshRate(mTargetMinRate, mTargetMaxRate, targetRate);
                
                // Reset flag after a delay to allow enforcement to complete
                mHandler.postDelayed(() -> mIsEnforcingRate = false, 500);
            }
        } catch (Exception e) {
            logError("Error checking refresh rate: " + e.getMessage());
            mIsEnforcingRate = false;
        }
    }

    private boolean isRateInTargetRange(float rate) {
        // Check if rate is within our min-max range (with tolerance for floating point)
        return (rate >= (mTargetMinRate - 1f)) && (rate <= (mTargetMaxRate + 1f));
    }

    private float selectBestRate(float currentRate) {
        // If we're in fixed mode (min == max), use that
        if (Math.abs(mTargetMinRate - mTargetMaxRate) < 1f) {
            return mTargetMinRate;
        }
        
        // For dynamic mode, choose closest valid rate
        if (currentRate < 90f) {
            return mTargetMinRate;
        } else {
            return mTargetMaxRate;
        }
    }

    // Force the display to specific refresh rates
    private void forceRefreshRate(float minRate, float maxRate, float userRate) {
        try {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_USER_REFRESH_RATE, userRate);
            logInfo("Forced refresh rate: " + minRate + "-" + maxRate + "Hz (user: " + userRate + "Hz)");
        } catch (Exception e) {
            logError("Failed to force refresh rate: " + e.getMessage());
        }
    }

    protected void setPenRefreshRate() {
        setRefreshRateRange(PEN_MIN_RATE, PEN_MAX_RATE, "Dynamic (60-120Hz)");
    }

    protected void setFixedRefreshRate(float fixedRate) {
        String modeName = "Fixed " + (int)fixedRate + "Hz";
        setRefreshRateRange(fixedRate, fixedRate, modeName);
    }

    private void setRefreshRateRange(float minRate, float maxRate, String modeName) {
        boolean penMode = mSharedPrefs.getBoolean(KEY_PEN_MODE, false);

        if (!penMode) {
            logInfo("Enabling pen mode with " + modeName);
            
            // Get current refresh rate settings
            float currentMaxRate = Settings.System.getFloat(mContext.getContentResolver(), 
                                                           KEY_PEAK_REFRESH_RATE, 144f);
            float currentMinRate = Settings.System.getFloat(mContext.getContentResolver(), 
                                                           KEY_MIN_REFRESH_RATE, 144f);
            float currentUserRate = Settings.System.getFloat(mContext.getContentResolver(), 
                                                            "user_refresh_rate", 0f);

            // Save current values for restoration later
            mSharedPrefs.edit()
                    .putFloat(KEY_MIN_REFRESH_RATE, currentMinRate)
                    .putFloat(KEY_PEAK_REFRESH_RATE, currentMaxRate)
                    .putFloat(KEY_USER_REFRESH_RATE, currentUserRate)
                    .putBoolean(KEY_PEN_MODE, true)
                    .apply();
        } else {
            logInfo("Updating pen mode to " + modeName);
        }

        // Store target rates for enforcement
        mTargetMinRate = minRate;
        mTargetMaxRate = maxRate;

        // Apply the new rates
        float userRate = (minRate == maxRate) ? minRate : maxRate;
        forceRefreshRate(minRate, maxRate, userRate);
        
        // Enable active monitoring
        mPenModeActive = true;
        registerDisplayListener();
        
        // Force initial rate check after a short delay
        mHandler.postDelayed(() -> checkAndEnforceRefreshRate(), 200);
        
        logInfo("Pen mode configured: " + modeName + " with active enforcement");
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
