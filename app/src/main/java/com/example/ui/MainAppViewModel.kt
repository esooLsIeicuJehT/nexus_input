package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calibration.CalibrationManager
import com.example.calibration.StickCalibrationState
import com.example.calibration.TouchLatencyResult
import com.example.calibration.TriggerCalibrationState
import com.example.data.ControlystDatabase
import com.example.data.ControlystRepository
import com.example.data.entity.ConfigProfileEntity
import com.example.data.entity.GameEntity
import com.example.injector.InputInjector
import com.example.injector.InputInjectorFactory
import com.example.injector.PrivilegeDetector
import com.example.injector.PrivilegeProbeResult
import com.example.model.AntiCheatSeverity
import com.example.model.ControllerProfile
import com.example.model.ControllerType
import com.example.model.CrosshairConfig
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.PrivilegeMethod
import com.example.service.MappingForegroundService
import com.example.service.ShizukuPairingManager
import com.example.service.ShizukuPairingState
import com.example.service.PanicKillSwitch
import com.example.model.*
import com.example.ai.vision.AiHudDetector
import com.example.ai.ConfigDiffEngine
import com.example.backup.LocalBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ControlystDatabase.getDatabase(application)
    private val profilePersistence = com.example.data.ProfilePersistence(db)
    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    val repository = ControlystRepository(db.gameDao(), db.configProfileDao(), db.macroDao(), application)
    val privilegeDetector = PrivilegeDetector(application)
    val calibrationManager = CalibrationManager(application)
    // AI & HUD Recognition States
    private val _aiHudCandidates = MutableStateFlow<List<AiHudCandidate>>(emptyList())
    val aiHudCandidates: StateFlow<List<AiHudCandidate>> = _aiHudCandidates.asStateFlow()

    private val _diffResult = MutableStateFlow<ConfigDiffResult?>(null)
    val diffResult: StateFlow<ConfigDiffResult?> = _diffResult.asStateFlow()


    // Games and Profiles Flow
    val games: StateFlow<List<GameEntity>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ConfigProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Privilege probe state
    private val _privilegeResults = MutableStateFlow<List<PrivilegeProbeResult>>(emptyList())
    val privilegeResults: StateFlow<List<PrivilegeProbeResult>> = _privilegeResults.asStateFlow()

    private val _activePrivilegeMethod = MutableStateFlow(PrivilegeMethod.ACCESSIBILITY)
    val activePrivilegeMethod: StateFlow<PrivilegeMethod> = _activePrivilegeMethod.asStateFlow()

    // Controller Profile
    private val _controllerProfile = MutableStateFlow(ControllerProfile())
    val controllerProfile: StateFlow<ControllerProfile> = _controllerProfile.asStateFlow()

    // Active Selected Game & Config
    private val _selectedGame = MutableStateFlow<GameEntity?>(null)
    val selectedGame: StateFlow<GameEntity?> = _selectedGame.asStateFlow()

    private val _activeConfig = MutableStateFlow(MappingConfig(id = "new_profile", profileName = "Choose a game", gamePackage = ""))
    val activeConfig: StateFlow<MappingConfig> = _activeConfig.asStateFlow()

    // Calibration States
    val stickCalibrationState: StateFlow<StickCalibrationState> = calibrationManager.stickState
    val triggerCalibrationState: StateFlow<TriggerCalibrationState> = calibrationManager.triggerState
    val touchLatencyResult: StateFlow<TouchLatencyResult> = calibrationManager.latencyResult

    // Monetization State

    // Active Injector
    var currentInjector: InputInjector = InputInjectorFactory.createInjector(PrivilegeMethod.ACCESSIBILITY)
        private set

    // Onboarding step (0..5), if completed = -1
    private val _onboardingStep = MutableStateFlow(-1)
    val onboardingStep: StateFlow<Int> = _onboardingStep.asStateFlow()

    // Production navigation is limited to mapping, profiles, devices, overlays and status.
    private val _currentTab = MutableStateFlow("library")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Feedback Toast / Snackbar message
    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val migration = withContext(Dispatchers.IO) {
                com.example.data.LegacyProfileMigration.migrate(getApplication(), db)
            }
            if (migration.errors.isNotEmpty()) showSnack(migration.errors.joinToString("\n"))
            else if (migration.imported > 0) showSnack("Migrated ${migration.imported} legacy profiles into Room; original source retained.")
            val savedState = db.mapperStateDao().get()
            val restored = savedState?.activeProfileId?.let { db.configProfileDao().getProfileById(it) }
            if (restored != null) {
                try {
                    val config = ControlystRepository.deserializeJsonToConfig(restored.jsonBlob)
                    val errors = com.example.input.ProfileValidator.errors(config, restored.gamePackage, restored.id, false)
                    require(errors.isEmpty()) { errors.joinToString("; ") }
                    _activeConfig.value = config
                    _selectedGame.value = db.gameDao().getGame(config.gamePackage)
                    if (savedState.mappingEnabled) showSnack("Saved mapping-enabled preference restored. Launch the game to explicitly arm mapping after permissions are checked.")
                } catch (error: Exception) {
                    showSnack("Saved profile restore failed: ${error.message}")
                }
            }
            refreshPrivileges()
            detectController()
        }
    }

    private val _diagnostics = MutableStateFlow<String?>(null)
    val diagnostics = _diagnostics.asStateFlow()
    fun runSelfCheck() {
        viewModelScope.launch {
            try {
                _diagnostics.value = withContext(Dispatchers.IO) { com.example.diagnostics.ReleaseDiagnostics.collect(getApplication()) }
                showSnack("Device observations collected. Injection still requires the device test checklist.")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _diagnostics.value = "Self-check failed: ${error.message}"
                showSnack(_diagnostics.value!!)
            }
        }
    }

    fun selectTab(tab: String) {
        if(tab !in setOf("home","library","profiles","profile_detail","mapper","devices","system","fps","crosshair","calibration","profile_files","root_webui","macro","safety")) {
            showSnack("Screen unavailable: $tab")
            return
        }
        _currentTab.value = tab
    }

    fun showSnack(msg: String) {
        Log.i("NexusUI", msg)
        _snackMessage.value = msg
    }

    fun clearSnack() {
        _snackMessage.value = null
    }

    fun refreshPrivileges() {
        viewModelScope.launch {
            val probes = privilegeDetector.probeAll()
            _privilegeResults.value = probes
            val best = privilegeDetector.detectBestMethod()
            _activePrivilegeMethod.value = best
            currentInjector = InputInjectorFactory.createInjector(best)
        }
    }

    fun overridePrivilegeMethod(method: PrivilegeMethod) {
        updateActiveConfig(_activeConfig.value.copy(preferredBackend = method))
        _activePrivilegeMethod.value = method
        currentInjector = InputInjectorFactory.createInjector(method)
        showSnack("Switched injector to: ${method.title}")
    }

    fun detectController() {
        val detected = calibrationManager.detectConnectedController()
        _controllerProfile.value = detected
    }

    fun overrideControllerType(type: ControllerType) {
        _controllerProfile.value = _controllerProfile.value.copy(
            type = type,
            manualOverride = true
        )
        _activeConfig.value = _activeConfig.value.copy(controllerType = type)
        showSnack("Layout mapped to: ${type.displayName}")
    }

    private val _installedApps = MutableStateFlow<List<GameEntity>>(emptyList())
    val installedApps = _installedApps.asStateFlow()
    private val _appInventoryLoading = MutableStateFlow(false)
    val appInventoryLoading = _appInventoryLoading.asStateFlow()
    fun refreshInstalledApps() {
        viewModelScope.launch {
            _appInventoryLoading.value = true
            try { _installedApps.value = withContext(Dispatchers.IO) { repository.scanInstalledApps().distinctBy { it.packageName }.sortedBy { it.displayName.lowercase() } } }
            catch(error: Exception) { if(error is kotlinx.coroutines.CancellationException) throw error;showSnack("Installed app query failed: ${error.message}") }
            finally { _appInventoryLoading.value = false }
        }
    }
    private var selectionJob: kotlinx.coroutines.Job? = null
    fun selectGame(game: GameEntity) {
        selectionJob?.cancel()
        _selectedGame.value = game
        _activeConfig.value = MappingConfig(id="${game.packageName}_default",profileName="${game.displayName} Layout",gamePackage=game.packageName,gameTitle=game.displayName)
        selectionJob = viewModelScope.launch {
            try {
                val saved = db.configProfileDao().getDefaultForGame(game.packageName)
                if(saved != null) {
                    val config = ControlystRepository.deserializeJsonToConfig(saved.jsonBlob)
                    val errors = com.example.input.ProfileValidator.errors(config, game.packageName, saved.id, false)
                    require(errors.isEmpty()) { errors.joinToString("; ") }
                    _activeConfig.value = config
                }
            } catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                showSnack("Profile load failed: ${error.message}")
            }
        }
    }
    fun selectSavedProfile(entity: ConfigProfileEntity) {
        selectionJob?.cancel()
        selectionJob=viewModelScope.launch {
            try {
                val config=ControlystRepository.deserializeJsonToConfig(entity.jsonBlob)
                val errors=com.example.input.ProfileValidator.errors(config,entity.gamePackage,entity.id,false)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                _activeConfig.value=config
                _selectedGame.value=db.gameDao().getGame(config.gamePackage)
                profilePersistence.save(config)
            } catch(error:Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                showSnack("Profile selection failed: ${error.message}")
            }
        }
    }
    fun createProfile(name: String) {
        val game=_selectedGame.value ?: run { showSnack("Choose a game before creating a profile");return }
        if(name.isBlank()) { showSnack("Profile name is required");return }
        updateActiveConfig(MappingConfig(id=java.util.UUID.randomUUID().toString(),profileName=name.trim(),gamePackage=game.packageName,gameTitle=game.displayName)) {
            showSnack("Profile created. Add physical input bindings in Mapper.")
        }
    }

    fun launchGameWithMapping(game: GameEntity, context: Context) {
        viewModelScope.launch {
            try {
                selectionJob?.join()
                val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
                    ?: error("${game.displayName} is not installed or has no launch activity")
                require(com.example.service.ControlystAccessibilityService.isServiceRunning()) {
                    "Enable the NEXUS INPUT accessibility service before launching with mapping"
                }
                val config = if (_activeConfig.value.gamePackage == game.packageName) _activeConfig.value else {
                    val saved = db.configProfileDao().getDefaultForGame(game.packageName)
                        ?: error("Save a profile for ${game.displayName} before launching")
                    ControlystRepository.deserializeJsonToConfig(saved.jsonBlob)
                }
                val errors = com.example.input.ProfileValidator.errors(config, game.packageName)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                saveMutex.lock()
                try { profilePersistence.save(config) } finally { saveMutex.unlock() }
                val serviceIntent = Intent(context, MappingForegroundService::class.java).apply {
                    action = MappingForegroundService.ACTION_START_MAPPING
                    putExtra(MappingForegroundService.EXTRA_GAME_PACKAGE, game.packageName)
                    putExtra(MappingForegroundService.EXTRA_CONFIG_ID, config.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                else context.startService(serviceIntent)
                _selectedGame.value = game
                _activeConfig.value = config
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                showSnack("Launch requested for ${game.displayName}. Backend readiness appears in the mapping status.")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                com.example.service.MappingRuntimeBridge.reportError("Launch failed: ${error.message}")
                showSnack("Launch failed: ${error.message}")
            }
        }
    }

    fun updateActiveConfig(updated: MappingConfig, onSaved: () -> Unit = {}) {
        val errors = com.example.input.ProfileValidator.errors(updated, requireBindings = false)
        if (errors.isNotEmpty()) { showSnack("Profile rejected: " + errors.joinToString("; ")); return }
        _activeConfig.value = updated
        viewModelScope.launch {
            saveMutex.lock()
            try {
                profilePersistence.save(updated)
                onSaved()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                showSnack("Profile save failed: ${error.message}")
            } finally { saveMutex.unlock() }
        }
    }

    fun updateCrosshair(crosshair: CrosshairConfig) {
        val updated = _activeConfig.value.copy(crosshair = crosshair)
        updateActiveConfig(updated)
        MappingForegroundService.currentCrosshairConfig.value = crosshair
    }

    // Node editing functions
    fun addNode(node: MappingNode) {
        val list = _activeConfig.value.buttons.toMutableList()
        list.add(node)
        updateActiveConfig(_activeConfig.value.copy(buttons = list))
    }

    fun updateNode(node: MappingNode) {
        val list = _activeConfig.value.buttons.map { if (it.id == node.id) node else it }
        updateActiveConfig(_activeConfig.value.copy(buttons = list))
    }

    fun removeNode(id: String) {
        val list = _activeConfig.value.buttons.filterNot { it.id == id }
        updateActiveConfig(_activeConfig.value.copy(buttons = list))
    }

    private val _screenshot = MutableStateFlow<Bitmap?>(null)
    val screenshot: StateFlow<Bitmap?> = _screenshot.asStateFlow()

    fun importScreenshot(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected document is not a readable image" }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: error("Image decode failed")
                }
            }
            result.onSuccess { bitmap ->
                _screenshot.value = bitmap
                _aiHudCandidates.value = emptyList()
                showSnack("Screenshot imported: ${bitmap.width} × ${bitmap.height}. Review coordinates before saving.")
            }.onFailure {
                _screenshot.value = null
                _aiHudCandidates.value = emptyList()
                showSnack("Screenshot import failed: ${it.message}")
            }
        }
    }

    fun captureScreenshot() {
        val service = com.example.service.ControlystAccessibilityService.getInstance()
        if (service == null) { showSnack("Enable NEXUS INPUT accessibility service to capture; importing remains available."); return }
        service.captureScreenshot { result ->
            result.onSuccess { _screenshot.value = it; _aiHudCandidates.value = emptyList(); showSnack("Screenshot captured. Review before mapping.") }
                .onFailure { _screenshot.value = null; _aiHudCandidates.value = emptyList(); showSnack("Capture failed: ${it.message}") }
        }
    }

    fun assignHudCandidateInput(id: String, input: String) {
        _aiHudCandidates.value = _aiHudCandidates.value.map {
            if (it.id == id) it.copy(recommendedKey = input.trim().uppercase()) else it
        }
    }

    fun runAutoDetectHud() = runAiHudScan()

    fun addGame(game: GameEntity) {
        viewModelScope.launch {
            try {
                require(getApplication<Application>().packageManager.getLaunchIntentForPackage(game.packageName) != null) { "Application is no longer installed or launchable" }
                repository.insertGame(game)
                selectGame(game)
                showSnack("Added ${game.displayName} to library")
            } catch(error:Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                showSnack("Add game failed: ${error.message}")
            }
        }
    }

    fun startStickCalibration(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                if(!calibrationManager.runStickCalibration {}) {
                    showSnack("Stick calibration failed: ${stickCalibrationState.value.error ?: stickCalibrationState.value.phase}")
                }
            } finally { onComplete() }
        }
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            try {
                val entities=repository.allProfiles.first()
                val configs=entities.map { entity ->
                    val config=ControlystRepository.deserializeJsonToConfig(entity.jsonBlob)
                    val errors=com.example.input.ProfileValidator.errors(config,entity.gamePackage,entity.id,false)
                    require(errors.isEmpty()) { errors.joinToString("; ") };config
                }
                val zip=LocalBackupManager.exportFullBackupZip(context,configs)
                val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",zip)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type="application/zip";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },"Save or share NEXUS INPUT backup"))
                showSnack("Backup ZIP created with ${configs.size} saved profiles; choose where to save it.")
            } catch(error:Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                showSnack("Backup export failed: ${error.message}")
            }
        }
    }

    fun runLatencyBenchmark(onComplete: (TouchLatencyResult) -> Unit) {
        viewModelScope.launch {
            val res = calibrationManager.measureTouchLatency(currentInjector)
            onComplete(res)
        }
    }

    val shizukuPairingState: StateFlow<ShizukuPairingState> = ShizukuPairingManager.pairingState

    fun startShizukuPairingHelper(port: Int = 0) {
        ShizukuPairingManager.showPairingNotification(getApplication(), port)
        showSnack(ShizukuPairingManager.pairingState.value.statusMessage)
    }

    fun dismissShizukuPairingHelper() {
        ShizukuPairingManager.dismissHelper(getApplication())
    }

    fun submitShizukuPairingCode(code: String, port: Int = 0) {
        ShizukuPairingManager.handlePairingCodeReceived(getApplication(), code, port)
    }

    // Onboarding Navigation
    fun nextOnboardingStep() {
        if (_onboardingStep.value < 5) {
            _onboardingStep.value += 1
        } else {
            completeOnboarding()
        }
    }

    fun prevOnboardingStep() {
        if (_onboardingStep.value > 0) {
            _onboardingStep.value -= 1
        }
    }

    fun triggerPanicKillSwitch() {
        PanicKillSwitch.trigger(getApplication(), currentInjector)
        showSnack("Emergency stop requested; release status is shown in System diagnostics.")
    }

    fun runAiHudScan() {
        val bitmap = _screenshot.value
        viewModelScope.launch {
            val detection = withContext(Dispatchers.Default) { AiHudDetector.detect(bitmap) }
            if(bitmap !== _screenshot.value) { showSnack("Screenshot changed during detection; run the scan again");return@launch }
            _aiHudCandidates.value = detection.candidates
            showSnack(detection.error ?: "Found ${detection.candidates.size} contrast regions. Assign each input; action semantics are not detected.")
        }
    }

    fun confirmAiHudCandidates(candidates: List<AiHudCandidate>) {
        if (candidates.any { it.recommendedKey.isBlank() }) {
            showSnack("Assign a controller input to every selected contrast region first.")
            return
        }
        val newNodes = candidates.map { c ->
            MappingNode(
                id = c.id,
                xNorm = c.xNorm,
                yNorm = c.yNorm,
                radiusNorm = 0.055f,
                type = when (c.recommendedKey) { "LS" -> NodeType.JOYSTICK_ZONE; "RS" -> NodeType.CAMERA_DRAG; else -> NodeType.BUTTON },
                boundKey = c.recommendedKey,
                label = c.predictedAction
            )
        }
        val combined = _activeConfig.value.buttons.toMutableList()
        newNodes.forEach { n ->
            combined.removeAll { it.id == n.id }
            combined.add(n)
        }
        updateActiveConfig(_activeConfig.value.copy(buttons = combined))
        _aiHudCandidates.value = emptyList()
        showSnack("Assigned ${newNodes.size} image regions. Review the profile before mapping.")
    }

    fun dismissAiHudCandidates() {
        _aiHudCandidates.value = emptyList()
    }

    fun runConfigDiff() {
        viewModelScope.launch {
            val detection = AiHudDetector.detect(_screenshot.value)
            if (detection.error != null) { showSnack(detection.error); return@launch }
            val freshCandidates = detection.candidates
            val diffList = ConfigDiffEngine.calculateDiff(_activeConfig.value, freshCandidates)
            val result = ConfigDiffResult(
                unchangedCount = diffList.count { it.status == DiffStatus.UNCHANGED },
                movedNodes = diffList.filter { it.status == DiffStatus.MOVED },
                missingNodes = diffList.filter { it.status == DiffStatus.MISSING },
                newDetectedNodes = diffList.filter { it.status == DiffStatus.NEW_DETECTED }
            )
            _diffResult.value = result
            showSnack("HUD Diff: ${result.movedNodes.size} moved, ${result.missingNodes.size} missing, ${result.newDetectedNodes.size} new")
        }
    }

    fun applyDiffRepair() {
        val diff = _diffResult.value ?: return
        val allItems = diff.movedNodes + diff.missingNodes + diff.newDetectedNodes
        val repaired = ConfigDiffEngine.repairExistingConfig(_activeConfig.value, allItems)
        updateActiveConfig(repaired)
        _diffResult.value = null
        showSnack("Repaired config without rebuilding! Nodes shifted to match new HUD.")
    }

    fun dismissDiff() {
        _diffResult.value = null
    }

    fun completeOnboarding() {
        _onboardingStep.value = -1
    }

    fun restartOnboarding() {
        _onboardingStep.value = 0
    }

    fun startStickCalibration() {
        startStickCalibration {}
    }

    fun calibrateTriggerAndSave(left: Boolean) {
        viewModelScope.launch {
            if(!calibrationManager.runTriggerCalibration(left)) {
                showSnack("Trigger calibration failed: ${triggerCalibrationState.value.error ?: triggerCalibrationState.value.phase}");return@launch
            }
            val measured=triggerCalibrationState.value
            val key=if(left) "LT" else "RT"
            val targets=_activeConfig.value.buttons.filter { com.example.input.ControllerBindingAliases.canonical(it.boundKey)==key }
            if(targets.isEmpty()) { showSnack("Trigger measured; add a $key binding before saving its thresholds");return@launch }
            updateActiveConfig(_activeConfig.value.copy(buttons=_activeConfig.value.buttons.map { node ->
                if(node in targets) node.copy(triggerPressThreshold=measured.pressThreshold,triggerReleaseThreshold=measured.releaseThreshold) else node
            })) { showSnack("Measured $key thresholds saved to Room") }
        }
    }

    fun saveCalibrationToRoom(innerDZ: Float, outerDZ: Float) {
        val currentCfg = _activeConfig.value
        val updatedJoystick = currentCfg.joystick.copy(
            innerDeadzone = innerDZ,
            outerDeadzone = outerDZ
        )
        val updatedCfg = currentCfg.copy(joystick = updatedJoystick, buttons = currentCfg.buttons.map {
            if (it.type == NodeType.JOYSTICK_ZONE) it.copy(deadzoneInner = innerDZ, deadzoneOuter = outerDZ) else it
        })
        updateActiveConfig(updatedCfg) { showSnack("Calibration saved to Room database successfully!") }
    }
}
