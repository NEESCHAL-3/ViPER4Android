package com.nees.audio.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.util.size
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nees.audio.R
import com.nees.audio.SERVICE_CHANNEL_ID
import com.nees.audio.audio.AudioDevice
import com.nees.audio.audio.AudioOutputDetector
import com.nees.audio.audio.AudioSessionMonitor
import com.nees.audio.data.model.DeviceSettings
import com.nees.audio.data.repository.ViperRepository
import com.nees.audio.effect.EffectState
import com.nees.audio.effect.NeesOemDefaults
import com.nees.audio.effect.NeesOemInitializer
import com.nees.audio.effect.saveEffectPrefs
import com.nees.audio.effect.deserializeEffectPrefs
import com.nees.audio.effect.serializeEffectPrefs
import com.nees.audio.utils.FileLogger
import com.nees.audio.utils.RootShell
import com.nees.audio.utils.WavDecoder
import com.nees.audio.viper.ConfigChannel
import com.nees.audio.viper.ViperDispatcher
import com.nees.audio.viper.ViperEffect
import com.nees.audio.viper.ViperParams
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.inject.Inject

@AndroidEntryPoint
class ViperService : LifecycleService() {
    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.nees.audio.service.START"
        const val ACTION_STOP = "com.nees.audio.service.STOP"
        const val ACTION_TOGGLE_MASTER = "com.nees.audio.service.TOGGLE_MASTER"
        const val EXTRA_MASTER_ENABLED = "com.nees.audio.service.EXTRA_MASTER_ENABLED"

        fun startService(context: Context) {
            val intent =
                Intent(context, ViperService::class.java).apply {
                    action = ACTION_START
                }
            context.startForegroundService(intent)
        }

        fun toggleMaster(
            context: Context,
            enabled: Boolean,
        ) {
            val intent =
                Intent(context, ViperService::class.java).apply {
                    action = ACTION_TOGGLE_MASTER
                    putExtra(EXTRA_MASTER_ENABLED, enabled)
                }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: ViperService get() = this@ViperService
    }

    private val binder = LocalBinder()
    private val sessions = SparseArray<ViperEffect>()
    private var globalEffect: ViperEffect? = null
    private var useAidlTypeUuid: Boolean = true
    private var globalMode: Boolean = false
    private var audioOutputDetector: AudioOutputDetector? = null
    private var sessionMonitor: AudioSessionMonitor? = null
    private var stateProvider: (() -> EffectState)? = null
    private var lastUiState: EffectState? = null
    private var lastBulkDdcKey: String? = null
    private var lastBulkConvolverKey: String? = null
    private var bootMasterEnabled: Boolean = false
    private val masterEnabled: Boolean
        get() = stateProvider?.invoke()?.masterEnable ?: lastUiState?.masterEnable ?: bootMasterEnabled

    fun setStateProvider(provider: () -> EffectState) {
        stateProvider = provider
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        FileLogger.i("Service", "Service created")
        lifecycleScope.launch {
            NeesOemInitializer.ensureInstalled(repository)
            ensureConfigLoaded()
            val state = ViperDispatcher.loadFullStateFromPrefs(repository)
            lastUiState = state
            if (state.masterEnable) {
                applyState(state, true)
            }
            startAudioOutputMonitor()
        }
    }

    private var configLoaded = false

    private suspend fun ensureConfigLoaded() {
        if (configLoaded) return
        // ColorWave ROM uses the AIDL-native DSP exclusively.
        // Never fall back to the legacy AudioEffect type because the
        // baked driver/control plane is AIDL + V4MS shared memory.
        useAidlTypeUuid = true
        globalMode = repository.getBooleanPreference(ViperRepository.PREF_GLOBAL_MODE).first()
        bootMasterEnabled = repository.getBooleanPreference(ViperRepository.PREF_MASTER_ENABLE).first()
        configLoaded = true
    }

