package com.jarvis.bubblenotify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class NotifBubble implements BubbleFeature {
    private static final int AI_CAPSULE_GAP_DP = 6;
    private static final int CARD_GAP_DP = 8;
    private static final int CLOSE_COLOR = -8748153;
    private static final int CODE_TEXT = -7705344;
    private static final long COPY_TIP_MS = 1400;
    private static final long EMPTY_HIDE_MS = 2000;
    private static final long ENTER_ANIM_MS = 220;
    private static final int HANDLE_BG = -1073741825;
    private static final int HANDLE_BORDER = 687865856;
    private static final int HANDLE_TEXT = -14736603;
    private static final int HINT_DISMISS = -1750963;
    private static final int HINT_MINIMIZE = -15961500;
    private static final long LEVEL1_IDLE_MS = 3000;
    private static final int LEVEL_DOCKED = 3;
    private static final int LEVEL_EXPANDED = 1;
    private static final int LEVEL_HIDDEN = 0;
    private static final int LEVEL_MINI = 2;
    private static final long MINI_DOCK_MS = 3000;
    private static final int MINI_WIDTH_DP = 200;
    private static final String PIN_BALLOON_OFF_B64 = "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";
    private static final String PIN_BALLOON_ON_B64 = "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";
    private static final long REPLY_FOCUS_CHECK_MS = 800;
    // Scene guard queue: notifications that arrive during a
    // landscape game/video scene, replayed in order on scene exit.
    // Elements are Bundle (single) or ArrayList<Bundle> (list sync).
    private final ArrayList<Object> suppressedQueue = new ArrayList<>();
    private static final int REPLY_INPUT_BG = 335544320;
    private static final int REPLY_SEND_BG = -15961500;
    private static final long REPOP_GUARD_MS = 5000;
    private static final long SAME_APP_POP_WINDOW_MS = 30000;
    private static final int SMALL_ICON_BG = -13154481;
    private static final int TEXT_DIM = -8748153;
    private static final int TEXT_MAIN = -15065826;
    private static final int TEXT_SECONDARY = -11905960;
    private EditText activeReplyEdit;
    private NotifItem activeReplyItem;
    private final Context ctx;
    private final BubbleHost host;
    private View level1Container;
    private View miniView;
    private Bitmap pinBitmapOff;
    private Bitmap pinBitmapOn;
    private boolean screenOn;
    private final ArrayList<View> dockedPileIcons = new ArrayList<>();
    private final ArrayList<String> dockedPkgs = new ArrayList<>();
    private final ArrayList<NotifItem> items = new ArrayList<>();
    private final Map<String, CodeInfo> codes = new HashMap();
    private final Map<String, String> appLabelCache = new HashMap();
    private int level = 0;
    private boolean fromDocked = false;
    private boolean cardFromFocus = false;
    private String expandedPkg = null;
    private String soloPkg = null;
    private boolean replyFallbackLaunched = false;
    private long lastLevel1ShownAt = 0;
    private final Map<String, Long> lastPopByPkg = new HashMap();
    private long lastPopAt = 0;
    private final Runnable pendingHideRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.1
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (NotifBubble.this.items.isEmpty()) {
                    NotifBubble.this.hideAll();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("pendingHide failed", th);
            }
        }
    };
    private final Runnable toMiniRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.2
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED && !NotifBubble.this.items.isEmpty()) {
                    if (NotifBubble.this.isCardPinned()) {
                        return;
                    }
                    if (NotifBubble.this.activeReplyEdit != null && NotifBubble.this.activeReplyEdit.hasFocus()) {
                        NotifBubble.this.host.getHandler().postDelayed(this, 3000L);
                    } else {
                        NotifBubble.this.showLevel2(false);
                    }
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("toMini failed", th);
            }
        }
    };
    private final Runnable toDockRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.3
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (NotifBubble.this.level == NotifBubble.LEVEL_MINI && !NotifBubble.this.items.isEmpty()) {
                    if (NotifBubble.this.host.isFocusActive() && !NotifBubble.this.host.isFocusView(NotifBubble.this.miniView)) {
                        NotifBubble.this.host.getHandler().postDelayed(this, 3000L);
                        return;
                    }
                    try {
                        FileLog.i("dlDock level=" + NotifBubble.this.level
                                + " anyPinned=" + NotifBubble.this.anyPinned()
                                + " pinnedKeys=" + NotifBubble.this.pinnedKeys.size()
                                + " items=" + NotifBubble.this.items.size());
                    } catch (Throwable ignored) {
                    }
                    if (NotifBubble.this.anyPinned()) {
                        for (ArrayList arrayList : NotifBubble.this.groupItemsByApp()) {
                            if (arrayList != null && !arrayList.isEmpty() && ((NotifItem) arrayList.get(0)).pkg != null && !NotifBubble.this.isPinned((NotifItem) arrayList.get(0))) {
                                NotifBubble.this.dockedHiddenPkgs.add(((NotifItem) arrayList.get(0)).pkg);
                            }
                        }
                        NotifBubble.this.rebuildDocked(false);
                        NotifBubble.this.refreshMini();
                        NotifBubble.this.host.getHandler().postDelayed(this, 3000L);
                        return;
                    }
                    NotifBubble.this.showLevel3();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("toDock failed", th);
            }
        }
    };
    private String overlayDockPkg = null;
    private final Runnable overlayDockRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.4
        @Override // java.lang.Runnable
        public void run() {
            String str;
            try {
                if (NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED && NotifBubble.this.isCardPinned() && (str = NotifBubble.this.overlayDockPkg) != null) {
                    NotifBubble.this.dockedHiddenPkgs.add(str);
                    NotifBubble.this.rebuildDocked(false);
                    NotifBubble.this.applyDockedHidden();
                    NotifBubble.this.host.updateWindowSize();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("overlayDock failed", th);
            }
        }
    };
    private final Runnable landscapeDockRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.5
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (!NotifBubble.this.items.isEmpty() && NotifBubble.this.level == 0) {
                    NotifBubble.this.showLevel3();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("landscapeDock failed", th);
            }
        }
    };
    private final Runnable replyFocusCheck = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.6
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (!NotifBubble.this.replyFallbackLaunched && NotifBubble.this.activeReplyEdit != null && NotifBubble.this.activeReplyItem != null && !NotifBubble.this.activeReplyEdit.hasFocus()) {
                    NotifBubble.this.replyFallbackLaunched = true;
                    NotifBubble.this.launchReplyActivity(NotifBubble.this.activeReplyItem);
                    NotifBubble.this.clearReplyFocus();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("reply focus check failed", th);
            }
        }
    };
    private final HashSet<String> pinnedKeys = new HashSet<>();
    private String pinnedCardPkg = null;
    private final HashSet<String> dockedHiddenPkgs = new HashSet<>();

    private static class NotifItem {
        Bitmap icon;
        int iconAccent;
        boolean iconSmall;
        PendingIntent intent;
        String key;
        String pkg;
        boolean isDownload;
        boolean isDownloadDone;
        int progress;
        boolean progressIndeterminate;
        int progressMax;
        PendingIntent replyIntent;
        String replyKey;
        String text;
        long time;
        String title;

        private NotifItem() {
        }
    }

    private static class CodeInfo {
        String code;
        String kind;

        private CodeInfo() {
        }
    }

    public NotifBubble(BubbleHost bubbleHost) {
        this.screenOn = true;
        this.host = bubbleHost;
        this.ctx = bubbleHost.getApp();
        this.screenOn = bubbleHost.isScreenOn();
    }

    private static boolean looksLikeSummaryOf(NotifItem notifItem, NotifItem notifItem2) {
        String strSummarySender;
        if (notifItem == null || notifItem2 == null) {
            return false;
        }
        try {
            if (notifItem.pkg == null || !notifItem.pkg.equals(notifItem2.pkg) || (strSummarySender = Settings.summarySender(notifItem.text)) == null) {
                return false;
            }
            if (notifItem2.title == null || !notifItem2.title.equals(strSummarySender)) {
                return (notifItem2.text == null || notifItem2.text.isEmpty() || !notifItem.text.contains(notifItem2.text)) ? false : true;
            }
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r6v20, types: [java.lang.Throwable] */
    /* JADX WARN: Type inference failed for: r6v21, types: [java.lang.Throwable] */
    /* JADX WARN: Type inference failed for: r6v22, types: [java.lang.Throwable] */

    public void onNotifyList(ArrayList<Bundle> arrayList) {
        // Scene guard: queue list syncs during a game/video scene
        // too; replayed in order by flushSuppressedQueue().
        if (this.host != null && this.host.isSceneSuppressed() && arrayList != null) {
            synchronized (this.suppressedQueue) {
                this.suppressedQueue.add(new ArrayList<>(arrayList));
                while (this.suppressedQueue.size() > 20) {
                    this.suppressedQueue.remove(0);
                }
            }
            return;
        }
        boolean objArr;
        // fixed217: log what the listener broadcast delivered, so a
        // download that never reaches here is visible.
        try {
            int n = arrayList == null ? -1 : arrayList.size();
            String first = "";
            if (arrayList != null && !arrayList.isEmpty() && arrayList.get(0) != null) {
                Bundle b0 = arrayList.get(0);
                first = " pkg=" + b0.getString(Settings.ITEM_PACKAGE)
                        + " title=" + b0.getString(Settings.ITEM_TITLE)
                        + " isDl=" + b0.getBoolean(Settings.ITEM_IS_DOWNLOAD, false)
                        + " prog=" + b0.getInt(Settings.ITEM_PROGRESS, 0)
                        + "/" + b0.getInt(Settings.ITEM_PROGRESS_MAX, 0);
            }
            FileLog.i("dlList n=" + n + first);
        } catch (Throwable ignored) {
        }
        try {
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            Object[] objArr2 = null;
            NotifItem notifItem = this.items.isEmpty() ? null : this.items.get(0);
            HashSet hashSet = new HashSet();
            for (NotifItem notifItem2 : this.items) {
                if (notifItem2.key != null) {
                    hashSet.add(notifItem2.key);
                }
            }
            this.items.clear();
            if (arrayList != null) {
                for (Bundle bundle : arrayList) {
                    if (bundle != null) {
                        // v203: per-app NEVER rule — filtered out
                        // of the sync, so it never reaches items.
                        if (this.host != null && this.host.appRuleFor(bundle.getString(Settings.ITEM_PACKAGE)) == AppRules.NEVER) {
                            FileLog.i("rule drop (list): pkg=" + bundle.getString(Settings.ITEM_PACKAGE));
                            continue;
                        }
                        NotifItem notifItem3 = new NotifItem();
                        notifItem3.key = bundle.getString(Settings.ITEM_KEY);
                        notifItem3.title = bundle.getString(Settings.ITEM_TITLE);
                        notifItem3.text = bundle.getString(Settings.ITEM_TEXT);
                        notifItem3.pkg = bundle.getString(Settings.ITEM_PACKAGE);
                        notifItem3.isDownload = bundle.getBoolean(Settings.ITEM_IS_DOWNLOAD, false);
                        notifItem3.isDownloadDone = bundle.getBoolean(Settings.ITEM_DOWNLOAD_DONE, false);
                        notifItem3.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
                        notifItem3.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
                        notifItem3.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
                        notifItem3.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
                        try {
                            notifItem3.icon = (Bitmap) bundle.getParcelable(Settings.ITEM_ICON);
                        } catch (Throwable th) {
                        }
                        try {
                            notifItem3.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
                        } catch (Throwable th2) {
                        }
                        try {
                            notifItem3.replyIntent = (PendingIntent) bundle.getParcelable(Settings.ITEM_REPLY_INTENT);
                            notifItem3.replyKey = bundle.getString(Settings.ITEM_REPLY_KEY);
                        } catch (Throwable th3) {
                        }
                        notifItem3.iconSmall = bundle.getBoolean(Settings.ITEM_ICON_SMALL, false);
                        notifItem3.iconAccent = bundle.getInt(Settings.ITEM_ICON_ACCENT, 0);
                        this.items.add(notifItem3);
                    }
                }
            }
            HashSet hashSet2 = new HashSet();
            for (NotifItem notifItem4 : this.items) {
                if (notifItem4.key != null) {
                    hashSet2.add(notifItem4.key);
                }
            }
            this.codes.keySet().retainAll(hashSet2);
            // fixed212: a download auto-pins on pop (2.0 behaviour)
            // so the capsule stays out showing live progress
            // instead of docking after 3s. Same bookkeeping as the
            // code auto-pin below.
            for (NotifItem dlItem : this.items) {
                if (dlItem.isDownload && dlItem.key != null
                        && !this.pinnedKeys.contains(dlItem.key)) {
                    this.pinnedKeys.add(dlItem.key);
                    if (this.level == LEVEL_EXPANDED && this.expandedPkg != null && this.expandedPkg.equals(dlItem.pkg)) {
                        this.pinnedCardPkg = dlItem.pkg;
                    }
                }
            }
            // fixed216: diagnose why the download is not pinned and
            // not showing progress. Log what arrived and what the
            // pin set holds, and pin by pkg+progress as a fallback
            // so the capsule stays even if the isDownload flag did
            // not survive the broadcast.
            for (NotifItem dlItem : this.items) {
                if (dlItem.isDownload || (dlItem.progressMax > 0 && dlItem.progress >= 0)) {
                    try {
                        FileLog.i("dlPin pkg=" + dlItem.pkg
                                + " title=" + dlItem.title
                                + " isDownload=" + dlItem.isDownload
                                + " prog=" + dlItem.progress + "/" + dlItem.progressMax
                                + " pinned=" + this.pinnedKeys.contains(dlItem.key)
                                + " pinnedKeys=" + this.pinnedKeys.size());
                    } catch (Throwable ignored) {
                    }
                    if (dlItem.key != null && !this.pinnedKeys.contains(dlItem.key)) {
                        this.pinnedKeys.add(dlItem.key);
                    }
                }
            }
            // fixed219: completed downloads dismiss after 3s.
            for (NotifItem doneItem : this.items) {
                if (doneItem.isDownloadDone && doneItem.key != null) {
                    scheduleDownloadDoneDismiss(doneItem.key);
                }
            }
            if (this.items.isEmpty()) {
                scheduleEmptyHide();
                return;
            }
            Iterator<NotifItem> it = this.items.iterator();
            while (true) {
                if (!it.hasNext()) {
                    objArr = false;
                    break;
                }
                NotifItem next = it.next();
                if (next.key != null && !hashSet.contains(next.key)) {
                    objArr = true;
                    break;
                }
            }
            if (objArr) {
                NotifItem notifItem5 = this.items.isEmpty() ? null : this.items.get(0);
                if (notifItem5 != null && looksLikeSummaryOf(notifItem5, notifItem)) {
                    refreshCurrentLevel();
                } else if ((this.level == LEVEL_EXPANDED && SystemClock.uptimeMillis() - this.lastLevel1ShownAt < REPOP_GUARD_MS) || SystemClock.uptimeMillis() - this.lastPopAt < REPOP_GUARD_MS) {
                    refreshCurrentLevel();
                } else if (notifItem5 != null && isSameAppRecentPop(notifItem5.pkg)) {
                    refreshCurrentLevel();
                } else {
                    onNewNotification();
                }
                applyEdgeDocking();
                return;
            }
            refreshCurrentLevel();
        } catch (Throwable th4) {
            this.host.log("NotifBubble.onNotifyList failed", th4);
        }
        applyEdgeDocking();
    }

    /** v203: per-app EDGE rule — items of an EDGE app skip the
     * pop and go straight into the docked edge pile, using the
     * same trio dockFromMini() uses (mark pkg docked-hidden,
     * rebuild the pile, refresh the mini row). Pinned items
     * (downloads / codes) are never docked, same as manual dock. */
    private void applyEdgeDocking() {
        try {
            if (this.host == null || this.items.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (NotifItem it : this.items) {
                if (it != null && it.pkg != null
                        && this.host.appRuleFor(it.pkg) == AppRules.EDGE
                        && !isPinned(it)
                        && !this.dockedHiddenPkgs.contains(it.pkg)) {
                    this.dockedHiddenPkgs.add(it.pkg);
                    changed = true;
                }
            }
            if (changed) {
                rebuildDocked(false);
                applyDockedHidden();
                refreshMini();
            }
        } catch (Throwable th) {
            this.host.log("applyEdgeDocking failed", th);
        }
    }

    public boolean hasKey(String str) {
        if (str == null) {
            return false;
        }
        Iterator<NotifItem> it = this.items.iterator();
        while (it.hasNext()) {
            if (str.equals(it.next().key)) {
                return true;
            }
        }
        return false;
    }

    /** Scene guard: replay everything queued during a game/video
     * scene, in arrival order. Called by BubbleManager on scene exit. */
    public void flushSuppressedQueue() {
        ArrayList<Object> copy;
        synchronized (this.suppressedQueue) {
            copy = new ArrayList<>(this.suppressedQueue);
            this.suppressedQueue.clear();
        }
        for (Object entry : copy) {
            try {
                if (entry instanceof Bundle) {
                    onNotifySingle((Bundle) entry);
                } else if (entry instanceof ArrayList) {
                    onNotifyList((ArrayList<Bundle>) entry);
                }
            } catch (Throwable th) {
                this.host.log("flushSuppressedQueue entry failed", th);
            }
        }
    }

    public void onNotifySingle(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        // Scene guard: queue while a landscape game/video scene is
        // active; flushed on scene exit. Same-key singles keep only
        // the latest, like the 2.0 queue.
        if (this.host != null && this.host.isSceneSuppressed()) {
            synchronized (this.suppressedQueue) {
                try {
                    String k = bundle.getString(Settings.ITEM_KEY);
                    if (k != null) {
                        java.util.Iterator<Object> qit = this.suppressedQueue.iterator();
                        while (qit.hasNext()) {
                            Object e = qit.next();
                            if (e instanceof Bundle
                                    && k.equals(((Bundle) e).getString(Settings.ITEM_KEY))) {
                                qit.remove();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                this.suppressedQueue.add(bundle);
                while (this.suppressedQueue.size() > 20) {
                    this.suppressedQueue.remove(0);
                }
            }
            return;
        }
        // v203: per-app NEVER rule — this app's notifications
        // never become bubbles (system notification untouched).
        if (this.host != null && this.host.appRuleFor(bundle.getString(Settings.ITEM_PACKAGE)) == AppRules.NEVER) {
            FileLog.i("rule drop (single): pkg=" + bundle.getString(Settings.ITEM_PACKAGE));
            return;
        }
        try {
            String string = bundle.getString(Settings.ITEM_KEY);
            // fixed217: the hook path never carries the listener's
            // isDownload flag, so a download capsule built here
            // has no percent and never pins. Detect it here: a
            // progress bar plus a download signal means download.
            try {
                String sPkg = bundle.getString(Settings.ITEM_PACKAGE);
                String sTitle = bundle.getString(Settings.ITEM_TITLE);
                String sText = bundle.getString(Settings.ITEM_TEXT);
                int sProg = bundle.getInt(Settings.ITEM_PROGRESS, 0);
                int sProgMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
                boolean sIsDl = bundle.getBoolean(Settings.ITEM_IS_DOWNLOAD, false);
                boolean sSignal = sIsDl;
                if (!sSignal && sTitle != null && sTitle.contains("下载")) {
                    sSignal = true;
                }
                if (!sSignal && sText != null && sText.contains("下载")) {
                    sSignal = true;
                }
                if (!sSignal && sTitle != null && sTitle.contains("安装")) {
                    sSignal = true;
                }
                if (!sSignal && sPkg != null && (sPkg.equals("com.meizu.mstore")
                        || sPkg.contains("download") || sPkg.contains("market"))) {
                    sSignal = true;
                }
                // fixed219: completion words mean done even
                // with no progress extras (the app drops them).
                boolean sComplete = (sTitle != null
                        && (sTitle.contains("下载完成")
                                || sTitle.contains("安装完成")
                                || sTitle.contains("安装成功")
                                || sTitle.contains("已完成")
                                || sTitle.contains("下载完毕")
                                || sTitle.contains("已下载")
                                || sTitle.contains("下载成功")))
                        || (sText != null
                                && (sText.contains("下载完成")
                                        || sText.contains("安装完成")
                                        || sText.contains("安装成功")
                                        || sText.contains("已完成")
                                        || sText.contains("下载完毕")
                                        || sText.contains("已下载")
                                        || sText.contains("下载成功")));
                // fixed220: pct hit 100% IS completion even when
                // the title still says "正在安装" — the store does
                // not rewrite the title. Without this the done
                // dismiss never fires and the capsule stays pinned
                // forever, blocking the edge fan.
                if (!sComplete && sSignal && sProgMax > 0 && sProg >= sProgMax) {
                    sComplete = true;
                }
                boolean sDl = (sSignal && sProgMax > 0) || sComplete;
                FileLog.i("dlSingle pkg=" + sPkg + " title=" + sTitle
                        + " isDl=" + sIsDl + " prog=" + sProg + "/" + sProgMax
                        + " signal=" + sSignal + " dl=" + sDl);
                if (sDl) {
                    if (sComplete) {
                        String doneTitle = (sTitle != null && sTitle.contains("安装")) ? "安装完成" : "下载完成";
                        bundle.putString(Settings.ITEM_TITLE, doneTitle);
                        bundle.putBoolean(Settings.ITEM_DOWNLOAD_DONE, true);
                        bundle.putBoolean(Settings.ITEM_IS_DOWNLOAD, true);
                        try {
                            FileLog.i("dlDoneSingle pkg=" + sPkg + " title=" + doneTitle);
                        } catch (Throwable ignored) {
                        }
                    } else {
                    // Rewrite the title with the percent (the
                    // listener's rewrite never reaches this path).
                    String base = sTitle == null ? "" : sTitle;
                    int pct = sProgMax > 0 ? (sProg * 100) / sProgMax : 0;
                    String stripped = base;
                    int pctIdx = stripped.lastIndexOf(" %");
                    if (pctIdx < 0) {
                        pctIdx = stripped.lastIndexOf("%");
                    }
                    if (pctIdx >= 0) {
                        String tail = stripped.substring(pctIdx).replace("%", "").trim();
                        boolean allDigits = tail.length() > 0;
                        for (int ci = 0; ci < tail.length(); ci++) {
                            if (!Character.isDigit(tail.charAt(ci))) {
                                allDigits = false;
                                break;
                            }
                        }
                        if (allDigits) {
                            stripped = stripped.substring(0, pctIdx).trim();
                        }
                    }
                    bundle.putString(Settings.ITEM_TITLE, stripped + " " + pct + "%");
                    bundle.putBoolean(Settings.ITEM_IS_DOWNLOAD, true);
                    }
                }
            } catch (Throwable ignored) {
            }
            for (NotifItem notifItem : this.items) {
                if (string != null && string.equals(notifItem.key)) {
                    notifItem.title = bundle.getString(Settings.ITEM_TITLE);
                    notifItem.text = bundle.getString(Settings.ITEM_TEXT);
                    notifItem.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
                    notifItem.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
                    notifItem.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
                    notifItem.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
                    try {
                        notifItem.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
                    } catch (Throwable th) {
                    }
                    if (bundle.getBoolean(Settings.ITEM_IS_DOWNLOAD, false)) {
                        notifItem.isDownload = true;
                        if (notifItem.key != null) {
                            this.pinnedKeys.add(notifItem.key);
                        }
                    }
                    if (bundle.getBoolean(Settings.ITEM_DOWNLOAD_DONE, false)) {
                        notifItem.isDownloadDone = true;
                        if (notifItem.key != null) {
                            scheduleDownloadDoneDismiss(notifItem.key);
                        }
                    }
                    refreshCurrentLevel();
                    return;
                }
            }
            NotifItem notifItem2 = new NotifItem();
            notifItem2.key = string;
            notifItem2.title = bundle.getString(Settings.ITEM_TITLE);
            notifItem2.text = bundle.getString(Settings.ITEM_TEXT);
            notifItem2.pkg = bundle.getString(Settings.ITEM_PACKAGE);
            notifItem2.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
            notifItem2.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
            notifItem2.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
            notifItem2.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
            try {
                notifItem2.icon = (Bitmap) bundle.getParcelable(Settings.ITEM_ICON);
            } catch (Throwable th2) {
            }
            try {
                notifItem2.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
            } catch (Throwable th3) {
            }
            try {
                notifItem2.replyIntent = (PendingIntent) bundle.getParcelable(Settings.ITEM_REPLY_INTENT);
                notifItem2.replyKey = bundle.getString(Settings.ITEM_REPLY_KEY);
            } catch (Throwable th4) {
            }
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            notifItem2.isDownload = bundle.getBoolean(Settings.ITEM_IS_DOWNLOAD, false);
            notifItem2.isDownloadDone = bundle.getBoolean(Settings.ITEM_DOWNLOAD_DONE, false);
            if (notifItem2.isDownload && notifItem2.key != null) {
                this.pinnedKeys.add(notifItem2.key);
            }
            if (notifItem2.isDownloadDone && notifItem2.key != null) {
                scheduleDownloadDoneDismiss(notifItem2.key);
            }
            this.items.add(0, notifItem2);
            while (this.items.size() > 8) {
                this.items.remove(this.items.size() - 1);
            }
            onNewNotification();
        } catch (Throwable th5) {
            this.host.log("NotifBubble.onNotifySingle failed", th5);
        }
        applyEdgeDocking();
    }

    public void onTestNotify(String str, String str2, String str3, Bitmap bitmap, PendingIntent pendingIntent) {
        try {
            NotifItem notifItem = new NotifItem();
            notifItem.key = "test:" + SystemClock.uptimeMillis();
            notifItem.title = str;
            notifItem.text = str2;
            notifItem.pkg = str3;
            notifItem.icon = bitmap;
            notifItem.intent = pendingIntent;
            notifItem.time = System.currentTimeMillis();
            this.items.add(0, notifItem);
            while (this.items.size() > 8) {
                this.items.remove(this.items.size() - 1);
            }
            onNewNotification();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onTestNotify failed", th);
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void onTestCode(String str, String str2, String str3, String str4) {
        NotifItem next;
        Object[] objArr;
        if (str4 == null) {
            return;
        }
        try {
            if (str4.isEmpty()) {
                return;
            }
            Iterator<NotifItem> it = this.items.iterator();
            do {
                objArr = null;
                if (!it.hasNext()) {
                    next = null;
                    break;
                }
                next = it.next();
            } while (!"test:code".equals(next.key));
            if (next == null) {
                NotifItem notifItem = new NotifItem();
                notifItem.key = "test:code";
                if (str == null) {
                    str = "测试短信";
                }
                notifItem.title = str;
                if (str2 == null) {
                    str2 = "";
                }
                notifItem.text = str2;
                notifItem.pkg = Settings.MODULE_PACKAGE;
                notifItem.time = System.currentTimeMillis();
                this.items.add(0, notifItem);
                while (this.items.size() > 8) {
                    this.items.remove(this.items.size() - 1);
                }
            } else {
                if (str == null) {
                    str = next.title;
                }
                next.title = str;
                if (str2 == null) {
                    str2 = next.text;
                }
                next.text = str2;
                next.time = System.currentTimeMillis();
            }
            CodeInfo codeInfo = new CodeInfo();
            if (str3 == null) {
                str3 = Settings.CODE_KIND_VERIFY;
            }
            codeInfo.kind = str3;
            codeInfo.code = str4;
            this.codes.put("test:code", codeInfo);
            onNewNotification();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onTestCode failed", th);
        }
    }

    public void onAiCode(String str, String str2, String str3) {
        NotifItem next;
        if (str == null || str2 == null || str3 == null) {
            return;
        }
        try {
            if (str3.isEmpty()) {
                return;
            }
            Iterator<NotifItem> it = this.items.iterator();
            do {
                if (!it.hasNext()) {
                    next = null;
                    break;
                }
                next = it.next();
            } while (!str.equals(next.key));
            if (next == null) {
                return;
            }
            CodeInfo codeInfo = new CodeInfo();
            codeInfo.kind = str2;
            codeInfo.code = str3;
            this.codes.put(str, codeInfo);
            copyCodeToClipboard(str3);
            if (!isPinned(next)) {
                this.pinnedKeys.add(next.key);
                if (this.level == LEVEL_EXPANDED && this.expandedPkg != null && this.expandedPkg.equals(next.pkg)) {
                    this.pinnedCardPkg = next.pkg;
                }
            }
            if (this.level == LEVEL_EXPANDED) {
                refreshLevel1();
            } else if (this.level == LEVEL_MINI) {
                refreshMini();
            }
            if (this.level == LEVEL_EXPANDED) {
                scheduleLevel1Timer();
            } else if (this.level == LEVEL_MINI) {
                scheduleMiniTimer(this.fromDocked);
            }
        } catch (Throwable th) {
            this.host.log("NotifBubble.onAiCode failed", th);
        }
    }

    public void onClearAll() {
        try {
            this.items.clear();
            this.codes.clear();
            hideAll();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onClearAll failed", th);
        }
    }

    public void onFeatureDisabled() {
        onClearAll();
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onScreenChanged(boolean z) {
        this.screenOn = z;
        try {
            if (!z) {
                cancelAllTimers();
            } else if (this.level == LEVEL_EXPANDED) {
                scheduleLevel1Timer();
            } else if (this.level == LEVEL_MINI) {
                scheduleMiniTimer(this.fromDocked);
            }
        } catch (Throwable th) {
            this.host.log("NotifBubble.onScreenChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onConfigurationChanged() {
        try {
            refreshCurrentLevel();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onConfigurationChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onDestroy() {
        try {
            cancelAllTimers();
            this.items.clear();
            this.codes.clear();
            hideAll();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onDestroy failed", th);
        }
    }

    private void onNewNotification() {
        if (this.host.isLandscape()) {
            if (this.level == LEVEL_EXPANDED) {
                refreshLevel1();
                scheduleLevel1Timer();
                return;
            } else if (this.level == LEVEL_MINI) {
                refreshMini();
                return;
            } else if (this.level == 3) {
                refreshDocked();
                return;
            } else {
                scheduleLandscapeDock();
                return;
            }
        }
        String str = null;
        if (this.level == LEVEL_EXPANDED) {
            if (isCardPinned()) {
                if (!this.items.isEmpty() && this.items.get(0).pkg != null) {
                    this.dockedHiddenPkgs.remove(this.items.get(0).pkg);
                }
                this.soloPkg = this.items.isEmpty() ? null : this.items.get(0).pkg;
                rebuildMini(false);
                if (this.miniView != null) {
                    this.miniView.setVisibility(0);
                }
                this.overlayDockPkg = this.soloPkg;
                this.host.getHandler().removeCallbacks(this.overlayDockRunnable);
                this.host.getHandler().postDelayed(this.overlayDockRunnable, 3000L);
                scheduleLevel1Timer();
                return;
            }
            refreshLevel1();
            scheduleLevel1Timer();
            return;
        }
        if (this.level == LEVEL_MINI) {
            if (!this.items.isEmpty() && this.items.get(0).pkg != null) {
                this.dockedHiddenPkgs.remove(this.items.get(0).pkg);
            }
            boolean z = this.host.isFocusActive() && !this.host.isFocusView(this.miniView);
            if (z && !this.items.isEmpty()) {
                str = this.items.get(0).pkg;
            }
            this.soloPkg = str;
            refreshMini();
            if (anyPinned()) {
                scheduleMiniTimer(this.fromDocked);
            }
            if (this.miniView != null && z && !this.host.isFocusCard()) {
                this.host.setFocusView(this.miniView);
                return;
            }
            return;
        }
        if (this.level == 3) {
            this.soloPkg = this.items.isEmpty() ? null : this.items.get(0).pkg;
            showLevel2(false);
            return;
        }
        NotifItem notifItem = this.items.isEmpty() ? null : this.items.get(0);
        FileLog.i("POP new notification topKey=" + (notifItem == null ? null : notifItem.key) + " topPkg=" + (notifItem != null ? notifItem.pkg : null) + " items=" + this.items.size());
        recordPop();
        showLevel2(false);
    }

    private boolean isSameAppRecentPop(String str) {
        if (str == null) {
            return false;
        }
        try {
            Long l = this.lastPopByPkg.get(str);
            return l != null && SystemClock.uptimeMillis() - l.longValue() < SAME_APP_POP_WINDOW_MS;
        } catch (Throwable th) {
            return false;
        }
    }

    private void recordPop() {
        String str;
        try {
            long jUptimeMillis = SystemClock.uptimeMillis();
            this.lastPopAt = jUptimeMillis;
            if (!this.items.isEmpty() && (str = this.items.get(0).pkg) != null) {
                this.lastPopByPkg.put(str, Long.valueOf(jUptimeMillis));
                if (this.lastPopByPkg.size() > 32) {
                    Iterator<Map.Entry<String, Long>> it = this.lastPopByPkg.entrySet().iterator();
                    while (it.hasNext()) {
                        if (jUptimeMillis - it.next().getValue().longValue() > SAME_APP_POP_WINDOW_MS) {
                            it.remove();
                        }
                    }
                }
            }
        } catch (Throwable th) {
        }
    }

    public void showLevel1() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        this.level = LEVEL_EXPANDED;
        this.lastLevel1ShownAt = SystemClock.uptimeMillis();
        this.fromDocked = false;
        this.cardFromFocus = this.host.isFocusView(this.miniView);
        removeMiniView();
        removeDockedView();
        rebuildLevel1(true);
        scheduleLevel1Timer();
        enforceSingleLevelView();
    }

    public void showLevel2(boolean z) {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        int i = this.level;
        if (this.level == LEVEL_EXPANDED && this.expandedPkg != null) {
            this.expandedPkg = null;
        }
        if (this.level == LEVEL_MINI) {
            refreshMini();
            enforceSingleLevelView();
            return;
        }
        clearReplyFocus();
        this.level = LEVEL_MINI;
        this.fromDocked = z;
        removeDockedView();
        final View view = this.level1Container;
        if (view != null && view.getParent() != null && view.getWidth() > 0 && view.getHeight() > 0) {
            int[] iArr = new int[LEVEL_MINI];
            view.getLocationOnScreen(iArr);
            float fDp = (this.ctx.getResources().getDisplayMetrics().heightPixels - this.host.dp(14)) - (iArr[LEVEL_EXPANDED] + view.getHeight());
            if (fDp < 0.0f) {
                fDp = 0.0f;
            }
            float width = view.getWidth() * 0.33999997f;
            float height = (view.getHeight() * 0.33999997f) + fDp;
            view.animate().cancel();
            view.setPivotX(0.0f);
            view.setPivotY(0.0f);
            view.setTranslationX(0.0f);
            view.setTranslationY(0.0f);
            view.animate().withLayer().scaleX(0.66f).scaleY(0.66f).translationX(width).translationY(height).alpha(0.0f).setDuration(ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.7
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    try {
                        view.setTranslationX(0.0f);
                        view.setTranslationY(0.0f);
                        view.setScaleX(1.0f);
                        view.setScaleY(1.0f);
                        view.setAlpha(1.0f);
                        if (NotifBubble.this.level != NotifBubble.LEVEL_MINI) {
                            return;
                        }
                        if (NotifBubble.this.level1Container == view) {
                            NotifBubble.this.removeLevel1View();
                        }
                        if (!NotifBubble.this.items.isEmpty() && NotifBubble.this.miniView == null) {
                            NotifBubble.this.rebuildMini(false);
                        }
                        boolean z2 = NotifBubble.this.cardFromFocus ? false : true;
                        if (!NotifBubble.this.fromDocked && NotifBubble.this.miniView != null && !z2 && !NotifBubble.this.host.isFocusCard()) {
                            NotifBubble.this.host.setFocusView(NotifBubble.this.miniView);
                        }
                        NotifBubble.this.scheduleMiniTimer(NotifBubble.this.fromDocked);
                        NotifBubble.this.enforceSingleLevelView();
                        NotifBubble.this.host.updateWindowSize();
                    } catch (Throwable th) {
                    }
                }
            }).start();
            return;
        }
        removeLevel1View();
        rebuildMini(true);
        boolean z2 = i == LEVEL_EXPANDED && !this.cardFromFocus;
        if (!this.fromDocked && this.miniView != null && !z2 && !this.host.isFocusCard()) {
            this.host.setFocusView(this.miniView);
        }
        scheduleMiniTimer(this.fromDocked);
        enforceSingleLevelView();
    }

    public void expandFromDock() {
        if (this.items.isEmpty()) {
            return;
        }
        if (this.level == 3) {
            this.soloPkg = null;
            showLevel2(true);
        } else if (this.level == LEVEL_MINI && !this.dockedHiddenPkgs.isEmpty()) {
            this.soloPkg = null;
            this.dockedHiddenPkgs.clear();
            removeDockedView();
            showLevel2(true);
            scheduleMiniTimer(false);
        }
    }

    boolean ownsView(View view) {
        return view != null && (view == this.miniView || view == this.level1Container);
    }

    public void dockFromMini() {
        if (this.level != LEVEL_MINI || this.items.isEmpty()) {
            return;
        }
        if (anyPinned()) {
            for (ArrayList<NotifItem> arrayList : groupItemsByApp()) {
                if (arrayList != null && !arrayList.isEmpty() && arrayList.get(0).pkg != null && !isPinned(arrayList.get(0))) {
                    this.dockedHiddenPkgs.add(arrayList.get(0).pkg);
                }
            }
            rebuildDocked(false);
            refreshMini();
            scheduleMiniTimer(this.fromDocked);
            return;
        }
        showLevel3();
    }

    public void showLevel3() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        this.expandedPkg = null;
        this.pinnedCardPkg = null;
        this.soloPkg = null;
        this.dockedHiddenPkgs.clear();
        clearReplyFocus();
        this.level = 3;
        removeLevel1View();
        removeMiniView();
        rebuildDocked(true);
        cancelAllTimers();
        enforceSingleLevelView();
        this.host.dockPlayerTogether();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideAll() {
        this.level = 0;
        this.fromDocked = false;
        this.expandedPkg = null;
        this.pinnedCardPkg = null;
        this.soloPkg = null;
        this.dockedHiddenPkgs.clear();
        clearReplyFocus();
        cancelAllTimers();
        try {
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
        } catch (Throwable th) {
        }
        removeLevel1View();
        removeMiniView();
        removeDockedView();
    }

    private void scheduleEmptyHide() {
        try {
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            this.host.getHandler().postDelayed(this.pendingHideRunnable, EMPTY_HIDE_MS);
        } catch (Throwable th) {
            this.host.log("scheduleEmptyHide failed", th);
            hideAll();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enforceSingleLevelView() {
        try {
            if (this.level == LEVEL_EXPANDED) {
                if (!isCardPinned()) {
                    removeMiniView();
                }
                removeDockedView();
            } else {
                if (this.level == LEVEL_MINI) {
                    removeLevel1View();
                    if (!anyPinned()) {
                        removeDockedView();
                        return;
                    }
                    return;
                }
                if (this.level == 3) {
                    removeLevel1View();
                    removeMiniView();
                } else {
                    removeLevel1View();
                    removeMiniView();
                    removeDockedView();
                }
            }
        } catch (Throwable th) {
            this.host.log("enforceSingleLevelView failed", th);
        }
    }

    private void refreshCurrentLevel() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        switch (this.level) {
            case LEVEL_EXPANDED /* 1 */:
                refreshLevel1();
                break;
            case LEVEL_MINI /* 2 */:
                refreshMini();
                break;
            case 3:
                refreshDocked();
                break;
            default:
                showLevel2Silent();
                break;
        }
        enforceSingleLevelView();
    }

    private void showLevel2Silent() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        clearReplyFocus();
        this.level = LEVEL_MINI;
        this.fromDocked = false;
        removeLevel1View();
        removeDockedView();
        rebuildMini(false);
        if (this.miniView != null && !this.host.isFocusCard()) {
            this.host.setFocusView(this.miniView);
        }
        scheduleMiniTimer(this.fromDocked);
        enforceSingleLevelView();
    }

    private void refreshLevel1() {
        if (this.level != LEVEL_EXPANDED || isCardPinned()) {
            return;
        }
        if (this.activeReplyEdit != null && this.activeReplyEdit.hasFocus()) {
            return;
        }
        rebuildLevel1(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshMini() {
        if (this.level != LEVEL_MINI) {
            return;
        }
        boolean z = this.miniView != null && this.host.isFocusView(this.miniView);
        rebuildMini(false);
        if (z && this.miniView != null) {
            this.host.setFocusView(this.miniView);
        }
    }

    private void refreshDocked() {
        if (this.level != 3) {
            return;
        }
        rebuildDocked(false);
    }

    public void scheduleLevel1Timer() {
        this.host.getHandler().removeCallbacks(this.toMiniRunnable);
        if (!isPinned(currentCardItem()) && this.screenOn && this.level == LEVEL_EXPANDED) {
            this.host.getHandler().postDelayed(this.toMiniRunnable, 3000L);
        }
    }

    private long miniDockDelayMs() {
        String str;
        if (isCardExpand()) {
            return 3000L;
        }
        try {
            float f = this.ctx.getResources().getDisplayMetrics().density;
            Paint paint = new Paint();
            paint.setTextSize(11.0f * f);
            long j = 0;
            for (NotifItem notifItem : this.items) {
                CodeInfo codeInfo = notifItem.key != null ? this.codes.get(notifItem.key) : null;
                if (codeInfo != null) {
                    str = codeInfo.kind + " " + codeInfo.code;
                } else if (notifItem.title != null && !notifItem.title.isEmpty()) {
                    str = notifItem.title;
                } else {
                    str = notifItem.text != null ? notifItem.text : "";
                }
                float fMeasureText = (paint.measureText(str) / f) - 90.0f;
                if (fMeasureText > 0.0f) {
                    long j2 = (long) (fMeasureText * 40.0f);
                    if (j2 > j) {
                        j = j2;
                    }
                }
            }
            if (j > 12000) {
                j = 12000;
            }
            return j + 3000;
        } catch (Throwable th) {
            return 3000L;
        }
    }

    public void scheduleMiniTimer(boolean z) {
        this.host.getHandler().removeCallbacks(this.toDockRunnable);
        if (this.screenOn && this.level == LEVEL_MINI) {
            this.host.getHandler().postDelayed(this.toDockRunnable, miniDockDelayMs());
        }
    }

    private void scheduleLandscapeDock() {
        this.host.getHandler().removeCallbacks(this.landscapeDockRunnable);
        if (this.screenOn) {
            this.host.getHandler().postDelayed(this.landscapeDockRunnable, 600L);
        }
    }

    private void cancelAllTimers() {
        this.host.getHandler().removeCallbacks(this.toMiniRunnable);
        this.host.getHandler().removeCallbacks(this.toDockRunnable);
        this.host.getHandler().removeCallbacks(this.landscapeDockRunnable);
    }

    private final HashSet<String> downloadDonePending = new HashSet<>();

    // fixed219: a completed download shows "下载完成"/"安装完成"
    // for 3s, then the capsule is dismissed (unpinned). One
    // pending dismiss per key; re-posts of the same done state
    // do not stack more timers.
    private void scheduleDownloadDoneDismiss(final String key) {
        if (key == null) {
            return;
        }
        try {
            if (this.downloadDonePending.contains(key)) {
                return;
            }
            this.downloadDonePending.add(key);
            this.host.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        NotifBubble.this.downloadDonePending.remove(key);
                        NotifBubble.this.dismissItem(key);
                    } catch (Throwable ignored) {
                    }
                }
            }, 3000L);
        } catch (Throwable ignored) {
        }
    }

    public void dismissItem(String str) {
        if (str == null) {
            return;
        }
        boolean z = false;
        for (int size = this.items.size() - LEVEL_EXPANDED; size >= 0; size--) {
            if (str.equals(this.items.get(size).key)) {
                this.items.remove(size);
                z = true;
            }
        }
        this.codes.remove(str);
        if (!z) {
            return;
        }
        if (!this.pinnedKeys.isEmpty() || !this.dockedHiddenPkgs.isEmpty()) {
            HashSet hashSet = new HashSet();
            HashSet hashSet2 = new HashSet();
            for (NotifItem notifItem : this.items) {
                if (notifItem.key != null) {
                    hashSet.add(notifItem.key);
                }
                if (notifItem.pkg != null) {
                    hashSet2.add(notifItem.pkg);
                }
            }
            this.pinnedKeys.retainAll(hashSet);
            this.dockedHiddenPkgs.retainAll(hashSet2);
        }
        if (!str.startsWith("test:")) {
            this.host.sendDismiss(str);
        }
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        refreshCurrentLevel();
        if (this.level == LEVEL_EXPANDED) {
            scheduleLevel1Timer();
        } else if (this.level == LEVEL_MINI) {
            scheduleMiniTimer(this.fromDocked);
        }
    }


    /* JADX INFO: Access modifiers changed from: private */
    public void applyDockedHidden() {
        if (this.miniView == null) {
            return;
        }
        try {
            ViewGroup viewGroup = (ViewGroup) this.miniView;
            for (int i = 0; i < viewGroup.getChildCount(); i += LEVEL_EXPANDED) {
                View childAt = viewGroup.getChildAt(i);
                Object tag = childAt.getTag();
                if ((tag instanceof String) && this.dockedHiddenPkgs.contains((String) tag)) {
                    childAt.setVisibility(8);
                }
            }
        } catch (Throwable th) {
            this.host.log("applyDockedHidden failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isPinned(NotifItem notifItem) {
        return (notifItem == null || notifItem.key == null || !this.pinnedKeys.contains(notifItem.key)) ? false : true;
    }

    public boolean anyPinned() {
        Iterator<NotifItem> it = this.items.iterator();
        while (it.hasNext()) {
            if (isPinned(it.next())) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isCardPinned() {
        if (this.level != LEVEL_EXPANDED) {
            return false;
        }
        String str = this.pinnedCardPkg != null ? this.pinnedCardPkg : this.expandedPkg;
        if (str != null) {
            for (NotifItem notifItem : this.items) {
                if (str.equals(notifItem.pkg) && isPinned(notifItem)) {
                    return true;
                }
            }
            return false;
        }
        return isPinned(currentCardItem());
    }

    private String currentCardPkg() {
        if (this.expandedPkg != null) {
            return this.expandedPkg;
        }
        if (!this.items.isEmpty()) {
            return this.items.get(0).pkg;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public NotifItem currentCardItem() {
        if (this.expandedPkg != null) {
            for (NotifItem notifItem : this.items) {
                if (this.expandedPkg.equals(notifItem.pkg)) {
                    return notifItem;
                }
            }
        }
        if (!this.items.isEmpty()) {
            return this.items.get(0);
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unpinForDock(NotifItem notifItem) {
        if (notifItem == null || notifItem.key == null) {
            return;
        }
        this.pinnedKeys.remove(notifItem.key);
        if (notifItem.pkg != null && notifItem.pkg.equals(this.pinnedCardPkg)) {
            this.pinnedCardPkg = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void togglePin(NotifItem notifItem) {
        if (notifItem == null || notifItem.key == null) {
            return;
        }
        boolean zIsCardPinned = isCardPinned();
        NotifItem notifItemCurrentCardItem = currentCardItem();
        if (this.pinnedKeys.contains(notifItem.key)) {
            this.pinnedKeys.remove(notifItem.key);
        } else {
            this.pinnedKeys.add(notifItem.key);
        }
        if (this.level == LEVEL_EXPANDED && this.expandedPkg != null && this.expandedPkg.equals(notifItem.pkg)) {
            if (this.pinnedKeys.contains(notifItem.key)) {
                this.pinnedCardPkg = notifItem.pkg;
            } else if (notifItem.pkg != null && notifItem.pkg.equals(this.pinnedCardPkg)) {
                this.pinnedCardPkg = null;
            }
        }
        if (zIsCardPinned && notifItemCurrentCardItem != null && notifItemCurrentCardItem.key != null && notifItemCurrentCardItem.key.equals(notifItem.key) && !isPinned(notifItem)) {
            removeMiniView();
        }
        refreshCurrentLevel();
        try {
            this.host.repositionPile();
        } catch (Throwable th) {
        }
        if (this.level == LEVEL_EXPANDED) {
            scheduleLevel1Timer();
        } else if (this.level == LEVEL_MINI) {
            scheduleMiniTimer(this.fromDocked);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Bitmap pinBitmap(boolean z) {
        try {
            if (z) {
                if (this.pinBitmapOn == null) {
                    byte[] bArrDecode = Base64.decode(PIN_BALLOON_ON_B64, 0);
                    this.pinBitmapOn = BitmapFactory.decodeByteArray(bArrDecode, 0, bArrDecode.length);
                }
                if (this.pinBitmapOn != null) {
                    return this.pinBitmapOn;
                }
            } else {
                if (this.pinBitmapOff == null) {
                    byte[] bArrDecode2 = Base64.decode(PIN_BALLOON_OFF_B64, 0);
                    this.pinBitmapOff = BitmapFactory.decodeByteArray(bArrDecode2, 0, bArrDecode2.length);
                }
                if (this.pinBitmapOff != null) {
                    return this.pinBitmapOff;
                }
            }
        } catch (Throwable th) {
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(LEVEL_EXPANDED);
        paint.setColor(z ? -16736023 : -6908261);
        canvas.drawCircle(48.0f, 48.0f, 40.0f, paint);
        return bitmapCreateBitmap;
    }

    private void copyCodeToClipboard(String str) {
        ClipboardManager clipboardManager;
        if (str == null) {
            return;
        }
        try {
            if (!str.isEmpty() && (clipboardManager = (ClipboardManager) this.ctx.getSystemService("clipboard")) != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText(Settings.EXTRA_CODE, str));
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void copyCodeAndDismiss(String str, final String str2, final TextView textView) {
        try {
            copyCodeToClipboard(str);
            textView.setText("已复制 ✓");
            textView.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.8
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        textView.setText("复制");
                    } catch (Throwable th) {
                    }
                }
            }, COPY_TIP_MS);
            textView.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.9
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        NotifBubble.this.dismissItem(str2);
                        if (!NotifBubble.this.items.isEmpty()) {
                            NotifBubble.this.showLevel2(false);
                        }
                    } catch (Throwable th) {
                        NotifBubble.this.host.log("copy dismiss failed", th);
                    }
                }
            }, 600L);
        } catch (Throwable th) {
            this.host.log("copy code failed", th);
        }
    }

    private TextView buildCopyPill(final String str, final String str2, boolean z) {
        final TextView textView = new TextView(this.ctx);
        textView.setText("复制");
        float f = z ? 12.0f : 12.5f;
        int i = LEVEL_MINI;
        textView.setTextSize(LEVEL_MINI, f);
        textView.setTextColor(CODE_TEXT);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(620738381);
        gradientDrawable.setCornerRadius(this.host.dp(8));
        gradientDrawable.setStroke(this.host.dp(LEVEL_EXPANDED), -2130725043);
        textView.setBackground(gradientDrawable);
        if (!z) {
            textView.setMinHeight(this.host.dp(36));
        }
        int iDp = this.host.dp(z ? 8 : 12);
        int iDp2 = this.host.dp(z ? LEVEL_MINI : AI_CAPSULE_GAP_DP);
        int iDp3 = this.host.dp(z ? 8 : 12);
        BubbleHost bubbleHost = this.host;
        if (!z) {
            i = AI_CAPSULE_GAP_DP;
        }
        textView.setPadding(iDp, iDp2, iDp3, bubbleHost.dp(i));
        textView.setClickable(true);
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                NotifBubble.this.copyCodeAndDismiss(str, str2, textView);
            }
        });
        return textView;
    }

    private ImageView buildPinView(final NotifItem notifItem) {
        ImageView imageView = new ImageView(this.ctx);
        imageView.setImageBitmap(pinBitmap(isPinned(notifItem)));
        imageView.setClickable(true);
        imageView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    NotifBubble.this.togglePin(notifItem);
                    if (view instanceof ImageView) {
                        ((ImageView) view).setImageBitmap(NotifBubble.this.pinBitmap(NotifBubble.this.isPinned(notifItem)));
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("pin click failed", th);
                }
            }
        });
        return imageView;
    }

    private int bubbleWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).cardWidthDp();
            }
        } catch (Throwable th) {
        }
        if (this.host.isLandscape()) {
            return Settings.BUBBLE_WIDTH_LANDSCAPE_DP;
        }
        return 302;
    }

    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:596)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    private void rebuildLevel1(boolean z) {
        removeLevel1View();
        if (this.items.isEmpty()) {
            return;
        }
        ArrayList<NotifItem> arrayList = this.items;
        if (this.expandedPkg != null) {
            ArrayList<NotifItem> arrayList2 = new ArrayList<>();
            for (NotifItem notifItem : this.items) {
                if (this.expandedPkg.equals(notifItem.pkg)) {
                    arrayList2.add(notifItem);
                }
            }
            if (!arrayList2.isEmpty()) {
                arrayList = arrayList2;
            } else {
                this.expandedPkg = null;
            }
        }
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(LEVEL_EXPANDED);
        linearLayout.setClipChildren(false);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(this.host.dp(bubbleWidthDp()), -2));
        int iMin = Math.min(arrayList.size(), 3);
        for (int i = 0; i < iMin; i += LEVEL_EXPANDED) {
            View viewBuildCard = buildCard(arrayList.get(i));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) {
                layoutParams.topMargin = this.host.dp(8);
            }
            linearLayout.addView(viewBuildCard, layoutParams);
        }
        if (arrayList.size() > 3) {
            TextView textView = new TextView(this.ctx);
            textView.setText("还有 " + (arrayList.size() - 3) + " 条较早的通知");
            textView.setTextSize(LEVEL_MINI, 11.5f);
            textView.setTextColor(this.host.textDimColor());
            textView.setGravity(17);
            textView.setPadding(0, this.host.dp(AI_CAPSULE_GAP_DP), 0, this.host.dp(LEVEL_MINI));
            linearLayout.addView(textView);
        }
        View viewCapCardHeight = this.host.capCardHeight(linearLayout);
        this.level1Container = viewCapCardHeight;
        this.host.addDockViewAtBottom(viewCapCardHeight);
        this.host.setFocusView(viewCapCardHeight);
        if (z) {
            animateIn(viewCapCardHeight);
        }
        this.host.updateWindowSize();
        this.host.setCardOpen(true);
        try {
            this.host.getHandler().post(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.12
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        NotifBubble.this.host.applyCardBottomOffset();
                    } catch (Throwable th) {
                    }
                }
            });
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeLevel1View() {
        this.host.setCardOpen(false);
        if (this.level1Container != null) {
            this.host.removeDockView(this.level1Container);
            this.level1Container = null;
        }
    }





    private View buildCard(final NotifItem notifItem) {
        boolean tmpDockLeft = false;
        try {
            tmpDockLeft = this.host.isDockLeft();
        } catch (Throwable th) {
        }
        final boolean zIsDockLeft = tmpDockLeft;
        FrameLayout frameLayout = new FrameLayout(this.ctx);
        frameLayout.setClipChildren(false);
        final TextView textView = new TextView(this.ctx);
        textView.setText(zIsDockLeft ? "‹ 缩小" : "缩小 ›");
        textView.setTextSize(LEVEL_MINI, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-15961500);
        textView.setGravity(8388627);
        textView.setPadding(this.host.dp(14), 0, 0, 0);
        textView.setVisibility(8);
        frameLayout.addView(textView, new FrameLayout.LayoutParams(-1, -1));
        final TextView textView2 = new TextView(this.ctx);
        textView2.setText(zIsDockLeft ? "取消 ›" : "‹ 取消");
        textView2.setTextSize(LEVEL_MINI, 13.0f);
        textView2.setTypeface(Typeface.DEFAULT_BOLD);
        textView2.setTextColor(HINT_DISMISS);
        textView2.setGravity(8388629);
        textView2.setPadding(0, 0, this.host.dp(14), 0);
        textView2.setVisibility(8);
        frameLayout.addView(textView2, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(LEVEL_EXPANDED);
        final GradientDrawable gradientDrawableFrostedBackground = this.host.frostedBackground(20);
        linearLayout.setBackground(gradientDrawableFrostedBackground);
        linearLayout.setPadding(this.host.dp(13), this.host.dp(12), this.host.dp(13), this.host.dp(11));
        LinearLayout linearLayout2 = new LinearLayout(this.ctx);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.addView(buildIconView(notifItem, 34, 10, false));
        TextView textView3 = new TextView(this.ctx);
        textView3.setText(appLabel(notifItem.pkg));
        textView3.setTextSize(LEVEL_MINI, 12.0f);
        textView3.setTextColor(this.host.textSubColor());
        textView3.setSingleLine(true);
        textView3.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = this.host.dp(8);
        linearLayout2.addView(textView3, layoutParams);
        TextView textView4 = new TextView(this.ctx);
        textView4.setText(formatTime(notifItem.time));
        textView4.setTextSize(LEVEL_MINI, 11.0f);
        textView4.setTextColor(this.host.textDimColor());
        linearLayout2.addView(textView4);
        CodeInfo codeInfo = notifItem.key != null ? this.codes.get(notifItem.key) : null;
        if (codeInfo != null && Settings.CODE_KIND_VERIFY.equals(codeInfo.kind)) {
            TextView textViewBuildCopyPill = buildCopyPill(codeInfo.code, notifItem.key, false);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(this.host.dp(52), -2);
            layoutParams2.leftMargin = this.host.dp(4);
            linearLayout2.addView(textViewBuildCopyPill, layoutParams2);
        } else {
            ImageView imageViewBuildPinView = buildPinView(notifItem);
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(this.host.dp(28), this.host.dp(28));
            layoutParams3.leftMargin = this.host.dp(4);
            linearLayout2.addView(imageViewBuildPinView, layoutParams3);
        }
        TextView textView5 = new TextView(this.ctx);
        textView5.setText("✕");
        textView5.setTextSize(LEVEL_MINI, 17.0f);
        textView5.setTextColor(this.host.textDimColor());
        textView5.setGravity(17);
        textView5.setClickable(true);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(this.host.dp(36), this.host.dp(36));
        layoutParams4.leftMargin = this.host.dp(4);
        textView5.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    NotifBubble.this.dismissItem(notifItem.key);
                    if (!NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel2(false);
                    }
                } catch (Throwable th2) {
                    NotifBubble.this.host.log("close click failed", th2);
                }
            }
        });
        linearLayout2.addView(textView5, layoutParams4);
        linearLayout.addView(linearLayout2);
        if (codeInfo != null) {
            TextView textView6 = new TextView(this.ctx);
            textView6.setText(codeInfo.kind);
            textView6.setTextSize(LEVEL_MINI, 12.0f);
            textView6.setTextColor(this.host.textDimColor());
            LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams5.topMargin = this.host.dp(8);
            linearLayout.addView(textView6, layoutParams5);
            LinearLayout linearLayout3 = new LinearLayout(this.ctx);
            linearLayout3.setOrientation(0);
            linearLayout3.setGravity(16);
            TextView textView7 = new TextView(this.ctx);
            textView7.setText(codeInfo.code);
            textView7.setTextSize(LEVEL_MINI, 24.0f);
            textView7.setTypeface(Typeface.DEFAULT_BOLD);
            textView7.setTextColor(CODE_TEXT);
            textView7.setLetterSpacing(0.12f);
            textView7.setSingleLine(true);
            textView7.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout3.addView(textView7, new LinearLayout.LayoutParams(0, -2, 1.0f));
            if (!Settings.CODE_KIND_VERIFY.equals(codeInfo.kind)) {
                TextView textViewBuildCopyPill2 = buildCopyPill(codeInfo.code, notifItem.key, false);
                LinearLayout.LayoutParams layoutParams6 = new LinearLayout.LayoutParams(-2, -2);
                layoutParams6.leftMargin = this.host.dp(8);
                linearLayout3.addView(textViewBuildCopyPill2, layoutParams6);
            }
            LinearLayout.LayoutParams layoutParams7 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams7.topMargin = this.host.dp(4);
            linearLayout.addView(linearLayout3, layoutParams7);
        }
        if (codeInfo == null && notifItem.title != null && !notifItem.title.isEmpty()) {
            TextView textView8 = new TextView(this.ctx);
            textView8.setText(notifItem.title);
            textView8.setTextSize(LEVEL_MINI, 14.5f);
            textView8.setTypeface(Typeface.DEFAULT_BOLD);
            textView8.setTextColor(this.host.textMainColor());
            textView8.setMaxLines(LEVEL_MINI);
            textView8.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams layoutParams8 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams8.topMargin = this.host.dp(8);
            linearLayout.addView(textView8, layoutParams8);
        }
        if (codeInfo == null && notifItem.text != null && !notifItem.text.isEmpty()) {
            TextView textView9 = new TextView(this.ctx);
            textView9.setText(notifItem.text);
            textView9.setTextSize(LEVEL_MINI, 13.0f);
            textView9.setTextColor(this.host.textSubColor());
            textView9.setLineSpacing(0.0f, 1.5f);
            textView9.setMaxLines(3);
            textView9.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams layoutParams9 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams9.topMargin = this.host.dp(4);
            linearLayout.addView(textView9, layoutParams9);
        }
        if (codeInfo == null && ((notifItem.title == null || notifItem.title.isEmpty()) && (notifItem.text == null || notifItem.text.isEmpty()))) {
            TextView textView10 = new TextView(this.ctx);
            textView10.setText("点击卡片打开查看");
            textView10.setTextSize(LEVEL_MINI, 13.0f);
            textView10.setTextColor(this.host.textSubColor());
            LinearLayout.LayoutParams layoutParams10 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams10.topMargin = this.host.dp(8);
            linearLayout.addView(textView10, layoutParams10);
        }
        if (notifItem.replyIntent != null && notifItem.replyKey != null) {
            linearLayout.addView(buildReplyRow(notifItem));
        }
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        frameLayout.setOnTouchListener(new SwipeListener(linearLayout, true, true, new Callback() { // from class: com.jarvis.bubblenotify.NotifBubble.14
            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onTap() {
                try {
                    if (!NotifBubble.this.isTapOpen()) {
                        return;
                    }
                    try {
                        if (notifItem.intent != null) {
                            NotifBubble.this.host.openPendingIntent(notifItem.intent);
                        }
                    } finally {
                        NotifBubble.this.dismissItem(notifItem.key);
                        if (!NotifBubble.this.items.isEmpty()) {
                            NotifBubble.this.showLevel2(false);
                        }
                    }
                } catch (Throwable th2) {
                    NotifBubble.this.host.log("card tap failed", th2);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeLeft() {
                try {
                    NotifBubble.this.dismissItem(notifItem.key);
                    if (!NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel2(false);
                    }
                } catch (Throwable th2) {
                    NotifBubble.this.host.log("card swipe left failed", th2);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeRight() {
                try {
                    if (NotifBubble.this.isCardPinned()) {
                        NotifBubble.this.unpinForDock(NotifBubble.this.currentCardItem());
                    }
                    if (NotifBubble.this.level1Container != null && NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED) {
                        final View view = NotifBubble.this.level1Container;
                        view.animate().withLayer().translationX(NotifBubble.this.host.dp(120)).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.14.1
                            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                            public void onAnimationEnd(Animator animator) {
                                view.setTranslationX(0.0f);
                                view.setAlpha(1.0f);
                                if (NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED && NotifBubble.this.level1Container == view && !NotifBubble.this.items.isEmpty()) {
                                    NotifBubble.this.showLevel3();
                                }
                            }
                        }).start();
                    } else if (NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED && !NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel3();
                    }
                } catch (Throwable th2) {
                    NotifBubble.this.host.log("card swipe right failed", th2);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragStart() {
                NotifBubble.this.setSwipeMode(true);
                NotifBubble.this.host.getHandler().removeCallbacks(NotifBubble.this.toMiniRunnable);
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragEnd() {
                NotifBubble.this.setSwipeMode(false);
                if (NotifBubble.this.level == NotifBubble.LEVEL_EXPANDED) {
                    NotifBubble.this.scheduleLevel1Timer();
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragProgress(float f) {
                int iDp = NotifBubble.this.host.dp(28);
                boolean z = (!zIsDockLeft ? f > ((float) iDp) : f < ((float) (-iDp))) ? false : true;
                if ((!zIsDockLeft ? f < ((float) (-iDp)) : f > ((float) iDp)) ? false : true) {
                    textView2.setVisibility(0);
                    textView.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(NotifBubble.LEVEL_EXPANDED), NotifBubble.HINT_DISMISS);
                } else if (!z) {
                    textView.setVisibility(8);
                    textView2.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(NotifBubble.LEVEL_EXPANDED), NotifBubble.this.host.borderColor());
                } else {
                    textView.setVisibility(0);
                    textView2.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(NotifBubble.LEVEL_EXPANDED), -15961500);
                }
            }
        }));
        return frameLayout;
    }

    private View buildReplyRow(final NotifItem notifItem) {
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = this.host.dp(8);
        linearLayout.setLayoutParams(layoutParams);
        final EditText editText = new EditText(this.ctx);
        editText.setHint("回复…");
        editText.setHintTextColor(-8748153);
        editText.setTextColor(this.host.textMainColor());
        editText.setTextSize(LEVEL_MINI, 13.0f);
        editText.setSingleLine(true);
        editText.setInputType(LEVEL_EXPANDED);
        editText.setImeOptions(4);
        editText.setPadding(this.host.dp(10), this.host.dp(7), this.host.dp(10), this.host.dp(7));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(REPLY_INPUT_BG);
        gradientDrawable.setCornerRadius(this.host.dp(999));
        editText.setBackground(gradientDrawable);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.rightMargin = this.host.dp(8);
        editText.setLayoutParams(layoutParams2);
        editText.setOnTouchListener(new View.OnTouchListener() { // from class: com.jarvis.bubblenotify.NotifBubble.15
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view, MotionEvent motionEvent) {
                try {
                    if (motionEvent.getAction() == 0) {
                        NotifBubble.this.setReplyFocusable(true);
                        NotifBubble.this.activeReplyItem = notifItem;
                        NotifBubble.this.activeReplyEdit = editText;
                        NotifBubble.this.replyFallbackLaunched = false;
                        NotifBubble.this.host.getHandler().removeCallbacks(NotifBubble.this.replyFocusCheck);
                        NotifBubble.this.host.getHandler().postDelayed(NotifBubble.this.replyFocusCheck, NotifBubble.REPLY_FOCUS_CHECK_MS);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("reply touch failed", th);
                }
                return false;
            }
        });
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.jarvis.bubblenotify.NotifBubble.16
            @Override // android.view.View.OnFocusChangeListener
            public void onFocusChange(View view, boolean z) {
                NotifBubble notifBubble = NotifBubble.this;
                try {
                    if (z) {
                        notifBubble.host.getHandler().removeCallbacks(NotifBubble.this.replyFocusCheck);
                        NotifBubble.this.activeReplyItem = notifItem;
                        NotifBubble.this.activeReplyEdit = editText;
                        NotifBubble.this.showKeyboard(editText);
                    } else if (notifBubble.activeReplyEdit == editText) {
                        NotifBubble.this.activeReplyEdit = null;
                        NotifBubble.this.activeReplyItem = null;
                        NotifBubble.this.setReplyFocusable(false);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("reply focus failed", th);
                }
            }
        });
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.jarvis.bubblenotify.NotifBubble.17
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == 4) {
                    NotifBubble.this.sendReply(notifItem, editText, null);
                    return true;
                }
                return false;
            }
        });
        linearLayout.addView(editText);
        final TextView textView = new TextView(this.ctx);
        textView.setText("发送");
        textView.setTextSize(LEVEL_MINI, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-1);
        textView.setGravity(17);
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-15961500);
        gradientDrawable2.setCornerRadius(this.host.dp(999));
        textView.setBackground(gradientDrawable2);
        textView.setPadding(this.host.dp(14), this.host.dp(7), this.host.dp(14), this.host.dp(7));
        textView.setClickable(true);
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.18
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                NotifBubble.this.sendReply(notifItem, editText, textView);
            }
        });
        linearLayout.addView(textView);
        return linearLayout;
    }

    public void sendReply(NotifItem notifItem, EditText editText, final TextView textView) {
        if (notifItem != null) {
            try {
                if (notifItem.replyIntent != null && notifItem.replyKey != null && editText != null) {
                    String string = editText.getText() != null ? editText.getText().toString() : "";
                    if (string.trim().isEmpty()) {
                        return;
                    }
                    Bundle bundle = new Bundle();
                    bundle.putCharSequence(notifItem.replyKey, string);
                    Intent intent = new Intent();
                    RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder(notifItem.replyKey).build()}, intent, bundle);
                    notifItem.replyIntent.send(this.ctx, 0, intent);
                    editText.setText("");
                    if (textView != null) {
                        textView.setText("已发送");
                        textView.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.19
                            @Override // java.lang.Runnable
                            public void run() {
                                try {
                                    textView.setText("发送");
                                } catch (Throwable th) {
                                }
                            }
                        }, COPY_TIP_MS);
                    }
                    this.host.log("Quick reply sent.");
                    hideKeyboard(editText);
                    editText.clearFocus();
                }
            } catch (Throwable th) {
                this.host.log("sendReply failed", th);
            }
        }
    }

    public void launchReplyActivity(NotifItem notifItem) {
        try {
            Intent intent = new Intent(this.ctx, (Class<?>) ReplyActivity.class);
            intent.addFlags(1350565888);
            intent.putExtra(Settings.EXTRA_INTENT, notifItem.replyIntent);
            intent.putExtra(Settings.EXTRA_KEY, notifItem.replyKey);
            intent.putExtra(Settings.EXTRA_TITLE, notifItem.title);
            this.ctx.startActivity(intent);
        } catch (Throwable th) {
            this.host.log("launchReplyActivity failed", th);
        }
    }

    public void setReplyFocusable(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setReplyFocusable(z);
            }
        } catch (Throwable th) {
            this.host.log("setReplyFocusable failed", th);
        }
    }

    public void clearReplyFocus() {
        try {
            this.host.getHandler().removeCallbacks(this.replyFocusCheck);
            if (this.activeReplyEdit != null) {
                hideKeyboard(this.activeReplyEdit);
                this.activeReplyEdit.clearFocus();
                this.activeReplyEdit = null;
            }
            this.activeReplyItem = null;
            setReplyFocusable(false);
        } catch (Throwable th) {
            this.host.log("clearReplyFocus failed", th);
        }
    }

    public void showKeyboard(View view) {
        try {
            InputMethodManager inputMethodManager = (InputMethodManager) this.ctx.getSystemService("input_method");
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(view, LEVEL_EXPANDED);
            }
        } catch (Throwable th) {
        }
    }

    private void hideKeyboard(View view) {
        try {
            InputMethodManager inputMethodManager = (InputMethodManager) this.ctx.getSystemService("input_method");
            if (inputMethodManager != null && view.getWindowToken() != null) {
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public ArrayList<ArrayList<NotifItem>> groupItemsByApp() {
        ArrayList<NotifItem> arrayList;
        ArrayList<ArrayList<NotifItem>> arrayList2 = new ArrayList<>();
        for (NotifItem notifItem : this.items) {
            String str = notifItem.pkg;
            Iterator<ArrayList<NotifItem>> it = arrayList2.iterator();
            while (true) {
                if (!it.hasNext()) {
                    arrayList = null;
                    break;
                }
                arrayList = it.next();
                if (!arrayList.isEmpty()) {
                    String str2 = arrayList.get(0).pkg;
                    if (str2 != null) {
                        if (str2.equals(str)) {
                            break;
                        }
                    } else {
                        if (str == null) {
                            break;
                        }
                    }
                }
            }
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                arrayList2.add(arrayList);
            }
            arrayList.add(notifItem);
        }
        Collections.sort(arrayList2, new Comparator<ArrayList<NotifItem>>() { // from class: com.jarvis.bubblenotify.NotifBubble.20
            @Override // java.util.Comparator
            public int compare(ArrayList<NotifItem> arrayList3, ArrayList<NotifItem> arrayList4) {
                long j = 0;
                long j2 = (arrayList3 == null || arrayList3.isEmpty()) ? 0L : arrayList3.get(0).time;
                if (arrayList4 != null && !arrayList4.isEmpty()) {
                    j = arrayList4.get(0).time;
                }
                if (j2 < j) {
                    return NotifBubble.LEVEL_EXPANDED;
                }
                return j2 > j ? -1 : 0;
            }
        });
        return arrayList2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:596)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    public void rebuildMini(boolean z) {
        boolean zIsDockUpper;
        removeMiniView();
        if (this.items.isEmpty()) {
            return;
        }
        ArrayList<ArrayList<NotifItem>> arrayListGroupItemsByApp = groupItemsByApp();
        if (!isCardExpand()) {
            ArrayList<ArrayList<NotifItem>> arrayList = new ArrayList<>();
            Iterator<ArrayList<NotifItem>> it = arrayListGroupItemsByApp.iterator();
            while (it.hasNext()) {
                for (NotifItem notifItem : it.next()) {
                    ArrayList<NotifItem> arrayList2 = new ArrayList<>();
                    arrayList2.add(notifItem);
                    arrayList.add(arrayList2);
                }
            }
            Collections.sort(arrayList, new Comparator<ArrayList<NotifItem>>() { // from class: com.jarvis.bubblenotify.NotifBubble.21
                @Override // java.util.Comparator
                public int compare(ArrayList<NotifItem> arrayList3, ArrayList<NotifItem> arrayList4) {
                    long j = 0;
                    long j2 = (arrayList3 == null || arrayList3.isEmpty()) ? 0L : arrayList3.get(0).time;
                    if (arrayList4 != null && !arrayList4.isEmpty()) {
                        j = arrayList4.get(0).time;
                    }
                    if (j2 < j) {
                        return NotifBubble.LEVEL_EXPANDED;
                    }
                    return j2 > j ? -1 : 0;
                }
            });
            arrayListGroupItemsByApp = arrayList;
        }
        if (anyPinned()) {
            ArrayList arrayList3 = new ArrayList();
            ArrayList arrayList4 = new ArrayList();
            for (ArrayList<NotifItem> arrayList5 : arrayListGroupItemsByApp) {
                if (!arrayList5.isEmpty() && isPinned(arrayList5.get(0))) {
                    arrayList3.add(arrayList5);
                } else {
                    arrayList4.add(arrayList5);
                }
            }
            try {
                zIsDockUpper = this.host.isDockUpper();
            } catch (Throwable th) {
                zIsDockUpper = false;
            }
            arrayListGroupItemsByApp.clear();
            if (zIsDockUpper) {
                arrayListGroupItemsByApp.addAll(arrayList3);
                arrayListGroupItemsByApp.addAll(arrayList4);
            } else {
                arrayListGroupItemsByApp.addAll(arrayList4);
                arrayListGroupItemsByApp.addAll(arrayList3);
            }
        }
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(LEVEL_EXPANDED);
        linearLayout.setClipChildren(false);
        for (int i = 0; i < arrayListGroupItemsByApp.size(); i += LEVEL_EXPANDED) {
            ArrayList<NotifItem> arrayList6 = arrayListGroupItemsByApp.get(i);
            View viewBuildMiniCapsule = buildMiniCapsule(arrayList6.get(0), arrayList6.size());
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) {
                layoutParams.topMargin = this.host.dp(8);
            }
            linearLayout.addView(viewBuildMiniCapsule, layoutParams);
        }
        this.miniView = linearLayout;
        this.host.addDockView(linearLayout);
        applySoloCapsule();
        applyDockedHidden();
        if (z) {
            animateIn(linearLayout);
        }
        this.host.updateWindowSize();
    }

    private void applySoloCapsule() {
        boolean z;
        boolean z2;
        if (this.miniView == null) {
            return;
        }
        try {
            ViewGroup viewGroup = (ViewGroup) this.miniView;
            int i = 0;
            while (true) {
                z = true;
                if (i >= viewGroup.getChildCount()) {
                    z2 = false;
                    break;
                }
                View childAt = viewGroup.getChildAt(i);
                if (this.soloPkg == null || !this.soloPkg.equals(childAt.getTag())) {
                    i += LEVEL_EXPANDED;
                } else {
                    z2 = true;
                    break;
                }
            }
            if (this.soloPkg != null && z2) {
                z = false;
            }
            for (int i2 = 0; i2 < viewGroup.getChildCount(); i2 += LEVEL_EXPANDED) {
                View childAt2 = viewGroup.getChildAt(i2);
                childAt2.setVisibility((z || this.soloPkg.equals(childAt2.getTag())) ? 0 : 8);
            }
            if (!z2) {
                this.soloPkg = null;
            }
        } catch (Throwable th) {
            this.host.log("applySoloCapsule failed", th);
        }
    }

    private View buildMiniCapsule(final NotifItem notifItem, int i) {
        String str;
        int i2;
        FrameLayout frameLayout = new FrameLayout(this.ctx);
        frameLayout.setClipChildren(false);
        frameLayout.setTag(notifItem.pkg);
        frameLayout.setPadding(0, this.host.dp(AI_CAPSULE_GAP_DP), 0, 0);
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setBackground(this.host.pillBackground());
        linearLayout.setPadding(this.host.dp(AI_CAPSULE_GAP_DP), this.host.dp(AI_CAPSULE_GAP_DP), this.host.dp(14), this.host.dp(AI_CAPSULE_GAP_DP));
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(this.host.dp(capsuleWidthDp()), this.host.dp(capsuleHeightDp())));
        FrameLayout frameLayout2 = new FrameLayout(this.ctx);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(LEVEL_EXPANDED);
        gradientDrawable.setColor(-1);
        frameLayout2.setBackground(gradientDrawable);
        ImageView imageView = new ImageView(this.ctx);
        setNotifIcon(imageView, notifItem, true, 0, 0);
        frameLayout2.addView(imageView, new FrameLayout.LayoutParams(this.host.dp(35), this.host.dp(35), 17));
        linearLayout.addView(frameLayout2, new LinearLayout.LayoutParams(this.host.dp(38), this.host.dp(38)));
        LinearLayout linearLayout2 = new LinearLayout(this.ctx);
        linearLayout2.setOrientation(LEVEL_EXPANDED);
        linearLayout2.setGravity(16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = this.host.dp(8);
        TextView textView = new TextView(this.ctx);
        textView.setText(appLabel(notifItem.pkg) + (i > LEVEL_EXPANDED ? " · " + i + " 条通知" : ""));
        textView.setTextSize(LEVEL_MINI, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(this.host.textMainColor());
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setMaxWidth(this.host.dp(bubbleWidthDp() - 110));
        linearLayout2.addView(textView);
        TextView textView2 = new TextView(this.ctx);
        CodeInfo codeInfo = notifItem.key != null ? this.codes.get(notifItem.key) : null;
        if (codeInfo != null) {
            str = codeInfo.kind + " " + codeInfo.code;
        } else if (notifItem.title != null && !notifItem.title.isEmpty()) {
            str = notifItem.title;
        } else {
            str = notifItem.text != null ? notifItem.text : "";
        }
        // Download deleted (2026-11-23): no percent in the capsule.
        textView2.setText(str);
        textView2.setTextSize(LEVEL_MINI, 11.0f);
        textView2.setTextColor(this.host.textSubColor());
        textView2.setSingleLine(true);
        if (!isCardExpand()) {
            textView2.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            i2 = -1;
            textView2.setMarqueeRepeatLimit(-1);
            textView2.setSelected(true);
        } else {
            i2 = -1;
            textView2.setEllipsize(TextUtils.TruncateAt.END);
        }
        textView2.setMaxWidth(this.host.dp(bubbleWidthDp() - 110));
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2, layoutParams);
        if (codeInfo != null && Settings.CODE_KIND_VERIFY.equals(codeInfo.kind)) {
            View viewBuildCopyPill = buildCopyPill(codeInfo.code, notifItem.key, true);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(this.host.dp(48), this.host.dp(18));
            layoutParams2.leftMargin = this.host.dp(AI_CAPSULE_GAP_DP);
            layoutParams2.topMargin = this.host.dp(4);
            linearLayout.addView(viewBuildCopyPill, layoutParams2);
        } else {
            View viewBuildPinView = buildPinView(notifItem);
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(this.host.dp(22), this.host.dp(22));
            layoutParams3.leftMargin = this.host.dp(AI_CAPSULE_GAP_DP);
            layoutParams3.topMargin = this.host.dp(4);
            linearLayout.addView(viewBuildPinView, layoutParams3);
        }
        if (i > LEVEL_EXPANDED) {
            TextView textView3 = new TextView(this.ctx);
            textView3.setText(String.valueOf(i));
            textView3.setTextSize(LEVEL_MINI, 12.0f);
            textView3.setTypeface(Typeface.DEFAULT_BOLD);
            textView3.setTextColor(i2);
            textView3.setGravity(17);
            GradientDrawable gradientDrawable2 = new GradientDrawable();
            gradientDrawable2.setShape(LEVEL_EXPANDED);
            gradientDrawable2.setColor(HINT_DISMISS);
            textView3.setBackground(gradientDrawable2);
            textView3.setMinWidth(this.host.dp(21));
            textView3.setMinHeight(this.host.dp(21));
            textView3.setPadding(this.host.dp(4), 0, this.host.dp(4), 0);
            frameLayout.addView(textView3, new FrameLayout.LayoutParams(-2, -2, 8388661));
            textView3.setTranslationX(0.0f);
            textView3.setTranslationY(0.0f);
        }
        frameLayout.setOnTouchListener(new SwipeListener(frameLayout, true, true, new Callback() { // from class: com.jarvis.bubblenotify.NotifBubble.22
            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onTap() {
                boolean zIsEmpty = NotifBubble.this.items.isEmpty();
                try {
                    if (NotifBubble.this.level == NotifBubble.LEVEL_MINI && !NotifBubble.this.items.isEmpty()) {
                        if (!NotifBubble.this.isCardExpand()) {
                            try {
                                if (NotifBubble.this.isTapOpen() && notifItem.intent != null) {
                                    NotifBubble.this.host.openPendingIntent(notifItem.intent);
                                }
                                if (!zIsEmpty) {
                                    return;
                                } else {
                                    return;
                                }
                            } finally {
                                NotifBubble.this.dismissItem(notifItem.key);
                                if (!NotifBubble.this.items.isEmpty()) {
                                    NotifBubble.this.showLevel2(false);
                                }
                            }
                        }
                        // fixed212: a download capsule does NOT expand
                        // into a card on tap ("取消展开") — it stays
                        // pinned showing live progress.
                        if (notifItem.isDownload) {
                            return;
                        }
                        NotifBubble.this.expandedPkg = notifItem.pkg;
                        NotifBubble.this.showLevel1();
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini tap failed", th);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeLeft() {
                try {
                    if (NotifBubble.this.level == NotifBubble.LEVEL_MINI && !NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.dismissItem(notifItem.key);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini swipe left failed", th);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeRight() {
                try {
                    if (NotifBubble.this.level == NotifBubble.LEVEL_MINI && !NotifBubble.this.items.isEmpty()) {
                        if (NotifBubble.this.isPinned(notifItem)) {
                            NotifBubble.this.unpinForDock(notifItem);
                        }
                        NotifBubble.this.dockFromMini();
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini swipe right failed", th);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragStart() {
                NotifBubble.this.setSwipeMode(true);
                NotifBubble.this.host.getHandler().removeCallbacks(NotifBubble.this.toDockRunnable);
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragEnd() {
                NotifBubble.this.setSwipeMode(false);
                if (NotifBubble.this.level == NotifBubble.LEVEL_MINI) {
                    NotifBubble.this.scheduleMiniTimer(NotifBubble.this.fromDocked);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragProgress(float f) {
            }
        }));
        return frameLayout;
    }

    private void removeMiniView() {
        if (this.miniView != null) {
            this.host.removeDockView(this.miniView);
            this.miniView = null;
        }
    }
    /* JADX INFO: Access modifiers changed from: private */
    public void rebuildDocked(boolean z) {
        ArrayList arrayList = new ArrayList(this.dockedPkgs);
        removeDockedView();
        if (this.items.isEmpty()) {
            return;
        }
        ArrayList<ArrayList<NotifItem>> arrayListGroupItemsByApp = groupItemsByApp();
        ArrayList arrayList2 = new ArrayList();
        int size = arrayListGroupItemsByApp.size();
        while (true) {
            size--;
            if (size < 0) {
                break;
            }
            ArrayList<NotifItem> arrayList3 = arrayListGroupItemsByApp.get(size);
            if (arrayList3 == null || arrayList3.isEmpty() || !isPinned(arrayList3.get(0))) {
                View viewBuildDockedIcon = buildDockedIcon(arrayList3.get(0), arrayList3.size());
                this.dockedPileIcons.add(viewBuildDockedIcon);
                if (!arrayList.contains(arrayList3.get(0).pkg)) {
                    arrayList2.add(viewBuildDockedIcon);
                }
                this.host.addDockedIcon(viewBuildDockedIcon, new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.23
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            if ((NotifBubble.this.level == 3 || (NotifBubble.this.level == NotifBubble.LEVEL_MINI && !NotifBubble.this.dockedHiddenPkgs.isEmpty())) && !NotifBubble.this.items.isEmpty()) {
                                NotifBubble.this.host.expandAllToMini();
                            }
                        } catch (Throwable th) {
                            NotifBubble.this.host.log("docked tap failed", th);
                        }
                    }
                });
            }
        }
        this.dockedPkgs.clear();
        for (ArrayList<NotifItem> arrayList4 : arrayListGroupItemsByApp) {
            if (arrayList4 != null && !arrayList4.isEmpty() && !isPinned(arrayList4.get(0)) && !this.dockedPkgs.contains(arrayList4.get(0).pkg)) {
                this.dockedPkgs.add(arrayList4.get(0).pkg);
            }
        }
        if (z) {
            Iterator it = arrayList2.iterator();
            while (it.hasNext()) {
                animateDockIn((View) it.next());
            }
        }
        this.host.updateWindowSize();
    }

    private void animateDockIn(View view) {
        try {
            view.animate().cancel();
            view.setTranslationY(this.host.dp(16));
            view.animate().withLayer().translationY(0.0f).setDuration(ENTER_ANIM_MS).setListener(null).start();
        } catch (Throwable th) {
            this.host.log("animateDockIn failed", th);
        }
    }

    private View buildDockedIcon(NotifItem notifItem, int i) {
        FrameLayout frameLayout = new FrameLayout(this.ctx);
        ImageView imageView = new ImageView(this.ctx);
        setNotifIcon(imageView, notifItem, true, 0, 0);
        frameLayout.addView(imageView, new FrameLayout.LayoutParams(this.host.dp(34), this.host.dp(34), 8388627));
        FrameLayout frameLayout2 = new FrameLayout(this.ctx);
        frameLayout2.setTag(BubbleManager.TAG_EDGE);
        frameLayout2.addView(frameLayout, new FrameLayout.LayoutParams(this.host.dp(22), this.host.dp(34)));
        if (i > LEVEL_EXPANDED) {
            TextView textView = new TextView(this.ctx);
            textView.setText(String.valueOf(i));
            textView.setTextSize(LEVEL_MINI, 10.0f);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
            textView.setTextColor(-1);
            textView.setGravity(17);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setShape(LEVEL_EXPANDED);
            gradientDrawable.setColor(HINT_DISMISS);
            textView.setBackground(gradientDrawable);
            textView.setMinWidth(this.host.dp(16));
            textView.setMinHeight(this.host.dp(16));
            textView.setPadding(this.host.dp(3), 0, this.host.dp(3), 0);
            frameLayout2.addView(textView, new FrameLayout.LayoutParams(-2, -2, 8388661));
        }
        return frameLayout2;
    }

    private void removeDockedView() {
        if (!this.dockedPileIcons.isEmpty()) {
            Iterator<View> it = this.dockedPileIcons.iterator();
            while (it.hasNext()) {
                this.host.removeDockedIcon(it.next());
            }
            this.dockedPileIcons.clear();
        }
        this.dockedPkgs.clear();
    }

    interface Callback {
        void onDragEnd();

        void onDragProgress(float f);

        void onDragStart();

        void onSwipeLeft();

        void onSwipeRight();

        void onTap();
    }

    private class SwipeListener implements View.OnTouchListener {
        private final Callback callback;
        private float dockDragDownRawX;
        private float dockDragDownRawY;
        private Runnable dockDragLongPress;
        private long downTime;
        private float downX;
        private float downY;
        private boolean dragStarted;
        private boolean dragging;
        private long lastSwipeEndTime;
        private final boolean swipeLeftEnabled;
        private final boolean swipeRightEnabled;
        private final View target;
        private VelocityTracker velocityTracker;

        SwipeListener(View view, boolean z, boolean z2, Callback callback) {
            this.target = view;
            this.swipeLeftEnabled = z;
            this.swipeRightEnabled = z2;
            this.callback = callback;
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            float xVelocity;
            boolean z;
            boolean z2;
            boolean zIsDockLeft;
            final boolean z3;
            try {
                int actionMasked = motionEvent.getActionMasked();
                if (actionMasked == 0) {
                    this.dockDragDownRawX = motionEvent.getRawX();
                    this.dockDragDownRawY = motionEvent.getRawY();
                    if (this.dockDragLongPress == null) {
                        this.dockDragLongPress = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.1
                            @Override // java.lang.Runnable
                            public void run() {
                                try {
                                    NotifBubble.this.host.startDockDrag();
                                } catch (Throwable th) {
                                }
                            }
                        };
                    }
                    NotifBubble.this.host.getHandler().postDelayed(this.dockDragLongPress, ViewConfiguration.getLongPressTimeout());
                } else if (actionMasked == NotifBubble.LEVEL_MINI) {
                    if (NotifBubble.this.host.isDockDragging()) {
                        NotifBubble.this.host.moveDockDrag(motionEvent.getRawY());
                        return true;
                    }
                    float rawX = motionEvent.getRawX() - this.dockDragDownRawX;
                    float rawY = motionEvent.getRawY() - this.dockDragDownRawY;
                    int scaledTouchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                    if ((rawX * rawX) + (rawY * rawY) > scaledTouchSlop * scaledTouchSlop) {
                        NotifBubble.this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    }
                } else if (actionMasked == NotifBubble.LEVEL_EXPANDED || actionMasked == 3) {
                    NotifBubble.this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    if (NotifBubble.this.host.isDockDragging()) {
                        NotifBubble.this.host.endDockDrag(motionEvent.getRawX(), motionEvent.getRawY());
                        return true;
                    }
                }
            } catch (Throwable th) {
            }
            try {
                switch (motionEvent.getActionMasked()) {
                    case 0:
                        this.downX = motionEvent.getX();
                        this.downY = motionEvent.getY();
                        this.downTime = SystemClock.uptimeMillis();
                        this.dragging = false;
                        this.dragStarted = false;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        if (this.velocityTracker == null) {
                            this.velocityTracker = VelocityTracker.obtain();
                        } else {
                            this.velocityTracker.clear();
                        }
                        this.velocityTracker.addMovement(motionEvent);
                        return true;
                    case NotifBubble.LEVEL_EXPANDED /* 1 */:
                        float x = motionEvent.getX() - this.downX;
                        long jUptimeMillis = SystemClock.uptimeMillis() - this.downTime;
                        if (this.velocityTracker == null) {
                            xVelocity = 0.0f;
                        } else {
                            this.velocityTracker.addMovement(motionEvent);
                            this.velocityTracker.computeCurrentVelocity(1000);
                            xVelocity = this.velocityTracker.getXVelocity();
                            this.velocityTracker.recycle();
                            this.velocityTracker = null;
                        }
                        if (Math.abs(x) > NotifBubble.this.host.dp(84) || Math.abs(xVelocity) > 1000.0f) {
                            z = true;
                        } else {
                            z = false;
                        }
                        if (x >= 0.0f) {
                            z2 = true;
                        } else {
                            z2 = false;
                        }
                        try {
                            zIsDockLeft = NotifBubble.this.host.isDockLeft();
                        } catch (Throwable th2) {
                            zIsDockLeft = false;
                        }
                        if (!zIsDockLeft) {
                            z3 = z2;
                        } else if (!z2) {
                            z3 = true;
                        } else {
                            z3 = false;
                        }
                        boolean z4 = z3 ? this.swipeRightEnabled : this.swipeLeftEnabled;
                        if (this.dragging && z && z4) {
                            this.lastSwipeEndTime = SystemClock.uptimeMillis();
                            this.target.animate().withLayer().translationX(this.target.getWidth() * (z2 ? NotifBubble.LEVEL_EXPANDED : -1) * 1.5f).alpha(0.0f).setDuration(200L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.2
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    SwipeListener.this.target.setTranslationX(0.0f);
                                    SwipeListener.this.target.setAlpha(1.0f);
                                    SwipeListener.this.callback.onDragProgress(0.0f);
                                    SwipeListener.this.callback.onDragEnd();
                                    if (z3) {
                                        SwipeListener.this.callback.onSwipeRight();
                                    } else {
                                        SwipeListener.this.callback.onSwipeLeft();
                                    }
                                }
                            }).start();
                        } else if (this.dragging) {
                            this.target.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(NotifBubble.ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.3
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    SwipeListener.this.callback.onDragProgress(0.0f);
                                    SwipeListener.this.callback.onDragEnd();
                                }
                            }).start();
                        } else if (jUptimeMillis < 500 && Math.abs(x) < NotifBubble.this.host.dp(10) && SystemClock.uptimeMillis() - this.lastSwipeEndTime > 350) {
                            this.callback.onTap();
                        }
                        return true;
                    case NotifBubble.LEVEL_MINI /* 2 */:
                        if (this.velocityTracker != null) {
                            this.velocityTracker.addMovement(motionEvent);
                        }
                        float x2 = motionEvent.getX() - this.downX;
                        float y = motionEvent.getY() - this.downY;
                        if (!this.dragging && Math.abs(x2) > NotifBubble.this.host.dp(8) && Math.abs(x2) > Math.abs(y) * 1.5f) {
                            this.dragging = true;
                        }
                        if (this.dragging) {
                            if (!this.dragStarted) {
                                this.dragStarted = true;
                                this.callback.onDragStart();
                            }
                            this.target.setTranslationX(x2);
                            this.target.setAlpha(1.0f - Math.min(0.6f, (Math.abs(x2) / Math.max(NotifBubble.LEVEL_EXPANDED, this.target.getWidth())) * 0.9f));
                            this.callback.onDragProgress(x2);
                        }
                        return true;
                    case 3:
                        if (this.velocityTracker != null) {
                            this.velocityTracker.recycle();
                            this.velocityTracker = null;
                        }
                        if (this.dragging) {
                            this.target.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(NotifBubble.ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.4
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    SwipeListener.this.callback.onDragProgress(0.0f);
                                    SwipeListener.this.callback.onDragEnd();
                                }
                            }).start();
                        }
                        return true;
                    default:
                        return false;
                }
            } catch (Throwable th3) {
                NotifBubble.this.host.log("SwipeListener failed", th3);
                return false;
            }
        }
    }

    private void animateIn(final View view) {
        try {
            view.animate().cancel();
            view.setAlpha(0.0f);
            view.setTranslationX(0.0f);
            view.setTranslationY(0.0f);
            view.setScaleX(0.85f);
            view.setScaleY(0.85f);
            final int[] iArr = {0};
            view.post(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.24
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        if (view.getParent() == null) {
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                            return;
                        }
                        if ((view.getWidth() == 0 || view.getHeight() == 0) && iArr[0] < 5) {
                            int[] iArr2 = iArr;
                            iArr2[0] = iArr2[0] + NotifBubble.LEVEL_EXPANDED;
                            view.post(this);
                        } else if (view.getWidth() > 0 && view.getHeight() > 0) {
                            view.setPivotX(view.getWidth());
                            view.setPivotY(view.getHeight());
                            view.animate().withLayer().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(NotifBubble.ENTER_ANIM_MS).start();
                        } else {
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                        }
                    } catch (Throwable th) {
                    }
                }
            });
        } catch (Throwable th) {
        }
    }

    public void setSwipeMode(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setSwipeMode(z);
            }
        } catch (Throwable th) {
            this.host.log("setSwipeMode failed", th);
        }
    }

    public boolean isTapOpen() {
        try {
            if (!(this.host instanceof BubbleManager)) {
                return true;
            }
            return ((BubbleManager) this.host).isTapOpen();
        } catch (Throwable th) {
            return true;
        }
    }

    public boolean isCardExpand() {
        try {
            if (!(this.host instanceof BubbleManager)) {
                return true;
            }
            return ((BubbleManager) this.host).isCardExpand();
        } catch (Throwable th) {
            return true;
        }
    }

    private int capsuleWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).capsuleWidthDp();
            }
            return 200;
        } catch (Throwable th) {
            return 200;
        }
    }

    private int capsuleHeightDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).capsuleHeightDp();
            }
            return 52;
        } catch (Throwable th) {
            return 52;
        }
    }

    private String appLabel(String str) {
        String string;
        if (str == null) {
            return "";
        }
        String str2 = this.appLabelCache.get(str);
        if (str2 != null) {
            return str2;
        }
        try {
            PackageManager packageManager = this.ctx.getPackageManager();
            CharSequence applicationLabel = packageManager.getApplicationLabel(packageManager.getApplicationInfo(str, 0));
            if (applicationLabel != null && applicationLabel.length() > 0) {
                string = applicationLabel.toString();
            } else {
                string = str;
            }
        } catch (Throwable th) {
            string = str;
        }
        this.appLabelCache.put(str, string);
        return string;
    }

    private static String formatTime(long j) {
        try {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(j));
        } catch (Throwable th) {
            return "";
        }
    }

    private View buildIconView(NotifItem notifItem, int i, int i2, boolean z) {
        if (appIconDrawable(notifItem.pkg) != null || notifItem.icon != null) {
            ImageView imageView = new ImageView(this.ctx);
            setNotifIcon(imageView, notifItem, z, i2, i);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(this.host.dp(i), this.host.dp(i)));
            return imageView;
        }
        TextView textView = new TextView(this.ctx);
        String strAppLabel = appLabel(notifItem.pkg);
        textView.setText(strAppLabel.isEmpty() ? "?" : strAppLabel.substring(0, LEVEL_EXPANDED));
        textView.setTextSize(LEVEL_MINI, 16.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-1);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-14009531);
        if (z) {
            gradientDrawable.setShape(LEVEL_EXPANDED);
        } else {
            gradientDrawable.setCornerRadius(this.host.dp(i2));
        }
        textView.setBackground(gradientDrawable);
        textView.setLayoutParams(new LinearLayout.LayoutParams(this.host.dp(i), this.host.dp(i)));
        return textView;
    }

    private Drawable appIconDrawable(String str) {
        if (str == null) {
            return null;
        }
        try {
            return this.ctx.getPackageManager().getApplicationIcon(str);
        } catch (Throwable th) {
            return null;
        }
    }

    private void setNotifIcon(ImageView imageView, NotifItem notifItem, boolean z, int i, int i2) {
        Drawable drawableAppIconDrawable = appIconDrawable(notifItem.pkg);
        if (drawableAppIconDrawable != null) {
            imageView.setImageDrawable(drawableAppIconDrawable);
            if (z) {
                clipToCircle(imageView);
                return;
            }
            return;
        }
        Bitmap bitmapIconBitmap = notifItem.icon != null ? iconBitmap(notifItem, z, i, i2) : null;
        if (bitmapIconBitmap != null) {
            imageView.setImageBitmap(bitmapIconBitmap);
        } else {
            imageView.setImageBitmap(placeholderBitmap(appLabel(notifItem.pkg)));
        }
    }

    private void clipToCircle(ImageView imageView) {
        try {
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new ViewOutlineProvider() { // from class: com.jarvis.bubblenotify.NotifBubble.25
                @Override // android.view.ViewOutlineProvider
                public void getOutline(View view, Outline outline) {
                    if (Math.min(view.getWidth(), view.getHeight()) <= 0) {
                        view.getLayoutParams();
                    }
                    int iMin = Math.min(view.getWidth() > 0 ? view.getWidth() : 96, view.getHeight() > 0 ? view.getHeight() : 96);
                    outline.setOval(0, 0, iMin, iMin);
                }
            });
        } catch (Throwable th) {
        }
    }

    private Bitmap iconBitmap(NotifItem notifItem, boolean z, int i, int i2) {
        Bitmap bitmap = notifItem.icon;
        if (bitmap == null) {
            return null;
        }
        if (!notifItem.iconSmall) {
            return z ? circularBitmap(bitmap) : roundedBitmap(bitmap, i, i2);
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int iMin = Math.min(width, height);
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMin, iMin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        int i3 = notifItem.iconAccent != 0 ? notifItem.iconAccent : SMALL_ICON_BG;
        Paint paint = new Paint(LEVEL_EXPANDED);
        paint.setColor(i3);
        Path path = new Path();
        if (z) {
            float f = iMin / 2.0f;
            path.addCircle(f, f, f, Path.Direction.CW);
        } else {
            float f2 = i2 > 0 ? (i * iMin) / i2 : iMin * 0.3f;
            float f3 = iMin;
            path.addRoundRect(new RectF(0.0f, 0.0f, f3, f3), f2, f2, Path.Direction.CW);
        }
        canvas.clipPath(path);
        float f4 = iMin;
        canvas.drawRect(0.0f, 0.0f, f4, f4, paint);
        float f5 = 0.66f * f4;
        float f6 = (f4 - f5) / 2.0f;
        float f7 = f5 + f6;
        Rect rect = new Rect((width - iMin) / LEVEL_MINI, (height - iMin) / LEVEL_MINI, (width + iMin) / LEVEL_MINI, (height + iMin) / LEVEL_MINI);
        RectF rectF = new RectF(f6, f6, f7, f7);
        canvas.drawBitmap(bitmap, rect, rectF, (Paint) null);
        return bitmapCreateBitmap;
    }

    private Bitmap placeholderBitmap(String str) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(LEVEL_EXPANDED);
        paint.setColor(-14009531);
        canvas.drawCircle(48.0f, 48.0f, 48.0f, paint);
        String strSubstring = (str == null || str.isEmpty()) ? "?" : str.substring(0, LEVEL_EXPANDED);
        Paint paint2 = new Paint(LEVEL_EXPANDED);
        paint2.setColor(-1);
        paint2.setTextSize(48.0f);
        paint2.setTypeface(Typeface.DEFAULT_BOLD);
        paint2.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fontMetrics = paint2.getFontMetrics();
        canvas.drawText(strSubstring, 48.0f, 48.0f - ((fontMetrics.ascent + fontMetrics.descent) / 2.0f), paint2);
        return bitmapCreateBitmap;
    }

    private static Bitmap roundedBitmap(Bitmap bitmap, int i, int i2) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float f = i2 > 0 ? (i * width) / i2 : width * 0.3f;
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Path path = new Path();
        path.addRoundRect(new RectF(0.0f, 0.0f, width, height), f, f, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, (Paint) null);
        return bitmapCreateBitmap;
    }

    private static Bitmap circularBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int iMin = Math.min(width, height);
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMin, iMin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Path path = new Path();
        float f = iMin / 2.0f;
        path.addCircle(f, f, f, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawBitmap(bitmap, (iMin - width) / 2.0f, (iMin - height) / 2.0f, (Paint) null);
        return bitmapCreateBitmap;
    }
}
