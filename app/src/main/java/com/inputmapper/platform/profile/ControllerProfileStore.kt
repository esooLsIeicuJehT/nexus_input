package com.inputmapper.platform.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** App-private persistent controller calibration store with backward compatibility for 0.3 profiles. */
class ControllerProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(profile: ControllerProfile) {
        prefs.edit().putString(profile.profileId, encode(profile).toString()).apply()
    }

    fun load(profileId: String): ControllerProfile? = prefs.getString(profileId, null)?.let {
        runCatching { decode(JSONObject(it)) }.getOrNull()
    }

    fun list(): List<ControllerProfile> = prefs.all.values.mapNotNull { raw ->
        (raw as? String)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
    }.sortedByDescending { it.savedAtMillis }

    fun remove(profileId: String) {
        prefs.edit().remove(profileId).apply()
    }

    companion object {
        private const val PREFS = "gamepad_pro_controller_profiles"

        fun stableProfileId(vendorId: Int, productId: Int, descriptor: String): String {
            val safeDescriptor = descriptor.ifBlank { "no-descriptor" }
            return "%04x:%04x:%s".format(vendorId, productId, safeDescriptor)
        }

        private fun encode(profile: ControllerProfile): JSONObject = JSONObject().apply {
            put("profileId", profile.profileId)
            put("deviceName", profile.deviceName)
            put("descriptor", profile.descriptor)
            put("vendorId", profile.vendorId)
            put("productId", profile.productId)
            put("savedAtMillis", profile.savedAtMillis)
            put("axes", JSONArray().apply {
                profile.axes.forEach { axis ->
                    put(JSONObject().apply {
                        put("axis", axis.axis)
                        put("declaredMin", axis.declaredMin.toDouble())
                        put("declaredMax", axis.declaredMax.toDouble())
                        put("flat", axis.flat.toDouble())
                        put("fuzz", axis.fuzz.toDouble())
                        put("resolution", axis.resolution.toDouble())
                        put("center", axis.center.toDouble())
                        put("observedMin", axis.observedMin.toDouble())
                        put("observedMax", axis.observedMax.toDouble())
                    })
                }
            })
            put("buttons", JSONArray().apply {
                profile.buttons.sortedWith(compareBy<ButtonCalibration> { it.keyCode }.thenBy { it.scanCode }).forEach { button ->
                    put(JSONObject().apply {
                        put("keyCode", button.keyCode)
                        put("scanCode", button.scanCode)
                    })
                }
            })
        }

        private fun decode(json: JSONObject): ControllerProfile {
            val axesJson = json.getJSONArray("axes")
            val axes = buildList {
                for (i in 0 until axesJson.length()) {
                    val item = axesJson.getJSONObject(i)
                    add(
                        AxisCalibration(
                            axis = item.getInt("axis"),
                            declaredMin = item.getDouble("declaredMin").toFloat(),
                            declaredMax = item.getDouble("declaredMax").toFloat(),
                            flat = item.getDouble("flat").toFloat(),
                            fuzz = item.getDouble("fuzz").toFloat(),
                            resolution = item.getDouble("resolution").toFloat(),
                            center = item.getDouble("center").toFloat(),
                            observedMin = item.getDouble("observedMin").toFloat(),
                            observedMax = item.getDouble("observedMax").toFloat()
                        )
                    )
                }
            }

            val buttonsRaw = json.get("buttons")
            val buttons = when (buttonsRaw) {
                is JSONArray -> buildList {
                    for (i in 0 until buttonsRaw.length()) {
                        val item = buttonsRaw.getJSONObject(i)
                        add(ButtonCalibration(item.getInt("keyCode"), item.getInt("scanCode")))
                    }
                }
                is JSONObject -> buildList {
                    // 0.3.x used keyCode -> scanCode object storage. Preserve those profiles.
                    val keys = buttonsRaw.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        add(ButtonCalibration(key.toInt(), buttonsRaw.getInt(key)))
                    }
                }
                else -> emptyList()
            }

            return ControllerProfile(
                profileId = json.getString("profileId"),
                deviceName = json.getString("deviceName"),
                descriptor = json.getString("descriptor"),
                vendorId = json.getInt("vendorId"),
                productId = json.getInt("productId"),
                savedAtMillis = json.getLong("savedAtMillis"),
                axes = axes,
                buttons = buttons
            )
        }
    }
}
