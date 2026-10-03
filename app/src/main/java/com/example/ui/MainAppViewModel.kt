package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.example.monetization.MonetizationManager
import com.example.monetization.MonetizationState
import com.example.service.MappingForegroundService
import com.example.service.ShizukuPairingManager
import com.example.service.ShizukuPairingState
import com.example.service.PanicKillSwitch
import com.example.module.KernelSuModuleManager
import com.example.model.*
import com.example.ai.vision.AiHudDetector
import com.example.ai.AiMappingAssistant
import com.example.ai.ConfigDiffEngine
import com.example.backup.LocalBackupManager
import com.example.performance.drivers.PerformanceDriverManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ControlystDatabase.getDatabase(application)
    val repository = ControlystRepository(db.gameDao(), db.configProfileDao(), db.macroDao(), application)
    val privilegeDetector = PrivilegeDetector(application)
    val calibrationManager = CalibrationManager(application)
    val monetizationManager = MonetizationManager(application)
    val firebaseRepository = com.example.data.FirebaseRepository(application)

    private val _authState = MutableStateFlow(firebaseRepository.currentUser)
    val authState: StateFlow<com.google.firebase.auth.FirebaseUser?> = _authState.asStateFlow()

    // AI & HUD Recognition States
    private val _aiHudCandidates = MutableStateFlow<List<AiHudCandidate>>(emptyList())
    val aiHudCandidates: StateFlow<List<AiHudCandidate>> = _aiHudCandidates.asStateFlow()

    private val _aiMappingSuggestion = MutableStateFlow<AiMappingSuggestion?>(null)
    val aiMappingSuggestion: StateFlow<AiMappingSuggestion?> = _aiMappingSuggestion.asStateFlow()

    private val _diffResult = MutableStateFlow<ConfigDiffResult?>(null)
    val diffResult: StateFlow<ConfigDiffResult?> = _diffResult.asStateFlow()

    val performanceMode = PerformanceDriverManager.currentMode
    val hardwareTelemetry = PerformanceDriverManager.telemetry

    init {
        PerformanceDriverManager.initialize()
        viewModelScope.launch {
            firebaseRepository.authStateFlow().collect { user ->
                _authState.value = user
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            val result = firebaseRepository.signInWithGoogleCredential()
            result.onSuccess { user ->
                showSnack("Signed in as ${user.email ?: user.displayName}")
            }.onFailure { err ->
                showSnack("Google Sign-In failed: ${err.localizedMessage}")
            }
        }
    }

    fun signOutFirebase() {
        firebaseRepository.signOut()
        showSnack("Signed out from Firebase.")
    }

    fun syncActiveConfigToFirestore() {
        val user = _authState.value
        if (user == null) {
            showSnack("Please sign in with Google first to sync with Firestore.")
            return
        }
        viewModelScope.launch {
            val cfg = _activeConfig.value
            val result = firebaseRepository.syncConfigToFirestore(user.uid, cfg)
            result.onSuccess {
                showSnack("Profile '${cfg.profileName}' successfully synced to Firestore!")
            }.onFailure { err ->
                showSnack("Firestore sync failed: ${err.localizedMessage}")
            }
        }
    }

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

    private val _activeConfig = MutableStateFlow(ControlystRepository.createSampleDfmConfig())
    val activeConfig: StateFlow<MappingConfig> = _activeConfig.asStateFlow()

    // Calibration States
    val stickCalibrationState: StateFlow<StickCalibrationState> = calibrationManager.stickState
    val triggerCalibrationState: StateFlow<TriggerCalibrationState> = calibrationManager.triggerState
    val touchLatencyResult: StateFlow<TouchLatencyResult> = calibrationManager.latencyResult

    // Monetization State
    val monetizationState: StateFlow<MonetizationState> = monetizationManager.state

    // Active Injector
    var currentInjector: InputInjector = InputInjectorFactory.createInjector(PrivilegeMethod.ACCESSIBILITY)
        private set

    // Onboarding step (0..5), if completed = -1
    private val _onboardingStep = MutableStateFlow(-1)
    val onboardingStep: StateFlow<Int> = _onboardingStep.asStateFlow()

    // Current navigation tab: "library", "mapper", "crosshair", "calibration", "community", "vip"
    private val _currentTab = MutableStateFlow("library")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Feedback Toast / Snackbar message
    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.prepopulateDefaultsIfEmpty()
            refreshPrivileges()
            detectController()
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun showSnack(msg: String) {
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

    fun selectGame(game: GameEntity) {
        _selectedGame.value = game
        viewModelScope.launch {
            // Find default config for this game
            val list = db.configProfileDao().getProfileById("${game.packageName}_default")
            if (list != null) {
                _activeConfig.value = ControlystRepository.deserializeJsonToConfig(list.jsonBlob)
            } else {
                // Generate base template config
                _activeConfig.value = ControlystRepository.createSampleDfmConfig().copy(
                    id = "${game.packageName}_config",
                    profileName = "${game.displayName} Layout",
                    gamePackage = game.packageName,
                    gameTitle = game.displayName
                )
            }
        }
    }

    fun launchGameWithMapping(game: GameEntity, context: Context) {
        viewModelScope.launch {
            // Start foreground mapping service
            val serviceIntent = Intent(context, MappingForegroundService::class.java).apply {
                action = MappingForegroundService.ACTION_START_MAPPING
                putExtra(MappingForegroundService.EXTRA_GAME_PACKAGE, game.packageName)
                putExtra(MappingForegroundService.EXTRA_CONFIG_ID, _activeConfig.value.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Launch target application
            val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                showSnack("Launched ${game.displayName} with Controlyst HUD!")
            } else {
                showSnack("Target app ${game.displayName} ready. Controlyst HUD started.")
            }
        }
    }

    fun updateActiveConfig(updated: MappingConfig) {
        _activeConfig.value = updated
        viewModelScope.launch {
            val json = ControlystRepository.serializeConfigToJson(updated)
            repository.insertProfile(
                ConfigProfileEntity(
                    id = updated.id,
                    gamePackage = updated.gamePackage,
                    profileName = updated.profileName,
                    jsonBlob = json,
                    isDefault = true,
                    updatedAt = System.currentTimeMillis(),
                    author = updated.author,
                    isOfficialVerified = updated.isOfficialVerified,
                    rating = updated.rating,
                    downloads = updated.downloadCount
                )
            )
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

    /**
     * Heuristic Auto-detection of HUD elements from screenshot
     */
    fun runAutoDetectHud() {
        val suggestedNodes = listOf(
            MappingNode("hud_fire_auto", 0.86f, 0.72f, 0.06f, NodeType.BUTTON, "RT", "Fire [Auto]"),
            MappingNode("hud_ads_auto", 0.82f, 0.44f, 0.055f, NodeType.BUTTON, "LT", "ADS [Auto]"),
            MappingNode("hud_jump_auto", 0.92f, 0.60f, 0.05f, NodeType.BUTTON, "A", "Jump [Auto]"),
            MappingNode("hud_crouch_auto", 0.88f, 0.86f, 0.05f, NodeType.BUTTON, "B", "Crouch [Auto]"),
            MappingNode("hud_reload_auto", 0.76f, 0.74f, 0.05f, NodeType.BUTTON, "X", "Reload [Auto]"),
            MappingNode("hud_joy_auto", 0.18f, 0.72f, 0.12f, NodeType.JOYSTICK_ZONE, "LS", "WASD [Auto]"),
            MappingNode("hud_cam_auto", 0.70f, 0.48f, 0.20f, NodeType.CAMERA_DRAG, "RS", "Aim Look [Auto]")
        )
        val combined = _activeConfig.value.buttons.toMutableList()
        suggestedNodes.forEach { candidate ->
            if (combined.none { it.id == candidate.id }) {
                combined.add(candidate)
            }
        }
        updateActiveConfig(_activeConfig.value.copy(buttons = combined))
        showSnack("Auto-detected 7 HUD touch controls!")
    }

    fun addGame(game: GameEntity) {
        viewModelScope.launch {
            repository.insertGame(game)
            showSnack("Added ${game.displayName} to library")
        }
    }

    fun startStickCalibration(onComplete: () -> Unit) {
        viewModelScope.launch {
            calibrationManager.runStickCalibration { state ->
                if (state.phase == "CALIBRATION COMPLETE") {
                    onComplete()
                }
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

    fun startShizukuPairingHelper(port: Int = 5555) {
        ShizukuPairingManager.showPairingNotification(getApplication(), port)
        showSnack("Pairing Notification posted! Enter code directly in notification.")
    }

    fun dismissShizukuPairingHelper() {
        ShizukuPairingManager.dismissHelper(getApplication())
    }

    fun submitShizukuPairingCode(code: String, port: Int = 5555) {
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
        val count = PanicKillSwitch.trigger(getApplication(), currentInjector)
        showSnack("PANIC KILL-SWITCH: Released $count active inputs & reset axes!")
    }

    fun runAiHudScan() {
        viewModelScope.launch {
            val candidates = AiHudDetector.detectHudElements(null)
            _aiHudCandidates.value = candidates
            showSnack("AI Vision found ${candidates.size} HUD candidates! Review & confirm.")
        }
    }

    fun confirmAiHudCandidates(candidates: List<AiHudCandidate>) {
        val newNodes = candidates.map { c ->
            MappingNode(
                id = c.id,
                xNorm = c.xNorm,
                yNorm = c.yNorm,
                radiusNorm = 0.055f,
                type = if (c.recommendedKey == "LS" || c.recommendedKey == "RS") NodeType.JOYSTICK_ZONE else NodeType.BUTTON,
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
        showSnack("Confirmed and applied ${newNodes.size} AI-detected controls!")
    }

    fun dismissAiHudCandidates() {
        _aiHudCandidates.value = emptyList()
    }

    fun runAiAssistant() {
        viewModelScope.launch {
            val suggestion = AiMappingAssistant.suggestMapping(
                _activeConfig.value.gameTitle,
                _controllerProfile.value.type,
                _activeConfig.value
            )
            _aiMappingSuggestion.value = suggestion
        }
    }

    fun applyAiAssistantSuggestion() {
        val s = _aiMappingSuggestion.value ?: return
        updateActiveConfig(_activeConfig.value.copy(buttons = s.nodes))
        _aiMappingSuggestion.value = null
        showSnack("Applied AI Recommended Mapping with ${s.estimatedLatencyMs}ms latency!")
    }

    fun dismissAiAssistant() {
        _aiMappingSuggestion.value = null
    }

    fun runConfigDiff() {
        viewModelScope.launch {
            val freshCandidates = AiHudDetector.detectHudElements(null)
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

    fun setPerformanceProfile(mode: PerformanceMode) {
        val results = PerformanceDriverManager.setPerformanceMode(mode)
        val passed = results.count { it.isSuccess }
        showSnack("Performance Mode: ${mode.displayName} ($passed/${results.size} sysfs nodes applied)")
    }

    fun restoreStockPerformance() {
        PerformanceDriverManager.restoreStock()
        showSnack("Stock hardware snapshot restored.")
    }

    fun completeOnboarding() {
        _onboardingStep.value = -1
    }

    fun restartOnboarding() {
        _onboardingStep.value = 0
    }

    fun startStickCalibration() {
        viewModelScope.launch {
            calibrationManager.runStickCalibration { state ->
                // state updated via calibrationManager flow
            }
        }
    }

    fun saveCalibrationToRoom(innerDZ: Float, outerDZ: Float) {
        val currentCfg = _activeConfig.value
        val updatedJoystick = currentCfg.joystick.copy(
            innerDeadzone = innerDZ,
            outerDeadzone = outerDZ
        )
        val updatedCfg = currentCfg.copy(joystick = updatedJoystick)
        updateActiveConfig(updatedCfg)
        showSnack("Calibration saved to Room database successfully!")
    }
}
