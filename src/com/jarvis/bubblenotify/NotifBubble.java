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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private static final int MINI_WIDTH_DP = 200; // fixed16: capsule shortened 240dp -> 200dp
    private static final int CARD_GAP_DP = 8;
    private static final int CLOSE_COLOR = -8748153;
    private static final int CODE_TEXT = -7705344;
    private static final long COPY_TIP_MS = 1400;
    private static final long ENTER_ANIM_MS = 220;
    private static final int HANDLE_BG = -1073741825;
    private static final int HANDLE_BORDER = 687865856;
    private static final int HANDLE_TEXT = -14736603;
    private static final int HINT_DISMISS = -1750963;
    private static final int HINT_MINIMIZE = -15961500;
    private static final int LEVEL_DOCKED = 3;
    private static final int LEVEL_EXPANDED = 1;
    private static final int LEVEL_HIDDEN = 0;
    private static final int LEVEL_MINI = 2;
    /** Within this window after a level-1 pop, a new notification only refreshes. */
    private static final long REPOP_GUARD_MS = 5000;
    /** Same app, multiple notifications within this window: pop only once. */
    private static final long SAME_APP_POP_WINDOW_MS = 30000;
    private static final long REPLY_FOCUS_CHECK_MS = 800;
    private static final int REPLY_INPUT_BG = 335544320;
    private static final int REPLY_SEND_BG = -15961500;
    private static final int SMALL_ICON_BG = -13154481;
    private static final int TEXT_DIM = -8748153;
    private static final int TEXT_MAIN = -15065826;
    private static final int TEXT_SECONDARY = -11905960;
    private EditText activeReplyEdit;
    private NotifItem activeReplyItem;
    private final Context ctx;
    // fixed49: notification docked icons live in the SHARED docked pile
    // (BubbleManager), not in a per-bubble container. Track the icons
    // this bubble added so removeDockedView can take them out of the pile.
    private final ArrayList<View> dockedPileIcons = new ArrayList<>();
    // fixed67: packages already sitting in the docked pile (see
    // rebuildDocked) — their icons must not replay the entrance
    // animation when another capsule docks.
    private final ArrayList<String> dockedPkgs = new ArrayList<>();
    private final BubbleHost host;
    private View level1Container;
    private View miniView;
    private boolean screenOn;
    private final ArrayList<NotifItem> items = new ArrayList<>();
    private final Map<String, CodeInfo> codes = new HashMap();
    private final Map<String, String> appLabelCache = new HashMap();
    private int level = 0;
    private boolean fromDocked = false;
    // fixed78: whether the currently open card was opened while our own
    // capsule held the focus (single pop) — false when it was opened
    // from expand-all (no focus). The shrink-back path restores the
    // pre-card capsule arrangement accordingly.
    private boolean cardFromFocus = false;
    // fixed43: the package whose card is currently expanded. Tapping one
    // app's capsule expands only that app's notifications, and the card
    // goes to the bottom of the dock. Null means "no app selected" (the
    // card shows the top items across apps, as before). Cleared whenever
    // the bubble is hidden or the selected app has no items left.
    private String expandedPkg = null;
    // fixed79: the package whose capsule popped most recently (a fresh
    // pop). While set, only that app's capsule shows in the mini — older
    // apps' capsules stay hidden until expand-all. Without it, a fresh
    // pop rebuilds the whole mini and every old capsule pops along with
    // the new one (user 2026-11-17 22:51). Null = every capsule shows.
    private String soloPkg = null;
    // fixed74: capsules are ordered purely by notification time (see
    // groupItemsByApp) — the fixed45 bottomPkgs sinking is deleted.
    // fixed66: the fixed65 icon row above the player card is reverted
    // (user 2026-11-17 20:30 "上个版本改的播放器卡片上面排列图标那个撤回不要了，
    // 其他不变") — ordinary notifications stay as plain capsules/cards
    // while the player card is open. The player 3s auto-shrink/dock
    // (fixed65) is kept.
    private boolean replyFallbackLaunched = false;
    private long lastLevel1ShownAt = 0;
    /** Last pop time per package, for the same-app pop throttle. */
    private final Map<String, Long> lastPopByPkg = new HashMap();
    /** Last pop time (any package). A new notification within this window of
     *  any pop only refreshes — this kills the residual double-pop where two
     *  notifications (or a notification and its summary) arrive back-to-back. */
    private long lastPopAt = 0;
    // fixed31: delayed hide for a transiently empty list. Apps cancel and
    // re-post their notification on every update (message + summary, or a
    // text edit); the removal broadcast can empty the list for a moment,
    // and the old code hid the bubble immediately (hideAll, level -> 0),
    // so the re-post popped it again with the entrance animation — the
    // "pops twice" the user keeps seeing. Now an empty list only hides
    // after 2s with no replacement; a re-post inside the window cancels
    // the hide and refreshes in place. This is Flyme Island's rule in
    // spirit: never tear the island down on a transient state (its busy
    // island parks messages instead of dropping them).
    private static final long EMPTY_HIDE_MS = 2000;
    // fixed63: user 2026-11-17 19:47 — the expanded card auto-shrinks
    // after 3s without a tap, and ordinary capsules auto-dock after 3s
    // without a tap. (Music/call keep the shared Settings timers.)
    private static final long LEVEL1_IDLE_MS = 3000;
    private static final long MINI_DOCK_MS = 3000;
    private final Runnable pendingHideRunnable = new Runnable() {
        @Override
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
    private final Runnable toMiniRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.1
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (NotifBubble.this.level == 1 && !NotifBubble.this.items.isEmpty()) {
                    // fixed107: pinned card stays expanded — no auto-shrink.
                    // fixed126: use isCardPinned() (any item of the card's
                    // app), not just the top item, or a same-app new
                    // notification would let the pinned card shrink.
                    if (NotifBubble.this.isCardPinned()) {
                        return;
                    }
                    if (NotifBubble.this.activeReplyEdit != null && NotifBubble.this.activeReplyEdit.hasFocus()) {
                        NotifBubble.this.host.getHandler().postDelayed(this, LEVEL1_IDLE_MS);
                    } else {
                        NotifBubble.this.showLevel2(false);
                    }
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("toMini failed", th);
            }
        }
    };
    private final Runnable toDockRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.2
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (NotifBubble.this.level == 2 && !NotifBubble.this.items.isEmpty()) {
                    // fixed74: while another bubble holds the focus, a
                    // hidden capsule must not dock out from under it —
                    // wait for the focus to end, then re-check.
                    if (NotifBubble.this.host.isFocusActive() && !NotifBubble.this.host.isFocusView(NotifBubble.this.miniView)) {
                        NotifBubble.this.host.getHandler().postDelayed(this, MINI_DOCK_MS);
                        return;
                    }
                    // fixed139: a download in progress keeps its
                    // capsule out — do not auto-dock while any item
                    // is still downloading. Re-check later so it
                    // docks normally once the download finishes, and
                    // refreshMini() keeps updating the percentage in
                    // place meanwhile. Manual swipe-to-dock/dismiss
                    // is untouched; only the automatic timer is held.
                    if (NotifBubble.this.anyDownloading()) {
                        NotifBubble.this.host.getHandler().postDelayed(this, MINI_DOCK_MS);
                        return;
                    }
                    // fixed111: a pin is counted separately — while a
                    // pin is active, the OTHER (unpinned) capsules
                    // still auto-shrink and auto-dock normally: their
                    // pile icons appear in the shared pile, and only
                    // the pinned capsule stays out in the mini.
                    if (NotifBubble.this.anyPinned()) {
                        for (ArrayList<NotifItem> g : NotifBubble.this.groupItemsByApp()) {
                            if (g != null && !g.isEmpty() && g.get(0).pkg != null && !NotifBubble.this.isPinned(g.get(0))) {
                                NotifBubble.this.dockedHiddenPkgs.add(g.get(0).pkg);
                            }
                        }
                        NotifBubble.this.rebuildDocked(false);
                        NotifBubble.this.refreshMini();
                        NotifBubble.this.host.getHandler().postDelayed(this, MINI_DOCK_MS);
                        return;
                    }
                    NotifBubble.this.showLevel3();
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("toDock failed", th);
            }
        }
    };
    // fixed126: pkg of the capsule overlaying a pinned card, to auto-dock it.
    private String overlayDockPkg = null;
    private final Runnable overlayDockRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // A new notification that arrived while a card was pinned
                // shows as a capsule overlay; it must still auto-dock
                // after 3s like any other capsule, without touching the
                // pinned card (level stays 1).
                if (NotifBubble.this.level == 1 && NotifBubble.this.isCardPinned()) {
                    String pkg = NotifBubble.this.overlayDockPkg;
                    if (pkg != null) {
                        NotifBubble.this.dockedHiddenPkgs.add(pkg);
                        NotifBubble.this.rebuildDocked(false);
                        NotifBubble.this.applyDockedHidden();
                        NotifBubble.this.host.updateWindowSize();
                    }
                }
            } catch (Throwable th) {
                NotifBubble.this.host.log("overlayDock failed", th);
            }
        }
    };

    private final Runnable landscapeDockRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.3
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
    private final Runnable replyFocusCheck = new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.10
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

    private static class NotifItem {
        Bitmap icon;
        int iconAccent;
        boolean iconSmall;
        PendingIntent intent;
        String key;
        String pkg;
        PendingIntent replyIntent;
        String replyKey;
        String text;
        long time;
        String title;
        int progress;
        int progressMax;
        boolean progressIndeterminate;

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

    /**
     * True if the new item is a WeChat-style aggregated summary ("[5条]test: 111")
     * of the item already shown. Last line of defence against the double pop:
     * the service-side dedupe should have dropped it, but if it slips through
     * (count and message both changed), the bubble just refreshes instead of
     * popping a second time.
     */
    private static boolean looksLikeSummaryOf(NotifItem notifItem, NotifItem oldTop) {
        try {
            if (notifItem == null || oldTop == null) {
                return false;
            }
            if (notifItem.pkg == null || !notifItem.pkg.equals(oldTop.pkg)) {
                return false;
            }
            String sender = Settings.summarySender(notifItem.text);
            if (sender == null) {
                return false;
            }
            if (oldTop.title != null && oldTop.title.equals(sender)) {
                return true;
            }
            return oldTop.text != null && !oldTop.text.isEmpty() && notifItem.text.contains(oldTop.text);
        } catch (Throwable th) {
            return false;
        }
    }

    public void onNotifyList(ArrayList<Bundle> arrayList) {
        try {
            // fixed31: a non-empty list cancels any pending delayed hide
            // (a re-post arrived inside the empty window — see
            // pendingHideRunnable). The bubble stays where it is and the
            // new item just refreshes it instead of re-popping.
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            NotifItem oldTop = this.items.isEmpty() ? null : this.items.get(0);
            HashSet hashSet = new HashSet();
            for (NotifItem notifItem : this.items) {
                if (notifItem.key != null) {
                    hashSet.add(notifItem.key);
                }
            }
            this.items.clear();
            boolean z = false;
            if (arrayList != null) {
                for (Bundle bundle : arrayList) {
                    if (bundle != null) {
                        NotifItem notifItem2 = new NotifItem();
                        notifItem2.key = bundle.getString(Settings.ITEM_KEY);
                        notifItem2.title = bundle.getString(Settings.ITEM_TITLE);
                        notifItem2.text = bundle.getString(Settings.ITEM_TEXT);
                        notifItem2.pkg = bundle.getString(Settings.ITEM_PACKAGE);
                        notifItem2.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
                        notifItem2.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
                        notifItem2.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
                        notifItem2.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
                        try {
                            notifItem2.icon = (Bitmap) bundle.getParcelable(Settings.ITEM_ICON);
                        } catch (Throwable th) {
                        }
                        try {
                            notifItem2.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
                        } catch (Throwable th2) {
                        }
                        try {
                            notifItem2.replyIntent = (PendingIntent) bundle.getParcelable(Settings.ITEM_REPLY_INTENT);
                            notifItem2.replyKey = bundle.getString(Settings.ITEM_REPLY_KEY);
                        } catch (Throwable th3) {
                        }
                        notifItem2.iconSmall = bundle.getBoolean(Settings.ITEM_ICON_SMALL, false);
                        notifItem2.iconAccent = bundle.getInt(Settings.ITEM_ICON_ACCENT, 0);
                        this.items.add(notifItem2);
                    }
                }
            }
            HashSet hashSet2 = new HashSet();
            for (NotifItem notifItem3 : this.items) {
                if (notifItem3.key != null) {
                    hashSet2.add(notifItem3.key);
                }
            }
            this.codes.keySet().retainAll(hashSet2);
            if (this.items.isEmpty()) {
                // fixed31: do NOT hide immediately — the list may be
                // transiently empty between an app's cancel and re-post.
                // Hide after 2s only if it is still empty.
                scheduleEmptyHide();
                return;
            }
            for (NotifItem notifItem4 : this.items) {
                if (notifItem4.key != null && !hashSet.contains(notifItem4.key)) {
                    z = true;
                    break;
                }
            }
            if (z) {
                NotifItem newTop = this.items.isEmpty() ? null : this.items.get(0);
                if (newTop != null && looksLikeSummaryOf(newTop, oldTop)) {
                    refreshCurrentLevel();
                } else if (this.level == 1 && SystemClock.uptimeMillis() - this.lastLevel1ShownAt < REPOP_GUARD_MS) {
                    refreshCurrentLevel();
                } else if (SystemClock.uptimeMillis() - this.lastPopAt < REPOP_GUARD_MS) {
                    // fixed6: still double-popping — any notification within 3s
                    // of the last pop only refreshes, whatever the app.
                    refreshCurrentLevel();
                } else if (newTop != null && isSameAppRecentPop(newTop.pkg)) {
                    // fixed6: same app, multiple notifications within 15s — pop
                    // only once. The new item is already in the list, so just
                    // refresh the capsule/card silently, no re-pop animation.
                    refreshCurrentLevel();
                } else {
                    onNewNotification();
                }
            } else {
                refreshCurrentLevel();
            }
        } catch (Throwable th4) {
            this.host.log("NotifBubble.onNotifyList failed", th4);
        }
    }

    // fixed98: does the bubble already hold an item with this key?
    public boolean hasKey(String key) {
        if (key == null) {
            return false;
        }
        for (NotifItem item : this.items) {
            if (key.equals(item.key)) {
                return true;
            }
        }
        return false;
    }

    // fixed98: merge ONE notification (NotifCollection fallback for a
    // notification the listener service missed). Same key already
    // present -> refresh in place, no pop. New key -> add and pop.
    public void onNotifySingle(Bundle bundle) {
        try {
            if (bundle == null) {
                return;
            }
            String key = bundle.getString(Settings.ITEM_KEY);
            for (NotifItem item : this.items) {
                if (key != null && key.equals(item.key)) {
                    item.title = bundle.getString(Settings.ITEM_TITLE);
                    item.text = bundle.getString(Settings.ITEM_TEXT);
                    item.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
                    item.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
                    item.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
                    item.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
                    try {
                        item.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
                    } catch (Throwable ignored) {
                    }
                    refreshCurrentLevel();
                    return;
                }
            }
            NotifItem notifItem = new NotifItem();
            notifItem.key = key;
            notifItem.title = bundle.getString(Settings.ITEM_TITLE);
            notifItem.text = bundle.getString(Settings.ITEM_TEXT);
            notifItem.pkg = bundle.getString(Settings.ITEM_PACKAGE);
            notifItem.time = bundle.getLong(Settings.ITEM_TIME, System.currentTimeMillis());
            notifItem.progress = bundle.getInt(Settings.ITEM_PROGRESS, 0);
            notifItem.progressMax = bundle.getInt(Settings.ITEM_PROGRESS_MAX, 0);
            notifItem.progressIndeterminate = bundle.getBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, false);
            try {
                notifItem.icon = (Bitmap) bundle.getParcelable(Settings.ITEM_ICON);
            } catch (Throwable ignored) {
            }
            try {
                notifItem.intent = (PendingIntent) bundle.getParcelable(Settings.ITEM_INTENT);
            } catch (Throwable ignored) {
            }
            try {
                notifItem.replyIntent = (PendingIntent) bundle.getParcelable(Settings.ITEM_REPLY_INTENT);
                notifItem.replyKey = bundle.getString(Settings.ITEM_REPLY_KEY);
            } catch (Throwable ignored) {
            }
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            this.items.add(0, notifItem);
            while (this.items.size() > 8) {
                this.items.remove(this.items.size() - 1);
            }
            onNewNotification();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onNotifySingle failed", th);
        }
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

    // fixed89: test-code button — post a test notification and
    // attach the test code capsule to it in one step, so the button
    // actually shows the code display. Uses the fixed key "test:code"
    // so a repeat tap replaces the previous test item instead of
    // stacking test notifications.
    public void onTestCode(String title, String text, String kind, String code) {
        try {
            if (code == null || code.isEmpty()) {
                return;
            }
            String key = "test:code";
            NotifItem existing = null;
            for (NotifItem item : this.items) {
                if (key.equals(item.key)) {
                    existing = item;
                    break;
                }
            }
            if (existing == null) {
                NotifItem notifItem = new NotifItem();
                notifItem.key = key;
                notifItem.title = title == null ? "测试短信" : title;
                notifItem.text = text == null ? "" : text;
                notifItem.pkg = "com.jarvis.bubblenotify";
                notifItem.time = System.currentTimeMillis();
                this.items.add(0, notifItem);
                while (this.items.size() > 8) {
                    this.items.remove(this.items.size() - 1);
                }
            } else {
                existing.title = title == null ? existing.title : title;
                existing.text = text == null ? existing.text : text;
                existing.time = System.currentTimeMillis();
            }
            CodeInfo codeInfo = new CodeInfo();
            codeInfo.kind = kind == null ? Settings.CODE_KIND_VERIFY : kind;
            codeInfo.code = code;
            this.codes.put(key, codeInfo);
            onNewNotification();
        } catch (Throwable th) {
            this.host.log("NotifBubble.onTestCode failed", th);
        }
    }

    public void onAiCode(String str, String str2, String str3) {
        boolean z;
        if (str == null || str2 == null || str3 == null) {
            return;
        }
        try {
            if (str3.isEmpty()) {
                return;
            }
            Iterator<NotifItem> it = this.items.iterator();
            while (true) {
                if (!it.hasNext()) {
                    z = false;
                    break;
                } else if (str.equals(it.next().key)) {
                    z = true;
                    break;
                }
            }
            if (!z) {
                return;
            }
            CodeInfo codeInfo = new CodeInfo();
            codeInfo.kind = str2;
            codeInfo.code = str3;
            this.codes.put(str, codeInfo);
            if (this.level == 1) {
                refreshLevel1();
            } else if (this.level == 2) {
                refreshMini();
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
            } else if (this.level == 1) {
                scheduleLevel1Timer();
            } else if (this.level == 2) {
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

    // fixed13: once shrunk, never re-pop. Flyme Island's rule: after the
    // bubble shrinks to a capsule, a new notification only refreshes it in
    // place — it does not replay the entrance animation. Previously, when
    // the bubble was already a mini (level 2), onNewNotification fell
    // through to showLevel2, which rebuilds the mini and replays animateIn,
    // so the capsule visibly popped again for every new notification.
    // Only pop in when the bubble is actually hidden (level 0).
    private void onNewNotification() {
        if (this.host.isLandscape()) {
            if (this.level == 1) {
                refreshLevel1();
                scheduleLevel1Timer();
                return;
            } else if (this.level == 2) {
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
        // fixed5: notifications arrive as a capsule (level 2) and never
        // auto-expand into the card. The card only opens when the user taps
        // the capsule (see rebuildMini onTap -> showLevel1). If the card is
        // already open, just refresh it in place.
        if (this.level == 1) {
            // fixed110: a pinned card stays expanded, but new
            // notifications still pop as capsules above/below it, and
            // those capsules stay tappable / pinnable.
            if (isCardPinned()) {
                if (!this.items.isEmpty() && this.items.get(0).pkg != null) {
                    this.dockedHiddenPkgs.remove(this.items.get(0).pkg);
                }
                this.soloPkg = this.items.isEmpty() ? null : this.items.get(0).pkg;
                rebuildMini(false);
                if (this.miniView != null) {
                    // addDockView hides a new view while another view
                    // holds the focus (the card) — force the overlay
                    // visible; the card keeps the focus.
                    this.miniView.setVisibility(View.VISIBLE);
                }
                // fixed126: the overlay capsule must still auto-dock after
                // 3s like any other capsule (scheduleMiniTimer only runs
                // at level 2, so it never fires while the card is open).
                this.overlayDockPkg = this.soloPkg;
                this.host.getHandler().removeCallbacks(this.overlayDockRunnable);
                this.host.getHandler().postDelayed(this.overlayDockRunnable, MINI_DOCK_MS);
                // fixed127: do NOT refresh the card — it stays frozen on
                // the pinned app; the new notification lives only as the
                // overlay capsule above.
                scheduleLevel1Timer();
                return;
            }
            refreshLevel1();
            scheduleLevel1Timer();
            return;
        }
        if (this.level == 2) {
            // fixed108: a genuinely new notification un-hides its app
            // (a fresh pop must be seen even if it docked while pinned).
            if (!this.items.isEmpty() && this.items.get(0).pkg != null) {
                this.dockedHiddenPkgs.remove(this.items.get(0).pkg);
            }
            // fixed79: a fresh pop shows only the new notification's
            // capsule. If another capsule holds the focus (or a card
            // does), the older apps' capsules stay hidden; if the mini
            // is already showing, the new capsule just joins the
            // visible set (soloPkg cleared).
            boolean stealing = this.host.isFocusActive() && !this.host.isFocusView(this.miniView);
            this.soloPkg = stealing ? (this.items.isEmpty() ? null : this.items.get(0).pkg) : null;
            refreshMini();
            if (anyPinned()) {
                scheduleMiniTimer(this.fromDocked);
            }
            // fixed76: a genuinely new notification must be seen. If
            // another capsule (music / call / nav) holds the focus, the
            // rebuild above left this capsule GONE and the notification
            // never appeared. Take the focus (no re-pop animation:
            // refreshMini already rebuilt it in place) — unless the
            // focus is an expanded card, which a notification must
            // never hide (e.g. a ringing call card).
            if (this.miniView != null && stealing && !this.host.isFocusCard()) {
                this.host.setFocusView(this.miniView);
            }
            return;
        }
        // fixed76: a genuinely new notification pops as a capsule even
        // when the previous one already docked (the old fixed13 rule
        // only refreshed the pile icon, so the capsule never appeared).
        // Rapid re-pops are still suppressed upstream in onNotifyList
        // (5s REPOP guard / 30s same-app window -> refreshDocked).
        if (this.level == 3) {
            // fixed79: the mini pops fresh — only the new notification's
            // capsule; the docked older ones stay in the pile until
            // expand-all.
            this.soloPkg = this.items.isEmpty() ? null : this.items.get(0).pkg;
            showLevel2(false);
            return;
        }
        // fixed33: log every real pop (level was 0). Two POP lines for one
        // notification = the bubble really popped twice; compare with the
        // Service's Added lines to tell service-side from display-side.
        NotifItem top = this.items.isEmpty() ? null : this.items.get(0);
        FileLog.i("POP new notification topKey=" + (top == null ? null : top.key) + " topPkg=" + (top == null ? null : top.pkg) + " items=" + this.items.size());
        recordPop();
        showLevel2(false);
    }

    /** True if this package already popped within the same-app window. */
    private boolean isSameAppRecentPop(String pkg) {
        try {
            if (pkg == null) {
                return false;
            }
            Long last = this.lastPopByPkg.get(pkg);
            if (last == null) {
                return false;
            }
            return SystemClock.uptimeMillis() - last.longValue() < SAME_APP_POP_WINDOW_MS;
        } catch (Throwable th) {
            return false;
        }
    }

    /** Records a pop for the current top item's package. */
    private void recordPop() {
        try {
            long now = SystemClock.uptimeMillis();
            this.lastPopAt = now;
            if (!this.items.isEmpty()) {
                String pkg = this.items.get(0).pkg;
                if (pkg != null) {
                    this.lastPopByPkg.put(pkg, Long.valueOf(now));
                    if (this.lastPopByPkg.size() > 32) {
                        java.util.Iterator<Map.Entry<String, Long>> it = this.lastPopByPkg.entrySet().iterator();
                        while (it.hasNext()) {
                            if (now - it.next().getValue().longValue() > SAME_APP_POP_WINDOW_MS) {
                                it.remove();
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showLevel1() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        this.level = 1;
        this.lastLevel1ShownAt = SystemClock.uptimeMillis();
        this.fromDocked = false;
        // fixed78: record whether this card was opened while our own
        // capsule held the focus (single pop) or from expand-all (no
        // focus) — read before removeMiniView() clears it.
        this.cardFromFocus = this.host.isFocusView(this.miniView);
        removeMiniView();
        removeDockedView();
        rebuildLevel1(true);
        scheduleLevel1Timer();
        enforceSingleLevelView();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showLevel2(boolean z) {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        final int prevLevel = this.level;
        // fixed74: no sinking — the handled app stays in notification-time
        // order (see groupItemsByApp). Just clear the selection.
        if (this.level == 1 && this.expandedPkg != null) {
            this.expandedPkg = null;
        }
        // fixed28 (ported from Flyme Island 1.0.38-r3 o0()): same mode runs
        // only the content updater, never the entrance animation. If we are
        // already a mini, just refresh in place — re-running rebuildMini(true)
        // here would replay animateIn and look like a second pop.
        if (this.level == 2) {
            refreshMini();
            enforceSingleLevelView();
            return;
        }
        clearReplyFocus();
        this.level = 2;
        this.fromDocked = z;
        removeDockedView();
        // fixed60 (replaces the fixed58 shrink): shrink from the
        // expanded position — and yes, the transparent outside-tap
        // layer was (most of) the cause. While a card is expanded the
        // window was fullscreen and the dock sat lifted by the card
        // offset (70dp); the old code tore the layer down FIRST, which
        // instantly shrank the window back to dock size and dropped the
        // dock margin to 0 — the card teleported down ~56dp in the same
        // frame the scale animation started, so the shrink never began
        // where the card was and looked like it came from the top-left
        // / far away (the pivot was already bottom-right, but the
        // teleport dominated the visual).
        // fixed63: the layer is deleted entirely, so there is nothing
        // to tear down mid-animation any more — the window stays
        // dock-sized throughout. The card scales down AND slides down
        // to the capsule's landing spot (bottom 14dp), fading out;
        // only when it arrives is the card removed and the capsule
        // built, already at its final position, with no entrance scale
        // replayed. The capsule is NOT built before the animation — in
        // the dock it would sit above the card and jump down at the
        // end. If the card is gone or not laid out yet, fall back to
        // the old path.
        final View shrinkCard = this.level1Container;
        if (shrinkCard != null && shrinkCard.getParent() != null && shrinkCard.getWidth() > 0 && shrinkCard.getHeight() > 0) {
            // fixed63: no layer any more — the window stays dock-sized
            // for the whole animation, nothing to keep up or tear down.
            // How far the card's bottom edge must slide down so its
            // bottom-right corner lands exactly where the capsule's
            // bottom-right corner will be (screen bottom minus 14dp).
            int[] loc = new int[2];
            shrinkCard.getLocationOnScreen(loc);
            int screenH = this.ctx.getResources().getDisplayMetrics().heightPixels;
            float landDy = ((float) (screenH - this.host.dp(14))) - ((float) (loc[1] + shrinkCard.getHeight()));
            if (landDy < 0.0f) {
                landDy = 0.0f;
            }
            final float slideDy = landDy;
            // fixed62: do not rely on the pivot. fixed60 set the pivot
            // to the card's bottom-right, but the card STILL shrank
            // toward the top-left on this device (user 19:30) — whatever
            // resets or ignores the pivot (a layout pass, the entrance
            // animator, Flyme's ViewPropertyAnimator), a
            // pivot-independent shrink cannot be affected by it. Scale
            // around the top-left (the default pivot, 0,0) and translate
            // explicitly so the card's bottom-right corner stays glued
            // while it slides down to the capsule: scale s maps the
            // bottom-right (w,h) to (s*w, s*h), so tx=(1-s)*w and
            // ty=(1-s)*h+slideDy lands it exactly on the capsule's
            // bottom-right corner (card and capsule share the 12dp
            // right margin; the capsule's bottom sits 14dp up).
            final float shrinkScale = 0.66f;
            final float tx = (1.0f - shrinkScale) * shrinkCard.getWidth();
            final float ty = (1.0f - shrinkScale) * shrinkCard.getHeight() + slideDy;
            shrinkCard.animate().cancel();
            shrinkCard.setPivotX(0.0f);
            shrinkCard.setPivotY(0.0f);
            shrinkCard.setTranslationX(0.0f);
            shrinkCard.setTranslationY(0.0f);
            shrinkCard.animate().withLayer().scaleX(shrinkScale).scaleY(shrinkScale).translationX(tx).translationY(ty).alpha(0.0f).setDuration(ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() {
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    try {
                        shrinkCard.setTranslationX(0.0f);
                        shrinkCard.setTranslationY(0.0f);
                        shrinkCard.setScaleX(1.0f);
                        shrinkCard.setScaleY(1.0f);
                        shrinkCard.setAlpha(1.0f);
                        // Re-expanded mid-shrink (a capsule was tapped):
                        // the new card owns the card-open state now,
                        // leave everything alone.
                        if (NotifBubble.this.level != 2) {
                            return;
                        }
                        if (NotifBubble.this.level1Container == shrinkCard) {
                            NotifBubble.this.removeLevel1View();
                        }
                        // else: a new card took over mid-shrink — it
                        // already set the card-open state and owns the
                        // dock margin now; do not touch it.
                        if (!NotifBubble.this.items.isEmpty() && NotifBubble.this.miniView == null) {
                            NotifBubble.this.rebuildMini(false);
                        }
                        // fixed74: same focus rule as the direct path.
                        // fixed76: prevLevel != 3 exclusion dropped, and a
                        // fresh pop never hides an expanded card.
                        // fixed78: same rule as the direct path — a card
                        // opened from expand-all restores the pre-card
                        // capsules on shrink (prevLevel is always 1 here).
                        boolean restoreAll = !NotifBubble.this.cardFromFocus;
                        if (!NotifBubble.this.fromDocked && NotifBubble.this.miniView != null && !restoreAll && !NotifBubble.this.host.isFocusCard()) {
                            NotifBubble.this.host.setFocusView(NotifBubble.this.miniView);
                        }
                        NotifBubble.this.scheduleMiniTimer(NotifBubble.this.fromDocked);
                        NotifBubble.this.enforceSingleLevelView();
                        NotifBubble.this.host.updateWindowSize();
                    } catch (Throwable ignored) {
                    }
                }
            }).start();
            return;
        }
        removeLevel1View();
        rebuildMini(true);
        // fixed74: a capsule that pops fresh (from hidden) or shrinks back
        // from a card takes the focus; one coming out of the dock
        // (expand-all) does not.
        // fixed76: a genuinely new notification also pops out of the dock
        // (onNewNotification level 3 -> showLevel2) and must take the
        // focus too, so the prevLevel != 3 exclusion is gone; only the
        // fromDocked (expand-all) exclusion remains. A fresh pop never
        // hides an expanded card (isFocusCard).
        // fixed78: shrinking back from a card opened from expand-all
        // restores the pre-card capsules (removeLevel1View already
        // cleared the focus and revealed them); otherwise the capsule
        // takes the focus. A fresh pop never hides an expanded card.
        boolean restoreAll = prevLevel == 1 && !this.cardFromFocus;
        if (!this.fromDocked && this.miniView != null && !restoreAll && !this.host.isFocusCard()) {
            this.host.setFocusView(this.miniView);
        }
        scheduleMiniTimer(this.fromDocked);
        enforceSingleLevelView();
    }

    /* JADX INFO: Access modifiers changed from: private */
    // fixed74: expand-all — come out of the dock to capsules. No-op
    // unless actually docked.
    public void expandFromDock() {
        if (this.items.isEmpty()) {
            return;
        }
        if (this.level == 3) {
            // fixed79: expand-all shows every capsule.
            this.soloPkg = null;
            showLevel2(true);
        } else if (this.level == 2 && !this.dockedHiddenPkgs.isEmpty()) {
            // fixed113: a pin holds a capsule out while the rest docked —
            // tapping a pile icon calls the docked capsules back out.
            // Clear the hidden set, take the pile icons down, rebuild
            // the mini with every capsule, restart the 3s auto-dock.
            this.soloPkg = null;
            this.dockedHiddenPkgs.clear();
            removeDockedView();
            showLevel2(true);
            scheduleMiniTimer(false);
        }
    }

    // fixed74: BubbleManager uses this to map a dock view back to its
    // bubble (no-sinking index memory). A notification owns its mini
    // container and its expanded card; docked pile icons are not dock
    // children and are not covered.
    boolean ownsView(View v) {
        return v != null && (v == this.miniView || v == this.level1Container);
    }

    // fixed69: the pile closes as one unit — when the player docks, a
    // notification capsule docks with it. No-op unless capsules are out
    // (level 2); an expanded card (level 1) is never yanked down by
    // another bubble docking.
    public void dockFromMini() {
        if (this.level != 2 || this.items.isEmpty()) {
            return;
        }
        // fixed109: a pinned capsule is never dragged to the dock by
        // another bubble docking (user: player auto-dock pulled pinned
        // notifications back). fixed111: the OTHER (unpinned) capsules
        // still dock normally (pile icons appear) — only the pinned
        // one stays at level 2.
        if (anyPinned()) {
            for (ArrayList<NotifItem> g : groupItemsByApp()) {
                if (g != null && !g.isEmpty() && g.get(0).pkg != null && !isPinned(g.get(0))) {
                    this.dockedHiddenPkgs.add(g.get(0).pkg);
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
        // fixed57: docking clears the expanded-app selection — no app is
        // selected in the docked state, so a later expand must not be
        // filtered to the app that was open before.
        this.expandedPkg = null;
        this.pinnedCardPkg = null;
        // fixed79: and the solo pop with it — the pile holds every app.
        this.soloPkg = null;
        // fixed108: docking reveals every pile icon together, including
        // the ones that hid while a pin was active.
        this.dockedHiddenPkgs.clear();
        clearReplyFocus();
        this.level = 3;
        removeLevel1View();
        removeMiniView();
        rebuildDocked(true);
        cancelAllTimers();
        enforceSingleLevelView();
        // fixed69: the pile closes as one unit — when the notifications
        // dock, a player capsule docks with them. Guarded inside
        // dockPlayerTogether (only a level-2 player capsule moves), so
        // this cannot loop back.
        this.host.dockPlayerTogether();
    }

    private void hideAll() {
        this.level = 0;
        this.fromDocked = false;
        // fixed43: clear the expanded-app selection when the bubble hides.
        this.expandedPkg = null;
        this.pinnedCardPkg = null;
        // fixed79: and the solo pop with it.
        this.soloPkg = null;
        // fixed108: and the deferred hidden set with it.
        this.dockedHiddenPkgs.clear();
        clearReplyFocus();
        cancelAllTimers();
        try {
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
        } catch (Throwable ignored) {
        }
        removeLevel1View();
        removeMiniView();
        removeDockedView();
    }

    // fixed31: schedule the delayed hide for a transiently empty list
    // (see pendingHideRunnable). Only the onNotifyList empty path uses
    // this; user-driven hides (dismissItem, onClearAll) stay immediate.
    private void scheduleEmptyHide() {
        try {
            this.host.getHandler().removeCallbacks(this.pendingHideRunnable);
            this.host.getHandler().postDelayed(this.pendingHideRunnable, EMPTY_HIDE_MS);
        } catch (Throwable th) {
            this.host.log("scheduleEmptyHide failed", th);
            hideAll();
        }
    }

    // fixed31: single-view invariant (Flyme Island's single mode funnel
    // o0, applied to views). Exactly one level's view may be in the dock:
    // whatever does not match `level` is removed. This kills the
    // double-display the user screenshotted (expanded card AND docked
    // icon on screen at once) no matter which path leaked the extra view.
    private void enforceSingleLevelView() {
        try {
            if (this.level == 1) {
                // fixed110: a pinned card lets new-notification capsules
                // show above/below it — keep the mini overlay.
                if (!isCardPinned()) {
                    removeMiniView();
                }
                removeDockedView();
            } else if (this.level == 2) {
                removeLevel1View();
                // fixed111: while a pin is active, docked apps' pile
                // icons stay in the shared pile alongside the pinned
                // capsule — do not remove them.
                if (!anyPinned()) {
                    removeDockedView();
                }
            } else if (this.level == 3) {
                removeLevel1View();
                removeMiniView();
            } else {
                removeLevel1View();
                removeMiniView();
                removeDockedView();
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
            case 1:
                refreshLevel1();
                break;
            case 2:
                refreshMini();
                break;
            case 3:
                refreshDocked();
                break;
            default:
                // fixed24/27: a refresh must never POP a hidden bubble (the
                // old default called showLevel2(false), re-popping on every
                // re-broadcast and defeating the guards — every notification
                // popped twice). But fixed24 went too far: staying hidden
                // swallowed guard-suppressed notifications entirely, so some
                // notifications never appeared at all ("sometimes pops,
                // sometimes doesn't"). Now show the capsule SILENTLY (no
                // entrance animation): the user can see the notification,
                // but a duplicate never re-pops. A genuinely new
                // notification still pops via onNewNotification().
                showLevel2Silent();
                break;
        }
        enforceSingleLevelView();
    }

    // fixed27: show the mini capsule WITHOUT the entrance animation.
    // Used by refresh paths that must not re-pop (guard-suppressed
    // duplicates) but must not swallow the notification either.
    private void showLevel2Silent() {
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        clearReplyFocus();
        this.level = 2;
        this.fromDocked = false;
        removeLevel1View();
        removeDockedView();
        rebuildMini(false);
        // fixed74: a silently shown capsule is still a fresh pop — it
        // takes the focus so it is actually visible.
        // fixed76: unless an expanded card holds the focus (a ringing
        // call card must not be hidden by a notification).
        if (this.miniView != null && !this.host.isFocusCard()) {
            this.host.setFocusView(this.miniView);
        }
        scheduleMiniTimer(this.fromDocked);
        enforceSingleLevelView();
    }

    private void refreshLevel1() {
        if (this.level != 1) {
            return;
        }
        // fixed127: a pinned card is frozen — a new notification (same
        // app or different app) must not rebuild or replace it. It only
        // appears as a capsule overlay (onNewNotification) and auto-docks
        // after 3s. The pin icon itself is updated in place in
        // buildPinView, so freezing the rebuild does not hide the pin.
        if (isCardPinned()) {
            return;
        }
        if (this.activeReplyEdit != null && this.activeReplyEdit.hasFocus()) {
            return;
        }
        rebuildLevel1(false);
    }

    private void refreshMini() {
        if (this.level != 2) {
            return;
        }
        // fixed74: a refresh rebuilds the container (remove + add) — if
        // this capsule held the focus, the remove clears it, so take it
        // back after the rebuild.
        boolean wasFocus = this.miniView != null && this.host.isFocusView(this.miniView);
        rebuildMini(false);
        if (wasFocus && this.miniView != null) {
            this.host.setFocusView(this.miniView);
        }
    }

    private void refreshDocked() {
        if (this.level != 3) {
            return;
        }
        rebuildDocked(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleLevel1Timer() {
        this.host.getHandler().removeCallbacks(this.toMiniRunnable);
        // fixed107: a pinned card never auto-shrinks — no timer while pinned.
        if (isPinned(currentCardItem())) {
            return;
        }
        if (this.screenOn && this.level == 1) {
            this.host.getHandler().postDelayed(this.toMiniRunnable, LEVEL1_IDLE_MS);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleMiniTimer(boolean z) {
        this.host.getHandler().removeCallbacks(this.toDockRunnable);
        // fixed108: the timer keeps running while pinned — toDockRunnable
        // hides non-pinned capsules instead of docking the whole bubble.
        if (this.screenOn && this.level == 2) {
            // fixed63: ordinary capsules auto-dock after 3s without a
            // tap, whether they came fresh or back from the dock.
            this.host.getHandler().postDelayed(this.toDockRunnable, MINI_DOCK_MS);
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

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissItem(String str) {
        if (str == null) {
            return;
        }
        boolean z = false;
        for (int size = this.items.size() - 1; size >= 0; size--) {
            if (str.equals(this.items.get(size).key)) {
                this.items.remove(size);
                z = true;
            }
        }
        this.codes.remove(str);
        if (!z) {
            return;
        }
        // fixed107: drop pins for notifications that are gone.
        // fixed111: pinnedKeys is keyed by item key, dockedHiddenPkgs
        // stays keyed by package (capsule tag).
        if (!this.pinnedKeys.isEmpty() || !this.dockedHiddenPkgs.isEmpty()) {
            HashSet<String> presentKeys = new HashSet<>();
            HashSet<String> presentPkgs = new HashSet<>();
            for (NotifItem it : this.items) {
                if (it.key != null) {
                    presentKeys.add(it.key);
                }
                if (it.pkg != null) {
                    presentPkgs.add(it.pkg);
                }
            }
            this.pinnedKeys.retainAll(presentKeys);
            this.dockedHiddenPkgs.retainAll(presentPkgs);
        }
        if (!str.startsWith("test:")) {
            this.host.sendDismiss(str);
        }
        if (this.items.isEmpty()) {
            hideAll();
            return;
        }
        refreshCurrentLevel();
        if (this.level == 1) {
            scheduleLevel1Timer();
        } else if (this.level == 2) {
            scheduleMiniTimer(this.fromDocked);
        }
    }

    // fixed111: pinned notification keys (per-notification, by item
    // key). Each notification is counted separately — pinning one
    // never pins or freezes the others, even from the same app. A
    // pinned capsule never auto-docks and a pinned card never
    // auto-shrinks; the pin stays until the user taps it again or
    // the notification is dismissed by hand. The pin icon is the
    // Flyme balloon (fixed108): white = unpinned, blue = pinned.
    private final HashSet<String> pinnedKeys = new HashSet<>();
    // fixed127: package of the app whose expanded card the user pinned.
    // Recorded in togglePin; the pinned card stays anchored to this app
    // no matter which app's notification arrives next.
    private String pinnedCardPkg = null;

    // fixed108/111: packages whose capsules docked while a pin was
    // active. Their capsule hides from the mini (applyDockedHidden)
    // and their icon shows in the shared pile (rebuildDocked skips
    // only pinned groups); showLevel3 clears this when everything
    // docks together.
    private final HashSet<String> dockedHiddenPkgs = new HashSet<>();

    private void applyDockedHidden() {
        if (this.miniView == null) {
            return;
        }
        try {
            android.view.ViewGroup container = (android.view.ViewGroup) this.miniView;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof String && this.dockedHiddenPkgs.contains((String) tag)) {
                    child.setVisibility(View.GONE);
                }
            }
        } catch (Throwable th) {
            this.host.log("applyDockedHidden failed", th);
        }
    }

    // fixed111: pin is per-notification (by item key).
    private boolean isPinned(NotifItem item) {
        return item != null && item.key != null && this.pinnedKeys.contains(item.key);
    }

    public boolean anyPinned() {
        for (NotifItem it : this.items) {
            if (isPinned(it)) {
                return true;
            }
        }
        return false;
    }

    // fixed110: is the currently expanded card pinned?
    // fixed126: the pin is per-item (by key). A new notification from
    // the SAME app becomes the card's top item with a different key, so
    // checking only currentCardItem() would miss the pin and let the new
    // notification replace the pinned card. The card is pinned if ANY
    // of its displayed items (its app, expandedPkg) is pinned.
    // fixed127: anchor to pinnedCardPkg (recorded when the user pins
    // the card) so the check holds even if expandedPkg is null, and a
    // new notification from ANY app can never unpin or replace it.
    private boolean isCardPinned() {
        if (this.level != 1) {
            return false;
        }
        String cardPkg = this.pinnedCardPkg != null ? this.pinnedCardPkg : this.expandedPkg;
        if (cardPkg != null) {
            for (NotifItem it : this.items) {
                if (cardPkg.equals(it.pkg) && isPinned(it)) {
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

    private NotifItem currentCardItem() {
        if (this.expandedPkg != null) {
            for (NotifItem it : this.items) {
                if (this.expandedPkg.equals(it.pkg)) {
                    return it;
                }
            }
        }
        if (!this.items.isEmpty()) {
            return this.items.get(0);
        }
        return null;
    }

    // fixed132: minimal unpin for a manual swipe-to-dock. The pin only
    // blocks the 3s auto-dock; a manual swipe is an explicit dock, so the
    // pin comes off first and the item docks normally into the pile.
    // No level refresh here — the dock call that follows rebuilds
    // everything anyway (togglePin's refresh would rebuild the card
    // mid-swipe and break the dock animation's container check).
    private void unpinForDock(NotifItem item) {
        if (item == null || item.key == null) {
            return;
        }
        this.pinnedKeys.remove(item.key);
        if (item.pkg != null && item.pkg.equals(this.pinnedCardPkg)) {
            this.pinnedCardPkg = null;
        }
    }

    private void togglePin(NotifItem item) {
        if (item == null || item.key == null) {
            return;
        }
        boolean wasCardPinned = isCardPinned();
        NotifItem cardItem = currentCardItem();
        if (this.pinnedKeys.contains(item.key)) {
            this.pinnedKeys.remove(item.key);
        } else {
            this.pinnedKeys.add(item.key);
        }
        // fixed127: record which app's card is pinned. Only the open
        // card's own item counts — an overlay capsule's pin must not
        // re-anchor the card to another app.
        if (this.level == 1 && this.expandedPkg != null && this.expandedPkg.equals(item.pkg)) {
            if (this.pinnedKeys.contains(item.key)) {
                this.pinnedCardPkg = item.pkg;
            } else if (item.pkg != null && item.pkg.equals(this.pinnedCardPkg)) {
                this.pinnedCardPkg = null;
            }
        }
        // fixed110: unpinning the expanded card itself drops the
        // capsule overlay (it only lives under a pinned card).
        // Unpinning a capsule in the overlay leaves it alone.
        if (wasCardPinned && cardItem != null && cardItem.key != null && cardItem.key.equals(item.key) && !isPinned(item)) {
            removeMiniView();
        }
        refreshCurrentLevel();
        try {
            this.host.repositionPile();
        } catch (Throwable ignored) {
        }
        if (this.level == 1) {
            scheduleLevel1Timer();
        } else if (this.level == 2) {
            scheduleMiniTimer(this.fromDocked);
        }
    }

    // fixed108: the pin is the Flyme balloon itself (exact shape
    // extracted from the user's reference images, embedded as PNG):
    // pinned = blue balloon + white arc, unpinned = white balloon +
    // blue arc. Decoded once per state and cached.
    private static final String PIN_BALLOON_ON_B64 = "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";
    private static final String PIN_BALLOON_OFF_B64 = "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";
    private Bitmap pinBitmapOn;
    private Bitmap pinBitmapOff;

    private Bitmap pinBitmap(boolean pinned) {
        try {
            if (pinned) {
                if (this.pinBitmapOn == null) {
                    byte[] data = android.util.Base64.decode(PIN_BALLOON_ON_B64, android.util.Base64.DEFAULT);
                    this.pinBitmapOn = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                }
                if (this.pinBitmapOn != null) {
                    return this.pinBitmapOn;
                }
            } else {
                if (this.pinBitmapOff == null) {
                    byte[] data = android.util.Base64.decode(PIN_BALLOON_OFF_B64, android.util.Base64.DEFAULT);
                    this.pinBitmapOff = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                }
                if (this.pinBitmapOff != null) {
                    return this.pinBitmapOff;
                }
            }
        } catch (Throwable ignored) {
        }
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(pinned ? 0xFF00A0E9 : 0xFF96969B);
        c.drawCircle(48, 48, 40, p);
        return bmp;
    }


    private ImageView buildPinView(final NotifItem item) {
        ImageView iv = new ImageView(this.ctx);
        iv.setImageBitmap(pinBitmap(isPinned(item)));
        iv.setClickable(true);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    NotifBubble.this.togglePin(item);
                    // fixed127: the pinned card is no longer rebuilt
                    // (refreshLevel1 freezes it), so flip the tapped
                    // pin's icon in place.
                    if (v instanceof ImageView) {
                        ((ImageView) v).setImageBitmap(NotifBubble.this.pinBitmap(NotifBubble.this.isPinned(item)));
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("pin click failed", th);
                }
            }
        });
        return iv;
    }

    private int bubbleWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).cardWidthDp();
            }
        } catch (Throwable ignored) {
        }
        return this.host.isLandscape() ? Settings.BUBBLE_WIDTH_LANDSCAPE_DP : Settings.BUBBLE_WIDTH_DP;
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
        ArrayList<NotifItem> arrayList;
        removeLevel1View();
        if (this.items.isEmpty()) {
            return;
        }
        // fixed43: the expanded card shows only the selected app's
        // notifications (expandedPkg, set when the user taps that app's
        // capsule). If the selected app has no items left, fall back to
        // all items and clear the selection.
        ArrayList<NotifItem> displayItems = this.items;
        if (this.expandedPkg != null) {
            ArrayList<NotifItem> filtered = new ArrayList<>();
            for (NotifItem it : this.items) {
                if (this.expandedPkg.equals(it.pkg)) {
                    filtered.add(it);
                }
            }
            if (!filtered.isEmpty()) {
                displayItems = filtered;
            } else {
                this.expandedPkg = null;
            }
        }
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(1);
        linearLayout.setClipChildren(false);
        int iDp = this.host.dp(bubbleWidthDp());
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(iDp, -2));
        int iMin = Math.min(displayItems.size(), 3);
        int i = 0;
        while (true) {
            arrayList = displayItems;
            if (i >= iMin) {
                break;
            }
            NotifItem notifItem = arrayList.get(i);
            View viewBuildCard = buildCard(notifItem);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) {
                layoutParams.topMargin = this.host.dp(8);
            }
            linearLayout.addView(viewBuildCard, layoutParams);
            CodeInfo codeInfo = notifItem.key != null ? this.codes.get(notifItem.key) : null;
            if (codeInfo != null) {
                View viewBuildAiCapsule = buildAiCapsule(codeInfo, notifItem.key);
                LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(iDp - this.host.dp(18), -2);
                layoutParams2.topMargin = this.host.dp(AI_CAPSULE_GAP_DP);
                layoutParams2.gravity = 8388613;
                linearLayout.addView(viewBuildAiCapsule, layoutParams2);
            }
            i++;
        }
        if (arrayList.size() > 3) {
            TextView textView = new TextView(this.ctx);
            textView.setText("还有 " + (displayItems.size() - 3) + " 条较早的通知");
            textView.setTextSize(2, 11.5f);
            textView.setTextColor(this.host.textDimColor());
            textView.setGravity(17);
            textView.setPadding(0, this.host.dp(AI_CAPSULE_GAP_DP), 0, this.host.dp(2));
            linearLayout.addView(textView);
        }
        // fixed102: cap the card at the user-set height (0 = auto);
        // the content scrolls inside when capped, so nothing is cut off.
        View cardView = this.host.capCardHeight(linearLayout);
        this.level1Container = cardView;
        // fixed43: the selected capsule's expanded card automatically goes
        // to the bottom of the dock (below the player, the call bubble and
        // any other notification capsules), whatever the card height.
        this.host.addDockViewAtBottom(cardView);
        // fixed74: an expanded card takes the focus — only this card
        // shows; other capsules and the docked pile are hidden.
        this.host.setFocusView(cardView);
        if (z) {
            animateIn(cardView);
        }
        this.host.updateWindowSize();
        // fixed63: no outside-tap layer any more — tell the host a card
        // is open so the dock margin pins its bottom edge to the user's
        // offset. The 3s idle timer (scheduleLevel1Timer) is what
        // shrinks it back now, not a tap outside.
        this.host.setCardOpen(true);
        // fixed42: pin the card's bottom edge to the user-set offset.
        // Rebuilds can land the dock margin on a stale layout and the
        // bottom edge drifts when the card changes height. Re-assert it
        // on the next frame, after the new content has been measured —
        // no matter how tall the card is, its bottom stays at the set
        // position. The pin is a no-op if the margin is already right,
        // and it returns early if the card was closed before it runs.
        try {
            this.host.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        NotifBubble.this.host.applyCardBottomOffset();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void removeLevel1View() {
        // fixed63: the card is gone — tell the host so the dock margin
        // drops back to 0 and the window resizes to the capsules.
        this.host.setCardOpen(false);
        if (this.level1Container != null) {
            this.host.removeDockView(this.level1Container);
            this.level1Container = null;
        }
    }

    /** fixed133: true if this item carries download/task progress. */
    private static boolean hasProgress(NotifItem item) {
        return item != null && (item.progressMax > 0 || item.progressIndeterminate);
    }

    /** fixed139: a download is in progress while it has progress and
     * has not reached its max (indeterminate counts as in progress). */
    private static boolean isDownloading(NotifItem item) {
        return item != null && (item.progressMax > 0 || item.progressIndeterminate)
                && (item.progressIndeterminate || item.progress < item.progressMax);
    }

    private boolean anyDownloading() {
        for (NotifItem item : this.items) {
            if (isDownloading(item)) {
                return true;
            }
        }
        return false;
    }

    /** fixed133: progress percent 0-100, or -1 for indeterminate / none. */
    private static int progressPercent(NotifItem item) {
        if (item == null || item.progressMax <= 0) {
            return -1;
        }
        int p = item.progress * 100 / item.progressMax;
        if (p < 0) {
            return 0;
        }
        if (p > 100) {
            return 100;
        }
        return p;
    }

    private View buildCard(final NotifItem notifItem) {
        FrameLayout frameLayout = new FrameLayout(this.ctx);
        frameLayout.setClipChildren(false);
        // fixed138: mirror the drag-hint arrows with the dock side.
        // Dock on the right: swipe right to dock ("缩小 ›"), swipe left
        // to dismiss ("‹ 取消"). Dock on the left: the actions swap,
        // so the arrows swap too ("‹ 缩小" / "取消 ›").
        boolean tmpMirror = false;
        try {
            tmpMirror = this.host.isDockLeft();
        } catch (Throwable ignored) {
        }
        final boolean hintMirror = tmpMirror;
        final TextView textView = new TextView(this.ctx);
        textView.setText(hintMirror ? "‹ 缩小" : "缩小 ›");
        textView.setTextSize(2, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-15961500);
        textView.setGravity(8388627);
        textView.setPadding(this.host.dp(14), 0, 0, 0);
        textView.setVisibility(8);
        frameLayout.addView(textView, new FrameLayout.LayoutParams(-1, -1));
        final TextView textView2 = new TextView(this.ctx);
        textView2.setText(hintMirror ? "取消 ›" : "‹ 取消");
        textView2.setTextSize(2, 13.0f);
        textView2.setTypeface(Typeface.DEFAULT_BOLD);
        textView2.setTextColor(HINT_DISMISS);
        textView2.setGravity(8388629);
        textView2.setPadding(0, 0, this.host.dp(14), 0);
        textView2.setVisibility(8);
        frameLayout.addView(textView2, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(1);
        final GradientDrawable gradientDrawableFrostedBackground = this.host.frostedBackground(20);
        linearLayout.setBackground(gradientDrawableFrostedBackground);
        linearLayout.setPadding(this.host.dp(13), this.host.dp(12), this.host.dp(13), this.host.dp(11));
        LinearLayout linearLayout2 = new LinearLayout(this.ctx);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.addView(buildIconView(notifItem, 34, 10, false));
        TextView textView3 = new TextView(this.ctx);
        textView3.setText(appLabel(notifItem.pkg));
        textView3.setTextSize(2, 12.0f);
        textView3.setTextColor(this.host.textSubColor());
        textView3.setSingleLine(true);
        textView3.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = this.host.dp(8);
        linearLayout2.addView(textView3, layoutParams);
        TextView textView4 = new TextView(this.ctx);
        textView4.setText(formatTime(notifItem.time));
        textView4.setTextSize(2, 11.0f);
        textView4.setTextColor(this.host.textDimColor());
        linearLayout2.addView(textView4);
        // fixed107: pin toggle in the card header, left of the X.
        ImageView pinView = buildPinView(notifItem);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(this.host.dp(28), this.host.dp(28));
        pinLp.leftMargin = this.host.dp(4);
        linearLayout2.addView(pinView, pinLp);
        TextView textView5 = new TextView(this.ctx);
        textView5.setText("✕");
        textView5.setTextSize(2, 17.0f);
        textView5.setTextColor(this.host.textDimColor());
        textView5.setGravity(17);
        textView5.setClickable(true);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(this.host.dp(36), this.host.dp(36));
        layoutParams2.leftMargin = this.host.dp(4);
        textView5.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    NotifBubble.this.dismissItem(notifItem.key);
                    // fixed69: like the card tap (fixed59) — after a card
                    // is dismissed by hand, the rest are capsules, not
                    // cards; only a tap expands a card again.
                    if (!NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel2(false);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("close click failed", th);
                }
            }
        });
        linearLayout2.addView(textView5, layoutParams2);
        linearLayout.addView(linearLayout2);
        if (notifItem.title != null && !notifItem.title.isEmpty()) {
            TextView textView6 = new TextView(this.ctx);
            textView6.setText(notifItem.title);
            textView6.setTextSize(2, 14.5f);
            textView6.setTypeface(Typeface.DEFAULT_BOLD);
            textView6.setTextColor(this.host.textMainColor());
            textView6.setMaxLines(2);
            textView6.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams3.topMargin = this.host.dp(8);
            linearLayout.addView(textView6, layoutParams3);
        }
        if (notifItem.text != null && !notifItem.text.isEmpty()) {
            TextView textView7 = new TextView(this.ctx);
            textView7.setText(notifItem.text);
            textView7.setTextSize(2, 13.0f);
            textView7.setTextColor(this.host.textSubColor());
            textView7.setLineSpacing(0.0f, 1.5f);
            textView7.setMaxLines(3);
            textView7.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams4.topMargin = this.host.dp(4);
            linearLayout.addView(textView7, layoutParams4);
        }
        // fixed33: belt and suspenders — a card must never render as a
        // header-only bar (screenshots 2081/2084/2088). If the Service
        // enrichment still left both fields empty, show an explicit
        // tap-to-open line instead of a blank card.
        if ((notifItem.title == null || notifItem.title.isEmpty()) && (notifItem.text == null || notifItem.text.isEmpty())) {
            TextView textView8 = new TextView(this.ctx);
            textView8.setText("点击卡片打开查看");
            textView8.setTextSize(2, 13.0f);
            textView8.setTextColor(this.host.textSubColor());
            LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams5.topMargin = this.host.dp(8);
            linearLayout.addView(textView8, layoutParams5);
        }
        // fixed133: download/task progress bar + percent in the card.
        if (hasProgress(notifItem)) {
            LinearLayout progressRow = new LinearLayout(this.ctx);
            progressRow.setOrientation(0);
            progressRow.setGravity(16);
            LinearLayout.LayoutParams progressRowLp = new LinearLayout.LayoutParams(-1, -2);
            progressRowLp.topMargin = this.host.dp(8);
            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this.ctx, null, android.R.attr.progressBarStyleHorizontal);
            if (notifItem.progressIndeterminate || notifItem.progressMax <= 0) {
                progressBar.setIndeterminate(true);
            } else {
                progressBar.setMax(notifItem.progressMax);
                progressBar.setProgress(notifItem.progress);
            }
            LinearLayout.LayoutParams progressBarLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            progressRow.addView(progressBar, progressBarLp);
            int cardPct = progressPercent(notifItem);
            if (cardPct >= 0) {
                TextView pctView = new TextView(this.ctx);
                pctView.setText(cardPct + "%");
                pctView.setTextSize(2, 12.0f);
                pctView.setTextColor(this.host.textSubColor());
                LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(-2, -2);
                pctLp.leftMargin = this.host.dp(8);
                progressRow.addView(pctView, pctLp);
            }
            linearLayout.addView(progressRow, progressRowLp);
        }
        if (notifItem.replyIntent != null && notifItem.replyKey != null) {
            linearLayout.addView(buildReplyRow(notifItem));
        }
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        frameLayout.setOnTouchListener(new SwipeListener(linearLayout, true, true, new SwipeListener.Callback() { // from class: com.jarvis.bubblenotify.NotifBubble.5
            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onTap() {
                try {
                    if (!NotifBubble.this.isTapOpen()) {
                        return;
                    }
                    // fixed15: after tapping the card to open in a small
                    // window, the notification must be dismissed. Dismiss
                    // runs even if the open throws, so the bubble never
                    // lingers after the app opens.
                    try {
                        if (notifItem.intent != null) {
                            NotifBubble.this.host.openPendingIntent(notifItem.intent);
                        }
                    } finally {
                        NotifBubble.this.dismissItem(notifItem.key);
                        // fixed59: supersedes fixed57 (which docked the
                        // rest into the shared pile). After the tapped
                        // card opens in a small window, the remaining
                        // notifications shrink back to capsules instead
                        // of docking or staying expanded — showLevel2
                        // from level 1 plays the fixed58 shrink
                        // animation, so the rebuilt card scales down
                        // from its expanded position into the capsules.
                        // (dismissItem already hid everything when this
                        // was the last one.)
                        if (!NotifBubble.this.items.isEmpty()) {
                            NotifBubble.this.showLevel2(false);
                        }
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("card tap failed", th);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeLeft() {
                try {
                    NotifBubble.this.dismissItem(notifItem.key);
                    // fixed69: like the card tap (fixed59) — after a card
                    // is dismissed by hand, the rest are capsules, not
                    // cards; only a tap expands a card again.
                    if (!NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel2(false);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("card swipe left failed", th);
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onSwipeRight() {
                try {
                    // fixed132: swiping a pinned card to dock auto-unpins
                    // it first (user: "钉住后滑动自动解除钉住"). The pin
                    // only blocks the 3s auto-dock; a manual swipe docks
                    // the card into the pile, so the pin comes off and
                    // rebuildDocked includes the group (previously the
                    // pinned group was skipped and the pile lost icons).
                    if (NotifBubble.this.isCardPinned()) {
                        NotifBubble.this.unpinForDock(NotifBubble.this.currentCardItem());
                    }
                    // fixed30: card right-swipe now docks directly (level 3)
                    // instead of shrinking to the mini capsule (level 2).
                    if (NotifBubble.this.level1Container != null && NotifBubble.this.level == 1) {
                        final View linearLayout3 = NotifBubble.this.level1Container;
                        linearLayout3.animate().withLayer().translationX(NotifBubble.this.host.dp(120)).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.5.1
                            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                            public void onAnimationEnd(Animator animator) {
                                linearLayout3.setTranslationX(0.0f);
                                linearLayout3.setAlpha(1.0f);
                                // fixed31: only dock if the animating card
                                // is still the current one. A new
                                // notification during the 180ms swipe
                                // rebuilds the card (new container); the old
                                // animation must not dock the fresh card.
                                if (NotifBubble.this.level == 1 && NotifBubble.this.level1Container == linearLayout3 && !NotifBubble.this.items.isEmpty()) {
                                    NotifBubble.this.showLevel3();
                                }
                            }
                        }).start();
                    } else if (NotifBubble.this.level == 1 && !NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.showLevel3();
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("card swipe right failed", th);
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
                if (NotifBubble.this.level == 1) {
                    NotifBubble.this.scheduleLevel1Timer();
                }
            }

            @Override // com.jarvis.bubblenotify.NotifBubble.SwipeListener.Callback
            public void onDragProgress(float f) {
                int iDp = NotifBubble.this.host.dp(28);
                // fixed138: mirror the drag hints with the dock side.
                // f is the physical displacement; the dock action is a
                // rightward drag on the right edge and a leftward drag
                // on the left edge. Show the dock hint + blue border for
                // a drag toward the dock edge, the dismiss hint + red
                // border for a drag away from it.
                boolean dockDrag = hintMirror ? f < (-iDp) : f > iDp;
                boolean dismissDrag = hintMirror ? f > iDp : f < (-iDp);
                if (dismissDrag) {
                    textView2.setVisibility(0);
                    textView.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(1), NotifBubble.HINT_DISMISS);
                    return;
                }
                if (dockDrag) {
                    textView.setVisibility(0);
                    textView2.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(1), -15961500);
                } else {
                    textView.setVisibility(8);
                    textView2.setVisibility(8);
                    gradientDrawableFrostedBackground.setStroke(NotifBubble.this.host.dp(1), NotifBubble.this.host.borderColor());
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
        editText.setTextSize(2, 13.0f);
        editText.setSingleLine(true);
        editText.setInputType(1);
        editText.setImeOptions(4);
        editText.setPadding(this.host.dp(10), this.host.dp(7), this.host.dp(10), this.host.dp(7));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(REPLY_INPUT_BG);
        gradientDrawable.setCornerRadius(this.host.dp(999));
        editText.setBackground(gradientDrawable);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.rightMargin = this.host.dp(8);
        editText.setLayoutParams(layoutParams2);
        editText.setOnTouchListener(new View.OnTouchListener() { // from class: com.jarvis.bubblenotify.NotifBubble.6

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
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.jarvis.bubblenotify.NotifBubble.7
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
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.jarvis.bubblenotify.NotifBubble.8
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
        textView.setTextSize(2, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-1);
        textView.setGravity(17);
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(-15961500);
        gradientDrawable2.setCornerRadius(this.host.dp(999));
        textView.setBackground(gradientDrawable2);
        textView.setPadding(this.host.dp(14), this.host.dp(7), this.host.dp(14), this.host.dp(7));
        textView.setClickable(true);
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                NotifBubble.this.sendReply(notifItem, editText, textView);
            }
        });
        linearLayout.addView(textView);
        return linearLayout;
    }

    /* JADX INFO: Access modifiers changed from: private */
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
                        textView.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.11
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

    /* JADX INFO: Access modifiers changed from: private */
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

    /* JADX INFO: Access modifiers changed from: private */
    public void setReplyFocusable(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setReplyFocusable(z);
            }
        } catch (Throwable th) {
            this.host.log("setReplyFocusable failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
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

    /* JADX INFO: Access modifiers changed from: private */
    public void showKeyboard(View view) {
        try {
            InputMethodManager inputMethodManager = (InputMethodManager) this.ctx.getSystemService("input_method");
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(view, 1);
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

    private View buildAiCapsule(final CodeInfo codeInfo, final String notifKey) {
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(1);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(HANDLE_BG);
        gradientDrawable.setCornerRadius(this.host.dp(22));
        gradientDrawable.setStroke(this.host.dp(1), 1935461302);
        linearLayout.setBackground(gradientDrawable);
        linearLayout.setPadding(this.host.dp(12), this.host.dp(8), this.host.dp(10), this.host.dp(9));
        LinearLayout linearLayout2 = new LinearLayout(this.ctx);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        // fixed72: the "AI" badge is gone (user 2026-11-17 21:19
        // "前面的ai图标去掉") — the label stands alone, no chip.
        TextView textView2 = new TextView(this.ctx);
        textView2.setText("识别提取 · " + codeInfo.kind);
        textView2.setTextSize(2, 11.5f);
        textView2.setTextColor(-15961500);
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2);
        LinearLayout linearLayout3 = new LinearLayout(this.ctx);
        linearLayout3.setOrientation(0);
        linearLayout3.setGravity(16);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = this.host.dp(AI_CAPSULE_GAP_DP);
        TextView textView3 = new TextView(this.ctx);
        textView3.setText(codeInfo.code);
        textView3.setTextSize(2, 24.0f);
        textView3.setTypeface(Typeface.DEFAULT_BOLD);
        textView3.setTextColor(CODE_TEXT);
        textView3.setLetterSpacing(0.12f);
        textView3.setSingleLine(true);
        textView3.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(textView3, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final TextView textView4 = new TextView(this.ctx);
        textView4.setText("复制");
        textView4.setTextSize(2, 12.5f);
        textView4.setTextColor(CODE_TEXT);
        textView4.setGravity(17);
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setColor(620738381);
        gradientDrawable3.setCornerRadius(this.host.dp(8));
        gradientDrawable3.setStroke(this.host.dp(1), -2130725043);
        textView4.setBackground(gradientDrawable3);
        textView4.setMinHeight(this.host.dp(36));
        textView4.setPadding(this.host.dp(12), this.host.dp(AI_CAPSULE_GAP_DP), this.host.dp(12), this.host.dp(AI_CAPSULE_GAP_DP));
        textView4.setClickable(true);
        textView4.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.NotifBubble.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    ClipboardManager clipboardManager = (ClipboardManager) NotifBubble.this.ctx.getSystemService("clipboard");
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(Settings.EXTRA_CODE, codeInfo.code));
                    }
                    textView4.setText("已复制 ✓");
                    textView4.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.NotifBubble.12.1
                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                textView4.setText("复制");
                            } catch (Throwable th) {
                            }
                        }
                    }, NotifBubble.COPY_TIP_MS);
                    // fixed72: after copying, the notification the code
                    // came from goes away (user 2026-11-17 21:19 "点击复制
                    // 后自动消掉短信通知") — same as the card X button:
                    // dismiss this item, the rest become capsules.
                    // 600ms so the "已复制 ✓" flash is still visible.
                    textView4.postDelayed(new Runnable() {
                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                NotifBubble.this.dismissItem(notifKey);
                                if (!NotifBubble.this.items.isEmpty()) {
                                    NotifBubble.this.showLevel2(false);
                                }
                            } catch (Throwable th) {
                                NotifBubble.this.host.log("copy dismiss failed", th);
                            }
                        }
                    }, 600);
                } catch (Throwable th) {
                    NotifBubble.this.host.log("copy code failed", th);
                }
            }
        });
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams3.leftMargin = this.host.dp(8);
        linearLayout3.addView(textView4, layoutParams3);
        linearLayout.addView(linearLayout3, layoutParams2);
        return linearLayout;
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
    // fixed43: group the current items by package (app), preserving the
    // order of first appearance (most recent first). Each group is one
    // app's notifications: element 0 is that app's most recent item.
    private ArrayList<ArrayList<NotifItem>> groupItemsByApp() {
        ArrayList<ArrayList<NotifItem>> groups = new ArrayList<>();
        for (NotifItem item : this.items) {
            String pkg = item.pkg;
            ArrayList<NotifItem> group = null;
            for (ArrayList<NotifItem> g : groups) {
                if (!g.isEmpty()) {
                    String gp = g.get(0).pkg;
                    if (gp == null ? pkg == null : gp.equals(pkg)) {
                        group = g;
                        break;
                    }
                }
            }
            if (group == null) {
                group = new ArrayList<>();
                groups.add(group);
            }
            group.add(item);
        }
        // fixed74: capsules are ordered purely by notification time —
        // each group ranks by its newest item's post time (newest
        // first), and a handled app never sinks. The old fixed45
        // bottomPkgs sinking is deleted. This order drives both the
        // mini capsules and the docked stack.
        Collections.sort(groups, new Comparator<ArrayList<NotifItem>>() {
            @Override
            public int compare(ArrayList<NotifItem> a, ArrayList<NotifItem> b) {
                long ta = (a == null || a.isEmpty()) ? 0L : a.get(0).time;
                long tb = (b == null || b.isEmpty()) ? 0L : b.get(0).time;
                return ta < tb ? 1 : (ta > tb ? -1 : 0);
            }
        });
        return groups;
    }

    private void rebuildMini(boolean z) {
        removeMiniView();
        if (this.items.isEmpty()) {
            return;
        }
        // fixed43: one capsule per app, shown separately instead of a
        // single merged "N 条通知" capsule. Each app's capsule is its own
        // pill (icon + label + latest text), stacked vertically in one
        // container so the level system still sees a single mini view.
        // Tapping a capsule expands only that app's card.
        ArrayList<ArrayList<NotifItem>> groups = groupItemsByApp();
        // fixed116: pinned capsules anchor at the edge side. Upper
        // half: pinned first (top), others below. Lower half: others
        // first (above), pinned last (bottom). Each side keeps
        // notification-time order.
        if (anyPinned()) {
            ArrayList<ArrayList<NotifItem>> pinnedGroups = new ArrayList<>();
            ArrayList<ArrayList<NotifItem>> otherGroups = new ArrayList<>();
            for (ArrayList<NotifItem> group : groups) {
                if (!group.isEmpty() && isPinned(group.get(0))) {
                    pinnedGroups.add(group);
                } else {
                    otherGroups.add(group);
                }
            }
            boolean upper = false;
            try {
                upper = this.host.isDockUpper();
            } catch (Throwable ignored) {
            }
            groups.clear();
            if (upper) {
                groups.addAll(pinnedGroups);
                groups.addAll(otherGroups);
            } else {
                groups.addAll(otherGroups);
                groups.addAll(pinnedGroups);
            }
        }
        LinearLayout container = new LinearLayout(this.ctx);
        container.setOrientation(1);
        container.setClipChildren(false);
        for (int gi = 0; gi < groups.size(); gi++) {
            ArrayList<NotifItem> group = groups.get(gi);
            View capsule = buildMiniCapsule(group.get(0), group.size());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (gi > 0) {
                lp.topMargin = this.host.dp(8);
            }
            container.addView(capsule, lp);
        }
        this.miniView = container;
        this.host.addDockView(container);
        // fixed79: a fresh pop shows only the new app's capsule.
        applySoloCapsule();
        // fixed108: capsules that auto-docked while a pin is active
        // stay hidden until the pin is released.
        applyDockedHidden();
        if (z) {
            animateIn(container);
        }
        this.host.updateWindowSize();
    }

    // fixed79: hide every mini capsule except the fresh pop's app
    // (soloPkg). Null soloPkg = show all (expand-all). If the solo app
    // has no capsule any more (its last item was dismissed), fall back
    // to showing all so the mini never comes up empty.
    private void applySoloCapsule() {
        if (this.miniView == null) {
            return;
        }
        try {
            // miniView is declared as View; the container is a LinearLayout.
            android.view.ViewGroup container = (android.view.ViewGroup) this.miniView;
            boolean found = false;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (this.soloPkg != null && this.soloPkg.equals(child.getTag())) {
                    found = true;
                    break;
                }
            }
            boolean showAll = this.soloPkg == null || !found;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                child.setVisibility(showAll || this.soloPkg.equals(child.getTag()) ? View.VISIBLE : View.GONE);
            }
            if (!found) {
                this.soloPkg = null;
            }
        } catch (Throwable th) {
            this.host.log("applySoloCapsule failed", th);
        }
    }

    // fixed43: build one app's mini capsule (a single pill). topItem is
    // that app's most recent notification; countForApp is how many
    // notifications that app currently has. Tap expands only this app's
    // card (sets expandedPkg); swipe left dismisses this app's top item;
    // swipe right docks.
    private View buildMiniCapsule(final NotifItem topItem, final int countForApp) {
        FrameLayout frameLayout = new FrameLayout(this.ctx);
        frameLayout.setClipChildren(false);
        // fixed75: the 6dp right padding made this frame 206dp wide while
        // the pill inside is 200dp — the pill sat 6dp left of the call /
        // music capsules (screenshot 2196: notification pill's right edge
        // 6dp short). Drop the right padding; the frame is now exactly
        // 200dp and the pill's edges line up with the other capsules.
        // fixed79: tag the capsule with its app so a fresh pop can hide
        // the older apps' capsules (see soloPkg / applySoloCapsule).
        frameLayout.setTag(topItem.pkg);
        frameLayout.setPadding(0, this.host.dp(AI_CAPSULE_GAP_DP), 0, 0);
        LinearLayout linearLayout = new LinearLayout(this.ctx);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setBackground(this.host.pillBackground());
        linearLayout.setPadding(this.host.dp(AI_CAPSULE_GAP_DP), this.host.dp(AI_CAPSULE_GAP_DP), this.host.dp(14), this.host.dp(AI_CAPSULE_GAP_DP));
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(this.host.dp(capsuleWidthDp()), this.host.dp(capsuleHeightDp())));
        FrameLayout frameLayout2 = new FrameLayout(this.ctx);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(-1);
        frameLayout2.setBackground(gradientDrawable);
        ImageView imageView = new ImageView(this.ctx);
        setNotifIcon(imageView, topItem, true, 0, 0);
        frameLayout2.addView(imageView, new FrameLayout.LayoutParams(this.host.dp(35), this.host.dp(35), 17));
        linearLayout.addView(frameLayout2, new LinearLayout.LayoutParams(this.host.dp(38), this.host.dp(38)));
        LinearLayout linearLayout2 = new LinearLayout(this.ctx);
        linearLayout2.setOrientation(1);
        linearLayout2.setGravity(16);
        // fixed110: the text column flexes (weight 1) so the pin stays
        // at a fixed right end — long text can no longer push/clip it.
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = this.host.dp(8);
        TextView textView = new TextView(this.ctx);
        textView.setText(appLabel(topItem.pkg) + (countForApp > 1 ? " · " + countForApp + " 条通知" : ""));
        textView.setTextSize(2, 13.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(this.host.textMainColor());
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setMaxWidth(this.host.dp(bubbleWidthDp() - 110));
        linearLayout2.addView(textView);
        TextView textView2 = new TextView(this.ctx);
        CodeInfo codeInfo = topItem.key != null ? this.codes.get(topItem.key) : null;
        String miniSub;
        if (codeInfo != null) {
            miniSub = codeInfo.kind + " " + codeInfo.code;
        } else if (topItem.title != null && !topItem.title.isEmpty()) {
            miniSub = topItem.title;
        } else {
            miniSub = topItem.text != null ? topItem.text : "";
        }
        // fixed133: show download progress percent in the capsule second line.
        int miniPct = progressPercent(topItem);
        if (miniPct >= 0) {
            miniSub = miniSub + " " + miniPct + "%";
        }
        textView2.setText(miniSub);
        textView2.setTextSize(2, 11.0f);
        textView2.setTextColor(this.host.textSubColor());
        textView2.setSingleLine(true);
        textView2.setEllipsize(TextUtils.TruncateAt.END);
        textView2.setMaxWidth(this.host.dp(bubbleWidthDp() - 110));
        linearLayout2.addView(textView2);
        linearLayout.addView(linearLayout2, layoutParams);
        // fixed107: pin toggle at the capsule's right end. The clickable
        // child consumes the tap, so the capsule swipe listener never
        // sees it — same pattern as the card's X button.
        // fixed110: fixed position — the text column flexes (above), so
        // the pin never moves; nudged down 4dp so the count badge
        // (top-right) cannot cover it.
        ImageView pinView = buildPinView(topItem);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(this.host.dp(22), this.host.dp(22));
        pinLp.leftMargin = this.host.dp(6);
        pinLp.topMargin = this.host.dp(4);
        linearLayout.addView(pinView, pinLp);
        if (countForApp > 1) {
            TextView textView3 = new TextView(this.ctx);
            textView3.setText(String.valueOf(countForApp));
            textView3.setTextSize(2, 12.0f);
            textView3.setTypeface(Typeface.DEFAULT_BOLD);
            textView3.setTextColor(-1);
            textView3.setGravity(17);
            GradientDrawable gradientDrawable2 = new GradientDrawable();
            gradientDrawable2.setShape(1);
            gradientDrawable2.setColor(HINT_DISMISS);
            textView3.setBackground(gradientDrawable2);
            textView3.setMinWidth(this.host.dp(21));
            textView3.setMinHeight(this.host.dp(21));
            textView3.setPadding(this.host.dp(4), 0, this.host.dp(4), 0);
            frameLayout.addView(textView3, new FrameLayout.LayoutParams(-2, -2, 8388661));
            textView3.setTranslationX(0.0f);
            textView3.setTranslationY(0.0f);
        }
        frameLayout.setOnTouchListener(new SwipeListener(frameLayout, true, true, new SwipeListener.Callback() {
            @Override
            public void onTap() {
                try {
                    if (NotifBubble.this.level == 2 && !NotifBubble.this.items.isEmpty()) {
                        if (!NotifBubble.this.isCardExpand()) {
                            // fixed101: card-expand off — tapping the
                            // capsule opens the app directly (small
                            // window / fullscreen per the freeform
                            // switch), like tapping the card; the
                            // notification is dismissed the same way.
                            try {
                                if (NotifBubble.this.isTapOpen() && topItem.intent != null) {
                                    NotifBubble.this.host.openPendingIntent(topItem.intent);
                                }
                            } finally {
                                NotifBubble.this.dismissItem(topItem.key);
                                if (!NotifBubble.this.items.isEmpty()) {
                                    NotifBubble.this.showLevel2(false);
                                }
                            }
                            return;
                        }
                        NotifBubble.this.expandedPkg = topItem.pkg;
                        NotifBubble.this.showLevel1();
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini tap failed", th);
                }
            }

            @Override
            public void onSwipeLeft() {
                try {
                    if (NotifBubble.this.level == 2 && !NotifBubble.this.items.isEmpty()) {
                        NotifBubble.this.dismissItem(topItem.key);
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini swipe left failed", th);
                }
            }

            @Override
            public void onSwipeRight() {
                try {
                    if (NotifBubble.this.level == 2 && !NotifBubble.this.items.isEmpty()) {
                        // fixed132: swiping a pinned capsule to dock
                        // auto-unpins it first (user: "钉住后滑动自动解除
                        // 钉住"). The pin only blocks the 3s auto-dock;
                        // a manual swipe docks the capsule into the pile,
                        // so the pin comes off and rebuildDocked includes
                        // the group (previously the pinned group was
                        // skipped and the pile lost its icons).
                        if (NotifBubble.this.isPinned(topItem)) {
                            NotifBubble.this.unpinForDock(topItem);
                        }
                        NotifBubble.this.dockFromMini();
                    }
                } catch (Throwable th) {
                    NotifBubble.this.host.log("mini swipe right failed", th);
                }
            }

            @Override
            public void onDragStart() {
                NotifBubble.this.setSwipeMode(true);
                NotifBubble.this.host.getHandler().removeCallbacks(NotifBubble.this.toDockRunnable);
            }

            @Override
            public void onDragEnd() {
                NotifBubble.this.setSwipeMode(false);
                if (NotifBubble.this.level == 2) {
                    NotifBubble.this.scheduleMiniTimer(NotifBubble.this.fromDocked);
                }
            }

            @Override
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
    private void rebuildDocked(boolean z) {
        // fixed67: snapshot which apps were already in the pile BEFORE
        // rebuilding — their icons must not move with the shrink
        // animation when another capsule docks (user 2026-11-17 20:34:
        // "已经贴边的图标不要跟着缩小动画动"). Only the newly docking
        // icon animates, and it just slides vertically up into the
        // pile ("贴边往上靠"), no scale pulse.
        ArrayList<String> prevDocked = new ArrayList<>(this.dockedPkgs);
        removeDockedView();
        if (this.items.isEmpty()) {
            return;
        }
        // fixed49: notification docked icons go into the SHARED docked pile
        // (BubbleManager), overlapping with the player disc, call avatar
        // and nav icon — one pile at the edge, above the capsules, with a
        // single centered handle. No per-bubble docked container here any
        // more (fixed47's [handle][iconStack] row is replaced by the pile).
        ArrayList<ArrayList<NotifItem>> groups = groupItemsByApp();
        // Add oldest first so the most recent ends up on top (drawn last).
        ArrayList<View> newIcons = new ArrayList<>();
        for (int gi = groups.size() - 1; gi >= 0; gi--) {
            ArrayList<NotifItem> group = groups.get(gi);
            // fixed111: a pinned group stays out as a capsule — it
            // gets no pile icon while pinned.
            if (group != null && !group.isEmpty() && isPinned(group.get(0))) {
                continue;
            }
            View icon = buildDockedIcon(group.get(0), group.size());
            this.dockedPileIcons.add(icon);
            if (!prevDocked.contains(group.get(0).pkg)) {
                newIcons.add(icon);
            }
            this.host.addDockedIcon(icon, new Runnable() {
                @Override
                public void run() {
                    try {
                        // fixed113: a pin holds a capsule out (level 2)
                        // while the rest docked — the pile icons are up
                        // even though the level is not 3, so the tap must
                        // still call the docked capsules back out.
                        if ((NotifBubble.this.level == 3 || (NotifBubble.this.level == 2 && !NotifBubble.this.dockedHiddenPkgs.isEmpty())) && !NotifBubble.this.items.isEmpty()) {
                            // fixed74: tapping any docked icon expands ALL
                            // capsules (notifications, player, call, nav),
                            // not just this bubble's + the player's.
                            NotifBubble.this.host.expandAllToMini();
                        }
                    } catch (Throwable th) {
                        NotifBubble.this.host.log("docked tap failed", th);
                    }
                }
            });
        }
        this.dockedPkgs.clear();
        for (ArrayList<NotifItem> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            // fixed111: pinned groups have no pile icon — keep
            // dockedPkgs in sync with the icons actually added.
            if (isPinned(group.get(0))) {
                continue;
            }
            if (!this.dockedPkgs.contains(group.get(0).pkg)) {
                this.dockedPkgs.add(group.get(0).pkg);
            }
        }
        if (z) {
            for (View icon : newIcons) {
                animateDockIn(icon);
            }
        }
        this.host.updateWindowSize();
    }

    // fixed67: a newly docking icon does not scale in — it just slides
    // vertically up into the pile (from the capsule area below),
    // "贴边往上靠" against the edge. Already-docked icons get no
    // animation at all.
    // fixed68: no fade either (user 2026-11-17 20:45: still popping,
    // not smooth) — the fade-in read as a pop; a pure vertical slide
    // is the whole entrance.
    private void animateDockIn(final View icon) {
        try {
            icon.animate().cancel();
            icon.setTranslationY(this.host.dp(16));
            icon.animate().withLayer().translationY(0.0f).setDuration(ENTER_ANIM_MS).setListener(null).start();
        } catch (Throwable th) {
            this.host.log("animateDockIn failed", th);
        }
    }

    // fixed43: build one app's docked strip ([handle][icon], icon against
    // the edge) wrapped in a FrameLayout that carries TAG_EDGE and the
    // per-app count badge. topItem is that app's most recent notification.
    // Tap goes back to the mini capsules (level 2), where each app's
    // capsule is shown separately.
    // fixed47: build one app's docked icon (no handle — the stack has a
    // single centered handle, see rebuildDocked). Returns the 22dp-wide
    // icon frame (34dp icon overflowing right toward the edge, START
    // gravity, so the missing half stays against the edge) wrapped in a
    // FrameLayout that carries TAG_EDGE and the per-app count badge (only
    // when this app has more than one notification).
    private View buildDockedIcon(final NotifItem topItem, final int countForApp) {
        FrameLayout iconFrame = new FrameLayout(this.ctx);
        ImageView imageView = new ImageView(this.ctx);
        setNotifIcon(imageView, topItem, true, 0, 0);
        iconFrame.addView(imageView, new FrameLayout.LayoutParams(this.host.dp(34), this.host.dp(34), 8388627));
        FrameLayout dockWrap = new FrameLayout(this.ctx);
        // fixed26: the wrapper itself must carry TAG_EDGE. addDockView
        // decides the right margin from the VIEW IT RECEIVES, not from the
        // inner strip.
        dockWrap.setTag(BubbleManager.TAG_EDGE);
        dockWrap.addView(iconFrame, new FrameLayout.LayoutParams(this.host.dp(22), this.host.dp(34)));
        // fixed6: docked state also shows the count badge, at the top-right
        // corner, smaller than the mini capsule badge (16dp / 10sp). The
        // badge overlays the icon inside a FrameLayout so it stays inside
        // the window instead of being clipped. Shown only when this app
        // has more than one notification.
        if (countForApp > 1) {
            TextView dockBadge = new TextView(this.ctx);
            dockBadge.setText(String.valueOf(countForApp));
            dockBadge.setTextSize(2, 10.0f);
            dockBadge.setTypeface(Typeface.DEFAULT_BOLD);
            dockBadge.setTextColor(-1);
            dockBadge.setGravity(17);
            GradientDrawable dockBadgeBg = new GradientDrawable();
            dockBadgeBg.setShape(1);
            dockBadgeBg.setColor(HINT_DISMISS);
            dockBadge.setBackground(dockBadgeBg);
            dockBadge.setMinWidth(this.host.dp(16));
            dockBadge.setMinHeight(this.host.dp(16));
            dockBadge.setPadding(this.host.dp(3), 0, this.host.dp(3), 0);
            dockWrap.addView(dockBadge, new FrameLayout.LayoutParams(-2, -2, 8388661));
        }
        return dockWrap;
    }

    private void removeDockedView() {
        if (!this.dockedPileIcons.isEmpty()) {
            for (View icon : this.dockedPileIcons) {
                this.host.removeDockedIcon(icon);
            }
            this.dockedPileIcons.clear();
        }
        // fixed67: the pile is empty now — the next rebuild treats every
        // icon as new (rebuildDocked snapshots dockedPkgs first, so this
        // clear does not affect the snapshot taken before this call).
        this.dockedPkgs.clear();
    }

    private class SwipeListener implements View.OnTouchListener {
        private final Callback callback;
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
        // fixed112: long-press drag to move the dock.
        private Runnable dockDragLongPress;
        private float dockDragDownRawX;
        private float dockDragDownRawY;

        interface Callback {
            void onDragEnd();

            void onDragProgress(float f);

            void onDragStart();

            void onSwipeLeft();

            void onSwipeRight();

            void onTap();
        }

        SwipeListener(View view, boolean z, boolean z2, Callback callback) {
            this.target = view;
            this.swipeLeftEnabled = z;
            this.swipeRightEnabled = z2;
            this.callback = callback;
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            // fixed112: long-press drag to move the dock — capsules and
            // cards can be dragged to reposition, not just the pile.
            try {
                int action = motionEvent.getActionMasked();
                if (action == 0) {
                    this.dockDragDownRawX = motionEvent.getRawX();
                    this.dockDragDownRawY = motionEvent.getRawY();
                    if (this.dockDragLongPress == null) {
                        this.dockDragLongPress = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    NotifBubble.this.host.startDockDrag();
                                } catch (Throwable ignored) {
                                }
                            }
                        };
                    }
                    NotifBubble.this.host.getHandler().postDelayed(this.dockDragLongPress, android.view.ViewConfiguration.getLongPressTimeout());
                } else if (action == 2) {
                    if (NotifBubble.this.host.isDockDragging()) {
                        NotifBubble.this.host.moveDockDrag(motionEvent.getRawY());
                        return true;
                    }
                    float dx = motionEvent.getRawX() - this.dockDragDownRawX;
                    float dy = motionEvent.getRawY() - this.dockDragDownRawY;
                    int slop = android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                    if (dx * dx + dy * dy > slop * slop) {
                        NotifBubble.this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    }
                } else if (action == 1 || action == 3) {
                    NotifBubble.this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    if (NotifBubble.this.host.isDockDragging()) {
                        NotifBubble.this.host.endDockDrag(motionEvent.getRawX(), motionEvent.getRawY());
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            float xVelocity;
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
                    case 1:
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
                        boolean z = Math.abs(x) > ((float) NotifBubble.this.host.dp(84)) || Math.abs(xVelocity) > 1000.0f;
                        // fixed103: mirror left/right when the dock is on
                        // the left edge. The view still flies off in the
                        // physical swipe direction (i); only the action is
                        // mirrored, and the enabled flag follows the action.
                        final boolean physicalRight = x >= 0.0f;
                        boolean mirrorSwipes = false;
                        try {
                            mirrorSwipes = NotifBubble.this.host.isDockLeft();
                        } catch (Throwable ignored) {
                        }
                        final boolean swipeRight = mirrorSwipes ? !physicalRight : physicalRight;
                        boolean z2 = swipeRight ? this.swipeRightEnabled : this.swipeLeftEnabled;
                        if (this.dragging && z && z2) {
                            this.lastSwipeEndTime = SystemClock.uptimeMillis();
                            final int i = physicalRight ? 1 : -1;
                            this.target.animate().withLayer().translationX(this.target.getWidth() * i * 1.5f).alpha(0.0f).setDuration(200L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.1
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    SwipeListener.this.target.setTranslationX(0.0f);
                                    SwipeListener.this.target.setAlpha(1.0f);
                                    SwipeListener.this.callback.onDragProgress(0.0f);
                                    SwipeListener.this.callback.onDragEnd();
                                    if (swipeRight) {
                                        SwipeListener.this.callback.onSwipeRight();
                                    } else {
                                        SwipeListener.this.callback.onSwipeLeft();
                                    }
                                }
                            }).start();
                        } else if (this.dragging) {
                            this.target.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(NotifBubble.ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.2
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
                    case 2:
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
                            this.target.setAlpha(1.0f - Math.min(0.6f, (Math.abs(x2) / Math.max(1, this.target.getWidth())) * 0.9f));
                            this.callback.onDragProgress(x2);
                        }
                        return true;
                    case 3:
                        if (this.velocityTracker != null) {
                            this.velocityTracker.recycle();
                            this.velocityTracker = null;
                        }
                        if (this.dragging) {
                            this.target.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(NotifBubble.ENTER_ANIM_MS).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.NotifBubble.SwipeListener.3
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
            } catch (Throwable th) {
                NotifBubble.this.host.log("SwipeListener failed", th);
                return false;
            }
        }
    }

    // fixed56: uniform expand/shrink in place. The old entrance slid
    // the view in from an offset (translationY 10dp) — after a full
    // expand, the shrink looked like the capsule bounced in from far
    // away. Now the view scales uniformly (scaleX == scaleY) around
    // its bottom-right corner, where the capsule sits, with no
    // translation at all. Pivot is set after layout (post), so the
    // corner is exact; a view detached before the post still ends up
    // fully visible instead of stuck at alpha 0 / scale 0.85.
    // fixed58: the post can run before layout, when width/height are
    // still 0 — the pivot would land at (0,0) and the scale would swing
    // the capsule in from the top-left corner ("bouncing in from far
    // away"). Wait for a real size (a few frames at most), then snap
    // fully visible if it never arrives.
    private void animateIn(final View view) {
        try {
            view.animate().cancel();
            view.setAlpha(0.0f);
            view.setTranslationX(0.0f);
            view.setTranslationY(0.0f);
            view.setScaleX(0.85f);
            view.setScaleY(0.85f);
            final int[] pivotTries = {0};
            view.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (view.getParent() == null) {
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                            return;
                        }
                        if ((view.getWidth() == 0 || view.getHeight() == 0) && pivotTries[0] < 5) {
                            pivotTries[0]++;
                            view.post(this);
                            return;
                        }
                        if (view.getWidth() > 0 && view.getHeight() > 0) {
                            view.setPivotX((float) view.getWidth());
                            view.setPivotY((float) view.getHeight());
                            view.animate().withLayer().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(ENTER_ANIM_MS).start();
                        } else {
                            // fixed62: still unmeasured after 5 frames —
                            // the default pivot is the top-left corner, so
                            // animating the scale now would grow the
                            // capsule from the top-left. Just snap it to
                            // full size.
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setSwipeMode(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setSwipeMode(z);
            }
        } catch (Throwable th) {
            this.host.log("setSwipeMode failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isTapOpen() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).isTapOpen();
            }
            return true;
        } catch (Throwable th) {
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isCardExpand() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).isCardExpand();
            }
            return true;
        } catch (Throwable th) {
            return true;
        }
    }

    /* fixed102: user-adjustable capsule / card size (fallback = old constants). */
    private int capsuleWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).capsuleWidthDp();
            }
        } catch (Throwable ignored) {
        }
        return MINI_WIDTH_DP;
    }

    private int capsuleHeightDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).capsuleHeightDp();
            }
        } catch (Throwable ignored) {
        }
        return 52;
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
        Drawable appIcon = appIconDrawable(notifItem.pkg);
        if (appIcon != null || notifItem.icon != null) {
            ImageView imageView = new ImageView(this.ctx);
            setNotifIcon(imageView, notifItem, z, i2, i);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(this.host.dp(i), this.host.dp(i)));
            return imageView;
        }
        TextView textView = new TextView(this.ctx);
        String strAppLabel = appLabel(notifItem.pkg);
        textView.setText(strAppLabel.isEmpty() ? "?" : strAppLabel.substring(0, 1));
        textView.setTextSize(2, 16.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-1);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-14009531);
        if (z) {
            gradientDrawable.setShape(1);
        } else {
            gradientDrawable.setCornerRadius(this.host.dp(i2));
        }
        textView.setBackground(gradientDrawable);
        textView.setLayoutParams(new LinearLayout.LayoutParams(this.host.dp(i), this.host.dp(i)));
        return textView;
    }

    // fixed15: load the app icon Drawable directly here, in SystemUI, and
    // set it on the ImageView — no Bitmap conversion, no Bundle parceling.
    // Every Bitmap-rendering attempt in NotificationService kept producing
    // a white/blank bitmap on this device, so the icon never showed. The
    // Drawable is what the launcher itself displays, and setting it
    // directly is the one path that cannot go white. Falls back to the
    // broadcast bitmap, then to the letter placeholder.
    private Drawable appIconDrawable(String pkg) {
        if (pkg == null) {
            return null;
        }
        try {
            return this.ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable th) {
            return null;
        }
    }

    private void setNotifIcon(ImageView imageView, NotifItem notifItem, boolean circular, int i, int i2) {
        Drawable appIcon = appIconDrawable(notifItem.pkg);
        if (appIcon != null) {
            imageView.setImageDrawable(appIcon);
            if (circular) {
                clipToCircle(imageView);
            }
            return;
        }
        Bitmap bitmapIconBitmap = notifItem.icon != null ? iconBitmap(notifItem, circular, i, i2) : null;
        if (bitmapIconBitmap != null) {
            imageView.setImageBitmap(bitmapIconBitmap);
            return;
        }
        imageView.setImageBitmap(placeholderBitmap(appLabel(notifItem.pkg)));
    }

    private void clipToCircle(final ImageView imageView) {
        try {
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    int size = Math.min(view.getWidth(), view.getHeight());
                    if (size <= 0) {
                        size = view.getLayoutParams() != null ? 0 : 0;
                    }
                    int w = view.getWidth() > 0 ? view.getWidth() : 96;
                    int h = view.getHeight() > 0 ? view.getHeight() : 96;
                    int s = Math.min(w, h);
                    outline.setOval(0, 0, s, s);
                }
            });
        } catch (Throwable ignored) {
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
        Paint paint = new Paint(1);
        paint.setColor(i3);
        Path path = new Path();
        if (z) {
            float f = iMin / 2.0f;
            path.addCircle(f, f, f, Path.Direction.CW);
        } else {
            float f2 = i2 > 0 ? (iMin * i) / i2 : iMin * 0.3f;
            float f3 = iMin;
            path.addRoundRect(new RectF(0.0f, 0.0f, f3, f3), f2, f2, Path.Direction.CW);
        }
        canvas.clipPath(path);
        float f4 = iMin;
        canvas.drawRect(0.0f, 0.0f, f4, f4, paint);
        float f5 = 0.66f * f4;
        float f6 = (f4 - f5) / 2.0f;
        float f7 = f5 + f6;
        canvas.drawBitmap(bitmap, new Rect((width - iMin) / 2, (height - iMin) / 2, (width + iMin) / 2, (height + iMin) / 2), new RectF(f6, f6, f7, f7), (Paint) null);
        return bitmapCreateBitmap;
    }

    private Bitmap placeholderBitmap(String str) {
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint(1);
        paint.setColor(-14009531);
        canvas.drawCircle(48.0f, 48.0f, 48.0f, paint);
        String strSubstring = (str == null || str.isEmpty()) ? "?" : str.substring(0, 1);
        Paint paint2 = new Paint(1);
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
        float f = i2 > 0 ? (width * i) / i2 : width * 0.3f;
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
