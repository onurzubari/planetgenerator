package com.onur.planetgen.erosion;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Represents water flow field on a height map.
 * Computes downslope routing, slope magnitude, and flow accumulation.
 */
public final class FlowField {
    public final float[][] flowX;   // X component of normalized flow direction
    public final float[][] flowY;   // Y component of normalized flow direction
    public final float[][] accum;   // Flow accumulation (how much water passes through)
    public final float[][] slope;   // Local slope magnitude toward steepest descent
    public final int[][] targetY;   // Y coordinate of the downstream cell (-1 for sinks)
    public final int[][] targetX;   // X coordinate of the downstream cell (-1 for sinks)

    FlowField(float[][] flowX,
              float[][] flowY,
              float[][] accum,
              float[][] slope,
              int[][] targetY,
              int[][] targetX) {
        this.flowX = flowX;
        this.flowY = flowY;
        this.accum = accum;
        this.slope = slope;
        this.targetY = targetY;
        this.targetX = targetX;
    }

    /**
     * Compute flow field using a temporary workspace that is reused across calls.
     */
    public static FlowField compute(float[][] h, Workspace workspace) {
        int H = h.length;
        int W = h[0].length;

        workspace.ensureCapacity(H, W);

        float[][] flowX = workspace.flowX;
        float[][] flowY = workspace.flowY;
        float[][] accum = workspace.accum;
        float[][] slope = workspace.slope;
        int[][] targetY = workspace.targetY;
        int[][] targetX = workspace.targetX;
        boolean[] visited = workspace.visited;

        // Reset working arrays
        for (int y = 0; y < H; y++) {
            Arrays.fill(flowX[y], 0f);
            Arrays.fill(flowY[y], 0f);
            Arrays.fill(accum[y], 1f); // Each cell contributes at least its own runoff
            Arrays.fill(slope[y], 0f);
        }
        Arrays.fill(visited, 0, H * W, false);

        // Determine steepest descent direction per cell
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float center = h[y][x];

                float maxSlope = 0f;
                int bestY = -1;
                int bestX = x;
                float bestFx = 0f;
                float bestFy = 0f;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        int ny = y + dy;
                        if (ny < 0 || ny >= H) continue; // clamp at poles

                        int nx = x + dx;
                        int wrappedX = (nx + W) % W;

                        float neighbor = h[ny][wrappedX];
                        float diff = center - neighbor;
                        if (diff <= 0f) continue; // only consider downslope

                        float distance = (dx == 0 || dy == 0) ? 1f : 1.41421356f;
                        float slopeValue = diff / distance;

                        if (slopeValue > maxSlope) {
                            maxSlope = slopeValue;
                            bestY = ny;
                            bestX = wrappedX;
                            bestFx = dx / distance;
                            bestFy = dy / distance;
                        }
                    }
                }

                targetY[y][x] = bestY;
                targetX[y][x] = bestY >= 0 ? bestX : -1;
                flowX[y][x] = bestY >= 0 ? bestFx : 0f;
                flowY[y][x] = bestY >= 0 ? bestFy : 0f;
                slope[y][x] = maxSlope;
            }
        }

        // Accumulate flow by processing cells from highest to lowest elevation
        PriorityQueue<Cell> queue = workspace.queue;
        queue.clear();

        Cell[] cells = workspace.cells;
        int total = H * W;
        for (int index = 0; index < total; index++) {
            Cell cell = cells[index];
            cell.height = h[cell.y][cell.x];
            queue.offer(cell);
        }

        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            int cy = cell.y;
            int cx = cell.x;

            int flatIndex = cy * W + cx;
            if (visited[flatIndex]) continue;
            visited[flatIndex] = true;

            int ty = targetY[cy][cx];
            if (ty >= 0) {
                int tx = targetX[cy][cx];
                accum[ty][tx] += accum[cy][cx];
            }
        }

        return new FlowField(flowX, flowY, accum, slope, targetY, targetX);
    }

    /**
     * Convenience overload that allocates a workspace on each call.
     * Prefer {@link #compute(float[][], Workspace)} when invoked repeatedly.
     */
    public static FlowField compute(float[][] h) {
        Workspace workspace = new Workspace(h.length, h[0].length);
        return compute(h, workspace);
    }

    /**
     * Reusable buffers for flow computation to avoid repeated allocations.
     */
    public static final class Workspace {
        private final PriorityQueue<Cell> queue;
        float[][] flowX;
        float[][] flowY;
        float[][] accum;
        float[][] slope;
        int[][] targetY;
        int[][] targetX;
        boolean[] visited;
        Cell[] cells;
        int height;
        int width;

        public Workspace(int height, int width) {
            this.queue = new PriorityQueue<>((a, b) -> Float.compare(b.height, a.height));
            resize(height, width);
        }

        public void ensureCapacity(int height, int width) {
            if (height != this.height || width != this.width) {
                resize(height, width);
            }
        }

        private void resize(int height, int width) {
            this.height = height;
            this.width = width;

            flowX = new float[height][width];
            flowY = new float[height][width];
            accum = new float[height][width];
            slope = new float[height][width];
            targetY = new int[height][width];
            targetX = new int[height][width];
            visited = new boolean[height * width];

            cells = new Cell[height * width];
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    cells[idx++] = new Cell(y, x);
                }
            }

            queue.clear();
        }
    }

    /**
     * Cell metadata reused during priority queue processing.
     */
    private static final class Cell {
        final int y;
        final int x;
        float height;

        Cell(int y, int x) {
            this.y = y;
            this.x = x;
        }
    }
}
