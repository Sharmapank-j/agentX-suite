package com.agentx.core.agent.memory

import com.agentx.domain.models.Memory
import kotlinx.serialization.Serializable

@Serializable
data class MemoryItem(
    val id: String,
    val key: String,
    val content: String,
    val importance: Float = 0.5f
)

interface MemoryManager {
    suspend fun retrieveRelevantMemories(query: String, limit: Int = 5): List<MemoryItem>
    suspend fun storeMemory(key: String, content: String)
}

class ConversationMemory : MemoryManager {
    private val inMemoryStore = mutableListOf<MemoryItem>()

    override suspend fun retrieveRelevantMemories(query: String, limit: Int): List<MemoryItem> {
        return inMemoryStore.take(limit)
    }

    override suspend fun storeMemory(key: String, content: String) {
        inMemoryStore.add(MemoryItem(System.currentTimeMillis().toString(), key, content))
    }
}
