package com.agentx.medical.core.agent.execution

import com.agentx.medical.core.agent.core.AgentContext
import com.agentx.medical.core.agent.core.AgentResult
import com.agentx.medical.domain.models.AgentRole
import com.agentx.medical.domain.models.ExecutionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionState(
    val conversationId: String = "",
    val activeAgentId: String? = null,
    val activeRole: AgentRole? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val status: ExecutionStatus = ExecutionStatus.QUEUED,
    val currentActivity: String = "",
    val intermediateOutputs: List<String> = emptyList()
)

interface AgentExecutor {
    suspend fun executeAgent(agentId: String, context: AgentContext): AgentResult
    suspend fun cancel()
}

class ExecutionQueue {
    private val _state = MutableStateFlow(ExecutionState())
    val state: StateFlow<ExecutionState> = _state.asStateFlow()

    fun updateState(newState: ExecutionState) {
        _state.value = newState
    }

    fun updateProgress(
        conversationId: String,
        step: Int,
        total: Int,
        role: AgentRole?,
        agentId: String?,
        activity: String,
        intermediateOutputs: List<String> = _state.value.intermediateOutputs
    ) {
        _state.value = ExecutionState(
            conversationId = conversationId,
            activeAgentId = agentId,
            activeRole = role,
            currentStep = step,
            totalSteps = total,
            status = ExecutionStatus.RUNNING,
            currentActivity = activity,
            intermediateOutputs = intermediateOutputs
        )
    }

    fun complete(conversationId: String, finalSummary: String = "") {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            status = ExecutionStatus.COMPLETED,
            currentActivity = if (finalSummary.isNotBlank()) finalSummary else "Multi-agent clinical workflow complete."
        )
    }

    fun fail(conversationId: String, errorReason: String) {
        _state.value = _state.value.copy(
            conversationId = conversationId,
            status = ExecutionStatus.FAILED,
            currentActivity = "Workflow halted: $errorReason"
        )
    }

    fun reset() {
        _state.value = ExecutionState()
    }
}

