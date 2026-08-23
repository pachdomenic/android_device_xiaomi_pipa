/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Persistent Background Service managing the iOS-inspired stylus charging experience:
 * 1. On Connect / Attach: Triggers charging popup + ongoing notification.
 * 2. On Detach / Remove: Triggers disconnected popup + cleans up notification.
 * 3. On 100% Full Charge: Triggers fully charged popup (once per cycle).
 * 4. On Significant Battery Update: Updates popup in-place without duplicating windows.
 * 5. On Low/Critical Battery: Alerts with warning popup.
 * 6. On Manual Broadcast: Handles ACTION_SHOW_STYLUS_POPUP for testing and status checks.
 */
public class PenChargingService extends Service {

    private static final String TAG = "PenChargingService";
    private static final String CHANNEL_ID = "pen_charging_status";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_SHOW_STYLUS_POPUP =
            "org.lineageos.xiaomiperipheralmanager.ACTION_SHOW_STYLUS_POPUP";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_BATTERY_LEVEL = "battery_level";
    public static final String EXTRA_DEVICE_NAME = "device_name";

    private static final long POLL_INTERVAL_ACTIVE_MS = 1200L; // 1.2s snappy polling while screen is on
    private static final long NINETY_NINE_TO_FULL_MS = 60000L; // 1 minute
    private static final int BATTERY_LEVEL_UNKNOWN = Integer.MIN_VALUE;

    public static final String PREF_STYLUS_CHARGING_NOTIF = "stylus_charging_notification_key";

