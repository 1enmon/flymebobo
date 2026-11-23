package com.jarvis.bubblenotify;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public class NotificationService extends NotificationListenerService {
    private static final long MUSIC_INACTIVE_DELAY_MS = 2000;
    private static final long NOTIF_DEDUP_WINDOW_MS = 15000;
    private static final long NOTIF_GROUP_WINDOW_MS = 10000;
    private static final long NOTIF_KEY_TTL_MS = 3600000;
    private static final long NOTIF_SUMMARY_WINDOW_MS = 60000;
    private String callKey;
    private Boolean currentFavorite;
    private String currentLyricBigText;
    private String currentLyricText;
    private String currentLyricTicker;
    private String currentMediaKey;
    private String currentMediaTitle;
    private String currentNotifTitle;
    private String lastArtistMeta;
    private String lastNotifTitle;
    private String lastRawTitle;
    private String lastSubtitle;
    private MediaController.Callback mediaCallback;
    private MediaController mediaController;
    private Runnable pendingMusicInactiveRunnable;
    private String swappedArtist;
    private boolean swappedLayout;
    private String swappedSongTitle;
    private static final Pattern DIST_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(公里|千米|米|km|m)");
    private static final Pattern REMAIN_DIST_PATTERN = Pattern.compile("(?:剩余|还剩)\\s*(\\d+(?:\\.\\d+)?\\s*(?:公里|千米|米|km|m))");
    private static final Pattern REMAIN_TIME_PATTERN = Pattern.compile("(?:约|预计)\\s*(\\d+\\s*分钟)");
    private static final Set<String> PUSH_SERVICE_PACKAGES = new HashSet(Arrays.asList("com.meizu.cloud.pushservice", "com.meizu.push", "com.meizu.flyme.push", "com.meizu.cloud.pushsdk", "com.xiaomi.xmsf", "com.huawei.android.pushagent", "com.huawei.hms", "com.coloros.mcs", "com.heytap.mcs", "com.heytap.msp", "com.vivo.pushservice", "com.vivo.push", "com.samsung.android.push"));
    private static final Set<String> VIDEO_PACKAGES = new HashSet(Arrays.asList("tv.danmaku.bili", "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite", "com.ss.android.ugc.live", "com.kuaishou.nebula", "com.kuaishou.nebula.lite", "com.tencent.qqlive", "com.iqiyi.qyplayer", "com.youku.phone", "com.hunantv.imgo.activity", "com.ss.android.article.video", "com.baidu.video", "com.sohu.sohuvideo", "com.tencent.weishi", "com.miui.video", "com.meizu.media.video"));
    private long currentMediaDuration = -1;
    // fixed181: network lyrics (LRCLIB, 2.0 v88). The 1.x
    // NetworkLyrics class existed but was dead code — never called
    // from anywhere. Wire it up: fetch once per song on a
    // background thread when the notification/MediaSession carry
    // no lyric (QQ Original / Qishui have none), then pick the
    // line by playback position. Same public API as 2.0's
    // fetchNetworkLyrics (lrclib.net /api/get, /api/search
    // fallback), 5.5s timeout.
    private java.util.List<NetworkLyrics.Line> netLyricLines;
    private String netLyricSong;
    private String netLyricArtist;
    private boolean netLyricFetching;
    private String netLyricFetchingFor;
    private String netLyricLastLine;
    private String netLyricTickPackage;
    private PendingIntent netLyricTickIntent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<RecentNotif> recentNotifs = new ArrayList();
    private final Set<String> selfCancelledKeys = new HashSet();
    private final Map<String, Long> recentNotifFingerprints = new HashMap();
    private final Map<String, Long> recentNotifContentFingerprints = new HashMap();
    private final Map<String, Long> seenNotifKeys = new HashMap();
    private final Map<String, String> seenNotifKeyContent = new HashMap();
    private final LinkedHashMap<String, String> lastNotifFingerprintByPkg = new LinkedHashMap<String, String>(32, 0.75f, true) { // from class: com.jarvis.bubblenotify.NotificationService.1
        @Override // java.util.LinkedHashMap
        protected boolean removeEldestEntry(Map.Entry<String, String> entry) {
            return size() > 32;
        }
    };
    private final Map<String, MsgState> msgStates = new HashMap();
    private final Map<String, String> lastMsgByConversation = new HashMap();
    private final Map<String, Long> recentNotifGroups = new HashMap();
    private final Map<String, LastNotif> lastNotifByPkg = new HashMap();
    // fixed181: 1s network-lyric tick (2.0's lyric poll, v84).
    // While music is playing and network lines are loaded for the
    // current song, re-pick the line by playback position; only
    // re-broadcast when the line actually changed, so the music
    // bubble's lyric rolls on even though the notification never
    // re-posts a lyric (QQ Original / Qishui). Stops itself when
    // music stops or the song changes.
    private final Runnable netLyricTickRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                MediaController mc = NotificationService.this.mediaController;
                if (mc == null || NotificationService.this.netLyricLines == null
                        || NotificationService.this.netLyricLines.isEmpty()
                        || NotificationService.this.netLyricSong == null) {
                    return;
                }
                PlaybackState ps = mc.getPlaybackState();
                boolean playing = ps != null && ps.getState() == 3;
                if (!playing) {
                    return;
                }
                long pos = ps.getPosition();
                if (pos >= 0 && ps.getLastPositionUpdateTime() > 0) {
                    pos += SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime();
                }
                String line = NetworkLyrics.lineAt(NotificationService.this.netLyricLines, pos);
                if (line == null || line.equals(NotificationService.this.netLyricLastLine)) {
                    NotificationService.this.mainHandler.postDelayed(NotificationService.this.netLyricTickRunnable, 1000);
                    return;
                }
                NotificationService.this.netLyricLastLine = line;
                if (NotificationService.this.netLyricTickPackage != null) {
                    NotificationService.this.sendMusicStateFromController(NotificationService.this.netLyricTickPackage, NotificationService.this.netLyricTickIntent);
                }
                NotificationService.this.mainHandler.postDelayed(NotificationService.this.netLyricTickRunnable, 1000);
            } catch (Throwable ignored) {
            }
        }
    };

    private void startNetLyricTick() {
        try {
            this.mainHandler.removeCallbacks(this.netLyricTickRunnable);
            this.mainHandler.postDelayed(this.netLyricTickRunnable, 1000);
        } catch (Throwable ignored) {
        }
    }

    private void stopNetLyricTick() {
        try {
            this.mainHandler.removeCallbacks(this.netLyricTickRunnable);
        } catch (Throwable ignored) {
        }
    }

    // fixed181: current playback position in ms, same formula as
    // sendMusicStateFromController.
    private long currentMusicPositionMs() {
        try {
            MediaController mc = this.mediaController;
            if (mc == null) {
                return -1;
            }
            PlaybackState ps = mc.getPlaybackState();
            if (ps == null) {
                return -1;
            }
            long pos = ps.getPosition();
            if (pos >= 0 && ps.getLastPositionUpdateTime() > 0) {
                pos += SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime();
            }
            return pos;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // fixed181: the network line for the current song at the
    // current position, or null (no lines, or the song changed).
    private String netLyricLineAt(String song, String artist, long positionMs) {
        try {
            if (this.netLyricLines == null || this.netLyricLines.isEmpty()
                    || song == null || !song.equals(this.netLyricSong)) {
                return null;
            }
            if (artist != null && this.netLyricArtist != null
                    && !artist.equals(this.netLyricArtist)) {
                return null;
            }
            if (positionMs < 0) {
                positionMs = currentMusicPositionMs();
            }
            if (positionMs < 0) {
                return null;
            }
            return NetworkLyrics.lineAt(this.netLyricLines, positionMs);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // fixed181: fetch LRCLIB lyrics once per song on a background
    // thread (2.0 maybeFetchNetworkLyrics). Guards: already
    // fetching, same song already fetching, already have lines
    // for this exact song. On success store the timed lines and
    // start the 1s tick so the lyric rolls on.
    private void maybeFetchNetworkLyrics(String song, String artist, long durationMs) {
        try {
            if (song == null || song.isEmpty() || this.netLyricFetching) {
                return;
            }
            String key = song + "\u0000" + (artist == null ? "" : artist);
            if (key.equals(this.netLyricFetchingFor)) {
                return;
            }
            if (song.equals(this.netLyricSong)
                    && (artist == null ? this.netLyricArtist == null : artist.equals(this.netLyricArtist))
                    && this.netLyricLines != null && !this.netLyricLines.isEmpty()) {
                return;
            }
            this.netLyricFetching = true;
            this.netLyricFetchingFor = key;
            final String fSong = song;
            final String fArtist = artist;
            final long fDurMs = durationMs;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String lrc = NetworkLyrics.fetchSyncedLrc(fSong, fArtist, null, fDurMs);
                        if (lrc == null || lrc.isEmpty()) {
                            return;
                        }
                        final java.util.List<NetworkLyrics.Line> lines = NetworkLyrics.parseLrc(lrc);
                        if (lines == null || lines.isEmpty()) {
                            return;
                        }
                        NotificationService.this.mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    NotificationService.this.netLyricSong = fSong;
                                    NotificationService.this.netLyricArtist = fArtist;
                                    NotificationService.this.netLyricLines = lines;
                                    NotificationService.this.netLyricLastLine = null;
                                    NotificationService.this.startNetLyricTick();
                                    // Push the first line out now so
                                    // the bubble shows a lyric without
                                    // waiting for the next re-post.
                                    if (NotificationService.this.netLyricTickPackage != null) {
                                        NotificationService.this.sendMusicStateFromController(NotificationService.this.netLyricTickPackage, NotificationService.this.netLyricTickIntent);
                                    }
                                    FileLog.i("netLyric loaded song=" + fSong + " lines=" + lines.size());
                                } catch (Throwable ignored) {
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    } finally {
                        NotificationService.this.netLyricFetching = false;
                    }
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() { // from class: com.jarvis.bubblenotify.NotificationService.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String stringExtra;
            boolean z;
            if (intent == null || intent.getAction() == null) {
                return;
            }
            try {
                if (Settings.ACTION_MUSIC_CONTROL.equals(intent.getAction())) {
                    NotificationService.this.handleMusicControl(intent.getStringExtra(Settings.EXTRA_CONTROL), intent.getLongExtra(Settings.EXTRA_SEEK_POSITION, -1L));
                    return;
                }
                if (Settings.ACTION_CALL_CONTROL.equals(intent.getAction())) {
                    NotificationService.this.fireCallAction(intent.getStringExtra(Settings.EXTRA_CONTROL));
                    return;
                }
                if (Settings.ACTION_DISMISS.equals(intent.getAction()) && (stringExtra = intent.getStringExtra(Settings.EXTRA_KEY)) != null) {
                    synchronized (NotificationService.this.recentNotifs) {
                        z = false;
                        for (int size = NotificationService.this.recentNotifs.size() - 1; size >= 0; size--) {
                            if (stringExtra.equals(((RecentNotif) NotificationService.this.recentNotifs.get(size)).key)) {
                                NotificationService.this.recentNotifs.remove(size);
                                z = true;
                            }
                        }
                    }
                    synchronized (NotificationService.this.selfCancelledKeys) {
                        NotificationService.this.selfCancelledKeys.remove(stringExtra);
                    }
                    try {
                        NotificationService.this.cancelNotification(stringExtra);
                    } catch (Throwable th) {
                    }
                    if (z) {
                        NotificationService.this.broadcastNotifyList();
                    }
                    Log.i(Settings.TAG, "Notification dismissed by bubble.");
                }
            } catch (Throwable th2) {
                Log.e(Settings.TAG, "Error handling command " + intent.getAction(), th2);
            }
        }
    };
    private boolean commandReceiverRegistered = false;

    private boolean isPushService(String str) {
        if (str == null) {
            return false;
        }
        if (PUSH_SERVICE_PACKAGES.contains(str)) {
            return true;
        }
        try {
            CharSequence applicationLabel = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(str, 0));
            return applicationLabel != null && "推送服务".equals(applicationLabel.toString());
        } catch (Throwable th) {
        }
        return false;
    }

    // fixed176: the notification key is "userId|pkg|id|tag|uid" — the
    // pkg field is the ORIGINAL app, which is what a push service
    // posts on behalf of (e.g. com.hpbr.bosszhipin for BOSS).
    private String keyPackage(String key) {
        if (key == null) {
            return null;
        }
        try {
            String[] parts = key.split("\\|");
            if (parts.length >= 2) {
                return parts[1];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // fixed176: true when the package is installed on the device
    // (its app label resolves) — guards the key-package lookup so a
    // stale/unknown package never becomes the display package.
    private boolean isInstalledApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        try {
            if (getPackageManager().getApplicationInfo(pkg, 0) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // v204: package-visibility fallback. After a reinstall
        // (new signature), Flyme's "read app list" privacy grant
        // resets and getApplicationInfo throws NameNotFound for
        // every third-party app — which made the push-service
        // delegation above drop ALL delegated notifications.
        // LauncherApps is the launcher-facing API and is not
        // subject to that filter.
        try {
            android.content.pm.LauncherApps launcherApps = (android.content.pm.LauncherApps) getSystemService("launcherapps");
            if (launcherApps != null) {
                java.util.List<android.content.pm.LauncherActivityInfo> list = launcherApps.getActivityList(pkg, android.os.Process.myUserHandle());
                if (list != null && !list.isEmpty()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isVideoApp(String str) {
        ApplicationInfo applicationInfo;
        if (str != null) {
            try {
                if (VIDEO_PACKAGES.contains(str)) {
                    return true;
                }
                if (str == null && (applicationInfo = getPackageManager().getApplicationInfo(str, 0)) != null && applicationInfo.category == 2) {
                    return true;
                }
            } catch (Throwable th) {
            }
        } else if (str == null) {
        }
        return false;
    }

    private static class LastNotif {
        String text;
        long time;
        String title;

        LastNotif(String str, String str2, long j) {
            this.title = str;
            this.text = str2;
            this.time = j;
        }
    }

    private static class MsgState {
        long postTime;
        final LinkedHashSet<String> seen = new LinkedHashSet<>();

        MsgState(long j) {
            this.postTime = j;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:37:0x0064  */

    // fixed193: does the live title/text say the download is
    // done? (2.0 v242). Apps rewrite the notification as "下载完成"
    // (and drop the progress extras) instead of posting 100%.


    // fixed193: live download progress refresh (2.0 v95/v98/v99/
    // v240/v241/v242). Re-reads the active download notification(s)
    // and updates the capsule in real time; detects completion
    // (progress 100% or a completion word) and a vanished
    // notification (gone for 2 ticks). Only broadcasts when
    // something visible actually changed.
    // fixed200: forward a diagnostic line to SystemUI.
    // The listener service (app uid) cannot write to /sdcard/
    // (scoped storage), so it broadcasts the line and SystemUI
    // (system uid) appends it to /sdcard/BubbleNotify.log, the
    // file the user can read. Best-effort, never throws.

    /** v240: speed straight from the text, e.g. TapTap's
     * "40.81 MB/s  剩余时间 35秒". Returns bytes/sec, or -1. */

    /** v240: ETA seconds from the text. TapTap: "剩余时间 35秒";
     * browser subText: "还剩 43 秒". Returns seconds, or -1. */

    /** v239/v240/v241: line 1 = file name (what is downloading);
     * line 2 = progress details — "正在下载 45%" + " · 12.5MB/48MB"
     * (byte counts, when the notification carries a real byte
     * total) + " · 2.3MB/s" (when a speed sample exists) +
     * " · 还剩35秒" (ETA from the text). */

    // fixed195: 2.0 download set (v95/v98/v99/v239/v240) — the
    // whole 2.0 progress reading, ported. Root cause of
    // "一直是0%": a download app reports progress in BYTES
    // (progressMax = total bytes, e.g. 1.75GB), and the old code
    // did `progress * 100` in INT — 83MB * 100 overflows int and
    // the percent comes out 0. 2.0 reads the percent with LONG
    // arithmetic and also parses byte counts out of the text
    // ("已下载 79.26 MB，共 1.75 GB", Coolapk "2.03M/1.98G").
    /** v239: raw byte counts (progress / progressMax) when the
     * notification carries a real byte total. Returns null when
     * progress is percent-based (progressMax <= 100) or absent.
     * Extras first, then RemoteViews. */

    /** v240: parse a size like "2.03M", "1.98G", "79.26 MB",
     * "1.75 GB", "0.00 KB" into bytes. Units B/KB/MB/GB and the
     * bare K/M/G, with or without a space. -1 if unparsable. */

    /** v240: downloaded/total from the text fields. Coolapk puts
     * it in infoText as "2.03M/1.98G"; the browser puts it in
     * text as "已下载 79.26 MB，共 1.75 GB". Returns
     * {downloaded, total} in bytes, or null. */

    /** v95/v98/v99: download percent the 2.0 way — the max of
     * extras / RemoteViews / text percent, then byte counts from
     * the text (Coolapk / browser) or from extras/RemoteViews
     * when progressMax > 100. LONG arithmetic throughout, so a
     * byte total never overflows the percent. -1 if nothing
     * usable. */


    private boolean isRepeatNotifFingerprint(String str, StatusBarNotification statusBarNotification, String str2, String str3, int i, int i2) {
        if (str == null || statusBarNotification == null) {
            return false;
        }
        try {
            int id = statusBarNotification.getId();
            long postTime = statusBarNotification.getPostTime();
            if (str2 == null) {
                str2 = "";
            }
            if (str3 == null) {
                str3 = "";
            }
            String str4 = id + ":" + postTime + ":" + str2 + "/" + str3 + ":" + i + "/" + i2;
            synchronized (this.lastNotifFingerprintByPkg) {
                if (str4.equals(this.lastNotifFingerprintByPkg.get(str))) {
                    return true;
                }
                this.lastNotifFingerprintByPkg.put(str, str4);
                return false;
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private boolean isRepeatNotifKey(String str, String str2, String str3, int i, int i2) {
        if (str == null) {
            return false;
        }
        if (str2 == null) {
            str2 = "";
        }
        if (str3 == null) {
            str3 = "";
        }
        try {
            String str4 = str2 + "\u0001" + str3 + "\u0001" + i + "/" + i2;
            long jUptimeMillis = SystemClock.uptimeMillis();
            synchronized (this.seenNotifKeys) {
                Long l = this.seenNotifKeys.get(str);
                String str5 = this.seenNotifKeyContent.get(str);
                if (l != null && jUptimeMillis - l.longValue() < NOTIF_KEY_TTL_MS && str4.equals(str5)) {
                    return true;
                }
                this.seenNotifKeys.put(str, Long.valueOf(jUptimeMillis));
                this.seenNotifKeyContent.put(str, str4);
                if (this.seenNotifKeys.size() > 128) {
                    Iterator<Map.Entry<String, Long>> it = this.seenNotifKeys.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, Long> next = it.next();
                        if (jUptimeMillis - next.getValue().longValue() > NOTIF_KEY_TTL_MS) {
                            it.remove();
                            this.seenNotifKeyContent.remove(next.getKey());
                        }
                    }
                }
                return false;
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private boolean isDuplicateNotif(String str, String str2, String str3) {
        try {
            String str4 = str + "\u0001" + (str2 == null ? "" : str2) + "\u0001" + (str3 == null ? "" : str3);
            long jUptimeMillis = SystemClock.uptimeMillis();
            synchronized (this.recentNotifFingerprints) {
                Long l = this.recentNotifFingerprints.get(str4);
                if (l != null && jUptimeMillis - l.longValue() < NOTIF_DEDUP_WINDOW_MS) {
                    return true;
                }
                this.recentNotifFingerprints.put(str4, Long.valueOf(jUptimeMillis));
                if (this.recentNotifFingerprints.size() > 64) {
                    Iterator<Map.Entry<String, Long>> it = this.recentNotifFingerprints.entrySet().iterator();
                    while (it.hasNext()) {
                        if (jUptimeMillis - it.next().getValue().longValue() > NOTIF_DEDUP_WINDOW_MS) {
                            it.remove();
                        }
                    }
                }
                synchronized (this.recentNotifContentFingerprints) {
                    try {
                        String str5 = (str2 == null ? "" : str2) + "\u0001" + (str3 == null ? "" : str3);
                        Long l2 = this.recentNotifContentFingerprints.get(str5);
                        if (l2 != null && jUptimeMillis - l2.longValue() < NOTIF_DEDUP_WINDOW_MS) {
                            return true;
                        }
                        this.recentNotifContentFingerprints.put(str5, Long.valueOf(jUptimeMillis));
                        if (this.recentNotifContentFingerprints.size() > 64) {
                            Iterator<Map.Entry<String, Long>> it2 = this.recentNotifContentFingerprints.entrySet().iterator();
                            while (it2.hasNext()) {
                                if (jUptimeMillis - it2.next().getValue().longValue() > NOTIF_DEDUP_WINDOW_MS) {
                                    it2.remove();
                                }
                            }
                        }
                        synchronized (this.lastNotifByPkg) {
                            LastNotif lastNotif = this.lastNotifByPkg.get(str);
                            if (lastNotif != null && jUptimeMillis - lastNotif.time < NOTIF_DEDUP_WINDOW_MS && isSummaryOf(lastNotif, str2, str3)) {
                                return true;
                            }
                            if (lastNotif != null && jUptimeMillis - lastNotif.time < NOTIF_SUMMARY_WINDOW_MS && isAggregatedSummaryOf(lastNotif, str2, str3)) {
                                return true;
                            }
                            this.lastNotifByPkg.put(str, new LastNotif(str2, str3, jUptimeMillis));
                            if (this.lastNotifByPkg.size() > 32) {
                                this.lastNotifByPkg.clear();
                            }
                            return false;
                        }
                    } catch (Throwable th) {
                        throw th;
                    }
                }
            }
        } catch (Throwable th2) {
            return false;
        }
    }

    private static boolean isSummaryOf(LastNotif lastNotif, String str, String str2) {
        try {
            if (lastNotif.title != null && str != null && lastNotif.title.equals(str) && lastNotif.text != null && str2 != null && !lastNotif.text.isEmpty() && !str2.isEmpty()) {
                if (str2.contains(lastNotif.text) && str2.contains(str)) {
                    return true;
                }
                return lastNotif.text.contains(str2) && lastNotif.text.contains(str);
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean isAggregatedSummaryOf(LastNotif lastNotif, String str, String str2) {
        try {
            String strSummarySender = Settings.summarySender(str2);
            if (strSummarySender == null) {
                return false;
            }
            if (lastNotif.title == null || !lastNotif.title.equals(strSummarySender)) {
                return (lastNotif.text == null || lastNotif.text.isEmpty() || !str2.contains(lastNotif.text)) ? false : true;
            }
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    private boolean isRedundantGroupSummary(Notification notification, String str) {
        String group;
        boolean z;
        try {
            if ((notification.flags & 512) == 0 || (group = notification.getGroup()) == null) {
                return false;
            }
            long jUptimeMillis = SystemClock.uptimeMillis();
            synchronized (this.recentNotifGroups) {
                Long l = this.recentNotifGroups.get(str + "\u0001" + group);
                z = l != null && jUptimeMillis - l.longValue() < NOTIF_GROUP_WINDOW_MS;
            }
            return z;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX WARN: Bottom block not found for handler: all -> 0x0049 */
    /* JADX WARN: Code restructure failed: missing block: B:19:0x004a, code lost:
    
        return;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    // v207 fix: this method was a jadx stub in the repo source
    // ("Method not decompiled") that threw
    // UnsupportedOperationException on EVERY call — killing
    // onNotificationPosted for every normal notification since
    // the source was decompiled (the listener path silently
    // died; only the hook fallback kept some bubbles alive).
    // Reconstructed faithfully from the decompiler's own
    // register dump left in the stub's comment: record a child
    // notification's group so a later group summary for the
    // same pkg+group counts as redundant
    // (isRedundantGroupSummary reads the same map).
    private void noteNotifGroup(Notification notification, String str) {
        try {
            if ((notification.flags & 512) != 0) {
                return;
            }
            String group = notification.getGroup();
            if (group == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            synchronized (this.recentNotifGroups) {
                this.recentNotifGroups.put(str + "\u0001" + group, Long.valueOf(now));
                if (this.recentNotifGroups.size() > 64) {
                    this.recentNotifGroups.clear();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /* JADX WARN: Code duplicated, block: B:137:0x021a A[Catch: all -> 0x026c, TryCatch #1 {, blocks: (B:85:0x0161, B:87:0x016b, B:89:0x0171, B:93:0x0177, B:95:0x0189, B:97:0x0199, B:100:0x01a2, B:101:0x01a7, B:103:0x01ad, B:109:0x01c2, B:113:0x01c8, B:114:0x01ca, B:127:0x0209, B:133:0x020f, B:134:0x0210, B:135:0x0214, B:137:0x021a, B:139:0x022d, B:142:0x023f, B:143:0x0241, B:153:0x0269, B:154:0x026a, B:99:0x01a0, B:144:0x0242, B:146:0x024f, B:148:0x025f, B:149:0x0265, B:115:0x01cb, B:117:0x01d5, B:119:0x01db, B:120:0x01df, B:122:0x01e5, B:124:0x01f8, B:126:0x0208, B:129:0x020b), top: B:164:0x0161, outer: #3, inners: #0, #2 }] */
    /* JADX WARN: Code duplicated, block: B:139:0x022d A[Catch: all -> 0x026c, TryCatch #1 {, blocks: (B:85:0x0161, B:87:0x016b, B:89:0x0171, B:93:0x0177, B:95:0x0189, B:97:0x0199, B:100:0x01a2, B:101:0x01a7, B:103:0x01ad, B:109:0x01c2, B:113:0x01c8, B:114:0x01ca, B:127:0x0209, B:133:0x020f, B:134:0x0210, B:135:0x0214, B:137:0x021a, B:139:0x022d, B:142:0x023f, B:143:0x0241, B:153:0x0269, B:154:0x026a, B:99:0x01a0, B:144:0x0242, B:146:0x024f, B:148:0x025f, B:149:0x0265, B:115:0x01cb, B:117:0x01d5, B:119:0x01db, B:120:0x01df, B:122:0x01e5, B:124:0x01f8, B:126:0x0208, B:129:0x020b), top: B:164:0x0161, outer: #3, inners: #0, #2 }] */
    /* JADX WARN: Code duplicated, block: B:142:0x023f A[Catch: all -> 0x026c, TryCatch #1 {, blocks: (B:85:0x0161, B:87:0x016b, B:89:0x0171, B:93:0x0177, B:95:0x0189, B:97:0x0199, B:100:0x01a2, B:101:0x01a7, B:103:0x01ad, B:109:0x01c2, B:113:0x01c8, B:114:0x01ca, B:127:0x0209, B:133:0x020f, B:134:0x0210, B:135:0x0214, B:137:0x021a, B:139:0x022d, B:142:0x023f, B:143:0x0241, B:153:0x0269, B:154:0x026a, B:99:0x01a0, B:144:0x0242, B:146:0x024f, B:148:0x025f, B:149:0x0265, B:115:0x01cb, B:117:0x01d5, B:119:0x01db, B:120:0x01df, B:122:0x01e5, B:124:0x01f8, B:126:0x0208, B:129:0x020b), top: B:164:0x0161, outer: #3, inners: #0, #2 }] */
    /* JADX WARN: Code duplicated, block: B:146:0x024f A[Catch: all -> 0x0267, TryCatch #0 {, blocks: (B:144:0x0242, B:146:0x024f, B:148:0x025f, B:149:0x0265), top: B:162:0x0242, outer: #1 }] */
    /* JADX WARN: Code duplicated, block: B:148:0x025f A[Catch: all -> 0x0267, TryCatch #0 {, blocks: (B:144:0x0242, B:146:0x024f, B:148:0x025f, B:149:0x0265), top: B:162:0x0242, outer: #1 }] */
    /* JADX WARN: Code duplicated, block: B:162:0x0242 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:183:0x023c A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:82:0x0149  */
    private boolean isRedundantMessage(StatusBarNotification statusBarNotification, Notification notification, Bundle bundle, String str, String str2, String str3) {
        String str4;
        boolean z;
        Iterator it;
        Iterator<String> it2;
        CharSequence[] charSequenceArray;
        Bundle bundle2;
        CharSequence charSequence;
        if (statusBarNotification == null || statusBarNotification.getKey() == null) {
            return false;
        }
        try {
            ArrayList arrayList = new ArrayList();
            if (bundle != null) {
                Parcelable[] parcelableArray = bundle.getParcelableArray("android.messages");
                if (parcelableArray != null) {
                    for (int iMax = Math.max(0, parcelableArray.length - 25); iMax < parcelableArray.length; iMax++) {
                        if ((parcelableArray[iMax] instanceof Bundle) && (charSequence = (bundle2 = (Bundle) parcelableArray[iMax]).getCharSequence(Settings.EXTRA_TEXT)) != null && charSequence.length() > 0) {
                            CharSequence charSequence2 = bundle2.getCharSequence("sender");
                            arrayList.add((charSequence2 == null ? "" : charSequence2.toString()) + "\u0001" + charSequence.toString());
                        }
                    }
                }
                if (arrayList.isEmpty() && (charSequenceArray = bundle.getCharSequenceArray("android.textLines")) != null) {
                    for (int iMax2 = Math.max(0, charSequenceArray.length - 25); iMax2 < charSequenceArray.length; iMax2++) {
                        if (charSequenceArray[iMax2] != null && charSequenceArray[iMax2].length() > 0) {
                            arrayList.add(charSequenceArray[iMax2].toString());
                        }
                    }
                }
            }
            if (arrayList.isEmpty()) {
                String str5 = str2 == null ? "" : str2;
                if (str3 == null) {
                    str3 = "";
                }
                String str6 = str5 + "\u0001" + str3;
                if (!str6.trim().isEmpty()) {
                    arrayList.add(str6);
                }
            }
            if (arrayList.isEmpty()) {
                return false;
            }
            if (bundle != null) {
                CharSequence charSequence3 = bundle.getCharSequence("android.conversationTitle");
                String string = charSequence3 == null ? "" : charSequence3.toString();
                if (string.isEmpty() && notification != null && notification.getShortcutId() != null) {
                    string = notification.getShortcutId();
                }
                if (!string.isEmpty() || str2 == null || str2.isEmpty() || "微信".equals(str2) || "QQ".equals(str2) || str2.contains("条新消息") || str2.contains("新消息")) {
                    str2 = string;
                }
                if (!str2.isEmpty()) {
                    str4 = str + "\u0001" + str2;
                } else {
                    str4 = null;
                }
            } else {
                str4 = null;
            }
            String str7 = (String) arrayList.get(arrayList.size() - 1);
            String key = statusBarNotification.getKey();
            long postTime = statusBarNotification.getPostTime();
            synchronized (this.msgStates) {
                MsgState msgState = this.msgStates.get(key);
                if (msgState != null && postTime < msgState.postTime) {
                    return true;
                }
                if (msgState == null) {
                    msgState = new MsgState(postTime);
                    this.msgStates.put(key, msgState);
                    if (this.msgStates.size() > 256) {
                        Iterator<String> it3 = this.msgStates.keySet().iterator();
                        if (it3.hasNext()) {
                            it3.next();
                            it3.remove();
                        }
                    }
                } else {
                    msgState.postTime = postTime;
                }
                Iterator it4 = arrayList.iterator();
                while (true) {
                    if (!it4.hasNext()) {
                        z = true;
                        break;
                    }
                    if (!msgState.seen.contains((String) it4.next())) {
                        z = false;
                        break;
                    }
                }
                if (z) {
                    return true;
                }
                if (str4 != null) {
                    synchronized (this.lastMsgByConversation) {
                        String str8 = this.lastMsgByConversation.get(str4);
                        if (str8 != null && str8.equals(str7)) {
                            Iterator it5 = arrayList.iterator();
                            while (it5.hasNext()) {
                                msgState.seen.add((String) it5.next());
                                if (msgState.seen.size() > 64) {
                                    msgState.seen.remove(msgState.seen.iterator().next());
                                }
                            }
                            return true;
                        }
                        it = arrayList.iterator();
                        while (it.hasNext()) {
                            msgState.seen.add((String) it.next());
                            if (msgState.seen.size() > 64) {
                                msgState.seen.remove(msgState.seen.iterator().next());
                            }
                        }
                        if (str4 != null) {
                            synchronized (this.lastMsgByConversation) {
                                this.lastMsgByConversation.put(str4, str7);
                                if (this.lastMsgByConversation.size() > 256) {
                                    it2 = this.lastMsgByConversation.keySet().iterator();
                                    if (it2.hasNext()) {
                                        it2.next();
                                        it2.remove();
                                    }
                                }
                            }
                        }
                        return false;
                    }
                }
                it = arrayList.iterator();
                while (it.hasNext()) {
                    msgState.seen.add((String) it.next());
                    if (msgState.seen.size() > 64) {
                        msgState.seen.remove(msgState.seen.iterator().next());
                    }
                }
                if (str4 != null) {
                    synchronized (this.lastMsgByConversation) {
                        this.lastMsgByConversation.put(str4, str7);
                        if (this.lastMsgByConversation.size() > 256) {
                            it2 = this.lastMsgByConversation.keySet().iterator();
                            if (it2.hasNext()) {
                                it2.next();
                                it2.remove();
                            }
                        }
                    }
                }
                return false;
            }
        } catch (Throwable th) {
            return false;
        }
    }

    private static class RecentNotif {
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

        private RecentNotif() {
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        registerCommandReceiver();
    }

    @Override // android.service.notification.NotificationListenerService, android.app.Service
    public void onDestroy() {
        super.onDestroy();
        try {
            if (this.commandReceiverRegistered) {
                unregisterReceiver(this.commandReceiver);
                this.commandReceiverRegistered = false;
            }
        } catch (Throwable th) {
        }
        releaseMediaController();
    }

    // v203: periodic update check (ported from 2.0). First run
    // 60s after the listener connects, then every 6 hours. A
    // newer GitHub release posts one system notification (once
    // per versionCode) — never a silent install.
    private boolean updateCheckStarted = false;
    private final android.os.Handler updateCheckHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateCheckRunnable = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                UpdateChecker.checkAsync(NotificationService.this, new UpdateChecker.Callback() {
                    @Override // com.jarvis.bubblenotify.UpdateChecker.Callback
                    public void onResult(UpdateChecker.UpdateInfo info) {
                        if (info != null && info.available) {
                            UpdateChecker.notifyUpdate(NotificationService.this, info);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
            try {
                NotificationService.this.updateCheckHandler.postDelayed(NotificationService.this.updateCheckRunnable, 21600000L);
            } catch (Throwable ignored2) {
            }
        }
    };

    private void startUpdateCheck() {
        if (this.updateCheckStarted) {
            return;
        }
        this.updateCheckStarted = true;
        try {
            this.updateCheckHandler.postDelayed(this.updateCheckRunnable, 60000L);
        } catch (Throwable ignored) {
        }
    }

    @Override // android.service.notification.NotificationListenerService
    public void onListenerConnected() {
        Log.i(Settings.TAG, "Notification listener connected.");
        // v205 diag: prove liveness in the SystemUI log (the
        // svc log file may be unwritable under scoped storage).
        startUpdateCheck();
        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications != null) {
                long jUptimeMillis = SystemClock.uptimeMillis();
                synchronized (this.seenNotifKeys) {
                    for (StatusBarNotification statusBarNotification : activeNotifications) {
                        if (statusBarNotification != null && statusBarNotification.getKey() != null) {
                            this.seenNotifKeys.put(statusBarNotification.getKey(), Long.valueOf(jUptimeMillis));
                            try {
                                Bundle bundle = statusBarNotification.getNotification() == null ? null : statusBarNotification.getNotification().extras;
                                String text = getText(bundle, "android.title");
                                String text2 = getText(bundle, "android.text");
                                if (text2 == null || text2.isEmpty()) {
                                    text2 = getText(bundle, "android.bigText");
                                }
                                int i = bundle != null ? bundle.getInt("android.progress", 0) : 0;
                                int i2 = bundle != null ? bundle.getInt("android.progressMax", 0) : 0;
                                Map<String, String> map = this.seenNotifKeyContent;
                                String key = statusBarNotification.getKey();
                                if (text == null) {
                                    text = "";
                                }
                                if (text2 == null) {
                                    text2 = "";
                                }
                                map.put(key, text + "\u0001" + text2 + "\u0001" + i + "/" + i2);
                            } catch (Throwable th) {
                            }
                        }
                    }
                }
                Log.i(Settings.TAG, "Seeded " + activeNotifications.length + " active notification keys.");
            }
        } catch (Throwable th2) {
            Log.i(Settings.TAG, "Seed active keys failed", th2);
        }
    }

    // fixed188: code recognition for ALL notifications (user
    // "取件码这个你确定是全部通知都可以识别"). The old call sat
    // at the end of onNotificationPosted, after the music/call/
    // ongoing branches, so music/call notifications and ongoing
    // (non-delivery, non-progress) notifications were never
    // scanned. Run it up front, for any notification with a
    // title/text, before any branch returns — the scan only reads
    // text, it does not depend on the notification popping a
    // bubble. The late call is removed to avoid double scans.
    private void tryExtractCodeEarly(StatusBarNotification sbn, Notification notification, Bundle bundle, SharedPreferences prefs) {
        try {
            String title = getText(bundle, "android.title");
            String text = getText(bundle, "android.text");
            if (text == null || text.isEmpty()) {
                text = getText(bundle, "android.bigText");
            }
            if (text == null || text.isEmpty()) {
                text = getLastTextLine(bundle);
            }
            if (text == null || text.isEmpty()) {
                text = getLatestMessageText(bundle);
            }
            if ((text == null || text.isEmpty()) && notification != null && notification.tickerText != null && !notification.tickerText.toString().trim().isEmpty()) {
                text = notification.tickerText.toString();
            }
            String text3 = (text == null || text.isEmpty()) ? getText(bundle, "android.subText") : text;
            if ((title == null || title.isEmpty()) && (text3 == null || text3.isEmpty())) {
                return;
            }
            final String key = sbn.getKey();
            LocalCodeExtractor.extractCode(title, text3, prefs, new LocalCodeExtractor.Callback() {
                @Override
                public void onCodeFound(String kind, String code) {
                    Intent intent = new Intent(Settings.ACTION_CODE);
                    intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                    intent.putExtra(Settings.EXTRA_KEY, key);
                    intent.putExtra(Settings.EXTRA_CODE_KIND, kind);
                    intent.putExtra(Settings.EXTRA_CODE, code);
                    NotificationService.this.sendBroadcast(intent);
                    Log.i(Settings.TAG, "Local code found: " + kind);
                }

                @Override
                public void onNoCode() {
                }
            });
        } catch (Throwable th) {
            Log.e(Settings.TAG, "LocalCodeExtractor.extractCode failed", th);
        }
    }

    /* JADX WARN: Code duplicated, block: B:131:0x0251  */
    @Override // android.service.notification.NotificationListenerService
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        CharSequence charSequence;
        int i;
        int i2;
        boolean z;
        int i3;
        MediaSession.Token token;
        if (statusBarNotification == null) {
            return;
        }
        try {
            if (statusBarNotification.getNotification() != null) {
                SharedPreferences sharedPreferences = getSharedPreferences(Settings.PREFS_NAME, 0);
                if (!sharedPreferences.getBoolean(Settings.KEY_ENABLED, true)) {
                    return;
                }
                String packageName = statusBarNotification.getPackageName();
                if (getPackageName().equals(packageName)) {
                    return;
                }
                Notification notification = statusBarNotification.getNotification();
                Bundle bundle = notification.extras;
                FileLog.i("Posted pkg=" + packageName + " key=" + statusBarNotification.getKey() + " ongoing=" + isOngoing(notification) + " flags=" + notification.flags);
                if (isPushService(packageName)) {
                    // fixed176: push-service delegation — the key's 2nd
                    // field is the REAL app (e.g. com.hpbr.bosszhipin
                    // for BOSS). Show the real app's icon/name instead
                    // of dropping, like flymebobo 2.4 v328. Only drop
                    // when the key carries no valid installed app.
                    String realPkg = keyPackage(statusBarNotification.getKey());
                    if (realPkg != null && !realPkg.isEmpty()
                            && !realPkg.equals(packageName)
                            && isInstalledApp(realPkg)) {
                        FileLog.i("Push service delegated: pkg=" + packageName + " realPkg=" + realPkg + " key=" + statusBarNotification.getKey());
                        packageName = realPkg;
                    } else {
                        FileLog.i("Push service notification dropped: pkg=" + packageName + " title=" + getText(bundle, "android.title") + " text=" + getText(bundle, "android.text"));
                        return;
                    }
                }
                // fixed188: scan for a code up front, before the
                // music/call/ongoing branches return early.
                tryExtractCodeEarly(statusBarNotification, notification, bundle, sharedPreferences);
                if (sharedPreferences.getBoolean(Settings.KEY_MUSIC_ENABLED, true)) {
                    if (bundle != null) {
                        token = (MediaSession.Token) bundle.getParcelable("android.mediaSession");
                    } else {
                        token = null;
                    }
                    if (token != null) {
                        handleMediaNotification(statusBarNotification, notification, token);
                        return;
                    }
                }
                if (isCallNotification(statusBarNotification, notification)) {
                    if (sharedPreferences.getBoolean(Settings.KEY_CALL_ENABLED, true)) {
                        handleCallNotification(statusBarNotification, notification, bundle);
                        return;
                    }
                    return;
                }
                boolean z2 = Settings.PKG_MEITUAN.equals(packageName) || Settings.PKG_ELEME.equals(packageName) || Settings.PKG_AMAP.equals(packageName);
                int i4 = bundle != null ? bundle.getInt("android.progress", 0) : 0;
                int i5 = bundle != null ? bundle.getInt("android.progressMax", 0) : 0;
                boolean z3 = bundle != null && bundle.getBoolean("android.progressIndeterminate", false);
                // fixed213: RemoteViews progress (fixed206 had it).
                // The store's install progress often lives in the
                // RemoteViews, not the extras — without this, z4 is
                // false and an ongoing download is dropped before
                // the download detection below ever runs.
                int[] progressFromRemoteViews = readProgressFromRemoteViews(notification);
                if (progressFromRemoteViews != null) {
                    if ((progressFromRemoteViews[0] * 100) / progressFromRemoteViews[1] > (i5 > 0 ? (i4 * 100) / i5 : -1)) {
                        i4 = progressFromRemoteViews[0];
                        i5 = progressFromRemoteViews[1];
                    }
                }
                boolean z4 = i5 > 0 || z3;
                if (isOngoing(notification) && !z2 && !z4) {
                    FileLog.i("Notification skipped: ongoing, not delivery: " + packageName + " flags=" + notification.flags);
                }
                if ((!isOngoing(notification) || z2 || z4) && !Settings.SYSTEMUI_PACKAGE.equals(packageName) && !"android".equals(packageName)) {
                    String text = getText(bundle, "android.title");
                    String text2 = getText(bundle, "android.text");
                    if (text2 == null || text2.isEmpty()) {
                        text2 = getText(bundle, "android.bigText");
                    }
                    if (text2 == null || text2.isEmpty()) {
                        text2 = getLastTextLine(bundle);
                    }
                    if (text2 == null || text2.isEmpty()) {
                        text2 = getLatestMessageText(bundle);
                    }
                    if ((text2 == null || text2.isEmpty()) && (charSequence = notification.tickerText) != null && !charSequence.toString().trim().isEmpty()) {
                        text2 = charSequence.toString();
                    }
                    String text3 = (text2 == null || text2.isEmpty()) ? getText(bundle, "android.subText") : text2;
                    if ((text == null || text.isEmpty()) && (text3 == null || text3.isEmpty())) {
                        FileLog.i("Notification dropped: empty title and text: " + packageName + " flags=" + notification.flags);
                    }
                    if ((text != null && !text.isEmpty()) || (text3 != null && !text3.isEmpty())) {
                        // fixed212: download detection (2.0 dual
                        // condition, ported). A download is a regular
                        // notification capsule whose title carries the
                        // live percent; the notification is NOT
                        // cancelled so it keeps re-posting and the
                        // capsule refreshes in real time.
                        i = i4;
                        i2 = i5;
                        z = z4;
                        boolean isDownload = false;
                        int downloadPct = -1;
                        // fixed213: no `if (z)` gate — 2.0 calls
                        // readDownloadPct unconditionally. The percent
                        // can live in RemoteViews or text while the
                        // extras carry no progress; gating on z made
                        // fixed212 miss those downloads entirely.
                        String channelId = null;
                        try { channelId = notification.getChannelId(); } catch (Throwable ignored) { }
                        String dlInfo = bundle != null ? getText(bundle, "android.infoText") : null;
                        downloadPct = readDownloadPct(notification, text, text3, dlInfo);
                        if (downloadPct >= 0 && hasDownloadSignal(packageName, channelId, text, text3)) {
                            isDownload = true;
                            // Force the progress flags from the percent
                            // so the download passes the progress-based
                            // exemptions (dedup / duplicate) below.
                            z = true;
                            i = downloadPct;
                            i2 = 100;
                        }
                        // fixed219: completion — pct hit 100, or the
                        // app rewrote the notification as done.
                        boolean downloadDone = isDownload
                                && (downloadPct >= 100
                                        || isDownloadComplete(text, text3));
                        if (downloadDone) {
                            text = (text != null && text.contains("安装")) ? "安装完成" : "下载完成";
                            try {
                                sendLogLine("dlDone pkg=" + packageName
                                        + " title=" + text + " pct=" + downloadPct);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (isDownload && !downloadDone) {
                            // Strip a trailing percent the store may
                            // already carry ("正在安装 18%") so it is
                            // not repeated, then append the live one.
                            String base = text == null ? "" : text.replaceAll("\\s+[0-9]+%$", "").trim();
                            if (base.isEmpty()) {
                                base = text3 == null ? "" : text3.replaceAll("\\s+[0-9]+%$", "").trim();
                            }
                            if (!base.isEmpty()) {
                                text = base + " " + downloadPct + "%";
                            }
                        }
                        // fixed215: download detection diagnostic, forwarded
                        // to SystemUI's log (the listener's own file is
                        // not readable). Logged for any post from a
                        // downloader pkg or with a percent, so a missed
                        // download shows its inputs and the verdict.
                        try {
                            if (isDownload || downloadPct >= 0 || isDownloaderPkg(packageName)) {
                                sendLogLine("dlDetect pkg=" + packageName
                                        + " title=" + text + " text=" + text3
                                        + " pct=" + downloadPct
                                        + " signal=" + hasDownloadSignal(packageName, channelId, text, text3)
                                        + " isDownload=" + isDownload);
                            }
                        } catch (Throwable ignored) {
                        }
                        if (!z && System.currentTimeMillis() - statusBarNotification.getPostTime() > 5000) {
                            FileLog.i("Stale notification dropped (>5s old): " + packageName);
                            return;
                        }
                        int i6 = z ? 512 : z2 ? 576 : 578;
                        if ((notification.flags & i6) != 0) {
                            FileLog.i("Notification dropped by flags mask " + i6 + ": " + packageName);
                            return;
                        }
                        int i7 = i2;
                        boolean z5 = z3;
                        if (isRepeatNotifFingerprint(packageName, statusBarNotification, text, text3, i, i7)) {
                            FileLog.i("Repeat notification fingerprint dropped: " + packageName);
                            return;
                        }
                        if (isRepeatNotifKey(statusBarNotification.getKey(), text, text3, i, i7)) {
                            FileLog.i("Repeat notification key dropped: " + statusBarNotification.getKey());
                            return;
                        }
                        // fixed197: downloads skip the redundant-message
                        // filter (same exemption as isDuplicateNotif
                        // below). It drops a re-post whose title+text
                        // were already seen and never looks at
                        // progress — so a download's progress updates
                        // (same title/text, only progress changed)
                        // were all dropped here, the RecentNotif kept
                        // progressMax=0, and the tick never tracked
                        // it. That is the "still 0%".
                        if (!z && isRedundantMessage(statusBarNotification, notification, bundle, packageName, text, text3)) {
                            FileLog.i("Redundant message dropped (already seen): " + packageName);
                            return;
                        }
                        if (!z && isDuplicateNotif(packageName, text, text3)) {
                            FileLog.i("Duplicate notification dropped: " + packageName);
                            return;
                        }
                        if (isRedundantGroupSummary(notification, packageName)) {
                            FileLog.i("Group summary dropped (child already shown): " + packageName);
                            return;
                        }
                        noteNotifGroup(notification, packageName);
                        RecentNotif recentNotif = new RecentNotif();
                        recentNotif.key = statusBarNotification.getKey();
                        recentNotif.isDownload = isDownload;
                        recentNotif.isDownloadDone = downloadDone;
                        recentNotif.title = text;
                        recentNotif.text = text3;
                        recentNotif.pkg = packageName;
                        recentNotif.intent = notification.contentIntent;
                        recentNotif.time = statusBarNotification.getPostTime();
                        recentNotif.progress = i;
                        recentNotif.progressMax = i7;
                        recentNotif.progressIndeterminate = z5;
                        if (notification.actions != null) {
                            for (Notification.Action action : notification.actions) {
                                if (action != null && action.actionIntent != null && action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                                    recentNotif.replyIntent = action.actionIntent;
                                    recentNotif.replyKey = action.getRemoteInputs()[0].getResultKey();
                                    break;
                                }
                            }
                        }
                        IconResult iconResultExtractIcon = extractIcon(notification, packageName);
                        if (iconResultExtractIcon != null && iconResultExtractIcon.bitmap != null) {
                            recentNotif.icon = scaleBitmap(iconResultExtractIcon.bitmap, 96);
                            recentNotif.iconSmall = iconResultExtractIcon.small;
                            recentNotif.iconAccent = iconResultExtractIcon.accent;
                        }
                        synchronized (this.recentNotifs) {
                            try {
                                for (int size = this.recentNotifs.size() - 1; size >= 0; size--) {
                                    if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.recentNotifs.get(size).key)) {
                                        this.recentNotifs.remove(size);
                                    }
                                }
                                this.recentNotifs.add(0, recentNotif);
                                FileLog.i("Added notif key=" + recentNotif.key + " pkg=" + packageName + " title=" + recentNotif.title + " text=" + recentNotif.text);
                                while (this.recentNotifs.size() > 8) {
                                    RecentNotif recentNotifRemove = this.recentNotifs.remove(this.recentNotifs.size() - 1);
                                    if (recentNotifRemove != null && recentNotifRemove.key != null) {
                                        synchronized (this.selfCancelledKeys) {
                                            this.selfCancelledKeys.remove(recentNotifRemove.key);
                                        }
                                    }
                                }
                            } catch (Throwable th) {
                                throw th;
                            }
                        }
                        // fixed212: do NOT cancel a download — it must
                        // stay alive so it keeps re-posting and the
                        // capsule refreshes its percent in real time.
                        // (fixed202 root cause: cancelling killed the
                        // re-posts, so the capsule stuck at 0%.)
                        if (!isDownload && statusBarNotification.getKey() != null) {
                            try {
                                synchronized (this.selfCancelledKeys) {
                                    this.selfCancelledKeys.add(statusBarNotification.getKey());
                                }
                                cancelNotification(statusBarNotification.getKey());
                            } catch (Throwable th2) {
                                Log.e(Settings.TAG, "cancelNotification failed", th2);
                            }
                        }
                        // Download deleted (2026-11-23): no post-time
                        // download setup, no tick. Downloads are
                        // regular notifications now.
                        broadcastNotifyList();
                        Log.i(Settings.TAG, "Notification posted: " + packageName);
                    }
                }
            }
        } catch (Throwable th4) {
            Log.e(Settings.TAG, "Error in onNotificationPosted", th4);
        }
    }

    @Override // android.service.notification.NotificationListenerService
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        boolean zContains;
        boolean z;
        boolean zContains2;
        boolean zContains3;
        StatusBarNotification[] activeNotifications = null;
        if (statusBarNotification == null) {
            z = false;
        } else {
            try {
                if (statusBarNotification.getKey() == null) {
                    zContains = false;
                } else {
                    synchronized (this.selfCancelledKeys) {
                        zContains = this.selfCancelledKeys.contains(statusBarNotification.getKey());
                    }
                }
                if (zContains) {
                    z = false;
                } else {
                    synchronized (this.recentNotifs) {
                        z = false;
                        for (int size = this.recentNotifs.size() - 1; size >= 0; size--) {
                            if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.recentNotifs.get(size).key)) {
                                this.recentNotifs.remove(size);
                                z = true;
                            }
                        }
                    }
                }
                if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.currentMediaKey)) {
                    releaseMediaController();
                    sendMusicInactiveDelayed(statusBarNotification.getPackageName());
                    Log.i(Settings.TAG, "Media notification removed, music inactive.");
                }
                if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.callKey)) {
                    synchronized (this.selfCancelledKeys) {
                        zContains2 = this.selfCancelledKeys.contains(statusBarNotification.getKey());
                    }
                    if (!zContains2) {
                        this.callKey = null;
                        sendCallBroadcast(Settings.CALL_IDLE, null, null);
                        Log.i(Settings.TAG, "Call notification removed, call idle.");
                    } else {
                        Log.i(Settings.TAG, "Call notification removed (self-cancel, call still active).");
                    }
                }
            } catch (Throwable th) {
                Log.e(Settings.TAG, "Error in onNotificationRemoved", th);
                return;
            }
        }
        try {
            activeNotifications = getActiveNotifications();
        } catch (Throwable th2) {
        }
        if (activeNotifications == null || activeNotifications.length == 0) {
            synchronized (this.recentNotifs) {
                for (int size2 = this.recentNotifs.size() - 1; size2 >= 0; size2--) {
                    String str = this.recentNotifs.get(size2).key;
                    if (str == null) {
                        zContains3 = false;
                    } else {
                        synchronized (this.selfCancelledKeys) {
                            zContains3 = this.selfCancelledKeys.contains(str);
                        }
                    }
                    if (!zContains3) {
                        this.recentNotifs.remove(size2);
                        z = true;
                    }
                }
            }
        }
        if (z) {
            broadcastNotifyList();
            Log.i(Settings.TAG, "Notify list updated after removal.");
        }
    }

    public void broadcastNotifyList() {
        String string;
        ArrayList<Parcelable> arrayList = new ArrayList<>();
        synchronized (this.recentNotifs) {
            for (RecentNotif recentNotif : this.recentNotifs) {
                Bundle bundle = new Bundle();
                bundle.putString(Settings.ITEM_KEY, recentNotif.key);
                bundle.putString(Settings.ITEM_TITLE, recentNotif.title);
                bundle.putString(Settings.ITEM_TEXT, recentNotif.text);
                bundle.putString(Settings.ITEM_PACKAGE, recentNotif.pkg);
                if (recentNotif.isDownload) {
                    bundle.putBoolean(Settings.ITEM_IS_DOWNLOAD, true);
                }
                if (recentNotif.isDownloadDone) {
                    bundle.putBoolean(Settings.ITEM_DOWNLOAD_DONE, true);
                }
                bundle.putLong(Settings.ITEM_TIME, recentNotif.time);
                if (recentNotif.progressMax > 0 || recentNotif.progressIndeterminate) {
                    bundle.putInt(Settings.ITEM_PROGRESS, recentNotif.progress);
                    bundle.putInt(Settings.ITEM_PROGRESS_MAX, recentNotif.progressMax);
                    bundle.putBoolean(Settings.ITEM_PROGRESS_INDETERMINATE, recentNotif.progressIndeterminate);
                }
                if (recentNotif.icon != null) {
                    bundle.putParcelable(Settings.ITEM_ICON, recentNotif.icon);
                }
                if (recentNotif.iconSmall) {
                    bundle.putBoolean(Settings.ITEM_ICON_SMALL, true);
                    bundle.putInt(Settings.ITEM_ICON_ACCENT, recentNotif.iconAccent);
                }
                if (recentNotif.intent != null) {
                    bundle.putParcelable(Settings.ITEM_INTENT, recentNotif.intent);
                }
                if (recentNotif.replyIntent != null && recentNotif.replyKey != null) {
                    bundle.putParcelable(Settings.ITEM_REPLY_INTENT, recentNotif.replyIntent);
                    bundle.putString(Settings.ITEM_REPLY_KEY, recentNotif.replyKey);
                }
                arrayList.add(bundle);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Parcelable parcelable : arrayList) {
            if ((parcelable instanceof Bundle) && (string = ((Bundle) parcelable).getString(Settings.ITEM_KEY)) != null) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(string);
            }
        }
        FileLog.i("Broadcast notify list: " + arrayList.size() + " items keys=" + String.valueOf(sb));
        Intent intent = new Intent(Settings.ACTION_NOTIFY_LIST);
        intent.setPackage(Settings.SYSTEMUI_PACKAGE);
        intent.putParcelableArrayListExtra(Settings.EXTRA_NOTIFY_LIST, arrayList);
        sendBroadcast(intent);
    }

    private boolean isCallNotification(StatusBarNotification statusBarNotification, Notification notification) {
        if ("call".equals(notification.category)) {
            return true;
        }
        if (notification.fullScreenIntent != null) {
            String packageName = statusBarNotification.getPackageName();
            if (packageName != null) {
                String lowerCase = packageName.toLowerCase();
                if (lowerCase.contains("dialer") || lowerCase.contains("phone") || lowerCase.contains("telecom") || lowerCase.contains("incallui") || lowerCase.contains("call")) {
                    return true;
                }
            }
            try {
                Bundle bundle = notification.extras;
                String text = bundle != null ? getText(bundle, "android.title") : null;
                if (text != null) {
                    return text.replaceAll("[^0-9]", "").length() >= 7 || text.contains("来电") || text.contains("呼入") || text.contains("正在呼叫");
                }
                return false;
            } catch (Throwable th) {
                return false;
            }
        }
        return false;
    }

    private void handleCallNotification(StatusBarNotification statusBarNotification, Notification notification, Bundle bundle) {
        String text = getText(bundle, "android.title");
        String text2 = getText(bundle, "android.text");
        this.callKey = statusBarNotification.getKey();
        sendCallBroadcast(Settings.CALL_RINGING, text, text2);
        Log.i(Settings.TAG, "Call ringing from " + statusBarNotification.getPackageName());
        // fixed177: do NOT cancel the call notification any more
        // (2.0 way). Its actions — answer / hangup / speaker — must
        // stay live so fireCallAction can fire them. The system
        // banner / fullscreen UI is suppressed by the
        // CallBannerSuppress + InCallUiHide hooks instead.
    }

    // fixed177: 2.0 speaker switching — fire the incallui call
    // notification's OWN action via actionIntent.send(). No
    // setSpeakerphoneOn, no direct AudioManager/TelecomManager
    // calls. The notification carries ONE speaker action (a toggle,
    // "打开扬声器" / "关闭扬声器"), so a speaker command fires
    // whichever speaker action exists. Two passes: first match the
    // stored call key, then package-only (the key goes stale when
    // the notification refreshes after answering).
    // fixed179: 2.0 speaker switching, verbatim (v125/v138/
    // v141). Fire the incallui notification's OWN action via
    // actionIntent.send(). The notification carries ONE speaker
    // action (a toggle); a speaker command fires whichever
    // speaker action exists (kind 3 or 4) — one tap = one toggle.
    // No setSpeakerphoneOn, no direct AudioManager/TelecomManager.
    // Two passes: first match the stored call key, then
    // package-only (the key goes stale when the notification
    // refreshes after answering). Also require the action's
    // PendingIntent to be created by incallui (2.0).
    private void fireCallAction(String command) {
        if (command == null) {
            return;
        }
        int want = classifyCallAction(command);
        if (want < 0) {
            return;
        }
        if (fireCallActionPass(want, this.callKey, true)) {
            return;
        }
        fireCallActionPass(want, this.callKey, false);
    }

    private boolean fireCallActionPass(int want, String key,
            boolean matchKey) {
        try {
            StatusBarNotification[] act = getActiveNotifications();
            if (act == null) {
                return false;
            }
            for (StatusBarNotification sbn : act) {
                if (sbn == null
                        || !"com.android.incallui".equals(
                                sbn.getPackageName())) {
                    continue;
                }
                if (matchKey && key != null && !key.isEmpty()
                        && !key.equals(sbn.getKey())) {
                    continue;
                }
                Notification n = sbn.getNotification();
                if (n == null || n.actions == null) {
                    continue;
                }
                for (Notification.Action a : n.actions) {
                    if (a == null || a.actionIntent == null
                            || a.title == null) {
                        continue;
                    }
                    int kind = classifyCallAction(a.title.toString());
                    // 2.0 v138: a speaker command fires whichever
                    // speaker action exists — it toggles.
                    boolean speakerCmd = want == 3 || want == 4;
                    boolean speakerAct = kind == 3 || kind == 4;
                    if (speakerCmd ? !speakerAct : kind != want) {
                        continue;
                    }
                    try {
                        if (!"com.android.incallui".equals(
                                a.actionIntent.getCreatorPackage())) {
                            continue;
                        }
                    } catch (Throwable ignored) {
                        continue;
                    }
                    try {
                        a.actionIntent.send();
                        FileLog.i("fireCallAction fired kind=" + kind
                                + " title=" + a.title + " key=" + sbn.getKey());
                        return true;
                    } catch (Throwable t) {
                        FileLog.i("fireCallAction send threw: " + t);
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.i("fireCallAction threw: " + t);
        }
        return false;
    }

    // fixed179: 2.0's classifier, verbatim (v125, Flyme Island
    // CallActionPolicy.classify, same titles): 0 answer, 1 reject,
    // 2 hangup, 3 speaker on, 4 speaker off, 5 silence. Exact
    // equals for answer/reject/hangup (lowercase); the speaker
    // button shows an icon, not text, so its real title is often
    // just "扬声器" / "speaker" / "免提" (a single toggle) — match
    // by CONTAINS (v130). No kind 6: 2.0 has no speaker-answer
    // action, and the 扬声器接听 button is removed (fixed179).
    private int classifyCallAction(String title) {
        if (title == null) {
            return -1;
        }
        String t = title.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("接听") || t.equals("接聽")
                || t.equals("answer") || t.equals("accept")) {
            return 0;
        }
        if (t.equals("拒接") || t.equals("拒绝") || t.equals("拒絕")
                || t.equals("decline") || t.equals("reject")) {
            return 1;
        }
        if (t.equals("挂断") || t.equals("掛斷")
                || t.equals("hang up")) {
            return 2;
        }
        if (t.contains("扬声器") || t.contains("揚聲器")
                || t.contains("speaker") || t.contains("免提")) {
            if (t.contains("关") || t.contains("關")
                    || t.contains("off")) {
                return 4;
            }
            return 3;
        }
        if (t.equals("静音") || t.equals("靜音")
                || t.equals("silence")) {
            return 5;
        }
        return -1;
    }

    private void sendCallBroadcast(String str, String str2, String str3) {
        Intent intent = new Intent(Settings.ACTION_CALL);
        intent.setPackage(Settings.SYSTEMUI_PACKAGE);
        intent.putExtra(Settings.EXTRA_CALL_STATE, str);
        intent.putExtra(Settings.EXTRA_CALL_NAME, str2);
        intent.putExtra(Settings.EXTRA_CALL_NUMBER, str3);
        sendBroadcast(intent);
    }

    private void handleMediaNotification(StatusBarNotification statusBarNotification, Notification notification, MediaSession.Token token) {
        String strTrim;
        CharSequence charSequence;
        CharSequence charSequence2;
        cancelPendingMusicInactive();
        String key = statusBarNotification.getKey();
        if (key != null && key.equals(this.currentMediaKey) && this.mediaController != null) {
            updateLyricCandidates(notification, true);
            try {
                if (notification.extras != null && (charSequence2 = notification.extras.getCharSequence("android.title")) != null && !charSequence2.toString().trim().isEmpty()) {
                    this.currentNotifTitle = charSequence2.toString().trim();
                }
            } catch (Throwable th) {
            }
            sendMusicStateFromController(statusBarNotification.getPackageName(), notification.contentIntent);
            return;
        }
        try {
            if (notification.extras != null && (charSequence = notification.extras.getCharSequence("android.title")) != null) {
                strTrim = charSequence.toString().trim();
            } else {
                strTrim = null;
            }
        } catch (Throwable th2) {
            strTrim = null;
        }
        if (strTrim != null && !strTrim.isEmpty()) {
            this.currentNotifTitle = strTrim;
        }
        updateLyricCandidates(notification, false);
        releaseMediaController();
        try {
            final String packageName = statusBarNotification.getPackageName();
            final PendingIntent pendingIntent = notification.contentIntent;
            MediaController mediaController = new MediaController(this, token);
            this.mediaCallback = new MediaController.Callback() { // from class: com.jarvis.bubblenotify.NotificationService.5
                @Override // android.media.session.MediaController.Callback
                public void onMetadataChanged(MediaMetadata mediaMetadata) {
                    NotificationService.this.sendMusicStateFromController(packageName, pendingIntent);
                }

                @Override // android.media.session.MediaController.Callback
                public void onPlaybackStateChanged(PlaybackState playbackState) {
                    NotificationService.this.sendMusicStateFromController(packageName, pendingIntent);
                }

                @Override // android.media.session.MediaController.Callback
                public void onSessionDestroyed() {
                    NotificationService.this.releaseMediaController();
                    NotificationService.this.sendMusicInactiveDelayed(packageName);
                    Log.i(Settings.TAG, "Media session destroyed, music inactive.");
                }
            };
            mediaController.registerCallback(this.mediaCallback);
            this.mediaController = mediaController;
            this.currentMediaKey = key;
            this.currentMediaTitle = null;
            this.currentMediaDuration = -1L;
            this.swappedLayout = false;
            this.swappedSongTitle = null;
            this.swappedArtist = null;
            this.lastSubtitle = null;
            this.lastRawTitle = null;
            this.lastArtistMeta = null;
            this.lastNotifTitle = null;
            this.currentFavorite = null;
            sendMusicStateFromController(packageName, pendingIntent);
            Log.i(Settings.TAG, "Media session attached: " + packageName);
        } catch (Throwable th3) {
            Log.e(Settings.TAG, "Failed to attach media session", th3);
            releaseMediaController();
        }
    }

    private Bitmap getActiveNotificationLargeIcon() {
        StatusBarNotification[] activeNotifications;
        try {
            if (this.currentMediaKey == null || (activeNotifications = getActiveNotifications()) == null) {
                return null;
            }
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                if (statusBarNotification != null && this.currentMediaKey.equals(statusBarNotification.getKey())) {
                    Notification notification = statusBarNotification.getNotification();
                    if (notification == null || notification.extras == null) {
                        return null;
                    }
                    Parcelable parcelable = notification.extras.getParcelable("android.largeIcon");
                    if (parcelable instanceof Bitmap) {
                        return (Bitmap) parcelable;
                    }
                    try {
                        Icon largeIcon = notification.getLargeIcon();
                        if (largeIcon != null) {
                            Drawable drawableLoadDrawable = largeIcon.loadDrawable(this);
                            if (drawableLoadDrawable instanceof BitmapDrawable) {
                                return ((BitmapDrawable) drawableLoadDrawable).getBitmap();
                            }
                        }
                    } catch (Throwable th) {
                    }
                    return null;
                }
            }
        } catch (Throwable th2) {
        }
        return null;
    }

    private void refreshLyricCandidatesFromActive() {
        StatusBarNotification[] activeNotifications;
        try {
            if (this.currentMediaKey == null || (activeNotifications = getActiveNotifications()) == null) {
                return;
            }
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                if (statusBarNotification != null && this.currentMediaKey.equals(statusBarNotification.getKey())) {
                    updateLyricCandidates(statusBarNotification.getNotification(), true);
                    return;
                }
            }
        } catch (Throwable th) {
        }
    }

    /* JADX WARN: Code duplicated, block: B:16:0x0035  */
    private void updateLyricCandidates(Notification notification, boolean z) {
        String strTrim;
        String strTrim2;
        String strTrim3 = null;
        if (notification == null) {
            strTrim = null;
            strTrim2 = null;
        } else {
            try {
                if (notification.extras != null) {
                    CharSequence charSequence = notification.extras.getCharSequence("android.text");
                    if (charSequence == null) {
                        strTrim = null;
                    } else {
                        strTrim = charSequence.toString().trim();
                    }
                    CharSequence charSequence2 = notification.extras.getCharSequence("android.bigText");
                    if (charSequence2 == null) {
                        strTrim2 = null;
                    } else {
                        strTrim2 = charSequence2.toString().trim();
                    }
                } else {
                    strTrim = null;
                    strTrim2 = null;
                }
            } catch (Throwable th) {
                return;
            }
        }
        if (notification != null && notification.tickerText != null) {
            strTrim3 = notification.tickerText.toString().trim();
        }
        if (z) {
            if (strTrim != null && !strTrim.isEmpty()) {
                this.currentLyricText = strTrim;
            }
            if (strTrim2 != null && !strTrim2.isEmpty()) {
                this.currentLyricBigText = strTrim2;
            }
            if (strTrim3 != null && !strTrim3.isEmpty()) {
                this.currentLyricTicker = strTrim3;
                return;
            }
            return;
        }
        this.currentLyricText = strTrim;
        this.currentLyricBigText = strTrim2;
        this.currentLyricTicker = strTrim3;
    }

    // fixed182: 2.0's isLyricCandidate, completed — a lyric line
    // is never the song title, the artist, the album artist, a
    // song+artist combination, a credits line, a status line or a
    // copyright notice. The credit/status/copyright checks now live
    // INSIDE the check (2.0 v172/v228), so every candidate is
    // filtered instead of relying on the caller.
    private static boolean isLyricCandidate(String str, String str2, String str3, String str4) {
        if (str == null) {
            return false;
        }
        try {
            String strTrim = str.trim();
            if (strTrim.isEmpty()) {
                return false;
            }
            if (isCreditOrLyricTitle(strTrim)) {
                return false;
            }
            if (isStatusMessage(strTrim)) {
                return false;
            }
            if (isCopyrightNotice(strTrim)) {
                return false;
            }
            if (str2 != null && !str2.isEmpty() && (strTrim.equals(str2) || strTrim.startsWith(str2 + " - ") || strTrim.endsWith(" - " + str2) || strTrim.startsWith(str2 + "：") || strTrim.endsWith("：" + str2) || strTrim.startsWith(str2 + " \u2013 ") || strTrim.endsWith(" \u2013 " + str2))) {
                return false;
            }
            if (str3 != null && !str3.isEmpty() && (strTrim.equals(str3) || strTrim.equals(str2 + " - " + str3) || strTrim.equals(str3 + " - " + str2) || strTrim.equals(str2 + "：" + str3) || strTrim.equals(str3 + "：" + str2) || strTrim.equals(str2 + " \u2013 " + str3) || strTrim.equals(str3 + " \u2013 " + str2))) {
                return false;
            }
            // fixed182: 2.0 v233 — "artist - album" is metadata,
            // never a lyric. A real lyric line never starts with
            // the artist's name followed by a separator.
            if (str3 != null && !str3.isEmpty() && (strTrim.startsWith(str3 + " - ") || strTrim.startsWith(str3 + " \u2013 ") || strTrim.startsWith(str3 + "："))) {
                return false;
            }
            if (str4 != null && !str4.isEmpty() && strTrim.equals(str4)) {
                return false;
            }
            if (str2 != null && !str2.isEmpty()) {
                String strReplaceAll = strTrim.replaceAll("\\s+", "");
                String strReplaceAll2 = str2.replaceAll("\\s+", "");
                if (!strReplaceAll2.isEmpty() && (strReplaceAll.equals(strReplaceAll2) || strReplaceAll.startsWith(strReplaceAll2 + "-") || strReplaceAll.startsWith(strReplaceAll2 + "："))) {
                    return false;
                }
                if (str3 != null && !str3.isEmpty()) {
                    String strReplaceAll3 = str3.replaceAll("\\s+", "");
                    return (!strReplaceAll3.isEmpty() && strReplaceAll.contains(strReplaceAll2) && strReplaceAll.contains(strReplaceAll3)) ? false : true;
                }
                return true;
            }
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean isCreditOrLyricTitle(String str) {
        if (str == null) {
            return false;
        }
        String strTrim = str.trim();
        // fixed182: 2.0's credits, completed — the 15 original
        // Chinese prefixes, plus v228 production credits
        // (制作/混音/母带/监制/出品/发行).
        String[] strArr = {"词：", "曲：", "词:", "曲:", "作词", "作曲", "编曲", "演唱", "原唱", "词曲", "作词：", "作曲：", "编曲：", "演唱：", "原唱：",
                "制作", "混音", "母带", "监制", "出品", "发行", "制作：", "混音：", "母带：", "监制：", "出品：", "发行："};
        for (int i = 0; i < strArr.length; i++) {
            if (strTrim.startsWith(strArr[i])) {
                return true;
            }
        }
        // fixed182: 2.0 v209 English credits (case-insensitive) —
        // "Lyrics by: ..." from LRCLIB / ticker was passing as a
        // title and as a lyric.
        String low = strTrim.toLowerCase(java.util.Locale.ROOT);
        String[] enPrefixes = {"lyrics by", "lyric by", "lyrics:", "lyric:", "written by", "composed by", "music by", "words by", "arranged by", "produced by", "mixed by", "mastered by"};
        for (String p : enPrefixes) {
            if (low.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // fixed182: 2.0 v228 — a status line is never a lyric
    // (NetEase "网易云音乐正在播放", "列表为空", "暂无", ...).
    private static boolean isStatusMessage(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.contains("\u5217\u8868\u4e3a\u7a7a")
                || str.contains("\u70b9\u51fb\u9009\u62e9")
                || str.contains("\u70b9\u51fb\u64ad\u653e")
                || str.contains("\u6682\u65e0")
                || str.contains("\u672a\u64ad\u653e")
                || str.contains("\u6ca1\u6709\u6b63\u5728\u64ad\u653e")
                || str.contains("\u97f3\u4e50\u6b63\u5728\u64ad\u653e");
    }

    // fixed182: 2.0 v228 — a copyright notice is never a lyric
    // ("(© ...)" / "(P) 2024 ..." / "版权所有").
    private static boolean isCopyrightNotice(String str) {
        if (str == null) {
            return false;
        }
        String t = str.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.contains("\u00a9") || t.contains("\u2117")
                || t.contains("\u7248\u6743")) {
            return true;
        }
        String low = t.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("copyright")) {
            return true;
        }
        if (t.startsWith("(P)") || t.startsWith("(C)")
                || t.startsWith("(p)") || t.startsWith("(c)")) {
            return true;
        }
        return false;
    }

    private void cancelPendingMusicInactive() {
        try {
            if (this.pendingMusicInactiveRunnable != null) {
                this.mainHandler.removeCallbacks(this.pendingMusicInactiveRunnable);
                this.pendingMusicInactiveRunnable = null;
            }
        } catch (Throwable th) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendMusicInactiveDelayed(final String str) {
        cancelPendingMusicInactive();
        this.pendingMusicInactiveRunnable = new Runnable() { // from class: com.jarvis.bubblenotify.NotificationService.6
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (NotificationService.this.mediaController == null && NotificationService.this.currentMediaKey == null) {
                        NotificationService.this.sendMusicBroadcast(false, false, null, null, null, str, null, -1L, -1L, null);
                        Log.i(Settings.TAG, "Media inactive (delayed).");
                    }
                } catch (Throwable th) {
                    Log.i(Settings.TAG, "Delayed music inactive failed: " + th.getClass().getSimpleName());
                }
            }
        };
        this.mainHandler.postDelayed(this.pendingMusicInactiveRunnable, MUSIC_INACTIVE_DELAY_MS);
    }

    /* JADX WARN: Code duplicated, block: B:104:0x012d A[Catch: all -> 0x0354, TRY_ENTER, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:113:0x015e  */
    /* JADX WARN: Code duplicated, block: B:115:0x0162 A[ADDED_TO_REGION] */
    /* JADX WARN: Code duplicated, block: B:125:0x0199  */
    /* JADX WARN: Code duplicated, block: B:127:0x019c A[ADDED_TO_REGION] */
    /* JADX WARN: Code duplicated, block: B:192:0x0268  */
    /* JADX WARN: Code duplicated, block: B:194:0x026b A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:196:0x0278  */
    /* JADX WARN: Code duplicated, block: B:199:0x0282 A[ADDED_TO_REGION] */
    /* JADX WARN: Code duplicated, block: B:210:0x02a5 A[ADDED_TO_REGION, REMOVE] */
    /* JADX WARN: Code duplicated, block: B:213:0x02aa A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:226:0x02d2  */
    /* JADX WARN: Code duplicated, block: B:229:0x02df  */
    /* JADX WARN: Code duplicated, block: B:230:0x02e0  */
    /* JADX WARN: Code duplicated, block: B:232:0x02e4  */
    /* JADX WARN: Code duplicated, block: B:233:0x02e5  */
    /* JADX WARN: Code duplicated, block: B:235:0x02e8  */
    /* JADX WARN: Code duplicated, block: B:236:0x02ea A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:238:0x02f2 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:239:0x02f5 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:241:0x02fd A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:242:0x0300 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:244:0x0308 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:245:0x030b A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:247:0x0311  */
    /* JADX WARN: Code duplicated, block: B:248:0x0313  */
    /* JADX WARN: Code duplicated, block: B:251:0x031c A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:253:0x0324 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:255:0x032d  */
    /* JADX WARN: Code duplicated, block: B:256:0x032e A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:259:0x033d A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:64:0x00b9 A[ADDED_TO_REGION] */
    /* JADX WARN: Code duplicated, block: B:66:0x00be  */
    /* JADX WARN: Code duplicated, block: B:69:0x00c4 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:75:0x00e0 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:78:0x00e8 A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    /* JADX WARN: Code duplicated, block: B:83:0x00f8 A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:84:0x00fa A[Catch: all -> 0x0354, TryCatch #2 {all -> 0x0354, blocks: (B:5:0x0009, B:7:0x000f, B:13:0x001c, B:28:0x003f, B:261:0x0345, B:31:0x0051, B:33:0x0063, B:35:0x006b, B:37:0x0073, B:45:0x0089, B:47:0x008d, B:51:0x0099, B:53:0x00a1, B:65:0x00bb, B:69:0x00c4, B:71:0x00ca, B:73:0x00d0, B:75:0x00e0, B:76:0x00e2, B:78:0x00e8, B:80:0x00ec, B:82:0x00f4, B:97:0x0116, B:104:0x012d, B:106:0x0133, B:108:0x0139, B:110:0x0156, B:116:0x0164, B:118:0x016a, B:120:0x0170, B:122:0x018d, B:128:0x019e, B:130:0x01a4, B:133:0x01ae, B:197:0x027e, B:200:0x0284, B:202:0x0288, B:205:0x0292, B:207:0x0298, B:211:0x02a6, B:213:0x02aa, B:216:0x02b0, B:218:0x02b7, B:220:0x02bb, B:222:0x02c1, B:225:0x02cc, B:227:0x02d3, B:249:0x0314, B:251:0x031c, B:253:0x0324, B:257:0x0337, B:259:0x033d, B:256:0x032e, B:236:0x02ea, B:238:0x02f2, B:239:0x02f5, B:241:0x02fd, B:242:0x0300, B:244:0x0308, B:245:0x030b, B:224:0x02c9, B:138:0x01bd, B:140:0x01c1, B:142:0x01c9, B:144:0x01cd, B:158:0x01f4, B:160:0x01fa, B:162:0x0206, B:164:0x020a, B:166:0x0212, B:171:0x021f, B:175:0x022b, B:177:0x0235, B:179:0x023b, B:181:0x0243, B:183:0x024b, B:185:0x0253, B:187:0x025b, B:194:0x026b, B:148:0x01d9, B:150:0x01dd, B:152:0x01e5, B:154:0x01e9, B:84:0x00fa, B:86:0x0100), top: B:270:0x0009 }] */
    // fixed189: parse "song-artist" (2.0 v206/v211/v215). Dash
    // order: U+2013 (en dash), then '-', then U+00B7 (middle dot).
    // The dash must not be at either end. Returns [before, after]
    // or null.
    private static String[] parseArtistSong(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int dash = s.indexOf('\u2013');
        if (dash < 0) {
            dash = s.indexOf('-');
        }
        if (dash < 0) {
            dash = s.indexOf('\u00b7');
        }
        if (dash <= 0 || dash >= s.length() - 1) {
            return null;
        }
        String a = s.substring(0, dash).trim();
        String b = s.substring(dash + 1).trim();
        if (a.isEmpty() || b.isEmpty()) {
            return null;
        }
        return new String[]{a, b};
    }

    // fixed189: resolve Flyme song/artist (2.0 v206/v211/v215).
    // DISPLAY_SUBTITLE is "song-artist" (song first); if it does
    // not split, the whole subtitle is the artist. If the song is
    // still unknown, split metadata ARTIST the same way (Flyme's
    // ARTIST is "song-artist", song first). ALBUM is never a song
    // (v215 lesson); metadata TITLE is a lyric, never a song.
    // Returns [song, artist] (either may be null) or null if
    // nothing usable.
    private static String[] resolveFlymeSongArtist(String subtitle, String metaArtist, String album) {
        String song = null;
        String artist = null;
        if (subtitle != null && !subtitle.isEmpty()
                && !isStatusMessage(subtitle)
                && !isCreditOrLyricTitle(subtitle)) {
            String[] p = parseArtistSong(subtitle);
            if (p != null) {
                song = p[0];
                artist = p[1];
            } else {
                artist = subtitle;
            }
        }
        if (song == null && metaArtist != null && !metaArtist.isEmpty()
                && !isStatusMessage(metaArtist)
                && !isCreditOrLyricTitle(metaArtist)) {
            String[] p = parseArtistSong(metaArtist);
            if (p != null) {
                song = p[0];
                if (artist == null) {
                    artist = p[1];
                }
            }
        }
        if (song == null && artist == null) {
            return null;
        }
        return new String[]{song, artist};
    }

    // fixed190: download capsule uses 2.0's dual condition
    // (v110/v241). A download needs BOTH real progress AND a
    // download signal. Pure-progress checks mis-detected chat
    // messages with stale progress extras as downloads.



    public void sendMusicStateFromController(String str, PendingIntent pendingIntent) {
        long j;
        String string;
        String str2;
        boolean z;
        long j2 = -1;
        String str3 = null;
        boolean z2 = false;
        String str4;
        String str5;
        long j3 = 0;
        String string2 = null;
        String string3;
        String strTrim;
        String strTrim2;
        boolean z3;
        String str6;
        String str7;
        long j4;
        String str8;
        String str9;
        String str10;
        Bitmap bitmap;
        Boolean favoriteState;
        Bitmap bitmap2;
        String str11;
        String str12;
        long j5;
        boolean z4;
        boolean z5 = false;
        boolean z6;
        MediaController mediaController = this.mediaController;
        if (mediaController == null) {
            return;
        }
        try {
            PlaybackState playbackState = mediaController.getPlaybackState();
            boolean z7 = playbackState != null && playbackState.getState() == 3;
            long position = playbackState != null ? playbackState.getPosition() : -1L;
            if (z7 && position >= 0 && playbackState != null) {
                try {
                    long lastPositionUpdateTime = playbackState.getLastPositionUpdateTime();
                    if (lastPositionUpdateTime > 0) {
                        position += SystemClock.elapsedRealtime() - lastPositionUpdateTime;
                    }
                    j = position;
                } catch (Throwable th) {
                    j = position;
                }
            } else {
                j = position;
            }
            MediaMetadata metadata = mediaController.getMetadata();
            if (metadata == null) {
                str11 = null;
                str12 = null;
                bitmap2 = null;
                j5 = -1;
                string = null;
            } else {
                String string4 = metadata.getString("android.media.metadata.TITLE");
                string = metadata.getString("android.media.metadata.ARTIST");
                if (this.currentNotifTitle != null && !this.currentNotifTitle.isEmpty() && !isCreditOrLyricTitle(this.currentNotifTitle)) {
                    str2 = this.currentNotifTitle;
                    z = true;
                } else {
                    str2 = string4;
                    z = false;
                }
                try {
                    j2 = metadata.getLong("android.media.metadata.DURATION");
                } catch (Throwable th2) {
                    j2 = -1;
                }
                try {
                    try {
                        try {
                            if (this.currentMediaTitle == null || this.currentMediaTitle.isEmpty()) {
                                str3 = string4;
                            } else {
                                if (j2 > 0) {
                                    str3 = string4;
                                    if (this.currentMediaDuration <= 0 || j2 != this.currentMediaDuration) {
                                    }
                                    z2 = true;
                                    if (!z2 && !z) {
                                        str4 = this.currentMediaTitle;
                                    } else {
                                        str4 = str2;
                                    }
                                    if (j2 > 0 && this.currentMediaDuration > 0 && j2 != this.currentMediaDuration) {
                                        this.swappedLayout = false;
                                        this.swappedSongTitle = null;
                                        this.swappedArtist = null;
                                        this.lastArtistMeta = null;
                                        this.lastNotifTitle = null;
                                        this.currentFavorite = null;
                                    }
                                    if (j2 > 0) {
                                        this.currentMediaDuration = j2;
                                    }
                                    if (!isCreditOrLyricTitle(str4) && this.currentMediaTitle != null && !this.currentMediaTitle.isEmpty()) {
                                        str5 = this.currentMediaTitle;
                                    } else {
                                        if (str4 != null && !str4.isEmpty()) {
                                            this.currentMediaTitle = str4;
                                        }
                                        str5 = str4;
                                    }
                                    j3 = metadata.getLong("android.media.metadata.DURATION");
                                    string2 = metadata.getString("android.media.metadata.ALBUM_ARTIST");
                                    refreshLyricCandidatesFromActive();
                                    string3 = metadata.getString("android.media.metadata.DISPLAY_SUBTITLE");
                                    if (string3 == null && !string3.isEmpty() && string3.contains("-")) {
                                        int iIndexOf = string3.indexOf(45);
                                        strTrim2 = string3.substring(0, iIndexOf).trim();
                                        strTrim = string3.substring(iIndexOf + 1).trim();
                                        if (strTrim2.isEmpty() || strTrim.isEmpty()) {
                                            strTrim = null;
                                            strTrim2 = null;
                                        }
                                    } else {
                                        strTrim = null;
                                        strTrim2 = null;
                                    }
                                    if (strTrim2 != null && string != null && !string.isEmpty() && string.contains("-")) {
                                        int iIndexOf2 = string.indexOf(45);
                                        String strTrim3 = string.substring(0, iIndexOf2).trim();
                                        String strTrim4 = string.substring(iIndexOf2 + 1).trim();
                                        if (!strTrim3.isEmpty() && !strTrim4.isEmpty()) {
                                            strTrim = strTrim4;
                                            strTrim2 = strTrim3;
                                            z3 = true;
                                        } else {
                                            z3 = false;
                                        }
                                    } else {
                                        z3 = false;
                                    }
                                    if (strTrim2 != null || str3 == null || str3.isEmpty()) {
                                        str6 = str5;
                                        str7 = str3;
                                        j4 = j3;
                                    } else {
                                        str7 = str3;
                                        if (strTrim2.equals(str7)) {
                                            str6 = str5;
                                            j4 = j3;
                                        } else if (string == null || !strTrim2.equals(string)) {
                                            if (z3) {
                                                z4 = (this.lastArtistMeta == null || !this.lastArtistMeta.equals(string) || this.lastRawTitle == null || this.lastRawTitle.equals(str7)) ? false : true;
                                            } else {
                                                z4 = (this.lastSubtitle == null || !this.lastSubtitle.equals(string3) || this.lastRawTitle == null || this.lastRawTitle.equals(str7)) ? false : true;
                                            }
                                            str6 = str5;
                                            if (this.lastNotifTitle == null) {
                                                j4 = j3;
                                            } else {
                                                j4 = j3;
                                                if (this.lastNotifTitle.equals(this.currentNotifTitle) && this.lastRawTitle != null && !this.lastRawTitle.equals(str7) && !str7.equals(this.currentNotifTitle)) {
                                                    z5 = true;
                                                }
                                                if (!strTrim2.equals(this.currentNotifTitle) || z4 || ((!z3 && str7.length() > strTrim2.length()) || isCreditOrLyricTitle(str7) || str7.startsWith("女：") || str7.startsWith("男：") || str7.startsWith("合：") || str7.startsWith("女:") || str7.startsWith("男:") || z5)) {
                                                    z6 = true;
                                                } else {
                                                    z6 = false;
                                                }
                                                if (z6) {
                                                    this.swappedLayout = true;
                                                    this.swappedSongTitle = strTrim2;
                                                    this.swappedArtist = strTrim;
                                                }
                                            }
                                            z5 = false;
                                            if (strTrim2.equals(this.currentNotifTitle)) {
                                                z6 = true;
                                            } else {
                                                z6 = true;
                                            }
                                            if (z6) {
                                                this.swappedLayout = true;
                                                this.swappedSongTitle = strTrim2;
                                                this.swappedArtist = strTrim;
                                            }
                                        } else {
                                            str6 = str5;
                                            j4 = j3;
                                        }
                                    }
                                    if (!this.swappedLayout && strTrim2 != null && this.swappedSongTitle != null && !strTrim2.equals(this.swappedSongTitle) && str7 != null && str7.equals(strTrim2)) {
                                        this.swappedLayout = false;
                                        str8 = null;
                                        this.swappedSongTitle = null;
                                        this.swappedArtist = null;
                                    } else {
                                        str8 = null;
                                    }
                                    if (!this.swappedLayout && this.swappedSongTitle != null && str7 != null && !str7.isEmpty()) {
                                        String str13 = this.swappedSongTitle;
                                        if (string == null || string.isEmpty() || string.contains(this.swappedSongTitle)) {
                                            string = this.swappedArtist;
                                        }
                                        this.currentMediaTitle = str13;
                                        str6 = str13;
                                        str9 = str7;
                                    } else {
                                        str9 = str8;
                                    }
                                    this.lastSubtitle = string3;
                                    this.lastRawTitle = str7;
                                    this.lastArtistMeta = string;
                                    this.lastNotifTitle = this.currentNotifTitle;
                                    if (strTrim2 == null) {
                                        strTrim2 = str6;
                                    }
                                    if (strTrim == null) {
                                        strTrim = string;
                                    }
                                    if (str9 != null) {
                                        str10 = str9;
                                    } else if (isLyricCandidate(this.currentLyricTicker, strTrim2, strTrim, string2)) {
                                        str10 = this.currentLyricTicker;
                                    } else if (isLyricCandidate(this.currentLyricText, strTrim2, strTrim, string2)) {
                                        str10 = this.currentLyricText;
                                    } else if (isLyricCandidate(this.currentLyricBigText, strTrim2, strTrim, string2)) {
                                        str10 = this.currentLyricBigText;
                                    } else if (!isLyricCandidate(string3, strTrim2, strTrim, string2)) {
                                        str10 = str8;
                                    } else {
                                        str10 = string3;
                                    }
                                    bitmap = metadata.getBitmap("android.media.metadata.ALBUM_ART");
                                    if (bitmap == null) {
                                        bitmap = metadata.getBitmap("android.media.metadata.DISPLAY_ICON");
                                    }
                                    if (bitmap == null) {
                                        bitmap = getActiveNotificationLargeIcon();
                                    }
                                    if (bitmap != null) {
                                        bitmap = scaleBitmap(bitmap, 96);
                                    }
                                    favoriteState = readFavoriteState(metadata);
                                    if (favoriteState != null) {
                                        this.currentFavorite = favoriteState;
                                    }
                                    bitmap2 = bitmap;
                                    str11 = str10;
                                    str12 = str6;
                                    j5 = j4;
                                } else {
                                    str3 = string4;
                                }
                                if (j2 <= 0 && j > 5000) {
                                    z2 = true;
                                }
                                if (!z2) {
                                    str4 = str2;
                                } else {
                                    str4 = str2;
                                }
                                if (j2 > 0) {
                                    this.swappedLayout = false;
                                    this.swappedSongTitle = null;
                                    this.swappedArtist = null;
                                    this.lastArtistMeta = null;
                                    this.lastNotifTitle = null;
                                    this.currentFavorite = null;
                                }
                                if (j2 > 0) {
                                    this.currentMediaDuration = j2;
                                }
                                if (!isCreditOrLyricTitle(str4)) {
                                    if (str4 != null) {
                                        this.currentMediaTitle = str4;
                                    }
                                    str5 = str4;
                                } else {
                                    if (str4 != null) {
                                        this.currentMediaTitle = str4;
                                    }
                                    str5 = str4;
                                }
                                j3 = metadata.getLong("android.media.metadata.DURATION");
                                string2 = metadata.getString("android.media.metadata.ALBUM_ARTIST");
                                refreshLyricCandidatesFromActive();
                                string3 = metadata.getString("android.media.metadata.DISPLAY_SUBTITLE");
                                if (string3 == null) {
                                    strTrim = null;
                                    strTrim2 = null;
                                } else {
                                    strTrim = null;
                                    strTrim2 = null;
                                }
                                if (strTrim2 != null) {
                                    z3 = false;
                                } else {
                                    z3 = false;
                                }
                                if (strTrim2 != null) {
                                    str6 = str5;
                                    str7 = str3;
                                    j4 = j3;
                                } else {
                                    str6 = str5;
                                    str7 = str3;
                                    j4 = j3;
                                }
                                if (!this.swappedLayout) {
                                    str8 = null;
                                } else {
                                    str8 = null;
                                }
                                if (!this.swappedLayout) {
                                    str9 = str8;
                                } else {
                                    str9 = str8;
                                }
                                this.lastSubtitle = string3;
                                this.lastRawTitle = str7;
                                this.lastArtistMeta = string;
                                this.lastNotifTitle = this.currentNotifTitle;
                                if (strTrim2 == null) {
                                    strTrim2 = str6;
                                }
                                if (strTrim == null) {
                                    strTrim = string;
                                }
                                if (str9 != null) {
                                    str10 = str9;
                                } else if (isLyricCandidate(this.currentLyricTicker, strTrim2, strTrim, string2)) {
                                    str10 = this.currentLyricTicker;
                                } else if (isLyricCandidate(this.currentLyricText, strTrim2, strTrim, string2)) {
                                    str10 = this.currentLyricText;
                                } else if (isLyricCandidate(this.currentLyricBigText, strTrim2, strTrim, string2)) {
                                    str10 = this.currentLyricBigText;
                                } else if (!isLyricCandidate(string3, strTrim2, strTrim, string2)) {
                                    str10 = str8;
                                } else {
                                    str10 = string3;
                                }
                                bitmap = metadata.getBitmap("android.media.metadata.ALBUM_ART");
                                if (bitmap == null) {
                                    bitmap = metadata.getBitmap("android.media.metadata.DISPLAY_ICON");
                                }
                                if (bitmap == null) {
                                    bitmap = getActiveNotificationLargeIcon();
                                }
                                if (bitmap != null) {
                                    bitmap = scaleBitmap(bitmap, 96);
                                }
                                favoriteState = readFavoriteState(metadata);
                                if (favoriteState != null) {
                                    this.currentFavorite = favoriteState;
                                }
                                bitmap2 = bitmap;
                                str11 = str10;
                                str12 = str6;
                                j5 = j4;
                            }
                            j3 = metadata.getLong("android.media.metadata.DURATION");
                        } catch (Throwable th3) {
                            j3 = -1;
                        }
                        string2 = metadata.getString("android.media.metadata.ALBUM_ARTIST");
                    } catch (Throwable th4) {
                        string2 = null;
                    }
                    string3 = metadata.getString("android.media.metadata.DISPLAY_SUBTITLE");
                } catch (Throwable th5) {
                    string3 = null;
                }
                z2 = false;
                if (!z2) {
                    str4 = str2;
                } else {
                    str4 = str2;
                }
                if (j2 > 0) {
                    this.swappedLayout = false;
                    this.swappedSongTitle = null;
                    this.swappedArtist = null;
                    this.lastArtistMeta = null;
                    this.lastNotifTitle = null;
                    this.currentFavorite = null;
                }
                if (j2 > 0) {
                    this.currentMediaDuration = j2;
                }
                if (!isCreditOrLyricTitle(str4)) {
                    if (str4 != null) {
                        this.currentMediaTitle = str4;
                    }
                    str5 = str4;
                } else {
                    if (str4 != null) {
                        this.currentMediaTitle = str4;
                    }
                    str5 = str4;
                }
                refreshLyricCandidatesFromActive();
                if (string3 == null) {
                    strTrim = null;
                    strTrim2 = null;
                } else {
                    strTrim = null;
                    strTrim2 = null;
                }
                if (strTrim2 != null) {
                    z3 = false;
                } else {
                    z3 = false;
                }
                if (strTrim2 != null) {
                    str6 = str5;
                    str7 = str3;
                    j4 = j3;
                } else {
                    str6 = str5;
                    str7 = str3;
                    j4 = j3;
                }
                if (!this.swappedLayout) {
                    str8 = null;
                } else {
                    str8 = null;
                }
                if (!this.swappedLayout) {
                    str9 = str8;
                } else {
                    str9 = str8;
                }
                this.lastSubtitle = string3;
                this.lastRawTitle = str7;
                this.lastArtistMeta = string;
                this.lastNotifTitle = this.currentNotifTitle;
                if (strTrim2 == null) {
                    strTrim2 = str6;
                }
                if (strTrim == null) {
                    strTrim = string;
                }
                if (str9 != null) {
                    str10 = str9;
                } else if (isLyricCandidate(this.currentLyricTicker, strTrim2, strTrim, string2)) {
                    str10 = this.currentLyricTicker;
                } else if (isLyricCandidate(this.currentLyricText, strTrim2, strTrim, string2)) {
                    str10 = this.currentLyricText;
                } else if (isLyricCandidate(this.currentLyricBigText, strTrim2, strTrim, string2)) {
                    str10 = this.currentLyricBigText;
                } else if (!isLyricCandidate(string3, strTrim2, strTrim, string2)) {
                    str10 = str8;
                } else {
                    str10 = string3;
                }
                bitmap = metadata.getBitmap("android.media.metadata.ALBUM_ART");
                if (bitmap == null) {
                    bitmap = metadata.getBitmap("android.media.metadata.DISPLAY_ICON");
                }
                if (bitmap == null) {
                    bitmap = getActiveNotificationLargeIcon();
                }
                if (bitmap != null) {
                    bitmap = scaleBitmap(bitmap, 96);
                }
                favoriteState = readFavoriteState(metadata);
                if (favoriteState != null) {
                    this.currentFavorite = favoriteState;
                }
                bitmap2 = bitmap;
                str11 = str10;
                str12 = str6;
                j5 = j4;
            }
            // fixed189: QQ Music Flyme (com.meizu.media.music)
            // song/artist/lyric/duration recognition (user 2026-11-23
            // "识别异常了" / "不识别歌词" / "歌曲时长也不识别了").
            // Root cause: the DISPLAY_SUBTITLE "song-artist" split
            // was dead code (condition `string3 == null`, always
            // false), so the title came from the notification title
            // (a lyric line) and the artist came from metadata
            // ARTIST raw (Flyme's ARTIST is "song-artist" or a
            // lyric). Resolve it here, before the network-lyrics
            // fetch and the broadcast, so maybeFetchNetworkLyrics
            // also gets the right song/artist.
            if ("com.meizu.media.music".equals(str) && metadata != null) {
                String flymeSubtitle = null;
                String flymeArtist = null;
                try { flymeSubtitle = metadata.getString("android.media.metadata.DISPLAY_SUBTITLE"); } catch (Throwable ignored) { }
                try { flymeArtist = metadata.getString("android.media.metadata.ARTIST"); } catch (Throwable ignored) { }
                String flymeAlbum = null;
                try { flymeAlbum = metadata.getString("android.media.metadata.ALBUM"); } catch (Throwable ignored) { }
                String[] resolved = resolveFlymeSongArtist(flymeSubtitle, flymeArtist, flymeAlbum);
                if (resolved != null) {
                    if (resolved[0] != null && !resolved[0].isEmpty()) {
                        str12 = resolved[0];
                        this.currentMediaTitle = resolved[0];
                    }
                    if (resolved[1] != null && !resolved[1].isEmpty()) {
                        string = resolved[1];
                    }
                }
                // Lyric: Flyme puts the current lyric in the
                // notification title. If the resolved lyric is
                // missing or is just the artist name, use the
                // notification title as the lyric.
                if ((str11 == null || str11.isEmpty() || (string != null && str11.equals(string)))
                        && this.currentNotifTitle != null && !this.currentNotifTitle.isEmpty()
                        && isLyricCandidate(this.currentNotifTitle, str12, string, string2)) {
                    str11 = this.currentNotifTitle;
                }
            }
            // fixed194: duration fallback for ALL music apps (user
            // 2026-11-23 "QQ音乐flyme版有了，其他音乐平台没有了").
            // The broadcast duration j5 comes from a deep branch
            // (j4 <- j3) that may not run, leaving j5 = 0 ("--:--").
            // Fall back to the DURATION read at the top (j2) and to
            // the cached duration. This was inside the Flyme-only
            // block in fixed189, so only Flyme got it; other
            // platforms (QQ original, NetEase, Migu...) stayed
            // "--:--". j2/currentMediaDuration are generic, not
            // Flyme-specific, so the fallback is safe for all.
            if (j5 <= 0 && j2 > 0) {
                j5 = j2;
            }
            if (j5 <= 0 && this.currentMediaDuration > 0) {
                j5 = this.currentMediaDuration;
            }
            // fixed181: network lyrics. Remember the package /
            // intent so the 1s tick can re-send. If we know the
            // song, trigger the LRCLIB fetch (guarded, once per
            // song). If no local lyric was found, fall back to the
            // network line at the current position.
            this.netLyricTickPackage = str;
            this.netLyricTickIntent = pendingIntent;
            if (str12 != null && !str12.isEmpty()) {
                maybeFetchNetworkLyrics(str12, string, j5);
            }
            if ((str11 == null || str11.isEmpty()) && str12 != null && !str12.isEmpty()) {
                String netLine = netLyricLineAt(str12, string, j);
                if (netLine != null) {
                    str11 = netLine;
                }
            }
            sendMusicBroadcast(true, z7, str12, string, bitmap2, str, pendingIntent, j5, j, str11);
        } catch (Throwable th6) {
            Log.e(Settings.TAG, "Error reading media state", th6);
        }
    }

    public void sendMusicBroadcast(boolean z, boolean z2, String str, String str2, Bitmap bitmap, String str3, PendingIntent pendingIntent, long j, long j2, String str4) {
        Intent intent = new Intent(Settings.ACTION_MUSIC);
        intent.setPackage(Settings.SYSTEMUI_PACKAGE);
        intent.putExtra(Settings.EXTRA_MUSIC_ACTIVE, z);
        intent.putExtra(Settings.EXTRA_MUSIC_PLAYING, z2);
        intent.putExtra(Settings.EXTRA_MUSIC_TITLE, str);
        intent.putExtra(Settings.EXTRA_MUSIC_ARTIST, str2);
        intent.putExtra(Settings.EXTRA_PACKAGE, str3);
        intent.putExtra(Settings.EXTRA_MUSIC_DURATION, j);
        intent.putExtra(Settings.EXTRA_MUSIC_POSITION, j2);
        if (str4 != null) {
            intent.putExtra(Settings.EXTRA_MUSIC_LYRIC, str4);
        }
        if (this.currentFavorite != null) {
            intent.putExtra(Settings.EXTRA_MUSIC_FAVORITE, this.currentFavorite.booleanValue());
        }
        if (bitmap != null) {
            intent.putExtra(Settings.EXTRA_MUSIC_ALBUM, bitmap);
        }
        if (pendingIntent != null) {
            intent.putExtra(Settings.EXTRA_INTENT, pendingIntent);
        }
        sendBroadcast(intent);
    }

    public void releaseMediaController() {
        stopNetLyricTick();
        this.netLyricLines = null;
        this.netLyricSong = null;
        this.netLyricArtist = null;
        this.netLyricLastLine = null;
        MediaController mediaController = this.mediaController;
        this.mediaController = null;
        this.currentMediaKey = null;
        this.currentFavorite = null;
        if (mediaController != null) {
            try {
                if (this.mediaCallback != null) {
                    mediaController.unregisterCallback(this.mediaCallback);
                }
            } catch (Throwable th) {
            }
        }
        this.mediaCallback = null;
    }

    private boolean sendFavoriteAction() {
        StatusBarNotification[] activeNotifications;
        try {
            if (this.currentMediaKey == null || (activeNotifications = getActiveNotifications()) == null) {
                return false;
            }
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                if (statusBarNotification != null && statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.currentMediaKey)) {
                    Notification notification = statusBarNotification.getNotification();
                    if (notification != null && notification.actions != null) {
                        for (Notification.Action action : notification.actions) {
                            if (action != null && action.title != null) {
                                String lowerCase = action.title.toString().toLowerCase();
                                if ((lowerCase.contains("收藏") || lowerCase.contains("喜欢") || lowerCase.contains("like") || lowerCase.contains(Settings.CONTROL_FAVORITE) || lowerCase.contains("favourite") || lowerCase.contains("heart") || lowerCase.contains("红心")) && action.actionIntent != null) {
                                    action.actionIntent.send();
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                    return false;
                }
            }
        } catch (Throwable th) {
            Log.e(Settings.TAG, "sendFavoriteAction failed", th);
        }
        return false;
    }

    private Boolean readFavoriteState(MediaMetadata mediaMetadata) {
        StatusBarNotification[] activeNotifications;
        if (mediaMetadata != null) {
            try {
                Rating rating = mediaMetadata.getRating("android.media.metadata.USER_RATING");
                if (rating != null && rating.getRatingStyle() == 1) {
                    return Boolean.valueOf(rating.hasHeart());
                }
            } catch (Throwable th) {
            }
        }
        try {
            if (this.currentMediaKey == null || (activeNotifications = getActiveNotifications()) == null) {
                return null;
            }
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                if (statusBarNotification != null && statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.currentMediaKey)) {
                    Notification notification = statusBarNotification.getNotification();
                    if (notification != null && notification.actions != null) {
                        for (Notification.Action action : notification.actions) {
                            if (action != null && action.title != null) {
                                String string = action.title.toString();
                                String lowerCase = string.toLowerCase();
                                if (string.contains("收藏") || string.contains("喜欢") || lowerCase.contains("like") || lowerCase.contains(Settings.CONTROL_FAVORITE) || lowerCase.contains("favourite") || lowerCase.contains("heart") || string.contains("红心")) {
                                    if (!string.contains("已收藏") && !string.contains("已喜欢") && !string.contains("取消收藏") && !string.contains("取消喜欢") && !string.contains("已加") && !lowerCase.contains("liked") && !lowerCase.contains("unlike")) {
                                        return null;
                                    }
                                    return Boolean.TRUE;
                                }
                            }
                        }
                        return null;
                    }
                    return null;
                }
            }
        } catch (Throwable th2) {
        }
        return null;
    }

    public void handleMusicControl(String str, long j) {
        if (str == null) {
            return;
        }
        try {
            MediaController mediaController = this.mediaController;
            if (mediaController == null || mediaController.getTransportControls() == null) {
                Log.i(Settings.TAG, "Music control ignored, no active session: " + str);
                return;
            }
            boolean z = true;
            if (Settings.CONTROL_TOGGLE.equals(str)) {
                PlaybackState playbackState = mediaController.getPlaybackState();
                if (playbackState == null || playbackState.getState() != 3) {
                    z = false;
                }
                if (z) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
                Log.i(Settings.TAG, "Music control: toggle -> " + (z ? "pause" : "play"));
                return;
            }
            if (Settings.CONTROL_NEXT.equals(str)) {
                mediaController.getTransportControls().skipToNext();
                Log.i(Settings.TAG, "Music control: next");
                return;
            }
            if (Settings.CONTROL_PREV.equals(str)) {
                mediaController.getTransportControls().skipToPrevious();
                Log.i(Settings.TAG, "Music control: prev");
                return;
            }
            if (Settings.CONTROL_STOP.equals(str)) {
                mediaController.getTransportControls().stop();
                Log.i(Settings.TAG, "Music control: stop");
                return;
            }
            if (Settings.CONTROL_FAVORITE.equals(str)) {
                boolean zSendFavoriteAction = sendFavoriteAction();
                if (!zSendFavoriteAction) {
                    try {
                        mediaController.getTransportControls().setRating(Rating.newHeartRating(true));
                    } catch (Throwable th) {
                        z = zSendFavoriteAction;
                    }
                } else {
                    z = zSendFavoriteAction;
                }
                Log.i(Settings.TAG, "Music control: favorite -> " + z);
                return;
            }
            if (Settings.CONTROL_SEEK.equals(str) && j >= 0) {
                mediaController.getTransportControls().seekTo(j);
                Log.i(Settings.TAG, "Music control: seek to " + j);
            }
        } catch (Throwable th2) {
            Log.e(Settings.TAG, "Error handling music control " + str, th2);
        }
    }

    private void registerCommandReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Settings.ACTION_MUSIC_CONTROL);
        intentFilter.addAction(Settings.ACTION_DISMISS);
        intentFilter.addAction(Settings.ACTION_CALL_CONTROL);
        try {
            Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, Integer.TYPE).invoke(this, this.commandReceiver, intentFilter, 2);
            this.commandReceiverRegistered = true;
            Log.i(Settings.TAG, "Command receiver registered (exported).");
        } catch (Throwable th) {
            try {
                registerReceiver(this.commandReceiver, intentFilter);
                this.commandReceiverRegistered = true;
                Log.i(Settings.TAG, "Command receiver registered (legacy).");
            } catch (Throwable th2) {
                Log.e(Settings.TAG, "Failed to register command receiver", th2);
            }
        }
    }

    private static boolean isOngoing(Notification notification) {
        return ((notification.flags & 2) == 0 && (notification.flags & 32) == 0) ? false : true;
    }

    private static String getLastTextLine(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        try {
            CharSequence[] charSequenceArray = bundle.getCharSequenceArray("android.textLines");
            if (charSequenceArray != null) {
                for (int length = charSequenceArray.length - 1; length >= 0; length--) {
                    if (charSequenceArray[length] != null && !charSequenceArray[length].toString().trim().isEmpty()) {
                        return charSequenceArray[length].toString();
                    }
                }
            }
        } catch (Throwable th) {
        }
        return null;
    }

    private static String getLatestMessageText(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        try {
            Parcelable[] parcelableArray = bundle.getParcelableArray("android.messages");
            if (parcelableArray != null) {
                for (int length = parcelableArray.length - 1; length >= 0; length--) {
                    Parcelable parcelable = parcelableArray[length];
                    if (parcelable != null && "android.app.Notification$MessagingStyle$Message".equals(parcelable.getClass().getName())) {
                        try {
                            Object objInvoke = parcelable.getClass().getMethod("getText", new Class[0]).invoke(parcelable, new Object[0]);
                            if ((objInvoke instanceof CharSequence) && !objInvoke.toString().trim().isEmpty()) {
                                return objInvoke.toString();
                            }
                        } catch (Throwable th) {
                        }
                    }
                }
            }
        } catch (Throwable th2) {
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getText(Bundle bundle, String str) {
        if (bundle == null) {
            return null;
        }
        try {
            CharSequence charSequence = bundle.getCharSequence(str);
            if (charSequence == null) {
                return null;
            }
            return charSequence.toString();
        } catch (Throwable th) {
            return null;
        }
    }

    private static class IconResult {
        int accent;
        Bitmap bitmap;
        boolean small;

        private IconResult() {
        }
    }

    private IconResult extractIcon(Notification notification, String str) {
        Drawable applicationIcon;
        Drawable drawableLoadDrawable;
        Bitmap bitmapAppIconViaPackageContext = appIconViaPackageContext(str);
        if (bitmapAppIconViaPackageContext != null && !isBadIconBitmap(bitmapAppIconViaPackageContext)) {
            IconResult iconResult = new IconResult();
            iconResult.bitmap = bitmapAppIconViaPackageContext;
            return iconResult;
        }
        Log.i(Settings.TAG, "PackageContext icon bad/blank for " + str + ", trying resources");
        Bitmap bitmapAppIconBitmapFromResources = appIconBitmapFromResources(str);
        if (bitmapAppIconBitmapFromResources != null && !isBadIconBitmap(bitmapAppIconBitmapFromResources)) {
            IconResult iconResult2 = new IconResult();
            iconResult2.bitmap = bitmapAppIconBitmapFromResources;
            return iconResult2;
        }
        Bitmap bitmapLauncherIconBitmap = launcherIconBitmap(str);
        if (bitmapLauncherIconBitmap != null && !isBadIconBitmap(bitmapLauncherIconBitmap)) {
            IconResult iconResult3 = new IconResult();
            iconResult3.bitmap = bitmapLauncherIconBitmap;
            return iconResult3;
        }
        try {
            applicationIcon = getPackageManager().getApplicationIcon(str);
        } catch (Throwable th) {
            applicationIcon = null;
        }
        if (applicationIcon != null) {
            Bitmap bitmapDrawableToBitmap = drawableToBitmap(applicationIcon);
            if (bitmapDrawableToBitmap != null && !isBadIconBitmap(bitmapDrawableToBitmap)) {
                IconResult iconResult4 = new IconResult();
                iconResult4.bitmap = bitmapDrawableToBitmap;
                return iconResult4;
            }
            Log.i(Settings.TAG, "App icon blank/white, falling back to small icon: " + str);
        }
        try {
            if (notification.getSmallIcon() != null && (drawableLoadDrawable = notification.getSmallIcon().loadDrawable(this)) != null) {
                Bitmap bitmapDrawableToBitmap2 = drawableToBitmap(drawableLoadDrawable);
                if (bitmapDrawableToBitmap2 != null && !isBadIconBitmap(bitmapDrawableToBitmap2)) {
                    IconResult iconResult5 = new IconResult();
                    iconResult5.bitmap = bitmapDrawableToBitmap2;
                    iconResult5.small = true;
                    iconResult5.accent = notification.color;
                    return iconResult5;
                }
                Log.i(Settings.TAG, "Small icon bad/white for " + str + ", using placeholder");
            }
        } catch (Throwable th2) {
        }
        return null;
    }

    private Bitmap appIconViaPackageContext(String str) {
        try {
            Context contextCreatePackageContext = createPackageContext(str, 3);
            if (contextCreatePackageContext == null) {
                return null;
            }
            return drawableToBitmap(contextCreatePackageContext.getPackageManager().getApplicationIcon(str));
        } catch (Throwable th) {
            Log.i(Settings.TAG, "PackageContext icon failed: " + str + " " + String.valueOf(th));
            return null;
        }
    }

    private Bitmap appIconBitmapFromResources(String str) {
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(str, 0);
            if (applicationInfo != null && applicationInfo.icon != 0) {
                Resources resourcesForApplication = getPackageManager().getResourcesForApplication(str);
                try {
                    Bitmap bitmapDecodeResource = BitmapFactory.decodeResource(resourcesForApplication, applicationInfo.icon);
                    if (bitmapDecodeResource != null) {
                        return bitmapDecodeResource;
                    }
                } catch (Throwable th) {
                }
                try {
                    return drawableToBitmap(resourcesForApplication.getDrawable(applicationInfo.icon, null));
                } catch (Throwable th2) {
                    return null;
                }
            }
            return null;
        } catch (Throwable th3) {
        }
        return null;
    }

    private Bitmap launcherIconBitmap(String str) {
        List<LauncherActivityInfo> activityList;
        try {
            LauncherApps launcherApps = (LauncherApps) getSystemService("launcherapps");
            if (launcherApps != null && (activityList = launcherApps.getActivityList(str, Process.myUserHandle())) != null && !activityList.isEmpty()) {
                return drawableToBitmap(activityList.get(0).getIcon(0));
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static boolean isBadIconBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return true;
        }
        int iMax = Math.max(1, width / 8);
        int iMax2 = Math.max(1, height / 8);
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        for (int i4 = 0; i4 < height; i4 += iMax2) {
            for (int i5 = 0; i5 < width; i5 += iMax) {
                int pixel = bitmap.getPixel(i5, i4);
                i++;
                if (((pixel >>> 24) & 255) >= 128) {
                    i2++;
                    int i6 = (pixel >> 16) & 255;
                    int i7 = (pixel >> 8) & 255;
                    int i8 = pixel & 255;
                    if (i6 > 240 && i7 > 240 && i8 > 240) {
                        i3++;
                    }
                }
            }
        }
        if (i == 0 || i2 * 10 < i || i3 * 10 > i2 * 9) {
            return true;
        }
        return false;
    }

    private static boolean isBlankBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return true;
        }
        int iMax = Math.max(1, width / 8);
        int iMax2 = Math.max(1, height / 8);
        int i = 0;
        int i2 = 0;
        for (int i3 = 0; i3 < height; i3 += iMax2) {
            for (int i4 = 0; i4 < width; i4 += iMax) {
                i++;
                if (((bitmap.getPixel(i4, i3) >>> 24) & 255) >= 128) {
                    i2++;
                }
            }
        }
        if (i == 0 || i2 * 10 < i) {
            return true;
        }
        return false;
    }

    private static boolean isSolidColorBitmap(Bitmap bitmap) {
        Bitmap bitmap2 = bitmap;
        int i = 0;
        if (bitmap2 == null) {
            return false;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > 0 && height > 0) {
            int iMax = Math.max(1, width / 8);
            int iMax2 = Math.max(1, height / 8);
            HashSet hashSet = new HashSet();
            long j = 0;
            int i2 = 0;
            boolean z = false;
            int i3 = 0;
            int i4 = 0;
            int i5 = 0;
            int iAbs = 0;
            long j2 = 0;
            long j3 = 0;
            while (i2 < height) {
                int i6 = 0;
                while (i6 < width) {
                    int i7 = width;
                    int pixel = bitmap2.getPixel(i6, i2);
                    int i8 = height;
                    if (((pixel >>> 24) & 255) >= 128) {
                        if (!z) {
                            i3 = (pixel >> 16) & 255;
                            i4 = (pixel >> 8) & 255;
                            i5 = pixel & 255;
                            z = true;
                        }
                        int i9 = (pixel >> 16) & 255;
                        int i10 = (pixel >> 8) & 255;
                        j2 += (long) i10;
                        int i11 = pixel & 255;
                        j3 += (long) i11;
                        iAbs += Math.abs(i9 - i3) + Math.abs(i10 - i4) + Math.abs(i11 - i5);
                        hashSet.add(Integer.valueOf(((i9 / 16) << 8) | ((i10 / 16) << 4) | (i11 / 16)));
                        i++;
                        z = z;
                        j += (long) i9;
                    }
                    i6 += iMax;
                    bitmap2 = bitmap;
                    i2 = i2;
                    width = i7;
                    height = i8;
                }
                i2 += iMax2;
                bitmap2 = bitmap;
                height = height;
            }
            if (!z || i == 0 || hashSet.size() <= 5) {
                return true;
            }
            long j4 = i;
            int i12 = (int) (j / j4);
            int i13 = (int) (j2 / j4);
            int i14 = (int) (j3 / j4);
            if (i12 <= 235 || i13 <= 235 || i14 <= 235) {
                return (i12 < 20 && i13 < 20 && i14 < 20) || iAbs / i < 24;
            }
            return true;
        }
        return true;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            try {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    return bitmap;
                }
            } catch (Throwable th) {
            }
        }
        if (drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) drawable;
            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap);
            try {
                Drawable background = adaptiveIconDrawable.getBackground();
                if (background != null) {
                    background.setBounds(0, 0, 192, 192);
                    background.draw(canvas);
                }
            } catch (Throwable th2) {
            }
            try {
                Drawable foreground = adaptiveIconDrawable.getForeground();
                if (foreground != null) {
                    foreground.setBounds(0, 0, 192, 192);
                    foreground.draw(canvas);
                }
            } catch (Throwable th3) {
            }
            if (!isBadIconBitmap(bitmapCreateBitmap)) {
                return bitmapCreateBitmap;
            }
            Bitmap bitmapCreateBitmap2 = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Canvas canvas2 = new Canvas(bitmapCreateBitmap2);
            try {
                adaptiveIconDrawable.setBounds(0, 0, 192, 192);
                adaptiveIconDrawable.draw(canvas2);
            } catch (Throwable th4) {
            }
            return bitmapCreateBitmap2;
        }
        int iMin = Math.min(Math.max(drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96, drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96), 192);
        Bitmap bitmapCreateBitmap3 = Bitmap.createBitmap(iMin, iMin, Bitmap.Config.ARGB_8888);
        Canvas canvas3 = new Canvas(bitmapCreateBitmap3);
        drawable.setBounds(0, 0, iMin, iMin);
        drawable.draw(canvas3);
        return bitmapCreateBitmap3;
    }

    private static Bitmap scaleBitmap(Bitmap bitmap, int i) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int iMax = Math.max(width, height);
        if (iMax <= i || iMax <= 0) {
            return bitmap;
        }
        float f = i / iMax;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * f)), Math.max(1, Math.round(height * f)), true);
    }

    // ---- fixed206: independent download window ----
    // fixed199: 2.0 start/stopDownloadTick (idempotent per
    // key — re-posts of the same download re-arm the same
    // runnable instead of stacking ticks).



    // fixed193: does the live title/text say the download is
    // done? (2.0 v242). Apps rewrite the notification as "下载完成"
    // (and drop the progress extras) instead of posting 100%.

    // fixed199: download tick rewritten the 2.0 way (2.0

    // fixed209: is this a download-related notification?
    // Title/text mentions download, install or update.

    // fixed208: strip a trailing " 18%" / "18%" so "正在安装"
    // and "正在安装 18%" compare equal (same download).

    // fixed206: send one download state to SystemUI.













    // fixed190: download capsule uses 2.0's dual condition
    // (v110/v241). A download needs BOTH real progress AND a
    // download signal. Pure-progress checks mis-detected chat
    // messages with stale progress extras as downloads.




    // fixed212: download detection (ported from 2.0 dual condition).
    private static int parseProgressPercent(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        try {
            Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%").matcher(str);
            if (matcher.find()) {
                float f = Float.parseFloat(matcher.group(1));
                if (f >= 0.0f && f <= 100.0f) {
                    return Math.round(f);
                }
            }
            Matcher matcher2 = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:MB|KB|GB|B)?\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s*(?:MB|KB|GB|B)?").matcher(str);
            if (matcher2.find()) {
                float f2 = Float.parseFloat(matcher2.group(1));
                float f3 = Float.parseFloat(matcher2.group(2));
                if (f3 > 0.0f && f2 >= 0.0f && f2 <= f3) {
                    return Math.round((f2 * 100.0f) / f3);
                }
            }
        } catch (Throwable th) {
        }
        return -1;
    }

    private static int readDownloadPct(Notification n, String title, String text, String infoText) {
        if (n == null) {
            return -1;
        }
        long best = -1;
        Bundle b = n.extras;
        if (b != null) {
            long prog = b.getInt(Notification.EXTRA_PROGRESS, 0);
            long progMax = b.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            if (progMax > 0) {
                best = (prog * 100L) / progMax;
            }
        }
        int[] rv = readProgressFromRemoteViews(n);
        if (rv != null && rv[1] > 0) {
            long rvPct = ((long) rv[0] * 100L) / rv[1];
            if (rvPct > best) {
                best = rvPct;
            }
        }
        int textPct = parseProgressPercent(text);
        if (textPct < 0) {
            textPct = parseProgressPercent(title);
        }
        if (textPct > best) {
            best = textPct;
        }
        long[] dl = parseDownloadedTotal(text, infoText);
        if (dl != null && dl[1] > 0) {
            long bytePct = (dl[0] * 100L) / dl[1];
            if (bytePct > best) {
                best = bytePct;
            }
        }
        long[] bytes = readDownloadBytes(n);
        if (bytes != null && bytes[1] > 0) {
            long bytePct = (bytes[0] * 100L) / bytes[1];
            if (bytePct > best) {
                best = bytePct;
            }
        }
        if (best < 0) {
            return -1;
        }
        if (best > 100) {
            best = 100;
        }
        return (int) best;
    }

    private static int[] readProgressFromRemoteViews(Notification notification) {
        try {
            RemoteViews remoteViews = notification.contentView;
            if (remoteViews == null) {
                remoteViews = notification.bigContentView;
            }
            if (remoteViews == null) {
                return null;
            }
            Field declaredField = RemoteViews.class.getDeclaredField("mActions");
            declaredField.setAccessible(true);
            ArrayList arrayList = (ArrayList) declaredField.get(remoteViews);
            if (arrayList == null) {
                return null;
            }
            int iIntValue = -1;
            int iIntValue2 = -1;
            for (Object obj : arrayList) {
                if (obj != null) {
                    Class<?> cls = obj.getClass();
                    if (cls.getSimpleName().equals("ReflectionAction")) {
                        Field declaredField2 = cls.getDeclaredField("methodName");
                        declaredField2.setAccessible(true);
                        String str = (String) declaredField2.get(obj);
                        Field declaredField3 = cls.getDeclaredField("value");
                        declaredField3.setAccessible(true);
                        Object obj2 = declaredField3.get(obj);
                        if ("setMax".equals(str) && (obj2 instanceof Number)) {
                            iIntValue = ((Number) obj2).intValue();
                        } else if ("setProgress".equals(str) && (obj2 instanceof Number)) {
                            iIntValue2 = ((Number) obj2).intValue();
                        }
                    }
                }
            }
            if (iIntValue > 0 && iIntValue2 >= 0) {
                return new int[]{iIntValue2, iIntValue};
            }
        } catch (Throwable th) {
        }
        return null;
    }

    private static boolean hasDownloadSignal(String pkg, String channelId,
            String title, String text) {
        // v241: 安装 no longer excluded — app-store install
        // progress is a download capsule too. The signal gate
        // below still blocks a stray 安装 from a non-store app.
        if (containsAny(title, "上传", "备份", "同步")
                || containsAny(text, "上传", "备份", "同步")) {
            return false;
        }
        if (containsAny(title, "下载") || containsAny(text, "下载")) {
            return true;
        }
        if (channelId != null
                && channelId.toLowerCase(java.util.Locale.US).contains("download")) {
            return true;
        }
        return pkg != null && isDownloaderPkg(pkg);
    }

    private static boolean isDownloaderPkg(String pkg) {
        if (pkg.equals("com.meizu.mstore")
                || pkg.equals("com.xiaomi.market")
                || pkg.equals("com.huawei.appmarket")
                || pkg.equals("com.oppo.market")
                || pkg.equals("com.vivo.appstore")
                || pkg.equals("com.bbk.appstore")
                || pkg.equals("com.tencent.android.qqdownloader")
                || pkg.equals("com.android.vending")) {
            return true;
        }
        if (pkg.equals("com.android.browser")
                || pkg.equals("com.UCMobile")
                || pkg.equals("com.tencent.mtt")
                || pkg.equals("com.baidu.searchbox")
                || pkg.equals("org.mozilla.firefox")
                || pkg.equals("com.microsoft.emmx")
                || pkg.equals("com.quark.browser")) {
            return true;
        }
        String lower = pkg.toLowerCase();
        return lower.contains("download") || lower.contains("downloader");
    }

    private static boolean containsAny(String s, String... words) {
        if (s == null) {
            return false;
        }
        for (String w : words) {
            if (w != null && s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static long[] parseDownloadedTotal(String text, String infoText) {
        if (infoText != null) {
            String str = infoText.trim();
            int slash = str.indexOf('/');
            if (slash > 0 && slash < str.length() - 1) {
                long dl = parseSizeToBytes(str.substring(0, slash).trim());
                long total = parseSizeToBytes(str.substring(slash + 1).trim());
                if (dl >= 0 && total > 0) {
                    return new long[]{dl, total};
                }
            }
        }
        if (text != null) {
            String str = text.trim();
            int a = str.indexOf("已下载");
            int b = str.indexOf("，共", a >= 0 ? a : 0);
            if (b < 0) {
                b = str.indexOf(",共", a >= 0 ? a : 0);
            }
            if (a >= 0 && b > a) {
                long dl = parseSizeToBytes(str.substring(a + 3, b).trim());
                long total = parseSizeToBytes(str.substring(b + 2).trim());
                if (dl >= 0 && total > 0) {
                    return new long[]{dl, total};
                }
            }
        }
        return null;
    }

    private static long[] readDownloadBytes(Notification n) {
        if (n == null) {
            return null;
        }
        Bundle b = n.extras;
        if (b != null) {
            long prog = b.getInt(Notification.EXTRA_PROGRESS, 0);
            long progMax = b.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
            if (progMax > 100 && prog >= 0) {
                return new long[]{prog, progMax};
            }
        }
        int[] rv = readProgressFromRemoteViews(n);
        if (rv != null && rv[1] > 100 && rv[0] >= 0) {
            return new long[]{rv[0], rv[1]};
        }
        return null;
    }

    private static long parseSizeToBytes(String str) {
        if (str == null) {
            return -1;
        }
        String t = str.trim().toUpperCase(java.util.Locale.US);
        if (t.isEmpty()) {
            return -1;
        }
        long mult = 1;
        if (t.endsWith("GB") || t.endsWith("G")) {
            mult = 1024L * 1024L * 1024L;
            t = t.replaceAll("(GB|G)$", "").trim();
        } else if (t.endsWith("MB") || t.endsWith("M")) {
            mult = 1024L * 1024L;
            t = t.replaceAll("(MB|M)$", "").trim();
        } else if (t.endsWith("KB") || t.endsWith("K")) {
            mult = 1024L;
            t = t.replaceAll("(KB|K)$", "").trim();
        } else if (t.endsWith("B")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            double v = Double.parseDouble(t);
            if (v < 0) {
                return -1;
            }
            return (long) (v * mult);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // fixed215: listener log forwarding (fixed200/fixed204).
    // Explicit package — an implicit broadcast is dropped.
    private void sendLogLine(String line) {
        try {
            android.content.Intent it = new android.content.Intent(Settings.ACTION_LOG);
            it.putExtra("line", line);
            it.setPackage(Settings.SYSTEMUI_PACKAGE);
            sendBroadcast(it);
        } catch (Throwable ignored) {
        }
    }

    // fixed219: download completion (2.0 v242, fixed193). Apps
    // rewrite the notification as "下载完成" (and drop the
    // progress extras) instead of posting 100%.
    private static boolean isDownloadComplete(String title, String text) {
        return (title != null
                && (title.contains("下载完成")
                        || title.contains("安装完成")
                        || title.contains("已完成")
                        || title.contains("下载完毕")
                        || title.contains("已下载")
                        || title.contains("下载成功")
                        || title.toLowerCase(java.util.Locale.ROOT)
                                .contains("download complete")))
                || (text != null
                        && (text.contains("下载完成")
                                || text.contains("安装完成")
                                || text.contains("已完成")
                                || text.contains("下载完毕")
                                || text.contains("已下载")
                                || text.contains("下载成功")
                                || text.toLowerCase(java.util.Locale.ROOT)
                                        .contains("download complete")));
    }
}
