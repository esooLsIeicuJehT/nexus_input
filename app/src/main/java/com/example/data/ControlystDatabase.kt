package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ConfigProfileDao
import com.example.data.dao.GameDao
import com.example.data.dao.MacroDao
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.data.entity.MacroEntity

@Database(
    entities = [GameEntity::class, ConfigProfileEntity::class, MacroEntity::class, com.example.data.entity.ProfileMigrationEntity::class, com.example.data.entity.MapperStateEntity::class],
    version = 3,
    exportSchema = true
)
abstract class ControlystDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun configProfileDao(): ConfigProfileDao
    abstract fun macroDao(): MacroDao
    abstract fun profileMigrationDao(): com.example.data.dao.ProfileMigrationDao
    abstract fun mapperStateDao(): com.example.data.dao.MapperStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `profile_migrations` (`sourceKey` TEXT NOT NULL, `checksum` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `mapper_state` (`id` INTEGER NOT NULL, `activeProfileId` TEXT, `mappingEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val BUILT_IN_PROFILE_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                BuiltInProfiles.seed(db)
            }
        }

        @Volatile
        private var INSTANCE: ControlystDatabase? = null

        fun getDatabase(context: Context): ControlystDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ControlystDatabase::class.java,
                    "controlyst_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(BUILT_IN_PROFILE_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
