package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.entity.*
import com.example.model.*
import com.example.service.MappingRuntimeBridge
import com.example.ui.MainAppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ProfileWorkflowTest {
    private fun await(predicate: () -> Boolean) {
        val deadline=System.nanoTime()+5_000_000_000
        while(!predicate() && System.nanoTime()<deadline) Thread.sleep(10)
        assertTrue("Workflow did not reach its observable state",predicate())
    }
    private fun withViewModel(block: (MainAppViewModel,Application) -> Unit) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(LegacyProfileMigration.PREFERENCES,0).edit().clear().commit()
        app.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES,0).edit().clear().commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store=ViewModelStore()
        try {
            val vm=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainAppViewModel::class.java]
            block(vm,app)
        } finally { store.clear();Dispatchers.resetMain();MappingRuntimeBridge.disarm() }
    }
    @Test fun selectingGamesAndSavedProfilesNeverLoadsADifferentPackage() = withViewModel { vm,app ->
        val db=ControlystDatabase.getDatabase(app)
        val a=MappingConfig(id="alpha",profileName="Alpha",gamePackage="com.test.alpha",buttons=listOf(MappingNode("a",.2f,.3f)))
        val b=a.copy(id="beta",profileName="Beta",gamePackage="com.test.beta")
        runBlocking { ProfilePersistence(db).save(a);ProfilePersistence(db).save(b) }
        vm.selectGame(GameEntity(a.gamePackage,"Alpha"));vm.selectGame(GameEntity(b.gamePackage,"Beta"))
        await { vm.activeConfig.value.id==b.id }
        assertEquals(b.gamePackage,vm.selectedGame.value!!.packageName)
        vm.selectSavedProfile(runBlocking { db.configProfileDao().getProfileById(a.id)!! })
        await { vm.activeConfig.value.id==a.id }
        assertEquals(a.gamePackage,vm.activeConfig.value.gamePackage)
    }
    @Test fun missingAppLaunchReportsFailureAndCannotArmMapping() = withViewModel { vm,app ->
        vm.launchGameWithMapping(GameEntity("com.missing.game","Missing"),app)
        await { vm.snackMessage.value?.contains("Launch failed")==true }
        assertFalse(MappingRuntimeBridge.state.value.armed)
        assertTrue(MappingRuntimeBridge.state.value.error!!.contains("not installed"))
    }
    @Test fun invalidImportDoesNotClaimSaveSuccessOrAlterActiveProfile() = withViewModel { vm,_ ->
        val before=vm.activeConfig.value
        var saved=false
        vm.updateActiveConfig(MappingConfig(id="bad",profileName="Bad",gamePackage="not-a-package")) { saved=true }
        assertEquals(before,vm.activeConfig.value);assertFalse(saved)
        assertTrue(vm.snackMessage.value!!.contains("Profile rejected"))
    }    @Test fun screenshotImportReadsActualFileAndUnreadableImagesClearDetectionInput() = withViewModel { vm,app ->
        val file=java.io.File(app.cacheDir,"fixture.png")
        val bitmap=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        vm.importScreenshot(android.net.Uri.fromFile(file))
        await { vm.screenshot.value!=null }
        assertEquals(200,vm.screenshot.value!!.width)
        assertEquals(100,vm.screenshot.value!!.height)
        file.writeText("not an image")
        vm.importScreenshot(android.net.Uri.fromFile(file))
        await { vm.snackMessage.value?.contains("Screenshot import failed")==true }
        assertNull(vm.screenshot.value);assertTrue(vm.aiHudCandidates.value.isEmpty())
        file.delete();bitmap.recycle()
    }

}
