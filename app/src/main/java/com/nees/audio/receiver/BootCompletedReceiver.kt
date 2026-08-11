package com.nees.audio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nees.audio.data.repository.ViperRepository
import com.nees.audio.service.ViperService
import com.nees.audio.utils.FileLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: ViperRepository

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val supportedAction =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                intent.action == Intent.ACTION_USER_UNLOCKED
        if (!supportedAction) return

        val autoStart =
            runBlocking {
                repository.getBooleanPreference(ViperRepository.PREF_AUTO_START, true).first()
            }
        if (!autoStart) return

        try {
            ViperService.startService(context)
        } catch (e: Exception) {
            FileLogger.e(
                "BootReceiver",
                "Cannot start FGS from boot, will start on next app open",
                e,
            )
        }
    }
}
