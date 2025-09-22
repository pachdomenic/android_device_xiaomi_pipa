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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StylusSettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "XiaomiStylusSettings";
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    private SharedPreferences mStylusPreference;
    private MainSwitchPreference mStylusModePref;
    private SwitchPreference mForceRecognizePref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.stylus_settings);

            Context context = getContext();
            mStylusPreference = PreferenceManager.getDefaultSharedPreferences(context);

            // Initialize main stylus mode switch
            mStylusModePref = (MainSwitchPreference) findPreference(STYLUS_MODE_KEY);
            if (mStylusModePref != null) {
                mStylusModePref.setChecked(mStylusPreference.getBoolean(STYLUS_MODE_KEY, false));
                logInfo("Stylus mode initialized: " + mStylusModePref.isChecked());
            }

            // Initialize force recognize switch
            mForceRecognizePref = (SwitchPreference) findPreference(FORCE_RECOGNIZE_STYLUS_KEY);
            if (mForceRecognizePref != null) {
                mForceRecognizePref.setChecked(mStylusPreference.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false));
                logInfo("Force recognize initialized: " + mForceRecognizePref.isChecked());
            }
            
            // Update footer with current status
            updateFooterInfo();
            
        } catch (Exception e) {
            logError("Error creating preferences: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mStylusPreference.registerOnSharedPreferenceChangeListener(this);
            // Update footer when returning to this screen
            updateFooterInfo();
        } catch (Exception e) {
            logError("Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mStylusPreference.unregisterOnSharedPreferenceChangeListener(this);
        } catch (Exception e) {
            logError("Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        try {
            if (STYLUS_MODE_KEY.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Stylus mode changed to: " + enabled);
                setStylusMode(enabled);
                // Update footer after mode change
                updateFooterInfo();
            } else if (FORCE_RECOGNIZE_STYLUS_KEY.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Force recognize changed to: " + enabled);
                setForceRecognizeStylus(enabled);
                // Update footer after setting change
                updateFooterInfo();
            }
        } catch (Exception e) {
            logError("Error handling preference change: " + e.getMessage());
        }
    }

     // Enables or disables stylus mode and applies/restores refresh rate constraints.
    private void setStylusMode(boolean enabled) {
        try {
            // Notify PenUtils about the mode change
            PenUtils.onStylusModeChanged(enabled);
            
            if (enabled) {
                logInfo("Stylus mode enabled - refresh rate limited to 60-120Hz");
            } else {
                logInfo("Stylus mode disabled - default refresh rates restored");
            }
        } catch (Exception e) {
            logError("Error setting stylus mode: " + e.getMessage());
        }
    }

     // Enables or disables force recognize mode for third-party styluses.
    private void setForceRecognizeStylus(boolean enabled) {
        try {
            // Notify PenUtils about the setting change
            PenUtils.onForceRecognizeChanged(enabled);
            
            if (enabled) {
                logInfo("Force recognize enabled - third party styluses supported");
            } else {
                logInfo("Force recognize disabled - only Xiaomi pen supported");
            }
        } catch (Exception e) {
            logError("Error setting force recognize: " + e.getMessage());
        }
    }

     // Update the footer preference with current pen status and mode information.
     // Shows: Status (Active/Inactive) and Pen connection state (Connected/Not detected)
    private void updateFooterInfo() {
        try {
            FooterPreference footerPref = (FooterPreference) findPreference("footer_key");
            if (footerPref != null) {
                boolean penConnected = PenUtils.isPenConnected();
                boolean penModeActive = PenUtils.isPenModeEnabled();
                
                StringBuilder info = new StringBuilder();
                // Original info text from strings.xml
                info.append(getString(R.string.stylus_more_info));
                info.append("\n\n");
                
                // Current status
                info.append("Status: ");
                if (penModeActive) {
                    info.append("Active (60-120Hz enforced)");
                } else {
                    info.append("Inactive");
                }
                
                // Pen connection state
                info.append("\nPen: ");
                info.append(penConnected ? "Connected" : "Not detected");
                
                footerPref.setTitle(info.toString());
            }
        } catch (Exception e) {
            logError("Error updating footer: " + e.getMessage());
        }
    }

    // Logging helpers with timestamps
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
