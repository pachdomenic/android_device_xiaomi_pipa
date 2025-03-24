/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import android.preference.PreferenceManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for stylus/pen configuration
 * Allows users to manually enable/disable the pen mode
 */
public class StylusSettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "XiaomiPenSettings";
    private static boolean DEBUG = SystemProperties.getBoolean("persist.xiaomi.pen.debug", false);
    private static final String STYLUS_KEY = "stylus_switch_key";

    private SharedPreferences mStylusPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.stylus_settings);

            mStylusPreference = PreferenceManager.getDefaultSharedPreferences(getContext());
            SwitchPreference switchPreference = (SwitchPreference) findPreference(STYLUS_KEY);

            if (switchPreference != null) {
                switchPreference.setChecked(mStylusPreference.getBoolean(STYLUS_KEY, false));
                switchPreference.setEnabled(true);
            } else {
                logError("Could not find stylus switch preference");
            }
            
            logInfo("Stylus settings fragment created");
        } catch (Exception e) {
            logError("Error creating stylus settings: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mStylusPreference.registerOnSharedPreferenceChangeListener(this);
            logDebug("Registered preference change listener");
        } catch (Exception e) {
            logError("Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mStylusPreference.unregisterOnSharedPreferenceChangeListener(this);
            logDebug("Unregistered preference change listener");
        } catch (Exception e) {
            logError("Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreference, String key) {
        if (STYLUS_KEY.equals(key)) {
            try {
                boolean newStatus = mStylusPreference.getBoolean(key, false);
                logInfo("Stylus preference changed to: " + newStatus);
                forceStylus(newStatus);
            } catch (Exception e) {
                logError("Error handling preference change: " + e.getMessage());
            }
        }
    }

    private void forceStylus(boolean status) {
        try {
            mStylusPreference.edit().putBoolean(STYLUS_KEY, status).apply();
            logInfo("Setting stylus mode: " + (status ? "enabled" : "disabled"));
            
            if (status) {
                PenUtils.enablePenMode();
            } else {
                PenUtils.disablePenMode();
            }
        } catch (Exception e) {
            logError("Error setting stylus mode: " + e.getMessage());
        }
    }
    
    // Enhanced logging helpers to match other classes
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
