package com.agentx.medical.intelligence

/**
 * Conversation-level preference controlling the depth and token budget of medical generation.
 * Exactly 5 levels that can be adjusted on the fly per conversation via composer controls.
 * Default: [DETAILED]
 */
enum class AnswerDepth(
    val id: String,
    val title: String,
    val shortDescription: String,
    val tokenBudget: Int,
    val iconName: String,
    val promptDirective: String
) {
    EXPERT(
        id = "EXPERT",
        title = "Expert",
        shortDescription = "Exhaustive depth, rare presentations, multi-tier management, and controversies",
        tokenBudget = 4096,
        iconName = "AutoAwesome",
        promptDirective = """
            ANSWER DEPTH: EXPERT (COMPREHENSIVE & EXHAUSTIVE)
            - Provide an exhaustive, deeply comprehensive clinical appraisal.
            - Address all standard sections plus atypical variants, sub-phenotypes, and multi-morbid complexities.
            - Explore pathophysiological molecular cascades and pharmacokinetic subtleties in full detail.
            - Provide exhaustive multi-tier management algorithms, including refractory escalation pathways and specialized ICU/interventional procedures.
            - Detail rare drug adverse effects, specific drug interactions, and institutional guideline discrepancies.
        """.trimIndent()
    ),

    ADVANCED(
        id = "ADVANCED",
        title = "Advanced",
        shortDescription = "In-depth breakdown, detailed mechanisms, diagnostic thresholds, and stepped therapy",
        tokenBudget = 3072,
        iconName = "TrendingUp",
        promptDirective = """
            ANSWER DEPTH: ADVANCED (IN-DEPTH CLINICAL BREAKDOWN)
            - Deliver an in-depth medical analysis with thorough clinical explanations.
            - Detail underlying disease mechanisms and pathophysiological progression.
            - Provide an extensive differential diagnosis with specific supporting and refuting clinical features.
            - Detail investigative pathways with exact numeric diagnostic thresholds and imaging findings.
            - Detail stepped pharmacological and non-pharmacological treatment regimens with dosing, routes, and monitoring.
        """.trimIndent()
    ),

    DETAILED(
        id = "DETAILED",
        title = "Detailed",
        shortDescription = "Systematic structured clinical coverage across definitions, workup, and management",
        tokenBudget = 2048,
        iconName = "FormatListBulleted",
        promptDirective = """
            ANSWER DEPTH: DETAILED (STRUCTURED FULL WORKUP)
            - Provide a comprehensive, systematically structured medical response.
            - Systematically cover: Definition & Epidemiology, Aetiology & Pathophysiology, Clinical Features, Investigations (with key diagnostic thresholds), and Management (immediate, definitive, supportive).
            - Detail primary differential diagnoses and the rationale for investigation selection.
            - Provide complete pharmacological details for first-line therapies (drugs, doses, routes, monitoring) and common complications.
        """.trimIndent()
    ),

    STANDARD(
        id = "STANDARD",
        title = "Standard",
        shortDescription = "Balanced explanation of core definitions, findings, and primary management",
        tokenBudget = 1024,
        iconName = "Subject",
        promptDirective = """
            ANSWER DEPTH: STANDARD (BALANCED CLINICAL SUMMARY)
            - Provide a clear, balanced, and direct medical explanation.
            - Focus on core definitions, cardinal signs and symptoms, essential diagnostic investigations, and first-line management principles.
            - Include key differential diagnoses and major safety warnings without excessive minutiae.
            - Maintain high clinical utility while keeping the narrative focused and readable.
        """.trimIndent()
    ),

    BRIEF(
        id = "BRIEF",
        title = "Brief",
        shortDescription = "Crisp high-yield summary of essential facts, red flags, and immediate actions",
        tokenBudget = 512,
        iconName = "Bolt",
        promptDirective = """
            ANSWER DEPTH: BRIEF (CONCISE & HIGH-YIELD)
            - Provide a crisp, highly focused, actionable summary.
            - State the primary diagnosis/concept, essential clinical facts, core diagnostic criteria, and immediate first-line action points.
            - Highlight critical red flags and vital safety contraindications.
            - Avoid lengthy background narratives; prioritize immediate clinical relevance.
        """.trimIndent()
    );

    companion object {
        val DEFAULT = DETAILED

        fun fromString(value: String?): AnswerDepth {
            if (value.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.id.equals(value, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