    private NotificationManager mNotificationManager;
    private PowerManager mPowerManager;
    private SharedPreferences mPreferences;
    private StylusPillController mPillController;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsMonitoring = false;
    private boolean mWasCharging = false;
    private boolean mHasShown100PercentPopup = false;
    private boolean mHasWarnedLow = false;
    private boolean mHasWarnedCritical = false;
    private int mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
    private Long mFirstSeen99AtMs = null;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsMonitoring) return;
            updateChargingStatus();
            mHandler.postDelayed(this, POLL_INTERVAL_ACTIVE_MS);
        }
    };

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (PenChargingManager.DEBUG) Log.d(TAG, "Screen turned ON, starting active monitoring");
                startMonitoring();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (PenChargingManager.DEBUG) Log.d(TAG, "Screen turned OFF, pausing monitoring");
                stopMonitoring();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                handleBluetoothConnected(intent);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) ||
                       BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED.equals(action)) {
                updateChargingStatus();
            } else if (ACTION_SHOW_STYLUS_POPUP.equals(action)) {
                handleManualPopupRequest(intent);
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (PREF_STYLUS_CHARGING_NOTIF.equals(key)) {
                        boolean enabled = prefs.getBoolean(key, true);
                        if (!enabled) {
                            hideNotification();
                            mWasCharging = false;
                            mHasShown100PercentPopup = false;
                            mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
                            mFirstSeen99AtMs = null;
                        } else {
                            if (mPowerManager != null && mPowerManager.isInteractive()) {
                                updateChargingStatus();
                            }
                        }
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);

        createNotificationChannel();
        mPillController = new StylusPillController(this);

        registerReceivers();

        if (mPowerManager != null && mPowerManager.isInteractive()) {
            startMonitoring();
        }

        Log.i(TAG, "PenChargingService initialized with iOS-inspired charging controller");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_STYLUS_POPUP.equals(intent.getAction())) {
            handleManualPopupRequest(intent);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        }
        try {
            unregisterReceiver(mStateReceiver);
        } catch (Exception ignored) {}
        Log.i(TAG, "PenChargingService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (mNotificationManager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pen_charging_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.pen_charging_channel_description));
        channel.setShowBadge(false);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        filter.addAction(ACTION_SHOW_STYLUS_POPUP);
        registerReceiver(mStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void startMonitoring() {
        if (!mIsMonitoring && PenChargingManager.isAvailable()) {
            mIsMonitoring = true;
            mHandler.post(mPollRunnable);
        }
    }

    private void stopMonitoring() {
        if (mIsMonitoring) {
            mIsMonitoring = false;
            mHandler.removeCallbacks(mPollRunnable);
            hideNotification();
            mPillController.hidePill();
            mWasCharging = false;
            mHasShown100PercentPopup = false;
            mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
            mFirstSeen99AtMs = null;
        }
    }

    private void handleBluetoothConnected(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        String name = (device != null && device.getName() != null) ? device.getName() : null;
        PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);
        int level = (status != null && status.batteryLevel >= 0) ? status.batteryLevel : -1;

        if (status != null && status.isCharging) {
            mPillController.showPopup(level >= 100 ? StylusState.FULLY_CHARGED : StylusState.CHARGING, level, name);
        } else {
            mPillController.showPopup(StylusState.CONNECTED, level, name);
        }
    }

    private void handleManualPopupRequest(Intent intent) {
        String stateStr = intent.getStringExtra(EXTRA_STATE);
        int batteryLevel = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1);
        String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);

        StylusState state;
        if (stateStr != null) {
            try {
                state = StylusState.valueOf(stateStr.toUpperCase());
            } catch (Exception e) {
                state = StylusState.CHARGING;
            }
        } else {
            PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);
            if (status != null && status.isCharging) {
                state = status.batteryLevel >= 100 ? StylusState.FULLY_CHARGED : StylusState.CHARGING;
                batteryLevel = status.batteryLevel;
            } else if (status != null && status.isConnected) {
                state = StylusState.CONNECTED;
                batteryLevel = status.batteryLevel;
            } else {
                state = StylusState.DISCONNECTED;
            }
        }

        Log.i(TAG, "Handling manual popup request: state=" + state + ", level=" + batteryLevel);
        mPillController.showPopup(state, batteryLevel, deviceName);
    }

    private void updateChargingStatus() {
        PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);
        if (status == null || !status.isConnected || !status.isCharging) {
            if (mWasCharging) {
                Log.i(TAG, "Stylus detached -> Triggering disconnected popup and removing notification");
                mPillController.showPopup(StylusState.DISCONNECTED, mLastBatteryLevel, null);
                hideNotification();
                mWasCharging = false;
                mHasShown100PercentPopup = false;
                mHasWarnedLow = false;
                mHasWarnedCritical = false;
                mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
                mFirstSeen99AtMs = null;
            }
            return;
        }

        // Pen is actively connected and charging
        int rawLevel = -1;
        if (status.batteryLevel >= 0 && status.batteryLevel <= 100) {
            rawLevel = status.batteryLevel;
        }

        int displayLevel;
        if (rawLevel == 99) {
            long now = SystemClock.elapsedRealtime();
            if (mFirstSeen99AtMs == null) mFirstSeen99AtMs = now;
            if (now - mFirstSeen99AtMs >= NINETY_NINE_TO_FULL_MS) {
                displayLevel = 100;
            } else {
                displayLevel = 99;
            }
        } else {
            mFirstSeen99AtMs = null;
            displayLevel = rawLevel;
        }

        boolean justAttached = !mWasCharging;
        mWasCharging = true;

        if (justAttached) {
            Log.i(TAG, "Stylus attached -> Triggering iOS floating charging popup (" +
                    (displayLevel >= 0 ? displayLevel + "%" : "unknown SoC") + ")");
            mLastBatteryLevel = displayLevel;
            StylusState state = (displayLevel >= 100) ? StylusState.FULLY_CHARGED : StylusState.CHARGING;
            mPillController.showPopup(state, displayLevel, null);

            if (displayLevel >= 100) {
                mHasShown100PercentPopup = true;
            }
            showNotification(displayLevel);
        } else {
            // Charging progress updates while remaining attached
            if (displayLevel < 100 && displayLevel >= 0) {
                mHasShown100PercentPopup = false;
            } else if (displayLevel == 100) {
                // Trigger 100% full charge popup once per cycle
                if (!mHasShown100PercentPopup) {
                    mHasShown100PercentPopup = true;
                    Log.i(TAG, "Stylus reached 100% -> Triggering full charge popup");
                    mPillController.showPopup(StylusState.FULLY_CHARGED, 100, null);
                    mHandler.post(() -> Toast.makeText(getApplicationContext(),
                            R.string.pen_fully_charged_toast,
                            Toast.LENGTH_SHORT).show());
                }
            }

            if (displayLevel != mLastBatteryLevel) {
                // Significant battery level jump update in place
                if (mLastBatteryLevel >= 0 && Math.abs(displayLevel - mLastBatteryLevel) >= 5) {
                    mPillController.showPopup(displayLevel >= 100 ? StylusState.FULLY_CHARGED : StylusState.CHARGING,
                            displayLevel, null);
                }

                mLastBatteryLevel = displayLevel;
                showNotification(displayLevel);
            }
        }
    }

    private void showNotification(int batteryLevel) {
        boolean notifEnabled = mPreferences == null || mPreferences.getBoolean(PREF_STYLUS_CHARGING_NOTIF, true);
        if (!notifEnabled || mNotificationManager == null) {
            hideNotification();
            return;
        }

        String title;
        String content;
        if (batteryLevel >= 100) {
            title = getString(R.string.pen_charging_notification_title_full);
            content = getString(R.string.pen_charging_notification_content_full);
        } else if (batteryLevel >= 0) {
            title = getString(R.string.pen_charging_notification_title);
            content = getString(R.string.pen_charging_notification_content, batteryLevel);
        } else {
            title = getString(R.string.pen_charging_notification_title);
            content = getString(R.string.pen_charging_notification_content_unknown);
        }

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stylus_charging)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void hideNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }

    public static void start(Context context) {
        if (PenChargingManager.isAvailable()) {
            Intent intent = new Intent(context, PenChargingService.class);
            context.startService(intent);
            Log.i(TAG, "Starting PenChargingService");
        } else {
            Log.w(TAG, "Wireless charging hardware not available, skipping PenChargingService");
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PenChargingService.class));
    }
}
