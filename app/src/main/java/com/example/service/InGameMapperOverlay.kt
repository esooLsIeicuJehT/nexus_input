package com.example.service

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import com.example.data.ControlystDatabase
import com.example.data.ProfilePersistence
import com.example.input.MapperEditSession
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Android windows over the actual game. Injection is released before any editable window opens. */
class InGameMapperOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: ScrollView? = null
    private var panelStatus: TextView? = null
    private var editor: FrameLayout? = null
    private var dialog: AlertDialog? = null
    private var session: MapperEditSession? = null
    private var selected: String? = null
    private var saving = false
    private var closed = false
    private var bubbleObserver: Job? = null
    private var saveJob: Job? = null
    private var snapToGrid = false
    private var nodesVisible = true
    private var inspector: ScrollView? = null
    private var initialSelection: String? = null

    private fun displaySize(): Pair<Int,Int> {
        if(Build.VERSION.SDK_INT >= 30) {
            val bounds=wm.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics=android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        return metrics.widthPixels to metrics.heightPixels
    }
    private fun safeControlInsets(): Pair<Int,Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            ).let { it.top to it.bottom }
        } else 0 to 0

    private fun clamp(layout: WindowManager.LayoutParams, width: Int, height: Int) {
        val (w,h)=displaySize()
        val position=OverlayBounds.position(layout.x,layout.y,width,height,w,h)
        layout.x=position.first;layout.y=position.second
    }
    private fun params(width: Int, height: Int, focusable: Boolean = false) = WindowManager.LayoutParams(width,height,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private fun fail(message: String, error: Throwable? = null, preserveRuntimeError: Boolean = false) {
        val original = MappingRuntimeBridge.state.value.error.takeIf { preserveRuntimeError }
        val displayed = if (original == null) message else "$message\n$original"
        android.util.Log.e("NexusOverlay",displayed,error)
        if (original == null) MappingRuntimeBridge.reportError(message)
        Toast.makeText(context,displayed,Toast.LENGTH_LONG).show()
    }
    private fun remove(view: View?) { if (view != null) try { wm.removeView(view) } catch(error:Exception) { fail("Overlay removal failed: ${error.message}",error) } }
    private fun closePanel() { remove(panel);panel=null;panelStatus=null }
    // Translucent static gradients keep the overlay lightweight over a live game.
    private fun glass(oval: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xC51B3049.toInt(), 0xC0091526.toInt(), 0xB0291D4D.toInt())
    ).apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = 16*density
        setStroke(maxOf(1, density.roundToInt()), 0xB016F1FF.toInt())
    }
    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 11f; setTextColor(Color.WHITE); background = glass(); minHeight = (44*density).roundToInt(); setPadding((12*density).roundToInt(),0,(12*density).roundToInt(),0)
        setOnClickListener { action() }
    }

    fun show() {
        if (closed || bubble != null) return
        if (!Settings.canDrawOverlays(context)) { fail("Floating mapper requires Android overlay permission");return }
        val size = (52*density).roundToInt()
        val view = TextView(context).apply {
            text="N";textSize=22f;gravity=Gravity.CENTER;setTextColor(0xFF16F1FF.toInt())
            contentDescription="NEXUS INPUT floating controls: tap for mapper, long-press to panic"
            background=glass(oval=true); elevation=8*density
        }
        val layout=params(size,size).apply { x=(12*density).roundToInt();y=(120*density).roundToInt() }
        clamp(layout,size,size)
        var downX=0f;var downY=0f;var baseX=0;var baseY=0;var dragged=false;var longPressed=false
        val panic = Runnable { longPressed=true;PanicKillSwitch.triggerPanic(context,"Floating bubble") }
        view.setOnTouchListener { _, event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX;downY=event.rawY;baseX=layout.x;baseY=layout.y;dragged=false;longPressed=false;handler.postDelayed(panic,800) }
                MotionEvent.ACTION_MOVE -> {
                    if(hypot(event.rawX-downX,event.rawY-downY)>8*density) { dragged=true;handler.removeCallbacks(panic) }
                    if(dragged) {
                        layout.x=(baseX+event.rawX-downX).roundToInt().coerceAtLeast(0)
                        layout.y=(baseY+event.rawY-downY).roundToInt().coerceAtLeast(0)
                        clamp(layout,size,size)
                        try { wm.updateViewLayout(view,layout) } catch(error:Exception) { fail("Bubble move failed: ${error.message}",error) }
                    }
                }
                MotionEvent.ACTION_UP -> { handler.removeCallbacks(panic);if(!dragged&&!longPressed) { view.performClick();togglePanel() } }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(panic)
            };true
        }
        try { wm.addView(view,layout);bubble=view;bubbleParams=layout } catch(error:Exception) { fail("Floating bubble failed: ${error.message}",error);return }
        bubbleObserver?.cancel()
        bubbleObserver=scope.launch { MappingRuntimeBridge.state.collect { state ->
            view.setTextColor(if(state.backendReady) 0xFF20D5A4.toInt() else if(state.error!=null) 0xFFFF6588.toInt() else 0xFF16F1FF.toInt())
            panelStatus?.text = MapperPanelStatus.text(state)
        } }
    }

    private fun togglePanel() {
        if (panel != null) { closePanel();return }
        val status = TextView(context).apply {
            text = MapperPanelStatus.text(MappingRuntimeBridge.state.value)
            textSize = 11f;setTextColor(Color.WHITE);setPadding(8,8,8,8)
        }
        val menu=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8);background=glass();elevation=8*density
            addView(TextView(context).apply { text="NEXUS INPUT · QUICK MENU";setTextColor(0xFF16F1FF.toInt());textSize=12f })
            addView(status)
            val actions=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
            actions.addView(button("✎\nMapper") { beginEdit() },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(button("⇄\nProfile") { chooseProfile() },LinearLayout.LayoutParams(0,-2,1f))
            actions.addView(button("◎\nCamera") { initialSelection="RS";beginEdit() },LinearLayout.LayoutParams(0,-2,1f))
            addView(actions)
            addView(button("▣  Screenshot mapper") { captureForMapper() })
            val safety=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
            safety.addView(button("!\nPanic") { PanicKillSwitch.triggerPanic(context,"Floating controls") },LinearLayout.LayoutParams(0,-2,1f))
            safety.addView(button("×\nClose") { closePanel() },LinearLayout.LayoutParams(0,-2,1f))
            addView(safety)
        }
        val (screenWidth,screenHeight) = displaySize()
        val width = (300*density).roundToInt().coerceAtMost(screenWidth.coerceAtLeast(1))
        val height = (360*density).roundToInt().coerceAtMost(screenHeight.coerceAtLeast(1))
        val window = ScrollView(context).apply { addView(menu) }
        val layout=params(width,height).apply { x=bubbleParams?.x?:0;y=(bubbleParams?.y?:0)+(54*density).roundToInt() }
        clamp(layout,width,height)
        try { wm.addView(window,layout);panel=window;panelStatus=status }
        catch(error:Exception) { fail("Floating controls failed: ${error.message}",error) }
    }

    private fun beginEdit() {
        val config=MappingRuntimeBridge.config.value ?: run { fail("No armed game profile is available to edit",preserveRuntimeError=true);return }
        val service=ControlystAccessibilityService.getInstance() ?: run { fail("Capture service is unavailable; release cannot be confirmed",preserveRuntimeError=true);return }
        closePanel()
        service.emergencyRelease { released -> handler.post {
            if(closed) return@post
            if(!released) { fail("Mapper edit aborted: backend did not acknowledge release",preserveRuntimeError=true);return@post }
            session=MapperEditSession(config)
            selected=initialSelection?.let { binding -> config.buttons.firstOrNull { it.boundKey==binding }?.id }
            initialSelection=null;nodesVisible=true;openEditor()
        } }
    }

    private fun captureForMapper() {
        val config=MappingRuntimeBridge.config.value ?: run { fail("Launch a mapped game before capturing",preserveRuntimeError=true);return }
        val service=ControlystAccessibilityService.getInstance() ?: run { fail("Screenshot capture service is unavailable",preserveRuntimeError=true);return }
        if(!MappingRuntimeBridge.state.value.targetForeground) { fail("Game focus must be confirmed before screenshot capture");return }
        service.emergencyRelease { released -> handler.post {
            if(closed) return@post
            if(!released) { fail("Screenshot aborted: contact release was not acknowledged",preserveRuntimeError=true);return@post }
            closePanel();bubbleObserver?.cancel();bubbleObserver=null;remove(bubble);bubble=null
            var pending=true
            val timeout=Runnable { if(pending && !closed) { pending=false;fail("Screenshot capture timed out");show() } }
            handler.postDelayed(timeout,8_000)
            Choreographer.getInstance().postFrameCallback {
                Choreographer.getInstance().postFrameCallback captureFrame@{
                    if(closed || !pending) return@captureFrame
                    service.captureScreenshot { result -> handler.post captureResult@{
                        if(!pending || closed) { result.getOrNull()?.recycle();return@captureResult }
                        pending=false;handler.removeCallbacks(timeout)
                        result.onSuccess { bitmap ->
                            val opened=com.example.ui.mapper.MapperScreenshotHandoff.open(context,bitmap,config)
                            bitmap.recycle()
                            opened.onFailure { fail("Screenshot mapper could not open: ${it.message}",it);show() }
                        }.onFailure { fail("Screenshot capture failed: ${it.message}",it);show() }
                    } }
                }
            }
        } }
    }

    private fun chooseProfile() {
        val original=MappingRuntimeBridge.config.value ?: run { fail("No active game profile can be switched",preserveRuntimeError=true);return }
        val service=ControlystAccessibilityService.getInstance() ?: run { fail("Capture service is unavailable",preserveRuntimeError=true);return }
        service.emergencyRelease { released -> handler.post {
            if(closed) return@post
            if(!released) { fail("Profile switch aborted: release was not acknowledged",preserveRuntimeError=true);return@post }
            closePanel()
            saveJob=scope.launch {
                try {
                    val db=ControlystDatabase.getDatabase(context)
                    val profiles=withContext(Dispatchers.IO) { db.configProfileDao().getProfilesForGame(original.gamePackage).first() }
                    require(profiles.isNotEmpty()) { "No saved profiles for ${original.gamePackage}" }
                    val choice=AlertDialog.Builder(context).setTitle("Profiles for ${original.gameTitle.ifBlank { original.gamePackage }}")
                        .setItems(profiles.map { it.profileName+if(it.isDefault) " · default" else "" }.toTypedArray()) { _,index ->
                            saveJob=scope.launch {
                                try {
                                    val config=withContext(Dispatchers.IO) {
                                        ProfilePersistence(db).load(profiles[index].id,original.gamePackage).also { ProfilePersistence(db).save(it) }
                                    }
                                    if(!closed) {
                                        MappingForegroundService.currentCrosshairConfig.value=config.crosshair
                                        MappingRuntimeBridge.arm(config.gamePackage,config)
                                    }
                                } catch(error:Exception) { if(error is CancellationException) throw error;fail("Profile switch failed: ${error.message}",error) }
                            }
                        }.setNegativeButton("Cancel") { _,_ -> if(!closed) MappingRuntimeBridge.arm(original.gamePackage,original) }.create()
                    choice.window?.setType(params(1,1).type);dialog=choice
                    choice.show();choice.window?.setBackgroundDrawable(glass())
                } catch(error:Exception) { if(error is CancellationException) throw error;fail("Profile list unavailable: ${error.message}",error) }
            }
        } }
    }

    private fun showInspector(canvas: EditorCanvas) {
        val root=editor ?: return
        inspector?.let(root::removeView);inspector=null
        val node=session?.draft?.buttons?.firstOrNull { it.id==selected } ?: return
        val body=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;background=glass();setPadding(12,8,12,8) }
        fun label(text:String)=TextView(context).apply { this.text=text;setTextColor(Color.WHITE);textSize=11f }
        body.addView(label("SELECTED · ${node.boundKey}"))
        body.addView(label("${node.type} · ${node.buttonBehavior}"))
        fun updateNode(change: (MappingNode) -> MappingNode): MappingNode? {
            val edit=session ?: return null
            return try {
                edit.replaceDraft(edit.draft.copy(buttons=edit.draft.buttons.map { if(it.id==node.id) change(it) else it }));canvas.invalidate()
                edit.draft.buttons.firstOrNull { it.id==node.id }
            } catch(error:Exception) { fail("Tuning rejected: ${error.message}",error);null }
        }
        fun slider(title:String,value:Float,min:Float,max:Float,change:(Float)->Float?) {
            val caption=label("$title · ${"%.2f".format(value)}");body.addView(caption)
            body.addView(SeekBar(context).apply {
                var observed=value
                this.max=100;progress=(((value-min)/(max-min))*100).roundToInt().coerceIn(0,100)
                if(Build.VERSION.SDK_INT>=21) progressTintList=android.content.res.ColorStateList.valueOf(0xFF16F1FF.toInt())
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar:SeekBar?)=Unit
                    override fun onStopTrackingTouch(bar:SeekBar?)=Unit
                    override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
                        if(fromUser) {
                            val requested=min+(max-min)*progress/100
                            change(requested)?.let { observed=it }
                            caption.text="$title · ${"%.2f".format(observed)}"
                            bar?.progress=(((observed-min)/(max-min))*100).roundToInt().coerceIn(0,100)
                        }
                    }
                })
            })
        }
        slider("Radius",node.radiusNorm,.02f,.3f) { radius -> updateNode { it.copy(radiusNorm=radius) }?.radiusNorm }
        if(node.type in setOf(NodeType.JOYSTICK_ZONE,NodeType.CAMERA_DRAG)) {
            slider("Sensitivity",node.sensitivity,.25f,3f) { value -> updateNode { it.copy(sensitivity=value) }?.sensitivity }
            slider("Inner deadzone",node.deadzoneInner,0f,.9f) { value -> updateNode { it.copy(deadzoneInner=value.coerceAtMost(it.deadzoneOuter-.01f)) }?.deadzoneInner }
            slider("Outer deadzone",node.deadzoneOuter,.01f,1f) { value -> updateNode { it.copy(deadzoneOuter=value.coerceAtLeast(it.deadzoneInner+.01f)) }?.deadzoneOuter }
            body.addView(CheckBox(context).apply {
                text="Invert Y";setTextColor(Color.WHITE)
                isChecked=node.invertY || (node.type==NodeType.CAMERA_DRAG && session!!.draft.camera.invertY)
                setOnCheckedChangeListener { _,enabled ->
                    if(node.type==NodeType.CAMERA_DRAG) session?.setCameraInversion(enabled)
                    else updateNode { it.copy(invertY=enabled) }
                }
            })
        }
        if(node.type==NodeType.CAMERA_DRAG) {
            val settings=session!!.draft.camera
            fun camera(change:(CameraSettings)->CameraSettings): CameraSettings? {
                val edit=session ?: return null
                return try { edit.replaceDraft(edit.draft.copy(camera=change(edit.draft.camera)));edit.draft.camera }
                catch(error:Exception) { fail("Camera tuning rejected: ${error.message}",error);null }
            }
            slider("Horizontal",settings.horizontalSensitivity,.1f,3f) { value -> camera { it.copy(horizontalSensitivity=value) }?.horizontalSensitivity }
            slider("Vertical",settings.verticalSensitivity,.1f,3f) { value -> camera { it.copy(verticalSensitivity=value) }?.verticalSensitivity }
            slider("Smoothing",settings.smoothingFrames.toFloat(),1f,12f) { value -> camera { it.copy(smoothingFrames=value.roundToInt()) }?.smoothingFrames?.toFloat() }
            slider("Fast turn boost",settings.fastTurnBoost,1f,3f) { value -> camera { it.copy(fastTurnBoost=value) }?.fastTurnBoost }
        }
        if(node.type==NodeType.JOYSTICK_ZONE) {
            val settings=session!!.draft.joystick
            fun joystick(change:(JoystickSettings)->JoystickSettings): JoystickSettings? {
                val edit=session ?: return null
                return try { edit.replaceDraft(edit.draft.copy(joystick=change(edit.draft.joystick)));edit.draft.joystick }
                catch(error:Exception) { fail("Movement tuning rejected: ${error.message}",error);null }
            }
            slider("Walk radius",settings.walkRadiusScale,.1f,.95f) { value -> joystick { it.copy(walkRadiusScale=value.coerceAtMost(it.runRadiusScale-.01f)) }?.walkRadiusScale }
            slider("Run radius",settings.runRadiusScale,.2f,1.5f) { value -> joystick { it.copy(runRadiusScale=value.coerceAtLeast(it.walkRadiusScale+.01f)) }?.runRadiusScale }
            slider("Run threshold",settings.runThresholdNorm,.05f,.99f) { value -> joystick { it.copy(runThresholdNorm=value) }?.runThresholdNorm }
        }
        if(com.example.input.ControllerBindingAliases.canonical(node.boundKey) in setOf("LT","RT")) {
            slider("Press threshold",node.triggerPressThreshold,.1f,1f) { value -> updateNode { it.copy(triggerPressThreshold=value,triggerReleaseThreshold=it.triggerReleaseThreshold.coerceAtMost(value-.05f)) }?.triggerPressThreshold }
            slider("Release threshold",node.triggerReleaseThreshold,0f,.9f) { value -> updateNode { it.copy(triggerReleaseThreshold=value.coerceAtMost(it.triggerPressThreshold-.05f)) }?.triggerReleaseThreshold }
        }
        if(node.type==NodeType.BUTTON) body.addView(CheckBox(context).apply {
            text="Hold touch";setTextColor(Color.WHITE);isChecked=node.buttonBehavior==ButtonBehavior.HOLD
            setOnCheckedChangeListener { _,enabled -> updateNode { it.copy(buttonBehavior=if(enabled) ButtonBehavior.HOLD else ButtonBehavior.TAP) } }
        })
        body.addView(button("Rebind") { chooseBinding(false,canvas) })
        body.addView(button("Duplicate") { chooseBinding(false,canvas,duplicate=true) })
        body.addView(button("Delete") { session?.remove(node.id);selected=null;inspector?.let(root::removeView);inspector=null;canvas.invalidate() })
        val window=ScrollView(context).apply { addView(body) }
        val (w,h)=displaySize()
        root.addView(window,FrameLayout.LayoutParams(minOf((220*density).roundToInt(),w/2),minOf((360*density).roundToInt(),h/2)).apply {
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;rightMargin=(8*density).roundToInt()
        })
        inspector=window
    }

    private fun openEditor() {
        bubbleObserver?.cancel();bubbleObserver=null
        remove(bubble);bubble=null
        val root=FrameLayout(context)
        val canvas=EditorCanvas()
        root.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        val bar=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL;background=glass()
            addView(button("Add") { chooseBinding(true,canvas) })
            addView(button("Bind") { chooseBinding(false,canvas) })
            addView(button("Copy") { chooseBinding(false,canvas,duplicate=true) })
            addView(button("Learn") { learnBinding(canvas) })
            addView(button("Delete") { selected?.let {
                session?.remove(it);selected=null;inspector?.let(root::removeView);inspector=null;canvas.invalidate()
            } })
            addView(button("Grid") { snapToGrid=!snapToGrid;canvas.invalidate() })
            addView(button("Hide nodes") { nodesVisible=!nodesVisible;canvas.invalidate() })
            addView(button("Reset") { session?.reset();selected=null;inspector?.let(root::removeView);inspector=null;canvas.invalidate() })
            addView(button("− Size") { selected?.let { session?.resize(it,-.01f);canvas.invalidate() } })
            addView(button("+ Size") { selected?.let { session?.resize(it,.01f);canvas.invalidate() } })
            addView(button("Save") { save() })
            addView(button("Cancel") { finishEdit(session?.original) })
            addView(button("Panic") { PanicKillSwitch.triggerPanic(context,"In-game mapper") })
        }
        val scroll=HorizontalScrollView(context).apply { addView(bar);isFillViewport=true }
        val safeInsets=safeControlInsets()
        root.addView(scroll,FrameLayout.LayoutParams(-1,(52*density).roundToInt()).apply {
            gravity=Gravity.BOTTOM
            bottomMargin=safeInsets.second
        })
        root.addView(TextView(context).apply {
            text="Mapping paused · drag a binding over the real game · tap to select"
            setTextColor(Color.WHITE);background=glass();textSize=11f
        },FrameLayout.LayoutParams(-1,(26*density).roundToInt()).apply {
            gravity=Gravity.TOP
            topMargin=safeInsets.first
        })
        try { wm.addView(root,params(-1,-1));editor=root } catch(error:Exception) { fail("In-game mapper window failed: ${error.message}",error);finishEdit(null) }
        if(selected!=null) showInspector(canvas)
    }

    private fun chooseBinding(add: Boolean, canvas: View, duplicate: Boolean = false) {
        if(saving || (!add && selected==null)) { Toast.makeText(context,"Select a binding first",Toast.LENGTH_SHORT).show();return }
        val keys=com.example.input.ControllerBindingAliases.supported.sorted().toTypedArray()
        val choice=AlertDialog.Builder(context).setTitle(if(duplicate) "Copy to a distinct physical input" else if(add) "Add physical input" else "Bind physical input")
            .setItems(keys) { _, index ->
                val binding=keys[index]
                try {
                    if(duplicate) selected=selected?.let { session?.duplicate(it,binding,
                        if(binding in setOf("LT","RT")) ButtonBehavior.HOLD else ButtonBehavior.TAP) }
                    else if(add) selected=session?.add(binding)
                    else selected?.let { session?.bind(it,binding,if(binding in setOf("LT","RT")) ButtonBehavior.HOLD else ButtonBehavior.TAP) }
                } catch(error:Exception) { fail("Mapping change rejected: ${error.message}",error);return@setItems }
                canvas.invalidate()
                if(canvas is EditorCanvas) showInspector(canvas)
                if(binding !in setOf("LS","RS")) chooseBehavior(canvas)
            }.setNegativeButton("Cancel",null).create()
        choice.window?.setType(params(1,1).type);dialog=choice
        try { choice.show();choice.window?.setBackgroundDrawable(glass()) } catch(error:Exception) { fail("Binding dialog failed: ${error.message}",error) }
    }
    private fun learnBinding(canvas: View) {
        if (saving || closed) return
        val node=session?.draft?.buttons?.firstOrNull { it.id==selected }
        if (node==null || node.type in setOf(NodeType.JOYSTICK_ZONE,NodeType.CAMERA_DRAG)) {
            Toast.makeText(context,"Select a button target first",Toast.LENGTH_SHORT).show();return
        }
        val choice=AlertDialog.Builder(context).setTitle("Learn ${node.boundKey}")
            .setMessage("Press your controller button or one D-pad direction. Only delivered events can be learned.")
            .setNegativeButton("Cancel",null).create()
        choice.setOnKeyListener { _, _, event ->
            if (!com.example.input.ControllerSourceClassifier.accepts(event.source,event.device?.sources ?: 0)) false
            else {
                if (event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0) {
                    if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN || event.scanCode > 0) {
                        session?.bindObserved(node.id,event)
                        canvas.invalidate();choice.dismiss()
                    } else {
                        Toast.makeText(context,"This event has no usable key or scan code",Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
        }
        choice.window?.setType(params(1,1).type);dialog=choice
        val learnAfter = com.example.input.ControllerInputMonitor.state.value.observedEventSequence
        val hatJob = scope.launch {
            com.example.input.ControllerInputMonitor.state.collect { live ->
                val hat = live.lastHatPress
                if (!closed && !saving && hat != null && hat.sequence > learnAfter) {
                    session?.bind(node.id, hat.binding, node.buttonBehavior)
                    canvas.invalidate();choice.dismiss()
                }
            }
        }
        choice.setOnDismissListener { hatJob.cancel() }
        try { choice.show();choice.window?.setBackgroundDrawable(glass()) }
        catch(error:Exception) { hatJob.cancel();fail("Learn dialog failed: ${error.message}",error) }
    }
    private fun chooseBehavior(canvas: View) {
        val node=session?.draft?.buttons?.firstOrNull { it.id==selected } ?: return
        if(node.type != NodeType.BUTTON) return
        val choice=AlertDialog.Builder(context).setTitle("Touch behavior for ${node.boundKey}")
            .setItems(arrayOf("TAP","HOLD")) { _,index -> session?.bind(node.id,node.boundKey,ButtonBehavior.entries[index]);canvas.invalidate() }.create()
        choice.window?.setType(params(1,1).type);dialog=choice
        try { choice.show();choice.window?.setBackgroundDrawable(glass()) } catch(error:Exception) { fail("Behavior dialog failed: ${error.message}",error) }
    }
    private fun save() {
        if(saving) return
        val edited=try { session?.validated() ?: error("No edit session") } catch(error:Exception) { fail("Profile rejected: ${error.message}",error);return }
        saving=true
        saveJob=scope.launch {
            try {
                withContext(Dispatchers.IO) { ProfilePersistence(ControlystDatabase.getDatabase(context)).save(edited) }
                if(!closed) { saving=false;MappingForegroundService.currentCrosshairConfig.value=edited.crosshair;finishEdit(edited);Toast.makeText(context,"Game layout saved",Toast.LENGTH_SHORT).show() }
            } catch(error:Exception) { if(error is CancellationException) throw error;fail("Mapper save failed: ${error.message}",error) }
            finally { saving=false }
        }
    }
    private fun finishEdit(config: MappingConfig?) {
        if(saving) return
        dialog?.dismiss();dialog=null;remove(editor);editor=null;inspector=null;session=null
        if(config!=null && !closed) MappingRuntimeBridge.arm(config.gamePackage,config)
        if(!closed) show()
    }
    fun onConfigurationChanged(configuration: Configuration) {
        if(editor!=null) {
            saveJob?.cancel();saveJob=null;saving=false
            fail("Screen orientation changed during editing; mapping stopped. Inspect the saved profile before restarting.")
            finishEdit(null)
        }
        else { bubbleObserver?.cancel();bubbleObserver=null;closePanel();remove(bubble);bubble=null;show() }
    }
    fun hide() {
        closed=true;bubbleObserver?.cancel();bubbleObserver=null;saveJob?.cancel();saveJob=null;handler.removeCallbacksAndMessages(null);dialog?.dismiss();dialog=null
        remove(editor);editor=null;closePanel();remove(bubble);bubble=null;session=null;scope.cancel()
    }
    private inner class EditorCanvas : View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private var resizing=false
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if(!nodesVisible) return
            if(snapToGrid) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=density;paint.color=0x2516F1FF
                for(i in 1..19) {
                    canvas.drawLine(width*i/20f,0f,width*i/20f,height.toFloat(),paint)
                    canvas.drawLine(0f,height*i/20f,width.toFloat(),height*i/20f,paint)
                }
            }
            session?.draft?.buttons?.forEach { node ->
                val x=node.xNorm*(width-1);val y=node.yNorm*(height-1)
                val radius=maxOf(18*density,node.radiusNorm*minOf(width,height))
                paint.style=Paint.Style.FILL;paint.color=0x770B2440;canvas.drawCircle(x,y,radius,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=7*density;paint.color=0x3016F1FF;canvas.drawCircle(x,y,radius,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=if(node.id==selected) Color.WHITE else 0xFF16F1FF.toInt();canvas.drawCircle(x,y,radius,paint)
                val label=when(com.example.input.ControllerBindingAliases.canonical(node.boundKey)) {
                    "DPAD_UP" -> "↑"; "DPAD_DOWN" -> "↓"; "DPAD_LEFT" -> "←"; "DPAD_RIGHT" -> "→"
                    else -> node.boundKey
                }
                paint.style=Paint.Style.FILL;paint.textSize=12*density;paint.textAlign=Paint.Align.CENTER;canvas.drawText(label,x,y+4*density,paint)
                if(node.id==selected) {
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=density;paint.color=Color.WHITE
                    canvas.drawRect(x-radius,y-radius,x+radius,y+radius,paint)
                    paint.style=Paint.Style.FILL;paint.color=0xFFB47AFF.toInt()
                    for(sx in listOf(-1,1)) for(sy in listOf(-1,1))
                        canvas.drawRect(x+sx*radius-4*density,y+sy*radius-4*density,x+sx*radius+4*density,y+sy*radius+4*density,paint)
                }
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if(saving) return true
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val previous=session?.draft?.buttons?.firstOrNull { it.id==selected }
                    val radius=previous?.let { maxOf(18*density,it.radiusNorm*minOf(width,height)) }
                    resizing=previous!=null && radius!=null && listOf(-1,1).any { sx -> listOf(-1,1).any { sy ->
                        hypot(event.x-(previous.xNorm*(width-1)+sx*radius),event.y-(previous.yNorm*(height-1)+sy*radius))<22*density
                    } }
                    if(!resizing) selected=session?.draft?.buttons?.minByOrNull { hypot(it.xNorm*(width-1)-event.x,it.yNorm*(height-1)-event.y) }
                        ?.takeIf { hypot(it.xNorm*(width-1)-event.x,it.yNorm*(height-1)-event.y)<40*density }?.id
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> selected?.let { id ->
                    if(resizing) session?.draft?.buttons?.firstOrNull { it.id==id }?.let { node ->
                        val wanted=maxOf(kotlin.math.abs(event.x-node.xNorm*(width-1)),kotlin.math.abs(event.y-node.yNorm*(height-1)))/minOf(width,height)
                        session?.resize(id,wanted-node.radiusNorm)
                    } else session?.move(id,event.x,event.y,width,height,snapToGrid)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> { resizing=false;performClick();showInspector(this) }
                MotionEvent.ACTION_CANCEL -> resizing=false
            };return true
        }
        override fun performClick(): Boolean { super.performClick();return true }
    }
}

/** Connection and foreground observations do not establish delivery to the game. */
internal object MapperPanelStatus {
    fun text(state: MappingRuntimeState): String = buildString {
        append("Mapping: ").append(if (state.armed) "armed" else "stopped")
        append("\nProfile: ").append(state.profileName ?: "none armed")
        append("\nBackend: ").append(state.backend?.title ?: "not selected")
        append("\nBackend connection prepared: ").append(state.backendReady)
        append("\nTarget in foreground: ").append(state.targetForeground)
        state.notice?.let { append("\nNotice: ").append(it) }
        state.error?.let { append("\nError: ").append(it) }
    }
}

internal object OverlayBounds {
    fun position(x: Int,y: Int,windowWidth: Int,windowHeight: Int,screenWidth: Int,screenHeight: Int): Pair<Int,Int> =
        x.coerceIn(0,(screenWidth-windowWidth).coerceAtLeast(0)) to
            y.coerceIn(0,(screenHeight-windowHeight).coerceAtLeast(0))
}
