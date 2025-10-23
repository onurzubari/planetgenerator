# Architecture: Realistic Procedural Planet Texture Generator (Java)

**Author:** Onur Zubari  
**Language:** Java 17+  
**Outputs:** 2:1 equirectangular textures — **albedo**, **height (16‑bit)**, **normal**, **roughness**, **clouds (RGBA)**, optional **night/emissive**  
**Primary goal:** Photorealistic, lighting‑agnostic PBR texture set for game/film use, fully procedural/deterministic from a seed.

---

## 1) High‑Level Overview

**Pipeline**
```
3D Spherical Noise → Continental Mask → Mountains/Detail → Erosion (thermal+hydraulic)
→ Climate (temperature+precipitation) → Biomes → Albedo/Roughness shading
→ Clouds (separate RGBA) → Normals from Height → Export 2:1 maps
```

**Core principles**
- Generate on the **unit sphere**; **export** to 2:1 equirectangular.
- Keep **albedo lighting‑agnostic** (minimal baked shadows); realism from micro‑variation and erosion.
- Separate **clouds** into their own texture for parallax/animation.
- Deterministic outputs with a **seed**; configurable **presets**.

---

## 2) Functional Requirements

- **Inputs**: seed, resolution (e.g., 4096×2048, 8192×4096), preset (earthlike/desert/ice/lava/alien), tuning parameters (sea level, mountain intensity, erosion iterations, climate bias).
- **Outputs** (all 2:1 equirectangular):
  - `planet_albedo.png` (8‑bit)
  - `planet_height_16u.png` (16‑bit grayscale)
  - `planet_normal.png` (8‑bit RGB tangent‑space)
  - `planet_roughness.png` (8‑bit)
  - `planet_clouds.png` (8‑bit RGBA)
  - optional `planet_night.png` (8‑bit emissive)
- CLI usage; (optional) preview UI.
- Multithreaded generation; reproducible results.

---

## 3) Non‑Functional Requirements

- **Performance**: 8k×4k within minutes on modern desktop (parallelized); scalable to 16k with patience.
- **Quality**: Seamless wrap; natural polar behavior; erosion‑shaped landforms; believable biomes; realistic cloud morphology.
- **Maintainability**: Modular packages; clean interfaces; presets and LUTs as data files.

---

## 4) Project Structure

```
com.onur.planetgen
 ├─ core/
 │   ├─ noise/
 │   │   ├─ Noise.java
 │   │   ├─ OpenSimplex2.java
 │   │   ├─ DomainWarpNoise.java
 │   │   └─ NoiseUtils.java
 │   ├─ planet/
 │   │   ├─ HeightField.java
 │   │   ├─ BiomeClassifier.java
 │   │   ├─ ClimateModel.java
 │   │   └─ SphericalSampler.java
 │   ├─ erosion/
 │   │   ├─ ThermalErosion.java
 │   │   ├─ HydraulicErosion.java
 │   │   └─ FlowField.java
 │   ├─ render/
 │   │   ├─ AlbedoRenderer.java
 │   │   ├─ NormalMapRenderer.java
 │   │   ├─ RoughnessRenderer.java
 │   │   └─ CloudRenderer.java
 │   ├─ atmosphere/
 │   │   └─ CloudField.java
 │   └─ util/
 │       ├─ Vec2.java
 │       ├─ Vec3.java
 │       ├─ MathUtil.java
 │       └─ ImageUtil.java
 ├─ cli/
 │   └─ Main.java
 └─ ui/
     └─ Previewer.java (optional)
```

---

## 5) Core Math & Coordinate System

**Equirectangular mapping (2:1)**
```
// pixel center
u = (x + 0.5) / W
v = (y + 0.5) / H
λ (lon) = 2πu − π
φ (lat) = π/2 − πv

// unit sphere
nx = cos φ · cos λ
ny = sin φ
nz = cos φ · sin λ
```
Feed `(nx,ny,nz)` to 3D noise → **globally seamless**; poles have natural distortion.

**Normal derivation** (cosine latitude correction):
```
dhdx = (hE − hW) / (2 · cos φ)
dhdy = (hS − hN) / 2
n = normalize( −dhdx, −dhdy, 1 )
```

---

## 6) Terrain Synthesis

### 6.1 Continental Base
- Low‑frequency **OpenSimplex2** fBm (1–3 octaves) for tectonic‑scale shapes.
- **Domain warp** with another low‑freq noise to break symmetry.
- Result `C ∈ [−1,1]`; shift/scale around **sea level** threshold.

**Formula**
```
C0 = fBm_low(OS2, s·P)
W  = fBm_low(OS2, sW·P)
C  = C0 + warp_amount * W
height0 = k0 * C
```

### 6.2 Mountains & Ridges
- Ridged multifractal (`ridge = 1 − |fBm|`), modulated by continental mask.
```
R = ridge(OS2, sR·P)
height1 = height0 + kR * mask_land(C) * R
```

### 6.3 Detail & Microstructure
- Mid/high‑frequency simplex/perlin; slope‑masked.
```
D = fBm_mid(OS2, sD·P)
S = slope(height1)
height = height1 + kD * S^γ * D
```

