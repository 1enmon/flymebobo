package com.jarvis.bubblenotify;

import android.app.Application;
import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: classes.dex */
public final class CallBannerSuppress {
    private static final long GATE_TTL_MS = 30000;
    private final Application app;
    private final XposedInterface bridge;
    private boolean done;
    private long gateStamp;
    private boolean gateValue;

    public CallBannerSuppress(XposedInterface xposedInterface, Application application) {
        this.bridge = xposedInterface;
        this.app = application;
    }

    public void install() {
        if (this.done) {
            return;
        }
        try {
            int i = 0;
            for (Method method : Class.forName("com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl", false, this.app.getClassLoader()).getDeclaredMethods()) {
                if ("showNotification".equals(method.getName()) && attach(method)) {
                    i++;
                }
            }
            if (i == 0) {
                log("CallBannerSuppress: showNotification not found");
            } else {
                this.done = true;
                log("CallBannerSuppress: HeadsUpManagerImpl.showNotification hooked (" + i + " overloads)");
            }
        } catch (Throwable th) {
            log("CallBannerSuppress install failed: " + String.valueOf(th));
        }
    }

    private boolean attach(Method method) {
        try {
            this.bridge.hook(method).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.CallBannerSuppress.1
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    if (CallBannerSuppress.this.shouldDrop(chain.getArg(0))) {
                        CallBannerSuppress.this.log("incoming-call banner suppressed");
                        return null;
                    }
                    return chain.proceed();
                }
            });
            StringBuilder sb = new StringBuilder();
            for (Class<?> cls : method.getParameterTypes()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(cls.getSimpleName());
            }
            log("CallBannerSuppress: hooked showNotification(" + sb.toString() + ")");
            return true;
        } catch (Throwable th) {
            log("CallBannerSuppress: hook failed for " + String.valueOf(method) + ": " + String.valueOf(th));
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean shouldDrop(Object obj) {
        if (obj == null) {
            return false;
        }
        try {
            Object objInvoke = obj.getClass().getMethod("getSbn", new Class[0]).invoke(obj, new Object[0]);
            return (objInvoke instanceof StatusBarNotification) && isCallHeadsUp((StatusBarNotification) objInvoke) && suppressionOn();
        } catch (Throwable th) {
            return false;
        }
    }

    private boolean isCallHeadsUp(StatusBarNotification statusBarNotification) {
        String packageName = statusBarNotification.getPackageName();
        if (packageName == null) {
            return false;
        }
        Notification notification = statusBarNotification.getNotification();
        String channelId = notification == null ? null : notification.getChannelId();
        if (Settings.INCALLUI_PACKAGE.equals(packageName)) {
            return channelId == null || channelId.startsWith("phone_");
        }
        if (notification == null) {
            return false;
        }
        if ("call".equals(notification.category)) {
            return true;
        }
        if (notification.fullScreenIntent == null) {
            return false;
        }
        String lowerCase = packageName.toLowerCase();
        return lowerCase.contains("dialer") || lowerCase.contains("phone") || lowerCase.contains("telecom") || lowerCase.contains("incallui") || lowerCase.contains("call");
    }

    private boolean suppressionOn() {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (jCurrentTimeMillis - this.gateStamp < GATE_TTL_MS) {
            return this.gateValue;
        }
        boolean z = false;
        try {
            SharedPreferences remotePreferences = this.bridge.getRemotePreferences(Settings.PREFS_NAME);
            if (remotePreferences.getBoolean(Settings.KEY_CALL_ENABLED, true) && remotePreferences.getBoolean(Settings.KEY_HIDE_CALL_UI, true)) {
                z = true;
            }
        } catch (Throwable th) {
        }
        this.gateValue = z;
        this.gateStamp = jCurrentTimeMillis;
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String str) {
        try {
            Log.i(Settings.TAG, str);
        } catch (Throwable th) {
        }
    }
}
