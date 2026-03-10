/*
 * Copyright (C) 2025-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

/**
 * Quick Settings tile for Stylus Mode.
 */
public class StylusTileService extends TileService {

    private static final String TAG = "XiaomiStylusTile";

    static final String ACTION_STYLUS_CHANGED =
            "org.lineageos.xiaomiperipheralmanager.STYLUS_MODE_CHANGED";

    private static final String STYLUS_MODE_KEY         = "stylus_mode_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    // Used only for the deferred retry in onStartListening.
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Receiver for real-time updates originating from StylusSettingsFragment while QS is open.
    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_STYLUS_CHANGED.equals(intent.getAction())) return;
            boolean active = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(STYLUS_MODE_KEY, false);
            Log.d(TAG, "LocalBroadcast — stylus=" + active);
            updateTile(active);
        }
    };

    // -----------------------------------------------------------------------
    // TileService lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onStartListening() {
        super.onStartListening();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mSettingsReceiver, new IntentFilter(ACTION_STYLUS_CHANGED));

        boolean active = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(STYLUS_MODE_KEY, false);
        Log.d(TAG, "onStartListening — stylus=" + active
                + ", tile=" + (getQsTile() != null ? "ok" : "null"));

        // Direct call — getQsTile() is documented non-null here.
        updateTile(active);

        final boolean activeFinal = active;
        mHandler.postDelayed(() -> updateTile(activeFinal), 350);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mHandler.removeCallbacksAndMessages(null);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSettingsReceiver);
    }

    // -----------------------------------------------------------------------
    // User interaction
    // -----------------------------------------------------------------------

    @Override
    public void onClick() {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "onClick — tile is null");
            return;
        }

        final boolean newState = (tile.getState() != Tile.STATE_ACTIVE);
        Log.d(TAG, "onClick — newState=" + newState);

        // Immediate visual feedback before any I/O.
        updateTile(newState);

        // Read refresh rate before persisting the new stylus state.
        final String refreshRate = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(STYLUS_REFRESH_RATE_KEY, "dynamic");

        // Persist the new state asynchronously.
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(STYLUS_MODE_KEY, newState)
                .apply();

        // PenUtils operations (sysprop write + input device iteration) are fast
        // and safe to run on the main thread.
        PenUtils.onStylusModeChanged(newState);
        if (newState) PenUtils.setRefreshRateMode(refreshRate);

        // Notify the settings page if it is currently open.
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_STYLUS_CHANGED));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    /**
     * Sets tile state and icon atomically to match {@code active}.
     * Falls back gracefully if the tile is transiently null.
     */
    private void updateTile(boolean active) {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "updateTile — tile is null (skipping)");
            return;
        }
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this,
                active ? R.drawable.ic_stylus_tile
                       : R.drawable.ic_stylus_tile_off));
        tile.updateTile();
    }
}
