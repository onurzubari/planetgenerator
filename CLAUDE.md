# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**planetgen** is a procedural planet texture generator written in Java 17+. It produces photorealistic, lighting-agnostic PBR texture sets from a single seed:
- **Outputs**: 2:1 equirectangular textures (albedo, height-16bit, normal, roughness, clouds-RGBA)
- **Core pipeline**: Spherical noise → terrain synthesis → erosion → climate/biomes → shading → export
- **Key principle**: All generation is deterministic from a seed; fully procedural with no baked shadows

## Build & Run

### Prerequisites
- Java 17+
- Gradle (or use `./gradlew`)

### Commands

```bash
# Build the project
./gradlew build

# Run with default settings (produces 4096×2048 textures with seed 123456)
./gradlew run

# Run with custom arguments (example)
./gradlew run --args="--seed 42 --resolution 2048x1024 --preset earthlike --export albedo,height,normal,clouds --out ./textures"

# Run a single test
./gradlew test --tests MappingTest

# Run all tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Build a standalone JAR
./gradlew jar
```

### CLI Usage

```bash
java -jar build/libs/planetgen-0.1.0.jar \
  --seed 987654 \
  --resolution 8192x4096 \
  --preset earthlike \
  --export albedo,height,normal,roughness,clouds \
  --out ./output
```

**Arguments**:
- `--seed`: Random seed (long) — default: 123456
- `--resolution`: Format `WxH` where W=2×H (2:1 equirectangular) — default: 4096x2048
- `--preset`: Style preset name (earthlike, desert, ice, lava, alien) — default: earthlike
- `--export`: Comma-separated list of maps to generate (albedo, height, normal, roughness, clouds) — default: all
- `--out`: Output directory path — default: output

## Project Structure

```
planetgen/
├─ src/main/java/com/onur/planetgen/
│  ├─ cli/Main.java                      # Entry point, argument parsing, orchestration
│  ├─ noise/
│  │  ├─ Noise.java                      # 3D noise interface
│  │  ├─ OpenSimplex2.java               # OpenSimplex2 3D implementation (stub)
│  │  └─ DomainWarpNoise.java            # Domain warping wrapper for adding realism
│  ├─ planet/
│  │  ├─ SphericalSampler.java           # Equirectangular ↔ spherical mapping
│  │  ├─ HeightField.java                # Terrain synthesis (fBm, ridged, detail)
│  │  ├─ ClimateModel.java               # Temperature and moisture calculation
│  │  └─ BiomeClassifier.java            # (T, M) → biome lookup + properties
│  ├─ erosion/
│  │  ├─ ThermalErosion.java             # Slope-limited material diffusion
│  │  ├─ HydraulicErosion.java           # Rainfall → flow → sediment transport
│  │  └─ FlowField.java                  # Flow vectors & accumulation cache
│  ├─ atmosphere/
│  │  └─ CloudField.java                 # Cloud alpha/opacity generation
│  ├─ render/
│  │  ├─ AlbedoRenderer.java             # Base color + height bands + biome tints
│  │  ├─ NormalMapRenderer.java          # Height → normal via finite differences
│  │  ├─ RoughnessRenderer.java          # Surface roughness from slope + humidity
│  │  └─ CloudRenderer.java              # Cloud alpha → RGBA with soft white color
│  └─ util/
│     ├─ Vec2.java, Vec3.java            # Basic vector types (records)
│     ├─ MathUtil.java                   # Clamping, interpolation helpers
│     └─ ImageUtil.java                  # PNG export (8-bit, 16-bit, ARGB)
├─ src/test/java/com/onur/planetgen/
│  └─ MappingTest.java                   # Sanity checks (aspect ratio, round-trip)
├─ src/main/resources/
│  └─ logback.xml (optional)
├─ presets/
│  ├─ presets.yml                        # Tuning parameters per preset
│  └─ biome-lut.json                     # Biome color + roughness LUT
├─ build.gradle                          # Gradle build (Groovy DSL)
├─ gradle.properties                     # JVM args, Java version
├─ settings.gradle
└─ README.md
```

## Architecture & Coordinate System

### Equirectangular Mapping (2:1)
All terrain is generated on the **unit sphere** and exported as 2:1 equirectangular (Plate Carrée) textures:

```
// Pixel center → spherical angles
u = (x + 0.5) / W  (normalized [0, 1])
v = (y + 0.5) / H
λ (longitude) = 2π·u − π  (radians, [-π, π])
φ (latitude)  = π/2 − π·v (radians, [-π/2, π/2])

// Unit sphere normal
nx = cos(φ) · cos(λ)
ny = sin(φ)
nz = cos(φ) · sin(λ)

// Feed (nx, ny, nz) to 3D noise → globally seamless, poles have natural distortion
```

### Normal Derivation (Cosine Latitude Correction)
Heights use finite differences but **must correct for latitude** to avoid distortion at poles:

```
dhdx = (hE − hW) / (2 · cos(φ))    ← latitude correction
dhdy = (hS − hN) / 2
normal = normalize(−dhdx, −dhdy, 1)
```

Always clamp `cos(φ)` near poles (e.g., `Math.max(1e-6, Math.cos(lat))`) to avoid division by zero.

## Key Implementation Details

### Terrain Synthesis Pipeline
1. **Continental base**: Low-frequency fBm (1–3 octaves) + domain warp for tectonic-scale shapes
2. **Mountains**: Ridged multifractal (`1 − |noise|`) modulated by continental mask
3. **Detail**: Mid-frequency fBm, slope-masked to avoid noise on cliffs
4. **Normalization**: Normalize to [−1, 1], apply sea level threshold (~0.02 for earthlike)

