package com.exteragram.messenger.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

public class AyuDownloadEngine {

    public static final int MODE_AUTO = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_MAXIMUM = 2;
    public static final int MODE_CUSTOM = 3;

    public static final int ERROR_GENERIC = 0;
    public static final int ERROR_FLOOD = 1;
    public static final int ERROR_MIGRATE = 2;
    public static final int ERROR_CDN = 3;
    public static final int ERROR_TIMEOUT = 4;

    private static final String PREFS_NAME = "ayu_download_engine";

    private static final int MIN_CONNECTIONS_PER_DOWNLOAD = 2;
    private static final int MAX_CONNECTIONS_PER_DOWNLOAD = 16;
    private static final int MIN_TOTAL_CONNECTIONS = 4;
    private static final int MAX_TOTAL_CONNECTIONS = 32;

    private static final int LARGE_FILE_THRESHOLD = 100 * 1024 * 1024;
    private static final long STORAGE_SAFETY_MARGIN = 64L * 1024 * 1024;

    private static final int[] CHUNK_STEPS = {
            32 * 1024, 64 * 1024, 128 * 1024, 256 * 1024, 512 * 1024, 1024 * 1024
    };
    private static final int DEFAULT_CHUNK_INDEX = 2;
    private static final int DEFAULT_REQUESTS = 4;

    private static final int WINDOW_SIZE = 8;
    private static final long ADJUST_INTERVAL_MS = 2000;

    private static final Object sync = new Object();
    private static volatile boolean configLoaded;

    private static volatile boolean enabled;
    private static volatile int mode = MODE_AUTO;
    private static volatile boolean wifiAcceleration = true;
    private static volatile boolean mobileAcceleration;

    private static volatile int customMaxSimultaneous = 4;
    private static volatile int customConnectionsPerDownload = 8;
    private static volatile int customMaxTotalConnections = 16;

    private static volatile int chunkIndex = DEFAULT_CHUNK_INDEX;
    private static volatile int currentRequests = DEFAULT_REQUESTS;

    private static final long[] windowBytes = new long[WINDOW_SIZE];
    private static final long[] windowTimeMs = new long[WINDOW_SIZE];
    private static int windowPos;
    private static int windowFilled;
    private static long lastSampleTime;

    private static volatile long totalBytes;
    private static volatile long totalTimeMs;
    private static volatile long bestThroughputBps;
    private static volatile long lastThroughputBps;
    private static volatile long totalLatencyMs;
    private static volatile long latencySamples;
    private static volatile int errorCount;
    private static volatile int floodCount;
    private static volatile int timeoutCount;
    private static volatile long lastAdjustTime;

    private static final int MAX_EVENTS = 120;
    private static final String[] eventRing = new String[MAX_EVENTS];
    private static int eventPos;
    private static final long bootTime = SystemClock.elapsedRealtime();

