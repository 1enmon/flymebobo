package com.jarvis.bubblenotify;

import android.content.Intent;
import android.content.SharedPreferences;

/* JADX INFO: loaded from: classes.dex */
public final class Settings {
    // fixed214: module versionCode, written into every FileLog line
    // so a log file always says which build produced it. Bump this
    // together with apktool.yml versionCode on every build.
    public static final int VERSION_CODE = 211;
    public static final String ACTION_CALL = "com.jarvis.bubblenotify.CALL";
    // fixed215: listener log forwarding (fixed200). The listener
    // cannot write /sdcard/ (scoped storage), so it broadcasts
    // its diagnostic lines here and SystemUI appends them to
    // /sdcard/BubbleNotify.log, which the user can read.
    public static final String ACTION_LOG = "com.jarvis.bubblenotify.LOG";
    public static final String ACTION_CALL_CONTROL = "com.jarvis.bubblenotify.CALL_CONTROL";
    public static final String ACTION_CODE = "com.jarvis.bubblenotify.CODE";
    public static final String ACTION_DISMISS = "com.jarvis.bubblenotify.DISMISS";
    public static final String ACTION_HIDE = "com.jarvis.bubblenotify.HIDE";
    public static final String ACTION_MUSIC = "com.jarvis.bubblenotify.MUSIC";
    public static final String ACTION_MUSIC_CONTROL = "com.jarvis.bubblenotify.MUSIC_CONTROL";
    public static final String ACTION_NOTIFY = "com.jarvis.bubblenotify.NOTIFY";
    public static final String ACTION_NOTIFY_LIST = "com.jarvis.bubblenotify.NOTIFY_LIST";
    public static final String ACTION_SETTINGS = "com.jarvis.bubblenotify.SETTINGS";
    public static final String ACTION_TEST_CODE = "com.jarvis.bubblenotify.TEST_CODE";
    public static final int BUBBLE_WIDTH_DP = 302;
    public static final int BUBBLE_WIDTH_LANDSCAPE_DP = 262;
    public static final String CALL_IDLE = "idle";
    public static final long CALL_MINI_TO_DOCK_MS = 3000;
    public static final String CALL_OFFHOOK = "offhook";
    public static final String CALL_RINGING = "ringing";
    public static final String CALL_UI_REQUEST_KEY = "bubblenotify_call_ui_request";
    public static final int CARD_WIDTH_LANDSCAPE_DP = 282;
    public static final String CODE_KIND_PICKUP = "取件码";
    public static final String CODE_KIND_VERIFY = "验证码";
    public static final String CONTROL_FAVORITE = "favorite";
    public static final String CONTROL_NEXT = "next";
    public static final String CONTROL_PREV = "prev";
    public static final String CONTROL_SEEK = "seek";
    public static final String CONTROL_STOP = "stop";
    public static final String CONTROL_TOGGLE = "toggle";
    public static final int DEFAULT_BUBBLE_OPACITY = 75;
    public static final boolean DEFAULT_CALL_ENABLED = true;
    public static final int DEFAULT_CAPSULE_CORNER_DP = 52;
    public static final int DEFAULT_CAPSULE_HEIGHT_DP = 52;
    public static final int DEFAULT_CAPSULE_WIDTH_DP = 200;
    public static final int DEFAULT_CARD_BOTTOM_OFFSET_DP = 32;
    public static final int DEFAULT_CARD_CORNER_DP = 20;
    public static final int DEFAULT_CARD_HEIGHT_DP = 0;
    public static final int DEFAULT_CARD_WIDTH_DP = 302;
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_FREEFORM = true;
    public static final int DEFAULT_MUSIC_CARD_CORNER_DP = 20;
    public static final int DEFAULT_MUSIC_CARD_HEIGHT_DP = 0;
    public static final int DEFAULT_MUSIC_CARD_OPACITY = 75;
    public static final int DEFAULT_MUSIC_CARD_WIDTH_DP = 302;
    public static final boolean DEFAULT_MUSIC_ENABLED = true;
    public static final boolean DEFAULT_TAP_OPEN = true;
    public static final int DOCK_MARGIN_BOTTOM_DP = 14;
    public static final int DOCK_MARGIN_RIGHT_DP = 12;
    public static final int DOCK_PEEK_DP = 22;
    public static final String EXTRA_CALL_NAME = "call_name";
    public static final String EXTRA_CALL_NUMBER = "call_number";
    public static final String EXTRA_CALL_STATE = "call_state";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_CODE_KIND = "code_kind";
    public static final String EXTRA_CONTROL = "control";
    public static final String EXTRA_ICON = "icon";
    public static final String EXTRA_INTENT = "intent";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_MUSIC_ACTIVE = "music_active";
    public static final String EXTRA_MUSIC_ALBUM = "music_album";
    public static final String EXTRA_MUSIC_ARTIST = "music_artist";
    public static final String EXTRA_MUSIC_DURATION = "music_duration";
    public static final String EXTRA_MUSIC_FAVORITE = "music_favorite";
    public static final String EXTRA_MUSIC_LRC = "music_lrc";
    public static final String EXTRA_MUSIC_LYRIC = "music_lyric";
    public static final String EXTRA_MUSIC_PLAYING = "music_playing";
    public static final String EXTRA_MUSIC_POSITION = "music_position";
    public static final String EXTRA_MUSIC_TITLE = "music_title";
    public static final String EXTRA_NOTIFY_LIST = "notify_list";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_SEEK_POSITION = "seek_position";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TITLE = "title";
    public static final long IDLE_TO_MINI_MS = 3000;
    public static final String INCALLUI_PACKAGE = "com.android.incallui";
    public static final String ITEM_ICON = "item_icon";
    public static final String ITEM_ICON_ACCENT = "item_icon_accent";
    public static final String ITEM_ICON_SMALL = "item_icon_small";
    public static final String ITEM_INTENT = "item_intent";
    public static final String ITEM_KEY = "item_key";
    public static final String ITEM_PACKAGE = "item_package";
    public static final String ITEM_PROGRESS = "item_progress";
    public static final String ITEM_PROGRESS_INDETERMINATE = "item_progress_indeterminate";
    public static final String ITEM_PROGRESS_MAX = "item_progress_max";
    public static final String ITEM_REPLY_INTENT = "item_reply_intent";
    public static final String ITEM_REPLY_KEY = "item_reply_key";
    public static final String ITEM_TEXT = "item_text";
    public static final String ITEM_TIME = "item_time";
    public static final String ITEM_TITLE = "item_title";
    // fixed212: download capsule (ported from 2.0) — auto-pins,
    // shows live percent, tap does not expand into a card.
    public static final String ITEM_IS_DOWNLOAD = "item_is_download";
    public static final String ITEM_DOWNLOAD_DONE = "item_download_done";
    public static final String KEY_BUBBLE_OPACITY = "bubble_opacity";
    public static final String KEY_CALL_ENABLED = "call_enabled";
    public static final String KEY_CAPSULE_CORNER_DP = "capsule_corner_dp";
    public static final String KEY_CAPSULE_HEIGHT_DP = "capsule_height_dp";
    public static final String KEY_CAPSULE_WIDTH_DP = "capsule_width_dp";
    public static final String KEY_CARD_BOTTOM_OFFSET_DP = "card_bottom_offset_dp";
    public static final String KEY_CARD_CORNER_DP = "card_corner_dp";
    public static final String KEY_CARD_EXPAND = "card_expand";
    public static final String KEY_CARD_HEIGHT_DP = "card_height_dp";
    public static final String KEY_CARD_WIDTH_DP = "card_width_dp";
    public static final String KEY_CODE_RULES = "code_rules";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_FREEFORM = "freeform";
    public static final String KEY_HIDE_CALL_UI = "hide_call_ui";
    public static final String KEY_MUSIC_CARD_CORNER_DP = "music_card_corner_dp";
    public static final String KEY_MUSIC_CARD_HEIGHT_DP = "music_card_height_dp";
    public static final String KEY_MUSIC_CARD_OPACITY = "music_card_opacity";
    public static final String KEY_MUSIC_CARD_WIDTH_DP = "music_card_width_dp";
    public static final String KEY_MUSIC_ENABLED = "music_enabled";
    public static final String KEY_TAP_OPEN = "tap_open";
    public static final long LANDSCAPE_DOCK_MS = 600;
    public static final int MAX_RECENT_NOTIFICATIONS = 8;
    public static final int MAX_VISIBLE_BUBBLES = 3;
    public static final int MINI_HEIGHT_DP = 52;
    public static final long MINI_TO_DOCK_MS = 2600;
    public static final String MODULE_PACKAGE = "com.jarvis.bubblenotify";
    public static final String OFFSET_FILE_PATH = "/data/local/tmp/BubbleNotify_offset.txt";
    public static final String PKG_AMAP = "com.autonavi.minimap";
    public static final String PKG_ELEME = "me.ele";
    public static final String PKG_MEITUAN = "com.sankuai.meituan";
    public static final String PREFS_NAME = "settings";
    public static final long REDOCK_MS = 3000;
    public static final int SWIPE_HINT_DP = 28;
    public static final int SWIPE_MIN_DP = 84;
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    public static final String TAG = "BubbleNotify: ";

