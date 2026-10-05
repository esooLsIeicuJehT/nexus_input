package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.data.entity.ProfileMigrationEntity
import com.example.data.entity.ConfigProfileEntity
import com.example.input.ProfileValidator
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Imports recognized legacy JSON atomically. Unknown formats remain intact and observable. */
object LegacyProfileMigration {
    const val PREFERENCES = "nexus_game_profiles_v1"
    data class Report(val imported: Int, val errors: List<String>)

    suspend fun migrate(context: Context, db: ControlystDatabase): Report {
        var imported = 0
        val errors = mutableListOf<String>()
        val values = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).all
        for ((key, value) in values) {
            // Booleans and numbers can be preference metadata; no source entry is deleted.
            if (value !is String && value !is Set<*>) continue
            val texts = if (value is String) listOf(value) else (value as Set<*>).map { it as? String ?: error("Non-string legacy profile") }.sorted()
            val snapshot = JSONArray(texts).toString()
            val hash = MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()).joinToString("") { "%02x".format(it) }
            try {
                db.withTransaction {
                    if (db.profileMigrationDao().get(key)?.checksum == hash) return@withTransaction
                    val configs = texts.flatMap { parse(it) }
                    require(configs.map { it.id }.distinct().size == configs.size) { "Duplicate legacy profile IDs" }
                    configs.forEach { config ->
                        val problems = ProfileValidator.errors(config, requireBindings = false)
                        require(problems.isEmpty()) { problems.joinToString("; ") }
                        val json = ControlystRepository.serializeConfigToJson(config)
                        val existing = db.configProfileDao().getProfileById(config.id)
                        if (existing != null) {
                            require(ControlystRepository.deserializeJsonToConfig(existing.jsonBlob) == config) {
                                "Room already contains a different profile ${config.id}; legacy source retained for review"
                            }
                        } else {
                            db.configProfileDao().insertProfile(ConfigProfileEntity(config.id, config.gamePackage,
                                config.profileName, json, updatedAt = config.lastUpdated, author = config.author,
                                isOfficialVerified = false, rating = 0f, downloads = 0))
                        }
                    }
                    db.profileMigrationDao().insert(ProfileMigrationEntity(key, hash, System.currentTimeMillis()))
                    imported += configs.size
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = "Legacy migration '$key' failed: ${error.message}. Original preferences retained."
                android.util.Log.e("NexusMigration", message, error)
                errors += message
            }
        }
        return Report(imported, errors)
    }

    internal fun parse(text: String): List<com.example.model.MappingConfig> {
        val trimmed = text.trim()
        val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
            val obj = JSONObject(trimmed)
            if (obj.has("profiles")) obj.getJSONArray("profiles") else JSONArray().put(obj)
        }
        require(array.length() > 0) { "Legacy profile collection is empty or unrecognized" }
        return (0 until array.length()).map {
            ControlystRepository.deserializeJsonToConfig(array.getJSONObject(it).toString())
        }
    }
}
