package com.nees.audio.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nees.audio.effect.EffectState
import com.nees.audio.effect.Effects
import com.nees.audio.ui.components.LabeledSlider
import com.nees.audio.ui.components.LiquidSwitch
import com.nees.audio.ui.components.rememberColorWaveHaptics
import com.nees.audio.ui.screens.debug.DebugLogDialog
import com.nees.audio.ui.screens.device.DeviceDialog
import com.nees.audio.ui.screens.preset.PresetDialog
import com.nees.audio.ui.screens.settings.SettingsDialog
import com.nees.audio.ui.screens.status.DriverStatusDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class ColorWaveProfile(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val pgcStrength: Int,
    val pgcMax: Int,
    val bass: Int,
    val psycho: Int,
    val clarity: Int,
    val midWidth: Int,
    val highWidth: Int,
    val eq: List<Double>,
)

private val profiles =
    listOf(
        ColorWaveProfile(
            "Adaptive", "Balanced and intelligent", Icons.Default.AutoAwesome,
            listOf(Color(0xFF4A74FF), Color(0xFF7B5CF6)),
            240, 720, 170, 28, 90, 105, 112,
            listOf(-1.2, -0.3, 2.4, 1.4, -1.0, -0.2, 0.8, 1.3, 0.7, 0.1),
        ),
        ColorWaveProfile(
            "Music", "Rhythm, body and detail", Icons.Default.MusicNote,
            listOf(Color(0xFFEA4B9B), Color(0xFF7658F4)),
            240, 720, 260, 45, 165, 125, 138,
            listOf(-1.0, 0.4, 3.0, 1.8, -1.0, -0.1, 1.0, 1.7, 1.0, 0.2),
        ),
        ColorWaveProfile(
            "Cinema", "Stage, impact and dialogue", Icons.Default.Movie,
            listOf(Color(0xFF5667F7), Color(0xFF22A7D8)),
            240, 720, 300, 50, 190, 145, 160,
            listOf(-1.3, -0.3, 2.8, 1.6, -0.7, 0.3, 1.7, 2.1, 1.2, 0.1),
        ),
        ColorWaveProfile(
            "Game", "Position, punch and speed", Icons.Default.SportsEsports,
            listOf(Color(0xFF00A99B), Color(0xFF3977F1)),
            240, 720, 180, 25, 220, 155, 175,
            listOf(-1.8, -0.8, 1.0, 0.6, -0.2, 0.8, 2.2, 2.6, 1.5, 0.3),
        ),
        ColorWaveProfile(
            "Voice", "Focused and intelligible", Icons.Default.Hearing,
            listOf(Color(0xFFFF8A45), Color(0xFFE74D7A)),
            240, 720, 90, 8, 260, 95, 100,
            listOf(-2.8, -1.8, -0.8, -0.1, 0.8, 1.8, 2.8, 2.3, 1.0, -0.2),
        ),
    )

private fun detectProfile(state: EffectState): String =
    profiles.firstOrNull { p ->
        state.playbackGainControl.strength == p.pgcStrength &&
            state.playbackGainControl.maxGain == p.pgcMax &&
            state.bass.gain == p.bass &&
            state.psychoacousticBass.intensity == p.psycho &&
            state.clarity.gain == p.clarity &&
            state.stereoImager.midWidth == p.midWidth &&
            state.stereoImager.highWidth == p.highWidth &&
            state.eq.bands.size == p.eq.size &&
            state.eq.bands.zip(p.eq).all { (a, b) -> abs(a - b) < .05 }
    }?.name ?: "Custom"

