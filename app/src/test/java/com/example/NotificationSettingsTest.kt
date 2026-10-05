package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.ui.onboarding.notificationSettingsIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationSettingsTest {
    @Test @Config(sdk=[24]) fun oldAndroidOpensTheActualApplicationSettingsPage() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val intent=notificationSettingsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.action)
        assertEquals("package:${context.packageName}",intent.dataString)
    }
    @Test @Config(sdk=[34]) fun supportedAndroidOpensNotificationSettingsForTheExactPackage() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val intent=notificationSettingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS,intent.action)
        assertEquals(context.packageName,intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
