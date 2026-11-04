package com.agentx.core.agent.orchestrator

import com.agentx.core.agent.core.AgentContext
import com.agentx.core.agent.core.AgentInstance
import com.agentx.core.agent.core.AgentResult
import com.agentx.core.agent.core.ToolCallRequest
import com.agentx.core.agent.core.ToolCallResult
import com.agentx.core.agent.tools.ClinicalToolExecutor
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.core.inference.ContextBudget
import com.agentx.core.inference.ContextManager
import com.agentx.core.inference.InferenceEngine
import com.agentx.core.inference.InferenceRequest
import com.agentx.domain.models.AgentRole
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SubTask(
    val id: String,
    val title: String,
    val assignedRole: AgentRole,
    val instructions: String,
    val dependencies: List<String> = emptyList(),
    val isCompleted: Boolean = false
)

@Serializable
data class ExecutionPlan(
    val originalGoal: String,
    val subtasks: List<SubTask>,
    val createdAt: Long = System.currentTimeMillis()
)

interface TaskPlanner {
    suspend fun createPlan(userGoal: String, availableAgents: List<AgentRole>): ExecutionPlan
}

interface TaskDispatcher {
    suspend fun dispatch(subTask: SubTask, context: AgentContext): AgentResult
}

interface ResultAggregator {
    suspend fun synthesize(originalGoal: String, results: List<AgentResult>): String
}

interface Supervisor {
    suspend fun coordinate(userGoal: String, conversationId: String): AgentResult
}

// ─── Tool Call Parser ─────────────────────────────────────────────────────────

/**
 * Parses tool_call blocks that the LLM may emit in formats like:
 *   <tool_call>{"name": "tool_clinical_calc", "arguments": {...}}</tool_call>
 */
