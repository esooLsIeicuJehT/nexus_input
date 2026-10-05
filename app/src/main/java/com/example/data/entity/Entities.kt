package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AntiCheatSeverity

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val iconUri: String = "",
    val isGameTag: Boolean = true,
    val antiCheatSeverity: AntiCheatSeverity = AntiCheatSeverity.SAFE,
    val antiCheatNotes: String = "Game compatibility has not been assessed",
    val playTimeMinutes: Long = 0,
    val lastPlayedTimestamp: Long = 0
)

@Entity(tableName = "config_profiles")
data class ConfigProfileEntity(
    @PrimaryKey
    val id: String,
    val gamePackage: String,
    val profileName: String,
    val jsonBlob: String,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val author: String = "Local user",
    val isOfficialVerified: Boolean = false,
    val rating: Float = 0f,
    val downloads: Int = 0
)

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gamePackage: String,
    val name: String,
    val triggerKey: String,
    val stepsJson: String
)

@Entity(tableName = "profile_migrations")
data class ProfileMigrationEntity(@PrimaryKey val sourceKey: String, val checksum: String, val importedAt: Long)

@Entity(tableName = "mapper_state")
data class MapperStateEntity(@PrimaryKey val id: Int = 1, val activeProfileId: String?, val mappingEnabled: Boolean)
