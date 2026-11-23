package com.jarvis.bubblenotify;

import android.app.Application;
import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public final class NotifCollectionHook {
    private static final long WAIT_MS = 1000;
    // fixed176: same push-service list as NotificationService — the
    // hook runs in SystemUI and cannot see its private set.
    private static final Set<String> PUSH_SERVICE_PACKAGES = new HashSet(Arrays.asList("com.meizu.cloud.pushservice", "com.meizu.push", "com.meizu.flyme.push", "com.meizu.cloud.pushsdk", "com.xiaomi.xmsf", "com.huawei.android.pushagent", "com.huawei.hms", "com.coloros.mcs", "com.heytap.mcs", "com.heytap.msp", "com.vivo.pushservice", "com.vivo.push", "com.samsung.android.push"));
    private final Application app;
    private final XposedInterface bridge;
    private boolean done;
    private final BubbleManager host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bundle> awaiting = new HashMap();

    public NotifCollectionHook(XposedInterface xposedInterface, Application application, BubbleManager bubbleManager) {
        this.bridge = xposedInterface;
        this.app = application;
        this.host = bubbleManager;
    }

    public void install() {
        if (this.done) {
            return;
        }
        try {
            Class<?> cls = Class.forName("com.android.systemui.statusbar.notification.collection.NotifCollection", false, this.app.getClassLoader());
            Class<?> cls2 = Class.forName("android.service.notification.NotificationListenerService$RankingMap", false, this.app.getClassLoader());
            this.bridge.hook(cls.getDeclaredMethod("onNotificationPosted", StatusBarNotification.class, cls2)).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.NotifCollectionHook.1
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object objProceed = chain.proceed();
                    try {
                        NotifCollectionHook.this.notePosted((StatusBarNotification) chain.getArg(0));
                    } catch (Throwable th) {
                    }
                    return objProceed;
                }
            });
            try {
                this.bridge.hook(cls.getDeclaredMethod("onNotificationRemoved", StatusBarNotification.class, cls2, Integer.TYPE)).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.NotifCollectionHook.2
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object objProceed = chain.proceed();
                        try {
                            StatusBarNotification statusBarNotification = (StatusBarNotification) chain.getArg(0);
                            if (statusBarNotification != null && statusBarNotification.getKey() != null) {
                                synchronized (NotifCollectionHook.this.awaiting) {
                                    NotifCollectionHook.this.awaiting.remove(statusBarNotification.getKey());
                                }
                            }
                        } catch (Throwable th) {
                        }
                        return objProceed;
                    }
                });
            } catch (Throwable th) {
                log("NotifCollectionHook: onNotificationRemoved hook failed: " + String.valueOf(th));
            }
            this.done = true;
            log("NotifCollectionHook: NotifCollection.onNotificationPosted hooked");
        } catch (Throwable th2) {
            log("NotifCollectionHook install failed: " + String.valueOf(th2));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notePosted(StatusBarNotification statusBarNotification) {
        Bundle bundleCollect;
        if (statusBarNotification == null || statusBarNotification.getKey() == null || !this.host.isEnabled() || (bundleCollect = collect(statusBarNotification)) == null) {
            return;
        }
        // fixed179: intercept ALL notifications, not just push
        // (user 2026-11-23 "拦截所以通知"). A notification that
        // popped a bubble must not stay in the shade as well. Wait
        // for the main path to pop the bubble, then clear the
        // system card via IStatusBarService.onNotificationClear
        // (the status bar's own swipe-away path, no ownership
        // check). Only when the bubble actually popped
        // (hasNotifKey), so a dropped notification is never
        // cleared from the shade.
        {
            final String pushKey = statusBarNotification.getKey();
            this.main.postDelayed(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        if (NotifCollectionHook.this.host.hasNotifKey(pushKey)) {
                            clearViaStatusBar(pushKey);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, 1500);
        }
        final String key = statusBarNotification.getKey();
        synchronized (this.awaiting) {
            this.awaiting.put(key, bundleCollect);
        }
        this.main.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifCollectionHook.3
            @Override // java.lang.Runnable
            public void run() {
                NotifCollectionHook.this.deliverIfMissed(key);
            }
        }, WAIT_MS);
    }

    private Bundle collect(StatusBarNotification statusBarNotification) {
        Notification notification;
        String packageName = statusBarNotification.getPackageName();
        if (packageName == null || packageName.equals(Settings.MODULE_PACKAGE) || packageName.equals(Settings.SYSTEMUI_PACKAGE) || packageName.equals("android") || (notification = statusBarNotification.getNotification()) == null) {
            return null;
        }
        // fixed176: push-service delegation — the key's 2nd field is
        // the REAL app. Use it for the icon/package so the fallback
        // bubble shows the real app, not "推送服务". Drop when the
        // key carries no valid installed app.
        if (isPushService(packageName)) {
            String realPkg = keyPackage(statusBarNotification.getKey());
            if (realPkg != null && !realPkg.isEmpty()
                    && !realPkg.equals(packageName)
                    && isInstalledApp(realPkg)) {
                log("Push service delegated (hook): pkg=" + packageName + " realPkg=" + realPkg);
                packageName = realPkg;
            } else {
                log("collect drop: push-delegate pkg=" + packageName + " realPkg=" + realPkg);
                return null;
            }
        }
        Bundle bundle = notification.extras;
        if ((bundle != null && bundle.getParcelable("android.mediaSession") != null) || "call".equals(notification.category) || (notification.flags & 512) != 0) {
            log("collect drop: media/call/summary pkg=" + packageName + " flags=" + notification.flags + " cat=" + notification.category);
            return null;
        }
        String text = readText(bundle, "android.title");
        String text2 = readText(bundle, "android.text");
        if (text2 == null || text2.isEmpty()) {
            text2 = readText(bundle, "android.bigText");
        }
        if ((text == null || text.isEmpty()) && (text2 == null || text2.isEmpty())) {
            log("collect drop: empty title+text pkg=" + packageName);
            return null;
        }
        Bundle bundle2 = new Bundle();
        bundle2.putString(Settings.ITEM_KEY, statusBarNotification.getKey());
        bundle2.putString(Settings.ITEM_TITLE, text);
        bundle2.putString(Settings.ITEM_TEXT, text2);
        bundle2.putString(Settings.ITEM_PACKAGE, packageName);
        bundle2.putLong(Settings.ITEM_TIME, statusBarNotification.getPostTime());
        if (bundle != null) {
            int i = bundle.getInt("android.progress", 0);
            int i2 = bundle.getInt("android.progressMax", 0);
            boolean z = bundle.getBoolean("android.progressIndeterminate", false);
            if (i2 > 0 || z) {
                bundle2.putInt(Settings.ITEM_PROGRESS, i);
                bundle2.putInt(Settings.ITEM_PROGRESS_MAX, i2);
                bundle2.putBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, z);
            }
        }
        bundle2.putParcelable(Settings.ITEM_INTENT, notification.contentIntent);
        Bitmap bitmapDrawAppIcon = drawAppIcon(packageName);
        if (bitmapDrawAppIcon != null) {
            bundle2.putParcelable(Settings.ITEM_ICON, bitmapDrawAppIcon);
        }
        attachReply(bundle2, notification);
        return bundle2;
    }

    private void attachReply(Bundle bundle, Notification notification) {
        try {
            if (notification.actions == null) {
                return;
            }
            for (Notification.Action action : notification.actions) {
                if (action != null && action.getRemoteInputs() != null && action.getRemoteInputs().length > 0 && action.actionIntent != null) {
                    bundle.putParcelable(Settings.ITEM_REPLY_INTENT, action.actionIntent);
                    bundle.putString(Settings.ITEM_REPLY_KEY, action.getRemoteInputs()[0].getResultKey());
                    return;
                }
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deliverIfMissed(String str) {
        Bundle bundleRemove;
        synchronized (this.awaiting) {
            bundleRemove = this.awaiting.remove(str);
        }
        if (bundleRemove == null) {
            return;
        }
        try {
            if (this.host.getNotifBubble() == null) {
                return;
            }
            // fixed218: a re-post that carries a progress bar is a
            // live download/install — route it to onNotifySingle
            // even when the key is already present, so the
            // capsule's percent refreshes. The listener broadcast
            // path (onNotifyList) never runs for these, so without
            // this the percent only refreshes on manual dismiss.
            // Non-download re-posts keep the old fallback behaviour
            // (skip when already present).
            boolean hasProgress = bundleRemove.getInt(Settings.ITEM_PROGRESS_MAX, 0) > 0;
            if (this.host.hasNotifKey(str) && !hasProgress) {
                return;
            }
            this.host.getNotifBubble().onNotifySingle(bundleRemove);
            log("NotifCollectionHook: fallback bubble for missed notification: " + bundleRemove.getString(Settings.ITEM_PACKAGE) + " key=" + str);
        } catch (Throwable th) {
            log("NotifCollectionHook fallback failed: " + String.valueOf(th));
        }
    }

    private String readText(Bundle bundle, String str) {
        if (bundle == null) {
            return null;
        }
        try {
            CharSequence charSequence = bundle.getCharSequence(str);
            if (charSequence == null) {
                return null;
            }
            return charSequence.toString();
        } catch (Throwable th) {
            return null;
        }
    }

    private Bitmap drawAppIcon(String str) {
        try {
            Drawable applicationIcon = this.app.getPackageManager().getApplicationIcon(str);
            if (applicationIcon == null) {
                return null;
            }
            int intrinsicWidth = applicationIcon.getIntrinsicWidth() > 0 ? applicationIcon.getIntrinsicWidth() : 96;
            int intrinsicHeight = applicationIcon.getIntrinsicHeight() > 0 ? applicationIcon.getIntrinsicHeight() : 96;
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            applicationIcon.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
            applicationIcon.draw(canvas);
            return bitmapCreateBitmap;
        } catch (Throwable th) {
            return null;
        }
    }

    private boolean isPushService(String str) {
        if (str == null) {
            return false;
        }
        if (PUSH_SERVICE_PACKAGES.contains(str)) {
            return true;
        }
        try {
            CharSequence applicationLabel = this.app.getPackageManager().getApplicationLabel(this.app.getPackageManager().getApplicationInfo(str, 0));
            return applicationLabel != null && "推送服务".equals(applicationLabel.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    // fixed176: the notification key is "userId|pkg|id|tag|uid" — the
    // pkg field is the ORIGINAL app a push service posts for.
    private String keyPackage(String key) {
        if (key == null) {
            return null;
        }
        try {
            String[] parts = key.split("\\|");
            if (parts.length >= 2) {
                return parts[1];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isInstalledApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        try {
            if (this.app.getPackageManager().getApplicationInfo(pkg, 0) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // v204: same LauncherApps fallback as the listener's
        // isInstalledApp (package-visibility hardening).
        try {
            android.content.pm.LauncherApps launcherApps = (android.content.pm.LauncherApps) this.app.getSystemService("launcherapps");
            if (launcherApps != null) {
                java.util.List<android.content.pm.LauncherActivityInfo> list = launcherApps.getActivityList(pkg, android.os.Process.myUserHandle());
                if (list != null && !list.isEmpty()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // fixed176: IStatusBarService.onNotificationClear, the flymebobo
    // 2.4 v492 route (learned from FlymeIsland's CoolapkSystemClear).
    // The hook runs inside SystemUI, so it can call the status bar's
    // own "user swiped the notification away" path directly — no
    // ownership check, which is why the listener's cancelNotification
    // cannot remove delegated push notifications. The pkg in the key
    // is the ORIGINAL app, which is what onNotificationClear wants.
    // Returns false on any failure.
    private boolean clearViaStatusBar(String key) {
        try {
            String[] parts = key.split("\\|");
            if (parts.length < 2) {
                return false;
            }
            int userId = Integer.parseInt(parts[0]);
            String origPkg = parts[1];
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "statusbar");
            Class<?> stub = Class.forName(
                    "com.android.internal.statusbar"
                            + ".IStatusBarService$Stub");
            Class<?> svc = Class.forName(
                    "com.android.internal.statusbar.IStatusBarService");
            Object bar = stub.getMethod("asInterface",
                    android.os.IBinder.class).invoke(null, binder);
            Class<?> visCls = Class.forName(
                    "com.android.internal.statusbar"
                            + ".NotificationVisibility");
            Object vis = visCls.getMethod("obtain", String.class,
                    Integer.TYPE, Integer.TYPE, Boolean.TYPE)
                    .invoke(null, key, 0, 1, true);
            svc.getMethod("onNotificationClear", String.class,
                    Integer.TYPE, String.class, Integer.TYPE,
                    Integer.TYPE, visCls)
                    .invoke(bar, origPkg, userId, key, 1, 0, vis);
            log("Push cleared via onNotificationClear key=" + key);
            return true;
        } catch (Throwable t) {
            log("clearViaStatusBar threw: " + String.valueOf(t));
            return false;
        }
    }

    private void log(String str) {
        try {
            Log.i(Settings.TAG, str);
        } catch (Throwable th) {
        }
    }
}
