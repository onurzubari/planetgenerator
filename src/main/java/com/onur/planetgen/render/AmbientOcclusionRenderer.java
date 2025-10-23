package com.onur.planetgen.render;

/**
 * Ambient Occlusion (AO) map generator.
 * Creates shadowing texture that shows occlusion in crevices and valleys.
 *
 * Two methods implemented:
 * 1. Slope-based AO (fast) - steeper slopes = more occlusion
 * 2. Height-difference AO (accurate) - lower pixels surrounded by higher = more occlusion
 */
public final class AmbientOcclusionRenderer {
    private AmbientOcclusionRenderer() {}

    /**
     * Generate ambient occlusion map using slope-based method.
     * Fast and gives good visual results for terrain features.
     *
     * @param height height field (normalized [-1, 1])
     * @return AO texture as Gray8: 0 (bright) to 255 (occluded)
     */
    public static int[][] renderSlopeBased(float[][] height) {
        int H = height.length, W = height[0].length;
        int[][] ao = new int[H][W];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // Calculate slope at this pixel
                float slope = estimateSlope(height, x, y);

                // Steeper slopes = more occlusion (darker)
                // Flat areas = less occlusion (brighter)
                // Range: slope from 0 (flat) to ~2.0 (very steep)
                float aoFactor = Math.min(1.0f, slope * 0.5f);

                // Invert: higher slope → darker (more occlusion)
                float brightness = 1.0f - aoFactor;

                // Apply gamma for natural appearance
                brightness = (float) Math.pow(brightness, 0.5);

                ao[y][x] = (int) (brightness * 255);
            }
        }

        return ao;
    }

    /**
     * Generate ambient occlusion map using height-difference method.
     * More accurate: considers if pixel is surrounded by higher neighbors.
     *
     * @param height height field
     * @param kernelRadius neighborhood radius to check (typically 1-3 pixels)
     * @return AO texture as Gray8
     */
    public static int[][] renderHeightDifference(float[][] height, int kernelRadius) {
        int H = height.length, W = height[0].length;
        int[][] ao = new int[H][W];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float centerHeight = height[y][x];
                float sumHeightDiff = 0.0f;
                int sampleCount = 0;

                // Check neighbors in kernel
                for (int dy = -kernelRadius; dy <= kernelRadius; dy++) {
                    for (int dx = -kernelRadius; dx <= kernelRadius; dx++) {
                        if (dx == 0 && dy == 0) continue;

                        int ny = y + dy;
                        int nx = (x + dx + W) % W;

                        // Clamp y (no wrap at poles)
                        if (ny < 0 || ny >= H) continue;

                        float neighborHeight = height[ny][nx];
                        float heightDiff = neighborHeight - centerHeight;

                        // Only count positive differences (neighbors higher)
                        if (heightDiff > 0) {
                            sumHeightDiff += heightDiff;
                        }

                        sampleCount++;
                    }
                }

                // Average height difference
                float avgHeightDiff = (sampleCount > 0) ? sumHeightDiff / sampleCount : 0.0f;

                // Convert to occlusion: higher neighbors → more occlusion
                // Normalize by typical height range
                float occlusion = Math.min(1.0f, avgHeightDiff * 2.0f);

                // Apply gamma
                occlusion = (float) Math.pow(occlusion, 0.5);

                ao[y][x] = (int) (occlusion * 255);
            }
        }

        return ao;
    }

    /**
     * Generate AO using combined method: slope + height difference.
     * Best visual results, still reasonably fast.
     *
     * @param height height field
     * @return AO texture as Gray8
     */
    public static int[][] render(float[][] height) {
        int H = height.length, W = height[0].length;
        int[][] aoSlope = renderSlopeBased(height);
        int[][] aoHeightDiff = renderHeightDifference(height, 1);

        int[][] ao = new int[H][W];

        // Blend both methods: 60% slope-based, 40% height-difference
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float slopeVal = ((aoSlope[y][x] & 0xFF) / 255.0f);
                float heightVal = ((aoHeightDiff[y][x] & 0xFF) / 255.0f);

                float blended = slopeVal * 0.6f + heightVal * 0.4f;
                ao[y][x] = (int) (blended * 255);
            }
        }

        return ao;
    }

    /**
     * Smooth AO texture with box filter for natural appearance.
     *
     * @param ao AO texture to smooth
     * @param kernelRadius filter radius (typically 1-2 pixels)
     * @return smoothed AO texture
     */
    public static int[][] smooth(int[][] ao, int kernelRadius) {
        int H = ao.length, W = ao[0].length;
        int[][] smoothed = new int[H][W];

        int kernelSize = 2 * kernelRadius + 1;
        float kernelSum = kernelSize * kernelSize;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float sum = 0;

                for (int dy = -kernelRadius; dy <= kernelRadius; dy++) {
                    for (int dx = -kernelRadius; dx <= kernelRadius; dx++) {
                        int ny = y + dy;
                        int nx = (x + dx + W) % W;

                        if (ny >= 0 && ny < H) {
                            sum += (ao[ny][nx] & 0xFF);
                        }
                    }
                }

                float avg = sum / kernelSum;
                smoothed[y][x] = (byte) avg;
            }
        }

        return smoothed;
    }

    /**
     * Estimate slope at a pixel using Sobel operator.
     * Returns magnitude of gradient.
     */
    private static float estimateSlope(float[][] height, int x, int y) {
        int H = height.length, W = height[0].length;

        // Neighbors
        int xW = (x - 1 + W) % W;
        int xE = (x + 1) % W;
        int yN = Math.max(0, y - 1);
        int yS = Math.min(H - 1, y + 1);

        // Height differences
        float dhdx = (height[y][xE] - height[y][xW]) * 0.5f;
        float dhdy = (height[yS][x] - height[yN][x]) * 0.5f;

        // Gradient magnitude = slope
        return (float) Math.sqrt(dhdx * dhdx + dhdy * dhdy);
    }
}
