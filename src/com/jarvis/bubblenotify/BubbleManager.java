package com.jarvis.bubblenotify;

import android.app.ActivityOptions;
import android.app.Application;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class BubbleManager implements BubbleHost {
    private static final int DOCK_GAP_DP = 10;
    private static final BubbleManager INSTANCE = new BubbleManager();
    public static final String TAG_EDGE = "bn_edge";
    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final int WINDOWING_MODE_FULLSCREEN = 1;
    private Application app;
    private CallBubble callBubble;
    private float density;
    private LinearLayout dock;
    // fixed74: unified focus — the one view currently allowed to show
    // (a fresh capsule pop or an expanded card); every other dock child
    // is GONE until the focus ends. And no-sinking index memory: each
    // bubble's last dock slot, so a view removed and re-added (refresh,
    // card shrink, dock expand) returns to its old slot instead of
    // sinking to the end. Keyed by owning bubble — views are rebuilt on
    // refresh, so the view object itself cannot be the key.
    private View focusView;
    private final Map<BubbleFeature, Integer> lastDockIndex = new HashMap<BubbleFeature, Integer>();
    // fixed49: shared docked pile. All docked icons (player disc, call
    // avatar, nav icon, notification icons) overlap in ONE pile at the
    // edge, above the capsules. The pile container is the first child
    // of the dock whenever any icon is docked; each bubble contributes
    // just its icon. fixed70: the "‹" handle is gone (user 2026-11-17
    // 21:07 "贴边的箭头去掉吧不要了，只保留图标") — icons only.
    private LinearLayout dockedPile;
    private FrameLayout dockedPileIcons;
    private final ArrayList<View> dockedPileIconList = new ArrayList<>();
    // fixed67: pile glide state. The window is bottom-anchored, so when
    // a card/capsule below the pile shrinks away the window shrinks and
    // the already-docked pile would teleport down/up with the resize.
    // Instead its screen Y is recorded before the resize and the pile
    // container glides vertically to its new spot ("贴边往下或者往上靠"),
    // no scale. pileGlideFromY is the pre-resize screen Y (MIN_VALUE =
    // none armed); pileGlideArmedAt guards against stale arms;
    // pileGlideSeq supersedes glides scheduled by an earlier resize in
    // the same transition (updateWindowSize runs several times per shrink).
    private int pileGlideFromY = Integer.MIN_VALUE;
    private long pileGlideArmedAt = 0;
    private int pileGlideSeq = 0;
    private MusicBubble musicBubble;
    private NotifBubble notifBubble;
    private WindowManager.LayoutParams params;
    private FrameLayout root;
    private int screenHeightPx;
    private int screenWidthPx;
    private WindowManager wm;
    private XposedInterface xposed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean initialized = false;
    private boolean windowAdded = false;
    // fixed61: fixed60 moved the pile into its own window and ordinary
    // bubbles stopped showing (user: installed fixed60, rebooted, test
    // notification shows nothing at all, music capsule still there).
    // The pile window is reverted — the pile is the dock's first child
    // again, exactly as in fixed59, which the user confirmed working.
    // fixed60's shrink animation is kept; only the pile window goes.
    // fixed58: adaptive frosted glass. The bubbles sit bottom-right over
    // the wallpaper, so the glass theme is decided by sampling the
    // wallpaper's bottom-right corner: bright wallpaper -> dark glass
    // with light text, dark wallpaper -> light glass with dark text.
    // Sampled lazily (cached 60s) and re-sampled on wallpaper change,
    // screen-on and rotation. A live wallpaper (no bitmap) keeps the
    // previous theme; the default is the old light glass.
    private boolean glassDark = false;
    private long glassSampledAt = 0;
    private static final long GLASS_CACHE_MS = 60000;
    private static final double GLASS_DARK_LUMINANCE = 0.62;
    // fixed50: cross-process window lock. A second SystemUI process (or a
    // second module load) would create a second BubbleManager + window, so
    // every notification pops twice and both copies stay clickable (user:
    // "只要弹出2次后，后续所有的通知都是双份通知" / "而且都能点击" /
    // "消除后所有的都会消除" — two live windows sharing one broadcast).
    // The process-name guard (fixed32) was not enough. Hold an exclusive
    // file lock for the lifetime of this BubbleManager; a second process
    // that cannot acquire it stays inert (no receiver, no window).
    private java.nio.channels.FileChannel windowLockChannel;
    private java.nio.channels.FileLock windowLock;
    private boolean swiping = false;
    // fixed63: the transparent outside-tap layer is deleted. The host
    // only tracks whether a notification card is open, so the dock
    // margin can pin the card's bottom edge to the user's offset.
    private boolean cardOpen = false;
    private boolean screenOn = true;
    private boolean replyFocusable = false;
    private boolean enabled = true;
    private boolean musicEnabled = true;
    private boolean callEnabled = true;
    // fixed38: user-adjustable expanded-card bottom offset (dp). Read
    // from remote prefs and from the ACTION_SETTINGS intent; the expanded
    // card's dock margin uses it, so the user can move the card up/down
    // from the app and see it move live.
    private int cardBottomOffsetDp = Settings.DEFAULT_CARD_BOTTOM_OFFSET_DP;
    // fixed80: movable dock (user 2026-11-17 22:43: long-press the
    // docked pile to drag it; release on the left/right half to snap
    // to that edge; capsules and cards follow, same window). The dock
    // sits dockEdgeDistDp from the anchored edge (top when dockUpper,
    // bottom otherwise); the default (right edge, bottom, 14dp) is the
    // old fixed position. New notifications stack toward the screen
    // centre: lower half -> newest capsule on top ("向上刷新"), upper
    // half -> newest at the bottom ("向下刷新").
    private boolean dockLeft = false;
    private boolean dockUpper = false;
    private int dockEdgeDistDp = 14;
    private boolean dockDragging = false;
    private float dragDownX = 0;
    private float dragDownY = 0;
    private float dragLastRawY = 0;
    private float dragStartRawY = 0;
    // fixed115: throttle for the touchable-region diagnostic log.
    private long lastRegionLogMs;
    private int dragStartParamsY = 0;
    private boolean dragLongPressFired = false;
    private Runnable dragLongPressRunnable;
    private View.OnTouchListener pileDragListener;
    private boolean tapOpen = true;
    private boolean cardExpand = true;
    // fixed102: user-adjustable size / opacity / corner radius.
    private int capsuleWidth = Settings.DEFAULT_CAPSULE_WIDTH_DP;
    private int capsuleHeight = Settings.DEFAULT_CAPSULE_HEIGHT_DP;
    private int cardWidth = Settings.DEFAULT_CARD_WIDTH_DP;
    private int cardHeight = Settings.DEFAULT_CARD_HEIGHT_DP;
    private int bubbleOpacity = Settings.DEFAULT_BUBBLE_OPACITY;
    private int capsuleCorner = Settings.DEFAULT_CAPSULE_CORNER_DP;
    private int cardCorner = Settings.DEFAULT_CARD_CORNER_DP;
    private boolean freeform = true;
    private final List<BubbleFeature> features = new ArrayList();
    private final BroadcastReceiver receiver = new BroadcastReceiver() { // from class: com.jarvis.bubblenotify.BubbleManager.1
        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        /* JADX WARN: Code duplicated, block: B:28:0x0056  */
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            try {
                String action = intent.getAction();
                // fixed58: wallpaper changed -> re-sample the glass theme.
                if ("android.intent.action.WALLPAPER_CHANGED".equals(action)) {
                    BubbleManager.this.refreshGlassTheme(true);
                    return;
                }
                if ("android.intent.action.SCREEN_ON".equals(action)) {
                    BubbleManager.this.onScreenPowerChanged(true);
                    BubbleManager.this.refreshGlassTheme(true);
                }
                if ("android.intent.action.SCREEN_OFF".equals(action)) {
                    BubbleManager.this.onScreenPowerChanged(false);
                    return;
                }
                if ("android.intent.action.CONFIGURATION_CHANGED".equals(action)) {
                    BubbleManager.this.onConfigurationChangedInternal();
                    BubbleManager.this.refreshGlassTheme(true);
                    return;
                }
                boolean zEquals = Settings.ACTION_SETTINGS.equals(action);
                BubbleManager bubbleManager = BubbleManager.this;
                if (zEquals) {
                    bubbleManager.applySettingsFromIntent(intent);
                    return;
                }
                if (!bubbleManager.enabled) {
                    return;
                }
                switch (action) {
                    case "com.jarvis.bubblenotify.NOTIFY_LIST":
                        if (BubbleManager.this.notifBubble != null) {
                            ArrayList<Bundle> parcelableArrayListExtra = intent.getParcelableArrayListExtra(Settings.EXTRA_NOTIFY_LIST);
                            if (parcelableArrayListExtra == null) {
                                parcelableArrayListExtra = new ArrayList<>();
                            }
                            // fixed33: log every received list — a double
                            // receive of the same list shows the receiver
                            // (or the broadcast) fired twice.
                            FileLog.i("Received notify list: " + parcelableArrayListExtra.size() + " items");
                            BubbleManager.this.notifBubble.onNotifyList(parcelableArrayListExtra);
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.NOTIFY":
                        if (BubbleManager.this.notifBubble != null) {
                            BubbleManager.this.notifBubble.onTestNotify(intent.getStringExtra(Settings.EXTRA_TITLE), intent.getStringExtra(Settings.EXTRA_TEXT), intent.getStringExtra(Settings.EXTRA_PACKAGE), (Bitmap) intent.getParcelableExtra(Settings.EXTRA_ICON), (PendingIntent) intent.getParcelableExtra(Settings.EXTRA_INTENT));
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.HIDE":
                        if (BubbleManager.this.notifBubble != null) {
                            BubbleManager.this.notifBubble.onClearAll();
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.CODE":
                        if (BubbleManager.this.notifBubble != null) {
                            BubbleManager.this.notifBubble.onAiCode(intent.getStringExtra(Settings.EXTRA_KEY), intent.getStringExtra(Settings.EXTRA_CODE_KIND), intent.getStringExtra(Settings.EXTRA_CODE));
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.TEST_CODE":
                        if (BubbleManager.this.notifBubble != null) {
                            BubbleManager.this.notifBubble.onTestCode(intent.getStringExtra(Settings.EXTRA_TITLE), intent.getStringExtra(Settings.EXTRA_TEXT), intent.getStringExtra(Settings.EXTRA_CODE_KIND), intent.getStringExtra(Settings.EXTRA_CODE));
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.MUSIC":
                        if (BubbleManager.this.musicBubble != null) {
                            BubbleManager.this.musicBubble.onMusicBroadcast(intent);
                            break;
                        }
                        break;
                    case "com.jarvis.bubblenotify.CALL":
                        if (BubbleManager.this.callBubble != null) {
                            BubbleManager.this.callBubble.onCallBroadcast(intent);
                            break;
                        }
                        break;
                    default:
                        break;
                }
            } catch (Throwable th) {
                BubbleManager.this.log("Error handling broadcast " + intent.getAction(), th);
            }
        }
    };

    public static BubbleManager getInstance() {
        return INSTANCE;
    }

    public synchronized void init(XposedInterface xposedInterface, Application application) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.xposed = xposedInterface;
        this.app = application;
        // fixed50: if another process already holds the window lock, this
        // BubbleManager stays inert — no receiver, no features, no window.
        // Otherwise this process would show a second copy of every bubble.
        if (!acquireWindowLock()) {
            log("BubbleManager inert: another process holds the window lock.");
            FileLog.i("BubbleManager inert: another process holds the window lock");
            return;
        }
        loadSettingsFromRemotePrefs();
        // fixed94: second half of Flyme Island's call interception —
        // suppress the incoming-call heads-up banner in SystemUI.
        new CallBannerSuppress(xposedInterface, application).install();
        // fixed98: Flyme Island-style listening — NotifCollection hook
        // in SystemUI as a fallback for notifications the listener
        // service misses.
        new NotifCollectionHook(xposedInterface, application, this).install();
        try {
            PowerManager powerManager = (PowerManager) application.getSystemService("power");
            this.screenOn = powerManager == null || powerManager.isInteractive();
        } catch (Throwable th) {
            this.screenOn = true;
        }
        registerReceiver();
        this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.2
            @Override // java.lang.Runnable
            public void run() {
                try {
                    BubbleManager.this.createFeatures();
                    BubbleManager.this.setupWindow();
                } catch (Throwable th2) {
                    BubbleManager.this.log("BubbleManager init failed", th2);
                }
            }
        });
        log("BubbleManager init, enabled=" + this.enabled + " music=" + this.musicEnabled + " call=" + this.callEnabled + " tapOpen=" + this.tapOpen + " cardExpand=" + this.cardExpand + " capsule=" + this.capsuleWidth + "x" + this.capsuleHeight + " card=" + this.cardWidth + "x" + this.cardHeight + " opacity=" + this.bubbleOpacity + " corner=" + this.capsuleCorner + "/" + this.cardCorner);
    }

    // fixed98: fallback-hook helpers.
    public boolean hasNotifKey(String key) {
        return this.notifBubble != null && this.notifBubble.hasKey(key);
    }

    public NotifBubble getNotifBubble() {
        return this.notifBubble;
    }

    // fixed50: exclusive cross-process lock for the bubble window. The
    // lock is held for the lifetime of this process (the OS releases it
    // when the process dies), so at most one BubbleManager window exists
    // across all SystemUI processes. Returns false only when another
    // process already holds the lock; any I/O failure returns true (do
    // not block the window just because locking is unavailable).
    // fixed51: /data/local/tmp is not writable from SystemUI on this
    // device (the log file never appeared there either), so the lock
    // file could not be created and the lock never engaged — fixed50
    // did not actually block the second window. Use SystemUI's own
    // files dir first (guaranteed writable, same UID for all SystemUI
    // processes), /data/local/tmp only as a fallback.
    private boolean acquireWindowLock() {
        String[] paths = new String[0];
        try {
            java.io.File filesDir = this.app.getFilesDir();
            if (filesDir != null) {
                paths = new String[]{new java.io.File(filesDir, "BubbleNotify_window.lock").getAbsolutePath(), "/data/local/tmp/BubbleNotify_window.lock"};
            } else {
                paths = new String[]{"/data/local/tmp/BubbleNotify_window.lock"};
            }
        } catch (Throwable ignored) {
            paths = new String[]{"/data/local/tmp/BubbleNotify_window.lock"};
        }
        for (String path : paths) {
            try {
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path, "rw");
                java.nio.channels.FileChannel ch = raf.getChannel();
                java.nio.channels.FileLock lock = ch.tryLock();
                if (lock == null) {
                    try {
                        ch.close();
                    } catch (Throwable ignored) {
                    }
                    try {
                        raf.close();
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
                this.windowLockChannel = ch;
                this.windowLock = lock;
                return true;
            } catch (Throwable ignored) {
                // try the next path
            }
        }
        log("acquireWindowLock failed on all paths, allowing window");
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void createFeatures() {
        this.notifBubble = new NotifBubble(this);
        this.musicBubble = new MusicBubble(this);
        this.callBubble = new CallBubble(this);
        this.features.clear();
        this.features.add(this.notifBubble);
        this.features.add(this.musicBubble);
        this.features.add(this.callBubble);
        if (!this.enabled) {
            try {
                this.notifBubble.onFeatureDisabled();
            } catch (Throwable th) {
            }
            callFeatureDisabled(this.musicBubble);
            callFeatureDisabled(this.callBubble);
            return;
        }
        if (!this.musicEnabled) {
            callFeatureDisabled(this.musicBubble);
        }
        if (!this.callEnabled) {
            callFeatureDisabled(this.callBubble);
        }
    }

    private void loadSettingsFromRemotePrefs() {
        try {
            SharedPreferences remotePreferences = this.xposed.getRemotePreferences(Settings.PREFS_NAME);
            this.enabled = remotePreferences.getBoolean(Settings.KEY_ENABLED, true);
            this.musicEnabled = remotePreferences.getBoolean(Settings.KEY_MUSIC_ENABLED, true);
            this.callEnabled = remotePreferences.getBoolean(Settings.KEY_CALL_ENABLED, true);
            this.tapOpen = remotePreferences.getBoolean(Settings.KEY_TAP_OPEN, true);
            this.cardExpand = remotePreferences.getBoolean(Settings.KEY_CARD_EXPAND, true);
            this.freeform = remotePreferences.getBoolean(Settings.KEY_FREEFORM, true);
            this.cardBottomOffsetDp = remotePreferences.getInt(Settings.KEY_CARD_BOTTOM_OFFSET_DP, Settings.DEFAULT_CARD_BOTTOM_OFFSET_DP);
            this.capsuleWidth = remotePreferences.getInt(Settings.KEY_CAPSULE_WIDTH_DP, Settings.DEFAULT_CAPSULE_WIDTH_DP);
            this.capsuleHeight = remotePreferences.getInt(Settings.KEY_CAPSULE_HEIGHT_DP, Settings.DEFAULT_CAPSULE_HEIGHT_DP);
            this.cardWidth = remotePreferences.getInt(Settings.KEY_CARD_WIDTH_DP, Settings.DEFAULT_CARD_WIDTH_DP);
            this.cardHeight = remotePreferences.getInt(Settings.KEY_CARD_HEIGHT_DP, Settings.DEFAULT_CARD_HEIGHT_DP);
            this.bubbleOpacity = remotePreferences.getInt(Settings.KEY_BUBBLE_OPACITY, Settings.DEFAULT_BUBBLE_OPACITY);
            this.capsuleCorner = remotePreferences.getInt(Settings.KEY_CAPSULE_CORNER_DP, Settings.DEFAULT_CAPSULE_CORNER_DP);
            this.cardCorner = remotePreferences.getInt(Settings.KEY_CARD_CORNER_DP, Settings.DEFAULT_CARD_CORNER_DP);
            log("Settings loaded from remote prefs.");
        } catch (Throwable th) {
            log("Remote prefs unavailable, using defaults: " + String.valueOf(th));
        }
        // fixed46: the offset must survive an app reinstall + reboot. The
        // remote prefs are wiped on reinstall, but the durable file in
        // /data/local/tmp is not. If it holds a value, it wins — it is the
        // last position the user actually set. This also covers a failed
        // remote-prefs read on boot (which otherwise silently falls back
        // to the 32dp default and forces the user to re-adjust).
        try {
            int saved = readOffsetFile();
            if (saved >= 0) {
                this.cardBottomOffsetDp = saved;
            }
        } catch (Throwable ignored) {
        }
    }

    // fixed46: read the durable card-offset file (see
    // Settings.OFFSET_FILE_PATH). Returns the saved offset in dp, or -1
    // when there is no file or it does not hold a valid value.
    static int readOffsetFile() {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(Settings.OFFSET_FILE_PATH));
            String line = r.readLine();
            r.close();
            if (line != null) {
                int v = Integer.parseInt(line.trim());
                if (v >= 0 && v <= 200) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applySettingsFromIntent(Intent intent) {
        if (intent.hasExtra(Settings.KEY_ENABLED)) {
            this.enabled = intent.getBooleanExtra(Settings.KEY_ENABLED, this.enabled);
            this.musicEnabled = intent.getBooleanExtra(Settings.KEY_MUSIC_ENABLED, this.musicEnabled);
            this.callEnabled = intent.getBooleanExtra(Settings.KEY_CALL_ENABLED, this.callEnabled);
            this.tapOpen = intent.getBooleanExtra(Settings.KEY_TAP_OPEN, this.tapOpen);
            this.cardExpand = intent.getBooleanExtra(Settings.KEY_CARD_EXPAND, this.cardExpand);
            this.freeform = intent.getBooleanExtra(Settings.KEY_FREEFORM, this.freeform);
            this.cardBottomOffsetDp = intent.getIntExtra(Settings.KEY_CARD_BOTTOM_OFFSET_DP, this.cardBottomOffsetDp);
            this.capsuleWidth = intent.getIntExtra(Settings.KEY_CAPSULE_WIDTH_DP, this.capsuleWidth);
            this.capsuleHeight = intent.getIntExtra(Settings.KEY_CAPSULE_HEIGHT_DP, this.capsuleHeight);
            this.cardWidth = intent.getIntExtra(Settings.KEY_CARD_WIDTH_DP, this.cardWidth);
            this.cardHeight = intent.getIntExtra(Settings.KEY_CARD_HEIGHT_DP, this.cardHeight);
            this.bubbleOpacity = intent.getIntExtra(Settings.KEY_BUBBLE_OPACITY, this.bubbleOpacity);
            this.capsuleCorner = intent.getIntExtra(Settings.KEY_CAPSULE_CORNER_DP, this.capsuleCorner);
            this.cardCorner = intent.getIntExtra(Settings.KEY_CARD_CORNER_DP, this.cardCorner);
        } else {
            loadSettingsFromRemotePrefs();
        }
        if (!this.enabled) {
            disableAllFeatures();
        } else {
            if (!this.musicEnabled) {
                callFeatureDisabled(this.musicBubble);
            }
            if (!this.callEnabled) {
                callFeatureDisabled(this.callBubble);
            }
        }
        // fixed38: live-apply the card offset. If an expanded card is
        // open right now, re-lift the dock to the new offset immediately
        // so the card moves as the user drags the slider in the app —
        // no reopen needed.
        // fixed42: use the shared pin so the bottom edge is always the
        // set offset, whatever the card height.
        applyCardBottomOffset();
        updateWindowSize();
        log("Settings applied: enabled=" + this.enabled + " music=" + this.musicEnabled + " call=" + this.callEnabled + " tapOpen=" + this.tapOpen + " cardExpand=" + this.cardExpand + " freeform=" + this.freeform + " capsule=" + this.capsuleWidth + "x" + this.capsuleHeight + " card=" + this.cardWidth + "x" + this.cardHeight + " opacity=" + this.bubbleOpacity + " corner=" + this.capsuleCorner + "/" + this.cardCorner);
    }

    private void disableAllFeatures() {
        if (this.notifBubble != null) {
            try {
                this.notifBubble.onFeatureDisabled();
            } catch (Throwable th) {
                log("NotifBubble.onFeatureDisabled failed", th);
            }
        }
        callFeatureDisabled(this.musicBubble);
        callFeatureDisabled(this.callBubble);
    }

    private void callFeatureDisabled(BubbleFeature bubbleFeature) {
        if (bubbleFeature == null) {
            return;
        }
        try {
            bubbleFeature.getClass().getMethod("onFeatureDisabled", new Class[0]).invoke(bubbleFeature, new Object[0]);
        } catch (NoSuchMethodException e) {
            log("onFeatureDisabled not yet implemented in " + bubbleFeature.getClass().getSimpleName());
        } catch (Throwable th) {
            log("onFeatureDisabled failed for " + bubbleFeature.getClass().getSimpleName(), th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setupWindow() {
        if (this.windowAdded) {
            return;
        }
        try {
            this.density = this.app.getResources().getDisplayMetrics().density;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            this.wm = (WindowManager) this.app.getSystemService("window");
            this.wm.getDefaultDisplay().getMetrics(displayMetrics);
            this.screenWidthPx = displayMetrics.widthPixels;
            this.screenHeightPx = displayMetrics.heightPixels;
            this.root = new FrameLayout(this.app);
            this.root.setClickable(false);
            this.root.setFocusable(false);
            this.root.setClipChildren(false);
            this.dock = new LinearLayout(this.app);
            this.dock.setOrientation(1);
            // fixed80: restore the dragged dock position (right edge,
            // bottom, 14dp when never moved).
            loadDockPosition();
            int hGrav = this.dockLeft ? 8388611 : 8388613;
            int vGrav = this.dockUpper ? 48 : 80;
            this.dock.setGravity(hGrav);
            this.dock.setClipChildren(false);
            this.dock.setLayoutParams(new FrameLayout.LayoutParams(-2, -2, vGrav | hGrav));
            this.root.addView(this.dock);
            this.params = new WindowManager.LayoutParams(1, 1, 2038, 56, -3);
            this.params.gravity = vGrav | hGrav;
            this.params.x = 0;
            this.params.y = dp(this.dockEdgeDistDp);
            this.params.layoutInDisplayCutoutMode = 1;
            this.params.setTitle("BubbleNotify");
            this.wm.addView(this.root, this.params);
            this.windowAdded = true;
            installTouchableRegionListener();
            log("Bubble window added. screen=" + this.screenWidthPx + "x" + this.screenHeightPx);
        } catch (Throwable th) {
            log("Failed to add bubble window", th);
        }
    }

    // fixed59: the window rect covers the whole dock — pile at the top,
    // capsules at the bottom — so the blank space between them (and
    // beside a narrow capsule) is inside the window. With
    // FLAG_NOT_TOUCH_MODAL, touches inside the window rect are delivered
    // to this window and dropped when no child consumes them, so the app
    // underneath that gap can never be tapped. Shrink the window's
    // touchable region to the union of the dock children's rects, so
    // touches on empty window area fall through to the app below.
    // Done through ViewTreeObserver.OnComputeInternalInsetsListener —
    // the same mechanism overlay apps use to punch holes in their
    // windows. It is a hidden API (not in android-34.jar), so it is
    // wired up with reflection; if the wiring fails, behaviour is
    // unchanged (whole window rect stays touchable) and the failure is
    // logged. fixed63: no fullscreen layer any more, so the region is
    // always the dock children's union. The region is recomputed on
    // every traversal, so adding/removing a bubble re-punches the
    // holes automatically.
    private void installTouchableRegionListener() {
        try {
            final Class<?> listenerClass = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            final Class<?> infoClass = Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            final java.lang.reflect.Field regionField = infoClass.getField("touchableRegion");
            final java.lang.reflect.Method setInsetsMethod = infoClass.getMethod("setTouchableInsets", int.class);
            final int insetsRegion = infoClass.getField("TOUCHABLE_INSETS_REGION").getInt(null);
            Object listener = Proxy.newProxyInstance(BubbleManager.class.getClassLoader(), new Class<?>[]{listenerClass}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("onComputeInternalInsets".equals(method.getName()) && args != null && args.length == 1) {
                        try {
                            Object info = args[0];
                            // fixed63: no fullscreen layer any more, so
                            // the window is always dock-sized and the
                            // touchable region is always the dock
                            // children's union (the gaps fall through).
                            Region region = new Region();
                            if (BubbleManager.this.dock != null) {
                                // fixed114: build the region from the
                                // actual bubbles, not the containers. The
                                // pile container and the mini container
                                // are bigger than the visible bubbles
                                // (icon spread, capsule margins), so
                                // their rects swallowed taps in the gap
                                // between pile and capsule. Each icon and
                                // each capsule contributes its own rect.
                                int[] loc = new int[2];
                                for (int i = 0; i < BubbleManager.this.dock.getChildCount(); i++) {
                                    View child = BubbleManager.this.dock.getChildAt(i);
                                    if (child == null || child.getVisibility() != View.VISIBLE) {
                                        continue;
                                    }
                                    if (child == BubbleManager.this.dockedPile && BubbleManager.this.dockedPileIcons != null) {
                                        ViewGroup icons = BubbleManager.this.dockedPileIcons;
                                        for (int j = 0; j < icons.getChildCount(); j++) {
                                            View icon = icons.getChildAt(j);
                                            if (icon == null || icon.getVisibility() != View.VISIBLE) {
                                                continue;
                                            }
                                            icon.getLocationInWindow(loc);
                                            region.union(new Rect(loc[0], loc[1], loc[0] + icon.getWidth(), loc[1] + icon.getHeight()));
                                        }
                                    } else if (child instanceof LinearLayout) {
                                        // mini container: each capsule.
                                        ViewGroup group = (ViewGroup) child;
                                        for (int j = 0; j < group.getChildCount(); j++) {
                                            View cap = group.getChildAt(j);
                                            if (cap == null || cap.getVisibility() != View.VISIBLE) {
                                                continue;
                                            }
                                            cap.getLocationInWindow(loc);
                                            region.union(new Rect(loc[0], loc[1], loc[0] + cap.getWidth(), loc[1] + cap.getHeight()));
                                        }
                                    } else {
                                        // card container and anything else:
                                        // the view itself is the bubble.
                                        child.getLocationInWindow(loc);
                                        region.union(new Rect(loc[0], loc[1], loc[0] + child.getWidth(), loc[1] + child.getHeight()));
                                    }
                                }
                            }
                            Region target = (Region) regionField.get(info);
                            target.set(region);
                            setInsetsMethod.invoke(info, insetsRegion);
                            // fixed115: diagnostic — is the listener
                            // actually running, and what region does it
                            // set? Throttled to once per second.
                            try {
                                long now = android.os.SystemClock.uptimeMillis();
                                if (now - BubbleManager.this.lastRegionLogMs > 1000) {
                                    BubbleManager.this.lastRegionLogMs = now;
                                    android.graphics.Rect bounds = new android.graphics.Rect();
                                    boolean hasBounds = region.getBounds(bounds);
                                    log("Touchable region: " + (hasBounds ? bounds.toShortString() : "empty") + " children=" + (BubbleManager.this.dock != null ? BubbleManager.this.dock.getChildCount() : -1));
                                }
                            } catch (Throwable ignored2) {
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    return null;
                }
            });
            // fixed115: getMethod can be blocked by hidden-API
            // enforcement on Flyme — fall back to getDeclaredMethod +
            // setAccessible, and log which path worked.
            java.lang.reflect.Method addMethod = null;
            try {
                addMethod = this.root.getViewTreeObserver().getClass().getMethod("addOnComputeInternalInsetsListener", listenerClass);
            } catch (Throwable ignored) {
            }
            if (addMethod == null) {
                try {
                    addMethod = this.root.getViewTreeObserver().getClass().getDeclaredMethod("addOnComputeInternalInsetsListener", listenerClass);
                    addMethod.setAccessible(true);
                    log("Touchable region listener: using getDeclaredMethod.");
                } catch (Throwable th2) {
                    log("Touchable region listener: no add method", th2);
                }
            }
            if (addMethod != null) {
                addMethod.invoke(this.root.getViewTreeObserver(), listener);
                log("Touchable region listener installed.");
            }
        } catch (Throwable th) {
            log("Touchable region listener failed", th);
        }
    }

    public void setSwipeMode(boolean z) {
        if (!this.windowAdded || this.swiping == z) {
            return;
        }
        this.swiping = z;
        try {
            if (z) {
                this.params.width = this.screenWidthPx > 0 ? this.screenWidthPx : -1;
                this.params.x = 0;
                this.wm.updateViewLayout(this.root, this.params);
                return;
            }
            updateWindowSize();
        } catch (Throwable th) {
            log("Failed to switch swipe window mode", th);
        }
    }

    // fixed34: lift (or restore) the dock from the bottom edge. Used when
    // the window goes fullscreen for outside-tap, so the expanded card's
    // bottom clears the gesture bar and the rounded screen corners.
    private void setDockBottomMargin(int marginPx) {
        try {
            if (this.dock == null) {
                return;
            }
            android.view.ViewGroup.LayoutParams lp = this.dock.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                if (flp.bottomMargin != marginPx) {
                    flp.bottomMargin = marginPx;
                    this.dock.setLayoutParams(flp);
                }
            }
        } catch (Throwable th) {
            log("setDockBottomMargin failed", th);
        }
    }

    // fixed42: pin the expanded card's bottom edge to the user-set offset
    // (the slider value), no matter how tall the card is. The dock is
    // bottom-anchored (gravity BOTTOM|END) in the window, so its bottom
    // margin IS the card's bottom edge — but the margin can be applied
    // to a stale layout when the card content changes height, so the
    // bottom can drift. Re-assert the margin and force a layout. Only
    // applies while a card is open; when the card is closed the dock
    // margin must stay 0.
    // fixed63: no fullscreen layer any more — the window always sits
    // 14dp above the screen bottom, so the margin is offset-14 to keep
    // the card's bottom edge exactly at the user's offset.
    public void applyCardBottomOffset() {
        if (!this.cardOpen || this.dock == null || this.root == null) {
            return;
        }
        try {
            // fixed80: same rule as setCardOpen — the lift is
            // offset-dockEdgeDist when bottom-anchored, 0 when top.
            int margin = this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp));
            setDockBottomMargin(margin);
            this.root.requestLayout();
            this.dock.requestLayout();
        } catch (Throwable th) {
            log("applyCardBottomOffset failed", th);
        }
    }

    // fixed63: the transparent outside-tap layer is deleted (user
    // 2026-11-17 19:46 "不要透明点击层了"). There is no fullscreen
    // window any more: the window always stays dock-sized, so taps
    // outside the dock reach the app behind on their own, and the
    // expanded card auto-shrinks on its 3s idle timer instead of on
    // an outside tap. This method now only tracks whether a card is
    // open, so the dock margin can pin the card's bottom edge to the
    // user's offset (window sits 14dp up, so margin is offset-14)
    // and drop back to 0 when the card closes.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void setCardOpen(boolean open) {
        if (!this.windowAdded || this.root == null) {
            return;
        }
        this.cardOpen = open;
        try {
            if (open) {
                // fixed80: the card's bottom edge sits at max(offset,
                // dockEdgeDist) from the screen bottom when the dock is
                // bottom-anchored; top-anchored, the bottom offset does
                // not apply (margin 0).
                int margin = this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp));
                setDockBottomMargin(margin);
            } else {
                // fixed34: restore the dock's bottom margin.
                setDockBottomMargin(0);
            }
            this.params.y = dp(this.dockEdgeDistDp);
        } catch (Throwable th) {
        }
        updateWindowSize();
    }


    // fixed80: apply the dragged dock position — side, vertical
    // anchor, and the card bottom offset that follows from them.
    // Called on drag end. Capsules and cards ride along because they
    // are all in the same window.
    private void applyDockPosition() {
        if (!this.windowAdded || this.params == null) {
            return;
        }
        // fixed119: a drag can flip dockUpper — the pile may need to
        // move to the other side of the capsules.
        repositionPile();
        try {
            int vGrav = this.dockUpper ? 48 : 80;
            int hGrav = this.dockLeft ? 8388611 : 8388613;
            this.params.gravity = vGrav | hGrav;
            this.params.y = dp(this.dockEdgeDistDp);
            if (this.dock != null) {
                android.view.ViewGroup.LayoutParams lp = this.dock.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    flp.gravity = vGrav | hGrav;
                    this.dock.setLayoutParams(flp);
                }
                this.dock.setGravity(hGrav);
            }
            relayoutDock();
            relayoutDockedPile();
            if (this.cardOpen) {
                int margin = this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp));
                setDockBottomMargin(margin);
            } else {
                setDockBottomMargin(0);
            }
            updateWindowSize();
        } catch (Throwable th) {
            log("applyDockPosition failed", th);
        }
    }

    // fixed80: the dock position is dragged in SystemUI, so it is saved
    // where SystemUI can write — its own files dir first (fixed51: the
    // /data/local/tmp path is not writable from SystemUI on this phone),
    // /data/local/tmp as a fallback. Format: "side,upper,edgeDistDp"
    // (side 0 = right, 1 = left; upper 0 = bottom-anchored, 1 = top).
    private void saveDockPosition() {
        String line = (this.dockLeft ? 1 : 0) + "," + (this.dockUpper ? 1 : 0) + "," + this.dockEdgeDistDp;
        try {
            java.io.File f = new java.io.File(this.app.getFilesDir(), "BubbleNotify_dock.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(line);
            w.close();
            return;
        } catch (Throwable ignored) {
        }
        try {
            java.io.FileWriter w = new java.io.FileWriter("/data/local/tmp/BubbleNotify_dock.txt", false);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private void loadDockPosition() {
        String line = null;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(new java.io.File(this.app.getFilesDir(), "BubbleNotify_dock.txt")));
            line = r.readLine();
            r.close();
        } catch (Throwable ignored) {
        }
        if (line == null) {
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/data/local/tmp/BubbleNotify_dock.txt"));
                line = r.readLine();
                r.close();
            } catch (Throwable ignored) {
            }
        }
        if (line == null) {
            return;
        }
        try {
            String[] parts = line.trim().split(",");
            if (parts.length >= 3) {
                this.dockLeft = Integer.parseInt(parts[0].trim()) == 1;
                this.dockUpper = Integer.parseInt(parts[1].trim()) == 1;
                int d = Integer.parseInt(parts[2].trim());
                if (d >= 0 && d <= 4000) {
                    this.dockEdgeDistDp = d;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // fixed80: long-press drag of the docked pile. The pile is the only
    // handle — capsules have their own swipe gestures. A long-press (no
    // move past the touch slop) enters drag mode; the window follows the
    // finger vertically, delta-based, so the pile stays under the
    // finger; on release the side snaps to the finger's half and the
    // vertical anchor snaps to the half the dock's centre is in.
    private View.OnTouchListener pileDragTouchListener() {
        if (this.pileDragListener != null) {
            return this.pileDragListener;
        }
        this.pileDragListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                try {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            BubbleManager.this.dragDownX = ev.getRawX();
                            BubbleManager.this.dragDownY = ev.getRawY();
                            BubbleManager.this.dragLastRawY = ev.getRawY();
                            BubbleManager.this.dragLongPressFired = false;
                            if (BubbleManager.this.dragLongPressRunnable == null) {
                                BubbleManager.this.dragLongPressRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        BubbleManager.this.startPileDrag();
                                    }
                                };
                            }
                            BubbleManager.this.handler.postDelayed(BubbleManager.this.dragLongPressRunnable, android.view.ViewConfiguration.getLongPressTimeout());
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            BubbleManager.this.dragLastRawY = ev.getRawY();
                            if (BubbleManager.this.dockDragging) {
                                BubbleManager.this.movePileDrag(ev.getRawY());
                                return true;
                            }
                            float dx = ev.getRawX() - BubbleManager.this.dragDownX;
                            float dy = ev.getRawY() - BubbleManager.this.dragDownY;
                            int slop = android.view.ViewConfiguration.get(BubbleManager.this.app).getScaledTouchSlop();
                            if (dx * dx + dy * dy > slop * slop) {
                                BubbleManager.this.handler.removeCallbacks(BubbleManager.this.dragLongPressRunnable);
                            }
                            return false;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            BubbleManager.this.handler.removeCallbacks(BubbleManager.this.dragLongPressRunnable);
                            if (BubbleManager.this.dockDragging) {
                                BubbleManager.this.endPileDrag(ev.getRawX(), ev.getRawY());
                                return true;
                            }
                            return false;
                        default:
                            return false;
                    }
                } catch (Throwable th) {
                    BubbleManager.this.log("pile drag touch failed", th);
                    return false;
                }
            }
        };
        return this.pileDragListener;
    }

    private void startPileDrag() {
        // fixed112: the pile need not be in the dock — a capsule or a
        // card can start the drag too. Only the dock/window must be ready.
        if (this.dock == null || !this.windowAdded || this.params == null) {
            return;
        }
        this.dockDragging = true;
        this.dragLongPressFired = true;
        this.dragStartRawY = this.dragLastRawY;
        this.dragStartParamsY = this.params.y;
        try {
            if (this.dock != null) {
                this.dock.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        } catch (Throwable ignored) {
        }
        log("Pile drag started.");
    }

    // fixed112: dock-drag entry points for capsules and cards (via
    // BubbleHost). The pile drag listener keeps using these too.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void startDockDrag() {
        try {
            startPileDrag();
        } catch (Throwable th) {
            log("startDockDrag failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void moveDockDrag(float rawY) {
        try {
            movePileDrag(rawY);
        } catch (Throwable th) {
            log("moveDockDrag failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void endDockDrag(float rawX, float rawY) {
        try {
            endPileDrag(rawX, rawY);
        } catch (Throwable th) {
            log("endDockDrag failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isDockDragging() {
        return this.dockDragging;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isDockUpper() {
        return this.dockUpper;
    }

    // fixed119: the pile follows the pinned capsule's screen-half rule.
    // Upper half + a pin active: pile below the capsules (centre side).
    // Otherwise: pile at index 0 (top).
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void repositionPile() {
        try {
            if (this.dock == null || this.dockedPile == null || this.dockedPile.getParent() != this.dock) {
                return;
            }
            boolean anyPinned = false;
            try {
                if (this.notifBubble != null && this.notifBubble.anyPinned()) {
                    anyPinned = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (!anyPinned && this.musicBubble != null && this.musicBubble.isPlayerPinned()) {
                    anyPinned = true;
                }
            } catch (Throwable ignored2) {
            }
            int target = (this.dockUpper && anyPinned) ? 1 : 0;
            int current = this.dock.indexOfChild(this.dockedPile);
            if (current == target) {
                return;
            }
            if (target > this.dock.getChildCount()) {
                target = this.dock.getChildCount();
            }
            this.dock.removeView(this.dockedPile);
            LinearLayout.LayoutParams pileLp = new LinearLayout.LayoutParams(-2, -2);
            pileLp.gravity = this.dockLeft ? 8388611 : 8388613;
            pileLp.rightMargin = 0;
            pileLp.leftMargin = 0;
            this.dock.addView(this.dockedPile, target, pileLp);
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("repositionPile failed", th);
        }
    }

    private void movePileDrag(float rawY) {
        if (!this.dockDragging || !this.windowAdded || this.root == null || this.params == null) {
            return;
        }
        try {
            int windowH = this.params.height;
            if (windowH <= 0) {
                windowH = this.dock != null ? this.dock.getHeight() : 0;
            }
            if (windowH <= 0 || this.screenHeightPx <= 0) {
                return;
            }
            float deltaY = rawY - this.dragStartRawY;
            int maxY = Math.max(0, this.screenHeightPx - windowH);
            int newParamsY;
            if (this.dockUpper) {
                newParamsY = (int) (this.dragStartParamsY + deltaY);
            } else {
                newParamsY = (int) (this.dragStartParamsY - deltaY);
            }
            newParamsY = Math.max(0, Math.min(newParamsY, maxY));
            this.params.y = newParamsY;
            this.wm.updateViewLayout(this.root, this.params);
        } catch (Throwable th) {
            log("movePileDrag failed", th);
        }
    }

    private void endPileDrag(float rawX, float rawY) {
        if (!this.dockDragging) {
            return;
        }
        this.dockDragging = false;
        try {
            int windowH = this.params.height;
            if (windowH <= 0) {
                windowH = this.dock != null ? this.dock.getHeight() : 0;
            }
            if (windowH > 0 && this.screenHeightPx > 0 && this.screenWidthPx > 0 && this.density > 0) {
                int windowTop = this.dockUpper ? this.params.y : (this.screenHeightPx - this.params.y - windowH);
                windowTop = Math.max(0, Math.min(windowTop, this.screenHeightPx - windowH));
                float windowCenter = windowTop + windowH / 2.0f;
                this.dockUpper = windowCenter < this.screenHeightPx / 2.0f;
                this.dockLeft = rawX < this.screenWidthPx / 2.0f;
                int edgeDistPx = this.dockUpper ? windowTop : (this.screenHeightPx - windowTop - windowH);
                this.dockEdgeDistDp = Math.max(0, Math.round(edgeDistPx / this.density));
                saveDockPosition();
                log("Pile drag ended: side=" + (this.dockLeft ? "left" : "right") + " upper=" + this.dockUpper + " edgeDistDp=" + this.dockEdgeDistDp);
            }
            applyDockPosition();
        } catch (Throwable th) {
            log("endPileDrag failed", th);
        }
    }

    public boolean isTapOpen() {
        return this.tapOpen;
    }

    public boolean isCardExpand() {
        return this.cardExpand;
    }

    // fixed103: dock side for swipe mirroring.
    public boolean isDockLeft() {
        return this.dockLeft;
    }

    // fixed102: user-adjustable bubble geometry / style.
    public int capsuleWidthDp() {
        return this.capsuleWidth;
    }

    public int capsuleHeightDp() {
        return this.capsuleHeight;
    }

    public int cardWidthDp() {
        try {
            if (isLandscape()) {
                return Settings.CARD_WIDTH_LANDSCAPE_DP;
            }
            return this.cardWidth;
        } catch (Throwable th) {
            return Settings.BUBBLE_WIDTH_DP;
        }
    }

    public int cardHeightDp() {
        return this.cardHeight;
    }

    public int bubbleOpacity() {
        return this.bubbleOpacity;
    }

    public int capsuleCornerDp() {
        return this.capsuleCorner;
    }

    public int cardCornerDp() {
        return this.cardCorner;
    }

    // fixed102: cap an expanded card at the user-set height (0 = auto).
    // The content scrolls inside a fixed-height ScrollView so nothing is
    // cut off. Content is updated through field references (titleView,
    // nameView, ...), not by walking the hierarchy, so wrapping is safe.
    public View capCardHeight(View content) {
        int h = cardHeightDp();
        if (h <= 0 || content == null) {
            return content;
        }
        try {
            ViewGroup.LayoutParams lp = content.getLayoutParams();
            int w = lp != null ? lp.width : dp(cardWidthDp());
            android.widget.ScrollView sv = new android.widget.ScrollView(getApp());
            sv.setLayoutParams(new LinearLayout.LayoutParams(w, dp(h)));
            content.setLayoutParams(new android.widget.ScrollView.LayoutParams(-1, -2));
            sv.addView(content);
            return sv;
        } catch (Throwable th) {
            return content;
        }
    }

    private void applyWindowFlags(boolean z) {
        int i;
        if (this.replyFocusable) {
            i = 32;
        } else {
            i = 40;
        }
        if (!z) {
            i |= 16;
        }
        this.params.flags = i;
    }

    public void setReplyFocusable(boolean z) {
        if (!this.windowAdded) {
            this.replyFocusable = z;
            return;
        }
        if (this.replyFocusable == z) {
            return;
        }
        this.replyFocusable = z;
        try {
            applyWindowFlags(visibleDockChildren() > 0);
            this.wm.updateViewLayout(this.root, this.params);
        } catch (Throwable th) {
            log("Failed to switch reply focus mode", th);
        }
    }

    private int visibleDockChildren() {
        if (this.dock == null) {
            return 0;
        }
        int i = 0;
        for (int i2 = 0; i2 < this.dock.getChildCount(); i2++) {
            if (this.dock.getChildAt(i2).getVisibility() == 0) {
                i++;
            }
        }
        return i;
    }

    private void relayoutDock() {
        if (this.dock == null) {
            return;
        }
        int childCount = this.dock.getChildCount();
        // fixed80: mirror the whole dock to the dragged edge — children
        // align START and the 12dp edge inset moves to the left margin.
        int edgeGrav = this.dockLeft ? 8388611 : 8388613;
        int i = 0;
        while (i < childCount) {
            View childAt = this.dock.getChildAt(i);
            ViewGroup.LayoutParams layoutParams = childAt.getLayoutParams();
            if (layoutParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) layoutParams;
                layoutParams2.gravity = edgeGrav;
                layoutParams2.bottomMargin = i == childCount + (-1) ? 0 : dp(DOCK_GAP_DP);
                if (this.dockLeft) {
                    layoutParams2.leftMargin = TAG_EDGE.equals(childAt.getTag()) ? 0 : dp(12);
                    layoutParams2.rightMargin = 0;
                } else {
                    layoutParams2.rightMargin = TAG_EDGE.equals(childAt.getTag()) ? 0 : dp(12);
                    layoutParams2.leftMargin = 0;
                }
                childAt.setLayoutParams(layoutParams2);
            }
            i++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onScreenPowerChanged(boolean z) {
        this.screenOn = z;
        for (BubbleFeature bubbleFeature : new ArrayList<BubbleFeature>(this.features)) {
            try {
                bubbleFeature.onScreenChanged(z);
            } catch (Throwable th) {
                log("onScreenChanged failed for " + bubbleFeature.getClass().getSimpleName(), th);
            }
        }
        log(z ? "Screen on." : "Screen off.");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onConfigurationChangedInternal() {
        try {
            if (this.wm != null) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                this.wm.getDefaultDisplay().getMetrics(displayMetrics);
                this.screenWidthPx = displayMetrics.widthPixels;
                this.screenHeightPx = displayMetrics.heightPixels;
            }
        } catch (Throwable th) {
            log("Failed to refresh screen size", th);
        }
        for (BubbleFeature bubbleFeature : new ArrayList<BubbleFeature>(this.features)) {
            try {
                bubbleFeature.onConfigurationChanged();
            } catch (Throwable th2) {
                log("onConfigurationChanged failed for " + bubbleFeature.getClass().getSimpleName(), th2);
            }
        }
        updateWindowSize();
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public Application getApp() {
        return this.app;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public Handler getHandler() {
        return this.handler;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int dp(int i) {
        float f = this.density;
        if (f <= 0.0f && this.app != null) {
            f = this.app.getResources().getDisplayMetrics().density;
        }
        if (f <= 0.0f) {
            f = 1.0f;
        }
        return Math.round(i * f);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isLandscape() {
        return this.app != null && this.app.getResources().getConfiguration().orientation == 2;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isScreenOn() {
        return this.screenOn;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void addDockView(final View view) {
        LinearLayout.LayoutParams layoutParams;
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.3
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.addDockView(view);
                }
            });
            return;
        }
        if (this.dock == null) {
            log("addDockView before window ready, dropping view.");
            return;
        }
        try {
            if (view.getParent() == this.dock) {
                this.dock.removeView(view);
            } else if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            ViewGroup.LayoutParams layoutParams2 = view.getLayoutParams();
            if (layoutParams2 instanceof LinearLayout.LayoutParams) {
                layoutParams = (LinearLayout.LayoutParams) layoutParams2;
            } else if (layoutParams2 != null) {
                layoutParams = new LinearLayout.LayoutParams(layoutParams2.width, layoutParams2.height);
            } else {
                layoutParams = new LinearLayout.LayoutParams(-2, -2);
            }
            // fixed80: mirror to the dragged edge (gravity START,
            // inset on the left margin).
            layoutParams.gravity = this.dockLeft ? 8388611 : 8388613;
            if (layoutParams.width == -1) {
                layoutParams.width = -2;
            }
            if (layoutParams.height == -1) {
                layoutParams.height = -2;
            }
            if (this.dockLeft) {
                layoutParams.leftMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.rightMargin = 0;
            } else {
                layoutParams.rightMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.leftMargin = 0;
            }
            view.setLayoutParams(layoutParams);
            // fixed74: no sinking — a view whose owner already had a
            // dock slot returns to it (refresh, card shrink, dock
            // expand); a brand-new bubble appends at the end. A view
            // added while another bubble holds the focus stays hidden
            // (GONE) until the focus ends.
            BubbleFeature owner = ownerOf(view);
            Integer savedIndex = owner == null ? null : this.lastDockIndex.remove(owner);
            int insertAt;
            if (savedIndex != null) {
                insertAt = Math.min(savedIndex.intValue(), this.dock.getChildCount());
            } else if (!this.dockUpper && this.dock.getChildCount() > 0 && this.dock.getChildAt(0) == this.dockedPile) {
                // fixed80: dock in the lower half — new notifications
                // stack upward (toward the screen centre): the newest
                // capsule goes right below the pile, above the older
                // capsules.
                insertAt = 1;
            } else if (!this.dockUpper) {
                insertAt = 0;
            } else {
                // Upper half — new notifications stack downward: the
                // newest capsule appends at the end, closest to the
                // centre.
                insertAt = this.dock.getChildCount();
            }
            this.dock.addView(view, insertAt);
            if (this.focusView != null) {
                view.setVisibility(view == this.focusView ? View.VISIBLE : View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
            }
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockView failed", th);
        }
    }

    // fixed43: append a view at the very bottom of the dock (the last
    // child, closest to the screen bottom edge). Used for the selected
    // notification's expanded card: whichever capsule the user taps, its
    // card automatically goes to the bottom of the integrated dock.
    // Same layout-param
    // setup and main-thread handling as addDockView, only the insertion
    // index differs.
    public void addDockViewAtBottom(final View view) {
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    BubbleManager.this.addDockViewAtBottom(view);
                }
            });
            return;
        }
        if (this.dock == null) {
            log("addDockViewAtBottom before window ready, dropping view.");
            return;
        }
        try {
            if (view.getParent() == this.dock) {
                this.dock.removeView(view);
            } else if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            ViewGroup.LayoutParams layoutParams2 = view.getLayoutParams();
            LinearLayout.LayoutParams layoutParams;
            if (layoutParams2 instanceof LinearLayout.LayoutParams) {
                layoutParams = (LinearLayout.LayoutParams) layoutParams2;
            } else if (layoutParams2 != null) {
                layoutParams = new LinearLayout.LayoutParams(layoutParams2.width, layoutParams2.height);
            } else {
                layoutParams = new LinearLayout.LayoutParams(-2, -2);
            }
            // fixed80: mirror to the dragged edge (gravity START,
            // inset on the left margin). Cards still append at the end
            // — the card bottom offset needs the card last.
            layoutParams.gravity = this.dockLeft ? 8388611 : 8388613;
            if (layoutParams.width == -1) {
                layoutParams.width = -2;
            }
            if (layoutParams.height == -1) {
                layoutParams.height = -2;
            }
            if (this.dockLeft) {
                layoutParams.leftMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.rightMargin = 0;
            } else {
                layoutParams.rightMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.leftMargin = 0;
            }
            view.setLayoutParams(layoutParams);
            // fixed73: same as addDockView now — no independent group.
            // A card (any bubble's) appends at the very end, i.e. the
            // bottom of the integrated dock.
            this.dock.addView(view, this.dock.getChildCount());
            // fixed74: a card added while another bubble holds the focus
            // stays hidden until it takes the focus itself (setFocusView
            // runs right after the add in every card path).
            if (this.focusView != null && view != this.focusView) {
                view.setVisibility(View.GONE);
            }
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockViewAtBottom failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void removeDockView(final View view) {
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.4
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.removeDockView(view);
                }
            });
            return;
        }
        if (this.dock == null) {
            return;
        }
        try {
            if (view.getParent() == this.dock) {
                // fixed74: remember this bubble's slot — but only for
                // capsules. A card (302dp / 262dp landscape wide) must
                // not overwrite the capsule's slot, or the capsule
                // would sink to the card's old (bottom) slot when it
                // comes back.
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                boolean isCard = lp != null && (lp.width == dp(cardWidthDp()) || lp.width == dp(Settings.BUBBLE_WIDTH_LANDSCAPE_DP));
                if (!isCard) {
                    BubbleFeature owner = ownerOf(view);
                    if (owner != null) {
                        this.lastDockIndex.put(owner, Integer.valueOf(this.dock.indexOfChild(view)));
                    }
                }
                // fixed74: taking the focus view out ends the focus —
                // everything else becomes visible again.
                if (view == this.focusView) {
                    clearFocus();
                }
                this.dock.removeView(view);
                relayoutDock();
                updateWindowSize();
            }
        } catch (Throwable th) {
            log("removeDockView failed", th);
        }
    }

    // fixed49: shared docked pile. Ensures the pile container exists and
    // is the first child of the dock (above the capsules), then adds the
    // icon to the overlapping icon stack. Each icon is lifted 12dp per
    // older entry so the pile reads as a deck of cards (same as the
    // notification docked stack, fixed44). fixed70: no handle any more —
    // the pile is icons only, flush against the edge.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void addDockedIcon(final View icon, final Runnable onTap) {
        if (icon == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.addDockedIcon(icon, onTap);
                }
            });
            return;
        }
        if (this.dock == null) {
            log("addDockedIcon before window ready, dropping icon.");
            return;
        }
        try {
            if (this.dockedPile == null) {
                LinearLayout pile = new LinearLayout(this.app);
                pile.setOrientation(0);
                pile.setGravity(16);
                pile.setClipChildren(false);
                pile.setTag(TAG_EDGE);
                // fixed70: the "‹" handle is gone (user 2026-11-17 21:07:
                // "贴边的箭头去掉吧不要了，只保留图标") — the pile is
                // just the overlapping icons, flush against the edge
                // (no 4dp gap that used to sit after the handle).
                FrameLayout icons = new FrameLayout(this.app);
                icons.setClipChildren(false);
                LinearLayout.LayoutParams iconsLp = new LinearLayout.LayoutParams(-2, -2);
                pile.addView(icons, iconsLp);
                this.dockedPileIcons = icons;
                this.dockedPile = pile;
            }
            if (icon.getParent() != null) {
                ((ViewGroup) icon.getParent()).removeView(icon);
            }
            if (this.dockedPileIconList.contains(icon)) {
                this.dockedPileIconList.remove(icon);
            }
            icon.setClickable(true);
            if (onTap != null) {
                icon.setOnClickListener(new View.OnClickListener() {
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        try {
                            onTap.run();
                        } catch (Throwable th) {
                            BubbleManager.this.log("docked pile tap failed", th);
                        }
                    }
                });
            }
            // fixed80: long-press a docked icon to drag the whole dock.
            icon.setOnTouchListener(pileDragTouchListener());
            this.dockedPileIconList.add(icon);
            relayoutDockedPile();
            // fixed61: pile back in the main dock as its first child
            // (fixed59 layout). fixed60's own window for the pile is
            // reverted: after installing fixed60 ordinary bubbles stopped
            // showing at all. The pile stays flush against the right
            // edge (fixed52): gravity END + rightMargin 0.
            if (this.dockedPile.getParent() != this.dock) {
                if (this.dockedPile.getParent() != null) {
                    ((ViewGroup) this.dockedPile.getParent()).removeView(this.dockedPile);
                }
                LinearLayout.LayoutParams pileLp = new LinearLayout.LayoutParams(-2, -2);
                // fixed80: mirror the pile to the dragged edge.
                pileLp.gravity = this.dockLeft ? 8388611 : 8388613;
                pileLp.rightMargin = 0;
                pileLp.leftMargin = 0;
                this.dock.addView(this.dockedPile, 0, pileLp);
                // fixed74: a pile (re)created while a bubble holds the
                // focus stays hidden until the focus ends.
                if (this.focusView != null) {
                    this.dockedPile.setVisibility(View.GONE);
                }
            }
            // fixed119: the pile may belong below the capsules (upper
            // half + a pin active), not always at index 0.
            repositionPile();
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockedIcon failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void removeDockedIcon(final View icon) {
        if (icon == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.removeDockedIcon(icon);
                }
            });
            return;
        }
        // fixed67: the pile is destroyed when its last icon leaves and
        // rebuilt by the next addDockedIcon, so record its screen Y now
        // (before the teardown) — updateWindowSize can no longer see
        // the old pile once it is gone.
        armPileGlide();
        try {
            if (this.dockedPileIcons != null && icon.getParent() == this.dockedPileIcons) {
                this.dockedPileIcons.removeView(icon);
            }
            this.dockedPileIconList.remove(icon);
            if (this.dockedPileIconList.isEmpty()) {
                // fixed61: pile back in the dock (fixed59) — take the
                // pile row out of the dock when the last icon leaves.
                if (this.dockedPile != null && this.dockedPile.getParent() == this.dock) {
                    this.dock.removeView(this.dockedPile);
                }
                this.dockedPile = null;
                this.dockedPileIcons = null;
            } else {
                relayoutDockedPile();
            }
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("removeDockedIcon failed", th);
        }
    }

    // fixed69: the pile opens and closes as one unit at capsule level.
    // Each call is guarded inside the bubble itself (only a docked
    // player expands, only a capsule-out bubble docks), so a dock
    // triggering the other side's dock cannot loop back.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void expandPlayerToMini() {
        try {
            if (this.musicBubble != null) {
                this.musicBubble.expandFromDock();
            }
        } catch (Throwable th) {
            log("expandPlayerToMini failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void dockPlayerTogether() {
        try {
            if (this.musicBubble != null) {
                this.musicBubble.dockFromMini();
            }
        } catch (Throwable th) {
            log("dockPlayerTogether failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void dockNotifsTogether() {
        try {
            if (this.notifBubble != null) {
                this.notifBubble.dockFromMini();
            }
        } catch (Throwable th) {
            log("dockNotifsTogether failed", th);
        }
    }

    // fixed74: which bubble owns a dock view (for the no-sinking index
    // memory). The pile itself has no owner.
    private BubbleFeature ownerOf(View v) {
        if (v == null) {
            return null;
        }
        try {
            if (this.musicBubble != null && this.musicBubble.ownsView(v)) {
                return this.musicBubble;
            }
            if (this.callBubble != null && this.callBubble.ownsView(v)) {
                return this.callBubble;
            }
            if (this.notifBubble != null && this.notifBubble.ownsView(v)) {
                return this.notifBubble;
            }
        } catch (Throwable th) {
        }
        return null;
    }

    // fixed74: unified focus. setFocusView shows only the given view
    // (a fresh capsule or an expanded card) and hides every other dock
    // child, pile included. clearFocus restores them all. Removing the
    // focus view (removeDockView) clears the focus automatically.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void setFocusView(final View view) {
        if (view == null || this.dock == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.setFocusView(view);
                }
            });
            return;
        }
        try {
            if (view.getParent() != this.dock) {
                return;
            }
            this.focusView = view;
            for (int i = 0; i < this.dock.getChildCount(); i++) {
                View child = this.dock.getChildAt(i);
                child.setVisibility(child == view ? View.VISIBLE : View.GONE);
            }
            updateWindowSize();
        } catch (Throwable th) {
            log("setFocusView failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void clearFocus() {
        if (this.focusView == null || this.dock == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() {
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.clearFocus();
                }
            });
            return;
        }
        try {
            this.focusView = null;
            for (int i = 0; i < this.dock.getChildCount(); i++) {
                this.dock.getChildAt(i).setVisibility(View.VISIBLE);
            }
            updateWindowSize();
        } catch (Throwable th) {
            log("clearFocus failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isFocusActive() {
        return this.focusView != null;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isFocusView(View view) {
        return view != null && view == this.focusView;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isFocusCard() {
        try {
            if (this.focusView == null || this.focusView.getParent() != this.dock) {
                return false;
            }
            ViewGroup.LayoutParams lp = this.focusView.getLayoutParams();
            return lp != null && (lp.width == dp(cardWidthDp()) || lp.width == dp(Settings.BUBBLE_WIDTH_LANDSCAPE_DP));
        } catch (Throwable th) {
            return false;
        }
    }

    // fixed74: expand-all — clear the focus, then pull every docked
    // bubble out to its capsule. Each bubble guards itself (only a
    // docked one expands; a dismissed live bubble stays down).
    @Override // com.jarvis.bubblenotify.BubbleHost
    public void expandAllToMini() {
        try {
            clearFocus();
            if (this.notifBubble != null) {
                this.notifBubble.expandFromDock();
            }
            if (this.musicBubble != null) {
                this.musicBubble.expandFromDock();
            }
            if (this.callBubble != null) {
                this.callBubble.expandFromDock();
            }
        } catch (Throwable th) {
            log("expandAllToMini failed", th);
        }
    }

    private void relayoutDockedPile() {
        if (this.dockedPileIcons == null) {
            return;
        }
        // Oldest first so the most recent ends up on top (drawn last).
        // Most recent sits at the very bottom; each older icon is lifted
        // 12dp so the pile reads as a deck of cards.
        // fixed80: mirror the pile to the dragged edge — the icon
        // frames sit BOTTOM|START on the left edge (BOTTOM|END on the
        // right), and each icon's overflowing inner view (34-40dp in a
        // 22dp frame) flips its overflow toward the edge too.
        int frameGrav = this.dockLeft ? 8388691 : 8388693;
        int overflowGrav = this.dockLeft ? 8388629 : 8388627;
        int n = this.dockedPileIconList.size();
        for (int i = 0; i < n; i++) {
            View icon = this.dockedPileIconList.get(i);
            int gi = (n - 1) - i;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, frameGrav);
            lp.bottomMargin = dp(gi * 12);
            icon.setLayoutParams(lp);
            flipIconOverflow(icon, overflowGrav);
            if (icon.getParent() != this.dockedPileIcons) {
                this.dockedPileIcons.addView(icon, lp);
            } else {
                this.dockedPileIcons.updateViewLayout(icon, lp);
            }
        }
    }

    // fixed80: flip an already-docked icon's inner overflow toward the
    // current edge. The overflowing view is the one wider than the 22dp
    // frame (34dp app icon / 38dp avatar / 38dp nav arrow / 40dp disc)
    // carrying an overflow gravity (START 8388627 on the right edge,
    // END 8388629 on the left). The bubbles build the icon with the
    // edge at build time, but an icon docked before the edge changed
    // keeps its old gravity until it re-docks — this re-flips it in
    // place so the pile stays flush after a drag. Only a child of a
    // 22dp-wide frame is flipped, so nothing else in the icon is
    // touched.
    private void flipIconOverflow(View view, int gravity) {
        if (!(view instanceof android.view.ViewGroup)) {
            return;
        }
        try {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            android.view.ViewGroup.LayoutParams vgLp = vg.getLayoutParams();
            boolean parentIsFrame = vgLp != null && vgLp.width == dp(22);
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (parentIsFrame && lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    if (flp.width > dp(22) && (flp.gravity == 8388627 || flp.gravity == 8388629)) {
                        flp.gravity = gravity;
                        child.setLayoutParams(flp);
                    }
                }
                flipIconOverflow(child, gravity);
            }
        } catch (Throwable th) {
            log("flipIconOverflow failed", th);
        }
    }

    // fixed67: record the docked pile's current screen Y as the glide
    // start. Only when the pile is actually laid out (a freshly
    // recreated pile has height 0 — leave any armed value alone, it
    // belongs to the pile that was just torn down). Never clears an
    // armed value: staleness is guarded by pileGlideArmedAt.
    private void armPileGlide() {
        try {
            if (this.dockedPile != null && this.dockedPile.getParent() == this.dock && this.dockedPile.getHeight() > 0) {
                int[] loc = new int[2];
                this.dockedPile.getLocationOnScreen(loc);
                this.pileGlideFromY = loc[1];
                this.pileGlideArmedAt = SystemClock.uptimeMillis();
                this.pileGlideSeq++;
            }
        } catch (Throwable ignored) {
        }
    }

    // fixed67: after the window resize is applied, glide the pile
    // container vertically from its pre-resize screen Y to its new
    // one. Double-posted so it runs after the next layout pass; the
    // seq check drops glides superseded by a later resize in the same
    // transition. Small shifts (< 4dp) stay instant.
    private void schedulePileGlide() {
        try {
            if (this.pileGlideFromY == Integer.MIN_VALUE || SystemClock.uptimeMillis() - this.pileGlideArmedAt > 500) {
                return;
            }
            final int fromY = this.pileGlideFromY;
            final int seq = this.pileGlideSeq;
            this.pileGlideFromY = Integer.MIN_VALUE;
            this.dock.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        BubbleManager.this.dock.post(new Runnable() {
                            @Override
                            public void run() {
                                BubbleManager.this.glidePileTo(fromY, seq, 5);
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void glidePileTo(int fromY, int seq, int triesLeft) {
        try {
            if (seq != this.pileGlideSeq) {
                return;
            }
            if (this.dockedPile == null || this.dockedPile.getParent() != this.dock || this.dockedPile.getHeight() <= 0) {
                return;
            }
            this.dockedPile.animate().cancel();
            this.dockedPile.setTranslationY(0.0f);
            int[] loc = new int[2];
            this.dockedPile.getLocationOnScreen(loc);
            int dy = fromY - loc[1];
            if (dy > -dp(4) && dy < dp(4)) {
                // fixed68: the window resize from updateViewLayout is
                // async — this measurement may run before it lands, in
                // which case dy is ~0 and the pile would still teleport
                // on the next frame. Retry a few times so the glide
                // does not silently miss.
                if (triesLeft > 0 && SystemClock.uptimeMillis() - this.pileGlideArmedAt < 500) {
                    final int f = fromY;
                    final int s = seq;
                    final int t = triesLeft - 1;
                    this.dock.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BubbleManager.this.glidePileTo(f, s, t);
                        }
                    }, 16L);
                }
                return;
            }
            this.dockedPile.setTranslationY(dy);
            this.dockedPile.animate().translationY(0.0f).setDuration(200L).setListener(null).start();
        } catch (Throwable th) {
            log("glidePile failed", th);
        }
    }

    // fixed73: the fixed53 special ranking (music 0 / call 1 / nav 2 /
    // ordinary 3) is deleted with the independent group — there is no
    // separate group left to rank. ownsView() in the three bubbles is
    // now unused but kept (harmless helper).

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void updateWindowSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.5
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.updateWindowSize();
                }
            });
            return;
        }
        if (!this.windowAdded || this.dock == null || this.swiping) {
            return;
        }
        // fixed67: record the pile's screen Y before this resize — a
        // shrink below the pile must glide it down/up to the edge, not
        // teleport it with the window.
        // fixed80: not during a pile drag — the pile follows the finger.
        if (!this.dockDragging) {
            armPileGlide();
        }
        try {
            if (visibleDockChildren() == 0) {
                this.params.width = 1;
                this.params.height = 1;
                this.params.x = 0;
                applyWindowFlags(false);
                this.wm.updateViewLayout(this.root, this.params);
                return;
            }
            int i = this.screenWidthPx > 0 ? this.screenWidthPx : 1080;
            int i2 = this.screenHeightPx > 0 ? this.screenHeightPx : 2400;
            this.dock.measure(View.MeasureSpec.makeMeasureSpec(i, Integer.MIN_VALUE), View.MeasureSpec.makeMeasureSpec(i2, Integer.MIN_VALUE));
            int iMax = Math.max(1, this.dock.getMeasuredWidth());
            int iMax2 = Math.max(1, this.dock.getMeasuredHeight());
            int iDp = i - dp(24);
            if (iMax > iDp) {
                iMax = Math.max(1, iDp);
                log("Window width clamped to " + iMax);
            }
            int i3 = (int) (i2 * 0.85f);
            if (iMax2 > i3) {
                iMax2 = i3;
            }
            // fixed34: safety margin — the measured dock height can
            // undershoot the laid-out height by a few dp, which clips the
            // bottom of the lowest card (screenshot 2090). Give the
            // window a little extra room so the card's bottom edge and
            // rounded corners are never cut off.
            iMax2 = Math.min(iMax2 + dp(8), i2);
            this.params.width = iMax;
            this.params.height = iMax2;
            this.params.x = 0;
            applyWindowFlags(true);
            this.wm.updateViewLayout(this.root, this.params);
            if (!this.dockDragging) {
                schedulePileGlide();
            }
        } catch (Throwable th) {
            log("Failed to update window size", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void sendCommand(Intent intent) {
        if (intent == null || this.app == null) {
            return;
        }
        try {
            intent.setPackage(Settings.MODULE_PACKAGE);
            this.app.sendBroadcast(intent);
        } catch (Throwable th) {
            log("sendCommand failed: " + intent.getAction(), th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void sendDismiss(String str) {
        if (str == null) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_DISMISS);
        intent.putExtra(Settings.EXTRA_KEY, str);
        sendCommand(intent);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void sendMusicControl(String str) {
        Intent intent = new Intent(Settings.ACTION_MUSIC_CONTROL);
        intent.putExtra(Settings.EXTRA_CONTROL, str);
        sendCommand(intent);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void sendMusicSeek(long j) {
        Intent intent = new Intent(Settings.ACTION_MUSIC_CONTROL);
        intent.putExtra(Settings.EXTRA_CONTROL, Settings.CONTROL_SEEK);
        intent.putExtra(Settings.EXTRA_SEEK_POSITION, j);
        sendCommand(intent);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void openPendingIntent(PendingIntent pendingIntent) {
        if (pendingIntent == null || !this.tapOpen) {
            return;
        }
        try {
            String creatorPkg = null;
            try {
                creatorPkg = pendingIntent.getCreatorPackage();
            } catch (Throwable ignored) {
            }
            Bundle bundleMakeFreeformOptions = makeFreeformOptions(creatorPkg);
            try {
                if (bundleMakeFreeformOptions != null) {
                    pendingIntent.send(this.app, 0, null, null, null, null, bundleMakeFreeformOptions);
                } else {
                    pendingIntent.send();
                }
            } catch (Throwable th) {
                log("openPendingIntent with options failed, retry plain: " + String.valueOf(th));
                try {
                    pendingIntent.send();
                } catch (Throwable th2) {
                    log("Failed to send contentIntent", th2);
                }
            }
        } catch (Throwable th) {
            log("openPendingIntent failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void openIntent(Intent intent) {
        if (intent == null || !this.tapOpen) {
            return;
        }
        try {
            intent.addFlags(268435456);
            String targetPkg = intent.getPackage();
            if (targetPkg == null && intent.getComponent() != null) {
                targetPkg = intent.getComponent().getPackageName();
            }
            Bundle bundleMakeFreeformOptions = makeFreeformOptions(targetPkg);
            Application application = this.app;
            if (bundleMakeFreeformOptions != null) {
                application.startActivity(intent, bundleMakeFreeformOptions);
            } else {
                application.startActivity(intent);
            }
        } catch (Throwable th) {
            log("openIntent with options failed, retry plain: " + String.valueOf(th));
            try {
                this.app.startActivity(intent);
            } catch (Throwable th2) {
                log("Failed to start activity", th2);
            }
        }
    }

    // fixed14: Flyme small-window (小窗) open. Flyme Island's approach:
    // flyme.view.WindowManagerExt.setStartWindowMode(pkg) + the
    // "start_windowmode" flag in the launch options. The standard Android
    // WINDOWING_MODE_FREEFORM is ignored by Flyme's window manager, which
    // is why tapping the card opened fullscreen even with freeform on.
    // This runs inside SystemUI, so the START_TASKS_FROM_RECENTS permission
    // Flyme Island checks is available here too.
    private boolean prepareFlymeMiniWindow(String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            Class<?> cls = Class.forName("flyme.view.WindowManagerExt");
            Object inst = cls.getMethod("getInstance", Context.class).invoke(null, this.app);
            if (inst == null) {
                return false;
            }
            cls.getMethod("setStartWindowMode", String.class).invoke(inst, pkg);
            return true;
        } catch (Throwable th) {
            log("Flyme mini window unavailable: " + th.getClass().getSimpleName());
            return false;
        }
    }

    // fixed88: clear a persisted Flyme small-window start mode for
    // the package. setStartWindowMode(pkg) (1-arg, used by
    // prepareFlymeMiniWindow) arms the next launch as a mini window and
    // the mode sticks; the 2-arg overload with false clears it, so a
    // later plain launch is fullscreen again. Best-effort: any failure
    // just means the clear did not happen.
    private void clearFlymeMiniWindow(String pkg) {
        if (pkg == null) {
            return;
        }
        try {
            Class<?> cls = Class.forName("flyme.view.WindowManagerExt");
            Object inst = cls.getMethod("getInstance", Context.class).invoke(null, this.app);
            if (inst == null) {
                return;
            }
            try {
                cls.getMethod("setStartWindowMode", String.class, Boolean.TYPE).invoke(inst, pkg, Boolean.FALSE);
                log("Flyme mini window cleared for " + pkg);
            } catch (Throwable th) {
                log("Flyme mini window clear unavailable: " + th.getClass().getSimpleName());
            }
        } catch (Throwable ignored) {
        }
    }

    private Bundle makeFreeformOptions(String pkg) {
        if (!this.freeform) {
            // fixed88: freeform off = fullscreen. Clear any persisted
            // mini-window start mode, then launch with explicit
            // fullscreen windowing mode instead of a bare send (a bare
            // send would reuse an existing small-window task).
            clearFlymeMiniWindow(pkg);
            try {
                ActivityOptions opts = ActivityOptions.makeBasic();
                try {
                    ActivityOptions.class.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(opts, Integer.valueOf(WINDOWING_MODE_FULLSCREEN));
                } catch (Throwable ignored) {
                }
                return opts.toBundle();
            } catch (Throwable th) {
                log("Failed to build fullscreen options", th);
                return null;
            }
        }
        try {
            ActivityOptions activityOptionsMakeBasic = ActivityOptions.makeBasic();
            // Flyme path first: proprietary small window.
            if (prepareFlymeMiniWindow(pkg)) {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    try {
                        activityOptionsMakeBasic.setPendingIntentBackgroundActivityStartMode(1);
                    } catch (Throwable ignored) {
                    }
                }
                Bundle bundle = activityOptionsMakeBasic.toBundle();
                bundle.putBoolean("start_windowmode", true);
                log("Flyme mini window prepared for " + pkg);
                return bundle;
            }
            // Standard Android freeform fallback (non-Flyme devices).
            float f = this.screenWidthPx > 0 ? this.screenWidthPx : 1080;
            float f2 = this.screenHeightPx > 0 ? this.screenHeightPx : 2400;
            Rect rect = new Rect(Math.round(0.075f * f), Math.round(0.175f * f2), Math.round(f * 0.925f), Math.round(f2 * 0.825f));
            activityOptionsMakeBasic.setLaunchBounds(rect);
            try {
                ActivityOptions.class.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(activityOptionsMakeBasic, Integer.valueOf(WINDOWING_MODE_FREEFORM));
            } catch (Throwable th) {
                log("setLaunchWindowingMode unavailable, bounds only: " + String.valueOf(th));
            }
            return activityOptionsMakeBasic.toBundle();
        } catch (Throwable th2) {
            log("Failed to build freeform options", th2);
            return null;
        }
    }

    public boolean isFreeform() {
        return this.freeform;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void log(String str) {
        try {
            if (this.xposed != null) {
                this.xposed.log(4, Settings.TAG, str);
            } else {
                Log.i(Settings.TAG, str);
            }
        } catch (Throwable th) {
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void log(String str, Throwable th) {
        try {
            if (this.xposed != null) {
                this.xposed.log(6, Settings.TAG, str, th);
            } else {
                Log.e(Settings.TAG, str, th);
            }
        } catch (Throwable th2) {
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public GradientDrawable frostedBackground(int i) {
        refreshGlassTheme(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(glassFillColor());
        // fixed64: visible border on every card. The old 1dp white
        // 30%/55% strokes were nearly invisible on bright wallpapers.
        gradientDrawable.setStroke(dp(1), borderColor());
        // fixed102: card corner radius is user-adjustable; the passed
        // radius is kept only as a fallback (it is ignored).
        gradientDrawable.setCornerRadius(dp(cardCornerDp()));
        return gradientDrawable;
    }

    // fixed102: frosted-glass fill with the user-set opacity. Default
    // 75% gives alpha 191 (0xBF), exactly the old fill.
    private int glassFillColor() {
        int alpha = Math.max(0, Math.min(255, (this.bubbleOpacity * 255) / 100));
        int rgb = this.glassDark ? 0x1E1E1E : 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public GradientDrawable pillBackground() {
        refreshGlassTheme(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(glassFillColor());
        // fixed64: visible border on every capsule (same edge as cards).
        gradientDrawable.setStroke(dp(1), borderColor());
        // fixed102: capsule corner radius is user-adjustable (default 52,
        // i.e. a pill: >= half the capsule height).
        gradientDrawable.setCornerRadius(dp(capsuleCornerDp()));
        return gradientDrawable;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isDarkGlass() {
        refreshGlassTheme(false);
        return this.glassDark;
    }

    // fixed64: border colour for every card and capsule. Dark glass
    // (bright wallpaper) gets a strong white edge; light glass (dark
    // wallpaper) gets a dark edge — in both cases the outline reads
    // against the frosted fill and the wallpaper behind it.
    @Override // com.jarvis.bubblenotify.BubbleHost
    public int borderColor() {
        refreshGlassTheme(false);
        int base = this.glassDark ? 0xB3FFFFFF : 0x59000000;
        int baseAlpha = (base >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, (baseAlpha * this.bubbleOpacity) / 75));
        return (alpha << 24) | (base & 0x00FFFFFF);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textMainColor() {
        return isDarkGlass() ? 0xFFF5F5F5 : -15065826;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textSubColor() {
        return isDarkGlass() ? 0xFFD8D8D8 : -11905960;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textDimColor() {
        return isDarkGlass() ? 0xFFB0B0B0 : -8748153;
    }

    // fixed58: sample the wallpaper brightness behind the bubbles. The
    // bubbles live at the bottom-right corner, so only that corner of
    // the wallpaper is sampled (right 35% x bottom 30%, every 8th
    // pixel — a few thousand samples, cheap). Luminance uses the same
    // Rec.709 weights Flyme's backdrop shader uses. Above the threshold
    // the wallpaper counts as bright and the glass goes dark.
    private void refreshGlassTheme(boolean force) {
        try {
            long now = SystemClock.uptimeMillis();
            if (!force && now - this.glassSampledAt < GLASS_CACHE_MS) {
                return;
            }
            this.glassSampledAt = now;
            if (this.app == null) {
                return;
            }
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this.app);
            Drawable drawable = wallpaperManager != null ? wallpaperManager.getDrawable() : null;
            Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            int x0 = (int) (width * 0.65);
            int y0 = (int) (height * 0.70);
            long luminanceSum = 0;
            int samples = 0;
            for (int y = y0; y < height; y += 8) {
                for (int x = x0; x < width; x += 8) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = (pixel >> 16) & 255;
                    int g = (pixel >> 8) & 255;
                    int b = pixel & 255;
                    luminanceSum += (long) (0.2126 * r + 0.7152 * g + 0.0722 * b);
                    samples++;
                }
            }
            if (samples == 0) {
                return;
            }
            double luminance = luminanceSum / (double) samples / 255.0;
            boolean dark = luminance > GLASS_DARK_LUMINANCE;
            if (dark != this.glassDark) {
                this.glassDark = dark;
                log("Glass theme: " + (dark ? "dark" : "light") + " (wallpaper luminance " + ((int) (luminance * 100)) + "%)");
            }
        } catch (Throwable th) {
            log("Glass theme sampling failed", th);
        }
    }

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Settings.ACTION_NOTIFY_LIST);
        intentFilter.addAction(Settings.ACTION_NOTIFY);
        intentFilter.addAction(Settings.ACTION_HIDE);
        intentFilter.addAction(Settings.ACTION_MUSIC);
        intentFilter.addAction(Settings.ACTION_CODE);
        intentFilter.addAction(Settings.ACTION_TEST_CODE);
        intentFilter.addAction(Settings.ACTION_CALL);
        intentFilter.addAction(Settings.ACTION_SETTINGS);
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.intent.action.CONFIGURATION_CHANGED");
        intentFilter.addAction("android.intent.action.WALLPAPER_CHANGED");
        try {
            Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, Integer.TYPE).invoke(this.app, this.receiver, intentFilter, 2);
            log("Receiver registered (exported).");
        } catch (Throwable th) {
            try {
                this.app.registerReceiver(this.receiver, intentFilter);
                log("Receiver registered (legacy).");
            } catch (Throwable th2) {
                log("Failed to register receiver", th2);
            }
        }
    }
}
