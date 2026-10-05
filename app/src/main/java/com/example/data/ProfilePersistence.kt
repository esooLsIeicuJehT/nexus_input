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
        val config = ControlystRepository.deserializeJsonToConfig(entity.jsonBlob)
        val errors = ProfileValidator.errors(config, gamePackage, id)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return config
    }
}
