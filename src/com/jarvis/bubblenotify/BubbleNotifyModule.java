package com.jarvis.bubblenotify;

import android.app.Application;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/* JADX INFO: loaded from: classes.dex */
public class BubbleNotifyModule extends XposedModule {
    private volatile boolean initialized = false;
    private volatile boolean inCallUiInitialized = false;

    public void onPackageReady(XposedModuleInterface.PackageReadyParam packageReadyParam) {
        String packageName = packageReadyParam.getPackageName();
        if (Settings.SYSTEMUI_PACKAGE.equals(packageName)) {
            initSystemUi(packageReadyParam);
            return;
        }
        // fixed32: also load in the InCallUI process to hide the system
        // incoming-call fullscreen page (ported from Flyme Island
        // InCallUiHooks). The user must scope this module to the Phone
        // app (com.android.incallui) in LSPosed, or this never runs.
        if (Settings.INCALLUI_PACKAGE.equals(packageName)) {
            log(4, Settings.TAG, "Loaded in " + packageName + " (" + packageReadyParam.getApplicationInfo().processName + ")");
            FileLog.i("Loaded in " + packageName + " (" + packageReadyParam.getApplicationInfo().processName + ")");
            try {
                hook(Class.forName("android.app.Application").getMethod("onCreate", new Class[0])).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.BubbleNotifyModule.2
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object objProceed = chain.proceed();
                        try {
                            Object thisObject = chain.getThisObject();
                            if (thisObject instanceof Application) {
                                BubbleNotifyModule.this.tryInitInCallUi((Application) thisObject);
                            }
                        } catch (Throwable th) {
                            BubbleNotifyModule.this.log(6, Settings.TAG, "Error in onCreate hook (incallui)", th);
                        }
                        return objProceed;
                    }
                });
                log(4, Settings.TAG, "Application.onCreate hook installed (incallui).");
            } catch (Throwable th) {
                log(6, Settings.TAG, "Failed to hook Application.onCreate (incallui)", th);
            }
            try {
                Object objInvoke = Class.forName("android.app.ActivityThread").getMethod("currentApplication", new Class[0]).invoke(null, new Object[0]);
                if (objInvoke instanceof Application) {
                    tryInitInCallUi((Application) objInvoke);
                }
            } catch (Throwable th2) {
            }
            return;
        }
        detach();
    }

    private void initSystemUi(XposedModuleInterface.PackageReadyParam packageReadyParam) {
        String packageName = packageReadyParam.getPackageName();
        log(4, Settings.TAG, "Loaded in " + packageName + " (" + packageReadyParam.getApplicationInfo().processName + ")");
            FileLog.i("Loaded in " + packageName + " (" + packageReadyParam.getApplicationInfo().processName + ")");
        try {
            hook(Class.forName("android.app.Application").getMethod("onCreate", new Class[0])).intercept(new XposedInterface.Hooker() { // from class: com.jarvis.bubblenotify.BubbleNotifyModule.1
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object objProceed = chain.proceed();
                    try {
                        Object thisObject = chain.getThisObject();
                        if (thisObject instanceof Application) {
                            BubbleNotifyModule.this.tryInit((Application) thisObject);
                        }
                    } catch (Throwable th) {
                        BubbleNotifyModule.this.log(6, Settings.TAG, "Error in onCreate hook", th);
                    }
                    return objProceed;
                }
            });
            log(4, Settings.TAG, "Application.onCreate hook installed.");
        } catch (Throwable th) {
            log(6, Settings.TAG, "Failed to hook Application.onCreate", th);
        }
        try {
            Object objInvoke = Class.forName("android.app.ActivityThread").getMethod("currentApplication", new Class[0]).invoke(null, new Object[0]);
            if (objInvoke instanceof Application) {
                tryInit((Application) objInvoke);
            }
        } catch (Throwable th2) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void tryInit(Application application) {
        if (this.initialized) {
            return;
        }
        if (Settings.SYSTEMUI_PACKAGE.equals(application.getPackageName())) {
            // fixed32: a second SystemUI sub-process (e.g. :remote) would
            // create a second BubbleManager + window, so the same
            // notification pops twice and a card and a docked icon can
            // show at once (screenshot 2084: two independent windows
            // overlap, which a single dock's children cannot do). Only
            // the main SystemUI process inits.
            String proc = application.getApplicationInfo().processName;
            if (!Settings.SYSTEMUI_PACKAGE.equals(proc)) {
                log(4, Settings.TAG, "Skip non-main SystemUI process: " + proc);
                FileLog.i("Skip non-main SystemUI process: " + proc);
                return;
            }
            this.initialized = true;
            log(4, Settings.TAG, "Application: " + application.getClass().getName());
            BubbleManager.getInstance().init(this, application);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: Access modifiers changed from: private */

    public synchronized void tryInitInCallUi(Application application) {
        if (this.inCallUiInitialized) {
            return;
        }
        if (!Settings.INCALLUI_PACKAGE.equals(application.getPackageName())) {
            return;
        }
        this.inCallUiInitialized = true;
        log(4, Settings.TAG, "InCallUI: " + application.getClass().getName());
        try {
            new InCallUiHide(this, application).install();
        } catch (Throwable th) {
            log(6, Settings.TAG, "InCallUiHide install failed", th);
        }
    }
}
