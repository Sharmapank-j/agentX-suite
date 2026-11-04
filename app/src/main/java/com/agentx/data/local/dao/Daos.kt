package com.agentx.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentx.data.local.entity.AgentEntity
import com.agentx.data.local.entity.AgentTeamEntity
import com.agentx.data.local.entity.ConversationEntity
import com.agentx.data.local.entity.ExecutionEntity
import com.agentx.data.local.entity.MemoryEntity
import com.agentx.data.local.entity.MessageEntity
import com.agentx.data.local.entity.ModelEntity
import com.agentx.data.local.entity.TeamMemberEntity
import com.agentx.data.local.entity.ToolEntity
import com.agentx.data.local.entity.ToolPermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY is_active DESC, created_at DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE is_active = 1 LIMIT 1")
    fun getActiveModel(): Flow<ModelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("UPDATE models SET is_active = CASE WHEN id = :modelId THEN 1 ELSE 0 END")
    suspend fun setActiveModel(modelId: String)

    @Delete
    suspend fun deleteModel(model: ModelEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY is_pinned DESC, updated_at DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY name ASC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agents: List<AgentEntity>)

    @Update
    suspend fun updateAgent(agent: AgentEntity)

    @Delete
    suspend fun deleteAgent(agent: AgentEntity)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM agent_teams ORDER BY created_at DESC")
    fun getAllTeams(): Flow<List<AgentTeamEntity>>

    @Query("SELECT * FROM agent_teams WHERE id = :id LIMIT 1")
    suspend fun getTeamById(id: String): AgentTeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: AgentTeamEntity)

    @Delete
    suspend fun deleteTeam(team: AgentTeamEntity)

    @Query("SELECT * FROM team_members WHERE team_id = :teamId ORDER BY execution_order ASC")
    fun getTeamMembers(teamId: String): Flow<List<TeamMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMembers(members: List<TeamMemberEntity>)

    @Query("DELETE FROM team_members WHERE team_id = :teamId")
    suspend fun deleteTeamMembers(teamId: String)
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY category ASC, name ASC")
    fun getAllTools(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE id = :id LIMIT 1")
    suspend fun getToolById(id: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tools: List<ToolEntity>)

    @Query("SELECT * FROM tool_permissions WHERE tool_id = :toolId LIMIT 1")
    suspend fun getPermission(toolId: String): ToolPermissionEntity?

    @Query("SELECT * FROM tool_permissions")
    fun getAllPermissions(): Flow<List<ToolPermissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPermission(permission: ToolPermissionEntity)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions WHERE conversation_id = :conversationId ORDER BY started_at DESC")
    fun getExecutionsForConversation(conversationId: String): Flow<List<ExecutionEntity>>

    @Query("SELECT * FROM executions ORDER BY started_at DESC LIMIT 100")
    fun getRecentExecutions(): Flow<List<ExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: ExecutionEntity)

    @Update
    suspend fun updateExecution(execution: ExecutionEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun getActiveMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)
}
