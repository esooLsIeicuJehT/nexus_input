package com.inputmapper.platform.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent, versioned game mapping profiles. */
class GameProfileStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(profile: GameProfile) {
        prefs.edit().putString(profile.profileId, encode(profile).toString()).apply()
    }

    fun load(profileId: String): GameProfile? = prefs.getString(profileId, null)?.let { raw ->
        runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun findByPackage(packageName: String): GameProfile? = list().firstOrNull { it.packageName == packageName }

    fun list(): List<GameProfile> = prefs.all.values.mapNotNull { raw ->
        (raw as? String)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
    }.sortedBy { it.displayName.lowercase() }

    fun remove(profileId: String) {
        prefs.edit().remove(profileId).apply()
        if (activeProfileId() == profileId) setActiveProfile(null)
    }

    fun setActiveProfile(profileId: String?) {
        statePrefs().edit().apply {
            if (profileId == null) remove(KEY_ACTIVE_PROFILE) else putString(KEY_ACTIVE_PROFILE, profileId)
        }.apply()
    }

    fun activeProfileId(): String? = statePrefs().getString(KEY_ACTIVE_PROFILE, null)

    fun activeProfile(): GameProfile? = activeProfileId()?.let(::load)

    fun setMappingEnabled(enabled: Boolean) {
        statePrefs().edit().putBoolean(KEY_MAPPING_ENABLED, enabled).apply()
    }

    fun isMappingEnabled(): Boolean = statePrefs().getBoolean(KEY_MAPPING_ENABLED, false)

    fun create(displayName: String, packageName: String): GameProfile {
        val id = UUID.randomUUID().toString()
        return GameProfile.fresh(id, displayName, packageName).also(::save)
    }

    private fun statePrefs() = context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    private fun encode(profile: GameProfile) = JSONObject().apply {
        put("schema", SCHEMA)
        put("profileId", profile.profileId)
        put("displayName", profile.displayName)
        put("packageName", profile.packageName)
        put("controllerProfileId", profile.controllerProfileId ?: JSONObject.NULL)
        put("preferredBackend", profile.preferredBackend ?: JSONObject.NULL)
        put("savedAtMillis", profile.savedAtMillis)
        put("touchMappings", JSONArray().apply {
            profile.touchMappings.forEach { mapping ->
                put(JSONObject().apply {
                    put("id", mapping.id)
                    put("label", mapping.label)
                    put("keyCode", mapping.input.keyCode)
                    put("scanCode", mapping.input.scanCode)
                    put("action", mapping.action.name)
                    put("xNorm", mapping.xNorm.toDouble())
                    put("yNorm", mapping.yNorm.toDouble())
                    put("slot", mapping.slot)
                })
            }
        })
        put("stickMappings", JSONArray().apply {
            profile.stickMappings.forEach { mapping ->
                put(JSONObject().apply {
                    put("id", mapping.id)
                    put("label", mapping.label)
                    put("action", mapping.action.name)
                    put("axisX", mapping.axisX)
                    put("axisY", mapping.axisY)
                    put("xNorm", mapping.xNorm.toDouble())
                    put("yNorm", mapping.yNorm.toDouble())
                    put("radiusNorm", mapping.radiusNorm.toDouble())
                    put("sensitivity", mapping.sensitivity.toDouble())
                    put("deadzone", mapping.deadzone.toDouble())
                    put("invertY", mapping.invertY)
                    put("slot", mapping.slot)
                })
            }
        })
    }

    private fun decode(json: JSONObject): GameProfile {
        val touches = buildList {
            val array = json.optJSONArray("touchMappings") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    TouchMapping(
                        id = item.getString("id"),
                        label = item.optString("label", "Button"),
                        input = ControllerButtonBinding(item.getInt("keyCode"), item.getInt("scanCode")),
                        action = runCatching { TouchActionType.valueOf(item.getString("action")) }.getOrDefault(TouchActionType.TAP),
                        xNorm = item.getDouble("xNorm").toFloat().coerceIn(0f, 1f),
                        yNorm = item.getDouble("yNorm").toFloat().coerceIn(0f, 1f),
                        slot = item.optInt("slot", i + 2).coerceIn(0, 31)
                    )
                )
            }
        }
        val sticks = buildList {
            val array = json.optJSONArray("stickMappings") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    StickMapping(
                        id = item.getString("id"),
                        label = item.optString("label", "Stick"),
                        action = runCatching { StickActionType.valueOf(item.getString("action")) }.getOrDefault(StickActionType.VIRTUAL_JOYSTICK),
                        axisX = item.getInt("axisX"),
                        axisY = item.getInt("axisY"),
                        xNorm = item.getDouble("xNorm").toFloat().coerceIn(0f, 1f),
                        yNorm = item.getDouble("yNorm").toFloat().coerceIn(0f, 1f),
                        radiusNorm = item.optDouble("radiusNorm", 0.12).toFloat().coerceIn(0.02f, 0.5f),
                        sensitivity = item.optDouble("sensitivity", 1.0).toFloat().coerceIn(0.1f, 5f),
                        deadzone = item.optDouble("deadzone", 0.12).toFloat().coerceIn(0f, 0.9f),
                        invertY = item.optBoolean("invertY", false),
                        slot = item.optInt("slot", i).coerceIn(0, 31)
                    )
                )
            }
        }
        return GameProfile(
            profileId = json.getString("profileId"),
            displayName = json.getString("displayName"),
            packageName = json.getString("packageName"),
            controllerProfileId = json.optString("controllerProfileId").takeIf { it.isNotBlank() && it != "null" },
            preferredBackend = json.optString("preferredBackend").takeIf { it.isNotBlank() && it != "null" },
            savedAtMillis = json.optLong("savedAtMillis", 0L),
            touchMappings = touches,
            stickMappings = sticks
        )
    }

    companion object {
        private const val PREFS = "nexus_game_profiles_v1"
        private const val STATE_PREFS = "nexus_mapper_state"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_MAPPING_ENABLED = "mapping_enabled"
        private const val SCHEMA = 1
    }
}
