package com.onur.planetgen.hydrology;

/**
 * Detects lake features from height field.
 * Lakes form in local minima where water accumulates.
 */
public final class LakeDetector {
    private LakeDetector() {}

    /**
     * Identify local minima as potential lakes.
     * A cell is a lake if all neighbors are higher.
     *
     * @param h height field (normalized [-1, 1])
     * @param threshold minimum height for lakes (typically around seaLevel)
     * @return lake mask: 1.0 for lake pixels, 0.0 for non-lake
     */
    public static float[][] detectLakes(float[][] h, double threshold) {
        int H = h.length;
        int W = h[0].length;
        float[][] lakes = new float[H][W];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float centerHeight = h[y][x];

                // Only consider areas below threshold (water level)
                if (centerHeight >= threshold) {
                    lakes[y][x] = 0.0f;
                    continue;
                }

                // Check if local minimum (all neighbors higher)
                boolean isMinimum = true;
                int neighborCount = 0;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        int ny = y + dy;
                        int nx = (x + dx + W) % W; // Wrap horizontally

                        // Clamp vertically (poles)
                        if (ny < 0 || ny >= H) {
                            continue;
                        }

                        if (h[ny][nx] <= centerHeight) {
                            isMinimum = false;
                            break;
                        }
                        neighborCount++;
                    }
                    if (!isMinimum) break;
                }

                lakes[y][x] = (isMinimum && neighborCount > 0) ? 1.0f : 0.0f;
            }
        }

        // Expand lakes via flood fill to adjacent water pixels
        expandLakes(lakes, h, threshold);

        return lakes;
    }

    /**
     * Expand lakes via flood fill to fill connected water basins.
     */
    private static void expandLakes(float[][] lakes, float[][] h, double threshold) {
        int H = h.length;
        int W = h[0].length;

        boolean changed = true;
        int iterations = 0;
        int maxIterations = H; // Prevent infinite loops

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (lakes[y][x] > 0) continue; // Already a lake
                    if (h[y][x] >= threshold) continue; // Not water level

                    // Check if adjacent to existing lake
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;

                            int ny = y + dy;
                            int nx = (x + dx + W) % W;

                            if (ny >= 0 && ny < H && lakes[ny][nx] > 0) {
                                // Adjacent to lake - add to lake
                                lakes[y][x] = 0.9f;
                                changed = true;
                                break;
                            }
                        }
                        if (lakes[y][x] > 0) break;
                    }
                }
            }
        }
    }

    /**
     * Get largest connected lake regions.
     * Useful for rendering major lakes differently.
     *
     * @param lakes lake mask from detectLakes
     * @param minSize minimum lake size (pixels)
     * @return lake regions with lake ID per pixel
     */
    public static int[][] getLakeRegions(float[][] lakes, int minSize) {
        int H = lakes.length;
        int W = lakes[0].length;
        int[][] regions = new int[H][W];
        boolean[][] visited = new boolean[H][W];

        int lakeId = 0;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (visited[y][x] || lakes[y][x] < 0.5f) continue;

                // Flood fill from this lake pixel
                int size = floodFill(lakes, visited, regions, y, x, ++lakeId, W, H);

                // Mark as invalid if too small
                if (size < minSize) {
                    for (int fy = 0; fy < H; fy++) {
                        for (int fx = 0; fx < W; fx++) {
                            if (regions[fy][fx] == lakeId) {
                                regions[fy][fx] = 0;
                            }
                        }
                    }
                    lakeId--;
                }
            }
        }

        return regions;
    }

    private static int floodFill(float[][] lakes, boolean[][] visited, int[][] regions,
                                   int startY, int startX, int lakeId, int W, int H) {
        int[] queue = new int[W * H * 2]; // x, y pairs
        int queueSize = 0;

        queue[queueSize++] = startX;
        queue[queueSize++] = startY;
        visited[startY][startX] = true;
        regions[startY][startX] = lakeId;

        int count = 1;

        while (queueSize > 0) {
            int x = queue[--queueSize];
            int y = queue[--queueSize];

            // 4-neighbor connectivity
            int[] ny = {y - 1, y + 1, y, y};
            int[] nx = {x, x, x - 1, x + 1};

            for (int d = 0; d < 4; d++) {
                int ny_val = ny[d];
                int nx_val = (nx[d] + W) % W;

                if (ny_val >= 0 && ny_val < H && !visited[ny_val][nx_val] && lakes[ny_val][nx_val] > 0.5f) {
                    visited[ny_val][nx_val] = true;
                    regions[ny_val][nx_val] = lakeId;
                    queue[queueSize++] = nx_val;
                    queue[queueSize++] = ny_val;
                    count++;
                }
            }
        }

        return count;
    }
}
