package com.example.ui.mapper

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.model.MappingConfig
import java.io.File
import java.util.UUID

/** Transfers an actual captured Bitmap to the existing mapper; never substitutes a prior image. */
object MapperScreenshotHandoff {
    private const val ACTION_SUFFIX = ".OPEN_MAPPER_CAPTURE"
    private const val EXTRA_PROFILE_ID = "nexus.capture.profile_id"
    private const val EXTRA_GAME_PACKAGE = "nexus.capture.game_package"
    private const val DIRECTORY = "mapper-captures"

    data class Request(val profileId: String, val gamePackage: String, val uri: Uri)

    fun open(context: Context, bitmap: Bitmap, config: MappingConfig): Result<Unit> = runCatching {
        val intent = createLaunchIntent(context, bitmap, config)
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            val file = File(File(context.cacheDir, DIRECTORY), intent.data?.lastPathSegment.orEmpty())
            if (file.exists() && !file.delete()) Log.e("NexusScreenshot", "Failed to remove unlaunched capture: ${file.name}")
            throw error
        }
    }.onFailure { Log.e("NexusScreenshot", "Captured screenshot handoff failed", it) }

    internal fun createLaunchIntent(context: Context, bitmap: Bitmap, config: MappingConfig): Intent {
        require(config.id.isNotBlank() && config.gamePackage.isNotBlank()) { "A saved game profile is required for screenshot mapping" }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Captured screenshot is unavailable or recycled" }
        val directory = File(context.cacheDir, DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot cache directory" }
        val file = File(directory, "nexus-${UUID.randomUUID()}.png")
        check(file.createNewFile()) { "Cannot create captured screenshot file" }
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Android could not encode the captured screenshot" }
            }
            check(file.length() > 0L) { "Captured screenshot file is empty" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return Intent(context, MainActivity::class.java).apply {
                action = context.packageName + ACTION_SUFFIX
                data = uri
                clipData = ClipData.newRawUri("Nexus captured screenshot", uri)
                putExtra(EXTRA_PROFILE_ID, config.id)
                putExtra(EXTRA_GAME_PACKAGE, config.gamePackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: Exception) {
            if (file.exists() && !file.delete()) Log.e("NexusScreenshot", "Failed to remove rejected capture: ${file.name}")
            throw error
        }
    }

    /** Null means an ordinary launcher intent; malformed capture requests remain explicit errors. */
    fun request(intent: Intent?, applicationId: String): Result<Request?> = runCatching {
        if (intent?.action != applicationId + ACTION_SUFFIX) return@runCatching null
        val id = intent.getStringExtra(EXTRA_PROFILE_ID)?.takeIf(String::isNotBlank)
            ?: error("Captured screenshot request has no profile ID")
        val game = intent.getStringExtra(EXTRA_GAME_PACKAGE)?.takeIf(String::isNotBlank)
            ?: error("Captured screenshot request has no game package")
        val uri = intent.data ?: error("Captured screenshot request has no image URI")
        require(uri.scheme == "content" && uri.authority == "$applicationId.fileprovider") {
            "Captured screenshot URI is outside the Nexus file provider"
        }
        val segments = uri.pathSegments
        require(segments.size == 3 && segments[0] == "all_cache" && segments[1] == DIRECTORY &&
            uri.query == null && uri.fragment == null) { "Captured screenshot URI is outside its capture directory" }
        val filename = segments[2]
        require(filename.startsWith("nexus-") && filename.endsWith(".png")) { "Invalid captured screenshot filename" }
        UUID.fromString(filename.removePrefix("nexus-").removeSuffix(".png"))
        Request(id, game, uri)
    }
}
