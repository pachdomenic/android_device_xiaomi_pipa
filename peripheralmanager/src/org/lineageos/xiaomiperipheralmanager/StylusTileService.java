/*
 * Copyright (C) 2025 The LineageOS Project
 * Copyright (C) 2025 SheoranPranshu
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StylusTileService extends TileService {

    private static final String TAG = "XiaomiStylusTile";
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";
    
    private Context mContext;
    private SharedPreferences mPreferences;
    private Tile mTile;
    
    // Current state cache
    private boolean mStylusModeEnabled = false;
    private String mRefreshRateMode = "dynamic";

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        logInfo("Stylus tile service created");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mTile = getQsTile();
        if (mTile != null) {
            syncFromSettings();
            updateTileView();
            logDebug("Tile listening started");
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        logDebug("Tile listening stopped");
    }

    @Override
    public void onClick() {
        if (mTile == null) {
            logError("Tile is null in onClick");
            return;
        }

        // Toggle the state
        mStylusModeEnabled = !mStylusModeEnabled;
        
        // Update tile UI IMMEDIATELY (before any async operations)
        updateTileView();
        
        // Now persist the changes
        mPreferences.edit().putBoolean(STYLUS_MODE_KEY, mStylusModeEnabled).apply();
        
        // Apply the mode
        try {
            PenUtils.onStylusModeChanged(mStylusModeEnabled);
            
            if (mStylusModeEnabled) {
                // Apply saved refresh rate when enabling
                PenUtils.setRefreshRateMode(mRefreshRateMode);
                logInfo("Stylus mode enabled with refresh rate: " + mRefreshRateMode);
            } else {
                logInfo("Stylus mode disabled");
            }
        } catch (Exception e) {
            logError("Error applying stylus mode: " + e.getMessage());
        }
        
        // Broadcast to update settings page if it's open
        sendBroadcast(new Intent("org.lineageos.xiaomiperipheralmanager.STYLUS_MODE_CHANGED"));
    }

    /**
     * Sync state from SharedPreferences
     * This is called when tile starts listening
     */
    private void syncFromSettings() {
        try {
            mStylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
            mRefreshRateMode = mPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
            logDebug("Synced from settings - Mode: " + mStylusModeEnabled + ", Rate: " + mRefreshRateMode);
        } catch (Exception e) {
            logError("Error syncing from settings: " + e.getMessage());
        }
    }

    /**
     * Update the tile UI based on current state
     * This is called immediately after state changes
     */
    private void updateTileView() {
        if (mTile == null) return;

        try {
            // Set label
            mTile.setLabel(getString(R.string.stylus_tile_label));
            
            // Format subtitle based on refresh rate mode
            String subtitle = getRefreshRateDisplayText(mRefreshRateMode);
            
            // Update tile state and subtitle
            if (mStylusModeEnabled) {
                mTile.setState(Tile.STATE_ACTIVE);
                mTile.setSubtitle(subtitle);
            } else {
                mTile.setState(Tile.STATE_INACTIVE);
                mTile.setSubtitle(subtitle);
            }
            
            // Apply changes immediately
            mTile.updateTile();
            
            logDebug("Tile updated - State: " + (mStylusModeEnabled ? "Active" : "Inactive") + 
                    ", Mode: " + subtitle);
        } catch (Exception e) {
            logError("Error updating tile: " + e.getMessage());
        }
    }

    /**
     * Convert refresh rate mode to display text
     */
    private String getRefreshRateDisplayText(String mode) {
        switch (mode) {
            case "60":
                return "60Hz";
            case "120":
                return "120Hz";
            case "dynamic":
            default:
                return "Dynamic";
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
