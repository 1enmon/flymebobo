package com.jarvis.bubblenotify;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.View;

/* JADX INFO: loaded from: classes.dex */
public interface BubbleHost {
    void addDockView(View view);

    int dp(int i);

    GradientDrawable frostedBackground(int i);

    // fixed58: adaptive frosted glass. The glass (pill + card
    // backgrounds) follows the wallpaper brightness behind the bubbles:
    // bright wallpaper -> dark glass, dark wallpaper -> light glass.
    // Text sitting on glass must flip with it, so the host owns the
    // current text colors too — bubbles ask instead of using fixed
    // dark constants. (Text on coloured chips keeps its own colour.)
    // fixed64: the border colour flips with the glass too — a visible
    // edge on both themes, so every card and capsule reads as a
    // bordered panel instead of blending into the wallpaper.
    boolean isDarkGlass();

    int borderColor();

    int textMainColor();

    int textSubColor();

    int textDimColor();

    Application getApp();

    Handler getHandler();

    boolean isEnabled();

    boolean isLandscape();

    boolean isScreenOn();

    void log(String str);

    void log(String str, Throwable th);

    void openIntent(Intent intent);

    void openPendingIntent(PendingIntent pendingIntent);

    GradientDrawable pillBackground();

    void removeDockView(View view);

    // fixed63: the transparent outside-tap layer is deleted (user
    // 2026-11-17 19:46 "不要透明点击层了"). The host tracks whether a
    // notification card is open instead: while one is, the dock's
    // bottom margin pins the card's bottom edge to the user's offset;
    // otherwise the margin is 0 and the capsules sit 14dp up.
    void setCardOpen(boolean open);


    void applyCardBottomOffset();

    void addDockViewAtBottom(View view);

    // fixed49: shared docked pile. All docked icons (player disc, call
    // avatar, nav icon, notification icons) overlap in ONE pile at the
    // edge, above the capsules, with a single centered handle — instead
    // of each bubble owning a separate docked row. A bubble adds just its
    // icon (no handle, no row); the pile provides the handle and the
    // overlapping layout. onTap runs when the icon is tapped.
    void addDockedIcon(View icon, Runnable onTap);

    void removeDockedIcon(View icon);

    // fixed69: the pile opens and closes as one unit at capsule level
    // (user 2026-11-17 20:58-20:59: tapping any docked icon expands the
    // player capsule together; shrinking to dock docks everything
    // together). Cards stay strictly tap-to-open (user 21:01).
    void expandPlayerToMini();

    void dockPlayerTogether();

    void dockNotifsTogether();

    void sendCommand(Intent intent);

    void sendDismiss(String str);

    void sendMusicControl(String str);

    void sendMusicSeek(long j);

    // fixed74: unified focus — at most one bubble is "in focus" (a
    // fresh capsule pop or an expanded card); every other dock child
    // (other capsules and the docked pile) is hidden until the focus
    // ends. A fresh capsule pop and an expanded card take the focus;
    // tapping a docked icon clears it and expands ALL capsules.
    void setFocusView(View view);

    void clearFocus();

    boolean isFocusActive();

    boolean isFocusView(View view);

    // fixed76: true when the view holding the focus is an expanded card
    // (302dp / 262dp landscape wide) still in the dock. A new
    // notification capsule may steal the focus from another capsule,
    // but never from a card (e.g. a ringing call card).
    boolean isFocusCard();

    // fixed74: expand-all — tapping any docked icon expands every
    // docked bubble to its capsule (notifications, player, call, nav),
    // superseding fixed69's expandPlayerToMini for pile taps.
    void expandAllToMini();

    // fixed102: user-adjustable bubble size / opacity / corner radius.
    int capsuleWidthDp();

    int capsuleHeightDp();

    int cardWidthDp();

    int cardHeightDp();

    int bubbleOpacity();

    int capsuleCornerDp();

    int cardCornerDp();

    // fixed102: cap an expanded card at the user-set height (0 = auto);
    // the content scrolls inside when capped, so nothing is cut off.
    View capCardHeight(View content);

    // fixed103: true when the dock sits on the left edge. Swipes are
    // mirrored then (right side unchanged, left side mirrored).
    boolean isDockLeft();

    // fixed116: true when the dock sits in the upper half of the
    // screen. Pinned capsules anchor at the edge side; other capsules
    // go below the pin up top, above it down below.
    boolean isDockUpper();

    // fixed119: move the docked pile to the centre side of the pinned
    // capsule (below the capsules up top, above them down below).
    // Called when the pile is added, when the dock moves, and when a
    // pin toggles.
    void repositionPile();

    // fixed112: long-press drag to move the dock — not just the docked
    // pile (fixed80) but capsules and expanded cards too. A bubble's
    // swipe listener long-presses into this drag; the host moves the
    // whole window and snaps side/height on release.
    void startDockDrag();

    void moveDockDrag(float rawY);

    void endDockDrag(float rawX, float rawY);

    boolean isDockDragging();

    void updateWindowSize();
}
