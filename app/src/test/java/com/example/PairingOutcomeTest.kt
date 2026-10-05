package com.example

import com.example.service.ShizukuPairingManager
import org.junit.Assert.*
import org.junit.Test

class PairingOutcomeTest {
    @Test fun noSuccessOnFailureOrUnconfirmedExit() {
        assertFalse(ShizukuPairingManager.evaluateAdbPair(1, "Successfully paired to localhost:1234").success)
        assertFalse(ShizukuPairingManager.evaluateAdbPair(0, "error: connection refused").success)
        assertFalse(ShizukuPairingManager.evaluateAdbPair(0, "").success)
    }
    @Test fun confirmedPairingIsNotAppAuthorization() {
        val outcome = ShizukuPairingManager.evaluateAdbPair(0, "Successfully paired to localhost:1234 [guid=abc]")
        assertTrue(outcome.success)
        assertTrue(outcome.message.contains("does not authorize"))
    }
}
