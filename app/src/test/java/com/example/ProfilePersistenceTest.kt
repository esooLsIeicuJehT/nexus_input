package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.LocalBackupManager
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ProfilePersistenceTest {
    @Test fun persistenceValidatesExactTargetsAndDoesNotDestroyExistingRowsOnFailure() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ControlystDatabase::class.java).build()
        val store=ProfilePersistence(db)
        val first=MappingConfig(id="first",profileName="First",gamePackage="com.test.game",buttons=listOf(MappingNode("a",.2f,.3f)))
        store.save(first);store.save(first.copy(id="second",profileName="Second"))
        assertFalse(db.configProfileDao().getProfileById("first")!!.isDefault)
        assertTrue(db.configProfileDao().getProfileById("second")!!.isDefault)
        assertEquals("second",db.mapperStateDao().get()!!.activeProfileId)
        assertTrue(runCatching { store.save(first.copy(buttons=listOf(MappingNode("a",Float.NaN,.3f)))) }.isFailure)
        assertEquals(first,store.load("first","com.test.game"))
        assertTrue(runCatching { store.load("first","com.wrong.game") }.isFailure)
        assertTrue(runCatching { store.load("missing","com.test.game") }.isFailure)
        db.close()
    }
    @Test fun exportedBackupPreservesFullCanonicalProfile() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val profile=MappingConfig(id="backup",profileName="Backup",gamePackage="com.test.game",
            preferredBackend=PrivilegeMethod.SHIZUKU,controllerProfileId="device",
            crosshair=CrosshairConfig(opacity=.3f,outlineEnabled=false,outlineThicknessDp=4f),
            buttons=listOf(MappingNode("held",.4f,.5f,boundKey="RT",inputKeyCode=105,inputScanCode=0,touchSlot=11,invertY=true)))
        val zip=LocalBackupManager.exportFullBackupZip(context,listOf(profile))
        ZipFile(zip).use { archive ->
            val json=archive.getInputStream(archive.getEntry("profiles/backup.json")).bufferedReader().use { it.readText() }
            assertEquals(profile,ControlystRepository.deserializeJsonToConfig(json))
        }
        zip.delete();Unit
    }
}
