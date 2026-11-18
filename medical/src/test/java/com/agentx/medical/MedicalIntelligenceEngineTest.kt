package com.agentx.medical

import com.agentx.medical.intelligence.AnswerDepth
import com.agentx.medical.intelligence.MedicalDiscipline
import com.agentx.medical.intelligence.MedicalPromptBuilder
import com.agentx.medical.intelligence.MedicalQueryClassification
import com.agentx.medical.intelligence.MedicalQueryClassifier
import com.agentx.medical.intelligence.MedicalQueryIntent
import com.agentx.medical.intelligence.ResponseStyle
import com.agentx.medical.intelligence.UrgencyLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalIntelligenceEngineTest {

    @Test
    fun testParseChunkJson() {
        val chunk = """{"id":"chatcmpl-9b72cd3a-df2b-44af-94c9-e1400837b986","object":"chat.completion.chunk","created":1794717597,"model":"medimind-v2-q5_k_m","choices":[{"index":0,"delta":{"content":"What"},"finish_reason":null}]}"""
        val parsed = Json.parseToJsonElement(chunk).jsonObject
        val delta = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
        val content = delta?.get("content")?.jsonPrimitive?.content
        assertEquals("What", content)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Response Style Specifications (5 User Preferences)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testResponseStylesExactCountAndDefault() {
        assertEquals(5, ResponseStyle.entries.size)
        assertEquals(ResponseStyle.MEDICAL_STUDENT, ResponseStyle.DEFAULT)

        val styles = listOf(
            ResponseStyle.RESEARCH,
            ResponseStyle.ADVANCED,
            ResponseStyle.CLINICAL_PROFESSIONAL,
            ResponseStyle.MEDICAL_STUDENT,
            ResponseStyle.QUICK_REFERENCE_VIVA
        )
        for (style in styles) {
            assertTrue(style.title.isNotBlank())
            assertTrue(style.shortDescription.isNotBlank())
            assertTrue(style.targetAudience.isNotBlank())
            assertTrue(style.promptDirective.isNotBlank())
            assertEquals(style, ResponseStyle.fromString(style.name))
            assertEquals(style, ResponseStyle.fromString(style.id))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Answer Depth Specifications (5 Conversation Preferences)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testAnswerDepthsExactCountAndDefault() {
        assertEquals(5, AnswerDepth.entries.size)
        assertEquals(AnswerDepth.DETAILED, AnswerDepth.DEFAULT)

        val depths = listOf(
            AnswerDepth.EXPERT,
            AnswerDepth.ADVANCED,
            AnswerDepth.DETAILED,
            AnswerDepth.STANDARD,
            AnswerDepth.BRIEF
        )
        for (depth in depths) {
            assertTrue(depth.title.isNotBlank())
            assertTrue(depth.shortDescription.isNotBlank())
            assertTrue(depth.tokenBudget >= 512)
            assertTrue(depth.promptDirective.isNotBlank())
            assertEquals(depth, AnswerDepth.fromString(depth.name))
            assertEquals(depth, AnswerDepth.fromString(depth.id))
        }

        assertEquals(4096, AnswerDepth.EXPERT.tokenBudget)
        assertEquals(3072, AnswerDepth.ADVANCED.tokenBudget)
        assertEquals(2048, AnswerDepth.DETAILED.tokenBudget)
        assertEquals(1024, AnswerDepth.STANDARD.tokenBudget)
        assertEquals(512, AnswerDepth.BRIEF.tokenBudget)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. The 25 Combination Generation System (5 Styles x 5 Depths Matrix)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testAllTwentyFiveCombinationsGenerateDistinctSystemPrompts() {
        val sampleClassification = MedicalQueryClassification(
            primaryDiscipline = MedicalDiscipline.PULMONOLOGY,
            secondaryDiscipline = MedicalDiscipline.EMERGENCY_CRITICAL_CARE,
            intent = MedicalQueryIntent.CLINICAL_MANAGEMENT,
            urgency = UrgencyLevel.URGENT,
            detectedEntities = listOf("copd")
        )

        val generatedPrompts = mutableSetOf<String>()
        var count = 0

        for (style in ResponseStyle.entries) {
            for (depth in AnswerDepth.entries) {
                count++
                val prompt = MedicalPromptBuilder.buildSystemPrompt(sampleClassification, style, depth)

                assertTrue("Prompt for $style + $depth must not be blank", prompt.isNotBlank())
                assertTrue("Prompt must contain style directive", prompt.contains(style.title.uppercase()))
                assertTrue("Prompt must contain depth directive", prompt.contains(depth.title.uppercase()))
                assertTrue("Prompt must contain discipline", prompt.contains("PULMONOLOGY"))
                assertTrue("Prompt must contain MediMind identification", prompt.contains("MediMind"))

                generatedPrompts.add(prompt)
            }
        }

        assertEquals(25, count)
        assertEquals("All 25 style-depth combinations must produce distinct prompts", 25, generatedPrompts.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Medical Query Classifier Tests Across Specialties & Urgencies
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testCopdQueryClassification() {
        val query = "Manage acute exacerbation of COPD with hypercapnic respiratory failure"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalDiscipline.PULMONOLOGY, classification.primaryDiscipline)
        assertEquals(MedicalQueryIntent.CLINICAL_MANAGEMENT, classification.intent)
        assertTrue(classification.detectedEntities.contains("copd"))
    }

    @Test
    fun testDkaEmergencyClassification() {
        val query = "Resuscitation protocol and IV insulin for adult diabetic ketoacidosis DKA"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalDiscipline.ENDOCRINOLOGY_METABOLISM, classification.primaryDiscipline)
        assertEquals(UrgencyLevel.EMERGENCY, classification.urgency)
        assertTrue(classification.detectedEntities.contains("dka") || classification.detectedEntities.contains("diabetic ketoacidosis"))
    }

    @Test
    fun testAkiClassification() {
        val query = "KDIGO staging, workup, and acute management of AKI with hyperkalemia"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalDiscipline.NEPHROLOGY_UROLOGY, classification.primaryDiscipline)
        assertEquals(MedicalQueryIntent.CLINICAL_MANAGEMENT, classification.intent)
        assertTrue(classification.detectedEntities.contains("aki"))
    }

    @Test
    fun testPphEmergencyClassification() {
        val query = "Emergency protocol for severe postpartum haemorrhage PPH with hypotension"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalDiscipline.OBSTETRICS_GYNAECOLOGY, classification.primaryDiscipline)
        assertEquals(UrgencyLevel.EMERGENCY, classification.urgency)
    }

    @Test
    fun testAppendicitisClassification() {
        val query = "Alvarado score criteria and surgical indications for suspected acute appendicitis"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalDiscipline.GENERAL_SURGERY, classification.primaryDiscipline)
        assertTrue(classification.detectedEntities.contains("appendicitis"))
    }

    @Test
    fun testAceInhibitorPharmacologyClassification() {
        val query = "Explain the mechanism of action, hemodynamic effects, and adverse effects of ACE inhibitors"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals(MedicalQueryIntent.PHARMACOTHERAPY, classification.intent)
        assertTrue(classification.detectedEntities.contains("ace inhibitors"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Dynamic Token Budgeting Tests
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testDynamicMaxTokensCalculation() {
        // Brief depth with 3840 context
        val briefTokens = MedicalPromptBuilder.computeMaxTokens(
            depth = AnswerDepth.BRIEF,
            contextLength = 3840,
            estimatedPromptTokens = 500
        )
        assertEquals(512, briefTokens)

        // Expert depth with 3840 context
        val expertTokens = MedicalPromptBuilder.computeMaxTokens(
            depth = AnswerDepth.EXPERT,
            contextLength = 3840,
            estimatedPromptTokens = 500
        )
        // (3840 - 500 - 64) = 3276 -> coerced to min(4096, 3276) = 3276
        assertTrue(expertTokens in 3000..4096)

        // Standard depth
        val standardTokens = MedicalPromptBuilder.computeMaxTokens(
            depth = AnswerDepth.STANDARD,
            contextLength = 3840,
            estimatedPromptTokens = 500
        )
        assertEquals(1024, standardTokens)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Emergency Override Test
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testEmergencyUrgencyIncludesImmediateAbcdeDirectives() {
        val emergencyClassification = MedicalQueryClassification(
            primaryDiscipline = MedicalDiscipline.EMERGENCY_CRITICAL_CARE,
            intent = MedicalQueryIntent.EMERGENCY_RESUSCITATION,
            urgency = UrgencyLevel.EMERGENCY
        )
        val prompt = MedicalPromptBuilder.buildSystemPrompt(
            classification = emergencyClassification,
            style = ResponseStyle.CLINICAL_PROFESSIONAL,
            depth = AnswerDepth.DETAILED
        )

        assertTrue(prompt.contains("CRITICAL MEDICAL ALERT — EMERGENCY / RESUSCITATION PROTOCOL"))
        assertTrue(prompt.contains("ABCDE"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Keyword Collision & High-Specificity Clinical Intent Tests
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testToxicologyPrecedenceOverPharmacotherapy() {
        val query = "Management and NAC antidote protocol for paracetamol overdose"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals("Overdose query must classify as TOXICOLOGY, not collide with 'dose' in pharmacotherapy",
            MedicalQueryIntent.TOXICOLOGY, classification.intent)
    }

    @Test
    fun testDrugInteractionPrecedence() {
        val query = "Mechanism of adverse drug interaction and contraindication between warfarin and amiodarone"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals("Drug interaction query must classify as DRUG_INTERACTION",
            MedicalQueryIntent.DRUG_INTERACTION, classification.intent)
    }

    @Test
    fun testClinicalCalculationPrecedence() {
        val query = "How to calculate Child-Pugh score and prognostic staging for cirrhosis"
        val classification = MedicalQueryClassifier.classify(query)

        assertEquals("Score calculation query must classify as CLINICAL_CALCULATION",
            MedicalQueryIntent.CLINICAL_CALCULATION, classification.intent)
    }
}
