package com.onur.planetgen.erosion;

public final class HydraulicErosion {
    private HydraulicErosion() {}

    /**
     * Apply hydraulic erosion via rainfall and sediment transport.
     *
     * Algorithm:
     * 1. Add rainfall to each cell
     * 2. Compute downslope flow routing
     * 3. Calculate sediment capacity based on flow and slope
     * 4. Erode or deposit sediment
     * 5. Evaporate water
     *
     * @param h height field (modified in-place)
     * @param iterations number of erosion iterations
     * @param rainfall amount of water added per iteration (typical: 0.1-0.6)
     * @param evaporation rate of evaporation per iteration (typical: 0.1-0.5)
     */
    public static void apply(float[][] h, int iterations, double rainfall, double evaporation) {
        if (iterations <= 0 || h == null || h.length == 0) {
            return;
        }

        int H = h.length;
        int W = h[0].length;

        // Water and sediment fields
        float[][] water = new float[H][W];
        float[][] sediment = new float[H][W];

        // Parameters
        double sedimentCapacityFactor = 0.01;  // Controls how much sediment water can carry
        double minCapacity = 0.0001;            // Minimum sediment capacity
        double erosionRate = 0.1;               // How aggressively to erode
        double depositionRate = 0.1;            // How aggressively to deposit

        for (int iter = 0; iter < iterations; iter++) {
            // 1. Add rainfall uniformly
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    water[y][x] += (float) rainfall;
                }
            }

            // 2. Compute flow field (most expensive step)
            FlowField flow = FlowField.compute(h);

            // 3. Process each cell for erosion/deposition
            float[][] newHeight = new float[H][W];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    newHeight[y][x] = h[y][x];
                }
            }

            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float w = water[y][x];
                    float s = sediment[y][x];

                    if (w < 0.001) continue; // Skip dry cells

                    // Calculate slope (approximate via neighbors)
                    float slope = estimateSlope(h, x, y);
                    slope = Math.max(0.001f, slope); // Avoid division by zero

                    // Sediment carrying capacity: C = k_cap * |flow| * slope
                    float flowMagnitude = (float) Math.sqrt(flow.flowX[y][x] * flow.flowX[y][x] +
                                                             flow.flowY[y][x] * flow.flowY[y][x]);
                    float capacity = (float) (sedimentCapacityFactor * w * slope * (1.0 + flow.accum[y][x]));
                    capacity = Math.max((float) minCapacity, capacity);

                    // Erode or deposit sediment
                    if (s < capacity) {
                        // Can carry more: erode from bed
                        float erosion = (float) (erosionRate * (capacity - s) * w);
                        newHeight[y][x] -= erosion;
                        sediment[y][x] += erosion;
                    } else {
                        // Carrying too much: deposit
                        float deposition = (float) (depositionRate * (s - capacity));
                        newHeight[y][x] += deposition;
                        sediment[y][x] -= deposition;
                    }
                }
            }

            // 4. Advect water and sediment downslope
            float[][] newWater = new float[H][W];
            float[][] newSediment = new float[H][W];

            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (water[y][x] < 0.001) continue;

                    // Flow to downslope neighbor
                    float fx = flow.flowX[y][x];
                    float fy = flow.flowY[y][x];

                    if (Math.abs(fx) < 0.001 && Math.abs(fy) < 0.001) {
                        // Local minimum (sink) - accumulate water
                        newWater[y][x] += water[y][x];
                        newSediment[y][x] += sediment[y][x];
                    } else {
                        // Find best neighbor in flow direction
                        int bestNy = y, bestNx = x;
                        float bestDist = Float.MAX_VALUE;

                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;

                                float dist = Math.abs(dx - fx) + Math.abs(dy - fy);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    bestNy = y + dy;
                                    bestNx = x + dx;
                                }
                            }
                        }

                        // Clamp y, wrap x
                        if (bestNy >= 0 && bestNy < H) {
                            bestNx = (bestNx + W) % W;
                            newWater[bestNy][bestNx] += water[y][x];
                            newSediment[bestNy][bestNx] += sediment[y][x];
                        } else {
                            // Flow out of bounds at poles - keep locally
                            newWater[y][x] += water[y][x];
                            newSediment[y][x] += sediment[y][x];
                        }
                    }
                }
            }

            water = newWater;
            sediment = newSediment;

            // 5. Apply height changes
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    h[y][x] = newHeight[y][x];
                }
            }

            // 6. Evaporate water
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    water[y][x] *= (float) (1.0 - evaporation);
                    // Sediment settles out as water evaporates
                    if (water[y][x] < 0.001) {
                        h[y][x] += sediment[y][x];
                        sediment[y][x] = 0;
                    }
                }
            }
        }
    }

    private static float estimateSlope(float[][] h, int x, int y) {
        int H = h.length;
        int W = h[0].length;

        int xW = (x - 1 + W) % W;
        int xE = (x + 1) % W;
        int yN = Math.max(0, y - 1);
        int yS = Math.min(H - 1, y + 1);

        float dhdx = (h[y][xE] - h[y][xW]) * 0.5f;
        float dhdy = (h[yS][x] - h[yN][x]) * 0.5f;

        return (float) Math.sqrt(dhdx * dhdx + dhdy * dhdy);
    }
}
