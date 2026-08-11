package com.nees.audio.ui.screens.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nees.audio.R
import com.nees.audio.ui.components.rememberColorWaveHaptics
import com.nees.audio.ui.screens.main.DriverStatus

@Composable
fun DriverStatusDialog(driverStatus: DriverStatus, onDismiss: () -> Unit) {
    val haptics = rememberColorWaveHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = { EngineGlyph(active = driverStatus.streaming) },
        title = { Text("ColorWave Engine", fontWeight = FontWeight.Bold) },
        text = {
            if (!driverStatus.installed) {
                Text(
                    text = stringResource(R.string.driver_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(if (driverStatus.streaming) "Processing" else "Ready", driverStatus.streaming)
                    Spacer(Modifier.height(4.dp))
                    StatusRow("Version", driverStatus.versionName)
                    StatusRow("Architecture", driverStatus.architecture)
                    StatusRow("Sample rate", if (driverStatus.samplingRate > 0) "${driverStatus.samplingRate / 1000f} kHz" else "—")
                    StatusRow("Interface", "AIDL / native effect")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun EngineGlyph(active: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = primary.copy(alpha = .10f)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(48.dp)) {
                drawCircle(
                    color = primary.copy(alpha = .16f),
                    radius = size.minDimension * .48f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = if (active) 300f else 210f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            Icon(Icons.Default.Memory, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
