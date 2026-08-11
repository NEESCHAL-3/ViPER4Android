package com.nees.audio.effect

/**
 * ColorWave factory Studio reference.
 *
 * This overlays only the values deliberately captured from the requested
 * Output/Dynamics screenshots. Everything not listed here remains inherited
 * from the existing Rodin factory tuning.
 *
 * Visible reference:
 *   Output Gain       6.0 dB  -> raw 200
 *   Output Pan        50:50   -> raw 0
 *   Threshold Limit  -0.1 dB -> raw 99
 *
 *   Playback Gain Control ON
 *   Strength          2.4x    -> raw 240
 *   Max Gain          7.2x    -> raw 720
 *   Output Threshold -0.5 dB -> raw 94
 *
 *   LUFS Targeting ON
 *   Target           -13.6 LUFS -> raw 136
 *   Max Gain           9.7 dB   -> raw 97
 *   Speed              Fast     -> raw 2
 *
 *   Multiband Compressor master OFF
 *   Sub band enabled ON
 *   Sub upper crossover 200 Hz
 *   Sub threshold -18 dB
 *   Sub ratio 0.5 -> raw 50
 *   Sub Auto Knee ON
 *   Sub Knee 0 dB
 */
object ColorWaveFactoryPreset {
    private fun replaceFirst(
        values: List<Int>,
        value: Int,
    ): List<Int> {
        if (values.isEmpty()) return values
        return values.toMutableList().apply { this[0] = value }
    }

    private fun replaceFirstBool(
        values: List<Boolean>,
        value: Boolean,
    ): List<Boolean> {
        if (values.isEmpty()) return values
        return values.toMutableList().apply { this[0] = value }
    }

    fun apply(base: EffectState): EffectState {
        val mbc = base.multibandCompressor

        return base.copy(
            // ColorWave Tone factory defaults:
            // ViPER Bass ON / Subwoofer mode / 6.2x
            // ViPER Clarity ON / XHiFi mode / 1.5x
            bass =
                base.bass.copy(
                    enable = true,
                    mode = 2,
                    gain = 620,
                ),
            clarity =
                base.clarity.copy(
                    enable = true,
                    mode = 2,
                    gain = 150,
                ),
            out =
                base.out.copy(
                    volume = 200,
                    channelPan = 0,
                    limiter = 99,
                ),
            playbackGainControl =
                base.playbackGainControl.copy(
                    enable = true,
                    strength = 240,
                    maxGain = 720,
                    outputThreshold = 94,
                ),
            lufs =
                base.lufs.copy(
                    enable = true,
                    target = 136,
                    maxGain = 97,
                    speed = 2,
                ),
            multibandCompressor =
                mbc.copy(
                    enable = false,
                    bandEnables = replaceFirstBool(mbc.bandEnables, true),
                    crossovers = replaceFirst(mbc.crossovers, 200),
                    thresholds = replaceFirst(mbc.thresholds, -18),
                    ratios = replaceFirst(mbc.ratios, 50),
                    kneeAutos = replaceFirstBool(mbc.kneeAutos, true),
                    knees = replaceFirst(mbc.knees, 0),
                ),
        )
    }
}
