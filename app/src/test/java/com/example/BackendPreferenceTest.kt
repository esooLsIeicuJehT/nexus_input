package com.example

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControlystDatabase
import com.example.data.ControlystRepository
import com.example.data.LegacyProfileMigration
import com.example.data.ProfilePersistence
import com.example.injector.PrivilegeAvailabilityState
import com.example.injector.PrivilegeBackendSelector
import com.example.injector.PrivilegeProbeResult
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.PrivilegeMethod
import com.example.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Availability is an explicit fixture; preference persistence uses real Room/SQLite. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackendPreferenceTest {
    private val original = MappingConfig(
        id = "backend-preference", profileName = "Saved Accessibility", gamePackage = "com.test.game",
        preferredBackend = PrivilegeMethod.ACCESSIBILITY,
        buttons = listOf(MappingNode("a", .2f, .3f))
    )

    private fun withViewModel(block: (MainAppViewModel, ControlystDatabase, TestCoroutineScheduler) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(LegacyProfileMigration.PREFERENCES, 0).edit().clear().commit()
        app.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES, 0).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(app, ControlystDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking { ProfilePersistence(db).save(original) }
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val store = ViewModelStore()
        try {
            val vm = MainAppViewModel(app, db)
            store.put("backend-preference-test", vm)
            await(scheduler) { vm.activeConfig.value.id == original.id && vm.privilegeResults.value.isNotEmpty() }
            block(vm, db, scheduler)
        } finally {
            store.clear()
            scheduler.runCurrent()
            db.close()
            Dispatchers.resetMain()
        }
    }

    private fun await(scheduler: TestCoroutineScheduler, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            scheduler.runCurrent()
            if (predicate()) return
            Thread.sleep(10)
        }
        fail("Backend preference did not reach the expected observable state")
    }

    private fun saved(db: ControlystDatabase): MappingConfig = runBlocking {
        ControlystRepository.deserializeJsonToConfig(db.configProfileDao().getProfileById(original.id)!!.jsonBlob)
    }

    @Suppress("UNCHECKED_CAST")
    private fun availableShizukuFixture(vm: MainAppViewModel) {
        (vm.javaClass.getDeclaredField("_activePrivilegeMethod").apply { isAccessible = true }.get(vm)
            as MutableStateFlow<PrivilegeMethod>).value = PrivilegeMethod.SHIZUKU
        (vm.javaClass.getDeclaredField("_privilegeResults").apply { isAccessible = true }.get(vm)
            as MutableStateFlow<List<PrivilegeProbeResult>>).value = listOf(
            PrivilegeProbeResult(PrivilegeMethod.SHIZUKU, PrivilegeAvailabilityState.AVAILABLE, "Explicit availability fixture")
        )
    }

    private fun rejectShizukuWrites(db: ControlystDatabase) {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_shizuku_test_fixture BEFORE INSERT ON config_profiles
            WHEN NEW.jsonBlob LIKE '%"preferredBackend":"SHIZUKU"%'
            BEGIN SELECT RAISE(ABORT, 'Test fixture rejects the backend write'); END
        """.trimIndent())
    }

    @Test fun loadedForcedPreferenceIsIndependentOfTheGloballyAvailableBackend() = withViewModel { vm, db, _ ->
        availableShizukuFixture(vm)
        assertEquals(PrivilegeMethod.SHIZUKU, vm.activePrivilegeMethod.value)
        assertEquals(PrivilegeMethod.ACCESSIBILITY, vm.activeConfig.value.preferredBackend)
        assertEquals(PrivilegeMethod.ACCESSIBILITY, saved(db).preferredBackend)
        assertEquals(listOf(PrivilegeMethod.ACCESSIBILITY), PrivilegeBackendSelector.order(vm.activeConfig.value.preferredBackend))
    }

    @Test fun explicitlySelectingTheAvailableShizukuPersistsTheActualRequestedPreference() = withViewModel { vm, db, scheduler ->
        availableShizukuFixture(vm)
        vm.overridePrivilegeMethod(PrivilegeMethod.SHIZUKU)
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile backend saved:") == true }
        assertEquals(PrivilegeMethod.SHIZUKU, saved(db).preferredBackend)
        assertEquals(PrivilegeMethod.SHIZUKU, vm.activeConfig.value.preferredBackend)
        assertEquals(listOf(PrivilegeMethod.SHIZUKU), PrivilegeBackendSelector.order(vm.activeConfig.value.preferredBackend))
    }

    @Test fun automaticExplicitlyClearsTheSavedForcedPreference() = withViewModel { vm, db, scheduler ->
        vm.overridePrivilegeMethod(null)
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile backend saved: Automatic") == true }
        assertNull(vm.activeConfig.value.preferredBackend)
        assertNull(saved(db).preferredBackend)
        assertTrue(PrivilegeBackendSelector.order(vm.activeConfig.value.preferredBackend).contains(PrivilegeMethod.SHIZUKU))
    }

    @Test fun rejectedPersistenceReportsFailureAndRestoresThePreviousPreference() = withViewModel { vm, db, scheduler ->
        rejectShizukuWrites(db)
        vm.overridePrivilegeMethod(PrivilegeMethod.SHIZUKU)
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile save failed:") == true }
        assertEquals(PrivilegeMethod.ACCESSIBILITY, vm.activeConfig.value.preferredBackend)
        assertEquals(original, saved(db))
        assertFalse(vm.snackMessage.value!!.contains("Profile backend saved:"))
    }

    @Test fun failedSaveCannotOverwriteANewerEdit() = withViewModel { vm, db, scheduler ->
        rejectShizukuWrites(db)
        vm.overridePrivilegeMethod(PrivilegeMethod.SHIZUKU)
        val newer = original.copy(profileName = "Newer edit retains Accessibility")
        var newerSaved = false
        vm.updateActiveConfig(newer) { newerSaved = true }
        await(scheduler) { newerSaved }
        assertEquals(newer, vm.activeConfig.value)
        assertEquals(newer, saved(db))
    }
}