### 6.4 Normalization & Sea Level
Normalize height to [-1,1] → apply `seaLevel` (≈ 0 by default).

---

## 7) Erosion (Realism Pass)

### 7.1 Thermal Erosion (talus)
- Iteratively relax steep slopes: move material from high to low if local slope > `talusAngle`.

**Pseudo**
```
repeat N_thermal times:
  for each cell i:
    for neighbors j:
      Δh = h[i] − h[j]
      if Δh > talus:
         m = k_thermal * (Δh − talus)
         h[i] -= m; h[j] += m/nb
```

### 7.2 Hydraulic Erosion
- Rainfall → flow routing → sediment capacity → transport/deposition → evaporation.

**Key fields**: water W, sediment S, flow F, capacity C.

**Pseudo**
```
repeat N_hydro times:
  W += rainfall
  F = downSlopeFlux(h + W)
  C = k_cap * |F| * slope(h)
  if S < C: pick up from bed (erode); else deposit
  advect(W,S) along F
  W *= (1 − evap)
```

**Implementation notes**
- Use 4‑ or 8‑neighbor stencil; wrap horizontally; clamp at poles.
- Keep iterations moderate (e.g., 30–100) for performance.
- Cache/decimate for preview; full res for final bake.

---

## 8) Climate & Biomes

### 8.1 Temperature Model
```
T = T0 − k_lat * |sin φ| − k_alt * max(0, h − seaLevel)
```

### 8.2 Moisture Model
- Base moisture from low‑freq noise + rain‑shadow from flow.
```
M = M0 + k_flow * flowAccum − k_slope * slope(h)
```

### 8.3 Biome Classification
- Use `(T, M)` to index a LUT.

**Example (simplified)**
```
if T < 0.2:
  biome = (M < 0.3) ? TUNDRA : TAIGA
else if T < 0.6:
  biome = (M < 0.3) ? STEPPE : FOREST
else:
  biome = (M < 0.3) ? DESERT : JUNGLE
```

**Biome properties**: base color, roughness, micro‑detail amplitude, snow line behavior.

---

## 9) Shading & PBR Maps

### 9.1 Albedo
- Base color from biome.
- Height bands: beach → grass → rock → snow; latitude tints.
- Ambient occlusion proxy from slope/curvature (very subtle ~2–5%).
- Color jitter per‑texel for micro‑variation.

### 9.2 Roughness
```
R = clamp( biomeR + a*slope + b*(1−humidity), 0, 1 )
```

### 9.3 Normal
- From height via finite differences (cos φ scaling as above).

### 9.4 Optional Emissive (Night Lights / Lava)
- For earthlike: population mask from proximity to rivers/coasts.
- For lava worlds: thresholded ridged noise with glow falloff.

---

## 10) Clouds (RGBA, separate texture)

**Goal**: realistic stratocumulus/cirrus morphology; soft edges; variable coverage.

**Recipe**
1. **Coverage**: low‑freq fBm controls macro distribution.
2. **Detail**: ridged fBm + Worley (cellular) for billows/breakup.
3. **Wind shear**: directional domain warp (different per latitude band).
4. **Opacity shaping**:
```
α = saturate( pow( fbmRidged(P_warped), γ ) − threshold )
```
5. **Color**: near‑white (e.g., 230/240/255), use α only; leave RGB nearly neutral.
6. **Animation** (optional): P_warped += t * windVector(φ).

---

## 11) Exporters

- **Albedo/Normal/Roughness/Clouds**: 8‑bit PNG (sRGB), premultiplied alpha for clouds if desired.
- **Height**: 16‑bit PNG (TYPE_USHORT_GRAY) or EXR for high fidelity.
- Ensure exact pixel identity at columns 0 and W−1 (wrap) → no visible seam.
- Optional **filmic tonemapping** for albedo (Reinhard/ACES‑like) before save.

---

## 12) Public APIs (Java)

```java
interface HeightField {
  float heightAt(int x, int y); // normalized [-1,1]
  float[][] data();
}

final class ClimateModel {
  ClimateSample sample(int x, int y, float height, double latRad);
}

final class BiomeClassifier {
  Biome classify(double temp, double moist);
}

final class AlbedoRenderer {
  int argbAt(int x, int y, float height, Biome biome, ClimateSample c);
}

final class CloudField {
  float alphaAt(int x, int y); // 0..1
}

final class NormalMapRenderer {
  int argbAt(int x, int y, float[][] height);
}
```

**CLI (Main.java)**
```
planetgen \
 --seed 123456 \
 --resolution 8192x4096 \
 --preset earthlike \
 --sea 0.03 --mountains 0.8 --erosion-steps 60 \
 --export albedo,height,normal,roughness,clouds \
 --out ./textures
```

---

## 13) Configuration Files

