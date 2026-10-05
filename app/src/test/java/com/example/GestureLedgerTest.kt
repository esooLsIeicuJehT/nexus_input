package com.example

import com.example.input.GestureLedger
import org.junit.Assert.*
import org.junit.Test

class GestureLedgerTest {
    @Test fun noCompletionIsInventedWhileARequestIsOutstanding() {
        val ledger=GestureLedger();val first=ledger.begin();val second=ledger.begin()
        assertEquals(2,ledger.count);assertFalse(ledger.awaitIdle(5))
        assertTrue(ledger.complete(first));assertFalse(ledger.complete(first));assertFalse(ledger.awaitIdle(0))
        assertTrue(ledger.complete(second));assertEquals(0,ledger.count);assertTrue(ledger.awaitIdle(0))
    }
    @Test fun waitingCanObserveARealCompletionFromAnotherThread() {
        val ledger=GestureLedger();val token=ledger.begin()
        val completion=Thread { Thread.sleep(20);ledger.complete(token) }.apply { start() }
        assertTrue(ledger.awaitIdle(1000));completion.join();assertEquals(0,ledger.count)
    }
}
