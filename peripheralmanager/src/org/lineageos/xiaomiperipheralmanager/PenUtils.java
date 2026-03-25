/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.os.SystemProperties;
import android.util.Log;
import android.view.InputDevice;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;

public class PenUtils {

    private static final String TAG = "XiaomiPeripheralManagerPenUtils";
    private static final boolean DEBUG = false;

    private static final int penVendorId = 6421;
    private static final int penProductId = 19841;

    private static InputManager mInputManager;

    private static final String STYLUS_KEY = "stylus_switch_key";
    // Match keys used in StylusSettingsFragment / XML
    private static final String STYLUS_MODE_KEY = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_STYLUS_KEY = "force_recognize_stylus_key";

    private static SharedPreferences preferences;
    private static RefreshUtils mRefreshUtils;

    public static void setup(Context context) {
        mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        mInputManager.registerInputDeviceListener(mInputDeviceListener, null);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        mRefreshUtils = new RefreshUtils(context);
        refreshPenMode();
    }

    public static void enablePenMode() {
        Log.d(TAG, "enablePenMode: Enable Pen Mode");
        SystemProperties.set("persist.vendor.parts.pen", "18");
        // Bump trigger so init can re-run xiaomi-pen even if value is unchanged
        int seq = SystemProperties.getInt("sys.vendor.parts.pen_trigger", 0);
        SystemProperties.set("sys.vendor.parts.pen_trigger", Integer.toString(seq + 1));
        Log.d(TAG, "enablePenMode: Setting Refresh Rates for Pen");
    }

    public static void disablePenMode() {
        Log.d(TAG, "disablePenMode: Disable Pen Mode");
        SystemProperties.set("persist.vendor.parts.pen", "2");
        int seq = SystemProperties.getInt("sys.vendor.parts.pen_trigger", 0);
        SystemProperties.set("sys.vendor.parts.pen_trigger", Integer.toString(seq + 1));
        Log.d(TAG, "disablePenMode: Resetting Refresh Rate Values");
    }

    private static void refreshPenMode() {
        // If user explicitly enabled "Force recognize stylus" in settings,
        // keep pen mode enabled regardless of which input devices are present.
        boolean stylusModeEnabled = preferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceRecognizeEnabled = preferences.getBoolean(FORCE_RECOGNIZE_STYLUS_KEY, false);

        if (stylusModeEnabled && forceRecognizeEnabled) {
            if (DEBUG) Log.d(TAG, "refreshPenMode: force-recognize stylus enabled, forcing pen mode ON");
            enablePenMode();
            return;
        }

        for (int id : mInputManager.getInputDeviceIds()) {
            if (isDeviceXiaomiPen(id) || preferences.getBoolean(STYLUS_KEY, false)) {
                if (DEBUG) Log.d(TAG, "refreshPenMode: Found Xiaomi Pen");
                enablePenMode();
                return;
            }
        }
        if (DEBUG) Log.d(TAG, "refreshPenMode: No Xiaomi Pen found");
        disablePenMode();
    }

    private static boolean isDeviceXiaomiPen(int id) {
        InputDevice inputDevice = mInputManager.getInputDevice(id);
        return inputDevice.getVendorId() == penVendorId &&
                inputDevice.getProductId() == penProductId;
    }

    private static InputDeviceListener mInputDeviceListener = new InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int id) {
                refreshPenMode();
            }
            @Override
            public void onInputDeviceRemoved(int id) {
                refreshPenMode();
            }
            @Override
            public void onInputDeviceChanged(int id) {
                refreshPenMode();
            }
        };
}
