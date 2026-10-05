package com.example.data

import android.content.Context
import android.view.KeyEvent
import androidx.room.withTransaction
import com.example.data.entity.*
import com.example.input.ControllerBindingAliases
import com.example.input.ProfileValidator
import com.example.model.*
import org.json.JSONObject
import java.security.MessageDigest

/** The schema-1 contract preserved at f8fd74b5686c453af53bd09f125dfa9fe7fbe237. */
object LegacyProfileMigration {
    const val PREFERENCES = "nexus_game_profiles_v1"
    const val STATE_PREFERENCES = "nexus_mapper_state"
    private const val STATE_JOURNAL = "state:nexus_mapper_state"
    data class Report(val imported: Int, val errors: List<String>)

    suspend fun migrate(context: Context, db: ControlystDatabase): Report {
        var imported = 0
        val errors = mutableListOf<String>()
        val values = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).all
        for ((key, value) in values) {
            try {
                require(value is String) { "Expected one schema-1 JSON string per profile ID" }
                val hash = checksum(value)
                db.withTransaction {
                    val journalKey = "profile:$key"
                    if (db.profileMigrationDao().get(journalKey)?.checksum == hash) return@withTransaction
                    val config = parse(value)
                    require(config.id == key) { "Preference key does not match profileId" }
                    val problems = ProfileValidator.errors(config, expectedId = key, requireBindings = false)
                    require(problems.isEmpty()) { problems.joinToString("; ") }
                    val existing = db.configProfileDao().getProfileById(config.id)
                    if (existing != null) {
                        require(ControlystRepository.deserializeJsonToConfig(existing.jsonBlob) == config) {
                            "Room already contains a different profile ${config.id}; resolve the collision explicitly"
                        }
                    } else {
                        db.configProfileDao().insertProfile(ConfigProfileEntity(config.id, config.gamePackage,
                            config.profileName, ControlystRepository.serializeConfigToJson(config),
                            updatedAt = config.lastUpdated))
                    }
                    if (db.gameDao().getGame(config.gamePackage) == null) {
                        db.gameDao().insertGame(GameEntity(config.gamePackage, config.gameTitle))
                    }
                    db.profileMigrationDao().insert(ProfileMigrationEntity(journalKey, hash, System.currentTimeMillis()))
                    imported++
                }
            } catch (error: Exception) {
                recordError(errors, "profile '$key'", error)
            }
        }
        val state = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE).all
        if (state.isNotEmpty()) {
            try {
                db.withTransaction {
                    // Import state only once; subsequent v1 edits must never be overwritten by retained legacy prefs.
                    if (db.profileMigrationDao().get(STATE_JOURNAL) != null) return@withTransaction
                    val active = state["active_profile"]
                    val enabled = state["mapping_enabled"]
                    require(active == null || active is String) { "active_profile must be a string" }
                    require(enabled == null || enabled is Boolean) { "mapping_enabled must be a boolean" }
                    val activeId = active as String?
                    if (activeId != null) {
                        require(db.profileMigrationDao().get("profile:$activeId") != null &&
                            db.configProfileDao().getProfileById(activeId) != null) { "Active legacy profile has not migrated successfully" }
                    }
                    require(db.mapperStateDao().get() == null) { "Room already contains mapper state; resolve the collision explicitly" }
                    db.mapperStateDao().save(MapperStateEntity(activeProfileId = activeId, mappingEnabled = enabled as? Boolean ?: false))
                    val snapshot = JSONObject().put("active_profile", activeId ?: JSONObject.NULL)
                        .put("mapping_enabled", enabled ?: false).toString()
                    db.profileMigrationDao().insert(ProfileMigrationEntity(STATE_JOURNAL, checksum(snapshot), System.currentTimeMillis()))
                }
            } catch (error: Exception) {
                recordError(errors, "mapper state", error)
            }
        }
        return Report(imported, errors)
    }

    private fun recordError(errors: MutableList<String>, source: String, error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        val message = "Legacy migration $source failed: ${error.message}. Original preferences retained."
        android.util.Log.e("NexusMigration", message, error)
        errors += message
    }

    private fun checksum(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    internal fun parse(text: String): MappingConfig {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1) { "Unsupported legacy schema" }
        fun nullableString(key: String): String? {
            require(json.has(key)) { "Missing legacy $key" }
            return if (json.isNull(key)) null else json.getString(key).also {
            require(it.isNotBlank()) { "$key cannot be blank" }
        }
        }
        val backend = nullableString("preferredBackend")?.let {
            when (it) {
                "KERNEL_SU" -> PrivilegeMethod.KERNELSU
                "SHIZUKU" -> PrivilegeMethod.SHIZUKU
                "MAGISK" -> PrivilegeMethod.MAGISK
                "APATCH" -> PrivilegeMethod.APATCH // retained, but backend selection remains fail-closed
                "ACCESSIBILITY" -> PrivilegeMethod.ACCESSIBILITY
                else -> error("Unknown legacy backend '$it'")
            }
        }
        val touches = json.getJSONArray("touchMappings")
        val sticks = json.getJSONArray("stickMappings")
        val nodes = (0 until touches.length()).map { i -> touches.getJSONObject(i).let { node ->
            val key = node.getInt("keyCode")
            val scan = node.getInt("scanCode")
            val label = ControllerBindingAliases.forKeyCode(key).firstOrNull()
                ?: if (key == KeyEvent.KEYCODE_UNKNOWN) "SCAN_$scan" else "KEY_$key"
            MappingNode(id = node.getString("id"), label = node.getString("label"), boundKey = label,
                xNorm = node.getDouble("xNorm").toFloat(), yNorm = node.getDouble("yNorm").toFloat(),
                inputKeyCode = key, inputScanCode = scan, touchSlot = node.getInt("slot"),
                buttonBehavior = ButtonBehavior.valueOf(node.getString("action")))
        } } + (0 until sticks.length()).map { i -> sticks.getJSONObject(i).let { node ->
            val type = when (node.getString("action")) {
                "VIRTUAL_JOYSTICK" -> NodeType.JOYSTICK_ZONE
                "CAMERA_DRAG" -> NodeType.CAMERA_DRAG
                else -> error("Unknown legacy stick action")
            }
            MappingNode(id = node.getString("id"), label = node.getString("label"),
                boundKey = if (type == NodeType.JOYSTICK_ZONE) "LS" else "RS", type = type,
                xNorm = node.getDouble("xNorm").toFloat(), yNorm = node.getDouble("yNorm").toFloat(),
                radiusNorm = node.getDouble("radiusNorm").toFloat(), sensitivity = node.getDouble("sensitivity").toFloat(),
                deadzoneInner = node.getDouble("deadzone").toFloat(), deadzoneOuter = 1f,
                axisX = node.getInt("axisX"), axisY = node.getInt("axisY"), invertY = node.getBoolean("invertY"),
                touchSlot = node.getInt("slot"))
        } }
        return MappingConfig(id = json.getString("profileId"), profileName = json.getString("displayName"),
            gamePackage = json.getString("packageName"), gameTitle = json.getString("displayName"),
            controllerProfileId = nullableString("controllerProfileId"), preferredBackend = backend,
            buttons = nodes, lastUpdated = json.getLong("savedAtMillis"))
    }
}
