package com.agentx.medical.intelligence

/**
 * Builds the complete clinical prompt for the local Medimind model.
 * Seamlessly harmonizes:
 * - 21 Medical Disciplines
 * - 20+ Medical Query Intents
 * - 3 Triage Urgency Levels
 * - 5 Response Styles (User-level Preference)
 * - 5 Answer Depths (Conversation-level Preference)
 * Creating a robust 25-combination matrix where neither style nor depth overrides the other.
 */
object MedicalPromptBuilder {

    fun buildSystemPrompt(
        classification: MedicalQueryClassification,
        style: ResponseStyle = ResponseStyle.DEFAULT,
        depth: AnswerDepth = AnswerDepth.DEFAULT
    ): String {
        val builder = StringBuilder()

        // 1. Foundation Persona
        builder.appendLine("You are MediMind, a professional, evidence-based, on-device medical language model powering AgentX Medical.")
        builder.appendLine("You provide accurate, medically rigorous, and actionable clinical intelligence for healthcare professionals, resident physicians, and medical students.")
        builder.appendLine()

        // 2. Emergency & Triage Priority
        if (classification.urgency == UrgencyLevel.EMERGENCY) {
            builder.appendLine("================================================================================")
            builder.appendLine(classification.urgency.promptDirectives)
            builder.appendLine("================================================================================")
            builder.appendLine()
        } else if (classification.urgency == UrgencyLevel.URGENT) {
            builder.appendLine(classification.urgency.promptDirectives)
            builder.appendLine()
        }

        // 3. Clinical Discipline Context
        builder.appendLine("CLINICAL SPECIALTY FOCUS: ${classification.primaryDiscipline.title.uppercase()}")
        builder.appendLine("Discipline Scope: ${classification.primaryDiscipline.description}")
        if (classification.secondaryDiscipline != null) {
            builder.appendLine("Secondary Interdisciplinary Consideration: ${classification.secondaryDiscipline.title}")
        }
        builder.appendLine()

        // 4. Clinical Query Intent Directive (Augmented by dynamic OTA guideline directives if available)
        val dynamicDirective = DynamicMedicalKnowledgeManager.getUpdatedGuidelineDirective(classification.intent.name)
            ?: DynamicMedicalKnowledgeManager.getUpdatedGuidelineDirective(classification.primaryDiscipline.name)
        if (dynamicDirective != null && dynamicDirective.isNotBlank()) {
            builder.appendLine("LATEST CLINICAL GUIDELINE DIRECTIVE (OTA Hot-Updated):")
            builder.appendLine(dynamicDirective)
        } else {
            builder.appendLine(classification.intent.promptDirectives)
        }
        builder.appendLine()

        // 5. Response Style (User-Level Persistent Preference - 1 of 5)
        builder.appendLine("================================================================================")
        builder.appendLine("ACTIVE RESPONSE STYLE: [${style.title.uppercase()}]")
        builder.appendLine("Target Audience: ${style.targetAudience}")
        builder.appendLine(style.promptDirective)
        builder.appendLine("================================================================================")
        builder.appendLine()

        // 6. Answer Depth (Conversation-Level Preference - 1 of 5)
        builder.appendLine("================================================================================")
        builder.appendLine("ACTIVE ANSWER DEPTH: [${depth.title.uppercase()}]")
        builder.appendLine(depth.promptDirective)
        builder.appendLine("================================================================================")
        builder.appendLine()

        // 7. Clinical Reasoning & Output Formatting Guidelines
        builder.appendLine("CLINICAL FORMATTING & STRUCTURAL MANDATES:")
        builder.appendLine("- Format your response using clean Markdown with hierarchical headings (##, ###).")
        builder.appendLine("- Use Markdown tables (| Column 1 | Column 2 | Column 3 |) for comparative data, investigation thresholds, differential tables, or medication dosing where appropriate.")
        builder.appendLine("- When discussing differential diagnoses, always state likelihood and differentiating features.")
        builder.appendLine("- When discussing medications, include exact drug names, adult/pediatric dosages, route, frequency, and vital monitoring precautions.")
        builder.appendLine("- Internal reasoning: You may use an opening <think> ... </think> block for step-by-step diagnostic breakdown, after which output the clean, structured clinical answer.")
        builder.appendLine()

        // 8. Core Medical Safety Guardrail
        builder.appendLine("CLINICAL GOVERNANCE:")
        builder.appendLine("This information serves as clinical decision support. Always correlate with direct clinical examination, patient vital signs, and institutional protocols.")

        return builder.toString().trim()
    }

    /**
     * Helper to compute dynamic max output tokens based on AnswerDepth and hardware limits.
     */
    fun computeMaxTokens(
        depth: AnswerDepth,
        contextLength: Int = 3840,
        estimatedPromptTokens: Int = 512
    ): Int {
        val requestedBudget = depth.tokenBudget
        val availableInContext = (contextLength - estimatedPromptTokens - 64).coerceAtLeast(256)
        return minOf(requestedBudget, availableInContext).coerceIn(256, 4096)
    }
}
