package com.jarvis.bubblenotify;

/**
 * Minimal SVG pathData parser (commands M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z,
 * absolute and relative). Pure Java with no Android dependency, so the parsing
 * and arc conversion can be unit-tested on the JVM.
 *
 * <p>IconView feeds the commands into android.graphics.Path through the sink.
 * VectorIcons stores its icons as 24x24 viewport pathData, so the parsed
 * coordinates are in that 24-unit grid and the caller scales them to the
 * on-screen icon size.
 */
public final class SvgPathParser {

    /** Drawing commands produced by the parser. */
    public interface PathSink {
        void moveTo(float x, float y);

        void lineTo(float x, float y);

        void cubicTo(float x1, float y1, float x2, float y2, float x, float y);

        void quadTo(float x1, float y1, float x, float y);

        void close();
    }

    private SvgPathParser() {
    }

    /** Parse {@code pathData} and feed the commands to {@code sink}. */
    public static void parse(String pathData, PathSink sink) {
        if (pathData == null || sink == null) {
            return;
        }
        Tokenizer t = new Tokenizer(pathData);
        char cmd = 0;
        float cx = 0.0f;
        float cy = 0.0f;
        float sx = 0.0f;
        float sy = 0.0f;
        float px = 0.0f;
        float py = 0.0f;
        float qx = 0.0f;
        float qy = 0.0f;
        char prevCmd = 0;
        while (t.hasMore()) {
            t.skipSeparators();
            if (!t.hasMore()) {
                break;
            }
            char c = t.peek();
            if (isCommand(c)) {
                cmd = t.next();
                if (cmd == 'Z' || cmd == 'z') {
                    sink.close();
                    cx = sx;
                    cy = sy;
                    prevCmd = cmd;
                    cmd = 0;
                    continue;
                }
            } else if (cmd == 0) {
                t.next();
                continue;
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    sink.moveTo(x, y);
                    cx = x;
                    cy = y;
                    sx = x;
                    sy = y;
                    cmd = rel ? 'l' : 'L';
                    prevCmd = rel ? 'm' : 'M';
                    break;
                }
                case 'L': {
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    sink.lineTo(x, y);
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'H': {
                    float x = t.nextFloat();
                    if (rel) {
                        x += cx;
                    }
                    sink.lineTo(x, cy);
                    cx = x;
                    prevCmd = cmd;
                    break;
                }
                case 'V': {
                    float y = t.nextFloat();
                    if (rel) {
                        y += cy;
                    }
                    sink.lineTo(cx, y);
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'C': {
                    float x1 = t.nextFloat();
                    float y1 = t.nextFloat();
                    float x2 = t.nextFloat();
                    float y2 = t.nextFloat();
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    if (rel) {
                        x1 += cx;
                        y1 += cy;
                        x2 += cx;
                        y2 += cy;
                        x += cx;
                        y += cy;
                    }
                    sink.cubicTo(x1, y1, x2, y2, x, y);
                    px = x2;
                    py = y2;
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'S': {
                    float x2 = t.nextFloat();
                    float y2 = t.nextFloat();
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    float x1;
                    float y1;
                    if (prevCmd == 'C' || prevCmd == 'c' || prevCmd == 'S' || prevCmd == 's') {
                        x1 = 2.0f * cx - px;
                        y1 = 2.0f * cy - py;
                    } else {
                        x1 = cx;
                        y1 = cy;
                    }
                    if (rel) {
                        x2 += cx;
                        y2 += cy;
                        x += cx;
                        y += cy;
                    }
                    sink.cubicTo(x1, y1, x2, y2, x, y);
                    px = x2;
                    py = y2;
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'Q': {
                    float x1 = t.nextFloat();
                    float y1 = t.nextFloat();
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    if (rel) {
                        x1 += cx;
                        y1 += cy;
                        x += cx;
                        y += cy;
                    }
                    sink.quadTo(x1, y1, x, y);
                    qx = x1;
                    qy = y1;
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'T': {
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    float x1;
                    float y1;
                    if (prevCmd == 'Q' || prevCmd == 'q' || prevCmd == 'T' || prevCmd == 't') {
                        x1 = 2.0f * cx - qx;
                        y1 = 2.0f * cy - qy;
                    } else {
                        x1 = cx;
                        y1 = cy;
                    }
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    sink.quadTo(x1, y1, x, y);
                    qx = x1;
                    qy = y1;
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                case 'A': {
                    float rx = Math.abs(t.nextFloat());
                    float ry = Math.abs(t.nextFloat());
                    float angle = t.nextFloat();
                    boolean largeArc = t.nextFloat() != 0.0f;
                    boolean sweep = t.nextFloat() != 0.0f;
                    float x = t.nextFloat();
                    float y = t.nextFloat();
                    if (rel) {
                        x += cx;
                        y += cy;
                    }
                    arcTo(sink, cx, cy, rx, ry, angle, largeArc, sweep, x, y);
                    cx = x;
                    cy = y;
                    prevCmd = cmd;
                    break;
                }
                default: {
                    t.next();
                    break;
                }
            }
        }
    }

    private static boolean isCommand(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Convert an SVG elliptical arc to cubic Bezier segments (SVG spec F.6.5).
     * The arc is split into segments of at most 90 degrees.
     */
    private static void arcTo(PathSink sink, float x0, float y0, float rx, float ry,
            float angleDeg, boolean largeArc, boolean sweep, float x1, float y1) {
        if (rx == 0.0f || ry == 0.0f || (x0 == x1 && y0 == y1)) {
            if (x0 != x1 || y0 != y1) {
                sink.lineTo(x1, y1);
            }
            return;
        }
        double phi = Math.toRadians(angleDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double dx2 = (x0 - x1) / 2.0;
        double dy2 = (y0 - y1) / 2.0;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;
        double rxSq = rx * rx;
        double rySq = ry * ry;
        double x1pSq = x1p * x1p;
        double y1pSq = y1p * y1p;
        double lambda = x1pSq / rxSq + y1pSq / rySq;
        if (lambda > 1.0) {
            double s = Math.sqrt(lambda);
            rx *= (float) s;
            ry *= (float) s;
            rxSq = rx * rx;
            rySq = ry * ry;
        }
        double sign = (largeArc == sweep) ? -1.0 : 1.0;
        double num = rxSq * rySq - rxSq * y1pSq - rySq * x1pSq;
        double den = rxSq * y1pSq + rySq * x1pSq;
        double coef = sign * Math.sqrt(Math.max(0.0, num / den));
        double cxp = coef * rx * y1p / ry;
        double cyp = -coef * ry * x1p / rx;
        double cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0;
        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;
        double theta1 = angleBetween(1.0, 0.0, ux, uy);
        double dTheta = angleBetween(ux, uy, vx, vy);
        if (!sweep && dTheta > 0.0) {
            dTheta -= 360.0;
        } else if (sweep && dTheta < 0.0) {
            dTheta += 360.0;
        }
        int segments = (int) Math.ceil(Math.abs(dTheta) / 90.0);
        if (segments < 1) {
            segments = 1;
        }
        double delta = Math.toRadians(dTheta) / segments;
        double t = Math.toRadians(theta1);
        for (int i = 0; i < segments; i++) {
            double t2 = t + delta;
            double cosT = Math.cos(t);
            double sinT = Math.sin(t);
            double cosT2 = Math.cos(t2);
            double sinT2 = Math.sin(t2);
            double k = 4.0 / 3.0 * Math.tan((t2 - t) / 4.0);
            double xStart = cx + rx * cosT * cosPhi - ry * sinT * sinPhi;
            double yStart = cy + rx * cosT * sinPhi + ry * sinT * cosPhi;
            double xEnd = cx + rx * cosT2 * cosPhi - ry * sinT2 * sinPhi;
            double yEnd = cy + rx * cosT2 * sinPhi + ry * sinT2 * cosPhi;
            double dx1 = -rx * sinT;
            double dy1 = ry * cosT;
            double dx2b = -rx * sinT2;
            double dy2b = ry * cosT2;
            double c1x = xStart + k * (dx1 * cosPhi - dy1 * sinPhi);
            double c1y = yStart + k * (dx1 * sinPhi + dy1 * cosPhi);
            double c2x = xEnd - k * (dx2b * cosPhi - dy2b * sinPhi);
            double c2y = yEnd - k * (dx2b * sinPhi + dy2b * cosPhi);
            sink.cubicTo((float) c1x, (float) c1y, (float) c2x, (float) c2y,
                    (float) xEnd, (float) yEnd);
            t = t2;
        }
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double ang = Math.acos(Math.max(-1.0, Math.min(1.0, dot / len)));
        if (ux * vy - uy * vx < 0.0) {
            ang = -ang;
        }
        return Math.toDegrees(ang);
    }

    /** Tokenizer for pathData numbers and commands. */
    private static final class Tokenizer {
        private final String s;
        private int pos;

        Tokenizer(String s) {
            this.s = s;
            this.pos = 0;
        }

        boolean hasMore() {
            return pos < s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        char next() {
            return s.charAt(pos++);
        }

        void skipSeparators() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        float nextFloat() {
            skipSeparators();
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                pos++;
            }
            boolean seenDot = false;
            boolean seenDigit = false;
            boolean seenExp = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    seenDigit = true;
                    pos++;
                } else if (c == '.' && !seenDot && !seenExp) {
                    seenDot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && seenDigit && !seenExp) {
                    seenExp = true;
                    pos++;
                    if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("Expected number at " + start + " in: " + s);
            }
            return Float.parseFloat(s.substring(start, pos));
        }
    }
}
