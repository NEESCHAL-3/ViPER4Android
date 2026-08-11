package com.nees.audio.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nees.audio.ui.components.LiquidSwitch
import com.nees.audio.ui.components.rememberColorWaveHaptics
import com.nees.audio.ui.screens.main.DriverStatus

@Composable
fun SettingsDialog(
    autoStartEnabled: Boolean,
    globalModeEnabled: Boolean,
    aidlModeActive: Boolean,
    driverStatus: DriverStatus,
    appVersionName: String,
    onAutoStartChanged: (Boolean) -> Unit,
    onGlobalModeChanged: (Boolean) -> Unit,
    onImportPreset: () -> Unit,
    onImportKernel: () -> Unit,
    onDebugUnlocked: () -> Unit,
    onImportVdc: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberColorWaveHaptics()
    val taps = remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { haptics.click(); onDismiss() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ColorWave", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("Settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(48.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 64.dp),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 34.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SettingsHero(driverStatus.installed, aidlModeActive) }

                    item { SectionLabel("Engine behavior") }
                    item {
                        SettingsGroup {
                            ToggleRow(
                                Icons.Default.RestartAlt,
                                "Start automatically",
                                "Restore the active ColorWave state after boot",
                                autoStartEnabled,
                                onAutoStartChanged,
                            )
                            Divider()
                            ToggleRow(
                                Icons.Default.Tune,
                                "Global processing",
                                "Apply ColorWave on the Android global audio session",
                                globalModeEnabled,
                                onGlobalModeChanged,
                            )
                        }
                    }

                    item { SectionLabel("Sound library") }
                    item {
                        SettingsGroup {
                            ActionRow(Icons.Default.FileOpen, "Import profile", "Load a saved ColorWave sound profile", onImportPreset)
                            Divider()
                            ActionRow(Icons.Default.Memory, "Import convolution", "Add an impulse response kernel", onImportKernel)
                            Divider()
                            ActionRow(Icons.Default.Tune, "Import device correction", "Add a DDC/VDC correction profile", onImportVdc)
                        }
                    }

                    item { SectionLabel("Engine information") }
                    item {
                        SettingsGroup {
                            InfoRow(Icons.Default.Memory, "DSP", if (driverStatus.installed) driverStatus.versionName else "Unavailable") {
                                taps.intValue++
                                if (taps.intValue >= 7) {
                                    taps.intValue = 0
                                    haptics.confirm()
                                    onDebugUnlocked()
                                    Toast.makeText(context, "Developer diagnostics enabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Divider()
                            InfoRow(Icons.Default.Info, "Architecture", if (driverStatus.installed) driverStatus.architecture else "—")
                            Divider()
                            InfoRow(Icons.Default.Tune, "Processing interface", if (aidlModeActive) "AIDL" else "Legacy")
                            Divider()
                            InfoRow(Icons.Default.Info, "ColorWave", appVersionName.ifBlank { "dev" })
                            Divider()
                            InfoRow(Icons.Default.AutoAwesome, "Appearance", "System light / dark")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHero(driverReady: Boolean, aidl: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .11f)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (driverReady) "Engine ready" else "Engine unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (driverReady) "${if (aidl) "Native AIDL" else "Legacy"} processing is available"
                    else "ColorWave could not reach its native DSP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 7.dp, bottom = 1.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .16f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(icon)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        LiquidSwitch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val haptics = rememberColorWaveHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { haptics.click(); onClick() }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(icon)
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(icon)
        Spacer(Modifier.width(13.dp))
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .09f)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .16f))
}
