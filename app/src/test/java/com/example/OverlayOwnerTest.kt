package com.example
import androidx.lifecycle.Lifecycle
import com.example.service.OverlayOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class OverlayOwnerTest {
    @Test fun composeWindowHasRestoredSavedStateAndACompleteLifecycle() {
        val owner=OverlayOwner()
        assertTrue(owner.savedStateRegistry.isRestored)
        assertEquals(Lifecycle.State.RESUMED,owner.lifecycle.currentState)
        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED,owner.lifecycle.currentState)
    }
}
