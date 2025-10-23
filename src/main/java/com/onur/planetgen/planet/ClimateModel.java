package com.onur.planetgen.planet;

import com.onur.planetgen.noise.OpenSimplex2;

public final class ClimateModel {
    public record Sample(double temp, double moist, double humidity) {}

    private static final OpenSimplex2 MOISTURE_NOISE = new OpenSimplex2(42424242L);
    private static final double TEMP_LAT_COEFF = 1.2;  // Latitude-based temperature drop
    private static final double TEMP_ALT_COEFF = 0.9;  // Altitude-based temperature drop
    private static final double MOISTURE_BIAS = 0.1;   // Base moisture level
    private static final double SEA_LEVEL = 0.02;
    private static final double BASE_TEMP = 0.7;       // Base temperature (equatorial)

    private ClimateModel() {}

    /**
     * Sample climate at a given position on the planet.
     *
     * Temperature model:
     *   T = T₀ − k_lat·|sin(φ)| − k_alt·max(0, h − seaLevel)
     *
     * Moisture model:
     *   M = M₀ + noise(perlin) + k_alt·(seaLevel - h)  [inverted: water gathers in valleys]
     *
     * @param x pixel x coordinate
     * @param y pixel y coordinate
     * @param h height at this location (normalized [-1, 1])
     * @param lat latitude in radians
     * @return climate sample with temperature, moisture, and humidity
     */
    public static Sample sample(int x, int y, float h, double lat) {
        // Temperature: base - latitude effect - altitude effect
        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);

        // Latitude-based cooling (stronger at poles)
        double tempLat = BASE_TEMP - TEMP_LAT_COEFF * Math.abs(sinLat);

        // Altitude-based cooling (stronger above sea level)
        double altitudeEffect = Math.max(0.0, h - SEA_LEVEL);
        double tempAlt = -TEMP_ALT_COEFF * altitudeEffect;

        double temperature = tempLat + tempAlt;
        temperature = clamp(temperature, -1.0, 1.0); // Clamp to reasonable range

        // Moisture: base + perlin noise + altitude inversion (water in valleys)
        // Generate spatially coherent moisture using noise
        double moistureNoise = MOISTURE_NOISE.noise3(x * 0.005, y * 0.005, lat * 0.3);
        moistureNoise = (moistureNoise + 1.0) * 0.5; // Normalize to [0, 1]

        // Water accumulates in valleys (negative altitude effect)
        double altitudeMoisture = (SEA_LEVEL - h) * 0.5; // Inverted: higher in valleys

        double moisture = MOISTURE_BIAS + 0.4 * moistureNoise + 0.4 * Math.max(0.0, altitudeMoisture);
        moisture = clamp(moisture, 0.0, 1.0);

        // Humidity is similar to moisture but affected by temperature
        // (hot regions have lower effective humidity)
        double humidity = moisture * (0.5 + 0.5 * (1.0 - Math.max(0.0, temperature)));
        humidity = clamp(humidity, 0.0, 1.0);

        return new Sample(temperature, moisture, humidity);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
