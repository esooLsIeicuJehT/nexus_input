package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ConfigProfileDao
import com.example.data.dao.GameDao
import com.example.data.dao.MacroDao
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.data.entity.MacroEntity

@Database(
    entities = [GameEntity::class, ConfigProfileEntity::class, MacroEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ControlystDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun configProfileDao(): ConfigProfileDao
    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile
        private var INSTANCE: ControlystDatabase? = null

        fun getDatabase(context: Context): ControlystDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ControlystDatabase::class.java,
                    "controlyst_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
