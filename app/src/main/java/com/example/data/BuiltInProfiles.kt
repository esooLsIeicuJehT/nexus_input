package com.example.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.ButtonBehavior
import com.example.model.CameraSettings
import com.example.model.ControllerType
import com.example.model.JoystickSettings
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType

/** Factory profiles ship as editable fallbacks. User-saved defaults always outrank them. */
object BuiltInProfiles {
    const val DELTA_FORCE_PACKAGE = "com.proxima.dfm"
    const val DELTA_FORCE_PROFILE_ID = "builtin_delta_force_nexus_v1"

    val deltaForce: MappingConfig
        get() = MappingConfig(
            id = DELTA_FORCE_PROFILE_ID,
            profileName = "Delta Force - Nexus Preset",
            gamePackage = DELTA_FORCE_PACKAGE,
            gameTitle = "Delta Force",
            controllerType = ControllerType.XBOX,
            targetAspectRatio = "19.5:9",
            joystick = JoystickSettings(
                innerDeadzone = .02f,
                outerDeadzone = 1f,
                runThresholdNorm = .75f,
                sprintLockEnabled = false,
                walkRadiusScale = .62f,
                runRadiusScale = 1f,
                curveExponent = 1f
            ),
            camera = CameraSettings(
                horizontalSensitivity = 1f,
                verticalSensitivity = .85f,
                accelerationCurve = 1.2f,
                smoothingFrames = 3,
                verticalRatio = 1f,
                fastTurnBoost = 1f,
                invertY = true,
                mouseDpiScale = 1f
            ),
            buttons = listOf(
                MappingNode("df_a", .90118736f, .9280799f, .05f, NodeType.BUTTON, "A"),
                MappingNode("df_b", .8291504f, .93591213f, .05f, NodeType.BUTTON, "B"),
                MappingNode("df_lt", .94355994f, .5454779f, .05f, NodeType.BUTTON, "LT", buttonBehavior = ButtonBehavior.HOLD),
                MappingNode("df_rt", .09772131f, .5510186f, .05f, NodeType.BUTTON, "RT", buttonBehavior = ButtonBehavior.HOLD),
                MappingNode("df_x", .7319335f, .89420795f, .05f, NodeType.BUTTON, "X"),
                MappingNode("df_y", .8583154f, .2455817f, .05f, NodeType.BUTTON, "Y"),
                MappingNode(
                    id = "df_ls",
                    xNorm = .19823368f,
                    yNorm = .7986154f,
                    radiusNorm = .12f,
                    type = NodeType.JOYSTICK_ZONE,
                    boundKey = "LS",
                    deadzoneInner = .02f,
                    deadzoneOuter = 1f
                ),
                MappingNode(
                    id = "df_rs",
                    xNorm = .6845802f,
                    yNorm = .20454982f,
                    radiusNorm = .12f,
                    type = NodeType.CAMERA_DRAG,
                    boundKey = "RS"
                )
            ),
            author = "Built-in Nexus preset",
            isOfficialVerified = false,
            lastUpdated = 0L
        )

    /**
     * Seeds without replacing anything the user has already saved. isDefault=false means
     * an edited/user profile wins getDefaultForGame(); on a clean install this is the fallback.
     */
    fun seed(db: SupportSQLiteDatabase) {
        val profile = deltaForce
        val json = ControlystRepository.serializeConfigToJson(profile)
        db.execSQL(
            """INSERT OR IGNORE INTO config_profiles
                (id, gamePackage, profileName, jsonBlob, isDefault, updatedAt, author, isOfficialVerified, rating, downloads)
                VALUES (?, ?, ?, ?, 0, ?, ?, 0, 0.0, 0)""".trimIndent(),
            arrayOf<Any?>(
                profile.id,
                profile.gamePackage,
                profile.profileName,
                json,
                profile.lastUpdated,
                profile.author
            )
        )
    }
}
