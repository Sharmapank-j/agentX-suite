package com.agentx.medical.medical

import android.app.Application
import com.agentx.medical.core.agent.orchestrator.ClinicalAgentExecutor
import com.agentx.medical.core.agent.orchestrator.ClinicalSupervisor
import com.agentx.medical.core.agent.prompts.PromptManager
import com.agentx.medical.core.agent.tools.ClinicalToolDefinitions
import com.agentx.medical.core.agent.tools.ClinicalToolExecutor
import com.agentx.medical.core.agent.tools.ToolRegistry
import com.agentx.medical.core.inference.CloudInferenceEngine
import com.agentx.medical.core.inference.HuggingFaceDownloader
import com.agentx.medical.core.inference.HybridInferenceEngine
import com.agentx.medical.core.inference.InferenceEngine
import com.agentx.medical.core.inference.LlamaCppEngine
import com.agentx.medical.data.local.AgentXMedicalDatabase
import com.agentx.medical.data.local.ModelFileManager
import com.agentx.medical.data.repository.AgentRepository
import com.agentx.medical.data.repository.AgentRepositoryImpl
import com.agentx.medical.data.repository.ConversationRepository
import com.agentx.medical.data.repository.ConversationRepositoryImpl
import com.agentx.medical.data.repository.ExecutionRepository
import com.agentx.medical.data.repository.ExecutionRepositoryImpl
import com.agentx.medical.data.repository.MemoryRepository
import com.agentx.medical.data.repository.MemoryRepositoryImpl
import com.agentx.medical.data.repository.ModelRepository
import com.agentx.medical.data.repository.ModelRepositoryImpl
import com.agentx.medical.data.repository.ToolRepository
import com.agentx.medical.data.repository.ToolRepositoryImpl
import com.agentx.medical.domain.models.AgentRole

class AgentXMedicalApplication : Application() {

    // ─── Infrastructure ───────────────────────────────────────────────────────

    lateinit var database: AgentXMedicalDatabase
        private set

    lateinit var modelRepository: ModelRepository
        private set

    lateinit var modelFileManager: ModelFileManager
        private set

    lateinit var huggingFaceDownloader: HuggingFaceDownloader
        private set

    lateinit var inferenceEngine: InferenceEngine
        private set

    lateinit var hybridInferenceEngine: HybridInferenceEngine
        private set

    // ─── Repositories ─────────────────────────────────────────────────────────

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var agentRepository: AgentRepository
        private set

    lateinit var toolRepository: ToolRepository
        private set

    lateinit var executionRepository: ExecutionRepository
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    // ─── Phase 5 & 6: Agent Runtime & Collaboration ──────────────────────────

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var clinicalToolExecutor: ClinicalToolExecutor
        private set

    lateinit var executionQueue: com.agentx.medical.core.agent.execution.ExecutionQueue
        private set

    lateinit var clinicalSupervisor: ClinicalSupervisor
        private set

    override fun onCreate() {
        super.onCreate()

        // Infrastructure
        database = AgentXMedicalDatabase.getInstance(this)
        modelFileManager = ModelFileManager(this)
        huggingFaceDownloader = HuggingFaceDownloader(this, modelFileManager)
        val localLlama = LlamaCppEngine()
        val airLlm = com.agentx.medical.core.inference.AirLlmEngine()
        val cloudEngine = CloudInferenceEngine()
        hybridInferenceEngine = HybridInferenceEngine(
            llamaEngine = localLlama,
            airLlmEngine = airLlm,
            cloudEngine = cloudEngine,
            activeLocalBackend = com.agentx.medical.core.inference.LocalBackendType.AIR_LLM
        )
        inferenceEngine = hybridInferenceEngine

        // Repositories
        modelRepository = ModelRepositoryImpl(database.modelDao())
        conversationRepository = ConversationRepositoryImpl(database.conversationDao(), database.messageDao())
        agentRepository = AgentRepositoryImpl(database.agentDao(), database.teamDao())
        toolRepository = ToolRepositoryImpl(database.toolDao())
        executionRepository = ExecutionRepositoryImpl(database.executionDao())
        memoryRepository = MemoryRepositoryImpl(database.memoryDao())

        // ── Phase 5: Tool Registry ────────────────────────────────────────────
        toolRegistry = ToolRegistry()
        ClinicalToolDefinitions.all.forEach { toolRegistry.register(it) }

        // ── Phase 5: Tool Executor ────────────────────────────────────────────
        clinicalToolExecutor = ClinicalToolExecutor()

        // ── Phase 5: Per-role ClinicalAgentExecutors ──────────────────────────
        val agentExecutors: Map<AgentRole, ClinicalAgentExecutor> = AgentRole.entries
            .filter { it != AgentRole.SUPERVISOR } // Supervisor is the orchestrator itself
            .associate { role ->
                role to ClinicalAgentExecutor(
                    id = "agent_${role.name.lowercase()}",
                    name = role.title,
                    role = role,
                    systemPrompt = PromptManager.buildAgentSystemPrompt(
                        role = role,
                        tools = ClinicalToolDefinitions.all
                    ),
                    inferenceEngine = inferenceEngine,
                    toolRegistry = toolRegistry,
                    toolExecutor = clinicalToolExecutor
                )
            }

        // ── Phase 6: Multi-Agent Collaboration & Supervisor ──────────────────
        executionQueue = com.agentx.medical.core.agent.execution.ExecutionQueue()

        clinicalSupervisor = ClinicalSupervisor(
            agents = agentExecutors,
            inferenceEngine = inferenceEngine,
            executionRepository = executionRepository,
            executionQueue = executionQueue
        )
    }
}
