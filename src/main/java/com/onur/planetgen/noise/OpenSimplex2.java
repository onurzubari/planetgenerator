package com.onur.planetgen.noise;

import java.util.Random;

public final class OpenSimplex2 implements Noise {
    private final short[] perm = new short[256];

    public OpenSimplex2(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 256; i++) {
            perm[i] = p[i];
        }
    }

    @Override
    public double noise3(double x, double y, double z) {
        // TODO: replace with a proper OpenSimplex2 3D implementation
        // Stub: simple value noise-ish hash (not final quality)
        int X = fastFloor(x), Y = fastFloor(y), Z = fastFloor(z);
        double xf = x - X, yf = y - Y, zf = z - Z;
        double u = fade(xf), v = fade(yf), w = fade(zf);

        double n000 = grad(hash(X, Y, Z), xf, yf, zf);
        double n100 = grad(hash(X + 1, Y, Z), xf - 1, yf, zf);
        double n010 = grad(hash(X, Y + 1, Z), xf, yf - 1, zf);
        double n110 = grad(hash(X + 1, Y + 1, Z), xf - 1, yf - 1, zf);
        double n001 = grad(hash(X, Y, Z + 1), xf, yf, zf - 1);
        double n101 = grad(hash(X + 1, Y, Z + 1), xf - 1, yf, zf - 1);
        double n011 = grad(hash(X, Y + 1, Z + 1), xf, yf - 1, zf - 1);
        double n111 = grad(hash(X + 1, Y + 1, Z + 1), xf - 1, yf - 1, zf - 1);

        double x00 = lerp(u, n000, n100), x10 = lerp(u, n010, n110),
               x01 = lerp(u, n001, n101), x11 = lerp(u, n011, n111);
        double y0 = lerp(v, x00, x10), y1 = lerp(v, x01, x11);
        return lerp(w, y0, y1);
    }

    private int hash(int x, int y, int z) {
        return perm[(x + perm[(y + perm[z & 255]) & 255]) & 255] & 255;
    }

    private static int fastFloor(double x) {
        return x >= 0 ? (int) x : ((int) x - 1);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int h, double x, double y, double z) {
        int hh = h & 15;
        double u = hh < 8 ? x : y;
        double v = hh < 4 ? y : ((hh == 12 || hh == 14) ? x : z);
        return (((hh & 1) == 0) ? u : -u) + (((hh & 2) == 0) ? v : -v);
    }
}
