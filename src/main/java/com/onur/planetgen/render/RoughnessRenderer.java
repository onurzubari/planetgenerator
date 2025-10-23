package com.onur.planetgen.render;

public final class RoughnessRenderer {
    private RoughnessRenderer() {}

    public static int[][] render(float[][] h) {
        int H = h.length, W = h[0].length;
        int[][] g = new int[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                g[y][x] = (int) (Math.min(255, Math.max(0, 127 + 64 * h[y][x])));
            }
        }
        return g;
    }
}
