package com.example.injector

import java.io.BufferedReader
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

interface ShellExecutor {
    fun run(argv: List<String>, timeoutMillis: Long = 3_000): CommandResult
}

/**
 * Process runner carried over from the device-verified 0.6.2 hardening source.
 * stdout/stderr are drained concurrently so probes do not deadlock on filled pipes.
 */
class ProcessShellExecutor : ShellExecutor {
    override fun run(argv: List<String>, timeoutMillis: Long): CommandResult {
        require(argv.isNotEmpty())
        return try {
            val process = ProcessBuilder(argv).start()
            val outThread = StreamCollector(process.inputStream.bufferedReader())
            val errThread = StreamCollector(process.errorStream.bufferedReader())
            outThread.start()
            errThread.start()

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) process.destroyForcibly()

            outThread.join(500)
            errThread.join(500)

            CommandResult(
                exitCode = if (finished) process.exitValue() else null,
                stdout = outThread.text,
                stderr = errThread.text,
                timedOut = !finished
            )
        } catch (t: Throwable) {
            CommandResult(
                exitCode = null,
                stdout = "",
                stderr = t.message.orEmpty(),
                timedOut = false
            )
        }
    }

    private class StreamCollector(private val reader: BufferedReader) : Thread() {
        @Volatile
        var text: String = ""

        override fun run() {
            text = try {
                reader.use { it.readText() }
            } catch (_: Throwable) {
                ""
            }
        }
    }
}
