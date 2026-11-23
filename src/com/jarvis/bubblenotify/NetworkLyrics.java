package com.jarvis.bubblenotify;

import android.net.Uri;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class NetworkLyrics {
    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");
    private static final int MAX_LINES = 400;
    private static final String TAG = "BubbleNotify: ";
    private static final int TIMEOUT_MS = 5500;
    private static final String USER_AGENT = "BubbleNotify/1.0.3 (LRCLIB)";

    public static final class Line implements Comparable<Line> {
        public final String text;
        public final long timeMs;

        public Line(long j, String str) {
            this.timeMs = j;
            this.text = str;
        }

        @Override // java.lang.Comparable
        public int compareTo(Line line) {
            return Long.compare(this.timeMs, line.timeMs);
        }
    }

    private NetworkLyrics() {
    }

    public static String fetchSyncedLrc(String str, String str2, String str3, long j) {
        String strSyncedFromJson;
        String strSyncedFromJson2;
        if (str == null || str.isEmpty() || str2 == null || str2.isEmpty()) {
            return null;
        }
        try {
            Uri.Builder builderAppendQueryParameter = Uri.parse("https://lrclib.net/api/get").buildUpon().appendQueryParameter("track_name", str).appendQueryParameter("artist_name", str2);
            if (str3 != null && !str3.isEmpty()) {
                builderAppendQueryParameter.appendQueryParameter("album_name", str3);
            }
            if (j > 0) {
                builderAppendQueryParameter.appendQueryParameter("duration", Long.toString(Math.round(j / 1000.0d)));
            }
            String strHttpGet = httpGet(builderAppendQueryParameter.build().toString());
            if (strHttpGet != null && (strSyncedFromJson2 = syncedFromJson(new JSONObject(strHttpGet))) != null) {
                return strSyncedFromJson2;
            }
        } catch (Throwable th) {
            Log.i("BubbleNotify: ", "LRCLIB get failed: " + th.getClass().getSimpleName());
        }
        try {
            Uri.Builder builderAppendQueryParameter2 = Uri.parse("https://lrclib.net/api/search").buildUpon().appendQueryParameter("track_name", str).appendQueryParameter("artist_name", str2);
            if (str3 != null && !str3.isEmpty()) {
                builderAppendQueryParameter2.appendQueryParameter("album_name", str3);
            }
            String strHttpGet2 = httpGet(builderAppendQueryParameter2.build().toString());
            if (strHttpGet2 == null) {
                return null;
            }
            JSONArray jSONArray = new JSONArray(strHttpGet2);
            for (int i = 0; i < jSONArray.length() && i < 20; i++) {
                JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject != null) {
                    String strOptString = jSONObjectOptJSONObject.optString("trackName", "");
                    String strOptString2 = jSONObjectOptJSONObject.optString("artistName", "");
                    if (strOptString.equalsIgnoreCase(str.trim()) && strOptString2.equalsIgnoreCase(str2.trim()) && (strSyncedFromJson = syncedFromJson(jSONObjectOptJSONObject)) != null) {
                        return strSyncedFromJson;
                    }
                }
            }
        } catch (Throwable th2) {
            Log.i("BubbleNotify: ", "LRCLIB search failed: " + th2.getClass().getSimpleName());
        }
        return null;
    }

    private static String syncedFromJson(JSONObject jSONObject) {
        String strOptString = jSONObject.optString("syncedLyrics", null);
        if (strOptString == null) {
            return null;
        }
        String strTrim = strOptString.trim();
        if (strTrim.isEmpty()) {
            return null;
        }
        return strTrim;
    }

    private static String httpGet(String str) {
        HttpURLConnection httpURLConnection;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            try {
                httpURLConnection.setConnectTimeout(TIMEOUT_MS);
                httpURLConnection.setReadTimeout(TIMEOUT_MS);
                httpURLConnection.setRequestProperty("User-Agent", USER_AGENT);
                httpURLConnection.setRequestProperty("Accept", "application/json");
                if (httpURLConnection.getResponseCode() == 200) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    while (true) {
                        String line = bufferedReader.readLine();
                        if (line == null) {
                            break;
                        }
                        sb.append(line).append('\n');
                    }
                    bufferedReader.close();
                    String string = sb.toString();
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                    return string;
                }
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
                return null;
            } catch (Throwable th) {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
                return null;
            }
        } catch (Throwable th2) {
            httpURLConnection = null;
        }
        return null;
    }

    public static List<Line> parseLrc(String str) {
        long j;
        ArrayList arrayList = new ArrayList();
        if (str != null) {
            for (String str2 : str.split("\\r?\\n")) {
                if (arrayList.size() >= MAX_LINES) {
                    break;
                }
                Matcher matcher = LRC_TIMESTAMP.matcher(str2);
                ArrayList arrayList2 = new ArrayList();
                int iMax = 0;
                while (matcher.find()) {
                    long j2 = Long.parseLong(matcher.group(1));
                    long j3 = Long.parseLong(matcher.group(2));
                    String strGroup = matcher.group(3);
                    if (strGroup == null) {
                        j = 0;
                    } else {
                        j = Long.parseLong(strGroup);
                        if (strGroup.length() != 1) {
                            if (strGroup.length() == 2) {
                                j *= 10;
                            }
                        } else {
                            j *= 100;
                        }
                    }
                    arrayList2.add(Long.valueOf((j2 * 60000) + (j3 * 1000) + j));
                    iMax = Math.max(iMax, matcher.end());
                }
                if (!arrayList2.isEmpty()) {
                    String strTrim = str2.substring(iMax).trim();
                    if (!strTrim.isEmpty()) {
                        Iterator it = arrayList2.iterator();
                        while (it.hasNext()) {
                            arrayList.add(new Line(((Long) it.next()).longValue(), strTrim));
                        }
                    }
                }
            }
            Collections.sort(arrayList);
            return arrayList;
        }
        return arrayList;
    }

    public static String lineAt(List<Line> list, long j) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        int size = list.size() - 1;
        int i = 0;
        int i2 = -1;
        while (i <= size) {
            int i3 = (i + size) >>> 1;
            if (list.get(i3).timeMs <= j) {
                i = i3 + 1;
                i2 = i3;
            } else {
                size = i3 - 1;
            }
        }
        if (i2 < 0) {
            return null;
        }
        return list.get(i2).text;
    }
}
