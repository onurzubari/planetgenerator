package com.onur.planetgen.render;

import com.onur.planetgen.planet.BiomeClassifier;
import com.onur.planetgen.planet.ClimateModel;
import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.erosion.FlowField;
import com.onur.planetgen.hydrology.RiverDetector;
import com.onur.planetgen.hydrology.LakeDetector;
import com.onur.planetgen.config.Preset;

public final class AlbedoRenderer {
    private AlbedoRenderer() {}

    /**
     * Render albedo map from height field.
     * Uses biome-based coloring with height banding and subtle shading.
     */
    public static int[][] render(float[][] height) {
        return renderWithHydrology(height, null);
    }

    /**
     * Render albedo map with rivers and lakes.
     * Computes flow field and detects hydrological features.
     */
    public static int[][] renderWithHydrology(float[][] height, Preset preset) {
        int H = height.length, W = height[0].length;
        int[][] argb = new int[H][W];
        SphericalSampler sampler = new SphericalSampler(W, H);

        // Compute flow field and detect water features
        System.out.println("Computing flow field for river detection...");
        long flowStart = System.currentTimeMillis();
        FlowField flowField = FlowField.compute(height);
        float[][] rivers = RiverDetector.detectRivers(flowField, 0.4);
        rivers = RiverDetector.smoothRivers(rivers, 1);
        long flowTime = System.currentTimeMillis() - flowStart;
        System.out.println(String.format("Hydrology computation: %.1fs", flowTime / 1000.0));

        // Detect lakes
        double lakeThreshold = (preset != null) ? preset.seaLevel : -0.05;
        float[][] lakes = LakeDetector.detectLakes(height, lakeThreshold);

        for (int y = 0; y < H; y++) {
            double lat = sampler.lat(y);
            for (int x = 0; x < W; x++) {
                float h = height[y][x];
                ClimateModel.Sample climate = ClimateModel.sample(x, y, h, lat);
                BiomeClassifier.Biome biome = BiomeClassifier.classify(climate.temp(), climate.moist());

                // Base color from biome
                int[] baseColor = getBiomeColor(biome);

                // Height banding: ocean -> beach -> grass -> rock -> snow
                int[] finalColor = applyHeightBanding(baseColor, h, biome);

                // Add subtle ambient occlusion from slope
                float slope = estimateSlope(height, x, y);
                finalColor = applyAmbientOcclusion(finalColor, slope);

                // Blend lakes (water color)
                if (lakes[y][x] > 0.5f) {
                    int[] waterColor = new int[]{30, 80, 180};
                    blendColor(finalColor, waterColor, 0.8);
                }

                // Blend rivers (darker water lines)
                if (rivers[y][x] > 0.1f) {
                    int[] riverColor = new int[]{20, 60, 140};
                    blendColor(finalColor, riverColor, Math.min(1.0, rivers[y][x] * 0.6));
                }

                // Add micro-variation via per-texel jitter
                finalColor = addMicroVariation(finalColor, x, y);

                argb[y][x] = argb(255, finalColor[0], finalColor[1], finalColor[2]);
            }
        }
        return argb;
    }

    private static int[] getBiomeColor(BiomeClassifier.Biome biome) {
        return switch (biome) {
            case DESERT -> new int[]{206, 185, 140};
            case FOREST -> new int[]{72, 110, 78};
            case JUNGLE -> new int[]{62, 96, 68};
            case STEPPE -> new int[]{180, 165, 120};
            case TUNDRA -> new int[]{170, 175, 180};
            case TAIGA -> new int[]{74, 102, 74};
            case ICE -> new int[]{230, 235, 240};
            case SWAMP -> new int[]{80, 92, 70};
            case RAINFOREST -> new int[]{58, 90, 64};
        };
    }

    private static int[] applyHeightBanding(int[] baseColor, float h, BiomeClassifier.Biome biome) {
        int[] color = baseColor.clone();

        if (h < -0.2) {
            // Deep ocean
            blendColor(color, new int[]{12, 50, 120}, 0.7);
        } else if (h < -0.05) {
            // Shallow ocean
            blendColor(color, new int[]{25, 100, 150}, 0.5);
        } else if (h < 0.0) {
            // Beach/sand
            blendColor(color, new int[]{220, 200, 100}, 0.6);
        } else if (h > 0.5) {
            // Snow line
            blendColor(color, new int[]{240, 240, 255}, 0.7);
        } else if (h > 0.3) {
            // High altitude - rocky
            blendColor(color, new int[]{150, 140, 130}, 0.4);
        }

        return color;
    }

    private static int[] applyAmbientOcclusion(int[] color, float slope) {
        // Subtle AO: reduce brightness on steep slopes (2-5%)
        double aoFactor = 0.97 + 0.03 * Math.max(0.0, 1.0 - slope);
        color[0] = (int) (color[0] * aoFactor);
        color[1] = (int) (color[1] * aoFactor);
        color[2] = (int) (color[2] * aoFactor);
        return color;
    }

    private static int[] addMicroVariation(int[] color, int x, int y) {
        // Pseudo-random per-texel jitter for micro-variation
        long hash = ((long) x * 73856093L) ^ ((long) y * 19349663L);
        int jitter = (int) ((hash >>> 16) & 0xFF) - 128; // [-128, 127]
        jitter = (int) (jitter * 0.02); // Scale down to ±2.5

        color[0] = clampInt(color[0] + jitter, 0, 255);
        color[1] = clampInt(color[1] + jitter, 0, 255);
        color[2] = clampInt(color[2] + jitter, 0, 255);
        return color;
    }

    private static void blendColor(int[] dst, int[] src, double weight) {
        for (int i = 0; i < 3; i++) {
            dst[i] = (int) (dst[i] * (1.0 - weight) + src[i] * weight);
        }
    }

    private static float estimateSlope(float[][] height, int x, int y) {
        int H = height.length, W = height[0].length;
        int xW = (x - 1 + W) % W;
        int xE = (x + 1) % W;
        int yN = Math.max(0, y - 1);
        int yS = Math.min(H - 1, y + 1);

        float dhdx = (height[y][xE] - height[y][xW]) * 0.5f;
        float dhdy = (height[yS][x] - height[yN][x]) * 0.5f;

        return (float) Math.sqrt(dhdx * dhdx + dhdy * dhdy);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
