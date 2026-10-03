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
        val backupZip = File(backupDir, "controlyst_backup_${System.currentTimeMillis()}.zip")
        if (backupZip.exists()) backupZip.delete()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupZip))).use { zos ->
            // 1. metadata.json
            val metaJson = JSONObject().apply {
                put("appVersion", "1.0.0")
                put("backupSchemaVersion", 3)
                put("timestamp", System.currentTimeMillis())
                put("totalProfilesCount", configs.size)
                put("deviceModel", android.os.Build.MODEL)
                put("deviceManufacturer", android.os.Build.MANUFACTURER)
            }
            writeStringToZip(zos, "metadata.json", metaJson.toString(2))

            // 2. profiles/ with complete MappingConfig payload
            configs.forEach { cfg ->
                val cfgJson = JSONObject().apply {
                    put("schemaVersion", cfg.schemaVersion)
                    put("id", cfg.id)
                    put("profileName", cfg.profileName)
                    put("gamePackage", cfg.gamePackage)
                    put("gameTitle", cfg.gameTitle)
                    put("controllerType", cfg.controllerType.name)
                    put("targetAspectRatio", cfg.targetAspectRatio)
                    put("antiRecoilEnabled", cfg.antiRecoilEnabled)
                    put("antiRecoilVerticalPull", cfg.antiRecoilVerticalPull)
                    put("author", cfg.author)
                    put("isOfficialVerified", cfg.isOfficialVerified)
                    put("downloadCount", cfg.downloadCount)
                    put("rating", cfg.rating)
                    put("lastUpdated", cfg.lastUpdated)

                    // Joystick settings
                    put("joystick", JSONObject().apply {
                        put("innerDeadzone", cfg.joystick.innerDeadzone)
                        put("outerDeadzone", cfg.joystick.outerDeadzone)
                        put("runThresholdNorm", cfg.joystick.runThresholdNorm)
                        put("sprintLockEnabled", cfg.joystick.sprintLockEnabled)
                        put("curveExponent", cfg.joystick.curveExponent)
                    })

                    // Camera settings
                    put("camera", JSONObject().apply {
                        put("horizontalSensitivity", cfg.camera.horizontalSensitivity)
                        put("verticalSensitivity", cfg.camera.verticalSensitivity)
                        put("accelerationCurve", cfg.camera.accelerationCurve)
                        put("smoothingFrames", cfg.camera.smoothingFrames)
                        put("invertY", cfg.camera.invertY)
                        put("mouseDpiScale", cfg.camera.mouseDpiScale)
                    })

                    // Buttons and nodes array
                    val nodesArray = JSONArray()
                    cfg.buttons.forEach { node ->
                        val nodeObj = JSONObject().apply {
                            put("id", node.id)
                            put("xNorm", node.xNorm)
                            put("yNorm", node.yNorm)
                            put("radiusNorm", node.radiusNorm)
                            put("type", node.type.name)
                            put("boundKey", node.boundKey)
                            put("label", node.label)
                            put("turboHz", node.turboHz)
                            put("deadzoneInner", node.deadzoneInner)
                            put("deadzoneOuter", node.deadzoneOuter)
                            put("sensitivity", node.sensitivity)

                            val macroArray = JSONArray()
                            node.macroActions.forEach { step ->
                                macroArray.put(JSONObject().apply {
                                    put("delayMs", step.delayMs)
                                    put("actionType", step.actionType)
                                    put("xNorm", step.xNorm)
                                    put("yNorm", step.yNorm)
                                    put("durationMs", step.durationMs)
                                })
                            }
                            put("macroActions", macroArray)
                        }
                        nodesArray.put(nodeObj)
                    }
                    put("buttons", nodesArray)

                    // Crosshair config
                    put("crosshair", JSONObject().apply {
                        put("isEnabled", cfg.crosshair.isEnabled)
                        put("shape", cfg.crosshair.shape.name)
                        put("sizeDp", cfg.crosshair.sizeDp)
                        put("thicknessDp", cfg.crosshair.thicknessDp)
                        put("gapDp", cfg.crosshair.gapDp)
                        put("colorHex", cfg.crosshair.colorHex)
                        put("opacity", cfg.crosshair.opacity)
                        put("outlineEnabled", cfg.crosshair.outlineEnabled)
                        put("outlineColorHex", cfg.crosshair.outlineColorHex)
                        put("outlineThicknessDp", cfg.crosshair.outlineThicknessDp)
                        put("offsetX", cfg.crosshair.offsetX)
                        put("offsetY", cfg.crosshair.offsetY)
                        put("dynamicSpread", cfg.crosshair.dynamicSpread)
                    })
                }
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
