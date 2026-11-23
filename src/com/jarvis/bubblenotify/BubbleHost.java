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


    void addDockViewAtBottom(View view);

    void addDockedIcon(View view, Runnable runnable);

    void applyCardBottomOffset();

    int appRuleFor(String str);

    int borderColor();

    int bubbleOpacity();

    View capCardHeight(View view);

    View capMusicCardHeight(View view);

    int capsuleCornerDp();

    int capsuleHeightDp();

    int capsuleWidthDp();

    int cardCornerDp();

    int cardHeightDp();

    int cardWidthDp();

    void clearFocus();

    void dockNotifsTogether();

    void dockPlayerTogether();

    int dp(int i);

    void endDockDrag(float f, float f2);

    void expandAllToMini();

    void expandPlayerToMini();

    GradientDrawable frostedBackground(int i);

    Application getApp();

    Handler getHandler();

    boolean isDarkGlass();

    boolean isDockDragging();

    boolean isDockLeft();

    boolean isDockUpper();

    boolean isEnabled();

    boolean isFocusActive();

    boolean isFocusCard();

    boolean isFocusView(View view);

    boolean isLandscape();

    boolean isScreenOn();

    boolean isSceneSuppressed();

    void log(String str);

    void log(String str, Throwable th);

    void moveDockDrag(float f);

    int musicCardCornerDp();

    int musicCardHeightDp();

    int musicCardOpacity();

    int musicCardWidthDp();

    GradientDrawable musicFrostedBackground();

    void openIntent(Intent intent);

    void openPendingIntent(PendingIntent pendingIntent);

    GradientDrawable pillBackground();

    void removeDockView(View view);

    void removeDockedIcon(View view);

    void repositionPile();

    void sendCommand(Intent intent);

    void sendDismiss(String str);

    void sendMusicControl(String str);

    void sendMusicSeek(long j);

    void setCardOpen(boolean z);

    void setFocusView(View view);

    void startDockDrag();

    int textDimColor();

    int textMainColor();

    int textSubColor();

    void updateWindowSize();
}
