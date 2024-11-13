package org.lineageos.xiaomiperipheralmanager;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.lineageos.xiaomiperipheralmanager.RefreshUtils;

public class PenEventService extends Service {

    private Handler mHandler;
    private RefreshUtils mRefreshUtils;
    private static final String TAG = "PenEventService";
    private static final boolean DEBUG = true;
    private static final String PEN_INPUT_PATH = "/dev/input/event2"; // Path to the pen input device
    private boolean isRunning = false;
    private static final int DEBOUNCE_DELAY = 300; // ms
    private boolean penAttached = false;
    private boolean penStatePending = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        mRefreshUtils = new RefreshUtils(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        if (DEBUG) Log.d(TAG, "Starting service");
        new Thread(new Runnable() {
            @Override
            public void run() {
                startListeningForPenEvents();
            }
        }).start();
        return START_STICKY;
    }

    private void startListeningForPenEvents() {
        if (DEBUG) Log.d(TAG, "Starting to listen for pen events " + String.valueOf(isRunning));
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(PEN_INPUT_PATH);
            while (isRunning) {
                byte[] buffer = new byte[48];
                int bytesRead = inputStream.read(buffer);

                if (DEBUG) Log.d(TAG, "bytes read --> " + String.valueOf(bytesRead));

                if (bytesRead > 0) {
                    processPenEvent(buffer);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while reading pen input", e);
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
        }
    }

    private void processPenEvent(byte[] buffer) {
        if (buffer.length < 48) {
            Log.e(TAG, "Unexpected buffer size: " + buffer.length);
            return;
        }

        if (DEBUG) Log.d(TAG, "Raw buffer data: " + buffer);

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        // Pen attachment state is derived from byte 20
        // TODO: Deconstruct bytes based on event datatypes and extract event reliably
        boolean currentPenAttached = byteBuffer.get(20) != 0;

        if (DEBUG) Log.d(TAG, "Current Pen Attached: " + String.valueOf(currentPenAttached));

        if (!penStatePending) {
            penStatePending = true;

            // A simple debounce
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    confirmPenState(currentPenAttached);
                }
            }, DEBOUNCE_DELAY);
        }
    }

    private void confirmPenState(boolean currentPenAttached) {
        if (penAttached == currentPenAttached) {
            penStatePending = false;
            return;
        }
        penAttached = currentPenAttached;

        if (penAttached) {
            showToast("Pen Attached");
            mRefreshUtils.setDefaultRefreshRate();
        } else {
            showToast("Pen Detached");
            mRefreshUtils.setPenRefreshRate();
        }

        penStatePending = false;
    }

    private void showToast(final String message) {
        Handler handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PenEventService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
