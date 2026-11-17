package com.jarvis.bubblenotify;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/* fixed71: local (on-device, no network) extraction of verification /
* pickup codes from a notification's title + text. Replaces AiClient,
* which sent the notification to a remote OpenAI-compatible endpoint —
* the user asked to drop the AI call and recognise locally instead
* (2026-11-17 21:08 "去掉ai识别短信验证码取件码取货码改成本地识别").
* Same Callback shape as AiClient had, so the rest of the pipeline
* (NotificationService -> ACTION_CODE broadcast -> NotifBubble codes map
* -> buildAiCapsule) is unchanged. Runs synchronously on the caller's
* thread: the input is a short notification text, regex only.
*
* fixed89: the recognition rules are customisable. The keyword lists
* (pickup / verify / verify-before / verify-en) and the bare-digit
* exclude words live in SharedPreferences as a JSON string under
* Settings.KEY_CODE_RULES; the MainActivity rules editor writes it.
* extractCode reads the JSON on every call and recompiles only when
* it changed, so a rule edit takes effect on the next notification
* with no restart and no broadcast. Absent / malformed JSON falls
* back to the built-in defaults below. Keywords are Pattern.quoted,
* so a user-typed keyword is always literal text, never a regex that
* could break the whole matcher.
*/
public class LocalCodeExtractor {
    public interface Callback {
        void onCodeFound(String kind, String code);

        void onNoCode();
    }

    // Built-in defaults (the fixed71 rules).
    public static final String DEF_PICKUP = "取件码|取货码|提货码|自提码|取件密码|提货密码";
    public static final String DEF_VERIFY = "短信验证码|手机验证码|验证码|动态码|校验码|验证代码";
    public static final String DEF_VERIFY_BEFORE = "验证码|动态码|校验码";
    public static final String DEF_VERIFY_EN = "verification code|verify code|OTP|auth code";
    public static final String DEF_EXCLUDE = "订单|单号|卡号|尾号|账号|工号|编号|流水|运单|包裹|快递|房号|房间|金额|价格";

    // Separators that may sit between a keyword and its code:
    // "验证码是123456" / "验证码：123456" / "验证码 123456" /
    // "验证码（123456）" / "取件码".
    private static final String SEP = "[\\s:：是为（(【\\[]";
    private static final Pattern BARE_DIGITS = Pattern.compile("(?<!\\d)(\\d{4,6})(?!\\d)");

    // Compiled rules cache — recompiled only when the rules JSON changes.
    private static String cachedJson = null;
    private static Pattern pickupPattern;
    private static Pattern verifyAfterPattern;
    private static Pattern verifyBeforePattern;
    private static Pattern verifyEnPattern;
    private static String[] excludeWords;
    private static boolean bareOn = true;

    static {
        rebuildRules(null);
    }

