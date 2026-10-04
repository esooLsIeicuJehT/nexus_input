package com.inputmapper.platform.game

import com.inputmapper.platform.profile.AxisCalibration
import com.inputmapper.platform.profile.ControllerProfile
import kotlin.math.abs
import kotlin.math.max

class ControllerNormalizer(private val profile: ControllerProfile?) {
    private val axes = profile?.axes?.associateBy { it.axis }.orEmpty()

    fun normalize(axis: Int, raw: Float, requestedDeadzone: Float): Float {
        val calibration = axes[axis]
        val center = calibration?.center ?: 0f
        val min = calibration?.observedMin?.takeIf { it < center } ?: calibration?.declaredMin ?: -1f
        val max = calibration?.observedMax?.takeIf { it > center } ?: calibration?.declaredMax ?: 1f
        val span = if (raw >= center) max - center else center - min
        if (span <= 0.0001f) return 0f
        var normalized = ((raw - center) / span).coerceIn(-1f, 1f)
        val hardwareDeadzone = calibration?.flat?.let { flat ->
            val declaredSpan = max(abs((calibration.declaredMax - calibration.declaredMin) / 2f), 0.0001f)
            (flat / declaredSpan).coerceIn(0f, 0.9f)
        } ?: 0f
        val deadzone = max(requestedDeadzone, hardwareDeadzone)
        if (abs(normalized) <= deadzone) return 0f
        normalized = ((abs(normalized) - deadzone) / (1f - deadzone)).coerceIn(0f, 1f) * if (normalized < 0f) -1f else 1f
        return normalized
    }
}
