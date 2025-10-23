package com.onur.planetgen.planet;

import com.onur.planetgen.noise.OpenSimplex2;

public final class HeightField {
    private HeightField() {}

    public static float[][] generate(long seed, SphericalSampler sp /* + params */) {
        int W = sp.W, H = sp.H;
        float[][] h = new float[H][W];
        OpenSimplex2 os = new OpenSimplex2(seed);

        // TODO: replace with domain-warped fBm + ridged + details per architecture.md
        double scale = 2.2;
        int oct = 6;
        double lac = 2.0, gain = 0.5;
        double sea = 0.0;

        for (int y = 0; y < H; y++) {
            double lat = sp.lat(y);
            double cLat = Math.cos(lat), sLat = Math.sin(lat);
            for (int x = 0; x < W; x++) {
                double lon = sp.lon(x);
                double nx = cLat * Math.cos(lon), ny = sLat, nz = cLat * Math.sin(lon);
                double val = fbm(os, nx, ny, nz, scale, oct, lac, gain);
                double ridge = 1.0 - Math.abs(val);
                double hh = 0.65 * val + 0.35 * ridge; // shaping stub
                h[y][x] = (float) hh;
            }
        }
        return h;
    }

    private static double fbm(OpenSimplex2 os, double x, double y, double z, double s, int oct, double lac, double gain) {
        double amp = 1, freq = 1, sum = 0;
        for (int i = 0; i < oct; i++) {
            sum += amp * os.noise3(x * s * freq, y * s * freq, z * s * freq);
            amp *= gain;
            freq *= lac;
        }
        return sum;
    }
}
