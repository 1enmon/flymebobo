package com.jarvis.bubblenotify;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/* JADX INFO: loaded from: classes.dex */
public final class VectorIcons {
    public static final String PHONE = vector(fillPath("M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81 a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27 a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"));
    public static final String SPEAKER = vector(fillPath("M3 9v6h4l5 4.5v-15L7 9H3Z") + strokePath("M15.5 8.7a4.5 4.5 0 0 1 0 6.6M18.2 6a8.5 8.5 0 0 1 0 12", 1.9f));
    public static final String PREV = vector(fillPath("M19 20 9 12l10-8v16Z") + fillPath("M6 5h0.4a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z"));
    public static final String NEXT = vector(fillPath("m5 4 10 8-10 8V4Z") + fillPath("M17.6 5h0.4a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-0.4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z"));
    public static final String PAUSE = vector(fillPath("M7.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.2A1.2 1.2 0 0 1 6 17.8V6.2A1.2 1.2 0 0 1 7.2 5Z") + fillPath("M15.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.6a1.2 1.2 0 0 1-1.2-1.2V6.2a1.2 1.2 0 0 1 1.2-1.2Z"));
    public static final String PLAY = vector(fillPath("M8 5.5v13l11-6.5-11-6.5Z"));
    public static final String NAV_RIGHT = vector(strokePath("M6 20v-5a4 4 0 0 1 4-4h7", 2.4f) + strokePath("m12.5 6 5 5-5 5", 2.4f));
    public static final String NAV_LEFT = vector(strokePath("M18 20v-5a4 4 0 0 0-4-4H7", 2.4f) + strokePath("m11.5 6-5 5 5 5", 2.4f));
    public static final String NAV_STRAIGHT = vector(strokePath("M12 20V6.5", 2.4f) + strokePath("m6.5 12 5.5-6 5.5 6", 2.4f));
    public static final String NAV_ARRIVE = vector(strokePath("M12 21.5s-6.8-5.6-6.8-10.8a6.8 6.8 0 0 1 13.6 0c0 5.2-6.8 10.8-6.8 10.8Z", 2.2f) + fillPath("M14.4 10.6a2.4 2.4 0 1 1-4.8 0a2.4 2.4 0 1 1 4.8 0Z"));
    public static final String NAV_UTURN = vector(strokePath("M7 20V11a5 5 0 0 1 10 0v6", 2.4f) + strokePath("m13.5 13.5 3.5 3.5 3.5-3.5", 2.4f));
    // fixed31: roundabout arrow, ported from Flyme Island ou0 (dir 4).
    // ou0 draws addArc(5.2,5.2,18.8,18.8,-30,300) + an arrowhead at the
    // top: arc from (17.9,8.6) to (12,5.2), large-arc, sweep clockwise,
    // then M7.8 1.6 L12 5.8 L7.8 10. SvgPathParser converts the A command
    // to cubic segments, so IconView can draw it like the other arrows.
    public static final String NAV_ROUNDABOUT = vector(strokePath("M17.9 8.6A6.8 6.8 0 1 1 12 5.2", 2.4f) + strokePath("M7.8 1.6 12 5.8 7.8 10", 2.4f));
    public static final String MEITUAN_CLOCHE = vector(strokePath("M4 16.5a8 8 0 0 1 16 0", 2.0f) + strokePath("M2.5 20h19", 2.0f) + fillPath("M13.2 6.8a1.2 1.2 0 1 1-2.4 0a1.2 1.2 0 1 1 2.4 0Z"));
    public static final String GAODE_ARROW = vector(fillPath("M12 2.2 19.2 21 12 16.8 4.8 21Z"));
    private static final Map<String, Drawable.ConstantState> CACHE = new HashMap();

    private VectorIcons() {
    }

    private static String vector(String str) {
        return "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\">" + str + "</vector>";
    }

    private static String fillPath(String str) {
        return "<path android:fillColor=\"#FFFFFF\" android:pathData=\"" + str + "\"/>";
    }

    private static String strokePath(String str, float f) {
        return "<path android:fillColor=\"#00000000\" android:strokeColor=\"#FFFFFF\" android:strokeWidth=\"" + f + "\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" android:pathData=\"" + str + "\"/>";
    }

    public static Drawable load(Context context, String str) {
        Drawable.ConstantState constantState;
        if (context == null || str == null) {
            return null;
        }
        synchronized (CACHE) {
            constantState = CACHE.get(str);
        }
        if (constantState != null) {
            try {
                Drawable drawableNewDrawable = constantState.newDrawable(context.getResources());
                if (drawableNewDrawable != null) {
                    return drawableNewDrawable;
                }
            } catch (Throwable th) {
            }
        }
        try {
            XmlPullParserFactory xmlPullParserFactoryNewInstance = XmlPullParserFactory.newInstance();
            xmlPullParserFactoryNewInstance.setNamespaceAware(true);
            XmlPullParser xmlPullParserNewPullParser = xmlPullParserFactoryNewInstance.newPullParser();
            xmlPullParserNewPullParser.setInput(new StringReader(str));
            Drawable drawableCreateFromXml = Drawable.createFromXml(context.getResources(), xmlPullParserNewPullParser);
            if (drawableCreateFromXml != null && drawableCreateFromXml.getConstantState() != null) {
                synchronized (CACHE) {
                    CACHE.put(str, drawableCreateFromXml.getConstantState());
                }
            }
            return drawableCreateFromXml;
        } catch (Throwable th2) {
            Log.e(Settings.TAG, "VectorIcons.load failed", th2);
            return null;
        }
    }
}
