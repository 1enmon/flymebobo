package com.jarvis.bubblenotify;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

/* JADX INFO: loaded from: classes.dex */
public final class InCallUiHide {
    private static final long GATE_TTL_MS = 30000;
    private static final int HOOK_ALL = 7;
    private static final int HOOK_BROADCAST = 2;
    private static final int HOOK_PAGE = 1;
    private static final int HOOK_PRESENTER = 4;
    private static final long REQUEST_TTL_MS = 3000;
    private static final int RETRIES = 24;
    private static final long RETRY_DELAY_MS = 5000;
    private final Application app;
    private final XposedInterface bridge;
    private Method callListIncoming;
    private Method callListInstance;
    private int installed;
    private final ClassLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean switchCache;
    private boolean switchReady;
    private long switchStamp;

    public InCallUiHide(XposedInterface xposedInterface, Application application) {
        this.bridge = xposedInterface;
        this.app = application;
        this.loader = application.getClassLoader();
    }

    public void install() {
        refreshSwitches();
        tryInstall(0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tryInstall(final int i) {
        if ((this.installed & HOOK_PAGE) == 0 && attachPageHook()) {
            this.installed |= HOOK_PAGE;
        }
        if ((this.installed & HOOK_BROADCAST) == 0 && attachBroadcastHook()) {
            this.installed |= HOOK_BROADCAST;
        }
        if ((this.installed & HOOK_PRESENTER) == 0 && attachPresenterHook()) {
            this.installed |= HOOK_PRESENTER;
        }
        if (this.installed == HOOK_ALL) {
            log("InCallUiHide: all 3 call hooks installed");
        } else {
            if (i < RETRIES) {
                this.main.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.InCallUiHide.1
                    @Override // java.lang.Runnable
                    public void run() {
                        InCallUiHide.this.tryInstall(i + InCallUiHide.HOOK_PAGE);
                    }
                }, RETRY_DELAY_MS);
                return;
            }
            log("InCallUiHide: gave up, hooked: fullscreen-page=" + ((this.installed & HOOK_PAGE) != 0 ? HOOK_PAGE : false) + " fullscreen-broadcast=" + ((this.installed & HOOK_BROADCAST) != 0 ? HOOK_PAGE : false) + " showInCall=" + ((this.installed & HOOK_PRESENTER) != 0));
        }
    }

    private boolean attachPageHook() {
        try {
            Method methodLookup = lookup("com.android.incallui.InCallActivity", "onCreate", Bundle.class);
            if (methodLookup == null) {
                return false;
            }
            this.bridge.hook(methodLookup).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.InCallUiHide.2
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    boolean zShouldYield = InCallUiHide.this.shouldYield(null, true);
                    Object objProceed = chain.proceed();
                    if (zShouldYield) {
                        Object thisObject = chain.getThisObject();
                        if (thisObject instanceof Activity) {
                            ((Activity) thisObject).finish();
                        }
                    }
                    return objProceed;
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook InCallActivity.onCreate failed: " + String.valueOf(th));
            return false;
        }
    }

    private boolean attachBroadcastHook() {
        try {
            Method methodLookup = lookup("com.android.incallui.notification.NotificationBroadcastReceiver", "performFullscreenIntent", Context.class, Intent.class);
            if (methodLookup == null) {
                return false;
            }
            this.bridge.hook(methodLookup).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.InCallUiHide.3
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object arg = chain.getArg(0);
                    if (InCallUiHide.this.shouldYield(arg instanceof Context ? (Context) arg : null, true)) {
                        return null;
                    }
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook performFullscreenIntent failed: " + String.valueOf(th));
            return false;
        }
    }

    private boolean attachPresenterHook() {
        try {
            Class cls = Boolean.TYPE;
            Method methodLookup = lookup("com.android.incallui.InCallPresenter", "showInCall", cls, cls, cls, cls, cls, cls, cls);
            if (methodLookup == null) {
                return false;
            }
            this.bridge.hook(methodLookup).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.InCallUiHide.4
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    if (InCallUiHide.this.shouldYield(null, InCallUiHide.this.hasIncomingCall())) {
                        return null;
                    }
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook InCallPresenter.showInCall failed: " + String.valueOf(th));
            return false;
        }
    }

    private Method lookup(String str, String str2, Class<?>... clsArr) {
        try {
            return Class.forName(str, false, this.loader).getDeclaredMethod(str2, clsArr);
        } catch (Throwable th) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean shouldYield(Context context, boolean z) {
        if (hasFreshRequest() || !switchesOn() || !z) {
            return false;
        }
        if (context == null) {
            context = this.app;
        }
        // fixed185: suppress on the lock screen too (user
        // "来电拦截没全"). The bubble is a SystemUI overlay
        // and shows on the lock screen, so the user can still
        // answer there. The old !deviceLocked() let the system
        // call UI leak through whenever the phone was locked.
        return screenUsable(context);
    }

    private boolean hasFreshRequest() {
        try {
            String string = android.provider.Settings.Global.getString(this.app.getContentResolver(), Settings.CALL_UI_REQUEST_KEY);
            if (string == null || string.isEmpty()) {
                return false;
            }
            try {
                long jElapsedRealtime = SystemClock.elapsedRealtime() - Long.parseLong(string);
                if (jElapsedRealtime >= 0 && jElapsedRealtime <= 3000) {
                    log("call UI request token fresh, let through");
                    return true;
                }
            } catch (NumberFormatException e) {
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private boolean switchesOn() {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (!this.switchReady || jCurrentTimeMillis - this.switchStamp >= GATE_TTL_MS) {
            refreshSwitches();
        }
        return this.switchCache;
    }

    private void refreshSwitches() {
        boolean z = false;
        try {
            SharedPreferences remotePreferences = this.bridge.getRemotePreferences(Settings.PREFS_NAME);
            if (remotePreferences.getBoolean(Settings.KEY_CALL_ENABLED, true) && remotePreferences.getBoolean(Settings.KEY_HIDE_CALL_UI, true)) {
                z = true;
            }
        } catch (Throwable th) {
            log("gate load failed: " + String.valueOf(th));
        }
        this.switchCache = z;
        this.switchReady = true;
        this.switchStamp = System.currentTimeMillis();
        log("InCallUiHide gate loaded: " + z);
    }

    private boolean screenUsable(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            return powerManager == null || powerManager.isInteractive();
        } catch (Throwable th) {
            return true;
        }
    }

    private boolean deviceLocked(Context context) {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService("keyguard");
            if (keyguardManager == null) {
                return true;
            }
            return Build.VERSION.SDK_INT >= 33 ? keyguardManager.isKeyguardLocked() : keyguardManager.inKeyguardRestrictedInputMode();
        } catch (Throwable th) {
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean hasIncomingCall() {
        try {
            if (this.callListInstance == null) {
                Class<?> cls = Class.forName("com.android.incallui.call.CallList", false, this.loader);
                this.callListInstance = cls.getDeclaredMethod("getInstance", new Class[0]);
                this.callListIncoming = cls.getDeclaredMethod("getIncomingCall", new Class[0]);
            }
            return this.callListIncoming.invoke(this.callListInstance.invoke(null, new Object[0]), new Object[0]) != null;
        } catch (Throwable th) {
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
