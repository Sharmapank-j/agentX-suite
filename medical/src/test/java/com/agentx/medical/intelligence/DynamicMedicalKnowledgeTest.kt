package com.agentx.medical.intelligence

import com.agentx.core.ota.ComponentType
import com.agentx.core.ota.InstalledComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DynamicMedicalKnowledgeTest {

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        tempFile = File.createTempFile("ota_med_pack_", ".json")
        val sampleJson = """
        {
          "packId": "medical-intelligence-pack",
          "version": "1.3.0",
          "releaseDate": "2026-11-18",
          "additionalEntities": [
            "scombroid", "ciguatera", "serotonin syndrome"
          ],
          "additionalKeywords": {
            "Toxicology & Poisoning": ["scombroid", "ciguatera", "anticholinergic toxidrome"]
          },
          "updatedGuidelineDirectives": {
            "TOXICOLOGY": "EMERGENCY TOXICOLOGY PROTOCOL 2026: Immediate airway protection, early ECG rhythm monitoring, rapid toxidrome recognition, and targeted antidote delivery."
          },
          "additionalCalculators": [
            {
              "id": "qsofa",
              "name": "quick SOFA (qSOFA)",
              "description": "Bedside risk assessment for suspected sepsis outside ICU",
              "formulaDescription": "1 pt each: RR >= 22/min, Altered mentation (GCS < 15), SBP <= 100 mmHg",
              "clinicalInterpretation": "Score >= 2 suggests high risk of in-hospital mortality."
            }
          ]
        }
        """.trimIndent()
        tempFile.writeText(sampleJson)
    }

    @After
    fun tearDown() {
        tempFile.delete()
        DynamicMedicalKnowledgeManager.resetToBuiltIn()
    }

    @Test
    fun testDynamicMedicalKnowledgeHotReloadAndAugmentation() {
        assertFalse(DynamicMedicalKnowledgeManager.isDynamicPackActive)
        assertEquals("Built-in v1.0", DynamicMedicalKnowledgeManager.currentVersion)

        val component = InstalledComponent(
            id = "medical-intelligence-pack",
            name = "MediMind Clinical Guidelines",
            type = ComponentType.INTELLIGENCE_PACK,
            version = "1.3.0",
            versionCode = 10300,
            filePath = tempFile.absolutePath,
            sha256 = "dummy",
            sizeBytes = tempFile.length()
        )

        val ok = DynamicMedicalKnowledgeManager.loadFromComponent(component)
        assertTrue(ok)
        assertTrue(DynamicMedicalKnowledgeManager.isDynamicPackActive)
        assertEquals("1.3.0", DynamicMedicalKnowledgeManager.currentVersion)

        // Entity augmentation
        val augmented = DynamicMedicalKnowledgeManager.augmentEntities(listOf("copd", "asthma"))
        assertTrue(augmented.contains("scombroid"))
        assertTrue(augmented.contains("ciguatera"))
        assertTrue(augmented.contains("serotonin syndrome"))

        // Directive override
        val directive = DynamicMedicalKnowledgeManager.getUpdatedGuidelineDirective("TOXICOLOGY")
        assertTrue(directive != null && directive.contains("EMERGENCY TOXICOLOGY PROTOCOL 2026"))

        // Calculator check
        val calculators = DynamicMedicalKnowledgeManager.getCalculators()
        assertEquals(1, calculators.size)
        assertEquals("qsofa", calculators.first().id)

        // Classifier uses augmented entities
        val classification = MedicalQueryClassifier.classify("Patient presenting with acute scombroid poisoning after tuna ingestion")
        assertEquals(MedicalQueryIntent.TOXICOLOGY, classification.intent)
        assertTrue(classification.detectedEntities.contains("scombroid"))

        // Reset test
        DynamicMedicalKnowledgeManager.resetToBuiltIn()
        assertFalse(DynamicMedicalKnowledgeManager.isDynamicPackActive)
        assertEquals("Built-in v1.0", DynamicMedicalKnowledgeManager.currentVersion)
    }
}
