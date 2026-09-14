/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;

public class CrashlyticsUtils {

    public static boolean isGooglePlayServicesAvailable(Context context) {
        try {
            if (context == null) {
                return false;
            }
            GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
            if (googleApiAvailability == null) {
                return false;
            }
            int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
            return resultCode == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static String getPerformanceClassString() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return "Low";
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return "Average";
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return "High";
            default:
                return "N/A";
        }
    }

    public static void logEvents(Context context) {
        try {
            if (ApplicationLoader.getFirebaseAnalytics() == null) {
                return;
            }
            Bundle params = new Bundle();
            params.putString("android_version", Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "unknown");
            params.putString("version", BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME : "unknown");
            params.putInt("version_code", BuildConfig.VERSION_CODE);
            params.putBoolean("has_play_services", context != null && isGooglePlayServicesAvailable(context));
            try {
                String manufacturer = Build.MANUFACTURER != null ? LocaleUtils.capitalize(Build.MANUFACTURER) : "";
                String model = Build.MODEL != null ? Build.MODEL : "";
                params.putString("device", (manufacturer + " " + model).trim());
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("device", "unknown");
            }
            params.putString("performance_class", getPerformanceClassString());
            try {
                String locale = LocaleController.getSystemLocaleStringIso639();
                params.putString("locale", locale != null ? locale : "unknown");
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("locale", "unknown");
            }
            try {
                var cacheDir = AndroidUtilities.getCacheDir();
                params.putString("cache_path", cacheDir != null ? cacheDir.getAbsolutePath() : "unknown");
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("cache_path", "unknown");
            }
            params.putInt("refresh_rate", (int) AndroidUtilities.screenRefreshRate);
            try {
                if (AndroidUtilities.displaySize != null) {
                    params.putString("display", AndroidUtilities.displaySize.x + "x" + AndroidUtilities.displaySize.y);
                } else {
                    params.putString("display", "unknown");
                }
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("display", "unknown");
            }
            params.putBoolean("debug_build", BuildVars.isBetaApp());
            ApplicationLoader.getFirebaseAnalytics().logEvent("stats", params);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
