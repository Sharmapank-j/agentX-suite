package com.agentx.medical.data.repository

import com.agentx.medical.data.local.dao.AgentDao
import com.agentx.medical.data.local.dao.ConversationDao
import com.agentx.medical.data.local.dao.ExecutionDao
import com.agentx.medical.data.local.dao.MemoryDao
import com.agentx.medical.data.local.dao.MessageDao
import com.agentx.medical.data.local.dao.ModelDao
import com.agentx.medical.data.local.dao.TeamDao
import com.agentx.medical.data.local.dao.ToolDao
import com.agentx.medical.data.local.entity.AgentEntity
import com.agentx.medical.data.local.entity.AgentTeamEntity
import com.agentx.medical.data.local.entity.ConversationEntity
import com.agentx.medical.data.local.entity.ExecutionEntity
import com.agentx.medical.data.local.entity.MemoryEntity
import com.agentx.medical.data.local.entity.MessageEntity
import com.agentx.medical.data.local.entity.ModelEntity
import com.agentx.medical.data.local.entity.TeamMemberEntity
import com.agentx.medical.data.local.entity.ToolEntity
import com.agentx.medical.data.local.entity.ToolPermissionEntity
import com.agentx.medical.domain.models.Agent
import com.agentx.medical.domain.models.AgentRole
import com.agentx.medical.domain.models.AgentTeam
import com.agentx.medical.domain.models.Conversation
import com.agentx.medical.domain.models.ExecutionLog
import com.agentx.medical.domain.models.ExecutionStatus
import com.agentx.medical.domain.models.GGUFModel
import com.agentx.medical.domain.models.Memory
import com.agentx.medical.domain.models.Message
import com.agentx.medical.domain.models.MessageRole
import com.agentx.medical.domain.models.MessageStatus
import com.agentx.medical.domain.models.Tool
import com.agentx.medical.domain.models.ToolPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// --- Model Repository ---
interface ModelRepository {
    fun getAllModels(): Flow<List<GGUFModel>>
    fun getActiveModel(): Flow<GGUFModel?>
    suspend fun getModelById(id: String): GGUFModel?
    suspend fun addModel(model: GGUFModel)
    suspend fun updateModel(model: GGUFModel)
    suspend fun setActiveModel(id: String)
    suspend fun deleteModel(id: String)
}

class ModelRepositoryImpl(private val modelDao: ModelDao) : ModelRepository {
    override fun getAllModels(): Flow<List<GGUFModel>> =
        modelDao.getAllModels().map { list ->
            list.map { it.toDomain() }
        }

    override fun getActiveModel(): Flow<GGUFModel?> =
        modelDao.getActiveModel().map { it?.toDomain() }

    override suspend fun getModelById(id: String): GGUFModel? =
        modelDao.getModelById(id)?.toDomain()

    override suspend fun addModel(model: GGUFModel) {
        modelDao.insertModel(model.toEntity())
    }

    override suspend fun updateModel(model: GGUFModel) {
        modelDao.updateModel(model.toEntity())
    }

    override suspend fun setActiveModel(id: String) {
        modelDao.setActiveModel(id)
    }

    override suspend fun deleteModel(id: String) {
        modelDao.getModelById(id)?.let { modelDao.deleteModel(it) }
    }

    private fun ModelEntity.toDomain() = GGUFModel(
        id = id,
        name = name,
        filePath = filePath,
        uriString = uriString,
        sizeBytes = sizeBytes,
        architecture = architecture,
        contextLength = contextLength,
        tensorCount = tensorCount,
        format = format,
        quantization = quantization,
        capabilityProfile = capabilityProfile,
        isStaged = isStaged,
        isActive = isActive,
        threads = threads,
        temperature = temperature,
        topP = topP,
        createdAt = createdAt
    )

    private fun GGUFModel.toEntity() = ModelEntity(
        id = id,
        name = name,
        filePath = filePath,
        uriString = uriString,
        sizeBytes = sizeBytes,
        architecture = architecture,
        contextLength = contextLength,
        tensorCount = tensorCount,
        format = format,
        quantization = quantization,
        capabilityProfile = capabilityProfile,
        isStaged = isStaged,
        isActive = isActive,
        threads = threads,
        temperature = temperature,
        topP = topP,
        createdAt = createdAt
    )
}

