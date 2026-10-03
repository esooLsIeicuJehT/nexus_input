package com.example.injector

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class ShellResult<out T> {
    data class Success<T>(val data: T, val exitCode: Int = 0) : ShellResult<T>()
    data class Failure(val errorCode: ErrorCode, val message: String, val exception: Throwable? = null) : ShellResult<Nothing>()

    enum class ErrorCode {
        METHOD_UNAVAILABLE,
        PERMISSION_DENIED,
        EXECUTION_TIMEOUT,
        PROCESS_FAILED,
        INVALID_ARGUMENTS,
        UNKNOWN_EXCEPTION
    }
}

object RootController {
    private const val TAG = "RootController"

    /**
     * Executes a shell command via Superuser (su) with explicit success/failure tracking.
     */
    fun executeSuCommand(command: String, timeoutMs: Long = 5000L): ShellResult<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            val startTime = System.currentTimeMillis()
            while (process.isAlive) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    process.destroy()
                    return ShellResult.Failure(
                        ShellResult.ErrorCode.EXECUTION_TIMEOUT,
                        "Command execution timed out after ${timeoutMs}ms: $command"
                    )
                }
                Thread.sleep(10)
            }

            while (outputReader.ready()) {
                output.append(outputReader.readLine()).append("\n")
            }
            while (errorReader.ready()) {
                error.append(errorReader.readLine()).append("\n")
            }

            val exitCode = process.exitValue()
            if (exitCode == 0) {
                ShellResult.Success(output.toString().trim(), exitCode)
            } else {
                ShellResult.Failure(
                    ShellResult.ErrorCode.PROCESS_FAILED,
                    "su process failed with exit code $exitCode. Error: ${error.toString().trim()}"
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception executing su: ${e.message}", e)
            ShellResult.Failure(ShellResult.ErrorCode.PERMISSION_DENIED, "Root permission denied or su binary blocked.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing su: ${e.message}", e)
            ShellResult.Failure(ShellResult.ErrorCode.UNKNOWN_EXCEPTION, "Failed to execute su command: ${e.localizedMessage}", e)
        }
    }

    /**
     * Executes a command via Shizuku rish / remote process bridge with explicit success/failure tracking.
     */
    fun executeShizukuCommand(command: String): ShellResult<String> {
        return try {
            if (!Shizuku.pingBinder()) {
                return ShellResult.Failure(
                    ShellResult.ErrorCode.METHOD_UNAVAILABLE,
                    "Shizuku binder is not alive or service is not running."
                )
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return ShellResult.Failure(
                    ShellResult.ErrorCode.PERMISSION_DENIED,
                    "Shizuku permission not granted by user."
                )
            }

            val process = Runtime.getRuntime().exec(arrayOf("rish", "-c", command))
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val exitCode = process.waitFor()
            val output = outputReader.readText().trim()
            val error = errorReader.readText().trim()

            if (exitCode == 0) {
                ShellResult.Success(output, exitCode)
            } else {
                ShellResult.Failure(
                    ShellResult.ErrorCode.PROCESS_FAILED,
                    "Shizuku rish command exited with code $exitCode. Error: $error"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing Shizuku rish: ${e.message}", e)
            ShellResult.Failure(ShellResult.ErrorCode.UNKNOWN_EXCEPTION, "Shizuku execution error: ${e.localizedMessage}", e)
        }
    }

    /**
     * Verifies if root (su) is available and functional.
     */
    fun verifyRootAvailable(): ShellResult<Boolean> {
        val result = executeSuCommand("id")
        return when (result) {
            is ShellResult.Success -> {
                if (result.data.contains("uid=0")) {
                    ShellResult.Success(true, result.exitCode)
                } else {
                    ShellResult.Failure(ShellResult.ErrorCode.PROCESS_FAILED, "su executed but did not return root uid=0. Output: ${result.data}")
                }
            }
            is ShellResult.Failure -> result
        }
    }

    /**
     * Verifies if Shizuku is available and permitted.
     */
    fun verifyShizukuAvailable(): ShellResult<Boolean> {
        return try {
            if (!Shizuku.pingBinder()) {
                return ShellResult.Failure(ShellResult.ErrorCode.METHOD_UNAVAILABLE, "Shizuku binder ping failed.")
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return ShellResult.Failure(ShellResult.ErrorCode.PERMISSION_DENIED, "Shizuku permission DENIED.")
            }
            ShellResult.Success(true, 0)
        } catch (e: Exception) {
            ShellResult.Failure(ShellResult.ErrorCode.UNKNOWN_EXCEPTION, "Shizuku check error: ${e.message}", e)
        }
    }
}
