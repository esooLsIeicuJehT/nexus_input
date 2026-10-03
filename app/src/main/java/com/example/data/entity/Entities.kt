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
    val antiCheatNotes: String = "No aggressive injection anti-cheat detected",
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
    val author: String = "Controlyst Community",
    val isOfficialVerified: Boolean = false,
    val rating: Float = 4.8f,
    val downloads: Int = 120
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
