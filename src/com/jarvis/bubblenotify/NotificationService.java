package com.jarvis.bubblenotify;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public class NotificationService extends NotificationListenerService {
    private static final Pattern DIST_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(公里|千米|米|km|m)");
    private static final Pattern REMAIN_DIST_PATTERN = Pattern.compile("(?:剩余|还剩)\\s*(\\d+(?:\\.\\d+)?\\s*(?:公里|千米|米|km|m))");
    private static final Pattern REMAIN_TIME_PATTERN = Pattern.compile("(?:约|预计)\\s*(\\d+\\s*分钟)");

    // fixed36: vendor push services post notifications on behalf of apps
    // (e.g. Meituan's "商品已送达" arrives as "推送服务" instead of
    // "美团"). The user wants only the app's own notifications — drop
    // anything posted by a push service. Belt and suspenders: match the
    // package name against the known vendor push services, and also match
    // the app label "推送服务" (the exact case on this phone) so a
    // package we did not list is still caught.
    private static final java.util.Set<String> PUSH_SERVICE_PACKAGES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "com.meizu.cloud.pushservice", "com.meizu.push", "com.meizu.flyme.push", "com.meizu.cloud.pushsdk",
            "com.xiaomi.xmsf",
            "com.huawei.android.pushagent", "com.huawei.hms",
            "com.coloros.mcs", "com.heytap.mcs", "com.heytap.msp",
            "com.vivo.pushservice", "com.vivo.push",
            "com.samsung.android.push"));

    private boolean isPushService(String pkg) {
        if (pkg == null) {
            return false;
        }
        if (PUSH_SERVICE_PACKAGES.contains(pkg)) {
            return true;
        }
        try {
            CharSequence label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
            if (label != null && "推送服务".equals(label.toString())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
    private static final long NOTIF_DEDUP_WINDOW_MS = 15000;
    private static final long NOTIF_GROUP_WINDOW_MS = 10000;
    private static final long NOTIF_SUMMARY_WINDOW_MS = 60000;
    private String callKey;
    private String currentLyricBigText;
    private String currentLyricText;
    private String currentLyricTicker;
    private String currentMediaKey;
    private String currentMediaTitle;
    // fixed19: duration of the current song, for the sticky-title check.
    private long currentMediaDuration = -1;
    // fixed18: the notification's android.title is the stable song title.
    // NetEase writes the current lyric line into the MediaSession TITLE,
    // so the metadata title cannot be trusted as the song title.
    private String currentNotifTitle;
    // fixed31: network lyrics (fixed18 LRCLIB) are OFF — lyrics are back
    // to the original notification source (updateLyricCandidates, see
    // sendMusicStateFromController). The LRCLIB lookup returned nothing
    // for songs the library does not carry, so the card showed no lyric
    // at all. NetworkLyrics.java stays in the tree, unused, in case the
    // network source is wanted again later.
    // fixed31: delayed "music inactive". When a music app cancels and
    // re-posts its notification (every lyric line for some apps), the
    // removal used to broadcast inactive immediately, the bubble hid
    // 2.5s later, and the re-post popped it again — the player popping
    // twice. Now the inactive broadcast waits 2s and is cancelled if a
    // replacement media notification attaches first.
    private static final long MUSIC_INACTIVE_DELAY_MS = 2000;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingMusicInactiveRunnable;
    private MediaController.Callback mediaCallback;
    private MediaController mediaController;
    private final List<RecentNotif> recentNotifs = new ArrayList();
    private final Set<String> selfCancelledKeys = new HashSet();
    /** pkg + title + text fingerprints seen recently, for duplicate suppression. */
    private final Map<String, Long> recentNotifFingerprints = new HashMap();
    /** title + text fingerprints seen recently, across packages. The same message
     *  can arrive twice via different senders (e.g. a push service and the app
     *  itself); the per-package fingerprint above cannot catch that. */
    private final Map<String, Long> recentNotifContentFingerprints = new HashMap();
    /** Notification keys seen recently (sbn.getKey(), Android's native stable
     *  key). Same key = same notification re-posted/updated, not a new one. */
    private final Map<String, Long> seenNotifKeys = new HashMap();
    /** fixed97: content last seen under each key ("title\u0001text").
     *  WeChat reuses one key per conversation and updates the content,
     *  so key-alone drops every later message from the same person.
     *  Only same key AND same content is a repeat. */
    private final Map<String, String> seenNotifKeyContent = new HashMap();
    private static final long NOTIF_KEY_TTL_MS = 3600000;
    /** fixed28 (ported from Flyme Island 1.0.38-r3 W()): per-package last
     *  notification fingerprint, "id:postTime:title/text". One entry per
     *  package, max 32 packages. A repeat fingerprint means the system
     *  re-delivered the exact same notification — drop it with ZERO action
     *  (no broadcast, no refresh), instead of letting it reach the bubble
     *  and re-pop. The key cache above catches same-key re-posts; this
     *  catches re-deliveries whose key differs but whose content is
     *  identical, which the old dedup chain let through. */
    private final java.util.LinkedHashMap<String, String> lastNotifFingerprintByPkg = new java.util.LinkedHashMap<String, String>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
            return size() > 32;
        }
    };
    // fixed11: Flyme Island-style lifecycle dedup (see DIAGNOSIS.md #36).
    // Keyed by notification key; each state remembers the postTime and the
    // set of individual message digests already seen for that key, so a
    // summary that only repeats already-seen messages is not fresh. A second
    // map tracks the latest message per conversation, so a summary of a
    // conversation already shown is not fresh either. No time window — an
    // old re-delivery is stale by postTime alone.
    private final Map<String, MsgState> msgStates = new HashMap();
    private final Map<String, String> lastMsgByConversation = new HashMap();
    /** pkg + group keys of child notifications seen recently, for summary filtering. */
    private final Map<String, Long> recentNotifGroups = new HashMap();
    /** Last notification per package, for catching unflagged group summaries. */
    private final Map<String, LastNotif> lastNotifByPkg = new HashMap();

    /** Video apps whose media sessions must not show the player card/capsule.
     *  Video is notification-only ("只播放通知就行了"). The app-category check
     *  below catches the rest, including apps not listed here. */
    private static final java.util.Set<String> VIDEO_PACKAGES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "tv.danmaku.bili",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.ss.android.ugc.live",
            "com.kuaishou.nebula",
            "com.kuaishou.nebula.lite",
            "com.tencent.qqlive",
            "com.iqiyi.qyplayer",
            "com.youku.phone",
            "com.hunantv.imgo.activity",
            "com.ss.android.article.video",
            "com.baidu.video",
            "com.sohu.sohuvideo",
            "com.tencent.weishi",
            "com.miui.video",
            "com.meizu.media.video"
    ));

    /** True if this package is a video app: listed above, or its manifest
     *  declares appCategory="video". Music apps declare "audio" instead. */
    private boolean isVideoApp(String pkg) {
        try {
            if (pkg != null && VIDEO_PACKAGES.contains(pkg)) {
                return true;
            }
            if (pkg != null) {
                android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
                if (info != null && info.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO) {
                    return true;
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private static class LastNotif {
        String text;
        long time;
        String title;

        LastNotif(String title, String text, long time) {
            this.title = title;
            this.text = text;
            this.time = time;
        }
    }

    private static class MsgState {
        long postTime;
        final java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

        MsgState(long postTime) {
            this.postTime = postTime;
        }
    }

    /** fixed28 (ported from Flyme Island 1.0.38-r3 W()): true if this
     *  package's last notification had the identical fingerprint
     *  (id:postTime:title/text). A repeat fingerprint is a re-delivery of
     *  the exact same notification — drop it with zero action. */
    private boolean isRepeatNotifFingerprint(String pkg, android.service.notification.StatusBarNotification sbn, String title, String text) {
        if (pkg == null || sbn == null) {
            return false;
        }
        try {
            String fp = sbn.getId() + ":" + sbn.getPostTime() + ":" + (title == null ? "" : title) + "/" + (text == null ? "" : text);
            synchronized (this.lastNotifFingerprintByPkg) {
                String last = this.lastNotifFingerprintByPkg.get(pkg);
                if (fp.equals(last)) {
                    return true;
                }
                this.lastNotifFingerprintByPkg.put(pkg, fp);
            }
        } catch (Throwable th) {
        }
        return false;
    }

    /** True if this notification key was already shown with the SAME
     *  content within the TTL. sbn.getKey() stays the same when an app
     *  updates a notification's content (WeChat reuses one key per
     *  conversation), so key-alone would drop every later message from
     *  the same person. Only same key AND same title+text is a repeat
     *  (identical re-delivery); new content under the same key is a new
     *  notification and pops once. */
    private boolean isRepeatNotifKey(String key, String title, String text) {
        if (key == null) {
            return false;
        }
        try {
            String content = (title == null ? "" : title) + "\u0001" + (text == null ? "" : text);
            long now = SystemClock.uptimeMillis();
            synchronized (this.seenNotifKeys) {
                Long last = this.seenNotifKeys.get(key);
                String lastContent = this.seenNotifKeyContent.get(key);
                if (last != null && now - last.longValue() < NOTIF_KEY_TTL_MS && content.equals(lastContent)) {
                    return true;
                }
                this.seenNotifKeys.put(key, Long.valueOf(now));
                this.seenNotifKeyContent.put(key, content);
                if (this.seenNotifKeys.size() > 128) {
                    java.util.Iterator<Map.Entry<String, Long>> it = this.seenNotifKeys.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, Long> e = it.next();
                        if (now - e.getValue().longValue() > NOTIF_KEY_TTL_MS) {
                            it.remove();
                            this.seenNotifKeyContent.remove(e.getKey());
                        }
                    }
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    /** True if the same pkg + title + text was already posted within the dedup window. */
    private boolean isDuplicateNotif(String pkg, String title, String text) {
        try {
            String fp = pkg + "\u0001" + (title == null ? "" : title) + "\u0001" + (text == null ? "" : text);
            long now = SystemClock.uptimeMillis();
            synchronized (this.recentNotifFingerprints) {
                Long last = this.recentNotifFingerprints.get(fp);
                if (last != null && now - last.longValue() < NOTIF_DEDUP_WINDOW_MS) {
                    return true;
                }
                this.recentNotifFingerprints.put(fp, Long.valueOf(now));
                if (this.recentNotifFingerprints.size() > 64) {
                    java.util.Iterator<Map.Entry<String, Long>> it = this.recentNotifFingerprints.entrySet().iterator();
                    while (it.hasNext()) {
                        if (now - it.next().getValue().longValue() > NOTIF_DEDUP_WINDOW_MS) {
                            it.remove();
                        }
                    }
                }
            }
            synchronized (this.recentNotifContentFingerprints) {
                String contentFp = (title == null ? "" : title) + "\u0001" + (text == null ? "" : text);
                Long lastContent = this.recentNotifContentFingerprints.get(contentFp);
                if (lastContent != null && now - lastContent.longValue() < NOTIF_DEDUP_WINDOW_MS) {
                    return true;
                }
                this.recentNotifContentFingerprints.put(contentFp, Long.valueOf(now));
                if (this.recentNotifContentFingerprints.size() > 64) {
                    java.util.Iterator<Map.Entry<String, Long>> it2 = this.recentNotifContentFingerprints.entrySet().iterator();
                    while (it2.hasNext()) {
                        if (now - it2.next().getValue().longValue() > NOTIF_DEDUP_WINDOW_MS) {
                            it2.remove();
                        }
                    }
                }
            }
            synchronized (this.lastNotifByPkg) {
                LastNotif prev = this.lastNotifByPkg.get(pkg);
                if (prev != null && now - prev.time < NOTIF_DEDUP_WINDOW_MS
                        && isSummaryOf(prev, title, text)) {
                    return true;
                }
                if (prev != null && now - prev.time < NOTIF_SUMMARY_WINDOW_MS
                        && isAggregatedSummaryOf(prev, title, text)) {
                    return true;
                }
                this.lastNotifByPkg.put(pkg, new LastNotif(title, text, now));
                if (this.lastNotifByPkg.size() > 32) {
                    this.lastNotifByPkg.clear();
                }
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    /**
     * True if the new notification looks like an aggregation of the previous one
     * from the same package: same title (sender), and one text contains the other
     * plus the title. This catches group summaries that are not flagged with
     * FLAG_GROUP_SUMMARY, e.g. WeChat's "[5条]test: 111" after "test: 111".
     */
    private static boolean isSummaryOf(LastNotif prev, String title, String text) {
        try {
            if (prev.title == null || title == null || !prev.title.equals(title)) {
                return false;
            }
            if (prev.text == null || text == null || prev.text.isEmpty() || text.isEmpty()) {
                return false;
            }
            if (text.contains(prev.text) && text.contains(title)) {
                return true;
            }
            return prev.text.contains(text) && prev.text.contains(title);
        } catch (Throwable th) {
            return false;
        }
    }

    /**
     * True if the new notification is a WeChat-style aggregated summary
     * ("[5条]test: 111") of a message we already showed. The plain contains-check
     * in isSummaryOf misses it when both the count and the message changed
     * ("[5条]test: 111" vs "[6条]test: 222" contain neither each other), which is
     * why the bubble popped twice once unread messages piled up.
     */
    private static boolean isAggregatedSummaryOf(LastNotif prev, String title, String text) {
        try {
            String sender = Settings.summarySender(text);
            if (sender == null) {
                return false;
            }
            if (prev.title != null && prev.title.equals(sender)) {
                return true;
            }
            return prev.text != null && !prev.text.isEmpty() && text.contains(prev.text);
        } catch (Throwable th) {
            return false;
        }
    }

    /** True if this is a group summary whose child notification was already shown. */
    private boolean isRedundantGroupSummary(Notification notification, String pkg) {
        try {
            if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                return false;
            }
            String group = notification.getGroup();
            if (group == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            synchronized (this.recentNotifGroups) {
                Long last = this.recentNotifGroups.get(pkg + "\u0001" + group);
                return last != null && now - last.longValue() < NOTIF_GROUP_WINDOW_MS;
            }
        } catch (Throwable th) {
            return false;
        }
    }

    private void noteNotifGroup(Notification notification, String pkg) {
        try {
            if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                return;
            }
            String group = notification.getGroup();
            if (group == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            synchronized (this.recentNotifGroups) {
                this.recentNotifGroups.put(pkg + "\u0001" + group, Long.valueOf(now));
                if (this.recentNotifGroups.size() > 64) {
                    this.recentNotifGroups.clear();
                }
            }
        } catch (Throwable th) {
        }
    }

    /**
     * fixed11: Flyme Island-style lifecycle dedup. Instead of matching
     * the summary's text format ("[N条]sender: msg") inside a time window,
     * track each notification by its KEY and remember which individual
     * messages were already seen for that key and for that conversation:
     *  - STALE: this postTime is older than the stored one for the key
     *    (an old re-delivery) -> redundant.
     *  - every message in this snapshot was already seen for this key, or
     *    its latest message equals the conversation's latest -> redundant.
     * Otherwise record the messages and return false (fresh, show it).
     * Message digests come from android.messages (MessagingStyle), falling
     * back to android.textLines, then to title+text. No time window is
     * involved, so a late summary is still caught.
     */
    private boolean isRedundantMessage(android.service.notification.StatusBarNotification sbn, Notification notification, Bundle bundle, String pkg, String title, String text) {
        if (sbn == null || sbn.getKey() == null) {
            return false;
        }
        // fixed12: applies to ALL apps, not just WeChat/QQ. The lifecycle
        // mechanism (per-key seen-set + postTime STALE + per-conversation
        // latest) is generic: a re-post of the same notification, or a
        // summary that only repeats already-seen messages, is redundant no
        // matter which app posted it.
        try {
            java.util.ArrayList<String> messages = new java.util.ArrayList<>();
            if (bundle != null) {
                android.os.Parcelable[] parcelableArray = bundle.getParcelableArray("android.messages");
                if (parcelableArray != null) {
                    for (int i = Math.max(0, parcelableArray.length - 25); i < parcelableArray.length; i++) {
                        if (parcelableArray[i] instanceof Bundle) {
                            Bundle m = (Bundle) parcelableArray[i];
                            CharSequence t = m.getCharSequence("text");
                            if (t != null && t.length() > 0) {
                                CharSequence sender = m.getCharSequence("sender");
                                String s = sender == null ? "" : sender.toString();
                                messages.add(s + "" + t.toString());
                            }
                        }
                    }
                }
                if (messages.isEmpty()) {
                    CharSequence[] lines = bundle.getCharSequenceArray("android.textLines");
                    if (lines != null) {
                        for (int i = Math.max(0, lines.length - 25); i < lines.length; i++) {
                            if (lines[i] != null && lines[i].length() > 0) {
                                messages.add(lines[i].toString());
                            }
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                String content = (title == null ? "" : title) + "" + (text == null ? "" : text);
                if (!content.trim().isEmpty()) {
                    messages.add(content);
                }
            }
            if (messages.isEmpty()) {
                return false;
            }
            String conversation = null;
            if (bundle != null) {
                CharSequence convTitle = bundle.getCharSequence("android.conversationTitle");
                String conv = convTitle == null ? "" : convTitle.toString();
                if (conv.isEmpty() && notification != null && notification.getShortcutId() != null) {
                    conv = notification.getShortcutId();
                }
                if (conv.isEmpty() && title != null && !title.isEmpty() && !"微信".equals(title) && !"QQ".equals(title) && !title.contains("条新消息") && !title.contains("新消息")) {
                    conv = title;
                }
                if (!conv.isEmpty()) {
                    conversation = pkg + "" + conv;
                }
            }
            String latest = messages.get(messages.size() - 1);
            String key = sbn.getKey();
            long postTime = sbn.getPostTime();
            synchronized (this.msgStates) {
                MsgState state = this.msgStates.get(key);
                if (state != null && postTime < state.postTime) {
                    return true;
                }
                if (state == null) {
                    state = new MsgState(postTime);
                    this.msgStates.put(key, state);
                    if (this.msgStates.size() > 256) {
                        java.util.Iterator<String> it = this.msgStates.keySet().iterator();
                        if (it.hasNext()) {
                            it.next();
                            it.remove();
                        }
                    }
                } else {
                    state.postTime = postTime;
                }
                boolean allSeen = true;
                for (String m : messages) {
                    if (!state.seen.contains(m)) {
                        allSeen = false;
                        break;
                    }
                }
                if (allSeen) {
                    return true;
                }
                if (conversation != null) {
                    synchronized (this.lastMsgByConversation) {
                        String last = this.lastMsgByConversation.get(conversation);
                        if (last != null && last.equals(latest)) {
                            for (String m : messages) {
                                state.seen.add(m);
                                if (state.seen.size() > 64) {
                                    state.seen.remove(state.seen.iterator().next());
                                }
                            }
                            return true;
                        }
                    }
                }
                for (String m : messages) {
                    state.seen.add(m);
                    if (state.seen.size() > 64) {
                        state.seen.remove(state.seen.iterator().next());
                    }
                }
                if (conversation != null) {
                    synchronized (this.lastMsgByConversation) {
                        this.lastMsgByConversation.put(conversation, latest);
                        if (this.lastMsgByConversation.size() > 256) {
                            java.util.Iterator<String> it = this.lastMsgByConversation.keySet().iterator();
                            if (it.hasNext()) {
                                it.next();
                                it.remove();
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() { // from class: com.jarvis.bubblenotify.NotificationService.1
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

    private static class RecentNotif {
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

    @Override // android.service.notification.NotificationListenerService
    public void onListenerConnected() {
        Log.i(Settings.TAG, "Notification listener connected.");
        // fixed9: when the listener (re)connects, the system re-delivers every
        // currently-active notification through onNotificationPosted. Seed the
        // key cache with those keys first, so the re-delivery does not pop
        // them all again — this is why duplicates came back "after a while"
        // (the service gets killed and restarted, the cache was empty).
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                long now = SystemClock.uptimeMillis();
                synchronized (this.seenNotifKeys) {
                    for (StatusBarNotification sbn : active) {
                        if (sbn != null && sbn.getKey() != null) {
                            this.seenNotifKeys.put(sbn.getKey(), Long.valueOf(now));
                            try {
                                Bundle b = sbn.getNotification() == null ? null : sbn.getNotification().extras;
                                String t = getText(b, "android.title");
                                String tx = getText(b, "android.text");
                                if (tx == null || tx.isEmpty()) {
                                    tx = getText(b, "android.bigText");
                                }
                                this.seenNotifKeyContent.put(sbn.getKey(), (t == null ? "" : t) + "\u0001" + (tx == null ? "" : tx));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
                Log.i(Settings.TAG, "Seeded " + active.length + " active notification keys.");
            }
        } catch (Throwable th) {
            Log.i(Settings.TAG, "Seed active keys failed", th);
        }
    }

    @Override // android.service.notification.NotificationListenerService
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        MediaSession.Token token;
        if (statusBarNotification == null) {
            return;
        }
        try {
            if (statusBarNotification.getNotification() == null) {
                return;
            }
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
            // fixed33: diagnostic logging (user will export the log for
            // analysis). One line per posted notification so a double
            // delivery shows up as two Posted lines for the same key.
            FileLog.i("Posted pkg=" + packageName + " key=" + statusBarNotification.getKey() + " ongoing=" + isOngoing(notification) + " flags=" + notification.flags);
            // fixed36: drop notifications posted by a vendor push service —
            // the user wants only the app's own notifications (e.g. Meituan's
            // delivery notice arrives as "推送服务" instead of "美团"; the app
            // posts its own copy too, so dropping the push-service copy leaves
            // one correct "美团" bubble and kills a double-pop source).
            if (isPushService(packageName)) {
                // fixed54: log the content too — BOSS直聘/鱼泡直聘的通知
                // 疑似只剩推送服务这一份(应用本身在后台不发自己的
                // 一份), 丢了就一条都收不到; 日志要能分清是哪种情况。
                FileLog.i("Push service notification dropped: pkg=" + packageName + " title=" + getText(bundle, "android.title") + " text=" + getText(bundle, "android.text"));
                return;
            }
            if (sharedPreferences.getBoolean(Settings.KEY_MUSIC_ENABLED, true)) {
                if (bundle != null) {
                    token = (MediaSession.Token) bundle.getParcelable("android.mediaSession");
                } else {
                    token = null;
                }
                if (token != null) {
                    if (isVideoApp(packageName)) {
                        // fixed6: video apps get no player card/capsule — the
                        // notification itself is enough. Drop the media session
                        // (do not attach, do not show the bubble); the app's own
                        // system notification stays visible, it is not cancelled.
                        Log.i(Settings.TAG, "Video app media session ignored, no player: " + packageName);
                        return;
                    }
                    handleMediaNotification(statusBarNotification, notification, token);
                    return;
                }
            }
            // fixed31: delivery live bubble removed — Meituan/Eleme
            // delivery notifications are ordinary notifications now.
            // They fall through to the normal path below (see
            // isDeliveryOrder): the first post shows as a normal capsule,
            // later progress updates are dropped by the key cache like any
            // other app's re-post. No more live-order step/progress bubble.
            // (fixed32: the nav branch moved above the media branch.)
            if (isCallNotification(statusBarNotification, notification)) {
                if (sharedPreferences.getBoolean(Settings.KEY_CALL_ENABLED, true)) {
                    handleCallNotification(statusBarNotification, notification, bundle);
                    return;
                }
                return;
            }
            // fixed31: delivery notifications are ordinary notifications.
            // They are ongoing (FLAG_ONGOING_EVENT), which the normal path
            // used to skip — exempt them so they show as a normal capsule
            // like any other app's notification.
            // fixed99: no live nav — Amap is an ordinary notification now.
            // Its ongoing nav post is exempt from the ongoing skip, like
            // Meituan/Eleme, so it still shows as a capsule.
            boolean isDeliveryOrder = Settings.PKG_MEITUAN.equals(packageName) || Settings.PKG_ELEME.equals(packageName) || Settings.PKG_AMAP.equals(packageName);
            // fixed55: this skip used to be silent — a BOSS直聘/鱼泡直聘
            // notification sits in the shade but never bubbles, and the
            // log showed nothing. Log it so the drop reason is visible.
            if (isOngoing(notification) && !isDeliveryOrder) {
                FileLog.i("Notification skipped: ongoing, not delivery: " + packageName + " flags=" + notification.flags);
            }
            if ((!isOngoing(notification) || isDeliveryOrder) && !Settings.SYSTEMUI_PACKAGE.equals(packageName) && !"android".equals(packageName)) {
                String text = getText(bundle, "android.title");
                String text2 = getText(bundle, "android.text");
                if (text2 == null || text2.isEmpty()) {
                    text2 = getText(bundle, "android.bigText");
                }
                // fixed33: some apps keep their content outside
                // android.text — WeChat MessagingStyle puts it in
                // android.messages / android.textLines, Meituan delivery
                // and custom-content notifications often leave text empty
                // and draw everything in a RemoteViews. Without these
                // fallbacks the expanded card renders as a header-only
                // bar (screenshots 2081 WeChat / 2084 WeChat / 2088
                // Meituan: icon + app name + time + close, no body).
                if (text2 == null || text2.isEmpty()) {
                    text2 = getLastTextLine(bundle);
                }
                if (text2 == null || text2.isEmpty()) {
                    text2 = getLatestMessageText(bundle);
                }
                if (text2 == null || text2.isEmpty()) {
                    CharSequence ticker = notification.tickerText;
                    if (ticker != null && !ticker.toString().trim().isEmpty()) {
                        text2 = ticker.toString();
                    }
                }
                if (text2 == null || text2.isEmpty()) {
                    text2 = getText(bundle, "android.subText");
                }
                // fixed55: empty title+text used to fall through silently
                // too — log it, same BOSS/鱼泡直聘 diagnosis as above.
                if ((text == null || text.isEmpty()) && (text2 == null || text2.isEmpty())) {
                    FileLog.i("Notification dropped: empty title and text: " + packageName + " flags=" + notification.flags);
                }
                if ((text != null && !text.isEmpty()) || (text2 != null && !text2.isEmpty())) {
                    // fixed28 (ported from Flyme Island 1.0.38-r3 V()): drop
                    // stale notifications — postTime more than 5s ago means
                    // the system re-delivered an old notification (e.g. after
                    // the listener service reconnected). This is Flyme's STALE
                    // check, as a single absolute window instead of a
                    // per-key postTime comparison.
                    if (System.currentTimeMillis() - statusBarNotification.getPostTime() > 5000) {
                        FileLog.i("Stale notification dropped (>5s old): " + packageName);
                        return;
                    }
                    // fixed28 (ported from Flyme Island 1.0.38-r3 V()): flags
                    // mask 578 = 0x242 = FLAG_ONGOING_EVENT (0x2) |
                    // FLAG_FOREGROUND_SERVICE (0x40) | FLAG_GROUP_SUMMARY
                    // (0x200). Ongoing/foreground-service notifications are
                    // not user messages, and a group summary is redundant
                    // once its children are shown — both are classic
                    // double-pop sources (message + its summary).
                    // fixed31: delivery notifications are ordinary now, so
                    // exempt them from the ongoing bit only (mask 576 =
                    // 0x240: foreground-service | group-summary still drop).
                    int dropMask = isDeliveryOrder ? 576 : 578;
                    if ((notification.flags & dropMask) != 0) {
                        FileLog.i("Notification dropped by flags mask " + dropMask + ": " + packageName);
                        return;
                    }
                    // fixed28 (ported from Flyme Island 1.0.38-r3 W()):
                    // per-package fingerprint gate. Identical id:postTime:
                    // title/text means the exact same notification was
                    // re-delivered — drop with zero action.
                    if (isRepeatNotifFingerprint(packageName, statusBarNotification, text, text2)) {
                        FileLog.i("Repeat notification fingerprint dropped: " + packageName);
                        return;
                    }
                    if (isRepeatNotifKey(statusBarNotification.getKey(), text, text2)) {
                        FileLog.i("Repeat notification key dropped: " + statusBarNotification.getKey());
                        return;
                    }
                    if (isRedundantMessage(statusBarNotification, notification, bundle, packageName, text, text2)) {
                        FileLog.i("Redundant message dropped (already seen): " + packageName);
                        return;
                    }
                    if (isDuplicateNotif(packageName, text, text2)) {
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
                    recentNotif.title = text;
                    recentNotif.text = text2;
                    recentNotif.pkg = packageName;
                    recentNotif.intent = notification.contentIntent;
                    recentNotif.time = statusBarNotification.getPostTime();
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
                        for (int size = this.recentNotifs.size() - 1; size >= 0; size--) {
                            if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.recentNotifs.get(size).key)) {
                                this.recentNotifs.remove(size);
                            }
                        }
                        this.recentNotifs.add(0, recentNotif);
                        // fixed33: log every accepted notification — a
                        // double delivery shows up as two Added lines for
                        // the same key (service side), while a single
                        // Added line plus two POP lines in the bubble
                        // points at the display side instead.
                        FileLog.i("Added notif key=" + recentNotif.key + " pkg=" + packageName + " title=" + recentNotif.title + " text=" + recentNotif.text);
                        while (this.recentNotifs.size() > 8) {
                            RecentNotif recentNotifRemove = this.recentNotifs.remove(this.recentNotifs.size() - 1);
                            if (recentNotifRemove != null && recentNotifRemove.key != null) {
                                synchronized (this.selfCancelledKeys) {
                                    this.selfCancelledKeys.remove(recentNotifRemove.key);
                                }
                            }
                        }
                    }
                    if (statusBarNotification.getKey() != null) {
                        try {
                            synchronized (this.selfCancelledKeys) {
                                this.selfCancelledKeys.add(statusBarNotification.getKey());
                            }
                            cancelNotification(statusBarNotification.getKey());
                        } catch (Throwable th) {
                            Log.e(Settings.TAG, "cancelNotification failed", th);
                        }
                    }
                    broadcastNotifyList();
                    Log.i(Settings.TAG, "Notification posted: " + packageName);
                    // fixed71: local code recognition — the old AiClient
                    // sent the notification to a remote AI endpoint; the
                    // user asked to drop the AI call and recognise
                    // locally instead. No key, no network, no switch:
                    // every notification is scanned on-device.
                    final String key = statusBarNotification.getKey();
                    try {
                        LocalCodeExtractor.extractCode(text, text2, sharedPreferences, new LocalCodeExtractor.Callback() {
                            @Override
                            public void onCodeFound(String str, String str2) {
                                Intent intent = new Intent(Settings.ACTION_CODE);
                                intent.setPackage(Settings.SYSTEMUI_PACKAGE);
                                intent.putExtra(Settings.EXTRA_KEY, key);
                                intent.putExtra(Settings.EXTRA_CODE_KIND, str);
                                intent.putExtra(Settings.EXTRA_CODE, str2);
                                NotificationService.this.sendBroadcast(intent);
                                Log.i(Settings.TAG, "Local code found: " + str);
                            }

                            @Override
                            public void onNoCode() {
                            }
                        });
                    } catch (Throwable th2) {
                        Log.e(Settings.TAG, "LocalCodeExtractor.extractCode failed", th2);
                    }
                }
            }
        } catch (Throwable th3) {
            Log.e(Settings.TAG, "Error in onNotificationPosted", th3);
        }
    }

    @Override // android.service.notification.NotificationListenerService
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        boolean zContains;
        boolean z;
        boolean z2;
        boolean z3;
        boolean zContains2;
        StatusBarNotification[] activeNotifications = null;
        if (statusBarNotification == null) {
            z2 = false;
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
                        z3 = false;
                        for (int size = this.recentNotifs.size() - 1; size >= 0; size--) {
                            if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.recentNotifs.get(size).key)) {
                                this.recentNotifs.remove(size);
                                z3 = true;
                            }
                        }
                    }
                    z = z3;
                }
                if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.currentMediaKey)) {
                    releaseMediaController();
                    // fixed31: delayed inactive (see sendMusicInactiveDelayed).
                    sendMusicInactiveDelayed(statusBarNotification.getPackageName());
                    Log.i(Settings.TAG, "Media notification removed, music inactive.");
                }
                if (statusBarNotification.getKey() != null && statusBarNotification.getKey().equals(this.callKey)) {
                    // fixed90: if we cancelled this notification ourselves
                    // (hide system prompt), the call is still ringing —
                    // the bubble hides on the real end via PhoneStateListener.
                    boolean selfCancelled = false;
                    synchronized (this.selfCancelledKeys) {
                        selfCancelled = this.selfCancelledKeys.contains(statusBarNotification.getKey());
                    }
                    if (!selfCancelled) {
                        this.callKey = null;
                        sendCallBroadcast(Settings.CALL_IDLE, null, null);
                        Log.i(Settings.TAG, "Call notification removed, call idle.");
                    } else {
                        Log.i(Settings.TAG, "Call notification removed (self-cancel, call still active).");
                    }
                }
                z2 = z;
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
                        zContains2 = false;
                    } else {
                        synchronized (this.selfCancelledKeys) {
                            zContains2 = this.selfCancelledKeys.contains(str);
                        }
                    }
                    if (!zContains2) {
                        this.recentNotifs.remove(size2);
                        z2 = true;
                    }
                }
            }
        }
        if (z2) {
            broadcastNotifyList();
            Log.i(Settings.TAG, "Notify list updated after removal.");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void broadcastNotifyList() {
        ArrayList<Parcelable> arrayList = new ArrayList<>();
        synchronized (this.recentNotifs) {
            for (RecentNotif recentNotif : this.recentNotifs) {
                Bundle bundle = new Bundle();
                bundle.putString(Settings.ITEM_KEY, recentNotif.key);
                bundle.putString(Settings.ITEM_TITLE, recentNotif.title);
                bundle.putString(Settings.ITEM_TEXT, recentNotif.text);
                bundle.putString(Settings.ITEM_PACKAGE, recentNotif.pkg);
                bundle.putLong(Settings.ITEM_TIME, recentNotif.time);
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
        // fixed33: log each broadcast so a double broadcast of the same
        // list (two identical Broadcast lines) is visible in logcat.
        StringBuilder keys = new StringBuilder();
        for (android.os.Parcelable p : arrayList) {
            if (p instanceof Bundle) {
                String k = ((Bundle) p).getString(Settings.ITEM_KEY);
                if (k != null) {
                    if (keys.length() > 0) {
                        keys.append(',');
                    }
                    keys.append(k);
                }
            }
        }
        FileLog.i("Broadcast notify list: " + arrayList.size() + " items keys=" + keys);
        Intent intent = new Intent(Settings.ACTION_NOTIFY_LIST);
        intent.setPackage(Settings.SYSTEMUI_PACKAGE);
        intent.putParcelableArrayListExtra(Settings.EXTRA_NOTIFY_LIST, arrayList);
        sendBroadcast(intent);
    }

    private boolean isCallNotification(StatusBarNotification statusBarNotification, Notification notification) {
        String packageName;
        if ("call".equals(notification.category)) {
            return true;
        }
        if (notification.fullScreenIntent != null) {
            if ((packageName = statusBarNotification.getPackageName()) != null) {
                String lowerCase = packageName.toLowerCase();
                // fixed91: "通话控制" (Flyme call control) posts the
                // incoming-call heads-up from a package that contains
                // none of dialer/phone/telecom — e.g. com.android.incallui
                // itself. Match incallui/call too.
                if (lowerCase.contains("dialer") || lowerCase.contains("phone") || lowerCase.contains("telecom") || lowerCase.contains("incallui") || lowerCase.contains("call")) {
                    return true;
                }
            }
            // fixed91: fallback — a full-screen-intent notification whose
            // title looks like a phone number (or a contact name with a
            // call word) is an incoming call, whatever package posted it.
            try {
                Bundle b = notification.extras;
                String title = b != null ? getText(b, "android.title") : null;
                if (title != null) {
                    String digits = title.replaceAll("[^0-9]", "");
                    if (digits.length() >= 7) {
                        return true;
                    }
                    if (title.contains("来电") || title.contains("呼入") || title.contains("正在呼叫")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
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
        // fixed90: "隐藏系统通话提示" — the system incoming-call heads-up
        // banner is a notification, and the InCallUiHide hooks only block
        // the fullscreen activity, not the banner. Cancel the system
        // notification here so only our call bubble shows. The bubble
        // hides on call end via its PhoneStateListener (IDLE), not via
        // this notification, so cancelling it is safe.
        try {
            SharedPreferences hidePrefs = getSharedPreferences(Settings.PREFS_NAME, 0);
            if (hidePrefs != null && hidePrefs.getBoolean(Settings.KEY_HIDE_CALL_UI, true)) {
                String key = statusBarNotification.getKey();
                if (key != null) {
                    synchronized (this.selfCancelledKeys) {
                        this.selfCancelledKeys.add(key);
                    }
                    cancelNotification(key);
                    Log.i(Settings.TAG, "Call notification cancelled (hide system prompt).");
                }
            }
        } catch (Throwable th) {
            Log.e(Settings.TAG, "cancel call notification failed", th);
        }
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
        // fixed31: a replacement media notification cancels any pending
        // delayed "music inactive" from the old notification's removal.
        cancelPendingMusicInactive();
        String key = statusBarNotification.getKey();
        if (key != null && key.equals(this.currentMediaKey) && this.mediaController != null) {
            // fixed31: lyrics are back to the original notification
            // source. Same notification key = same song: only fill lyric
            // slots that have a value, so a lyric-less re-post does not
            // wipe the lyric we already have.
            updateLyricCandidates(notification, true);
            try {
                if (notification.extras != null) {
                    CharSequence t = notification.extras.getCharSequence("android.title");
                    if (t != null && !t.toString().trim().isEmpty()) {
                        this.currentNotifTitle = t.toString().trim();
                    }
                }
            } catch (Throwable ignored) {
            }
            sendMusicStateFromController(statusBarNotification.getPackageName(), notification.contentIntent);
            return;
        }
        String notifTitle = null;
        try {
            if (notification.extras != null) {
                CharSequence t = notification.extras.getCharSequence("android.title");
                if (t != null) {
                    notifTitle = t.toString().trim();
                }
            }
        } catch (Throwable th) {
        }
        if (notifTitle != null && !notifTitle.isEmpty()) {
            this.currentNotifTitle = notifTitle;
        }
        // fixed31: lyrics are back to the original notification source
        // (new notification key = new song: reset the lyric candidates).
        updateLyricCandidates(notification, false);
        releaseMediaController();
        try {
            final String packageName = statusBarNotification.getPackageName();
            final PendingIntent pendingIntent = notification.contentIntent;
            MediaController mediaController = new MediaController(this, token);
            this.mediaCallback = new MediaController.Callback() { // from class: com.jarvis.bubblenotify.NotificationService.3
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
                    // fixed31: delayed inactive — a destroyed session is
                    // often just the app re-posting with a new session.
                    NotificationService.this.sendMusicInactiveDelayed(packageName);
                    Log.i(Settings.TAG, "Media session destroyed, music inactive.");
                }
            };
            mediaController.registerCallback(this.mediaCallback);
            this.mediaController = mediaController;
            this.currentMediaKey = key;
            sendMusicStateFromController(packageName, pendingIntent);
            Log.i(Settings.TAG, "Media session attached: " + packageName);
        } catch (Throwable th) {
            Log.e(Settings.TAG, "Failed to attach media session", th);
            releaseMediaController();
        }
    }

    // fixed128: refresh the lyric candidates from the currently active
    // media notification. The candidates are otherwise only updated when
    // a notification arrives (handleMediaNotification), but
    // sendMusicStateFromController() also runs on MediaSession metadata
    // changes (onMetadataChanged) — and a lyric line delivered through
    // the metadata (DISPLAY_SUBTITLE) would then lose to a stale
    // notification candidate. Re-reading the active notification keeps
    // ticker/text/bigText fresh on every lyric pick.
    private void refreshLyricCandidatesFromActive() {
        try {
            if (this.currentMediaKey == null) {
                return;
            }
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) {
                return;
            }
            for (StatusBarNotification sbn : active) {
                if (sbn != null && this.currentMediaKey.equals(sbn.getKey())) {
                    updateLyricCandidates(sbn.getNotification(), true);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Reads lyric candidates out of a media notification. Different apps hide the
     * current lyric line in different places: QQ Music uses android.text,
     * NetEase Cloud Music puts it in tickerText (that's the line scrolling in
     * the status bar) while android.text is just the artist. We keep all three
     * and let sendMusicStateFromController pick the first one that is not the
     * title/artist. sameSong=true (same notification key, or same title on a
     * re-post) only overwrites slots that have a value, so a lyric-less re-post
     * does not wipe the lyric we already have.
     */
    private void updateLyricCandidates(Notification notification, boolean sameSong) {
        try {
            String text = null;
            String bigText = null;
            String ticker = null;
            if (notification != null && notification.extras != null) {
                CharSequence cs = notification.extras.getCharSequence("android.text");
                if (cs != null) {
                    text = cs.toString().trim();
                }
                CharSequence cb = notification.extras.getCharSequence("android.bigText");
                if (cb != null) {
                    bigText = cb.toString().trim();
                }
            }
            if (notification != null && notification.tickerText != null) {
                ticker = notification.tickerText.toString().trim();
            }
            if (sameSong) {
                if (text != null && !text.isEmpty()) {
                    this.currentLyricText = text;
                }
                if (bigText != null && !bigText.isEmpty()) {
                    this.currentLyricBigText = bigText;
                }
                if (ticker != null && !ticker.isEmpty()) {
                    this.currentLyricTicker = ticker;
                }
            } else {
                this.currentLyricText = text;
                this.currentLyricBigText = bigText;
                this.currentLyricTicker = ticker;
            }
        } catch (Throwable th) {
        }
    }

    private static boolean isLyricCandidate(String s, String title, String artist, String albumArtist) {
        try {
            if (s == null) {
                return false;
            }
            s = s.trim();
            if (s.isEmpty()) {
                return false;
            }
            if (title != null && !title.isEmpty()) {
                if (s.equals(title) || s.startsWith(title + " - ") || s.endsWith(" - " + title)
                        || s.startsWith(title + "：") || s.endsWith("：" + title)) {
                    return false;
                }
            }
            if (artist != null && !artist.isEmpty()) {
                if (s.equals(artist) || s.equals(title + " - " + artist) || s.equals(artist + " - " + title)
                        || s.equals(title + "：" + artist) || s.equals(artist + "：" + title)) {
                    return false;
                }
            }
            if (albumArtist != null && !albumArtist.isEmpty() && s.equals(albumArtist)) {
                return false;
            }
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    // fixed17: NetEase (and some other players) put the current lyric line
    // into the MediaSession TITLE — e.g. "词：许嵩" (a credits line) or a
    // lyric sentence. MusicBubble treats any title change as a song change
    // (z), so every lyric refresh popped the docked player back out. A
    // credits/lyric line is never a song title: keep the previous real
    // title instead.
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

    // fixed31: delayed "music inactive" (see field comment). Cancels any
    // pending inactive broadcast — called when a media notification
    // attaches, i.e. the removal was just a re-post, not a real stop.
    private void cancelPendingMusicInactive() {
        try {
            if (this.pendingMusicInactiveRunnable != null) {
                this.mainHandler.removeCallbacks(this.pendingMusicInactiveRunnable);
                this.pendingMusicInactiveRunnable = null;
            }
        } catch (Throwable ignored) {
        }
    }

    // fixed31: schedule the inactive broadcast 2s out. It only fires if
    // no replacement media session attached in the meantime (a real stop),
    // so lyric re-posts never hide-then-re-pop the player.
    private void sendMusicInactiveDelayed(final String pkg) {
        cancelPendingMusicInactive();
        this.pendingMusicInactiveRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (NotificationService.this.mediaController == null
                            && NotificationService.this.currentMediaKey == null) {
                        NotificationService.this.sendMusicBroadcast(false, false, null, null, null, pkg, null, -1L, -1L, null);
                        Log.i(Settings.TAG, "Media inactive (delayed).");
                    }
                } catch (Throwable th) {
                    Log.i(Settings.TAG, "Delayed music inactive failed: " + th.getClass().getSimpleName());
                }
            }
        };
        this.mainHandler.postDelayed(this.pendingMusicInactiveRunnable, MUSIC_INACTIVE_DELAY_MS);
    }

    public void sendMusicStateFromController(String str, PendingIntent pendingIntent) {
        Bitmap bitmapScaleBitmap;
        long j;
        String str2;
        String str3;
        MediaController mediaController = this.mediaController;
        if (mediaController == null) {
            return;
        }
        try {
            PlaybackState playbackState = mediaController.getPlaybackState();
            boolean z = playbackState != null && playbackState.getState() == 3;
            long j2 = -1;
            long position = playbackState != null ? playbackState.getPosition() : -1L;
            // fixed56: PlaybackState.getPosition() is the position at the
            // LAST position update, not now — many players (QQ Music
            // re-posting every lyric line) only update it on state
            // changes, so every broadcast reset the bubble's base to the
            // same stale value and the progress bar looked frozen at
            // e.g. 0:54. Correct it with getLastPositionUpdateTime()
            // while playing, so the broadcast carries the live position.
            if (z && position >= 0 && playbackState != null) {
                try {
                    long lastUpdate = playbackState.getLastPositionUpdateTime();
                    if (lastUpdate > 0) {
                        position += android.os.SystemClock.elapsedRealtime() - lastUpdate;
                    }
                } catch (Throwable ignored) {
                }
            }
            MediaMetadata metadata = mediaController.getMetadata();
            String lyric = null;
            if (metadata == null) {
                bitmapScaleBitmap = null;
                j = -1;
                str2 = null;
                str3 = null;
            } else {
                String string = metadata.getString("android.media.metadata.TITLE");
                String string2 = metadata.getString("android.media.metadata.ARTIST");
                // fixed18: prefer the notification title — it is the stable
                // song title. The MediaSession TITLE is polluted with the
                // current lyric line by NetEase, which faked song changes
                // and popped the docked player out on every lyric refresh.
                if (this.currentNotifTitle != null && !this.currentNotifTitle.isEmpty()
                        && !isCreditOrLyricTitle(this.currentNotifTitle)) {
                    string = this.currentNotifTitle;
                }
                // fixed19 (strengthened in fixed26): sticky title. A title
                // change is a lyric line, not a new song, if the duration is
                // unchanged (same song) — regardless of position — or, when
                // the duration is unavailable, if we are mid-song (position
                // past 5s). The old check also required position > 10s, so a
                // lyric line arriving in the first 10s (or with no duration
                // reported) was accepted as a new title and popped the docked
                // player out — twice, once per lyric line. Only a duration
                // change, or a title change with the position reset near 0
                // (a new song just started), accepts a new title.
                long durForTitle = -1;
                try {
                    durForTitle = metadata.getLong("android.media.metadata.DURATION");
                } catch (Throwable ignored) {
                }
                boolean sameSong = this.currentMediaTitle != null && !this.currentMediaTitle.isEmpty()
                        && ((durForTitle > 0 && this.currentMediaDuration > 0
                                && durForTitle == this.currentMediaDuration)
                            || (durForTitle <= 0 && position > 5000));
                if (sameSong) {
                    string = this.currentMediaTitle;
                } else if (durForTitle > 0) {
                    this.currentMediaDuration = durForTitle;
                }
                // fixed17: a credits/lyric line in TITLE is not a new song —
                // keep the previous real title so lyric refreshes don't fake
                // a song change and pop the docked player out.
                if (isCreditOrLyricTitle(string) && this.currentMediaTitle != null && !this.currentMediaTitle.isEmpty()) {
                    string = this.currentMediaTitle;
                } else if (string != null && !string.isEmpty()) {
                    this.currentMediaTitle = string;
                }
                try {
                    j2 = metadata.getLong("android.media.metadata.DURATION");
                } catch (Throwable th) {
                }
                String albumArtist = null;
                try {
                    albumArtist = metadata.getString("android.media.metadata.ALBUM_ARTIST");
                } catch (Throwable th) {
                }
                // fixed31: lyrics are back to the original notification
                // source (fixed5 multi-candidate pick). Network lyrics
                // (fixed18 LRCLIB) are off: the lookup returned nothing
                // for songs the library does not carry, so the card
                // showed no lyric at all. Order: tickerText (NetEase)
                // -> android.text (QQ Music) -> android.bigText ->
                // DISPLAY_SUBTITLE; each must pass isLyricCandidate
                // (not the title/artist/album-artist).
                // fixed128: refresh the notification lyric candidates
                // from the active notification first, so a metadata-driven
                // update does not pick a stale notification line over the
                // fresh DISPLAY_SUBTITLE.
                refreshLyricCandidatesFromActive();
                String subtitle = null;
                try {
                    subtitle = metadata.getString("android.media.metadata.DISPLAY_SUBTITLE");
                } catch (Throwable ignored) {
                }
                if (isLyricCandidate(this.currentLyricTicker, string, string2, albumArtist)) {
                    lyric = this.currentLyricTicker;
                } else if (isLyricCandidate(this.currentLyricText, string, string2, albumArtist)) {
                    lyric = this.currentLyricText;
                } else if (isLyricCandidate(this.currentLyricBigText, string, string2, albumArtist)) {
                    lyric = this.currentLyricBigText;
                } else if (isLyricCandidate(subtitle, string, string2, albumArtist)) {
                    lyric = subtitle;
                }
                Bitmap bitmap = metadata.getBitmap("android.media.metadata.ALBUM_ART");
                if (bitmap == null) {
                    bitmap = metadata.getBitmap("android.media.metadata.DISPLAY_ICON");
                }
                if (bitmap == null) {
                    j = j2;
                    str2 = string;
                    str3 = string2;
                    bitmapScaleBitmap = bitmap;
                } else {
                    j = j2;
                    str2 = string;
                    str3 = string2;
                    bitmapScaleBitmap = scaleBitmap(bitmap, 96);
                }
            }
            sendMusicBroadcast(true, z, str2, str3, bitmapScaleBitmap, str, pendingIntent, j, position, lyric);
        } catch (Throwable th2) {
            Log.e(Settings.TAG, "Error reading media state", th2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendMusicBroadcast(boolean z, boolean z2, String str, String str2, Bitmap bitmap, String str3, PendingIntent pendingIntent, long j, long j2, String lyric) {
        Intent intent = new Intent(Settings.ACTION_MUSIC);
        intent.setPackage(Settings.SYSTEMUI_PACKAGE);
        intent.putExtra(Settings.EXTRA_MUSIC_ACTIVE, z);
        intent.putExtra(Settings.EXTRA_MUSIC_PLAYING, z2);
        intent.putExtra(Settings.EXTRA_MUSIC_TITLE, str);
        intent.putExtra(Settings.EXTRA_MUSIC_ARTIST, str2);
        intent.putExtra(Settings.EXTRA_PACKAGE, str3);
        intent.putExtra(Settings.EXTRA_MUSIC_DURATION, j);
        intent.putExtra(Settings.EXTRA_MUSIC_POSITION, j2);
        if (lyric != null) {
            intent.putExtra(Settings.EXTRA_MUSIC_LYRIC, lyric);
        }
        if (bitmap != null) {
            intent.putExtra(Settings.EXTRA_MUSIC_ALBUM, bitmap);
        }
        if (pendingIntent != null) {
            intent.putExtra(Settings.EXTRA_INTENT, pendingIntent);
        }
        sendBroadcast(intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseMediaController() {
        MediaController mediaController = this.mediaController;
        this.mediaController = null;
        this.currentMediaKey = null;
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

    /* JADX INFO: Access modifiers changed from: private */
    public void handleMusicControl(String str, long j) {
        if (str == null) {
            return;
        }
        try {
            MediaController mediaController = this.mediaController;
            if (mediaController != null && mediaController.getTransportControls() != null) {
                if (Settings.CONTROL_TOGGLE.equals(str)) {
                    PlaybackState playbackState = mediaController.getPlaybackState();
                    boolean z = playbackState != null && playbackState.getState() == 3;
                    if (z) {
                        mediaController.getTransportControls().pause();
                    } else {
                        mediaController.getTransportControls().play();
                    }
                    Log.i(Settings.TAG, "Music control: toggle -> " + (z ? "pause" : "play"));
                } else if (Settings.CONTROL_NEXT.equals(str)) {
                    mediaController.getTransportControls().skipToNext();
                    Log.i(Settings.TAG, "Music control: next");
                } else if (Settings.CONTROL_PREV.equals(str)) {
                    mediaController.getTransportControls().skipToPrevious();
                    Log.i(Settings.TAG, "Music control: prev");
                } else if (Settings.CONTROL_STOP.equals(str)) {
                    mediaController.getTransportControls().stop();
                    Log.i(Settings.TAG, "Music control: stop");
                } else if (Settings.CONTROL_SEEK.equals(str) && j >= 0) {
                    mediaController.getTransportControls().seekTo(j);
                    Log.i(Settings.TAG, "Music control: seek to " + j);
                }
                return;
            }
            Log.i(Settings.TAG, "Music control ignored, no active session: " + str);
        } catch (Throwable th) {
            Log.e(Settings.TAG, "Error handling music control " + str, th);
        }
    }

    private void registerCommandReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Settings.ACTION_MUSIC_CONTROL);
        intentFilter.addAction(Settings.ACTION_DISMISS);
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
        // fixed81: a foreground-service notification (flag 32) is
        // ongoing by definition — Amap posts its navigation
        // notification that way and it was not always also flagged
        // ongoing (flag 2), so the nav branch never matched and the
        // live-nav bubble never appeared.
        return (notification.flags & 2) != 0 || (notification.flags & 32) != 0;
    }

    // fixed33: last non-empty line of android.textLines (InboxStyle /
    // MessagingStyle notifications). The last line is the newest.
    private static String getLastTextLine(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        try {
            CharSequence[] lines = bundle.getCharSequenceArray("android.textLines");
            if (lines != null) {
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i] != null && !lines[i].toString().trim().isEmpty()) {
                        return lines[i].toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // fixed33: text of the latest android.messages entry
    // (Notification.MessagingStyle.Message). The last entry is newest.
    // Message does not declare Parcelable in android-34.jar, so a direct
    // instanceof/cast does not compile — read getText() by reflection.
    private static String getLatestMessageText(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        try {
            android.os.Parcelable[] messages = bundle.getParcelableArray("android.messages");
            if (messages != null) {
                for (int i = messages.length - 1; i >= 0; i--) {
                    Object msg = messages[i];
                    if (msg == null) {
                        continue;
                    }
                    if ("android.app.Notification$MessagingStyle$Message".equals(msg.getClass().getName())) {
                        try {
                            java.lang.reflect.Method getText = msg.getClass().getMethod("getText");
                            Object t = getText.invoke(msg);
                            if (t instanceof CharSequence && !t.toString().trim().isEmpty()) {
                                return t.toString();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getText(Bundle bundle, String str) {
        if (bundle == null) {
            return null;
        }
        try {
            CharSequence charSequence = bundle.getCharSequence(str);
            if (charSequence != null) {
                return charSequence.toString();
            }
            return null;
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
        Drawable drawableLoadDrawable;
        // fixed13: the target app's own Context first — it inflates the
        // icon with the correct theme/resources.
        Bitmap pkgCtxBitmap = appIconViaPackageContext(str);
        if (pkgCtxBitmap != null && !isBadIconBitmap(pkgCtxBitmap)) {
            IconResult iconResult = new IconResult();
            iconResult.bitmap = pkgCtxBitmap;
            return iconResult;
        }
        Log.i(Settings.TAG, "PackageContext icon bad/blank for " + str + ", trying resources");
        // fixed11: decode the icon straight from the app's own resources.
        // BitmapFactory.decodeResource gives the raw PNG bitmap and
        // bypasses Drawable rendering entirely — the Canvas re-draw is what
        // kept turning the icon white on this device.
        Bitmap resBitmap = appIconBitmapFromResources(str);
        if (resBitmap != null && !isBadIconBitmap(resBitmap)) {
            IconResult iconResult = new IconResult();
            iconResult.bitmap = resBitmap;
            return iconResult;
        }
        Bitmap launcherBitmap = launcherIconBitmap(str);
        if (launcherBitmap != null && !isBadIconBitmap(launcherBitmap)) {
            IconResult iconResult = new IconResult();
            iconResult.bitmap = launcherBitmap;
            return iconResult;
        }
        Drawable applicationIcon = null;
        try {
            applicationIcon = getPackageManager().getApplicationIcon(str);
        } catch (Throwable th) {
        }
        if (applicationIcon != null) {
            Bitmap appBitmap = drawableToBitmap(applicationIcon);
            if (appBitmap != null && !isBadIconBitmap(appBitmap)) {
                IconResult iconResult2 = new IconResult();
                iconResult2.bitmap = appBitmap;
                return iconResult2;
            }
            Log.i(Settings.TAG, "App icon blank/white, falling back to small icon: " + str);
        }
        try {
            if (notification.getSmallIcon() != null && (drawableLoadDrawable = notification.getSmallIcon().loadDrawable(this)) != null) {
                android.graphics.Bitmap smallBitmap = drawableToBitmap(drawableLoadDrawable);
                if (smallBitmap != null && !isBadIconBitmap(smallBitmap)) {
                    IconResult iconResult3 = new IconResult();
                    iconResult3.bitmap = smallBitmap;
                    iconResult3.small = true;
                    iconResult3.accent = notification.color;
                    return iconResult3;
                }
                Log.i(Settings.TAG, "Small icon bad/white for " + str + ", using placeholder");
            }
        } catch (Throwable th) {
        }
        return null;
    }

    // fixed13: load the icon with the TARGET app's own Context. Drawables
    // (especially VectorDrawable foregrounds inside an AdaptiveIconDrawable)
    // need the owning app's theme/resources to inflate correctly; loading
    // them with this service's own Context is what kept producing a blank
    // or white bitmap on this device.
    private Bitmap appIconViaPackageContext(String pkg) {
        try {
            android.content.Context pkgCtx = createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            if (pkgCtx == null) {
                return null;
            }
            android.graphics.drawable.Drawable d = pkgCtx.getPackageManager().getApplicationIcon(pkg);
            return drawableToBitmap(d);
        } catch (Throwable th) {
            Log.i(Settings.TAG, "PackageContext icon failed: " + pkg + " " + th);
            return null;
        }
    }

    // fixed11: read the app icon resource directly and decode the PNG with
    // BitmapFactory, so no Drawable has to be drawn on a Canvas at all.
    private Bitmap appIconBitmapFromResources(String pkg) {
        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager().getApplicationInfo(pkg, 0);
            if (appInfo == null || appInfo.icon == 0) {
                return null;
            }
            android.content.res.Resources res = getPackageManager().getResourcesForApplication(pkg);
            try {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeResource(res, appInfo.icon);
                if (bmp != null) {
                    return bmp;
                }
            } catch (Throwable th) {
            }
            try {
                android.graphics.drawable.Drawable d = res.getDrawable(appInfo.icon, null);
                return drawableToBitmap(d);
            } catch (Throwable th) {
            }
        } catch (Throwable th) {
        }
        return null;
    }

    private Bitmap launcherIconBitmap(String pkg) {
        try {
            android.content.pm.LauncherApps launcherApps = (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return null;
            }
            java.util.List<android.content.pm.LauncherActivityInfo> activities = launcherApps.getActivityList(pkg, android.os.Process.myUserHandle());
            if (activities == null || activities.isEmpty()) {
                return null;
            }
            Drawable icon = activities.get(0).getIcon(0);
            return drawableToBitmap(icon);
        } catch (Throwable th) {
            return null;
        }
    }

    // fixed10: bad icon = blank (mostly transparent) OR mostly white. A white
    // bitmap is opaque so the blank check passes it, yet it renders as the
    // white circle in the screenshot — the adaptive background did not draw.
    private static boolean isBadIconBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int stepX = Math.max(1, w / 8);
        int stepY = Math.max(1, h / 8);
        int opaque = 0;
        int white = 0;
        int total = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = bitmap.getPixel(x, y);
                total++;
                int a = (p >>> 24) & 0xFF;
                if (a >= 128) {
                    opaque++;
                    int r = (p >> 16) & 0xFF;
                    int g = (p >> 8) & 0xFF;
                    int b = p & 0xFF;
                    if (r > 240 && g > 240 && b > 240) {
                        white++;
                    }
                }
            }
        }
        if (total == 0 || opaque * 10 < total) {
            return true;
        }
        return white * 10 > opaque * 9;
    }

    // fixed9: blank = mostly transparent (no real content drawn). A solid
    // color is NOT blank — WeChat's green is a real icon.
    private static boolean isBlankBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int stepX = Math.max(1, w / 8);
        int stepY = Math.max(1, h / 8);
        int opaque = 0;
        int total = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = bitmap.getPixel(x, y);
                total++;
                if (((p >>> 24) & 0xFF) >= 128) {
                    opaque++;
                }
            }
        }
        return total == 0 || opaque * 10 < total;
    }

    private static boolean isSolidColorBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int stepX = Math.max(1, w / 8);
        int stepY = Math.max(1, h / 8);
        boolean hasFirst = false;
        int firstR = 0;
        int firstG = 0;
        int firstB = 0;
        int diffSum = 0;
        int count = 0;
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        java.util.HashSet<Integer> distinct = new java.util.HashSet<Integer>();
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = bitmap.getPixel(x, y);
                if (((p >>> 24) & 0xFF) < 128) {
                    continue;
                }
                if (!hasFirst) {
                    firstR = (p >> 16) & 0xFF;
                    firstG = (p >> 8) & 0xFF;
                    firstB = p & 0xFF;
                    hasFirst = true;
                }
                sumR += (p >> 16) & 0xFF;
                sumG += (p >> 8) & 0xFF;
                sumB += p & 0xFF;
                diffSum += Math.abs(((p >> 16) & 0xFF) - firstR)
                        + Math.abs(((p >> 8) & 0xFF) - firstG)
                        + Math.abs((p & 0xFF) - firstB);
                distinct.add((((p >> 16) & 0xFF) / 16 << 8) | (((p >> 8) & 0xFF) / 16 << 4) | ((p & 0xFF) / 16));
                count++;
            }
        }
        if (!hasFirst || count == 0) {
            return true;
        }
        if (distinct.size() <= 5) {
            return true;
        }
        // fixed6: a near-white or near-black image is a placeholder avatar
        // (e.g. QQ group default avatar renders as a pure white square),
        // even if it carries a faint watermark that defeats the color count.
        int avgR = (int) (sumR / count);
        int avgG = (int) (sumG / count);
        int avgB = (int) (sumB / count);
        if ((avgR > 235 && avgG > 235 && avgB > 235) || (avgR < 20 && avgG < 20 && avgB < 20)) {
            return true;
        }
        return (diffSum / count) < 24;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        // fixed11: if it is already a bitmap, take the original bitmap
        // directly — re-drawing it on a Canvas can only lose information.
        // This is the common case for a plain PNG launcher icon.
        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            try {
                android.graphics.Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                if (bmp != null && !bmp.isRecycled()) {
                    return bmp;
                }
            } catch (Throwable th) {
            }
        }
        // fixed9: AdaptiveIconDrawable (what getApplicationIcon returns for
        // most apps now) reports -1 for intrinsic size and draws its layers
        // with a safe-zone inset. Try drawing background + foreground
        // explicitly first; if that comes out blank (a layer failed to draw),
        // let the drawable draw itself, which composites the layers with
        // the safe-zone inset. Return whichever is not blank.
        if (drawable instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            android.graphics.drawable.AdaptiveIconDrawable adaptive = (android.graphics.drawable.AdaptiveIconDrawable) drawable;
            int size = 192;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            try {
                Drawable bg = adaptive.getBackground();
                if (bg != null) {
                    bg.setBounds(0, 0, size, size);
                    bg.draw(canvas);
                }
            } catch (Throwable th) {
            }
            try {
                Drawable fg = adaptive.getForeground();
                if (fg != null) {
                    fg.setBounds(0, 0, size, size);
                    fg.draw(canvas);
                }
            } catch (Throwable th) {
            }
            if (!isBadIconBitmap(bitmap)) {
                return bitmap;
            }
            Bitmap bitmap2 = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas2 = new Canvas(bitmap2);
            try {
                adaptive.setBounds(0, 0, size, size);
                adaptive.draw(canvas2);
            } catch (Throwable th) {
            }
            return bitmap2;
        }
        int iMin = Math.min(Math.max(drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96, drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96), 192);
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(iMin, iMin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        drawable.setBounds(0, 0, iMin, iMin);
        drawable.draw(canvas);
        return bitmapCreateBitmap;
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
}
