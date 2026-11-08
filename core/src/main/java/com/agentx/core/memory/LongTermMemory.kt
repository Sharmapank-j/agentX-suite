package com.agentx.core.memory

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Long-Term Memory Architecture specified in Section H of the Master Architecture.
 *
 * Implements:
 * - Profile memory: User preferences, facts, style.
 * - Episodic memory: Cross-session conversation context.
 * - Project memory: Scoped to specific tasks, codebases, or clinical workflows.
 * - Semantic keyword retrieval with importance weighting.
 * - User review, deletion, and export controls.
 * - Memory compaction and expiration.
 * - Strict offline-first storage preventing privacy leakage.
 */

@Serializable
enum class MemoryType {
    /** Durable user attributes, preferences, background, and stylistic requirements */
    PROFILE,
    /** Semantic summaries of past conversations, decisions, and outcomes */
    EPISODIC,
    /** Scoped context for specific projects, domains, or workspaces */
    PROJECT,
    /** Procedural memory: Reusable skills, agent recipes, and verified workflow patterns */
    PROCEDURAL,
    /** Working memory: Short-term scratchpad buffer for active multi-agent reasoning */
    WORKING
}

@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val key: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val importance: Float = 0.5f, // 0.0 (trivial) to 1.0 (vital)
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastAccessedAtMs: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val expiresAtMs: Long? = null,
    val isPinned: Boolean = false
) {
    val isExpired: Boolean
        get() = expiresAtMs != null && System.currentTimeMillis() > expiresAtMs
}

@Serializable
data class MemoryQuery(
    val query: String = "",
    val types: Set<MemoryType> = MemoryType.entries.toSet(),
    val tags: List<String> = emptyList(),
    val minImportance: Float = 0.0f,
    val limit: Int = 5
)

interface LongTermMemoryStore {
    suspend fun store(entry: MemoryEntry): MemoryEntry
    suspend fun get(id: String): MemoryEntry?
    suspend fun findByKey(key: String, type: MemoryType): MemoryEntry?
    suspend fun search(query: MemoryQuery): List<MemoryEntry>
    suspend fun listByType(type: MemoryType): List<MemoryEntry>
    suspend fun getAll(): List<MemoryEntry>
    suspend fun delete(id: String): Boolean
    suspend fun deleteByType(type: MemoryType): Int
    suspend fun clearAll(): Int
    suspend fun pruneExpiredAndLowImportance(maxEntries: Int = 500): Int
    suspend fun exportJson(): String
    suspend fun importJson(jsonString: String): Int
}

