package com.jarvis.bubblenotify;

/* JADX INFO: loaded from: classes.dex */
public final class SvgPathParser {

    public interface PathSink {
        void close();

        void cubicTo(float f, float f2, float f3, float f4, float f5, float f6);

        void lineTo(float f, float f2);

        void moveTo(float f, float f2);

        void quadTo(float f, float f2, float f3, float f4);
    }

    private SvgPathParser() {
    }

    /* JADX WARN: Code duplicated, block: B:101:0x021a  */
    /* JADX WARN: Code duplicated, block: B:104:0x0225  */
    /* JADX WARN: Code duplicated, block: B:115:0x007b A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:116:0x008f A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:117:0x00cd A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:118:0x0122 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:119:0x014e A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:120:0x017d A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:121:0x0199 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:122:0x01ad A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:123:0x01f1 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:124:0x0074 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:29:0x0081  */
    /* JADX WARN: Code duplicated, block: B:42:0x00ac  */
    /* JADX WARN: Code duplicated, block: B:44:0x00b5  */
    /* JADX WARN: Code duplicated, block: B:57:0x00f2  */
    /* JADX WARN: Code duplicated, block: B:59:0x00fb  */
    /* JADX WARN: Code duplicated, block: B:64:0x0134  */
    /* JADX WARN: Code duplicated, block: B:69:0x0158  */
    /* JADX WARN: Code duplicated, block: B:70:0x015d  */
    /* JADX WARN: Code duplicated, block: B:73:0x0168  */
    /* JADX WARN: Code duplicated, block: B:74:0x016b  */
    /* JADX WARN: Code duplicated, block: B:76:0x016f  */
    /* JADX WARN: Code duplicated, block: B:77:0x0172  */
    /* JADX WARN: Code duplicated, block: B:81:0x0187  */
    /* JADX WARN: Code duplicated, block: B:86:0x019f  */
    /* JADX WARN: Code duplicated, block: B:91:0x01c7  */
    /* JADX WARN: Code duplicated, block: B:96:0x020e  */
    /* JADX WARN: Code duplicated, block: B:97:0x0210  */
    public static void parse(String str, PathSink pathSink) {
        char c;
        boolean zIsLowerCase;
        boolean z;
        float fNextFloat;
        float fNextFloat2;
        float fNextFloat3;
        float fNextFloat4;
        float fNextFloat5;
        float fNextFloat6;
        float fNextFloat7;
        float fNextFloat8;
        float fNextFloat9;
        float fNextFloat10;
        float fNextFloat11;
        float fNextFloat12;
        float fNextFloat13;
        char c2;
        char c3;
        float fNextFloat14;
        float fNextFloat15;
        float fNextFloat16;
        float fNextFloat17;
        float fNextFloat18;
        float fNextFloat19;
        float fNextFloat20;
        float fNextFloat21;
        float f;
        float f2;
        float fNextFloat22;
        float fNextFloat23;
        float f3;
        float f4;
        float fNextFloat24;
        if (str != null && pathSink != null) {
            Tokenizer tokenizer = new Tokenizer(str);
            char c4 = 0;
            char c5 = 0;
            float f5 = 0.0f;
            float f6 = 0.0f;
            float f7 = 0.0f;
            float f8 = 0.0f;
            float f9 = 0.0f;
            float f10 = 0.0f;
            float f11 = 0.0f;
            float f12 = 0.0f;
            while (tokenizer.hasMore()) {
                tokenizer.skipSeparators();
                if (tokenizer.hasMore()) {
                    if (isCommand(tokenizer.peek())) {
                        char next = tokenizer.next();
                        if (next == 'Z' || next == 'z') {
                            pathSink.close();
                            c5 = next;
                            c4 = 0;
                            f5 = f7;
                            f6 = f8;
                        } else {
                            c = next;
                            zIsLowerCase = Character.isLowerCase(c);
                            switch (Character.toUpperCase(c)) {
                                case 'A':
                                    float fAbs = Math.abs(tokenizer.nextFloat());
                                    float fAbs2 = Math.abs(tokenizer.nextFloat());
                                    float fNextFloat25 = tokenizer.nextFloat();
                                    if (tokenizer.nextFloat() != 0.0f) {
                                        z = true;
                                    } else {
                                        z = false;
                                    }
                                    boolean z2 = tokenizer.nextFloat() != 0.0f;
                                    fNextFloat = tokenizer.nextFloat();
                                    fNextFloat2 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat += f5;
                                        fNextFloat2 += f6;
                                    }
                                    float f13 = fNextFloat;
                                    float f14 = fNextFloat2;
                                    arcTo(pathSink, f5, f6, fAbs, fAbs2, fNextFloat25, z, z2, f13, f14);
                                    c4 = c;
                                    c5 = c4;
                                    f5 = f13;
                                    f6 = f14;
                                    break;
                                case 'C':
                                    fNextFloat3 = tokenizer.nextFloat();
                                    fNextFloat4 = tokenizer.nextFloat();
                                    fNextFloat5 = tokenizer.nextFloat();
                                    fNextFloat6 = tokenizer.nextFloat();
                                    fNextFloat7 = tokenizer.nextFloat();
                                    fNextFloat8 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat3 += f5;
                                        fNextFloat4 += f6;
                                        fNextFloat5 += f5;
                                        fNextFloat6 += f6;
                                        fNextFloat7 += f5;
                                        fNextFloat8 += f6;
                                    }
                                    float f15 = fNextFloat5;
                                    float f16 = fNextFloat6;
                                    pathSink.cubicTo(fNextFloat3, fNextFloat4, f15, f16, fNextFloat7, fNextFloat8);
                                    f5 = fNextFloat7;
                                    f6 = fNextFloat8;
                                    f10 = f16;
                                    c4 = c;
                                    c5 = c4;
                                    f9 = f15;
                                    break;
                                case 'H':
                                    fNextFloat9 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat9 += f5;
                                    }
                                    f5 = fNextFloat9;
                                    pathSink.lineTo(f5, f6);
                                    c4 = c;
                                    c5 = c4;
                                    break;
                                case 'L':
                                    fNextFloat10 = tokenizer.nextFloat();
                                    fNextFloat11 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat10 += f5;
                                        fNextFloat11 += f6;
                                    }
                                    f5 = fNextFloat10;
                                    f6 = fNextFloat11;
                                    pathSink.lineTo(f5, f6);
                                    c4 = c;
                                    c5 = c4;
                                    break;
                                case 'M':
                                    fNextFloat12 = tokenizer.nextFloat();
                                    fNextFloat13 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        f5 = fNextFloat12;
                                        f6 = fNextFloat13;
                                    } else {
                                        f5 = fNextFloat12 + f5;
                                        f6 = fNextFloat13 + f6;
                                    }
                                    pathSink.moveTo(f5, f6);
                                    if (zIsLowerCase) {
                                        c2 = 'l';
                                    } else {
                                        c2 = 'L';
                                    }
                                    if (zIsLowerCase) {
                                        c3 = 'm';
                                    } else {
                                        c3 = 'M';
                                    }
                                    f7 = f5;
                                    f8 = f6;
                                    char c6 = c2;
                                    c5 = c3;
                                    c4 = c6;
                                    break;
                                case 'Q':
                                    fNextFloat14 = tokenizer.nextFloat();
                                    fNextFloat15 = tokenizer.nextFloat();
                                    fNextFloat16 = tokenizer.nextFloat();
                                    fNextFloat17 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat14 += f5;
                                        fNextFloat15 += f6;
                                        fNextFloat16 += f5;
                                        fNextFloat17 += f6;
                                    }
                                    f5 = fNextFloat16;
                                    f6 = fNextFloat17;
                                    pathSink.quadTo(fNextFloat14, fNextFloat15, f5, f6);
                                    f11 = fNextFloat14;
                                    f12 = fNextFloat15;
                                    c4 = c;
                                    c5 = c4;
                                    break;
                                case 'S':
                                    fNextFloat18 = tokenizer.nextFloat();
                                    fNextFloat19 = tokenizer.nextFloat();
                                    fNextFloat20 = tokenizer.nextFloat();
                                    fNextFloat21 = tokenizer.nextFloat();
                                    if (c5 != 'C' || c5 == 'c' || c5 == 'S' || c5 == 's') {
                                        f = (f5 * 2.0f) - f9;
                                        f2 = (2.0f * f6) - f10;
                                    } else {
                                        f = f5;
                                        f2 = f6;
                                    }
                                    if (zIsLowerCase) {
                                        fNextFloat18 += f5;
                                        fNextFloat19 += f6;
                                        fNextFloat20 += f5;
                                        fNextFloat21 += f6;
                                    }
                                    float f17 = fNextFloat18;
                                    float f18 = fNextFloat19;
                                    pathSink.cubicTo(f, f2, f17, f18, fNextFloat20, fNextFloat21);
                                    f5 = fNextFloat20;
                                    f6 = fNextFloat21;
                                    f10 = f18;
                                    c4 = c;
                                    c5 = c4;
                                    f9 = f17;
                                    break;
                                case Settings.SWIPE_MIN_DP /* 84 */:
                                    fNextFloat22 = tokenizer.nextFloat();
                                    fNextFloat23 = tokenizer.nextFloat();
                                    if (c5 != 'Q' || c5 == 'q' || c5 == 'T' || c5 == 't') {
                                        f3 = (f5 * 2.0f) - f11;
                                        f4 = (2.0f * f6) - f12;
                                    } else {
                                        f3 = f5;
                                        f4 = f6;
                                    }
                                    if (zIsLowerCase) {
                                        fNextFloat22 += f5;
                                        fNextFloat23 += f6;
                                    }
                                    f5 = fNextFloat22;
                                    f6 = fNextFloat23;
                                    pathSink.quadTo(f3, f4, f5, f6);
                                    f11 = f3;
                                    f12 = f4;
                                    c4 = c;
                                    c5 = c4;
                                    break;
                                case 'V':
                                    fNextFloat24 = tokenizer.nextFloat();
                                    if (zIsLowerCase) {
                                        fNextFloat24 += f6;
                                    }
                                    f6 = fNextFloat24;
                                    pathSink.lineTo(f5, f6);
                                    c4 = c;
                                    c5 = c4;
                                    break;
                                default:
                                    tokenizer.next();
                                    c4 = c;
                                    break;
                            }
                        }
                    } else if (c4 != 0) {
                        c = c4;
                        zIsLowerCase = Character.isLowerCase(c);
                        switch (Character.toUpperCase(c)) {
                            case 'A':
                                float fAbs3 = Math.abs(tokenizer.nextFloat());
                                float fAbs4 = Math.abs(tokenizer.nextFloat());
                                float fNextFloat26 = tokenizer.nextFloat();
                                if (tokenizer.nextFloat() != 0.0f) {
                                    z = true;
                                } else {
                                    z = false;
                                }
                                boolean z2 = tokenizer.nextFloat() != 0.0f;
                                fNextFloat = tokenizer.nextFloat();
                                fNextFloat2 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat += f5;
                                    fNextFloat2 += f6;
                                }
                                float f19 = fNextFloat;
                                float f110 = fNextFloat2;
                                arcTo(pathSink, f5, f6, fAbs3, fAbs4, fNextFloat26, z, z2, f19, f110);
                                c4 = c;
                                c5 = c4;
                                f5 = f19;
                                f6 = f110;
                                break;
                            case 'C':
                                fNextFloat3 = tokenizer.nextFloat();
                                fNextFloat4 = tokenizer.nextFloat();
                                fNextFloat5 = tokenizer.nextFloat();
                                fNextFloat6 = tokenizer.nextFloat();
                                fNextFloat7 = tokenizer.nextFloat();
                                fNextFloat8 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat3 += f5;
                                    fNextFloat4 += f6;
                                    fNextFloat5 += f5;
                                    fNextFloat6 += f6;
                                    fNextFloat7 += f5;
                                    fNextFloat8 += f6;
                                }
                                float f111 = fNextFloat5;
                                float f112 = fNextFloat6;
                                pathSink.cubicTo(fNextFloat3, fNextFloat4, f111, f112, fNextFloat7, fNextFloat8);
                                f5 = fNextFloat7;
                                f6 = fNextFloat8;
                                f10 = f112;
                                c4 = c;
                                c5 = c4;
                                f9 = f111;
                                break;
                            case 'H':
                                fNextFloat9 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat9 += f5;
                                }
                                f5 = fNextFloat9;
                                pathSink.lineTo(f5, f6);
                                c4 = c;
                                c5 = c4;
                                break;
                            case 'L':
                                fNextFloat10 = tokenizer.nextFloat();
                                fNextFloat11 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat10 += f5;
                                    fNextFloat11 += f6;
                                }
                                f5 = fNextFloat10;
                                f6 = fNextFloat11;
                                pathSink.lineTo(f5, f6);
                                c4 = c;
                                c5 = c4;
                                break;
                            case 'M':
                                fNextFloat12 = tokenizer.nextFloat();
                                fNextFloat13 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    f5 = fNextFloat12;
                                    f6 = fNextFloat13;
                                } else {
                                    f5 = fNextFloat12 + f5;
                                    f6 = fNextFloat13 + f6;
                                }
                                pathSink.moveTo(f5, f6);
                                if (zIsLowerCase) {
                                    c2 = 'l';
                                } else {
                                    c2 = 'L';
                                }
                                if (zIsLowerCase) {
                                    c3 = 'm';
                                } else {
                                    c3 = 'M';
                                }
                                f7 = f5;
                                f8 = f6;
                                char c7 = c2;
                                c5 = c3;
                                c4 = c7;
                                break;
                            case 'Q':
                                fNextFloat14 = tokenizer.nextFloat();
                                fNextFloat15 = tokenizer.nextFloat();
                                fNextFloat16 = tokenizer.nextFloat();
                                fNextFloat17 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat14 += f5;
                                    fNextFloat15 += f6;
                                    fNextFloat16 += f5;
                                    fNextFloat17 += f6;
                                }
                                f5 = fNextFloat16;
                                f6 = fNextFloat17;
                                pathSink.quadTo(fNextFloat14, fNextFloat15, f5, f6);
                                f11 = fNextFloat14;
                                f12 = fNextFloat15;
                                c4 = c;
                                c5 = c4;
                                break;
                            case 'S':
                                fNextFloat18 = tokenizer.nextFloat();
                                fNextFloat19 = tokenizer.nextFloat();
                                fNextFloat20 = tokenizer.nextFloat();
                                fNextFloat21 = tokenizer.nextFloat();
                                if (c5 != 'C') {
                                    f = (f5 * 2.0f) - f9;
                                    f2 = (2.0f * f6) - f10;
                                } else {
                                    f = (f5 * 2.0f) - f9;
                                    f2 = (2.0f * f6) - f10;
                                }
                                if (zIsLowerCase) {
                                    fNextFloat18 += f5;
                                    fNextFloat19 += f6;
                                    fNextFloat20 += f5;
                                    fNextFloat21 += f6;
                                }
                                float f113 = fNextFloat18;
                                float f114 = fNextFloat19;
                                pathSink.cubicTo(f, f2, f113, f114, fNextFloat20, fNextFloat21);
                                f5 = fNextFloat20;
                                f6 = fNextFloat21;
                                f10 = f114;
                                c4 = c;
                                c5 = c4;
                                f9 = f113;
                                break;
                            case Settings.SWIPE_MIN_DP /* 84 */:
                                fNextFloat22 = tokenizer.nextFloat();
                                fNextFloat23 = tokenizer.nextFloat();
                                if (c5 != 'Q') {
                                    f3 = (f5 * 2.0f) - f11;
                                    f4 = (2.0f * f6) - f12;
                                } else {
                                    f3 = (f5 * 2.0f) - f11;
                                    f4 = (2.0f * f6) - f12;
                                }
                                if (zIsLowerCase) {
                                    fNextFloat22 += f5;
                                    fNextFloat23 += f6;
                                }
                                f5 = fNextFloat22;
                                f6 = fNextFloat23;
                                pathSink.quadTo(f3, f4, f5, f6);
                                f11 = f3;
                                f12 = f4;
                                c4 = c;
                                c5 = c4;
                                break;
                            case 'V':
                                fNextFloat24 = tokenizer.nextFloat();
                                if (zIsLowerCase) {
                                    fNextFloat24 += f6;
                                }
                                f6 = fNextFloat24;
                                pathSink.lineTo(f5, f6);
                                c4 = c;
                                c5 = c4;
                                break;
                            default:
                                tokenizer.next();
                                c4 = c;
                                break;
                        }
                    } else {
                        tokenizer.next();
                    }
                } else {
                    return;
                }
            }
        }
    }

    private static boolean isCommand(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static void arcTo(PathSink pathSink, float f, float f2, float f3, float f4, float f5, boolean z, boolean z2, float f6, float f7) {
        double d;
        double d2;
        float f8;
        float f9;
        if (f3 != 0.0f && f4 != 0.0f) {
            if (f != f6 || f2 != f7) {
                double radians = Math.toRadians(((double) f5) % 360.0d);
                double dCos = Math.cos(radians);
                double dSin = Math.sin(radians);
                double d3 = ((double) (f - f6)) / 2.0d;
                double d4 = ((double) (f2 - f7)) / 2.0d;
                double d5 = (dCos * d3) + (dSin * d4);
                double d6 = ((-dSin) * d3) + (d4 * dCos);
                double d7 = f3 * f3;
                double d8 = f4 * f4;
                double d9 = d5 * d5;
                double d10 = d6 * d6;
                double d11 = (d9 / d7) + (d10 / d8);
                if (d11 <= 1.0d) {
                    d = d8;
                    d2 = d7;
                    f8 = f3;
                    f9 = f4;
                } else {
                    float fSqrt = (float) Math.sqrt(d11);
                    float f10 = f3 * fSqrt;
                    float f11 = fSqrt * f4;
                    d2 = f10 * f10;
                    d = f11 * f11;
                    f8 = f10;
                    f9 = f11;
                }
                double d12 = d2 * d;
                double d13 = d2 * d10;
                double d14 = d * d9;
                double dSqrt = Math.sqrt(Math.max(0.0d, ((d12 - d13) - d14) / (d13 + d14))) * (z == z2 ? -1.0d : 1.0d);
                double d15 = f8;
                float f12 = f8;
                double d16 = f9;
                double d17 = ((dSqrt * d15) * d6) / d16;
                double d18 = (((-dSqrt) * d16) * d5) / d15;
                float f13 = f12;
                double d19 = ((dCos * d17) - (dSin * d18)) + (((double) (f + f6)) / 2.0d);
                double d20 = (dSin * d17) + (dCos * d18) + (((double) (f2 + f7)) / 2.0d);
                double d21 = (d5 - d17) / d15;
                double d22 = (d6 - d18) / d16;
                double d23 = ((-d6) - d18) / d16;
                double dAngleBetween = angleBetween(1.0d, 0.0d, d21, d22);
                double dAngleBetween2 = angleBetween(d21, d22, ((-d5) - d17) / d15, d23);
                if (!z2 && dAngleBetween2 > 0.0d) {
                    dAngleBetween2 -= 360.0d;
                } else if (z2 && dAngleBetween2 < 0.0d) {
                    dAngleBetween2 += 360.0d;
                }
                int iCeil = (int) Math.ceil(Math.abs(dAngleBetween2) / 90.0d);
                if (iCeil < 1) {
                    iCeil = 1;
                }
                double radians2 = Math.toRadians(dAngleBetween2) / ((double) iCeil);
                double radians3 = Math.toRadians(dAngleBetween);
                int i = 0;
                while (i < iCeil) {
                    double d24 = radians3 + radians2;
                    double dCos2 = Math.cos(radians3);
                    double dSin2 = Math.sin(radians3);
                    double dCos3 = Math.cos(d24);
                    double dSin3 = Math.sin(d24);
                    double dTan = Math.tan((d24 - radians3) / 4.0d) * 1.3333333333333333d;
                    double d25 = d15 * dCos2;
                    double d26 = d16 * dSin2;
                    double d27 = (d19 + (d25 * dCos)) - (d26 * dSin);
                    double d28 = d20 + (d25 * dSin) + (d26 * dCos);
                    double d29 = d15 * dCos3;
                    double d30 = d16 * dSin3;
                    double d31 = d15;
                    double d32 = (d19 + (d29 * dCos)) - (d30 * dSin);
                    double d33 = radians2;
                    double d34 = d20 + (d29 * dSin) + (d30 * dCos);
                    int i2 = iCeil;
                    double d35 = d20;
                    float f14 = f13;
                    double d36 = -f14;
                    double d37 = dSin2 * d36;
                    double d38 = dCos2 * d16;
                    double d39 = d36 * dSin3;
                    double d40 = d16 * dCos3;
                    pathSink.cubicTo((float) (d27 + (((d37 * dCos) - (d38 * dSin)) * dTan)), (float) (d28 + (((d37 * dSin) + (d38 * dCos)) * dTan)), (float) (d32 - (((d39 * dCos) - (d40 * dSin)) * dTan)), (float) (d34 - (dTan * ((d39 * dSin) + (d40 * dCos)))), (float) d32, (float) d34);
                    i++;
                    radians2 = d33;
                    d15 = d31;
                    d20 = d35;
                    radians3 = d24;
                    f13 = f14;
                    iCeil = i2;
                }
                return;
            }
        }
        if (f != f6 || f2 != f7) {
            pathSink.lineTo(f6, f7);
        }
    }

    private static double angleBetween(double d, double d2, double d3, double d4) {
        double dAcos = Math.acos(Math.max(-1.0d, Math.min(1.0d, ((d * d3) + (d2 * d4)) / Math.sqrt(((d * d) + (d2 * d2)) * ((d3 * d3) + (d4 * d4))))));
        if ((d * d4) - (d2 * d3) < 0.0d) {
            dAcos = -dAcos;
        }
        return Math.toDegrees(dAcos);
    }

    private static final class Tokenizer {
        private int pos = 0;
        private final String s;

        Tokenizer(String str) {
            this.s = str;
        }

        boolean hasMore() {
            return this.pos < this.s.length();
        }

        char peek() {
            return this.s.charAt(this.pos);
        }

        char next() {
            String str = this.s;
            int i = this.pos;
            this.pos = i + 1;
            return str.charAt(i);
        }

        void skipSeparators() {
            while (this.pos < this.s.length()) {
                char cCharAt = this.s.charAt(this.pos);
                if (cCharAt == ' ' || cCharAt == '\t' || cCharAt == '\n' || cCharAt == '\r' || cCharAt == ',') {
                    this.pos++;
                } else {
                    return;
                }
            }
        }

        float nextFloat() {
            skipSeparators();
            int i = this.pos;
            if (this.pos < this.s.length() && (this.s.charAt(this.pos) == '+' || this.s.charAt(this.pos) == '-')) {
                this.pos++;
            }
            boolean z = false;
            boolean z2 = false;
            boolean z3 = false;
            while (this.pos < this.s.length()) {
                char cCharAt = this.s.charAt(this.pos);
                if (cCharAt >= '0' && cCharAt <= '9') {
                    this.pos++;
                    z3 = true;
                } else if (cCharAt == '.' && !z && !z2) {
                    this.pos++;
                    z = true;
                } else {
                    if ((cCharAt != 'e' && cCharAt != 'E') || !z3 || z2) {
                        break;
                    }
                    this.pos++;
                    if (this.pos < this.s.length() && (this.s.charAt(this.pos) == '+' || this.s.charAt(this.pos) == '-')) {
                        this.pos++;
                    }
                    z2 = true;
                }
            }
            if (i == this.pos) {
                throw new IllegalArgumentException("Expected number at " + i + " in: " + this.s);
            }
            return Float.parseFloat(this.s.substring(i, this.pos));
        }
    }
}
