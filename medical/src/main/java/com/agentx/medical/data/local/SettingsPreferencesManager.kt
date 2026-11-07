package com.agentx.medical.data.local

import android.content.Context
import android.content.SharedPreferences
import com.agentx.medical.core.inference.CloudModelConfig
import com.agentx.medical.core.inference.CloudProvider
import com.agentx.medical.core.inference.InferenceMode
import com.agentx.medical.core.inference.LocalBackendType

/**
 * Persistent SharedPreferences storage for AgentX Medical clinical settings,
 * gateway endpoints, and runtime flags.
 */
class SettingsPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "agentx_medical_user_preferences"
        private const val KEY_INFERENCE_MODE = "inference_mode"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GATEWAY_API_KEY = "gateway_api_key"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_LOCAL_BACKEND = "local_backend"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_THREADS = "cpu_threads"
        private const val KEY_MLOCK = "enable_mlock"
    }

    fun loadCloudConfig(): CloudModelConfig {
        val providerStr = prefs.getString(KEY_CLOUD_PROVIDER, CloudProvider.CUSTOM_GATEWAY.name) ?: CloudProvider.CUSTOM_GATEWAY.name
        val provider = try {
            CloudProvider.valueOf(providerStr)
        } catch (_: Exception) {
            CloudProvider.CUSTOM_GATEWAY
        }

        val modeStr = prefs.getString(KEY_INFERENCE_MODE, InferenceMode.HYBRID_AUTO.name) ?: InferenceMode.HYBRID_AUTO.name
        val mode = try {
            InferenceMode.valueOf(modeStr)
        } catch (_: Exception) {
            InferenceMode.HYBRID_AUTO
        }

        val legacyApiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, null)
            ?: if (provider == CloudProvider.GEMINI) legacyApiKey else ""
        val gatewayApiKey = prefs.getString(KEY_GATEWAY_API_KEY, null)
            ?: if (provider == CloudProvider.CUSTOM_GATEWAY) legacyApiKey else ""
        val modelName = prefs.getString(KEY_MODEL_NAME, "clinical-llama-3b") ?: "clinical-llama-3b"
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
            .putString(KEY_GEMINI_API_KEY, config.geminiApiKey)
            .putString(KEY_GATEWAY_API_KEY, config.gatewayApiKey)
            .remove(KEY_API_KEY)
            .putString(KEY_MODEL_NAME, config.modelName)
            .putString(KEY_CUSTOM_BASE_URL, config.customBaseUrl)
            .apply()
    }

    fun loadInferenceMode(): InferenceMode {
        val modeStr = prefs.getString(KEY_INFERENCE_MODE, InferenceMode.HYBRID_AUTO.name) ?: InferenceMode.HYBRID_AUTO.name
        return try {
            InferenceMode.valueOf(modeStr)
        } catch (_: Exception) {
            InferenceMode.HYBRID_AUTO
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

    fun loadThreads(): Int = prefs.getInt(KEY_THREADS, 4)
    fun saveThreads(threads: Int) = prefs.edit().putInt(KEY_THREADS, threads).apply()

    fun loadMlock(): Boolean = prefs.getBoolean(KEY_MLOCK, false)
    fun saveMlock(enabled: Boolean) = prefs.edit().putBoolean(KEY_MLOCK, enabled).apply()
}
