package com.agentx.core.agent.framework

import com.agentx.core.inference.model.ModelInfo
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Advanced Multi-Agent Framework contracts specified in Section E of the Master Architecture.
 */

@Serializable
enum class AgentRole(val displayName: String, val purpose: String) {
    SUPERVISOR("Supervisor", "Coordinates team milestones, monitors queue dependencies, and oversees execution."),
    PLANNER("Planner", "Decomposes complex requests into a directed acyclic graph (DAG) of subtasks."),
    RESEARCHER("Researcher", "Retrieves factual context, literature citations, and verified documentation."),
    SPECIALIST("Specialist", "Domain-specialized worker (Coder, Pathologist, Cardiologist, Systems Architect)."),
    REVIEWER("Reviewer", "Quality, safety, and contraindication audit barrier before final synthesis."),
    SYNTHESIZER("Synthesizer", "Merges individual subtask findings into cohesive, beautifully styled responses.")
}

@Serializable
enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    WAITING_APPROVAL
}

@Serializable
data class AgentTask(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val description: String,
    val assignedRole: AgentRole,
    val dependencies: List<String> = emptyList(), // Task IDs that must finish first
    val status: TaskStatus = TaskStatus.PENDING,
    val result: String? = null,
    val requiresHumanApproval: Boolean = false
)

@Serializable
data class TaskDependencyGraph(
    val planId: String = UUID.randomUUID().toString().take(8),
    val goal: String,
    val tasks: List<AgentTask> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
) {
    /** Returns all tasks whose dependencies are fully resolved */
    fun getExecutableTasks(): List<AgentTask> {
        val completedIds = tasks.filter { it.status == TaskStatus.COMPLETED }.map { it.id }.toSet()
        return tasks.filter { task ->
            task.status == TaskStatus.PENDING && task.dependencies.all { it in completedIds }
        }
    }

    /** Returns true if all tasks in the DAG are completed */
    fun isFinished(): Boolean = tasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.SKIPPED }
}

@Serializable
data class ExecutionBudget(
    val maxIterations: Int = 5,
    val maxTokens: Int = 4096,
    val timeoutMs: Long = 60_000L,
    val allowParallelExecution: Boolean = true
)

@Serializable
data class AgentMessage(
    val id: String = UUID.randomUUID().toString().take(8),
    val senderRole: AgentRole,
    val recipientRole: AgentRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class AgentExecutionLog(
    val taskId: String,
    val role: AgentRole,
    val thought: String,
    val toolCalls: List<String> = emptyList(),
    val output: String,
    val durationMs: Long,
    val tokensUsed: Int
)

/**
 * Maps agents to designated models or lets them share the central active local model.
 */
interface AgentModelRouter {
    fun getPreferredModelForRole(role: AgentRole, activeModel: ModelInfo?): ModelInfo? {
        // By default, share the single active local model to prevent multiple model allocation OOMs
        return activeModel
    }
}
