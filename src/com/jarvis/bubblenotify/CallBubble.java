package com.jarvis.bubblenotify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class CallBubble implements BubbleFeature {
    private static final long CALL_TICK_MS = 1000;
    private static final int COLOR_ANSWER = -14701995;
    private static final int COLOR_AVATAR_END = -12877116;
    private static final int COLOR_AVATAR_START = -13923718;
    private static final int COLOR_GHOST_BTN = 335544320;
    private static final int COLOR_GHOST_ICON = -14736603;
    private static final int COLOR_HANDLE_BG = -1073741825;
    private static final int COLOR_HANDLE_BORDER = 687865856;
    private static final int COLOR_HANDLE_TEXT = -14736603;
    private static final int COLOR_HANGUP = -1750963;
    private static final int COLOR_SPEAKER_ANSWER = -14975074;
    private static final int COLOR_SPEAKER_ON = -15961500;
    private static final int COLOR_SPEAKER_ON_RING = -1084437578;
    private static final int COLOR_STATUS = -15961500;
    private static final int COLOR_TEXT_DIM = -8748153;
    private static final int COLOR_TEXT_MAIN = -15065826;
    private static final int COLOR_TEXT_SUB = -11905960;
    private static final long HANGUP_VERIFY_DELAY_MS = 1500;
    private static final int LEVEL_CARD = 1;
    private static final int LEVEL_DOCK = 3;
    private static final int LEVEL_MINI = 2;
    private static final int LEVEL_NONE = 0;
    private static final long PULSE_DURATION_MS = 1600;
    private static final long TAP_IGNORE_AFTER_SWIPE_MS = 350;
    private View answerHalo;
    private TextView avatarCharView;
    private String callName;
    private String callNumber;
    private View cardView;
    private View currentView;
    private View dockView;
    private ValueAnimator haloAnimator;
    private final Handler handler;
    private final BubbleHost host;
    private TextView miniAvatarCharView;
    private TextView miniNameView;
    private TextView miniStatusView;
    private LinearLayout miniView;
    private TextView nameView;
    private TextView numberView;
    private PhoneStateListener phoneListener;
    private ValueAnimator pulseAnimator1;
    private ValueAnimator pulseAnimator2;
    private View pulseRing1;
    private View pulseRing2;
    private IconView speakerBtnView;
    private TextView speakerLabel;
    // fixed180: mini pill speaker button — the icon button from
    // the ringing card (blue SPEAKER icon), not the "扬声器" text
    // label. Replaces speakerMarkView.
    private IconView miniSpeakerBtnView;
    private TextView statusView;
    private TelephonyManager telephonyManager;
    private boolean disabled = false;
    private boolean destroyed = false;
    private String callState = Settings.CALL_IDLE;
    private boolean speakerOn = false;
    private long callStartElapsed = 0;
    private int level = 0;
    private boolean returnedFromDock = false;
    private boolean cardFromFocus = false;
    private long lastSwipeUpTime = 0;
    private final Runnable idleRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.1
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (!CallBubble.this.destroyed && !CallBubble.this.disabled && CallBubble.this.screenOn()) {
                    if (CallBubble.this.level == CallBubble.LEVEL_CARD && Settings.CALL_OFFHOOK.equals(CallBubble.this.callState)) {
                        CallBubble.this.showLevel(CallBubble.LEVEL_MINI);
                        return;
                    }
                    if (CallBubble.this.level == CallBubble.LEVEL_MINI) {
                        if (CallBubble.this.host.isFocusActive() && !CallBubble.this.host.isFocusView(CallBubble.this.currentView)) {
                            CallBubble.this.postDelayed(CallBubble.this.idleRunnable, 3000L);
                        } else {
                            CallBubble.this.showLevel(3);
                        }
                    }
                }
            } catch (Throwable th) {
                CallBubble.this.log("Call idle timer failed", th);
            }
        }
    };
    private final Runnable callTickRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.2
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (!CallBubble.this.destroyed && !CallBubble.this.disabled && Settings.CALL_OFFHOOK.equals(CallBubble.this.callState) && CallBubble.this.level != 0 && CallBubble.this.screenOn()) {
                    CallBubble.this.updateCallTimeTexts();
                    CallBubble.this.postDelayed(CallBubble.this.callTickRunnable, CallBubble.CALL_TICK_MS);
                }
            } catch (Throwable th) {
                CallBubble.this.log("Call tick failed", th);
            }
        }
    };
    private final Runnable hangupVerifyRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.3
        @Override // java.lang.Runnable
        public void run() {
            int callState;
            try {
                if (CallBubble.this.destroyed || Settings.CALL_IDLE.equals(CallBubble.this.callState)) {
                    return;
                }
                try {
                    if (CallBubble.this.telephonyManager == null) {
                        callState = 0;
                    } else {
                        callState = CallBubble.this.telephonyManager.getCallState();
                    }
                    if (callState == 0) {
                        CallBubble.this.log("CallBubble: hangup verified idle, hiding.");
                        CallBubble.this.endCallUi();
                    }
                } catch (Throwable th) {
                    CallBubble.this.log("getCallState failed", th);
                }
            } catch (Throwable th2) {
                CallBubble.this.log("hangupVerify failed", th2);
            }
        }
    };

    boolean ownsView(View view) {
        if (view == null) {
            return false;
        }
        return view == this.currentView || view == this.cardView || view == this.miniView || view == this.dockView;
    }

    public CallBubble(BubbleHost bubbleHost) {
        this.host = bubbleHost;
        this.handler = bubbleHost != null ? bubbleHost.getHandler() : null;
        if (bubbleHost != null) {
            try {
                if (bubbleHost.getApp() != null) {
                    this.telephonyManager = (TelephonyManager) bubbleHost.getApp().getSystemService("phone");
                    if (this.telephonyManager != null) {
                        this.phoneListener = new PhoneStateListener() { // from class: com.jarvis.bubblenotify.CallBubble.4
                            @Override // android.telephony.PhoneStateListener
                            public void onCallStateChanged(final int i, final String str) {
                                try {
                                    if (CallBubble.this.handler != null) {
                                        CallBubble.this.handler.post(new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.4.1
                                            @Override // java.lang.Runnable
                                            public void run() {
                                                CallBubble.this.handlePhoneState(i, str);
                                            }
                                        });
                                    }
                                } catch (Throwable th) {
                                    CallBubble.this.log("PhoneStateListener post failed", th);
                                }
                            }
                        };
                        this.telephonyManager.listen(this.phoneListener, 32);
                        log("CallBubble: PhoneStateListener registered.");
                    }
                }
            } catch (Throwable th) {
                log("CallBubble: failed to register PhoneStateListener", th);
            }
        }
    }

    public void onCallBroadcast(Intent intent) {
        try {
            if (!this.destroyed && intent != null && this.host != null) {
                if (!this.host.isEnabled()) {
                    endCallUi();
                    return;
                }
                this.disabled = false;
                String stringExtra = intent.getStringExtra(Settings.EXTRA_CALL_STATE);
                String stringExtra2 = intent.getStringExtra(Settings.EXTRA_CALL_NAME);
                String stringExtra3 = intent.getStringExtra(Settings.EXTRA_CALL_NUMBER);
                if (stringExtra2 != null && !stringExtra2.isEmpty()) {
                    this.callName = stringExtra2;
                }
                if (stringExtra3 != null && !stringExtra3.isEmpty()) {
                    this.callNumber = stringExtra3;
                }
                if (stringExtra == null) {
                    log("CallBubble: broadcast without state, ignored.");
                    return;
                }
                if (Settings.CALL_RINGING.equals(stringExtra)) {
                    dropStaleCallState();
                    if (Settings.CALL_OFFHOOK.equals(this.callState)) {
                        return;
                    }
                    this.callState = Settings.CALL_RINGING;
                    if (this.level != LEVEL_CARD) {
                        showLevel(LEVEL_CARD);
                    } else {
                        refreshLevelContent();
                    }
                    log("CallBubble: RINGING (broadcast).");
                    return;
                }
                if (Settings.CALL_OFFHOOK.equals(stringExtra)) {
                    if (Settings.CALL_RINGING.equals(this.callState)) {
                        enterOffhook();
                        return;
                    } else {
                        if (Settings.CALL_OFFHOOK.equals(this.callState)) {
                            refreshLevelContent();
                            return;
                        }
                        return;
                    }
                }
                if (Settings.CALL_IDLE.equals(stringExtra)) {
                    if (!Settings.CALL_IDLE.equals(this.callState)) {
                        // fixed185: the call notification is
                        // re-posted when answered (incoming ->
                        // ongoing, key changes), and the old key's
                        // removal sends CALL_IDLE. Verify the phone
                        // is actually idle before hiding — if it is
                        // still OFFHOOK/RINGING the call is up and
                        // the bubble stays (user "点了接听后气泡
                        // 自动消失了").
                        try {
                            int phoneState = this.telephonyManager != null ? this.telephonyManager.getCallState() : 0;
                            if (phoneState != 0) {
                                log("CallBubble: IDLE broadcast ignored, phone still in call (state=" + phoneState + ").");
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        log("CallBubble: IDLE (broadcast), hiding.");
                        endCallUi();
                        return;
                    }
                    return;
                }
                log("CallBubble: unknown call state in broadcast, ignored.");
            }
        } catch (Throwable th) {
            log("onCallBroadcast failed", th);
        }
    }

    public void handlePhoneState(int i, String str) {
        try {
            switch (i) {
                case 0:
                    if (!Settings.CALL_IDLE.equals(this.callState)) {
                        log("CallBubble: IDLE (listener), hiding.");
                        endCallUi();
                    }
                    break;
                case LEVEL_CARD /* 1 */:
                    dropStaleCallState();
                    if (!this.disabled && Settings.CALL_IDLE.equals(this.callState)) {
                        if ((this.callNumber == null || this.callNumber.isEmpty()) && str != null && !str.isEmpty()) {
                            this.callNumber = str;
                        }
                        this.callState = Settings.CALL_RINGING;
                        showLevel(LEVEL_CARD);
                        log("CallBubble: RINGING (listener).");
                        break;
                    }
                    break;
                case LEVEL_MINI /* 2 */:
                    if (!this.disabled) {
                        if (Settings.CALL_RINGING.equals(this.callState)) {
                            enterOffhook();
                            break;
                        } else if (Settings.CALL_OFFHOOK.equals(this.callState)) {
                            // fixed179: 2.0 v141 — no speaker touch
                            // here. applyCallSpeaker now fires the
                            // TOGGLE action, so re-applying on every
                            // OFFHOOK event would flip it again. The
                            // tick updates the timer.
                            break;
                        }
                    }
                    break;
            }
        } catch (Throwable th) {
            log("handlePhoneState failed", th);
        }
    }

    private void enterOffhook() {
        try {
            boolean z = !Settings.CALL_OFFHOOK.equals(this.callState) ? true : false;
            this.callState = Settings.CALL_OFFHOOK;
            if (z) {
                this.callStartElapsed = SystemClock.elapsedRealtime();
            }
            // fixed179: 2.0 v141 — no speaker touch here. The
            // user answers with the earpiece and taps the speaker
            // to switch; firing the toggle here would flip it
            // once more.
            // fixed186: correction of fixed185 (user "来电接听我
            // 不是说大卡片不缩 / 我是说接听后保持mini气泡") —
            // the big card DOES shrink to the mini pill on answer,
            // and the mini pill stays (the CALL_IDLE guard below
            // keeps it from vanishing). The mini pill is added
            // via addDockView with a 12dp edge margin (its tag is
            // not TAG_EDGE), so it is not flush to the edge
            // ("不要贴边").
            showLevel(LEVEL_MINI);
            startCallTick();
        } catch (Throwable th) {
            log("enterOffhook failed", th);
        }
    }

    public void onFeatureDisabled() {
        try {
            this.disabled = true;
            hideAll();
            log("Call feature disabled, bubble hidden.");
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
                stopCallTick();
                stopPulse();
            } else if (this.level != 0 && !this.disabled && !Settings.CALL_IDLE.equals(this.callState)) {
                scheduleIdle();
                refreshCallTick();
                if (this.level == LEVEL_CARD && Settings.CALL_RINGING.equals(this.callState)) {
                    startPulse();
                }
            }
        } catch (Throwable th) {
            log("Call onScreenChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onConfigurationChanged() {
        try {
            if (!this.destroyed && !this.disabled && this.level == LEVEL_CARD && this.currentView != null) {
                removeCurrentView();
                this.level = 0;
                showLevel(LEVEL_CARD);
            }
        } catch (Throwable th) {
            log("Call onConfigurationChanged failed", th);
        }
    }

    @Override // com.jarvis.bubblenotify.BubbleFeature
    public void onDestroy() {
        try {
            hideAll();
        } catch (Throwable th) {
        }
        this.destroyed = true;
        try {
            if (this.telephonyManager != null && this.phoneListener != null) {
                this.telephonyManager.listen(this.phoneListener, 0);
            }
        } catch (Throwable th2) {
            log("CallBubble: failed to unregister PhoneStateListener", th2);
        }
        this.telephonyManager = null;
        this.phoneListener = null;
    }

    public void answerCall(boolean z) {
        try {
            if (this.host != null && this.host.getApp() != null) {
                // fixed179: 2.0 v126 — answer with the direct
                // TelecomManager.acceptRingingCall() and force the
                // speaker off. The v125 notification-action answer
                // does not fire on this Flyme, so fixed178's
                // speaker_answer/answer broadcast is dropped. The
                // user taps the speaker (mini pill / card) after
                // the call connects to switch. z is ignored: the
                // 扬声器接听 button is removed (fixed179), every
                // answer is an earpiece answer.
                TelecomManager telecomManager = (TelecomManager) this.host.getApp().getSystemService("telecom");
                if (telecomManager == null) {
                    log("CallBubble: no TelecomManager, cannot answer.");
                    return;
                }
                telecomManager.acceptRingingCall();
                this.speakerOn = false;
                enterOffhook();
                log("CallBubble: acceptRingingCall() called.");
            }
        } catch (Throwable th) {
            log("CallBubble: acceptRingingCall failed", th);
        }
    }

    public void openCallUi() {
        try {
            if (this.host != null && this.host.getApp() != null) {
                try {
                    android.provider.Settings.Global.putString(this.host.getApp().getContentResolver(), Settings.CALL_UI_REQUEST_KEY, String.valueOf(SystemClock.elapsedRealtime()));
                } catch (Throwable th) {
                }
                TelecomManager telecomManager = (TelecomManager) this.host.getApp().getSystemService("telecom");
                if (telecomManager == null) {
                    log("CallBubble: no TelecomManager, cannot open call UI.");
                } else {
                    telecomManager.showInCallScreen(false);
                    log("CallBubble: showInCallScreen() called.");
                }
            }
        } catch (Throwable th2) {
            log("CallBubble: showInCallScreen failed", th2);
        }
    }

    public void hangupCall() {
        try {
            if (this.host != null && this.host.getApp() != null) {
                TelecomManager telecomManager = (TelecomManager) this.host.getApp().getSystemService("telecom");
                if (telecomManager == null) {
                    log("CallBubble: no TelecomManager, cannot hang up.");
                    return;
                }
                telecomManager.endCall();
                log("CallBubble: endCall() called.");
                postDelayed(this.hangupVerifyRunnable, HANGUP_VERIFY_DELAY_MS);
            }
        } catch (Throwable th) {
            log("CallBubble: endCall failed", th);
        }
    }

    public void toggleSpeaker() {
        // fixed177: speaker only after answering — the incallui
        // notification has no speaker action while ringing, and the
        // user must tap answer first.
        if (!Settings.CALL_OFFHOOK.equals(this.callState)) {
            log("CallBubble: speaker ignored, call not answered yet.");
            return;
        }
        boolean z = !this.speakerOn;
        if (applySpeaker(z)) {
            this.speakerOn = z;
            refreshLevelContent();
            resetIdle();
        }
    }

    // fixed177: 2.0 speaker switching — fire the incallui call
    // notification's OWN speaker action via the listener service
    // (ACTION_CALL_CONTROL -> fireCallAction). No direct
    // AudioManager.setSpeakerphoneOn any more: it fires before the
    // call connects (audio mode not yet MODE_IN_CALL) and is
    // ignored, so the call falls back to handset.
    private boolean applySpeaker(boolean z) {
        try {
            return sendCallControl(z ? "speaker_on" : "speaker_off");
        } catch (Throwable th) {
            log("CallBubble: sendCallControl failed", th);
            return false;
        }
    }

    private boolean sendCallControl(String command) {
        try {
            if (this.host == null || this.host.getApp() == null) {
                return false;
            }
            android.content.Intent intent =
                    new android.content.Intent(Settings.ACTION_CALL_CONTROL);
            intent.putExtra(Settings.EXTRA_CONTROL, command);
            this.host.getApp().sendBroadcast(intent);
            log("CallBubble: sent call control " + command);
            return true;
        } catch (Throwable th) {
            log("CallBubble: sendCallControl failed", th);
            return false;
        }
    }

    public void showLevel(int i) {
        View viewBuildCardView;
        if (this.destroyed || this.disabled || this.host == null) {
            return;
        }
        if (i == this.level && this.currentView != null) {
            refreshLevelContent();
            return;
        }
        int i2 = this.level;
        if (i == LEVEL_CARD) {
            this.cardFromFocus = this.host.isFocusView(this.currentView);
        }
        removeCurrentView();
        this.level = i;
        this.returnedFromDock = (i == LEVEL_MINI && i2 == 3) ? true : false;
        try {
            switch (i) {
                case LEVEL_CARD /* 1 */:
                    viewBuildCardView = buildCardView();
                    break;
                case LEVEL_MINI /* 2 */:
                    viewBuildCardView = buildMiniView();
                    break;
                case 3:
                    viewBuildCardView = buildDockView();
                    break;
                default:
                    this.level = 0;
                    return;
            }
            this.currentView = viewBuildCardView;
            try {
                if (i == LEVEL_CARD) {
                    this.host.addDockViewAtBottom(viewBuildCardView);
                } else if (i == 3) {
                    this.host.addDockedIcon(viewBuildCardView, new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.5
                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                if (!CallBubble.this.tapAllowed()) {
                                    return;
                                }
                                CallBubble.this.host.expandAllToMini();
                            } catch (Throwable th) {
                                CallBubble.this.log("Call dock tap failed", th);
                            }
                        }
                    });
                } else {
                    this.host.addDockView(viewBuildCardView);
                }
                this.host.updateWindowSize();
                if (i == LEVEL_CARD) {
                    this.host.setFocusView(viewBuildCardView);
                } else if (i == LEVEL_MINI && (i2 == 0 || i2 == LEVEL_CARD)) {
                    if (!((i2 != LEVEL_CARD || this.cardFromFocus) ? false : true) && !this.host.isFocusCard()) {
                        this.host.setFocusView(viewBuildCardView);
                    }
                }
            } catch (Throwable th) {
                log("Call addDockView failed", th);
            }
            this.host.setCardOpen(i == LEVEL_CARD ? true : false);
            if (i == 3) {
                viewBuildCardView.animate().cancel();
                viewBuildCardView.setTranslationY(dp(16));
                viewBuildCardView.animate().withLayer().translationY(0.0f).setDuration(200L).start();
            } else {
                animateIn(viewBuildCardView);
            }
            if (i == LEVEL_CARD && Settings.CALL_RINGING.equals(this.callState)) {
                startPulse();
            }
            scheduleIdle();
            refreshCallTick();
        } catch (Throwable th2) {
            log("Call build level " + i + " failed", th2);
            this.level = 0;
        }
    }

    public void swipeToLevel(int i) {
        this.lastSwipeUpTime = SystemClock.uptimeMillis();
        showLevel(i);
    }

    public boolean tapAllowed() {
        return SystemClock.uptimeMillis() - this.lastSwipeUpTime >= TAP_IGNORE_AFTER_SWIPE_MS;
    }

    private int capsuleWidthDp() {
        try {
            if (this.host instanceof BubbleManager) {
                return ((BubbleManager) this.host).capsuleWidthDp();
            }
            return Settings.DEFAULT_CAPSULE_WIDTH_DP;
        } catch (Throwable th) {
            return Settings.DEFAULT_CAPSULE_WIDTH_DP;
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

    public void expandFromDock() {
        if (this.level != 3) {
            return;
        }
        showLevel(LEVEL_MINI);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissCallCapsule() {
        hideAll();
        dropStaleCallState();
        log("Call capsule dismissed by user swipe.");
    }

    private void hideAll() {
        cancelIdle();
        stopCallTick();
        removeCallbacks(this.hangupVerifyRunnable);
        removeCurrentView();
        this.level = 0;
        this.host.setCardOpen(false);
        try {
            this.host.updateWindowSize();
        } catch (Throwable th) {
            log("Call updateWindowSize failed", th);
        }
    }

    public void endCallUi() {
        this.callState = Settings.CALL_IDLE;
        this.callName = null;
        this.callNumber = null;
        this.speakerOn = false;
        this.callStartElapsed = 0L;
        hideAll();
    }

    private boolean dropStaleCallState() {
        try {
            int callState = this.telephonyManager == null ? 0 : this.telephonyManager.getCallState();
            if (Settings.CALL_OFFHOOK.equals(this.callState) && callState != LEVEL_MINI) {
                resetCallState();
                return true;
            }
            if (Settings.CALL_RINGING.equals(this.callState) && callState == 0) {
                resetCallState();
                return true;
            }
            return false;
        } catch (Throwable th) {
        }
        return false;
    }

    private void resetCallState() {
        this.callState = Settings.CALL_IDLE;
        this.callName = null;
        this.callNumber = null;
        this.speakerOn = false;
        this.callStartElapsed = 0L;
        log("CallBubble: dropped stale call state.");
    }

    private void removeCurrentView() {
        stopPulse();
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
                log("Call removeDockView failed", th2);
            }
            this.currentView = null;
        }
        this.cardView = null;
        this.nameView = null;
        this.numberView = null;
        this.statusView = null;
        this.avatarCharView = null;
        this.pulseRing1 = null;
        this.pulseRing2 = null;
        this.answerHalo = null;
        this.speakerBtnView = null;
        this.speakerLabel = null;
        this.miniView = null;
        this.miniNameView = null;
        this.miniStatusView = null;
        this.miniAvatarCharView = null;
        this.miniSpeakerBtnView = null;
        this.dockView = null;
    }

    private void animateIn(final View view) {
        try {
            view.animate().cancel();
            view.setAlpha(0.0f);
            view.setTranslationX(0.0f);
            view.setTranslationY(0.0f);
            view.setScaleX(0.85f);
            view.setScaleY(0.85f);
            view.post(new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.6
                @Override // java.lang.Runnable
                public void run() {
                    try {
                        if (view.getParent() == null) {
                            view.setAlpha(1.0f);
                            view.setScaleX(1.0f);
                            view.setScaleY(1.0f);
                        } else {
                            view.setPivotX(view.getWidth());
                            view.setPivotY(view.getHeight());
                            view.animate().withLayer().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200L).start();
                        }
                    } catch (Throwable th) {
                        CallBubble.this.log("Call animateIn failed", th);
                    }
                }
            });
        } catch (Throwable th) {
            log("Call animateIn failed", th);
        }
    }

    private void scheduleIdle() {
        cancelIdle();
        if (this.destroyed || this.disabled || this.level == 0 || this.level == 3 || !screenOn()) {
            return;
        }
        if (this.level == LEVEL_CARD) {
            if (!Settings.CALL_OFFHOOK.equals(this.callState)) {
                return;
            }
            postDelayed(this.idleRunnable, 3000L);
        } else if (this.level == LEVEL_MINI) {
            postDelayed(this.idleRunnable, 3000L);
        }
    }

    public void cancelIdle() {
        removeCallbacks(this.idleRunnable);
    }

    public void resetIdle() {
        scheduleIdle();
    }

    private void startCallTick() {
        stopCallTick();
        if (this.destroyed || this.disabled || !Settings.CALL_OFFHOOK.equals(this.callState) || this.level == 0 || !screenOn()) {
            return;
        }
        postDelayed(this.callTickRunnable, CALL_TICK_MS);
    }

    private void stopCallTick() {
        removeCallbacks(this.callTickRunnable);
    }

    private void refreshCallTick() {
        if (Settings.CALL_OFFHOOK.equals(this.callState) && this.level != 0 && screenOn() && !this.disabled) {
            startCallTick();
        } else {
            stopCallTick();
        }
    }

    private void startPulse() {
        try {
            if (!this.destroyed && !this.disabled && Settings.CALL_RINGING.equals(this.callState) && this.level == LEVEL_CARD && screenOn()) {
                stopPulse();
                this.pulseAnimator1 = makePulseAnimator(this.pulseRing1, 1.15f, 0L);
                this.pulseAnimator2 = makePulseAnimator(this.pulseRing2, 1.15f, 800L);
                this.haloAnimator = makePulseAnimator(this.answerHalo, 1.12f, 0L);
                if (this.pulseAnimator1 != null) {
                    this.pulseAnimator1.start();
                }
                if (this.pulseAnimator2 != null) {
                    this.pulseAnimator2.start();
                }
                if (this.haloAnimator != null) {
                    this.haloAnimator.start();
                }
            }
        } catch (Throwable th) {
            log("startPulse failed", th);
        }
    }

    private ValueAnimator makePulseAnimator(final View view, final float f, long j) {
        if (view == null) {
            return null;
        }
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
        valueAnimatorOfFloat.setDuration(PULSE_DURATION_MS);
        valueAnimatorOfFloat.setRepeatCount(-1);
        valueAnimatorOfFloat.setStartDelay(j);
        valueAnimatorOfFloat.setInterpolator(new LinearInterpolator());
        valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.jarvis.bubblenotify.CallBubble.7
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                float f2 = ((f - 1.0f) * fFloatValue) + 1.0f;
                view.setScaleX(f2);
                view.setScaleY(f2);
                view.setAlpha((1.0f - fFloatValue) * 0.7f);
            }
        });
        return valueAnimatorOfFloat;
    }

    private void stopPulse() {
        try {
            if (this.pulseAnimator1 != null) {
                this.pulseAnimator1.cancel();
                this.pulseAnimator1 = null;
            }
            if (this.pulseAnimator2 != null) {
                this.pulseAnimator2.cancel();
                this.pulseAnimator2 = null;
            }
            if (this.haloAnimator != null) {
                this.haloAnimator.cancel();
                this.haloAnimator = null;
            }
            if (this.pulseRing1 != null) {
                this.pulseRing1.setAlpha(0.0f);
                this.pulseRing1.setScaleX(1.0f);
                this.pulseRing1.setScaleY(1.0f);
            }
            if (this.pulseRing2 != null) {
                this.pulseRing2.setAlpha(0.0f);
                this.pulseRing2.setScaleX(1.0f);
                this.pulseRing2.setScaleY(1.0f);
            }
            if (this.answerHalo != null) {
                this.answerHalo.setAlpha(0.0f);
                this.answerHalo.setScaleX(1.0f);
                this.answerHalo.setScaleY(1.0f);
            }
        } catch (Throwable th) {
            log("stopPulse failed", th);
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
    private View buildCardView() {
        Application app = this.host.getApp();
        final boolean zEquals = Settings.CALL_RINGING.equals(this.callState);
        LinearLayout linearLayout = new LinearLayout(app);
        linearLayout.setOrientation(LEVEL_CARD);
        linearLayout.setBackground(this.host.frostedBackground(22));
        linearLayout.setPadding(dp(14), dp(14), dp(14), dp(14));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(cardWidthDp()), -2));
        linearLayout.setClipChildren(false);
        linearLayout.setClickable(true);
        linearLayout.setOnTouchListener(new SwipeTapListener(this.host, new SwipeTapListener.Callback() { // from class: com.jarvis.bubblenotify.CallBubble.8
            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onTap() {
                CallBubble.this.resetIdle();
                CallBubble.this.openCallUi();
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onSwipeRight() {
                if (!zEquals) {
                    CallBubble.this.swipeToLevel(CallBubble.LEVEL_MINI);
                }
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onSwipeLeft() {
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onDragStart() {
                CallBubble.this.setSwipeMode(true);
                CallBubble.this.cancelIdle();
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onDragEnd() {
                CallBubble.this.setSwipeMode(false);
            }
        }));
        LinearLayout linearLayout2 = new LinearLayout(app);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setClipChildren(false);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        FrameLayout frameLayout = new FrameLayout(app);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        frameLayout.setClipChildren(false);
        this.pulseRing1 = null;
        this.pulseRing2 = null;
        this.answerHalo = null;
        FrameLayout frameLayoutMakeAvatar = makeAvatar(app, 46, 18.0f);
        frameLayoutMakeAvatar.setLayoutParams(new FrameLayout.LayoutParams(dp(46), dp(46)));
        this.avatarCharView = (TextView) frameLayoutMakeAvatar.getChildAt(0);
        frameLayout.addView(frameLayoutMakeAvatar);
        linearLayout2.addView(frameLayout);
        LinearLayout linearLayout3 = new LinearLayout(app);
        linearLayout3.setOrientation(LEVEL_CARD);
        linearLayout3.setGravity(16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(12);
        linearLayout3.setLayoutParams(layoutParams);
        this.nameView = new TextView(app);
        this.nameView.setTextColor(this.host.textMainColor());
        this.nameView.setTextSize(LEVEL_MINI, 16.5f);
        this.nameView.setTypeface(Typeface.DEFAULT_BOLD);
        this.nameView.setSingleLine(true);
        this.nameView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(this.nameView);
        this.numberView = new TextView(app);
        this.numberView.setTextColor(this.host.textDimColor());
        this.numberView.setTextSize(LEVEL_MINI, 12.5f);
        this.numberView.setSingleLine(true);
        this.numberView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(this.numberView);
        this.statusView = new TextView(app);
        this.statusView.setTextColor(-15961500);
        this.statusView.setTextSize(LEVEL_MINI, 12.0f);
        this.statusView.setSingleLine(true);
        linearLayout3.addView(this.statusView);
        linearLayout2.addView(linearLayout3);
        linearLayout.addView(linearLayout2);
        LinearLayout linearLayout4 = new LinearLayout(app);
        linearLayout4.setOrientation(0);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(14);
        linearLayout4.setLayoutParams(layoutParams2);
        if (zEquals) {
            linearLayout4.addView(makeActionColumn(app, VectorIcons.PHONE, 135.0f, COLOR_HANGUP, -1, 48, 21, "挂断", new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.9
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    CallBubble.this.hangupCall();
                }
            }));
            // fixed179: 扬声器接听 button removed from the ringing
            // card (2.0 way) — answer with the earpiece first, then
            // tap the speaker on the mini pill / card to switch.
            linearLayout4.addView(makeAnswerColumn(app));
        } else {
            linearLayout4.addView(makeActionColumn(app, VectorIcons.PHONE, 135.0f, COLOR_HANGUP, -1, 44, 20, "挂断", new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.11
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    CallBubble.this.hangupCall();
                }
            }));
            linearLayout4.addView(makeSpeakerColumn(app));
            linearLayout4.addView(makeTextActionColumn(app, "›", -14736603, COLOR_GHOST_BTN, "缩小", 44, 19.0f, new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.12
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    try {
                        CallBubble.this.showLevel(CallBubble.LEVEL_MINI);
                    } catch (Throwable th) {
                        CallBubble.this.log("Call minimize failed", th);
                    }
                }
            }));
        }
        linearLayout.addView(linearLayout4);
        View viewCapCardHeight = this.host.capCardHeight(linearLayout);
        this.cardView = viewCapCardHeight;
        refreshLevelContent();
        return viewCapCardHeight;
    }

    private LinearLayout makeAnswerColumn(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LEVEL_CARD);
        linearLayout.setGravity(LEVEL_CARD);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        frameLayout.setClipChildren(false);
        IconView iconView = new IconView(context);
        iconView.setIconXml(VectorIcons.PHONE);
        iconView.setIconColor(-1);
        iconView.setIconSizeDp(21);
        iconView.setIconBackground(COLOR_ANSWER, 999.0f);
        iconView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        iconView.setClickable(true);
        iconView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.13
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                CallBubble.this.answerCall(false);
            }
        });
        frameLayout.addView(iconView);
        linearLayout.addView(frameLayout);
        return linearLayout;
    }

    private LinearLayout makeSpeakerColumn(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LEVEL_CARD);
        linearLayout.setGravity(LEVEL_CARD);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.speakerBtnView = new IconView(context);
        this.speakerBtnView.setIconXml(VectorIcons.SPEAKER);
        this.speakerBtnView.setIconColor(-1);
        this.speakerBtnView.setIconSizeDp(20);
        this.speakerBtnView.setIconBackground(this.speakerOn ? -15961500 : COLOR_SPEAKER_ANSWER, 999.0f);
        if (this.speakerOn) {
            this.speakerBtnView.setIconBackgroundStroke(COLOR_SPEAKER_ON_RING, 2.0f);
        }
        this.speakerBtnView.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        this.speakerBtnView.setClickable(true);
        this.speakerBtnView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.14
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                CallBubble.this.toggleSpeaker();
            }
        });
        linearLayout.addView(this.speakerBtnView);
        return linearLayout;
    }

    private LinearLayout makeActionColumn(Context context, String str, float f, int i, int i2, int i3, int i4, String str2, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LEVEL_CARD);
        linearLayout.setGravity(LEVEL_CARD);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        IconView iconView = new IconView(context);
        iconView.setIconXml(str);
        iconView.setIconColor(i2);
        iconView.setIconSizeDp(i4);
        iconView.setIconRotation(f);
        iconView.setIconBackground(i, 999.0f);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(i3), dp(i3)));
        iconView.setClickable(true);
        iconView.setOnClickListener(onClickListener);
        linearLayout.addView(iconView);
        return linearLayout;
    }

    private LinearLayout makeTextActionColumn(Context context, String str, int i, int i2, String str2, int i3, float f, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LEVEL_CARD);
        linearLayout.setGravity(LEVEL_CARD);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextSize(LEVEL_MINI, f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(i);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i2);
        gradientDrawable.setShape(LEVEL_CARD);
        textView.setBackground(gradientDrawable);
        textView.setLayoutParams(new LinearLayout.LayoutParams(dp(i3), dp(i3)));
        textView.setClickable(true);
        textView.setOnClickListener(onClickListener);
        linearLayout.addView(textView);
        return linearLayout;
    }

    private TextView makeActionLabel(Context context, String str) {
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextSize(LEVEL_MINI, 11.0f);
        textView.setTextColor(this.host.textSubColor());
        textView.setGravity(17);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.topMargin = dp(6);
        textView.setLayoutParams(layoutParams);
        return textView;
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
            gradientDrawable.setStroke(dp(LEVEL_CARD), this.host.borderColor());
            linearLayout.setBackground(gradientDrawable);
        }
        linearLayout.setPadding(dp(6), dp(6), dp(8), dp(6));
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(capsuleWidthDp()), dp(capsuleHeightDp())));
        linearLayout.setClickable(true);
        linearLayout.setOnTouchListener(new SwipeTapListener(this.host, new SwipeTapListener.Callback() { // from class: com.jarvis.bubblenotify.CallBubble.15
            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onTap() {
                try {
                    if (!CallBubble.this.tapAllowed()) {
                        return;
                    }
                    CallBubble.this.showLevel(CallBubble.LEVEL_CARD);
                } catch (Throwable th2) {
                    CallBubble.this.log("Call mini tap failed", th2);
                }
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onSwipeRight() {
                CallBubble.this.swipeToLevel(3);
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onSwipeLeft() {
                CallBubble.this.dismissCallCapsule();
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onDragStart() {
                CallBubble.this.setSwipeMode(true);
                CallBubble.this.cancelIdle();
            }

            @Override // com.jarvis.bubblenotify.CallBubble.SwipeTapListener.Callback
            public void onDragEnd() {
                CallBubble.this.setSwipeMode(false);
            }
        }));
        FrameLayout frameLayoutMakeAvatar = makeAvatar(app, 38, 15.0f);
        frameLayoutMakeAvatar.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        this.miniAvatarCharView = (TextView) frameLayoutMakeAvatar.getChildAt(0);
        linearLayout.addView(frameLayoutMakeAvatar);
        LinearLayout linearLayout2 = new LinearLayout(app);
        linearLayout2.setOrientation(LEVEL_CARD);
        linearLayout2.setGravity(16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(8);
        linearLayout2.setLayoutParams(layoutParams);
        this.miniNameView = new TextView(app);
        this.miniNameView.setTextColor(this.host.textMainColor());
        this.miniNameView.setTextSize(LEVEL_MINI, 13.0f);
        this.miniNameView.setTypeface(Typeface.DEFAULT_BOLD);
        this.miniNameView.setSingleLine(true);
        this.miniNameView.setEllipsize(TextUtils.TruncateAt.END);
        this.miniNameView.setMaxWidth(dp(150));
        linearLayout2.addView(this.miniNameView);
        this.miniStatusView = new TextView(app);
        this.miniStatusView.setTextColor(-15961500);
        this.miniStatusView.setTextSize(LEVEL_MINI, 11.5f);
        this.miniStatusView.setSingleLine(true);
        this.miniStatusView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(this.miniStatusView);
        linearLayout.addView(linearLayout2);
        // fixed180: mini pill speaker button — the icon button
        // from the earlier picture (ringing card's blue speaker
        // button): SPEAKER icon, white, blue COLOR_SPEAKER_ANSWER
        // background, circular, 38dp like the hangup button.
        // Tap it to toggle the speaker (2.0: answer first, then
        // tap to switch).
        this.miniSpeakerBtnView = new IconView(app);
        this.miniSpeakerBtnView.setIconXml(VectorIcons.SPEAKER);
        this.miniSpeakerBtnView.setIconColor(-1);
        this.miniSpeakerBtnView.setIconSizeDp(18);
        this.miniSpeakerBtnView.setIconBackground(this.speakerOn ? -15961500 : COLOR_SPEAKER_ANSWER, 999.0f);
        if (this.speakerOn) {
            this.miniSpeakerBtnView.setIconBackgroundStroke(COLOR_SPEAKER_ON_RING, 2.0f);
        }
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(dp(38), dp(38));
        layoutParams2.leftMargin = dp(4);
        this.miniSpeakerBtnView.setLayoutParams(layoutParams2);
        this.miniSpeakerBtnView.setClickable(true);
        this.miniSpeakerBtnView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.17
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                CallBubble.this.toggleSpeaker();
            }
        });
        linearLayout.addView(this.miniSpeakerBtnView);
        IconView iconView = new IconView(app);
        iconView.setIconXml(VectorIcons.PHONE);
        iconView.setIconColor(-1);
        iconView.setIconSizeDp(18);
        iconView.setIconRotation(135.0f);
        iconView.setIconBackground(COLOR_HANGUP, 999.0f);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(dp(38), dp(38));
        layoutParams3.leftMargin = dp(6);
        iconView.setLayoutParams(layoutParams3);
        iconView.setClickable(true);
        iconView.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.CallBubble.16
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                CallBubble.this.hangupCall();
            }
        });
        linearLayout.addView(iconView);
        this.miniView = linearLayout;
        refreshLevelContent();
        return linearLayout;
    }

    private FrameLayout buildDockView() {
        Application app = this.host.getApp();
        FrameLayout frameLayout = new FrameLayout(app);
        frameLayout.setClipChildren(false);
        frameLayout.addView(makeAvatar(app, 38, 15.0f), new FrameLayout.LayoutParams(dp(38), dp(38), 8388627));
        FrameLayout frameLayout2 = new FrameLayout(app);
        frameLayout2.setClipChildren(false);
        frameLayout2.addView(frameLayout, new FrameLayout.LayoutParams(dp(22), dp(38)));
        this.dockView = frameLayout2;
        return frameLayout2;
    }

    private void refreshLevelContent() {
        try {
            int i = 8;
            if (this.level == LEVEL_CARD && this.cardView != null) {
                String strDisplayName = displayName();
                String strDisplayNumber = displayNumber();
                if (this.nameView != null) {
                    this.nameView.setText(strDisplayName);
                }
                if (this.numberView != null) {
                    if (strDisplayNumber == null || strDisplayNumber.isEmpty()) {
                        this.numberView.setVisibility(8);
                    } else {
                        this.numberView.setVisibility(0);
                        this.numberView.setText(strDisplayNumber);
                    }
                }
                if (this.statusView != null) {
                    this.statusView.setText(Settings.CALL_RINGING.equals(this.callState) ? "正在呼入…" : callTimeText());
                }
                if (this.avatarCharView != null) {
                    this.avatarCharView.setText(avatarChar());
                }
                updateSpeakerButton();
                return;
            }
            if (this.level == LEVEL_MINI && this.miniView != null) {
                if (this.miniNameView != null) {
                    this.miniNameView.setText(displayName());
                }
                if (this.miniStatusView != null) {
                    this.miniStatusView.setText(miniCallTimeText());
                }
                if (this.miniAvatarCharView != null) {
                    this.miniAvatarCharView.setText(avatarChar());
                }
                if (this.miniSpeakerBtnView != null) {
                    // fixed180: icon button — blue when off, teal
                    // + ring when on (same as the card's speaker
                    // button).
                    this.miniSpeakerBtnView.setIconBackground(this.speakerOn ? -15961500 : COLOR_SPEAKER_ANSWER, 999.0f);
                    this.miniSpeakerBtnView.setIconBackgroundStroke(this.speakerOn ? COLOR_SPEAKER_ON_RING : 0, this.speakerOn ? 2.0f : 0.0f);
                }
            }
        } catch (Throwable th) {
            log("refreshLevelContent failed", th);
        }
    }

    public void updateCallTimeTexts() {
        try {
            if (!Settings.CALL_OFFHOOK.equals(this.callState)) {
                return;
            }
            String strCallTimeText = callTimeText();
            if (this.level == LEVEL_CARD && this.statusView != null) {
                this.statusView.setText(strCallTimeText);
            }
            if (this.level == LEVEL_MINI && this.miniStatusView != null) {
                this.miniStatusView.setText(miniCallTimeText());
            }
        } catch (Throwable th) {
            log("updateCallTimeTexts failed", th);
        }
    }

    private void updateSpeakerButton() {
        try {
            // fixed177: speaker only after answering — dim + disable
            // the button while ringing so it cannot be tapped first.
            boolean answered = Settings.CALL_OFFHOOK.equals(this.callState);
            if (this.speakerBtnView != null) {
                this.speakerBtnView.setIconBackground(this.speakerOn ? -15961500 : COLOR_SPEAKER_ANSWER, 999.0f);
                this.speakerBtnView.setIconBackgroundStroke(this.speakerOn ? COLOR_SPEAKER_ON_RING : 0, this.speakerOn ? 2.0f : 0.0f);
                this.speakerBtnView.setClickable(answered);
                this.speakerBtnView.setAlpha(answered ? 1.0f : 0.4f);
            }
            // fixed180: the mini pill's icon button follows the
            // same state (the mini pill only shows after answering,
            // so it is always clickable there).
            if (this.miniSpeakerBtnView != null) {
                this.miniSpeakerBtnView.setIconBackground(this.speakerOn ? -15961500 : COLOR_SPEAKER_ANSWER, 999.0f);
                this.miniSpeakerBtnView.setIconBackgroundStroke(this.speakerOn ? COLOR_SPEAKER_ON_RING : 0, this.speakerOn ? 2.0f : 0.0f);
            }
            if (this.speakerLabel != null) {
                this.speakerLabel.setText(this.speakerOn ? "扬声器开" : "扬声器");
                this.speakerLabel.setAlpha(answered ? 1.0f : 0.4f);
            }
        } catch (Throwable th) {
            log("updateSpeakerButton failed", th);
        }
    }

    private String displayName() {
        if (this.callName != null && !this.callName.isEmpty()) {
            return this.callName;
        }
        if (this.callNumber != null && !this.callNumber.isEmpty()) {
            return this.callNumber;
        }
        return "未知号码";
    }

    private String displayNumber() {
        if (this.callNumber == null || this.callNumber.isEmpty() || this.callName == null || this.callName.isEmpty() || this.callName.equals(this.callNumber)) {
            return null;
        }
        return this.callNumber;
    }

    private String avatarChar() {
        String str = (this.callName == null || this.callName.isEmpty()) ? this.callNumber : this.callName;
        if (str == null || str.isEmpty()) {
            return "?";
        }
        return str.substring(0, Character.charCount(str.codePointAt(0)));
    }

    private String callTimeText() {
        long jMax = this.callStartElapsed > 0 ? Math.max(0L, (SystemClock.elapsedRealtime() - this.callStartElapsed) / CALL_TICK_MS) : 0L;
        long j = jMax / 60;
        long j2 = jMax % 60;
        return "通话中 " + j + ":" + (j2 < 10 ? "0" + j2 : String.valueOf(j2));
    }

    private String miniCallTimeText() {
        String strCallTimeText = callTimeText();
        return strCallTimeText.startsWith("通话中 ") ? strCallTimeText.substring(4) : strCallTimeText;
    }

    private FrameLayout makeAvatar(Context context, int i, float f) {
        FrameLayout frameLayout = new FrameLayout(context);
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{COLOR_AVATAR_START, COLOR_AVATAR_END});
        gradientDrawable.setShape(LEVEL_CARD);
        frameLayout.setBackground(gradientDrawable);
        TextView textView = new TextView(context);
        textView.setText(avatarChar());
        textView.setTextSize(LEVEL_MINI, f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-1);
        textView.setGravity(17);
        textView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(textView);
        return frameLayout;
    }

    private View makePulseRing(Context context, int i, int i2) {
        View view = new View(context);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(LEVEL_CARD);
        gradientDrawable.setStroke(dp(LEVEL_MINI), i2);
        view.setBackground(gradientDrawable);
        view.setLayoutParams(new FrameLayout.LayoutParams(i, i));
        view.setAlpha(0.0f);
        return view;
    }

    private TextView makeDockHandle(Context context) {
        TextView textView = new TextView(context);
        textView.setText("‹");
        textView.setTextSize(LEVEL_MINI, 16.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-14736603);
        textView.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(COLOR_HANDLE_BG);
        gradientDrawable.setCornerRadius(dp(99));
        gradientDrawable.setStroke(dp(LEVEL_CARD), COLOR_HANDLE_BORDER);
        textView.setBackground(gradientDrawable);
        textView.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(34)));
        return textView;
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
                return 302;
            }
            return Settings.CARD_WIDTH_LANDSCAPE_DP;
        } catch (Throwable th) {
            return 302;
        }
    }

    public boolean screenOn() {
        try {
            return this.host.isScreenOn();
        } catch (Throwable th) {
            return true;
        }
    }

    public void log(String str) {
        try {
            this.host.log(str);
        } catch (Throwable th) {
        }
    }

    public void log(String str, Throwable th) {
        try {
            this.host.log(str, th);
        } catch (Throwable th2) {
        }
    }

    public void setSwipeMode(boolean z) {
        try {
            if (this.host instanceof BubbleManager) {
                ((BubbleManager) this.host).setSwipeMode(z);
            }
        } catch (Throwable th) {
            log("setSwipeMode failed", th);
        }
    }

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

    private static class SwipeTapListener implements View.OnTouchListener {
        private final Callback callback;
        private float dockDragDownRawX;
        private float dockDragDownRawY;
        private Runnable dockDragLongPress;
        private long downTime;
        private float downX;
        private float downY;
        private boolean dragStarted;
        private boolean dragging;
        private final BubbleHost host;
        private VelocityTracker velocityTracker;

        interface Callback {
            void onDragEnd();

            void onDragStart();

            void onSwipeLeft();

            void onSwipeRight();

            void onTap();
        }

        SwipeTapListener(BubbleHost bubbleHost, Callback callback) {
            this.host = bubbleHost;
            this.callback = callback;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean mirrorSwipes() {
            try {
                return this.host != null && this.host.isDockLeft();
            } catch (Throwable th) {
                return false;
            }
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(final View view, MotionEvent motionEvent) {
            float xVelocity;
            try {
                int actionMasked = motionEvent.getActionMasked();
                if (actionMasked == 0) {
                    this.dockDragDownRawX = motionEvent.getRawX();
                    this.dockDragDownRawY = motionEvent.getRawY();
                    if (this.dockDragLongPress == null) {
                        this.dockDragLongPress = new Runnable() { // from class: com.jarvis.bubblenotify.CallBubble.SwipeTapListener.1
                            @Override // java.lang.Runnable
                            public void run() {
                                try {
                                    SwipeTapListener.this.host.startDockDrag();
                                } catch (Throwable th) {
                                }
                            }
                        };
                    }
                    this.host.getHandler().postDelayed(this.dockDragLongPress, ViewConfiguration.getLongPressTimeout());
                } else if (actionMasked == CallBubble.LEVEL_MINI) {
                    if (this.host.isDockDragging()) {
                        this.host.moveDockDrag(motionEvent.getRawY());
                        return true;
                    }
                    float rawX = motionEvent.getRawX() - this.dockDragDownRawX;
                    float rawY = motionEvent.getRawY() - this.dockDragDownRawY;
                    int scaledTouchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                    if ((rawX * rawX) + (rawY * rawY) > scaledTouchSlop * scaledTouchSlop) {
                        this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    }
                } else if (actionMasked == CallBubble.LEVEL_CARD || actionMasked == 3) {
                    this.host.getHandler().removeCallbacks(this.dockDragLongPress);
                    if (this.host.isDockDragging()) {
                        this.host.endDockDrag(motionEvent.getRawX(), motionEvent.getRawY());
                        return true;
                    }
                }
            } catch (Throwable th) {
            }
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
                    case CallBubble.LEVEL_CARD /* 1 */:
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
                            view.animate().withLayer().translationX(-view.getWidth()).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.CallBubble.SwipeTapListener.2
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
                            view.animate().withLayer().translationX(view.getWidth()).alpha(0.0f).setDuration(180L).setListener(new AnimatorListenerAdapter() { // from class: com.jarvis.bubblenotify.CallBubble.SwipeTapListener.3
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
                            view.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(180L).start();
                        } else if (jUptimeMillis < 500 && Math.abs(x) < f * 10.0f) {
                            this.callback.onTap();
                        }
                        return true;
                    case CallBubble.LEVEL_MINI /* 2 */:
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
                            view.setAlpha(1.0f - Math.min(0.5f, (Math.abs(x2) / Math.max(CallBubble.LEVEL_CARD, view.getWidth())) * 0.8f));
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
                            view.animate().withLayer().translationX(0.0f).alpha(1.0f).setDuration(180L).start();
                        }
                        return true;
                    default:
                        return false;
                }
            } catch (Throwable th2) {
                return false;
            }
        }
    }
}
