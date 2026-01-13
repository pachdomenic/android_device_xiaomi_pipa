package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "XiaomiLidBoot";
    private static final String LID_KEY = "lid_switch_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean lidEnabled = prefs.getBoolean(LID_KEY, true);
            
            Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.LID_BEHAVIOR, lidEnabled ? 1 : 0);
            
            Log.i(TAG, "Boot completed - set lid_behavior to: " + (lidEnabled ? 1 : 0));
        }
    }
}
