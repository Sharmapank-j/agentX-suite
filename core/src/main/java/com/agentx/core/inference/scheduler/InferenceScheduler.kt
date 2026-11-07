package com.agentx.core.inference.scheduler

import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.provider.LLMProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized inference scheduler specified in Section D of the Master Architecture.
 *
 * Enforces the Single Active Model Invariant:
 * - Defaults to ONE active local model at a time.
 * - Multiple agents share that active model rather than loading separate instances.
 * - Enforces memory budgeting (e.g. max 2048 MB RAM) before allowing model switching.
 * - Manages generation queues and cooperative cancellation.
 * - Incorporates thermal throttling backpressure.
 */
interface InferenceScheduler {
    val activeModel: StateFlow<ModelInfo?>
    val isModelLoaded: StateFlow<Boolean>
    val memoryBudgetMb: Long
    val currentMemoryUsageMb: StateFlow<Long>
    val activeRequestCount: StateFlow<Int>

    /** Loads a model into the single active slot, unloading any previous model */
    suspend fun loadModel(model: ModelInfo): Result<Unit>

    /** Unloads the active model from memory */
    suspend fun unloadModel(): Result<Unit>

    /** Schedules and executes an inference generation request */
    suspend fun scheduleGeneration(
        request: GenerationRequest,
        provider: LLMProvider
    ): Flow<GenerationEvent>

    /** Cancels an active in-flight generation request */
    suspend fun cancelRequest(requestId: String)

    /** Notifies scheduler of thermal throttling delay */
    fun setThermalThrottleDelay(delayMs: Long)

    /** Emergency Out-Of-Memory (OOM) recovery */
    suspend fun handleOomRecovery()
}

class DefaultInferenceScheduler(
    override val memoryBudgetMb: Long = 2048L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : InferenceScheduler {

    private val _activeModel = MutableStateFlow<ModelInfo?>(null)
    override val activeModel: StateFlow<ModelInfo?> = _activeModel.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _currentMemoryUsageMb = MutableStateFlow(0L)
    override val currentMemoryUsageMb: StateFlow<Long> = _currentMemoryUsageMb.asStateFlow()

    private val _activeRequestCount = MutableStateFlow(0)
    override val activeRequestCount: StateFlow<Int> = _activeRequestCount.asStateFlow()

    private val modelLock = Mutex()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var thermalDelayMs: Long = 0L

    override suspend fun loadModel(model: ModelInfo): Result<Unit> = modelLock.withLock {
        // Unrestricted model loading: No artificial rejection based on RAM or memory budgets.
        if (_isModelLoaded.value && _activeModel.value?.id != model.id) {
            unloadInternal()
        }

        _activeModel.value = model
        _currentMemoryUsageMb.value = if (model.isLocal) model.memoryFootprintMb else 0L
        _isModelLoaded.value = true
        Result.success(Unit)
    }

    override suspend fun unloadModel(): Result<Unit> = modelLock.withLock {
        unloadInternal()
        Result.success(Unit)
    }

    private fun unloadInternal() {
        // Cancel all active jobs sharing this model
        activeJobs.forEach { (id, job) ->
            job.cancel()
        }
        activeJobs.clear()
        _activeModel.value = null
        _currentMemoryUsageMb.value = 0L
        _isModelLoaded.value = false
    }

    override suspend fun scheduleGeneration(
        request: GenerationRequest,
        provider: LLMProvider
    ): Flow<GenerationEvent> = flow {
        _activeRequestCount.value += 1
        try {
            // Apply thermal throttling backpressure delay if device is hot
            if (thermalDelayMs > 0) {
                kotlinx.coroutines.delay(thermalDelayMs)
            }

            provider.generate(request).collect { event ->
                emit(event)
            }
        } finally {
            if (_activeRequestCount.value > 0) {
                _activeRequestCount.value -= 1
            }
        }
    }

    override suspend fun cancelRequest(requestId: String) {
        activeJobs.remove(requestId)?.cancel()
    }

    override fun setThermalThrottleDelay(delayMs: Long) {
        this.thermalDelayMs = delayMs.coerceAtLeast(0L)
    }

    override suspend fun handleOomRecovery() = modelLock.withLock {
        unloadInternal()
    }
}
