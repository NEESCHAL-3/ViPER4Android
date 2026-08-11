package com.nees.audio.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

class ColorWaveHaptics(
    private val context: Context,
    private val view: View,
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                context
                    .getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun pulse(
        effect: Int,
        fallback: Int,
    ) {
        val vib = vibrator

        if (Build.VERSION.SDK_INT >= 29 && vib?.hasVibrator() == true) {
            try {
                vib.vibrate(VibrationEffect.createPredefined(effect))
                return
            } catch (_: Throwable) {
            }
        }

        view.performHapticFeedback(fallback)
    }

    // Lightweight system haptic for sliders: avoids vibrator-service spam.
    fun textureTick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun click() {
        if (Build.VERSION.SDK_INT >= 29) {
            pulse(
                VibrationEffect.EFFECT_CLICK,
                HapticFeedbackConstants.VIRTUAL_KEY,
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun confirm() {
        if (Build.VERSION.SDK_INT >= 29) {
            pulse(
                VibrationEffect.EFFECT_HEAVY_CLICK,
                HapticFeedbackConstants.CONFIRM,
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
}

@Composable
fun rememberColorWaveHaptics(): ColorWaveHaptics {
    val context = LocalContext.current
    val view = LocalView.current

    return remember(context, view) {
        ColorWaveHaptics(
            context = context,
            view = view,
        )
    }
}
