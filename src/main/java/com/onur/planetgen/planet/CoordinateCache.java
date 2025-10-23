package com.onur.planetgen.planet;

/**
 * Caches precomputed spherical coordinates for efficient terrain generation.
 * Eliminates redundant trigonometric computations during noise sampling.
 */
public final class CoordinateCache {
    public final int W, H;

    // Per-pixel cached coordinates
    public final double[] lons;
    public final double[] lats;

    // Per-scanline cached values
    public final double[] cosLat;
    public final double[] sinLat;

    // Per-pixel cached unit sphere normals
    public final double[] nx;
    public final double[] ny;
    public final double[] nz;

    public CoordinateCache(int W, int H, SphericalSampler sampler) {
        this.W = W;
        this.H = H;

        this.lons = new double[W];
        this.lats = new double[H];
        this.cosLat = new double[H];
        this.sinLat = new double[H];

        this.nx = new double[W * H];
        this.ny = new double[W * H];
        this.nz = new double[W * H];

        // Precompute all longitude values
        for (int x = 0; x < W; x++) {
            lons[x] = sampler.lon(x);
        }

        // Precompute latitude and trig values
        for (int y = 0; y < H; y++) {
            lats[y] = sampler.lat(y);
            cosLat[y] = Math.cos(lats[y]);
            sinLat[y] = Math.sin(lats[y]);
        }

        // Precompute unit sphere normals for all pixels
        for (int y = 0; y < H; y++) {
            double cLat = cosLat[y];
            double sLat = sinLat[y];

            for (int x = 0; x < W; x++) {
                double lon = lons[x];
                double cosLon = Math.cos(lon);
                double sinLon = Math.sin(lon);

                int idx = y * W + x;
                nx[idx] = cLat * cosLon;
                ny[idx] = sLat;
                nz[idx] = cLat * sinLon;
            }
        }
    }

    /**
     * Get unit sphere normal for a pixel.
     */
    public void getNormal(int x, int y, double[] out) {
        int idx = y * W + x;
        out[0] = nx[idx];
        out[1] = ny[idx];
        out[2] = nz[idx];
    }
}
