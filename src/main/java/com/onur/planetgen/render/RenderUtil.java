package com.onur.planetgen.render;

/**
 * Utility helpers for converting procedural float fields into packed ARGB textures.
 * Centralises clamping and packing logic so renderers can focus on domain logic.
 */
public final class RenderUtil {
    private RenderUtil() {}

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    public static int argb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    public static int clamp255(float value) {
        int v = Math.round(value);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    /**
     * Convert a float field in [0,1] to an 8-bit grayscale texture.
     */
    public static int[][] toGray8(float[][] data) {
        int h = data.length;
        int w = data[0].length;
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            float[] row = data[y];
            int[] dst = out[y];
            for (int x = 0; x < w; x++) {
                float v = clamp01(row[x]);
                dst[x] = (int) (v * 255f);
            }
        }
        return out;
    }

    /**
     * Pack three float channels (0..1) into RGB with full alpha.
     *
     * @param r channel for red
     * @param g channel for green
     * @param b channel for blue
     */
    public static int[][] packToRgb(float[][] r, float[][] g, float[][] b) {
        int h = r.length;
        int w = r[0].length;
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            float[] rowR = r[y];
            float[] rowG = g[y];
            float[] rowB = b[y];
            int[] dst = out[y];
            for (int x = 0; x < w; x++) {
                int rr = clamp255(rowR[x] * 255f);
                int gg = clamp255(rowG[x] * 255f);
                int bb = clamp255(rowB[x] * 255f);
                dst[x] = argb(255, rr, gg, bb);
            }
        }
        return out;
    }

    /**
     * Create an ARGB mask by applying a float alpha channel to a constant colour.
     */
    public static int[][] colorWithAlpha(float[][] alpha, int r, int g, int b) {
        int h = alpha.length;
        int w = alpha[0].length;
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            float[] row = alpha[y];
            int[] dst = out[y];
            for (int x = 0; x < w; x++) {
                int a = clamp255(clamp01(row[x]) * 255f);
                dst[x] = argb(a, r, g, b);
            }
        }
        return out;
    }
}
