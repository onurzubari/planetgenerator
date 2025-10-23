package com.onur.planetgen.noise;

public final class OpenSimplex2 implements Noise {
    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    public OpenSimplex2(long seed) {
        // Initialize permutation table with xorshift-based PRNG for better quality
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Fisher-Yates shuffle with xorshift PRNG
        long state = seed;
        for (int i = 255; i > 0; i--) {
            // XORshift for better distribution than basic Random
            state ^= state << 13;
            state ^= state >> 7;
            state ^= state << 17;
            int j = (int) ((state & 0x7FFFFFFFL) % (i + 1));
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }

        // Duplicate permutation table for wraparound
        for (int i = 0; i < 256; i++) {
            perm[i] = p[i];
            perm[i + 256] = p[i];
            permMod12[i] = perm[i] % 12;
            permMod12[i + 256] = perm[i + 256] % 12;
        }
    }

    @Override
    public double noise3(double x, double y, double z) {
        int xi = fastFloor(x);
        int yi = fastFloor(y);
        int zi = fastFloor(z);

        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int n000 = hash(xi, yi, zi);
        int n100 = hash(xi + 1, yi, zi);
        int n010 = hash(xi, yi + 1, zi);
        int n110 = hash(xi + 1, yi + 1, zi);
        int n001 = hash(xi, yi, zi + 1);
        int n101 = hash(xi + 1, yi, zi + 1);
        int n011 = hash(xi, yi + 1, zi + 1);
        int n111 = hash(xi + 1, yi + 1, zi + 1);

        double g000 = grad(n000, xf, yf, zf);
        double g100 = grad(n100, xf - 1, yf, zf);
        double g010 = grad(n010, xf, yf - 1, zf);
        double g110 = grad(n110, xf - 1, yf - 1, zf);
        double g001 = grad(n001, xf, yf, zf - 1);
        double g101 = grad(n101, xf - 1, yf, zf - 1);
        double g011 = grad(n011, xf, yf - 1, zf - 1);
        double g111 = grad(n111, xf - 1, yf - 1, zf - 1);

        double x0 = lerp(u, g000, g100);
        double x1 = lerp(u, g010, g110);
        double x2 = lerp(u, g001, g101);
        double x3 = lerp(u, g011, g111);

        double y0 = lerp(v, x0, x1);
        double y1 = lerp(v, x2, x3);

        return lerp(w, y0, y1);
    }

    private int hash(int x, int y, int z) {
        int h = perm[(perm[(perm[x & 255] + y) & 255] + z) & 255];
        return h;
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

    private static double grad(int hash, double x, double y, double z) {
        int h = hash % 12;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return (((h & 1) == 0) ? u : -u) + (((h & 2) == 0) ? v : -v);
    }
}
