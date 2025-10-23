package com.onur.planetgen.gui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.onur.planetgen.atmosphere.MultiLayerCloudField
import com.onur.planetgen.config.Preset
import com.onur.planetgen.erosion.HydraulicErosion
import com.onur.planetgen.erosion.ThermalErosion
import com.onur.planetgen.planet.CoordinateCache
import com.onur.planetgen.planet.ParallelHeightFieldGenerator
import com.onur.planetgen.planet.SphericalSampler
import com.onur.planetgen.render.*
import com.onur.planetgen.util.ImageUtil
import kotlinx.coroutines.*
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.math.roundToInt

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Planet Generator Studio") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            PlanetGeneratorApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanetGeneratorApp() {
    val coroutineScope = rememberCoroutineScope()

    var seedText by remember { mutableStateOf("123456") }
    var resolutionText by remember { mutableStateOf("4096x2048") }
    var presetExpanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(PRESET_OPTIONS.first()) }
    var outDirText by remember { mutableStateOf(Paths.get("output").absolutePathString()) }
    var applyHydraulic by remember { mutableStateOf(true) }

    val generatedFiles = remember { mutableStateListOf<Path>() }
    var currentOutputDir by remember { mutableStateOf<Path?>(null) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewPath by remember { mutableStateOf<Path?>(null) }
    var renamingPath by remember { mutableStateOf<Path?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var seaLevel by remember { mutableStateOf(0.02f) }
    var mountainIntensity by remember { mutableStateOf(1.0f) }
    var rainfall by remember { mutableStateOf(0.6f) }
    var evaporation by remember { mutableStateOf(0.1f) }
    var thermalIterations by remember { mutableStateOf(8f) }
    var hydraulicIterations by remember { mutableStateOf(15f) }
    var cloudCoverage by remember { mutableStateOf(0.55f) }

    val exportSelections = remember {
        mutableStateMapOf<ExportMap, Boolean>().apply {
            ExportMap.entries.forEach { put(it, it.defaultSelected) }
        }
    }

    val logLines = remember { mutableStateListOf<String>() }
    var generationState by remember { mutableStateOf<GenerationState>(GenerationState.Idle) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val logListState = rememberLazyListState()
    val fileListState = rememberLazyListState()
    var previewTab by remember { mutableStateOf(PreviewTab.STILL) }
    var settingsTab by remember { mutableStateOf(SettingsTab.GENERAL) }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            logListState.animateScrollToItem(logLines.size - 1)
        }
    }

    LaunchedEffect(selectedPreset) {
        val defaults = Preset(selectedPreset.key)
        seaLevel = defaults.seaLevel.toFloat()
        mountainIntensity = defaults.mountainIntensity.toFloat()
        rainfall = defaults.rainfall.toFloat()
        evaporation = defaults.evaporation.toFloat()
        thermalIterations = defaults.thermalIterations.toFloat()
        hydraulicIterations = defaults.hydraulicIterations.toFloat()
        cloudCoverage = defaults.cloudCoverage.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Planet Generator Studio",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            text = "Experiment with presets, tweak seeds, launch batch-quality outputs, and monitor progress in real time.",
            style = MaterialTheme.typography.bodyMedium
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                        label = { Text("Seed") },
                        singleLine = true,
                        enabled = generationState !is GenerationState.Running,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = resolutionText,
                        onValueChange = { resolutionText = it },
                        label = { Text("Resolution (WxH)") },
                        singleLine = true,
                        enabled = generationState !is GenerationState.Running,
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = !presetExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPreset.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preset") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        PRESET_OPTIONS.forEach { option ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedPreset = option
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = outDirText,
                    onValueChange = { outDirText = it },
                    label = { Text("Output Folder") },
                    singleLine = true,
                    enabled = generationState !is GenerationState.Running,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (Desktop.isDesktopSupported()) {
                                    runCatching { Paths.get(outDirText.ifBlank { "output" }) }
                                        .onSuccess { openFolder(it) }
                                        .onFailure { error -> logLines += "Failed to open folder: ${error.message}" }
                                }
                            },
                            enabled = Desktop.isDesktopSupported()
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Open folder")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Export Maps", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExportMap.entries.forEach { map ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = exportSelections[map] == true,
                                    onCheckedChange = { exportSelections[map] = it },
                                    enabled = generationState !is GenerationState.Running
                                )
                                Text(map.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = settingsTab.ordinal) {
                    SettingsTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = settingsTab.ordinal == index,
                            onClick = { settingsTab = SettingsTab.entries[index] },
                            text = { Text(tab.label) }
                        )
                    }
                }
                when (settingsTab) {
                    SettingsTab.GENERAL -> {
                        Text(
                            text = "Switch to Advanced to fine-tune erosion, climate, and cloud behaviour.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    SettingsTab.ADVANCED -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ParameterSlider(
                                label = "Sea level",
                                value = seaLevel,
                                onValueChange = { seaLevel = it },
                                valueRange = -0.2f..0.3f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = generationState !is GenerationState.Running
                            )
                            ParameterSlider(
                                label = "Mountain intensity",
                                value = mountainIntensity,
                                onValueChange = { mountainIntensity = it },
                                valueRange = 0.5f..2.0f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = generationState !is GenerationState.Running
                            )
                            ParameterSlider(
                                label = "Rainfall",
                                value = rainfall,
                                onValueChange = { rainfall = it },
                                valueRange = 0f..1f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = generationState !is GenerationState.Running
                            )
                            ParameterSlider(
                                label = "Evaporation",
                                value = evaporation,
                                onValueChange = { evaporation = it },
                                valueRange = 0f..0.5f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = generationState !is GenerationState.Running
                            )
                            ParameterSlider(
                                label = "Thermal iterations",
                                value = thermalIterations,
                                onValueChange = { thermalIterations = it.roundToInt().coerceIn(0, 40).toFloat() },
                                valueRange = 0f..40f,
                                valueFormatter = { it.roundToInt().toString() },
                                enabled = generationState !is GenerationState.Running,
                                steps = 39
                            )
                            ParameterSlider(
                                label = "Hydraulic iterations",
                                value = hydraulicIterations,
                                onValueChange = { hydraulicIterations = it.roundToInt().coerceIn(0, 80).toFloat() },
                                valueRange = 0f..80f,
                                valueFormatter = { it.roundToInt().toString() },
                                enabled = generationState !is GenerationState.Running,
                                steps = 79
                            )
                            ParameterSlider(
                                label = "Cloud coverage",
                                value = cloudCoverage,
                                onValueChange = { cloudCoverage = it },
                                valueRange = 0f..1f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = generationState !is GenerationState.Running
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = applyHydraulic,
                                    onCheckedChange = { applyHydraulic = it },
                                    enabled = generationState !is GenerationState.Running
                                )
                                Text("Enable hydraulic erosion")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isRunning = generationState is GenerationState.Running
                    Button(
                        onClick = {
                            if (isRunning) {
                                activeJob?.cancel(CancellationException("Cancelled by user"))
                                return@Button
                            }
                            val seed = seedText.toLongOrNull()
                            if (seed == null) {
                                logLines += "Seed must be a valid integer."
                                return@Button
                            }
                            val request = buildRequest(
                                seed = seed,
                                resolutionText = resolutionText,
                                presetOption = selectedPreset,
                                outDirText = outDirText,
                                exportSelections = exportSelections,
                                applyHydraulic = applyHydraulic,
                                overrides = ParameterOverrides(
                                    seaLevel = seaLevel,
                                    mountainIntensity = mountainIntensity,
                                    rainfall = rainfall,
                                    evaporation = evaporation,
                                    thermalIterations = thermalIterations.roundToInt(),
                                    hydraulicIterations = hydraulicIterations.roundToInt(),
                                    cloudCoverage = cloudCoverage
                                ),
                                onError = { message -> logLines += message }
                            ) ?: return@Button

                            logLines.clear()
                            logLines += "Preparing generation..."
                            generationState = GenerationState.Running

                            activeJob = coroutineScope.launch {
                                try {
                                    runCatching {
                                        generatePlanet(request) { message ->
                                            withContext(Dispatchers.Main) {
                                                logLines += message
                                            }
                                        }
                                    }.onSuccess { summary ->
                                        previewPath = summary.previewImage
                                        previewImage = summary.previewImage?.let { loadPreviewBitmap(it) }
                                        currentOutputDir = summary.outputDir
                                        refreshGeneratedFiles(summary.outputDir, generatedFiles) { err ->
                                            logLines += err
                                        }
                                        renamingPath = null
                                        renameInput = ""
                                        logLines += "Finished in ${summary.totalDuration.toMillis() / 1000.0}s"
                                        generationState = GenerationState.Completed(summary)
                                    }.onFailure { throwable ->
                                        if (throwable is CancellationException) {
                                            logLines += "Generation cancelled."
                                            generationState = GenerationState.Cancelled
                                        } else {
                                            logLines += "Error: ${throwable.message ?: throwable::class.simpleName}"
                                            generationState = GenerationState.Failed(throwable)
                                        }
                                    }
                                } finally {
                                    activeJob = null
                                }
                            }
                        }
                    ) {
                        if (isRunning) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel")
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate")
                        }
                    }

                    TextButton(
                        onClick = {
                            if (Desktop.isDesktopSupported()) {
                                runCatching { Paths.get(outDirText.ifBlank { "output" }) }
                                    .onSuccess { openFolder(it) }
                                    .onFailure { error -> logLines += "Failed to open folder: ${error.message}" }
                            }
                        },
                        enabled = Desktop.isDesktopSupported()
                    ) {
                        Text("Reveal Output")
                    }
                }

                when (val state = generationState) {
                    is GenerationState.Running -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    is GenerationState.Completed -> Text(
                        text = "Last run exported: ${state.summary.exportedMaps.joinToString()}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    is GenerationState.Failed -> Text(
                        text = "Warning: ${state.throwable.message ?: "Unknown error"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    is GenerationState.Cancelled -> Text(
                        text = "Generation cancelled.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    GenerationState.Idle -> {}
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    items(logLines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier
                        .padding(vertical = 16.dp),
                    adapter = rememberScrollbarAdapter(logListState)
                )
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    enabled: Boolean = true,
    steps: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(max = 160.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueFormatter(value),
            modifier = Modifier.widthIn(min = 48.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private val PRESET_OPTIONS = listOf(
    PresetOption("earthlike", "Earthlike"),
    PresetOption("desert", "Desert"),
    PresetOption("ice", "Ice World"),
    PresetOption("lava", "Lava"),
    PresetOption("alien", "Alien")
)

private fun buildRequest(
    seed: Long,
    resolutionText: String,
    presetOption: PresetOption,
    outDirText: String,
    exportSelections: Map<ExportMap, Boolean>,
    applyHydraulic: Boolean,
    overrides: ParameterOverrides,
    onError: (String) -> Unit
): GenerationRequest? {
    val dimensions = resolutionText.lowercase(Locale.getDefault()).split("x")
    if (dimensions.size != 2) {
        onError("Resolution must be formatted as widthxheight, e.g. 4096x2048.")
        return null
    }
    val width = dimensions[0].toIntOrNull()
    val height = dimensions[1].toIntOrNull()
    if (width == null || height == null) {
        onError("Resolution must contain valid integers.")
        return null
    }
    if (width != 2 * height) {
        onError("Resolution must be 2:1 (e.g. 4096x2048).")
        return null
    }

    val path = try {
        Paths.get(outDirText.ifBlank { "output" })
    } catch (ex: Exception) {
        onError("Output path is invalid: ${ex.message}")
        return null
    }
    val selectedMaps = exportSelections.filterValues { it }.keys
    if (selectedMaps.isEmpty()) {
        onError("Select at least one export map.")
        return null
    }
    return GenerationRequest(
        seed = seed,
        width = width,
        height = height,
        presetName = presetOption.key,
        outputDir = path,
        exports = selectedMaps.toSet(),
        applyHydraulic = applyHydraulic,
        overrides = overrides
    )
}

suspend fun generatePlanet(
    request: GenerationRequest,
    onLog: suspend (String) -> Unit
): GenerationSummary = withContext(Dispatchers.Default) {
    Files.createDirectories(request.outputDir)

    onLog("Loading preset: ${request.presetName}")
    val preset = Preset(request.presetName).apply {
        seaLevel = request.overrides.seaLevel.toDouble()
        mountainIntensity = request.overrides.mountainIntensity.toDouble()
        rainfall = request.overrides.rainfall.toDouble()
        evaporation = request.overrides.evaporation.toDouble()
        thermalIterations = request.overrides.thermalIterations
        hydraulicIterations = request.overrides.hydraulicIterations
        cloudCoverage = request.overrides.cloudCoverage.toDouble()
    }

    val sampler = SphericalSampler(request.width, request.height)
    val coordinateCache = CoordinateCache(request.width, request.height, sampler)

    val heightField: Array<FloatArray>
    val terrainStart = Instant.now()
    onLog("Generating terrain at ${request.width}x${request.height}...")
    heightField = ParallelHeightFieldGenerator.generateParallel(request.seed, sampler, preset)
    val terrainDuration = Duration.between(terrainStart, Instant.now())
    onLog("Terrain generated in ${terrainDuration.seconds}.${terrainDuration.toMillisPart()}s")

    onLog("Applying thermal erosion (${preset.thermalIterations} iterations)...")
    val thermalStart = Instant.now()
    ThermalErosion.apply(heightField, preset.thermalIterations, preset.thermalTalus, preset.thermalK)
    val thermalDuration = Duration.between(thermalStart, Instant.now())
    onLog("Thermal erosion done in ${thermalDuration.seconds}.${thermalDuration.toMillisPart()}s")

    if (request.applyHydraulic) {
        onLog("Applying hydraulic erosion (${preset.hydraulicIterations} iterations)...")
        val hydraulicStart = Instant.now()
        HydraulicErosion.apply(heightField, preset.hydraulicIterations, preset.rainfall, preset.evaporation)
        val hydraulicDuration = Duration.between(hydraulicStart, Instant.now())
        onLog("Hydraulic erosion done in ${hydraulicDuration.seconds}.${hydraulicDuration.toMillisPart()}s")
    }

    val exportedMaps = mutableListOf<String>()
    var previewImagePath: Path? = null

    if (ExportMap.ALBEDO in request.exports) {
        onLog("Rendering albedo with hydrology overlays...")
        val argb = AlbedoRenderer.renderWithHydrology(heightField, preset)
        val path = request.outputDir.resolve("planet_albedo_${request.width}x${request.height}.png")
        ImageUtil.saveARGB(argb, path)
        previewImagePath = path
        exportedMaps += "albedo"
    }

    if (ExportMap.NORMAL in request.exports) {
        onLog("Rendering normal map...")
        val normals = NormalMapRenderer.render(heightField)
        ImageUtil.saveARGB(normals, request.outputDir.resolve("planet_normal.png"))
        exportedMaps += "normal"
    }

    if (ExportMap.ROUGHNESS in request.exports) {
        onLog("Rendering roughness...")
        val roughness = RoughnessRenderer.render(heightField)
        ImageUtil.saveGray8(roughness, request.outputDir.resolve("planet_roughness.png"))
        exportedMaps += "roughness"
    }

    if (ExportMap.HEIGHT in request.exports) {
        onLog("Exporting height map...")
        ImageUtil.saveGray16(heightField, request.outputDir.resolve("planet_height_16u.png"))
        exportedMaps += "height"
    }

    if (ExportMap.CLOUDS in request.exports) {
        onLog("Generating multi-layer clouds...")
        val cloudStart = Instant.now()
        val cloudField = MultiLayerCloudField.generateParallel(
            request.seed + 1,
            sampler,
            preset,
            coordinateCache
        )
        val cloudDuration = Duration.between(cloudStart, Instant.now())
        onLog("Cloud generation completed in ${cloudDuration.seconds}.${cloudDuration.toMillisPart()}s")
        val clouds = CloudRenderer.render(cloudField)
        ImageUtil.saveARGB(clouds, request.outputDir.resolve("planet_clouds.png"))
        exportedMaps += "clouds"
    }

    if (ExportMap.EMISSIVE in request.exports) {
        onLog("Rendering emissive map...")
        val emissive = EmissiveRenderer.render(heightField, preset, request.seed)
        ImageUtil.saveARGB(emissive, request.outputDir.resolve("planet_emissive.png"))
        exportedMaps += "emissive"
    }

    if (ExportMap.AO in request.exports) {
        onLog("Rendering ambient occlusion...")
        val aoStart = Instant.now()
        var ao = AmbientOcclusionRenderer.render(heightField)
        ao = AmbientOcclusionRenderer.smooth(ao, 1)
        val aoDuration = Duration.between(aoStart, Instant.now())
        onLog("Ambient occlusion finished in ${aoDuration.seconds}.${aoDuration.toMillisPart()}s")
        ImageUtil.saveGray8(ao, request.outputDir.resolve("planet_ao.png"))
        exportedMaps += "ao"
    }

    val totalDuration = Duration.between(terrainStart, Instant.now())
    GenerationSummary(
        totalDuration = totalDuration,
        exportedMaps = exportedMaps,
        outputDir = request.outputDir,
        previewImage = previewImagePath
    )
}

private fun openFolder(path: Path) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val directory = path.toFile()
            if (!directory.exists()) {
                Files.createDirectories(path)
            }
            Desktop.getDesktop().open(directory)
        }
    }
}

private fun loadPreviewBitmap(path: Path): ImageBitmap? {
    return try {
        loadImageBitmap(path.toFile().inputStream())
    } catch (e: Exception) {
        null
    }
}

private fun refreshGeneratedFiles(
    dir: Path,
    generatedFiles: SnapshotStateList<Path>,
    onError: (String) -> Unit
) {
    try {
        generatedFiles.clear()
        Files.list(dir)
            .filter { Files.isRegularFile(it) }
            .sorted(Comparator.reverseOrder())
            .forEach { generatedFiles.add(it) }
    } catch (e: Exception) {
        onError("Error loading files: ${e.message}")
    }
}

private data class PresetOption(val key: String, val label: String)

private enum class SettingsTab(val label: String) {
    GENERAL("General"),
    ADVANCED("Advanced")
}

enum class ExportMap(val label: String, val defaultSelected: Boolean) {
    ALBEDO("Albedo", true),
    HEIGHT("Height", true),
    NORMAL("Normal", true),
    ROUGHNESS("Roughness", true),
    CLOUDS("Clouds", true),
    EMISSIVE("Emissive", false),
    AO("Ambient Occlusion", false)
}

data class ParameterOverrides(
    val seaLevel: Float,
    val mountainIntensity: Float,
    val rainfall: Float,
    val evaporation: Float,
    val thermalIterations: Int,
    val hydraulicIterations: Int,
    val cloudCoverage: Float
)

private sealed interface GenerationState {
    data object Idle : GenerationState
    data object Running : GenerationState
    data object Cancelled : GenerationState
    data class Completed(val summary: GenerationSummary) : GenerationState
    data class Failed(val throwable: Throwable) : GenerationState
}

data class GenerationRequest(
    val seed: Long,
    val width: Int,
    val height: Int,
    val presetName: String,
    val outputDir: Path,
    val exports: Set<ExportMap>,
    val applyHydraulic: Boolean = true,
    val overrides: ParameterOverrides = ParameterOverrides(
        seaLevel = 0.02f,
        mountainIntensity = 1.0f,
        rainfall = 0.6f,
        evaporation = 0.1f,
        thermalIterations = 20,
        hydraulicIterations = 60,
        cloudCoverage = 0.55f
    )
)

data class GenerationSummary(
    val totalDuration: Duration,
    val exportedMaps: List<String>,
    val outputDir: Path,
    val previewImage: Path? = null
)

enum class PreviewTab {
    STILL, ROTATING
}
