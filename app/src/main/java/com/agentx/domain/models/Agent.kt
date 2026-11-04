package com.agentx.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val role: AgentRole,
    val systemPrompt: String,
    val modelId: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 1024,
    val assignedToolIds: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val icon: String = "robot"
)

@Serializable
data class AgentTeam(
    val id: String,
    val name: String,
    val description: String,
    val supervisorAgentId: String,
    val memberAgentIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