private fun applyProfile(
    profile: ColorWaveProfile,
    vm: MainViewModel,
) {
    vm.applyColorWaveProfile(
        name = profile.name,
        pgcStrength = profile.pgcStrength,
        pgcMax = profile.pgcMax,
        bassGain = profile.bass,
        psychoIntensity = profile.psycho,
        clarityGain = profile.clarity,
        stereoMid = profile.midWidth,
        stereoHigh = profile.highWidth,
        eqBands = profile.eq,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveSettingsOnBackground()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presetList.collectAsStateWithLifecycle()
    val devices by viewModel.deviceSettingsList.collectAsStateWithLifecycle()
    val driver by viewModel.driverStatus.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStartEnabled.collectAsStateWithLifecycle()
    val globalMode by viewModel.globalModeEnabled.collectAsStateWithLifecycle()
    val aidlMode by viewModel.aidlModeEnabled.collectAsStateWithLifecycle()
    val debug by viewModel.debugModeEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = rememberColorWaveHaptics()
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showDriver by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    if (showDevices) {
        DeviceDialog(
            devices = devices,
            activeDeviceId = state.activeDeviceId,
            onRename = viewModel::renameDevice,
            onLoad = viewModel::loadDevicePreset,
            onUpdate = viewModel::saveDevicePreset,
            onDelete = viewModel::deleteDeviceSettings,
            onDismiss = { showDevices = false },
        )
    }

    if (showPresets) {
        PresetDialog(
            presets = presets,
            onSave = viewModel::savePreset,
            onLoad = {
                haptics.confirm()
                viewModel.loadPreset(it)
                showPresets = false
            },
            onDelete = viewModel::deletePreset,
            onRename = viewModel::renamePreset,
            onUpdate = viewModel::updatePreset,
            onClearAll = {
                viewModel.clearAllPresets("Clearing profiles", "Cleared") { count ->
                    Toast.makeText(context, "Cleared: $count", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPresets = false },
        )
    }

    if (showDriver) {
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.queryDriverStatus()
                delay(500)
            }
        }
        DriverStatusDialog(driverStatus = driver, onDismiss = { showDriver = false })
    }

    if (showDebug) {
        DebugLogDialog(
            onDisableDebug = {
                viewModel.disableDebugMode()
                showDebug = false
            },
            onDismiss = { showDebug = false },
        )
    }

    val importPreset = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPresetFiles(uris, "Import profile", "Imported") { ok ->
                Toast.makeText(context, if (ok) "Imported" else "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importKernel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importKernels(uris, "Import kernel", "Imported") { ok ->
                Toast.makeText(context, if (ok) "Imported" else "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importVdc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVdcs(uris, "Import VDC", "Imported") { ok ->
                Toast.makeText(context, if (ok) "Imported" else "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showSettings) {
        LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
        SettingsDialog(
            autoStartEnabled = autoStart,
            globalModeEnabled = globalMode,
            aidlModeActive = aidlMode,
            driverStatus = driver,
            appVersionName = version,
            onAutoStartChanged = viewModel::toggleAutoStart,
            onGlobalModeChanged = viewModel::toggleGlobalMode,
            onImportPreset = { importPreset.launch(arrayOf("application/json", "*/*")) },
            onImportKernel = { importKernel.launch(arrayOf("audio/*", "application/octet-stream", "*/*")) },
            onDebugUnlocked = viewModel::enableDebugMode,
            onImportVdc = { importVdc.launch(arrayOf("*/*")) },
            onDismiss = { showSettings = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ColorWaveBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ColorWaveTopBar(
                    debugEnabled = debug,
                    onDebug = { haptics.click(); showDebug = true },
                    onInfo = { haptics.click(); showDriver = true },
                    onSettings = { haptics.click(); showSettings = true },
                )
            },
            bottomBar = {
                ColorWaveFloatingNav(
                    selected = pagerState.currentPage,
                    onSelect = { page ->
                        if (page != pagerState.currentPage) {
                            haptics.click()
                            scope.launch { pagerState.animateScrollToPage(page) }
                        }
                    },
                )
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { page ->
                when (page) {
                    0 -> HomePage(
                        state = state,
                        vm = viewModel,
                        onStudio = {
                            haptics.click()
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                    )
                    1 -> StudioPage(state = state, vm = viewModel)
                    else -> SystemPage(
                        state = state,
                        driverInstalled = driver.installed,
                        driverStreaming = driver.streaming,
                        sampleRate = driver.samplingRate,
                        aidl = aidlMode,
                        global = globalMode,
                        autoStart = autoStart,
                        version = version,
                        onDevices = { haptics.click(); showDevices = true },
                        onPresets = { haptics.click(); showPresets = true },
                        onDriver = { haptics.click(); showDriver = true },
                        onSettings = { haptics.click(); showSettings = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorWaveBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(12500),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "drift",
        )
    val breathe by
        transition.animateFloat(
            initialValue = .86f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    tween(8200),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "ambient_breathe",
        )

    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    listOf(
                        primary.copy(alpha = .105f),
                        primary.copy(alpha = .035f),
                        Color.Transparent,
                    ),
                ),
            radius = size.minDimension * .77f * breathe,
            center = Offset(
                size.width * (.08f + .20f * drift),
                size.height * .12f,
            ),
        )

        drawCircle(
            brush =
                Brush.radialGradient(
                    listOf(
                        tertiary.copy(alpha = .085f),
                        tertiary.copy(alpha = .025f),
                        Color.Transparent,
                    ),
                ),
            radius = size.minDimension * .66f,
            center = Offset(
                size.width * (.94f - .17f * drift),
                size.height * .48f,
            ),
        )

        drawCircle(
            brush =
                Brush.radialGradient(
                    listOf(
                        primary.copy(alpha = .045f),
                        Color.Transparent,
                    ),
                ),
            radius = size.minDimension * .54f,
            center = Offset(
                size.width * .48f,
                size.height * (.90f - .05f * drift),
            ),
        )
    }
}

@Composable
private fun ColorWaveTopBar(
    debugEnabled: Boolean,
    onDebug: () -> Unit,
    onInfo: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
            ) {
                Box(contentAlignment = Alignment.Center) { ColorWaveMiniGlyph() }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "ColorWave",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }

        if (debugEnabled) {
            IconButton(onClick = onDebug) { Icon(Icons.Default.BugReport, contentDescription = "Debug") }
        }
        IconButton(onClick = onInfo) { Icon(Icons.Default.Info, contentDescription = "Engine info") }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun ColorWaveMiniGlyph() {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp)) {
        val bars = 5
        repeat(bars) { i ->
            val x = size.width * (i + 1) / (bars + 1)
            val ratio = when (i) {
                0, 4 -> .42f
                1, 3 -> .72f
                else -> 1f
            }
            val h = size.height * ratio
            drawLine(
                color = primary,
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ColorWaveFloatingNav(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(31.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f),
                ),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(
                    selected = selected == 0,
                    icon = Icons.Default.Home,
                    label = "Home",
                    onClick = { onSelect(0) },
                )
                NavItem(
                    selected = selected == 1,
                    icon = Icons.Default.Equalizer,
                    label = "Studio",
                    animatedEq = true,
                    onClick = { onSelect(1) },
                )
                NavItem(
                    selected = selected == 2,
                    icon = Icons.Default.Memory,
                    label = "System",
                    onClick = { onSelect(2) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    animatedEq: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed) .95f else 1f,
            animationSpec = spring(dampingRatio = .72f, stiffness = 520f),
            label = "nav_press",
        )

    Surface(
        modifier = Modifier.scale(pressScale),
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(25.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = .76f, stiffness = 430f),
                    )
                    .padding(
                        horizontal = if (selected) 15.dp else 13.dp,
                        vertical = 12.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (animatedEq && selected) {
                AnimatedNavEq(
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            AnimatedVisibility(visible = selected) {
                Text(
                    label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavEq(color: Color) {
    val transition = rememberInfiniteTransition(label = "nav_eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500)),
        label = "nav_eq_phase",
    )

    Canvas(Modifier.size(22.dp)) {
        repeat(4) { i ->
            val x = size.width * (i + 1) / 5f
            val level = .34f + .52f * ((sin(phase + i * 1.15f) + 1f) / 2f)
            val h = size.height * level
            drawLine(
                color = color,
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = 2.3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HomePage(
    state: EffectState,
    vm: MainViewModel,
    onStudio: () -> Unit,
) {
    val profile = remember(
        state.playbackGainControl.strength,
        state.playbackGainControl.maxGain,
        state.bass.gain,
        state.psychoacousticBass.intensity,
        state.clarity.gain,
        state.stereoImager.midWidth,
        state.stereoImager.highWidth,
        state.eq.bands,
    ) { detectProfile(state) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SoundFieldHero(
                state = state,
                profile = profile,
                onMaster = vm::setMasterEnabled,
            )
        }

        item {
            SectionHeading(
                title = "Sound signatures",
                subtitle = "Swipe between complete ColorWave tuning profiles",
            )
        }

        item {
            ProfileDeck(
                selectedName = profile,
                onProfile = { applyProfile(it, vm) },
            )
        }

        item {
            SectionHeading(
                title = "Shape your sound",
                subtitle = "Fast tactile controls for the active signature",
            )
        }

        item { SoundCharacterPanel(state = state, vm = vm) }
        item { StudioLaunchCard(onClick = onStudio) }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 3.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .16f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) { content() }
    } else {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .16f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) { content() }
    }
}

@Composable
private fun SoundFieldHero(
    state: EffectState,
    profile: String,
    onMaster: (Boolean) -> Unit,
) {
    val haptics = rememberColorWaveHaptics()
    val transition = rememberInfiniteTransition(label = "sound_field")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2150)),
            label = "sound_field_phase",
        )
    val glow by
        transition.animateFloat(
            initialValue = .82f,
            targetValue = 1.10f,
            animationSpec =
                infiniteRepeatable(
                    tween(2500),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "hero_glow",
        )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant

    FrostedSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(31.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = .145f),
                                tertiary.copy(alpha = .075f),
                                surface.copy(alpha = .04f),
                            ),
                        ),
                    ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = primary.copy(alpha = .055f),
                    radius = size.minDimension * .42f * glow,
                    center = Offset(size.width * .78f, size.height * .27f),
                )
                drawCircle(
                    color = tertiary.copy(alpha = .040f),
                    radius = size.minDimension * .33f,
                    center = Offset(size.width * .13f, size.height * .88f),
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "COLORWAVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary,
                            fontWeight = FontWeight.Bold,
                        )
                        AnimatedContent(
                            targetState = profile,
                            label = "profile_title",
                        ) { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "${state.activeDeviceName.ifBlank { "Speaker" }} • Audio Engine",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        EngineStatePill(state.masterEnable)
                        Spacer(Modifier.height(9.dp))
                        LiquidSwitch(
                            checked = state.masterEnable,
                            onCheckedChange = {
                                haptics.confirm()
                                onMaster(it)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Canvas(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(144.dp),
                ) {
                    val barCount = 16
                    val gap = size.width / (barCount + 1)
                    val center = size.height * .48f
                    val maxBar = size.height * .72f

                    repeat(barCount) { i ->
                        val normalized = i / (barCount - 1f)
                        val envelope =
                            (.18f + .82f * sin(normalized * PI).toFloat())
                                .coerceAtLeast(.16f)

                        val motion =
                            if (state.masterEnable) {
                                .46f +
                                    .54f *
                                    ((sin(phase + i * .49f) + 1f) / 2f)
                            } else {
                                .17f
                            }

                        val h = maxBar * envelope * motion
                        val x = gap * (i + 1)

                        drawLine(
                            brush =
                                Brush.verticalGradient(
                                    listOf(
                                        tertiary.copy(alpha = .94f),
                                        primary,
                                    ),
                                    startY = center - h / 2f,
                                    endY = center + h / 2f,
                                ),
                            start = Offset(x, center - h / 2f),
                            end = Offset(x, center + h / 2f),
                            strokeWidth = 6.5.dp.toPx(),
                            cap = StrokeCap.Round,
                        )

                        drawLine(
                            color = primary.copy(alpha = .055f),
                            start = Offset(x, center + h / 2f + 6.dp.toPx()),
                            end = Offset(
                                x,
                                center + h / 2f + 6.dp.toPx() + h * .24f,
                            ),
                            strokeWidth = 4.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    drawLine(
                        color = outline.copy(alpha = .16f),
                        start = Offset(0f, size.height * .93f),
                        end = Offset(size.width, size.height * .93f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroChip(
                        icon = Icons.Default.Speaker,
                        text = state.activeDeviceName.ifBlank { "Speaker" },
                    )
                    HeroChip(
                        icon = Icons.Default.GraphicEq,
                        text = profile,
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStatePill(active: Boolean) {
    val dotColor =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
        }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .11f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(7.dp)) {
                drawCircle(dotColor)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) "LIVE" else "OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .14f)),
    ) {
        Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileDeck(
    selectedName: String,
    onProfile: (ColorWaveProfile) -> Unit,
) {
    val haptics = rememberColorWaveHaptics()
    val scope = rememberCoroutineScope()
    val initial = profiles.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { profiles.size })
    var firstEmission by remember { mutableStateOf(true) }
    var lastApplied by remember { mutableIntStateOf(initial) }

    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (firstEmission) {
            firstEmission = false
            lastApplied = page
        } else if (page != lastApplied) {
            lastApplied = page
            haptics.confirm()
            onProfile(profiles[page])
        }
    }

    FrostedSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(188.dp),
                contentPadding = PaddingValues(horizontal = 42.dp),
                pageSpacing = 10.dp,
            ) { page ->
                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                ProfileCard(
                    profile = profiles[page],
                    selected = page == pagerState.currentPage,
                    pageOffset = offset,
                    onClick = {
                        scope.launch {
                            haptics.click()
                            pagerState.animateScrollToPage(page)
                        }
                    },
                )
            }

            Spacer(Modifier.height(9.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(profiles.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .width(if (selected) 26.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }

            if (selectedName == "Custom") {
                Text(
                    "Custom changes are active",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ColorWaveProfile,
    selected: Boolean,
    pageOffset: Float,
    onClick: () -> Unit,
) {
    val normalizedOffset = pageOffset.coerceIn(-1f, 1f)
    val scale =
        lerp(
            start = .89f,
            stop = 1f,
            fraction = 1f - abs(normalizedOffset),
        )
    val alpha =
        lerp(
            start = .60f,
            stop = 1f,
            fraction = 1f - abs(normalizedOffset),
        )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (pressed) .965f else 1f,
            animationSpec = spring(dampingRatio = .72f, stiffness = 480f),
            label = "profile_press",
        )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier =
            Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = scale * pressScale
                    scaleY = scale * pressScale
                    this.alpha = alpha
                    rotationY = normalizedOffset * -3.2f
                    cameraDistance = 24f * density
                },
        shape = RoundedCornerShape(29.dp),
        color = Color.Transparent,
        shadowElevation = if (selected) 3.dp else 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(profile.gradient)),
        ) {
            ProfileBackdrop(profile = profile)

            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = .075f),
                    radius = size.minDimension * .50f,
                    center = Offset(size.width * .92f, size.height * .02f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = .035f),
                    radius = size.minDimension * .35f,
                    center = Offset(size.width * .18f, size.height * .92f),
                )
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(if (selected) 50.dp else 46.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = .19f),
                        border =
                            BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = .15f),
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                profile.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (selected) 27.dp else 24.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = .92f),
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp).size(17.dp),
                                tint = profile.gradient.first(),
                            )
                        }
                    }
                }

                Column {
                    Text(
                        profile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        profile.subtitle,
                        color = Color.White.copy(alpha = .84f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBackdrop(profile: ColorWaveProfile) {
    Canvas(Modifier.fillMaxSize()) {
        val path = Path()
        var x = -20f
        while (x <= size.width + 20f) {
            val n = x / size.width
            val y = size.height * .50f + sin(n * PI.toFloat() * 3.2f + profile.name.length * .4f) * size.height * .14f
            if (x <= -19f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 5f
        }
        drawPath(
            path,
            color = Color.White.copy(alpha = .11f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color.White.copy(alpha = .06f),
            radius = size.minDimension * .46f,
            center = Offset(size.width * .92f, size.height * .08f),
        )
    }
}

@Composable
private fun SoundCharacterPanel(
    state: EffectState,
    vm: MainViewModel,
) {
    val bassMacro =
        ((state.bass.gain - 50) * 100 / 300)
            .coerceIn(0, 100)
            .toFloat()

    val detailMacro =
        (state.clarity.gain * 100 / 300)
            .coerceIn(0, 100)
            .toFloat()

    val spaceMacro =
        (state.stereoImager.midWidth - 80)
            .coerceIn(0, 100)
            .toFloat()

    FrostedSurface(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            LabeledSlider(
                label = "Bass weight",
                value = bassMacro,
                onValueChange = {
                    vm.applyColorWaveBassMacro(it.roundToInt())
                },
                valueRange = 0f..100f,
                valueLabel = "${bassMacro.roundToInt()}%",
            )

            HorizontalDivider(
                color =
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .14f),
            )

            LabeledSlider(
                label = "Detail",
                value = detailMacro,
                onValueChange = {
                    vm.applyColorWaveDetailMacro(it.roundToInt())
                },
                valueRange = 0f..100f,
                valueLabel = "${detailMacro.roundToInt()}%",
            )

            HorizontalDivider(
                color =
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .14f),
            )

            LabeledSlider(
                label = "Space",
                value = spaceMacro,
                onValueChange = {
                    vm.applyColorWaveSpaceMacro(it.roundToInt())
                },
                valueRange = 0f..100f,
                valueLabel = "${spaceMacro.roundToInt()}%",
            )
        }
    }
}

@Composable
private fun StudioLaunchCard(onClick: () -> Unit) {
    FrostedSurface(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) { AnimatedNavEq(color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ColorWave Studio", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Open the complete processing chain", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("OPEN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StudioPage(
    state: EffectState,
    vm: MainViewModel,
) {
    val haptics = rememberColorWaveHaptics()
    var group by remember { mutableIntStateOf(0) }
    val groups = listOf("Output", "Tone", "Space", "Advanced")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 116.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SectionHeading(
                    title = "Studio",
                    subtitle = "The complete ColorWave processing chain",
                )
                Spacer(Modifier.height(13.dp))
                StudioSegmentedControl(
                    labels = groups,
                    selected = group,
                    onSelect = {
                        haptics.click()
                        group = it
                    },
                )
                Spacer(Modifier.height(14.dp))
                StudioVisualHeader(group = group, state = state)
            }
        }

        when (group) {
            0 -> {
                item { StudioGroupTitle("Output", "Level, loudness and dynamics") }
                item { MasterLimiterRows(state, vm) }
                item { PlaybackGainSection(state, vm) }
                item { LUFSTargetingSection(state, vm) }
                item { MultibandCompressorSection(state, vm) }
                item { FetCompressorSection(state, vm) }
            }
            1 -> {
                item { StudioGroupTitle("Tone", "Correction, EQ, bass and clarity") }
                item { DdcSection(state, vm) }
                item { SpectrumExtensionSection(state, vm) }
                item { EqualizerSection(state, vm) }
                item { DynamicEqSection(state, vm) }
                item { PsychoacousticBassSection(state, vm) }
                item { ViperBassSection(state, vm) }
                item { ViperBassMonoSection(state, vm) }
                item { ViperClaritySection(state, vm) }
            }
            2 -> {
                item { StudioGroupTitle("Space", "Width, surround and ambience") }
                item { FieldSurroundSection(state, vm) }
                item { DiffSurroundSection(state, vm) }
                item { StereoImagerSection(state, vm) }
                item { HeadphoneSurroundSection(state, vm) }
                item { ReverberationSection(state, vm) }
            }
            else -> {
                item { StudioGroupTitle("Advanced", "Character, convolution and protection") }
                item { DynamicSystemSection(state, vm) }
                item { TubeSimulatorSection(state, vm) }
                item { AnalogXSection(state, vm) }
                item { ConvolverSection(state, vm) }
                item { AuditoryProtectionSection(state, vm) }
                item { SpeakerOptSection(state, vm) }
            }
        }
    }
}

@Composable
private fun StudioSegmentedControl(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .15f)),
    ) {
        Row(modifier = Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(17.dp),
                    color = if (selected == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = if (selected == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioVisualHeader(
    group: Int,
    state: EffectState,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val transition = rememberInfiniteTransition(label = "studio_visual")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2800)),
            label = "studio_phase",
        )

    FrostedSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = .075f),
                                tertiary.copy(alpha = .035f),
                                Color.Transparent,
                            ),
                        ),
                    ),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .padding(16.dp),
            ) {
                when (group) {
                    0 -> {
                        repeat(12) { i ->
                            val x = size.width * (i + 1) / 15f
                            val motion =
                                (sin(phase + i * .62f) + 1f) / 2f
                            val h =
                                size.height *
                                    (.16f +
                                        .66f *
                                        (.40f + .60f * motion))
                            drawLine(
                                brush =
                                    Brush.verticalGradient(
                                        listOf(tertiary, primary),
                                    ),
                                start = Offset(x, size.height - h),
                                end = Offset(x, size.height),
                                strokeWidth = 6.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    1 -> {
                        val path = Path()
                        val bands = state.eq.bands.ifEmpty { List(10) { 0.0 } }
                        bands.forEachIndexed { index, value ->
                            val x =
                                size.width *
                                    index /
                                    (bands.size - 1).coerceAtLeast(1)
                            val animated =
                                value.toFloat() +
                                    sin(phase + index * .5f) * .12f
                            val y =
                                size.height / 2f -
                                    (animated / 8f) *
                                    size.height *
                                    .40f
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawLine(
                            color = outlineVariant.copy(alpha = .32f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1.dp.toPx(),
                        )

                        drawPath(
                            path,
                            brush =
                                Brush.horizontalGradient(
                                    listOf(primary, tertiary),
                                ),
                            style =
                                Stroke(
                                    width = 5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                        )
                    }

                    2 -> {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val pulse = .92f + .08f * sin(phase)

                        repeat(4) { i ->
                            drawCircle(
                                color =
                                    primary.copy(
                                        alpha = .08f + i * .055f,
                                    ),
                                radius =
                                    size.minDimension *
                                        (.14f + i * .095f) *
                                        pulse,
                                center = center,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }

                        drawLine(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(tertiary, primary),
                                ),
                            start = Offset(size.width * .10f, center.y),
                            end = Offset(size.width * .90f, center.y),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    else -> {
                        repeat(3) { row ->
                            val y = size.height * (.25f + row * .25f)
                            drawLine(
                                color = outlineVariant.copy(alpha = .28f),
                                start = Offset(size.width * .08f, y),
                                end = Offset(size.width * .92f, y),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )

                            val base = .25f + row * .21f
                            val travel =
                                .06f *
                                    ((sin(phase + row * 1.4f) + 1f) / 2f)

                            drawCircle(
                                color = if (row == 1) tertiary else primary,
                                radius = 7.dp.toPx(),
                                center = Offset(size.width * (base + travel), y),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioGroupTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SystemPage(
    state: EffectState,
    driverInstalled: Boolean,
    driverStreaming: Boolean,
    sampleRate: Int,
    aidl: Boolean,
    global: Boolean,
    autoStart: Boolean,
    version: String,
    onDevices: () -> Unit,
    onPresets: () -> Unit,
    onDriver: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeading(
                title = "System",
                subtitle = "Engine health, devices and saved configuration",
            )
        }

        item {
            EngineHealthCard(
                driverInstalled = driverInstalled,
                streaming = driverStreaming,
                sampleRate = sampleRate,
                aidl = aidl,
                output = state.activeDeviceName.ifBlank { "Speaker" },
            )
        }

        item { SystemAction(Icons.Default.Devices, "Output devices", "Per-device ColorWave profiles", onDevices) }
        item { SystemAction(Icons.Default.LibraryMusic, "Saved profiles", "Your custom sound signatures", onPresets) }
        item { SystemAction(Icons.Default.Info, "Engine diagnostics", "Native DSP state and audio stream", onDriver) }
        item { SystemAction(Icons.Default.Settings, "ColorWave settings", "Startup, imports and engine behavior", onSettings) }

        item {
            FrostedSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SystemStat("Global processing", if (global) "On" else "Off")
                    SystemStat("Auto start", if (autoStart) "On" else "Off")
                    SystemStat("Interface", if (aidl) "AIDL" else "Legacy")
                    SystemStat("ColorWave", version.ifBlank { "dev" })
                }
            }
        }
    }
}

@Composable
private fun EngineHealthCard(
    driverInstalled: Boolean,
    streaming: Boolean,
    sampleRate: Int,
    aidl: Boolean,
    output: String,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val transition = rememberInfiniteTransition(label = "engine_ring")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000)),
        label = "engine_spin",
    )

    FrostedSurface(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    drawCircle(
                        color = surfaceVariant,
                        radius = size.minDimension * .42f,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                        startAngle = spin,
                        sweepAngle = if (streaming) 300f else 225f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(30.dp), tint = primary)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (driverInstalled) "ColorWave Engine" else "Engine unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        !driverInstalled -> "Native DSP could not be reached"
                        streaming -> "Processing audio now"
                        else -> "Ready for audio"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniStatus(if (aidl) "AIDL" else "Legacy")
                    MiniStatus(if (sampleRate > 0) "${sampleRate / 1000f} kHz" else "—")
                    MiniStatus(output)
                }
            }
        }
    }
}

@Composable
private fun MiniStatus(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .60f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SystemAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .98f else 1f,
        animationSpec = spring(),
        label = "system_action",
    )

    Surface(
        modifier = Modifier.fillMaxWidth().scale(scale),
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .15f)),
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .09f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SystemStat(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
