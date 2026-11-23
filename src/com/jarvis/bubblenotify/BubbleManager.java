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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import io.github.libxposed.api.XposedInterface;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class BubbleManager implements BubbleHost {
    private static final int DOCK_GAP_DP = 10;
    private static final long GLASS_CACHE_MS = 60000;
    private static final double GLASS_DARK_LUMINANCE = 0.62d;
    private static final BubbleManager INSTANCE = new BubbleManager();
    public static final String TAG_EDGE = "bn_edge";
    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final int WINDOWING_MODE_FULLSCREEN = 1;
    private Application app;
    private CallBubble callBubble;
    private float density;
    private LinearLayout dock;
    private LinearLayout dockedPile;
    private FrameLayout dockedPileIcons;
    private Runnable dragLongPressRunnable;
    private View focusView;
    private long lastRegionLogMs;
    private MusicBubble musicBubble;
    private NotifBubble notifBubble;
    private WindowManager.LayoutParams params;
    private View.OnTouchListener pileDragListener;
    private FrameLayout root;
    private int screenHeightPx;
    private int screenWidthPx;
    private FileLock windowLock;
    private FileChannel windowLockChannel;
    private WindowManager wm;
    private XposedInterface xposed;
    private final Map<BubbleFeature, Integer> lastDockIndex = new HashMap();
    private final ArrayList<View> dockedPileIconList = new ArrayList<>();
    private int pileGlideFromY = Integer.MIN_VALUE;
    private long pileGlideArmedAt = 0;
    private int pileGlideSeq = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean initialized = false;
    private boolean windowAdded = false;
    // fixed192: charging animation window (2.0 v477 Sakura).
    private View chargingRoot;
    private ChargingAnimView chargingView;
    private WindowManager.LayoutParams chargingParams;
    private boolean chargingWindowAdded = false;
    private boolean chargingAnimPlaying = false;
    private boolean glassDark = false;
    private long glassSampledAt = 0;
    private boolean swiping = false;
    private boolean cardOpen = false;
    private boolean screenOn = true;
    // Scene guard: landscape game/video scene hides bubbles.
    // See ImmersiveSceneGuard.
    private ImmersiveSceneGuard sceneGuard;
    private boolean sceneSuppressed;
    private boolean replyFocusable = false;
    private boolean enabled = true;
    // v203: per-app bubble rules (AppRules), pkg -> mode.
    private final java.util.HashMap<String, Integer> appRules = new java.util.HashMap<>();
    private boolean musicEnabled = true;
    private boolean callEnabled = true;
    private int cardBottomOffsetDp = 32;
    private boolean dockLeft = false;
    private boolean dockUpper = false;
    private int dockEdgeDistDp = 14;
    private boolean dockDragging = false;
    private float dragDownX = 0.0f;
    private float dragDownY = 0.0f;
    private float dragLastRawY = 0.0f;
    private float dragStartRawY = 0.0f;
    private int dragStartParamsY = 0;
    private boolean dragLongPressFired = false;
    private boolean tapOpen = true;
    private boolean cardExpand = true;
    private int capsuleWidth = Settings.DEFAULT_CAPSULE_WIDTH_DP;
    private int capsuleHeight = 52;
    private int cardWidth = 302;
    private int cardHeight = 0;
    private int bubbleOpacity = 75;
    private int capsuleCorner = 52;
    private int cardCorner = 20;
    private int musicCardWidth = 302;
    private int musicCardHeight = 0;
    private int musicCardOpacity = 75;
    private int musicCardCorner = 20;
    private boolean freeform = true;
    private final List<BubbleFeature> features = new ArrayList();
    private final BroadcastReceiver receiver = new BroadcastReceiver() { // from class: com.jarvis.bubblenotify.BubbleManager.1
        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        /* JADX WARN: Code duplicated, block: B:30:0x006d  */
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            try {
                String action = intent.getAction();
                if ("android.intent.action.WALLPAPER_CHANGED".equals(action)) {
                    BubbleManager.this.refreshGlassTheme(true);
                }
                if ("android.intent.action.SCREEN_ON".equals(action)) {
                    BubbleManager.this.onScreenPowerChanged(true);
                    BubbleManager.this.refreshGlassTheme(true);
                }
                if ("android.intent.action.SCREEN_OFF".equals(action)) {
                    BubbleManager.this.onScreenPowerChanged(false);
                    return;
                }
                // fixed192: charging animation (2.0 v477). Only fires
                // on plug-in (ACTION_POWER_CONNECTED); battery
                // broadcasts while charging do not replay it.
                if ("android.intent.action.ACTION_POWER_CONNECTED".equals(action)) {
                    BubbleManager.this.showChargingAnim();
                    return;
                }
                if ("android.intent.action.CONFIGURATION_CHANGED".equals(action)) {
                    BubbleManager.this.onConfigurationChangedInternal();
                    BubbleManager.this.refreshGlassTheme(true);
                    return;
                }
                if (Settings.ACTION_LOG.equals(action)) {
                    try {
                        String line = intent.getStringExtra("line");
                        if (line != null) {
                            FileLog.i(line);
                        }
                    } catch (Throwable ignored) {
                    }
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
        if (!acquireWindowLock()) {
            log("BubbleManager inert: another process holds the window lock.");
            FileLog.i("BubbleManager inert: another process holds the window lock");
            return;
        }
        loadSettingsFromRemotePrefs();
        new CallBannerSuppress(xposedInterface, application).install();
        new CallNotifSuppress(xposedInterface, application).install();
        new NotifCollectionHook(xposedInterface, application, this).install();
        try {
            PowerManager powerManager = (PowerManager) application.getSystemService("power");
            this.screenOn = (powerManager == null || powerManager.isInteractive()) ? true : false;
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

    public boolean hasNotifKey(String str) {
        return this.notifBubble != null && this.notifBubble.hasKey(str);
    }

    public NotifBubble getNotifBubble() {
        return this.notifBubble;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v0, types: [java.lang.String] */
    /* JADX WARN: Type inference failed for: r0v1 */
    /* JADX WARN: Type inference failed for: r0v10 */
    /* JADX WARN: Type inference failed for: r0v11 */
    /* JADX WARN: Type inference failed for: r0v2 */
    /* JADX WARN: Type inference failed for: r0v3 */
    /* JADX WARN: Type inference failed for: r0v8 */
    /* JADX WARN: Type inference failed for: r3v1, types: [java.lang.String[]] */
    /* JADX WARN: Type inference failed for: r5v0, types: [java.lang.String] */
    private boolean acquireWindowLock() {
        String[] r0 = {"/data/local/tmp/BubbleNotify_window.lock"};
        try {
            File filesDir = this.app.getFilesDir();
            if (filesDir != null) {
                r0 = new String[]{new File(filesDir, "BubbleNotify_window.lock").getAbsolutePath(), "/data/local/tmp/BubbleNotify_window.lock"};
            } else {
                r0 = new String[]{"/data/local/tmp/BubbleNotify_window.lock"};
            }
        } catch (Throwable th) {
            r0 = new String[]{"/data/local/tmp/BubbleNotify_window.lock"};
        }
        int length = r0.length;
        for (int i = 0; i < length; i += WINDOWING_MODE_FULLSCREEN) {
            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile((String) r0[i], "rw");
                FileChannel channel = randomAccessFile.getChannel();
                FileLock fileLockTryLock = channel.tryLock();
                if (fileLockTryLock == null) {
                    try {
                        channel.close();
                    } catch (Throwable th2) {
                    }
                    try {
                        randomAccessFile.close();
                    } catch (Throwable th3) {
                    }
                    return false;
                }
                this.windowLockChannel = channel;
                this.windowLock = fileLockTryLock;
                return true;
            } catch (Throwable th4) {
            }
        }
        log("acquireWindowLock failed on all paths, allowing window");
        return true;
    }

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
        } else {
            if (!this.musicEnabled) {
                callFeatureDisabled(this.musicBubble);
            }
            if (!this.callEnabled) {
                callFeatureDisabled(this.callBubble);
            }
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
            this.cardBottomOffsetDp = remotePreferences.getInt(Settings.KEY_CARD_BOTTOM_OFFSET_DP, 32);
            this.capsuleWidth = remotePreferences.getInt(Settings.KEY_CAPSULE_WIDTH_DP, Settings.DEFAULT_CAPSULE_WIDTH_DP);
            this.capsuleHeight = remotePreferences.getInt(Settings.KEY_CAPSULE_HEIGHT_DP, 52);
            this.cardWidth = remotePreferences.getInt(Settings.KEY_CARD_WIDTH_DP, 302);
            this.cardHeight = remotePreferences.getInt(Settings.KEY_CARD_HEIGHT_DP, 0);
            this.bubbleOpacity = remotePreferences.getInt(Settings.KEY_BUBBLE_OPACITY, 75);
            this.capsuleCorner = remotePreferences.getInt(Settings.KEY_CAPSULE_CORNER_DP, 52);
            this.cardCorner = remotePreferences.getInt(Settings.KEY_CARD_CORNER_DP, 20);
            this.musicCardWidth = remotePreferences.getInt(Settings.KEY_MUSIC_CARD_WIDTH_DP, 302);
            this.musicCardHeight = remotePreferences.getInt(Settings.KEY_MUSIC_CARD_HEIGHT_DP, 0);
            this.musicCardOpacity = remotePreferences.getInt(Settings.KEY_MUSIC_CARD_OPACITY, 75);
            this.musicCardCorner = remotePreferences.getInt(Settings.KEY_MUSIC_CARD_CORNER_DP, 20);
            this.appRules.clear();
            this.appRules.putAll(AppRules.parseRules(remotePreferences));
            log("Settings loaded from remote prefs.");
        } catch (Throwable th) {
            log("Remote prefs unavailable, using defaults: " + String.valueOf(th));
        }
        try {
            int offsetFile = readOffsetFile();
            if (offsetFile >= 0) {
                this.cardBottomOffsetDp = offsetFile;
            }
        } catch (Throwable th2) {
        }
    }

    static int readOffsetFile() {
        int i;
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(Settings.OFFSET_FILE_PATH));
            String line = bufferedReader.readLine();
            bufferedReader.close();
            if (line != null && (i = Integer.parseInt(line.trim())) >= 0 && i <= 200) {
                return i;
            }
            return -1;
        } catch (Throwable th) {
            return -1;
        }
    }

    public void applySettingsFromIntent(Intent intent) {
        // v210: per-app rules ride this intent as a flattened
        // extra (AppRules.EXTRA_RULES) — v209 diagnostics proved
        // remote preferences do not refresh live in this process
        // (every live reload read {}), while intent extras are
        // how every other setting here arrives. Remote prefs
        // remain the fallback when no extra is present.
        try {
            String flat = intent.getStringExtra(AppRules.EXTRA_RULES);
            if (flat != null) {
                this.appRules.clear();
                this.appRules.putAll(AppRules.parseFlat(flat));
            } else {
                SharedPreferences rp = this.xposed.getRemotePreferences(Settings.PREFS_NAME);
                this.appRules.clear();
                this.appRules.putAll(AppRules.parseRules(rp));
            }
        } catch (Throwable ignored) {
        }
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
            this.musicCardWidth = intent.getIntExtra(Settings.KEY_MUSIC_CARD_WIDTH_DP, this.musicCardWidth);
            this.musicCardHeight = intent.getIntExtra(Settings.KEY_MUSIC_CARD_HEIGHT_DP, this.musicCardHeight);
            this.musicCardOpacity = intent.getIntExtra(Settings.KEY_MUSIC_CARD_OPACITY, this.musicCardOpacity);
            this.musicCardCorner = intent.getIntExtra(Settings.KEY_MUSIC_CARD_CORNER_DP, this.musicCardCorner);
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
            this.dock.setOrientation(WINDOWING_MODE_FULLSCREEN);
            loadDockPosition();
            int i = this.dockLeft ? 8388611 : 8388613;
            int i2 = this.dockUpper ? 48 : 80;
            this.dock.setGravity(i);
            this.dock.setClipChildren(false);
            int i3 = i | i2;
            this.dock.setLayoutParams(new FrameLayout.LayoutParams(-2, -2, i3));
            this.root.addView(this.dock);
            // fixed187/fixed188: notifications visible on the
            // lock screen (user "通知改成锁屏也可以看见" /
            // "锁屏看不见气泡"). fixed187 added FLAG_SHOW_WHEN_LOCKED
            // but TYPE_APPLICATION_OVERLAY (2038) still did not show
            // above the keyguard. fixed188 switches the type to
            // TYPE_SYSTEM_ERROR (2010), the same type 2.0 uses for
            // its bubble windows — a system-level type that reliably
            // shows above the lock screen. Flags stay 56 |
            // FLAG_SHOW_WHEN_LOCKED.
            this.params = new WindowManager.LayoutParams(WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_FULLSCREEN, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, 56 | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, -3);
            this.params.gravity = i3;
            this.params.x = 0;
            this.params.y = dp(this.dockEdgeDistDp);
            this.params.layoutInDisplayCutoutMode = WINDOWING_MODE_FULLSCREEN;
            this.params.setTitle("BubbleNotify");
            this.wm.addView(this.root, this.params);
            this.windowAdded = true;
            installTouchableRegionListener();
            // Scene guard init: entering a landscape game/video
            // scene hides the whole bubble root (same trick as the
            // screen-off hide in onScreenPowerChanged); leaving it
            // restores the root (unless the screen is off) and
            // flushes the notifications NotifBubble queued.
            try {
                this.sceneGuard = new ImmersiveSceneGuard(this.app, this.handler);
                this.sceneGuard.setListener(new ImmersiveSceneGuard.Listener() {
                    @Override
                    public void onSceneChanged(boolean immersive) {
                        BubbleManager.this.sceneSuppressed = immersive;
                        try {
                            if (BubbleManager.this.root != null) {
                                BubbleManager.this.root.setVisibility(
                                        immersive || !BubbleManager.this.screenOn
                                                ? View.GONE : View.VISIBLE);
                            }
                        } catch (Throwable ignored) {
                        }
                        if (!immersive && BubbleManager.this.notifBubble != null) {
                            BubbleManager.this.notifBubble.flushSuppressedQueue();
                        }
                    }
                });
                this.sceneGuard.start();
            } catch (Throwable ignored) {
            }
            log("Bubble window added. screen=" + this.screenWidthPx + "x" + this.screenHeightPx);
        } catch (Throwable th) {
            log("Failed to add bubble window", th);
        }
    }

    private void installTouchableRegionListener() {
        try {
            Class<?> cls = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            Class<?> cls2 = Class.forName("android.view.ViewTreeObserver$InternalInsetsInfo");
            final Field field = cls2.getField("touchableRegion");
            final Method method = cls2.getMethod("setTouchableInsets", Integer.TYPE);
            Method declaredMethod = null;
            final int i = cls2.getField("TOUCHABLE_INSETS_REGION").getInt(null);
            Object objNewProxyInstance = Proxy.newProxyInstance(BubbleManager.class.getClassLoader(), new Class[]{cls}, new InvocationHandler() { // from class: com.jarvis.bubblenotify.BubbleManager.3
                @Override // java.lang.reflect.InvocationHandler
                public Object invoke(Object obj, Method method2, Object[] objArr) throws Throwable {
                    if ("onComputeInternalInsets".equals(method2.getName()) && objArr != null && objArr.length == BubbleManager.WINDOWING_MODE_FULLSCREEN) {
                        try {
                            Object obj2 = objArr[0];
                            Region region = new Region();
                            if (BubbleManager.this.dock != null) {
                                int[] iArr = new int[2];
                                for (int i2 = 0; i2 < BubbleManager.this.dock.getChildCount(); i2 += BubbleManager.WINDOWING_MODE_FULLSCREEN) {
                                    View childAt = BubbleManager.this.dock.getChildAt(i2);
                                    if (childAt != null && childAt.getVisibility() == 0) {
                                        if (childAt == BubbleManager.this.dockedPile && BubbleManager.this.dockedPileIcons != null) {
                                            FrameLayout frameLayout = BubbleManager.this.dockedPileIcons;
                                            for (int i3 = 0; i3 < frameLayout.getChildCount(); i3 += BubbleManager.WINDOWING_MODE_FULLSCREEN) {
                                                View childAt2 = frameLayout.getChildAt(i3);
                                                if (childAt2 != null && childAt2.getVisibility() == 0) {
                                                    childAt2.getLocationInWindow(iArr);
                                                    region.union(new Rect(iArr[0], iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN], iArr[0] + childAt2.getWidth(), iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN] + childAt2.getHeight()));
                                                }
                                            }
                                        } else if (childAt instanceof LinearLayout) {
                                            ViewGroup viewGroup = (ViewGroup) childAt;
                                            for (int i4 = 0; i4 < viewGroup.getChildCount(); i4 += BubbleManager.WINDOWING_MODE_FULLSCREEN) {
                                                View childAt3 = viewGroup.getChildAt(i4);
                                                if (childAt3 != null && childAt3.getVisibility() == 0) {
                                                    childAt3.getLocationInWindow(iArr);
                                                    region.union(new Rect(iArr[0], iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN], iArr[0] + childAt3.getWidth(), iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN] + childAt3.getHeight()));
                                                }
                                            }
                                        } else {
                                            childAt.getLocationInWindow(iArr);
                                            region.union(new Rect(iArr[0], iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN], iArr[0] + childAt.getWidth(), iArr[BubbleManager.WINDOWING_MODE_FULLSCREEN] + childAt.getHeight()));
                                        }
                                    }
                                }
                            }
                            ((Region) field.get(obj2)).set(region);
                            method.invoke(obj2, Integer.valueOf(i));
                            try {
                                long jUptimeMillis = SystemClock.uptimeMillis();
                                if (jUptimeMillis - BubbleManager.this.lastRegionLogMs > 1000) {
                                    BubbleManager.this.lastRegionLogMs = jUptimeMillis;
                                    Rect rect = new Rect();
                                    BubbleManager.this.log("Touchable region: " + (region.getBounds(rect) ? rect.toShortString() : "empty") + " children=" + (BubbleManager.this.dock != null ? BubbleManager.this.dock.getChildCount() : -1));
                                    return null;
                                }
                                return null;
                            } catch (Throwable th) {
                                return null;
                            }
                        } catch (Throwable th2) {
                            return null;
                        }
                    }
                    return null;
                }
            });
            try {
                declaredMethod = this.root.getViewTreeObserver().getClass().getMethod("addOnComputeInternalInsetsListener", cls);
            } catch (Throwable th) {
            }
            if (declaredMethod == null) {
                try {
                    declaredMethod = this.root.getViewTreeObserver().getClass().getDeclaredMethod("addOnComputeInternalInsetsListener", cls);
                    declaredMethod.setAccessible(true);
                    log("Touchable region listener: using getDeclaredMethod.");
                } catch (Throwable th2) {
                    log("Touchable region listener: no add method", th2);
                }
            }
            if (declaredMethod != null) {
                declaredMethod.invoke(this.root.getViewTreeObserver(), objNewProxyInstance);
                log("Touchable region listener installed.");
            }
        } catch (Throwable th3) {
            log("Touchable region listener failed", th3);
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

    private void setDockBottomMargin(int i) {
        try {
            if (this.dock == null) {
                return;
            }
            ViewGroup.LayoutParams layoutParams = this.dock.getLayoutParams();
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) layoutParams;
                if (layoutParams2.bottomMargin != i) {
                    layoutParams2.bottomMargin = i;
                    this.dock.setLayoutParams(layoutParams2);
                }
            }
        } catch (Throwable th) {
            log("setDockBottomMargin failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void applyCardBottomOffset() {
        if (!this.cardOpen || this.dock == null || this.root == null) {
            return;
        }
        try {
            setDockBottomMargin(this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp)));
            this.root.requestLayout();
            this.dock.requestLayout();
        } catch (Throwable th) {
            log("applyCardBottomOffset failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void setCardOpen(boolean z) {
        if (!this.windowAdded || this.root == null) {
            return;
        }
        this.cardOpen = z;
        try {
            if (z) {
                setDockBottomMargin(this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp)));
            } else {
                setDockBottomMargin(0);
            }
            this.params.y = dp(this.dockEdgeDistDp);
        } catch (Throwable th) {
        }
        updateWindowSize();
    }

    private void applyDockPosition() {
        if (!this.windowAdded || this.params == null) {
            return;
        }
        repositionPile();
        try {
            int i = this.dockUpper ? 48 : 80;
            int i2 = this.dockLeft ? 8388611 : 8388613;
            int i3 = i | i2;
            this.params.gravity = i3;
            this.params.y = dp(this.dockEdgeDistDp);
            if (this.dock != null) {
                ViewGroup.LayoutParams layoutParams = this.dock.getLayoutParams();
                if (layoutParams instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) layoutParams;
                    layoutParams2.gravity = i3;
                    this.dock.setLayoutParams(layoutParams2);
                }
                this.dock.setGravity(i2);
            }
            relayoutDock();
            relayoutDockedPile();
            if (this.cardOpen) {
                setDockBottomMargin(this.dockUpper ? 0 : dp(Math.max(0, this.cardBottomOffsetDp - this.dockEdgeDistDp)));
            } else {
                setDockBottomMargin(0);
            }
            updateWindowSize();
        } catch (Throwable th) {
            log("applyDockPosition failed", th);
        }
    }

    /* JADX WARN: Type inference failed for: r0v7, types: [java.lang.Throwable] */
    /* JADX WARN: Type inference failed for: r2v1, types: [java.lang.Throwable] */
    private void saveDockPosition() {
        String str = (this.dockLeft ? 1 : 0) + "," + (this.dockUpper ? 1 : 0) + "," + this.dockEdgeDistDp;
        try {
            FileWriter fileWriter = new FileWriter(new File(this.app.getFilesDir(), "BubbleNotify_dock.txt"), false);
            fileWriter.write(str);
            fileWriter.close();
        } catch (Throwable th) {
            try {
                FileWriter fileWriter2 = new FileWriter("/data/local/tmp/BubbleNotify_dock.txt", false);
                fileWriter2.write(str);
                fileWriter2.close();
            } catch (Throwable th2) {
            }
        }
    }

    private void loadDockPosition() {
        String line = null;
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(this.app.getFilesDir(), "BubbleNotify_dock.txt")));
            line = bufferedReader.readLine();
            bufferedReader.close();
        } catch (Throwable th) {
        }
        if (line == null) {
            try {
                BufferedReader bufferedReader2 = new BufferedReader(new FileReader("/data/local/tmp/BubbleNotify_dock.txt"));
                line = bufferedReader2.readLine();
                bufferedReader2.close();
            } catch (Throwable th2) {
            }
        }
        if (line == null) {
            return;
        }
        try {
            String[] strArrSplit = line.trim().split(",");
            if (strArrSplit.length >= 3) {
                this.dockLeft = Integer.parseInt(strArrSplit[0].trim()) == WINDOWING_MODE_FULLSCREEN ? true : false;
                this.dockUpper = Integer.parseInt(strArrSplit[WINDOWING_MODE_FULLSCREEN].trim()) == WINDOWING_MODE_FULLSCREEN ? true : false;
                int i = Integer.parseInt(strArrSplit[2].trim());
                if (i >= 0 && i <= 4000) {
                    this.dockEdgeDistDp = i;
                }
            }
        } catch (Throwable th3) {
        }
    }

    private View.OnTouchListener pileDragTouchListener() {
        if (this.pileDragListener != null) {
            return this.pileDragListener;
        }
        this.pileDragListener = new View.OnTouchListener() { // from class: com.jarvis.bubblenotify.BubbleManager.4
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View view, MotionEvent motionEvent) {
                try {
                    switch (motionEvent.getActionMasked()) {
                        case 0:
                            BubbleManager.this.dragDownX = motionEvent.getRawX();
                            BubbleManager.this.dragDownY = motionEvent.getRawY();
                            BubbleManager.this.dragLastRawY = motionEvent.getRawY();
                            BubbleManager.this.dragLongPressFired = false;
                            if (BubbleManager.this.dragLongPressRunnable == null) {
                                BubbleManager.this.dragLongPressRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.4.1
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        BubbleManager.this.startPileDrag();
                                    }
                                };
                            }
                            BubbleManager.this.handler.postDelayed(BubbleManager.this.dragLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                            return false;
                        case BubbleManager.WINDOWING_MODE_FULLSCREEN /* 1 */:
                        case Settings.MAX_VISIBLE_BUBBLES /* 3 */:
                            BubbleManager.this.handler.removeCallbacks(BubbleManager.this.dragLongPressRunnable);
                            if (!BubbleManager.this.dockDragging) {
                                return false;
                            }
                            BubbleManager.this.endPileDrag(motionEvent.getRawX(), motionEvent.getRawY());
                            return true;
                        case 2:
                            BubbleManager.this.dragLastRawY = motionEvent.getRawY();
                            if (BubbleManager.this.dockDragging) {
                                BubbleManager.this.movePileDrag(motionEvent.getRawY());
                                return true;
                            }
                            float rawX = motionEvent.getRawX() - BubbleManager.this.dragDownX;
                            float rawY = motionEvent.getRawY() - BubbleManager.this.dragDownY;
                            int scaledTouchSlop = ViewConfiguration.get(BubbleManager.this.app).getScaledTouchSlop();
                            if ((rawX * rawX) + (rawY * rawY) > scaledTouchSlop * scaledTouchSlop) {
                                BubbleManager.this.handler.removeCallbacks(BubbleManager.this.dragLongPressRunnable);
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

    /* JADX INFO: Access modifiers changed from: private */
    public void startPileDrag() {
        if (this.dock == null || !this.windowAdded || this.params == null) {
            return;
        }
        this.dockDragging = true;
        this.dragLongPressFired = true;
        this.dragStartRawY = this.dragLastRawY;
        this.dragStartParamsY = this.params.y;
        try {
            if (this.dock != null) {
                this.dock.performHapticFeedback(0);
            }
        } catch (Throwable th) {
        }
        log("Pile drag started.");
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void startDockDrag() {
        try {
            startPileDrag();
        } catch (Throwable th) {
            log("startDockDrag failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void moveDockDrag(float f) {
        try {
            movePileDrag(f);
        } catch (Throwable th) {
            log("moveDockDrag failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void endDockDrag(float f, float f2) {
        try {
            endPileDrag(f, f2);
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

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void repositionPile() {
        boolean z;
        try {
            if (this.dock == null || this.dockedPile == null || this.dockedPile.getParent() != this.dock) {
                return;
            }
            int childCount = WINDOWING_MODE_FULLSCREEN;
            try {
                if (this.notifBubble != null && this.notifBubble.anyPinned()) {
                    z = true;
                } else {
                    z = false;
                }
            } catch (Throwable th) {
                z = false;
            }
            if (!z) {
                try {
                    if (this.musicBubble != null && this.musicBubble.isPlayerPinned()) {
                        z = true;
                    }
                } catch (Throwable th2) {
                }
            }
            if (!this.dockUpper || !z) {
                childCount = 0;
            }
            if (this.dock.indexOfChild(this.dockedPile) == childCount) {
                return;
            }
            if (childCount > this.dock.getChildCount()) {
                childCount = this.dock.getChildCount();
            }
            this.dock.removeView(this.dockedPile);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
            layoutParams.gravity = this.dockLeft ? 8388611 : 8388613;
            layoutParams.rightMargin = 0;
            layoutParams.leftMargin = 0;
            this.dock.addView(this.dockedPile, childCount, layoutParams);
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th3) {
            log("repositionPile failed", th3);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void movePileDrag(float f) {
        int i;
        if (!this.dockDragging || !this.windowAdded || this.root == null || this.params == null) {
            return;
        }
        try {
            int height = this.params.height;
            if (height <= 0) {
                height = this.dock != null ? this.dock.getHeight() : 0;
            }
            if (height > 0 && this.screenHeightPx > 0) {
                float f2 = f - this.dragStartRawY;
                int iMax = Math.max(0, this.screenHeightPx - height);
                if (this.dockUpper) {
                    i = (int) (this.dragStartParamsY + f2);
                } else {
                    i = (int) (this.dragStartParamsY - f2);
                }
                this.params.y = Math.max(0, Math.min(i, iMax));
                this.wm.updateViewLayout(this.root, this.params);
            }
        } catch (Throwable th) {
            log("movePileDrag failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void endPileDrag(float f, float f2) {
        if (!this.dockDragging) {
            return;
        }
        this.dockDragging = false;
        try {
            int height = this.params.height;
            if (height <= 0) {
                height = this.dock != null ? this.dock.getHeight() : 0;
            }
            if (height > 0 && this.screenHeightPx > 0 && this.screenWidthPx > 0 && this.density > 0.0f) {
                int iMax = Math.max(0, Math.min(this.dockUpper ? this.params.y : (this.screenHeightPx - this.params.y) - height, this.screenHeightPx - height));
                boolean z = true;
                this.dockUpper = ((float) iMax) + (((float) height) / 2.0f) < ((float) this.screenHeightPx) / 2.0f ? true : false;
                if (f >= this.screenWidthPx / 2.0f) {
                    z = false;
                }
                this.dockLeft = z;
                if (!this.dockUpper) {
                    iMax = (this.screenHeightPx - iMax) - height;
                }
                this.dockEdgeDistDp = Math.max(0, Math.round(iMax / this.density));
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

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isDockLeft() {
        return this.dockLeft;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int capsuleWidthDp() {
        return this.capsuleWidth;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int capsuleHeightDp() {
        return this.capsuleHeight;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int cardWidthDp() {
        try {
            if (isLandscape()) {
                return Settings.CARD_WIDTH_LANDSCAPE_DP;
            }
            return this.cardWidth;
        } catch (Throwable th) {
            return 302;
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int cardHeightDp() {
        return this.cardHeight;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int bubbleOpacity() {
        return this.bubbleOpacity;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int capsuleCornerDp() {
        return this.capsuleCorner;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int cardCornerDp() {
        return this.cardCorner;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int musicCardWidthDp() {
        return this.musicCardWidth;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int musicCardHeightDp() {
        return this.musicCardHeight;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int musicCardOpacity() {
        return this.musicCardOpacity;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int musicCardCornerDp() {
        return this.musicCardCorner;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public View capCardHeight(View view) {
        int iCardHeightDp = cardHeightDp();
        if (iCardHeightDp <= 0 || view == null) {
            return view;
        }
        try {
            int iDp = dp(cardWidthDp());
            view.measure(View.MeasureSpec.makeMeasureSpec(iDp, 1073741824), View.MeasureSpec.makeMeasureSpec(0, 0));
            if (view.getMeasuredHeight() <= dp(iCardHeightDp)) {
                return view;
            }
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams != null) {
                iDp = layoutParams.width;
            }
            ScrollView scrollView = new ScrollView(getApp());
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(iDp, dp(iCardHeightDp)));
            view.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
            scrollView.addView(view);
            return scrollView;
        } catch (Throwable th) {
            return view;
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public View capMusicCardHeight(View view) {
        int iMusicCardHeightDp = musicCardHeightDp();
        if (iMusicCardHeightDp <= 0 || view == null) {
            return view;
        }
        try {
            int iDp = dp(musicCardWidthDp());
            view.measure(View.MeasureSpec.makeMeasureSpec(iDp, 1073741824), View.MeasureSpec.makeMeasureSpec(0, 0));
            if (view.getMeasuredHeight() <= dp(iMusicCardHeightDp)) {
                return view;
            }
            if (view.getLayoutParams() == null) {
                new ViewGroup.LayoutParams(iDp, dp(iMusicCardHeightDp));
            }
            ScrollView scrollView = new ScrollView(this.app);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(iDp, dp(iMusicCardHeightDp)));
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setOverScrollMode(2);
            view.setLayoutParams(new ViewGroup.LayoutParams(iDp, -2));
            scrollView.addView(view);
            return scrollView;
        } catch (Throwable th) {
            return view;
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
        for (int i2 = 0; i2 < this.dock.getChildCount(); i2 += WINDOWING_MODE_FULLSCREEN) {
            if (this.dock.getChildAt(i2).getVisibility() == 0) {
                i += WINDOWING_MODE_FULLSCREEN;
            }
        }
        return i;
    }

    private void relayoutDock() {
        if (this.dock == null) {
            return;
        }
        int childCount = this.dock.getChildCount();
        int i = this.dockLeft ? 8388611 : 8388613;
        int i2 = 0;
        while (i2 < childCount) {
            View childAt = this.dock.getChildAt(i2);
            ViewGroup.LayoutParams layoutParams = childAt.getLayoutParams();
            if (layoutParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) layoutParams;
                layoutParams2.gravity = i;
                layoutParams2.bottomMargin = i2 == childCount + (-1) ? 0 : dp(DOCK_GAP_DP);
                if (this.dockLeft) {
                    layoutParams2.leftMargin = TAG_EDGE.equals(childAt.getTag()) ? 0 : dp(12);
                    layoutParams2.rightMargin = 0;
                } else {
                    layoutParams2.rightMargin = TAG_EDGE.equals(childAt.getTag()) ? 0 : dp(12);
                    layoutParams2.leftMargin = 0;
                }
                childAt.setLayoutParams(layoutParams2);
            }
            i2 += WINDOWING_MODE_FULLSCREEN;
        }
    }

    // fixed192: charging animation (2.0 v477 Sakura, ported).
    // Fullscreen window, FLAG_NOT_TOUCHABLE so it never blocks
    // taps; plays once on plug-in (~3.6s), then removes the window.
    // A guard stops rapid plug/unplug from replaying mid-animation.
    public void showChargingAnim() {
        if (chargingAnimPlaying || app == null || wm == null) {
            return;
        }
        chargingAnimPlaying = true;
        try {
            if (chargingRoot == null) {
                chargingView = new ChargingAnimView(app);
                chargingView.setOnDone(new Runnable() {
                    @Override
                    public void run() {
                        hideChargingAnim();
                    }
                });
                chargingRoot = chargingView;
                chargingParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                        android.graphics.PixelFormat.TRANSLUCENT);
                chargingParams.gravity = android.view.Gravity.TOP
                        | android.view.Gravity.LEFT;
                chargingParams.setTitle("BubbleNotify-charging");
            }
            if (!chargingWindowAdded) {
                wm.addView(chargingRoot, chargingParams);
                chargingWindowAdded = true;
            }
            chargingRoot.setVisibility(View.VISIBLE);
            chargingView.start();
        } catch (Throwable ignored) {
            chargingAnimPlaying = false;
        }
    }

    private void hideChargingAnim() {
        chargingAnimPlaying = false;
        try {
            if (chargingRoot != null && chargingWindowAdded) {
                wm.removeView(chargingRoot);
                chargingWindowAdded = false;
            }
        } catch (Throwable ignored) {
        }
    }

    public void onScreenPowerChanged(boolean z) {
        this.screenOn = z;
        // fixed192: notifications must NOT show on the always-on
        // display (user "通知不要到息屏显示上面去"). The bubble
        // window shows above the lock screen (FLAG_SHOW_WHEN_LOCKED),
        // which also puts it above the AOD when the screen is off.
        // Hide the whole bubble root while the screen is off; show
        // it again when the screen turns on (lock screen or home).
        // Bubbles that arrive while the screen is off stay in the
        // hidden root and appear when the screen turns on.
        try {
            if (this.root != null) {
                this.root.setVisibility(z && !this.sceneSuppressed ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable th) {
            log("hide bubble root on screen off failed", th);
        }
        for (BubbleFeature bubbleFeature : new ArrayList<BubbleFeature>(this.features)) {
            try {
                bubbleFeature.onScreenChanged(z);
            } catch (Throwable th) {
                log("onScreenChanged failed for " + bubbleFeature.getClass().getSimpleName(), th);
            }
        }
        log(z ? "Screen on." : "Screen off.");
    }

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
    public boolean isSceneSuppressed() {
        return this.sceneSuppressed;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int appRuleFor(String str) {
        // v211: feature shelved at wei's request — gates
        // stay in the code but always see DEFAULT until the
        // sync problem is solved and the entry returns.
        if (true) {
            return AppRules.DEFAULT;
        }
        Integer v = str == null ? null : this.appRules.get(str);
        return v == null ? AppRules.DEFAULT : v.intValue();
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isEnabled() {
        return this.enabled;
    }

    private boolean isCardView(View view) {
        ViewGroup.LayoutParams layoutParams;
        if (view == null || (layoutParams = view.getLayoutParams()) == null) {
            return false;
        }
        return layoutParams.width == dp(cardWidthDp()) || layoutParams.width == dp(musicCardWidthDp()) || layoutParams.width == dp(Settings.BUBBLE_WIDTH_LANDSCAPE_DP);
    }



    @Override // com.jarvis.bubblenotify.BubbleHost
    public void addDockView(final View view) {
        LinearLayout.LayoutParams layoutParams;
        int childCount;
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.5
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
            layoutParams.gravity = this.dockLeft ? 8388611 : 8388613;
            if (layoutParams.width == -1) {
                layoutParams.width = -2;
            }
            if (layoutParams.height == -1) {
                layoutParams.height = -2;
            }
            int i = 0;
            if (this.dockLeft) {
                layoutParams.leftMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.rightMargin = 0;
            } else {
                layoutParams.rightMargin = TAG_EDGE.equals(view.getTag()) ? 0 : dp(12);
                layoutParams.leftMargin = 0;
            }
            view.setLayoutParams(layoutParams);
            BubbleFeature bubbleFeatureOwnerOf = ownerOf(view);
            Integer num = null;
            Integer numRemove = bubbleFeatureOwnerOf == null ? null : this.lastDockIndex.remove(bubbleFeatureOwnerOf);
            if (numRemove == null || !isCardView(this.focusView)) {
                num = numRemove;
            }
            if (num != null) {
                childCount = Math.min(num.intValue(), this.dock.getChildCount());
            } else if (!this.dockUpper && this.dock.getChildCount() > 0 && this.dock.getChildAt(0) == this.dockedPile) {
                childCount = WINDOWING_MODE_FULLSCREEN;
            } else if (!this.dockUpper) {
                childCount = 0;
            } else {
                childCount = this.dock.getChildCount();
            }
            this.dock.addView(view, childCount);
            if (this.focusView != null) {
                if (view != this.focusView) {
                    i = 8;
                }
                view.setVisibility(i);
            } else {
                view.setVisibility(0);
            }
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockView failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void addDockViewAtBottom(final View view) {
        LinearLayout.LayoutParams layoutParams;
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.6
                @Override // java.lang.Runnable
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
            if (layoutParams2 instanceof LinearLayout.LayoutParams) {
                layoutParams = (LinearLayout.LayoutParams) layoutParams2;
            } else if (layoutParams2 != null) {
                layoutParams = new LinearLayout.LayoutParams(layoutParams2.width, layoutParams2.height);
            } else {
                layoutParams = new LinearLayout.LayoutParams(-2, -2);
            }
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
            this.dock.addView(view, this.dock.getChildCount());
            if (this.focusView != null && view != this.focusView) {
                view.setVisibility(8);
            }
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockViewAtBottom failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void removeDockView(final View view) {
        BubbleFeature bubbleFeatureOwnerOf;
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.7
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
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                if (!(layoutParams != null && (layoutParams.width == dp(cardWidthDp()) || layoutParams.width == dp(musicCardWidthDp()) || layoutParams.width == dp(Settings.BUBBLE_WIDTH_LANDSCAPE_DP))) && (bubbleFeatureOwnerOf = ownerOf(view)) != null) {
                    this.lastDockIndex.put(bubbleFeatureOwnerOf, Integer.valueOf(this.dock.indexOfChild(view)));
                }
                if (view == this.focusView) {
                    clearFocus();
                }
                this.dock.removeView(view);
                relayoutDock();
                updateWindowSize();
            } else if (view.getParent() instanceof ViewGroup) {
                // fixed204: the view may be attached to a different
                // parent (or the add was posted async and the parent
                // is not the dock yet) — remove it from wherever it
                // is instead of leaving a stale orphan on screen
                // that miniView no longer references.
                if (view == this.focusView) {
                    clearFocus();
                }
                ((ViewGroup) view.getParent()).removeView(view);
                relayoutDock();
                updateWindowSize();
            }
        } catch (Throwable th) {
            log("removeDockView failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void addDockedIcon(final View view, final Runnable runnable) {
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.8
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.addDockedIcon(view, runnable);
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
                LinearLayout linearLayout = new LinearLayout(this.app);
                linearLayout.setOrientation(0);
                linearLayout.setGravity(16);
                linearLayout.setClipChildren(false);
                linearLayout.setTag(TAG_EDGE);
                FrameLayout frameLayout = new FrameLayout(this.app);
                frameLayout.setClipChildren(false);
                linearLayout.addView(frameLayout, new LinearLayout.LayoutParams(-2, -2));
                this.dockedPileIcons = frameLayout;
                this.dockedPile = linearLayout;
            }
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            if (this.dockedPileIconList.contains(view)) {
                this.dockedPileIconList.remove(view);
            }
            view.setClickable(true);
            if (runnable != null) {
                view.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.BubbleManager.9
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view2) {
                        try {
                            runnable.run();
                        } catch (Throwable th) {
                            BubbleManager.this.log("docked pile tap failed", th);
                        }
                    }
                });
            }
            view.setOnTouchListener(pileDragTouchListener());
            this.dockedPileIconList.add(view);
            relayoutDockedPile();
            if (this.dockedPile.getParent() != this.dock) {
                if (this.dockedPile.getParent() != null) {
                    ((ViewGroup) this.dockedPile.getParent()).removeView(this.dockedPile);
                }
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
                layoutParams.gravity = this.dockLeft ? 8388611 : 8388613;
                layoutParams.rightMargin = 0;
                layoutParams.leftMargin = 0;
                this.dock.addView(this.dockedPile, 0, layoutParams);
                if (this.focusView != null) {
                    this.dockedPile.setVisibility(8);
                }
            }
            repositionPile();
            relayoutDock();
            updateWindowSize();
        } catch (Throwable th) {
            log("addDockedIcon failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void removeDockedIcon(final View view) {
        if (view == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.10
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.removeDockedIcon(view);
                }
            });
            return;
        }
        armPileGlide();
        try {
            if (this.dockedPileIcons != null && view.getParent() == this.dockedPileIcons) {
                this.dockedPileIcons.removeView(view);
            }
            this.dockedPileIconList.remove(view);
            if (this.dockedPileIconList.isEmpty()) {
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

    private BubbleFeature ownerOf(View view) {
        if (view == null) {
            return null;
        }
        try {
            if (this.musicBubble != null && this.musicBubble.ownsView(view)) {
                return this.musicBubble;
            }
            if (this.callBubble != null && this.callBubble.ownsView(view)) {
                return this.callBubble;
            }
            if (this.notifBubble != null && this.notifBubble.ownsView(view)) {
                return this.notifBubble;
            }
            return null;
        } catch (Throwable th) {
        }
        return null;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void setFocusView(final View view) {
        if (view == null || this.dock == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.11
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
            for (int i = 0; i < this.dock.getChildCount(); i += WINDOWING_MODE_FULLSCREEN) {
                View childAt = this.dock.getChildAt(i);
                childAt.setVisibility(childAt == view ? 0 : 8);
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
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.12
                @Override // java.lang.Runnable
                public void run() {
                    BubbleManager.this.clearFocus();
                }
            });
            return;
        }
        try {
            this.focusView = null;
            for (int i = 0; i < this.dock.getChildCount(); i += WINDOWING_MODE_FULLSCREEN) {
                this.dock.getChildAt(i).setVisibility(0);
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
        ViewGroup.LayoutParams layoutParams;
        try {
            if (this.focusView == null || this.focusView.getParent() != this.dock || (layoutParams = this.focusView.getLayoutParams()) == null) {
                return false;
            }
            return layoutParams.width == dp(cardWidthDp()) || layoutParams.width == dp(musicCardWidthDp()) || layoutParams.width == dp(Settings.BUBBLE_WIDTH_LANDSCAPE_DP);
        } catch (Throwable th) {
            return false;
        }
    }

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
        int i = this.dockLeft ? 8388691 : 8388693;
        int i2 = this.dockLeft ? 8388629 : 8388627;
        int size = this.dockedPileIconList.size();
        for (int i3 = 0; i3 < size; i3 += WINDOWING_MODE_FULLSCREEN) {
            View view = this.dockedPileIconList.get(i3);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2, i);
            layoutParams.bottomMargin = dp(((size - 1) - i3) * 12);
            view.setLayoutParams(layoutParams);
            flipIconOverflow(view, i2);
            if (view.getParent() != this.dockedPileIcons) {
                this.dockedPileIcons.addView(view, layoutParams);
            } else {
                this.dockedPileIcons.updateViewLayout(view, layoutParams);
            }
        }
    }

    private void flipIconOverflow(View view, int i) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        try {
            ViewGroup viewGroup = (ViewGroup) view;
            ViewGroup.LayoutParams layoutParams = viewGroup.getLayoutParams();
            boolean z = layoutParams != null && layoutParams.width == dp(22);
            for (int i2 = 0; i2 < viewGroup.getChildCount(); i2 += WINDOWING_MODE_FULLSCREEN) {
                View childAt = viewGroup.getChildAt(i2);
                ViewGroup.LayoutParams layoutParams2 = childAt.getLayoutParams();
                if (z && (layoutParams2 instanceof FrameLayout.LayoutParams)) {
                    FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) layoutParams2;
                    if (layoutParams3.width > dp(22) && (layoutParams3.gravity == 8388627 || layoutParams3.gravity == 8388629)) {
                        layoutParams3.gravity = i;
                        childAt.setLayoutParams(layoutParams3);
                    }
                }
                flipIconOverflow(childAt, i);
            }
        } catch (Throwable th) {
            log("flipIconOverflow failed", th);
        }
    }

    private void armPileGlide() {
        try {
            if (this.dockedPile != null && this.dockedPile.getParent() == this.dock && this.dockedPile.getHeight() > 0) {
                int[] iArr = new int[2];
                this.dockedPile.getLocationOnScreen(iArr);
                this.pileGlideFromY = iArr[WINDOWING_MODE_FULLSCREEN];
                this.pileGlideArmedAt = SystemClock.uptimeMillis();
                this.pileGlideSeq += WINDOWING_MODE_FULLSCREEN;
            }
        } catch (Throwable th) {
        }
    }

    private void schedulePileGlide() {
        try {
            if (this.pileGlideFromY != Integer.MIN_VALUE && SystemClock.uptimeMillis() - this.pileGlideArmedAt <= 500) {
                final int i = this.pileGlideFromY;
                final int i2 = this.pileGlideSeq;
                this.pileGlideFromY = Integer.MIN_VALUE;
                this.dock.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.13
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            BubbleManager.this.dock.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.13.1
                                @Override // java.lang.Runnable
                                public void run() {
                                    BubbleManager.this.glidePileTo(i, i2, BubbleManager.WINDOWING_MODE_FREEFORM);
                                }
                            });
                        } catch (Throwable th) {
                        }
                    }
                });
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void glidePileTo(final int i, final int i2, int i3) {
        try {
            if (i2 == this.pileGlideSeq && this.dockedPile != null && this.dockedPile.getParent() == this.dock && this.dockedPile.getHeight() > 0) {
                this.dockedPile.animate().cancel();
                this.dockedPile.setTranslationY(0.0f);
                int[] iArr = new int[2];
                this.dockedPile.getLocationOnScreen(iArr);
                int i4 = i - iArr[WINDOWING_MODE_FULLSCREEN];
                if (i4 > (-dp(4)) && i4 < dp(4)) {
                    if (i3 > 0 && SystemClock.uptimeMillis() - this.pileGlideArmedAt < 500) {
                        final int i5 = i3 - WINDOWING_MODE_FULLSCREEN;
                        this.dock.postDelayed(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.14
                            @Override // java.lang.Runnable
                            public void run() {
                                BubbleManager.this.glidePileTo(i, i2, i5);
                            }
                        }, 16L);
                        return;
                    }
                    return;
                }
                this.dockedPile.setTranslationY(i4);
                this.dockedPile.animate().translationY(0.0f).setDuration(200L).setListener(null).start();
            }
        } catch (Throwable th) {
            log("glidePile failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void updateWindowSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.BubbleManager.15
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
        if (!this.dockDragging) {
            armPileGlide();
        }
        try {
            if (visibleDockChildren() == 0) {
                this.params.width = WINDOWING_MODE_FULLSCREEN;
                this.params.height = WINDOWING_MODE_FULLSCREEN;
                this.params.x = 0;
                applyWindowFlags(false);
                this.wm.updateViewLayout(this.root, this.params);
                return;
            }
            int i = this.screenWidthPx > 0 ? this.screenWidthPx : 1080;
            int i2 = this.screenHeightPx > 0 ? this.screenHeightPx : 2400;
            this.dock.measure(View.MeasureSpec.makeMeasureSpec(i, Integer.MIN_VALUE), View.MeasureSpec.makeMeasureSpec(i2, Integer.MIN_VALUE));
            int iMax = Math.max(WINDOWING_MODE_FULLSCREEN, this.dock.getMeasuredWidth());
            int iMax2 = Math.max(WINDOWING_MODE_FULLSCREEN, this.dock.getMeasuredHeight());
            int iDp = i - dp(24);
            if (iMax > iDp) {
                iMax = Math.max(WINDOWING_MODE_FULLSCREEN, iDp);
                log("Window width clamped to " + iMax);
            }
            int i3 = (int) (i2 * 0.85f);
            if (iMax2 > i3) {
                iMax2 = i3;
            }
            int iMin = Math.min(iMax2 + dp(8), i2);
            this.params.width = iMax;
            this.params.height = iMin;
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
        String creatorPackage;
        if (pendingIntent == null || !this.tapOpen) {
            return;
        }
        try {
            creatorPackage = pendingIntent.getCreatorPackage();
        } catch (Throwable th) {
            creatorPackage = null;
        }
        try {
            Bundle bundleMakeFreeformOptions = makeFreeformOptions(creatorPackage);
            try {
                if (bundleMakeFreeformOptions != null) {
                    pendingIntent.send(this.app, 0, null, null, null, null, bundleMakeFreeformOptions);
                } else {
                    pendingIntent.send();
                }
            } catch (Throwable th2) {
                log("openPendingIntent with options failed, retry plain: " + String.valueOf(th2));
                try {
                    pendingIntent.send();
                } catch (Throwable th3) {
                    log("Failed to send contentIntent", th3);
                }
            }
        } catch (Throwable th4) {
            log("openPendingIntent failed", th4);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public void openIntent(Intent intent) {
        if (intent == null || !this.tapOpen) {
            return;
        }
        try {
            intent.addFlags(268435456);
            String packageName = intent.getPackage();
            if (packageName == null && intent.getComponent() != null) {
                packageName = intent.getComponent().getPackageName();
            }
            Bundle bundleMakeFreeformOptions = makeFreeformOptions(packageName);
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

    private boolean prepareFlymeMiniWindow(String str) {
        if (str == null) {
            return false;
        }
        try {
            Class<?> cls = Class.forName("flyme.view.WindowManagerExt");
            Object objInvoke = cls.getMethod("getInstance", Context.class).invoke(null, this.app);
            if (objInvoke == null) {
                return false;
            }
            cls.getMethod("setStartWindowMode", String.class).invoke(objInvoke, str);
            return true;
        } catch (Throwable th) {
            log("Flyme mini window unavailable: " + th.getClass().getSimpleName());
            return false;
        }
    }

    private void clearFlymeMiniWindow(String str) {
        if (str == null) {
            return;
        }
        try {
            Class<?> cls = Class.forName("flyme.view.WindowManagerExt");
            Object objInvoke = cls.getMethod("getInstance", Context.class).invoke(null, this.app);
            if (objInvoke == null) {
                return;
            }
            try {
                cls.getMethod("setStartWindowMode", String.class, Boolean.TYPE).invoke(objInvoke, str, Boolean.FALSE);
                log("Flyme mini window cleared for " + str);
            } catch (Throwable th) {
                log("Flyme mini window clear unavailable: " + th.getClass().getSimpleName());
            }
        } catch (Throwable th2) {
        }
    }

    private Bundle makeFreeformOptions(String str) {
        boolean z = this.freeform;
        Integer numValueOf = Integer.valueOf(WINDOWING_MODE_FULLSCREEN);
        if (!z) {
            clearFlymeMiniWindow(str);
            try {
                ActivityOptions activityOptionsMakeBasic = ActivityOptions.makeBasic();
                try {
                    ActivityOptions.class.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(activityOptionsMakeBasic, numValueOf);
                } catch (Throwable th) {
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        activityOptionsMakeBasic.setPendingIntentBackgroundActivityStartMode(WINDOWING_MODE_FULLSCREEN);
                    } catch (Throwable th2) {
                    }
                } else {
                    try {
                        ActivityOptions.class.getMethod("setPendingIntentBackgroundActivityStartMode", Integer.TYPE).invoke(activityOptionsMakeBasic, numValueOf);
                    } catch (Throwable th3) {
                    }
                }
                return activityOptionsMakeBasic.toBundle();
            } catch (Throwable th4) {
                log("Failed to build fullscreen options", th4);
                return null;
            }
        }
        try {
            ActivityOptions activityOptionsMakeBasic2 = ActivityOptions.makeBasic();
            if (prepareFlymeMiniWindow(str)) {
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        activityOptionsMakeBasic2.setPendingIntentBackgroundActivityStartMode(WINDOWING_MODE_FULLSCREEN);
                    } catch (Throwable th5) {
                    }
                }
                Bundle bundle = activityOptionsMakeBasic2.toBundle();
                bundle.putBoolean("start_windowmode", true);
                log("Flyme mini window prepared for " + str);
                return bundle;
            }
            float f = this.screenWidthPx > 0 ? this.screenWidthPx : 1080.0f;
            float f2 = this.screenHeightPx > 0 ? this.screenHeightPx : 2400.0f;
            activityOptionsMakeBasic2.setLaunchBounds(new Rect(Math.round(0.075f * f), Math.round(0.175f * f2), Math.round(f * 0.925f), Math.round(f2 * 0.825f)));
            try {
                ActivityOptions.class.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(activityOptionsMakeBasic2, Integer.valueOf(WINDOWING_MODE_FREEFORM));
            } catch (Throwable th6) {
                log("setLaunchWindowingMode unavailable, bounds only: " + String.valueOf(th6));
            }
            return activityOptionsMakeBasic2.toBundle();
        } catch (Throwable th7) {
            log("Failed to build freeform options", th7);
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
        gradientDrawable.setStroke(dp(WINDOWING_MODE_FULLSCREEN), borderColor());
        gradientDrawable.setCornerRadius(dp(cardCornerDp()));
        return gradientDrawable;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public GradientDrawable musicFrostedBackground() {
        refreshGlassTheme(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(musicGlassFillColor());
        gradientDrawable.setStroke(dp(WINDOWING_MODE_FULLSCREEN), borderColor());
        gradientDrawable.setCornerRadius(dp(musicCardCornerDp()));
        return gradientDrawable;
    }

    private int musicGlassFillColor() {
        return (Math.max(0, Math.min(255, (this.musicCardOpacity * 255) / 100)) << 24) | (this.glassDark ? 1973790 : 16777215);
    }

    private int glassFillColor() {
        return (Math.max(0, Math.min(255, (this.bubbleOpacity * 255) / 100)) << 24) | (this.glassDark ? 1973790 : 16777215);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public GradientDrawable pillBackground() {
        refreshGlassTheme(false);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(glassFillColor());
        gradientDrawable.setStroke(dp(WINDOWING_MODE_FULLSCREEN), borderColor());
        gradientDrawable.setCornerRadius(dp(capsuleCornerDp()));
        return gradientDrawable;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public boolean isDarkGlass() {
        refreshGlassTheme(false);
        return this.glassDark;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int borderColor() {
        refreshGlassTheme(false);
        int i = this.glassDark ? -1275068417 : 1493172224;
        return (Math.max(0, Math.min(255, (((i >>> 24) & 255) * this.bubbleOpacity) / 75)) << 24) | (i & 16777215);
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textMainColor() {
        return isDarkGlass() ? -657931 : -15065826;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textSubColor() {
        return isDarkGlass() ? -2565928 : -11905960;
    }

    @Override // com.jarvis.bubblenotify.BubbleHost
    public int textDimColor() {
        return isDarkGlass() ? -5197648 : -8748153;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshGlassTheme(boolean z) {
        try {
            long jUptimeMillis = SystemClock.uptimeMillis();
            if (!z && jUptimeMillis - this.glassSampledAt < GLASS_CACHE_MS) {
                return;
            }
            this.glassSampledAt = jUptimeMillis;
            if (this.app == null) {
                return;
            }
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this.app);
            Drawable drawable = wallpaperManager != null ? wallpaperManager.getDrawable() : null;
            Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
            if (bitmap != null && !bitmap.isRecycled()) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                if (width > 0 && height > 0) {
                    int i = (int) (((double) width) * 0.65d);
                    long j = 0;
                    int i2 = 0;
                    for (int i3 = (int) (((double) height) * 0.7d); i3 < height; i3 += 8) {
                        for (int i4 = i; i4 < width; i4 += 8) {
                            int pixel = bitmap.getPixel(i4, i3);
                            j += (long) ((((double) ((pixel >> 16) & 255)) * 0.2126d) + (((double) ((pixel >> 8) & 255)) * 0.7152d) + (((double) (pixel & 255)) * 0.0722d));
                            i2 += WINDOWING_MODE_FULLSCREEN;
                        }
                    }
                    if (i2 == 0) {
                        return;
                    }
                    double d = (j / ((double) i2)) / 255.0d;
                    boolean z2 = d > GLASS_DARK_LUMINANCE;
                    if (z2 != this.glassDark) {
                        this.glassDark = z2;
                        log("Glass theme: " + (z2 ? "dark" : "light") + " (wallpaper luminance " + ((int) (d * 100.0d)) + "%)");
                    }
                }
            }
        } catch (Throwable th) {
            log("Glass theme sampling failed", th);
        }
    }

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Settings.ACTION_LOG);
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
        intentFilter.addAction("android.intent.action.ACTION_POWER_CONNECTED");
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
