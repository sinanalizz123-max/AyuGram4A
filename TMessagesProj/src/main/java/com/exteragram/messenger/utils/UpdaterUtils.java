/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.core.content.FileProvider;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.updater.UpdaterBottomSheet;
import com.radolyn.ayugram.AyuConstants;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class UpdaterUtils {

    public static final DispatchQueue otaQueue = new DispatchQueue("otaQueue");

    private static final String uri = "https://api.github.com/repos/" + AyuConstants.APP_GITHUB + "/releases/latest";
    private static volatile String downloadURL = null;
    public static volatile String version, changelog, size, uploadDate;
    public static volatile File otaPath, versionPath, apkFile;

    private static volatile long id = 1L;
    private static final long updateCheckInterval = 3600000L; // 1 hour

    private static volatile boolean updateDownloaded;
    private static volatile boolean checkingForUpdates;

    public static void checkDirs() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            var dir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            otaPath = new File(dir, "ota");
            if (version != null) {
                versionPath = new File(otaPath, version);
                apkFile = new File(versionPath, "update.apk");
                try {
                    if (!versionPath.exists())
                        versionPath.mkdirs();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    updateDownloaded = apkFile.exists();
                } catch (Exception e) {
                    FileLog.e(e);
                    updateDownloaded = false;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void checkUpdates(BaseFragment fragment, boolean manual) {
        checkUpdates(fragment, manual, null, null);
    }

    public interface OnUpdateNotFound {
        void run();
    }

    public interface OnUpdateFound {
        void run();
    }

    public static void checkUpdates(BaseFragment fragment, boolean manual, OnUpdateNotFound onUpdateNotFound, OnUpdateFound onUpdateFound) {

        if (BuildVars.PM_BUILD || checkingForUpdates || id != 1L || (System.currentTimeMillis() - ExteraConfig.updateScheduleTimestamp < updateCheckInterval && !manual))
            return;

        checkingForUpdates = true;
        otaQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                ExteraConfig.editor.putLong("lastUpdateCheckTime", ExteraConfig.lastUpdateCheckTime = System.currentTimeMillis()).apply();
                String requestUri = uri;
                if (BuildVars.isBetaApp())
                    requestUri = requestUri.replace("/" + AyuConstants.APP_NAME + "/", "/" + AyuConstants.APP_NAME + "-Beta/");
                connection = (HttpURLConnection) new URI(requestUri).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", TranslatorUtils.formatUserAgent());
                connection.setRequestProperty("Content-Type", "application/json");

                var textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1)
                        textBuilder.append((char) c);
                }

                var obj = new JSONObject(textBuilder.toString());
                var arr = obj.getJSONArray("assets");

                if (arr.length() == 0) {
                    if (onUpdateNotFound != null)
                        AndroidUtilities.runOnUIThread(onUpdateNotFound::run);
                    return;
                }

                String link, installedApkType = getInstalledApkType();
                String[] supportedTypes = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64", "universal"};
                loop:
                for (int i = 0; i < arr.length(); i++) {
                    downloadURL = link = arr.getJSONObject(i).getString("browser_download_url");
                    size = AndroidUtilities.formatFileSize(arr.getJSONObject(i).getLong("size"));
                    if (link.contains("beta") && BuildVars.isBetaApp()) {
                        break;
                    }
                    for (String type : supportedTypes) {
                        if (link.contains(type) && Objects.equals(installedApkType, type)) {
                            break loop;
                        }
                    }
                }
                version = obj.getString("tag_name");
                changelog = obj.getString("body");
                uploadDate = obj.getString("published_at").replaceAll("[TZ]", " ");
                uploadDate = LocaleController.formatDateTime(getMillisFromDate(uploadDate, "yyyy-M-dd hh:mm:ss") / 1000);
                Update update = new Update(version, changelog, size, downloadURL, uploadDate);
                if (update.isNew() && fragment != null) {
                    checkDirs();
                    AndroidUtilities.runOnUIThread(() -> {
                        (new UpdaterBottomSheet(fragment.getContext(), fragment, true, update)).show();
                        if (onUpdateFound != null)
                            onUpdateFound.run();
                    });
                } else {
                    if (onUpdateNotFound != null)
                        AndroidUtilities.runOnUIThread(onUpdateNotFound::run);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                checkingForUpdates = false;
            }
        }, 200);
    }

    public static void downloadApk(Context context, String link, String title) {
        if (context == null) {
            return;
        }
        if (!updateDownloaded) {
            if (link == null) {
                return;
            }
            try {
                var request = new DownloadManager.Request(Uri.parse(link));

                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                request.setTitle(title);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(context, "ota/" + version, "update.apk");

                var manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) {
                    return;
                }
                id = manager.enqueue(request);

                var downloadBroadcastReceiver = new DownloadReceiver();
                var intentFilter = new IntentFilter();
                intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(downloadBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(downloadBroadcastReceiver, intentFilter);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            if (apkFile != null) {
                installApk(context, apkFile.getAbsolutePath());
            }
        }
    }

    public static void installApk(Context context, String path) {
        if (context == null || path == null) {
            return;
        }
        try {
            var file = new File(path);
            if (!file.exists())
                return;
            var install = new Intent(Intent.ACTION_VIEW);
            Uri fileUri;
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    fileUri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
                } catch (Exception e) {
                    FileLog.e(e);
                    return;
                }
            } else {
                fileUri = Uri.fromFile(file);
            }
            if (ApplicationLoader.applicationContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
                try {
                    AlertsCreator.createApkRestrictedDialog(context, null).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return;
            }
            if (fileUri != null) {
                install.setDataAndType(fileUri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    if (install.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(install);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String getOtaDirSize() {
        checkDirs();
        if (otaPath == null) {
            return AndroidUtilities.formatFileSize(0, true);
        }
        try {
            return AndroidUtilities.formatFileSize(Utilities.getDirSize(otaPath.getAbsolutePath(), 5, true), true);
        } catch (Exception e) {
            FileLog.e(e);
            return AndroidUtilities.formatFileSize(0, true);
        }
    }

    public static String getInstalledApkType() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
            }
            var info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            int mod = Math.floorMod(info.versionCode, 10);
            switch (mod) {
                case 1:
                case 3:
                    return "armeabi-v7a";
                case 2:
                case 4:
                    return "x86";
                case 5:
                case 7:
                    return "arm64-v8a";
                case 6:
                case 8:
                    return "x86_64";
                case 0:
                case 9:
                    return "universal";
            }
        } catch (Exception e) {
            FileLog.e(e);
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS[0];
            }
            return "arm64-v8a";
        }
        return null;
    }

    public static void cleanOtaDir() {
        checkDirs();
        if (otaPath != null) {
            cleanFolder(otaPath);
        }
    }

    public static void cleanFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        try {
            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        cleanFolder(file);
                    }
                }
            }
            folder.delete();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static long getMillisFromDate(String d, String format) {
        if (d == null || format == null) {
            return 1L;
        }
        @SuppressLint("SimpleDateFormat")
        var sdf = new SimpleDateFormat(format);
        try {
            Date date = sdf.parse(d);
            if (date == null) {
                return 1L;
            }
            return date.getTime();
        } catch (Exception ignore) {
            return 1L;
        }
    }

    public static SpannableStringBuilder replaceTags(CharSequence str) {
        if (str == null) {
            return new SpannableStringBuilder("");
        }
        try {
            int start;
            int end;
            StringBuilder stringBuilder = new StringBuilder(str);
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
            String symbol = "", font = AndroidUtilities.TYPEFACE_ROBOTO_REGULAR;
            for (int i = 0; i < 3; i++) {
                switch (i) {
                    case 0:
                        symbol = "**";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM;
                        break;
                    case 1:
                        symbol = "_";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_ITALIC;
                        break;
                    case 2:
                        symbol = "`";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_MONO;
                        break;
                }
                while ((start = stringBuilder.indexOf(symbol)) != -1) {
                    stringBuilder.replace(start, start + symbol.length(), "");
                    spannableStringBuilder.replace(start, start + symbol.length(), "");
                    end = stringBuilder.indexOf(symbol);
                    if (end >= 0) {
                        stringBuilder.replace(end, end + symbol.length(), "");
                        spannableStringBuilder.replace(end, end + symbol.length(), "");
                        spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(font)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            return spannableStringBuilder;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new SpannableStringBuilder(str);
    }

    public static class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            try {
                if (context == null || intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 1L);
                    DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (downloadManager == null) {
                        return;
                    }
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = null;
                    try {
                        cursor = downloadManager.query(query);
                        if (cursor != null && cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (columnIndex != -1) {
                                int status = cursor.getInt(columnIndex);
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    if (apkFile != null) {
                                        installApk(context, apkFile.getAbsolutePath());
                                    }
                                    id = 1L;
                                    updateDownloaded = false;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (cursor != null) {
                            try {
                                cursor.close();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
                    try {
                        Intent viewDownloadIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                        viewDownloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(viewDownloadIntent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (context != null) {
                        context.unregisterReceiver(this);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public static class Update {
        public final String version, size, downloadURL, uploadDate, changelog;

        public Update(String version, String changelog, String size, String downloadURL, String uploadDate) {
            this.version = version;
            this.changelog = changelog;
            this.size = size;
            this.downloadURL = downloadURL;
            this.uploadDate = uploadDate;
        }

        // todo: compare by version code, not version
        public boolean isNew() {
            if (version == null || BuildVars.BUILD_VERSION_STRING == null) {
                return false;
            }
            try {
                String[] current = BuildVars.BUILD_VERSION_STRING.split("\\.");
                String[] latest = version.split("\\.");

                int length = Math.max(current.length, latest.length);
                for (int i = 0; i < length; i++) {
                    int v1 = i < current.length ? Utilities.parseInt(current[i]) : 0;
                    int v2 = i < latest.length ? Utilities.parseInt(latest[i]) : 0;
                    if (v1 < v2) {
                        return true;
                    } else if (v1 > v2) {
                        return false;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                return false;
            }
            return false;
        }

        // todo: force update
        public boolean isForce() {
            return version != null && version.toLowerCase().contains("force");
        }
    }
}