// --- Conversation & Message Repository ---
interface ConversationRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    suspend fun getConversationById(id: String): Conversation?
    suspend fun createConversation(conversation: Conversation)
    suspend fun updateConversation(conversation: Conversation)
    suspend fun deleteConversation(id: String)
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(message: Message)
    suspend fun updateMessage(message: Message)
    suspend fun deleteMessage(id: String)
    suspend fun clearMessagesForConversation(conversationId: String)
}

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {
    override fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getConversationById(id: String): Conversation? =
        conversationDao.getConversationById(id)?.toDomain()

    override suspend fun createConversation(conversation: Conversation) {
        conversationDao.insertConversation(conversation.toEntity())
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversationDao.updateConversation(conversation.toEntity())
    }

    override suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun sendMessage(message: Message) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message.toEntity())
    }

    override suspend fun deleteMessage(id: String) {
        messageDao.deleteMessageById(id)
    }

    override suspend fun clearMessagesForConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        activeAgentId = activeAgentId,
        activeTeamId = activeTeamId,
        isPinned = isPinned
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        activeAgentId = activeAgentId,
        activeTeamId = activeTeamId,
        isPinned = isPinned
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        role = try { MessageRole.valueOf(role) } catch (_: Exception) { MessageRole.ASSISTANT },
        senderName = senderName,
        content = content,
        timestamp = timestamp,
        agentId = agentId,
        tokenCount = tokenCount,
        status = try { MessageStatus.valueOf(status) } catch (_: Exception) { MessageStatus.COMPLETED }
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        senderName = senderName,
        content = content,
        timestamp = timestamp,
        agentId = agentId,
        tokenCount = tokenCount,
        status = status.name
    )
}

// --- Agent & Team Repository ---
interface AgentRepository {
    fun getAllAgents(): Flow<List<Agent>>
    suspend fun getAgentById(id: String): Agent?
    suspend fun saveAgent(agent: Agent)
    suspend fun deleteAgent(id: String)
    fun getAllTeams(): Flow<List<AgentTeam>>
    suspend fun getTeamById(id: String): AgentTeam?
    suspend fun saveTeam(team: AgentTeam)
    suspend fun deleteTeam(id: String)
}

class AgentRepositoryImpl(
    private val agentDao: AgentDao,
    private val teamDao: TeamDao
) : AgentRepository {
    override fun getAllAgents(): Flow<List<Agent>> =
        agentDao.getAllAgents().map { list -> list.map { it.toDomain() } }

    override suspend fun getAgentById(id: String): Agent? =
        agentDao.getAgentById(id)?.toDomain()

    override suspend fun saveAgent(agent: Agent) {
        agentDao.insertAgent(agent.toEntity())
    }

    override suspend fun deleteAgent(id: String) {
        agentDao.getAgentById(id)?.let { agentDao.deleteAgent(it) }
    }

    override fun getAllTeams(): Flow<List<AgentTeam>> =
        combine(teamDao.getAllTeams(), agentDao.getAllAgents()) { teams, _ ->
            teams.map { it.toDomain(emptyList()) }
        }

    override suspend fun getTeamById(id: String): AgentTeam? =
        teamDao.getTeamById(id)?.toDomain(emptyList())

    override suspend fun saveTeam(team: AgentTeam) {
        teamDao.insertTeam(team.toEntity())
        teamDao.deleteTeamMembers(team.id)
        val memberEntities = team.memberAgentIds.mapIndexed { index, agentId ->
            TeamMemberEntity(
                id = "${team.id}_$agentId",
                teamId = team.id,
                agentId = agentId,
                executionOrder = index
            )
        }
        teamDao.insertTeamMembers(memberEntities)
    }

    override suspend fun deleteTeam(id: String) {
        teamDao.getTeamById(id)?.let { teamDao.deleteTeam(it) }
    }

    private fun AgentEntity.toDomain() = Agent(
        id = id,
        name = name,
        role = try { AgentRole.valueOf(role) } catch (_: Exception) { AgentRole.ANALYST },
        systemPrompt = systemPrompt,
        modelId = modelId,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        assignedToolIds = emptyList(),
        isDefault = isDefault,
        icon = icon
    )

    private fun Agent.toEntity() = AgentEntity(
        id = id,
        name = name,
        role = role.name,
        systemPrompt = systemPrompt,
        modelId = modelId,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        assignedToolsJson = "[]",
        isDefault = isDefault,
        icon = icon
    )

    private fun AgentTeamEntity.toDomain(memberIds: List<String>) = AgentTeam(
        id = id,
        name = name,
        description = description,
        supervisorAgentId = supervisorAgentId,
        memberAgentIds = memberIds,
        createdAt = createdAt
    )

    private fun AgentTeam.toEntity() = AgentTeamEntity(
        id = id,
        name = name,
        description = description,
        supervisorAgentId = supervisorAgentId,
        createdAt = createdAt
    )
}

