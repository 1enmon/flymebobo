package com.jarvis.bubblenotify;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Safety net for notifications the listener service misses.
//
// The module normally reads notifications through its own listener
// service (a separate process) and forwards them to SystemUI over a
// broadcast. That path can drop a notification even when it IS in the
// shade. This hook watches the same posts from inside SystemUI: it
// records every post, and one second later, if the bubble still has
// no item for that key, it feeds the notification straight in. The
// one-second delay gives the service path a chance first, so nothing
// pops twice. A removal cancels the pending fallback.
//
// Media and call notifications are left alone — they have their own
// bubble types and are owned by the listener service.
public final class NotifCollectionHook {
    private static final long WAIT_MS = 1000;

    private final XposedInterface bridge;
    private final Application app;
    private final BubbleManager host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bundle> awaiting = new HashMap();
    private boolean done;

    public NotifCollectionHook(XposedInterface xposed, Application app, BubbleManager host) {
        this.bridge = xposed;
        this.app = app;
        this.host = host;
    }

    public void install() {
        if (this.done) {
            return;
        }
        try {
            Class<?> collection = Class.forName("com.android.systemui.statusbar.notification.collection.NotifCollection", false, this.app.getClassLoader());
            Class<?> ranking = Class.forName("android.service.notification.NotificationListenerService$RankingMap", false, this.app.getClassLoader());
            Method posted = collection.getDeclaredMethod("onNotificationPosted", StatusBarNotification.class, ranking);
            this.bridge.hook(posted).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    try {
                        notePosted((StatusBarNotification) chain.getArg(0));
                    } catch (Throwable ignored) {
                    }
                    return result;
                }
            });
            try {
                Method removed = collection.getDeclaredMethod("onNotificationRemoved", StatusBarNotification.class, ranking, int.class);
                this.bridge.hook(removed).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            if (sbn != null && sbn.getKey() != null) {
                                synchronized (awaiting) {
                                    awaiting.remove(sbn.getKey());
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        return result;
                    }
                });
            } catch (Throwable th) {
                log("NotifCollectionHook: onNotificationRemoved hook failed: " + th);
            }
            this.done = true;
            log("NotifCollectionHook: NotifCollection.onNotificationPosted hooked");
        } catch (Throwable th) {
            log("NotifCollectionHook install failed: " + th);
        }
    }

    private void notePosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getKey() == null) {
            return;
        }
        if (!this.host.isEnabled()) {
            return;
        }
        Bundle item = collect(sbn);
        if (item == null) {
            return;
        }
        final String key = sbn.getKey();
        synchronized (this.awaiting) {
            this.awaiting.put(key, item);
        }
        this.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                deliverIfMissed(key);
            }
        }, WAIT_MS);
    }

    // Builds the bubble's item bundle, or null when this post is not
    // something the fallback should ever surface.
    private Bundle collect(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals("com.jarvis.bubblenotify") || pkg.equals("com.android.systemui") || pkg.equals("android")) {
            return null;
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return null;
        }
        Bundle extras = n.extras;
        if (extras != null && extras.getParcelable("android.mediaSession") != null) {
            return null;
        }
        if ("call".equals(n.category)) {
            return null;
        }
        if ((n.flags & 0x200) != 0) {
            return null;
        }
        String title = readText(extras, "android.title");
        String text = readText(extras, "android.text");
        if (text == null || text.isEmpty()) {
            text = readText(extras, "android.bigText");
        }
        if ((title == null || title.isEmpty()) && (text == null || text.isEmpty())) {
            return null;
        }
        Bundle b = new Bundle();
        b.putString(Settings.ITEM_KEY, sbn.getKey());
        b.putString(Settings.ITEM_TITLE, title);
        b.putString(Settings.ITEM_TEXT, text);
        b.putString(Settings.ITEM_PACKAGE, pkg);
        b.putLong(Settings.ITEM_TIME, sbn.getPostTime());
        b.putParcelable(Settings.ITEM_INTENT, n.contentIntent);
        Bitmap icon = drawAppIcon(pkg);
        if (icon != null) {
            b.putParcelable(Settings.ITEM_ICON, icon);
        }
        attachReply(b, n);
        return b;
    }

    // Quick-reply action, same shape the listener service sends.
    private void attachReply(Bundle b, Notification n) {
        try {
            if (n.actions == null) {
                return;
            }
            for (Notification.Action a : n.actions) {
                if (a != null && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0 && a.actionIntent != null) {
                    b.putParcelable(Settings.ITEM_REPLY_INTENT, a.actionIntent);
                    b.putString(Settings.ITEM_REPLY_KEY, a.getRemoteInputs()[0].getResultKey());
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void deliverIfMissed(String key) {
        Bundle b;
        synchronized (this.awaiting) {
            b = this.awaiting.remove(key);
        }
        if (b == null) {
            return;
        }
        try {
            if (this.host.hasNotifKey(key)) {
                return;
            }
            if (this.host.getNotifBubble() == null) {
                return;
            }
            this.host.getNotifBubble().onNotifySingle(b);
            log("NotifCollectionHook: fallback bubble for missed notification: " + b.getString(Settings.ITEM_PACKAGE) + " key=" + key);
        } catch (Throwable th) {
            log("NotifCollectionHook fallback failed: " + th);
        }
    }

    private String readText(Bundle b, String key) {
        if (b == null) {
            return null;
        }
        try {
            CharSequence cs = b.getCharSequence(key);
            return cs == null ? null : cs.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Bitmap drawAppIcon(String pkg) {
        try {
            PackageManager pm = this.app.getPackageManager();
            Drawable d = pm.getApplicationIcon(pkg);
            if (d == null) {
                return null;
            }
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void log(String msg) {
        try {
            Log.i(Settings.TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}
