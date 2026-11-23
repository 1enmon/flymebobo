package com.jarvis.bubblenotify;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class FileLog {
    private static final Object LOCK = new Object();
    public static final String LOG_PATH = "/data/local/tmp/BubbleNotify.log";
    public static final String LOG_PATH_ALT = "/sdcard/BubbleNotify.log";
    public static final String LOG_PATH_PRIVATE = "/data/data/com.jarvis.bubblenotify/files/BubbleNotify.log";
    // fixed199: separate log for the listener service. The app
    // creates and owns this file, so it can always write; it
    // sits on /sdcard root, so the user can read it with a
    // normal file manager. SystemUI keeps the old file.
    public static final String LOG_PATH_SVC = "/sdcard/BubbleNotify-svc.log";
    private static final long MAX_BYTES = 2097152;
    private static String cachedProcessName;

    public static void i(String str) {
        try {
            Log.i(Settings.TAG, str);
        } catch (Throwable th) {
        }
        write(str);
    }

    private static void write(String str) {
        synchronized (LOCK) {
            // fixed214: every log line carries the version
            // (v<versionCode>) so a log file always says which
            // build produced it.
            String str2 = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + processName() + " v" + Settings.VERSION_CODE + " " + str + "\n";
            String proc = processName();
            if (proc != null && proc.contains("bubblenotify")) {
                // Listener service (app process): write the
                // app-owned file the user can read. Do NOT write
                // the SystemUI-owned /sdcard file from here.
                writeTo(LOG_PATH_SVC, str2);
                writeTo(LOG_PATH_PRIVATE, str2);
            } else {
                // SystemUI (and anything else): the old file.
                writeTo(LOG_PATH, str2);
                writeTo(LOG_PATH_ALT, str2);
                writeTo(LOG_PATH_PRIVATE, str2);
            }
        }
    }

    private static void writeTo(String str, String str2) {
        try {
            File file = new File(str);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (file.exists() && file.length() > MAX_BYTES && !file.delete()) {
                return;
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            try {
                fileOutputStream.write(str2.getBytes("UTF-8"));
            } finally {
                fileOutputStream.close();
            }
        } catch (Throwable th) {
        }
    }

    private static String processName() {
        if (cachedProcessName != null) {
            return cachedProcessName;
        }
        String str = "?";
        try {
            FileInputStream fileInputStream = new FileInputStream("/proc/self/cmdline");
            byte[] bArr = new byte[256];
            int i = fileInputStream.read(bArr);
            fileInputStream.close();
            if (i > 0) {
                String str2 = new String(bArr, 0, i, "UTF-8");
                int iIndexOf = str2.indexOf(0);
                if (iIndexOf >= 0) {
                    str2 = str2.substring(0, iIndexOf);
                }
                if (!str2.isEmpty()) {
                    str = str2;
                }
            }
        } catch (Throwable th) {
        }
        cachedProcessName = str;
        return str;
    }
}
