package com.agentx.core.inference.routing

import com.agentx.core.inference.model.ExecutionMode
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationProvenance
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.provider.InferenceProvider
import com.agentx.core.inference.provider.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface RoutingPolicy {
    data class Manual(val providerId: String) : RoutingPolicy
    object LocalFirst : RoutingPolicy
    object RemoteFirst : RoutingPolicy
    object LowestLatency : RoutingPolicy
    data class CapabilityBased(val predicate: (ProviderCapabilities) -> Boolean) : RoutingPolicy
    object ExplicitHybrid : RoutingPolicy
}

/**
 * Intelligent Router that resolves the appropriate InferenceProvider based on policies,
 * provider health, device capabilities, and privacy rules.
 */
class ProviderRouter(
    private val registry: ProviderRegistry,
    var defaultPolicy: RoutingPolicy = RoutingPolicy.LocalFirst
) {

    suspend fun resolveProvider(
        request: GenerationRequest,
        policy: RoutingPolicy = defaultPolicy
    ): InferenceProvider {
        val all = registry.getAll()
        if (all.isEmpty()) {
            throw IllegalStateException("No inference providers registered in ProviderRegistry.")
        }

        return when (policy) {
            is RoutingPolicy.Manual -> {
                registry.get(policy.providerId)
                    ?: throw IllegalArgumentException("Requested provider '${policy.providerId}' is not registered.")
            }

            RoutingPolicy.LocalFirst -> {
                val local = all.firstOrNull { it.executionMode == ExecutionMode.LOCAL && it.healthCheck().isHealthy }
                local ?: all.firstOrNull { it.healthCheck().isHealthy }
                ?: all.first()
            }

            RoutingPolicy.RemoteFirst -> {
                val remote = all.firstOrNull { it.executionMode == ExecutionMode.REMOTE && it.healthCheck().isHealthy }
                remote ?: all.firstOrNull { it.healthCheck().isHealthy }
                ?: all.first()
            }

            RoutingPolicy.LowestLatency -> {
                val healthy = all.filter { it.healthCheck().isHealthy }
                if (healthy.isNotEmpty()) {
                    healthy.minByOrNull { it.healthCheck().latencyMs ?: Long.MAX_VALUE } ?: healthy.first()
                } else {
                    all.first()
                }
            }

            is RoutingPolicy.CapabilityBased -> {
                val matching = all.firstOrNull { policy.predicate(it.capabilities()) && it.healthCheck().isHealthy }
                matching ?: all.firstOrNull { policy.predicate(it.capabilities()) }
                ?: throw IllegalStateException("No provider satisfies the required capability constraints.")
            }

            RoutingPolicy.ExplicitHybrid -> {
                when (request.executionMode) {
                    ExecutionMode.LOCAL -> resolveProvider(request, RoutingPolicy.LocalFirst)
                    ExecutionMode.REMOTE -> resolveProvider(request, RoutingPolicy.RemoteFirst)
                    ExecutionMode.HYBRID -> resolveProvider(request, RoutingPolicy.LowestLatency)
                }
            }
        }
    }

    /**
     * Executes generation with strict provenance tracking and zero-stream-mixing fallback.
     *
     * Invariant: Never concatenate output from different models as though it were one continuous generation.
     * If a provider fails after emitting tokens, the error is delivered to the caller.
     * Fallback is only attempted if zero tokens have been emitted.
     */
    fun routeAndGenerate(
        request: GenerationRequest,
        policy: RoutingPolicy = defaultPolicy
    ): Flow<Pair<GenerationEvent, GenerationProvenance?>> = flow {
        val primaryProvider = resolveProvider(request, policy)
        var hasEmittedTokens = false
        var primaryFailed = false
        var failureCause: Throwable? = null
        val startTime = System.currentTimeMillis()
        var emittedTokensCount = 0

        try {
            primaryProvider.generate(request).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        hasEmittedTokens = true
                        emittedTokensCount++
                        emit(Pair(event, null))
                    }
                    is GenerationEvent.Finished -> {
                        val duration = System.currentTimeMillis() - startTime
                        val prov = GenerationProvenance(
                            providerId = primaryProvider.id,
                            providerName = primaryProvider.displayName,
                            modelId = event.modelId,
                            executionMode = primaryProvider.executionMode,
                            durationMs = duration,
                            totalTokens = event.totalTokens,
                            tokensPerSecond = if (duration > 50) ((event.totalTokens / (duration / 1000.0)) * 10).toInt() / 10.0 else 0.0
                        )
                        emit(Pair(event, prov))
                    }
                    is GenerationEvent.Error -> {
                        if (!hasEmittedTokens) {
                            primaryFailed = true
                            failureCause = event.cause
                        } else {
                            emit(Pair(event, null))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            if (!hasEmittedTokens) {
                primaryFailed = true
                failureCause = t
            } else {
                emit(Pair(GenerationEvent.Error(t, t.message ?: "Generation error", primaryProvider.id), null))
            }
        }

        // Clean Fallback only when zero tokens were emitted
        if (primaryFailed) {
            val fallback = registry.getAll().firstOrNull { it.id != primaryProvider.id && it.healthCheck().isHealthy }
            if (fallback != null) {
                val fallbackStart = System.currentTimeMillis()
                var fallbackCount = 0
                fallback.generate(request).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            fallbackCount++
                            emit(Pair(event, null))
                        }
                        is GenerationEvent.Finished -> {
                            val duration = System.currentTimeMillis() - fallbackStart
                            val prov = GenerationProvenance(
                                providerId = fallback.id,
                                providerName = fallback.displayName,
                                modelId = event.modelId,
                                executionMode = fallback.executionMode,
                                durationMs = duration,
                                totalTokens = event.totalTokens,
                                tokensPerSecond = if (duration > 50) ((event.totalTokens / (duration / 1000.0)) * 10).toInt() / 10.0 else 0.0
                            )
                            emit(Pair(event, prov))
                        }
                        is GenerationEvent.Error -> {
                            emit(Pair(event, null))
                        }
                    }
                }
            } else {
                val err = failureCause ?: IllegalStateException("Primary provider '${primaryProvider.id}' failed and no fallback available.")
                emit(Pair(GenerationEvent.Error(err, err.message ?: "Inference failure", primaryProvider.id), null))
            }
        }
    }
}
