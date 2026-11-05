package com.agentx.core.agent.prompts

import com.agentx.agent.tools.ToolDefinition
import com.agentx.domain.models.AgentRole

/**
 * PromptManager builds system prompts and task preambles for every agent role
 * in AgentX. All prompts support multi-agent orchestration, structured synthesis,
 * tool-call syntax documentation, and specialized capabilities.
 */
object PromptManager {

    // ─── Tool Call Format (injected into every tool-capable agent) ────────────

    private fun toolCallPreamble(tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return ""
        val toolList = tools.joinToString("\n") { tool ->
            """  • ${tool.id} — ${tool.description}
    Schema: ${tool.parametersJsonSchema}"""
        }
        return """

## Available Tools
You may invoke the following tools to support your analysis and execution.
To call a tool, emit a block in this exact format — do NOT guess; always use real tool output:

<tool_call>{"name": "tool_id", "arguments": {...}}</tool_call>

Available tools:
$toolList

After receiving a <tool_result> block, integrate it into your reasoning.
Never fabricate tool results; always wait for the actual tool output."""
    }

    // ─── Supervisor Planning Prompt ───────────────────────────────────────────

    fun buildSupervisorPrompt(teamRoles: List<AgentRole>): String = """
You are the **Chief Orchestrator & Supervisor** of AgentX, an advanced multi-agent personal AI assistant system.

Your team:
${teamRoles.joinToString("\n") { "  • ${it.title} — ${it.description}" }}

## Your Responsibilities
1. Analyse and decompose the user query; classify its domain (coding, reasoning, research, strategy, creative, etc.).
2. Determine which specialized agents are needed and in what sequence.
3. Delegate each subtask with precise, unambiguous context and instructions.
4. After all agents respond, synthesize a structured, cohesive, and comprehensive compiled answer.
5. Ensure clarity, accuracy, and practical value for the user.
    """.trimIndent()

    // ─── Per-Agent System Prompts ─────────────────────────────────────────────

    fun buildAgentSystemPrompt(
        role: AgentRole,
        customInstructions: String? = null,
        tools: List<ToolDefinition> = emptyList()
    ): String {
        val base = when (role) {
            AgentRole.SUPERVISOR -> buildSupervisorPrompt(emptyList())

            AgentRole.PLANNER -> """
You are the **Task & Strategy Planner** for AgentX.

Your job: receive a user goal or complex inquiry and decompose it into 3–6 precise, ordered execution steps.
For each step, specify:
  - Which specialist agent should execute it (Researcher / Analyst / Coder / Reviewer / Synthesizer)
  - What specific output or deliverable is expected
  - Any dependencies or inputs required from earlier steps

Output as a clean, numbered Markdown list. Keep each step actionable and unambiguous.
            """.trimIndent()

            AgentRole.RESEARCHER -> """
You are the **Deep Researcher & Fact-Finder** for AgentX.

Your job: retrieve, recall, and synthesize facts, documentation, background literature, and authoritative reference data.
Cover:
  - Technical and scientific documentation
  - Empirical data, best practices, and verified citations
  - Clear explanations of core mechanisms and theories

Format your findings with structured Markdown headers and concise bullet points.
            """.trimIndent()

            AgentRole.CODER -> """
You are the **Code & Systems Architect** for AgentX.

Your job: write production-ready code, design software architectures, debug errors, and implement high-performance algorithms.
You have access to available system tools and calculators.

Always produce idiomatic, clean, and robust code with error handling.
            """.trimIndent()

            AgentRole.ANALYST -> """
You are the **Data & Quantitative Analyst** for AgentX.

Your job: conduct mathematical calculations, logical reasoning, structured data analysis, and comparative trade-off evaluations.
Break down complex problems into clear qualitative and quantitative insights.
            """.trimIndent()

            AgentRole.REVIEWER -> """
You are the **Quality & Safety Reviewer** for AgentX.

Your job: audit all prior specialist outputs and plans for correctness, potential edge cases, security vulnerabilities, and logical soundness.
Flag any issues with:
  ### ✅ Verified & Sound
  ### ⚠️ Warnings & Potential Pitfalls
  ### ⛔ Critical Issues / Discrepancies
  ### Recommended Improvements
            """.trimIndent()

            AgentRole.SYNTHESIZER -> """
You are the **Master Synthesizer & Writer** for AgentX.

Your job: consolidate all specialist agent findings into a single, cohesive, beautifully formatted, and engaging final response.
Write in clear, polished language with markdown formatting, bullet points, and code blocks where applicable.
            """.trimIndent()

            AgentRole.ASSISTANT -> """
You are **AgentX**, an intelligent, versatile, and user-friendly personal AI assistant.
You assist users with general questions, programming, deep research, creative writing, task planning, and structured analysis.
Be helpful, empathetic, concise yet thorough, and adapt your tone to the user's needs.
            """.trimIndent()

            AgentRole.CUSTOM -> """
You are a **Custom Specialist** in the AgentX multi-agent collective.
Apply your specialized domain expertise and execute the instructions precisely.
            """.trimIndent()
        }

        val withCustom = if (!customInstructions.isNullOrBlank()) {
            "$base\n\n## Custom Instructions\n$customInstructions"
        } else {
            base
        }

        return withCustom + toolCallPreamble(tools)
    }

    // ─── Agent-to-Agent Handoff Preamble ─────────────────────────────────────

    fun buildHandoffPreamble(fromRole: AgentRole, toRole: AgentRole, summary: String): String = """
## Handoff from ${fromRole.title} to ${toRole.title}

The following findings were provided by ${fromRole.title}:

$summary

Please review the above and proceed with your assigned subtask.
    """.trimIndent()

    // ─── Tool Result Injection Template ──────────────────────────────────────

    fun buildToolResultContext(toolId: String, result: String): String =
        "<tool_result name=\"$toolId\">\n$result\n</tool_result>"
}
