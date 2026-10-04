package com.inputmapper.platform.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.inputmapper.platform.R

/** Branded launch screen. The design-pack phone mockup is shown FIT_CENTER, never stretched/cropped. */
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(NexusUi.bg) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(NexusUi.dp(this@SplashActivity, 24), NexusUi.dp(this@SplashActivity, 36), NexusUi.dp(this@SplashActivity, 24), NexusUi.dp(this@SplashActivity, 36))
        }
        val image = ImageView(this).apply {
            setImageResource(R.drawable.nexus_input_splash)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "NEXUS INPUT"
        }
        column.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(TextView(this).apply {
            text = "NEXUS INPUT"
            textSize = 22f
            setTextColor(NexusUi.text)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            text = "PLAY YOUR WAY"
            textSize = 11f
            setTextColor(NexusUi.cyan)
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = NexusUi.dp(this@SplashActivity, 8)
        })
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180L).start()
        root.postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }, 700L)
    }
}
