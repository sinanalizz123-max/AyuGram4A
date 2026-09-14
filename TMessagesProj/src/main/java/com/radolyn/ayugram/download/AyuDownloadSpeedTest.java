package com.radolyn.ayugram.download;

import com.exteragram.messenger.utils.AyuDownloadEngine;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AyuDownloadSpeedTest {

    public interface Listener {
        void onProgress(String status);
        void onFinished(Result result);
    }

    public static class Result {
        public boolean storageOk;
        public long storageAverageBps;
        public long storagePeakBps;
        public boolean latencyOk;
        public long latencyAverageMs;
        public long latencyMinMs;
        public int latencySamples;
        public String networkName;
        public int recommendedMode = AyuDownloadEngine.MODE_AUTO;
        public String recommendedReason;
    }

    private static final int STORAGE_BYTES = 8 * 1024 * 1024;
    private static final int STORAGE_CHUNK = 1024 * 1024;
    private static final int LATENCY_PROBES = 3;

    public static final String[] MODE_NAMES = new String[]{"Auto", "Balanced", "Maximum", "Custom"};

    public static String getModeName(int mode) {
        if (mode >= 0 && mode < MODE_NAMES.length) {
            return MODE_NAMES[mode];
        }
        return MODE_NAMES[0];
    }

    private final int currentAccount;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile int pendingRequestToken;
    private Thread worker;

    public AyuDownloadSpeedTest(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public void start(Listener listener) {
        if (listener == null) {
            return;
        }
        worker = new Thread(() -> {
            Result result = new Result();
            runStorageBenchmark(result, listener);
            if (!cancelled.get()) {
                runLatencyProbes(result, listener);
            }
            if (!cancelled.get()) {
                result.networkName = resolveNetworkName();
                decideRecommendation(result);
                AndroidUtilities.runOnUIThread(() -> {
                    if (!cancelled.get()) {
                        listener.onFinished(result);
                    }
                });
            }
        });
        worker.start();
    }

    public void cancel() {
        cancelled.set(true);
        try {
            int token = pendingRequestToken;
            if (token != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(token, true);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        worker = null;
    }

    private void runStorageBenchmark(Result result, Listener listener) {
        File dir = null;
        try {
            if (ApplicationLoader.applicationContext != null) {
                dir = ApplicationLoader.applicationContext.getCacheDir();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (dir == null) {
            result.storageOk = false;
            return;
        }
        File tmp = new File(dir, "ayu_speedtest_" + System.currentTimeMillis() + ".tmp");
        byte[] chunk = new byte[STORAGE_CHUNK];
        for (int i = 0; i < chunk.length; i += 4096) {
            chunk[i] = (byte) (i & 0xff);
        }
        ArrayList<Long> chunkBps = new ArrayList<>();
        long totalBytes = 0;
        long totalNanos = 0;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            int chunks = STORAGE_BYTES / STORAGE_CHUNK;
            for (int i = 0; i < chunks; i++) {
                if (cancelled.get()) {
                    return;
                }
                long start = System.nanoTime();
                out.write(chunk);
                out.flush();
                long elapsed = System.nanoTime() - start;
                if (elapsed > 0) {
                    chunkBps.add((long) (STORAGE_CHUNK * 1e9 / elapsed));
                }
                totalBytes += STORAGE_CHUNK;
                totalNanos += elapsed;
                final int done = i + 1;
                AndroidUtilities.runOnUIThread(() -> {
                    if (!cancelled.get()) {
                        listener.onProgress("Storage test: " + done + " / " + (STORAGE_BYTES / STORAGE_CHUNK) + " MB");
                    }
                });
            }
            if (totalNanos > 0 && totalBytes > 0) {
                result.storageOk = true;
                result.storageAverageBps = (long) (totalBytes * 1e9 / totalNanos);
                long peak = 0;
                for (Long bps : chunkBps) {
                    if (bps != null && bps > peak) {
                        peak = bps;
                    }
                }
                result.storagePeakBps = peak > 0 ? peak : result.storageAverageBps;
            } else {
                result.storageOk = false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            result.storageOk = false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                if (tmp.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void runLatencyProbes(Result result, Listener listener) {
        ArrayList<Long> samples = new ArrayList<>();
        for (int i = 0; i < LATENCY_PROBES; i++) {
            if (cancelled.get()) {
                return;
            }
            final int probe = i + 1;
            AndroidUtilities.runOnUIThread(() -> {
                if (!cancelled.get()) {
                    listener.onProgress("Telegram ping: " + probe + " / " + LATENCY_PROBES);
                }
            });
            try {
                if (!ApplicationLoader.isNetworkOnline()) {
                    break;
                }
            } catch (Exception e) {
                FileLog.e(e);
                break;
            }
            final long[] elapsed = new long[]{-1};
            final Object lock = new Object();
            TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
            final long start = System.nanoTime();
            int token = 0;
            try {
                token = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    synchronized (lock) {
                        if (response != null) {
                            elapsed[0] = (System.nanoTime() - start) / 1000000L;
                        }
                        lock.notifyAll();
                    }
                });
                pendingRequestToken = token;
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (token == 0) {
                break;
            }
            synchronized (lock) {
                if (elapsed[0] < 0 && !cancelled.get()) {
                    try {
                        lock.wait(15000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            pendingRequestToken = 0;
            if (cancelled.get()) {
                return;
            }
            if (elapsed[0] >= 0) {
                samples.add(elapsed[0]);
            } else {
                try {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(token, true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
        }
        if (!samples.isEmpty()) {
            long sum = 0;
            long min = Long.MAX_VALUE;
            for (Long s : samples) {
                if (s == null) {
                    continue;
                }
                sum += s;
                if (s < min) {
                    min = s;
                }
            }
            result.latencyOk = true;
            result.latencySamples = samples.size();
            result.latencyAverageMs = sum / samples.size();
            result.latencyMinMs = min;
        } else {
            result.latencyOk = false;
        }
    }

    private String resolveNetworkName() {
        try {
            int type = ApplicationLoader.getCurrentNetworkType();
            if (type == StatsController.TYPE_WIFI) {
                return ApplicationLoader.isConnectionSlow() ? "Wi-Fi (slow)" : "Wi-Fi";
            } else if (type == StatsController.TYPE_ROAMING) {
                return "Roaming";
            } else {
                return ApplicationLoader.isConnectionSlow() ? "Mobile (slow)" : "Mobile";
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void decideRecommendation(Result result) {
        if (!result.storageOk && !result.latencyOk) {
            result.recommendedMode = AyuDownloadEngine.MODE_AUTO;
            result.recommendedReason = "measurements unavailable";
            return;
        }
        if (result.storageOk && result.storageAverageBps < 20L * 1024L * 1024L) {
            result.recommendedMode = AyuDownloadEngine.MODE_BALANCED;
            result.recommendedReason = "storage write is the bottleneck";
            return;
        }
        if (result.latencyOk && result.latencyAverageMs > 600) {
            result.recommendedMode = AyuDownloadEngine.MODE_AUTO;
            result.recommendedReason = "high Telegram latency";
            return;
        }
        if (result.storageOk && result.storageAverageBps >= 80L * 1024L * 1024L
                && (!result.latencyOk || result.latencyAverageMs <= 150)) {
            result.recommendedMode = AyuDownloadEngine.MODE_MAXIMUM;
            result.recommendedReason = "fast storage and low latency";
            return;
        }
        result.recommendedMode = AyuDownloadEngine.MODE_BALANCED;
        result.recommendedReason = "moderate device and network speed";
    }

    public static String formatBps(long bps) {
        if (bps < 0) {
            return null;
        }
        double mbps = bps / (1024.0 * 1024.0);
        if (mbps >= 10) {
            return String.format(java.util.Locale.ROOT, "%.0f MB/s", mbps);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB/s", mbps);
    }

    public static String buildResultText(Result result) {
        StringBuilder sb = new StringBuilder();
        if (result.latencyOk) {
            sb.append("Latency: ").append(result.latencyAverageMs).append(" ms");
            sb.append(" (min ").append(result.latencyMinMs).append(" ms, ");
            sb.append(result.latencySamples).append(" probes)");
        } else {
            sb.append("Latency: unavailable (no Telegram response)");
        }
        sb.append("\n");
        String peak = result.storageOk ? formatBps(result.storagePeakBps) : null;
        String avg = result.storageOk ? formatBps(result.storageAverageBps) : null;
        sb.append("Peak: ").append(peak != null ? peak + " storage write" : "unavailable");
        sb.append("\n");
        sb.append("Average: ").append(avg != null ? avg + " storage write (8 MB test)" : "unavailable");
        sb.append("\n");
        sb.append("Recommended: ").append(getModeName(result.recommendedMode));
        if (result.recommendedReason != null) {
            sb.append(" (").append(result.recommendedReason).append(")");
        }
        sb.append("\n");
        sb.append("Network: ").append(result.networkName != null ? result.networkName : "unknown");
        sb.append("\n");
        sb.append("Storage: ").append(avg != null ? avg + " write" : "unavailable");
        return sb.toString();
    }

    public static int resolveAccount(int fallback) {
        try {
            return UserConfig.selectedAccount;
        } catch (Exception e) {
            FileLog.e(e);
            return fallback;
        }
    }
}
