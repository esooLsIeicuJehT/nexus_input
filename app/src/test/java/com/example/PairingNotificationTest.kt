package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.ShizukuPairingManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** Android notification observations in Robolectric, not device wireless pairing verification. */
@RunWith(RobolectricTestRunner::class)
class PairingNotificationTest {
    @Test @Config(sdk=[34]) fun deniedNotificationsNeverClaimAnActiveHelper() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        ShizukuPairingManager.showPairingNotification(context,39123)
        assertFalse(ShizukuPairingManager.pairingState.value.isHelperNotificationActive)
        assertTrue(ShizukuPairingManager.pairingState.value.statusMessage.contains("notifications are disabled"))
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test @Config(sdk=[24]) fun unsupportedAndroidReportsAnExplicitPairingFailure() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        ShizukuPairingManager.showPairingNotification(context,39123)
        assertFalse(ShizukuPairingManager.pairingState.value.isHelperNotificationActive)
        assertTrue(ShizukuPairingManager.pairingState.value.statusMessage.contains("Android 11"))
    }
    @Test @Config(sdk=[34]) fun noDefaultPortIsGuessedAndActivityRequiresAndroidNotificationObservation() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        ShizukuPairingManager.showPairingNotification(context)
        assertFalse(ShizukuPairingManager.pairingState.value.isHelperNotificationActive)
        assertTrue(ShizukuPairingManager.pairingState.value.statusMessage.contains("actual wireless pairing port"))
        val manager=context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        ShizukuPairingManager.showPairingNotification(context,39123)
        try {
            val deadline=System.nanoTime()+2_000_000_000
            while(!ShizukuPairingManager.pairingState.value.isHelperNotificationActive && System.nanoTime()<deadline) Thread.sleep(5)
            assertTrue(manager.activeNotifications.any { it.id==ShizukuPairingManager.NOTIFICATION_ID })
            assertTrue(ShizukuPairingManager.pairingState.value.isHelperNotificationActive)
            assertFalse(ShizukuPairingManager.pairingState.value.isPairingSuccessful)
        } finally { ShizukuPairingManager.dismissHelper(context) }
    }
}
