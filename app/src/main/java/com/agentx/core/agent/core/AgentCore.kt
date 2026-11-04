package com.agentx.core.agent.core

import com.agentx.domain.models.AgentRole
import kotlinx.serialization.Serializable

@Serializable
data class AgentContext(
    val conversationId: String,
    val taskId: String,
    val userGoal: String,
    val intermediateResults: Map<String, String> = emptyMap(),
    val sharedMemory: List<String> = emptyList(),
    val currentIteration: Int = 0,
    val maxIterations: Int = 10,
    val isCancelled: Boolean = false
)

@Serializable
data class AgentMessage(
    val id: String,
    val senderAgentId: String,
    val recipientAgentId: String? = null,
    val content: String,
    val role: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ToolCallRequest(
    val toolId: String,
    val argumentsJson: String
)

@Serializable
data class ToolCallResult(
    val toolId: String,
    val output: String,
    val isSuccess: Boolean,
    val error: String? = null
)

@Serializable
data class AgentResult(
    val agentId: String,
    val taskId: String,
    val output: String,
    val toolCalls: List<ToolCallResult> = emptyList(),
    val executionStatus: String = "COMPLETED",
    val tokenUsage: Int = 0,
    val executionTimeMs: Long = 0L,
    val errorDetails: String? = null
)

interface AgentInstance {
    val id: String
    val name: String
    val role: AgentRole
    val systemPrompt: String
    suspend fun execute(context: AgentContext): AgentResult
}
