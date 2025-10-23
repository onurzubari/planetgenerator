package com.onur.planetgen.erosion;

public final class ThermalErosion {
    private ThermalErosion() {}

    /**
     * Apply thermal erosion using slope-limited material diffusion.
     *
     * Material slides downslope when local slope exceeds talus angle.
     * Horizontally wrapping for seamless planets; pole clamping for natural distortion.
     *
     * @param h height field (modified in-place)
     * @param iterations number of erosion iterations
     * @param talus slope threshold (typical: 0.5 radians ≈ 0.55 in normalized height)
     * @param k erosion coefficient controlling diffusion rate (typical: 0.1-0.3)
     */
    public static void apply(float[][] h, int iterations, double talus, double k) {
        if (iterations <= 0 || h == null || h.length == 0) {
            return;
        }

        int H = h.length;
        int W = h[0].length;

        // Temporary arrays for accumulating material flow
        float[][] flow = new float[H][W];

        for (int iter = 0; iter < iterations; iter++) {
            // Clear flow accumulation
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    flow[y][x] = 0;
                }
            }

            // For each cell, calculate material movement
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float centerHeight = h[y][x];
                    float totalMaterial = 0;
                    int neighborCount = 0;

                    // 4-neighbor stencil (N, S, E, W)
                    int[] ny = {y - 1, y + 1, y, y};
                    int[] nx = {x, x, x + 1, x - 1};

                    for (int d = 0; d < 4; d++) {
                        int ny_val = ny[d];
                        int nx_val = nx[d];

                        // Wrap horizontally, clamp vertically (poles)
                        if (ny_val < 0 || ny_val >= H) {
                            continue; // Skip pole clamping (natural behavior)
                        }
                        nx_val = (nx_val + W) % W; // Horizontal wrap

                        float neighborHeight = h[ny_val][nx_val];
                        float heightDiff = centerHeight - neighborHeight;

                        // If slope exceeds talus angle, material slides
                        if (heightDiff > talus) {
                            float materialAmount = (float) (k * (heightDiff - talus));
                            totalMaterial += materialAmount;
                            flow[ny_val][nx_val] += materialAmount;
                            neighborCount++;
                        }
                    }

                    // Apply accumulated erosion
                    h[y][x] -= totalMaterial;
                }
            }

            // Add deposited material back to height field
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    h[y][x] += flow[y][x];
                }
            }
        }
    }
}
