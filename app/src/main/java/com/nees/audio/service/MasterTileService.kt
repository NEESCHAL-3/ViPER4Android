package com.nees.audio.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nees.audio.R
import com.nees.audio.data.repository.ViperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MasterTileService : TileService() {
    @Inject
    lateinit var repository: ViperRepository

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate,
        )

    override fun onStartListening() {
        super.onStartListening()

        scope.launch {
            val enabled =
                repository
                    .getBooleanPreference(
                        ViperRepository.PREF_MASTER_ENABLE,
                        false,
                    )
                    .first()

            render(enabled)
        }
    }

    override fun onClick() {
        super.onClick()

        scope.launch {
            val current =
                repository
                    .getBooleanPreference(
                        ViperRepository.PREF_MASTER_ENABLE,
                        false,
                    )
                    .first()

            val next = !current

            render(next)

            repository.setBooleanPreference(
                ViperRepository.PREF_MASTER_ENABLE,
                next,
            )

            ViperService.toggleMaster(
                this@MasterTileService,
                next,
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return

        tile.state =
            if (enabled) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }

        tile.label = getString(R.string.qs_tile_master_label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "On" else "Off"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription =
                if (enabled) {
                    "ColorWave audio processing on"
                } else {
                    "ColorWave audio processing off"
                }
        }

        tile.updateTile()
    }
}
