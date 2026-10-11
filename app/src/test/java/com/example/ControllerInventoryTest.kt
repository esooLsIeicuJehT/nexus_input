package com.example

import android.app.Application
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import com.example.calibration.CalibrationManager
import com.example.model.ControllerType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.InputDeviceBuilder

/** Real SDK inventory lookup with explicit fixtures; no physical device verification is implied.
 * SDK isVirtual(): virtual devices implement system functions and must not appear as user hardware.
 * https://developer.android.com/reference/android/view/InputDevice#isVirtual()
 * Framework isVirtual returns mId < 0; fixtures assert that SDK property before registering them.
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/InputDevice.java
 * Builder setters: https://robolectric.org/javadoc/4.15/org/robolectric/shadows/InputDeviceBuilder.html
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControllerInventoryTest {
    private lateinit var app: Application
    private lateinit var manager: InputManager

    @Before fun useActualEmptyAndroidInventory() {
        app = ApplicationProvider.getApplicationContext()
        manager = app.getSystemService(InputManager::class.java)
        assertTrue("The test sandbox must begin without invented/preexisting devices", manager.inputDeviceIds.isEmpty())
    }

    private fun register(id: Int, name: String, sources: Int, keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE): InputDevice {
        val device = InputDeviceBuilder.newBuilder().setId(id).setName(name).setDescriptor("fixture-$id")
            .setSources(sources).setKeyboardType(keyboardType).build()
        assertEquals("Fixture must use the real SDK virtual-device property", id < 0, device.isVirtual)
        shadowOf(manager).addInputDevice(device)
        assertNotNull("Framework lookup must resolve the registered fixture", InputDevice.getDevice(id))
        assertTrue("Android inventory must contain the registered fixture", InputDevice.getDeviceIds().contains(id))
        return device
    }

    @Test fun virtualGamepadNeverBecomesAConnectedController() {
        register(-91, "Virtual gamepad fixture", InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val result = CalibrationManager(app).detectConnectedController()
        assertFalse(result.connected); assertNull(result.deviceName); assertNull(result.vendorId)
    }

    @Test fun virtualMouseNeverBecomesThePhysicalFallback() {
        register(-92, "Virtual mouse fixture", InputDevice.SOURCE_MOUSE)
        assertFalse(CalibrationManager(app).detectConnectedController().connected)
    }

    @Test fun virtualKeyboardNeverBecomesThePhysicalFallback() {
        register(-93, "Virtual keyboard fixture", InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        assertFalse(CalibrationManager(app).detectConnectedController().connected)
    }

    @Test fun physicalGamepadIsSelectedAfterVirtualDevicesAreIgnored() {
        register(-91, "Virtual gamepad fixture", InputDevice.SOURCE_GAMEPAD)
        register(-93, "Virtual keyboard fixture", InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val physical = register(94, "Physical gamepad fixture", InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        val result = CalibrationManager(app).detectConnectedController()
        assertTrue(result.connected); assertEquals(physical.name, result.deviceName)
    }

    @Test fun physicalMouseFallbackStillWorksWithVirtualGamepadPresent() {
        register(-91, "Virtual gamepad fixture", InputDevice.SOURCE_GAMEPAD)
        val physical = register(95, "Physical mouse fixture", InputDevice.SOURCE_MOUSE)
        val result = CalibrationManager(app).detectConnectedController()
        assertTrue(result.connected); assertEquals(physical.name, result.deviceName); assertEquals(ControllerType.MOUSE_KEYBOARD, result.type)
    }

    @Test fun physicalKeyboardFallbackStillWorksWithVirtualMousePresent() {
        register(-92, "Virtual mouse fixture", InputDevice.SOURCE_MOUSE)
        val physical = register(96, "Physical keyboard fixture", InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val result = CalibrationManager(app).detectConnectedController()
        assertTrue(result.connected); assertEquals(physical.name, result.deviceName); assertEquals(ControllerType.MOUSE_KEYBOARD, result.type)
    }
}
