package com.agentx.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "uri_string") val uriString: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val architecture: String = "llama",
    @ColumnInfo(name = "context_length") val contextLength: Int = 2048,
    @ColumnInfo(name = "tensor_count") val tensorCount: Long = 0L,
    val format: String = "GGUF",
    val quantization: String = "Q4_K_M",
    @ColumnInfo(name = "capability_profile") val capabilityProfile: String = "medical-clinical",
    @ColumnInfo(name = "is_staged") val isStaged: Boolean = false,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    @ColumnInfo(name = "top_p") val topP: Float = 0.9f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["updated_at"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "active_agent_id") val activeAgentId: String? = null,
    @ColumnInfo(name = "active_team_id") val activeTeamId: String? = null,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String, // USER, ASSISTANT, SYSTEM, AGENT
    @ColumnInfo(name = "sender_name") val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "agent_id") val agentId: String? = null,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
    val status: String = "COMPLETED" // PENDING, STREAMING, COMPLETED, ERROR
)

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String, // SUPERVISOR, PLANNER, RESEARCHER, CODER, etc.
    @ColumnInfo(name = "system_prompt") val systemPrompt: String,
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    val temperature: Float = 0.7f,
    @ColumnInfo(name = "top_p") val topP: Float = 0.9f,
    @ColumnInfo(name = "max_tokens") val maxTokens: Int = 1024,
    @ColumnInfo(name = "assigned_tools_json") val assignedToolsJson: String = "[]",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    val icon: String = "robot"
)

@Entity(tableName = "agent_teams")
data class AgentTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "supervisor_agent_id") val supervisorAgentId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "team_members",
    foreignKeys = [
        ForeignKey(
            entity = AgentTeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["team_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AgentEntity::class,
            parentColumns = ["id"],
            childColumns = ["agent_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["team_id"]),
        Index(value = ["agent_id"]),
        Index(value = ["team_id", "agent_id"], unique = true)
    ]
)
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "team_id") val teamId: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "execution_order") val executionOrder: Int = 0,
    @ColumnInfo(name = "role_in_team") val roleInTeam: String = "MEMBER"
)

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    @ColumnInfo(name = "requires_permission") val requiresPermission: Boolean = true,
    @ColumnInfo(name = "schema_json") val schemaJson: String = "{}"
)

@Entity(
    tableName = "tool_permissions",
    foreignKeys = [
        ForeignKey(
            entity = ToolEntity::class,
            parentColumns = ["id"],
            childColumns = ["tool_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tool_id"], unique = true)
    ]
)
data class ToolPermissionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tool_id") val toolId: String,
    val granted: Boolean = false,
    @ColumnInfo(name = "granted_at") val grantedAt: Long? = null,
    @ColumnInfo(name = "auto_approve") val autoApprove: Boolean = false
)

@Entity(
    tableName = "executions",
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["agent_id"]),
        Index(value = ["started_at"])
    ]
)
data class ExecutionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val status: String, // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    @ColumnInfo(name = "input_context") val inputContext: String,
    @ColumnInfo(name = "output_result") val outputResult: String,
    @ColumnInfo(name = "tool_calls_json") val toolCallsJson: String = "[]",
    @ColumnInfo(name = "token_usage") val tokenUsage: Int = 0,
    @ColumnInfo(name = "execution_time_ms") val executionTimeMs: Long = 0L,
    @ColumnInfo(name = "error_details") val errorDetails: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["created_at"])
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val category: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false
)
