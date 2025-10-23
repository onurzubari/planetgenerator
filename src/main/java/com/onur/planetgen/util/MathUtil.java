package com.onur.planetgen.util;

public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
