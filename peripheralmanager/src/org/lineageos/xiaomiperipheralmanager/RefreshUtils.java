package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.database.ContentObserver;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;

public final class RefreshUtils {
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PEN_MODE = "pen_mode";
    private static final String PREF_FILE_NAME = "pen_refresh_prefs";

    private Context mContext;
    private SharedPreferences mSharedPrefs;
    private final ContentObserver mPeakObserver;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);

        mPeakObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                // Pen mode is considered active when this property is 18
                boolean penActive =
                        "18".equals(SystemProperties.get("persist.vendor.parts.pen", "2"));
                if (penActive) {
                    // This will clamp to 120 Hz if current max > 120
                    setPenRefreshRate();
                }
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(KEY_PEAK_REFRESH_RATE),
                false,
                mPeakObserver
        );
    }

    protected void setPenRefreshRate() {
        float maxRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 144f);
        float minRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 60f);

        if (maxRate <= 120f) {
            return;
        }

        boolean penMode = mSharedPrefs.getBoolean(KEY_PEN_MODE, false);

        if (!penMode) {
            mSharedPrefs.edit()
                    .putFloat(KEY_MIN_REFRESH_RATE, minRate)
                    .putFloat(KEY_PEAK_REFRESH_RATE, maxRate)
                    .putBoolean(KEY_PEN_MODE, true)
                    .apply();
        }

        // Always clamp when we're called and maxRate > 120
        maxRate = 120f;
        minRate = Math.min(minRate, 120f);

        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
    }

    protected void setDefaultRefreshRate() {
        float currentMinRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 60f);
        float currentMaxRate = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 60f);

        // This means that smooth display is off and so we stay in 60hz refresh rate
        if (currentMaxRate <= 60f) {
            mSharedPrefs.edit()
                    .putBoolean(KEY_PEN_MODE, false)
                    // keep our snapshot in sync with what the user chose
                    .putFloat(KEY_MIN_REFRESH_RATE, currentMinRate)
                    .putFloat(KEY_PEAK_REFRESH_RATE, currentMaxRate)
                    .apply();
            return;
        }

        // If we are here it means that smooth display was on andrestore what we saved when entering pen mode
        float defaultMinRate = mSharedPrefs.getFloat(KEY_MIN_REFRESH_RATE, currentMinRate);
        float defaultMaxRate = mSharedPrefs.getFloat(KEY_PEAK_REFRESH_RATE, currentMaxRate);

        mSharedPrefs.edit().putBoolean(KEY_PEN_MODE, false).apply();

        // Set the values in the Settings.System directly
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, defaultMinRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, defaultMaxRate);
    }
}
