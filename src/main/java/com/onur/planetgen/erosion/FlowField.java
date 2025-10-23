package com.onur.planetgen.erosion;

import java.util.PriorityQueue;

/**
 * Represents water flow field on a height map.
 * Computes downslope flow direction and accumulation.
 */
public final class FlowField {
    public final float[][] flowX;   // X component of flow direction
    public final float[][] flowY;   // Y component of flow direction
    public final float[][] accum;   // Flow accumulation (how much water)

    public FlowField(float[][] flowX, float[][] flowY, float[][] accum) {
        this.flowX = flowX;
        this.flowY = flowY;
        this.accum = accum;
    }

    /**
     * Compute flow field from height map using downslope steepest-descent routing.
     * Flow accumulation represents how much water flows through each cell.
     *
     * @param h height field (normalized [-1, 1])
     * @return flow field with routing information
     */
    public static FlowField compute(float[][] h) {
        int H = h.length;
        int W = h[0].length;

        float[][] flowX = new float[H][W];
        float[][] flowY = new float[H][W];
        float[][] accum = new float[H][W];

        // Initialize accumulation with 1 (each cell contributes itself)
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                accum[y][x] = 1.0f;
            }
        }

        // For each cell, find steepest downslope direction
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float centerHeight = h[y][x];
                float maxSlope = 0;
                float bestFlowX = 0;
                float bestFlowY = 0;

                // Check 8 neighbors (Moore neighborhood)
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        int ny = y + dy;
                        int nx = x + dx;

                        // Clamp y (poles), wrap x (seams)
                        if (ny < 0 || ny >= H) continue;
                        nx = (nx + W) % W;

                        float neighborHeight = h[ny][nx];
                        float heightDiff = centerHeight - neighborHeight;

                        // Only consider downslope neighbors
                        if (heightDiff <= 0) continue;

                        // Compute slope
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        float slope = heightDiff / distance;

                        if (slope > maxSlope) {
                            maxSlope = slope;
                            bestFlowX = dx / distance;
                            bestFlowY = dy / distance;
                        }
                    }
                }

                flowX[y][x] = bestFlowX;
                flowY[y][x] = bestFlowY;
            }
        }

        // Compute accumulation by propagating downslope (OPTIMIZED - O(n log n) instead of O(n²))
        // Use priority queue to process cells from highest to lowest elevation
        boolean[] visited = new boolean[H * W];

        // Create max heap based on elevation
        PriorityQueue<Cell> pq = new PriorityQueue<>((a, b) -> Float.compare(b.height, a.height));

        // Add all cells to priority queue
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                pq.offer(new Cell(y, x, h[y][x]));
            }
        }

        // Process cells from highest to lowest
        while (!pq.isEmpty()) {
            Cell cell = pq.poll();
            int cy = cell.y;
            int cx = cell.x;

            if (visited[cy * W + cx]) continue;
            visited[cy * W + cx] = true;

            // Propagate accumulation downslope
            if (flowX[cy][cx] != 0 || flowY[cy][cx] != 0) {
                float fx = flowX[cy][cx];
                float fy = flowY[cy][cx];

                // Find which neighbor is the flow target
                float bestDist = Float.MAX_VALUE;
                int targetY = cy, targetX = cx;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        float dist = Math.abs(dx - fx) + Math.abs(dy - fy);
                        if (dist < bestDist) {
                            bestDist = dist;
                            targetY = cy + dy;
                            targetX = cx + dx;
                        }
                    }
                }

                // Clamp y, wrap x
                if (targetY >= 0 && targetY < H) {
                    targetX = (targetX + W) % W;
                    accum[targetY][targetX] += accum[cy][cx];
                }
            }
        }

        return new FlowField(flowX, flowY, accum);
    }

    /**
     * Helper class for priority queue sorting by elevation.
     */
    private static final class Cell {
        final int y;
        final int x;
        final float height;

        Cell(int y, int x, float height) {
            this.y = y;
            this.x = x;
            this.height = height;
        }
    }
}
