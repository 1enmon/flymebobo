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
    private final Paint iconFillPaint;
    private final List<IconPath> iconPaths;
    private int iconSizeDp;
    private final Paint iconStrokePaint;
    private float rotationDeg;
    private final Paint strokePaint;

    private static final class IconPath {
        final Path path;
        final boolean stroke;
        final float strokeWidth;

        IconPath(Path path, boolean z, float f) {
            this.path = path;
            this.stroke = z;
            this.strokeWidth = f;
        }
    }

    public IconView(Context context) {
        super(context);
        this.iconPaths = new ArrayList();
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
        this.iconPaths = new ArrayList();
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

    private void parseIconXml(String str) {
        int iIndexOf;
        float f;
        int i = 0;
        while (true) {
            try {
                int iIndexOf2 = str.indexOf("<path", i);
                if (iIndexOf2 < 0 || (iIndexOf = str.indexOf("/>", iIndexOf2)) < 0) {
                    break;
                }
                String strSubstring = str.substring(iIndexOf2, iIndexOf);
                String strAttr = attr(strSubstring, "android:pathData");
                if (strAttr != null) {
                    String strAttr2 = attr(strSubstring, "android:fillColor");
                    String strAttr3 = attr(strSubstring, "android:strokeColor");
                    String strAttr4 = attr(strSubstring, "android:strokeWidth");
                    final Path path = new Path();
                    SvgPathParser.parse(strAttr, new SvgPathParser.PathSink() { // from class: com.jarvis.bubblenotify.IconView.1
                        @Override // com.jarvis.bubblenotify.SvgPathParser.PathSink
                        public void moveTo(float f2, float f3) {
                            path.moveTo(f2, f3);
                        }

                        @Override // com.jarvis.bubblenotify.SvgPathParser.PathSink
                        public void lineTo(float f2, float f3) {
                            path.lineTo(f2, f3);
                        }

                        @Override // com.jarvis.bubblenotify.SvgPathParser.PathSink
                        public void cubicTo(float f2, float f3, float f4, float f5, float f6, float f7) {
                            path.cubicTo(f2, f3, f4, f5, f6, f7);
                        }

                        @Override // com.jarvis.bubblenotify.SvgPathParser.PathSink
                        public void quadTo(float f2, float f3, float f4, float f5) {
                            path.quadTo(f2, f3, f4, f5);
                        }

                        @Override // com.jarvis.bubblenotify.SvgPathParser.PathSink
                        public void close() {
                            path.close();
                        }
                    });
                    boolean z = (strAttr3 == null || "#00000000".equalsIgnoreCase(strAttr3) || (strAttr2 != null && !"#00000000".equalsIgnoreCase(strAttr2))) ? false : true;
                    if (strAttr4 != null) {
                        try {
                            f = Float.parseFloat(strAttr4);
                        } catch (Throwable th) {
                            f = 1.0f;
                        }
                    } else {
                        f = 1.0f;
                    }
                    this.iconPaths.add(new IconPath(path, z, f));
                }
                i = iIndexOf + 2;
            } catch (Throwable th2) {
                this.iconPaths.clear();
                return;
            }
        }
    }

    private static String attr(String str, String str2) {
        int iIndexOf;
        String str3 = str2 + "=\"";
        int iIndexOf2 = str.indexOf(str3);
        if (iIndexOf2 < 0 || (iIndexOf = str.indexOf(34, str3.length() + iIndexOf2)) < 0) {
            return null;
        }
        return str.substring(iIndexOf2 + str3.length(), iIndexOf);
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
            float f3 = iDp / VIEWPORT;
            canvas.scale(f3, f3);
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
