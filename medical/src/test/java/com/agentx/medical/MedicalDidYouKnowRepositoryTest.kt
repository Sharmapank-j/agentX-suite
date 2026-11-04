package com.agentx.medical

import com.agentx.medical.domain.models.MedicalDidYouKnowRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalDidYouKnowRepositoryTest {

    @Test
    fun testMedicalRepositoryContainsEvidenceBasedInsights() {
        val insights = MedicalDidYouKnowRepository.getAllInsights()
        assertTrue("Expected at least 10 clinical insights", insights.size >= 10)

        for (insight in insights) {
            assertTrue("Clinical ID should not be blank", insight.id.isNotBlank())
            assertTrue("Clinical Category should not be blank", insight.category.isNotBlank())
            assertTrue("Headline should not be blank", insight.headline.isNotBlank())
            assertTrue("Fact should not be blank", insight.fact.isNotBlank())
            assertTrue("SuggestedPrompt should not be blank", insight.suggestedPrompt.isNotBlank())
            assertFalse("RelatedChips should not be empty", insight.relatedChips.isEmpty())
        }
    }

    @Test
    fun testRandomMedicalInsightRetrieval() {
        val randomInsight = MedicalDidYouKnowRepository.getRandomInsight()
        assertNotNull(randomInsight)
        assertTrue(randomInsight.headline.isNotBlank())
    }

    @Test
    fun testRandomMedicalInsightExclusion() {
        val insights = MedicalDidYouKnowRepository.getAllInsights()
        if (insights.size > 1) {
            val excludedId = insights.first().id
            val nextInsight = MedicalDidYouKnowRepository.getRandomInsight(excludeId = excludedId)
            assertNotEquals(excludedId, nextInsight.id)
        }
    }
}
