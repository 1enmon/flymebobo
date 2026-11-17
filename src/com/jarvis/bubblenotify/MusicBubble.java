package com.jarvis.bubblenotify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class MusicBubble implements BubbleFeature {
    private static final int COLOR_ACCENT = -13847650;
    private static final int COLOR_HANDLE_BG = -1073741825;
    private static final int COLOR_HANDLE_BORDER = 687865856;
    private static final int COLOR_HANDLE_TEXT = -14736603;
    private static final int COLOR_ICON_DARK = -14736603;
    private static final int COLOR_PILL_BG = -14736603;
    private static final int COLOR_CARD_OVERLAY = 0x99FFFFFF;
    private static final int MINI_WIDTH_DP = 200; // fixed74: length aligned with ordinary notification capsules (200dp); reverses fixed29's half-length player capsule (user 2026-11-17 21:33 "长度对齐").
    private static final int COLOR_PILL_BORDER = 1090519039;
    private static final int COLOR_TEXT_DIM = -8748153;
    private static final int COLOR_TEXT_MAIN = -15065826;
    private static final int COLOR_TEXT_SUB = -11905960;
    private static final int COLOR_TRACK = 520093696;
    private static final long DISC_ROTATION_MS = 5500;
    private static final int LEVEL_DOCK = 3;
    private static final int LEVEL_MINI = 2;
    private static final int LEVEL_NONE = 0;
    private static final int LEVEL_PLAYER = 1;
    private static final long PROGRESS_TICK_MS = 1000;
    // fixed65: user 2026-11-17 20:12 — the player card and capsule
    // also auto-shrink / auto-dock after 3s without a tap, like the
    // ordinary notifications. (The call bubble keeps the shared
    // timers.) This overrides the old "music capsule never auto-docks"
    // rule — the auto-dock marks it docked, so only a real song change
    // pops it back out, exactly like a manual dock.
    private static final long PLAYER_IDLE_MS = 3000;
    private static final long TAP_IGNORE_AFTER_SWIPE_MS = 350;
    private Bitmap albumBgBitmap;
    private Bitmap albumBgSource;
    // fixed64: rounded copy of the blurred album background, cached
    // per source so the player card's border does not re-allocate a
    // bitmap on every content refresh.
    private Bitmap albumBorderBitmap;
    private Bitmap albumBorderSource;
    private TextView artistView;
    private View currentView;
    private ObjectAnimator discAnimator;
    private DiscView discView;
    private DiscView dockDiscView;
    private View dockView;
    private final Handler handler;
    private final BubbleHost host;
    private TextView miniTextView;
    // fixed29: second line of the mini capsule — scrolling "title - artist"
    // (marquee). Line 1 (miniTextView) is only the playback status
    // ("正在播放" / "已暂停"); the song info lives here, below it.
    private TextView miniSubTextView;
    private LinearLayout miniView;
    private Bitmap musicAlbum;
    private String musicArtist;
    private PendingIntent musicIntent;
    private String musicLyric;
    // fixed31: network LRC removed with the fixed18 lyric source (see
    // updateLyricView). lyricView shows musicLyric, the notification's
    // current lyric line.
    private TextView lyricView;
    private String musicPackage;
    private String musicTitle;
    private IconView playBtn;
    private DiscView playerDiscView;
    private View playerView;
    private FrameLayout seekArea;
    private View seekFill;
    private TextView timeCurrentView;
    private TextView timeTotalView;
    private TextView titleView;
    private boolean disabled = false;
    private boolean destroyed = false;
    private boolean musicActive = false;
    private boolean musicPlaying = false;
    private boolean userDismissed = false;
    // fixed8: sticky manual dock. Once the user docks the player by hand,
    // only a real song change pops it back out — resume, lyric updates and
    // re-shows must not. Cleared when a song change pops it, when the user
    // taps the dock, or when the user stops playback.
    private boolean userDocked = false;

    // fixed109: player pin — the player is a regular notification now
    // (unified dock), so it gets the same balloon pin as notifications:
    // white balloon = unpinned, blue balloon = pinned. Pinned = stays
    // on screen, no auto-shrink / auto-dock. Calls get no pin.
    private boolean playerPinned = false;
    private static final String PIN_BALLOON_ON_B64 = "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";
    private static final String PIN_BALLOON_OFF_B64 = "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";
    private Bitmap pinBitmapOn;
    private Bitmap pinBitmapOff;
    private android.widget.ImageView pinView;

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

    private android.widget.ImageView buildPinView(final int sizeDp) {
        android.widget.ImageView iv = new android.widget.ImageView(this.host.getApp());
        iv.setImageBitmap(pinBitmap(this.playerPinned));
        iv.setClickable(true);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    MusicBubble.this.togglePlayerPin();
                } catch (Throwable th) {
                    MusicBubble.this.log("player pin click failed", th);
                }
            }
        });
        this.pinView = iv;
        return iv;
    }

    public boolean isPlayerPinned() {
        return this.playerPinned;
    }

    private void togglePlayerPin() {
        this.playerPinned = !this.playerPinned;
        if (this.pinView != null) {
            try {
                this.pinView.setImageBitmap(pinBitmap(this.playerPinned));
            } catch (Throwable ignored) {
            }
        }
        // fixed130: switch the capsule line at once (lyric <-> title -
        // artist) instead of waiting for the next music broadcast.
        refreshLevelContent();
        scheduleIdle();
        try {
            if (this.host != null) {
                this.host.repositionPile();
            }
        } catch (Throwable ignored) {
        }
    }
    // fixed78: whether the currently open card was opened while our own
    // capsule held the focus (single pop) — false when opened from
    // expand-all. See NotifBubble.cardFromFocus.
    private boolean cardFromFocus = false;
    private long durationMs = -1;
    private long positionBaseMs = 0;
    private long positionBaseTime = 0;
    // fixed62: last position carried by a MUSIC broadcast, to tell a
    // stale repeat (same value again) from real progress or a seek.
    private long lastBroadcastPositionMs = -1;
    private int level = 0;
    private long lastSwipeUpTime = 0;
    private boolean seeking = false;
    private float seekPreviewFraction = -1.0f;
    private final Runnable idleRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.MusicBubble.1
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (MusicBubble.this.destroyed || MusicBubble.this.disabled || !MusicBubble.this.screenOn()) {
                    return;
                }
                // fixed109: pinned player stays — no auto-shrink / dock.
                if (MusicBubble.this.playerPinned) {
                    return;
                }
                if (MusicBubble.this.level == 1) {
                    MusicBubble.this.showLevel(2);
                } else if (MusicBubble.this.level == 2) {
                    // fixed74: while another bubble holds the focus, a
                    // hidden capsule must not dock out from under it.
                    if (MusicBubble.this.host.isFocusActive() && !MusicBubble.this.host.isFocusView(MusicBubble.this.currentView)) {
                        MusicBubble.this.postDelayed(MusicBubble.this.idleRunnable, PLAYER_IDLE_MS);
                        return;
                    }
                    // fixed65: the capsule auto-docks after 3s without
                    // a tap. Mark it docked so only a real song change
                    // pops it back out (same gate as a manual dock) —
                    // otherwise the next lyric update would re-pop it.
                    MusicBubble.this.userDocked = true;
                    MusicBubble.this.showLevel(3);
                }
            } catch (Throwable th) {
                MusicBubble.this.log("Music idle timer failed", th);
            }
        }
    };
    private final Runnable tickRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.MusicBubble.2
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (!MusicBubble.this.destroyed && !MusicBubble.this.disabled && MusicBubble.this.musicActive && MusicBubble.this.musicPlaying) {
                    if (MusicBubble.this.screenOn() && MusicBubble.this.level == 1) {
                        MusicBubble.this.updateProgressUi();
                    }
                    // fixed56: always re-post while playing — the old code
                    // only re-posted when every condition held, so one
                    // transient miss (screen flicker, brief pause flag,
                    // level change) killed the tick chain forever and the
                    // progress bar froze until the next music broadcast.
                    MusicBubble.this.postDelayed(MusicBubble.this.tickRunnable, MusicBubble.PROGRESS_TICK_MS);
                }
            } catch (Throwable th) {
                MusicBubble.this.log("Music tick failed", th);
            }
        }
    };
    private Runnable inactiveHideRunnable;
    private final View.OnTouchListener seekTouchListener = new View.OnTouchListener() { // from class: com.jarvis.bubblenotify.MusicBubble.3
        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int width;
            try {
                if (MusicBubble.this.durationMs <= 0 || (width = view.getWidth()) <= 0) {
                    return false;
                }
                switch (motionEvent.getActionMasked()) {
                    case 0:
                    case 2:
                        MusicBubble.this.seeking = true;
                        MusicBubble.this.seekPreviewFraction = MusicBubble.clamp(motionEvent.getX() / width, 0.0f, 1.0f);
                        MusicBubble.this.updateProgressUi();
                        return true;
                    case 1:
                        MusicBubble.this.seeking = false;
                        long jRound = Math.round(MusicBubble.clamp(motionEvent.getX() / width, 0.0f, 1.0f) * MusicBubble.this.durationMs);
                        MusicBubble.this.seekPreviewFraction = -1.0f;
                        MusicBubble.this.host.sendMusicSeek(jRound);
                        MusicBubble.this.positionBaseMs = jRound;
                        MusicBubble.this.positionBaseTime = SystemClock.elapsedRealtime();
                        MusicBubble.this.updateProgressUi();
                        MusicBubble.this.resetIdle();
                        return true;
                    case 3:
                        MusicBubble.this.seeking = false;
                        MusicBubble.this.seekPreviewFraction = -1.0f;
                        MusicBubble.this.updateProgressUi();
                        return true;
                    default:
                        return false;
                }
            } catch (Throwable th) {
                MusicBubble.this.log("Music seek failed", th);
                return false;
            }
        }
    };

    public MusicBubble(BubbleHost bubbleHost) {
        this.host = bubbleHost;
        this.handler = bubbleHost != null ? bubbleHost.getHandler() : null;
    }

    public void onMusicBroadcast(Intent intent) {
        Bitmap bitmap;
        PendingIntent pendingIntent;
        try {
            if (!this.destroyed && intent != null && this.host != null) {
                if (!this.host.isEnabled()) {
                    hideAll();
                    return;
                }
                if (this.disabled) {
                    this.userDismissed = false;
                }
                this.disabled = false;
                if (!intent.getBooleanExtra(Settings.EXTRA_MUSIC_ACTIVE, true)) {
                    scheduleInactiveHide();
                    return;
                }
                cancelInactiveHide();
                boolean booleanExtra = intent.getBooleanExtra(Settings.EXTRA_MUSIC_PLAYING, false);
                String stringExtra = intent.getStringExtra(Settings.EXTRA_MUSIC_TITLE);
                String stringExtra2 = intent.getStringExtra(Settings.EXTRA_MUSIC_ARTIST);
                String lyricExtra = intent.getStringExtra(Settings.EXTRA_MUSIC_LYRIC);
                try {
                    bitmap = (Bitmap) intent.getParcelableExtra(Settings.EXTRA_MUSIC_ALBUM);
                } catch (Throwable th) {
                    log("Music album extra failed", th);
                    bitmap = null;
                }
                long longExtra = intent.getLongExtra(Settings.EXTRA_MUSIC_DURATION, -1L);
                long longExtra2 = intent.getLongExtra(Settings.EXTRA_MUSIC_POSITION, -1L);
                String stringExtra3 = intent.getStringExtra(Settings.EXTRA_PACKAGE);
                try {
                    pendingIntent = (PendingIntent) intent.getParcelableExtra(Settings.EXTRA_INTENT);
                } catch (Throwable th2) {
                    log("Music intent extra failed", th2);
                    pendingIntent = null;
                }
                // fixed17: a credits/lyric line arriving as the title is not a
                // song change — ignore it so lyric refreshes don't pop the
                // docked player back out.
                boolean titleIsCredit = isCreditOrLyricTitle(stringExtra);
                boolean titleChanged = !titleIsCredit && !TextUtils.isEmpty(stringExtra) && !equal(stringExtra, this.musicTitle);
                boolean artistChanged = !TextUtils.isEmpty(stringExtra2) && !equal(stringExtra2, this.musicArtist);
                boolean z = (!this.musicActive && TextUtils.isEmpty(this.musicTitle)) || titleChanged || artistChanged;
                boolean z2 = booleanExtra && !this.musicPlaying && this.musicActive;
                // fixed62: stale broadcast positions freeze the bar.
                // Some players only move PlaybackState.getPosition() on
                // state changes (and report a lastUpdateTime of 0, so
                // the fixed56 correction is skipped), so every lyric
                // re-post carried the same old position and each one
                // reset the local clock back to e.g. 0:54. A broadcast
                // position identical to the previous broadcast's is a
                // stale repeat — ignore it and keep the locally
                // interpolated clock. A different value is real progress
                // or a seek: adopt it. Song changes and play-state flips
                // always adopt.
                if (longExtra2 >= 0) {
                    if (z || booleanExtra != this.musicPlaying || longExtra2 != this.lastBroadcastPositionMs) {
                        this.positionBaseMs = longExtra2;
                        this.positionBaseTime = SystemClock.elapsedRealtime();
                    }
                    this.lastBroadcastPositionMs = longExtra2;
                } else if (booleanExtra != this.musicPlaying) {
                    this.positionBaseMs = currentPositionMs();
                    this.positionBaseTime = SystemClock.elapsedRealtime();
                }
                if (z && longExtra2 < 0) {
                    this.positionBaseMs = 0L;
                    this.positionBaseTime = SystemClock.elapsedRealtime();
                }
                if (longExtra > 0) {
                    this.durationMs = longExtra;
                } else if (z) {
                    this.durationMs = -1L;
                }
                this.musicActive = true;
                this.musicPlaying = booleanExtra;
                if (!TextUtils.isEmpty(stringExtra) && !titleIsCredit) {
                    this.musicTitle = stringExtra;
                }
                if (!TextUtils.isEmpty(stringExtra2)) {
                    this.musicArtist = stringExtra2;
                }
                if (z) {
                    this.musicLyric = lyricExtra;
                } else if (lyricExtra != null && !lyricExtra.isEmpty()) {
                    this.musicLyric = lyricExtra;
                }
                if (!TextUtils.isEmpty(stringExtra3)) {
                    this.musicPackage = stringExtra3;
                }
                if (pendingIntent != null) {
                    this.musicIntent = pendingIntent;
                } else if (z) {
                    this.musicIntent = null;
                }
                if (bitmap != null) {
                    this.musicAlbum = bitmap;
                } else if (z) {
                    this.musicAlbum = null;
                }
                if (z || z2) {
                    this.userDismissed = false;
                }
                if (z || z2 || (this.level == 0 && !this.userDismissed)) {
                    if (this.level == LEVEL_DOCK || this.userDocked) {
                        // fixed8: manually docked — only a real song change pops it
                        // out. Resume (z2), lyric updates and re-shows just refresh.
                        if (z) {
                            this.userDocked = false;
                            showLevel(2);
                            // fixed74: a song-change pop takes the focus
                            // like any fresh capsule pop.
                            // fixed78: never from an expanded card.
                            if (this.currentView != null && !this.host.isFocusCard()) {
                                this.host.setFocusView(this.currentView);
                            }
                        } else if (this.level != 0) {
                            refreshLevelContent();
                            refreshTickers();
                        }
                    } else if (this.level != 1) {
                        showLevel(2);
                    } else {
                        refreshLevelContent();
                        refreshTickers();
                    }
                } else if (this.level != 0) {
                    refreshLevelContent();
                    refreshTickers();
                }
                log("Music: playing=" + booleanExtra + " title=" + stringExtra);
            }
        } catch (Throwable th3) {
            log("onMusicBroadcast failed", th3);
        }
    }

    public void onFeatureDisabled() {
        try {
            this.disabled = true;
            hideAll();
            log("Music feature disabled, bubble hidden.");
        } catch (Throwable th) {
            log("onFeatureDisabled failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onScreenChanged(boolean z) {
        try {
            if (this.destroyed) {
                return;
            }
            if (!z) {
                cancelIdle();
                stopTick();
                updateDiscRotation();
            } else if (this.level != 0 && !this.disabled && this.musicActive) {
                scheduleIdle();
                refreshTickers();
            }
        } catch (Throwable th) {
            log("Music onScreenChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onConfigurationChanged() {
        try {
            if (!this.destroyed && !this.disabled && this.level == 1 && this.currentView != null) {
                removeCurrentView();
                this.level = 0;
                showLevel(1);
            }
        } catch (Throwable th) {
            log("Music onConfigurationChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onDestroy() {
        try {
            cancelInactiveHide();
        } catch (Throwable th) {
        }
        try {
            hideAll();
        } catch (Throwable th) {
        }
        this.destroyed = true;
        this.musicAlbum = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showLevel(int i) {
        View view;
        if (this.destroyed || this.disabled || this.host == null) {
            return;
        }
        if (i == this.level && this.currentView != null) {
            refreshLevelContent();
            return;
        }
        int prevLevel = this.level;
        // fixed78: record before removeCurrentView() clears the focus.
        if (i == 1) {
            this.cardFromFocus = this.host.isFocusView(this.currentView);
        }
        removeCurrentView();
        this.level = i;
        try {
            switch (i) {
                case 1:
                    view = buildPlayerView();
                    break;
                case 2:
                    view = buildMiniView();
                    break;
                case 3:
                    view = buildDockView();
                    break;
                default:
                    this.level = 0;
                    return;
            }
            this.currentView = view;
            try {
                if (i == 1) {
                    // fixed73: the expanded player card goes to the very
                    // bottom of the integrated dock.
                    this.host.addDockViewAtBottom(view);
                } else if (i == 3) {
                    this.host.addDockedIcon(view, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!MusicBubble.this.tapAllowed()) {
                                    return;
                                }
                                // fixed74: tapping any docked icon expands
                                // ALL capsules, not just the player.
                                MusicBubble.this.host.expandAllToMini();
                            } catch (Throwable th) {
                                MusicBubble.this.log("Dock tap failed", th);
                            }
                        }
                    });
                } else {
                    // fixed73: integrated dock — the mini capsule just
                    // appends at the end (addDockView).
                    this.host.addDockView(view);
                }
                this.host.updateWindowSize();
                // fixed74: unified focus — a card always takes the focus;
                // a capsule takes it when it pops fresh (from hidden) or
                // shrinks back from a card, but not when it comes out of
                // the dock (expand-all shows everything).
                if (i == 1) {
                    this.host.setFocusView(view);
                } else if (i == 2 && (prevLevel == 0 || prevLevel == 1)) {
                    // fixed78: shrinking back from a card opened from
                    // expand-all restores the pre-card capsules
                    // (removeCurrentView already cleared the focus);
                    // otherwise the capsule takes the focus. A fresh pop
                    // never hides an expanded card.
                    boolean restoreAll = prevLevel == 1 && !this.cardFromFocus;
                    if (!restoreAll && !this.host.isFocusCard()) {
                        this.host.setFocusView(view);
                    }
                }
            } catch (Throwable th) {
                log("Music addDockView failed", th);
            }
            // fixed80: the player card pins its bottom edge to the
            // user's offset like a notification card (was 14dp); any
            // other level drops the lift.
            this.host.setCardOpen(i == 1);
            if (i == 3) {
                // fixed68: the docked disc must not scale in (user
                // 2026-11-17 20:45: docked icons shrink in from the
                // left when things shrink) — animateIn scales 0.85->1
                // around the bottom-right pivot, i.e. it grows toward
                // the top-left. It just slides vertically up into the
                // pile, like a newly docking notification icon.
                view.animate().cancel();
                view.setTranslationY(dp(16));
                view.animate().translationY(0.0f).setDuration(200L).start();
                // fixed69: the pile closes as one unit — when the player
                // docks, notification capsules dock with it. Guarded
                // inside dockNotifsTogether (only level-2 notifications
                // move), so this cannot loop back.
                this.host.dockNotifsTogether();
            } else {
                animateIn(view);
            }
            scheduleIdle();
            refreshTickers();
        } catch (Throwable th2) {
            log("Music build level " + i + " failed", th2);
            this.level = 0;
        }
    }

    // fixed69: the pile opens as one unit — tapping another docked icon
    // pulls the docked player out to its capsule too. Same as tapping
    // the disc itself (userDocked cleared, showLevel(2)); no-op unless
    // actually docked.
    public void expandFromDock() {
        if (this.destroyed || this.disabled || this.host == null || this.level != LEVEL_DOCK) {
            return;
        }
        this.userDocked = false;
        showLevel(LEVEL_MINI);
    }

    // fixed69: the pile closes as one unit — when the notifications dock,
    // a player capsule docks with them. Same as the 3s auto-dock
    // (userDocked set, showLevel(3)); no-op unless a capsule is out.
    public void dockFromMini() {
        if (this.destroyed || this.disabled || this.host == null || this.level != LEVEL_MINI) {
            return;
        }
        // fixed109: a pinned player is never dragged to the dock by
        // notifications docking.
        if (this.playerPinned) {
            return;
        }
        this.userDocked = true;
        showLevel(LEVEL_DOCK);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void swipeToLevel(int i) {
        this.lastSwipeUpTime = SystemClock.uptimeMillis();
        // fixed8: swiping to the dock is a manual dock — remember it so only
        // a song change pops the player back out.
        this.userDocked = (i == LEVEL_DOCK);
        showLevel(i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean tapAllowed() {
        return SystemClock.uptimeMillis() - this.lastSwipeUpTime >= TAP_IGNORE_AFTER_SWIPE_MS;
    }

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

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissMusic() {
        // fixed5: left swipe now stops playback outright, not just hides the
        // bubble. Previously dismiss only hid the bubble while the music kept
        // playing, so lyric/notification re-posts kept re-popping it.
        // Stopping the session kills the source of those re-posts.
        try {
            if (this.musicActive && this.host != null) {
                this.host.sendMusicControl(Settings.CONTROL_STOP);
            }
        } catch (Throwable th) {
            log("Music stop on swipe failed", th);
        }
        this.musicPlaying = false;
        this.userDismissed = true;
        this.userDocked = false;
        hideAll();
        log("Music stopped by user swipe.");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openMusicApp() {
        Intent launchIntentForPackage;
        try {
            if (this.musicIntent != null) {
                this.host.openPendingIntent(this.musicIntent);
            } else if (!TextUtils.isEmpty(this.musicPackage) && (launchIntentForPackage = this.host.getApp().getPackageManager().getLaunchIntentForPackage(this.musicPackage)) != null) {
                this.host.openIntent(launchIntentForPackage);
            } else {
                log("openMusicApp: no music intent or package.");
            }
        } catch (Throwable th) {
            log("openMusicApp failed", th);
        }
    }

    private void hideAll() {
        cancelIdle();
        stopTick();
        removeCurrentView();
        this.level = 0;
        // fixed80: a card that ends must not leave the dock lifted.
        this.host.setCardOpen(false);
        try {
            this.host.updateWindowSize();
        } catch (Throwable th) {
            log("Music updateWindowSize failed", th);
        }
    }

    // fixed22: BubbleManager uses this to keep the player on top — a view
    // is a music view iff it is the player's currently shown view.
    boolean ownsView(View v) {
        return v != null && v == this.currentView;
    }

    private void removeCurrentView() {
        stopDiscRotation();
        if (this.currentView != null) {
            try {
                this.currentView.animate().cancel();
            } catch (Throwable th) {
            }
            try {
                if (this.level == 3) {
                    this.host.removeDockedIcon(this.currentView);
                } else {
                    this.host.removeDockView(this.currentView);
                }
            } catch (Throwable th2) {
                log("Music removeDockView failed", th2);
            }
            this.currentView = null;
        }
        this.playerView = null;
        this.playerDiscView = null;
        this.titleView = null;
        this.artistView = null;
        this.timeCurrentView = null;
        this.timeTotalView = null;
        this.seekArea = null;
        this.seekFill = null;
        this.playBtn = null;
        this.seeking = false;
        this.seekPreviewFraction = -1.0f;
        this.miniView = null;
        this.discView = null;
        this.miniTextView = null;
        this.dockView = null;
        this.dockDiscView = null;
    }

    // fixed56: uniform expand/shrink in place (see NotifBubble
    // animateIn). The old entrance slid in from translationX 24dp,
    // which read as bouncing in from far away; now the view scales
    // uniformly around its bottom-right corner, no translation.
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
                        // fixed62: wait for a real size, like the
                        // notification capsule does (fixed58). Animating
                        // the scale with the default top-left pivot is
                        // what made the player capsule appear to grow
                        // from the top-left corner.
                        if ((view.getWidth() == 0 || view.getHeight() == 0) && pivotTries[0] < 5) {
                            pivotTries[0]++;
                            view.post(this);
                            return;
                        }
                        if (view.getWidth() > 0 && view.getHeight() > 0) {
                            view.setPivotX((float) view.getWidth());
                            view.setPivotY((float) view.getHeight());
                            view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200L).start();
                        } else {
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                        }
                    } catch (Throwable th) {
                        log("Music animateIn failed", th);
                    }
                }
            });
        } catch (Throwable th) {
            log("Music animateIn failed", th);
        }
    }

    private void scheduleIdle() {
        cancelIdle();
        try {
            // fixed109: pinned player — no idle timer at all.
            if (this.playerPinned) {
                return;
            }
            if (!this.destroyed && !this.disabled && this.musicActive && screenOn()) {
                // fixed65: the idle timer now covers the capsule too
                // (level 2 auto-docks), and both levels use the 3s
                // player idle time instead of the shared 5s.
                if (this.level == 1 || this.level == 2) {
                    postDelayed(this.idleRunnable, PLAYER_IDLE_MS);
                }
            }
        } catch (Throwable th) {
            log("scheduleIdle failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cancelIdle() {
        removeCallbacks(this.idleRunnable);
    }

    /**
     * Delays hiding the bubble after music goes inactive. Music apps often
     * cancel and re-post their notification when the lyric line changes; hiding
     * immediately makes the next broadcast look like a song change
     * (musicActive was just cleared) and pops the docked bubble back out.
     * Waiting 1.5s lets the re-post arrive first, so lyric-only updates stay
     * a silent refresh. A real stop still hides the bubble, just 1.5s later.
     */
    private void scheduleInactiveHide() {
        try {
            cancelInactiveHide();
            if (this.inactiveHideRunnable == null) {
                this.inactiveHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            MusicBubble.this.musicActive = false;
                            MusicBubble.this.musicPlaying = false;
                            MusicBubble.this.hideAll();
                            log("Music inactive, bubble hidden.");
                        } catch (Throwable th) {
                            log("Inactive hide failed", th);
                        }
                    }
                };
            }
            postDelayed(this.inactiveHideRunnable, 2500L);
        } catch (Throwable th) {
            log("scheduleInactiveHide failed", th);
        }
    }

    private void cancelInactiveHide() {
        try {
            if (this.inactiveHideRunnable != null) {
                removeCallbacks(this.inactiveHideRunnable);
            }
        } catch (Throwable th) {
            log("cancelInactiveHide failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetIdle() {
        scheduleIdle();
    }

    private void startTick() {
        stopTick();
        if (this.destroyed || this.disabled || !this.musicActive || !this.musicPlaying || !screenOn() || this.level != 1) {
            return;
        }
        postDelayed(this.tickRunnable, PROGRESS_TICK_MS);
    }

    private void stopTick() {
        removeCallbacks(this.tickRunnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshTickers() {
        if (this.level == 1 && this.musicPlaying && this.musicActive && screenOn() && !this.disabled) {
            startTick();
        } else {
            stopTick();
        }
        updateDiscRotation();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public long currentPositionMs() {
        long jElapsedRealtime = this.positionBaseMs;
        if (this.musicPlaying) {
            jElapsedRealtime += SystemClock.elapsedRealtime() - this.positionBaseTime;
        }
        if (this.durationMs > 0 && jElapsedRealtime > this.durationMs) {
            jElapsedRealtime = this.durationMs;
        }
        if (jElapsedRealtime < 0) {
            return 0L;
        }
        return jElapsedRealtime;
    }

    /* JADX INFO: Access modifiers changed from: private */
    // fixed31: lyrics are back to the original notification source —
    // the lyric line in the card is the notification's current line
    // (musicLyric, from EXTRA_MUSIC_LYRIC). Network LRC (fixed18) is off:
    // the LRCLIB lookup returned nothing for songs the library does not
    // carry, so the card showed no lyric at all. The tick still refreshes
    // the line while the card is open, so it follows each re-post.
    private void updateLyricView() {
        try {
            if (this.lyricView == null) {
                return;
            }
            String line = this.musicLyric != null ? this.musicLyric : "";
            if (!line.equals(this.lyricView.getText().toString())) {
                this.lyricView.setText(line);
            }
        } catch (Throwable th) {
            log("updateLyricView failed", th);
        }
    }

    public void updateProgressUi() {
        float fMin;
        try {
            if (this.playerView == null) {
                return;
            }
            long j = this.durationMs;
            long jCurrentPositionMs = currentPositionMs();
            if (this.seeking && this.seekPreviewFraction >= 0.0f) {
                fMin = this.seekPreviewFraction;
                if (j > 0) {
                    jCurrentPositionMs = Math.round(j * fMin);
                }
            } else {
                // fixed77: long/long division truncated to 0 for any
                // position short of the duration, so the fill width
                // round(area * 0) stayed 0 forever while the clock
                // (raw ms) kept advancing. Float division.
                fMin = j > 0 ? Math.min(1.0f, ((float) jCurrentPositionMs) / j) : 0.0f;
            }
            if (this.timeCurrentView != null) {
                this.timeCurrentView.setText(formatTime(jCurrentPositionMs));
            }
            updateLyricView();
            if (this.timeTotalView != null) {
                this.timeTotalView.setText(j > 0 ? formatTime(j) : "--:--");
            }
            if (this.seekFill != null && this.seekArea != null) {
                int width = this.seekArea.getWidth();
                if (width > 0) {
                    ViewGroup.LayoutParams layoutParams = this.seekFill.getLayoutParams();
                    int iRound = Math.round(width * fMin);
                    if (layoutParams.width != iRound) {
                        layoutParams.width = iRound;
                        this.seekFill.setLayoutParams(layoutParams);
                    }
                    return;
                }
                this.seekArea.post(new Runnable() { // from class: com.jarvis.bubblenotify.MusicBubble.4
                    @Override // java.lang.Runnable
                    public void run() {
                        MusicBubble.this.updateProgressUi();
                    }
                });
            }
        } catch (Throwable th) {
            log("updateProgressUi failed", th);
        }
    }

    private static String formatTime(long j) {
        if (j < 0) {
            j = 0;
        }
        long j2 = j / PROGRESS_TICK_MS;
        long j3 = j2 / 60;
        long j4 = j2 % 60;
        return j3 + ":" + (j4 < 10 ? "0" + j4 : String.valueOf(j4));
    }

    private void updateDiscRotation() {
        try {
            View viewCurrentDiscView = currentDiscView();
            boolean z = viewCurrentDiscView != null && this.musicActive && this.musicPlaying && screenOn() && !this.destroyed && !this.disabled;
            ObjectAnimator objectAnimator = this.discAnimator;
            if (z) {
                if (objectAnimator == null) {
                    this.discAnimator = ObjectAnimator.ofFloat(viewCurrentDiscView, "rotation", viewCurrentDiscView.getRotation(), viewCurrentDiscView.getRotation() + 360.0f);
                    this.discAnimator.setDuration(DISC_ROTATION_MS);
                    this.discAnimator.setInterpolator(new LinearInterpolator());
                    this.discAnimator.setRepeatCount(-1);
                    this.discAnimator.start();
                    return;
                }
                boolean zIsPaused = this.discAnimator.isPaused();
                ObjectAnimator objectAnimator2 = this.discAnimator;
                if (zIsPaused) {
                    objectAnimator2.resume();
                    return;
                } else {
                    if (!objectAnimator2.isRunning()) {
                        this.discAnimator.start();
                        return;
                    }
                    return;
                }
            }
            if (objectAnimator != null && this.discAnimator.isRunning() && !this.discAnimator.isPaused()) {
                this.discAnimator.pause();
            }
        } catch (Throwable th) {
            log("updateDiscRotation failed", th);
        }
    }

    private View currentDiscView() {
        if (this.level == 1) {
            return this.playerDiscView;
        }
        if (this.level == 2) {
            return this.discView;
        }
        if (this.level == 3) {
            return this.dockDiscView;
        }
        return null;
    }

    private void stopDiscRotation() {
        try {
            if (this.discAnimator != null) {
                this.discAnimator.cancel();
                this.discAnimator = null;
            }
        } catch (Throwable th) {
            log("stopDiscRotation failed", th);
        }
    }

    private View buildPlayerView() {
        Application app = this.host.getApp();
        LinearLayout linearLayout = new LinearLayout(app);
        linearLayout.setOrientation(1);
        linearLayout.setBackground(playerBackground());
        linearLayout.setPadding(dp(13), dp(12), dp(13), dp(11));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(cardWidthDp()), -2));
        linearLayout.setClickable(true);
        linearLayout.setOnTouchListener(new SwipeTapListener(this.host, new SwipeTapListener.Callback() { // from class: com.jarvis.bubblenotify.MusicBubble.5
            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onTap() {
                if (!MusicBubble.this.tapAllowed()) {
                    return;
                }
                MusicBubble.this.resetIdle();
                MusicBubble.this.openMusicApp();
                // fixed62: like the notification card (fixed59), opening
                // the app in the small window shrinks the player card
                // back to its capsule instead of leaving it expanded.
                MusicBubble.this.showLevel(2);
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onSwipeRight() {
                MusicBubble.this.swipeToLevel(2);
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onSwipeLeft() {
                MusicBubble.this.dismissMusic();
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onDragStart() {
                MusicBubble.this.setSwipeMode(true);
                MusicBubble.this.cancelIdle();
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onDragEnd() {
                MusicBubble.this.setSwipeMode(false);
                // fixed65: a drag cancelled the idle timer — restart it
                // so the card/capsule still auto-shrinks/docks 3s after
                // a swipe that springs back without changing level.
                MusicBubble.this.scheduleIdle();
            }
        }));
        LinearLayout linearLayout2 = new LinearLayout(app);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        this.playerDiscView = new DiscView(app);
        this.playerDiscView.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        if (this.musicAlbum != null) {
            this.playerDiscView.setAlbum(this.musicAlbum);
        }
        linearLayout2.addView(this.playerDiscView);
        LinearLayout linearLayout3 = new LinearLayout(app);
        linearLayout3.setOrientation(1);
        linearLayout3.setGravity(16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(10);
        layoutParams.rightMargin = dp(6);
        linearLayout3.setLayoutParams(layoutParams);
        this.titleView = new TextView(app);
        this.titleView.setTextColor(playerTextMain());
        this.titleView.setTextSize(2, 14.5f);
        this.titleView.setTypeface(Typeface.DEFAULT_BOLD);
        this.titleView.setSingleLine(true);
        this.titleView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(this.titleView);
        this.artistView = new TextView(app);
        this.artistView.setTextColor(playerTextDim());
        this.artistView.setTextSize(2, 12.0f);
        this.artistView.setSingleLine(true);
        this.artistView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(this.artistView);
        linearLayout2.addView(linearLayout3);
        // fixed109: player pin at the card's top-right, left of collapse.
        android.widget.ImageView pinIv2 = buildPinView(28);
        LinearLayout.LayoutParams pinLp2 = new LinearLayout.LayoutParams(dp(28), dp(28));
        pinLp2.leftMargin = dp(4);
        linearLayout2.addView(pinIv2, pinLp2);
        TextView textView = new TextView(app);
        textView.setText("⌄");
        textView.setTextSize(2, 18.0f);
        textView.setTextColor(playerTextSub());
        textView.setGravity(17);
        textView.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        textView.setClickable(true);
        textView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MusicBubble.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    MusicBubble.this.showLevel(2);
                } catch (Throwable th) {
                    MusicBubble.this.log("Collapse failed", th);
                }
            }
        });
        linearLayout2.addView(textView);
        linearLayout.addView(linearLayout2);
        this.seekArea = new FrameLayout(app);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(26));
        layoutParams2.topMargin = dp(10);
        this.seekArea.setLayoutParams(layoutParams2);
        View view = new View(app);
        view.setLayoutParams(new FrameLayout.LayoutParams(-1, dp(5), 16));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(COLOR_TRACK);
        gradientDrawable.setCornerRadius(dp(3));
        view.setBackground(gradientDrawable);
        this.seekArea.addView(view);
        this.seekFill = new View(app);
        this.seekFill.setLayoutParams(new FrameLayout.LayoutParams(0, dp(5), 8388627));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setColor(COLOR_ACCENT);
        gradientDrawable2.setCornerRadius(dp(3));
        this.seekFill.setBackground(gradientDrawable2);
        this.seekArea.addView(this.seekFill);
        this.seekArea.setOnTouchListener(this.seekTouchListener);
        linearLayout.addView(this.seekArea);
        LinearLayout linearLayout4 = new LinearLayout(app);
        linearLayout4.setOrientation(0);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams3.topMargin = dp(2);
        linearLayout4.setLayoutParams(layoutParams3);
        this.timeCurrentView = new TextView(app);
        this.timeCurrentView.setTextColor(playerTextDim());
        this.timeCurrentView.setTextSize(2, 11.0f);
        linearLayout4.addView(this.timeCurrentView);
        View view2 = new View(app);
        view2.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));
        linearLayout4.addView(view2);
        this.timeTotalView = new TextView(app);
        this.timeTotalView.setTextColor(playerTextDim());
        this.timeTotalView.setTextSize(2, 11.0f);
        linearLayout4.addView(this.timeTotalView);
        linearLayout.addView(linearLayout4);
        // fixed31: lyric line — the notification's current lyric line
        // (musicLyric), updated by the tick. Rolls (marquee) as the song
        // plays; the source is back to the original notification lyric.
        this.lyricView = new TextView(app);
        this.lyricView.setTextColor(playerTextMain());
        this.lyricView.setTextSize(2, 13.0f);
        this.lyricView.setSingleLine(true);
        this.lyricView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        this.lyricView.setMarqueeRepeatLimit(-1);
        this.lyricView.setSelected(true);
        this.lyricView.setGravity(17);
        LinearLayout.LayoutParams lyricParams = new LinearLayout.LayoutParams(-1, -2);
        lyricParams.topMargin = dp(6);
        this.lyricView.setLayoutParams(lyricParams);
        linearLayout.addView(this.lyricView);
        updateLyricView();
        LinearLayout linearLayout5 = new LinearLayout(app);
        linearLayout5.setOrientation(0);
        linearLayout5.setGravity(16);
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setColor(COLOR_PILL_BG);
        gradientDrawable3.setCornerRadius(dp(999));
        gradientDrawable3.setStroke(dp(1), COLOR_PILL_BORDER);
        linearLayout5.setBackground(gradientDrawable3);
        linearLayout5.setPadding(dp(6), dp(3), dp(6), dp(3));
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams4.topMargin = dp(8);
        layoutParams4.gravity = 1;
        linearLayout5.setLayoutParams(layoutParams4);
        IconView iconViewMakeControlButton = makeControlButton(44, 24, VectorIcons.PREV);
        iconViewMakeControlButton.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MusicBubble.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view3) {
                try {
                    MusicBubble.this.host.sendMusicControl(Settings.CONTROL_PREV);
                    MusicBubble.this.resetIdle();
                } catch (Throwable th) {
                    MusicBubble.this.log("Music prev failed", th);
                }
            }
        });
        linearLayout5.addView(iconViewMakeControlButton);
        this.playBtn = makeControlButton(52, 26, this.musicPlaying ? VectorIcons.PAUSE : VectorIcons.PLAY);
        LinearLayout.LayoutParams layoutParams5 = (LinearLayout.LayoutParams) this.playBtn.getLayoutParams();
        layoutParams5.leftMargin = dp(10);
        layoutParams5.rightMargin = dp(10);
        this.playBtn.setLayoutParams(layoutParams5);
        this.playBtn.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MusicBubble.8
            @Override // android.view.View.OnClickListener
            public void onClick(View view3) {
                try {
                    boolean z = !MusicBubble.this.musicPlaying;
                    MusicBubble.this.positionBaseMs = MusicBubble.this.currentPositionMs();
                    MusicBubble.this.positionBaseTime = SystemClock.elapsedRealtime();
                    MusicBubble.this.musicPlaying = z;
                    MusicBubble.this.host.sendMusicControl(Settings.CONTROL_TOGGLE);
                    MusicBubble.this.updatePlayerContent();
                    MusicBubble.this.refreshTickers();
                    MusicBubble.this.resetIdle();
                } catch (Throwable th) {
                    MusicBubble.this.log("Music toggle failed", th);
                }
            }
        });
        linearLayout5.addView(this.playBtn);
        IconView iconViewMakeControlButton2 = makeControlButton(44, 24, VectorIcons.NEXT);
        iconViewMakeControlButton2.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MusicBubble.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view3) {
                try {
                    MusicBubble.this.host.sendMusicControl(Settings.CONTROL_NEXT);
                    MusicBubble.this.resetIdle();
                } catch (Throwable th) {
                    MusicBubble.this.log("Music next failed", th);
                }
            }
        });
        linearLayout5.addView(iconViewMakeControlButton2);
        linearLayout.addView(linearLayout5);
        // fixed102: cap the card at the user-set height (0 = auto);
        // the content scrolls inside when capped, so nothing is cut off.
        View cardView = this.host.capCardHeight(linearLayout);
        this.playerView = cardView;
        updatePlayerContent();
        return cardView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updatePlayerContent() {
        try {
            if (this.playerView == null) {
                return;
            }
            if (this.titleView != null) {
                this.titleView.setText((this.musicTitle == null || this.musicTitle.isEmpty()) ? "未知歌曲" : this.musicTitle);
            }
            if (this.artistView != null) {
                this.artistView.setText((this.musicArtist == null || this.musicArtist.isEmpty()) ? "未知歌手" : this.musicArtist);
            }
            if (this.playerDiscView != null) {
                this.playerDiscView.setAlbum(this.musicAlbum);
            }
            if (this.playerView != null) {
                this.playerView.setBackground(playerBackground());
                applyPlayerTextColors();
            }
            if (this.playBtn != null) {
                this.playBtn.setIconXml(this.musicPlaying ? VectorIcons.PAUSE : VectorIcons.PLAY);
            }
            // fixed128: the lyric line must refresh in place too — the
            // old code only set it when the card was rebuilt, so the
            // lyric stayed stuck on the line the card opened with.
            updateLyricView();
            updateProgressUi();
        } catch (Throwable th) {
            log("updatePlayerContent failed", th);
        }
    }

    private LinearLayout buildMiniView() {
        Application app = this.host.getApp();
        LinearLayout linearLayout = new LinearLayout(app);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        try {
            linearLayout.setBackground(this.host.pillBackground());
        } catch (Throwable th) {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(COLOR_HANDLE_BG);
            gradientDrawable.setCornerRadius(dp(999));
            gradientDrawable.setStroke(dp(1), this.host.borderColor());
            linearLayout.setBackground(gradientDrawable);
        }
        linearLayout.setPadding(dp(6), dp(6), dp(10), dp(6));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(capsuleWidthDp()), dp(capsuleHeightDp())));
        linearLayout.setClickable(true);
        linearLayout.setOnTouchListener(new SwipeTapListener(this.host, new SwipeTapListener.Callback() { // from class: com.jarvis.bubblenotify.MusicBubble.10
            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onTap() {
                try {
                    if (!MusicBubble.this.tapAllowed()) {
                        return;
                    }
                    if (!MusicBubble.this.isCardExpand()) {
                        // fixed101: card-expand off — tapping the capsule
                        // opens the music app directly, like the card tap.
                        MusicBubble.this.openMusicApp();
                        MusicBubble.this.showLevel(2);
                        return;
                    }
                    MusicBubble.this.showLevel(1);
                } catch (Throwable th2) {
                    MusicBubble.this.log("Mini tap failed", th2);
                }
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onSwipeRight() {
                MusicBubble.this.swipeToLevel(3);
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onSwipeLeft() {
                MusicBubble.this.dismissMusic();
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onDragStart() {
                MusicBubble.this.setSwipeMode(true);
                MusicBubble.this.cancelIdle();
            }

            @Override // com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.Callback
            public void onDragEnd() {
                MusicBubble.this.setSwipeMode(false);
                // fixed65: a drag cancelled the idle timer — restart it
                // so the card/capsule still auto-shrinks/docks 3s after
                // a swipe that springs back without changing level.
                MusicBubble.this.scheduleIdle();
            }
        }));
        stopDiscRotation();
        this.discView = new DiscView(app);
        // fixed29: disc shrunk 40dp -> 32dp so the two-line text column
        // still has room now that the capsule is only 100dp wide.
        this.discView.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        if (this.musicAlbum != null) {
            this.discView.setAlbum(this.musicAlbum);
        }
        linearLayout.addView(this.discView);
        // fixed29: two-line mini text. Line 1 = playback status only
        // ("正在播放" / "已暂停"); line 2 = scrolling "title - artist"
        // (marquee). The text column is a vertical stack next to the disc.
        LinearLayout textColumn = new LinearLayout(app);
        textColumn.setOrientation(1);
        textColumn.setGravity(16);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        columnParams.leftMargin = dp(6);
        textColumn.setLayoutParams(columnParams);
        this.miniTextView = new TextView(app);
        this.miniTextView.setTextColor(this.host.textMainColor());
        this.miniTextView.setTextSize(2, 11.0f);
        this.miniTextView.setTypeface(Typeface.DEFAULT_BOLD);
        this.miniTextView.setSingleLine(true);
        this.miniTextView.setHorizontallyScrolling(true);
        this.miniTextView.setEllipsize(TextUtils.TruncateAt.END);
        this.miniTextView.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        textColumn.addView(this.miniTextView);
        this.miniSubTextView = new TextView(app);
        this.miniSubTextView.setTextColor(this.host.textMainColor());
        this.miniSubTextView.setTextSize(2, 10.0f);
        this.miniSubTextView.setSingleLine(true);
        this.miniSubTextView.setHorizontallyScrolling(true);
        // MARQUEE + selected = the title/artist scrolls across the capsule.
        this.miniSubTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        this.miniSubTextView.setMarqueeRepeatLimit(-1);
        this.miniSubTextView.setSelected(true);
        this.miniSubTextView.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        textColumn.addView(this.miniSubTextView);
        linearLayout.addView(textColumn);
        // fixed109: player pin at the capsule's right end (same balloon
        // as notifications; the clickable child consumes the tap so the
        // capsule swipe listener never sees it).
        android.widget.ImageView pinIv = buildPinView(22);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        pinLp.leftMargin = dp(6);
        linearLayout.addView(pinIv, pinLp);
        this.miniView = linearLayout;
        updateMiniContent();
        return linearLayout;
    }

    /* JADX INFO: renamed from: com.jarvis.bubblenotify.MusicBubble$12, reason: invalid class name */
    class AnonymousClass12 implements View.OnClickListener {
        AnonymousClass12() {
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            try {
                if (!MusicBubble.this.tapAllowed()) {
                    return;
                }
                // fixed8: user tapped the dock to expand — no longer docked.
                MusicBubble.this.userDocked = false;
                MusicBubble.this.showLevel(2);
            } catch (Throwable th) {
                MusicBubble.this.log("Dock tap failed", th);
            }
        }
    }

    private void updateMiniContent() {
        try {
            if (this.miniView == null) {
                return;
            }
            if (this.discView != null) {
                this.discView.setAlbum(this.musicAlbum);
            }
            if (this.miniTextView != null) {
                // fixed29: line 1 is the playback status ONLY — "正在播放"
                // or "已暂停". No title, no artist, never the lyric.
                this.miniTextView.setText(this.musicPlaying ? "正在播放" : "已暂停");
                this.miniTextView.setEllipsize(TextUtils.TruncateAt.END);
                this.miniTextView.setSelected(false);
            }
            if (this.miniSubTextView != null) {
                // fixed29: line 2, below the status, is the scrolling
                // "title - artist" (marquee). setSelected(true) keeps the
                // marquee running.
                // fixed130: pinned -> line 2 scrolls the current lyric
                // line instead; unpinned -> "title - artist" as before.
                // No lyric yet -> fall back to "title - artist".
                String display;
                if (this.playerPinned && this.musicLyric != null && !this.musicLyric.isEmpty()) {
                    display = this.musicLyric;
                } else {
                    String title = (this.musicTitle == null || this.musicTitle.isEmpty()) ? "未知歌曲" : this.musicTitle;
                    display = title;
                    if (this.musicArtist != null && !this.musicArtist.isEmpty() && !this.musicArtist.equals(title)) {
                        display = title + " - " + this.musicArtist;
                    }
                }
                if (!display.equals(this.miniSubTextView.getText().toString())) {
                    this.miniSubTextView.setText(display);
                }
                this.miniSubTextView.setSelected(true);
            }
            updateDiscRotation();
        } catch (Throwable th) {
            log("updateMiniContent failed", th);
        }
    }

    // fixed66: the fixed66 independent dock is reverted (user 2026-11-17
    // 20:30 "不独立贴边") — the docked disc is a pile icon again: 22dp
    // frame, 40dp disc overflowing START toward the edge (fixed49/52).
    private FrameLayout buildDockView() {
        Application app = this.host.getApp();
        FrameLayout frameLayout = new FrameLayout(app);
        frameLayout.setClipChildren(false);
        this.dockDiscView = new DiscView(app);
        if (this.musicAlbum != null) {
            this.dockDiscView.setAlbum(this.musicAlbum);
        }
        frameLayout.addView(this.dockDiscView, new FrameLayout.LayoutParams(dp(40), dp(40), 8388627));
        FrameLayout frameLayout2 = new FrameLayout(app);
        frameLayout2.setClipChildren(false);
        frameLayout2.addView(frameLayout, new FrameLayout.LayoutParams(dp(22), dp(40)));
        this.dockView = frameLayout2;
        return frameLayout2;
    }

    private void refreshLevelContent() {
        if (this.level == 1) {
            updatePlayerContent();
        } else if (this.level == 2) {
            updateMiniContent();
        } else if (this.level == LEVEL_DOCK) {
            if (this.dockDiscView != null) {
                this.dockDiscView.setAlbum(this.musicAlbum);
            }
        }
    }

    private IconView makeControlButton(int i, int i2, String str) {
        IconView iconView = new IconView(this.host.getApp());
        iconView.setIconXml(str);
        iconView.setIconColor(-1);
        iconView.setIconSizeDp(i2);
        iconView.setClickable(true);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(i), dp(i)));
        return iconView;
    }

    private TextView makeDockHandle(Context context) {
        TextView textView = new TextView(context);
        textView.setText("‹");
        textView.setTextSize(2, 16.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-14736603);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(COLOR_HANDLE_BG);
        gradientDrawable.setCornerRadius(dp(99));
        gradientDrawable.setStroke(dp(1), COLOR_HANDLE_BORDER);
        textView.setBackground(gradientDrawable);
        textView.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(34)));
        return textView;
    }

    private static class ControlButtonView extends View {
        static final int MODE_NEXT = 3;
        static final int MODE_PAUSE = 2;
        static final int MODE_PLAY = 1;
        static final int MODE_PREV = 0;
        private final Paint bgPaint;
        private final Paint iconPaint;
        private final Path iconPath;
        private int mode;

        ControlButtonView(Context context, int i, int i2) {
            super(context);
            this.bgPaint = new Paint(1);
            this.iconPaint = new Paint(1);
            this.iconPath = new Path();
            this.mode = i;
            this.bgPaint.setColor(i2);
            this.iconPaint.setColor(-1);
            this.iconPaint.setStyle(Paint.Style.FILL);
            setClickable(true);
        }

        void setMode(int i) {
            if (this.mode != i) {
                this.mode = i;
                invalidate();
            }
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            float f = width / 2.0f;
            float f2 = height / 2.0f;
            float fMin = Math.min(width, height) / 2.0f;
            canvas.drawCircle(f, f2, fMin, this.bgPaint);
            float f3 = fMin * 0.78f;
            float f4 = 0.72f * f3;
            this.iconPath.reset();
            switch (this.mode) {
                case 0:
                    float f5 = 0.3f * f3;
                    float f6 = f3 * 0.78f;
                    float f7 = f - f6;
                    float f8 = f2 - f4;
                    float f9 = f7 + f5;
                    float f10 = f2 + f4;
                    float f11 = f5 * 0.4f;
                    canvas.drawRoundRect(f7, f8, f9, f10, f11, f11, this.iconPaint);
                    float f12 = f6 + f;
                    this.iconPath.moveTo(f12, f8);
                    this.iconPath.lineTo(f - (f3 * 0.28f), f2);
                    this.iconPath.lineTo(f12, f10);
                    this.iconPath.close();
                    canvas.drawPath(this.iconPath, this.iconPaint);
                    break;
                case 1:
                    float f13 = f - (0.55f * f3);
                    this.iconPath.moveTo(f13, f2 - f4);
                    this.iconPath.lineTo(f + (f3 * 0.75f), f2);
                    this.iconPath.lineTo(f13, f2 + f4);
                    this.iconPath.close();
                    canvas.drawPath(this.iconPath, this.iconPaint);
                    break;
                case 2:
                    float f14 = f3 * 0.22f;
                    float f15 = f3 * 0.32f;
                    float f16 = f14 * 0.45f;
                    float f17 = f - f15;
                    float f18 = f2 - f4;
                    float f19 = f2 + f4;
                    canvas.drawRoundRect(f17 - f14, f18, f17 + f14, f19, f16, f16, this.iconPaint);
                    float f20 = f + f15;
                    canvas.drawRoundRect(f20 - f14, f18, f20 + f14, f19, f16, f16, this.iconPaint);
                    break;
                case 3:
                    float f21 = 0.3f * f3;
                    float f22 = f3 * 0.78f;
                    float f23 = (f + f22) - f21;
                    float f24 = f2 - f4;
                    float f25 = f23 + f21;
                    float f26 = f2 + f4;
                    float f27 = f21 * 0.4f;
                    canvas.drawRoundRect(f23, f24, f25, f26, f27, f27, this.iconPaint);
                    float f28 = f - f22;
                    this.iconPath.moveTo(f28, f24);
                    this.iconPath.lineTo(f + (f3 * 0.28f), f2);
                    this.iconPath.lineTo(f28, f26);
                    this.iconPath.close();
                    canvas.drawPath(this.iconPath, this.iconPaint);
                    break;
            }
        }
    }

    // fixed58: player-card text follows the glass theme — except when an
    // album cover is showing. The blurred cover plus its white overlay
    // is always light, so dark text stays readable there whatever the
    // wallpaper is. The mini capsule has no cover, it always follows
    // the glass theme.
    private int playerTextMain() {
        return this.musicAlbum != null ? COLOR_TEXT_MAIN : this.host.textMainColor();
    }

    private int playerTextDim() {
        return this.musicAlbum != null ? COLOR_TEXT_DIM : this.host.textDimColor();
    }

    private int playerTextSub() {
        return this.musicAlbum != null ? COLOR_TEXT_SUB : this.host.textSubColor();
    }

    // fixed58: re-apply the player-card text colours. The cover can
    // arrive after the card was built on frosted glass (or be cleared),
    // which flips which text colour is readable — updatePlayerContent
    // calls this whenever it re-applies the background.
    private void applyPlayerTextColors() {
        try {
            if (this.titleView != null) {
                this.titleView.setTextColor(playerTextMain());
            }
            if (this.artistView != null) {
                this.artistView.setTextColor(playerTextDim());
            }
            if (this.timeCurrentView != null) {
                this.timeCurrentView.setTextColor(playerTextDim());
            }
            if (this.timeTotalView != null) {
                this.timeTotalView.setTextColor(playerTextDim());
            }
            if (this.lyricView != null) {
                this.lyricView.setTextColor(playerTextMain());
            }
        } catch (Throwable th) {
            log("applyPlayerTextColors failed", th);
        }
    }

    private Drawable playerBackground() {
        Bitmap bitmapBlurredAlbumBackground;
        try {
            if (this.musicAlbum == null || (bitmapBlurredAlbumBackground = blurredAlbumBackground(this.musicAlbum)) == null) {
                return this.host.frostedBackground(20);
            }
            // fixed64: the blurred-cover card had no border at all (a
            // bare BitmapDrawable). Round the blurred bitmap to the
            // card's corner radius (cached per source, like the blur)
            // and layer the theme border over it, so the cover card
            // matches the other cards.
            if (bitmapBlurredAlbumBackground != this.albumBorderSource || this.albumBorderBitmap == null || this.albumBorderBitmap.isRecycled()) {
                Bitmap rounded = Bitmap.createBitmap(bitmapBlurredAlbumBackground.getWidth(), bitmapBlurredAlbumBackground.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(rounded);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setShader(new BitmapShader(bitmapBlurredAlbumBackground, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                float radius = dp(20);
                canvas.drawRoundRect(new RectF(0.0f, 0.0f, rounded.getWidth(), rounded.getHeight()), radius, radius, paint);
                if (this.albumBorderBitmap != null && !this.albumBorderBitmap.isRecycled() && this.albumBorderBitmap != rounded) {
                    this.albumBorderBitmap.recycle();
                }
                this.albumBorderSource = bitmapBlurredAlbumBackground;
                this.albumBorderBitmap = rounded;
            }
            GradientDrawable border = new GradientDrawable();
            border.setColor(0);
            border.setCornerRadius(dp(20));
            border.setStroke(dp(1), this.host.borderColor());
            return new LayerDrawable(new Drawable[]{new BitmapDrawable(this.host.getApp().getResources(), this.albumBorderBitmap), border});
        } catch (Throwable th) {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(COLOR_HANDLE_BG);
            gradientDrawable.setCornerRadius(dp(20));
            gradientDrawable.setStroke(dp(1), this.host.borderColor());
            return gradientDrawable;
        }
    }

    private Bitmap blurredAlbumBackground(Bitmap bitmap) {
        if (bitmap != null) {
            try {
                if (!bitmap.isRecycled()) {
                    if (bitmap == this.albumBgSource && this.albumBgBitmap != null && !this.albumBgBitmap.isRecycled()) {
                        return this.albumBgBitmap;
                    }
                    int iMax = Math.max(1, dp(cardWidthDp()));
                    int iMax2 = Math.max(1, dp(240));
                    Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmap, Math.max(1, bitmap.getWidth() / 12), Math.max(1, bitmap.getHeight() / 12), true);
                    Bitmap bitmapCreateScaledBitmap2 = Bitmap.createScaledBitmap(bitmapCreateScaledBitmap, iMax, iMax2, true);
                    bitmapCreateScaledBitmap.recycle();
                    Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMax, iMax2, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmapCreateBitmap);
                    Path path = new Path();
                    RectF rectF = new RectF(0.0f, 0.0f, iMax, iMax2);
                    path.addRoundRect(rectF, dp(20), dp(20), Path.Direction.CW);
                    canvas.clipPath(path);
                    canvas.drawBitmap(bitmapCreateScaledBitmap2, 0.0f, 0.0f, (Paint) null);
                    bitmapCreateScaledBitmap2.recycle();
                    canvas.drawColor(COLOR_CARD_OVERLAY);
                    // fixed64: the border is no longer baked into the
                    // bitmap — playerBackground() layers the theme
                    // border over the rounded bitmap, so it stays
                    // current when the glass theme changes.
                    if (this.albumBgBitmap != null && this.albumBgBitmap != bitmapCreateBitmap) {
                        this.albumBgBitmap.recycle();
                    }
                    this.albumBgSource = bitmap;
                    this.albumBgBitmap = bitmapCreateBitmap;
                    return bitmapCreateBitmap;
                }
            } catch (Throwable th) {
                log("blurredAlbumBackground failed", th);
                return null;
            }
        }
        return null;
    }

    private int dp(int i) {
        try {
            return this.host.dp(i);
        } catch (Throwable th) {
            return i;
        }
    }

    private int cardWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).cardWidthDp();
            }
            if (!this.host.isLandscape()) {
                return Settings.BUBBLE_WIDTH_DP;
            }
            return Settings.CARD_WIDTH_LANDSCAPE_DP;
        } catch (Throwable th) {
            return Settings.BUBBLE_WIDTH_DP;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean screenOn() {
        try {
            return this.host.isScreenOn();
        } catch (Throwable th) {
            return true;
        }
    }

    private void log(String str) {
        try {
            this.host.log(str);
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String str, Throwable th) {
        try {
            this.host.log(str, th);
        } catch (Throwable th2) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setSwipeMode(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setSwipeMode(z);
            }
        } catch (Throwable th) {
            log("setSwipeMode failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void postDelayed(Runnable runnable, long j) {
        try {
            if (this.handler != null) {
                this.handler.postDelayed(runnable, j);
            }
        } catch (Throwable th) {
            log("postDelayed failed", th);
        }
    }

    private void removeCallbacks(Runnable runnable) {
        try {
            if (this.handler != null) {
                this.handler.removeCallbacks(runnable);
            }
        } catch (Throwable th) {
        }
    }

    // fixed17: same credit/lyric title filter as NotificationService.
    private static boolean isCreditOrLyricTitle(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        String[] prefixes = {"词：", "曲：", "词:", "曲:", "作词", "作曲", "编曲", "演唱", "原唱", "词曲", "作词：", "作曲：", "编曲：", "演唱：", "原唱："};
        for (String p : prefixes) {
            if (t.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equal(String str, String str2) {
        if (str == null) {
            return str2 == null;
        }
        return str.equals(str2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float clamp(float f, float f2, float f3) {
        if (f < f2) {
            return f2;
        }
        return f > f3 ? f3 : f;
    }

    private static class DiscView extends View {
        private Bitmap album;
        private final Paint albumPaint;
        private BitmapShader albumShader;
        private Bitmap albumShaderBitmap;
        private final float density;
        private final Paint discPaint;
        private final Paint groovePaint;
        private final Paint holePaint;
        private final Paint labelPaint;
        private final Paint notePaint;
        private int shaderSizePx;

        DiscView(Context context) {
            super(context);
            this.discPaint = new Paint(1);
            this.groovePaint = new Paint(1);
            this.albumPaint = new Paint(1);
            this.labelPaint = new Paint(1);
            this.holePaint = new Paint(1);
            this.notePaint = new Paint(1);
            this.shaderSizePx = -1;
            this.density = context.getResources().getDisplayMetrics().density;
            this.discPaint.setColor(-15395563);
            this.groovePaint.setStyle(Paint.Style.STROKE);
            this.groovePaint.setColor(-13750738);
            this.groovePaint.setStrokeWidth(Math.max(1.0f, this.density));
            this.labelPaint.setColor(-14796236);
            this.holePaint.setColor(-1447447);
            this.notePaint.setColor(MusicBubble.COLOR_ACCENT);
            this.notePaint.setTextSize(this.density * 12.0f);
            this.notePaint.setTextAlign(Paint.Align.CENTER);
            this.notePaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setAlbum(Bitmap bitmap) {
            if (this.album != bitmap) {
                this.album = bitmap;
                this.albumShader = null;
                invalidate();
            }
        }

        @Override // android.view.View
        protected void onSizeChanged(int i, int i2, int i3, int i4) {
            super.onSizeChanged(i, i2, i3, i4);
            this.albumShader = null;
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            float f = width / 2.0f;
            float f2 = height / 2.0f;
            float fMin = Math.min(width, height) / 2.0f;
            canvas.drawCircle(f, f2, fMin, this.discPaint);
            canvas.drawCircle(f, f2, 0.86f * fMin, this.groovePaint);
            canvas.drawCircle(f, f2, fMin * 0.7f, this.groovePaint);
            float f3 = this.density * 12.0f;
            if (this.album != null && !this.album.isRecycled() && this.album.getWidth() > 0 && this.album.getHeight() > 0) {
                int iRound = Math.round(f3 * 2.0f);
                if (this.albumShader == null || this.albumShaderBitmap != this.album || this.shaderSizePx != iRound) {
                    this.albumShader = new BitmapShader(this.album, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    float fMin2 = iRound / Math.min(this.album.getWidth(), this.album.getHeight());
                    Matrix matrix = new Matrix();
                    matrix.setScale(fMin2, fMin2);
                    matrix.postTranslate(f - ((this.album.getWidth() * fMin2) / 2.0f), f2 - ((this.album.getHeight() * fMin2) / 2.0f));
                    this.albumShader.setLocalMatrix(matrix);
                    this.albumShaderBitmap = this.album;
                    this.shaderSizePx = iRound;
                }
                this.albumPaint.setShader(this.albumShader);
                canvas.drawCircle(f, f2, f3, this.albumPaint);
            } else {
                canvas.drawCircle(f, f2, f3, this.labelPaint);
                Paint.FontMetrics fontMetrics = this.notePaint.getFontMetrics();
                canvas.drawText("♪", f, f2 - ((fontMetrics.ascent + fontMetrics.descent) / 2.0f), this.notePaint);
            }
            canvas.drawCircle(f, f2, this.density * 3.0f, this.holePaint);
        }
    }

    private static class SwipeTapListener implements View.OnTouchListener {
        private final BubbleHost host;
        private final Callback callback;
        private long downTime;
        private float downX;
        private float downY;
        private boolean dragStarted;
        private boolean dragging;
        private VelocityTracker velocityTracker;
        // fixed112: long-press drag to move the dock.
        private Runnable dockDragLongPress;
        private float dockDragDownRawX;
        private float dockDragDownRawY;

        interface Callback {
            void onDragEnd();

            void onDragStart();

            void onSwipeLeft();

            void onSwipeRight();

            void onTap();
        }

        SwipeTapListener(BubbleHost host, Callback callback) {
            this.host = host;
            this.callback = callback;
        }

        // fixed103: mirror left/right swipes when the dock is on the
        // left edge (right side unchanged, left side mirrored).
        private boolean mirrorSwipes() {
            try {
                return this.host != null && this.host.isDockLeft();
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(final View view, MotionEvent motionEvent) {
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
                                    SwipeTapListener.this.host.startDockDrag();
                                } catch (Throwable ignored) {
                                }
                            }
                        };
                    }
                    this.host.getHandler().postDelayed(this.dockDragLongPress, android.view.ViewConfiguration.getLongPressTimeout());
                } else if (action == 2) {
                    if (this.host.isDockDragging()) {
                        this.host.moveDockDrag(motionEvent.getRawY());
                        return true;
                    }
                    float dx = motionEvent.getRawX() - this.dockDragDownRawX;
                    float dy = motionEvent.getRawY() - this.dockDragDownRawY;
                    int slop = android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                    if (dx * dx + dy * dy > slop * slop) {
                        this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    }
                } else if (action == 1 || action == 3) {
                    this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    if (this.host.isDockDragging()) {
                        this.host.endDockDrag(motionEvent.getRawX(), motionEvent.getRawY());
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            float xVelocity;
            try {
                float f = view.getContext().getResources().getDisplayMetrics().density;
                switch (motionEvent.getActionMasked()) {
                    case 0:
                        this.downX = motionEvent.getX();
                        this.downY = motionEvent.getY();
                        this.downTime = SystemClock.uptimeMillis();
                        this.dragging = false;
                        this.dragStarted = false;
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
                        if (this.dragStarted) {
                            this.callback.onDragEnd();
                        }
                        float f2 = 84.0f * f;
                        if (this.dragging && x < 0.0f && ((-x) > f2 || xVelocity < -1000.0f)) {
                            view.animate().translationX(-view.getWidth()).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.1
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    view.setTranslationX(0.0f);
                                    view.setAlpha(1.0f);
                                    if (SwipeTapListener.this.mirrorSwipes()) {
                                        SwipeTapListener.this.callback.onSwipeRight();
                                    } else {
                                        SwipeTapListener.this.callback.onSwipeLeft();
                                    }
                                }
                            }).start();
                        } else if (this.dragging && x > 0.0f && (x > f2 || xVelocity > 1000.0f)) {
                            view.animate().translationX(view.getWidth()).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.MusicBubble.SwipeTapListener.2
                                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                                public void onAnimationEnd(Animator animator) {
                                    view.setTranslationX(0.0f);
                                    view.setAlpha(1.0f);
                                    if (SwipeTapListener.this.mirrorSwipes()) {
                                        SwipeTapListener.this.callback.onSwipeLeft();
                                    } else {
                                        SwipeTapListener.this.callback.onSwipeRight();
                                    }
                                }
                            }).start();
                        } else if (this.dragging) {
                            view.animate().translationX(0.0f).alpha(1.0f).setDuration(180L).start();
                        } else if (jUptimeMillis < 500 && Math.abs(x) < f * 10.0f) {
                            this.callback.onTap();
                        }
                        return true;
                    case 2:
                        if (this.velocityTracker != null) {
                            this.velocityTracker.addMovement(motionEvent);
                        }
                        float x2 = motionEvent.getX() - this.downX;
                        float y = motionEvent.getY() - this.downY;
                        if (!this.dragging && Math.abs(x2) > f * 8.0f && Math.abs(x2) > Math.abs(y) * 1.5f) {
                            this.dragging = true;
                        }
                        if (this.dragging) {
                            if (!this.dragStarted) {
                                this.dragStarted = true;
                                this.callback.onDragStart();
                            }
                            view.setTranslationX(x2);
                            view.setAlpha(1.0f - Math.min(0.5f, (Math.abs(x2) / Math.max(1, view.getWidth())) * 0.8f));
                        }
                        return true;
                    case 3:
                        if (this.velocityTracker != null) {
                            this.velocityTracker.recycle();
                            this.velocityTracker = null;
                        }
                        if (this.dragStarted) {
                            this.callback.onDragEnd();
                        }
                        if (this.dragging) {
                            view.animate().translationX(0.0f).alpha(1.0f).setDuration(180L).start();
                        }
                        return true;
                    default:
                        return false;
                }
            } catch (Throwable th) {
                return false;
            }
        }
    }
}
