package com.onur.planetgen.render;

import com.onur.planetgen.atmosphere.CloudField;
import com.onur.planetgen.atmosphere.MultiLayerCloudField;

public final class CloudRenderer {
    private CloudRenderer() {}

    public static int[][] render(CloudField f) {
        int H = f.alpha.length, W = f.alpha[0].length;
        int[][] argb = new int[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int a = (int) Math.round(Math.max(0, Math.min(1, f.alpha[y][x])) * 255);
                int r = 230, g = 240, b = 255;
                argb[y][x] = ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        }
        return argb;
    }

    public static int[][] render(MultiLayerCloudField f) {
        int H = f.alpha.length, W = f.alpha[0].length;
        int[][] argb = new int[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int a = (int) Math.round(Math.max(0, Math.min(1, f.alpha[y][x])) * 255);
                int r = 230, g = 240, b = 255;
                argb[y][x] = ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        }
        return argb;
    }
}
