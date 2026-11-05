package com.agentx.core.inference.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry holding available local and remote inference providers.
 */
class ProviderRegistry {

    private val providers = ConcurrentHashMap<String, InferenceProvider>()
    private val _registeredProviders = MutableStateFlow<List<InferenceProvider>>(emptyList())
    val registeredProviders: StateFlow<List<InferenceProvider>> = _registeredProviders.asStateFlow()

    fun register(provider: InferenceProvider) {
        providers[provider.id] = provider
        _registeredProviders.value = providers.values.toList()
    }

    fun unregister(providerId: String): InferenceProvider? {
        val removed = providers.remove(providerId)
        _registeredProviders.value = providers.values.toList()
        return removed
    }

    fun get(providerId: String): InferenceProvider? = providers[providerId]

    fun getAll(): List<InferenceProvider> = providers.values.toList()

    suspend fun getHealthyProviders(): List<InferenceProvider> {
        return providers.values.filter {
            try {
                it.healthCheck().isHealthy
            } catch (_: Exception) {
                false
            }
        }
    }
}
