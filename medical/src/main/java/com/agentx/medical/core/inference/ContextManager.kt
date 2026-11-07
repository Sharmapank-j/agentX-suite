package com.agentx.medical.core.inference

import com.agentx.medical.domain.models.Message
import com.agentx.medical.domain.models.MessageRole

data class ContextBudget(
    val maxContextTokens: Int = 2048,
    val maxOutputTokens: Int = 512,
    val reservedTokens: Int = 64
)

object ContextManager {

    /**
     * Fast token estimator. For clinical and English text, 1 token is roughly 4 characters
     * or ~0.75 words. We use a conservative character/word blended heuristic.
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val charTokens = (text.length + 3) / 4
        val wordTokens = (text.trim().split(Regex("\\s+")).size * 4) / 3
        return maxOf(charTokens, wordTokens)
    }

    /**
     * Prepares a sliding context window that fits within the model's token limits.
     * Keeps the system prompt and the current prompt intact, and includes as many recent
     * conversation messages as will fit in the available budget.
     */
    fun buildPromptWithContext(
        systemPrompt: String?,
        history: List<Message>,
        currentPrompt: String,
        budget: ContextBudget = ContextBudget()
    ): String {
        val estimatedSystemTokens = estimateTokens(systemPrompt ?: "")
        val estimatedCurrentTokens = estimateTokens(currentPrompt)

        val availableForHistory = budget.maxContextTokens -
                budget.maxOutputTokens -
                budget.reservedTokens -
                estimatedSystemTokens -
                estimatedCurrentTokens

        val filteredHistory = history.filter {
            it.status != com.agentx.medical.domain.models.MessageStatus.ERROR &&
                    it.content.isNotBlank() &&
                    it.role != MessageRole.SYSTEM
        }

        val includedMessages = mutableListOf<Message>()
        var accumulatedTokens = 0

        // Traverse backwards from most recent message to oldest
        for (msg in filteredHistory.asReversed()) {
            val msgTokens = estimateTokens(msg.content) + 8 // Formatting overhead per message
            if (accumulatedTokens + msgTokens <= availableForHistory) {
                includedMessages.add(0, msg)
                accumulatedTokens += msgTokens
            } else {
                break
            }
        }

        val wasTruncated = includedMessages.size < filteredHistory.size

        val builder = StringBuilder()

        if (wasTruncated) {
            builder.append("[Note: Older conversation history truncated]\n")
        }

        // Conversation History (clean text turns)
        for (msg in includedMessages) {
            val roleLabel = when (msg.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT, MessageRole.AGENT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            builder.append("$roleLabel: ").append(msg.content.trim()).append("\n")
        }

        // Current User Prompt
        if (includedMessages.isNotEmpty()) {
            builder.append("User: ").append(currentPrompt.trim())
        } else {
            builder.append(currentPrompt.trim())
        }

        return builder.toString()
    }
}
