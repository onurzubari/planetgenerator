package com.onur.planetgen.hydrology;

import com.onur.planetgen.erosion.FlowField;

/**
 * Detects river features from flow field accumulation.
 * Rivers are determined by high flow accumulation values.
 */
public final class RiverDetector {
    private RiverDetector() {}

    /**
     * Identify river pixels based on flow accumulation threshold.
     * Rivers indicate where water concentrates due to topography.
     *
     * @param flowField flow data with accumulation values
     * @param threshold flow accumulation threshold (typical: 0.3-0.5 normalized)
     * @return river mask: 1.0 where rivers exist, 0.0 elsewhere
     */
    public static float[][] detectRivers(FlowField flowField, double threshold) {
        int H = flowField.accum.length;
        int W = flowField.accum[0].length;
        float[][] rivers = new float[H][W];

        // Find min and max accumulation for normalization
        float minAccum = Float.POSITIVE_INFINITY;
        float maxAccum = Float.NEGATIVE_INFINITY;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float accum = flowField.accum[y][x];
                if (accum < minAccum) minAccum = accum;
                if (accum > maxAccum) maxAccum = accum;
            }
        }

        // Normalize accumulation to [0, 1]
        float range = Math.max(maxAccum - minAccum, 1e-6f);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float normalized = (flowField.accum[y][x] - minAccum) / range;

                // River exists if accumulation exceeds threshold
                if (normalized >= threshold) {
                    // Width inversely proportional to normalized accumulation
                    // Higher accumulation = wider river
                    rivers[y][x] = Math.min(1.0f, normalized);
                } else {
                    rivers[y][x] = 0.0f;
                }
            }
        }

        return rivers;
    }

    /**
     * Smooth river mask for more natural appearance.
     * Applies box filter to create smooth river curves.
     */
    public static float[][] smoothRivers(float[][] rivers, int kernelRadius) {
        int H = rivers.length;
        int W = rivers[0].length;
        float[][] smoothed = new float[H][W];

        int kernelSize = 2 * kernelRadius + 1;
        float kernelSum = kernelSize * kernelSize;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float sum = 0;

                for (int dy = -kernelRadius; dy <= kernelRadius; dy++) {
                    for (int dx = -kernelRadius; dx <= kernelRadius; dx++) {
                        int ny = y + dy;
                        int nx = (x + dx + W) % W; // Wrap horizontally

                        if (ny >= 0 && ny < H) {
                            sum += rivers[ny][nx];
                        }
                    }
                }

                smoothed[y][x] = sum / kernelSum;
            }
        }

        return smoothed;
    }
}
