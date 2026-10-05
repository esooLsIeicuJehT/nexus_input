package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class MigrationTest {
    @Test fun legacyImportIsIdempotentAndUnknownDataIsRetained() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ControlystDatabase::class.java).build()
        val prefs=context.getSharedPreferences(LegacyProfileMigration.PREFERENCES,0)
        prefs.edit().clear().commit()
        val config=MappingConfig(id="legacy",profileName="Saved",gamePackage="com.test.game")
        prefs.edit().putString("profiles",ControlystRepository.serializeConfigToJson(config)).commit()
        assertEquals(1,LegacyProfileMigration.migrate(context,db).imported)
        assertEquals(0,LegacyProfileMigration.migrate(context,db).imported)
        assertEquals(config,ControlystRepository.deserializeJsonToConfig(db.configProfileDao().getProfileById("legacy")!!.jsonBlob))
        prefs.edit().putString("unknown","{\"legacyOtherShape\":true}").commit()
        assertEquals(1,LegacyProfileMigration.migrate(context,db).errors.size)
        assertTrue(prefs.contains("unknown")); assertTrue(prefs.contains("profiles"))
        db.close()
    }
    @Test fun roomOneToTwoPreservesExistingRows() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-v1-test.db";context.deleteDatabase(name)
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object: SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE games (packageName TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, iconUri TEXT NOT NULL, isGameTag INTEGER NOT NULL, antiCheatSeverity TEXT NOT NULL, antiCheatNotes TEXT NOT NULL, playTimeMinutes INTEGER NOT NULL, lastPlayedTimestamp INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE config_profiles (id TEXT NOT NULL PRIMARY KEY, gamePackage TEXT NOT NULL, profileName TEXT NOT NULL, jsonBlob TEXT NOT NULL, isDefault INTEGER NOT NULL, updatedAt INTEGER NOT NULL, author TEXT NOT NULL, isOfficialVerified INTEGER NOT NULL, rating REAL NOT NULL, downloads INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE macros (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, gamePackage TEXT NOT NULL, name TEXT NOT NULL, triggerKey TEXT NOT NULL, stepsJson TEXT NOT NULL)")
                    db.execSQL("INSERT INTO games VALUES ('com.saved.game','Saved','','1','SAFE','Unknown',123,456)")
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) { error("Fixture must stay at v1") }
            }).build())
        helper.writableDatabase;helper.close()
        val db=Room.databaseBuilder(context,ControlystDatabase::class.java,name)
            .addMigrations(ControlystDatabase.MIGRATION_1_2).build()
        assertEquals(123L,db.gameDao().getGame("com.saved.game")!!.playTimeMinutes)
        assertNull(db.profileMigrationDao().get("none"))
        db.close();context.deleteDatabase(name)
    }
}
