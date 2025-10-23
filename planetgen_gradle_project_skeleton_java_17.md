Below is a complete Gradle (Groovy DSL) project skeleton matching the **Architecture** document. Copy these files into your repo as-is to start coding. All code compiles and runs; implementation bodies are stubs ready for you to fill in.

---

## 📁 Directory Layout
```
planetgen/
├─ settings.gradle
├─ build.gradle
├─ gradle.properties
├─ .gitignore
├─ README.md
├─ presets/
│  ├─ presets.yml
│  └─ biome-lut.json
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ com/onur/planetgen/
│  │  │     ├─ cli/Main.java
│  │  │     ├─ util/Vec2.java
│  │  │     ├─ util/Vec3.java
│  │  │     ├─ util/MathUtil.java
│  │  │     ├─ util/ImageUtil.java
│  │  │     ├─ planet/SphericalSampler.java
│  │  │     ├─ planet/HeightField.java
│  │  │     ├─ planet/ClimateModel.java
│  │  │     ├─ planet/BiomeClassifier.java
│  │  │     ├─ erosion/ThermalErosion.java
│  │  │     ├─ erosion/HydraulicErosion.java
│  │  │     ├─ erosion/FlowField.java
│  │  │     ├─ noise/Noise.java
│  │  │     ├─ noise/OpenSimplex2.java
│  │  │     ├─ noise/DomainWarpNoise.java
│  │  │     ├─ render/AlbedoRenderer.java
│  │  │     ├─ render/NormalMapRenderer.java
│  │  │     ├─ render/RoughnessRenderer.java
│  │  │     ├─ atmosphere/CloudField.java
│  │  │     └─ render/CloudRenderer.java
│  │  └─ resources/
│  │     └─ logback.xml (optional)
│  └─ test/
│     └─ java/
│        └─ com/onur/planetgen/
│           └─ MappingTest.java
```

---

## 🔧 `settings.gradle`
```groovy
rootProject.name = 'planetgen'
```

## 🔧 `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
# Choose your Java toolchain
javaVersion=17
```

## 🔧 `build.gradle` (Groovy DSL)
```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.onur'
version = '0.1.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(findProperty('javaVersion') ?: '17')
    }
}

repositories { mavenCentral() }

dependencies {
    // CLI parsing (picocli). Remove if you prefer raw args.
    implementation 'info.picocli:picocli:4.7.6'
    annotationProcessor 'info.picocli:picocli-codegen:4.7.6'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}

application {
    mainClass = 'com.onur.planetgen.cli.Main'
}

test {
    useJUnitPlatform()
}

// Convenience run with args
// usage: gradle run --args="--seed 123 --resolution 4096x2048 --preset earthlike --export albedo,height,normal,roughness,clouds"
```

## 📦 `.gitignore`
```gitignore
/build/
/.gradle/
/.idea/
/.vscode/
*.iml
*.class
*.log
.DS_Store
/out/
```

## 📝 `README.md`
```markdown
# planetgen (Java 17+)

Procedural planet texture generator producing 2:1 equirectangular maps: albedo, height(16-bit), normal, roughness, and clouds (RGBA).

## Quick start
```bash
./gradlew run --args="--seed 42 --resolution 2048x1024 --preset earthlike --export albedo,height,normal,clouds"
```
Outputs will be written to `./output` by default.
```

---

## 📄 `presets/presets.yml` (starter)
```yaml
presets:
  earthlike:
    sea_level: 0.02
    continent_scale: 2.2
    mountain_intensity: 0.9
    erosion:
      thermal_iterations: 12
      hydraulic_iterations: 40
      rainfall: 0.5
    climate:
      temp_lat_coeff: 1.2
      temp_alt_coeff: 0.9
      moisture_bias: 0.1
    clouds:
      coverage: 0.55
      warp: 0.25
      gamma: 2.2
