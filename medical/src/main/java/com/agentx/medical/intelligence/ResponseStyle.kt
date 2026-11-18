package com.agentx.medical.intelligence

/**
 * User-level persistent preference for clinical response style.
 * Exactly 5 styles designed for different healthcare personas and operational contexts.
 * Default: [MEDICAL_STUDENT]
 */
enum class ResponseStyle(
    val id: String,
    val title: String,
    val shortDescription: String,
    val targetAudience: String,
    val iconName: String,
    val promptDirective: String
) {
    RESEARCH(
        id = "RESEARCH",
        title = "Research",
        shortDescription = "Evidence appraisal, trial methodology, GRADE criteria, and evidence gaps",
        targetAudience = "Researchers, Academics, Journal Clubs & Clinician-Scientists",
        iconName = "Biotech",
        promptDirective = """
            ADOPT A RIGOROUS CLINICAL RESEARCH & ACADEMIC APPRAISAL PERSPECTIVE:
            - Emphasize randomized controlled trials (RCTs), systematic reviews, Cochrane analyses, and meta-analyses.
            - Explicitly discuss trial methodology, endpoints (primary vs secondary), statistical power, and hazard/odds ratios.
            - Evaluate quality of evidence using GRADE criteria (High/Moderate/Low/Very Low).
            - Highlight contradictory study findings, clinical trial limitations, and acknowledged knowledge gaps.
            - Maintain critical academic objectivity and methodological precision.
        """.trimIndent()
    ),

    ADVANCED(
        id = "ADVANCED",
        title = "Advanced",
        shortDescription = "Specialist pathophysiology, molecular mechanisms, and complex differentials",
        targetAudience = "Specialists, Fellows, Senior Registrars & Subspecialists",
        iconName = "Psychology",
        promptDirective = """
            ADOPT AN ADVANCED SUBSPECIALIST CLINICAL LEVEL:
            - Detail cellular, molecular, and receptor-level pathophysiological mechanisms.
            - Explore atypical, multi-system, and subtle clinical disease variants.
            - Formulate complex, nuanced differential diagnoses with detailed distinguishing criteria.
            - Cover second-line, third-line, salvage therapies, and refractory management algorithms.
            - Discuss receptor pharmacodynamics, organ-specific clearance kinetics, and emerging therapeutic targets.
        """.trimIndent()
    ),

    CLINICAL_PROFESSIONAL(
        id = "CLINICAL_PROFESSIONAL",
        title = "Clinical Professional",
        shortDescription = "Actionable workflow, tiered diagnostics with thresholds, and specific drug regimens",
        targetAudience = "Attending Physicians, Hospitalists, General Practitioners & Medical Officers",
        iconName = "LocalHospital",
        promptDirective = """
            ADOPT AN ACTION-ORIENTED CLINICAL PROFESSIONAL PERSPECTIVE:
            - Provide structured, practical clinical workflows (History, Focused Examination, Tiered Investigations, Stepped Management).
            - Include specific diagnostic thresholds and cutoffs for laboratory biomarkers and imaging.
            - For all pharmacological interventions, specify: exact drug name, dose, route, frequency, duration, monitoring requirements, and contraindications.
            - Detail clear patient monitoring parameters, red-flag escalation triggers, and criteria for intensive care or specialist transfer.
            - Differentiate clearly between immediate acute stabilization and long-term maintenance management.
        """.trimIndent()
    ),

    MEDICAL_STUDENT(
        id = "MEDICAL_STUDENT",
        title = "Medical Student",
        shortDescription = "Structured academic clarity, anatomy/physiology correlation, and high-yield exam points",
        targetAudience = "MBBS / MD Undergraduates & Board Exam Candidates",
        iconName = "School",
        promptDirective = """
            ADOPT A STRUCTURED MEDICAL EDUCATOR & PEDAGOGICAL PERSPECTIVE:
            - Provide clear, well-organized explanations with textbook clarity.
            - Correlate clinical presentations directly with anatomical structures, physiological pathways, and pathological changes.
            - Highlight classical diagnostic triads, hallmark signs, and high-yield examination facts.
            - Explain the pathophysiological rationale behind physical examination findings and abnormal test results.
            - Structure topics systematically: Definition & Epidemiology, Aetiopathogenesis, Clinical Features, Investigations, and Management Principles.
        """.trimIndent()
    ),

    QUICK_REFERENCE_VIVA(
        id = "QUICK_REFERENCE_VIVA",
        title = "Quick Reference / Viva",
        shortDescription = "High-yield rapid bullets, diagnostic criteria lists, viva questions, and mnemonics",
        targetAudience = "Ward Rounds, On-Call Shifts & Oral Viva Revision",
        iconName = "FlashOn",
        promptDirective = """
            ADOPT A RAPID-FIRE CLINICAL REFERENCE & VIVA REVISION FORMAT:
            - Present information in crisp, high-yield bulleted points and summary lists.
            - Include recognized clinical diagnostic criteria (e.g., modified Duke, McDonald, Jones, Rome IV) in clear tabular/list format.
            - State first-line drug choices with exact adult/paediatric doses immediately.
            - Provide vital red-flag checklists and immediate "must-not-miss" life threats.
            - Include high-yield viva examination questions with concise model answers and validated clinical mnemonics.
        """.trimIndent()
    );

    companion object {
        val DEFAULT = MEDICAL_STUDENT

        fun fromString(value: String?): ResponseStyle {
            if (value.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.id.equals(value, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
