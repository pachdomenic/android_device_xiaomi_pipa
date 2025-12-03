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
import android.preference.PreferenceManager;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;
import com.android.settingslib.widget.FooterPreference;
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
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";
    private SharedPreferences mStylusPreference;
    private RefreshUtils mRefreshUtils;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.stylus_settings);

    Context context = getContext();
    mStylusPreference = PreferenceManager.getDefaultSharedPreferences(context);
    mRefreshUtils = new RefreshUtils(context);

    SwitchPreferenceCompat forceRecognizePref =
        (SwitchPreferenceCompat)findPreference("force_recognize_stylus_key");
    forceRecognizePref.setChecked(
        mStylusPreference.getBoolean("force_recognize_stylus_key", false));
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (FORCE_RECOGNIZE_STYLUS_KEY.equals(key) {
            setForceRecognizeStylus(sharedPreferences.getBoolean(key, false));
        }

    }

    private void setStylusMode(boolean enabled) {
      if (enabled)
        mRefreshUtils.setPenRefreshRate();
      else
        mRefreshUtils.setDefaultRefreshRate();
    }

    private void setForceRecognizeStylus(boolean enabled) {
      if (enabled)
        PenUtils.enablePenMode();
      else
        PenUtils.disablePenMode();
    
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
