/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.preference.PreferenceFragment;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for keyboard configuration
 * Allows users to enable/disable keyboard monitoring service
 */
public class KeyboardSettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "XiaomiKeyboardSettings";
    private static final String KEYBOARD_MODE_KEY = "keyboard_mode_key";
    private static final String KEYBOARD_FOOTER_KEY = "keyboard_footer_key";

    private SharedPreferences mKeyboardPreference;
    private MainSwitchPreference mKeyboardModePref;
    private FooterPreference mFooterPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.keyboard_settings);

            Context context = getContext();
            mKeyboardPreference = PreferenceManager.getDefaultSharedPreferences(context);

            // Initialize preferences
            mKeyboardModePref = (MainSwitchPreference) findPreference(KEYBOARD_MODE_KEY);
            mFooterPref = (FooterPreference) findPreference(KEYBOARD_FOOTER_KEY);

            // Load initial state
            refreshUI();

            logInfo("Keyboard settings created successfully");
        } catch (Exception e) {
            logError("Error creating preferences: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mKeyboardPreference.registerOnSharedPreferenceChangeListener(this);
            refreshUI();
            logInfo("Keyboard settings resumed");
        } catch (Exception e) {
            logError("Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mKeyboardPreference.unregisterOnSharedPreferenceChangeListener(this);
            logInfo("Keyboard settings paused");
        } catch (Exception e) {
            logError("Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (KEYBOARD_MODE_KEY.equals(key)) {
            try {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Keyboard mode changed to: " + enabled);

                // Apply immediately
                KeyboardUtils.setKeyboardMonitoringEnabled(getContext(), enabled);

                // Update UI
                refreshUI();

            } catch (Exception e) {
                logError("Error handling preference change: " + e.getMessage());
            }
        }
    }

    /**
     * Refresh all UI elements to match current state
     */
    private void refreshUI() {
        try {
            boolean keyboardModeEnabled = mKeyboardPreference.getBoolean(KEYBOARD_MODE_KEY, false);

            // Update keyboard mode switch
            if (mKeyboardModePref != null && mKeyboardModePref.isChecked() != keyboardModeEnabled) {
                mKeyboardModePref.setChecked(keyboardModeEnabled);
                logDebug("Updated keyboard mode UI: " + keyboardModeEnabled);
            }

            // Update footer
            updateFooterInfo();

        } catch (Exception e) {
            logError("Error refreshing UI: " + e.getMessage());
        }
    }

    /**
     * Update the footer preference with current status
     */
    private void updateFooterInfo() {
        if (mFooterPref == null) return;

        try {
            boolean keyboardModeEnabled = mKeyboardPreference.getBoolean(KEYBOARD_MODE_KEY, false);

            StringBuilder info = new StringBuilder();

            // Original info text
            info.append(getString(R.string.keyboard_more_info));
            info.append("\n\n");

            // Current status
            String statusText = keyboardModeEnabled 
                ? getString(R.string.keyboard_status_enabled)
                : getString(R.string.keyboard_status_disabled);
            info.append(getString(R.string.keyboard_footer_status, statusText));

            mFooterPref.setTitle(info.toString());
        } catch (Exception e) {
            logError("Error updating footer: " + e.getMessage());
        }
    }

    // Logging helpers
    private void logDebug(String message) {
        Log.d(TAG, getTimestamp() + message);
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