object ToolCallParser {
    private val TOOL_CALL_REGEX = Regex(
        """<tool_call>\s*(\{.*?})\s*</tool_call>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun extract(text: String): List<ToolCallRequest> {
        return TOOL_CALL_REGEX.findAll(text).mapNotNull { match ->
            try {
                val json = Json.parseToJsonElement(match.groupValues[1]).jsonObject
                val name = json["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val args = json["arguments"]?.jsonObject?.toString() ?: "{}"
                ToolCallRequest(toolId = name, argumentsJson = args)
            } catch (_: Exception) {
                null
            }
        }.toList()
    }

    fun hasToolCall(text: String): Boolean = TOOL_CALL_REGEX.containsMatchIn(text)

    /**
     * Returns the response text with tool_call blocks stripped, leaving only the
     * human-readable clinical reasoning portion.
     */
    fun stripToolCalls(text: String): String = TOOL_CALL_REGEX.replace(text, "").trim()
}

// ─── Single-Agent Executor (with tool calling) ────────────────────────────────

class ClinicalAgentExecutor(
    override val id: String,
    override val name: String,
    override val role: AgentRole,
    override val systemPrompt: String,
    private val inferenceEngine: InferenceEngine,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ClinicalToolExecutor,
    private val maxToolCallRounds: Int = 3
) : AgentInstance {

    override suspend fun execute(context: AgentContext): AgentResult {
        if (context.isCancelled) {
            return AgentResult(
                agentId = id,
                taskId = context.taskId,
                output = "[Task cancelled before execution]",
                executionStatus = "CANCELLED"
            )
        }

        val startTime = System.currentTimeMillis()
        val allToolCalls = mutableListOf<ToolCallResult>()
        var currentPrompt = buildAgentPrompt(context)
        var finalOutput = ""
        var totalTokens = 0

        // Tool-call agentic loop (up to maxToolCallRounds)
        for (round in 1..maxToolCallRounds) {
            if (context.isCancelled) break

            val tokens = inferenceEngine.generate(
                InferenceRequest(
                    prompt = currentPrompt,
                    maxTokens = 512,
                    temperature = 0.3f,
                    topP = 0.9f
                )
            ).toList()

            val rawOutput = tokens.filterIsInstance<com.agentx.core.inference.GenerationEvent.Token>()
                .joinToString("") { it.text }
            val finishEvent = tokens.filterIsInstance<com.agentx.core.inference.GenerationEvent.Finished>().firstOrNull()
            totalTokens += finishEvent?.totalTokens ?: (rawOutput.length / 4)

            // Check for tool calls in the output
            if (!ToolCallParser.hasToolCall(rawOutput)) {
                finalOutput = rawOutput
                break
            }

            // Execute all extracted tool calls
            val toolCalls = ToolCallParser.extract(rawOutput)
            val toolResults = toolCalls.map { req ->
                toolExecutor.execute(req.toolId, req.argumentsJson)
            }
            allToolCalls.addAll(toolResults)

            // Build next-round prompt injecting real tool results
            val toolResultContext = toolResults.joinToString("\n") { result ->
                "<tool_result name=\"${result.toolId}\">\n${result.output}\n</tool_result>"
            }
            currentPrompt = buildString {
                appendLine(currentPrompt)
                appendLine(ToolCallParser.stripToolCalls(rawOutput))
                appendLine(toolResultContext)
                appendLine("<|assistant|>")
            }

            finalOutput = ToolCallParser.stripToolCalls(rawOutput)
        }

        return AgentResult(
            agentId = id,
            taskId = context.taskId,
            output = finalOutput.ifBlank { "[No output generated]" },
            toolCalls = allToolCalls,
            executionStatus = "COMPLETED",
            tokenUsage = totalTokens,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun buildAgentPrompt(context: AgentContext): String {
        val toolsBlock = toolRegistry.getAll().joinToString("\n") { tool ->
            "- ${tool.id}: ${tool.description}\n  Schema: ${tool.parametersJsonSchema}"
        }

        val systemWithTools = if (toolsBlock.isNotBlank()) {
            """$systemPrompt

## Available Clinical Tools
To call a tool, emit a block in this exact format:
<tool_call>{"name": "tool_id", "arguments": {...}}</tool_call>

Available tools:
$toolsBlock"""
        } else {
            systemPrompt
        }

        val history = context.sharedMemory.joinToString("\n")
        val intermediateContext = context.intermediateResults.entries.joinToString("\n") {
            "### ${it.key} Result:\n${it.value}"
        }

        return ContextManager.buildPromptWithContext(
            systemPrompt = systemWithTools,
            history = emptyList(),
            currentPrompt = buildString {
                if (intermediateContext.isNotBlank()) {
                    appendLine("## Prior Agent Findings:\n$intermediateContext\n")
                }
                if (history.isNotBlank()) {
                    appendLine("## Shared Context:\n$history\n")
                }
                appendLine("## Your Task:\n${context.userGoal}")
            },
            budget = ContextBudget(maxContextTokens = 2048, maxOutputTokens = 512)
        )
    }
}

// ─── Supervisor Orchestrator Implementation ───────────────────────────────────

/**
 * Robust JSON Parser for structured ExecutionPlans emitted by LLMs.
 * Handles markdown formatting (e.g. ```json ... ```), partial text, and raw JSON.
 */
object PlanParser {
    private val JSON_BLOCK_REGEX = Regex(
        """```(?:json)?\s*(\{.*?\})\s*```""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parse(rawText: String): ExecutionPlan? {
        val jsonCandidate = JSON_BLOCK_REGEX.find(rawText)?.groupValues?.get(1)
            ?: rawText.substringAfter('{').substringBeforeLast('}').let {
                if (it.isNotBlank()) "{$it}" else null
            } ?: return null

        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            json.decodeFromString<ExecutionPlan>(jsonCandidate)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Versatile task plan generator that dynamically creates structured,
 * sequential subtasks tailored to coding, research, analysis, planning, and medical inquiries.
 */
class HeuristicTaskPlanner : TaskPlanner {
    override suspend fun createPlan(userGoal: String, availableAgents: List<AgentRole>): ExecutionPlan {
        val lower = userGoal.lowercase()
        val subtasks = mutableListOf<SubTask>()

        // 1. Initial Plan Decomposition
        subtasks.add(
            SubTask(
                id = "subtask_plan",
                title = "Task Decomposition & Parameter Extraction",
                assignedRole = AgentRole.PLANNER,
                instructions = "Analyze '$userGoal'. Decompose the request into key parameters, requirements, and ordered execution steps.",
                dependencies = emptyList()
            )
        )

        // 2. Calculations / Dosing / Coding / Tools
        val hasCoding = lower.contains("code") || lower.contains("kotlin") || lower.contains("python") ||
                lower.contains("java") || lower.contains("script") || lower.contains("function") ||
                lower.contains("class") || lower.contains("bug") || lower.contains("algorithm") ||
                lower.contains("build") || lower.contains("api") || lower.contains("endpoint")

        val hasDosingOrDrug = lower.contains("dose") || lower.contains("drug") || lower.contains("medication") ||
                lower.contains("interact") || lower.contains("prescri") || lower.contains("antibiotic") ||
                lower.contains("anticoagul") || lower.contains("statin") || lower.contains("egfr") ||
                lower.contains("creatinine") || lower.contains("cha2ds2") || lower.contains("warfarin") ||
                lower.contains("aspirin") || lower.contains("calc") || lower.contains("bleeding") ||
                lower.contains("ckd") || lower.contains("stroke") || lower.contains("risk") || hasCoding

        if (hasDosingOrDrug) {
            subtasks.add(
                SubTask(
                    id = "subtask_coder",
                    title = "Code Architecture, Calculations & Risk Scoring",
                    assignedRole = AgentRole.CODER,
                    instructions = "Execute on-device calculators or implement code algorithms for '$userGoal'.",
                    dependencies = listOf("subtask_plan")
                )
            )
        }

        // 3. Diagnostics / Physiology / Data Analysis
        val hasLabOrDiagnosis = lower.contains("lab") || lower.contains("blood") || lower.contains("result") ||
                lower.contains("panel") || lower.contains("sodium") || lower.contains("troponin") ||
                lower.contains("hemoglobin") || lower.contains("diagnos") || lower.contains("curriculum") ||
                lower.contains("physiology") || lower.contains("study plan") || lower.contains("analyze") ||
                lower.contains("analysis") || lower.contains("compare")

        if (hasLabOrDiagnosis) {
            subtasks.add(
                SubTask(
                    id = "subtask_analyst",
                    title = "Diagnostic Reasoning & Analytical Interpretation",
                    assignedRole = AgentRole.ANALYST,
                    instructions = "Analyze findings, construct differential or analytical models, and evaluate mechanisms for '$userGoal'.",
                    dependencies = listOf("subtask_plan")
                )
            )
        }

        // 4. Evidence & Documentation Research
        subtasks.add(
            SubTask(
                id = "subtask_researcher",
                title = "Evidence & Authoritative Reference Review",
                assignedRole = AgentRole.RESEARCHER,
                instructions = "Retrieve verified evidence, reference documentation, and guidelines relevant to '$userGoal'.",
                dependencies = listOf("subtask_plan")
            )
        )

        // 5. Safety & Quality Audit
        val priorDependencies = subtasks.map { it.id }
        subtasks.add(
            SubTask(
                id = "subtask_reviewer",
                title = "Quality, Safety & Verification Screening",
                assignedRole = AgentRole.REVIEWER,
                instructions = "Perform a thorough audit of all prior findings. Screen for warnings, safety risks, edge cases, and contraindications.",
                dependencies = priorDependencies
            )
        )

        return ExecutionPlan(
            originalGoal = userGoal,
            subtasks = subtasks
        )
    }
}

/**
 * LLM-Driven Task Planner that requests an adaptive ExecutionPlan from the GGUF model,
 * seamlessly falling back to HeuristicTaskPlanner if offline inference is unavailable or non-JSON.
 */
class ClinicalTaskPlanner(
    private val inferenceEngine: InferenceEngine? = null,
    private val fallbackPlanner: TaskPlanner = HeuristicTaskPlanner()
) : TaskPlanner {

    override suspend fun createPlan(userGoal: String, availableAgents: List<AgentRole>): ExecutionPlan {
        if (inferenceEngine == null || inferenceEngine.getModelStatus().state != com.agentx.core.inference.ModelLifecycleState.LOADED) {
            return fallbackPlanner.createPlan(userGoal, availableAgents)
        }

        return try {
            val planningPrompt = """
You are the Task Planner. Decompose this user query into an execution plan JSON.
Available agent roles: ${availableAgents.joinToString(", ") { it.name }}

Query: "$userGoal"

Respond ONLY with a JSON object in this exact schema:
```json
{
  "originalGoal": "$userGoal",
  "subtasks": [
    {
      "id": "task_1",
      "title": "Short title",
      "assignedRole": "PLANNER",
      "instructions": "Specific task instructions",
      "dependencies": []
    }
  ]
}
```
            """.trimIndent()

            val tokens = inferenceEngine.generate(
                InferenceRequest(
                    prompt = planningPrompt,
                    maxTokens = 512,
                    temperature = 0.2f
                )
            ).toList()

            val output = tokens.filterIsInstance<com.agentx.core.inference.GenerationEvent.Token>()
                .joinToString("") { it.text }

            val parsedPlan = PlanParser.parse(output)
            if (parsedPlan != null && parsedPlan.subtasks.isNotEmpty()) {
                parsedPlan
            } else {
                fallbackPlanner.createPlan(userGoal, availableAgents)
            }
        } catch (_: Exception) {
            fallbackPlanner.createPlan(userGoal, availableAgents)
        }
    }
}

/**
 * Result Aggregator that synthesizes all multi-agent findings into a final physician-grade report.
 */
class ClinicalResultAggregator(
    private val inferenceEngine: InferenceEngine? = null
) : ResultAggregator {

    override suspend fun synthesize(originalGoal: String, results: List<AgentResult>): String {
        val validResults = results.filter { it.output.isNotBlank() && it.output != "[No output generated]" }

        return buildString {
            appendLine("## AgentX Multi-Agent Synthesis\n")
            appendLine("**Goal:** $originalGoal\n")
            appendLine("---\n")

            validResults.forEach { result ->
                appendLine("### Specialist (${result.agentId}) Output\n")
                appendLine(result.output.trim())
                if (result.toolCalls.isNotEmpty()) {
                    appendLine("\n**Verified Tool Executions:** ${result.toolCalls.joinToString(", ") { it.toolId }}")
                }
                appendLine("\n---\n")
            }

            appendLine("> ⚡ **AgentX Execution**: Synthesized across specialized autonomous agents with local tool verification.")
        }.trimEnd()
    }
}

class ClinicalSupervisor(
    private val agents: Map<AgentRole, ClinicalAgentExecutor>,
    private val inferenceEngine: InferenceEngine? = null,
    private val taskPlanner: TaskPlanner = ClinicalTaskPlanner(inferenceEngine),
    private val resultAggregator: ResultAggregator = ClinicalResultAggregator(inferenceEngine),
    private val executionRepository: com.agentx.data.repository.ExecutionRepository? = null,
    private val executionQueue: com.agentx.core.agent.execution.ExecutionQueue? = null,
    private val maxIterations: Int = 10,
    private val timeoutMs: Long = 180_000L
) : Supervisor {

    override suspend fun coordinate(userGoal: String, conversationId: String): AgentResult {
        val startTime = System.currentTimeMillis()
        val completedResults = mutableMapOf<String, AgentResult>()
        val allToolCalls = mutableListOf<ToolCallResult>()
        val sharedMemory = mutableListOf<String>()
        var totalTokens = 0

        // Step 1: Create Plan
        executionQueue?.updateProgress(
            conversationId = conversationId,
            step = 0,
            total = 1,
            role = AgentRole.PLANNER,
            agentId = "agent_planner",
            activity = "Decomposing query and formulating multi-agent plan..."
        )

        val plan = taskPlanner.createPlan(userGoal, agents.keys.toList())
        val totalSteps = plan.subtasks.size + 1

        // Step 2: Execute Subtasks in Order with Dependency Context
        for ((index, subTask) in plan.subtasks.withIndex()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                executionQueue?.fail(conversationId, "Workflow timed out after ${timeoutMs / 1000}s")
                break
            }

            val agent = agents[subTask.assignedRole] ?: agents.values.firstOrNull() ?: continue

            executionQueue?.updateProgress(
                conversationId = conversationId,
                step = index + 1,
                total = totalSteps,
                role = subTask.assignedRole,
                agentId = agent.id,
                activity = "${agent.name}: ${subTask.title}",
                intermediateOutputs = completedResults.values.map { "${it.agentId}: ${it.output.take(80)}..." }
            )

            // Gather dependencies outputs
            val depContext = subTask.dependencies.associateWith { depId ->
                completedResults[depId]?.output ?: ""
            }

            val context = AgentContext(
                conversationId = conversationId,
                taskId = subTask.id,
                userGoal = "${subTask.title}\n${subTask.instructions}",
                intermediateResults = depContext + completedResults.mapValues { it.value.output },
                sharedMemory = sharedMemory,
                currentIteration = index,
                maxIterations = maxIterations
            )

            val taskStartTime = System.currentTimeMillis()
            val result = agent.execute(context)
            val taskDuration = System.currentTimeMillis() - taskStartTime

            completedResults[subTask.id] = result
            allToolCalls.addAll(result.toolCalls)
            totalTokens += result.tokenUsage
            sharedMemory.add("${agent.name}: ${result.output}")

            // Log to ExecutionRepository
            executionRepository?.logExecution(
                com.agentx.domain.models.ExecutionLog(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    agentId = agent.id,
                    taskId = subTask.id,
                    status = if (result.executionStatus == "COMPLETED") com.agentx.domain.models.ExecutionStatus.COMPLETED else com.agentx.domain.models.ExecutionStatus.FAILED,
                    inputContext = context.userGoal,
                    outputResult = result.output,
                    toolCallsJson = try {
                        Json.encodeToString(result.toolCalls)
                    } catch (_: Exception) { "[]" },
                    tokenUsage = result.tokenUsage,
                    executionTimeMs = taskDuration,
                    errorDetails = result.errorDetails,
                    startedAt = taskStartTime,
                    completedAt = System.currentTimeMillis()
                )
            )
        }

        // Step 3: Synthesis
        executionQueue?.updateProgress(
            conversationId = conversationId,
            step = totalSteps,
            total = totalSteps,
            role = AgentRole.SYNTHESIZER,
            agentId = "agent_synthesizer",
            activity = "Consolidating specialist findings..."
        )

        val finalOutput = resultAggregator.synthesize(userGoal, completedResults.values.toList())

        val totalDuration = System.currentTimeMillis() - startTime
        executionQueue?.complete(conversationId, "Multi-agent execution complete.")

        // Log final supervisor aggregation
        executionRepository?.logExecution(
            com.agentx.domain.models.ExecutionLog(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = conversationId,
                agentId = "agent_supervisor",
                taskId = "${conversationId}_synthesis",
                status = com.agentx.domain.models.ExecutionStatus.COMPLETED,
                inputContext = userGoal,
                outputResult = finalOutput,
                toolCallsJson = "[]",
                tokenUsage = totalTokens,
                executionTimeMs = totalDuration,
                startedAt = startTime,
                completedAt = System.currentTimeMillis()
            )
        )

        return AgentResult(
            agentId = "agent_supervisor",
            taskId = "${conversationId}_final",
            output = finalOutput,
            toolCalls = allToolCalls,
            executionStatus = "COMPLETED",
            tokenUsage = totalTokens,
            executionTimeMs = totalDuration
        )
    }
}

typealias AutonomousSupervisor = ClinicalSupervisor
typealias AutonomousAgentExecutor = ClinicalAgentExecutor
typealias AutonomousTaskPlanner = ClinicalTaskPlanner
typealias AutonomousResultAggregator = ClinicalResultAggregator

