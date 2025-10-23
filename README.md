# planetgen (Java 17+)

Procedural planet texture generator producing 2:1 equirectangular maps: albedo, height (16-bit), normal, roughness, and clouds (RGBA).

## Overview

**planetgen** generates photorealistic, lighting-agnostic PBR texture sets from a single seed. All outputs are fully procedural and deterministic, making it ideal for game/film use.

**Core Pipeline:**
```
3D Spherical Noise → Continental Mask → Mountains/Detail
→ Erosion (thermal+hydraulic) → Climate (temperature+precipitation)
→ Biomes → Albedo/Roughness shading → Clouds → Export 2:1 equirectangular maps
```

## Quick Start

### Prerequisites
- Java 17+
- Gradle (included via wrapper)

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew run --args="--seed 42 --resolution 2048x1024 --preset earthlike --export albedo,height,normal,clouds"
```

Outputs will be written to `./output` by default.

### Full Example
```bash
./gradlew run --args="\
  --seed 987654 \
  --resolution 8192x4096 \
  --preset earthlike \
  --export albedo,height,normal,roughness,clouds \
  --out ./textures"
```

## Usage

```bash
java -jar build/libs/planetgen-0.1.0.jar [options]
```

### Options
- `--seed LONG`: Random seed (default: 123456)
- `--resolution WxH`: Resolution in format `WxH` where W=2×H for 2:1 equirectangular (default: 4096x2048)
- `--preset NAME`: Preset style — `earthlike`, `desert`, `ice`, `lava`, `alien` (default: earthlike)
- `--export TYPES`: Comma-separated list of maps to generate: `albedo,height,normal,roughness,clouds` (default: all)
- `--out PATH`: Output directory (default: output)

## Output Files

```
./output/
  planet_albedo_8192x4096.png         (8-bit sRGB)
  planet_height_16u.png               (16-bit grayscale)
  planet_normal.png                   (8-bit tangent-space normals)
  planet_roughness.png                (8-bit grayscale)
  planet_clouds.png                   (8-bit ARGB with soft white clouds)
```

## Project Structure

See `CLAUDE.md` for detailed architecture and file organization.

### Key Directories
- `src/main/java/com/onur/planetgen/` — Main source code organized by package (noise, planet, erosion, atmosphere, render, util, cli)
- `src/test/java/com/onur/planetgen/` — Unit tests
- `presets/` — Preset configurations (YAML) and biome lookup tables (JSON)

## Development

### Run Tests
```bash
./gradlew test
```

### Run Single Test
```bash
./gradlew test --tests MappingTest
```

### Clean Build
```bash
./gradlew clean
```

## Implementation Status

This is a **skeleton project** with stub implementations. Key TODOs:

1. **Replace OpenSimplex2 stub** with a proper 3D implementation
2. **Implement ThermalErosion** — slope-limited material diffusion
3. **Implement HydraulicErosion** — rainfall flow, sediment transport, deposition
4. **Wire preset loading** — Load YAML/JSON configurations at runtime
5. **Implement ClimateModel** — Full temperature and moisture modeling
6. **Implement CloudField** — fBm + ridged + Worley noise cloud generation
7. **Add parallelization** — Use `IntStream.parallel()` for scanline processing
8. **Performance optimization** — Caching, GPU acceleration (future phase)

See `architecture.md` for detailed specifications and formulas.

## Architecture & Formulas

All terrain generation happens on the **unit sphere** and is exported as 2:1 equirectangular (Plate Carrée) textures.

### Equirectangular Mapping
```
u = (x + 0.5) / W
v = (y + 0.5) / H
longitude = 2π·u − π
latitude  = π/2 − π·v

unit_sphere_normal = (cos(lat)·cos(lon), sin(lat), cos(lat)·sin(lon))
```

### Normal Derivation (Latitude-Corrected)
```
dhdx = (hE − hW) / (2·cos(lat))    ← Critical for poles
dhdy = (hS − hN) / 2
normal = normalize(−dhdx, −dhdy, 1)
```

## Performance Targets

- **4096×2048**: ~1 minute per stage (terrain, erosion, shading, export)
- **8192×4096**: ~3–5 minutes total with full erosion
- **16384×8192**: Feasible with patience (~15–30 minutes)

Use half-resolution for tuning, then upscale with the same seed for final output.

## Quality Checklist

- Seamless wrap (no visible vertical seam at columns 0 and W−1)
- Natural polar distortion without ringing artifacts
- Coherent coastlines with fractal detail at multiple scales
- Distinct mountain ranges, foothills, and plateaus
- Erosion-carved gullies and river basins
- Realistic biome distribution with altitude variation
- Soft, layered clouds with plausible coverage
- Minimal baked shadows; lighting-agnostic albedo

## References

- `architecture.md` — Detailed specifications, math, and design rationale
- `planetgen_gradle_project_skeleton_java_17.md` — Complete skeleton code reference
- `CLAUDE.md` — Development guide for Claude Code instances

## License

Author: Onur Zubari

---

**Status**: Work in Progress (Phase 1 MVP: spherical terrain synthesis + basic shading)
