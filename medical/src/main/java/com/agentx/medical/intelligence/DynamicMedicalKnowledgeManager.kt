package com.agentx.medical.intelligence

import com.agentx.core.ota.InstalledComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ClinicalCalculatorDef(
    val id: String,
    val name: String,
    val description: String,
    val formulaDescription: String,
    val clinicalInterpretation: String
)

@Serializable
data class MedicalKnowledgePack(
    val packId: String = "medical-intelligence-pack",
    val version: String = "1.0.0",
    val releaseDate: String = "2026-11-18",
    val additionalEntities: List<String> = emptyList(),
    val additionalKeywords: Map<String, List<String>> = emptyMap(),
    val updatedGuidelineDirectives: Map<String, String> = emptyMap(),
    val additionalCalculators: List<ClinicalCalculatorDef> = emptyList()
)

/**
 * Manages dynamically hot-loaded medical intelligence, clinical guidelines,
 * and entity dictionaries delivered via OTA component updates.
 * Allows instant guideline hot-patching without APK recompilation.
 */
object DynamicMedicalKnowledgeManager {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activePack: MedicalKnowledgePack? = null

    @Volatile
    private var activeComponent: InstalledComponent? = null

    val currentVersion: String
        get() = activePack?.version ?: "Built-in v1.0"

    val isDynamicPackActive: Boolean
        get() = activePack != null

    /**
     * Hot-reloads the knowledge pack from a verified local component file.
     */
    @Synchronized
    fun loadFromComponent(installed: InstalledComponent): Boolean {
        return try {
            val file = File(installed.filePath)
            if (!file.exists() || !file.isFile) return false
            val text = file.readText()
            val pack = json.decodeFromString<MedicalKnowledgePack>(text)
            activePack = pack
            activeComponent = installed
            logInfo("DynamicMedKnowledge", "Successfully hot-loaded medical knowledge pack v${pack.version} (${pack.additionalEntities.size} entities, ${pack.additionalCalculators.size} calculators)")
            true
        } catch (e: Exception) {
            logError("DynamicMedKnowledge", "Failed to load dynamic knowledge pack", e)
            false
        }
    }

    private fun logInfo(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logError(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        }
    }

    /**
     * Reverts to built-in compiled intelligence directives.
     */
    @Synchronized
    fun resetToBuiltIn() {
        activePack = null
        activeComponent = null
    }

    /**
     * Retrieves dynamic entities augmented with OTA updates.
     */
    fun augmentEntities(baseEntities: List<String>): List<String> {
        val extra = activePack?.additionalEntities ?: return baseEntities
        return (baseEntities + extra).distinct()
    }

    /**
     * Retrieves dynamic discipline keywords.
     */
    fun augmentKeywords(disciplineTitle: String, baseKeywords: List<String>): List<String> {
        val extra = activePack?.additionalKeywords?.get(disciplineTitle) ?: return baseKeywords
        return (baseKeywords + extra).distinct()
    }

    /**
     * Checks if an updated clinical guideline directive overrides the built-in directive.
     */
    fun getUpdatedGuidelineDirective(disciplineOrIntent: String): String? {
        return activePack?.updatedGuidelineDirectives?.get(disciplineOrIntent)
    }

    /**
     * Retrieves all dynamic clinical calculators.
     */
    fun getCalculators(): List<ClinicalCalculatorDef> {
        return activePack?.additionalCalculators ?: emptyList()
    }
}
