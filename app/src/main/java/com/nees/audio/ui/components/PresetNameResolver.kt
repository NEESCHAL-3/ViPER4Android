package com.nees.audio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nees.audio.data.model.DsPreset
import com.nees.audio.data.model.EqPreset
import com.nees.audio.viper.ViperDispatcher

@Composable
fun resolvePresetName(preset: EqPreset): String {
    val resId = preset.nameKey?.let { ViperDispatcher.EQ_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}

@Composable
fun resolvePresetName(preset: DsPreset): String {
    val resId = preset.nameKey?.let { ViperDispatcher.DS_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}
