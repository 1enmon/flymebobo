package com.jarvis.bubblenotify;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class LocalCodeExtractor {
    public static final String DEF_EXCLUDE = "订单|单号|卡号|尾号|账号|工号|编号|流水|运单|包裹|快递|房号|房间|金额|价格";
    public static final String DEF_PICKUP = "取件码|取货码|提货码|自提码|取餐码|取件密码|提货密码|寄存码|领取码";
    public static final String DEF_VERIFY = "短信验证码|手机验证码|验证码|动态码|校验码|验证代码";
    public static final String DEF_VERIFY_BEFORE = "验证码|动态码|校验码";
    public static final String DEF_VERIFY_EN = "verification code|verify code|OTP|auth code";
    private static final String SEP = "[\\s:：是为（(【\\[]";
    private static String[] excludeWords;
    private static Pattern pickupPattern;
    // fixed184: 2.0 v245 locker/courier code with no keyword
    // ("请凭173351取件", "凭10-18-0413取件"), and
    // hyphenated pickup codes ("10-18-0413").
    private static final Pattern LOCKER_CODE_PATTERN = Pattern.compile("\u51ed\\s*([0-9]{4,8}|[0-9]{2,4}(?:-[0-9]{2,4}){1,2})");
    private static final Pattern HYPHEN_CODE_PATTERN = Pattern.compile("[0-9]{2,4}(?:-[0-9]{2,4}){1,2}");
    private static Pattern verifyAfterPattern;
    private static Pattern verifyBeforePattern;
    private static Pattern verifyEnPattern;
    private static final Pattern BARE_DIGITS = Pattern.compile("(?<!\\d)(\\d{4,6})(?!\\d)");
    private static String cachedJson = null;
    private static boolean bareOn = true;

    public interface Callback {
        void onCodeFound(String str, String str2);

        void onNoCode();
    }

    static {
        rebuildRules(null);
    }

    public static String readRule(SharedPreferences sharedPreferences, String str, String str2) {
        String strOptString;
        if (sharedPreferences == null) {
            return str2;
        }
        try {
            String string = sharedPreferences.getString(Settings.KEY_CODE_RULES, null);
            if (string != null && !string.isEmpty()) {
                JSONObject jSONObject = new JSONObject(string);
                return (jSONObject.has(str) && (strOptString = jSONObject.optString(str, str2)) != null) ? strOptString : str2;
            }
            return str2;
        } catch (Throwable th) {
            return str2;
        }
    }

    public static boolean readBareOn(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return true;
        }
        try {
            String string = sharedPreferences.getString(Settings.KEY_CODE_RULES, null);
            if (string != null && !string.isEmpty()) {
                return new JSONObject(string).optBoolean("bareOn", true);
            }
            return true;
        } catch (Throwable th) {
            return true;
        }
    }

    private static synchronized void rebuildRules(SharedPreferences sharedPreferences) {
        String string;
        if (sharedPreferences != null) {
            try {
                string = sharedPreferences.getString(Settings.KEY_CODE_RULES, null);
            } catch (Throwable th) {
                string = null;
            }
        } else {
            string = null;
        }
        if (string != null) {
            if (string.equals(cachedJson)) {
                return;
            }
        }
        cachedJson = string;
        String rule = readRule(sharedPreferences, "pickup", DEF_PICKUP);
        String rule2 = readRule(sharedPreferences, "verify", DEF_VERIFY);
        String rule3 = readRule(sharedPreferences, "verifyBefore", DEF_VERIFY_BEFORE);
        String rule4 = readRule(sharedPreferences, "verifyEn", DEF_VERIFY_EN);
        String rule5 = readRule(sharedPreferences, "exclude", DEF_EXCLUDE);
        bareOn = readBareOn(sharedPreferences);
        pickupPattern = compileKeywordPattern(rule, "[\\s:：是为（(【\\[]{0,4}([A-Za-z0-9]{4,8})(?![A-Za-z0-9])", false);
        verifyAfterPattern = compileKeywordPattern(rule2, "[\\s:：是为（(【\\[]{0,4}(\\d{4,8})(?!\\d)", false);
        verifyBeforePattern = compileKeywordPattern(rule3, null, true);
        verifyEnPattern = compileKeywordPattern(rule4, "[^0-9]{0,12}(\\d{4,8})(?!\\d)", true);
        excludeWords = splitKeywords(rule5);
    }

    private static Pattern compileKeywordPattern(String str, String str2, boolean z) {
        try {
            String[] strArrSplitKeywords = splitKeywords(str);
            if (strArrSplitKeywords.length == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (String str3 : strArrSplitKeywords) {
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(Pattern.quote(str3));
            }
            return Pattern.compile(str2 == null ? "(\\d{4,8})(?:是|为|您的|你的|的)[^0-9]{0,4}(?:" + String.valueOf(sb) + ")" : "(?:" + String.valueOf(sb) + ")" + str2, z ? 2 : 0);
        } catch (Throwable th) {
            return null;
        }
    }

    private static String[] splitKeywords(String str) {
        if (str == null) {
            return new String[0];
        }
        String[] strArrSplit = str.split("\\|");
        ArrayList arrayList = new ArrayList();
        int length = strArrSplit.length;
        for (int i = 0; i < length; i++) {
            String str2 = strArrSplit[i];
            String strTrim = str2 == null ? "" : str2.trim();
            if (!strTrim.isEmpty()) {
                arrayList.add(strTrim);
            }
        }
        return (String[]) arrayList.toArray(new String[0]);
    }

    public static void extractCode(String str, String str2, Callback callback) {
        extractCode(str, str2, null, callback);
    }

    public static void extractCode(String str, String str2, SharedPreferences sharedPreferences, Callback callback) {
        String strFindBareCode;
        if (callback == null) {
            return;
        }
        try {
            rebuildRules(sharedPreferences);
            String strCombine = combine(str, str2);
            if (strCombine.isEmpty()) {
                callback.onNoCode();
                return;
            }
            String strFind = find(pickupPattern, strCombine);
            if (strFind != null) {
                callback.onCodeFound(Settings.CODE_KIND_PICKUP, strFind);
                return;
            }
            String lockerCode = find(LOCKER_CODE_PATTERN, strCombine);
            if (lockerCode != null) {
                callback.onCodeFound(Settings.CODE_KIND_PICKUP, lockerCode);
                return;
            }
            String hyphenCode = findHyphenPickupCode(strCombine);
            if (hyphenCode != null) {
                callback.onCodeFound(Settings.CODE_KIND_PICKUP, hyphenCode);
                return;
            }
            String strFind2 = find(verifyAfterPattern, strCombine);
            if (strFind2 != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, strFind2);
                return;
            }
            String strFind3 = find(verifyBeforePattern, strCombine);
            if (strFind3 != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, strFind3);
                return;
            }
            String strFind4 = find(verifyEnPattern, strCombine);
            if (strFind4 != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, strFind4);
            } else if (bareOn && (strFindBareCode = findBareCode(strCombine)) != null) {
                callback.onCodeFound(Settings.CODE_KIND_VERIFY, strFindBareCode);
            } else {
                callback.onNoCode();
            }
        } catch (Throwable th) {
            callback.onNoCode();
        }
    }

    private static String combine(String str, String str2) {
        StringBuilder sb = new StringBuilder();
        if (str != null && !str.trim().isEmpty()) {
            sb.append(str.trim());
        }
        if (str2 != null && !str2.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(str2.trim());
        }
        return sb.toString();
    }

    private static String find(Pattern pattern, String str) {
        String strGroup;
        if (pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(str);
        if (!matcher.find() || (strGroup = matcher.group(1)) == null || strGroup.isEmpty()) {
            return null;
        }
        return strGroup;
    }

    private static String findHyphenPickupCode(String str) {
        try {
            java.util.regex.Matcher m = HYPHEN_CODE_PATTERN.matcher(str);
            String[] keywords = {"取件码", "取货码", "提货码", "自提码", "取餐码", "寄存码", "领取码", "取件密码", "提货密码"};
            while (m.find()) {
                String code = m.group();
                int start = m.start();
                String before = str.substring(Math.max(0, start - 20), start);
                for (String kw : keywords) {
                    if (before.contains(kw)) {
                        return code;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String findBareCode(String str) {
        boolean z;
        char cCharAt;
        Matcher matcher = BARE_DIGITS.matcher(str);
        String str2 = null;
        int i = 0;
        while (matcher.find()) {
            String strGroup = matcher.group(1);
            int iStart = matcher.start(1);
            int iEnd = matcher.end(1);
            if (iStart > 0) {
                int i2 = iStart - 1;
                if (str.charAt(i2) == 65509 || str.charAt(i2) == 165) {
                }
            }
            if (iEnd >= str.length() || ((cCharAt = str.charAt(iEnd)) != 24180 && cCharAt != 26376 && cCharAt != 26085 && cCharAt != 20803 && cCharAt != 22359 && cCharAt != 21495)) {
                if (strGroup.length() != 4 || (!strGroup.startsWith("19") && !strGroup.startsWith("20"))) {
                    String strSubstring = str.substring(Math.max(0, iStart - 8), iStart);
                    if (excludeWords != null) {
                        String[] strArr = excludeWords;
                        int length = strArr.length;
                        int i3 = 0;
                        while (true) {
                            if (i3 < length) {
                                if (!strSubstring.contains(strArr[i3])) {
                                    i3++;
                                } else {
                                    z = true;
                                    break;
                                }
                            } else {
                                z = false;
                                break;
                            }
                        }
                    } else {
                        z = false;
                        break;
                    }
                    if (z) {
                        continue;
                    } else {
                        i++;
                        if (i > 1) {
                            return null;
                        }
                        str2 = strGroup;
                    }
                }
            }
        }
        return str2;
    }
}
