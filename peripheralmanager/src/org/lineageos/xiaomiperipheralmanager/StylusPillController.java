/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Controller managing the Material 3 Expressive stylus charging pill overlay.
 * Handles WindowManager injection, spring driven enter/exit, in-place state transitions,
 * haptic feedback and auto-dismissal timeouts.
 */
public class StylusPillController {

    private static final String TAG = "StylusPillController";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Vibrator mVibrator;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private StylusChargingPillView mPillView;
    private StylusChargingPillView.StylusPopupState mLastState;

    private final Runnable mDismissRunnable = this::hidePill;

    public StylusPillController(Context context) {
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        mVibrator = context.getSystemService(Vibrator.class);
    }

    /**
     * Shows or updates the floating charging pill for a given StylusState.
     *
     * @param state        Current state (CHARGING, FULLY_CHARGED, CONNECTED, DISCONNECTED, etc.)
     * @param batteryLevel Battery percentage (0-100, or -1 if unknown)
     * @param customName   Device name, or null for default
     */
    public void showPopup(StylusState state, int batteryLevel, String customName) {
        mMainHandler.post(() -> {
            mMainHandler.removeCallbacks(mDismissRunnable);

            StylusChargingPillView.StylusPopupState popupState = mapState(state);
            String title = state.getTitle(mContext, customName);
            String subtitle = state.getSubtitle(mContext);
            Integer percent = (batteryLevel >= 0 && batteryLevel <= 100) ? batteryLevel : null;

            StylusChargingPillView.StylusPopupModel model =
                    new StylusChargingPillView.StylusPopupModel(title, subtitle, percent, popupState);

            boolean stateChanged = popupState != mLastState;
            mLastState = popupState;

            // Reuse the visible pill (even mid-exit) and morph it to the new state
            if (mPillView != null && mPillView.isAttachedToWindow()) {
                if (mPillView.isExiting()) {
                    mPillView.cancelExit();
                }
                mPillView.setModel(model, true);
                if (stateChanged) performHaptic(popupState);
                announceAccessibility(model);
                mMainHandler.postDelayed(mDismissRunnable, state.getTimeoutMs());
                return;
            }

            StylusChargingPillView view = new StylusChargingPillView(mContext);
            view.setModel(model, false);

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    StylusChargingPillView.getWindowWidth(mContext),
                    StylusChargingPillView.getWindowHeight(mContext),
                    WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            // The window carries transparent padding for spring overshoot; align the capsule itself
            layoutParams.y = mContext.getResources().getDimensionPixelSize(R.dimen.stylus_card_top_margin)
                    - StylusChargingPillView.getWindowPadding(mContext);
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            layoutParams.windowAnimations = 0;
            layoutParams.setTitle("StylusChargingPill");

            try {
                mWindowManager.addView(view, layoutParams);
                mPillView = view;
                view.animateIn();
                performHaptic(popupState);
                announceAccessibility(model);
                mMainHandler.postDelayed(mDismissRunnable, state.getTimeoutMs());
            } catch (Exception e) {
                Log.e(TAG, "Failed to add stylus pill view to WindowManager", e);
                mPillView = null;
            }
        });
    }

    /**
     * Backward-compatible helper method.
     */
    public void showPill(int batteryLevel, boolean isFullyCharged) {
        StylusState state;
        if (isFullyCharged || batteryLevel >= 100) {
            state = StylusState.FULLY_CHARGED;
        } else if (batteryLevel >= 0) {
            state = StylusState.CHARGING;
        } else {
            state = StylusState.UNKNOWN;
        }
        showPopup(state, batteryLevel, null);
    }

    private StylusChargingPillView.StylusPopupState mapState(StylusState state) {
        if (state == null) return StylusChargingPillView.StylusPopupState.UNKNOWN;
        switch (state) {
            case CHARGING:
                return StylusChargingPillView.StylusPopupState.CHARGING;
            case FULLY_CHARGED:
                return StylusChargingPillView.StylusPopupState.FULLY_CHARGED;
            case CONNECTED:
                return StylusChargingPillView.StylusPopupState.CONNECTED;
            case DISCONNECTED:
                return StylusChargingPillView.StylusPopupState.DISCONNECTED;
            case LOW_BATTERY:
                return StylusChargingPillView.StylusPopupState.LOW_BATTERY;
            case CRITICAL_BATTERY:
                return StylusChargingPillView.StylusPopupState.CRITICAL_BATTERY;
            case UNKNOWN:
            default:
                return StylusChargingPillView.StylusPopupState.UNKNOWN;
        }
    }

    private void performHaptic(StylusChargingPillView.StylusPopupState state) {
        if (mVibrator == null || !mVibrator.hasVibrator()) return;

        VibrationEffect.Composition composition = VibrationEffect.startComposition();
        int[] primitives;
        switch (state) {
            case CHARGING:
                primitives = new int[] { VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK };
                composition.addPrimitive(primitives[0], 0.5f)
                        .addPrimitive(primitives[1], 0.7f, 40);
                break;
            case FULLY_CHARGED:
                primitives = new int[] { VibrationEffect.Composition.PRIMITIVE_TICK };
                composition.addPrimitive(primitives[0], 0.6f)
                        .addPrimitive(primitives[0], 0.9f, 70);
                break;
            case CRITICAL_BATTERY:
            case LOW_BATTERY:
                primitives = new int[] { VibrationEffect.Composition.PRIMITIVE_THUD };
                composition.addPrimitive(primitives[0], 0.6f);
                break;
            case DISCONNECTED:
                primitives = new int[] { VibrationEffect.Composition.PRIMITIVE_QUICK_FALL };
                composition.addPrimitive(primitives[0], 0.4f);
                break;
            default:
                primitives = new int[] { VibrationEffect.Composition.PRIMITIVE_LOW_TICK };
                composition.addPrimitive(primitives[0], 0.5f);
                break;
        }

        try {
            if (mVibrator.areAllPrimitivesSupported(primitives)) {
                mVibrator.vibrate(composition.compose());
            } else {
                mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to perform haptic feedback", e);
        }
    }

    private void announceAccessibility(StylusChargingPillView.StylusPopupModel model) {
        if (mPillView != null) {
            String battery = model.batteryPercent != null ? (model.batteryPercent + "%") : "";
            mPillView.announceForAccessibility(model.title + ", " + model.subtitle + " " + battery);
        }
    }

    /**
     * Collapses the pill back into a circle, fades it out and removes it from WindowManager.
     */
    public void hidePill() {
        mMainHandler.post(() -> {
            mMainHandler.removeCallbacks(mDismissRunnable);
            StylusChargingPillView view = mPillView;
            if (view == null || view.isExiting()) return;
            mLastState = null;

            view.animateOut(() -> {
                try {
                    if (view.isAttachedToWindow()) {
                        mWindowManager.removeViewImmediate(view);
                    }
                } catch (Exception ignored) {}
                if (mPillView == view) {
                    mPillView = null;
                }
            });
        });
    }
}
