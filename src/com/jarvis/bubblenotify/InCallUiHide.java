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

// Hides the system incoming-call UI so only the bubble card shows.
//
// Runs in the com.android.incallui process. Three interception points
// cover every way the system call UI can appear:
//   1. InCallActivity.onCreate — the fullscreen call page. We let the
//      activity create itself, then finish it right away when the
//      bubble owns the call.
//   2. NotificationBroadcastReceiver.performFullscreenIntent — the
//      broadcast that launches the fullscreen page. We drop it.
//   3. InCallPresenter.showInCall — the entry that brings up the
//      in-call UI. We drop it when a real incoming call is present.
//
// The bubble yields only when all of these hold: the call switches
// are on, the screen is interactive, the keyguard is NOT locked, and
// an incoming call exists. On the lock screen the system page still
// shows, so the user can answer from there.
//
// Escape hatch: tapping the bubble's call card writes a request
// token (CallBubble.openCallUi). A token younger than 3s lets the
// launch through, so the user can still open the system call page
// on demand. Without this the bubble could never hand the call back.
public final class InCallUiHide {
    private static final int RETRIES = 24;
    private static final long RETRY_DELAY_MS = 5000;
    private static final long REQUEST_TTL_MS = 3000;
    private static final long GATE_TTL_MS = 30000;

    private static final int HOOK_PAGE = 1;
    private static final int HOOK_BROADCAST = 2;
    private static final int HOOK_PRESENTER = 4;
    private static final int HOOK_ALL = HOOK_PAGE | HOOK_BROADCAST | HOOK_PRESENTER;

    private final XposedInterface bridge;
    private final Application app;
    private final ClassLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int installed;
    private Method callListInstance;
    private Method callListIncoming;
    private boolean switchCache;
    private boolean switchReady;
    private long switchStamp;

    public InCallUiHide(XposedInterface xposed, Application app) {
        this.bridge = xposed;
        this.app = app;
        this.loader = app.getClassLoader();
    }

    public void install() {
        refreshSwitches();
        tryInstall(0);
    }

    private void tryInstall(final int attempt) {
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
            return;
        }
        if (attempt < RETRIES) {
            this.main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tryInstall(attempt + 1);
                }
            }, RETRY_DELAY_MS);
            return;
        }
        log("InCallUiHide: gave up, hooked: fullscreen-page=" + ((this.installed & HOOK_PAGE) != 0) + " fullscreen-broadcast=" + ((this.installed & HOOK_BROADCAST) != 0) + " showInCall=" + ((this.installed & HOOK_PRESENTER) != 0));
    }

    private boolean attachPageHook() {
        try {
            Method target = lookup("com.android.incallui.InCallActivity", "onCreate", Bundle.class);
            if (target == null) {
                return false;
            }
            this.bridge.hook(target).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    boolean own = shouldYield(null, true);
                    Object result = chain.proceed();
                    if (own) {
                        Object self = chain.getThisObject();
                        if (self instanceof Activity) {
                            ((Activity) self).finish();
                        }
                    }
                    return result;
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook InCallActivity.onCreate failed: " + th);
            return false;
        }
    }

    private boolean attachBroadcastHook() {
        try {
            Method target = lookup("com.android.incallui.notification.NotificationBroadcastReceiver", "performFullscreenIntent", Context.class, Intent.class);
            if (target == null) {
                return false;
            }
            this.bridge.hook(target).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object first = chain.getArg(0);
                    Context ctx = first instanceof Context ? (Context) first : null;
                    if (shouldYield(ctx, true)) {
                        return null;
                    }
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook performFullscreenIntent failed: " + th);
            return false;
        }
    }

    private boolean attachPresenterHook() {
        try {
            Class<?> flag = Boolean.TYPE;
            Method target = lookup("com.android.incallui.InCallPresenter", "showInCall", flag, flag, flag, flag, flag, flag, flag);
            if (target == null) {
                return false;
            }
            this.bridge.hook(target).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    if (shouldYield(null, hasIncomingCall())) {
                        return null;
                    }
                    return chain.proceed();
                }
            });
            return true;
        } catch (Throwable th) {
            log("hook InCallPresenter.showInCall failed: " + th);
            return false;
        }
    }

    private Method lookup(String className, String name, Class<?>... params) {
        try {
            Class<?> cls = Class.forName(className, false, this.loader);
            return cls.getDeclaredMethod(name, params);
        } catch (Throwable th) {
            return null;
        }
    }

    // The bubble owns the call when the switches are on, the screen is
    // up, the device is unlocked, and a call is actually ringing.
    private boolean shouldYield(Context context, boolean incoming) {
        if (hasFreshRequest()) {
            return false;
        }
        if (!switchesOn() || !incoming) {
            return false;
        }
        Context ctx = context != null ? context : this.app;
        return screenUsable(ctx) && !deviceLocked(ctx);
    }

    // A request token written by the bubble's own call card. Fresh
    // means the user just tapped the card, so let the system page open.
    private boolean hasFreshRequest() {
        try {
            String token = android.provider.Settings.Global.getString(this.app.getContentResolver(), Settings.CALL_UI_REQUEST_KEY);
            if (token == null || token.isEmpty()) {
                return false;
            }
            try {
                long age = SystemClock.elapsedRealtime() - Long.parseLong(token);
                if (age >= 0 && age <= REQUEST_TTL_MS) {
                    log("call UI request token fresh, let through");
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean switchesOn() {
        long now = System.currentTimeMillis();
        if (!this.switchReady || now - this.switchStamp >= GATE_TTL_MS) {
            refreshSwitches();
        }
        return this.switchCache;
    }

    private void refreshSwitches() {
        boolean on = false;
        try {
            SharedPreferences prefs = this.bridge.getRemotePreferences(Settings.PREFS_NAME);
            on = prefs.getBoolean(Settings.KEY_CALL_ENABLED, true) && prefs.getBoolean(Settings.KEY_HIDE_CALL_UI, true);
        } catch (Throwable th) {
            log("gate load failed: " + th);
        }
        this.switchCache = on;
        this.switchReady = true;
        this.switchStamp = System.currentTimeMillis();
        log("InCallUiHide gate loaded: " + on);
    }

    private boolean screenUsable(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean deviceLocked(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) {
                return true;
            }
            return Build.VERSION.SDK_INT >= 33 ? km.isKeyguardLocked() : km.inKeyguardRestrictedInputMode();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean hasIncomingCall() {
        try {
            if (this.callListInstance == null) {
                Class<?> cls = Class.forName("com.android.incallui.call.CallList", false, this.loader);
                this.callListInstance = cls.getDeclaredMethod("getInstance");
                this.callListIncoming = cls.getDeclaredMethod("getIncomingCall");
            }
            Object list = this.callListInstance.invoke(null);
            return this.callListIncoming.invoke(list) != null;
        } catch (Throwable th) {
            return false;
        }
    }

    private void log(String msg) {
        try {
            Log.i(Settings.TAG, msg);
        } catch (Throwable ignored) {
        }
    }
}
