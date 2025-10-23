package com.onur.planetgen.render;

import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.config.Preset;

/**
 * Renders emissive texture for night lights or lava.
 * Creates lighting effects for various planet types.
 */
public final class EmissiveRenderer {
    private EmissiveRenderer() {}

    /**
     * Render emissive map based on preset type.
     *
     * @param height height field (normalized [-1, 1])
     * @param preset rendering configuration
     * @param seed random seed for procedural patterns
     * @return ARGB texture with emissive contribution
     */
    public static int[][] render(float[][] height, Preset preset, long seed) {
        if (!preset.enableEmissive || "none".equals(preset.emissiveType)) {
            // Return transparent black
            int H = height.length, W = height[0].length;
            int[][] empty = new int[H][W];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    empty[y][x] = 0; // Fully transparent
                }
            }
            return empty;
        }

        if ("night_lights".equals(preset.emissiveType)) {
            return renderNightLights(height, preset, seed);
        } else if ("lava".equals(preset.emissiveType)) {
            return renderLava(height, preset, seed);
        }

        return new int[height.length][height[0].length];
    }

    /**
     * Render night lights (city lights for habitable planets).
     * Uses proximity to water and river features as proxy for civilization.
     */
    private static int[][] renderNightLights(float[][] height, Preset preset, long seed) {
        int H = height.length, W = height[0].length;
        int[][] argb = new int[H][W];
        OpenSimplex2 noise = new OpenSimplex2(seed);

        double seaLevel = preset.seaLevel;
        double intensity = preset.emissiveIntensity;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float h = height[y][x];

                // Cities exist in temperate coastal regions (just below land, near water)
                // Use heuristic: height between sea level and elevation threshold
                double distanceFromWater = Math.abs(h - (seaLevel + 0.1));
                double coastlineProximity = Math.exp(-distanceFromWater * distanceFromWater * 5.0);

                // Add noise for clustered cities
                double pattern = noise.noise3(x * 0.01, y * 0.01, seed * 0.0001);
                pattern = (pattern + 1.0) * 0.5; // Normalize to [0, 1]

                // City lights are warm (yellow/orange)
                double emission = coastlineProximity * pattern * intensity;
                emission = Math.pow(emission, 0.4); // Soft glow falloff

                if (emission > 0.01) {
                    int a = (int) (emission * 255);
                    int r = (int) (255 * Math.min(1.0, 1.0 + 0.2 * (pattern - 0.5))); // Warm tint
                    int g = (int) (200 * Math.min(1.0, 1.0 + 0.1 * (pattern - 0.5)));
                    int b = (int) (100 * Math.min(1.0, 1.0 - 0.2 * (pattern - 0.5)));

                    argb[y][x] = argb(a, r, g, b);
                } else {
                    argb[y][x] = 0;
                }
            }
        }

        return argb;
    }

    /**
     * Render lava emission (for volcanic planets).
     * Uses high-temperature ridged noise to simulate lava flows.
     */
    private static int[][] renderLava(float[][] height, Preset preset, long seed) {
        int H = height.length, W = height[0].length;
        int[][] argb = new int[H][W];
        OpenSimplex2 noise = new OpenSimplex2(seed);

        double threshold = preset.emissiveThreshold;
        double intensity = preset.emissiveIntensity;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float h = height[y][x];

                // Lava exists in high elevations (volcanic peaks)
                // Use ridged noise pattern for fractal lava flows
                double n = noise.noise3(x * 0.02, y * 0.02, seed * 0.0001);
                double ridged = 1.0 - Math.abs(n);

                // Lava concentrated in high peaks
                double elevationFactor = Math.max(0.0, h - threshold) / (1.0 - threshold);
                double emission = elevationFactor * ridged * intensity;

                if (emission > 0.05) {
                    // Hot colors: orange to white
                    int a = (int) (emission * 255);

                    // Temperature color: dark red → orange → yellow → white
                    double temperature = emission; // 0 = dark, 1 = bright

                    int r = (int) (Math.min(255, 200 + 55 * temperature));
                    int g = (int) (Math.min(255, 100 * temperature));
                    int b = (int) (Math.min(255, 50 * temperature * temperature)); // Less blue at high temp

                    argb[y][x] = argb(a, r, g, b);
                } else {
                    argb[y][x] = 0;
                }
            }
        }

        return argb;
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }
}
