package com.onur.planetgen.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.*
import kotlin.random.Random

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
    var applyHydraulic by remember { mutableStateOf(false) }
    var batchCountText by remember { mutableStateOf("3") }

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
    var previewTab by remember { mutableStateOf(PreviewTab.STILL) }
    var settingsTab by remember { mutableStateOf(SettingsTab.GENERAL) }

    val isBusy = generationState is GenerationState.Running || generationState is GenerationState.BatchRunning

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
        applyHydraulic = false
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
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = resolutionText,
                        onValueChange = { resolutionText = it },
                        label = { Text("Resolution (WxH)") },
                        singleLine = true,
                        enabled = !isBusy,
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
                    enabled = !isBusy,
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
                                    enabled = !isBusy
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
                                enabled = !isBusy
                            )
                            ParameterSlider(
                                label = "Mountain intensity",
                                value = mountainIntensity,
                                onValueChange = { mountainIntensity = it },
                                valueRange = 0.5f..2.0f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = !isBusy
                            )
                            ParameterSlider(
                                label = "Rainfall",
                                value = rainfall,
                                onValueChange = { rainfall = it },
                                valueRange = 0f..1f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = !isBusy
                            )
                            ParameterSlider(
                                label = "Evaporation",
                                value = evaporation,
                                onValueChange = { evaporation = it },
                                valueRange = 0f..0.5f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = !isBusy
                            )
                            ParameterSlider(
                                label = "Thermal iterations",
                                value = thermalIterations,
                                onValueChange = { thermalIterations = it.roundToInt().coerceIn(0, 40).toFloat() },
                                valueRange = 0f..40f,
                                valueFormatter = { it.roundToInt().toString() },
                                enabled = !isBusy,
                                steps = 39
                            )
                            ParameterSlider(
                                label = "Hydraulic iterations",
                                value = hydraulicIterations,
                                onValueChange = { hydraulicIterations = it.roundToInt().coerceIn(0, 80).toFloat() },
                                valueRange = 0f..80f,
                                valueFormatter = { it.roundToInt().toString() },
                                enabled = !isBusy,
                                steps = 79
                            )
                            ParameterSlider(
                                label = "Cloud coverage",
                                value = cloudCoverage,
                                onValueChange = { cloudCoverage = it },
                                valueRange = 0f..1f,
                                valueFormatter = { "%.2f".format(it) },
                                enabled = !isBusy
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = applyHydraulic,
                                    onCheckedChange = { applyHydraulic = it },
                                    enabled = !isBusy
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
                    val isRunning = isBusy
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
                                        logLines += "Files -> ${summary.exportedMaps.joinToString()}"
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

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = batchCountText,
                        onValueChange = { input -> batchCountText = input.filter { it.isDigit() }.take(3) },
                        label = { Text("Batch size") },
                        singleLine = true,
                        enabled = !isBusy,
                        modifier = Modifier.width(140.dp)
                    )
                    Button(
                        onClick = {
                            val count = batchCountText.toIntOrNull()
                            if (count == null || count <= 0) {
                                logLines += "Batch size must be a positive integer."
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
                            logLines += "Starting batch generation of $count planets..."
                            generationState = GenerationState.BatchRunning(completed = 0, total = count)

                            val rng = Random(System.currentTimeMillis())
                            activeJob = coroutineScope.launch {
                                val summaries = mutableListOf<GenerationSummary>()
                                try {
                                    for (index in 0 until count) {
                                        ensureActive()
                                        val presetOption = PRESET_OPTIONS.random(rng)
                                        val overrides = randomOverrides(rng)
                                        val runRequest = request.copy(
                                            seed = rng.nextLong(),
                                            presetName = presetOption.key,
                                            overrides = overrides,
                                            applyHydraulic = false
                                        )
                                        withContext(Dispatchers.Main) {
                                            generationState = GenerationState.BatchRunning(index, count)
                                            logLines += "[${index + 1}/$count] Generating ${presetOption.label} (seed ${runRequest.seed})"
                                            logLines += "[${index + 1}/$count] Overrides -> sea=${format2(overrides.seaLevel)}, mountain=${format2(overrides.mountainIntensity)}, rain=${format2(overrides.rainfall)}, evap=${format2(overrides.evaporation)}, thermal=${overrides.thermalIterations}, hydraulic=${overrides.hydraulicIterations}, clouds=${format2(overrides.cloudCoverage)}"
                                            logLines += "[${index + 1}/$count] Hydraulic erosion disabled for this run."
                                        }
                                        val summary = generatePlanet(runRequest) { message ->
                                            withContext(Dispatchers.Main) {
                                                logLines += "[${index + 1}/$count] $message"
                                            }
                                        }
                                        summaries += summary
                                        withContext(Dispatchers.Main) {
                                            previewPath = summary.previewImage
                                            previewImage = summary.previewImage?.let { loadPreviewBitmap(it) }
                                            currentOutputDir = summary.outputDir
                                            refreshGeneratedFiles(summary.outputDir, generatedFiles) { err ->
                                                logLines += err
                                            }
                                            generationState = GenerationState.BatchRunning(index + 1, count)
                                            logLines += "[${index + 1}/$count] Completed in ${summary.totalDuration.toMillis() / 1000.0}s"
                                            logLines += "[${index + 1}/$count] Files -> ${summary.exportedMaps.joinToString()}"
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        generationState = GenerationState.BatchCompleted(summaries)
                                        logLines += "Batch generation complete."
                                    }
                                } catch (cancel: CancellationException) {
                                    withContext(Dispatchers.Main) {
                                        logLines += "Batch generation cancelled."
                                        generationState = GenerationState.Cancelled
                                    }
                                    throw cancel
                                } catch (throwable: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        logLines += "Batch generation failed: ${throwable.message ?: throwable::class.simpleName}"
                                        generationState = GenerationState.Failed(throwable)
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        activeJob = null
                                    }
                                }
                            }
                        },
                        enabled = !isBusy
                    ) {
                        Text("Batch Generate")
                    }
                }

                when (val state = generationState) {
                    is GenerationState.Running -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    is GenerationState.BatchRunning -> {
                        val progress = if (state.total <= 0) 0f else (state.completed.toFloat() / state.total)
                        val clamped = progress.coerceIn(0f, 1f)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { clamped },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Batch progress: ${state.completed}/${state.total}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    is GenerationState.Completed -> {
                        val presetLabel = PRESET_OPTIONS.firstOrNull { it.key == state.summary.presetName }?.label
                            ?: state.summary.presetName
                        Text(
                            text = "Last run ($presetLabel, seed ${state.summary.seed}) exported: ${state.summary.exportedMaps.joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is GenerationState.BatchCompleted -> {
                        val last = state.summaries.lastOrNull()
                        val suffix = if (last != null) {
                            val presetLabel = PRESET_OPTIONS.firstOrNull { it.key == last.presetName }?.label
                                ?: last.presetName
                            " Last planet: $presetLabel (seed ${last.seed})."
                        } else ""
                        Text(
                            text = "Batch generated ${state.summaries.size} planets.$suffix",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

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
                .heightIn(min = 320.dp)
                .weight(1f)
        ) {
            PreviewPanel(
                modifier = Modifier.fillMaxSize(),
                previewTab = previewTab,
                onTabChange = { previewTab = it },
                previewImage = previewImage,
                previewPath = previewPath
            )
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
private fun PreviewPanel(
    modifier: Modifier = Modifier,
    previewTab: PreviewTab,
    onTabChange: (PreviewTab) -> Unit,
    previewImage: ImageBitmap?,
    previewPath: Path?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.weight(1f))
            if (previewPath != null && Files.exists(previewPath)) {
                TextButton(
                    onClick = { openFile(previewPath) },
                    enabled = Desktop.isDesktopSupported()
                ) {
                    Text("Open Image")
                }
                TextButton(
                    onClick = {
                        val folder = previewPath.parent ?: previewPath
                        openFolder(folder)
                    },
                    enabled = Desktop.isDesktopSupported()
                ) {
                    Text("Reveal Folder")
                }
            }
        }
        TabRow(selectedTabIndex = previewTab.ordinal) {
            PreviewTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = previewTab.ordinal == index,
                    onClick = { onTabChange(PreviewTab.entries[index]) },
                    text = { Text(tab.label) }
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                previewImage == null -> Text(
                    text = "Run a generation to see the latest planet here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                previewTab == PreviewTab.STILL -> StillPreview(
                    image = previewImage,
                    modifier = Modifier.fillMaxSize()
                )

                previewTab == PreviewTab.ROTATING -> RotatingPreview(
                    image = previewImage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (previewPath != null) {
            Text(
                text = previewPath.fileName.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StillPreview(
    image: ImageBitmap,
    modifier: Modifier = Modifier
) {
    Image(
        bitmap = image,
        contentDescription = "Planet preview still",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun RotatingPreview(
    image: ImageBitmap,
    modifier: Modifier = Modifier
) {
    val pixelMap = remember(image) { image.toPixelMap() }
    var rotation by remember { mutableStateOf(0f) }
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    LaunchedEffect(image) {
        rotation = 0f
        var lastFrame = 0L
        while (true) {
            withFrameNanos { timestamp ->
                if (lastFrame != 0L) {
                    val deltaSeconds = (timestamp - lastFrame) / 1_000_000_000f
                    rotation = (rotation + deltaSeconds * ROTATION_SPEED_RADIANS).mod(TAU)
                }
                lastFrame = timestamp
            }
        }
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f * 0.85f
        val center = Offset(size.width / 2f, size.height / 2f)
        val lonSteps = 140
        val latSteps = 80
        val tileWidth = max(1f, (2f * radius) / lonSteps)
        val tileHeight = max(1f, (2f * radius) / latSteps)
        val width = pixelMap.width
        val height = pixelMap.height
        val rotationRad = rotation.toDouble()
        val circleRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        val circlePath = androidx.compose.ui.graphics.Path().apply { addOval(circleRect) }

        val light = floatArrayOf(0.35f, 0.2f, 1f)
        val lightLen = sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2])
        val lx = light[0] / lightLen
        val ly = light[1] / lightLen
        val lz = light[2] / lightLen

        clipPath(circlePath) {
            for (lonIndex in 0..lonSteps) {
                val deltaLon = ((lonIndex.toDouble() / lonSteps) - 0.5) * PI
                val sinDelta = sin(deltaLon)
                val cosDelta = cos(deltaLon)
                val textureLon = ((rotationRad + deltaLon) % TAU_DOUBLE + TAU_DOUBLE) % TAU_DOUBLE
                val u = (textureLon / TAU_DOUBLE).toFloat()

                for (latIndex in 0..latSteps) {
                    val lat = ((latIndex.toDouble() / latSteps) - 0.5) * PI
                    val sinLat = sin(lat)
                    val cosLat = cos(lat)

                    val x = (cosLat * sinDelta).toFloat()
                    val y = sinLat.toFloat()
                    val z = (cosLat * cosDelta).toFloat()
                    if (z <= 0f) continue

                    val screenX = center.x + x * radius
                    val screenY = center.y - y * radius

                    val px = (u * (width - 1)).toInt().coerceIn(0, width - 1)
                    val v = (0.5 - lat / PI).toFloat()
                    val py = (v * (height - 1)).toInt().coerceIn(0, height - 1)

                    val base = pixelMap[px, py]
                    val dot = max(0f, x * lx + y * ly + z * lz)
                    val shade = 0.25f + 0.75f * dot
                    val shaded = Color(
                        red = base.red * shade,
                        green = base.green * shade,
                        blue = base.blue * shade,
                        alpha = base.alpha
                    )

                    drawRect(
                        color = shaded,
                        topLeft = Offset(
                            x = screenX - tileWidth / 2f,
                            y = screenY - tileHeight / 2f
                        ),
                        size = Size(tileWidth, tileHeight)
                    )
                }
            }
        }

        drawCircle(
            color = outlineColor,
            radius = radius * 1.02f,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

private const val ROTATION_SPEED_RADIANS = 0.6f
private val TAU = (2 * PI).toFloat()
private val TAU_DOUBLE = 2 * PI

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

private fun randomOverrides(rng: Random): ParameterOverrides {
    return ParameterOverrides(
        seaLevel = rng.nextFloat(-0.2f..0.3f),
        mountainIntensity = rng.nextFloat(0.5f..2.0f),
        rainfall = rng.nextFloat(0f..1f),
        evaporation = rng.nextFloat(0f..0.5f),
        thermalIterations = rng.nextInt(0, 41),
        hydraulicIterations = rng.nextInt(0, 81),
        cloudCoverage = rng.nextFloat(0f..1f)
    )
}

private fun Random.nextFloat(range: ClosedFloatingPointRange<Float>): Float {
    return range.start + nextFloat() * (range.endInclusive - range.start)
}

private fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun buildOutputPath(map: ExportMap, request: GenerationRequest): Path {
    val presetSlug = slugifyPreset(request.presetName)
    val base = when (map) {
        ExportMap.ALBEDO -> "planet_albedo"
        ExportMap.HEIGHT -> "planet_height"
        ExportMap.NORMAL -> "planet_normal"
        ExportMap.ROUGHNESS -> "planet_roughness"
        ExportMap.METALLIC -> "planet_metallic"
        ExportMap.PBR -> "planet_pbr_pack"
        ExportMap.AO -> "planet_ao"
        ExportMap.BIOME -> "planet_biome_mask"
        ExportMap.MATERIAL -> "planet_material_mask"
        ExportMap.VEGETATION -> "planet_vegetation_density"
        ExportMap.DETAIL -> "planet_detail_map"
        ExportMap.SNOW -> "planet_snow_mask"
        ExportMap.OCEAN -> "planet_ocean_shading"
        ExportMap.ATMOSPHERE -> "planet_atmosphere"
        ExportMap.CLOUDS -> "planet_clouds"
        ExportMap.EMISSIVE -> "planet_emissive"
    }
    val suffix = if (map == ExportMap.HEIGHT) "_16u" else ""
    val fileName = "${base}_${presetSlug}_${request.seed}_${request.width}x${request.height}$suffix.png"
    return request.outputDir.resolve(fileName)
}

private fun slugifyPreset(preset: String): String {
    val slug = preset.lowercase(Locale.getDefault()).replace(NON_ALPHANUMERIC, "_").trim('_')
    return if (slug.isEmpty()) "preset" else slug
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

private fun uniquePath(base: Path): Path {
    if (!Files.exists(base)) return base
    val name = base.fileName.toString()
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var index = 1
    while (true) {
        val candidate = base.resolveSibling("${stem}_$index$ext")
        if (!Files.exists(candidate)) return candidate
        index++
    }
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

    val surface = SurfaceAnalyzer.analyze(heightField, preset, request.seed)

    val exportedMaps = mutableListOf<String>()
    var previewImagePath: Path? = null

    if (ExportMap.ALBEDO in request.exports) {
        onLog("Rendering albedo with hydrology overlays...")
        val path = uniquePath(buildOutputPath(ExportMap.ALBEDO, request))
        ImageUtil.saveARGB(surface.albedo(), path)
        onLog("Saved albedo -> ${path.fileName}")
        previewImagePath = path
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.NORMAL in request.exports) {
        onLog("Rendering normal map...")
        val normals = NormalMapRenderer.render(heightField)
        val path = uniquePath(buildOutputPath(ExportMap.NORMAL, request))
        ImageUtil.saveARGB(normals, path)
        onLog("Saved normal -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.ROUGHNESS in request.exports) {
        onLog("Exporting roughness map...")
        val path = uniquePath(buildOutputPath(ExportMap.ROUGHNESS, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.roughness()), path)
        onLog("Saved roughness -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.METALLIC in request.exports) {
        onLog("Exporting metallic map...")
        val path = uniquePath(buildOutputPath(ExportMap.METALLIC, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.metallic()), path)
        onLog("Saved metallic -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.PBR in request.exports) {
        onLog("Packing AO/Roughness/Metallic...")
        val pack = RenderUtil.packToRgb(surface.ambientOcclusion(), surface.roughness(), surface.metallic())
        val path = uniquePath(buildOutputPath(ExportMap.PBR, request))
        ImageUtil.saveARGB(pack, path)
        onLog("Saved PBR pack -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.AO in request.exports) {
        onLog("Exporting ambient occlusion...")
        val path = uniquePath(buildOutputPath(ExportMap.AO, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.ambientOcclusion()), path)
        onLog("Saved AO -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.BIOME in request.exports) {
        onLog("Exporting biome mask...")
        val path = uniquePath(buildOutputPath(ExportMap.BIOME, request))
        ImageUtil.saveARGB(surface.biomeMask(), path)
        onLog("Saved biome mask -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.MATERIAL in request.exports) {
        onLog("Exporting material mask...")
        val path = uniquePath(buildOutputPath(ExportMap.MATERIAL, request))
        ImageUtil.saveARGB(surface.materialMask(), path)
        onLog("Saved material mask -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.VEGETATION in request.exports) {
        onLog("Exporting vegetation density...")
        val path = uniquePath(buildOutputPath(ExportMap.VEGETATION, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.vegetation()), path)
        onLog("Saved vegetation -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.DETAIL in request.exports) {
        onLog("Exporting terrain detail map...")
        val path = uniquePath(buildOutputPath(ExportMap.DETAIL, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.detail()), path)
        onLog("Saved detail -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.SNOW in request.exports) {
        onLog("Exporting snow/weathering mask...")
        val path = uniquePath(buildOutputPath(ExportMap.SNOW, request))
        ImageUtil.saveGray8(RenderUtil.toGray8(surface.snow()), path)
        onLog("Saved snow mask -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.OCEAN in request.exports) {
        onLog("Exporting ocean shading map...")
        val path = uniquePath(buildOutputPath(ExportMap.OCEAN, request))
        ImageUtil.saveARGB(surface.oceanShading(), path)
        onLog("Saved ocean shading -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.ATMOSPHERE in request.exports) {
        onLog("Exporting atmosphere overlay...")
        val path = uniquePath(buildOutputPath(ExportMap.ATMOSPHERE, request))
        ImageUtil.saveARGB(surface.atmosphere(), path)
        onLog("Saved atmosphere -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.HEIGHT in request.exports) {
        onLog("Exporting height map...")
        val path = uniquePath(buildOutputPath(ExportMap.HEIGHT, request))
        ImageUtil.saveGray16(heightField, path)
        onLog("Saved height -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
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
        val path = uniquePath(buildOutputPath(ExportMap.CLOUDS, request))
        ImageUtil.saveARGB(clouds, path)
        onLog("Saved clouds -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.EMISSIVE in request.exports) {
        onLog("Rendering emissive map...")
        val emissive = EmissiveRenderer.render(heightField, preset, request.seed)
        val path = uniquePath(buildOutputPath(ExportMap.EMISSIVE, request))
        ImageUtil.saveARGB(emissive, path)
        onLog("Saved emissive -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    if (ExportMap.AO in request.exports) {
        onLog("Rendering ambient occlusion...")
        val aoStart = Instant.now()
        var ao = AmbientOcclusionRenderer.render(heightField)
        ao = AmbientOcclusionRenderer.smooth(ao, 1)
        val aoDuration = Duration.between(aoStart, Instant.now())
        onLog("Ambient occlusion finished in ${aoDuration.seconds}.${aoDuration.toMillisPart()}s")
        val path = uniquePath(buildOutputPath(ExportMap.AO, request))
        ImageUtil.saveGray8(ao, path)
        onLog("Saved AO -> ${path.fileName}")
        exportedMaps += path.fileName.toString()
    }

    val totalDuration = Duration.between(terrainStart, Instant.now())
    GenerationSummary(
        totalDuration = totalDuration,
        exportedMaps = exportedMaps,
        outputDir = request.outputDir,
        seed = request.seed,
        presetName = request.presetName,
        previewImage = previewImagePath
    )
}

private fun openFile(path: Path) {
    runCatching {
        if (Desktop.isDesktopSupported() && Files.exists(path)) {
            Desktop.getDesktop().open(path.toFile())
        }
    }
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
    METALLIC("Metallic", false),
    PBR("PBR Pack (AO/Rough/Metal)", false),
    AO("Ambient Occlusion", false),
    BIOME("Biome Mask", false),
    MATERIAL("Material Mask", false),
    VEGETATION("Vegetation Density", false),
    DETAIL("Detail Map", false),
    SNOW("Snow Mask", false),
    OCEAN("Ocean Shading", false),
    ATMOSPHERE("Atmosphere Overlay", false),
    CLOUDS("Clouds", true),
    EMISSIVE("Emissive", false)
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
    data class BatchRunning(val completed: Int, val total: Int) : GenerationState
    data class BatchCompleted(val summaries: List<GenerationSummary>) : GenerationState
    data class Failed(val throwable: Throwable) : GenerationState
}

data class GenerationRequest(
    val seed: Long,
    val width: Int,
    val height: Int,
    val presetName: String,
    val outputDir: Path,
    val exports: Set<ExportMap>,
    val applyHydraulic: Boolean = false,
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
    val seed: Long,
    val presetName: String,
    val previewImage: Path? = null
)

enum class PreviewTab(val label: String) {
    STILL("Still Image"),
    ROTATING("3D Orbit")
}
