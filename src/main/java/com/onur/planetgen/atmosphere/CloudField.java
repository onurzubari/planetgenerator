package com.onur.planetgen.atmosphere;

import com.onur.planetgen.planet.SphericalSampler;

public final class CloudField {
    public final float[][] alpha; // 0..1

    private CloudField(int W, int H) {
        alpha = new float[H][W];
    }

    public static CloudField generate(long seed, SphericalSampler sp /* + params */) {
        CloudField c = new CloudField(sp.W, sp.H);
        // TODO: fBm + ridged + worley + domain warp; write to c.alpha[y][x]
        return c;
    }
}
