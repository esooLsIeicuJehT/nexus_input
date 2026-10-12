package com.example

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControlystDatabase
import com.example.data.ControlystRepository
import com.example.data.LegacyProfileMigration
import com.example.data.ProfilePersistence
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.MapperStateEntity
import com.example.model.*
import com.example.service.MappingRuntimeBridge
import com.example.service.PanicKillSwitch
import com.example.ui.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Room/SQLite transactions and image files; controller hardware is not simulated here. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileActionsTest {
    private val original = MappingConfig(
        id = "profile-actions-source", profileName = "Source", gamePackage = "com.test.game",
        gameTitle = "Test game", controllerType = ControllerType.PLAYSTATION,
        preferredBackend = PrivilegeMethod.SHIZUKU, controllerProfileId = "saved-controller",
        targetAspectRatio = "16:9", joystick = JoystickSettings(innerDeadzone = .2f),
        camera = CameraSettings(invertY = true, horizontalSensitivity = 1.7f),
        crosshair = CrosshairConfig(opacity = .4f, offsetX = 12f), tags = listOf("custom"),
        buttons = listOf(
            MappingNode("vendor-button", .2f, .3f, boundKey = "CAPTURE", inputKeyCode = 0,
                inputScanCode = 705, touchSlot = 12),
            MappingNode("left-stick", .4f, .6f, type = NodeType.JOYSTICK_ZONE, boundKey = "LS",
                axisX = 0, axisY = 1, touchSlot = 13)
        ),
        author = "Imported author", isOfficialVerified = true, downloadCount = 17, rating = 4.2f,
        lastUpdated = 1000L
    )

    private fun withViewModel(block: (MainAppViewModel, ControlystDatabase, TestCoroutineScheduler, Application) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(LegacyProfileMigration.PREFERENCES, 0).edit().clear().commit()
        app.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES, 0).edit().clear().commit()
        MappingRuntimeBridge.disarm()
        PanicKillSwitch.resetPanic()
        val db = Room.inMemoryDatabaseBuilder(app, ControlystDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking { ProfilePersistence(db).save(original) }
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val store = ViewModelStore()
        try {
            val vm = MainAppViewModel(app, db)
            store.put("profile-actions-test", vm)
            await(scheduler) { vm.activeConfig.value.id == original.id && vm.privilegeResults.value.isNotEmpty() }
            block(vm, db, scheduler, app)
        } finally {
            store.clear()
            scheduler.runCurrent()
            db.close()
            MappingRuntimeBridge.disarm()
            PanicKillSwitch.resetPanic()
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
        fail("Profile action did not reach its observable state")
    }

    private fun entity(db: ControlystDatabase, id: String = original.id): ConfigProfileEntity = runBlocking {
        db.configProfileDao().getProfileById(id)!!
    }

    private fun state(db: ControlystDatabase): MapperStateEntity = runBlocking { db.mapperStateDao().get()!! }

    private fun insertOther(db: ControlystDatabase): MappingConfig {
        val other = original.copy(id = "other-profile", profileName = "Other", lastUpdated = 2000L)
        runBlocking { db.configProfileDao().insertProfile(ConfigProfileEntity(
            other.id, other.gamePackage, other.profileName, ControlystRepository.serializeConfigToJson(other)
        )) }
        return other
    }

    private fun rejectInserts(db: ControlystDatabase) {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_profile_action_insert BEFORE INSERT ON config_profiles
            BEGIN SELECT RAISE(ABORT, 'Profile action fixture rejects insert'); END
        """.trimIndent())
        val rejected = assertThrows(Exception::class.java) {
            runBlocking { ProfilePersistence(db).save(original.copy(profileName = "Rejected fixture preflight")) }
        }
        assertTrue(rejected.message.orEmpty().contains("Profile action fixture rejects insert"))
        assertEquals(original.profileName, entity(db).profileName)
    }

    @Test fun cloneCopiesActualSavedBindingsAndSettingsWithoutCopyingRemoteClaimsOrSelection() = withViewModel { vm, db, scheduler, _ ->
        val before = entity(db)
        val beforeState = state(db)
        var completed = false
        // The caller's stale JSON is deliberately wrong. The actual source is read inside Room.
        vm.cloneSavedProfile(before.copy(jsonBlob = "not current JSON"), "  My local copy  ") { completed = true }
        await(scheduler) { completed }
        val copiedEntity = runBlocking { db.configProfileDao().getAllProfiles().first().single { it.id != original.id } }
        val copied = ControlystRepository.deserializeJsonToConfig(copiedEntity.jsonBlob)
        assertNotEquals(original.id, copied.id)
        assertEquals("My local copy", copied.profileName)
        assertEquals(original.copy(id = copied.id, profileName = copied.profileName, author = "Local user",
            isOfficialVerified = false, downloadCount = 0, rating = 0f, lastUpdated = copied.lastUpdated), copied)
        assertFalse(copiedEntity.isDefault)
        assertFalse(copiedEntity.isOfficialVerified)
        assertEquals(0, copiedEntity.downloads)
        assertEquals(0f, copiedEntity.rating, 0f)
        assertEquals(before, entity(db))
        assertEquals(beforeState, state(db))
        assertEquals(original, vm.activeConfig.value)
    }

    @Test fun cloneRejectsBlankNamesMissingSourcesAndMismatchedPackagesWithoutAnAcknowledgement() = withViewModel { vm, db, scheduler, _ ->
        var completed = false
        vm.cloneSavedProfile(entity(db), " ") { completed = true }
        assertTrue(vm.snackMessage.value!!.contains("name is required"))
        vm.cloneSavedProfile(entity(db).copy(id = "missing"), "Copy") { completed = true }
        await(scheduler) { vm.snackMessage.value?.contains("not saved") == true }
        vm.cloneSavedProfile(entity(db).copy(gamePackage = "com.wrong.game"), "Copy") { completed = true }
        await(scheduler) { vm.snackMessage.value?.contains("different game") == true }
        assertFalse(completed)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, state(db).activeProfileId)
    }

    @Test fun failedCloneInsertReportsFailureAndLeavesTheSourceAndStateUntouched() = withViewModel { vm, db, scheduler, _ ->
        val before = entity(db)
        val beforeState = state(db)
        rejectInserts(db)
        var completed = false
        vm.cloneSavedProfile(before, "Failed copy") { completed = true }
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile clone failed:") == true }
        assertTrue(vm.snackMessage.value!!.contains("Profile action fixture rejects insert"))
        assertFalse(completed)
        assertEquals(before, entity(db))
        assertEquals(beforeState, state(db))
        assertEquals(original, vm.activeConfig.value)
    }

    @Test fun deletingTheSelectedProfileClearsItsStateWithoutSelectingTheRemainingProfile() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        runBlocking { db.mapperStateDao().save(state(db).copy(mappingEnabled = true)) }
        var completed = false
        vm.deleteSavedProfile(entity(db)) { completed = true }
        await(scheduler) { completed }
        assertNull(runBlocking { db.configProfileDao().getProfileById(original.id) })
        assertNotNull(runBlocking { db.configProfileDao().getProfileById(other.id) })
        assertNull(state(db).activeProfileId)
        assertFalse(state(db).mappingEnabled)
        assertEquals("", vm.activeConfig.value.gamePackage)
        assertNull(vm.selectedGame.value)
        assertTrue(vm.snackMessage.value!!.contains("No replacement was selected"))
    }

    @Test fun deletingAnUnselectedProfilePreservesTheSelectedProfileAndDefault() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        val before = entity(db)
        val beforeState = state(db)
        var completed = false
        vm.deleteSavedProfile(entity(db, other.id)) { completed = true }
        await(scheduler) { completed }
        assertNull(runBlocking { db.configProfileDao().getProfileById(other.id) })
        assertEquals(before, entity(db))
        assertEquals(beforeState, state(db))
        assertEquals(original, vm.activeConfig.value)
    }

    @Test fun deletingAnArmedProfileIsRefusedWithoutDisarmingOrDeletingIt() = withViewModel { vm, db, _, _ ->
        assertTrue(MappingRuntimeBridge.arm(original.gamePackage, original))
        var completed = false
        vm.deleteSavedProfile(entity(db)) { completed = true }
        assertFalse(completed)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, entity(db).id)
        assertTrue(MappingRuntimeBridge.state.value.armed)
        assertTrue(vm.snackMessage.value!!.startsWith("Profile deletion refused:"))
    }

    @Test fun deletionIsRefusedWhileAnActualSaveIsPending() = withViewModel { vm, db, scheduler, _ ->
        var saved = false
        vm.updateActiveConfig(original.copy(profileName = "Pending rename")) { saved = true }
        var deleted = false
        vm.deleteSavedProfile(entity(db)) { deleted = true }
        assertTrue(vm.snackMessage.value!!.contains("pending profile saves"))
        await(scheduler) { saved }
        assertFalse(deleted)
        assertEquals("Pending rename", entity(db).profileName)
    }

    @Test fun deletionRefusesAnUnconfirmedPanicRelease() = withViewModel { vm, db, _, app ->
        // With no AccessibilityService instance, the real panic path explicitly cannot confirm release.
        PanicKillSwitch.triggerPanic(app, "Profile action test: service unavailable")
        assertTrue(PanicKillSwitch.state.value.isKilled)
        assertFalse(PanicKillSwitch.state.value.releaseConfirmed)
        var deleted = false
        vm.deleteSavedProfile(entity(db)) { deleted = true }
        assertFalse(deleted)
        assertTrue(vm.snackMessage.value!!.contains("contact release is unconfirmed"))
        assertEquals(original.id, entity(db).id)
    }

    @Test fun deletionRefusesARetainedBackendCleanupFailureEvenWithoutAPanicRequest() = withViewModel { vm, db, _, _ ->
        MappingRuntimeBridge.disarm("Backend contact release or cleanup could not be confirmed. Panic can retry the retained backend.")
        assertFalse(PanicKillSwitch.state.value.isKilled)
        var deleted = false
        vm.deleteSavedProfile(entity(db)) { deleted = true }
        assertFalse(deleted)
        assertTrue(vm.snackMessage.value!!.contains("unconfirmed release or cleanup"))
        assertEquals(original.id, entity(db).id)
    }

    @Test fun aDisarmedLaunchFailureDoesNotRequireAnUnrelatedPanicToDeleteASafeProfile() = withViewModel { vm, db, scheduler, _ ->
        MappingRuntimeBridge.disarm("Launch failed: application is not installed")
        var deleted = false
        vm.deleteSavedProfile(entity(db)) { deleted = true }
        await(scheduler) { deleted }
        assertNull(runBlocking { db.configProfileDao().getProfileById(original.id) })
        assertNull(state(db).activeProfileId)
    }

    @Test fun failedDeleteRollsBackTheRowAndMapperStateAndNeverAcknowledgesSuccess() = withViewModel { vm, db, scheduler, _ ->
        val before = entity(db)
        val beforeState = state(db)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_profile_action_delete BEFORE DELETE ON config_profiles
            BEGIN SELECT RAISE(ABORT, 'Profile action fixture rejects delete'); END
        """.trimIndent())
        val rejected = assertThrows(Exception::class.java) {
            runBlocking { ProfilePersistence(db).delete(original.id, original.gamePackage) }
        }
        assertTrue(rejected.message.orEmpty().contains("Profile action fixture rejects delete"))
        var completed = false
        vm.deleteSavedProfile(before) { completed = true }
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile deletion failed:") == true }
        assertFalse(completed)
        assertEquals(before, entity(db))
        assertEquals(beforeState, state(db))
        assertEquals(original, vm.activeConfig.value)
    }

    @Test fun selectingAStaleEntityLoadsTheActualRowAndAcknowledgesTheCommittedState() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        val stale = entity(db, other.id)
        val fresh = other.copy(profileName = "Fresh saved name", camera = other.camera.copy(invertY = false))
        runBlocking { db.configProfileDao().insertProfile(stale.copy(profileName = fresh.profileName,
            jsonBlob = ControlystRepository.serializeConfigToJson(fresh))) }
        var completed = false
        vm.selectSavedProfile(stale) {
            assertEquals(fresh.id, state(db).activeProfileId)
            assertEquals(fresh, vm.activeConfig.value)
            completed = true
        }
        await(scheduler) { completed }
        assertEquals(fresh, vm.activeConfig.value)
        assertTrue(entity(db, other.id).isDefault)
        assertFalse(entity(db).isDefault)
    }

    @Test fun failedSelectionCannotOptimisticallyChangeTheUiOrPersistedSelection() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        val beforeState = state(db)
        rejectInserts(db)
        var completed = false
        vm.selectSavedProfile(entity(db, other.id)) { completed = true }
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile selection failed:") == true }
        assertFalse(completed)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(beforeState, state(db))
        assertTrue(entity(db).isDefault)
        assertFalse(entity(db, other.id).isDefault)
    }

    @Test fun missingAndMismatchedSelectionLeaveTheOriginalSelectionUntouched() = withViewModel { vm, db, scheduler, _ ->
        var completed = false
        vm.selectSavedProfileById("missing") { completed = true }
        await(scheduler) { vm.snackMessage.value?.contains("not saved") == true }
        vm.selectSavedProfile(entity(db).copy(gamePackage = "com.wrong.game")) { completed = true }
        await(scheduler) { vm.snackMessage.value?.contains("different game") == true }
        assertFalse(completed)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, state(db).activeProfileId)
    }

    @Test fun capturedImageHandoffSelectsOnlyTheExactSavedProfileAndDecodesTheRealImage() = withViewModel { vm, db, scheduler, app ->
        val other = insertOther(db)
        val file = java.io.File(app.cacheDir, "captured-profile-action.png")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            vm.selectTab("home")
            vm.importCapturedScreenshot(other.id, other.gamePackage, Uri.fromFile(file))
            await(scheduler) { vm.screenshot.value != null }
            assertEquals(other, vm.activeConfig.value)
            assertEquals(other.id, state(db).activeProfileId)
            assertEquals("mapper", vm.currentTab.value)
            assertEquals(120, vm.screenshot.value!!.width)
            assertEquals(80, vm.screenshot.value!!.height)
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test fun missingOrMismatchedCapturedProfileDoesNotImportOrNavigate() = withViewModel { vm, db, scheduler, app ->
        val uri = Uri.fromFile(java.io.File(app.cacheDir, "not-read-for-invalid-profile.png"))
        vm.selectTab("home")
        vm.importCapturedScreenshot("missing", original.gamePackage, uri)
        await(scheduler) { vm.snackMessage.value?.contains("not saved") == true }
        vm.importCapturedScreenshot(original.id, "com.wrong.game", uri)
        await(scheduler) { vm.snackMessage.value?.contains("different game") == true }
        assertEquals("home", vm.currentTab.value)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, state(db).activeProfileId)
        assertNull(vm.screenshot.value)
    }

    @Test fun capturedProfileHandoffRefusesAnArmedRuntimeWithoutChangingIt() = withViewModel { vm, db, _, app ->
        val other = insertOther(db)
        assertTrue(MappingRuntimeBridge.arm(original.gamePackage, original))
        vm.selectTab("home")
        vm.importCapturedScreenshot(other.id, other.gamePackage, Uri.fromFile(java.io.File(app.cacheDir, "unused.png")))
        assertTrue(vm.snackMessage.value!!.startsWith("Screenshot handoff refused:"))
        assertEquals("home", vm.currentTab.value)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, MappingRuntimeBridge.state.value.configId)
        assertNull(vm.screenshot.value)
    }

    @Test fun createProfileAcknowledgesOnlyAfterTheActualRowCommits() = withViewModel { vm, db, scheduler, _ ->
        var completed = false
        vm.createProfile("New profile") {
            val current = vm.activeConfig.value
            assertEquals("New profile", entity(db, current.id).profileName)
            assertEquals(current.id, state(db).activeProfileId)
            completed = true
        }
        await(scheduler) { completed }
        assertNotEquals(original.id, vm.activeConfig.value.id)
    }

    @Test fun rejectedProfileCreationDoesNotCloseTheDialogOrReplaceThePreviousSelection() = withViewModel { vm, db, scheduler, _ ->
        rejectInserts(db)
        var completed = false
        vm.createProfile("Rejected create") { completed = true }
        await(scheduler) { vm.snackMessage.value?.startsWith("Profile save failed:") == true }
        assertFalse(completed)
        assertEquals(original, vm.activeConfig.value)
        assertEquals(original.id, state(db).activeProfileId)
    }

    @Test fun externalCommittedProfileSelectionIsReflectedFromTheActualRoomRow() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        runBlocking { ProfilePersistence(db).select(other.id, other.gamePackage) }
        await(scheduler) { vm.activeConfig.value.id == other.id }
        assertEquals(other, vm.activeConfig.value)
        assertEquals(other.gamePackage, vm.selectedGame.value!!.packageName)
        assertEquals(other.id, state(db).activeProfileId)
        assertTrue(entity(db, other.id).isDefault)
    }

    @Test fun rejectedExternalSelectionCannotMakeTheUiLookSelectedOrSaved() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        val before = state(db)
        rejectInserts(db)
        val rejected = assertThrows(Exception::class.java) {
            runBlocking { ProfilePersistence(db).select(other.id, other.gamePackage) }
        }
        assertTrue(rejected.message.orEmpty().contains("Profile action fixture rejects insert"))
        scheduler.runCurrent()
        assertEquals(before, state(db))
        assertEquals(original, vm.activeConfig.value)
        assertFalse(entity(db, other.id).isDefault)
        assertFalse(vm.snackMessage.value.orEmpty().contains("saved", ignoreCase = true))
    }

    @Test fun anExternalSelectionDoesNotOverwriteANewUnsavedGameDraft() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        var selected = false
        val game = com.example.data.entity.GameEntity("com.test.unsaved", "Unsaved game")
        vm.selectGame(game) { selected = true }
        await(scheduler) { selected }
        val draft = vm.activeConfig.value
        assertEquals(game.packageName, draft.gamePackage)
        runBlocking { ProfilePersistence(db).select(other.id, other.gamePackage) }
        scheduler.runCurrent()
        assertEquals(draft, vm.activeConfig.value)
        assertEquals(game, vm.selectedGame.value)
        assertEquals(other.id, state(db).activeProfileId)
    }

    @Test fun missingExternalSelectedRowReportsFailureAndRetainsTheCurrentUiProfile() = withViewModel { vm, db, scheduler, _ ->
        runBlocking { db.mapperStateDao().save(state(db).copy(activeProfileId = "missing-committed-row")) }
        await(scheduler) { vm.snackMessage.value?.startsWith("Saved profile state refresh failed:") == true }
        assertEquals(original, vm.activeConfig.value)
        assertTrue(vm.snackMessage.value!!.contains("missing-committed-row"))
        assertEquals("missing-committed-row", state(db).activeProfileId)
    }

    @Test fun concurrentExternalSelectionCannotReplaceANewerLocalOptimisticSave() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        val newer = original.copy(profileName = "Newest local edit")
        var saved = false
        vm.updateActiveConfig(newer) { saved = true }
        // The local coroutine is still queued. An acknowledged external write does not erase it.
        runBlocking { ProfilePersistence(db).select(other.id, other.gamePackage) }
        assertEquals(newer, vm.activeConfig.value)
        await(scheduler) { saved }
        assertEquals(newer, vm.activeConfig.value)
        assertEquals(newer.id, state(db).activeProfileId)
        assertEquals(newer.profileName, entity(db).profileName)
    }

    @Test fun unavailableGameAdditionNeverAcknowledgesOrAddsALibraryRow() = withViewModel { vm, db, scheduler, _ ->
        var added = false
        val missing = com.example.data.entity.GameEntity("com.test.not.installed", "Missing")
        vm.addGame(missing) { added = true }
        await(scheduler) { vm.snackMessage.value?.startsWith("Add game failed:") == true }
        assertFalse(added)
        assertNull(runBlocking { db.gameDao().getGame(missing.packageName) })
        assertEquals(original, vm.activeConfig.value)
    }

    @Test fun aQueuedProfileSelectionCannotReplaceANewerLocalEditOrNavigate() = withViewModel { vm, db, scheduler, _ ->
        val other = insertOther(db)
        var selected = false
        vm.selectSavedProfile(entity(db, other.id)) { selected = true }
        val newer = original.copy(profileName = "Newer than the selection request")
        var saved = false
        vm.updateActiveConfig(newer) { saved = true }
        await(scheduler) { saved }
        assertFalse(selected)
        assertEquals(newer, vm.activeConfig.value)
        assertEquals(newer.id, state(db).activeProfileId)
    }
}
