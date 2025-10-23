package com.onur.planetgen.cli;

import picocli.CommandLine;
import java.nio.file.*;
import java.util.*;

import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.planet.HeightField;
import com.onur.planetgen.planet.ParallelHeightFieldGenerator;
import com.onur.planetgen.planet.CoordinateCache;
import com.onur.planetgen.render.AlbedoRenderer;
import com.onur.planetgen.render.NormalMapRenderer;
import com.onur.planetgen.render.RoughnessRenderer;
import com.onur.planetgen.render.CloudRenderer;
import com.onur.planetgen.render.EmissiveRenderer;
import com.onur.planetgen.render.AmbientOcclusionRenderer;
import com.onur.planetgen.atmosphere.CloudField;
import com.onur.planetgen.atmosphere.MultiLayerCloudField;
import com.onur.planetgen.util.ImageUtil;
import com.onur.planetgen.config.Preset;

@CommandLine.Command(name = "planetgen", mixinStandardHelpOptions = true,
        description = "Procedural planet texture generator (2:1 equirectangular)")
public class Main implements Runnable {

    @CommandLine.Option(names = "--seed", description = "Random seed", defaultValue = "123456")
    long seed;

    @CommandLine.Option(names = "--resolution", description = "WxH (2:1)", defaultValue = "4096x2048")
    String resolution;

    @CommandLine.Option(names = "--preset", description = "Style preset: earthlike, desert, ice, lava, alien",
            defaultValue = "earthlike")
    String presetName;

    @CommandLine.Option(names = "--export", split = ",",
            description = "Maps to export: albedo,height,normal,roughness,clouds,emissive,ao",
            defaultValue = "albedo,height,normal,roughness,clouds")
    List<String> export;

    @CommandLine.Option(names = "--out", description = "Output folder", defaultValue = "output")
    Path outDir;

    public static void main(String[] args) {
        int ec = new CommandLine(new Main()).execute(args);
        System.exit(ec);
    }

    @Override
    public void run() {
        try {
            // Parse resolution
            String[] wh = resolution.toLowerCase(Locale.ROOT).split("x");
            int W = Integer.parseInt(wh[0]);
            int H = Integer.parseInt(wh[1]);
            if (W != 2 * H) throw new IllegalArgumentException("Resolution must be 2:1 (e.g., 4096x2048)");
            Files.createDirectories(outDir);

            // Load preset (Phase 3 feature)
            System.out.println("Loading preset: " + presetName);
            Preset preset = new Preset(presetName);
            System.out.println(preset);

            // Generate terrain and apply erosion with parallel processing
            var sampler = new SphericalSampler(W, H);
            System.out.println("Generating height field with " + presetName + " preset (parallel)...");
            long startTime = System.currentTimeMillis();
            var coordCache = new CoordinateCache(W, H, sampler);
            var height = ParallelHeightFieldGenerator.generateParallel(seed, sampler, preset);
            long terrainTime = System.currentTimeMillis() - startTime;

            // Apply erosion
            System.out.println("Applying thermal erosion (" + preset.thermalIterations + " iterations)...");
            startTime = System.currentTimeMillis();
            com.onur.planetgen.erosion.ThermalErosion.apply(height, preset.thermalIterations,
                    preset.thermalTalus, preset.thermalK);
            long thermalTime = System.currentTimeMillis() - startTime;

            System.out.println("Applying hydraulic erosion (" + preset.hydraulicIterations + " iterations)...");
            startTime = System.currentTimeMillis();
            com.onur.planetgen.erosion.HydraulicErosion.apply(height, preset.hydraulicIterations,
                    preset.rainfall, preset.evaporation);
            long hydraulicTime = System.currentTimeMillis() - startTime;

            System.out.println(String.format("Terrain: %.1fs, Thermal: %.1fs, Hydraulic: %.1fs",
                    terrainTime / 1000.0, thermalTime / 1000.0, hydraulicTime / 1000.0));

            // Export requested maps
            if (export.contains("albedo")) {
                System.out.println("Rendering albedo with hydrology...");
                var argb = AlbedoRenderer.renderWithHydrology(height, preset);
                ImageUtil.saveARGB(argb, outDir.resolve("planet_albedo_" + W + "x" + H + ".png"));
            }

            if (export.contains("normal")) {
                System.out.println("Rendering normals...");
                var argbN = NormalMapRenderer.render(height);
                ImageUtil.saveARGB(argbN, outDir.resolve("planet_normal.png"));
            }

            if (export.contains("roughness")) {
                System.out.println("Rendering roughness...");
                var gray = RoughnessRenderer.render(height);
                ImageUtil.saveGray8(gray, outDir.resolve("planet_roughness.png"));
            }

            if (export.contains("height")) {
                System.out.println("Exporting height map...");
                ImageUtil.saveGray16(height, outDir.resolve("planet_height_16u.png"));
            }

            if (export.contains("clouds")) {
                System.out.println("Generating multi-layer clouds...");
                long cloudStart = System.currentTimeMillis();
                var cloudField = MultiLayerCloudField.generateParallel(seed + 1, sampler, preset, coordCache);
                long cloudTime = System.currentTimeMillis() - cloudStart;
                System.out.println(String.format("Cloud generation: %.1fs", cloudTime / 1000.0));
                var argbC = CloudRenderer.render(cloudField);
                ImageUtil.saveARGB(argbC, outDir.resolve("planet_clouds.png"));
            }

            if (export.contains("emissive")) {
                System.out.println("Rendering emissive map...");
                var argbE = EmissiveRenderer.render(height, preset, seed);
                ImageUtil.saveARGB(argbE, outDir.resolve("planet_emissive.png"));
            }

            if (export.contains("ao")) {
                System.out.println("Rendering ambient occlusion...");
                long aoStart = System.currentTimeMillis();
                var ao = AmbientOcclusionRenderer.render(height);
                ao = AmbientOcclusionRenderer.smooth(ao, 1);
                long aoTime = System.currentTimeMillis() - aoStart;
                System.out.println(String.format("AO generation: %.1fs", aoTime / 1000.0));
                ImageUtil.saveGray8(ao, outDir.resolve("planet_ao.png"));
            }

            System.out.println("Done → " + outDir.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error during generation:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
