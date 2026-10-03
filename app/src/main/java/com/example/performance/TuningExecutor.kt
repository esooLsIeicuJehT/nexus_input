package com.example.performance

import android.util.Log
import com.example.model.TuningVerificationResult
import java.io.File

object TuningExecutor {
    private const val TAG = "TuningExecutor"

    private val stockSnapshot = mutableMapOf<String, String>()

    /**
     * Reads a sysfs / procfs file safely.
     */
    fun readSysfs(path: String): String? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            return null
        }
        return try {
            file.readText().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading $path: ${e.message}")
            null
        }
    }

    /**
     * Applies a tuning value to a sysfs path with strict verification read-back:
     * 1. Checks file exists
     * 2. Checks readable & writable
     * 3. Reads current value and preserves original in stock snapshot
     * 4. Applies requested value via root or direct IO
     * 5. Reads back the resulting value
     * 6. Confirms if it actually changed
     */
    fun writeAndVerifySysfs(
        path: String,
        requestedValue: String,
        parameterName: String
    ): TuningVerificationResult {
        val file = File(path)
        if (!file.exists()) {
            return TuningVerificationResult(
                parameterName = parameterName,
                requestedValue = requestedValue,
                readBackValue = "None",
                isSuccess = false,
                failureReason = "Unsupported: interface does not exist ($path)"
            )
        }

        // Cache stock value if not already captured
        val currentValue = readSysfs(path)
        if (currentValue != null && !stockSnapshot.containsKey(path)) {
            stockSnapshot[path] = currentValue
        }

        // Attempt write via root shell or direct write
        val writeSuccess = executeWrite(path, requestedValue)
        if (!writeSuccess) {
            return TuningVerificationResult(
                parameterName = parameterName,
                requestedValue = requestedValue,
                readBackValue = currentValue ?: "Unknown",
                isSuccess = false,
                failureReason = "Permission denied or rejected by kernel write lock"
            )
        }

        // Read back result
        val readBack = readSysfs(path)
        val isVerified = readBack != null && (readBack == requestedValue || readBack.contains(requestedValue))

        return if (isVerified) {
            TuningVerificationResult(
                parameterName = parameterName,
                requestedValue = requestedValue,
                readBackValue = readBack ?: requestedValue,
                isSuccess = true
            )
        } else {
            TuningVerificationResult(
                parameterName = parameterName,
                requestedValue = requestedValue,
                readBackValue = readBack ?: "Null",
                isSuccess = false,
                failureReason = "Vendor controlled or kernel driver clamped value to $readBack"
            )
        }
    }

    private fun executeWrite(path: String, value: String): Boolean {
        return try {
            // First try direct Java write if permissive/accessible
            val file = File(path)
            if (file.canWrite()) {
                file.writeText(value)
                return true
            }

            // Fallback to su shell invocation
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo '$value' > $path")
            )
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing $value to $path: ${e.message}")
            false
        }
    }

    fun restoreStockSnapshot(): Boolean {
        var allRestored = true
        for ((path, originalVal) in stockSnapshot) {
            val result = writeAndVerifySysfs(path, originalVal, "Restore: $path")
            if (!result.isSuccess) {
                allRestored = false
            }
        }
        return allRestored
    }

    fun getStockSnapshot(): Map<String, String> = stockSnapshot.toMap()
}
