package com.onur.planetgen.atmosphere;

import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.noise.DomainWarpNoise;

public final class CloudField {
    public final float[][] alpha; // 0..1

    private static final double CLOUD_COVERAGE = 0.55;  // Average cloud coverage
    private static final double CLOUD_WARP_AMOUNT = 0.25; // Domain warp strength
    private static final double CLOUD_GAMMA = 2.4;      // Opacity shaping curve
    private static final double CLOUD_THRESHOLD = 0.3;   // Cloud formation threshold

    private CloudField(int W, int H) {
        alpha = new float[H][W];
    }

    /**
     * Generate clouds with realistic stratocumulus/cirrus morphology.
     * Uses low-frequency coverage control + high-frequency detail breakup.
     */
    public static CloudField generate(long seed, SphericalSampler sp) {
        CloudField c = new CloudField(sp.W, sp.H);

        // Create noise generators
        OpenSimplex2 base = new OpenSimplex2(seed);
        OpenSimplex2 warpX = new OpenSimplex2(seed + 10);
        OpenSimplex2 warpY = new OpenSimplex2(seed + 11);
        OpenSimplex2 warpZ = new OpenSimplex2(seed + 12);
        DomainWarpNoise coverage = new DomainWarpNoise(base, warpX, warpY, warpZ, CLOUD_WARP_AMOUNT);

        int W = sp.W, H = sp.H;

        for (int y = 0; y < H; y++) {
            double lat = sp.lat(y);
            double cLat = Math.cos(lat);
            double sLat = Math.sin(lat);

            for (int x = 0; x < W; x++) {
                double lon = sp.lon(x);
                double nx = cLat * Math.cos(lon);
                double ny = sLat;
                double nz = cLat * Math.sin(lon);

                // 1. Low-frequency coverage control (determines where clouds exist)
                double coverageNoise = fbmLowFreq(coverage, nx, ny, nz, 1.5, 2, 2.0, 0.5);
                coverageNoise = (coverageNoise + 1.0) * 0.5; // Normalize to [0, 1]

                // 2. High-frequency detail (ridged for billowing structure)
                double detailNoise = ridgedFbm(base, nx, ny, nz, 4.0, 4, 2.0, 0.6);
                detailNoise = (detailNoise + 1.0) * 0.5; // Normalize

                // 3. Combine: coverage controls presence, detail controls shape
                double cloudDensity = coverageNoise * 0.6 + detailNoise * 0.4;

                // 4. Apply threshold to create distinct clouds
                double opacity = Math.max(0.0, cloudDensity - CLOUD_THRESHOLD) / (1.0 - CLOUD_THRESHOLD);

                // 5. Apply gamma correction for softer alpha transitions
                opacity = Math.pow(Math.max(0.0, opacity), 1.0 / CLOUD_GAMMA);

                // 6. Scale by base coverage
                opacity = opacity * CLOUD_COVERAGE;

                // Clamp to [0, 1]
                c.alpha[y][x] = (float) Math.max(0.0, Math.min(1.0, opacity));
            }
        }

        return c;
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

    private static double ridgedFbm(OpenSimplex2 noise, double x, double y, double z,
                                     double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            double ridge = 1.0 - Math.abs(n); // Creates sharp ridges
            sum += amp * ridge;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain);
    }
}
