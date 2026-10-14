package com.example

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControlystDatabase
import com.example.data.LegacyProfileMigration
import com.example.injector.CommandResult
import com.example.injector.PrivilegeAvailabilityState
import com.example.injector.PrivilegeDetector
import com.example.injector.ShellExecutor
import com.example.model.PrivilegeMethod
import com.example.service.MappingRuntimeBridge
import com.example.service.PanicKillSwitch
import com.example.ui.MainAppViewModel
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Explicit denied-shell fixture; this test does not establish device privilege availability. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivilegeRefreshResponsivenessTest {
    @get:Rule val boundedTest = Timeout.seconds(45)
    @Test fun aBlockedDeniedRootProbeLeavesMainResponsiveAndPublishesOnlyAfterCompletion() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(LegacyProfileMigration.PREFERENCES, 0).edit().clear().commit()
        app.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES, 0).edit().clear().commit()
        MappingRuntimeBridge.disarm()
        PanicKillSwitch.resetPanic()
        val db = Room.inMemoryDatabaseBuilder(app, ControlystDatabase::class.java)
            .allowMainThreadQueries().build()
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val store = ViewModelStore()
        val commandThread = AtomicLong(-1)
        val commands = Collections.synchronizedList(mutableListOf<List<String>>())
        val finalCommandEntered = CountDownLatch(1)
        val allowFinalCommandToReturn = CountDownLatch(1)
        val deniedShell = object : ShellExecutor {
            override fun run(argv: List<String>, timeoutMillis: Long): CommandResult {
                commandThread.compareAndSet(-1, Thread.currentThread().id)
                commands.add(argv.toList())
                if (argv == listOf("su", "-c", "id")) {
                    finalCommandEntered.countDown()
                    if (!allowFinalCommandToReturn.await(10, TimeUnit.SECONDS)) {
                        return CommandResult(null, "", "Denied-shell fixture gate timed out", timedOut = true)
                    }
                }
                return CommandResult(7, "", "Explicit test fixture: su permission denied", timedOut = false)
            }
        }
        try {
            val vm = MainAppViewModel(app, db, PrivilegeDetector(app, deniedShell))
            store.put("privilege-refresh-responsiveness", vm)
            await(scheduler) { finalCommandEntered.count == 0L }

            assertNotEquals("Blocking shell work must not run on the main test scheduler thread",
                Thread.currentThread().id, commandThread.get())
            assertEquals(listOf(listOf("su", "-v"), listOf("su", "-V"), listOf("su", "-c", "id")), commands.toList())
            assertTrue("The incomplete batch must not be presented as available probes", vm.privilegeResults.value.isEmpty())
            assertNull(vm.activePrivilegeMethod.value)
            assertNull(vm.currentInjector)

            var mainActionCompleted = false
            vm.viewModelScope.launch {
                vm.selectTab("devices")
                mainActionCompleted = true
            }
            scheduler.runCurrent()
            assertTrue("A queued UI action must run while the shell command is still blocked", mainActionCompleted)
            assertEquals("devices", vm.currentTab.value)
            assertEquals(1L, allowFinalCommandToReturn.count)
            assertTrue(vm.privilegeResults.value.isEmpty())

            allowFinalCommandToReturn.countDown()
            await(scheduler) { vm.privilegeResults.value.isNotEmpty() }
            val rootMethods = setOf(PrivilegeMethod.MAGISK, PrivilegeMethod.KERNELSU, PrivilegeMethod.APATCH)
            val rootResults = vm.privilegeResults.value.filter { it.method in rootMethods }
            assertEquals(rootMethods, rootResults.map { it.method }.toSet())
            rootResults.forEach { result ->
                assertEquals(PrivilegeAvailabilityState.NOT_FOUND, result.state)
                assertFalse(result.isDetected)
                assertEquals("No usable uid=0 su session", result.statusDetail)
            }
        } finally {
            allowFinalCommandToReturn.countDown()
            store.clear()
            scheduler.runCurrent()
            db.close()
            MappingRuntimeBridge.disarm()
            PanicKillSwitch.resetPanic()
            Dispatchers.resetMain()
        }
    }

    private fun await(scheduler: TestCoroutineScheduler, condition: () -> Boolean) {
        // This bound does not rely on a Robolectric-interceptable time source.
        repeat(1_000) {
            scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Privilege refresh did not reach its observed state after 1000 scheduler attempts")
    }
}
