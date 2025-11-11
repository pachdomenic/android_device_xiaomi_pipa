/*
 * Copyright (C) 2023-2025 The LineageOS Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final String FOOTER_KEY = "footer_key";

    private SharedPreferences mStylusPreference;
    private MainSwitchPreference mStylusModePref;
    private SwitchPreference mForceRecognizePref;
    private ListPreference mRefreshRatePref;
    private FooterPreference mFooterPref;
    
    private Handler mHandler;
    private boolean mIsHandlingChange = false;
    
    // Broadcast receiver for tile updates
    private BroadcastReceiver mTileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logDebug("Received tile update broadcast");
            // Use handler to avoid potential timing issues
            mHandler.post(() -> refreshUI());
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.stylus_settings);
            
            mHandler = new Handler(Looper.getMainLooper());
            Context context = getContext();
            mStylusPreference = PreferenceManager.getDefaultSharedPreferences(context);

            // Initialize preferences
            mStylusModePref = (MainSwitchPreference) findPreference(STYLUS_MODE_KEY);
            mForceRecognizePref = (SwitchPreference) findPreference(FORCE_RECOGNIZE_STYLUS_KEY);
            mRefreshRatePref = (ListPreference) findPreference(STYLUS_REFRESH_RATE_KEY);
            mFooterPref = (FooterPreference) findPreference(FOOTER_KEY);

            // Load initial state
            refreshUI();
            
            logInfo("Stylus settings created successfully");
        } catch (Exception e) {
            logError("Error creating preferences: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            // Register preference change listener
            mStylusPreference.registerOnSharedPreferenceChangeListener(this);
            
            // Register broadcast receiver for tile updates
            IntentFilter filter = new IntentFilter("org.lineageos.xiaomiperipheralmanager.STYLUS_MODE_CHANGED");
            getContext().registerReceiver(mTileUpdateReceiver, filter);
            
            // Refresh UI to ensure sync with current state
            refreshUI();
            
            logInfo("Stylus settings resumed");
        } catch (Exception e) {
            logError("Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            // Unregister listeners
            mStylusPreference.unregisterOnSharedPreferenceChangeListener(this);
            
            try {
                getContext().unregisterReceiver(mTileUpdateReceiver);
            } catch (Exception e) {
                // Receiver might not be registered
            }
            
            logInfo("Stylus settings paused");
        } catch (Exception e) {
            logError("Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Prevent recursive updates
        if (mIsHandlingChange) {
            logDebug("Ignoring recursive preference change");
            return;
        }
        
        mIsHandlingChange = true;
        
        try {
            if (STYLUS_MODE_KEY.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Stylus mode changed to: " + enabled);
                
                // Apply immediately
                PenUtils.onStylusModeChanged(enabled);
                
                if (enabled) {
                    // Apply saved refresh rate
                    String refreshRate = sharedPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
                    PenUtils.setRefreshRateMode(refreshRate);
                }
                
                // Update UI
                refreshUI();
                
            } else if (FORCE_RECOGNIZE_STYLUS_KEY.equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Force recognize changed to: " + enabled);
                
                // Apply immediately
                PenUtils.onForceRecognizeChanged(enabled);
                
                // Update UI
                refreshUI();
                
            } else if (STYLUS_REFRESH_RATE_KEY.equals(key)) {
                String newRate = sharedPreferences.getString(key, "dynamic");
                logInfo("Refresh rate changed to: " + newRate);
                
                // Apply immediately if stylus mode is enabled
                boolean stylusModeEnabled = sharedPreferences.getBoolean(STYLUS_MODE_KEY, false);
                if (stylusModeEnabled) {
                    PenUtils.setRefreshRateMode(newRate);
                }
                
                // Update UI
                refreshUI();
            }
        } catch (Exception e) {
            logError("Error handling preference change: " + e.getMessage());
        } finally {
            // Reset flag after a short delay
            mHandler.postDelayed(() -> mIsHandlingChange = false, 100);
        }
    }

    /**
     * Refresh all UI elements to match current state
     * This is called when preferences change or when returning to the screen
     */
    private void refreshUI() {
        try {
            // Read current state
            boolean stylusModeEnabled = mStylusPreference.getBoolean(STYLUS_MODE_KEY, false);
            boolean forceRecognize = mStylusPreference.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);
            String refreshRate = mStylusPreference.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
            
            // Update stylus mode switch
            if (mStylusModePref != null && mStylusModePref.isChecked() != stylusModeEnabled) {
                mStylusModePref.setChecked(stylusModeEnabled);
                logDebug("Updated stylus mode UI: " + stylusModeEnabled);
            }
            
            // Update force recognize switch
            if (mForceRecognizePref != null && mForceRecognizePref.isChecked() != forceRecognize) {
                mForceRecognizePref.setChecked(forceRecognize);
                logDebug("Updated force recognize UI: " + forceRecognize);
            }
            
            // Update refresh rate preference
            if (mRefreshRatePref != null && !refreshRate.equals(mRefreshRatePref.getValue())) {
                mRefreshRatePref.setValue(refreshRate);
                updateRefreshRateSummary(refreshRate);
                logDebug("Updated refresh rate UI: " + refreshRate);
            }
            
            // Update footer
            updateFooterInfo();
            
        } catch (Exception e) {
            logError("Error refreshing UI: " + e.getMessage());
        }
    }

    /**
     * Update the refresh rate preference summary
     */
    private void updateRefreshRateSummary(String rateValue) {
        if (mRefreshRatePref == null) return;
        
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

    /**
     * Update the footer preference with current status
     */
    private void updateFooterInfo() {
        if (mFooterPref == null) return;
        
        try {
            boolean stylusModeEnabled = mStylusPreference.getBoolean(STYLUS_MODE_KEY, false);
            boolean penModeActive = PenUtils.isPenModeEnabled();
            boolean penConnected = PenUtils.isPenConnected();
            boolean forceRecognize = mStylusPreference.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);
            String refreshRate = mStylusPreference.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
            
            StringBuilder info = new StringBuilder();
            
            // Original info text
            info.append(getString(R.string.stylus_more_info));
            info.append("\n\n");
            
            // Current status
            String statusText;
            if (!stylusModeEnabled) {
                statusText = getString(R.string.stylus_status_inactive) + " (Off)";
            } else if (penConnected) {
                statusText = getString(R.string.stylus_status_active) + " (Pen Connected)";
            } else if (forceRecognize) {
                statusText = getString(R.string.stylus_status_active) + " (Force Mode)";
            } else {
                statusText = getString(R.string.stylus_status_inactive) + " (No Pen)";
            }
            info.append(getString(R.string.stylus_footer_status, statusText));
            
            // Current refresh rate mode
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
            String appliedStatus = penModeActive ? "Applied" : "Saved";
            info.append(getString(R.string.stylus_footer_refresh_rate, rateText + " (" + appliedStatus + ")"));
            
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
