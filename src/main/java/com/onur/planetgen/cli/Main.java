package com.onur.planetgen.cli;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.onur.planetgen.config.Preset;
import com.onur.planetgen.planet.CoordinateCache;
import com.onur.planetgen.planet.ParallelHeightFieldGenerator;
import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.render.CloudRenderer;
import com.onur.planetgen.render.EmissiveRenderer;
import com.onur.planetgen.render.NormalMapRenderer;
import com.onur.planetgen.render.RenderUtil;
import com.onur.planetgen.render.SurfaceAnalyzer;
import com.onur.planetgen.atmosphere.MultiLayerCloudField;
import com.onur.planetgen.util.ImageUtil;

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
            description = "Maps to export: albedo,height,normal,roughness,clouds,emissive,ao,metallic,pbrpack,biome,vegetation,detail,snow,atmosphere,ocean,material",
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
            String[] wh = resolution.toLowerCase(Locale.ROOT).split("x");
            int width = Integer.parseInt(wh[0]);
            int heightPx = Integer.parseInt(wh[1]);
            if (width != 2 * heightPx) {
                throw new IllegalArgumentException("Resolution must be 2:1 (e.g., 4096x2048)");
            }

            Files.createDirectories(outDir);
            Set<String> exportSet = new HashSet<>();
            for (String entry : export) {
                exportSet.add(entry.toLowerCase(Locale.ROOT));
            }

            System.out.println("Loading preset: " + presetName);
            Preset preset = new Preset(presetName);
            System.out.println(preset);

            SphericalSampler sampler = new SphericalSampler(width, heightPx);
            System.out.println("Generating height field with " + presetName + " preset (parallel)...");
            long startTime = System.currentTimeMillis();
            CoordinateCache coordCache = new CoordinateCache(width, heightPx, sampler);
            float[][] heightField = ParallelHeightFieldGenerator.generateParallel(seed, sampler, preset);
            long terrainTime = System.currentTimeMillis() - startTime;

            System.out.println("Applying thermal erosion (" + preset.thermalIterations + " iterations)...");
            startTime = System.currentTimeMillis();
            com.onur.planetgen.erosion.ThermalErosion.apply(heightField, preset.thermalIterations,
                    preset.thermalTalus, preset.thermalK);
            long thermalTime = System.currentTimeMillis() - startTime;

            System.out.println("Applying hydraulic erosion (" + preset.hydraulicIterations + " iterations)...");
            startTime = System.currentTimeMillis();
            com.onur.planetgen.erosion.HydraulicErosion.apply(heightField, preset.hydraulicIterations,
                    preset.rainfall, preset.evaporation);
            long hydraulicTime = System.currentTimeMillis() - startTime;

            System.out.printf(Locale.ROOT, "Terrain: %.1fs, Thermal: %.1fs, Hydraulic: %.1fs%n",
                    terrainTime / 1000.0, thermalTime / 1000.0, hydraulicTime / 1000.0);

            SurfaceAnalyzer.SurfaceData surface = SurfaceAnalyzer.analyze(heightField, preset, seed);

            if (exportSet.contains("albedo")) {
                System.out.println("Saving albedo map...");
                ImageUtil.saveARGB(surface.albedo(), outDir.resolve("planet_albedo_" + width + "x" + heightPx + ".png"));
            }

            if (exportSet.contains("normal")) {
                System.out.println("Rendering normals...");
                int[][] normals = NormalMapRenderer.render(heightField);
                ImageUtil.saveARGB(normals, outDir.resolve("planet_normal.png"));
            }

            if (exportSet.contains("roughness")) {
                System.out.println("Saving roughness map...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.roughness()),
                        outDir.resolve("planet_roughness.png"));
            }

            if (exportSet.contains("metallic")) {
                System.out.println("Saving metallic map...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.metallic()),
                        outDir.resolve("planet_metallic.png"));
            }

            if (exportSet.contains("pbrpack")) {
                System.out.println("Packing AO/Roughness/Metallic...");
                ImageUtil.saveARGB(RenderUtil.packToRgb(surface.ambientOcclusion(),
                                surface.roughness(), surface.metallic()),
                        outDir.resolve("planet_pbr_pack.png"));
            }

            if (exportSet.contains("ao")) {
                System.out.println("Saving ambient occlusion map...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.ambientOcclusion()),
                        outDir.resolve("planet_ao.png"));
            }

            if (exportSet.contains("height")) {
                System.out.println("Saving height map...");
                ImageUtil.saveGray16(heightField, outDir.resolve("planet_height_16u.png"));
            }

            if (exportSet.contains("biome")) {
                System.out.println("Saving biome mask...");
                ImageUtil.saveARGB(surface.biomeMask(), outDir.resolve("planet_biome_mask.png"));
            }

            if (exportSet.contains("material")) {
                System.out.println("Saving material mask...");
                ImageUtil.saveARGB(surface.materialMask(), outDir.resolve("planet_material_mask.png"));
            }

            if (exportSet.contains("vegetation")) {
                System.out.println("Saving vegetation density...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.vegetation()),
                        outDir.resolve("planet_vegetation_density.png"));
            }

            if (exportSet.contains("detail")) {
                System.out.println("Saving terrain detail map...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.detail()),
                        outDir.resolve("planet_detail_map.png"));
            }

            if (exportSet.contains("snow")) {
                System.out.println("Saving snow/weathering mask...");
                ImageUtil.saveGray8(RenderUtil.toGray8(surface.snow()),
                        outDir.resolve("planet_snow_mask.png"));
            }

            if (exportSet.contains("ocean")) {
                System.out.println("Saving ocean shading map...");
                ImageUtil.saveARGB(surface.oceanShading(), outDir.resolve("planet_ocean_shading.png"));
            }

            if (exportSet.contains("atmosphere")) {
                System.out.println("Saving atmosphere overlay...");
                ImageUtil.saveARGB(surface.atmosphere(), outDir.resolve("planet_atmosphere.png"));
            }

            if (exportSet.contains("clouds")) {
                System.out.println("Generating multi-layer clouds...");
                long cloudStart = System.currentTimeMillis();
                var cloudField = MultiLayerCloudField.generateParallel(seed + 1, sampler, preset, coordCache);
                long cloudTime = System.currentTimeMillis() - cloudStart;
                System.out.printf(Locale.ROOT, "Cloud generation: %.1fs%n", cloudTime / 1000.0);
                int[][] clouds = CloudRenderer.render(cloudField);
                ImageUtil.saveARGB(clouds, outDir.resolve("planet_clouds.png"));
            }

            if (exportSet.contains("emissive")) {
                System.out.println("Rendering emissive map...");
                int[][] emissive = EmissiveRenderer.render(heightField, preset, seed);
                ImageUtil.saveARGB(emissive, outDir.resolve("planet_emissive.png"));
            }

            System.out.println("Done -> " + outDir.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error during generation:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
