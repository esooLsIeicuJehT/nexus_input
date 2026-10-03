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
     * Seeds initial database with popular tactical games and verified community profiles
     */
    suspend fun prepopulateDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        val initialGames = listOf(
            GameEntity(
                packageName = "com.proxima.dfm",
                displayName = "Delta Force Mobile",
                iconUri = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=150",
                isGameTag = true,
                antiCheatSeverity = AntiCheatSeverity.HIGH_ALERT,
                antiCheatNotes = "Tencent ACE anti-cheat active. Shizuku or Accessibility mode strongly advised over raw su.",
                playTimeMinutes = 480,
                lastPlayedTimestamp = System.currentTimeMillis() - 3600000
            ),
            GameEntity(
                packageName = "com.activision.callofduty.warzone",
                displayName = "Warzone Mobile",
                iconUri = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=150",
                isGameTag = true,
                antiCheatSeverity = AntiCheatSeverity.MODERATE,
                antiCheatNotes = "Ricochet Mobile telemetry check. Standard controller mapping recommended.",
                playTimeMinutes = 320,
                lastPlayedTimestamp = System.currentTimeMillis() - 14400000
            ),
            GameEntity(
                packageName = "com.miHoYo.GenshinImpact",
                displayName = "Genshin Impact",
                iconUri = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=150",
                isGameTag = true,
                antiCheatSeverity = AntiCheatSeverity.SAFE,
                antiCheatNotes = "Safe for touch mapping overlays.",
                playTimeMinutes = 610,
                lastPlayedTimestamp = System.currentTimeMillis() - 86400000
            ),
            GameEntity(
                packageName = "com.tencent.ig",
                displayName = "PUBG Mobile",
                iconUri = "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=150",
                isGameTag = true,
                antiCheatSeverity = AntiCheatSeverity.HIGH_ALERT,
                antiCheatNotes = "Strict memory scanning. Use Accessibility injection only.",
                playTimeMinutes = 140,
                lastPlayedTimestamp = System.currentTimeMillis() - 172800000
            )
        )

        gameDao.insertGames(initialGames)

        // Seed default profiles
        val dfmProfile = createSampleDfmConfig()
        profileDao.insertProfile(
            ConfigProfileEntity(
                id = "dfm_pro_ranked",
                gamePackage = "com.proxima.dfm",
                profileName = "Ranked Tactical (Stadia/Xbox Fixed)",
                jsonBlob = serializeConfigToJson(dfmProfile),
                isDefault = true,
                author = "Controlyst Master",
                isOfficialVerified = true,
                rating = 4.9f,
                downloads = 14820
            )
        )

        val warzoneProfile = createSampleWarzoneConfig()
        profileDao.insertProfile(
            ConfigProfileEntity(
                id = "wzm_speed_aim",
                gamePackage = "com.activision.callofduty.warzone",
                profileName = "Fast ADS & Slide Cancel",
                jsonBlob = serializeConfigToJson(warzoneProfile),
                isDefault = true,
                author = "ApexAim",
                isOfficialVerified = true,
                rating = 4.8f,
                downloads = 9340
            )
        )
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
            val isLikelyGame = appName.contains("game", ignoreCase = true) ||
                    pkg.contains("game", ignoreCase = true) ||
                    pkg.contains("mobile", ignoreCase = true) ||
                    pkg.contains("unity", ignoreCase = true)

            list.add(
                GameEntity(
                    packageName = pkg,
                    displayName = appName,
                    iconUri = "",
                    isGameTag = isLikelyGame,
                    antiCheatSeverity = if (isLikelyGame) AntiCheatSeverity.MODERATE else AntiCheatSeverity.SAFE,
                    antiCheatNotes = if (isLikelyGame) "App categorized as interactive game" else "Standard Android application"
                )
            )
        }
        return list
    }

    companion object {
        fun createSampleDfmConfig(): MappingConfig {
            return MappingConfig(
                schemaVersion = 2,
                id = "dfm_pro_ranked",
                profileName = "Ranked Tactical (Stadia/Xbox Fixed)",
                gamePackage = "com.proxima.dfm",
                gameTitle = "Delta Force Mobile",
                controllerType = ControllerType.XBOX,
                buttons = listOf(
                    MappingNode("b_fire", 0.85f, 0.72f, 0.06f, NodeType.BUTTON, "RT", "Fire"),
                    MappingNode("b_ads", 0.82f, 0.42f, 0.055f, NodeType.BUTTON, "LT", "ADS Aim"),
                    MappingNode("b_jump", 0.92f, 0.60f, 0.05f, NodeType.BUTTON, "A", "Jump / Vault"),
                    MappingNode("b_crouch", 0.88f, 0.85f, 0.05f, NodeType.BUTTON, "B", "Crouch / Slide"),
                    MappingNode("b_reload", 0.74f, 0.76f, 0.05f, NodeType.BUTTON, "X", "Reload"),
                    MappingNode("b_weapon_swap", 0.70f, 0.90f, 0.05f, NodeType.BUTTON, "Y", "Switch Gun"),
                    MappingNode("b_tactical", 0.68f, 0.38f, 0.05f, NodeType.BUTTON, "LB", "Skill"),
                    MappingNode("b_lethal", 0.76f, 0.32f, 0.05f, NodeType.BUTTON, "RB", "Grenade"),
                    MappingNode("joy_move", 0.18f, 0.72f, 0.12f, NodeType.JOYSTICK_ZONE, "LS", "Move WASD"),
                    MappingNode("cam_aim", 0.70f, 0.50f, 0.20f, NodeType.CAMERA_DRAG, "RS", "Aim Look")
                ),
                crosshair = CrosshairConfig(
                    isEnabled = true,
                    sizeDp = 22f,
                    thicknessDp = 2.5f,
                    gapDp = 5f,
                    colorHex = "#00F0FF"
                ),
                author = "Controlyst Master",
                isOfficialVerified = true,
                rating = 4.9f,
                downloadCount = 14820
            )
        }

        fun createSampleWarzoneConfig(): MappingConfig {
            return MappingConfig(
                schemaVersion = 2,
                id = "wzm_speed_aim",
                profileName = "Fast ADS & Slide Cancel",
                gamePackage = "com.activision.callofduty.warzone",
                gameTitle = "Warzone Mobile",
                controllerType = ControllerType.PLAYSTATION,
                buttons = listOf(
                    MappingNode("w_fire", 0.86f, 0.70f, 0.06f, NodeType.BUTTON, "R2", "Fire"),
                    MappingNode("w_aim", 0.80f, 0.40f, 0.055f, NodeType.BUTTON, "L2", "ADS"),
                    MappingNode("w_jump", 0.91f, 0.58f, 0.05f, NodeType.BUTTON, "CROSS", "Jump"),
                    MappingNode("w_slide", 0.88f, 0.82f, 0.05f, NodeType.BUTTON, "CIRCLE", "Slide Cancel"),
                    MappingNode("w_armor", 0.65f, 0.88f, 0.05f, NodeType.BUTTON, "TRIANGLE", "Plates"),
                    MappingNode("w_joy", 0.18f, 0.70f, 0.12f, NodeType.JOYSTICK_ZONE, "L3", "Sprint / Move")
                ),
                crosshair = CrosshairConfig(
                    isEnabled = true,
                    sizeDp = 18f,
                    thicknessDp = 2f,
                    gapDp = 3f,
                    colorHex = "#10B981"
                ),
                author = "ApexAim",
                isOfficialVerified = true,
                rating = 4.8f,
                downloadCount = 9340
            )
        }

        fun serializeConfigToJson(config: MappingConfig): String {
            val json = JSONObject()
            json.put("schemaVersion", config.schemaVersion)
            json.put("id", config.id)
            json.put("profileName", config.profileName)
            json.put("gamePackage", config.gamePackage)
            json.put("gameTitle", config.gameTitle)
            json.put("controllerType", config.controllerType.name)
            json.put("targetAspectRatio", config.targetAspectRatio)

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
            val id = json.optString("id", "profile_${System.currentTimeMillis()}")
            val name = json.optString("profileName", "Imported Profile")
            val pkg = json.optString("gamePackage", "unknown.game")
            val title = json.optString("gameTitle", "")
            val ctrlTypeStr = json.optString("controllerType", "XBOX")
            val ctrlType = try {
                ControllerType.valueOf(ctrlTypeStr)
            } catch (e: Exception) {
                ControllerType.XBOX
            }
            val targetAspect = json.optString("targetAspectRatio", "19.5:9")

            val buttons = mutableListOf<MappingNode>()
            val buttonsArray = json.optJSONArray("buttons")
            if (buttonsArray != null) {
                for (i in 0 until buttonsArray.length()) {
                    val nodeObj = buttonsArray.getJSONObject(i)
                    val typeStr = nodeObj.optString("type", "BUTTON")
                    val type = try {
                        NodeType.valueOf(typeStr)
                    } catch (e: Exception) {
                        NodeType.BUTTON
                    }
                    buttons.add(
                        MappingNode(
                            id = nodeObj.optString("id", "node_$i"),
                            xNorm = nodeObj.optDouble("xNorm", 0.5).toFloat(),
                            yNorm = nodeObj.optDouble("yNorm", 0.5).toFloat(),
                            radiusNorm = nodeObj.optDouble("radiusNorm", 0.05).toFloat(),
                            type = type,
                            boundKey = nodeObj.optString("boundKey", "A"),
                            label = nodeObj.optString("label", ""),
                            turboHz = nodeObj.optInt("turboHz", 10),
                            deadzoneInner = nodeObj.optDouble("deadzoneInner", 0.15).toFloat(),
                            deadzoneOuter = nodeObj.optDouble("deadzoneOuter", 0.95).toFloat(),
                            sensitivity = nodeObj.optDouble("sensitivity", 1.0).toFloat()
                        )
                    )
                }
            }

            val crosshairObj = json.optJSONObject("crosshair")
            val crosshair = if (crosshairObj != null) {
                val shapeStr = crosshairObj.optString("shape", "CLASSIC_CROSS")
                val shape = try {
                    com.example.model.CrosshairShape.valueOf(shapeStr)
                } catch (e: Exception) {
                    com.example.model.CrosshairShape.CLASSIC_CROSS
                }
                CrosshairConfig(
                    isEnabled = crosshairObj.optBoolean("isEnabled", false),
                    shape = shape,
                    sizeDp = crosshairObj.optDouble("sizeDp", 24.0).toFloat(),
                    thicknessDp = crosshairObj.optDouble("thicknessDp", 2.5).toFloat(),
                    gapDp = crosshairObj.optDouble("gapDp", 4.0).toFloat(),
                    colorHex = crosshairObj.optString("colorHex", "#00F0FF"),
                    offsetX = crosshairObj.optDouble("offsetX", 0.0).toFloat(),
                    offsetY = crosshairObj.optDouble("offsetY", 0.0).toFloat(),
                    dynamicSpread = crosshairObj.optBoolean("dynamicSpread", true)
                )
            } else {
                CrosshairConfig()
            }

            return MappingConfig(
                schemaVersion = schemaVersion,
                id = id,
                profileName = name,
                gamePackage = pkg,
                gameTitle = title,
                controllerType = ctrlType,
                targetAspectRatio = targetAspect,
                buttons = buttons,
                crosshair = crosshair,
                author = json.optString("author", "Community"),
                isOfficialVerified = json.optBoolean("isOfficialVerified", false),
                rating = json.optDouble("rating", 4.5).toFloat(),
                downloadCount = json.optInt("downloadCount", 0),
                lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            )
        }
    }
}
