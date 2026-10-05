package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.dao.ConfigProfileDao
import com.example.data.dao.GameDao
import com.example.data.dao.MacroDao
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.model.AntiCheatSeverity
import com.example.model.ControllerType
import com.example.model.CrosshairConfig
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ControlystRepository(
    private val gameDao: GameDao,
    private val profileDao: ConfigProfileDao,
    private val macroDao: MacroDao,
    private val context: Context
) {
    val allGames: Flow<List<GameEntity>> = gameDao.getAllGames()
    val allProfiles: Flow<List<ConfigProfileEntity>> = profileDao.getAllProfiles()

    fun getProfilesForGame(packageName: String): Flow<List<ConfigProfileEntity>> {
        return profileDao.getProfilesForGame(packageName)
    }

    suspend fun insertGame(game: GameEntity) = withContext(Dispatchers.IO) {
        gameDao.insertGame(game)
    }

    suspend fun updateGame(game: GameEntity) = withContext(Dispatchers.IO) {
        gameDao.updateGame(game)
    }

    suspend fun deleteGame(packageName: String) = withContext(Dispatchers.IO) {
        gameDao.deleteGame(packageName)
    }

    suspend fun insertProfile(profile: ConfigProfileEntity) = withContext(Dispatchers.IO) {
        profileDao.insertProfile(profile)
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        profileDao.deleteProfile(id)
    }

    suspend fun getProfileById(id: String): ConfigProfileEntity? = withContext(Dispatchers.IO) {
        profileDao.getProfileById(id)
    }

    /**
     * Scans installed launchable apps on the device
     */
    fun scanInstalledApps(): List<GameEntity> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val list = mutableListOf<GameEntity>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            // Exclude Controlyst itself
            if (pkg == context.packageName) continue
            val appName = info.loadLabel(pm).toString()
            val isGame = android.os.Build.VERSION.SDK_INT >= 26 &&
                info.activityInfo.applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            list.add(
                GameEntity(
                    packageName = pkg,
                    displayName = appName,
                    iconUri = "",
                    isGameTag = isGame,
                    antiCheatNotes = "Game compatibility has not been assessed"
                )
            )
        }
        return list
    }

    companion object {
        fun serializeConfigToJson(config: MappingConfig): String {
            val json = JSONObject()
            json.put("schemaVersion", config.schemaVersion)
            json.put("id", config.id)
            json.put("profileName", config.profileName)
            json.put("gamePackage", config.gamePackage)
            json.put("gameTitle", config.gameTitle)
            json.put("controllerType", config.controllerType.name)
            json.put("targetAspectRatio", config.targetAspectRatio)
            json.put("preferredBackend", config.preferredBackend?.name ?: JSONObject.NULL)
            json.put("controllerProfileId", config.controllerProfileId ?: JSONObject.NULL)
            json.put("joystick", JSONObject().apply {
                put("innerDeadzone", config.joystick.innerDeadzone); put("outerDeadzone", config.joystick.outerDeadzone)
                put("runThresholdNorm", config.joystick.runThresholdNorm); put("sprintLockEnabled", config.joystick.sprintLockEnabled)
                put("curveExponent", config.joystick.curveExponent)
            })
            json.put("camera", JSONObject().apply {
                put("horizontalSensitivity", config.camera.horizontalSensitivity); put("verticalSensitivity", config.camera.verticalSensitivity)
                put("accelerationCurve", config.camera.accelerationCurve); put("smoothingFrames", config.camera.smoothingFrames)
                put("invertY", config.camera.invertY); put("mouseDpiScale", config.camera.mouseDpiScale)
            })
            json.put("antiRecoilEnabled", config.antiRecoilEnabled); json.put("antiRecoilVerticalPull", config.antiRecoilVerticalPull)
            json.put("tags", JSONArray(config.tags))

            val buttonsArray = JSONArray()
            for (node in config.buttons) {
                val nodeObj = JSONObject()
                nodeObj.put("id", node.id)
                nodeObj.put("xNorm", node.xNorm)
                nodeObj.put("yNorm", node.yNorm)
                nodeObj.put("radiusNorm", node.radiusNorm)
                nodeObj.put("type", node.type.name)
                nodeObj.put("boundKey", node.boundKey)
                nodeObj.put("label", node.label)
                nodeObj.put("turboHz", node.turboHz)
                nodeObj.put("deadzoneInner", node.deadzoneInner)
                nodeObj.put("deadzoneOuter", node.deadzoneOuter)
                nodeObj.put("sensitivity", node.sensitivity)
                nodeObj.put("buttonBehavior", node.buttonBehavior.name)
                nodeObj.put("inputKeyCode", node.inputKeyCode ?: JSONObject.NULL)
                nodeObj.put("inputScanCode", node.inputScanCode ?: JSONObject.NULL)
                nodeObj.put("touchSlot", node.touchSlot ?: JSONObject.NULL)
                nodeObj.put("axisX", node.axisX ?: JSONObject.NULL); nodeObj.put("axisY", node.axisY ?: JSONObject.NULL)
                nodeObj.put("invertY", node.invertY)
                nodeObj.put("triggerPressThreshold", node.triggerPressThreshold)
                nodeObj.put("triggerReleaseThreshold", node.triggerReleaseThreshold)
                nodeObj.put("macroActions", JSONArray().apply { node.macroActions.forEach { step ->
                    put(JSONObject().apply { put("delayMs", step.delayMs); put("durationMs", step.durationMs)
                        put("actionType", step.actionType); put("xNorm", step.xNorm); put("yNorm", step.yNorm)
                        put("endXNorm",step.endXNorm ?: JSONObject.NULL);put("endYNorm",step.endYNorm ?: JSONObject.NULL) })
                } })
                buttonsArray.put(nodeObj)
            }
            json.put("buttons", buttonsArray)

            val crosshairObj = JSONObject()
            crosshairObj.put("isEnabled", config.crosshair.isEnabled)
            crosshairObj.put("shape", config.crosshair.shape.name)
            crosshairObj.put("sizeDp", config.crosshair.sizeDp)
            crosshairObj.put("thicknessDp", config.crosshair.thicknessDp)
            crosshairObj.put("gapDp", config.crosshair.gapDp)
            crosshairObj.put("colorHex", config.crosshair.colorHex)
            crosshairObj.put("opacity", config.crosshair.opacity)
            crosshairObj.put("outlineEnabled", config.crosshair.outlineEnabled)
            crosshairObj.put("outlineColorHex", config.crosshair.outlineColorHex)
            crosshairObj.put("outlineThicknessDp", config.crosshair.outlineThicknessDp)
            crosshairObj.put("currentSpreadMultiplier", config.crosshair.currentSpreadMultiplier)
            crosshairObj.put("offsetX", config.crosshair.offsetX)
            crosshairObj.put("offsetY", config.crosshair.offsetY)
            crosshairObj.put("dynamicSpread", config.crosshair.dynamicSpread)
            json.put("crosshair", crosshairObj)

            json.put("author", config.author)
            json.put("isOfficialVerified", config.isOfficialVerified)
            json.put("rating", config.rating)
            json.put("downloadCount", config.downloadCount)
            json.put("lastUpdated", config.lastUpdated)

            return json.toString(2)
        }

        fun deserializeJsonToConfig(jsonStr: String): MappingConfig {
            val json = JSONObject(jsonStr)
            val schemaVersion = json.optInt("schemaVersion", 1)
            val id = json.getString("id")
            val name = json.optString("profileName", "Imported Profile")
            val pkg = json.getString("gamePackage")
            val title = json.optString("gameTitle", "")
            val ctrlTypeStr = json.optString("controllerType", "XBOX")
            val ctrlType = ControllerType.valueOf(ctrlTypeStr)
            val targetAspect = json.optString("targetAspectRatio", "19.5:9")

            val buttons = mutableListOf<MappingNode>()
            val buttonsArray = json.optJSONArray("buttons")
            if (buttonsArray != null) {
                for (i in 0 until buttonsArray.length()) {
                    val nodeObj = buttonsArray.getJSONObject(i)
                    val typeStr = nodeObj.optString("type", "BUTTON")
                    val type = NodeType.valueOf(typeStr)
                    fun optionalInt(key: String): Int? = if (nodeObj.has(key) && !nodeObj.isNull(key)) nodeObj.getInt(key) else null
                    buttons.add(
                        MappingNode(
                            id = nodeObj.getString("id"),
                            xNorm = nodeObj.getDouble("xNorm").toFloat(),
                            yNorm = nodeObj.getDouble("yNorm").toFloat(),
                            radiusNorm = nodeObj.optDouble("radiusNorm", 0.05).toFloat(),
                            type = type,
                            boundKey = nodeObj.optString("boundKey", "A"),
                            label = nodeObj.optString("label", ""),
                            turboHz = nodeObj.optInt("turboHz", 10),
                            deadzoneInner = nodeObj.optDouble("deadzoneInner", 0.15).toFloat(),
                            deadzoneOuter = nodeObj.optDouble("deadzoneOuter", 0.95).toFloat(),
                            sensitivity = nodeObj.optDouble("sensitivity", 1.0).toFloat(),
                            buttonBehavior = ButtonBehavior.valueOf(nodeObj.optString("buttonBehavior",
                                if (nodeObj.optString("boundKey").uppercase() in setOf("LT","RT","L2","R2")) "HOLD" else "TAP")),
                            inputKeyCode = optionalInt("inputKeyCode"), inputScanCode = optionalInt("inputScanCode"),
                            touchSlot = optionalInt("touchSlot"), axisX = optionalInt("axisX"), axisY = optionalInt("axisY"),
                            invertY = nodeObj.optBoolean("invertY", false),
                            triggerPressThreshold = nodeObj.optDouble("triggerPressThreshold", .55).toFloat(),
                            triggerReleaseThreshold = nodeObj.optDouble("triggerReleaseThreshold", .35).toFloat(),
                            macroActions = nodeObj.optJSONArray("macroActions")?.let { array ->
                                (0 until array.length()).map { index -> array.getJSONObject(index).let { step ->
                                    MacroStep(step.getLong("delayMs"), step.getString("actionType"),
                                        step.getDouble("xNorm").toFloat(), step.getDouble("yNorm").toFloat(), step.getLong("durationMs"),
                                        if(step.isNull("endXNorm")) null else step.getDouble("endXNorm").toFloat(),
                                        if(step.isNull("endYNorm")) null else step.getDouble("endYNorm").toFloat())
                                } }
                            } ?: emptyList()
                        )
                    )
                }
            }

            val crosshairObj = json.optJSONObject("crosshair")
            val crosshair = if (crosshairObj != null) {
                val shapeStr = crosshairObj.optString("shape", "CLASSIC_CROSS")
                val shape = com.example.model.CrosshairShape.valueOf(shapeStr)
                CrosshairConfig(
                    isEnabled = crosshairObj.optBoolean("isEnabled", false),
                    shape = shape,
                    sizeDp = crosshairObj.optDouble("sizeDp", 24.0).toFloat(),
                    thicknessDp = crosshairObj.optDouble("thicknessDp", 2.5).toFloat(),
                    gapDp = crosshairObj.optDouble("gapDp", 4.0).toFloat(),
                    colorHex = crosshairObj.optString("colorHex", "#00F0FF"),
                    opacity = crosshairObj.optDouble("opacity", .9).toFloat(),
                    outlineEnabled = crosshairObj.optBoolean("outlineEnabled", true),
                    outlineColorHex = crosshairObj.optString("outlineColorHex", "#000000"),
                    outlineThicknessDp = crosshairObj.optDouble("outlineThicknessDp", 1.0).toFloat(),
                    currentSpreadMultiplier = crosshairObj.optDouble("currentSpreadMultiplier", 1.0).toFloat(),
                    offsetX = crosshairObj.optDouble("offsetX", 0.0).toFloat(),
                    offsetY = crosshairObj.optDouble("offsetY", 0.0).toFloat(),
                    dynamicSpread = crosshairObj.optBoolean("dynamicSpread", true)
                )
            } else {
                CrosshairConfig()
            }

            val joystick = json.optJSONObject("joystick") ?: JSONObject()
            val camera = json.optJSONObject("camera") ?: JSONObject()
            return MappingConfig(
                preferredBackend = if (json.has("preferredBackend") && !json.isNull("preferredBackend")) PrivilegeMethod.valueOf(json.getString("preferredBackend")) else null,
                controllerProfileId = if (json.has("controllerProfileId") && !json.isNull("controllerProfileId")) json.getString("controllerProfileId") else null,
                joystick = JoystickSettings(joystick.optDouble("innerDeadzone", .15).toFloat(), joystick.optDouble("outerDeadzone", .95).toFloat(),
                    joystick.optDouble("runThresholdNorm", .75).toFloat(), joystick.optBoolean("sprintLockEnabled", false), joystick.optDouble("curveExponent", 1.0).toFloat()),
                camera = CameraSettings(camera.optDouble("horizontalSensitivity", 1.0).toFloat(), camera.optDouble("verticalSensitivity", .85).toFloat(),
                    camera.optDouble("accelerationCurve", 1.2).toFloat(), camera.optInt("smoothingFrames", 3), camera.optBoolean("invertY", false), camera.optDouble("mouseDpiScale", 1.0).toFloat()),
                antiRecoilEnabled = json.optBoolean("antiRecoilEnabled", false), antiRecoilVerticalPull = json.optDouble("antiRecoilVerticalPull", 0.0).toFloat(),
                tags = json.optJSONArray("tags")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
                schemaVersion = schemaVersion,
                id = id,
                profileName = name,
                gamePackage = pkg,
                gameTitle = title,
                controllerType = ctrlType,
                targetAspectRatio = targetAspect,
                buttons = buttons,
                crosshair = crosshair,
                author = json.optString("author", "Local user"),
                isOfficialVerified = json.optBoolean("isOfficialVerified", false),
                rating = json.optDouble("rating", 0.0).toFloat(),
                downloadCount = json.optInt("downloadCount", 0),
                lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            )
        }
    }
}
