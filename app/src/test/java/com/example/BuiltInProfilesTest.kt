package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BuiltInProfiles
import com.example.data.ControlystDatabase
import com.example.data.ControlystRepository
import com.example.data.ProfilePersistence
import com.example.model.MappingConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltInProfilesTest {
    @Test fun deltaForcePresetSeedsEditableFallbackWithInvertedCamera() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ControlystDatabase::class.java).build()
        try {
            BuiltInProfiles.seed(db.openHelper.writableDatabase)
            val entity = db.configProfileDao().getProfileById(BuiltInProfiles.DELTA_FORCE_PROFILE_ID)
            assertNotNull(entity)
            assertFalse(entity!!.isDefault)
            val profile = ControlystRepository.deserializeJsonToConfig(entity.jsonBlob)
            assertEquals("com.proxima.dfm", profile.gamePackage)
            assertEquals("19.5:9", profile.targetAspectRatio)
            assertTrue(profile.camera.invertY)
            assertEquals(.02f, profile.joystick.innerDeadzone, 0f)
            assertEquals(.90118736f, profile.buttons.first { it.boundKey == "A" }.xNorm, .000001f)
            assertEquals(.19823368f, profile.buttons.first { it.boundKey == "LS" }.xNorm, .000001f)
            assertEquals(.6845802f, profile.buttons.first { it.boundKey == "RS" }.xNorm, .000001f)
        } finally { db.close() }
    }

    @Test fun userSavedDeltaForceProfileOutranksFactoryPresetAndIsNotOverwritten() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ControlystDatabase::class.java).build()
        try {
            BuiltInProfiles.seed(db.openHelper.writableDatabase)
            val user = MappingConfig(
                id = "user-delta-force",
                profileName = "My Delta Force",
                gamePackage = BuiltInProfiles.DELTA_FORCE_PACKAGE,
                gameTitle = "Delta Force",
                lastUpdated = 1234L
            )
            ProfilePersistence(db).save(user)
            BuiltInProfiles.seed(db.openHelper.writableDatabase)
            val selected = db.configProfileDao().getDefaultForGame(BuiltInProfiles.DELTA_FORCE_PACKAGE)
            assertEquals(user.id, selected?.id)
            assertEquals(user.profileName, selected?.profileName)
            assertNotNull(db.configProfileDao().getProfileById(BuiltInProfiles.DELTA_FORCE_PROFILE_ID))
        } finally { db.close() }
    }
}