### Erosion (Two-Pass)
- **Thermal erosion**: Iteratively move material downslope where slope > talus angle (~0.5 rad)
- **Hydraulic erosion**: Rainfall → downslope flow routing → sediment capacity → transport/deposition → evaporation
- Keep iterations moderate (thermal: 12–25, hydraulic: 40–60) for performance
- **Important**: Wrap horizontally (use modulo), clamp at poles to avoid seam artifacts

### Climate & Biomes
Temperature model: `T = T₀ − k_lat·|sin(φ)| − k_alt·max(0, h − seaLevel)`
Moisture model: `M = M₀ + k_flow·flowAccum − k_slope·slope(h)`
Classification: Index (T, M) into a biome LUT → retrieve albedo, roughness, micro-detail params

### PBR Shading
- **Albedo**: Biome base color + height banding (beach→grass→rock→snow) + subtle AO from slope/curvature (~2–5%) + per-texel color jitter
- **Normal**: From height via finite differences (with latitude correction)
- **Roughness**: `clamp(biome_R + a·slope + b·(1−humidity), 0, 1)`
- **Clouds**: Separate RGBA texture; coverage from low-freq fBm, detail from ridged + Worley noise, domain-warped for wind shear

### Parallelization
Use `IntStream.range(0, H).parallel()` to process scanlines in parallel. Precompute latitude terms (sin/cos) and reuse low-frequency noise caches across threads.

## Preset System

Presets are loaded from `presets/presets.yml` (YAML format). Each preset configures:
- Sea level, continent scale, mountain intensity
- Erosion iterations (thermal & hydraulic) and rainfall rate
- Climate parameters (temp/latitude coeff, altitude coeff, moisture bias)
- Cloud coverage, warp amount, gamma

**Example (earthlike)**:
```yaml
earthlike:
  sea_level: 0.02
  continent_scale: 2.2
  mountain_intensity: 0.9
  erosion:
    thermal_iterations: 20
    hydraulic_iterations: 60
    rainfall: 0.6
  climate:
    temp_lat_coeff: 1.2
    temp_alt_coeff: 0.9
    moisture_bias: 0.1
  clouds:
    coverage: 0.55
    warp: 0.25
    gamma: 2.4
```

Biomes are configured in `presets/biome-lut.json` (JSON lookup table mapping enum names to RGB + roughness).

## Testing Strategy

**Unit tests** should verify:
- Equirectangular mapping: pixel ↔ spherical angles round-trip correctly
- Seam wrap: column 0 == column W−1 for all outputs (no visible vertical seam)
- Normalization: height ∈ [−1, 1], alpha ∈ [0, 1], RGB values valid

**Visual QA**:
- Load textures on a UV sphere (Blender, three.js). Check for:
  - No visible vertical seam
  - Natural polar distortion without ringing
  - Rivers/valleys continuous; realistic coastlines & archipelagos
  - Clouds soft and layered

**Performance checkpoints**:
- 4096×2048 (1 second per stage on modern desktop)
- 8192×4096 (3–5 minutes total with full erosion)
- 16384×8192 (long, but should be feasible with patience)

## Development Tips

1. **Tune at half-resolution**: Use 2048×1024, then upscale with the same seed for final output
2. **Adjust erosion iterations for preview**: Use 4–8 steps for quick feedback; 40–100 for final quality
3. **Avoid baked shadows in albedo**: Rely on biome tints, slope modulation, and subtle AO instead
4. **Height precision**: Save as 16-bit PNG for proper range; normals/roughness/albedo as 8-bit sRGB
5. **Guard poles in normals**: Always clamp `cos(φ)` to avoid blow-ups
6. **Cloud quality**: Apply light Gaussian smoothing to cloud output for softer, more photoreal edges

## Dependencies

- **picocli** (4.7.6): CLI argument parsing
- **JUnit 5** (5.10.2): Testing framework
- **Java standard library**: `java.awt.image` (BufferedImage), `javax.imageio` (PNG export), `java.nio.file` (I/O)

## Known TODOs (from skeleton)

- Replace `OpenSimplex2` stub with a proper 3D implementation (or use a mature library)
- Implement erosion modules (ThermalErosion, HydraulicErosion)
- Wire preset YAML/JSON loading (Jackson or SnakeYAML)
- Refine ClimateModel and implement full flow accumulation for hydraulic erosion
- Optimize ImageUtil for parallel output writing
- Consider GPU acceleration path (later phase)

## Phase Roadmap

**Phase 1 (MVP)**: Spherical fBm terrain, simple shading, height/normal export, basic clouds
**Phase 2**: Thermal + hydraulic erosion; climate and biomes; roughness map
**Phase 3**: Rivers & lakes; emissive (night lights / lava); preset library
**Phase 4**: Plate tectonics scaffolding; multi-layer clouds
**Phase 5**: GUI previewer; animation; OpenCL acceleration

## File Serialization Formats

- **Albedo/Normal/Roughness/Clouds**: 8-bit PNG (sRGB), ARGB or grayscale as appropriate
- **Height**: 16-bit PNG (TYPE_USHORT_GRAY) or EXR for future high-fidelity export
- Ensure exact pixel identity at columns 0 and W−1 (wrap seamlessly)

## References from Architecture Document

See `architecture.md` for:
- Detailed math and formulas (sections 5–10)
- Biome classification logic (section 8)
- Quality & realism checklist (section 16)
- Public API examples (section 12)
- Minimal data types (Appendix A)
