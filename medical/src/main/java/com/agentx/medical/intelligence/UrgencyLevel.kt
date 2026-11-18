package com.agentx.medical.intelligence

/**
 * Clinical urgency and triage stratification for user medical queries.
 */
enum class UrgencyLevel(
    val id: String,
    val title: String,
    val description: String,
    val promptDirectives: String
) {
    EMERGENCY(
        id = "EMERGENCY",
        title = "Emergency / Critical Priority",
        description = "Potentially life-threatening condition requiring immediate resuscitation or urgent intervention",
        promptDirectives = """
            🚨 CRITICAL MEDICAL ALERT — EMERGENCY / RESUSCITATION PROTOCOL:
            1. IMMEDIATELY prioritize life-saving ABCDE interventions (Airway, Breathing, Circulation, Disability, Exposure).
            2. Explicitly highlight immediate RED FLAGS, vital emergency pharmacological dosages (e.g., adrenaline/epinephrine, naloxone, atropine, fluids), and time-critical actions.
            3. Clearly mandate urgent senior clinical escalation and emergency department / ICU transfer.
            4. De-emphasize non-essential elective investigations until acute stability is achieved.
        """.trimIndent()
    ),

    URGENT(
        id = "URGENT",
        title = "Urgent",
        description = "Acute or deteriorating condition requiring prompt diagnostic evaluation and intervention within hours",
        promptDirectives = """
            ⚡ URGENT CLINICAL PRIORITY:
            - Focus on rapid diagnostic confirmation and early initiation of targeted therapy.
            - Highlight key warning signs indicating decompensation and escalation thresholds.
            - Specify essential same-day investigations and primary stabilization measures.
        """.trimIndent()
    ),

    ROUTINE(
        id = "ROUTINE",
        title = "Routine / Educational",
        description = "Elective, chronic disease management, educational inquiry, or academic reference",
        promptDirectives = """
            STANDARD CLINICAL / EDUCATIONAL PROTOCOL:
            - Provide systematic, evidence-based coverage according to selected response style and depth.
            - Emphasize structured clinical reasoning, guideline recommendations, and patient-centered considerations.
        """.trimIndent()
    );

    companion object {
        private val EMERGENCY_KEYWORDS = listOf(
            "cardiac arrest", "anaphylaxis", "severe chest pain", "stemi", "stridor", "status epilepticus",
            "tension pneumothorax", "massive hemoptysis", "dka", "diabetic ketoacidosis", "septic shock",
            "postpartum haemorrhage", "postpartum hemorrhage", "pph", "eclampsia", "acute pulmonary edema",
            "unconscious", "gcs <", "gcs 3", "gcs 4", "gcs 5", "gcs 6", "gcs 7", "gcs 8", "severe airway obstruction",
            "thunderclap headache", "meningococcal septicemia", "acute severe asthma", "tamponade"
        )

        private val URGENT_KEYWORDS = listOf(
            "acute", "worsening", "shortness of breath", "fever in neutropenia", "appendicitis", "stroke",
            "tia", "pulmonary embolism", "deep vein thrombosis", "aki", "acute kidney injury",
            "bowel obstruction", "unstable angina", "pancreatitis", "retinal detachment"
        )

        fun detect(query: String): UrgencyLevel {
            val lower = query.lowercase()
            if (EMERGENCY_KEYWORDS.any { lower.contains(it) }) {
                return EMERGENCY
            }
            if (URGENT_KEYWORDS.any { lower.contains(it) }) {
                return URGENT
            }
            return ROUTINE
        }
    }
}
