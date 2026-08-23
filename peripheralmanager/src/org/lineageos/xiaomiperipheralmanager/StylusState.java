/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.graphics.Color;

/**
 * UI State Model for the iOS-inspired Stylus Charging Popup.
 */
public enum StylusState {
    CHARGING(3500L),
    FULLY_CHARGED(4000L),
    CONNECTED(2800L),
    DISCONNECTED(2500L),
    LOW_BATTERY(4500L),
    CRITICAL_BATTERY(5000L),
    UNKNOWN(3500L);

    private final long mTimeoutMs;

    StylusState(long timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    public long getTimeoutMs() {
        return mTimeoutMs;
    }

    public String getTitle(Context context, String customName) {
        String name = (customName != null && !customName.isEmpty()) ? customName : context.getString(R.string.stylus_title);
        switch (this) {
            case FULLY_CHARGED:
                return context.getString(R.string.stylus_card_title_charged);
            case CHARGING:
                return context.getString(R.string.stylus_card_title_charging);
            case LOW_BATTERY:
                return context.getString(R.string.stylus_card_title_low_battery);
            case CRITICAL_BATTERY:
                return context.getString(R.string.stylus_card_title_critical_battery);
            case DISCONNECTED:
                return context.getString(R.string.stylus_card_title_disconnected);
            case CONNECTED:
            case UNKNOWN:
            default:
                return name;
        }
    }

    public String getSubtitle(Context context) {
        switch (this) {
            case FULLY_CHARGED:
                return context.getString(R.string.stylus_card_sub_charged);
            case CHARGING:
                return context.getString(R.string.stylus_card_sub_charging);
            case LOW_BATTERY:
                return context.getString(R.string.stylus_card_sub_low_battery);
            case CRITICAL_BATTERY:
                return context.getString(R.string.stylus_card_sub_critical_battery);
            case DISCONNECTED:
                return context.getString(R.string.stylus_card_sub_disconnected);
            case CONNECTED:
                return context.getString(R.string.stylus_card_sub_connected);
            case UNKNOWN:
            default:
                return context.getString(R.string.pen_charging_notification_content_unknown);
        }
    }

    public int getAccentColor() {
        switch (this) {
            case FULLY_CHARGED:
            case CHARGING:
                return Color.parseColor("#30D158"); // Apple Green
            case LOW_BATTERY:
                return Color.parseColor("#FF9F0A"); // Apple Orange
            case CRITICAL_BATTERY:
                return Color.parseColor("#FF453A"); // Apple Red
            case CONNECTED:
                return Color.parseColor("#0A84FF"); // Apple Blue
            case DISCONNECTED:
            case UNKNOWN:
            default:
                return Color.parseColor("#8E8E93"); // Neutral Gray
        }
    }
}
