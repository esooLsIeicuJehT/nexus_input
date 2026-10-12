package com.example.data

import androidx.room.withTransaction
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.MapperStateEntity
import com.example.input.ProfileValidator
import com.example.model.MappingConfig

/** One transaction for a saved profile, its default selection and current mapper state. */
class ProfilePersistence(private val db: ControlystDatabase) {
    suspend fun save(config: MappingConfig) {
        val errors = ProfileValidator.errors(config, requireBindings = false)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        db.withTransaction {
            if(db.gameDao().getGame(config.gamePackage)==null) db.gameDao().insertGame(com.example.data.entity.GameEntity(config.gamePackage,config.gameTitle.ifBlank { config.gamePackage }))
            db.configProfileDao().clearDefaults(config.gamePackage)
            db.configProfileDao().insertProfile(ConfigProfileEntity(config.id, config.gamePackage,
                config.profileName, ControlystRepository.serializeConfigToJson(config), isDefault = true,
                updatedAt = config.lastUpdated, author = config.author))
            val enabled = db.mapperStateDao().get()?.mappingEnabled ?: false
            db.mapperStateDao().save(MapperStateEntity(activeProfileId = config.id, mappingEnabled = enabled))
        }
    }

    suspend fun load(id: String, gamePackage: String): MappingConfig {
        val entity = db.configProfileDao().getProfileById(id) ?: error("Profile '$id' is not saved")
        require(entity.gamePackage == gamePackage) { "Stored profile targets a different game" }
        return decode(entity, requireBindings = true)
    }

    /** Selecting an existing row is acknowledged only after the state/default write commits. */
    suspend fun select(id: String, expectedGamePackage: String? = null): MappingConfig = db.withTransaction {
        val entity = db.configProfileDao().getProfileById(id) ?: error("Profile '$id' is not saved")
        require(expectedGamePackage == null || entity.gamePackage == expectedGamePackage) {
            "Stored profile targets a different game"
        }
        val config = decode(entity, requireBindings = false)
        save(config)
        config
    }

    /** A local copy does not silently change the default or the selected mapper profile. */
    suspend fun clone(id: String, gamePackage: String, name: String): MappingConfig = db.withTransaction {
        require(name.isNotBlank()) { "Profile name is required" }
        val entity = db.configProfileDao().getProfileById(id) ?: error("Profile '$id' is not saved")
        require(entity.gamePackage == gamePackage) { "Stored profile targets a different game" }
        val source = decode(entity, requireBindings = false)
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            profileName = name.trim(),
            author = "Local user",
            isOfficialVerified = false,
            downloadCount = 0,
            rating = 0f,
            lastUpdated = System.currentTimeMillis()
        )
        val errors = ProfileValidator.errors(copy, gamePackage, copy.id, requireBindings = false)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        // Never use REPLACE on a UUID collision, even though it is extremely unlikely.
        check(db.configProfileDao().getProfileById(copy.id) == null) { "New profile ID already exists" }
        db.configProfileDao().insertProfile(ConfigProfileEntity(
            id = copy.id, gamePackage = copy.gamePackage, profileName = copy.profileName,
            jsonBlob = ControlystRepository.serializeConfigToJson(copy), isDefault = false,
            updatedAt = copy.lastUpdated, author = copy.author
        ))
        copy
    }

    /** The UI/runtime must refuse this operation until injection has stopped. */
    suspend fun delete(id: String, gamePackage: String) = db.withTransaction {
        val entity = db.configProfileDao().getProfileById(id) ?: error("Profile '$id' is not saved")
        require(entity.gamePackage == gamePackage) { "Stored profile targets a different game" }
        val state = db.mapperStateDao().get()
        db.configProfileDao().deleteProfile(id)
        if (state?.activeProfileId == id) {
            db.mapperStateDao().save(state.copy(activeProfileId = null, mappingEnabled = false))
        }
    }

    private fun decode(entity: ConfigProfileEntity, requireBindings: Boolean): MappingConfig {
        val config = ControlystRepository.deserializeJsonToConfig(entity.jsonBlob)
        val errors = ProfileValidator.errors(config, entity.gamePackage, entity.id, requireBindings)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return config
    }
}
