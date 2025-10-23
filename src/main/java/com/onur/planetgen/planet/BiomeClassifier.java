package com.onur.planetgen.planet;

public final class BiomeClassifier {
    public enum Biome {
        TUNDRA, TAIGA, STEPPE, FOREST, DESERT, JUNGLE, ICE, SWAMP, RAINFOREST
    }

    public record Entry(int r, int g, int b, double rough) {}

    private BiomeClassifier() {}

    public static Biome classify(double temp, double moist) {
        if (temp < 0.2) return moist < 0.3 ? Biome.TUNDRA : Biome.TAIGA;
        if (temp < 0.6) return moist < 0.3 ? Biome.STEPPE : Biome.FOREST;
        return moist < 0.3 ? Biome.DESERT : Biome.JUNGLE;
    }
}
