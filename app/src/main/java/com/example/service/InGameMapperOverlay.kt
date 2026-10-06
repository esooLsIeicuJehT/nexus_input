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
    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(0xFF183247.toInt())
        setOnClickListener { action() }
    }

    fun show() {
        if (closed || bubble != null) return
        if (!Settings.canDrawOverlays(context)) { fail("Floating mapper requires Android overlay permission");return }
        val size = (52*density).roundToInt()
        val view = TextView(context).apply {
            text="N";textSize=22f;gravity=Gravity.CENTER;setTextColor(0xFF00D9EE.toInt())
            contentDescription="NEXUS INPUT floating controls: tap for mapper, long-press to panic"
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(0xFF071827.toInt());setStroke((2*density).roundToInt(),0xFF00D9EE.toInt()) }
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
            view.setTextColor(if(state.backendReady) 0xFF20D5A4.toInt() else if(state.error!=null) 0xFFFF6588.toInt() else 0xFF00D9EE.toInt())
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
            orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8);setBackgroundColor(0xF5071827.toInt())
            addView(TextView(context).apply { text="NEXUS INPUT";setTextColor(Color.WHITE) })
            addView(status)
            addView(button("Edit game layout") { beginEdit() })
            addView(button("Panic release") { PanicKillSwitch.triggerPanic(context,"Floating controls") })
            addView(button("Close controls") { closePanel() })
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
            session=MapperEditSession(config);selected=null;openEditor()
        } }
    }

    private fun openEditor() {
        bubbleObserver?.cancel();bubbleObserver=null
        remove(bubble);bubble=null
        val root=FrameLayout(context)
        val canvas=EditorCanvas()
        root.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        val bar=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL;setBackgroundColor(0xEC071827.toInt())
            addView(button("Add") { chooseBinding(true,canvas) })
            addView(button("Bind") { chooseBinding(false,canvas) })
            addView(button("Delete") { selected?.let { session?.remove(it);selected=null;canvas.invalidate() } })
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
            setTextColor(Color.WHITE);setBackgroundColor(0xDC071827.toInt());textSize=11f
        },FrameLayout.LayoutParams(-1,(26*density).roundToInt()).apply {
            gravity=Gravity.TOP
            topMargin=safeInsets.first
        })
        try { wm.addView(root,params(-1,-1));editor=root } catch(error:Exception) { fail("In-game mapper window failed: ${error.message}",error);finishEdit(null) }
    }

    private fun chooseBinding(add: Boolean, canvas: View) {
        if(saving || (!add && selected==null)) { Toast.makeText(context,"Select a binding first",Toast.LENGTH_SHORT).show();return }
        val keys=arrayOf("A","B","X","Y","LB","RB","LT","RT","DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT","L3","R3","START","SELECT","LS","RS")
        val choice=AlertDialog.Builder(context).setTitle(if(add) "Add physical input" else "Bind physical input")
            .setItems(keys) { _, index ->
                val binding=keys[index]
                if(add) selected=session?.add(binding)
                else selected?.let { session?.bind(it,binding,if(binding in setOf("LT","RT")) ButtonBehavior.HOLD else ButtonBehavior.TAP) }
                canvas.invalidate()
                if(binding !in setOf("LS","RS")) chooseBehavior(canvas)
            }.setNegativeButton("Cancel",null).create()
        choice.window?.setType(params(1,1).type);dialog=choice
        try { choice.show() } catch(error:Exception) { fail("Binding dialog failed: ${error.message}",error) }
    }
    private fun chooseBehavior(canvas: View) {
        val node=session?.draft?.buttons?.firstOrNull { it.id==selected } ?: return
        val choice=AlertDialog.Builder(context).setTitle("Touch behavior for ${node.boundKey}")
            .setItems(arrayOf("TAP","HOLD")) { _,index -> session?.bind(node.id,node.boundKey,ButtonBehavior.entries[index]);canvas.invalidate() }.create()
        choice.window?.setType(params(1,1).type);dialog=choice
        try { choice.show() } catch(error:Exception) { fail("Behavior dialog failed: ${error.message}",error) }
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
        dialog?.dismiss();dialog=null;remove(editor);editor=null;session=null
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
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            session?.draft?.buttons?.forEach { node ->
                val x=node.xNorm*(width-1);val y=node.yNorm*(height-1)
                val radius=maxOf(18*density,node.radiusNorm*minOf(width,height))
                paint.style=Paint.Style.FILL;paint.color=0x770B2440;canvas.drawCircle(x,y,radius,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=if(node.id==selected) Color.WHITE else 0xFF00D9EE.toInt();canvas.drawCircle(x,y,radius,paint)
                paint.style=Paint.Style.FILL;paint.textSize=12*density;paint.textAlign=Paint.Align.CENTER;canvas.drawText(node.boundKey,x,y+4*density,paint)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if(saving) return true
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { selected=session?.draft?.buttons?.minByOrNull { hypot(it.xNorm*(width-1)-event.x,it.yNorm*(height-1)-event.y) }
                    ?.takeIf { hypot(it.xNorm*(width-1)-event.x,it.yNorm*(height-1)-event.y)<40*density }?.id;invalidate() }
                MotionEvent.ACTION_MOVE -> selected?.let { session?.move(it,event.x,event.y,width,height);invalidate() }
                MotionEvent.ACTION_UP -> performClick()
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
