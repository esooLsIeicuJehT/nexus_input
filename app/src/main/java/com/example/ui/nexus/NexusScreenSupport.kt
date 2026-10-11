package com.example.ui.nexus

import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.input.ControllerSourceClassifier
import com.example.service.ControlystAccessibilityService
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class InputDeviceInventory(val devices: List<InputDevice> = emptyList(), val error: String? = null)
internal fun isNexusPhysicalInput(device: InputDevice): Boolean = !device.isVirtual &&
    (ControllerSourceClassifier.isControllerSource(device.sources) ||
        device.supportsSource(InputDevice.SOURCE_MOUSE) || device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE)

@Composable
internal fun rememberInputDevices(onChanged: () -> Unit, refreshKey: Int = 0): InputDeviceInventory {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onChanged)
    var inventory by remember { mutableStateOf(InputDeviceInventory()) }
    DisposableEffect(context, refreshKey) {
        val manager = context.getSystemService(InputManager::class.java)
        fun refresh() {
            inventory = try {
                checkNotNull(manager) { "Android InputManager is unavailable" }
                InputDeviceInventory(manager.inputDeviceIds.asSequence().mapNotNull(manager::getInputDevice).filter(::isNexusPhysicalInput).toList())
            } catch (error: Exception) {
                Log.e("NexusDevices", "Device inventory failed", error)
                InputDeviceInventory(error = "Device inventory failed: ${error.message}")
            }
            try { currentCallback() }
            catch (error: Exception) {
                Log.e("NexusDevices", "Controller observation failed", error)
                inventory = inventory.copy(error = "Controller observation failed: ${error.message}")
            }
        }
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        }
        refresh()
        try { manager?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper())) }
        catch (error: Exception) {
            Log.e("NexusDevices", "Device observation failed", error)
            inventory = inventory.copy(error = "Device observation failed: ${error.message}")
        }
        onDispose {
            try { manager?.unregisterInputDeviceListener(listener) }
            catch (error: Exception) { Log.e("NexusDevices", "Device observation cleanup failed", error) }
        }
    }
    return inventory
}

internal data class NexusPermissions(val overlay: Boolean, val accessibility: Boolean, val error: String? = null)
@Composable
internal fun rememberNexusPermissions(): NexusPermissions {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun read(): NexusPermissions = try {
        NexusPermissions(Settings.canDrawOverlays(context), ControlystAccessibilityService.isServiceRunning())
    } catch (error: Exception) {
        Log.e("NexusPermissions", "Permission observation failed", error)
        NexusPermissions(false, false, "Permission observation failed: ${error.message}")
    }
    var state by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state = read() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
internal fun NexusPackageIcon(packageName: String, modifier: Modifier = Modifier, size: Int = 96) {
    val context = LocalContext.current
    val result by produceState<Result<Bitmap>?>(null, context, packageName) {
        value = withContext(Dispatchers.IO) {
            try { Result.success(context.packageManager.getApplicationIcon(packageName).toBitmap(size, size)) }
            catch (error: Exception) {
                Log.w("NexusProfiles", "Cannot load package icon for $packageName", error)
                Result.failure(error)
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        val bitmap = result?.getOrNull()
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = "$packageName app icon", modifier = Modifier.fillMaxSize())
            result == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, contentDescription = "Package icon unavailable", tint = TextSecondary)
                Text("Unavailable", color = TextSecondary, fontSize = 8.sp)
            }
        }
    }
}
