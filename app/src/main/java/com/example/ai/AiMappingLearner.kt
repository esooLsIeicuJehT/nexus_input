package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.model.GameGenre
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class UserMappingPreferences(
    val preferredStickDeadzone: Float = 0.15f,
    val preferredCameraSensHorizontal: Float = 1.0f,
    val preferredCameraSensVertical: Float = 0.85f,
    val preferredTriggerThreshold: Float = 0.10f,
    val preferredGenreStyles: Map<String, String> = emptyMap(),
    val totalCorrectionsLearned: Int = 0
)

object AiMappingLearner {
    private const val PREFS_NAME = "controlyst_ai_learner_prefs"
    private const val KEY_PREFS_JSON = "key_user_learned_preferences"

    private val _userPreferences = MutableStateFlow(UserMappingPreferences())
    val userPreferences: StateFlow<UserMappingPreferences> = _userPreferences.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PREFS_JSON, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val json = JSONObject(jsonString)
                _userPreferences.value = UserMappingPreferences(
                    preferredStickDeadzone = json.optDouble("deadzone", 0.15).toFloat(),
                    preferredCameraSensHorizontal = json.optDouble("sensH", 1.0).toFloat(),
                    preferredCameraSensVertical = json.optDouble("sensV", 0.85).toFloat(),
                    preferredTriggerThreshold = json.optDouble("triggerThresh", 0.10).toFloat(),
                    totalCorrectionsLearned = json.optInt("learnedCount", 0)
                )
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    fun recordManualCorrection(context: Context, genre: GameGenre, nodeBoundKey: String, adjustedX: Float, adjustedY: Float) {
        val current = _userPreferences.value
        val updated = current.copy(
            totalCorrectionsLearned = current.totalCorrectionsLearned + 1
        )
        _userPreferences.value = updated
        persist(context, updated)
    }

    fun recordDeadzonePreference(context: Context, deadzone: Float, sensH: Float, sensV: Float) {
        val current = _userPreferences.value
        val updated = current.copy(
            preferredStickDeadzone = deadzone,
            preferredCameraSensHorizontal = sensH,
            preferredCameraSensVertical = sensV
        )
        _userPreferences.value = updated
        persist(context, updated)
    }

    private fun persist(context: Context, prefs: UserMappingPreferences) {
        try {
            val json = JSONObject().apply {
                put("deadzone", prefs.preferredStickDeadzone.toDouble())
                put("sensH", prefs.preferredCameraSensHorizontal.toDouble())
                put("sensV", prefs.preferredCameraSensVertical.toDouble())
                put("triggerThresh", prefs.preferredTriggerThreshold.toDouble())
                put("learnedCount", prefs.totalCorrectionsLearned)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFS_JSON, json.toString())
                .apply()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