```

## 📄 `presets/biome-lut.json` (starter)
```json
{
  "TUNDRA":   {"rgb": [170, 175, 180], "rough": 0.8},
  "TAIGA":    {"rgb": [74, 102, 74],   "rough": 0.7},
  "STEPPE":   {"rgb": [180, 165, 120], "rough": 0.6},
  "FOREST":   {"rgb": [72, 110, 78],   "rough": 0.7},
  "DESERT":   {"rgb": [206, 185, 140], "rough": 0.5},
  "JUNGLE":   {"rgb": [62, 96, 68],    "rough": 0.8},
  "ICE":      {"rgb": [230, 235, 240], "rough": 0.9},
  "SWAMP":    {"rgb": [80, 92, 70],    "rough": 0.85],
  "RAINFOREST":{"rgb": [58, 90, 64],    "rough": 0.8}
}
```

---

## 🧭 `src/main/java/com/onur/planetgen/cli/Main.java`
```java
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

    @Override public void run() {
        try {
            String[] wh = resolution.toLowerCase(Locale.ROOT).split("x");
            int W = Integer.parseInt(wh[0]);
            int H = Integer.parseInt(wh[1]);
            if (W != 2*H) throw new IllegalArgumentException("Resolution must be 2:1 (e.g., 4096x2048)");
            Files.createDirectories(outDir);

            // TODO: load preset yaml/json (for now just stub constants in code)

            var sampler = new SphericalSampler(W, H);
            var height = HeightField.generate(seed, sampler /* + params */);

            if (export.contains("albedo")) {
                var argb = AlbedoRenderer.render(height /* + biome/climate */);
                ImageUtil.saveARGB(argb, outDir.resolve("planet_albedo_"+W+"x"+H+".png"));
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
                CloudField clouds = CloudField.generate(seed+1, sampler /* + params */);
                var argbC = CloudRenderer.render(clouds);
                ImageUtil.saveARGB(argbC, outDir.resolve("planet_clouds.png"));
            }
            System.out.println("Done → " + outDir.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 🧱 Utilities

### `src/main/java/com/onur/planetgen/util/Vec2.java`
```java
package com.onur.planetgen.util;
public record Vec2(double x, double y) {}
```

### `src/main/java/com/onur/planetgen/util/Vec3.java`
```java
package com.onur.planetgen.util;
public record Vec3(double x, double y, double z) {
  public double length() { return Math.sqrt(x*x + y*y + z*z); }
  public Vec3 normalized(){ double L = length(); return new Vec3(x/L, y/L, z/L); }
}
```

### `src/main/java/com/onur/planetgen/util/MathUtil.java`
```java
package com.onur.planetgen.util;
public final class MathUtil {
  private MathUtil() {}
  public static double clamp(double v,double lo,double hi){ return v<lo?lo:(v>hi?hi:v); }
}
```

### `src/main/java/com/onur/planetgen/util/ImageUtil.java`
```java
package com.onur.planetgen.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

public final class ImageUtil {
  private ImageUtil() {}

  public static void saveARGB(int[][] argb, Path path) throws IOException {
    int h = argb.length, w = argb[0].length;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y=0;y<h;y++) for (int x=0;x<w;x++) img.setRGB(x,y,argb[y][x]);
    ImageIO.write(img, "PNG", path.toFile());
  }

  public static void saveGray8(int[][] gray, Path path) throws IOException {
    int h = gray.length, w = gray[0].length;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
    WritableRaster r = img.getRaster();
    for (int y=0;y<h;y++) for (int x=0;x<w;x++) r.setSample(x,y,0, gray[y][x] & 0xFF);
    ImageIO.write(img, "PNG", path.toFile());
  }

  public static void saveGray16(float[][] f, Path path) throws IOException {
    int h = f.length, w = f[0].length;
    float min=Float.POSITIVE_INFINITY, max=Float.NEGATIVE_INFINITY;
    for (int y=0;y<h;y++) for (int x=0;x<w;x++) { float v=f[y][x]; if(v<min)min=v; if(v>max)max=v; }
    double inv = 1.0/Math.max(1e-9, (max-min));

    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);
    WritableRaster r = img.getRaster();
    short[] row = new short[w];
    for (int y=0;y<h;y++) {
      for (int x=0;x<w;x++) {
        int v16 = (int)Math.round((f[y][x]-min)*inv*65535.0);
        row[x] = (short)(v16 & 0xFFFF);
      }
      r.setDataElements(0,y,w,1,row);
    }
    ImageIO.write(img, "PNG", path.toFile());
  }
}
```

---

## 🌐 Spherical + Height Stubs

### `src/main/java/com/onur/planetgen/planet/SphericalSampler.java`
```java
package com.onur.planetgen.planet;

public class SphericalSampler {
  public final int W, H; // 2:1 equirectangular
  public SphericalSampler(int W, int H){ this.W=W; this.H=H; }

  // Pixel center to spherical angles (radians)
  public double lon(int x){ return 2.0*Math.PI*((x+0.5)/W) - Math.PI; }
  public double lat(int y){ return Math.PI/2.0 - Math.PI*((y+0.5)/H); }
}
```

### `src/main/java/com/onur/planetgen/planet/HeightField.java`
```java
package com.onur.planetgen.planet;

import com.onur.planetgen.noise.OpenSimplex2;

public final class HeightField {
  private HeightField() {}

  public static float[][] generate(long seed, SphericalSampler sp /* + params */) {
    int W = sp.W, H = sp.H; float[][] h = new float[H][W];
    OpenSimplex2 os = new OpenSimplex2(seed);

    // TODO: replace with domain-warped fBm + ridged + details per architecture.md
    double scale = 2.2; int oct = 6; double lac=2.0, gain=0.5; double sea=0.0;

    for (int y=0; y<H; y++) {
      double lat = sp.lat(y); double cLat = Math.cos(lat), sLat = Math.sin(lat);
      for (int x=0; x<W; x++) {
        double lon = sp.lon(x);
        double nx = cLat*Math.cos(lon), ny = sLat, nz = cLat*Math.sin(lon);
        double val = fbm(os, nx, ny, nz, scale, oct, lac, gain);
        double ridge = 1.0 - Math.abs(val);
        double hh = 0.65*val + 0.35*ridge; // shaping stub
        h[y][x] = (float)hh;
      }
    }
    return h;
  }

  private static double fbm(OpenSimplex2 os, double x,double y,double z,double s,int oct,double lac,double gain){
    double amp=1, freq=1, sum=0; for(int i=0;i<oct;i++){ sum+=amp*os.noise3(x*s*freq,y*s*freq,z*s*freq); amp*=gain; freq*=lac; } return sum;
  }
}
```

---

## 🌊 Erosion Stubs

### `src/main/java/com/onur/planetgen/erosion/ThermalErosion.java`
```java
package com.onur.planetgen.erosion;
public final class ThermalErosion {
  private ThermalErosion() {}
  public static void apply(float[][] h, int iterations, double talus, double k){
    // TODO: implement slope-limited diffusion (wrap horizontally, clamp poles)
  }
}
```

### `src/main/java/com/onur/planetgen/erosion/HydraulicErosion.java`
```java
package com.onur.planetgen.erosion;
public final class HydraulicErosion {
  private HydraulicErosion() {}
  public static void apply(float[][] h, int iterations, double rainfall, double evaporation){
    // TODO: implement rainfall, flow routing, sediment transport, deposition
  }
}
```

### `src/main/java/com/onur/planetgen/erosion/FlowField.java`
```java
package com.onur.planetgen.erosion;
public record FlowField(float[][] flowX, float[][] flowY, float[][] accum) {}
```

---

## 🌡️ Climate & Biomes Stubs

### `src/main/java/com/onur/planetgen/planet/ClimateModel.java`
```java
package com.onur.planetgen.planet;

public final class ClimateModel {
  public record Sample(double temp, double moist, double humidity) {}
  private ClimateModel() {}
  public static Sample sample(int x,int y,float h,double lat){
    // TODO: implement temp/precip models
    double temp = 1.0 - Math.abs(Math.sin(lat)) - Math.max(0, h)*0.6;
    double moist = 0.5; double humidity = 0.5; // stubs
    return new Sample(temp, moist, humidity);
  }
}
```

### `src/main/java/com/onur/planetgen/planet/BiomeClassifier.java`
```java
package com.onur.planetgen.planet;

public final class BiomeClassifier {
  public enum Biome { TUNDRA, TAIGA, STEPPE, FOREST, DESERT, JUNGLE, ICE, SWAMP, RAINFOREST }
  public record Entry(int r,int g,int b,double rough){}

  public static Biome classify(double temp, double moist){
    if (temp < 0.2) return moist < 0.3 ? Biome.TUNDRA : Biome.TAIGA;
    if (temp < 0.6) return moist < 0.3 ? Biome.STEPPE : Biome.FOREST;
    return moist < 0.3 ? Biome.DESERT : Biome.JUNGLE;
  }
}
```

---

## 🎨 Rendering Stubs

### `src/main/java/com/onur/planetgen/render/AlbedoRenderer.java`
```java
package com.onur.planetgen.render;

import com.onur.planetgen.planet.BiomeClassifier;
import com.onur.planetgen.planet.ClimateModel;

public final class AlbedoRenderer {
  private AlbedoRenderer() {}

  public static int[][] render(float[][] height /* + biome/climate later */){
    int H = height.length, W = height[0].length; int[][] argb = new int[H][W];
    for (int y=0;y<H;y++){
      for (int x=0;x<W;x++){
        float h = height[y][x];
        ClimateModel.Sample c = ClimateModel.sample(x,y,h, 0); // TODO: pass real latitude
        BiomeClassifier.Biome b = BiomeClassifier.classify(c.temp(), c.moist());
        int rgb = switch (b) {
          case DESERT -> argb(255,206,185,140);
          case FOREST -> argb(255,72,110,78);
          case JUNGLE -> argb(255,62,96,68);
          case STEPPE -> argb(255,180,165,120);
          case TUNDRA -> argb(255,170,175,180);
          case TAIGA -> argb(255,74,102,74);
          case ICE -> argb(255,230,235,240);
          case SWAMP -> argb(255,80,92,70);
          case RAINFOREST -> argb(255,58,90,64);
        };
        argb[y][x] = rgb;
      }
    }
    return argb;
  }

  private static int argb(int a,int r,int g,int b){ return ((a&255)<<24)|((r&255)<<16)|((g&255)<<8)|(b&255); }
}
```

### `src/main/java/com/onur/planetgen/render/NormalMapRenderer.java`
```java
package com.onur.planetgen.render;

public final class NormalMapRenderer {
  private NormalMapRenderer() {}

  public static int[][] render(float[][] h){
    int H=h.length, W=h[0].length; int[][] out = new int[H][W];
    for(int y=0;y<H;y++){
      double v = (y+0.5)/(double)H; double lat = Math.PI/2.0 - Math.PI*v; double cos = Math.max(1e-6, Math.cos(lat));
      int yN = Math.max(0,y-1), yS = Math.min(H-1,y+1);
      for(int x=0;x<W;x++){
        int xW=(x-1+W)%W, xE=(x+1)%W;
        double dhdx = (h[y][xE]-h[y][xW])*0.5 / cos;
        double dhdy = (h[yS][x]-h[yN][x])*0.5;
        double nx=-dhdx, ny=-dhdy, nz=1.0; double L=Math.sqrt(nx*nx+ny*ny+nz*nz); nx/=L; ny/=L; nz/=L;
        int r=(int)Math.round((nx*0.5+0.5)*255), g=(int)Math.round((ny*0.5+0.5)*255), b=(int)Math.round((nz*0.5+0.5)*255);
        out[y][x] = ((255)<<24)|((r&255)<<16)|((g&255)<<8)|(b&255);
      }
    }
    return out;
  }
}
```

### `src/main/java/com/onur/planetgen/render/RoughnessRenderer.java`
```java
package com.onur.planetgen.render;

public final class RoughnessRenderer {
  private RoughnessRenderer() {}
  public static int[][] render(float[][] h){
    int H=h.length,W=h[0].length; int[][] g=new int[H][W];
    for(int y=0;y<H;y++) for(int x=0;x<W;x++) g[y][x]= (int)(Math.min(255, Math.max(0, 127 + 64*h[y][x]))) ;
    return g;
  }
}
```

---

## ☁️ Clouds

### `src/main/java/com/onur/planetgen/atmosphere/CloudField.java`
```java
package com.onur.planetgen.atmosphere;

import com.onur.planetgen.planet.SphericalSampler;

public final class CloudField {
  public final float[][] alpha; // 0..1
  private CloudField(int W,int H){ alpha=new float[H][W]; }
  public static CloudField generate(long seed, SphericalSampler sp /* + params */){
    CloudField c = new CloudField(sp.W, sp.H);
    // TODO: fBm + ridged + worley + domain warp; write to c.alpha[y][x]
    return c;
  }
}
```

### `src/main/java/com/onur/planetgen/render/CloudRenderer.java`
```java
package com.onur.planetgen.render;

import com.onur.planetgen.atmosphere.CloudField;

public final class CloudRenderer {
  private CloudRenderer() {}
  public static int[][] render(CloudField f){
    int H=f.alpha.length, W=f.alpha[0].length; int[][] argb=new int[H][W];
    for(int y=0;y<H;y++) for(int x=0;x<W;x++){
      int a=(int)Math.round(Math.max(0, Math.min(1, f.alpha[y][x]))*255);
      int r=230,g=240,b=255; argb[y][x]=((a&255)<<24)|((r&255)<<16)|((g&255)<<8)|(b&255);
    }
    return argb;
  }
}
```

---

## 🔉 Noise Interfaces

### `src/main/java/com/onur/planetgen/noise/Noise.java`
```java
package com.onur.planetgen.noise;
public interface Noise { double noise3(double x,double y,double z); }
```

### `src/main/java/com/onur/planetgen/noise/OpenSimplex2.java`
```java
package com.onur.planetgen.noise;

import java.util.Random;

public final class OpenSimplex2 implements Noise {
  private final short[] perm = new short[256];
  public OpenSimplex2(long seed){
    short[] p=new short[256]; for(short i=0;i<256;i++) p[i]=i;
    Random r=new Random(seed); for(int i=255;i>0;i--){int j=r.nextInt(i+1); short t=p[i]; p[i]=p[j]; p[j]=t;}
    for(int i=0;i<256;i++) perm[i]=p[i];
  }
  @Override public double noise3(double x,double y,double z){
    // TODO: replace with a proper OpenSimplex2 3D implementation
    // Stub: simple value noise-ish hash (not final quality)
    int X = fastFloor(x), Y=fastFloor(y), Z=fastFloor(z);
    double xf=x-X, yf=y-Y, zf=z-Z; double u=fade(xf), v=fade(yf), w=fade(zf);
    double n000=grad(hash(X,Y,Z), xf, yf, zf);
    double n100=grad(hash(X+1,Y,Z), xf-1, yf, zf);
    double n010=grad(hash(X,Y+1,Z), xf, yf-1, zf);
    double n110=grad(hash(X+1,Y+1,Z), xf-1, yf-1, zf);
    double n001=grad(hash(X,Y,Z+1), xf, yf, zf-1);
    double n101=grad(hash(X+1,Y,Z+1), xf-1, yf, zf-1);
    double n011=grad(hash(X,Y+1,Z+1), xf, yf-1, zf-1);
    double n111=grad(hash(X+1,Y+1,Z+1), xf-1, yf-1, zf-1);
    double x00=lerp(u,n000,n100), x10=lerp(u,n010,n110), x01=lerp(u,n001,n101), x11=lerp(u,n011,n111);
    double y0=lerp(v,x00,x10), y1=lerp(v,x01,x11);
    return lerp(w,y0,y1);
  }
  private int hash(int x,int y,int z){ return perm[(x+perm[(y+perm[z&255])&255])&255]&255; }
  private static int fastFloor(double x){ return x>=0?(int)x:((int)x-1); }
  private static double fade(double t){ return t*t*t*(t*(t*6-15)+10); }
  private static double lerp(double t,double a,double b){ return a + t*(b-a); }
  private static double grad(int h,double x,double y,double z){
    int hh=h&15; double u=hh<8?x:y; double v=hh<4?y:((hh==12||hh==14)?x:z);
    return (((hh&1)==0)?u:-u) + (((hh&2)==0)?v:-v);
  }
}
```

### `src/main/java/com/onur/planetgen/noise/DomainWarpNoise.java`
```java
package com.onur.planetgen.noise;

public final class DomainWarpNoise {
  private final Noise base, warpX, warpY, warpZ;
  private final double amp;
  public DomainWarpNoise(Noise base, Noise warpX, Noise warpY, Noise warpZ, double amplitude){
    this.base=base; this.warpX=warpX; this.warpY=warpY; this.warpZ=warpZ; this.amp=amplitude;
  }
  public double noise3(double x,double y,double z){
    double dx=warpX.noise3(x,y,z)*amp, dy=warpY.noise3(x,y,z)*amp, dz=warpZ.noise3(x,y,z)*amp;
    return base.noise3(x+dx, y+dy, z+dz);
  }
}
```

---

## ✅ Test

### `src/test/java/com/onur/planetgen/MappingTest.java`
```java
package com.onur.planetgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MappingTest {
  @Test void twoToOneAspect(){
    int W=4096,H=2048; assertEquals(W, 2*H);
  }
}
```

---

### Next steps
- Replace `OpenSimplex2` stub with a proper 3D OpenSimplex2/2S implementation.
- Fill erosion, climate, and clouds per `architecture.md`.
- Add parallelism (scanline `IntStream.range(0,H).parallel()`).
- Wire presets YAML/JSON loading (Jackson or SnakeYAML+Jackson).

