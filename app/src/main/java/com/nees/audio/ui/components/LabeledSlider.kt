package com.nees.audio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.roundToInt

data class SliderEdit(
    val displayValue: Double,
    val displayRange: ClosedFloatingPointRange<Double>,
    val decimals: Int,
    val onCommit: (Double) -> Unit,
    val unit: String = "",
)

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: String? = null,
    edit: SliderEdit? = null,
) {
    val haptics = rememberColorWaveHaptics()
    var showEdit by remember { mutableStateOf(false) }
    var lastHapticBucket by remember { mutableIntStateOf(Int.MIN_VALUE) }

    fun dispatch(next: Float) {
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
        val fraction = ((next - valueRange.start) / span).coerceIn(0f, 1f)

        // 5% detents: tactile, but light enough to keep dragging fluid.
        val bucket = (fraction * 20f).roundToInt()
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket
            haptics.textureTick()
        }

        onValueChange(next)
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            Surface(
                modifier =
                    Modifier
                        .widthIn(min = 62.dp)
                        .clickable(enabled = enabled && edit != null) {
                            haptics.click()
                            showEdit = true
                        },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f),
            ) {
                Text(
                    text = valueLabel ?: value.roundToInt().toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        OemSmoothSlider(
            value = value,
            onValueChange = ::dispatch,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }

    if (showEdit && edit != null) {
        NumberInputDialog(
            label = label,
            edit = edit,
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun OemSmoothSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
) {
    var dragging by remember { mutableStateOf(false) }

    val animatedValue by
        animateFloatAsState(
            targetValue = value,
            animationSpec =
                spring(
                    dampingRatio = .90f,
                    stiffness = 720f,
                ),
            label = "oem_slider_value",
        )

    val displayedValue = if (dragging) value else animatedValue
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val thumbInner = MaterialTheme.colorScheme.surface
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
    val density = LocalDensity.current
    val sidePx = with(density) { 14.dp.toPx() }

    fun quantize(v: Float): Float {
        val raw = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return raw

        val intervals = steps + 1
        val fraction = (raw - valueRange.start) / span
        val snapped = (fraction * intervals).roundToInt().toFloat() / intervals
        return valueRange.start + snapped * span
    }

    fun valueAt(
        x: Float,
        width: Float,
    ): Float {
        val usable = (width - sidePx * 2f).coerceAtLeast(1f)
        val fraction = ((x - sidePx) / usable).coerceIn(0f, 1f)
        return quantize(valueRange.start + fraction * span)
    }

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(enabled, valueRange, steps) {
                    if (enabled) {
                        detectTapGestures { position ->
                            onValueChange(
                                valueAt(
                                    position.x,
                                    size.width.toFloat(),
                                ),
                            )
                        }
                    }
                }
                .pointerInput(enabled, valueRange, steps) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { position ->
                                dragging = true
                                onValueChange(
                                    valueAt(
                                        position.x,
                                        size.width.toFloat(),
                                    ),
                                )
                            },
                            onDragEnd = {
                                dragging = false
                            },
                            onDragCancel = {
                                dragging = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onValueChange(
                                    valueAt(
                                        change.position.x,
                                        size.width.toFloat(),
                                    ),
                                )
                            },
                        )
                    }
                },
    ) {
        val fraction =
            ((displayedValue - valueRange.start) / span)
                .coerceIn(0f, 1f)

        val startX = sidePx
        val endX = size.width - sidePx
        val trackWidth = (endX - startX).coerceAtLeast(1f)
        val x = startX + trackWidth * fraction
        val y = size.height / 2f
        val trackHeight = 15.dp.toPx()
        val radius = trackHeight / 2f

        // Soft OEM pill.
        drawRoundRect(
            color =
                inactive.copy(
                    alpha = if (enabled) .78f else .34f,
                ),
            topLeft = Offset(startX, y - trackHeight / 2f),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )

        if (fraction > .001f) {
            drawRoundRect(
                color =
                    active.copy(
                        alpha = if (enabled) 1f else .42f,
                    ),
                topLeft = Offset(startX, y - trackHeight / 2f),
                size =
                    Size(
                        (x - startX).coerceAtLeast(trackHeight),
                        trackHeight,
                    ),
                cornerRadius = CornerRadius(radius, radius),
            )
        }

        val outerRadius =
            if (dragging) {
                13.5.dp.toPx()
            } else {
                12.5.dp.toPx()
            }

        // Blue ring + white center, like the ColorOS volume slider language.
        drawCircle(
            color =
                active.copy(
                    alpha = if (enabled) 1f else .45f,
                ),
            radius = outerRadius,
            center = Offset(x, y),
        )
        drawCircle(
            color = thumbInner,
            radius = 7.2.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

@Composable
fun NumberInputDialog(
    label: String,
    edit: SliderEdit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberColorWaveHaptics()

    fun fmt(v: Double): String =
        if (edit.decimals <= 0) {
            v.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${edit.decimals}f", v)
        }

    var text by remember { mutableStateOf(fmt(edit.displayValue)) }
    val parsed = text.trim().toDoubleOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    "${fmt(edit.displayRange.start)} – ${fmt(edit.displayRange.endInclusive)}${if (edit.unit.isNotBlank()) " ${edit.unit}" else ""}",
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = if (edit.unit.isNotBlank()) ({ Text(edit.unit) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                ) {
                    TextButton(
                        onClick = {
                            haptics.click()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }

                    TextButton(
                        enabled = parsed != null,
                        onClick = {
                            haptics.confirm()
                            edit.onCommit(
                                parsed!!.coerceIn(
                                    edit.displayRange.start,
                                    edit.displayRange.endInclusive,
                                ),
                            )
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
