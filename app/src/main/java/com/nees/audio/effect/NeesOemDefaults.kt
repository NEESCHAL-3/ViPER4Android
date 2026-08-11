package com.nees.audio.effect

/**
 * ColorWave factory tuning.
 *
 * Rodin speaker profile is intentionally tuned above the stock FS19xx SmartPA
 * protection/calibration layer. Do not compensate by modifying SmartPA gain,
 * thermal thresholds, or SPK_* vendor calibration files.
 */
object NeesOemDefaults {

    fun speaker(): EffectState =
        EffectState(
            masterEnable = true,

            // Keep real peak headroom. Adaptive gain below supplies perceived
            // loudness; the final software limiter catches boosted peaks.
            out =
                OutputState(
                    volume = 100,
                    channelPan = 0,
                    limiter = 90,
                ),

            // Quiet/medium content gets useful lift without a fixed preamp.
            playbackGainControl =
                PlaybackGainControlState(
                    enable = true,
                    strength = 65,
                    maxGain = 130,
                    outputThreshold = 94,
                ),

            // Keep LUFS/MBC/FET off in the factory speaker profile for now.
            // Rodin already has level-dependent FS19xx processing downstream.
            lufs = LufsState(enable = false),
            fetCompressor = FetCompressorState(enable = false),
            multibandCompressor = MultibandCompressorState(enable = false),

            // 10-band phone-speaker contour:
            // avoid wasting headroom on sub-bass, add punch, reduce boxiness,
            // preserve vocal presence and restrained detail.
            eq =
                EqState(
                    enable = true,
                    bandCount = 10,
                    bands =
                        listOf(
                            -1.5,
                            -0.5,
                            2.2,
                            1.4,
                            -1.1,
                            -0.3,
                            0.8,
                            1.4,
                            0.8,
                            0.2,
                        ),
                    bandsMap =
                        mapOf(
                            10 to
                                listOf(
                                    -1.5,
                                    -0.5,
                                    2.2,
                                    1.4,
                                    -1.1,
                                    -0.3,
                                    0.8,
                                    1.4,
                                    0.8,
                                    0.2,
                                ),
                        ),
                ),

            // Perceptual bass gives a small phone speaker more apparent low end
            // without demanding impossible deep-bass excursion.
            psychoacousticBass =
                PsychoacousticBassState(
                    enable = true,
                    cutoff = 86,
                    intensity = 34,
                    harmonicOrder = 2,
                    originalLevel = 92,
                ),

            // Controlled conventional bass. UI frequency 72 is serialized to
            // raw 87 by ParamRaw.bassFrequency().
            bass =
                BassState(
                    enable = true,
                    mode = 0,
                    frequency = 72,
                    gain = 68,
                    antiPop = true,
                ),

            clarity =
                ClarityState(
                    enable = true,
                    mode = 0,
                    gain = 16,
                ),

            stereoImager =
                StereoImagerState(
                    enable = true,
                    lowWidth = 100,
                    midWidth = 106,
                    highWidth = 110,
                    lowCrossover = 200,
                    highCrossover = 4000,
                ),

            spectrumExtension = SpectrumExtensionState(enable = false),
            dynamicEq = DynamicEqState(enable = false),
            convolver = ConvolverState(enable = false),
            fieldSurround = FieldSurroundState(enable = false),
            diffSurround = DiffSurroundState(enable = false),
            reverb = ReverbState(enable = false),
            dynamicSystem = DynamicSystemState(enable = false),
            bassMono = BassMonoState(enable = false),
            cure = CureState(enable = false),
            analogX = AnalogXState(enable = false),
            tubeSimulator = TubeSimulatorState(enable = false),
            speakerCorrection = SpeakerCorrectionState(enable = false),
        )

    fun headphone(): EffectState =
        EffectState(
            masterEnable = true,
            out =
                OutputState(
                    volume = 100,
                    channelPan = 0,
                    limiter = 94,
                ),
            playbackGainControl =
                PlaybackGainControlState(
                    enable = true,
                    strength = 40,
                    maxGain = 110,
                    outputThreshold = 94,
                ),
            eq =
                EqState(
                    enable = true,
                    bandCount = 10,
                    bands = listOf(2.4, 2.0, 1.2, 0.5, -0.4, 0.0, 0.8, 1.4, 1.8, 1.2),
                    bandsMap =
                        mapOf(
                            10 to listOf(2.4, 2.0, 1.2, 0.5, -0.4, 0.0, 0.8, 1.4, 1.8, 1.2),
                        ),
                ),
            psychoacousticBass =
                PsychoacousticBassState(
                    enable = true,
                    cutoff = 72,
                    intensity = 18,
                    harmonicOrder = 2,
                    originalLevel = 96,
                ),
            bass =
                BassState(
                    enable = true,
                    mode = 0,
                    frequency = 60,
                    gain = 70,
                    antiPop = true,
                ),
            clarity =
                ClarityState(
                    enable = true,
                    mode = 0,
                    gain = 14,
                ),
            stereoImager =
                StereoImagerState(
                    enable = true,
                    lowWidth = 100,
                    midWidth = 108,
                    highWidth = 112,
                    lowCrossover = 180,
                    highCrossover = 4200,
                ),
        )

    /**
     * Select the factory state for the current Android audio output.
     *
     * Kept tolerant because service revisions may pass an enum,
     * data object, or route label.
     */
    fun forDevice(device: Any?): EffectState {
        val route = device?.toString()?.lowercase().orEmpty()
        val personalAudio =
            route.contains("headphone") ||
                route.contains("headset") ||
                route.contains("wired") ||
                route.contains("usb") ||
                route.contains("bluetooth") ||
                route.contains("a2dp") ||
                route.contains("ble")

        return ColorWaveFactoryPreset.apply(if (personalAudio) headphone() else speaker())
    }

}
