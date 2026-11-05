package com.agentx

import android.app.Application
import com.agentx.core.agent.orchestrator.ClinicalAgentExecutor
import com.agentx.core.agent.orchestrator.ClinicalSupervisor
import com.agentx.core.agent.prompts.PromptManager
import com.agentx.agent.tools.ClinicalToolDefinitions
import com.agentx.agent.tools.ClinicalToolExecutor
import com.agentx.agent.tools.ToolRegistry
import com.agentx.core.inference.CloudInferenceEngine
import com.agentx.core.inference.HuggingFaceDownloader
import com.agentx.core.inference.HybridInferenceEngine
import com.agentx.core.inference.InferenceEngine
import com.agentx.core.inference.LlamaCppEngine
import com.agentx.data.local.AgentXDatabase
import com.agentx.data.local.ModelFileManager
import com.agentx.data.repository.AgentRepository
import com.agentx.data.repository.AgentRepositoryImpl
import com.agentx.data.repository.ConversationRepository
import com.agentx.data.repository.ConversationRepositoryImpl
import com.agentx.data.repository.ExecutionRepository
import com.agentx.data.repository.ExecutionRepositoryImpl
import com.agentx.data.repository.MemoryRepository
import com.agentx.data.repository.MemoryRepositoryImpl
import com.agentx.data.repository.ModelRepository
import com.agentx.data.repository.ModelRepositoryImpl
import com.agentx.data.repository.ToolRepository
import com.agentx.data.repository.ToolRepositoryImpl
import com.agentx.domain.models.AgentRole

class AgentXApplication : Application() {

    // ─── Infrastructure ───────────────────────────────────────────────────────

    lateinit var database: AgentXDatabase
        private set

    lateinit var modelRepository: ModelRepository
        private set

    lateinit var modelFileManager: ModelFileManager
        private set

    lateinit var huggingFaceDownloader: HuggingFaceDownloader
        private set

    lateinit var inferenceEngine: InferenceEngine
        private set

    // ─── Settings Preferences ─────────────────────────────────────────────
    lateinit var preferencesManager: com.agentx.data.local.SettingsPreferencesManager
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

    lateinit var executionQueue: com.agentx.core.agent.execution.ExecutionQueue
        private set

    lateinit var clinicalSupervisor: ClinicalSupervisor
        private set

    override fun onCreate() {
        super.onCreate()

        // Infrastructure
        database = AgentXDatabase.getInstance(this)
        modelFileManager = ModelFileManager(this)
        huggingFaceDownloader = HuggingFaceDownloader(this, modelFileManager)
        preferencesManager = com.agentx.data.local.SettingsPreferencesManager(this)

        val savedCloudConfig = preferencesManager.loadCloudConfig()
        val savedMode = preferencesManager.loadInferenceMode()
        val savedBackend = preferencesManager.loadLocalBackend()

        val localLlama = LlamaCppEngine()
        val airLlm = com.agentx.core.inference.AirLlmEngine()
        val cloudEngine = CloudInferenceEngine(config = savedCloudConfig)
        hybridInferenceEngine = HybridInferenceEngine(
            llamaEngine = localLlama,
            airLlmEngine = airLlm,
            cloudEngine = cloudEngine,
            activeLocalBackend = savedBackend
        ).apply {
            inferenceMode = savedMode
        }
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
        executionQueue = com.agentx.core.agent.execution.ExecutionQueue()

        clinicalSupervisor = ClinicalSupervisor(
            agents = agentExecutors,
            inferenceEngine = inferenceEngine,
            executionRepository = executionRepository,
            executionQueue = executionQueue
        )
    }
}
