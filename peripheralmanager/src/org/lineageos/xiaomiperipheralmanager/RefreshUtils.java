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

    private Context mContext;
    private SharedPreferences mSharedPrefs;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }

    protected void setPenRefreshRate() {
        boolean penMode = mSharedPrefs.getBoolean(KEY_PEN_MODE, false);

        if (!penMode) {
            // Save current user settings before changing them
            float maxRate = Settings.System.getFloat(mContext.getContentResolver(), 
                    KEY_PEAK_REFRESH_RATE, 144f);
            float minRate = Settings.System.getFloat(mContext.getContentResolver(), 
                    KEY_MIN_REFRESH_RATE, 30f);

            Log.d(TAG, "Saving user settings - min: " + minRate + "Hz, max: " + maxRate + "Hz");

            // Save original values in SharedPreferences
            mSharedPrefs.edit()
                    .putFloat(KEY_MIN_REFRESH_RATE, minRate)
                    .putFloat(KEY_PEAK_REFRESH_RATE, maxRate)
                    .putBoolean(KEY_PEN_MODE, true)
                    .apply();
        }

        // Set pen mode refresh rates: min 60Hz, max 120Hz
        // Pen only works on 60Hz and 120Hz
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 60f);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 120f);
        
        Log.d(TAG, "Pen mode enabled - set min: 60Hz, max: 120Hz");
    }

    protected void setDefaultRefreshRate() {
        // Restore user's saved settings
        float defaultMinRate = mSharedPrefs.getFloat(KEY_MIN_REFRESH_RATE, 30f);
        float defaultMaxRate = mSharedPrefs.getFloat(KEY_PEAK_REFRESH_RATE, 144f);

        Log.d(TAG, "Restoring user settings - min: " + defaultMinRate + "Hz, max: " + defaultMaxRate + "Hz");

        mSharedPrefs.edit().putBoolean(KEY_PEN_MODE, false).apply();

        // Restore the original values
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, defaultMinRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, defaultMaxRate);
    }
}
