package com.nees.audio.utils

import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {
    private const val TAG = "RootShell"
    private const val SU_TIMEOUT_SEC = 10L

    private data class SuSpec(
        val executable: String,
        val masterMount: Boolean,
    )

    @Volatile
    private var cachedSu: SuSpec? = null

    fun exec(
        command: String,
        timeoutSec: Long = SU_TIMEOUT_SEC,
    ): Process {
        val spec = getSuSpec()
        val argv =
            if (spec.masterMount) {
                arrayOf(spec.executable, "-M", "-c", command)
            } else {
                arrayOf(spec.executable, "-c", command)
            }

        val process = Runtime.getRuntime().exec(argv)
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw RuntimeException("su command timed out after ${timeoutSec}s")
        }
        return process
    }

    fun startShell(): Process {
        val spec = getSuSpec()
        val argv =
            if (spec.masterMount) {
                arrayOf(spec.executable, "-M")
            } else {
                arrayOf(spec.executable)
            }
        return ProcessBuilder(*argv)
            .redirectErrorStream(true)
            .start()
    }

    fun getSuPath(): String = getSuSpec().executable

    fun isRootAvailable(): Boolean =
        try {
            getSuSpec()
            true
        } catch (_: Exception) {
            false
        }

    private fun getSuSpec(): SuSpec {
        cachedSu?.let { return it }
        synchronized(this) {
            cachedSu?.let { return it }
            val spec = detectSu()
            cachedSu = spec
            return spec
        }
    }

    private fun detectSu(): SuSpec {
        val candidates =
            listOf(
                "su",
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/debug_ramdisk/su",
                "/su/bin/su",
                "/data/adb/ksu/bin/su",
            )

        for (path in candidates) {
            if (testSu(path, false)) {
                FileLogger.i(TAG, "root shell: $path")
                return SuSpec(path, false)
            }
            if (testSu(path, true)) {
                FileLogger.i(TAG, "root shell: $path -M")
                return SuSpec(path, true)
            }
        }

        FileLogger.e(TAG, "No working KernelSU/Magisk-compatible su command found")
        throw RuntimeException("Root unavailable")
    }

    private fun testSu(
        path: String,
        masterMount: Boolean,
    ): Boolean =
        try {
            val argv =
                if (masterMount) {
                    arrayOf(path, "-M", "-c", "id")
                } else {
                    arrayOf(path, "-c", "id")
                }
            val process = Runtime.getRuntime().exec(argv)
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                false
            } else if (process.exitValue() != 0) {
                false
            } else {
                process.inputStream.bufferedReader().readText().contains("uid=0")
            }
        } catch (_: Exception) {
            false
        }

    fun copyFile(
        src: File,
        destPath: String,
    ) {
        val destFile = File(destPath)
        val destDir = destFile.parentFile
        val tmpPath = "$destPath.tmp"
        val tmpFile = File(tmpPath)

        try {
            if (destDir != null && destDir.canWrite()) {
                if (!destDir.exists()) destDir.mkdirs()
                src.copyTo(tmpFile, overwrite = true)
                tmpFile.renameTo(destFile)
                destFile.setReadable(true, false)
                FileLogger.i(TAG, "Direct copy OK: $destPath")
                return
            }
        } catch (_: Exception) {
            FileLogger.d(TAG, "Direct copy failed for $destPath, trying su")
        }

        val safeSrc = src.absolutePath.replace("'", "")
        val safeDest = destPath.replace("'", "")
        val safeTmp = tmpPath.replace("'", "")
        exec("cp '$safeSrc' '$safeTmp' && mv '$safeTmp' '$safeDest' && chmod 644 '$safeDest'")
        FileLogger.i(TAG, "su copy OK: $destPath")
    }
}
