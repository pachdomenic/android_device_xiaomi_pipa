/*
 * Copyright (C) 2023 The LineageOS Project
 * Copyright (C) 2025 SheoranPranshu
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
import androidx.preference.ListPreference;
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
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private SharedPreferences mStylusPreference;
    private MainSwitchPreference mStylusModePref;
    private SwitchPreference mForceRecognizePref;
    private ListPreference mRefreshRatePref;

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

            // Initialize refresh rate preference
            mRefreshRatePref = (ListPreference) findPreference(STYLUS_REFRESH_RATE_KEY);
            if (mRefreshRatePref != null) {
                String savedRate = mStylusPreference.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
                mRefreshRatePref.setValue(savedRate);
                updateRefreshRateSummary(savedRate);
                logInfo("Refresh rate initialized: " + savedRate);
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
                updateFooterInfo();
            } else if (FORCE_RECOGNIZE_STYLUS_KEY.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Force recognize changed to: " + enabled);
                setForceRecognizeStylus(enabled);
                updateFooterInfo();
            } else if (STYLUS_REFRESH_RATE_KEY.equals(key)) {
                String newRate = sharedPreferences.getString(key, "dynamic");
                logInfo("Refresh rate changed to: " + newRate);
                setRefreshRate(newRate);
                updateRefreshRateSummary(newRate);
                updateFooterInfo();
            }
        } catch (Exception e) {
            logError("Error handling preference change: " + e.getMessage());
        }
    }

    private void setStylusMode(boolean enabled) {
        try {
            // Notify PenUtils about the mode change
            PenUtils.onStylusModeChanged(enabled);
            
            if (enabled) {
                // Apply the saved refresh rate preference
                String refreshRate = mStylusPreference.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
                setRefreshRate(refreshRate);
                logInfo("Stylus mode enabled with refresh rate: " + refreshRate);
            } else {
                logInfo("Stylus mode disabled - default refresh rates restored");
            }
        } catch (Exception e) {
            logError("Error setting stylus mode: " + e.getMessage());
        }
    }

     // Enable or disable force recognize mode
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

     // Apply the selected refresh rate configuration
    private void setRefreshRate(String rateValue) {
        try {
            boolean stylusModeEnabled = mStylusPreference.getBoolean(STYLUS_MODE_KEY, false);
            if (!stylusModeEnabled) {
                logInfo("Stylus mode disabled, skipping refresh rate change");
                return;
            }

            // Notify RefreshUtils to apply the rate
            PenUtils.setRefreshRateMode(rateValue);
            logInfo("Applied refresh rate mode: " + rateValue);
        } catch (Exception e) {
            logError("Error setting refresh rate: " + e.getMessage());
        }
    }

     // Update the refresh rate preference summary
    private void updateRefreshRateSummary(String rateValue) {
        if (mRefreshRatePref != null) {
            String summary;
            switch (rateValue) {
                case "60":
                    summary = getString(R.string.refresh_rate_60hz);
                    break;
                case "120":
                    summary = getString(R.string.refresh_rate_120hz);
                    break;
                case "dynamic":
                default:
                    summary = getString(R.string.refresh_rate_dynamic);
                    break;
            }
            mRefreshRatePref.setSummary(summary);
        }
    }

     // Update the footer preference with current pen status and mode information.
     // Shows: Status (Active/Inactive) and Current refresh rate mode
    private void updateFooterInfo() {
        try {
            FooterPreference footerPref = (FooterPreference) findPreference("footer_key");
            if (footerPref != null) {
                boolean penModeActive = PenUtils.isPenModeEnabled();
                String refreshRate = mStylusPreference.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
                
                StringBuilder info = new StringBuilder();
                // Original info text from strings.xml
                info.append(getString(R.string.stylus_more_info));
                info.append("\n\n");
                
                // Current status
                String statusText = penModeActive ? 
                    getString(R.string.stylus_status_active) : 
                    getString(R.string.stylus_status_inactive);
                info.append(getString(R.string.stylus_footer_status, statusText));
                
                // Current refresh rate mode (only show when active)
                if (penModeActive) {
                    info.append("\n");
                    String rateText;
                    switch (refreshRate) {
                        case "60":
                            rateText = getString(R.string.refresh_rate_60hz);
                            break;
                        case "120":
                            rateText = getString(R.string.refresh_rate_120hz);
                            break;
                        case "dynamic":
                        default:
                            rateText = getString(R.string.refresh_rate_dynamic);
                            break;
                    }
                    info.append(getString(R.string.stylus_footer_refresh_rate, rateText));
                }
                
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
