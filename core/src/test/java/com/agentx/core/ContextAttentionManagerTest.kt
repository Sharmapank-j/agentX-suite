package com.agentx.core

import com.agentx.core.context.AttentionBudget
import com.agentx.core.context.ContextAttentionManager
import com.agentx.core.context.ContextMessage
import com.agentx.core.memory.MemoryEntry
import com.agentx.core.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAttentionManagerTest {

    @Test
    fun testTokenEstimation() {
        assertEquals(0, ContextAttentionManager.estimateTokens(""))
        val text = "Artificial Intelligence at the Edge with llama.cpp runtime."
        val tokens = ContextAttentionManager.estimateTokens(text)
        assertTrue(tokens in 10..25)
    }

    @Test
    fun testContextBuildingWithMemoryAndHistory() {
        val memories = listOf(
            MemoryEntry(
                type = MemoryType.PROFILE,
                key = "name",
                content = "User is Dr. Smith, a cardiologist.",
                importance = 0.95f,
                isPinned = true
            )
        )

        val history = listOf(
            ContextMessage(
                id = "m1",
                role = "user",
                content = "Tell me about beta blockers."
            ),
            ContextMessage(
                id = "m2",
                role = "assistant",
                content = "Beta blockers manage cardiac arrhythmias and hypertension."
            )
        )

        val optimized = ContextAttentionManager.buildOptimizedPrompt(
            systemPrompt = "You are a specialized medical AI assistant.",
            userProfileMemories = memories,
            history = history,
            currentPrompt = "What are the primary contraindications?",
            budget = AttentionBudget(maxContextTokens = 2048, maxOutputTokens = 512)
        )

        assertTrue(optimized.fullPrompt.contains("### System Instructions:"))
        assertTrue(optimized.fullPrompt.contains("### Relevant User Memory & Profile:"))
        assertTrue(optimized.fullPrompt.contains("Dr. Smith, a cardiologist"))
        assertTrue(optimized.fullPrompt.contains("Tell me about beta blockers"))
        assertTrue(optimized.fullPrompt.contains("What are the primary contraindications?"))
        assertEquals(1, optimized.memoryInjectedCount)
        assertEquals(2, optimized.includedMessageCount)
        assertFalse(optimized.wasCompressed)
    }

    @Test
    fun testContextBudgetConstrainedCompression() {
        // Generate 25 messages
        val history = (1..25).map { i ->
            ContextMessage(
                id = "m$i",
                role = if (i % 2 == 1) "user" else "assistant",
                content = "Message turn $i with extensive discussion regarding clinical trial endpoints and biomarker efficacy."
            )
        }

        // Small budget forcing compression
        val smallBudget = AttentionBudget(maxContextTokens = 400, maxOutputTokens = 100, safetyMarginTokens = 20)

        val optimized = ContextAttentionManager.buildOptimizedPrompt(
            systemPrompt = "Concise Assistant",
            userProfileMemories = emptyList(),
            history = history,
            currentPrompt = "Summary conclusion please",
            budget = smallBudget
        )

        assertTrue("Should trigger compression", optimized.wasCompressed)
        assertTrue(optimized.compressedMessageCount > 0)
        assertTrue(optimized.fullPrompt.contains("### Previous Context Summary:"))
        assertTrue(optimized.fullPrompt.contains("Summary conclusion please"))
    }
}
