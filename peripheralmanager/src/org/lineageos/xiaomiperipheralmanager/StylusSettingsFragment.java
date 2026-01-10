/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

public class StylusSettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "XiaomiPeripheralManagerPenUtils";
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    private SharedPreferences mStylusPreference;
    private RefreshUtils mRefreshUtils;
    private ScreenStateReceiver mScreenStateReceiver;

    // Broadcast receiver to handle screen on/off
    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(TAG, "Screen turned ON - checking stylus mode status");
                
                // Reapply stylus mode settings when screen turns on
                boolean stylusModeEnabled = mStylusPreference.getBoolean(STYLUS_MODE_KEY, false);
                if (stylusModeEnabled) {
                    Log.d(TAG, "Reapplying stylus mode (120Hz lock)");
                    mRefreshUtils.setPenRefreshRate();
                    
                    // Also reapply force recognize stylus if it was enabled
                    boolean forceRecognizeEnabled = mStylusPreference.getBoolean(
                            FORCE_RECOGNIZE_STYLUS_KEY, false);
                    if (forceRecognizeEnabled) {
                        Log.d(TAG, "Reapplying force recognize stylus");
                        PenUtils.enablePenMode();
                    }
                }
            }
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.stylus_settings);

        Context context = getContext();
        mStylusPreference = PreferenceManager.getDefaultSharedPreferences(context);
        mRefreshUtils = new RefreshUtils(context);

        MainSwitchPreference stylusModePref =
            (MainSwitchPreference)findPreference(STYLUS_MODE_KEY);
        stylusModePref.setChecked(
            mStylusPreference.getBoolean(STYLUS_MODE_KEY, false));

        SwitchPreferenceCompat forceRecognizePref =
            (SwitchPreferenceCompat)findPreference(FORCE_RECOGNIZE_STYLUS_KEY);
        forceRecognizePref.setChecked(
            mStylusPreference.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false));
    }

    @Override
    public void onResume() {
        super.onResume();
        mStylusPreference.registerOnSharedPreferenceChangeListener(this);
        
        // Register screen state receiver
        mScreenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        getActivity().registerReceiver(mScreenStateReceiver, filter);
        
        Log.d(TAG, "Screen state receiver registered");
    }

    @Override
    public void onPause() {
        super.onPause();
        mStylusPreference.unregisterOnSharedPreferenceChangeListener(this);
        
        // Unregister screen state receiver
        if (mScreenStateReceiver != null) {
            try {
                getActivity().unregisterReceiver(mScreenStateReceiver);
                Log.d(TAG, "Screen state receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver not registered: " + e.getMessage());
            }
            mScreenStateReceiver = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (STYLUS_MODE_KEY.equals(key)) {
            setStylusMode(sharedPreferences.getBoolean(key, false));
        } else if (FORCE_RECOGNIZE_STYLUS_KEY.equals(key)) {
            setForceRecognizeStylus(sharedPreferences.getBoolean(key, false));
        }
    }

    private void setStylusMode(boolean enabled) {
        Log.d(TAG, "Stylus Mode: " + (enabled ? "ENABLED" : "DISABLED"));
        if (enabled) {
            mRefreshUtils.setPenRefreshRate();  // Sets min 60Hz, max 120Hz
        } else {
            mRefreshUtils.setDefaultRefreshRate();  // Restores user settings
        }
    }

    private void setForceRecognizeStylus(boolean enabled) {
        Log.d(TAG, "Force Recognize Stylus: " + (enabled ? "ENABLED" : "DISABLED"));
        if (enabled) {
            PenUtils.enablePenMode();
        } else {
            PenUtils.disablePenMode();
        }
    }
}
