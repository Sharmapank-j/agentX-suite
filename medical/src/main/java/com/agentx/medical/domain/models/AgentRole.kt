package com.agentx.medical.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class AgentRole(val title: String, val description: String) {
    SUPERVISOR(
        "Chief Orchestrator & Supervisor",
        "Orchestrates complex multi-agent workflows, assigns subtasks to specialized agents, and compiles the final comprehensive response."
    ),
    PLANNER(
        "Task & Strategy Planner",
        "Decomposes complex user requests, multi-step goals, and projects into structured, executable steps."
    ),
    RESEARCHER(
        "Deep Researcher & Fact-Finder",
        "Investigates complex topics, gathers factual background, verifies data, and synthesizes technical documentation."
    ),
    CODER(
        "Code & Systems Architect",
        "Writes high-performance code, architectures software solutions, debugs errors, and optimizes algorithms across all programming languages."
    ),
    ANALYST(
        "Data & Quantitative Analyst",
        "Conducts mathematical computations, statistical calculations, financial modeling, and structured data analysis."
    ),
    REVIEWER(
        "Quality & Safety Reviewer",
        "Audits solutions for correctness, edge-case coverage, security vulnerabilities, and logical soundness."
    ),
    SYNTHESIZER(
        "Master Synthesizer & Writer",
        "Integrates multi-agent findings into a clear, unified, and beautifully styled response with actionable summaries."
    ),
    ASSISTANT(
        "AgentX General Assistant",
        "Versatile, intuitive personal conversational assistant (ChatGPT style) for everyday queries, brainstorming, and daily productivity."
    ),
    CUSTOM(
        "Custom Specialist",
        "User-defined custom agent with customizable personality, instructions, and domain capabilities."
    )
}
