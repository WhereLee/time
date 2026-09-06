package com.reason.common.utils;

import java.math.BigDecimal;

public class BigDecimalUtil {
    public static BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        String str = StringUtils.toStringNotNull(obj);
        if (StringUtils.isEmpty(str)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(str);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static BigDecimal multiply(Object... objs) {
        if (objs == null || objs.length <= 0) {
            return BigDecimal.ZERO;
        }
        int length = objs.length;
        BigDecimal[] bigs = new BigDecimal[length];
        for (int i = 0; i < length; i++) {
            Object d = objs[i];
            if (d == null || "".equals(d)) {
                return BigDecimal.ZERO;
            }
            bigs[i] = toBigDecimal(d);
        }
        return multiply(bigs);
    }

    public static BigDecimal multiply(BigDecimal... bigs) {
        if (bigs == null || bigs.length <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal a = BigDecimal.ZERO;
        for (int i = 0, length = bigs.length; i < length; i++) {
            BigDecimal d = bigs[i];
            if (d == null || BigDecimal.ZERO.compareTo(d) == 0) {
                return BigDecimal.ZERO;
            }
            a = a.multiply(d);
        }
        return a;
    }

    public static BigDecimal divide(Object div, Object... objDivs) {
        return divide(div, 4, objDivs);
    }

    public static BigDecimal divide(Object div, int roundingMode, Object... objDivs) {
        BigDecimal divBig;
        if (objDivs == null || objDivs.length <= 0 || BigDecimal.ZERO.compareTo(divBig = toBigDecimal(div)) == 0) {
            return BigDecimal.ZERO;
        }
        int length = objDivs.length;
        BigDecimal[] bigDivs = new BigDecimal[length];
        for (int i = 0; i < length; i++) {
            Object d = objDivs[i];
            if (d == null || "".equals(d)) {
                return BigDecimal.ZERO;
            }
            bigDivs[i] = toBigDecimal(d);
        }
        return divide(divBig, roundingMode, bigDivs);
    }

    public static BigDecimal divide(BigDecimal div, BigDecimal... divs) {
        return divide(div, 4, divs);
    }

    public static BigDecimal divide(BigDecimal div, int roundingMode, BigDecimal... divs) {
        if (div == null || divs == null || divs.length <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal a = div;
        for (int i = 0, length = divs.length; i < length; i++) {
            BigDecimal d = divs[i];
            if (d == null || BigDecimal.ZERO.compareTo(d) == 0) {
                return BigDecimal.ZERO;
            }
            a = a.divide(d, roundingMode);
        }
        return a;
    }

    public static BigDecimal scale(BigDecimal bigDecimal, int scale) {
        return scale(bigDecimal, scale, 4);
    }

    public static BigDecimal scale(BigDecimal bigDecimal, int scale, int roundingMode) {
        if (bigDecimal == null) {
            return BigDecimal.ZERO.setScale(scale, roundingMode);
        }
        return bigDecimal.setScale(scale, roundingMode);
    }

    public static String toString(Object obj) {
        return toString(obj, 2, 4);
    }

    public static String toString(Object obj, int scale) {
        return toString(obj, scale, 4);
    }

    public static String toString(Object obj, int scale, int roundingMode) {
        if (obj == null || "".equals(obj)) {
            return BigDecimal.ZERO.setScale(scale, roundingMode).toPlainString();
        }
        return toBigDecimal(obj).setScale(scale, roundingMode).toPlainString();
    }

    public static String toString(BigDecimal bigDecimal) {
        return toString(bigDecimal, 2, 4);
    }

    public static String toString(BigDecimal bigDecimal, int scale) {
        return toString(bigDecimal, scale, 4);
    }

    public static String toString(BigDecimal bigDecimal, int scale, int roundingMode) {
        if (bigDecimal == null) {
            return BigDecimal.ZERO.setScale(scale, roundingMode).toPlainString();
        }
        return bigDecimal.setScale(scale, roundingMode).toPlainString();
    }

    public static String formatNumber(Object num, NumberEnum type) {
        if (num == null) {
            return "";
        }
        if (type.equals(NumberEnum.YU)) {
            return StringUtils.toStringNotNull(num);
        }
        BigDecimal numBig = toBigDecimal(num);
        if (NumberEnum.JS == type) {
            return numBig.setScale(2, 4).toPlainString();
        }
        if (NumberEnum.DF == type) {
            return numBig.setScale(4, 4).toPlainString();
        }
        if (NumberEnum.BL == type) {
            if (numBig.compareTo(BigDecimal.valueOf(0.01D)) < 0) {
                return "<0.01%";
            }
            return numBig.setScale(2, 4).toPlainString() + "%";
        }
        return StringUtils.toStringNotNull(num);
    }

    public enum NumberEnum {
        YU, JS, BL, DF;
    }

    public static void main(String[] args) {
        BigDecimal b;
        double a = 1000.12D;
        long as = System.nanoTime();
        System.out.println(toBigDecimal(Double.valueOf(a)));
        System.out.println("toBigDecimal        =" + (System.nanoTime() - as));
        long abs = System.nanoTime();
        BigDecimal ab = null;
        if (ab instanceof BigDecimal)
            System.out.println("ab is BigDecimal");
        System.out.println("instanceof1         =" + (System.nanoTime() - abs));
        long asu = System.nanoTime();
        System.out.println(StringUtils.toStringNotNull(Double.valueOf(a)));
        System.out.println("toStringNotNull1    =" + (System.nanoTime() - asu));
        long ans = System.nanoTime();
        try {
            b = new BigDecimal(a);
        } catch (Exception e) {
            b = BigDecimal.ZERO;
        }
        System.out.println(b);
        System.out.println("new BigDecimal      =" + (System.nanoTime() - ans));
        System.out.println("all                 =" + (System.nanoTime() - abs));
    }
}