class DefaultLongTermMemoryManager(
    private val maxCapacity: Int = 500
) : LongTermMemoryStore {

    private val lock = Any()
    private val memoryMap = LinkedHashMap<String, MemoryEntry>()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun store(entry: MemoryEntry): MemoryEntry = synchronized(lock) {
        val normalized = entry.copy(
            lastAccessedAtMs = System.currentTimeMillis()
        )
        memoryMap[normalized.id] = normalized

        // Auto-prune if capacity exceeded
        if (memoryMap.size > maxCapacity) {
            pruneInternal(maxCapacity)
        }
        normalized
    }

    override suspend fun get(id: String): MemoryEntry? = synchronized(lock) {
        val entry = memoryMap[id] ?: return null
        if (entry.isExpired) {
            memoryMap.remove(id)
            return null
        }
        val accessed = entry.copy(
            lastAccessedAtMs = System.currentTimeMillis(),
            accessCount = entry.accessCount + 1
        )
        memoryMap[id] = accessed
        accessed
    }

    override suspend fun findByKey(key: String, type: MemoryType): MemoryEntry? = synchronized(lock) {
        val match = memoryMap.values.firstOrNull { it.type == type && it.key.equals(key, ignoreCase = true) }
        if (match != null && match.isExpired) {
            memoryMap.remove(match.id)
            return null
        }
        match
    }

    override suspend fun search(query: MemoryQuery): List<MemoryEntry> = synchronized(lock) {
        val queryTokens = query.query.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val now = System.currentTimeMillis()

        // Clean expired on read
        val activeEntries = memoryMap.values.filter { !it.isExpired }

        activeEntries
            .filter { entry ->
                entry.type in query.types &&
                        entry.importance >= query.minImportance &&
                        (query.tags.isEmpty() || query.tags.any { tag -> entry.tags.contains(tag) })
            }
            .map { entry ->
                val score = computeRelevanceScore(entry, queryTokens, now)
                entry to score
            }
            .filter { if (queryTokens.isNotEmpty()) it.second > 0f else true }
            .sortedByDescending { it.second }
            .take(query.limit)
            .map { (entry, _) ->
                // Update access metadata
                val updated = entry.copy(
                    lastAccessedAtMs = now,
                    accessCount = entry.accessCount + 1
                )
                memoryMap[entry.id] = updated
                updated
            }
    }

    override suspend fun listByType(type: MemoryType): List<MemoryEntry> = synchronized(lock) {
        memoryMap.values.filter { it.type == type && !it.isExpired }
    }

    override suspend fun getAll(): List<MemoryEntry> = synchronized(lock) {
        memoryMap.values.filter { !it.isExpired }.sortedByDescending { it.importance }
    }

    override suspend fun delete(id: String): Boolean = synchronized(lock) {
        memoryMap.remove(id) != null
    }

    override suspend fun deleteByType(type: MemoryType): Int = synchronized(lock) {
        val toRemove = memoryMap.values.filter { it.type == type }.map { it.id }
        toRemove.forEach { memoryMap.remove(it) }
        toRemove.size
    }

    override suspend fun clearAll(): Int = synchronized(lock) {
        val size = memoryMap.size
        memoryMap.clear()
        size
    }

    override suspend fun pruneExpiredAndLowImportance(maxEntries: Int): Int = synchronized(lock) {
        pruneInternal(maxEntries)
    }

    private fun pruneInternal(targetCapacity: Int): Int {
        var removedCount = 0

        // 1. Remove expired
        val expired = memoryMap.values.filter { it.isExpired }.map { it.id }
        expired.forEach {
            memoryMap.remove(it)
            removedCount++
        }

        // 2. If still over target, remove unpinned items with lowest importance
        if (memoryMap.size > targetCapacity) {
            val unpinned = memoryMap.values
                .filter { !it.isPinned }
                .sortedWith(compareBy<MemoryEntry> { it.importance }.thenBy { it.lastAccessedAtMs })

            val excess = memoryMap.size - targetCapacity
            val candidates = unpinned.take(excess)
            candidates.forEach {
                memoryMap.remove(it.id)
                removedCount++
            }
        }
        return removedCount
    }

    override suspend fun exportJson(): String = synchronized(lock) {
        val active = memoryMap.values.filter { !it.isExpired }
        json.encodeToString(active)
    }

    override suspend fun importJson(jsonString: String): Int = synchronized(lock) {
        val imported = json.decodeFromString<List<MemoryEntry>>(jsonString)
        var count = 0
        imported.forEach {
            if (!it.isExpired) {
                memoryMap[it.id] = it
                count++
            }
        }
        count
    }

    /**
     * Heuristic extractor for auto-detecting facts from user statements.
     */
    fun extractFactsFromUserStatement(statement: String): List<MemoryEntry> {
        val text = statement.trim()
        val facts = mutableListOf<MemoryEntry>()

        val nameRegex = Regex("(?i)(?:my name is|i am called|call me)\\s+([a-zA-Z]+)")
        nameRegex.find(text)?.let {
            val name = it.groupValues[1]
            facts.add(
                MemoryEntry(
                    type = MemoryType.PROFILE,
                    key = "user_name",
                    content = "User's name is $name.",
                    tags = listOf("identity", "name"),
                    importance = 0.9f,
                    isPinned = true
                )
            )
        }

        val preferenceRegex = Regex("(?i)i (?:prefer|like|always use)\\s+([a-zA-Z0-9\\s+#]+?)(?:\\.|,|!|$)")
        preferenceRegex.find(text)?.let {
            val pref = it.groupValues[1].trim()
            if (pref.length in 3..60) {
                facts.add(
                    MemoryEntry(
                        type = MemoryType.PROFILE,
                        key = "preference_${pref.take(15).replace(" ", "_")}",
                        content = "User preference: $pref.",
                        tags = listOf("preference"),
                        importance = 0.7f
                    )
                )
            }
        }

        val studyRegex = Regex("(?i)i am (?:studying|a student of|working on)\\s+([a-zA-Z0-9\\s]+?)(?:\\.|,|!|$)")
        studyRegex.find(text)?.let {
            val subject = it.groupValues[1].trim()
            facts.add(
                MemoryEntry(
                    type = MemoryType.PROFILE,
                    key = "occupation_${subject.take(15).replace(" ", "_")}",
                    content = "User focus/study: $subject.",
                    tags = listOf("background", "study"),
                    importance = 0.8f
                )
            )
        }

        val goalRegex = Regex("(?i)my goal is (?:to\\s+)?([a-zA-Z0-9\\s]+?)(?:\\.|,|!|$)")
        goalRegex.find(text)?.let {
            val goal = it.groupValues[1].trim()
            facts.add(
                MemoryEntry(
                    type = MemoryType.PROFILE,
                    key = "goal_${goal.take(15).replace(" ", "_")}",
                    content = "User goal: $goal.",
                    tags = listOf("goal"),
                    importance = 0.85f
                )
            )
        }

        val techRegex = Regex("(?i)i (?:code|program|develop|write)\\s+(?:in|using)\\s+([a-zA-Z0-9\\s+#]+?)(?:\\.|,|!|$)")
        techRegex.find(text)?.let {
            val tech = it.groupValues[1].trim()
            facts.add(
                MemoryEntry(
                    type = MemoryType.PROFILE,
                    key = "tech_stack_${tech.take(15).replace(" ", "_")}",
                    content = "User development stack: $tech.",
                    tags = listOf("technology", "stack"),
                    importance = 0.8f
                )
            )
        }

        return facts
    }

    /**
     * Helper to render memories formatted for inclusion in the attention prompt context.
     */
    fun formatForPrompt(memories: List<MemoryEntry>): String {
        if (memories.isEmpty()) return ""
        return memories.joinToString("\n") { "- ${it.content}" }
    }

    private fun computeRelevanceScore(entry: MemoryEntry, queryTokens: List<String>, nowMs: Long): Float {
        var score = entry.importance * 0.4f // Baseline importance weight

        if (queryTokens.isEmpty()) return score

        val entryContent = (entry.key + " " + entry.content + " " + entry.tags.joinToString(" ")).lowercase()
        val matchedTokens = queryTokens.count { entryContent.contains(it) }

        if (matchedTokens == 0) return 0f

        val matchRatio = matchedTokens.toFloat() / queryTokens.size
        score += matchRatio * 0.5f

        // Recency boost (up to 0.1 for accessed within last 24h)
        val hoursSinceAccess = ((nowMs - entry.lastAccessedAtMs) / (1000 * 3600)).coerceAtLeast(0)
        val recencyBoost = (1.0f / (1.0f + hoursSinceAccess * 0.05f)) * 0.1f
        score += recencyBoost

        return score.coerceIn(0f, 1.0f)
    }
}
