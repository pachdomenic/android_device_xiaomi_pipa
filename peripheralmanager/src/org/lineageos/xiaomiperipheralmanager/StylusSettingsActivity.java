/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.os.Bundle;
import android.util.Log;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Settings activity for stylus/pen configuration
 * Hosts the StylusSettingsFragment for user configuration
 */
public class StylusSettingsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = "XiaomiPenSettings";
    private static final String TAG_STYLUS = "stylus";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "Opening stylus settings");
        
        getFragmentManager().beginTransaction().replace(
            com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                new StylusSettingsFragment(), TAG_STYLUS).commit();
    }
}
