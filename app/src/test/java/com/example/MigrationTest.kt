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
    // Fixture follows GameProfileStore.encode at the preserved 0.6.2 commit, without v1 coercions.
    private fun legacy(id: String = "legacy") = org.json.JSONObject()
        .put("schema",1).put("profileId",id).put("displayName","Saved game")
        .put("packageName","com.test.game").put("controllerProfileId","physical-controller")
        .put("preferredBackend","KERNEL_SU").put("savedAtMillis",123456L)
        .put("touchMappings",org.json.JSONArray().put(org.json.JSONObject()
            .put("id","fire").put("label","Fire").put("keyCode",104).put("scanCode",0)
            .put("action","HOLD").put("xNorm",.7).put("yNorm",.8).put("slot",7)))
        .put("stickMappings",org.json.JSONArray().put(org.json.JSONObject()
            .put("id","move").put("label","Move").put("action","VIRTUAL_JOYSTICK")
            .put("axisX",0).put("axisY",1).put("xNorm",.2).put("yNorm",.7)
            .put("radiusNorm",.1).put("sensitivity",1.3).put("deadzone",.12)
            .put("invertY",true).put("slot",0)).put(org.json.JSONObject()
            .put("id","look").put("label","Look").put("action","CAMERA_DRAG")
            .put("axisX",11).put("axisY",14).put("xNorm",.7).put("yNorm",.5)
            .put("radiusNorm",.18).put("sensitivity",.8).put("deadzone",.15)
            .put("invertY",false).put("slot",1)))
    @Test fun exactLegacyImportPreservesMappingsBackendAndStateIdempotently() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ControlystDatabase::class.java).build()
        val prefs=context.getSharedPreferences(LegacyProfileMigration.PREFERENCES,0)
        val state=context.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES,0)
        prefs.edit().clear().putString("legacy",legacy().toString()).commit()
        state.edit().clear().putString("active_profile","legacy").putBoolean("mapping_enabled",true).commit()
        assertEquals(1,LegacyProfileMigration.migrate(context,db).imported)
        assertEquals(0,LegacyProfileMigration.migrate(context,db).imported)
        val config=ControlystRepository.deserializeJsonToConfig(db.configProfileDao().getProfileById("legacy")!!.jsonBlob)
        assertEquals("physical-controller",config.controllerProfileId)
        assertEquals(PrivilegeMethod.KERNELSU,config.preferredBackend)
        assertEquals(123456L,config.lastUpdated)
        assertEquals(104,config.buttons[0].inputKeyCode);assertEquals(0,config.buttons[0].inputScanCode)
        assertEquals(ButtonBehavior.HOLD,config.buttons[0].buttonBehavior)
        assertEquals(7,config.buttons[0].touchSlot)
        assertEquals(NodeType.JOYSTICK_ZONE,config.buttons[1].type)
        assertTrue(config.buttons[1].invertY);assertEquals(1.3f,config.buttons[1].sensitivity,0f)
        assertEquals(.12f,config.buttons[1].deadzoneInner,0f)
        assertEquals(11,config.buttons[2].axisX);assertEquals(14,config.buttons[2].axisY)
        assertEquals(NodeType.CAMERA_DRAG,config.buttons[2].type)
        assertEquals("legacy",db.mapperStateDao().get()!!.activeProfileId)
        assertTrue(db.mapperStateDao().get()!!.mappingEnabled)
        assertFalse(com.example.service.MappingRuntimeBridge.state.value.armed)
        db.mapperStateDao().save(com.example.data.entity.MapperStateEntity(activeProfileId=null,mappingEnabled=false))
        LegacyProfileMigration.migrate(context,db)
        assertNull(db.mapperStateDao().get()!!.activeProfileId) // retained preferences do not overwrite subsequent edits
        assertTrue(prefs.contains("legacy"));assertTrue(state.getBoolean("mapping_enabled",false))
        db.close()
    }
    @Test fun invalidAndCollidingLegacyProfilesLeaveSourceAndExistingRoomRowsIntact() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ControlystDatabase::class.java).build()
        val prefs=context.getSharedPreferences(LegacyProfileMigration.PREFERENCES,0)
        val state=context.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES,0)
        state.edit().clear().putString("active_profile","legacy").commit()
        val bad=legacy();bad.getJSONArray("touchMappings").put(bad.getJSONArray("touchMappings").getJSONObject(0).let {
            org.json.JSONObject(it.toString()).put("id","duplicate") })
        prefs.edit().clear().putString("legacy",bad.toString()).putString("unknown","{}").commit()
        assertEquals(3,LegacyProfileMigration.migrate(context,db).errors.size)
        assertNull(db.configProfileDao().getProfileById("legacy"));assertNull(db.mapperStateDao().get())
        val existing=MappingConfig(id="legacy",profileName="Newer",gamePackage="com.test.game",lastUpdated=567L)
        db.configProfileDao().insertProfile(com.example.data.entity.ConfigProfileEntity("legacy","com.test.game","Newer",ControlystRepository.serializeConfigToJson(existing)))
        prefs.edit().remove("unknown").putString("legacy",legacy().toString()).commit()
        assertEquals(2,LegacyProfileMigration.migrate(context,db).errors.size)
        assertEquals(existing,ControlystRepository.deserializeJsonToConfig(db.configProfileDao().getProfileById("legacy")!!.jsonBlob))
        assertTrue(prefs.contains("legacy"));assertNull(db.profileMigrationDao().get("profile:legacy"))
        db.close()
    }
    @Test fun legacyUnknownEnumsSchemaAndMismatchedPreferenceKeysFailClosed() = runBlocking {
        assertTrue(runCatching { LegacyProfileMigration.parse(legacy().put("preferredBackend","UNKNOWN").toString()) }.isFailure)
        assertTrue(runCatching { LegacyProfileMigration.parse(legacy().put("schema",2).toString()) }.isFailure)
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(LegacyProfileMigration.STATE_PREFERENCES,0).edit().clear().commit()
        context.getSharedPreferences(LegacyProfileMigration.PREFERENCES,0).edit().clear().putString("different",legacy().toString()).commit()
        val db=Room.inMemoryDatabaseBuilder(context,ControlystDatabase::class.java).build()
        assertEquals(1,LegacyProfileMigration.migrate(context,db).errors.size)
        assertNull(db.configProfileDao().getProfileById("legacy"));db.close()
    }
    @Test fun roomOneToThreePreservesExistingRows() = runBlocking {
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
            .addMigrations(ControlystDatabase.MIGRATION_1_2,ControlystDatabase.MIGRATION_2_3).build()
        assertEquals(123L,db.gameDao().getGame("com.saved.game")!!.playTimeMinutes)
        assertNull(db.profileMigrationDao().get("none"));assertNull(db.mapperStateDao().get())
        db.close();context.deleteDatabase(name); Unit
    }
}
