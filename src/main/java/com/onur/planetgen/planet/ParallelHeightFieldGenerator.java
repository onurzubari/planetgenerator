package com.onur.planetgen.planet;

import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.noise.DomainWarpNoise;
import com.onur.planetgen.config.Preset;

/**
 * High-performance parallel height field generator using scanline processing.
 * Uses IntStream.parallel() for multi-threaded terrain synthesis.
 */
public final class ParallelHeightFieldGenerator {
    private ParallelHeightFieldGenerator() {}

    /**
     * Generate height field using parallel scanline processing.
     * Dramatically faster than sequential generation for large resolutions.
     *
     * Performance improvements:
     * - IntStream.parallel() for multi-core utilization
     * - Coordinate caching to eliminate redundant trig
     * - Pre-baked noise generators per thread
     * - Minimal memory allocation during processing
     */
    public static float[][] generateParallel(long seed, SphericalSampler sp, Preset preset) {
        int W = sp.W, H = sp.H;
        float[][] h = new float[H][W];

        // Precompute all spherical coordinates
        System.out.println("Caching coordinates...");
        CoordinateCache coordCache = new CoordinateCache(W, H, sp);

        // Create shared noise generators
        OpenSimplex2 baseNoise = new OpenSimplex2(seed);
        OpenSimplex2 warpXNoise = new OpenSimplex2(seed + 1);
        OpenSimplex2 warpYNoise = new OpenSimplex2(seed + 2);
        OpenSimplex2 warpZNoise = new OpenSimplex2(seed + 3);
        DomainWarpNoise domainWarped = new DomainWarpNoise(baseNoise, warpXNoise, warpYNoise, warpZNoise, 0.08);

        // Parameters
        double seaLevel = preset.seaLevel;
        double continentScale = preset.continentScale;
        double mountainIntensity = preset.mountainIntensity;

        // First pass: generate raw height (parallel)
        System.out.println("Generating terrain (parallel)...");
        float[] minMax = new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};

        // Parallel scanline processing
        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            double cLat = coordCache.cosLat[y];
            double sLat = coordCache.sinLat[y];

            for (int x = 0; x < W; x++) {
                int idx = y * W + x;

                // Get cached normal
                double nxv = coordCache.nx[idx];
                double nyv = coordCache.ny[idx];
                double nzv = coordCache.nz[idx];

                // 1. Continental base
                double continent = fbmLowFreq(domainWarped, nxv, nyv, nzv, continentScale, 2, 2.0, 0.5);

                // 2. Mountains
                double ridged = ridgedFbm(baseNoise, nxv, nyv, nzv, continentScale * 1.5, 4, 2.0, 0.6);
                double mountainMask = Math.max(0.0, continent);
                double mountains = ridged * mountainIntensity * mountainMask;

                // 3. Detail
                double detail = fbmMidFreq(baseNoise, nxv, nyv, nzv, continentScale * 3.0, 3, 2.0, 0.5);
                double slope = estimateSlope(baseNoise, nxv, nyv, nzv, continentScale, 0.01);
                double slopeMask = Math.pow(Math.max(0.0, slope), 2.0);
                double detailModulated = detail * 0.15 * slopeMask;

                // Combine
                double heightValue = 0.6 * continent + 0.3 * mountains + 0.1 * detailModulated;
                h[y][x] = (float) heightValue;

                // Track min/max (not thread-safe but acceptable for normalization)
                synchronized (minMax) {
                    if (heightValue < minMax[0]) minMax[0] = (float) heightValue;
                    if (heightValue > minMax[1]) minMax[1] = (float) heightValue;
                }
            }
        });

        float minH = minMax[0];
        float maxH = minMax[1];

        // Normalize height
        System.out.println("Normalizing height...");
        normalizeHeightParallel(h, minH, maxH);
        applySeaLevel(h, seaLevel);

        return h;
    }

    private static void normalizeHeightParallel(float[][] h, float minH, float maxH) {
        float range = maxH - minH;
        if (range < 1e-6) range = 1.0f;

        int H = h.length;
        int W = h[0].length;
        float finalRange = range;

        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            for (int x = 0; x < W; x++) {
                h[y][x] = 2.0f * ((h[y][x] - minH) / finalRange) - 1.0f;
            }
        });
    }

    private static void applySeaLevel(float[][] h, double seaLevel) {
        int H = h.length;
        int W = h[0].length;

        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            for (int x = 0; x < W; x++) {
                h[y][x] -= (float) seaLevel;
            }
        });
    }

    private static double fbmLowFreq(DomainWarpNoise noise, double x, double y, double z,
                                      double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain);
    }

    private static double fbmMidFreq(OpenSimplex2 noise, double x, double y, double z,
                                      double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain);
    }

    private static double ridgedFbm(OpenSimplex2 noise, double x, double y, double z,
                                     double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            double ridge = 1.0 - Math.abs(n);
            sum += amp * ridge;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain);
    }

    private static double estimateSlope(OpenSimplex2 noise, double x, double y, double z,
                                         double scale, double delta) {
        double h0 = noise.noise3(x * scale, y * scale, z * scale);
        double hx = noise.noise3((x + delta) * scale, y * scale, z * scale);
        double hy = noise.noise3(x * scale, (y + delta) * scale, z * scale);
        double hz = noise.noise3(x * scale, y * scale, (z + delta) * scale);

        double dx = hx - h0;
        double dy = hy - h0;
        double dz = hz - h0;

        return Math.sqrt(dx * dx + dy * dy + dz * dz) / delta;
    }
}