// --- Tool Repository ---
interface ToolRepository {
    fun getAllTools(): Flow<List<Tool>>
    fun getAllPermissions(): Flow<List<ToolPermission>>
    suspend fun getToolById(id: String): Tool?
    suspend fun updatePermission(permission: ToolPermission)
}

class ToolRepositoryImpl(private val toolDao: ToolDao) : ToolRepository {
    override fun getAllTools(): Flow<List<Tool>> =
        toolDao.getAllTools().map { list -> list.map { it.toDomain() } }

    override fun getAllPermissions(): Flow<List<ToolPermission>> =
        toolDao.getAllPermissions().map { list -> list.map { it.toDomain() } }

    override suspend fun getToolById(id: String): Tool? =
        toolDao.getToolById(id)?.toDomain()

    override suspend fun updatePermission(permission: ToolPermission) {
        toolDao.setPermission(
            ToolPermissionEntity(
                id = permission.toolId,
                toolId = permission.toolId,
                granted = permission.granted,
                grantedAt = permission.grantedAt,
                autoApprove = permission.autoApprove
            )
        )
    }

    private fun ToolEntity.toDomain() = Tool(
        id = id,
        name = name,
        description = description,
        category = category,
        requiresPermission = requiresPermission,
        schemaJson = schemaJson
    )

    private fun ToolPermissionEntity.toDomain() = ToolPermission(
        toolId = toolId,
        granted = granted,
        grantedAt = grantedAt,
        autoApprove = autoApprove
    )
}

// --- Execution & Memory Repositories ---
interface ExecutionRepository {
    fun getExecutionsForConversation(conversationId: String): Flow<List<ExecutionLog>>
    fun getRecentExecutions(): Flow<List<ExecutionLog>>
    suspend fun logExecution(log: ExecutionLog)
    suspend fun updateExecution(log: ExecutionLog)
}

class ExecutionRepositoryImpl(private val executionDao: ExecutionDao) : ExecutionRepository {
    override fun getExecutionsForConversation(conversationId: String): Flow<List<ExecutionLog>> =
        executionDao.getExecutionsForConversation(conversationId).map { list -> list.map { it.toDomain() } }

    override fun getRecentExecutions(): Flow<List<ExecutionLog>> =
        executionDao.getRecentExecutions().map { list -> list.map { it.toDomain() } }

    override suspend fun logExecution(log: ExecutionLog) {
        executionDao.insertExecution(log.toEntity())
    }

    override suspend fun updateExecution(log: ExecutionLog) {
        executionDao.updateExecution(log.toEntity())
    }

    private fun ExecutionEntity.toDomain() = ExecutionLog(
        id = id,
        conversationId = conversationId,
        agentId = agentId,
        taskId = taskId,
        status = try { ExecutionStatus.valueOf(status) } catch (_: Exception) { ExecutionStatus.FAILED },
        inputContext = inputContext,
        outputResult = outputResult,
        toolCallsJson = toolCallsJson,
        tokenUsage = tokenUsage,
        executionTimeMs = executionTimeMs,
        errorDetails = errorDetails,
        startedAt = startedAt,
        completedAt = completedAt
    )

    private fun ExecutionLog.toEntity() = ExecutionEntity(
        id = id,
        conversationId = conversationId,
        agentId = agentId,
        taskId = taskId,
        status = status.name,
        inputContext = inputContext,
        outputResult = outputResult,
        toolCallsJson = toolCallsJson,
        tokenUsage = tokenUsage,
        executionTimeMs = executionTimeMs,
        errorDetails = errorDetails,
        startedAt = startedAt,
        completedAt = completedAt
    )
}

interface MemoryRepository {
    fun getActiveMemories(): Flow<List<Memory>>
    suspend fun saveMemory(memory: Memory)
    suspend fun deleteMemory(id: String)
}

class MemoryRepositoryImpl(private val memoryDao: MemoryDao) : MemoryRepository {
    override fun getActiveMemories(): Flow<List<Memory>> =
        memoryDao.getActiveMemories().map { list -> list.map { it.toDomain() } }

    override suspend fun saveMemory(memory: Memory) {
        memoryDao.insertMemory(
            MemoryEntity(
                id = memory.id,
                category = memory.category,
                content = memory.content,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt,
                isArchived = memory.isArchived
            )
        )
    }

    override suspend fun deleteMemory(id: String) {
        memoryDao.deleteMemory(id)
    }

    private fun MemoryEntity.toDomain() = Memory(
        id = id,
        category = category,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isArchived = isArchived
    )
}
