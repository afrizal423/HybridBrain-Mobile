package com.afrizalmy.hybridbrain.data.local

import android.content.Context
import android.content.SharedPreferences
import com.afrizalmy.hybridbrain.data.model.AppSettings
import com.afrizalmy.hybridbrain.data.model.EngineMode

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hybridbrain_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CLOUD_MODEL_NAME = "cloud_model_name"
        private const val KEY_PTAC_ENABLED = "ptac_enabled"
        private const val KEY_PTAC_MAX_LOOPS = "ptac_max_loops"
        private const val KEY_ACTIVE_ROOM_ID = "active_room_id"
        private const val KEY_PREFERRED_BACKEND = "preferred_backend"
        private const val KEY_CUSTOM_GEMMA_PATH = "custom_gemma_path"
        private const val KEY_CUSTOM_EMBEDDING_PATH = "custom_embedding_path"
    }

    fun loadSettings(): AppSettings {
        val modeStr = prefs.getString(KEY_ENGINE_MODE, EngineMode.OFFLINE_EDGE.name) ?: EngineMode.OFFLINE_EDGE.name
        val mode = try {
            EngineMode.valueOf(modeStr)
        } catch (e: Exception) {
            EngineMode.OFFLINE_EDGE
        }

        return AppSettings(
            engineMode = mode,
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
            cloudModelName = prefs.getString(KEY_CLOUD_MODEL_NAME, "gemini-3.8-flash") ?: "gemini-3.8-flash",
            ptacEnabled = prefs.getBoolean(KEY_PTAC_ENABLED, true),
            ptacMaxLoops = prefs.getInt(KEY_PTAC_MAX_LOOPS, 20),
            activeRoomId = prefs.getString(KEY_ACTIVE_ROOM_ID, "default_room") ?: "default_room",
            preferredBackend = prefs.getString(KEY_PREFERRED_BACKEND, "AUTO") ?: "AUTO"
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_ENGINE_MODE, settings.engineMode.name)
            .putString(KEY_GEMINI_API_KEY, settings.geminiApiKey)
            .putString(KEY_CLOUD_MODEL_NAME, settings.cloudModelName)
            .putBoolean(KEY_PTAC_ENABLED, settings.ptacEnabled)
            .putInt(KEY_PTAC_MAX_LOOPS, settings.ptacMaxLoops)
            .putString(KEY_ACTIVE_ROOM_ID, settings.activeRoomId)
            .putString(KEY_PREFERRED_BACKEND, settings.preferredBackend)
            .apply()
    }

    fun getCustomGemmaPath(): String? = prefs.getString(KEY_CUSTOM_GEMMA_PATH, null)
    fun setCustomGemmaPath(path: String?) = prefs.edit().putString(KEY_CUSTOM_GEMMA_PATH, path).apply()

    fun getCustomEmbeddingPath(): String? = prefs.getString(KEY_CUSTOM_EMBEDDING_PATH, null)
    fun setCustomEmbeddingPath(path: String?) = prefs.edit().putString(KEY_CUSTOM_EMBEDDING_PATH, path).apply()
}
