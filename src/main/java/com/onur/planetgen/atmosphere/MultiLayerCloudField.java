package com.onur.planetgen.atmosphere;

import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.planet.CoordinateCache;
import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.noise.DomainWarpNoise;
import com.onur.planetgen.config.Preset;

/**
 * Multi-layer cloud system for photorealistic cloud morphology.
 * Generates stratocumulus (low), altocumulus (mid), and cirrus (high) layers.
 */
public final class MultiLayerCloudField {
    public final float[][] alpha;

    private static final double STRATO_SCALE = 1.5;   // Low clouds - large features
    private static final double ALTO_SCALE = 4.0;     // Mid clouds - medium features
    private static final double CIRRUS_SCALE = 8.0;   // High clouds - fine detail

    private MultiLayerCloudField(int W, int H) {
        this.alpha = new float[H][W];
    }

    /**
     * Generate multi-layer clouds using parallel processing.
     */
    public static MultiLayerCloudField generateParallel(long seed, SphericalSampler sp, Preset preset,
                                                         CoordinateCache coordCache) {
        MultiLayerCloudField clouds = new MultiLayerCloudField(sp.W, sp.H);

        // Create noise generators
        OpenSimplex2 base = new OpenSimplex2(seed);
        OpenSimplex2 warpX = new OpenSimplex2(seed + 10);
        OpenSimplex2 warpY = new OpenSimplex2(seed + 11);
        OpenSimplex2 warpZ = new OpenSimplex2(seed + 12);
        DomainWarpNoise coverage = new DomainWarpNoise(base, warpX, warpY, warpZ, preset.cloudWarp);

        int W = sp.W, H = sp.H;
        double cloudGamma = preset.cloudGamma;
        double cloudThreshold = 0.3;

        // Generate layers in parallel
        System.out.println("Generating multi-layer clouds (parallel)...");
        java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
            double[] normal = new double[3];

            for (int x = 0; x < W; x++) {
                coordCache.getNormal(x, y, normal);
                double nx = normal[0], ny = normal[1], nz = normal[2];

                // Layer 1: Stratocumulus (low, large, billowed)
                double strato = generateStratocumulus(base, coverage, nx, ny, nz);

                // Layer 2: Altocumulus (mid, medium, detailed)
                double alto = generateAltocumulus(base, nx, ny, nz);

                // Layer 3: Cirrus (high, fine, wispy)
                double cirrus = generateCirrus(base, nx, ny, nz);

                // Blend layers: strato dominant, alto adds detail, cirrus adds wisps
                double cloudDensity = 0.6 * strato + 0.3 * alto + 0.1 * cirrus;

                // Apply threshold for distinct clouds
                double opacity = Math.max(0.0, cloudDensity - cloudThreshold) / (1.0 - cloudThreshold);

                // Gamma correction for soft edges
                opacity = Math.pow(Math.max(0.0, opacity), 1.0 / cloudGamma);

                // Scale by base coverage
                opacity = opacity * preset.cloudCoverage;

                clouds.alpha[y][x] = (float) Math.max(0.0, Math.min(1.0, opacity));
            }
        });

        return clouds;
    }

    private static double generateStratocumulus(OpenSimplex2 base, DomainWarpNoise coverage,
                                                 double x, double y, double z) {
        // Low-frequency coverage for macro distribution
        double macroNoise = fbm(coverage, x, y, z, STRATO_SCALE, 2, 2.0, 0.5);
        macroNoise = (macroNoise + 1.0) * 0.5;

        // Mid-frequency detail for billowing structure
        double detail = fbm(base, x, y, z, STRATO_SCALE * 2.5, 3, 2.0, 0.6);
        detail = (detail + 1.0) * 0.5;

        // Blend: macro controls presence, detail controls shape
        return macroNoise * 0.7 + detail * 0.3;
    }

    private static double generateAltocumulus(OpenSimplex2 base, double x, double y, double z) {
        // Medium-frequency ridged noise for wispy structure
        double ridged = ridgedFbm(base, x, y, z, ALTO_SCALE, 4, 2.0, 0.6);
        ridged = (ridged + 1.0) * 0.5;

        // Add simple turbulence
        double turbulence = fbm(base, x, y, z, ALTO_SCALE * 3.0, 2, 2.0, 0.5);
        turbulence = (turbulence + 1.0) * 0.5;

        return ridged * 0.6 + turbulence * 0.4;
    }

    private static double generateCirrus(OpenSimplex2 base, double x, double y, double z) {
        // High-frequency noise for fine detail and wisps
        double fine = fbm(base, x, y, z, CIRRUS_SCALE, 5, 2.0, 0.5);
        fine = (fine + 1.0) * 0.5;

        // Make cirrus more transparent and wispy
        return fine * 0.6;
    }

    private static double fbm(OpenSimplex2 noise, double x, double y, double z,
                              double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain);
    }

    private static double fbm(DomainWarpNoise noise, double x, double y, double z,
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
}
