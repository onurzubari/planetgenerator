package com.onur.planetgen.planet;

public class SphericalSampler {
    public final int W, H; // 2:1 equirectangular

    public SphericalSampler(int W, int H) {
        this.W = W;
        this.H = H;
    }

    // Pixel center to spherical angles (radians)
    public double lon(int x) {
        return 2.0 * Math.PI * ((x + 0.5) / W) - Math.PI;
    }

    public double lat(int y) {
        return Math.PI / 2.0 - Math.PI * ((y + 0.5) / H);
    }
}
