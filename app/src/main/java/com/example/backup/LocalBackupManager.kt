package com.example.backup

import android.content.Context
import com.example.model.MappingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupMetadata(
    val appVersion: String = "1.0.0",
    val backupSchemaVersion: Int = 3,
    val timestamp: Long = System.currentTimeMillis(),
    val totalProfilesCount: Int = 0,
    val deviceModel: String = android.os.Build.MODEL ?: "Android Device"
)

object LocalBackupManager {

    suspend fun exportFullBackupZip(
        context: Context,
        configs: List<MappingConfig>
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupZip = File(backupDir, "nexus_backup_${System.currentTimeMillis()}.zip")
        if (backupZip.exists()) backupZip.delete()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupZip))).use { zos ->
            // 1. metadata.json
            val metaJson = JSONObject().apply {
                put("appVersion", com.example.BuildConfig.VERSION_NAME)
                put("backupSchemaVersion", 3)
                put("timestamp", System.currentTimeMillis())
                put("totalProfilesCount", configs.size)
                put("deviceModel", android.os.Build.MODEL)
                put("deviceManufacturer", android.os.Build.MANUFACTURER)
            }
            writeStringToZip(zos, "metadata.json", metaJson.toString(2))

            // 2. profiles/ with complete MappingConfig payload
            configs.forEach { cfg ->
                val errors = com.example.input.ProfileValidator.errors(cfg, requireBindings = false)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                require(cfg.id.matches(Regex("[A-Za-z0-9_.-]+"))) { "Unsafe profile ID for ZIP export" }
                val cfgJson = JSONObject(com.example.data.ControlystRepository.serializeConfigToJson(cfg))
                writeStringToZip(zos, "profiles/${cfg.id}.json", cfgJson.toString(2))
            }
        }

        backupZip
    }

    suspend fun validateAndInspectBackupZip(file: File): BackupMetadata? = withContext(Dispatchers.IO) {
        return@withContext try {
            ZipFile(file).use { zip ->
                val metaEntry = zip.getEntry("metadata.json") ?: return@withContext null
                val content = zip.getInputStream(metaEntry).bufferedReader().use { it.readText() }
                val json = JSONObject(content)
                BackupMetadata(
                    appVersion = json.optString("appVersion", "1.0.0"),
                    backupSchemaVersion = json.optInt("backupSchemaVersion", 1),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    totalProfilesCount = json.optInt("totalProfilesCount", 0),
                    deviceModel = json.optString("deviceModel", "Unknown")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeStringToZip(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}
