package com.jarvis.bubblenotify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class IconView extends View {
    public static final float CORNER_CIRCLE = 999.0f;
    /** VectorIcons uses a 24x24 viewport. */
    private static final float VIEWPORT = 24.0f;
    private int bgColor;
    private float bgCornerDp;
    private final Paint bgPaint;
    private final RectF bgRect;
    private int bgStrokeColor;
    private float bgStrokeWidthDp;
    private final float density;
    private Drawable icon;
    private int iconColor;
    private int iconSizeDp;
    private float rotationDeg;
    private final Paint strokePaint;
    /** Path-drawn icon, parsed from the vector XML. Drawn with Canvas when present. */
    private final List<IconPath> iconPaths = new ArrayList<>();
    private final Paint iconFillPaint;
    private final Paint iconStrokePaint;

    private static final class IconPath {
        final Path path;
        final boolean stroke;
        final float strokeWidth;

        IconPath(Path path, boolean stroke, float strokeWidth) {
            this.path = path;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
        }
    }

    public IconView(Context context) {
        super(context);
        this.iconColor = -1;
        this.bgColor = 0;
        this.bgCornerDp = 999.0f;
        this.bgStrokeColor = 0;
        this.bgStrokeWidthDp = 0.0f;
        this.rotationDeg = 0.0f;
        this.iconSizeDp = 0;
        this.bgPaint = new Paint(1);
        this.strokePaint = new Paint(1);
        this.bgRect = new RectF();
        this.density = context.getResources().getDisplayMetrics().density;
        this.iconFillPaint = new Paint(1);
        this.iconStrokePaint = new Paint(1);
    }

    public IconView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.iconColor = -1;
        this.bgColor = 0;
        this.bgCornerDp = 999.0f;
        this.bgStrokeColor = 0;
        this.bgStrokeWidthDp = 0.0f;
        this.rotationDeg = 0.0f;
        this.iconSizeDp = 0;
        this.bgPaint = new Paint(1);
        this.strokePaint = new Paint(1);
        this.bgRect = new RectF();
        this.density = context.getResources().getDisplayMetrics().density;
        this.iconFillPaint = new Paint(1);
        this.iconStrokePaint = new Paint(1);
    }

    public void setIconXml(String str) {
        this.iconPaths.clear();
        this.icon = null;
        if (str != null) {
            parseIconXml(str);
        }
        if (this.iconPaths.isEmpty()) {
            Drawable drawableLoad = VectorIcons.load(getContext(), str);
            this.icon = drawableLoad;
            if (drawableLoad != null) {
                drawableLoad.setTint(this.iconColor);
            }
        }
        invalidate();
    }

    /**
     * Extract the &lt;path&gt; elements from the vector XML string and parse
     * their pathData into Canvas paths. This replaces the fragile
     * Drawable.createFromXml() inflation, which can silently return null in
     * SystemUI and leaves the button as a bare coloured circle.
     */
    private void parseIconXml(String xml) {
        try {
            int idx = 0;
            while (true) {
                int start = xml.indexOf("<path", idx);
                if (start < 0) {
                    break;
                }
                int end = xml.indexOf("/>", start);
                if (end < 0) {
                    break;
                }
                String tag = xml.substring(start, end);
                String pathData = attr(tag, "android:pathData");
                if (pathData != null) {
                    String fill = attr(tag, "android:fillColor");
                    String stroke = attr(tag, "android:strokeColor");
                    String sw = attr(tag, "android:strokeWidth");
                    final Path path = new Path();
                    SvgPathParser.parse(pathData, new SvgPathParser.PathSink() {
                        @Override
                        public void moveTo(float x, float y) {
                            path.moveTo(x, y);
                        }

                        @Override
                        public void lineTo(float x, float y) {
                            path.lineTo(x, y);
                        }

                        @Override
                        public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
                            path.cubicTo(x1, y1, x2, y2, x, y);
                        }

                        @Override
                        public void quadTo(float x1, float y1, float x, float y) {
                            path.quadTo(x1, y1, x, y);
                        }

                        @Override
                        public void close() {
                            path.close();
                        }
                    });
                    boolean isStroke = stroke != null && !"#00000000".equalsIgnoreCase(stroke)
                            && (fill == null || "#00000000".equalsIgnoreCase(fill));
                    float strokeWidth = 1.0f;
                    if (sw != null) {
                        try {
                            strokeWidth = Float.parseFloat(sw);
                        } catch (Throwable ignored) {
                        }
                    }
                    this.iconPaths.add(new IconPath(path, isStroke, strokeWidth));
                }
                idx = end + 2;
            }
        } catch (Throwable ignored) {
            this.iconPaths.clear();
        }
    }

    private static String attr(String tag, String name) {
        String key = name + "=\"";
        int i = tag.indexOf(key);
        if (i < 0) {
            return null;
        }
        int j = tag.indexOf('"', i + key.length());
        if (j < 0) {
            return null;
        }
        return tag.substring(i + key.length(), j);
    }

    public void setIconColor(int i) {
        this.iconColor = i;
        if (this.icon != null) {
            this.icon.setTint(i);
        }
        invalidate();
    }

    public void setIconBackground(int i, float f) {
        this.bgColor = i;
        this.bgCornerDp = f;
        invalidate();
    }

    public void setIconBackgroundStroke(int i, float f) {
        this.bgStrokeColor = i;
        this.bgStrokeWidthDp = f;
        invalidate();
    }

    public void setIconRotation(float f) {
        this.rotationDeg = f;
        invalidate();
    }

    public void setIconSizeDp(int i) {
        this.iconSizeDp = i;
        invalidate();
    }

    private int dp(float f) {
        return Math.round(f * this.density);
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (this.bgColor != 0) {
            this.bgPaint.setColor(this.bgColor);
            this.bgRect.set(0.0f, 0.0f, width, height);
            float fMin = this.bgCornerDp >= 999.0f ? Math.min(width, height) / 2.0f : dp(this.bgCornerDp);
            canvas.drawRoundRect(this.bgRect, fMin, fMin, this.bgPaint);
            if (this.bgStrokeColor != 0 && this.bgStrokeWidthDp > 0.0f) {
                float fDp = dp(this.bgStrokeWidthDp);
                this.strokePaint.setColor(this.bgStrokeColor);
                this.strokePaint.setStyle(Paint.Style.STROKE);
                this.strokePaint.setStrokeWidth(fDp);
                float f = fDp / 2.0f;
                this.bgRect.inset(f, f);
                float f2 = fMin - f;
                canvas.drawRoundRect(this.bgRect, Math.max(0.0f, f2), Math.max(0.0f, f2), this.strokePaint);
            }
        }
        if (this.iconPaths.isEmpty() && this.icon == null) {
            return;
        }
        int iDp = this.iconSizeDp > 0 ? dp(this.iconSizeDp) : Math.min(width, height);
        int i = (width - iDp) / 2;
        int i2 = (height - iDp) / 2;
        if (this.rotationDeg != 0.0f) {
            canvas.save();
            canvas.rotate(this.rotationDeg, width / 2.0f, height / 2.0f);
        }
        if (!this.iconPaths.isEmpty()) {
            canvas.save();
            canvas.translate(i, i2);
            float scale = iDp / VIEWPORT;
            canvas.scale(scale, scale);
            for (IconPath iconPath : this.iconPaths) {
                if (iconPath.stroke) {
                    this.iconStrokePaint.setColor(this.iconColor);
                    this.iconStrokePaint.setStyle(Paint.Style.STROKE);
                    this.iconStrokePaint.setStrokeWidth(iconPath.strokeWidth);
                    this.iconStrokePaint.setStrokeCap(Paint.Cap.ROUND);
                    this.iconStrokePaint.setStrokeJoin(Paint.Join.ROUND);
                    canvas.drawPath(iconPath.path, this.iconStrokePaint);
                } else {
                    this.iconFillPaint.setColor(this.iconColor);
                    this.iconFillPaint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(iconPath.path, this.iconFillPaint);
                }
            }
            canvas.restore();
        } else if (this.icon != null) {
            this.icon.setBounds(i, i2, i + iDp, iDp + i2);
            this.icon.draw(canvas);
        }
        if (this.rotationDeg != 0.0f) {
            canvas.restore();
        }
    }
}
