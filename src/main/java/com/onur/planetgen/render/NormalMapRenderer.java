package com.onur.planetgen.render;

public final class NormalMapRenderer {
    private NormalMapRenderer() {}

    public static int[][] render(float[][] h) {
        int H = h.length, W = h[0].length;
        int[][] out = new int[H][W];
        for (int y = 0; y < H; y++) {
            double v = (y + 0.5) / (double) H;
            double lat = Math.PI / 2.0 - Math.PI * v;
            double cos = Math.max(1e-6, Math.cos(lat));
            int yN = Math.max(0, y - 1), yS = Math.min(H - 1, y + 1);
            for (int x = 0; x < W; x++) {
                int xW = (x - 1 + W) % W, xE = (x + 1) % W;
                double dhdx = (h[y][xE] - h[y][xW]) * 0.5 / cos;
                double dhdy = (h[yS][x] - h[yN][x]) * 0.5;
                double nx = -dhdx, ny = -dhdy, nz = 1.0;
                double L = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= L;
                ny /= L;
                nz /= L;
                int r = (int) Math.round((nx * 0.5 + 0.5) * 255);
                int g = (int) Math.round((ny * 0.5 + 0.5) * 255);
                int b = (int) Math.round((nz * 0.5 + 0.5) * 255);
                out[y][x] = ((255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        }
        return out;
    }
}
