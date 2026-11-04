package com.agentx

import com.agentx.core.inference.ContextBudget
import com.agentx.core.inference.ContextManager
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {

    @Test
    fun testTokenEstimation() {
        val emptyTokens = ContextManager.estimateTokens("")
        assertEquals(0, emptyTokens)

        val clinicalText = "Patient presents with stage 2 hypertension and microalbuminuria."
        val tokens = ContextManager.estimateTokens(clinicalText)
        assertTrue(tokens > 10)
    }

    @Test
    fun testPromptBuilderWithAllHistoryFitting() {
        val history = listOf(
            Message(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                senderName = "Clinician",
                content = "What is the starting dose of lisinopril?",
                status = MessageStatus.COMPLETED
            ),
            Message(
                id = "m2",
                conversationId = "c1",
                role = MessageRole.ASSISTANT,
                senderName = "Clinical Safety Reviewer",
                content = "Starting dose is 10 mg orally once daily for adult hypertension.",
                status = MessageStatus.COMPLETED
            )
        )

        val prompt = ContextManager.buildPromptWithContext(
            systemPrompt = "You are a clinical decision support assistant.",
            history = history,
            currentPrompt = "What if the patient has renal impairment with CrCl < 30?",
            budget = ContextBudget(maxContextTokens = 2048, maxOutputTokens = 512)
        )

        assertTrue(prompt.contains("<|system|>"))
        assertTrue(prompt.contains("clinical decision support assistant"))
        assertTrue(prompt.contains("<|user|>\nWhat is the starting dose of lisinopril?"))
        assertTrue(prompt.contains("<|assistant|>\nStarting dose is 10 mg orally once daily"))
        assertTrue(prompt.contains("<|user|>\nWhat if the patient has renal impairment with CrCl < 30?"))
        assertTrue(prompt.endsWith("<|assistant|>\n"))
        assertFalse(prompt.contains("Older clinical history truncated"))
    }

    @Test
    fun testPromptBuilderSlidingWindowTruncation() {
        // Create 20 long historical messages that exceed small budget
        val history = (1..20).map { i ->
            Message(
                id = "m$i",
                conversationId = "c1",
                role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                senderName = if (i % 2 == 1) "Clinician" else "Assistant",
                content = "Clinical case history step $i: Patient assessment and lab review notes detail extensive pathophysiology findings for cardiovascular parameters.",
                status = MessageStatus.COMPLETED
            )
        }

        val prompt = ContextManager.buildPromptWithContext(
            systemPrompt = "Medical Assistant",
            history = history,
            currentPrompt = "Final diagnosis review?",
            // Small budget forcing truncation
            budget = ContextBudget(maxContextTokens = 300, maxOutputTokens = 50, reservedTokens = 20)
        )

        assertTrue(prompt.contains("Older clinical history truncated"))
        // Most recent message should still be included
        assertTrue(prompt.contains("Clinical case history step 20"))
        // Oldest messages should have been truncated
        assertFalse(prompt.contains("Clinical case history step 1:"))
        // Current prompt must always be included
        assertTrue(prompt.contains("Final diagnosis review?"))
    }
}