    // Read one keyword list from the rules JSON (or the default).
    public static String readRule(SharedPreferences prefs, String key, String def) {
        try {
            if (prefs == null) {
                return def;
            }
            String json = prefs.getString(Settings.KEY_CODE_RULES, null);
            if (json == null || json.isEmpty()) {
                return def;
            }
            JSONObject obj = new JSONObject(json);
            if (!obj.has(key)) {
                return def;
            }
            String v = obj.optString(key, def);
            return v == null ? def : v;
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static boolean readBareOn(SharedPreferences prefs) {
        try {
            if (prefs == null) {
                return true;
            }
            String json = prefs.getString(Settings.KEY_CODE_RULES, null);
            if (json == null || json.isEmpty()) {
                return true;
            }
            JSONObject obj = new JSONObject(json);
            return obj.optBoolean("bareOn", true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    // (Re)build the compiled patterns from the rules JSON. Called on
    // every extract; cheap string compare, recompile only on change.
    private static synchronized void rebuildRules(SharedPreferences prefs) {
        String json = null;
        try {
            if (prefs != null) {
                json = prefs.getString(Settings.KEY_CODE_RULES, null);
            }
        } catch (Throwable ignored) {
        }
        if (json != null && json.equals(cachedJson)) {
            return;
        }
        cachedJson = json;
        String pickup = readRule(prefs, "pickup", DEF_PICKUP);
        String verify = readRule(prefs, "verify", DEF_VERIFY);
        String verifyBefore = readRule(prefs, "verifyBefore", DEF_VERIFY_BEFORE);
        String verifyEn = readRule(prefs, "verifyEn", DEF_VERIFY_EN);
        String exclude = readRule(prefs, "exclude", DEF_EXCLUDE);
        bareOn = readBareOn(prefs);
        pickupPattern = compileKeywordPattern(pickup, SEP + "{0,4}([A-Za-z0-9]{4,8})(?![A-Za-z0-9])", false);
        verifyAfterPattern = compileKeywordPattern(verify, SEP + "{0,4}(\\d{4,8})(?!\\d)", false);
        verifyBeforePattern = compileKeywordPattern(verifyBefore, null, true);
        verifyEnPattern = compileKeywordPattern(verifyEn, "[^0-9]{0,12}(\\d{4,8})(?!\\d)", true);
        excludeWords = splitKeywords(exclude);
    }

    // Build "(?:kw1|kw2|...)" + tail. Keywords are Pattern.quoted
    // (literal). verifyBefore is special: the code comes BEFORE the
    // keyword, so the pattern is "(\d{4,8})(?:是|为|您的|你的|的)
    // [^0-9]{0,4}(?:keywords)". Returns null when the list is empty
    // (rule disabled).
    private static Pattern compileKeywordPattern(String keywords, String tail, boolean ignoreCase) {
        try {
            String[] kws = splitKeywords(keywords);
            if (kws.length == 0) {
                return null;
            }
            StringBuilder alt = new StringBuilder();
            for (String kw : kws) {
                if (alt.length() > 0) {
                    alt.append('|');
                }
                alt.append(Pattern.quote(kw));
            }
            String regex;
            if (tail == null) {
                regex = "(\\d{4,8})(?:是|为|您的|你的|的)[^0-9]{0,4}(?:" + alt + ")";
            } else {
                regex = "(?:" + alt + ")" + tail;
            }
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(regex, flags);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String[] splitKeywords(String keywords) {
        if (keywords == null) {
            return new String[0];
        }
        String[] parts = keywords.split("\\|");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out.toArray(new String[0]);
    }

    public static void extractCode(String title, String text, Callback callback) {
        extractCode(title, text, null, callback);
    }

    public static void extractCode(String title, String text, SharedPreferences prefs, Callback callback) {
        if (callback == null) {
            return;
        }
        try {
            rebuildRules(prefs);
            String combined = combine(title, text);
            if (combined.isEmpty()) {
                callback.onNoCode();
                return;
            }
            String code;
            // Pickup codes first: their keywords are the most specific.
            code = find(pickupPattern, combined);
            if (code != null) {
                callback.onCodeFound(Settings.CODE_KIND_PICKUP, code);
                return;
            }
            code = find(verifyAfterPattern, combined);
            if (code != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, code);
                return;
            }
            code = find(verifyBeforePattern, combined);
            if (code != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, code);
                return;
            }
            code = find(verifyEnPattern, combined);
            if (code != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, code);
                return;
            }
            // Bare fallback: exactly one standalone 4-6 digit group that
            // is not a date / amount / order number. Ambiguous -> no code.
            if (bareOn) {
                code = findBareCode(combined);
                if (code != null) {
                    callback.onCodeFound(Settings.CODE_KIND_VERIFY, code);
                    return;
                }
            }
            callback.onNoCode();
        } catch (Throwable th) {
            callback.onNoCode();
        }
    }

    private static String combine(String title, String text) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            sb.append(title.trim());
        }
        if (text != null && !text.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text.trim());
        }
        return sb.toString();
    }

    private static String find(Pattern pattern, String input) {
        if (pattern == null) {
            return null;
        }
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            String g = m.group(1);
            if (g != null && !g.isEmpty()) {
                return g;
            }
        }
        return null;
    }

    private static String findBareCode(String input) {
        Matcher m = BARE_DIGITS.matcher(input);
        String found = null;
        int count = 0;
        while (m.find()) {
            String g = m.group(1);
            int start = m.start(1);
            int end = m.end(1);
            // Amount: ￥1234 / ¥1234.
            if (start > 0 && (input.charAt(start - 1) == '￥' || input.charAt(start - 1) == '¥')) {
                continue;
            }
            // Date / amount suffix: 2026年 / 11月 / 17日 / 1234元.
            if (end < input.length()) {
                char after = input.charAt(end);
                if (after == '年' || after == '月' || after == '日' || after == '元' || after == '块' || after == '号') {
                    continue;
                }
            }
            // A 4-digit group that is a year (19xx/20xx) is not a code.
            if (g.length() == 4 && (g.startsWith("19") || g.startsWith("20"))) {
                continue;
            }
            // Order / card / tracking / room number, not a code.
            String before = input.substring(Math.max(0, start - 8), start);
            boolean excluded = false;
            if (excludeWords != null) {
                for (String w : excludeWords) {
                    if (before.contains(w)) {
                        excluded = true;
                        break;
                    }
                }
            }
            if (excluded) {
                continue;
            }
            count++;
            if (count > 1) {
                return null;
            }
            found = g;
        }
        return found;
    }
}