    static {
        try {
            loadConfig();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static SharedPreferences prefs() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return null;
            }
            return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            try {
                SharedPreferences p = prefs();
                if (p == null) {
                    return;
                }
                enabled = p.getBoolean("enabled", false);
                mode = clamp(p.getInt("mode", MODE_AUTO), MODE_AUTO, MODE_CUSTOM);
                wifiAcceleration = p.getBoolean("wifiAcceleration", true);
                mobileAcceleration = p.getBoolean("mobileAcceleration", false);
                customMaxSimultaneous = clamp(p.getInt("maxSimultaneous", 4), 1, 10);
                customConnectionsPerDownload = clamp(p.getInt("connectionsPerDownload", 8), MIN_CONNECTIONS_PER_DOWNLOAD, MAX_CONNECTIONS_PER_DOWNLOAD);
                customMaxTotalConnections = clamp(p.getInt("maxTotalConnections", 16), MIN_TOTAL_CONNECTIONS, MAX_TOTAL_CONNECTIONS);
                configLoaded = true;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private static void ensureLoaded() {
        if (!configLoaded) {
            loadConfig();
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void putBoolean(String key, boolean value) {
        try {
            SharedPreferences p = prefs();
            if (p == null) {
                return;
            }
            p.edit().putBoolean(key, value).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void putInt(String key, int value) {
        try {
            SharedPreferences p = prefs();
            if (p == null) {
                return;
            }
            p.edit().putInt(key, value).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        putBoolean("enabled", value);
    }

    public static int getMode() {
        ensureLoaded();
        return mode;
    }

    public static void setMode(int value) {
        mode = clamp(value, MODE_AUTO, MODE_CUSTOM);
        putInt("mode", mode);
    }

    public static boolean isWifiAcceleration() {
        ensureLoaded();
        return wifiAcceleration;
    }

    public static void setWifiAcceleration(boolean value) {
        wifiAcceleration = value;
        putBoolean("wifiAcceleration", value);
    }

    public static boolean isMobileAcceleration() {
        ensureLoaded();
        return mobileAcceleration;
    }

    public static void setMobileAcceleration(boolean value) {
        mobileAcceleration = value;
        putBoolean("mobileAcceleration", value);
    }

    public static int getCustomMaxSimultaneous() {
        ensureLoaded();
        return customMaxSimultaneous;
    }

    public static void setCustomMaxSimultaneous(int value) {
        customMaxSimultaneous = clamp(value, 1, 10);
        putInt("maxSimultaneous", customMaxSimultaneous);
    }

    public static int getCustomConnectionsPerDownload() {
        ensureLoaded();
        return customConnectionsPerDownload;
    }

    public static void setCustomConnectionsPerDownload(int value) {
        customConnectionsPerDownload = clamp(value, MIN_CONNECTIONS_PER_DOWNLOAD, MAX_CONNECTIONS_PER_DOWNLOAD);
        putInt("connectionsPerDownload", customConnectionsPerDownload);
    }

    public static int getCustomMaxTotalConnections() {
        ensureLoaded();
        return customMaxTotalConnections;
    }

    public static void setCustomMaxTotalConnections(int value) {
        customMaxTotalConnections = clamp(value, MIN_TOTAL_CONNECTIONS, MAX_TOTAL_CONNECTIONS);
        putInt("maxTotalConnections", customMaxTotalConnections);
    }

    public static boolean isWifi() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return false;
            }
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static boolean accelerationAllowed(boolean wifi) {
        ensureLoaded();
        return wifi ? wifiAcceleration : mobileAcceleration;
    }

    public static int getChunkSize(boolean wifi) {
        ensureLoaded();
        if (!enabled || !accelerationAllowed(wifi)) {
            return 128 * 1024;
        }
        switch (mode) {
            case MODE_BALANCED:
                return 128 * 1024;
            case MODE_MAXIMUM:
                return 1024 * 1024;
            case MODE_CUSTOM:
                return wifi ? 512 * 1024 : 128 * 1024;
            case MODE_AUTO:
            default:
                int idx = chunkIndex;
                if (!wifi) {
                    idx = Math.min(idx, 3);
                }
                if (isLowMemory() || isLowBattery()) {
                    idx = Math.min(idx, DEFAULT_CHUNK_INDEX);
                }
                return CHUNK_STEPS[clamp(idx, 0, CHUNK_STEPS.length - 1)];
        }
    }

    public static int getMaxRequests(boolean wifi) {
        ensureLoaded();
        if (!enabled || !accelerationAllowed(wifi)) {
            return DEFAULT_REQUESTS;
        }
        switch (mode) {
            case MODE_BALANCED:
                return DEFAULT_REQUESTS;
            case MODE_MAXIMUM:
                return MAX_CONNECTIONS_PER_DOWNLOAD;
            case MODE_CUSTOM:
                return clamp(customConnectionsPerDownload, MIN_CONNECTIONS_PER_DOWNLOAD, MAX_CONNECTIONS_PER_DOWNLOAD);
            case MODE_AUTO:
            default:
                int req = currentRequests;
                if (!wifi) {
                    req = Math.min(req, 6);
                }
                if (isLowMemory() || isLowBattery()) {
                    req = Math.min(req, DEFAULT_REQUESTS);
                }
                int cap = Math.min(customMaxTotalConnections, MAX_TOTAL_CONNECTIONS);
                req = Math.min(req, Math.max(MIN_CONNECTIONS_PER_DOWNLOAD, cap));
                return clamp(req, MIN_CONNECTIONS_PER_DOWNLOAD, MAX_CONNECTIONS_PER_DOWNLOAD);
        }
    }

    public static int getMaxSimultaneous() {
        ensureLoaded();
        if (!enabled) {
            return 4;
        }
        switch (mode) {
            case MODE_BALANCED:
                return 4;
            case MODE_MAXIMUM:
                return 8;
            case MODE_CUSTOM:
                return clamp(customMaxSimultaneous, 1, 10);
            case MODE_AUTO:
            default:
                return isWifi() ? 6 : 4;
        }
    }

    public static int getLargeQueueMax(int fallback) {
        ensureLoaded();
        if (!enabled) {
            return fallback;
        }
        return clamp(getMaxSimultaneous() > 6 ? 4 : 2 + getMaxSimultaneous() / 3, 1, 8);
    }

    public static int getSmallQueueMax(int fallback) {
        ensureLoaded();
        if (!enabled) {
            return fallback;
        }
        return clamp(getMaxSimultaneous() + 2, 2, 10);
    }

    public static void onChunkReceived(long bytes, long latencyMs) {
        if (bytes <= 0) {
            return;
        }
        synchronized (sync) {
            long now = SystemClock.elapsedRealtime();
            long dt = lastSampleTime == 0 ? Math.max(1, latencyMs) : Math.max(1, now - lastSampleTime);
            lastSampleTime = now;
            windowBytes[windowPos] = bytes;
            windowTimeMs[windowPos] = Math.max(1, Math.min(dt, 10000));
            windowPos = (windowPos + 1) % WINDOW_SIZE;
            if (windowFilled < WINDOW_SIZE) {
                windowFilled++;
            }
            totalBytes += bytes;
            totalTimeMs += windowTimeMs[(windowPos + WINDOW_SIZE - 1) % WINDOW_SIZE];
            if (latencyMs > 0) {
                totalLatencyMs += latencyMs;
                latencySamples++;
            }
            adaptLocked(now);
        }
    }

    public static void onChunkReceived(long bytes) {
        onChunkReceived(bytes, 0);
    }

    public static void onServerError(String text) {
        if (text == null) {
            onChunkError(ERROR_GENERIC);
            return;
        }
        if (text.contains("FLOOD_WAIT") || text.contains("FLOOD_TEST")) {
            onChunkError(ERROR_FLOOD);
        } else if (text.contains("MIGRATE_") || text.contains("FILE_MIGRATE_") || text.contains("NETWORK_MIGRATE_") || text.contains("PHONE_MIGRATE_")) {
            onChunkError(ERROR_MIGRATE);
        } else if (text.contains("CDN") || text.contains("FILE_TOKEN_INVALID") || text.contains("REQUEST_TOKEN_INVALID")) {
            onChunkError(ERROR_CDN);
        } else if (text.contains("TIMEOUT") || text.contains("Timeout")) {
            onChunkError(ERROR_TIMEOUT);
        } else {
            onChunkError(ERROR_GENERIC);
        }
    }

    public static void onChunkError(int kind) {
        synchronized (sync) {
            errorCount++;
            if (kind == ERROR_FLOOD) {
                floodCount++;
            } else if (kind == ERROR_TIMEOUT) {
                timeoutCount++;
            }
            if (mode == MODE_BALANCED) {
                return;
            }
            if (mode == MODE_MAXIMUM && kind == ERROR_GENERIC) {
                return;
            }
            if (chunkIndex > 0) {
                chunkIndex--;
            }
            if (currentRequests > MIN_CONNECTIONS_PER_DOWNLOAD) {
                currentRequests--;
            }
            if (lastThroughputBps > 0) {
                bestThroughputBps = (long) (lastThroughputBps * 0.9);
            }
            lastAdjustTime = SystemClock.elapsedRealtime();
        }
    }

    public static void onTimeout() {
        onChunkError(ERROR_TIMEOUT);
    }

    public static void onFloodWait() {
        onChunkError(ERROR_FLOOD);
    }

    public static void onMigrate() {
        onChunkError(ERROR_MIGRATE);
    }

    public static void onCdnError() {
        onChunkError(ERROR_CDN);
    }

    private static void adaptLocked(long now) {
        if (mode != MODE_AUTO) {
            return;
        }
        if (windowFilled < 4) {
            return;
        }
        if (now - lastAdjustTime < ADJUST_INTERVAL_MS) {
            return;
        }
        long sumBytes = 0;
        long sumTime = 0;
        for (int i = 0; i < windowFilled; i++) {
            sumBytes += windowBytes[i];
            sumTime += windowTimeMs[i];
        }
        if (sumTime <= 0) {
            return;
        }
        long throughput = sumBytes * 1000 / sumTime;
        lastThroughputBps = throughput;
        if (bestThroughputBps == 0) {
            bestThroughputBps = throughput;
            lastAdjustTime = now;
            return;
        }
        double gain = bestThroughputBps == 0 ? 0 : (double) (throughput - bestThroughputBps) / (double) bestThroughputBps;
        if (gain > 0.10 && errorCount == 0) {
            if (chunkIndex < CHUNK_STEPS.length - 1) {
                chunkIndex++;
            }
            if (currentRequests < MAX_CONNECTIONS_PER_DOWNLOAD) {
                currentRequests++;
            }
            bestThroughputBps = throughput;
            lastAdjustTime = now;
        } else if (gain < -0.10 || errorCount > 2) {
            if (chunkIndex > 0) {
                chunkIndex--;
            }
            if (currentRequests > DEFAULT_REQUESTS) {
                currentRequests--;
            }
            lastAdjustTime = now;
        } else if (Math.abs(gain) < 0.05) {
            lastAdjustTime = now;
        }
    }

    public static long getThroughputBps() {
        return lastThroughputBps;
    }

    public static long getBestThroughputBps() {
        return bestThroughputBps;
    }

    public static long getAverageLatencyMs() {
        if (latencySamples == 0) {
            return 0;
        }
        return totalLatencyMs / latencySamples;
    }

    public static int getErrorCount() {
        return errorCount;
    }

    public static void resetDegradedState() {
        synchronized (sync) {
            chunkIndex = DEFAULT_CHUNK_INDEX;
            currentRequests = DEFAULT_REQUESTS;
        }
    }

    public static void resetStats() {
        synchronized (sync) {
            windowPos = 0;
            windowFilled = 0;
            lastSampleTime = 0;
            totalBytes = 0;
            totalTimeMs = 0;
            bestThroughputBps = 0;
            lastThroughputBps = 0;
            totalLatencyMs = 0;
            latencySamples = 0;
            errorCount = 0;
            floodCount = 0;
            timeoutCount = 0;
            chunkIndex = DEFAULT_CHUNK_INDEX;
            currentRequests = DEFAULT_REQUESTS;
            lastAdjustTime = 0;
        }
    }

    public static void logDownloadEvent(String tag) {
        synchronized (sync) {
            if (tag == null) {
                return;
            }
            String entry = (SystemClock.elapsedRealtime() - bootTime) + "ms " + tag;
            eventRing[eventPos % MAX_EVENTS] = entry;
            eventPos++;
            FileLog.d("dl-event: " + entry);
        }
    }

    public static String getDownloadEvents() {
        synchronized (sync) {
            int count = Math.min(eventPos, MAX_EVENTS);
            if (count <= 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                int idx = (eventPos - count + i) % MAX_EVENTS;
                if (idx < 0) {
                    idx += MAX_EVENTS;
                }
                String e = eventRing[idx];
                if (e == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(e);
            }
            if (sb.length() == 0) {
                return null;
            }
            String result = sb.toString();
            if (result.length() > 4000) {
                result = result.substring(result.length() - 4000);
                int nl = result.indexOf('\n');
                if (nl >= 0) {
                    result = result.substring(nl + 1);
                }
            }
            return result;
        }
    }

    public static long getAvailableStorage(File dir) {
        try {
            File target = dir;
            if (target == null) {
                if (ApplicationLoader.applicationContext == null) {
                    return -1;
                }
                target = ApplicationLoader.applicationContext.getCacheDir();
            }
            if (target == null) {
                return -1;
            }
            StatFs stat = new StatFs(target.getAbsolutePath());
            long blockSize = stat.getBlockSizeLong();
            long available = stat.getAvailableBlocksLong();
            return available * blockSize;
        } catch (Exception e) {
            FileLog.e(e);
            return -1;
        }
    }

    public static boolean hasEnoughStorage(long needed, File dir) {
        try {
            if (needed <= 0) {
                return true;
            }
            long available = getAvailableStorage(dir);
            if (available < 0) {
                return true;
            }
            return available >= needed + STORAGE_SAFETY_MARGIN;
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
    }

    public static boolean checkStorageForLargeFile(long totalBytesCount) {
        try {
            if (totalBytesCount <= LARGE_FILE_THRESHOLD) {
                return true;
            }
            File dir = null;
            try {
                if (ApplicationLoader.applicationContext != null) {
                    dir = ApplicationLoader.applicationContext.getCacheDir();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return hasEnoughStorage(totalBytesCount, dir);
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
    }

    public static boolean isLowMemory() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return false;
            }
            ActivityManager am = (ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return info.lowMemory;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean isLowBattery() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return false;
            }
            BatteryManager bm = (BatteryManager) ApplicationLoader.applicationContext.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) {
                return false;
            }
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            int status = 0;
            try {
                android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent battery = ApplicationLoader.applicationContext.registerReceiver(null, filter);
                if (battery != null) {
                    status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            return !charging && level >= 0 && level <= 15;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
}
