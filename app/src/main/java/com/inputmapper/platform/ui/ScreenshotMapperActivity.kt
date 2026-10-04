package com.inputmapper.platform.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.inputmapper.platform.game.ControllerButtonBinding
import com.inputmapper.platform.game.GameProfile
import com.inputmapper.platform.game.GameProfileStore
import com.inputmapper.platform.game.StickActionType
import com.inputmapper.platform.game.StickMapping
import com.inputmapper.platform.game.TouchActionType
import com.inputmapper.platform.game.TouchMapping
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

/**
 * Offline screenshot-based layout editor.
 *
 * This editor never pretends to capture the target game. It edits the same persisted GameProfile
 * used by the live accessibility overlay, but positions markers over a user-selected screenshot.
 */
class ScreenshotMapperActivity : Activity() {
    private lateinit var store: GameProfileStore
    private lateinit var profile: GameProfile
    private lateinit var canvas: FrameLayout
    private lateinit var image: ImageView
    private lateinit var status: TextView

    private val touchMappings = mutableListOf<TouchMapping>()
    private val stickMappings = mutableListOf<StickMapping>()
    private val markerViews = mutableMapOf<String, View>()
    private var pendingButton = false
    private var screenshotUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GameProfileStore(this)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val loaded = profileId?.let(store::load)
        if (loaded == null) {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        profile = loaded
        touchMappings += profile.touchMappings
        stickMappings += profile.stickMappings
        screenshotUri = getSharedPreferences(PREFS, MODE_PRIVATE).getString(uriKey(profile.profileId), null)?.let(Uri::parse)
        buildUi()
        screenshotUri?.let(::loadScreenshot)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NexusUi.bg)
            val side = NexusUi.dp(this@ScreenshotMapperActivity, 12)
            setPadding(side, side, side, side)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(side + bars.left, side + bars.top, side + bars.right, side + bars.bottom)
                insets
            }
            post { requestApplyInsets() }
        }

        status = NexusUi.body(this, "Screenshot Mapper • ${profile.displayName}\nChoose a screenshot, then drag saved controls to their in-game positions.", 13f)
        root.addView(status)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun tool(label: String, weight: Float = 1f, block: () -> Unit) {
            toolbar.addView(Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 11f
                setOnClickListener { block() }
            }, LinearLayout.LayoutParams(0, NexusUi.dp(this, 46), weight))
        }
        tool("Screenshot") { chooseScreenshot() }
        tool("+ Button") {
            pendingButton = true
            status.text = "Press the controller button or D-pad direction to add."
        }
        tool("+ LS") { addDefaultStick(StickActionType.VIRTUAL_JOYSTICK) }
        tool("+ RS") { addDefaultStick(StickActionType.CAMERA_DRAG) }
        tool("Save") { saveProfile(); Toast.makeText(this, "Layout saved", Toast.LENGTH_SHORT).show() }
        root.addView(toolbar, NexusUi.marginParams(this, top = 8))

        canvas = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(8, 14, 24))
            foregroundGravity = Gravity.CENTER
        }
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            setBackgroundColor(Color.rgb(8, 14, 24))
        }
        canvas.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(canvas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = NexusUi.dp(this@ScreenshotMapperActivity, 8)
        })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun bottomButton(label: String, block: () -> Unit) {
            bottom.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { block() }
            }, LinearLayout.LayoutParams(0, NexusUi.dp(this, 48), 1f))
        }
        bottomButton("Save") { saveProfile(); Toast.makeText(this, "Layout saved", Toast.LENGTH_SHORT).show() }
        bottomButton("Live Overlay") {
            saveProfile()
            val service = com.inputmapper.platform.accessibility.MapperAccessibilityService.current
            if (service == null) {
                Toast.makeText(this, "Accessibility service is not connected", Toast.LENGTH_LONG).show()
            } else {
                service.showOverlayEditor(profile.profileId)
                packageManager.getLaunchIntentForPackage(profile.packageName)?.let { startActivity(it) }
            }
        }
        bottomButton("Back") { saveProfile(); finish() }
        root.addView(bottom, NexusUi.marginParams(this, top = 8))

        setContentView(root)
        canvas.post { renderMarkers() }
    }

    private fun chooseScreenshot() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
            REQUEST_SCREENSHOT
        )
    }

    @Deprecated("Activity result API kept intentionally minimal for this no-AndroidX source tree")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREENSHOT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        screenshotUri = uri
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(uriKey(profile.profileId), uri.toString()).apply()
        loadScreenshot(uri)
    }

    private fun loadScreenshot(uri: Uri) {
        image.setImageURI(uri)
        image.post {
            val drawable = image.drawable
            if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                requestedOrientation = if (drawable.intrinsicWidth >= drawable.intrinsicHeight) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
                status.text = "Screenshot Mapper • ${profile.displayName}\n${drawable.intrinsicWidth}×${drawable.intrinsicHeight} • drag controls, tap button markers for TAP/HOLD, long-press to remove."
            }
            renderMarkers()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (pendingButton && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isController(event.device)) {
            pendingButton = false
            addButtonBinding(ControllerButtonBinding(event.keyCode, event.scanCode))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (pendingButton && event.action == MotionEvent.ACTION_MOVE && event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val code = when {
                x <= -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                x >= 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                y <= -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                y >= 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> null
            }
            if (code != null) {
                pendingButton = false
                addButtonBinding(ControllerButtonBinding(code, 0))
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun addButtonBinding(binding: ControllerButtonBinding) {
        val used = (touchMappings.map { it.slot } + stickMappings.map { it.slot }).toSet()
        val slot = (2..31).firstOrNull { it !in used }
        if (slot == null) {
            Toast.makeText(this, "No free touch slots remain", Toast.LENGTH_LONG).show()
            return
        }
        touchMappings += TouchMapping(
            id = UUID.randomUUID().toString(),
            label = binding.label().removePrefix("KEYCODE_BUTTON_").removePrefix("KEYCODE_"),
            input = binding,
            action = TouchActionType.TAP,
            xNorm = 0.5f,
            yNorm = 0.5f,
            slot = slot
        )
        saveProfile()
        renderMarkers()
        status.text = "Added ${binding.label()} • drag it into position."
    }

    private fun addDefaultStick(action: StickActionType) {
        if (stickMappings.any { it.action == action }) {
            Toast.makeText(this, "That stick mapping already exists", Toast.LENGTH_SHORT).show()
            return
        }
        val used = (touchMappings.map { it.slot } + stickMappings.map { it.slot }).toSet()
        val slot = (0..31).firstOrNull { it !in used } ?: return
        stickMappings += if (action == StickActionType.VIRTUAL_JOYSTICK) {
            StickMapping("left-stick", "LS", action, MotionEvent.AXIS_X, MotionEvent.AXIS_Y, 0.22f, 0.72f, 0.11f, 1f, 0.12f, false, slot)
        } else {
            StickMapping("right-camera", "RS", action, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, 0.74f, 0.56f, 0.18f, 1f, 0.12f, false, slot)
        }
        saveProfile()
        renderMarkers()
    }

    private fun renderMarkers() {
        if (!::canvas.isInitialized || canvas.width <= 0 || canvas.height <= 0) return
        markerViews.values.forEach { canvas.removeView(it) }
        markerViews.clear()
        touchMappings.forEach { mapping -> addMarker(mapping.id, "${mapping.label}\n${mapping.action}", mapping.xNorm, mapping.yNorm, NexusUi.cyan) { x, y, click, held ->
            val i = touchMappings.indexOfFirst { it.id == mapping.id }
            if (i >= 0) {
                if (held) touchMappings.removeAt(i) else {
                    var updated = touchMappings[i].copy(xNorm = x, yNorm = y)
                    if (click) updated = updated.copy(action = if (updated.action == TouchActionType.TAP) TouchActionType.HOLD else TouchActionType.TAP)
                    touchMappings[i] = updated
                }
                saveProfile(); renderMarkers()
            }
        } }
        stickMappings.forEach { mapping -> addMarker(mapping.id, "${mapping.label}\n${if (mapping.action == StickActionType.VIRTUAL_JOYSTICK) "JOYSTICK" else "CAMERA"}", mapping.xNorm, mapping.yNorm, NexusUi.violet) { x, y, _, held ->
            val i = stickMappings.indexOfFirst { it.id == mapping.id }
            if (i >= 0) {
                if (held) stickMappings.removeAt(i) else stickMappings[i] = stickMappings[i].copy(xNorm = x, yNorm = y)
                saveProfile(); renderMarkers()
            }
        } }
    }

    private fun addMarker(
        id: String,
        label: String,
        xNorm: Float,
        yNorm: Float,
        color: Int,
        onUpdate: (Float, Float, Boolean, Boolean) -> Unit
    ) {
        val rect = imageContentRect()
        if (rect.width() <= 1f || rect.height() <= 1f) return
        val size = NexusUi.dp(this, 68)
        val marker = TextView(this).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(NexusUi.dp(this@ScreenshotMapperActivity, 2), Color.WHITE)
            }
            alpha = 0.88f
        }
        val params = FrameLayout.LayoutParams(size, size)
        fun place(nx: Float, ny: Float) {
            params.leftMargin = (rect.left + nx * rect.width() - size / 2f).toInt().coerceIn(rect.left.toInt(), maxOf(rect.left.toInt(), (rect.right - size).toInt()))
            params.topMargin = (rect.top + ny * rect.height() - size / 2f).toInt().coerceIn(rect.top.toInt(), maxOf(rect.top.toInt(), (rect.bottom - size).toInt()))
        }
        place(xNorm, yNorm)
        var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0; var downTime = 0L; var moved = false
        marker.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startLeft = params.leftMargin; startTop = params.topMargin
                    downTime = SystemClock.uptimeMillis(); moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt(); val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    params.leftMargin = (startLeft + dx).coerceIn(rect.left.toInt(), maxOf(rect.left.toInt(), (rect.right - size).toInt()))
                    params.topMargin = (startTop + dy).coerceIn(rect.top.toInt(), maxOf(rect.top.toInt(), (rect.bottom - size).toInt()))
                    marker.layoutParams = params; true
                }
                MotionEvent.ACTION_UP -> {
                    val held = !moved && SystemClock.uptimeMillis() - downTime >= 650L
                    val click = !moved && !held
                    val nx = ((params.leftMargin + size / 2f - rect.left) / rect.width()).coerceIn(0f, 1f)
                    val ny = ((params.topMargin + size / 2f - rect.top) / rect.height()).coerceIn(0f, 1f)
                    onUpdate(nx, ny, click, held); true
                }
                else -> false
            }
        }
        canvas.addView(marker, params)
        markerViews[id] = marker
    }

    private fun imageContentRect(): RectF {
        val drawable = image.drawable ?: return RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val dw = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val dh = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val vw = image.width.toFloat().coerceAtLeast(1f)
        val vh = image.height.toFloat().coerceAtLeast(1f)
        val scale = min(vw / dw, vh / dh)
        val width = dw * scale
        val height = dh * scale
        val left = (vw - width) / 2f
        val top = (vh - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    private fun saveProfile() {
        profile = profile.copy(
            savedAtMillis = System.currentTimeMillis(),
            touchMappings = touchMappings.toList(),
            stickMappings = stickMappings.toList()
        )
        store.save(profile)
    }

    private fun isController(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun uriKey(profileId: String) = "screenshot_uri_$profileId"

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val PREFS = "nexus_screenshot_mapper"
        private const val REQUEST_SCREENSHOT = 6010
    }
}
