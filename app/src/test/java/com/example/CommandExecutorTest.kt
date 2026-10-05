package com.example

import com.example.injector.ProcessShellExecutor
import org.junit.Assert.*
import org.junit.Test

/** Executes real host processes in CI; this does not verify an Android su provider. */
class CommandExecutorTest {
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
}
