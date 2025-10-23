package com.onur.planetgen.erosion;

import java.util.Arrays;

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

        // Water and sediment fields (reused between iterations)
        float[][] water = new float[H][W];
        float[][] sediment = new float[H][W];
        float[][] nextWater = new float[H][W];
        float[][] nextSediment = new float[H][W];
        float[][] newHeight = new float[H][W];

        FlowField.Workspace flowWorkspace = new FlowField.Workspace(H, W);

        final float rainfallF = (float) rainfall;
        final float evaporationFactor = (float) (1.0 - evaporation);
        final float minWater = 0.001f;
        final float minSlope = 0.001f;
        final float sedimentCapacityFactor = (float) 0.01;
        final float minCapacity = (float) 0.0001;
        final float erosionRate = (float) 0.1;
        final float depositionRate = (float) 0.1;

        for (int iter = 0; iter < iterations; iter++) {
            // 1. Add rainfall uniformly
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                for (int x = 0; x < W; x++) {
                    waterRow[x] += rainfallF;
                }
            }

            // 2. Compute flow field (reuse buffers)
            FlowField flow = FlowField.compute(h, flowWorkspace);
            float[][] slope = flow.slope;
            float[][] accumulation = flow.accum;
            int[][] targetY = flow.targetY;
            int[][] targetX = flow.targetX;

            // 3. Copy height into working buffer for erosion edits
            for (int y = 0; y < H; y++) {
                System.arraycopy(h[y], 0, newHeight[y], 0, W);
            }

            // Apply erosion/deposition per cell
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                float[] sedimentRow = sediment[y];
                float[] newHeightRow = newHeight[y];
                float[] slopeRow = slope[y];
                float[] accumRow = accumulation[y];

                for (int x = 0; x < W; x++) {
                    float w = waterRow[x];
                    if (w < minWater) continue; // Skip dry cells

                    float s = sedimentRow[x];
                    float slopeValue = slopeRow[x];
                    if (slopeValue < minSlope) slopeValue = minSlope;

                    // Sediment carrying capacity: C = k_cap * water * slope * (1 + accumulation)
                    float capacity = sedimentCapacityFactor * w * slopeValue * (1.0f + accumRow[x]);
                    if (capacity < minCapacity) capacity = minCapacity;

                    if (s < capacity) {
                        // Erode from the bed
                        float erosion = erosionRate * (capacity - s) * w;
                        newHeightRow[x] -= erosion;
                        sedimentRow[x] += erosion;
                    } else {
                        // Deposit excess sediment
                        float deposition = depositionRate * (s - capacity);
                        newHeightRow[x] += deposition;
                        sedimentRow[x] -= deposition;
                    }
                }
            }

            // Ensure next buffers are clean before advection
            clear(nextWater);
            clear(nextSediment);

            // 4. Advect water and sediment downslope using precomputed targets
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                float[] sedimentRow = sediment[y];
                for (int x = 0; x < W; x++) {
                    float w = waterRow[x];
                    if (w < minWater) continue;

                    float s = sedimentRow[x];
                    int ty = targetY[y][x];
                    if (ty < 0) {
                        nextWater[y][x] += w;
                        nextSediment[y][x] += s;
                    } else {
                        int tx = targetX[y][x];
                        nextWater[ty][tx] += w;
                        nextSediment[ty][tx] += s;
                    }
                }
            }

            // Swap current and next buffers
            float[][] tmpWater = water;
            water = nextWater;
            nextWater = tmpWater;

            float[][] tmpSediment = sediment;
            sediment = nextSediment;
            nextSediment = tmpSediment;

            // 5. Apply height changes back onto the height field
            for (int y = 0; y < H; y++) {
                System.arraycopy(newHeight[y], 0, h[y], 0, W);
            }

            // 6. Evaporate water and settle remaining suspended sediment
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                float[] sedimentRow = sediment[y];
                float[] heightRow = h[y];

                for (int x = 0; x < W; x++) {
                    float w = waterRow[x] *= evaporationFactor;
                    if (w < minWater) {
                        heightRow[x] += sedimentRow[x];
                        sedimentRow[x] = 0f;
                        waterRow[x] = 0f;
                    }
                }
            }
        }
    }

    private static void clear(float[][] data) {
        for (float[] row : data) {
            Arrays.fill(row, 0f);
        }
    }
}
