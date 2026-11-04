package com.agentx

import com.agentx.domain.models.DidYouKnowRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DidYouKnowRepositoryTest {

    @Test
    fun testRepositoryContainsMultipleCuratedInsights() {
        val insights = DidYouKnowRepository.getAllInsights()
        assertTrue("Expected at least 10 curated insights", insights.size >= 10)

        for (insight in insights) {
            assertTrue("ID should not be blank", insight.id.isNotBlank())
            assertTrue("Category should not be blank", insight.category.isNotBlank())
            assertTrue("Emoji should not be blank", insight.emoji.isNotBlank())
            assertTrue("Headline should not be blank", insight.headline.isNotBlank())
            assertTrue("Fact should not be blank", insight.fact.isNotBlank())
            assertTrue("SuggestedPrompt should not be blank", insight.suggestedPrompt.isNotBlank())
            assertFalse("RelatedChips should not be empty", insight.relatedChips.isEmpty())
        }
    }

    @Test
    fun testRandomInsightRetrieval() {
        val randomInsight = DidYouKnowRepository.getRandomInsight()
        assertNotNull(randomInsight)
        assertTrue(randomInsight.headline.isNotBlank())
    }

    @Test
    fun testRandomInsightExclusion() {
        val insights = DidYouKnowRepository.getAllInsights()
        if (insights.size > 1) {
            val excludedId = insights.first().id
            val nextInsight = DidYouKnowRepository.getRandomInsight(excludeId = excludedId)
            assertNotEquals(excludedId, nextInsight.id)
        }
    }
}
