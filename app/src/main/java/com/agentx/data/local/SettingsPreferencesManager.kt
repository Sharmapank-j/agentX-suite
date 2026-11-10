package com.agentx.data.local

import android.content.Context
import android.content.SharedPreferences
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.InferenceMode
import com.agentx.core.inference.LocalBackendType
import com.agentx.core.security.SecureCredentialStore

/**
 * Persistent SharedPreferences storage for user settings, gateway endpoints,
 * model configurations, and runtime flags. Ensures settings persist across
 * activity recreations and app process restarts.
 */
class SettingsPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "agentx_user_preferences"
        private const val KEY_INFERENCE_MODE = "inference_mode"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GATEWAY_API_KEY = "gateway_api_key"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_LOCAL_BACKEND = "local_backend"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_THREADS = "cpu_threads"
        private const val KEY_MLOCK = "enable_mlock"
        private const val KEY_MULTIMODAL_QUALITY = "multimodal_quality"
        private const val KEY_MULTIMODAL_ENABLED = "multimodal_enabled"
    }

    fun loadCloudConfig(): CloudModelConfig {
        val providerStr = prefs.getString(KEY_CLOUD_PROVIDER, CloudProvider.GEMINI.name) ?: CloudProvider.GEMINI.name
        val provider = try {
            CloudProvider.valueOf(providerStr)
        } catch (_: Exception) {
            CloudProvider.GEMINI
        }

        val modeStr = prefs.getString(KEY_INFERENCE_MODE, InferenceMode.HYBRID_AUTO.name) ?: InferenceMode.HYBRID_AUTO.name
        val mode = try {
            InferenceMode.valueOf(modeStr)
        } catch (_: Exception) {
            InferenceMode.HYBRID_AUTO
        }

        val legacyApiKey = SecureCredentialStore.decrypt(prefs.getString(KEY_API_KEY, "") ?: "")
        val geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, null)?.let { SecureCredentialStore.decrypt(it) }
            ?: if (provider == CloudProvider.GEMINI) legacyApiKey else ""
        val gatewayApiKey = prefs.getString(KEY_GATEWAY_API_KEY, null)?.let { SecureCredentialStore.decrypt(it) }
            ?: if (provider == CloudProvider.CUSTOM_GATEWAY) legacyApiKey else ""
        val modelName = prefs.getString(KEY_MODEL_NAME, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        val customBaseUrl = prefs.getString(KEY_CUSTOM_BASE_URL, "http://192.168.1.100:8080/v1") ?: "http://192.168.1.100:8080/v1"

        return CloudModelConfig(
            provider = provider,
            geminiApiKey = geminiApiKey,
            gatewayApiKey = gatewayApiKey,
            apiKey = "",
            modelName = modelName,
            customBaseUrl = customBaseUrl,
            inferenceMode = mode,
            isEnabled = true
        )
    }

    fun saveCloudConfig(config: CloudModelConfig) {
        prefs.edit()
            .putString(KEY_CLOUD_PROVIDER, config.provider.name)
            .putString(KEY_INFERENCE_MODE, config.inferenceMode.name)
            .putString(KEY_GEMINI_API_KEY, SecureCredentialStore.encrypt(config.geminiApiKey))
            .putString(KEY_GATEWAY_API_KEY, SecureCredentialStore.encrypt(config.gatewayApiKey))
            .remove(KEY_API_KEY)
            .putString(KEY_MODEL_NAME, config.modelName)
            .putString(KEY_CUSTOM_BASE_URL, config.customBaseUrl)
            .apply()
    }

    fun loadInferenceMode(): InferenceMode {
        val modeStr = prefs.getString(KEY_INFERENCE_MODE, InferenceMode.LOCAL_ONLY.name) ?: InferenceMode.LOCAL_ONLY.name
        return try {
            InferenceMode.valueOf(modeStr)
        } catch (_: Exception) {
            InferenceMode.LOCAL_ONLY
        }
    }

    fun saveInferenceMode(mode: InferenceMode) {
        prefs.edit().putString(KEY_INFERENCE_MODE, mode.name).apply()
    }

    fun loadLocalBackend(): LocalBackendType {
        val backendStr = prefs.getString(KEY_LOCAL_BACKEND, LocalBackendType.LLAMA_CPP.name) ?: LocalBackendType.LLAMA_CPP.name
        return try {
            LocalBackendType.valueOf(backendStr)
        } catch (_: Exception) {
            LocalBackendType.LLAMA_CPP
        }
    }

    fun saveLocalBackend(backend: LocalBackendType) {
        prefs.edit().putString(KEY_LOCAL_BACKEND, backend.name).apply()
    }

    fun loadDarkTheme(): Boolean = prefs.getBoolean(KEY_DARK_THEME, true)
    fun saveDarkTheme(enabled: Boolean) = prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()

    fun loadHapticFeedback(): Boolean = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
    fun saveHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        com.agentx.core.device.HapticFeedbackHelper.isEnabled = enabled
    }

    fun loadThreads(): Int = prefs.getInt(KEY_THREADS, 4)
    fun saveThreads(threads: Int) = prefs.edit().putInt(KEY_THREADS, threads).apply()

    fun loadMlock(): Boolean = prefs.getBoolean(KEY_MLOCK, false)
    fun saveMlock(enabled: Boolean) = prefs.edit().putBoolean(KEY_MLOCK, enabled).apply()

    fun loadMultimodalQuality(): String = prefs.getString(KEY_MULTIMODAL_QUALITY, "BALANCED") ?: "BALANCED"
    fun saveMultimodalQuality(quality: String) = prefs.edit().putString(KEY_MULTIMODAL_QUALITY, quality).apply()

    fun loadMultimodalInputEnabled(): Boolean = prefs.getBoolean(KEY_MULTIMODAL_ENABLED, true)
    fun saveMultimodalInputEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_MULTIMODAL_ENABLED, enabled).apply()

    fun loadSavedPairingCode(): String? = prefs.getString("saved_pairing_code", null)
    fun saveSavedPairingCode(code: String) = prefs.edit().putString("saved_pairing_code", code).apply()

    fun loadPairedServerName(): String? = prefs.getString("paired_server_name", null)
    fun savePairedServerName(name: String) = prefs.edit().putString("paired_server_name", name).apply()
}