### 13.1 Presets (YAML)
```yaml
presets:
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

### 13.2 Biome LUT (JSON)
```json
{
  "TUNDRA":   {"rgb": [170, 175, 180], "rough": 0.8},
  "TAIGA":    {"rgb": [74, 102, 74],   "rough": 0.7},
  "STEPPE":   {"rgb": [180, 165, 120], "rough": 0.6},
  "FOREST":   {"rgb": [72, 110, 78],   "rough": 0.7},
  "DESERT":   {"rgb": [206, 185, 140], "rough": 0.5},
  "JUNGLE":   {"rgb": [62, 96, 68],    "rough": 0.75},
  "ICE":      {"rgb": [230, 235, 240], "rough": 0.9},
  "SWAMP":    {"rgb": [80, 92, 70],    "rough": 0.85],
  "RAINFOREST":{"rgb": [58, 90, 64],    "rough": 0.8}
}
```

---

## 14) Parallelization & Performance

- Process scanlines in parallel (`IntStream.range(0,H).parallel()`).
- Reuse/caches for low‑frequency noise; precompute latitude terms (sinφ, cosφ).
- Use primitive arrays; avoid boxing.
- Consider tile‑based erosion to improve cache locality.
- Optional GPU path later (LWJGL/OpenCL).

---

## 15) Validation & Testing

**Unit tests**
- Mapping correctness: longitude/latitude ↔ pixel indices round‑trip.
- Seam wrap: column 0 == column W−1 for all outputs.
- Normalization: height ∈ [−1,1]; alpha ∈ [0,1].

**Visual QA**
- Load textures on a UV sphere (Blender/three.js previewer). Check:
  - No visible vertical seam.
  - Natural polar distortion without ringing.
  - Rivers/valleys continuous; coastlines plausible; clouds soft, layered.

**Edge cases**
- Extreme presets (all desert/ice). Ensure no NaNs; stable erosion.

---

## 16) Quality & Realism Checklist

- Coastlines: fractal detail at multiple scales; small archipelagos present.
- Orography: distinct ranges, foothills, plateaus; erosion gullies.
- Hydrography: coherent basins; river deltas near coasts.
- Biomes: latitudinal bands with altitude/precipitation variations.
- Albedo: minimal directional shadowing; micro‑variation; subtle AO.
- Clouds: believable scales (macro fronts + meso billows), soft alpha; higher coverage along convergence zones.

---

## 17) Roadmap (Phased)

**Phase 1 (MVP)**: Spherical fBm terrain, simple shading, height/normal export, basic clouds.  
**Phase 2**: Thermal+hydraulic erosion; climate and biomes; roughness map.  
**Phase 3**: Rivers & lakes; emissive (night lights / lava); preset library.  
**Phase 4**: Plate tectonics scaffolding; better cloud morphology (multi‑layer).  
**Phase 5**: GUI previewer; keyframe animation for clouds; OpenCL acceleration.

---

## 18) Implementation Notes & Tips

- Work at half‑res for tuning; upscale with same seed for final.
- Keep erosion iterations adjustable; preview with 4–8 steps; final 40–100.
- Guard `cos φ` near poles (ε clamp) to avoid blow‑ups in normal derivation.
- For albedo, avoid strong, baked shadows; rely on biome tints and slope‑based modulation.
- Save height as **16‑bit**; normals/roughness/albedo as 8‑bit sRGB.
- Slight Gaussian smoothing of clouds improves photoreal edges.

---

## 19) Glossary

- **Equirectangular (Plate Carrée)**: Mapping of lon/lat to a 2:1 rectangle.
- **Domain warp**: Perturbing noise coordinates by another noise to add realism.
- **Ridged multifractal**: 1−|noise| shape for sharp mountain ridges.
- **fBm**: Fractal Brownian Motion — sum of octaves of noise at different scales.
- **Talus angle**: Slope threshold for material to slide in thermal erosion.

---

## 20) Acceptance Criteria

- Command line run produces all requested textures with consistent seed.
- Visual inspection on a sphere shows no seams and realistic geography.
- Cloud texture overlays cleanly; believable coverage and structure.
- Performance targets met at 8k×4k with parallelism enabled.

---

## 21) Example CLI Session

```
java -jar planetgen.jar \
  --seed 987654 \
  --resolution 8192x4096 \
  --preset earthlike \
  --sea 0.02 --mountains 0.9 \
  --erosion-thermal 25 --erosion-hydraulic 60 --rainfall 0.55 \
  --export albedo,height,normal,roughness,clouds \
  --out ./textures
```

**Output files**
```
./textures/
  planet_albedo_8192x4096.png
  planet_height_16u.png
  planet_normal.png
  planet_roughness.png
  planet_clouds.png
```

---

### Appendix A: Minimal Data Types

```java
record ClimateSample(double temp, double moist, double humidity) {}

enum Biome { TUNDRA, TAIGA, STEPPE, FOREST, DESERT, JUNGLE, ICE, SWAMP, RAINFOREST }

final class Preset { /* fields for terrain/climate/cloud params */ }
```

### Appendix B: Noise Selection Guide
- Use **OpenSimplex2** for base; add **ridged** for mountains; **Worley** for cloud breakup.
- Domain warp low‑frequency features by ~5–15% of scale for natural coastlines.

### Appendix C: Polar Cleanup
- Apply small lat‑dependent blur near |φ| > 80° to suppress aliasing.
- Clamp α in clouds near poles to avoid pinching.

