package com.edom.alarm;

import android.app.Application;
import android.content.Context;
import android.os.Build;

/**
 * Main application context for Android Smart Alarm.
 * Initializes device-protected storage for Direct Boot compatibility.
 */
public class AlarmApplication extends Application {

    private static AlarmApplication sInstance;
    private Context mDeviceProtectedContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        // Initialize Direct Boot protected context for accessing alarms before first unlock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mDeviceProtectedContext = createDeviceProtectedStorageContext();
        } else {
            mDeviceProtectedContext = this;
        }
    }

    public static AlarmApplication getInstance() {
        return sInstance;
    }

    public Context getProtectedStorageContext() {
        return mDeviceProtectedContext != null ? mDeviceProtectedContext : this;
    }
}
