package com.nees.audio.viper

import com.nees.audio.effect.EffectState
import com.nees.audio.utils.FileLogger
import com.nees.audio.utils.RootShell
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RootShmChannel {
    private const val TAG = "RootShmChannel"

    private const val STATUS_PATH = "/data/local/tmp/v4a/shm_status.bin"
    private const val PARAMS_PATH = "/data/local/tmp/v4a/shm_params.bin"
    private const val BULK_PATH = "/data/local/tmp/v4a/shm_bulk.bin"

    private const val MAGIC = 0x534D3456
    private const val VERSION = 5

    private const val STATUS_SIZE = 256
    private const val PARAMS_SIZE = 4096
    private const val BULK_SIZE = 4096

    private const val PARAMS_SLOT_A = 16
    private const val PARAMS_SLOT_B = PARAMS_SLOT_A + ViperParamsLayout.SIZE

    private const val BULK_HEADER = 32
    private const val BULK_SEQ = 8
    private const val BULK_COMMAND = 12
    private const val BULK_DATA_SIZE = 16

    private const val BULK_DDC_BASE = 0
    private const val BULK_DDC_REGION_SIZE = 2048
    private const val BULK_CONVOLVER_BASE = 2048
    private const val BULK_CONVOLVER_REGION_SIZE = 2048

    private const val BULK_CMD_DDC = 1
    private const val BULK_CMD_CONVOLVER_PATH = 2

    @Volatile
    private var initialized = false

    fun ensureInitialized(): Boolean {
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return try {
                RootShell.exec(
                    "mkdir -p /data/local/tmp/v4a && " +
                        "for p in '$STATUS_PATH:$STATUS_SIZE' '$PARAMS_PATH:$PARAMS_SIZE' '$BULK_PATH:$BULK_SIZE'; do " +
                        "f=\${p%%:*}; n=\${p##*:}; " +
                        "[ -f \"\$f\" ] || dd if=/dev/zero of=\"\$f\" bs=\"\$n\" count=1 2>/dev/null; " +
                        "chmod 666 \"\$f\"; " +
                        "chcon u:object_r:shell_data_file:s0 \"\$f\" 2>/dev/null; " +
                        "done",
                )

                val header =
                    ByteBuffer
                        .allocate(8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(MAGIC)
                        .putInt(VERSION)
                        .array()

                rootWrite(STATUS_PATH, 0, header)
                rootWrite(PARAMS_PATH, 0, header)
                rootWrite(BULK_PATH, 0, header)

                initialized = true
                FileLogger.i(TAG, "Root SHM transport initialized")
                true
            } catch (e: Exception) {
                FileLogger.e(TAG, "Root SHM init failed", e)
                false
            }
        }
    }

    fun writeFullState(
        state: EffectState,
        nextSlot: Int,
        updateCount: Int,
    ): Boolean {
        if (!ensureInitialized()) return false
        return try {
            val payload =
                ByteBuffer
                    .allocate(ViperParamsLayout.SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN)
            ViperParamsSerializer.write(payload, 0, state)

            val slotOffset = if (nextSlot == 0) PARAMS_SLOT_A else PARAMS_SLOT_B
            rootWrite(PARAMS_PATH, slotOffset, payload.array())

            val commit =
                ByteBuffer
                    .allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(nextSlot)
                    .putInt(updateCount)
                    .array()
            rootWrite(PARAMS_PATH, 8, commit)

            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Root params write failed", e)
            false
        }
    }

    fun writeDdc(
        perRateFloats: Int,
        coeffs: FloatArray,
        seq: Int,
    ): Boolean {
        if (!ensureInitialized()) return false
        val payloadSize = 8 + coeffs.size * 4
        if (BULK_HEADER + payloadSize > BULK_DDC_REGION_SIZE) return false

        return try {
            val body =
                ByteBuffer
                    .allocate(BULK_HEADER - BULK_COMMAND + payloadSize)
                    .order(ByteOrder.LITTLE_ENDIAN)
            body.putInt(BULK_CMD_DDC)
            body.putInt(payloadSize)
            body.putInt(0)
            body.putInt(0)
            body.putInt(0)
            body.putInt(perRateFloats)
            body.putInt(coeffs.size)
            coeffs.forEach { body.putFloat(it) }

            rootWrite(BULK_PATH, BULK_DDC_BASE + BULK_COMMAND, body.array())
            rootWriteInt(BULK_PATH, BULK_DDC_BASE + BULK_SEQ, seq)
            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Root DDC write failed", e)
            false
        }
    }

    fun writeConvolverPath(
        path: String,
        seq: Int,
    ): Boolean {
        if (!ensureInitialized()) return false
        val bytes = path.toByteArray(Charsets.UTF_8)
        if (BULK_HEADER + bytes.size > BULK_CONVOLVER_REGION_SIZE) return false

        return try {
            val body =
                ByteBuffer
                    .allocate(BULK_HEADER - BULK_COMMAND + bytes.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
            body.putInt(BULK_CMD_CONVOLVER_PATH)
            body.putInt(bytes.size)
            body.putInt(0)
            body.putInt(0)
            body.putInt(0)
            body.put(bytes)

            rootWrite(BULK_PATH, BULK_CONVOLVER_BASE + BULK_COMMAND, body.array())
            rootWriteInt(BULK_PATH, BULK_CONVOLVER_BASE + BULK_SEQ, seq)
            true
        } catch (e: Exception) {
            FileLogger.e(TAG, "Root convolver write failed", e)
            false
        }
    }

    private fun rootWriteInt(
        path: String,
        offset: Int,
        value: Int,
    ) {
        val bytes =
            ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array()
        rootWrite(path, offset, bytes)
    }

    private fun rootWrite(
        path: String,
        offset: Int,
        bytes: ByteArray,
    ) {
        val tmp = File.createTempFile("nees_shm_", ".bin")
        try {
            tmp.writeBytes(bytes)
            tmp.setReadable(true, false)

            val src = tmp.absolutePath.replace("'", "")
            val dst = path.replace("'", "")
            val p =
                RootShell.exec(
                    "dd if='$src' of='$dst' bs=1 seek=$offset conv=notrunc 2>/dev/null",
                )
            if (p.exitValue() != 0) {
                val err = p.errorStream.bufferedReader().readText()
                throw RuntimeException("dd failed: $err")
            }
        } finally {
            tmp.delete()
        }
    }
}