    private suspend fun ensureNeesOemDefaults() {
        val markerKey = "nees_oem_defaults_v1"
        if (repository.getBooleanPreference(markerKey, false).first()) return

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

        repository.setBooleanPreference(markerKey, true)

        bootMasterEnabled = true
        globalMode = true
        lastUiState = state

        FileLogger.i(
            "Service",
            "Nees OEM defaults v1 installed: master/global/autostart ON",
        )
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initGlobalEffect() {
        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(0, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create global effect")
            return
        }
        globalEffect = effect
        FileLogger.i("Service", "Global effect created (aidlType=$useAidlTypeUuid)")
    }

    private fun applyState(
        state: EffectState,
        masterOn: Boolean,
        aidlStateAlreadyWritten: Boolean = false,
    ) {
        if (!masterOn) {
            stopSessionMonitor()
            releaseAllSessions()
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
            return
        }
        if (globalMode) {
            if (globalEffect == null) initGlobalEffect()
        } else {
            if (sessionMonitor == null) startSessionMonitor()
        }
        var shmWritten = aidlStateAlreadyWritten
        globalEffect?.let { effect ->
            effect.enabled = true
            if (useAidlTypeUuid) {
                writeAidlFullState(state)
                shmWritten = true
            } else {
                applyFullStateHidl(effect, state, true)
            }
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = true
            if (useAidlTypeUuid) {
                if (!shmWritten) {
                    writeAidlFullState(state)
                    shmWritten = true
                }
            } else {
                applyFullStateHidl(effect, state, true)
            }
        }
    }

    private var currentServiceDeviceId: String = AudioDevice.ID_SPEAKER

    private fun startAudioOutputMonitor() {
        val detector = AudioOutputDetector(this)
        audioOutputDetector = detector
        currentServiceDeviceId = detector.activeDevice.value.id
        lifecycleScope.launch {
            detector.activeDevice.collect { device ->
                if (device.id != currentServiceDeviceId) {
                    FileLogger.i(
                        "Service",
                        "Device changed: $currentServiceDeviceId -> ${device.id} (${device.name})",
                    )
                    currentServiceDeviceId = device.id
                    reapplyForDevice(device)
                }
            }
        }
    }

    private suspend fun reapplyForDevice(device: AudioDevice) {
        val saved = repository.getDeviceSettings(device.id)
        val state: EffectState =
            if (saved != null) {
                FileLogger.i("Service", "Loading device settings from DB for ${device.id}")
                val baseState = ViperDispatcher.loadFullStateFromPrefs(repository)
                val json = JSONObject(saved.settingsJson)
                deserializeEffectPrefs(json, baseState).also {
                    repository.updateDeviceLastConnected(device.id)
                }
            } else {
                FileLogger.i("Service", "No DB entry for ${device.id}, using DataStore defaults")
                val s = NeesOemDefaults.forDevice(device)
                val json = serializeEffectPrefs(s)
                repository.saveDeviceSettings(
                    DeviceSettings(
                        deviceId = device.id,
                        deviceName = device.name,
                        isHeadphone = device.isHeadphone,
                        settingsJson = json.toString(),
                    ),
                )
                s
            }
        applyState(state, masterEnabled)
    }

    private suspend fun dispatchFullStateToEffect(
        effect: ViperEffect,
        skipShmWrite: Boolean = false,
    ) {
        val state = ViperDispatcher.loadFullStateFromPrefs(repository)
        val isMasterOn = masterEnabled
        effect.enabled = isMasterOn
        if (useAidlTypeUuid) {
            if (!skipShmWrite) {
                FileLogger.d(
                    "Service",
                    "AIDL shm apply full state (master=$isMasterOn)",
                )
                writeAidlFullState(state)
            }
            return
        }
        applyFullStateHidl(effect, state, isMasterOn)
    }

    private fun writeAidlFullState(state: EffectState) {
        lastUiState = state
        ConfigChannel.writeFullState(state)
        if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
            applyDdcDeviceAidl(state.ddc.device)
        }
        if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
            applyConvolverKernelAidl(state.convolver.kernelFile)
        }
    }

    private fun applyFullStateHidl(
        effect: ViperEffect,
        state: EffectState,
        masterEnabled: Boolean,
    ) {
        ViperDispatcher.dispatchFullState(effect, state, masterEnabled)
        if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
            applyDdcDeviceHidl(state.ddc.device, effect)
        }
        if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
            applyConvolverKernelHidl(state.convolver.kernelFile, effect)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                FileLogger.i("Service", "Service started")
            }

            ACTION_STOP -> {
                releaseAllSessions()
                globalEffect?.let {
                    it.enabled = false
                    it.release()
                }
                globalEffect = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_TOGGLE_MASTER -> {
                val next = intent.getBooleanExtra(EXTRA_MASTER_ENABLED, false)
                lifecycleScope.launch {
                    ensureConfigLoaded()
                    bootMasterEnabled = next
                    val state = ViperDispatcher.loadFullStateFromPrefs(repository)
                    stateProvider = null
                    lastUiState = state
                    applyState(state, next)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSessionMonitor()
        audioOutputDetector?.stop()
        audioOutputDetector = null
        globalEffect?.let {
            it.enabled = false
            it.release()
        }
        globalEffect = null
        releaseAllSessions()
        FileLogger.i("Service", "Service destroyed")
        super.onDestroy()
    }

    private fun startSessionMonitor() {
        stopSessionMonitor()
        val monitor =
            AudioSessionMonitor(
                context = this,
                onSessionOpen = { sessionId, pkg -> openSession(sessionId, pkg) },
                onSessionClose = { sessionId -> closeSession(sessionId) },
            )
        monitor.start()
        sessionMonitor = monitor
    }

    private fun stopSessionMonitor() {
        sessionMonitor?.stop()
        sessionMonitor = null
    }

    private fun openSession(
        sessionId: Int,
        packageName: String,
    ) {
        if (!masterEnabled) {
            FileLogger.d(
                "Service",
                "Master off: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (globalMode) {
            FileLogger.d(
                "Service",
                "Global mode: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (sessions.get(sessionId) != null) {
            FileLogger.w("Service", "Session $sessionId already open")
            return
        }

        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(sessionId, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create effect for session $sessionId ($packageName)")
            return
        }

        sessions.put(sessionId, effect)
        FileLogger.i("Service", "Opened session $sessionId for $packageName")

        lifecycleScope.launch {
            dispatchFullStateToEffect(effect)
            FileLogger.i("Service", "Applied full state to session $sessionId")
        }
    }

    private fun closeSession(sessionId: Int) {
        val effect = sessions.get(sessionId) ?: return
        effect.enabled = false
        effect.release()
        sessions.remove(sessionId)
        FileLogger.i("Service", "Closed session $sessionId")
    }

    private fun releaseAllSessions() {
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = false
            effect.release()
        }
        sessions.clear()
    }

    fun dispatchParam(
        param: Int,
        value: Int,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParam(
        param: Int,
        val1: Int,
        val2: Int,
        val3: Int,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, val1, val2, val3)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, val1, val2, val3)
        }
    }

    fun dispatchParam(
        param: Int,
        value: ByteArray,
        republishAidl: Boolean = true,
    ) {
        if (useAidlTypeUuid) {
            if (republishAidl) republishLastStateOnAidl()
            return
        }
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    private fun republishLastStateOnAidl() {
        val state = stateProvider?.invoke() ?: lastUiState ?: return
        ConfigChannel.writeFullState(state)
        if (state.ddc.enable && state.ddc.device.isNotEmpty()) {
            applyDdcDeviceAidl(state.ddc.device)
        }
        if (state.convolver.enable && state.convolver.kernelFile.isNotEmpty()) {
            applyConvolverKernelAidl(state.convolver.kernelFile)
        }
    }

    fun dispatchFullState(state: EffectState) {
        /*
         * AIDL config is global shared memory. UI changes must publish to SHM
         * even when this service currently has no tracked global/session
         * ViperEffect object. The previous implementation only reached
         * writeAidlFullState() from inside applyState() when an effect object
         * was present, so Home profiles/macros could update UI/DataStore while
         * the native DSP kept processing the old snapshot.
         */
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL direct full-state publish from controller (master=$masterEnabled)",
            )
            writeAidlFullState(state)
            applyState(
                state,
                masterEnabled,
                aidlStateAlreadyWritten = true,
            )
            return
        }

        applyState(state, masterEnabled)
    }

    fun parseVdc(file: File): Pair<List<FloatArray>, List<FloatArray>>? {
        try {
            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null
            for (line in file.readLines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 =
                            trimmed
                                .removePrefix("SR_44100:")
                                .split(",")
                                .map { it.trim().toFloat() }
                                .toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 =
                            trimmed
                                .removePrefix("SR_48000:")
                                .split(",")
                                .map { it.trim().toFloat() }
                                .toFloatArray()
                    }
                }
            }

            val a = coeffs44100
            val b = coeffs48000
            if (a == null || b == null || a.isEmpty() || a.size != b.size || a.size % 5 != 0) {
                FileLogger.w(
                    "Service",
                    "DDC coefficient parse failure: ${file.name} 44100=${a?.size} 48000=${b?.size}",
                )
                return null
            }
            val sec44 = a.toList().chunked(5).map { it.toFloatArray() }
            val sec48 = b.toList().chunked(5).map { it.toFloatArray() }
            return sec44 to sec48
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to parse DDC: ${file.name}", e)
            return null
        }
    }

    fun applyConvolverKernelAidl(
        fileName: String,
        force: Boolean = false,
    ) {
        if (fileName == lastBulkConvolverKey && !force) return
        if (fileName.isEmpty()) {
            ConfigChannel.writeBulkConvolverPath("")
            lastBulkConvolverKey = null
            return
        }
        val kernelDir = File(getExternalFilesDir(null), "Kernel")
        val src = File(kernelDir, fileName)
        if (!src.exists()) {
            FileLogger.w("Service", "Kernel src missing: $fileName")
            return
        }
        val safeName = fileName.replace("'", "")
        val stagedPath = "/data/local/tmp/v4a/kernel/$safeName"
        val staged = File(stagedPath)
        val needCopy = !(staged.exists() && staged.length() == src.length())
        if (needCopy) {
            FileLogger.d("Service", "Staging kernel '$fileName' to $stagedPath")
            RootShell.copyFile(src, stagedPath)
        } else {
            FileLogger.d("Service", "Kernel already staged at $stagedPath")
        }
        ConfigChannel.writeBulkConvolverPath(stagedPath)
        lastBulkConvolverKey = fileName
    }

    fun applyDdcDeviceAidl(
        name: String,
        force: Boolean = false,
    ) {
        if (name == lastBulkDdcKey && !force) return
        val ddcDir = File(getExternalFilesDir(null), "DDC")
        val file = File(ddcDir, "$name.vdc")
        if (!file.exists()) {
            FileLogger.w("Service", "DDC file missing: $name")
            return
        }
        val parsed = parseVdc(file) ?: return
        val sec44100 = parsed.first
        val sec48000 = parsed.second
        val perRateSize = sec44100.sumOf { it.size }
        val flat = FloatArray(perRateSize * 2)
        var off = 0
        for (sec in sec44100) {
            System.arraycopy(sec, 0, flat, off, sec.size)
            off += sec.size
        }
        for (sec in sec48000) {
            System.arraycopy(sec, 0, flat, off, sec.size)
            off += sec.size
        }
        ConfigChannel.writeBulkDdc(perRateSize, flat)
        lastBulkDdcKey = name
    }

    fun applyConvolverKernelHidl(
        fileName: String,
        effect: ViperEffect? = null,
    ) {
        val sendInts: (Int, Int, Int, Int) -> Unit =
            if (effect != null) {
                { p, a, b, c -> effect.setParameter(p, a, b, c) }
            } else {
                { p, a, b, c -> dispatchParam(p, a, b, c) }
            }
        val sendBytes: (Int, ByteArray) -> Unit =
            if (effect != null) {
                { p, v -> effect.setParameter(p, v) }
            } else {
                { p, v -> dispatchParam(p, v) }
            }
        if (fileName.isEmpty()) {
            sendInts(ViperParams.PARAM_CONVOLVER_PREPARE_BUFFER, 0, 0, 1)
            return
        }
        val src = File(File(getExternalFilesDir(null), "Kernel"), fileName)
        if (!src.exists()) {
            FileLogger.w("Service", "Kernel file missing: $fileName")
            return
        }

        try {
            val decoded = WavDecoder.decode(src.readBytes())
            val samples = decoded.samples
            val totalFloats = samples.size
            val channelCount = decoded.channels
            FileLogger.i("Service", "Kernel decoded: $fileName samples=$totalFloats ch=$channelCount")

            sendInts(ViperParams.PARAM_CONVOLVER_PREPARE_BUFFER, totalFloats, channelCount, 0)

            val rawBytes =
                ByteBuffer
                    .allocate(totalFloats * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .also { for (f in samples) it.putFloat(f) }
                    .array()
            val crc = CRC32().apply { update(rawBytes) }.value.toInt()

            val maxFloatsPerChunk = 2046
            var offset = 0
            var chunkIndex = 0
            while (offset < totalFloats) {
                val remaining = totalFloats - offset
                val floatsInChunk = minOf(remaining, maxFloatsPerChunk)
                val chunk = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                chunk.putInt(chunkIndex)
                chunk.putInt(floatsInChunk)
                chunk.put(rawBytes, offset * 4, floatsInChunk * 4)
                sendBytes(ViperParams.PARAM_CONVOLVER_SET_BUFFER, chunk.array())
                offset += floatsInChunk
                chunkIndex++
            }

            val kernelId = fileName.hashCode()
            sendInts(ViperParams.PARAM_CONVOLVER_COMMIT_BUFFER, totalFloats, crc, kernelId)
            FileLogger.i("Service", "Kernel streamed: $fileName chunks=$chunkIndex crc=0x${crc.toUInt().toString(16)}")
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to stream kernel: $fileName", e)
        }
    }

    fun applyDdcDeviceHidl(
        name: String,
        effect: ViperEffect? = null,
    ) {
        val file = File(File(getExternalFilesDir(null), "DDC"), "$name.vdc")
        if (!file.exists()) {
            FileLogger.w("Service", "DDC file missing: $name")
            return
        }
        val parsed = parseVdc(file) ?: return
        val sec44100 = parsed.first
        val sec48000 = parsed.second
        val sectionCount = sec44100.size
        val floatsPerRate = sectionCount * 5
        val naturalSize = 4 + floatsPerRate * 4 * 2
        val wireSize =
            when {
                naturalSize <= 256 -> {
                    256
                }

                naturalSize <= 1024 -> {
                    1024
                }

                else -> {
                    FileLogger.w("Service", "DDC file too large ($naturalSize bytes; max 1024)")
                    return
                }
            }
        val bytes = ByteArray(wireSize)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(floatsPerRate)
        for (s in sec44100) for (v in s) buf.putFloat(v)
        for (s in sec48000) for (v in s) buf.putFloat(v)
        if (effect == null) {
            dispatchParam(ViperParams.PARAM_DDC_COEFFICIENTS, bytes)
        } else {
            effect.setParameter(ViperParams.PARAM_DDC_COEFFICIENTS, bytes)
        }
    }

    fun getActiveEffect(): ViperEffect? {
        globalEffect?.let { if (it.isCreated) return it }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            if (effect.isCreated) return effect
        }
        return null
    }

    fun setGlobalMode(enabled: Boolean) {
        globalMode = enabled
        if (!masterEnabled) {
            applyState(EffectState(), false)
            return
        }
        if (enabled) {
            stopSessionMonitor()
            releaseAllSessions()
        } else {
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
        }
        lifecycleScope.launch {
            applyState(ViperDispatcher.loadFullStateFromPrefs(repository), true)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
