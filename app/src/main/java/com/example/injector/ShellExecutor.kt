package com.example.injector

import java.io.BufferedReader
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val outputTruncated: Boolean = false,
    val streamError: String? = null
) {
    val succeeded: Boolean get() = !timedOut && !outputTruncated && streamError == null && exitCode == 0
}

interface ShellExecutor {
    fun run(argv: List<String>, timeoutMillis: Long = 3_000): CommandResult
}

/**
 * Process runner carried over from the device-verified 0.6.2 hardening source.
 * stdout/stderr are drained concurrently so probes do not deadlock on filled pipes.
 */
class ProcessShellExecutor : ShellExecutor {
    override fun run(argv: List<String>, timeoutMillis: Long): CommandResult = execute(argv,timeoutMillis,null)

    /** Short stdin payloads, such as pairing codes, are never placed in argv or logged. */
    fun runWithInput(argv: List<String>, input: String, timeoutMillis: Long = 3_000): CommandResult {
        require(input.toByteArray(Charsets.UTF_8).size<=4096) { "Command stdin exceeds the bounded payload limit" }
        return execute(argv,timeoutMillis,input)
    }

    private fun execute(argv: List<String>, timeoutMillis: Long, input: String?): CommandResult {
        require(argv.isNotEmpty())
        require(timeoutMillis>0) { "Process timeout must be positive" }
        var ownedProcess: Process?=null
        return try {
            val process = ProcessBuilder(argv).start()
            ownedProcess=process
            val outThread = StreamCollector(process.inputStream.bufferedReader())
            val errThread = StreamCollector(process.errorStream.bufferedReader())
            outThread.start()
            errThread.start()

            process.outputStream.bufferedWriter().use { writer -> if(input!=null) writer.write(input) }

            val finished=ProcessWait.await(process,timeoutMillis)
            if (!finished) process.destroy()

            outThread.join(500)
            errThread.join(500)

            CommandResult(
                exitCode = if (finished) process.exitValue() else null,
                stdout = outThread.text,
                stderr = errThread.text,
                timedOut = !finished,
                outputTruncated = outThread.truncated || errThread.truncated,
                streamError = listOfNotNull(outThread.error,errThread.error,
                    if(outThread.isAlive || errThread.isAlive) "Command output streams did not finish" else null).takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
        } catch (t: Throwable) {
            if(t is InterruptedException) Thread.currentThread().interrupt()
            CommandResult(
                exitCode = null,
                stdout = "",
                stderr = "${t.javaClass.simpleName}: ${t.message}",
                timedOut = false
            )
        } finally { ownedProcess?.destroy() }
    }

    private class StreamCollector(private val reader: BufferedReader) : Thread() {
        @Volatile var text: String = ""
        @Volatile var truncated = false
        @Volatile var error: String? = null
        init { isDaemon=true }
        override fun run() {
            val output=StringBuilder()
            try {
                reader.use { source ->
                    val buffer=CharArray(4096)
                    while(true) {
                        val count=source.read(buffer)
                        if(count<0) break
                        val available=(262144-output.length).coerceAtLeast(0)
                        output.append(buffer,0,minOf(count,available))
                        if(count>available) truncated=true
                    }
                }
            } catch(failure:Throwable) { error="Command output read failed: ${failure.javaClass.simpleName}: ${failure.message}" }
            finally { text=output.toString() }
        }
    }
}

/** API-24-compatible bounded process wait, shared by diagnostics and actual ADB pairing. */
object ProcessWait {
    fun await(process: Process,timeoutMillis: Long): Boolean {
        require(timeoutMillis>0) { "Process timeout must be positive" }
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while(System.nanoTime()<deadline) {
            try { process.exitValue();return true } catch(_:IllegalThreadStateException) { Thread.sleep(10) }
        }
        return false
    }
}
