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

        // 7. Clinical Reasoning & Output Formatting Guidelines Tailored to Style and Depth
        builder.appendLine("CLINICAL FORMATTING & STRUCTURAL MANDATES:")
        when (style) {
            ResponseStyle.QUICK_REFERENCE_VIVA -> {
                builder.appendLine("- MANDATORY STYLE FORMAT: Present information in rapid-fire bulleted lists, high-yield diagnostic criteria, and 3-4 High-Yield Viva Questions with concise model answers.")
                builder.appendLine("- Highlight clinical mnemonics and rapid bedside red flags.")
                builder.appendLine("- Strictly avoid long explanatory paragraphs, narrative essays, or multi-page background histories.")
            }
            ResponseStyle.RESEARCH -> {
                builder.appendLine("- MANDATORY STYLE FORMAT: Present an evidence-based clinical trial and methodological appraisal.")
                builder.appendLine("- Organize into: Landmark Randomized Controlled Trials (with trial acronyms, study endpoints, hazard/odds ratios), Systematic Reviews/Cochrane Evidence, GRADE Quality Assessment, and Acknowledged Knowledge/Evidence Gaps.")
                builder.appendLine("- Strictly avoid introductory student tutorials, basic patient educational material, or generic disease summaries.")
            }
            ResponseStyle.ADVANCED -> {
                builder.appendLine("- MANDATORY STYLE FORMAT: High-level subspecialist clinical consultation.")
                builder.appendLine("- Deeply detail cellular and molecular pathophysiology, receptor-level pharmacodynamics, atypical/subtle disease variants, and refractory second/third-line salvage therapies.")
            }
            ResponseStyle.CLINICAL_PROFESSIONAL -> {
                builder.appendLine("- MANDATORY STYLE FORMAT: Action-oriented bedside clinical protocol.")
                builder.appendLine("- Organize into: Immediate Bedside Triage & Initial Orders, Focused Diagnostic Cutoffs (exact numeric laboratory and imaging thresholds), Prescriptive Pharmacotherapy (exact drug name, adult/pediatric dose, route, frequency, monitoring), and Red-Flag Escalation/ICU Transfer Triggers.")
            }
            ResponseStyle.MEDICAL_STUDENT -> {
                builder.appendLine("- MANDATORY STYLE FORMAT: Structured academic textbook teaching style.")
                builder.appendLine("- Correlate clinical presentations directly with anatomical structures and pathophysiological mechanisms. Highlight classic diagnostic triads, pathognomonic physical signs, and high-yield board examination pearls.")
            }
        }

        when (depth) {
            AnswerDepth.BRIEF -> {
                builder.appendLine("- STRICT DEPTH CONSTRAINT: BRIEF. The total response MUST be ultra-concise (strictly under 150-200 words). Provide ONLY 4-6 high-yield bullet points or direct essential answers. Omit introductory pleasantries, narrative preambles, and repetitive disclaimers.")
            }
            AnswerDepth.STANDARD -> {
                builder.appendLine("- DEPTH CONSTRAINT: STANDARD. Provide a balanced, focused clinical summary (approx. 250-400 words) covering cardinal definitions, primary diagnostics, and first-line management without excessive sub-specialty minutiae.")
            }
            AnswerDepth.DETAILED -> {
                builder.appendLine("- DEPTH CONSTRAINT: DETAILED. Comprehensive, systematically structured full clinical workup (approx. 500-800 words) spanning etiology, clinical features, tiered investigations with thresholds, and stepped management.")
            }
            AnswerDepth.ADVANCED -> {
                builder.appendLine("- DEPTH CONSTRAINT: ADVANCED. In-depth clinical breakdown (approx. 800-1200 words) with detailed mechanistic insight, numeric diagnostic cutoffs, and stepped multi-tier management algorithms.")
            }
            AnswerDepth.EXPERT -> {
                builder.appendLine("- DEPTH CONSTRAINT: EXPERT. Exhaustive master-level appraisal (deep comprehensive coverage). Exhaustively detail atypical variants, complex multi-morbidities, refractory escalation pathways, specialized interventional options, and institutional discrepancies.")
            }
        }

        builder.appendLine("- Format with clean Markdown (## headings for major sections, bullet lists for quick points, tables for comparative thresholds).")
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

    /**
     * Augments the user prompt with explicit style and depth steering anchors.
     * Ensures small on-device models (e.g. 1B-3B quantized GGUF) strictly obey persona and length
     * constraints even in single-turn or multi-turn chats.
     */
    fun augmentUserPrompt(
        userPrompt: String,
        style: ResponseStyle,
        depth: AnswerDepth
    ): String {
        val styleTag = when (style) {
            ResponseStyle.RESEARCH -> "Style: Research Appraisal (Cite trial evidence, GRADE levels, and meta-analyses)"
            ResponseStyle.ADVANCED -> "Style: Advanced Subspecialist (Deep pathophysiology, molecular mechanisms, and refractory management)"
            ResponseStyle.CLINICAL_PROFESSIONAL -> "Style: Clinical Professional (Actionable bedside protocol, exact drug dosing, and escalation triggers)"
            ResponseStyle.MEDICAL_STUDENT -> "Style: Medical Student (Correlate with anatomy/pathology, highlight classic triads and exam pearls)"
            ResponseStyle.QUICK_REFERENCE_VIVA -> "Style: Quick Reference / Viva (Rapid-fire bullets, mnemonics, and viva Q&A)"
        }

        val depthTag = when (depth) {
            AnswerDepth.BRIEF -> "Depth: BRIEF (Max 150 words, concise high-yield bullet points only)"
            AnswerDepth.STANDARD -> "Depth: STANDARD (Balanced ~300-word summary of core essentials)"
            AnswerDepth.DETAILED -> "Depth: DETAILED (Structured comprehensive workup)"
            AnswerDepth.ADVANCED -> "Depth: ADVANCED (In-depth analysis with numeric thresholds)"
            AnswerDepth.EXPERT -> "Depth: EXPERT (Exhaustive appraisal covering atypical & refractory cases)"
        }

        return "[$styleTag | $depthTag]\n$userPrompt"
    }
}
