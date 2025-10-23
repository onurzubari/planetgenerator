package com.onur.planetgen.cli;

import picocli.CommandLine;
import java.nio.file.*;
import java.util.*;

import com.onur.planetgen.planet.SphericalSampler;
import com.onur.planetgen.planet.HeightField;
import com.onur.planetgen.render.AlbedoRenderer;
import com.onur.planetgen.render.NormalMapRenderer;
import com.onur.planetgen.render.RoughnessRenderer;
import com.onur.planetgen.render.CloudRenderer;
import com.onur.planetgen.atmosphere.CloudField;
import com.onur.planetgen.util.ImageUtil;

@CommandLine.Command(name = "planetgen", mixinStandardHelpOptions = true,
        description = "Procedural planet texture generator (2:1 equirectangular)")
public class Main implements Runnable {

    @CommandLine.Option(names = "--seed", description = "Random seed", defaultValue = "123456")
    long seed;

    @CommandLine.Option(names = "--resolution", description = "WxH (2:1)", defaultValue = "4096x2048")
    String resolution;

    @CommandLine.Option(names = "--preset", description = "Style preset", defaultValue = "earthlike")
    String preset;

    @CommandLine.Option(names = "--export", split = ",", description = "Maps to export: albedo,height,normal,roughness,clouds",
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
            int W = Integer.parseInt(wh[0]);
            int H = Integer.parseInt(wh[1]);
            if (W != 2 * H) throw new IllegalArgumentException("Resolution must be 2:1 (e.g., 4096x2048)");
            Files.createDirectories(outDir);

            // TODO: load preset yaml/json (for now just stub constants in code)

            var sampler = new SphericalSampler(W, H);
            var height = HeightField.generate(seed, sampler /* + params */);

            if (export.contains("albedo")) {
                var argb = AlbedoRenderer.render(height /* + biome/climate */);
                ImageUtil.saveARGB(argb, outDir.resolve("planet_albedo_" + W + "x" + H + ".png"));
            }
            if (export.contains("normal")) {
                var argbN = NormalMapRenderer.render(height);
                ImageUtil.saveARGB(argbN, outDir.resolve("planet_normal.png"));
            }
            if (export.contains("roughness")) {
                var gray = RoughnessRenderer.render(height /* + humidity/slope */);
                ImageUtil.saveGray8(gray, outDir.resolve("planet_roughness.png"));
            }
            if (export.contains("height")) {
                ImageUtil.saveGray16(height, outDir.resolve("planet_height_16u.png"));
            }
            if (export.contains("clouds")) {
                CloudField clouds = CloudField.generate(seed + 1, sampler /* + params */);
                var argbC = CloudRenderer.render(clouds);
                ImageUtil.saveARGB(argbC, outDir.resolve("planet_clouds.png"));
            }
            System.out.println("Done → " + outDir.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
