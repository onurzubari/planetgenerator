package com.onur.planetgen.util;

public record Vec3(double x, double y, double z) {
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public Vec3 normalized() { double L = length(); return new Vec3(x / L, y / L, z / L); }
}
