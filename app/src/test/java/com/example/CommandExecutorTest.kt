package com.example

import com.example.injector.ProcessShellExecutor
import org.junit.Assert.*
import org.junit.Test

/** Executes real host processes in CI; this does not verify an Android su provider. */
class CommandExecutorTest {
    @Test fun realStdinUsesTheSameBoundedExitAndOutputFlow() {
        val result=ProcessShellExecutor().runWithInput(listOf("/bin/sh","-c","cat"),"test-only stdin\n",1000)
        assertTrue(result.succeeded)
        assertEquals("test-only stdin\n",result.stdout)
        assertEquals("",result.stderr)
        assertTrue(runCatching { ProcessShellExecutor().runWithInput(listOf("/bin/sh","-c","cat"),"x".repeat(4097)) }.isFailure)
        assertTrue(runCatching { ProcessShellExecutor().run(listOf("/bin/sh","-c","exit 0"),0) }.isFailure)
    }
    @Test fun actualExitCodeAndBothStreamsArePreserved() {
        val result=ProcessShellExecutor().run(listOf("/bin/sh","-c","printf out; printf denied >&2; exit 7"))
        assertEquals(7,result.exitCode);assertEquals("out",result.stdout);assertEquals("denied",result.stderr)
        assertFalse(result.succeeded);assertFalse(result.timedOut)
    }
    @Test fun oversizedOutputAndTimeoutCannotBecomeSuccess() {
        val result=ProcessShellExecutor().run(listOf("/bin/sh","-c","head -c 270000 /dev/zero"))
        assertTrue(result.outputTruncated);assertEquals(262144,result.stdout.length);assertFalse(result.succeeded)
        val timeout=ProcessShellExecutor().run(listOf("/bin/sh","-c","exec sleep 3"),30)
        assertTrue(timeout.timedOut);assertNull(timeout.exitCode);assertFalse(timeout.succeeded)
    }
    @Test fun sharedPairingWaitObservesActualCompletionAndRejectsInvalidTimeout() {
        val child=ProcessBuilder("/bin/sh","-c","exit 7").start()
        assertTrue(com.example.injector.ProcessWait.await(child,1000));assertEquals(7,child.exitValue())
        assertTrue(runCatching { com.example.injector.ProcessWait.await(child,0) }.isFailure)
    }

}