    public static String summarySender(String str) {
        int iIndexOf;
        if (str != null) {
            try {
                if (!str.startsWith("[") || (iIndexOf = str.indexOf(93)) <= 0 || iIndexOf > 10 || !str.substring(1, iIndexOf).matches("\\d+条")) {
                    return null;
                }
                String strTrim = str.substring(iIndexOf + 1).trim();
                int iIndexOf2 = strTrim.indexOf(58);
                int iIndexOf3 = strTrim.indexOf(65306);
                int iMax = (iIndexOf2 < 0 || iIndexOf3 < 0) ? Math.max(iIndexOf2, iIndexOf3) : Math.min(iIndexOf2, iIndexOf3);
                if (iMax <= 0) {
                    return null;
                }
                String strTrim2 = strTrim.substring(0, iMax).trim();
                if (strTrim2.isEmpty()) {
                    return null;
                }
                return strTrim2;
            } catch (Throwable th) {
                return null;
            }
        }
        return null;
    }

    private Settings() {
    }

    public static void putAllToIntent(Intent intent, SharedPreferences sharedPreferences) {
        intent.putExtra(KEY_ENABLED, sharedPreferences.getBoolean(KEY_ENABLED, true));
        intent.putExtra(KEY_MUSIC_ENABLED, sharedPreferences.getBoolean(KEY_MUSIC_ENABLED, true));
        intent.putExtra(KEY_CALL_ENABLED, sharedPreferences.getBoolean(KEY_CALL_ENABLED, true));
        intent.putExtra(KEY_HIDE_CALL_UI, sharedPreferences.getBoolean(KEY_HIDE_CALL_UI, true));
        intent.putExtra(KEY_TAP_OPEN, sharedPreferences.getBoolean(KEY_TAP_OPEN, true));
        intent.putExtra(KEY_CARD_EXPAND, sharedPreferences.getBoolean(KEY_CARD_EXPAND, true));
        intent.putExtra(KEY_FREEFORM, sharedPreferences.getBoolean(KEY_FREEFORM, true));
        intent.putExtra(KEY_CARD_BOTTOM_OFFSET_DP, sharedPreferences.getInt(KEY_CARD_BOTTOM_OFFSET_DP, 32));
        intent.putExtra(KEY_CAPSULE_WIDTH_DP, sharedPreferences.getInt(KEY_CAPSULE_WIDTH_DP, DEFAULT_CAPSULE_WIDTH_DP));
        intent.putExtra(KEY_CAPSULE_HEIGHT_DP, sharedPreferences.getInt(KEY_CAPSULE_HEIGHT_DP, 52));
        intent.putExtra(KEY_CARD_WIDTH_DP, sharedPreferences.getInt(KEY_CARD_WIDTH_DP, 302));
        intent.putExtra(KEY_CARD_HEIGHT_DP, sharedPreferences.getInt(KEY_CARD_HEIGHT_DP, 0));
        intent.putExtra(KEY_BUBBLE_OPACITY, sharedPreferences.getInt(KEY_BUBBLE_OPACITY, 75));
        intent.putExtra(KEY_CAPSULE_CORNER_DP, sharedPreferences.getInt(KEY_CAPSULE_CORNER_DP, 52));
        intent.putExtra(KEY_CARD_CORNER_DP, sharedPreferences.getInt(KEY_CARD_CORNER_DP, 20));
        intent.putExtra(KEY_MUSIC_CARD_WIDTH_DP, sharedPreferences.getInt(KEY_MUSIC_CARD_WIDTH_DP, 302));
        intent.putExtra(KEY_MUSIC_CARD_HEIGHT_DP, sharedPreferences.getInt(KEY_MUSIC_CARD_HEIGHT_DP, 0));
        intent.putExtra(KEY_MUSIC_CARD_OPACITY, sharedPreferences.getInt(KEY_MUSIC_CARD_OPACITY, 75));
        intent.putExtra(KEY_MUSIC_CARD_CORNER_DP, sharedPreferences.getInt(KEY_MUSIC_CARD_CORNER_DP, 20));
    }
}
