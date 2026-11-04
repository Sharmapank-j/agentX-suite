package com.agentx.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    AGENT
}

@Serializable
enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    ERROR
}

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val agentId: String? = null,
    val tokenCount: Int = 0,
    val status: MessageStatus = MessageStatus.COMPLETED
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val activeAgentId: String? = null,
    val activeTeamId: String? = null,
    val isPinned: Boolean = false
)

@Serializable
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val requiresPermission: Boolean = true,
    val schemaJson: String = "{}"
)

@Serializable
data class ToolPermission(
    val toolId: String,
    val granted: Boolean = false,
    val grantedAt: Long? = null,
    val autoApprove: Boolean = false
)

@Serializable
enum class ExecutionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class ExecutionLog(
    val id: String,
    val conversationId: String,
    val agentId: String,
    val taskId: String,
    val status: ExecutionStatus,
    val inputContext: String,
    val outputResult: String,
    val toolCallsJson: String = "[]",
    val tokenUsage: Int = 0,
    val executionTimeMs: Long = 0L,
    val errorDetails: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Serializable
data class Memory(
    val id: String,
    val category: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Serializable
data class GGUFModel(
    val id: String,
    val name: String,
    val filePath: String,
    val uriString: String,
    val sizeBytes: Long,
    val architecture: String = "llama",
    val contextLength: Int = 2048,
    val tensorCount: Long = 0L,
    val format: String = "GGUF",
    val quantization: String = "Q4_K_M",
    val capabilityProfile: String = "medical-clinical",
    val isStaged: Boolean = false,
    val isActive: Boolean = false,
    val isLoaded: Boolean = false,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val createdAt: Long = System.currentTimeMillis()
)
