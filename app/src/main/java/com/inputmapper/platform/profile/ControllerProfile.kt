package com.inputmapper.platform.profile

data class AxisCalibration(
    val axis: Int,
    val declaredMin: Float,
    val declaredMax: Float,
    val flat: Float,
    val fuzz: Float,
    val resolution: Float,
    val center: Float,
    val observedMin: Float,
    val observedMax: Float
)

data class ButtonCalibration(
    val keyCode: Int,
    val scanCode: Int
)

data class ControllerProfile(
    val profileId: String,
    val deviceName: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val savedAtMillis: Long,
    val axes: List<AxisCalibration>,
    /** Preserve both Android keyCode and raw scanCode so KEYCODE_UNKNOWN buttons stay distinct. */
    val buttons: List<ButtonCalibration>
)
