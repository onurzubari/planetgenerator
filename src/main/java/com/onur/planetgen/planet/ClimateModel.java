package com.onur.planetgen.planet;

public final class ClimateModel {
    public record Sample(double temp, double moist, double humidity) {}

    private ClimateModel() {}

    public static Sample sample(int x, int y, float h, double lat) {
        // TODO: implement temp/precip models
        double temp = 1.0 - Math.abs(Math.sin(lat)) - Math.max(0, h) * 0.6;
        double moist = 0.5;
        double humidity = 0.5; // stubs
        return new Sample(temp, moist, humidity);
    }
}
