package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.data.entity.MacroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY lastPlayedTimestamp DESC, displayName ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE packageName = :packageName LIMIT 1")
    suspend fun getGame(packageName: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("DELETE FROM games WHERE packageName = :packageName")
    suspend fun deleteGame(packageName: String)
}

@Dao
interface ConfigProfileDao {
    @Query("SELECT * FROM config_profiles WHERE gamePackage = :gamePackage ORDER BY isDefault DESC, updatedAt DESC")
    fun getProfilesForGame(gamePackage: String): Flow<List<ConfigProfileEntity>>

    @Query("SELECT * FROM config_profiles ORDER BY updatedAt DESC")
    fun getAllProfiles(): Flow<List<ConfigProfileEntity>>

    @Query("SELECT * FROM config_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ConfigProfileEntity?

    @Query("SELECT * FROM config_profiles WHERE gamePackage = :packageName ORDER BY isDefault DESC, updatedAt DESC LIMIT 1")
    suspend fun getDefaultForGame(packageName: String): ConfigProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ConfigProfileEntity)

    @Query("UPDATE config_profiles SET isDefault = 0 WHERE gamePackage = :gamePackage")
    suspend fun clearDefaults(gamePackage: String)

    @Query("DELETE FROM config_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros WHERE gamePackage = :gamePackage")
    fun getMacrosForGame(gamePackage: String): Flow<List<MacroEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacro(id: Long)
}

@Dao
interface ProfileMigrationDao {
    @Query("SELECT * FROM profile_migrations WHERE sourceKey = :key")
    suspend fun get(key: String): com.example.data.entity.ProfileMigrationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: com.example.data.entity.ProfileMigrationEntity)
}

@Dao
interface MapperStateDao {
    @Query("SELECT * FROM mapper_state WHERE id = 1")
    suspend fun get(): com.example.data.entity.MapperStateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: com.example.data.entity.MapperStateEntity)
}
