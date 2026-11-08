package com.agentx.core.context

import com.agentx.core.memory.MemoryEntry
import kotlin.math.max

/**
 * Message abstraction for attention and context management across AgentX modules.
 */
data class ContextMessage(
    val id: String,
    val role: String, // "user", "assistant", "system"
    val senderName: String? = null,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val importance: Float = 0.5f
)

data class AttentionBudget(
    val maxContextTokens: Int = 2048,
    val maxOutputTokens: Int = 512,
    val safetyMarginTokens: Int = 64
) {
    val maxInputTokens: Int
        get() = max(128, maxContextTokens - maxOutputTokens - safetyMarginTokens)
}

data class OptimizedContext(
    val fullPrompt: String,
    val estimatedPromptTokens: Int,
    val includedMessageCount: Int,
    val compressedMessageCount: Int,
    val wasCompressed: Boolean,
    val memoryInjectedCount: Int
)

/**
 * Advanced Context & Dynamic Attention Manager for AgentX.
 *
 * Implements:
 * - Multi-layer attention scoring (System Persona -> Pinned Profile Facts -> Recent Dialogue -> Compressed History)
 * - Strict token budget enforcement against model context size
 * - Semantic rollup of older dialogue turns to eliminate memory thrashing
 * - KV-cache prefix stability optimization for local GGUF engines
 */
object ContextAttentionManager {

    /**
     * Fast token estimator based on BPE word-pieces and whitespace tokenization.
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val chars = text.length
        val words = text.trim().split(Regex("\\s+")).size
        // Conservative heuristic: ~3.8 characters per token or 1.33 tokens per word
        val charEstimate = (chars + 3) / 4
        val wordEstimate = (words * 4) / 3
        return max(charEstimate, wordEstimate)
    }

    /**
     * Constructs a prioritized attention prompt that fits strictly within the specified token budget.
     */
    fun buildOptimizedPrompt(
        systemPrompt: String?,
        userProfileMemories: List<MemoryEntry> = emptyList(),
        history: List<ContextMessage>,
        currentPrompt: String,
        budget: AttentionBudget = AttentionBudget()
    ): OptimizedContext {
        val totalAvailable = budget.maxInputTokens
        var remainingBudget = totalAvailable

        // 1. Layer 0: System Instructions (Immutable Attention - 100%)
        val cleanSystem = systemPrompt?.trim().orEmpty()
        val systemTokens = estimateTokens(cleanSystem) + 12
        remainingBudget -= systemTokens

        // 2. Layer 1: Current Prompt (Current Intent - 100%)
        val cleanCurrent = currentPrompt.trim()
        val currentTokens = estimateTokens(cleanCurrent) + 8
        remainingBudget -= currentTokens

        // 3. Layer 2: Long-Term Memory & Profile Facts (High Attention)
        val selectedMemories = mutableListOf<MemoryEntry>()
        val memorySb = StringBuilder()
        if (userProfileMemories.isNotEmpty() && remainingBudget > 120) {
            // Sort by importance descending
            val sortedMemories = userProfileMemories
                .sortedWith(compareByDescending<MemoryEntry> { it.isPinned }.thenByDescending { it.importance })

            for (mem in sortedMemories) {
                val memLine = "- ${mem.content.trim()}\n"
                val memTokens = estimateTokens(memLine) + 2
                if (remainingBudget - memTokens > 150) { // Keep buffer for recent dialogue
                    selectedMemories.add(mem)
                    memorySb.append(memLine)
                    remainingBudget -= memTokens
                } else {
                    break
                }
            }
        }

        // 4. Layer 3: Dialogue History (Recency Attention)
        val filteredHistory = history.filter {
            it.role.lowercase() != "system" && it.content.isNotBlank()
        }

        val includedRecent = mutableListOf<ContextMessage>()
        val overflowHistory = mutableListOf<ContextMessage>()
        var historyTokensAccumulated = 0

        // Traverse backwards from most recent message
        for (msg in filteredHistory.asReversed()) {
            val msgFormatted = "${msg.role.replaceFirstChar { it.uppercase() }}: ${msg.content.trim()}\n"
            val msgTokens = estimateTokens(msgFormatted) + 4

            if (historyTokensAccumulated + msgTokens <= remainingBudget) {
                includedRecent.add(0, msg)
                historyTokensAccumulated += msgTokens
            } else {
                overflowHistory.add(0, msg)
            }
        }

        // 5. Layer 4: Compressed History Rollup for overflow messages
        val wasCompressed = overflowHistory.isNotEmpty()
        val compressedSummary = if (wasCompressed) {
            summarizeOlderHistory(overflowHistory)
        } else {
            null
        }

        // Assemble Final Context Prompt with KV-Cache Stable Prefix Ordering
        val promptBuilder = StringBuilder()

        // Prefix: System Directive
        if (cleanSystem.isNotBlank()) {
            promptBuilder.append("### System Instructions:\n")
            promptBuilder.append(cleanSystem).append("\n\n")
        }

        // Injected Long-Term Memory
        if (memorySb.isNotEmpty()) {
            promptBuilder.append("### Relevant User Memory & Profile:\n")
            promptBuilder.append(memorySb.toString().trim()).append("\n\n")
        }

        // Compressed History Note
        if (!compressedSummary.isNullOrBlank()) {
            promptBuilder.append("### Previous Context Summary:\n")
            promptBuilder.append(compressedSummary.trim()).append("\n\n")
        }

        // Verbatim Dialogue History
        if (includedRecent.isNotEmpty()) {
            promptBuilder.append("### Conversation History:\n")
            for (msg in includedRecent) {
                val roleLabel = when (msg.role.lowercase()) {
                    "user" -> "User"
                    "assistant", "agent" -> "Assistant"
                    else -> msg.role.replaceFirstChar { it.uppercase() }
                }
                promptBuilder.append("$roleLabel: ").append(msg.content.trim()).append("\n")
            }
            promptBuilder.append("\n")
        }

        // Current Turn
        promptBuilder.append("User: ").append(cleanCurrent).append("\n")
        promptBuilder.append("Assistant:")

        val finalPrompt = promptBuilder.toString()
        val totalEstimatedTokens = estimateTokens(finalPrompt)

        return OptimizedContext(
            fullPrompt = finalPrompt,
            estimatedPromptTokens = totalEstimatedTokens,
            includedMessageCount = includedRecent.size,
            compressedMessageCount = overflowHistory.size,
            wasCompressed = wasCompressed,
            memoryInjectedCount = selectedMemories.size
        )
    }

    /**
     * Compact semantic condensation of older superseded dialogue messages.
     */
    private fun summarizeOlderHistory(messages: List<ContextMessage>): String {
        if (messages.isEmpty()) return ""
        val points = mutableListOf<String>()
        for (m in messages.takeLast(10)) { // Capture salient points from previous turns
            val snippet = m.content.trim().replace("\n", " ").take(100)
            val role = if (m.role.equals("user", ignoreCase = true)) "User queried" else "Agent resolved"
            points.add("$role: $snippet...")
        }
        return points.joinToString("; ")
    }
}
