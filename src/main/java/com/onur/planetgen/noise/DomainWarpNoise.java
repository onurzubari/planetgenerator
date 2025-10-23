package com.onur.planetgen.noise;

public final class DomainWarpNoise implements Noise {
    private final Noise base, warpX, warpY, warpZ;
    private final double amp;

    public DomainWarpNoise(Noise base, Noise warpX, Noise warpY, Noise warpZ, double amplitude) {
        this.base = base;
        this.warpX = warpX;
        this.warpY = warpY;
        this.warpZ = warpZ;
        this.amp = amplitude;
    }

    @Override
    public double noise3(double x, double y, double z) {
        double dx = warpX.noise3(x, y, z) * amp;
        double dy = warpY.noise3(x, y, z) * amp;
        double dz = warpZ.noise3(x, y, z) * amp;
        return base.noise3(x + dx, y + dy, z + dz);
    }
}
