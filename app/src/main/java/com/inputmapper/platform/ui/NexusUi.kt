package com.inputmapper.platform.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object NexusUi {
    val bg = Color.rgb(5, 11, 20)
    val panel = Color.rgb(12, 21, 34)
    val panelAlt = Color.rgb(18, 29, 45)
    val cyan = Color.rgb(0, 210, 255)
    val violet = Color.rgb(126, 87, 255)
    val green = Color.rgb(51, 214, 122)
    val amber = Color.rgb(255, 190, 74)
    val red = Color.rgb(255, 94, 116)
    val text = Color.rgb(241, 247, 255)
    val muted = Color.rgb(151, 169, 191)
    val stroke = Color.rgb(31, 56, 82)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun rounded(context: Context, color: Int, radiusDp: Int = 18, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, 1), strokeColor)
        }

    fun title(context: Context, value: String, size: Float = 30f): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(NexusUi.text)
        setTypeface(typeface, Typeface.BOLD)
    }

    fun eyebrow(context: Context, value: String): TextView = TextView(context).apply {
        text = value.uppercase()
        textSize = 11f
        letterSpacing = 0.14f
        setTextColor(NexusUi.cyan)
        setTypeface(typeface, Typeface.BOLD)
    }

    fun body(context: Context, value: String, size: Float = 14f): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(NexusUi.muted)
    }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(context, panel, 20, stroke)
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
        layoutParams = marginParams(context, top = 12)
    }

    fun metric(context: Context, label: String, value: String, valueColor: Int = text): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(NexusUi.muted)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(valueColor)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
            })
        }

    fun primaryButton(context: Context, label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.BLACK)
        background = rounded(context, cyan, 18)
        setOnClickListener { onClick() }
        layoutParams = marginParams(context, top = 10, height = 52)
    }

    fun secondaryButton(context: Context, label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(NexusUi.text)
        background = rounded(context, panelAlt, 18, stroke)
        setOnClickListener { onClick() }
        layoutParams = marginParams(context, top = 10, height = 52)
    }

    fun dangerButton(context: Context, label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(NexusUi.text)
        background = rounded(context, Color.rgb(64, 25, 34), 18, red)
        setOnClickListener { onClick() }
        layoutParams = marginParams(context, top = 10, height = 52)
    }

    fun marginParams(context: Context, top: Int = 0, bottom: Int = 0, height: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height?.let { dp(context, it) } ?: ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, top)
            bottomMargin = dp(context, bottom)
        }

    fun page(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        val horizontal = dp(context, 20)
        val topBase = dp(context, 18)
        val bottomBase = dp(context, 24)
        setPadding(horizontal, topBase, horizontal, bottomBase)
        setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(
                horizontal + bars.left,
                topBase + bars.top,
                horizontal + bars.right,
                bottomBase + bars.bottom
            )
            insets
        }
        post { requestApplyInsets() }
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(stroke)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)).apply {
            topMargin = dp(context, 12)
            bottomMargin = dp(context, 12)
        }
    }
}
