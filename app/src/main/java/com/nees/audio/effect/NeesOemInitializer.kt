package com.nees.audio.effect

import com.nees.audio.audio.AudioDevice
import com.nees.audio.data.model.DeviceSettings
import com.nees.audio.data.repository.ViperRepository
import com.nees.audio.utils.FileLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object NeesOemInitializer {
    private const val MARKER = "nees_oem_defaults_v4"
    private val mutex = Mutex()

    suspend fun ensureInstalled(repository: ViperRepository): Boolean =
        mutex.withLock {
            if (repository.getBooleanPreference(MARKER, false).first()) {
                return@withLock false
            }

            val state = NeesOemDefaults.speaker()

            saveEffectPrefs(repository, state)
            repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, true)
            repository.setBooleanPreference(ViperRepository.PREF_GLOBAL_MODE, true)
            repository.setBooleanPreference(ViperRepository.PREF_AUTO_START, true)

            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = AudioDevice.ID_SPEAKER,
                    deviceName = "Speaker",
                    isHeadphone = false,
                    settingsJson = serializeEffectPrefs(state).toString(),
                ),
            )

            repository.setBooleanPreference(MARKER, true)

            FileLogger.i(
                "OEM",
                "ColorWave Rodin v4 committed: exclusive-DSP speaker tuning",
            )
            true
        }
}
