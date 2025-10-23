package com.onur.planetgen.planet;

import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.noise.DomainWarpNoise;

public final class HeightField {
    private HeightField() {}

    public static float[][] generate(long seed, SphericalSampler sp /* + params */) {
        int W = sp.W, H = sp.H;
        float[][] h = new float[H][W];

        // Parameters for Phase 1 (earthlike preset)
        double seaLevel = 0.02;
        double continentScale = 2.2;
        double mountainIntensity = 0.9;

        // Noise generators
        OpenSimplex2 base = new OpenSimplex2(seed);
        OpenSimplex2 warpX = new OpenSimplex2(seed + 1);
        OpenSimplex2 warpY = new OpenSimplex2(seed + 2);
        OpenSimplex2 warpZ = new OpenSimplex2(seed + 3);
        DomainWarpNoise domainWarped = new DomainWarpNoise(base, warpX, warpY, warpZ, 0.08);

        float minH = Float.POSITIVE_INFINITY;
        float maxH = Float.NEGATIVE_INFINITY;

        for (int y = 0; y < H; y++) {
            double lat = sp.lat(y);
            double cLat = Math.cos(lat);
            double sLat = Math.sin(lat);

            for (int x = 0; x < W; x++) {
                double lon = sp.lon(x);
                double nx = cLat * Math.cos(lon);
                double ny = sLat;
                double nz = cLat * Math.sin(lon);

                // 1. Continental base: domain-warped fBm (low frequency, 2 octaves)
                double continent = fbmLowFreq(domainWarped, nx, ny, nz, continentScale, 2, 2.0, 0.5);

                // 2. Mountains: ridged multifractal modulated by continental mask
                double ridged = ridgedFbm(base, nx, ny, nz, continentScale * 1.5, 4, 2.0, 0.6);
                double mountainMask = Math.max(0.0, continent); // Use continent as mask
                double mountains = ridged * mountainIntensity * mountainMask;

                // 3. Detail: mid-frequency noise (slope-modulated)
                double detail = fbmMidFreq(base, nx, ny, nz, continentScale * 3.0, 3, 2.0, 0.5);
                double slope = estimateSlope(base, nx, ny, nz, continentScale, 0.01);
                double slopeMask = Math.pow(Math.max(0.0, slope), 2.0); // Quadratic falloff
                double detailModulated = detail * 0.15 * slopeMask;

                // Combine: continent + mountains + detail
                double heightValue = 0.6 * continent + 0.3 * mountains + 0.1 * detailModulated;

                // Store for later normalization
                h[y][x] = (float) heightValue;
                if (heightValue < minH) minH = (float) heightValue;
                if (heightValue > maxH) maxH = (float) heightValue;
            }
        }

        // Normalize to [-1, 1] range
        normalizeHeight(h, minH, maxH);

        // Apply sea level offset
        applySeaLevel(h, seaLevel);

        return h;
    }

    private static void normalizeHeight(float[][] h, float minH, float maxH) {
        float range = maxH - minH;
        if (range < 1e-6) range = 1.0f; // Avoid division by zero

        for (int y = 0; y < h.length; y++) {
            for (int x = 0; x < h[y].length; x++) {
                h[y][x] = 2.0f * ((h[y][x] - minH) / range) - 1.0f;
            }
        }
    }

    private static void applySeaLevel(float[][] h, double seaLevel) {
        for (int y = 0; y < h.length; y++) {
            for (int x = 0; x < h[y].length; x++) {
                // Shift so that seaLevel threshold is applied
                h[y][x] -= (float) seaLevel;
            }
        }
    }

    private static double fbmLowFreq(DomainWarpNoise noise, double x, double y, double z,
                                      double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain); // Normalize by max possible amplitude
    }

    private static double fbmMidFreq(OpenSimplex2 noise, double x, double y, double z,
                                      double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / (1.0 - gain); // Normalize
    }

    private static double ridgedFbm(OpenSimplex2 noise, double x, double y, double z,
                                     double scale, int octaves, double lacunarity, double gain) {
        double amp = 1.0, freq = 1.0, sum = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = noise.noise3(x * scale * freq, y * scale * freq, z * scale * freq);
            double ridge = 1.0 - Math.abs(n); // Invert absolute value for ridges
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
