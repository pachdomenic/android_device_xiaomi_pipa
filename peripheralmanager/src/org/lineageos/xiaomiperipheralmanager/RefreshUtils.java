package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

public final class RefreshUtils {
    private static final String TAG = "RefreshUtils";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PEN_MODE = "pen_mode";
    private static final String PREF_FILE_NAME = "pen_refresh_prefs";

    private static final boolean DEBUG = true;

    private Context mContext;
    private SharedPreferences mSharedPrefs;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }

    protected void setPenRefreshRate() {
        if (DEBUG) Log.d(TAG, "Setting pen Refresh Rate");
        boolean penMode = mSharedPrefs.getBoolean(KEY_PEN_MODE, false);
        if (DEBUG) Log.d(TAG, "setPenRefreshRate: penMode = " + penMode);

        float maxRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 144f);
        float minRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 144f);

        if (DEBUG) Log.d(TAG, "setPenRefreshRate: maxRate = " + maxRate + ", minRate = " + minRate);

        // Update default values in SharedPreferences
        mSharedPrefs.edit()
                .putFloat(KEY_MIN_REFRESH_RATE, minRate)
                .putFloat(KEY_PEAK_REFRESH_RATE, maxRate)
                .putBoolean(KEY_PEN_MODE, true)
                .apply();

        if (DEBUG) Log.d(TAG, "setPenRefreshRate: SharedPreferences updated with values ^^^.");

        // Ensure valid values for maxRate and minRate
        maxRate = (maxRate != 60) ? 120 : maxRate;
        minRate = (minRate <= 60) ? 60 : 120;

        if (DEBUG) Log.d(TAG, "setPenRefreshRate: Validated maxRate = " + maxRate + ", minRate = " + minRate);

        // Set the values in the Settings.System
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);

        if (DEBUG) Log.d(TAG, "setPenRefreshRate: Settings.System updated");
    }

    protected void setDefaultRefreshRate() {
        if (DEBUG) Log.d(TAG, "Setting default Refresh Rate");
        float defaultMinRate = mSharedPrefs.getFloat(KEY_MIN_REFRESH_RATE, 144f);
        float defaultMaxRate = mSharedPrefs.getFloat(KEY_PEAK_REFRESH_RATE, 144f);

        if (DEBUG) Log.d(TAG, "setDefaultRefreshRate: defaultMinRate = " + defaultMinRate + ", defaultMaxRate = " + defaultMaxRate);

        mSharedPrefs.edit().putBoolean(KEY_PEN_MODE, false).apply();
        if (DEBUG) Log.d(TAG, "setDefaultRefreshRate: SharedPreferences updated to disable pen mode");
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, defaultMinRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, defaultMaxRate);
    }
}
