package com.agentx.medical.intelligence

data class MedicalQueryClassification(
    val primaryDiscipline: MedicalDiscipline,
    val secondaryDiscipline: MedicalDiscipline? = null,
    val intent: MedicalQueryIntent,
    val urgency: UrgencyLevel,
    val detectedEntities: List<String> = emptyList()
)

/**
 * Fast, deterministic on-device medical query classifier.
 * Categorizes queries across 21 disciplines, 20+ intents, and 3 triage urgency levels.
 */
object MedicalQueryClassifier {

    fun classify(query: String): MedicalQueryClassification {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        // 1. Triage Urgency Level
        val urgency = UrgencyLevel.detect(lower)

        // 2. Query Intent with specificity-first precedence and word-boundary safety
        val intent = when {
            // Critical emergency resuscitation
            urgency == UrgencyLevel.EMERGENCY && containsAnyWord(lower, "arrest", "resus", "cpr", "defib", "pulseless", "acls", "bls") ->
                MedicalQueryIntent.EMERGENCY_RESUSCITATION

            // High-specificity clinical domains (evaluated before generic management/pharmacology)
            containsAnyWord(lower, "overdose", "poison", "poisoning", "antidote", "toxidrome", "toxic ingestion", "toxicity") ->
                MedicalQueryIntent.TOXICOLOGY

            containsAnyWord(lower, "drug interaction", "interaction", "contraindication", "cyp450", "cyp3a4", "drug-drug") ->
                MedicalQueryIntent.DRUG_INTERACTION

            containsAnyWord(lower, "calculate", "equation", "formula", "score", "nomogram", "curb-65", "well's", "chadsvasc", "child-pugh", "alvarado") ->
                MedicalQueryIntent.CLINICAL_CALCULATION

            containsAnyWord(lower, "ecg", "ekg", "st elevation", "bundle branch", "qt prolongation", "arrhythmia", "lead v1") ->
                MedicalQueryIntent.ECG_INTERPRETATION

            containsAnyWord(lower, "interpret", "interpretation", "abg", "csf", "lft", "rft", "arterial blood gas", "lumbar puncture", "biopsy") ->
                MedicalQueryIntent.INVESTIGATION_INTERPRETATION

            containsAnyWord(lower, "surgery", "operation", "surgical", "operative", "resection", "laparoscopy", "laparotomy") ->
                MedicalQueryIntent.SURGICAL_PROCEDURES

            containsAnyWord(lower, "ethics", "consent", "capacity", "dnr", "advanced directive", "beneficence") ->
                MedicalQueryIntent.MEDICAL_ETHICS_LEGAL

            containsAnyWord(lower, "viva", "exam", "osce", "mnemonic", "high yield", "usmle", "plab", "neet pg") ->
                MedicalQueryIntent.VIVA_EXAM_PREPARATION

            containsAnyWord(lower, "guideline", "consensus", "nice", "gold", "bts guideline", "surviving sepsis") ->
                MedicalQueryIntent.CLINICAL_GUIDELINES

            containsAnyWord(lower, "differential", "ddx", "rule out", "diagnostic possibilities") ->
                MedicalQueryIntent.DIFFERENTIAL_DIAGNOSIS

            containsAnyWord(lower, "pathophysiology", "pathogenesis", "etiology", "disease mechanism") ->
                MedicalQueryIntent.PATHOPHYSIOLOGY

            // Pharmacotherapy (word-boundary safe so 'dose' does not collide with 'overdose')
            containsAnyWord(lower, "dose", "dosing", "dosage", "mechanism of action", "adverse effect", "side effect", "pharmacokinetics") ->
                MedicalQueryIntent.PHARMACOTHERAPY

            // General clinical management & evaluation
            containsAnyWord(lower, "treat", "treatment", "manage", "management", "therapy", "regimen", "therapeutic") ->
                MedicalQueryIntent.CLINICAL_MANAGEMENT

            containsAnyWord(lower, "diagnos", "diagnosis", "workup", "evaluation", "clinical assessment") ->
                MedicalQueryIntent.DIAGNOSTIC_EVALUATION

            containsAnyWord(lower, "counsel", "counselling", "lifestyle", "smoking cessation", "patient education") ->
                MedicalQueryIntent.PATIENT_COUNSELLING

            else -> MedicalQueryIntent.match(lower)
        }

        // 3. Disciplines Scoring with word-boundary matching, dynamic OTA keywords, and specificity weighting
        val scoredDisciplines = mutableListOf<Pair<MedicalDiscipline, Int>>()
        for (discipline in MedicalDiscipline.entries) {
            if (discipline == MedicalDiscipline.GENERAL_MEDICINE) continue
            var score = 0
            val effectiveKeywords = DynamicMedicalKnowledgeManager.augmentKeywords(discipline.title, discipline.keywords)
            for (keyword in effectiveKeywords) {
                val regex = Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE)
                val matches = regex.findAll(lower).count()
                if (matches > 0) {
                    score += matches * (keyword.length + 5)
                }
            }
            if (score > 0) {
                scoredDisciplines.add(discipline to score)
            }
        }
        scoredDisciplines.sortByDescending { it.second }

        val primaryDiscipline = scoredDisciplines.firstOrNull()?.first ?: MedicalDiscipline.GENERAL_MEDICINE
        val secondaryDiscipline = if (scoredDisciplines.size > 1) scoredDisciplines[1].first else null

        // 4. Clinical Entity Extraction (Base vocabulary + dynamic OTA additions)
        val baseEntities = listOf(
            "copd", "asthma", "pneumonia", "dka", "diabetic ketoacidosis", "aki", "acute kidney injury",
            "ckd", "pph", "postpartum haemorrhage", "appendicitis", "cholecystitis", "stemi", "nstemi",
            "heart failure", "atrial fibrillation", "stroke", "migraine", "epilepsy", "meningitis",
            "cirrhosis", "pancreatitis", "pulmonary embolism", "dvt", "sepsis", "anaphylaxis",
            "ace inhibitors", "metformin", "insulin", "adrenaline", "morphine", "amiodarone", "warfarin",
            "apixaban", "heparin", "ceftriaxone", "vancomycin", "piperacillin", "salbutamol", "furosemide"
        )
        val candidateEntities = DynamicMedicalKnowledgeManager.augmentEntities(baseEntities)
        val detectedEntities = candidateEntities.filter { lower.contains(it) }

        return MedicalQueryClassification(
            primaryDiscipline = primaryDiscipline,
            secondaryDiscipline = secondaryDiscipline,
            intent = intent,
            urgency = urgency,
            detectedEntities = detectedEntities
        )
    }

    private fun containsAnyWord(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            val pattern = if (kw.contains(" ") || kw.contains("-")) {
                Regex.escape(kw)
            } else {
                "\\b${Regex.escape(kw)}\\b"
            }
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                return true
            }
        }
        return false
    }
}
