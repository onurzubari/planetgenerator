package com.onur.planetgen.erosion;

public final class HydraulicErosion {
    private HydraulicErosion() {}

    /**
     * Apply hydraulic erosion using a lightweight shallow-water simulation.
     *
     * The new solver avoids the expensive global flow-field computation and instead
     * updates local four-direction fluxes with a stable explicit integration step.
     * Because the batch workflow skips hydraulic erosion, this routine is optimised
     * for occasional single-run use: it converges more quickly, moves less sediment,
     * and prefers deposition rather than carving deep channels.
     *
     * @param h height field (modified in-place)
     * @param iterations number of erosion iterations
     * @param rainfall amount of water added per iteration (typical: 0.05-0.3)
     * @param evaporation rate of evaporation per iteration (typical: 0.1-0.5)
     */
    public static void apply(float[][] h, int iterations, double rainfall, double evaporation) {
        if (iterations <= 0 || h == null || h.length == 0) {
            return;
        }

        int H = h.length;
        int W = h[0].length;

        float[][] water = new float[H][W];
        float[][] sediment = new float[H][W];
        float[][] newWater = new float[H][W];
        float[][] newHeight = new float[H][W];

        float[][] fluxLeft = new float[H][W];
        float[][] fluxRight = new float[H][W];
        float[][] fluxTop = new float[H][W];
        float[][] fluxBottom = new float[H][W];

        final float rainfallF = (float) rainfall;
        final float evaporationFactor = (float) (1.0 - evaporation);

        final float minWater = 0.0005f;
        final float minSlope = 0.0001f;
        final float minCapacity = 0.00005f;

        final float erosionRate = 0.03f;
        final float depositionRate = 0.15f;
        final float maxErosion = 0.01f;

        final float capacitySlopeFactor = 3.0f;
        final float capacityVelocityFactor = 1.0f;

        final float gravity = 9.81f;
        final float timeStep = 1.0f;
        final float pipeArea = 1.0f;
        final float cellSize = 1.0f;
        final float flowFactor = pipeArea * gravity * timeStep / cellSize;
        final float fluxDamping = 1.0f / (1.0f + 4.0f); // soft limit to keep flux stable

        for (int iter = 0; iter < iterations; iter++) {
            // 1. Rainfall
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                for (int x = 0; x < W; x++) {
                    waterRow[x] += rainfallF;
                }
            }

            // 2. Update directional fluxes based on height differences
            for (int y = 0; y < H; y++) {
                int yNorth = y > 0 ? y - 1 : y;
                int ySouth = y < H - 1 ? y + 1 : y;
                float[] waterRow = water[y];
                float[] rowFluxLeft = fluxLeft[y];
                float[] rowFluxRight = fluxRight[y];
                float[] rowFluxTop = fluxTop[y];
                float[] rowFluxBottom = fluxBottom[y];

                for (int x = 0; x < W; x++) {
                    int xWest = (x - 1 + W) % W;
                    int xEast = (x + 1) % W;

                    float currentHeight = h[y][x] + waterRow[x];

                    float slopeLeft = currentHeight - (h[y][xWest] + water[y][xWest]);
                    float slopeRight = currentHeight - (h[y][xEast] + water[y][xEast]);
                    float slopeTop = (y > 0) ? currentHeight - (h[yNorth][x] + water[yNorth][x]) : 0f;
                    float slopeBottom = (y < H - 1) ? currentHeight - (h[ySouth][x] + water[ySouth][x]) : 0f;

                    float newFluxLeft = slopeLeft > 0f ? (rowFluxLeft[x] + flowFactor * slopeLeft) * fluxDamping : 0f;
                    float newFluxRight = slopeRight > 0f ? (rowFluxRight[x] + flowFactor * slopeRight) * fluxDamping : 0f;
                    float newFluxTop = slopeTop > 0f ? (rowFluxTop[x] + flowFactor * slopeTop) * fluxDamping : 0f;
                    float newFluxBottom = slopeBottom > 0f ? (rowFluxBottom[x] + flowFactor * slopeBottom) * fluxDamping : 0f;

                    float totalOut = newFluxLeft + newFluxRight + newFluxTop + newFluxBottom;
                    float available = waterRow[x];
                    if (totalOut > available && totalOut > 0f) {
                        float scale = available / totalOut;
                        newFluxLeft *= scale;
                        newFluxRight *= scale;
                        newFluxTop *= scale;
                        newFluxBottom *= scale;
                    }

                    rowFluxLeft[x] = newFluxLeft;
                    rowFluxRight[x] = newFluxRight;
                    rowFluxTop[x] = newFluxTop;
                    rowFluxBottom[x] = newFluxBottom;
                }
            }

            // 3. Move water according to fluxes
            for (int y = 0; y < H; y++) {
                int yNorth = y > 0 ? y - 1 : y;
                int ySouth = y < H - 1 ? y + 1 : y;

                for (int x = 0; x < W; x++) {
                    int xWest = (x - 1 + W) % W;
                    int xEast = (x + 1) % W;

                    float outFlux = fluxLeft[y][x] + fluxRight[y][x] + fluxTop[y][x] + fluxBottom[y][x];

                    float inFlux = fluxRight[y][xWest] + fluxLeft[y][xEast];
                    if (y > 0) {
                        inFlux += fluxBottom[yNorth][x];
                    }
                    if (y < H - 1) {
                        inFlux += fluxTop[ySouth][x];
                    }

                    float newAmount = water[y][x] + inFlux - outFlux;
                    newWater[y][x] = newAmount > 0f ? newAmount : 0f;
                }
            }

            // Swap water buffers
            float[][] tmpWater = water;
            water = newWater;
            newWater = tmpWater;

            // 4. Copy terrain for edits
            for (int y = 0; y < H; y++) {
                System.arraycopy(h[y], 0, newHeight[y], 0, W);
            }

            // 5. Erode or deposit based on transport capacity
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                float[] sedimentRow = sediment[y];
                float[] newHeightRow = newHeight[y];

                for (int x = 0; x < W; x++) {
                    float waterHere = waterRow[x];
                    if (waterHere < minWater) {
                        continue;
                    }

                    float slope = localSlope(h, x, y);
                    if (slope < minSlope) {
                        slope = minSlope;
                    }

                    float velX = fluxRight[y][x] - fluxLeft[y][x];
                    float velY = fluxBottom[y][x] - fluxTop[y][x];
                    float velocity = (float) Math.sqrt(velX * velX + velY * velY);

                    float capacity = waterHere * (capacitySlopeFactor * slope + capacityVelocityFactor * velocity);
                    if (capacity < minCapacity) {
                        capacity = minCapacity;
                    }

                    float suspended = sedimentRow[x];
                    if (suspended > capacity) {
                        float deposit = depositionRate * (suspended - capacity);
                        if (deposit > suspended) {
                            deposit = suspended;
                        }
                        newHeightRow[x] += deposit;
                        sedimentRow[x] -= deposit;
                    } else {
                        float erode = erosionRate * (capacity - suspended);
                        if (erode > maxErosion) {
                            erode = maxErosion;
                        }
                        if (erode > 0f) {
                            newHeightRow[x] -= erode;
                            sedimentRow[x] += erode;
                        }
                    }
                }
            }

            for (int y = 0; y < H; y++) {
                System.arraycopy(newHeight[y], 0, h[y], 0, W);
            }

            // 6. Evaporate and settle sediment
            for (int y = 0; y < H; y++) {
                float[] waterRow = water[y];
                float[] sedimentRow = sediment[y];
                float[] heightRow = h[y];

                for (int x = 0; x < W; x++) {
                    waterRow[x] *= evaporationFactor;
                    if (waterRow[x] < minWater) {
                        heightRow[x] += sedimentRow[x];
                        sedimentRow[x] = 0f;
                        waterRow[x] = 0f;
                    }
                }
            }
        }
    }

    private static float localSlope(float[][] h, int x, int y) {
        int H = h.length;
        int W = h[0].length;

        int xWest = (x - 1 + W) % W;
        int xEast = (x + 1) % W;
        int yNorth = Math.max(0, y - 1);
        int ySouth = Math.min(H - 1, y + 1);

        float slopeX = (h[y][xEast] - h[y][xWest]) * 0.5f;
        float slopeY = (h[ySouth][x] - h[yNorth][x]) * 0.5f;

        return (float) Math.sqrt(slopeX * slopeX + slopeY * slopeY) + 1e-6f;
    }
}
