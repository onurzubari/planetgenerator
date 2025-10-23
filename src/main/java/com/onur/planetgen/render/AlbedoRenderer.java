package com.onur.planetgen.render;

import com.onur.planetgen.config.Preset;

/**
 * Backwards-compatible wrapper that exposes the physically based albedo generated
 * by {@link SurfaceAnalyzer}. The heavy lifting is performed once in the shared
 * surface analysis stage and can be re-used by other exporters.
 */
public final class AlbedoRenderer {
    private AlbedoRenderer() {}

    public static int[][] render(float[][] height) {
        return renderWithHydrology(height, null, 0L);
    }

    public static int[][] renderWithHydrology(float[][] height, Preset preset) {
        return renderWithHydrology(height, preset, 0L);
    }

    public static int[][] renderWithHydrology(float[][] height, Preset preset, long seed) {
        return SurfaceAnalyzer.analyze(height, preset, seed).albedo();
    }
}
