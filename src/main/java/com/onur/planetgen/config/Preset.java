package com.onur.planetgen.config;

/**
 * Preset configuration for planet generation.
 * Encapsulates all parameters for terrain, erosion, climate, clouds, and rendering.
 */
public class Preset {
    // Terrain parameters
    public double seaLevel = 0.02;
    public double continentScale = 2.2;
    public double mountainIntensity = 0.9;

    // Thermal erosion
    public int thermalIterations = 20;
    public double thermalTalus = 0.55;
    public double thermalK = 0.15;

    // Hydraulic erosion
    public int hydraulicIterations = 60;
    public double rainfall = 0.6;
    public double evaporation = 0.1;

    // Climate
    public double tempLatCoeff = 1.2;
    public double tempAltCoeff = 0.9;
    public double moistureBias = 0.1;

    // Clouds
    public double cloudCoverage = 0.55;
    public double cloudWarp = 0.25;
    public double cloudGamma = 2.4;

    // Emissive (night lights / lava)
    public boolean enableEmissive = false;
    public String emissiveType = "none"; // "none", "night_lights", "lava"
    public double emissiveIntensity = 0.5;
    public double emissiveThreshold = 0.3;

    // Rivers & lakes
    public boolean enableRivers = true;
    public double riverThreshold = 0.3; // Flow accumulation threshold
    public double lakeThreshold = 0.5;  // Local minimum threshold

    public Preset() {
    }

    public Preset(String presetName) {
        loadPreset(presetName);
    }

    /**
     * Load preset by name from built-in defaults.
     */
    private void loadPreset(String name) {
        switch (name.toLowerCase()) {
            case "earthlike":
                loadEarthlike();
                break;
            case "desert":
                loadDesert();
                break;
            case "ice":
                loadIce();
                break;
            case "lava":
                loadLava();
                break;
            case "alien":
                loadAlien();
                break;
            default:
                // Keep defaults
                break;
        }
    }

    private void loadEarthlike() {
        seaLevel = 0.02;
        continentScale = 2.2;
        mountainIntensity = 0.9;
        thermalIterations = 8;
        thermalTalus = 0.55;
        thermalK = 0.15;
        hydraulicIterations = 15;
        rainfall = 0.6;
        evaporation = 0.1;
        tempLatCoeff = 1.2;
        tempAltCoeff = 0.9;
        moistureBias = 0.1;
        cloudCoverage = 0.55;
        cloudWarp = 0.25;
        cloudGamma = 2.4;
        enableEmissive = true;
        emissiveType = "night_lights";
        emissiveIntensity = 0.3;
        enableRivers = true;
        riverThreshold = 0.3;
    }

    private void loadDesert() {
        seaLevel = 0.05;
        continentScale = 2.5;
        mountainIntensity = 0.6;
        thermalIterations = 6;
        thermalTalus = 0.55;
        thermalK = 0.15;
        hydraulicIterations = 10;
        rainfall = 0.2;
        evaporation = 0.3;
        tempLatCoeff = 0.8;
        tempAltCoeff = 1.1;
        moistureBias = -0.3;
        cloudCoverage = 0.2;
        cloudWarp = 0.15;
        cloudGamma = 2.0;
        enableEmissive = true;
        emissiveType = "night_lights";
        emissiveIntensity = 0.2;
        enableRivers = false;
    }

    private void loadIce() {
        seaLevel = -0.1;
        continentScale = 1.8;
        mountainIntensity = 1.2;
        thermalIterations = 12;
        thermalTalus = 0.55;
        thermalK = 0.15;
        hydraulicIterations = 20;
        rainfall = 0.4;
        evaporation = 0.05;
        tempLatCoeff = 1.5;
        tempAltCoeff = 1.3;
        moistureBias = 0.2;
        cloudCoverage = 0.7;
        cloudWarp = 0.3;
        cloudGamma = 2.6;
        enableEmissive = true;
        emissiveType = "night_lights";
        emissiveIntensity = 0.2;
        enableRivers = false;
    }

    private void loadLava() {
        seaLevel = 0.0;
        continentScale = 2.0;
        mountainIntensity = 1.5;
        thermalIterations = 10;
        thermalTalus = 0.55;
        thermalK = 0.15;
        hydraulicIterations = 12;
        rainfall = 0.1;
        evaporation = 0.2;
        tempLatCoeff = 0.5;
        tempAltCoeff = 0.5;
        moistureBias = -0.5;
        cloudCoverage = 0.1;
        cloudWarp = 0.1;
        cloudGamma = 1.8;
        enableEmissive = true;
        emissiveType = "lava";
        emissiveIntensity = 0.8;
        enableRivers = false;
    }

    private void loadAlien() {
        seaLevel = 0.03;
        continentScale = 3.0;
        mountainIntensity = 1.1;
        thermalIterations = 7;
        thermalTalus = 0.55;
        thermalK = 0.15;
        hydraulicIterations = 14;
        rainfall = 0.65;
        evaporation = 0.08;
        tempLatCoeff = 0.9;
        tempAltCoeff = 0.8;
        moistureBias = 0.15;
        cloudCoverage = 0.6;
        cloudWarp = 0.4;
        cloudGamma = 2.2;
        enableEmissive = true;
        emissiveType = "night_lights";
        emissiveIntensity = 0.4;
        enableRivers = true;
        riverThreshold = 0.25;
    }

    @Override
    public String toString() {
        return "Preset{" +
                "seaLevel=" + seaLevel +
                ", continentScale=" + continentScale +
                ", mountainIntensity=" + mountainIntensity +
                ", thermalIterations=" + thermalIterations +
                ", hydraulicIterations=" + hydraulicIterations +
                ", rainfall=" + rainfall +
                ", emissiveType='" + emissiveType + '\'' +
                '}';
    }
}
