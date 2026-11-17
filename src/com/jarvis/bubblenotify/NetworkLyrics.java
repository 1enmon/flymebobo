package com.jarvis.bubblenotify;

import android.net.Uri;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * fixed18: network lyrics via LRCLIB (https://lrclib.net), the same source
 * Flyme Island uses (its NetEase/Kugou direct providers are disabled).
 * Lyrics no longer come from notifications at all — notification lyric
 * lines were unreliable (different apps hide them in different extras)
 * and they polluted the song title, which kept popping the docked player
 * out on every lyric refresh.
 *
 * Flow: fetch the synced LRC for (title, artist, album, duration) from
 * lrclib.net, parse "[mm:ss.xx]text" lines into timed lines, then
 * lineAt(positionMs) gives the line playing right now — that is the
 * scrolling playback.
 */
public final class NetworkLyrics {
    private static final String TAG = Settings.TAG;
    private static final int TIMEOUT_MS = 5500;
    private static final String USER_AGENT = "BubbleNotify/1.0.3 (LRCLIB)";
    private static final int MAX_LINES = 400;

    /** One timed lyric line. */
    public static final class Line implements Comparable<Line> {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        @Override
        public int compareTo(Line other) {
            return Long.compare(this.timeMs, other.timeMs);
        }
    }

    private NetworkLyrics() {
    }

    /**
     * Fetch the synced LRC for a song. Tries the exact-match endpoint
     * first, then the search endpoint and picks the first result whose
     * title and artist match. Returns the raw LRC text, or null.
     * Must be called off the main thread.
     */
    public static String fetchSyncedLrc(String title, String artist, String album, long durationMs) {
        if (title == null || title.isEmpty() || artist == null || artist.isEmpty()) {
            return null;
        }
        try {
            Uri.Builder b = Uri.parse("https://lrclib.net/api/get").buildUpon()
                    .appendQueryParameter("track_name", title)
                    .appendQueryParameter("artist_name", artist);
            if (album != null && !album.isEmpty()) {
                b.appendQueryParameter("album_name", album);
            }
            if (durationMs > 0) {
                b.appendQueryParameter("duration", Long.toString(Math.round(durationMs / 1000.0)));
            }
            String body = httpGet(b.build().toString());
            if (body != null) {
                String lrc = syncedFromJson(new JSONObject(body));
                if (lrc != null) {
                    return lrc;
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "LRCLIB get failed: " + th.getClass().getSimpleName());
        }
        // Fallback: search, take the first title+artist match with synced lyrics.
        try {
            Uri.Builder b = Uri.parse("https://lrclib.net/api/search").buildUpon()
                    .appendQueryParameter("track_name", title)
                    .appendQueryParameter("artist_name", artist);
            if (album != null && !album.isEmpty()) {
                b.appendQueryParameter("album_name", album);
            }
            String body = httpGet(b.build().toString());
            if (body == null) {
                return null;
            }
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length() && i < 20; i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String t = obj.optString("trackName", "");
                String a = obj.optString("artistName", "");
                if (t.equalsIgnoreCase(title.trim()) && a.equalsIgnoreCase(artist.trim())) {
                    String lrc = syncedFromJson(obj);
                    if (lrc != null) {
                        return lrc;
                    }
                }
            }
        } catch (Throwable th) {
            Log.i(TAG, "LRCLIB search failed: " + th.getClass().getSimpleName());
        }
        return null;
    }

    private static String syncedFromJson(JSONObject obj) {
        String s = obj.optString("syncedLyrics", null);
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (Throwable th) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static final Pattern LRC_TIMESTAMP =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");

    /** Parse LRC text into timed lines, sorted by time. */
    public static List<Line> parseLrc(String lrc) {
        List<Line> lines = new ArrayList<>();
        if (lrc == null) {
            return lines;
        }
        for (String raw : lrc.split("\\r?\\n")) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            Matcher m = LRC_TIMESTAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (m.find()) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = 0;
                if (frac != null) {
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) {
                        ms *= 100;
                    } else if (frac.length() == 2) {
                        ms *= 10;
                    }
                }
                times.add(min * 60000L + sec * 1000L + ms);
                textStart = Math.max(textStart, m.end());
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = raw.substring(textStart).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (Long t : times) {
                lines.add(new Line(t, text));
            }
        }
        Collections.sort(lines);
        return lines;
    }

    /** The lyric line playing at positionMs, or null. Binary search. */
    public static String lineAt(List<Line> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found < 0 ? null : lines.get(found).text;
    }
}
