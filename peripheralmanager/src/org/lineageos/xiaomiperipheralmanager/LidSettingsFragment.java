/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Settings fragment for Smart Cover configuration.
 * Controls the lid_behavior setting which determines how the device
 * responds when the cover is opened/closed.
 */
public class LidSettingsFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "XiaomiLidSettings";
    private static final String LID_SWITCH_KEY = "lid_switch_key";
    private static final String SETTING_LID_BEHAVIOR = "lid_behavior";
    private static final int DEFAULT_LID_BEHAVIOR = 1;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.lid_settings);

        SwitchPreferenceCompat switchPreference = findPreference(LID_SWITCH_KEY);
        if (switchPreference != null) {
            switchPreference.setChecked(getLidBehaviorEnabled());
            switchPreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            boolean enabled = (Boolean) newValue;
            setLidBehaviorEnabled(enabled);
            Log.d(TAG, "Smart Cover lid behavior set to: " + enabled);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set Smart Cover state", e);
            return false;
        }
    }

    private boolean getLidBehaviorEnabled() {
        return Settings.Global.getInt(
            requireContext().getContentResolver(),
            SETTING_LID_BEHAVIOR,
            DEFAULT_LID_BEHAVIOR
        ) == 1;
    }

    private void setLidBehaviorEnabled(boolean enabled) {
        Settings.Global.putInt(
            requireContext().getContentResolver(),
            SETTING_LID_BEHAVIOR,
            enabled ? 1 : 0
        );
    }
}
