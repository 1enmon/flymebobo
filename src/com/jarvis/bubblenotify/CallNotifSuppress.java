package com.jarvis.bubblenotify;

import android.app.Application;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

// fixed186: port of flymebobo 2.0's CallNotifSuppress (v136/v137),
// per user "你这个来电拦截直接搬2.0就行了 / 他那个全面一点".
// Drops the system call notification from SystemUI's own collection
// so it never renders anywhere — not as a heads-up, not in the
// shade, not as the ongoing-call card in the control center.
// 1.0 previously only had CallBannerSuppress (the incoming banner)
// and InCallUiHide (the incallui activity), while the call
// notification itself still entered SystemUI's collection and
// showed up after answering — that is the "拦截不全面" gap.
//
// Only SystemUI's display is affected. The module's own
// NotificationListenerService runs in a different process and reads
// notifications straight from NotificationManagerService, so the
// bubble, the answer button and the speaker toggle keep working.
// The notification itself is NOT cancelled.
//
// Identification matches CallBannerSuppress: the in-call UI package
// with no channel or a "phone_*" channel; a call-category
// notification; or a fullscreen-intent notification from a
// dialer/phone/telecom package.
public final class CallNotifSuppress {
    private final XposedInterface bridge;
    private final Application app;
    private boolean done;

    public CallNotifSuppress(XposedInterface xposed, Application app) {
        this.bridge = xposed;
        this.app = app;
    }

    public void install() {
        if (done) {
            return;
        }
        try {
            Class<?> collection = Class.forName(
                    "com.android.systemui.statusbar.notification.collection.NotifCollection",
                    false, app.getClassLoader());
            int count = 0;
            for (Method m : collection.getDeclaredMethods()) {
                if (!"onNotificationPosted".equals(m.getName())) {
                    continue;
                }
                if (attach(m)) {
                    count++;
                }
            }
            if (count == 0) {
                log("CallNotifSuppress: onNotificationPosted not found");
                return;
            }
            done = true;
            log("CallNotifSuppress: NotifCollection.onNotificationPosted hooked ("
                    + count + " overloads)");
        } catch (Throwable th) {
            log("CallNotifSuppress install failed: " + th);
        }
    }

    // Every onNotificationPosted overload takes the
    // StatusBarNotification as its first argument, so one handler
    // covers all of them.
    private boolean attach(final Method target) {
        try {
            bridge.hook(target).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain)
                        throws Throwable {
                    if (shouldDrop(chain.getArg(0))) {
                        log("call notification dropped from SystemUI collection");
                        return null;
                    }
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable th) {
            log("CallNotifSuppress: hook failed for " + target + ": " + th);
            return false;
        }
    }

    private boolean shouldDrop(Object arg0) {
        if (!(arg0 instanceof StatusBarNotification)) {
            return false;
        }
        return isCallNotif((StatusBarNotification) arg0);
    }

    private boolean isCallNotif(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) {
            return false;
        }
        Notification n = sbn.getNotification();
        String channel = n == null ? null : n.getChannelId();
        if (Settings.INCALLUI_PACKAGE.equals(pkg)) {
            return channel == null || channel.startsWith("phone_");
        }
        if (n == null) {
            return false;
        }
        if ("call".equals(n.category)) {
            return true;
        }
        if (n.fullScreenIntent == null) {
            return false;
        }
        String lower = pkg.toLowerCase();
        return lower.contains("dialer") || lower.contains("phone")
                || lower.contains("telecom") || lower.contains("incallui")
                || lower.contains("call");
    }

    private void log(String msg) {
        try {
            Log.i(Settings.TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}
