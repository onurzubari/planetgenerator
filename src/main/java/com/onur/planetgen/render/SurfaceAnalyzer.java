package com.onur.planetgen.render;

import com.onur.planetgen.config.Preset;
import com.onur.planetgen.erosion.FlowField;
import com.onur.planetgen.hydrology.LakeDetector;
import com.onur.planetgen.hydrology.RiverDetector;
import com.onur.planetgen.noise.OpenSimplex2;
import com.onur.planetgen.planet.ClimateModel;
import com.onur.planetgen.planet.SphericalSampler;

/**
 * Performs a holistic surface analysis to derive physically based rendering maps,
 * biome masks, vegetation densities, snow/weathering data, and ocean shading.
 *
 * The analysis is intentionally CPU-only and deterministic, so it can be shared
 * between CLI exports, GUI previews, and future renderers without recomputation.
 */
public final class SurfaceAnalyzer {
    private SurfaceAnalyzer() {}

    public static SurfaceData analyze(float[][] height, Preset preset, long seed) {
        int h = height.length;
        int w = height[0].length;
        float seaLevel = preset != null ? (float) preset.seaLevel : 0.0f;
        SphericalSampler sampler = new SphericalSampler(w, h);

        FlowField flow = FlowField.compute(height);
        float[][] rivers = RiverDetector.smoothRivers(
                RiverDetector.detectRivers(flow, 0.35), 1);
        float[][] lakes = LakeDetector.detectLakes(height, seaLevel);

        int[][] aoGray = AmbientOcclusionRenderer.render(height);

        // Noise sources for detail/variation
        long detailSeed = seed ^ 0x9E3779B97F4A7C15L;
        long macroSeed = seed ^ 0xC6BC279692B5CC83L;
        OpenSimplex2 detailNoise = new OpenSimplex2(detailSeed);
        OpenSimplex2 macroNoise = new OpenSimplex2(macroSeed);

        float[][] metallic = new float[h][w];
        float[][] roughness = new float[h][w];
        float[][] ao = new float[h][w];
        float[][] vegetation = new float[h][w];
        float[][] snow = new float[h][w];
        float[][] detail = new float[h][w];
        float[][] waterDepth = new float[h][w];
        float[][] oceanSpecular = new float[h][w];
        float[][] atmosphereMask = new float[h][w];

        int[][] albedo = new int[h][w];
        int[][] biomeMask = new int[h][w];
        int[][] materialMask = new int[h][w];
        int[][] oceanShading = new int[h][w];
        int[][] atmosphere = new int[h][w];

        // Flow accumulation normalisation
        float minAccum = Float.MAX_VALUE;
        float maxAccum = Float.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float accum = flow.accum[y][x];
                if (accum < minAccum) minAccum = accum;
                if (accum > maxAccum) maxAccum = accum;
            }
        }
        float accumRange = Math.max(maxAccum - minAccum, 1e-5f);

        for (int y = 0; y < h; y++) {
            double lat = sampler.lat(y);
            double sinLat = Math.sin(lat);
            double cosLat = Math.cos(lat);
            float absSinLat = (float) Math.abs(sinLat);

            for (int x = 0; x < w; x++) {
                float heightValue = height[y][x];
                boolean isWater = heightValue < seaLevel;

                float slope = clamp01(flow.slope[y][x]);
                float accumNorm = (flow.accum[y][x] - minAccum) / accumRange;
                float riverStrength = clamp01(rivers[y][x]);
                float lakeStrength = clamp01(lakes[y][x]);
                float waterDepthValue = isWater ? seaLevel - heightValue : 0f;
                waterDepth[y][x] = waterDepthValue;

                ClimateModel.Sample climate = ClimateModel.sample(x, y, heightValue, lat);
                float tempNorm = (float) ((climate.temp() + 1.0) * 0.5);
                tempNorm = clamp01(tempNorm);
                float moisture = clamp01((float) climate.moist());
                float humidity = clamp01((float) climate.humidity());

                double nx = x / (double) w;
                double ny = y / (double) h;
                float detailNoiseVal = (float) ((detailNoise.noise3(nx * 12.0, ny * 12.0, seed * 0.17) + 1.0) * 0.5);
                float macroVariation = (float) ((macroNoise.noise3(nx * 2.5, ny * 2.5, seed * 0.05) + 1.0) * 0.5);
                detail[y][x] = detailNoiseVal;

                float vegetationAmount = computeVegetation(tempNorm, moisture, slope, riverStrength, lakeStrength);
                vegetation[y][x] = vegetationAmount;

                float snowAmount = computeSnowCoverage(tempNorm, heightValue, seaLevel, slope, absSinLat);
                snow[y][x] = snowAmount;

                BiomeType biome = classifyBiome(isWater, tempNorm, moisture, slope, riverStrength, lakeStrength,
                        snowAmount, heightValue, seaLevel, absSinLat, macroVariation);

                BiomeShading shading = shadeBiome(biome, tempNorm, moisture, vegetationAmount,
                        snowAmount, detailNoiseVal, macroVariation, riverStrength, lakeStrength,
                        waterDepthValue, lat, isWater);

                int baseR = RenderUtil.clamp255(shading.baseColor[0] * 255f);
                int baseG = RenderUtil.clamp255(shading.baseColor[1] * 255f);
                int baseB = RenderUtil.clamp255(shading.baseColor[2] * 255f);
                albedo[y][x] = RenderUtil.argb(baseR, baseG, baseB);

                roughness[y][x] = clamp01(shading.roughness);
                metallic[y][x] = clamp01(shading.metallic);
                oceanSpecular[y][x] = clamp01(shading.oceanSpecular);
                biomeMask[y][x] = shading.biomeColor;
                materialMask[y][x] = shading.materialColor;
                oceanShading[y][x] = shading.oceanColor;

                float aoFromRenderer = (aoGray[y][x] & 0xFF) / 255f;
                float occlusion = clamp01(aoFromRenderer * 0.6f + (1f - slope * 0.8f) * 0.4f);
                ao[y][x] = occlusion;

                float atmosphereStrength = computeAtmosphereMask(heightValue, seaLevel, tempNorm, humidity, absSinLat);
                atmosphereMask[y][x] = atmosphereStrength;
                atmosphere[y][x] = RenderUtil.argb(
                        RenderUtil.clamp255(atmosphereStrength * 210f),
                        RenderUtil.clamp255(shading.atmosphereColor[0] * 255f),
                        RenderUtil.clamp255(shading.atmosphereColor[1] * 255f),
                        RenderUtil.clamp255(shading.atmosphereColor[2] * 255f)
                );
            }
        }

        return new SurfaceData(
                w, h, seaLevel,
                albedo, biomeMask, materialMask,
                oceanShading, atmosphere,
                metallic, roughness, ao, vegetation, snow, detail,
                oceanSpecular, atmosphereMask, waterDepth
        );
    }

    private static float computeVegetation(float temp, float moisture, float slope,
                                           float riverStrength, float lakeStrength) {
        float thermal = clamp01(1f - Math.abs(temp - 0.65f) * 1.4f);
        float base = clamp01(moisture * thermal);
        float waterBoost = clamp01(riverStrength * 0.6f + lakeStrength * 0.4f);
        float slopePenalty = 1f - clamp01(slope * 1.5f);
        return clamp01(base * slopePenalty + waterBoost * 0.4f);
    }

    private static float computeSnowCoverage(float temp, float heightValue, float seaLevel,
                                             float slope, float absSinLat) {
        float altitude = clamp01((heightValue - seaLevel) * 1.4f);
        float polar = clamp01((absSinLat - 0.65f) * 1.2f);
        float cold = clamp01((0.18f - temp) * 2.0f);
        float slopePenalty = 1f - clamp01(slope * 1.1f);
        float combined = Math.max(Math.max(altitude, polar), cold);
        return clamp01(combined * slopePenalty);
    }

    private static float computeAtmosphereMask(float heightValue, float seaLevel,
                                               float temp, float humidity, float absSinLat) {
        float altitude = clamp01((heightValue - seaLevel) * 0.9f);
        float polarGlow = clamp01((absSinLat - 0.55f) * 0.9f);
        float dryness = clamp01(1f - humidity);
        float cold = clamp01((0.35f - temp) * 1.1f);
        float base = Math.max(altitude * 0.45f + dryness * 0.15f, polarGlow * 0.35f + cold * 0.25f);
        return clamp01(base);
    }

    private static BiomeType classifyBiome(boolean isWater, float temp, float moisture,
                                           float slope, float river, float lake,
                                           float snow, float heightValue, float seaLevel,
                                           float absSinLat, float macro) {
        if (isWater) {
            if (heightValue < seaLevel - 0.18f) return BiomeType.OCEAN_DEEP;
            if (heightValue < seaLevel - 0.04f) return BiomeType.OCEAN_SHALLOW;
            return BiomeType.BEACH;
        }
        if (snow > 0.6f) {
            return temp < 0.25f ? BiomeType.SNOW : BiomeType.ICE;
        }
        if (lake > 0.4f) {
            return BiomeType.SWAMP;
        }
        if (river > 0.5f && moisture > 0.5f) {
            return BiomeType.RIPARIAN;
        }
        if (slope > 0.75f || heightValue > seaLevel + 0.55f) {
            return BiomeType.MOUNTAIN;
        }
        if (temp < 0.25f) {
            return moisture > 0.45f ? BiomeType.TAIGA : BiomeType.TUNDRA;
        }
        if (temp < 0.5f) {
            if (moisture > 0.6f) return BiomeType.FOREST;
            if (moisture > 0.35f) return BiomeType.GRASSLAND;
            return BiomeType.SHRUBLAND;
        }
        if (moisture > 0.75f) {
            return BiomeType.RAINFOREST;
        }
        if (moisture > 0.55f) {
            return BiomeType.SAVANNA;
        }
        if (moisture > 0.35f) {
            return BiomeType.SEMIARID;
        }
        if (temp > 0.8f && macro > 0.6f) {
            return BiomeType.VOLCANIC;
        }
        return BiomeType.DESERT;
    }

    private static BiomeShading shadeBiome(BiomeType biome,
                                           float temp, float moisture, float vegetation,
                                           float snow, float detailNoise, float macro,
                                           float river, float lake, float waterDepth,
                                           double lat, boolean isWater) {
        float[] baseColor = new float[3];
        float[] atmosphereColor = new float[]{0.35f, 0.5f, 0.8f};
        float roughness = 0.6f;
        float metallic = 0.02f;
        float oceanSpec = 0f;
        int biomeColor;
        int materialColor;
        int oceanColor = 0;

        switch (biome) {
            case OCEAN_DEEP -> {
                float depthNorm = clamp01(waterDepth / 0.5f);
                float coldBias = clamp01((float) Math.abs(Math.sin(lat)) * 0.6f);
                baseColor[0] = 0.04f + 0.03f * (1f - depthNorm);
                baseColor[1] = 0.11f + 0.18f * (1f - depthNorm);
                baseColor[2] = 0.25f + 0.4f * (1f - depthNorm);
                baseColor[2] += coldBias * 0.12f;
                metallic = 0.0f;
                roughness = 0.05f + 0.05f * depthNorm;
                oceanSpec = clamp01(0.25f + (float) Math.pow(Math.cos(lat), 2) * 0.55f);
                oceanColor = RenderUtil.argb(
                        RenderUtil.clamp255(baseColor[0] * 255f + oceanSpec * 40f),
                        RenderUtil.clamp255(baseColor[1] * 255f + oceanSpec * 50f),
                        RenderUtil.clamp255(baseColor[2] * 255f + oceanSpec * 80f)
                );
                biomeColor = 0xFF1A2A5A;
                materialColor = 0xFF203050;
            }
            case OCEAN_SHALLOW -> {
                float depthNorm = clamp01(waterDepth / 0.2f);
                baseColor[0] = 0.12f + 0.08f * (1f - depthNorm);
                baseColor[1] = 0.32f + 0.25f * (1f - depthNorm);
                baseColor[2] = 0.42f + 0.35f * (1f - depthNorm);
                metallic = 0.0f;
                roughness = 0.08f;
                oceanSpec = clamp01(0.35f + (float) Math.pow(Math.cos(lat), 2) * 0.45f);
                oceanColor = RenderUtil.argb(
                        RenderUtil.clamp255(baseColor[0] * 255f + oceanSpec * 30f),
                        RenderUtil.clamp255(baseColor[1] * 255f + oceanSpec * 40f),
                        RenderUtil.clamp255(baseColor[2] * 255f + oceanSpec * 60f)
                );
                biomeColor = 0xFF2E6C88;
                materialColor = 0xFF2A6B76;
            }
            case BEACH -> {
                baseColor[0] = 0.78f;
                baseColor[1] = 0.71f;
                baseColor[2] = 0.56f;
                baseColor[0] += detailNoise * 0.06f;
                baseColor[1] += detailNoise * 0.04f;
                baseColor[2] += detailNoise * 0.02f;
                roughness = 0.55f;
                metallic = 0.0f;
                biomeColor = 0xFFE2D1A0;
                materialColor = 0xFFE0C07A;
            }
            case SWAMP -> {
                baseColor[0] = 0.24f;
                baseColor[1] = 0.33f + vegetation * 0.2f;
                baseColor[2] = 0.24f;
                roughness = 0.72f;
                metallic = 0.02f;
                biomeColor = 0xFF2F4B35;
                materialColor = 0xFF324132;
            }
            case RIPARIAN -> {
                baseColor[0] = 0.19f;
                baseColor[1] = 0.36f;
                baseColor[2] = 0.24f;
                baseColor[1] += river * 0.15f;
                roughness = 0.48f;
                metallic = 0.02f;
                biomeColor = 0xFF2D6C3A;
                materialColor = 0xFF2E4D33;
            }
            case RAINFOREST -> {
                baseColor[0] = 0.16f;
                baseColor[1] = 0.38f;
                baseColor[2] = 0.22f;
                baseColor[1] += vegetation * 0.2f;
                baseColor[2] += vegetation * 0.1f;
                roughness = 0.42f;
                metallic = 0.02f;
                biomeColor = 0xFF2C5E3A;
                materialColor = 0xFF295332;
            }
            case FOREST -> {
                baseColor[0] = 0.24f;
                baseColor[1] = 0.45f;
                baseColor[2] = 0.28f;
                baseColor[1] += vegetation * 0.1f;
                baseColor[2] += vegetation * 0.05f;
                roughness = 0.5f;
                metallic = 0.025f;
                biomeColor = 0xFF35663B;
                materialColor = 0xFF2F4B2B;
            }
            case TAIGA -> {
                baseColor[0] = 0.22f;
                baseColor[1] = 0.38f;
                baseColor[2] = 0.24f;
                baseColor[1] += snow * 0.2f;
                baseColor[2] += snow * 0.05f;
                roughness = 0.56f;
                metallic = 0.02f;
                biomeColor = 0xFF365338;
                materialColor = 0xFF304031;
            }
            case TUNDRA -> {
                baseColor[0] = 0.53f;
                baseColor[1] = 0.55f;
                baseColor[2] = 0.5f;
                baseColor[0] += vegetation * 0.05f;
                baseColor[1] += vegetation * 0.08f;
                roughness = 0.7f;
                metallic = 0.01f;
                biomeColor = 0xFF848F82;
                materialColor = 0xFF7B807A;
            }
            case SAVANNA -> {
                baseColor[0] = 0.58f;
                baseColor[1] = 0.52f;
                baseColor[2] = 0.28f;
                baseColor[1] += vegetation * 0.1f;
                roughness = 0.58f;
                metallic = 0.015f;
                biomeColor = 0xFF9C8C46;
                materialColor = 0xFF917B3E;
            }
            case SEMIARID -> {
                baseColor[0] = 0.55f;
                baseColor[1] = 0.48f;
                baseColor[2] = 0.32f;
                roughness = 0.65f;
                metallic = 0.015f;
                biomeColor = 0xFF9A7B4C;
                materialColor = 0xFF85633F;
            }
            case GRASSLAND -> {
                baseColor[0] = 0.45f;
                baseColor[1] = 0.57f;
                baseColor[2] = 0.32f;
                baseColor[1] += vegetation * 0.12f;
                roughness = 0.55f;
                metallic = 0.015f;
                biomeColor = 0xFF7DA653;
                materialColor = 0xFF5F7D3C;
            }
            case SHRUBLAND -> {
                baseColor[0] = 0.52f;
                baseColor[1] = 0.48f;
                baseColor[2] = 0.36f;
                roughness = 0.63f;
                biomeColor = 0xFF9C8B60;
                materialColor = 0xFF857454;
            }
            case DESERT -> {
                baseColor[0] = 0.78f + detailNoise * 0.08f;
                baseColor[1] = 0.68f + detailNoise * 0.05f;
                baseColor[2] = 0.45f + detailNoise * 0.03f;
                roughness = 0.8f;
                metallic = 0.008f;
                biomeColor = 0xFFE2BF7D;
                materialColor = 0xFFD0AA68;
            }
            case MOUNTAIN -> {
                baseColor[0] = 0.42f + detailNoise * 0.08f;
                baseColor[1] = 0.41f + detailNoise * 0.08f;
                baseColor[2] = 0.43f + detailNoise * 0.08f;
                roughness = 0.72f;
                metallic = 0.03f;
                biomeColor = 0xFF6E6C6F;
                materialColor = 0xFF545354;
            }
            case VOLCANIC -> {
                baseColor[0] = 0.35f + macro * 0.2f;
                baseColor[1] = 0.18f;
                baseColor[2] = 0.1f;
                float lavaGlow = clamp01(macro * 0.6f);
                baseColor[0] += lavaGlow * 0.4f;
                baseColor[1] += lavaGlow * 0.1f;
                metallic = 0.18f;
                roughness = 0.4f;
                biomeColor = 0xFF9D4023;
                materialColor = 0xFF5B2E28;
            }
            case ICE -> {
                baseColor[0] = 0.86f;
                baseColor[1] = 0.9f;
                baseColor[2] = 0.95f;
                roughness = 0.78f;
                metallic = 0.0f;
                biomeColor = 0xFFE5F1F8;
                materialColor = 0xFFDCECF2;
            }
            case SNOW -> {
                baseColor[0] = 0.92f;
                baseColor[1] = 0.94f;
                baseColor[2] = 0.97f;
                roughness = 0.74f;
                metallic = 0.0f;
                biomeColor = 0xFFF3F6FA;
                materialColor = 0xFFE9F0F6;
            }
            default -> {
                baseColor[0] = 0.5f;
                baseColor[1] = 0.5f;
                baseColor[2] = 0.5f;
                biomeColor = 0xFF808080;
                materialColor = 0xFF6A6A6A;
            }
        }

        // Apply vegetation tint
        if (!isWater) {
            float vegetationTint = vegetation * 0.6f;
            baseColor[0] = mix(baseColor[0], 0.18f, vegetationTint);
            baseColor[1] = mix(baseColor[1], 0.32f, vegetationTint);
            baseColor[2] = mix(baseColor[2], 0.18f, vegetationTint);
        }

        // Apply snow overlay
        if (snow > 0f) {
            baseColor[0] = mix(baseColor[0], 0.96f, snow);
            baseColor[1] = mix(baseColor[1], 0.97f, snow);
            baseColor[2] = mix(baseColor[2], 0.98f, snow);
        }

        // Add micro detail
        float detailStrength = isWater ? 0.02f : 0.08f;
        float detailOffset = (detailNoise - 0.5f) * detailStrength;
        baseColor[0] = clamp01(baseColor[0] + detailOffset);
        baseColor[1] = clamp01(baseColor[1] + detailOffset * 0.8f);
        baseColor[2] = clamp01(baseColor[2] + detailOffset * 0.6f);

        // Gently desaturate polar regions
        float polar = clamp01((float) Math.abs(Math.sin(lat)) * 0.9f - 0.45f);
        if (!isWater && polar > 0f) {
            float desat = polar * 0.1f;
            float avg = (baseColor[0] + baseColor[1] + baseColor[2]) / 3f;
            baseColor[0] = mix(baseColor[0], avg, desat);
            baseColor[1] = mix(baseColor[1], avg, desat);
            baseColor[2] = mix(baseColor[2], avg, desat);
        }

        int finalOceanColour;
        if (oceanColor == 0) {
            finalOceanColour = RenderUtil.argb(0,
                    RenderUtil.clamp255(baseColor[0] * 255f),
                    RenderUtil.clamp255(baseColor[1] * 255f),
                    RenderUtil.clamp255(baseColor[2] * 255f));
        } else {
            finalOceanColour = oceanColor;
        }

        return new BiomeShading(baseColor, roughness, metallic, oceanSpec,
                biomeColor, materialColor, finalOceanColour,
                new float[]{0.3f + polar * 0.08f, 0.42f + polar * 0.12f, 0.65f + polar * 0.1f});
    }

    private static float mix(float a, float b, float t) {
        return a * (1f - t) + b * t;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private enum BiomeType {
        OCEAN_DEEP,
        OCEAN_SHALLOW,
        BEACH,
        SWAMP,
        RIPARIAN,
        RAINFOREST,
        FOREST,
        TAIGA,
        TUNDRA,
        SAVANNA,
        SEMIARID,
        GRASSLAND,
        SHRUBLAND,
        DESERT,
        MOUNTAIN,
        VOLCANIC,
        ICE,
        SNOW
    }

    private record BiomeShading(float[] baseColor,
                                float roughness,
                                float metallic,
                                float oceanSpecular,
                                int biomeColor,
                                int materialColor,
                                int oceanColor,
                                float[] atmosphereColor) {}

    public static final class SurfaceData {
        private final int width;
        private final int height;
        private final float seaLevel;

        private final int[][] albedo;
        private final int[][] biomeMask;
        private final int[][] materialMask;
        private final int[][] oceanShading;
        private final int[][] atmosphere;

        private final float[][] metallic;
        private final float[][] roughness;
        private final float[][] ao;
        private final float[][] vegetation;
        private final float[][] snow;
        private final float[][] detail;
        private final float[][] oceanSpecular;
        private final float[][] atmosphereMask;
        private final float[][] waterDepth;

        private SurfaceData(int width, int height, float seaLevel,
                            int[][] albedo,
                            int[][] biomeMask,
                            int[][] materialMask,
                            int[][] oceanShading,
                            int[][] atmosphere,
                            float[][] metallic,
                            float[][] roughness,
                            float[][] ao,
                            float[][] vegetation,
                            float[][] snow,
                            float[][] detail,
                            float[][] oceanSpecular,
                            float[][] atmosphereMask,
                            float[][] waterDepth) {
            this.width = width;
            this.height = height;
            this.seaLevel = seaLevel;
            this.albedo = albedo;
            this.biomeMask = biomeMask;
            this.materialMask = materialMask;
            this.oceanShading = oceanShading;
            this.atmosphere = atmosphere;
            this.metallic = metallic;
            this.roughness = roughness;
            this.ao = ao;
            this.vegetation = vegetation;
            this.snow = snow;
            this.detail = detail;
            this.oceanSpecular = oceanSpecular;
            this.atmosphereMask = atmosphereMask;
            this.waterDepth = waterDepth;
        }

        public int width() { return width; }
        public int height() { return height; }
        public float seaLevel() { return seaLevel; }
        public int[][] albedo() { return albedo; }
        public int[][] biomeMask() { return biomeMask; }
        public int[][] materialMask() { return materialMask; }
        public int[][] oceanShading() { return oceanShading; }
        public int[][] atmosphere() { return atmosphere; }
        public float[][] metallic() { return metallic; }
        public float[][] roughness() { return roughness; }
        public float[][] ambientOcclusion() { return ao; }
        public float[][] vegetation() { return vegetation; }
        public float[][] snow() { return snow; }
        public float[][] detail() { return detail; }
        public float[][] oceanSpecular() { return oceanSpecular; }
        public float[][] atmosphereMask() { return atmosphereMask; }
        public float[][] waterDepth() { return waterDepth; }
    }
}
