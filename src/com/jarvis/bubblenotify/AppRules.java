package com.jarvis.bubblenotify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * flymebobo 1.x (v203) — per-app bubble rules, ported from 2.0's
 * 2.4.2 (backlog #5; UI previewed and approved 2026-11-23).
 *
 * Each app can be set to:
 *   DEFAULT — follow the global switches (no rule stored);
 *   NEVER   — its notifications never become bubbles (the system
 *             notification itself is untouched);
 *   EDGE    — no pop; the notification goes straight into the
 *             docked edge pile.
 *
 * Storage follows 1.x's own settings channel: rules live in the
 * same "settings" SharedPreferences as everything else, keys
 * "apprule_<pkg>" -> "never"/"edge". The APP writes them;
 * BubbleManager (SystemUI) reads them through libxposed remote
 * preferences, at startup and again on every ACTION_SETTINGS
 * broadcast, so a change applies at once with no restart.
 */
public final class AppRules {

    public static final int DEFAULT = 0;
    public static final int NEVER = 1;
    public static final int EDGE = 2;

    public static final String KEY_PREFIX = "apprule_";

    /** Intent extra carrying the whole rule set, flattened as
     * "pkg=value;pkg=value" — the live channel to SystemUI.
     * (Remote preferences do not refresh live in the hooked
     * process; the settings intent is how every other setting
     * in this app reaches SystemUI, so rules ride it too.) */
    public static final String EXTRA_RULES = "apprules_flat";

    public static String flatten(Map<String, Integer> rules) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : rules.entrySet()) {
            String v = value(e.getValue());
            if (v == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(v);
        }
        return sb.toString();
    }

    public static java.util.Map<String, Integer> parseFlat(
            String flat) {
        java.util.HashMap<String, Integer> out =
                new java.util.HashMap<>();
        if (flat == null) {
            return out;
        }
        for (String part : flat.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            int mode = parse(
                    part.substring(eq + 1).trim());
            if (mode != DEFAULT) {
                out.put(part.substring(0, eq).trim(), mode);
            }
        }
        return out;
    }
    public static final String VALUE_NEVER = "never";
    public static final String VALUE_EDGE = "edge";

    private AppRules() {
    }

    public static int parse(String v) {
        if (VALUE_NEVER.equals(v)) {
            return NEVER;
        }
        if (VALUE_EDGE.equals(v)) {
            return EDGE;
        }
        return DEFAULT;
    }

    public static String value(int mode) {
        if (mode == NEVER) {
            return VALUE_NEVER;
        }
        if (mode == EDGE) {
            return VALUE_EDGE;
        }
        return null;
    }

    /** SystemUI side: extract the rule map from the settings
     * prefs (remote preferences in BubbleManager). */
    public static Map<String, Integer> parseRules(
            SharedPreferences prefs) {
        Map<String, Integer> m = new HashMap<>();
        if (prefs == null) {
            return m;
        }
        try {
            for (Map.Entry<String, ?> e : prefs.getAll()
                    .entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith(
                        KEY_PREFIX)
                        && e.getValue() instanceof String) {
                    String pkg = e.getKey().substring(
                            KEY_PREFIX.length());
                    int mode = parse((String) e.getValue());
                    if (!pkg.isEmpty() && mode != DEFAULT) {
                        m.put(pkg, mode);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return m;
    }

    // ---- APP side ----

    /** All rules currently stored in the APP's settings prefs. */
    public static Map<String, Integer> readAll(Context appCtx) {
        if (appCtx == null) {
            return new HashMap<>();
        }
        return parseRules(appCtx.getSharedPreferences(
                Settings.PREFS_NAME, Context.MODE_PRIVATE));
    }

    /** Save one rule (DEFAULT removes it) and broadcast
     * ACTION_SETTINGS so SystemUI reloads at once — the same
     * channel every other 1.x setting uses. */
    public static void setRule(Context appCtx, String pkg,
            int mode) {
        if (appCtx == null || pkg == null) {
            return;
        }
        try {
            SharedPreferences.Editor ed = appCtx
                    .getSharedPreferences(Settings.PREFS_NAME,
                            Context.MODE_PRIVATE)
                    .edit();
            String v = value(mode);
            if (v == null) {
                ed.remove(KEY_PREFIX + pkg);
            } else {
                ed.putString(KEY_PREFIX + pkg, v);
            }
            // commit, not apply: SystemUI reads the rules back
            // from the prefs FILE through remote preferences, so
            // the write must be on disk before the broadcast
            // below arrives.
            ed.commit();
        } catch (Throwable ignored) {
        }
        try {
            // Explicit package, like MainActivity.pushSettings
            // and NotificationService.sendLogLine — an implicit
            // broadcast to SystemUI gets dropped (v203 bug: the
            // rule was written but SystemUI never reloaded, so
            // the app filter "had no effect" until a restart).
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.setPackage(Settings.SYSTEMUI_PACKAGE);
            intent.putExtra(EXTRA_RULES,
                    flatten(readAll(appCtx)));
            appCtx.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }
}
