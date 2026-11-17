package com.jarvis.bubblenotify;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends Activity {
    // fixed35: request code for exporting the diagnostic log file.
    // fixed81: "格窗工作台" style — light grey page, white rounded cards,
    // title + subtitle rows, hairline dividers, airy spacing.
    private static final int COLOR_PAGE_BG = 0xFFF5F5F5;
    private static final int COLOR_CARD_BG = 0xFFFFFFFF;
    private static final int COLOR_TITLE = 0xFF1A1A1A;
    private static final int COLOR_SUBTITLE = 0xFF8A8A8A;
    private static final int COLOR_BODY = 0xFF666666;
    private static final int COLOR_FOOTER = 0xFF999999;
    private static final int COLOR_ACCENT = 0xFF2CB33E;
    private static final int COLOR_ACCENT_PRESSED = 0xFF259B35;
    private static final int COLOR_DIVIDER = 0xFFEEEEEE;
    private static final int COLOR_BTN_BG = 0xFFF0F0F0;
    private static final int COLOR_BTN_PRESSED = 0xFFE2E2E2;
    private static final int COLOR_BAD = 0xFFE53935;
    private SharedPreferences prefs;
    // fixed102: the card-position card is hidden while card-expand is off.
    private LinearLayout offsetCard;
    private TextView statusText;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // fixed82: drop the black title bar (the app sets no theme, so
        // the default theme draws a dark ActionBar above the page).
        // The page starts with the large title on the light-grey
        // background instead, like the Flyme settings page.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        this.prefs = getSharedPreferences(Settings.PREFS_NAME, 0);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_PAGE_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        scrollView.addView(content, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scrollView);
        // fixed82: hide the ActionBar if the theme still provides one,
        // and make the status bar light (grey background, dark icons)
        // so the whole top reads like the Flyme settings page.
        try {
            android.app.ActionBar ab = getActionBar();
            if (ab != null) {
                ab.hide();
            }
        } catch (Throwable ignored) {
        }
        try {
            getWindow().setStatusBarColor(COLOR_PAGE_BG);
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Throwable ignored) {
        }
        // Header: large bold title + grey tagline.
        TextView title = new TextView(this);
        title.setText("煤气泡");
        title.setTextSize(28.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TITLE);
        content.addView(title);
        TextView tagline = new TextView(this);
        tagline.setText("右下角气泡 · 音乐 · 来电 · 验证码");
        tagline.setTextSize(13.0f);
        tagline.setTextColor(COLOR_SUBTITLE);
        tagline.setPadding(0, dp(4), 0, 0);
        content.addView(tagline);
        // Status card: notification-listener permission + setup steps +
        // the button that opens the permission settings.
        LinearLayout statusCard = makeCard(content);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(0);
        statusRow.setGravity(16);
        statusRow.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView statusLabel = new TextView(this);
        statusLabel.setText("通知访问权限");
        statusLabel.setTextSize(16.0f);
        statusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        statusLabel.setTextColor(COLOR_TITLE);
        statusRow.addView(statusLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.statusText = new TextView(this);
        this.statusText.setTextSize(14.0f);
        this.statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(this.statusText);
        statusCard.addView(statusRow);
        addDivider(statusCard, 16);
        TextView steps = new TextView(this);
        steps.setTextSize(13.0f);
        steps.setTextColor(COLOR_BODY);
        steps.setLineSpacing(dp(4), 1.0f);
        steps.setPadding(dp(16), dp(12), dp(16), dp(12));
        steps.setText("① LSPosed 里启用本模块并勾选“系统界面”\n② 系统设置里授予通知访问权限\n③ 重启手机（或重启 SystemUI）生效\n\n新通知在屏幕右下角弹出气泡：点气泡打开应用，左滑消除，右滑缩小，无操作自动逐级缩小贴边。贴边气泡长按可拖动换位置。");
        statusCard.addView(steps);
        Button setupBtn = new Button(this);
        setupBtn.setText("打开通知访问权限设置");
        setupBtn.setTextSize(15.0f);
        setupBtn.setTextColor(-1);
        setupBtn.setAllCaps(false);
        setupBtn.setBackground(roundedButtonBg(COLOR_ACCENT, COLOR_ACCENT_PRESSED, 14));
        setupBtn.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MainActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    MainActivity.this.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Throwable th) {
                }
            }
        });
        LinearLayout.LayoutParams setupLp = new LinearLayout.LayoutParams(-1, dp(48));
        setupLp.leftMargin = dp(16);
        setupLp.rightMargin = dp(16);
        setupLp.bottomMargin = dp(16);
        statusCard.addView(setupBtn, setupLp);
        // Features card: title + subtitle switch rows.
        LinearLayout featureCard = makeCard(content);
        addCardTitle(featureCard, "功能");
        addSwitch(featureCard, "总开关", "启用煤气泡", Settings.KEY_ENABLED, true);
        addDivider(featureCard, 16);
        addSwitch(featureCard, "音乐", "播放时气泡变成播放器，带旋转唱片", Settings.KEY_MUSIC_ENABLED, true);
        addDivider(featureCard, 16);
        addSwitch(featureCard, "来电", "来电时气泡显示接听、挂断、扬声器接听", Settings.KEY_CALL_ENABLED, true);
        addDivider(featureCard, 16);
        // fixed32: hide the system incoming-call fullscreen page (ported
        // from Flyme Island). Needs the module scoped to the Phone app
        // (com.android.incallui) in LSPosed too; tap the call card to
        // open the system call page.
        addSwitch(featureCard, "隐藏系统通话提示", "来电只显示气泡，不弹系统来电全屏页；点来电卡片可打开系统通话页", Settings.KEY_HIDE_CALL_UI, true);
        addDivider(featureCard, 16);
        addSwitch(featureCard, "点击气泡打开 App", "点气泡打开对应应用", Settings.KEY_TAP_OPEN, true);
        addDivider(featureCard, 16);
        addSwitch(featureCard, "卡片展开", "点胶囊展开卡片；关了点胶囊直接打开 App，小窗或全屏跟小窗开关", Settings.KEY_CARD_EXPAND, true);
        addDivider(featureCard, 16);
        addSwitch(featureCard, "小窗打开", "点气泡以自由窗口小窗打开应用，关闭则全屏", Settings.KEY_FREEFORM, true);
        addSpacer(featureCard, 4);
        // fixed38: user-adjustable expanded-card position. A slider sets
        // how far above the bottom edge the expanded card sits (0-100dp,
        // default 32). Dragging it pushes the new offset to SystemUI
        // live, so an open card moves as you drag — no reopen needed.
        this.offsetCard = makeCard(content);
        addCardTitle(this.offsetCard, "卡片展开位置");
        addCardSubtitle(this.offsetCard, "展开的卡片离屏幕底边的距离");
        addCardOffsetSlider(this.offsetCard);
        // fixed102: user-adjustable bubble size / opacity / corner radius.
        LinearLayout sizeCard = makeCard(content);
        addCardTitle(sizeCard, "气泡大小");
        addCardSubtitle(sizeCard, "胶囊与卡片的长短、高低、透明度与圆角；改完立即生效，内容自适应");
        addSizeSlider(sizeCard, "胶囊宽度", Settings.KEY_CAPSULE_WIDTH_DP, Settings.DEFAULT_CAPSULE_WIDTH_DP, 120, 320, "dp", false);
        addSizeSlider(sizeCard, "胶囊高度", Settings.KEY_CAPSULE_HEIGHT_DP, Settings.DEFAULT_CAPSULE_HEIGHT_DP, 40, 80, "dp", false);
        addSizeSlider(sizeCard, "卡片宽度", Settings.KEY_CARD_WIDTH_DP, Settings.DEFAULT_CARD_WIDTH_DP, 240, 380, "dp", false);
        addSizeSlider(sizeCard, "卡片高度", Settings.KEY_CARD_HEIGHT_DP, Settings.DEFAULT_CARD_HEIGHT_DP, 0, 600, "dp", true);
        addSizeSlider(sizeCard, "透明度", Settings.KEY_BUBBLE_OPACITY, Settings.DEFAULT_BUBBLE_OPACITY, 0, 100, "%", false);
        addSizeSlider(sizeCard, "胶囊圆角", Settings.KEY_CAPSULE_CORNER_DP, Settings.DEFAULT_CAPSULE_CORNER_DP, 0, 52, "dp", false);
        addSizeSlider(sizeCard, "卡片圆角", Settings.KEY_CARD_CORNER_DP, Settings.DEFAULT_CARD_CORNER_DP, 0, 40, "dp", false);
        addSizeResetButton(sizeCard);
        refreshOffsetCardVisibility();
        // fixed71: code recognition is local now (was AI via a remote
        // endpoint) — no switch, no Base URL / Key / model to fill in.
        // fixed81: the old note below still described the remote AI
        // endpoint; rewritten for local recognition.
        LinearLayout codeCard = makeCard(content);
        addCardTitle(codeCard, "验证码 / 取件码");
        addCardSubtitle(codeCard, "本机本地识别，不联网、不上传。短信里的验证码和取件码会在气泡下方大字显示，点一下复制，复制后自动消除这条通知。");
        // fixed89: customisable recognition rules — keyword lists,
        // "|" separated. An edit takes effect on the next notification
        // (LocalCodeExtractor recompiles when the rules JSON changes).
        addCardSubtitle(codeCard, "识别规则（关键词用 | 隔开，改完下一条通知就生效）：");
        addRuleField(codeCard, "取件码关键词", "pickup", LocalCodeExtractor.DEF_PICKUP);
        addRuleField(codeCard, "验证码关键词", "verify", LocalCodeExtractor.DEF_VERIFY);
        addRuleField(codeCard, "码在前关键词", "verifyBefore", LocalCodeExtractor.DEF_VERIFY_BEFORE);
        addRuleField(codeCard, "英文关键词", "verifyEn", LocalCodeExtractor.DEF_VERIFY_EN);
        addRuleField(codeCard, "排除词（裸数字不认）", "exclude", LocalCodeExtractor.DEF_EXCLUDE);
        addBareSwitch(codeCard);
        addRuleResetButton(codeCard);
        addSpacer(codeCard, 8);
        // Test card: two-column grid of test buttons.
        LinearLayout testCard = makeCard(content);
        addCardTitle(testCard, "测试");
        LinearLayout testRow1 = addButtonRow(testCard);
        addTestButton(testRow1, "测试通知", new Runnable() { // from class: com.jarvis.bubblenotify.MainActivity.2
            @Override // java.lang.Runnable
            public void run() {
                Intent intent = new Intent(Settings.ACTION_NOTIFY);
                intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                intent.putExtra(Settings.EXTRA_TITLE, "测试通知");
                intent.putExtra(Settings.EXTRA_TEXT, "这是一条测试通知: 点气泡打开应用, 左滑消除, 右滑缩小。");
                intent.putExtra(Settings.EXTRA_PACKAGE, MainActivity.this.getPackageName());
                MainActivity.this.sendBroadcast(intent);
            }
        });
        addTestButton(testRow1, "测试音乐", new Runnable() { // from class: com.jarvis.bubblenotify.MainActivity.3
            @Override // java.lang.Runnable
            public void run() {
                Intent intent = new Intent(Settings.ACTION_MUSIC);
                intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                intent.putExtra(Settings.EXTRA_MUSIC_ACTIVE, true);
                intent.putExtra(Settings.EXTRA_MUSIC_PLAYING, true);
                intent.putExtra(Settings.EXTRA_MUSIC_TITLE, "夜曲");
                intent.putExtra(Settings.EXTRA_MUSIC_ARTIST, "周杰伦");
                intent.putExtra(Settings.EXTRA_MUSIC_DURATION, 240000L);
                intent.putExtra(Settings.EXTRA_MUSIC_POSITION, 30000L);
                intent.putExtra(Settings.EXTRA_PACKAGE, "com.test.music");
                MainActivity.this.sendBroadcast(intent);
            }
        });
        LinearLayout testRow2 = addButtonRow(testCard);
        addTestButton(testRow2, "测试来电", new Runnable() { // from class: com.jarvis.bubblenotify.MainActivity.4
            @Override // java.lang.Runnable
            public void run() {
                Intent intent = new Intent(Settings.ACTION_CALL);
                intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                intent.putExtra(Settings.EXTRA_CALL_STATE, Settings.CALL_RINGING);
                intent.putExtra(Settings.EXTRA_CALL_NAME, "张三");
                intent.putExtra(Settings.EXTRA_CALL_NUMBER, "13800138000");
                MainActivity.this.sendBroadcast(intent);
            }
        });
        LinearLayout testRow3 = addButtonRow(testCard);
        addTestButton(testRow3, "测试验证码", new Runnable() { // from class: com.jarvis.bubblenotify.MainActivity.7
            @Override // java.lang.Runnable
            public void run() {
                // fixed89: the old button only sent ACTION_CODE with a
                // fake key "test:code", which onAiCode dropped (no
                // notification has that key) — so the button did
                // nothing. Now it posts a test notification AND its
                // code capsule in one tap via ACTION_TEST_CODE.
                Intent intent = new Intent(Settings.ACTION_TEST_CODE);
                intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                intent.putExtra(Settings.EXTRA_TITLE, "测试短信");
                intent.putExtra(Settings.EXTRA_TEXT, "您的验证码是 482913，请勿泄露。");
                intent.putExtra(Settings.EXTRA_CODE_KIND, Settings.CODE_KIND_VERIFY);
                intent.putExtra(Settings.EXTRA_CODE, "482913");
                MainActivity.this.sendBroadcast(intent);
            }
        });
        addSpacer(testCard, 10);
        TextView footer = new TextView(this);
        footer.setTextSize(12.0f);
        footer.setTextColor(COLOR_FOOTER);
        footer.setGravity(17);
        footer.setPadding(0, dp(20), 0, 0);
        footer.setText("v" + appVersionName() + " · 煤气泡 · 右下角气泡 · 本地验证码识别");
        content.addView(footer);
        pushSettings();
    }


    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean ok = isListenerEnabled();
        this.statusText.setText(ok ? "已授予 ✓" : "未授予 ✗");
        this.statusText.setTextColor(ok ? COLOR_ACCENT : COLOR_BAD);
    }

    private boolean isListenerEnabled() {
        try {
            String string = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (string == null) {
                return false;
            }
            return string.contains(new ComponentName(this, (Class<?>) NotificationService.class).flattenToString());
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pushSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.setPackage(Settings.SYSTEMUI_PACKAGE);
            Settings.putAllToIntent(intent, this.prefs);
            sendBroadcast(intent);
        } catch (Throwable th) {
        }
    }

    // fixed46: durable offset file (see Settings.OFFSET_FILE_PATH).
    // Returns the saved offset in dp, or -1 when there is no file or it
    // does not hold a valid value. All failures are swallowed — the
    // caller falls back to SharedPreferences.
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

    static void writeOffsetFile(int dp) {
        try {
            java.io.FileWriter w = new java.io.FileWriter(Settings.OFFSET_FILE_PATH, false);
            w.write(String.valueOf(dp));
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    // fixed81: white rounded card (20dp radius) on the grey page.
    private LinearLayout makeCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setBackground(roundedBg(COLOR_CARD_BG, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(card, lp);
        return card;
    }

    // fixed81: bold card title, 16sp.
    private void addCardTitle(LinearLayout card, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(16.0f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(COLOR_TITLE);
        tv.setPadding(dp(16), dp(16), dp(16), dp(8));
        card.addView(tv);
    }

    // fixed81: grey subtitle under a card title.
    private void addCardSubtitle(LinearLayout card, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13.0f);
        tv.setTextColor(COLOR_SUBTITLE);
        tv.setLineSpacing(dp(4), 1.0f);
        tv.setPadding(dp(16), dp(0), dp(16), dp(8));
        card.addView(tv);
    }

    // fixed81: hairline divider between card rows.
    private void addDivider(LinearLayout parent, int leftMarginDp) {
        View v = new View(this);
        v.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.leftMargin = dp(leftMarginDp);
        parent.addView(v, lp);
    }

    // fixed81: small bottom spacer inside a card.
    private void addSpacer(LinearLayout parent, int heightDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(heightDp)));
    }

    // fixed81: title + subtitle row with a Switch on the right.
    private void addSwitch(LinearLayout card, String title, String subtitle, final String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(1);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15.0f);
        titleView.setTextColor(COLOR_TITLE);
        textCol.addView(titleView);
        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextSize(12.0f);
        subView.setTextColor(COLOR_SUBTITLE);
        subView.setPadding(0, dp(2), 0, 0);
        textCol.addView(subView);
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Switch sw = new Switch(this);
        // fixed82: blue iOS-style switch, like the Flyme settings page
        // (blue track when on, light grey when off, white knob).
        try {
            android.content.res.ColorStateList trackTint = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                    new int[]{ 0xFF007AFF, 0xFFE5E5E5 });
            android.content.res.ColorStateList thumbTint = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                    new int[]{ 0xFFFFFFFF, 0xFFFFFFFF });
            sw.setTrackTintList(trackTint);
            sw.setThumbTintList(thumbTint);
        } catch (Throwable ignored) {
        }
        sw.setChecked(this.prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.jarvis.bubblenotify.MainActivity.8
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                MainActivity.this.prefs.edit().putBoolean(key, z2).apply();
                MainActivity.this.pushSettings();
                // fixed102: the card-position card only makes sense while
                // a card can expand; hide it when card-expand is off.
                if (Settings.KEY_CARD_EXPAND.equals(key)) {
                    MainActivity.this.refreshOffsetCardVisibility();
                }
            }
        });
        row.addView(sw);
        card.addView(row);
    }

    // fixed102: the card-position card is useless while card-expand is
    // off (no card ever expands, so there is nothing to position).
    private void refreshOffsetCardVisibility() {
        if (this.offsetCard == null) {
            return;
        }
        boolean expand = this.prefs.getBoolean(Settings.KEY_CARD_EXPAND, true);
        this.offsetCard.setVisibility(expand ? View.VISIBLE : View.GONE);
    }

    // fixed102: generic range slider for the bubble-size card. min/max are
    // the value bounds; the SeekBar runs 0..(max-min). zeroIsAuto shows
    // "自适应" for 0 (card height).
    private void addSizeSlider(LinearLayout card, String label, final String key, int def, final int min, int max, final String suffix, final boolean zeroIsAuto) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13.0f);
        labelView.setTextColor(COLOR_SUBTITLE);
        labelView.setPadding(dp(16), dp(10), dp(16), 0);
        card.addView(labelView);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(16), dp(4), dp(16), dp(8));
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        int current = this.prefs.getInt(key, def);
        if (current < min) {
            current = min;
        }
        if (current > max) {
            current = max;
        }
        seekBar.setProgress(current - min);
        final TextView valueView = new TextView(this);
        valueView.setTextSize(15.0f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(COLOR_ACCENT);
        valueView.setText(displaySizeValue(current, suffix, zeroIsAuto));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int v = progress + min;
                valueView.setText(displaySizeValue(v, suffix, zeroIsAuto));
                MainActivity.this.prefs.edit().putInt(key, v).apply();
                MainActivity.this.pushSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-2, -2);
        valueLp.leftMargin = dp(12);
        row.addView(valueView, valueLp);
        card.addView(row);
        // fixed109: remember this slider for "restore defaults".
        SizeSlider ss = new SizeSlider();
        ss.key = key;
        ss.def = def;
        ss.min = min;
        ss.suffix = suffix;
        ss.zeroIsAuto = zeroIsAuto;
        ss.seekBar = seekBar;
        ss.valueView = valueView;
        this.sizeSliders.add(ss);
    }

    // fixed109: reset all bubble-size sliders to their defaults.
    private void addSizeResetButton(LinearLayout card) {
        Button button = new Button(this);
        button.setText("恢复默认值");
        button.setTextSize(14.0f);
        button.setTextColor(COLOR_TITLE);
        button.setAllCaps(false);
        button.setBackground(roundedButtonBg(COLOR_BTN_BG, COLOR_BTN_PRESSED, 12));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    android.content.SharedPreferences.Editor ed = MainActivity.this.prefs.edit();
                    for (SizeSlider ss : MainActivity.this.sizeSliders) {
                        ed.putInt(ss.key, ss.def);
                        if (ss.seekBar != null) {
                            ss.seekBar.setProgress(ss.def - ss.min);
                        }
                        if (ss.valueView != null) {
                            ss.valueView.setText(displaySizeValue(ss.def, ss.suffix, ss.zeroIsAuto));
                        }
                    }
                    ed.apply();
                    MainActivity.this.pushSettings();
                    Toast.makeText(MainActivity.this, "已恢复默认值", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.topMargin = dp(8);
        card.addView(button, lp);
    }

    private String displaySizeValue(int v, String suffix, boolean zeroIsAuto) {
        if (zeroIsAuto && v == 0) {
            return "自适应";
        }
        return v + suffix;
    }

    // fixed38: slider for the expanded card's bottom offset (0-100dp).
    // Saves to prefs on every user drag and pushes the setting to
    // SystemUI live, so an open card moves as the slider moves.
    // fixed81: restyled to live inside its card — SeekBar + green value
    // on one row, under the card title + subtitle.
    private void addCardOffsetSlider(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(16), dp(8), dp(16), dp(16));
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        // fixed46: the offset must survive an app reinstall. The
        // SharedPreferences are wiped on reinstall, but the durable file
        // in /data/local/tmp is not. On startup, prefer the file's value
        // (and restore it into the prefs so the two stay in sync); fall
        // back to the prefs when there is no file yet.
        int current = readOffsetFile();
        if (current < 0) {
            current = this.prefs.getInt(Settings.KEY_CARD_BOTTOM_OFFSET_DP, Settings.DEFAULT_CARD_BOTTOM_OFFSET_DP);
        } else {
            this.prefs.edit().putInt(Settings.KEY_CARD_BOTTOM_OFFSET_DP, current).apply();
        }
        seekBar.setProgress(current);
        final TextView valueView = new TextView(this);
        valueView.setTextSize(15.0f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(COLOR_ACCENT);
        valueView.setText(current + "dp");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                valueView.setText(progress + "dp");
                MainActivity.this.prefs.edit().putInt(Settings.KEY_CARD_BOTTOM_OFFSET_DP, progress).apply();
                // fixed46: also write the durable file so the position
                // survives an app reinstall + reboot.
                writeOffsetFile(progress);
                MainActivity.this.pushSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-2, -2);
        valueLp.leftMargin = dp(12);
        row.addView(valueView, valueLp);
        card.addView(row);
    }

    private EditText addTextSetting(LinearLayout linearLayout, String str, final String str2, String str3, int i, String str4) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(14.0f);
        textView.setPadding(0, dp(10), 0, dp(2));
        linearLayout.addView(textView);
        final EditText editText = new EditText(this);
        editText.setText(this.prefs.getString(str2, str3));
        editText.setHint(str4);
        editText.setInputType(i);
        editText.setSingleLine(true);
        editText.setTextSize(14.0f);
        editText.setImeOptions(6);
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.jarvis.bubblenotify.MainActivity.9
            @Override // android.view.View.OnFocusChangeListener
            public void onFocusChange(View view, boolean z) {
                if (!z) {
                    MainActivity.this.prefs.edit().putString(str2, editText.getText().toString().trim()).apply();
                    MainActivity.this.pushSettings();
                }
            }
        });
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.jarvis.bubblenotify.MainActivity.10
            @Override // android.widget.TextView.OnEditorActionListener
            public boolean onEditorAction(TextView textView2, int i2, KeyEvent keyEvent) {
                if (i2 == 6) {
                    MainActivity.this.prefs.edit().putString(str2, editText.getText().toString().trim()).apply();
                    MainActivity.this.pushSettings();
                    editText.clearFocus();
                    return true;
                }
                return false;
            }
        });
        linearLayout.addView(editText, new LinearLayout.LayoutParams(-1, -2));
        return editText;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable th) {
            return "";
        }
    }

    // fixed81: a horizontal row inside the test card (two grid buttons).
    private LinearLayout addButtonRow(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setPadding(dp(16), dp(6), dp(16), dp(6));
        card.addView(row);
        return row;
    }

    // fixed81: light-grey rounded grid button (two per row, 48dp tall).
    // fixed89: rule-editor fields (one EditText per keyword list).
    private final java.util.ArrayList<android.widget.EditText> ruleEdits = new java.util.ArrayList<android.widget.EditText>();
    // fixed109: the 7 bubble-size sliders, so "restore defaults" can
    // move them back. Each entry holds key / default / min / suffix /
    // zeroIsAuto / SeekBar / value TextView.
    private static class SizeSlider {
        String key;
        int def;
        int min;
        String suffix;
        boolean zeroIsAuto;
        android.widget.SeekBar seekBar;
        android.widget.TextView valueView;
    }
    private final java.util.ArrayList<SizeSlider> sizeSliders = new java.util.ArrayList<SizeSlider>();
    private android.widget.Switch bareSwitch;

    private String getRuleValue(String jsonKey, String def) {
        return LocalCodeExtractor.readRule(this.prefs, jsonKey, def);
    }

    private void setRuleValue(String jsonKey, String value) {
        try {
            String json = this.prefs.getString(Settings.KEY_CODE_RULES, null);
            org.json.JSONObject obj = (json == null || json.isEmpty()) ? new org.json.JSONObject() : new org.json.JSONObject(json);
            obj.put(jsonKey, value == null ? "" : value);
            this.prefs.edit().putString(Settings.KEY_CODE_RULES, obj.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private void setBareOn(boolean on) {
        try {
            String json = this.prefs.getString(Settings.KEY_CODE_RULES, null);
            org.json.JSONObject obj = (json == null || json.isEmpty()) ? new org.json.JSONObject() : new org.json.JSONObject(json);
            obj.put("bareOn", on);
            this.prefs.edit().putString(Settings.KEY_CODE_RULES, obj.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private String defaultForRuleKey(String jsonKey) {
        if ("pickup".equals(jsonKey)) {
            return LocalCodeExtractor.DEF_PICKUP;
        }
        if ("verify".equals(jsonKey)) {
            return LocalCodeExtractor.DEF_VERIFY;
        }
        if ("verifyBefore".equals(jsonKey)) {
            return LocalCodeExtractor.DEF_VERIFY_BEFORE;
        }
        if ("verifyEn".equals(jsonKey)) {
            return LocalCodeExtractor.DEF_VERIFY_EN;
        }
        if ("exclude".equals(jsonKey)) {
            return LocalCodeExtractor.DEF_EXCLUDE;
        }
        return "";
    }

    // fixed89: one labelled keyword-list field. Saves on every edit.
    private void addRuleField(LinearLayout card, String label, final String jsonKey, final String defValue) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13.0f);
        labelView.setTextColor(COLOR_TITLE);
        labelView.setPadding(dp(16), dp(8), dp(16), dp(2));
        card.addView(labelView);
        final android.widget.EditText edit = new android.widget.EditText(this);
        edit.setText(getRuleValue(jsonKey, defValue));
        edit.setTextSize(13.0f);
        edit.setTextColor(COLOR_BODY);
        edit.setSingleLine(true);
        edit.setBackground(roundedBg(COLOR_BTN_BG, 8));
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setTag(jsonKey);
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                setRuleValue(jsonKey, s == null ? "" : s.toString());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(4);
        card.addView(edit, lp);
        this.ruleEdits.add(edit);
    }

    // fixed89: bare-digit fallback switch (a lone 4-6 digit group also
    // counts as a verification code).
    private void addBareSwitch(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(dp(16), dp(8), dp(16), dp(4));
        TextView labelView = new TextView(this);
        labelView.setText("兜底：单独的 4-6 位数字也认作验证码");
        labelView.setTextSize(13.0f);
        labelView.setTextColor(COLOR_TITLE);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Switch sw = new Switch(this);
        try {
            android.content.res.ColorStateList trackTint = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                    new int[]{ 0xFF007AFF, 0xFFE5E5E5 });
            android.content.res.ColorStateList thumbTint = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                    new int[]{ 0xFFFFFFFF, 0xFFFFFFFF });
            sw.setTrackTintList(trackTint);
            sw.setThumbTintList(thumbTint);
        } catch (Throwable ignored) {
        }
        sw.setChecked(LocalCodeExtractor.readBareOn(this.prefs));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setBareOn(isChecked);
            }
        });
        row.addView(sw);
        card.addView(row);
        this.bareSwitch = sw;
    }

    // fixed89: reset all custom rules to the built-in defaults.
    private void addRuleResetButton(LinearLayout card) {
        Button button = new Button(this);
        button.setText("恢复默认规则");
        button.setTextSize(14.0f);
        button.setTextColor(COLOR_TITLE);
        button.setAllCaps(false);
        button.setBackground(roundedButtonBg(COLOR_BTN_BG, COLOR_BTN_PRESSED, 12));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    MainActivity.this.prefs.edit().remove(Settings.KEY_CODE_RULES).apply();
                    for (android.widget.EditText edit : MainActivity.this.ruleEdits) {
                        Object tag = edit.getTag();
                        String key = tag == null ? "" : tag.toString();
                        edit.setText(defaultForRuleKey(key));
                    }
                    if (MainActivity.this.bareSwitch != null) {
                        MainActivity.this.bareSwitch.setChecked(true);
                    }
                    Toast.makeText(MainActivity.this, "已恢复默认规则", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.topMargin = dp(8);
        card.addView(button, lp);
    }

    private void addTestButton(LinearLayout row, String str, final Runnable runnable) {
        Button button = new Button(this);
        button.setText(str);
        button.setTextSize(15.0f);
        button.setTextColor(COLOR_TITLE);
        button.setAllCaps(false);
        button.setBackground(roundedButtonBg(COLOR_BTN_BG, COLOR_BTN_PRESSED, 12));
        button.setOnClickListener(new View.OnClickListener() { // from class: com.jarvis.bubblenotify.MainActivity.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                try {
                    runnable.run();
                } catch (Throwable th) {
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        if (row.getChildCount() > 0) {
            lp.leftMargin = dp(8);
        }
        row.addView(button, lp);
    }

    private GradientDrawable roundedBg(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private StateListDrawable roundedButtonBg(int normal, int pressed, int radiusDp) {
        GradientDrawable normalD = new GradientDrawable();
        normalD.setColor(normal);
        normalD.setCornerRadius(dp(radiusDp));
        GradientDrawable pressedD = new GradientDrawable();
        pressedD.setColor(pressed);
        pressedD.setCornerRadius(dp(radiusDp));
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, pressedD);
        d.addState(new int[]{}, normalD);
        return d;
    }
}
