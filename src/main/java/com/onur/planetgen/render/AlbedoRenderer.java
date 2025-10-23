package com.onur.planetgen.render;

import com.onur.planetgen.planet.BiomeClassifier;
import com.onur.planetgen.planet.ClimateModel;

public final class AlbedoRenderer {
    private AlbedoRenderer() {}

    public static int[][] render(float[][] height /* + biome/climate later */) {
        int H = height.length, W = height[0].length;
        int[][] argb = new int[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float h = height[y][x];
                ClimateModel.Sample c = ClimateModel.sample(x, y, h, 0); // TODO: pass real latitude
                BiomeClassifier.Biome b = BiomeClassifier.classify(c.temp(), c.moist());
                int rgb = switch (b) {
                    case DESERT -> argb(255, 206, 185, 140);
                    case FOREST -> argb(255, 72, 110, 78);
                    case JUNGLE -> argb(255, 62, 96, 68);
                    case STEPPE -> argb(255, 180, 165, 120);
                    case TUNDRA -> argb(255, 170, 175, 180);
                    case TAIGA -> argb(255, 74, 102, 74);
                    case ICE -> argb(255, 230, 235, 240);
                    case SWAMP -> argb(255, 80, 92, 70);
                    case RAINFOREST -> argb(255, 58, 90, 64);
                };
                argb[y][x] = rgb;
            }
        }
        return argb;
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
