package com.agentx.medical

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
import com.agentx.medical.domain.models.GGUFModel
import com.agentx.medical.core.inference.ModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri
import android.os.Environment

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

    lateinit var preferencesManager: com.agentx.medical.data.local.SettingsPreferencesManager
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
        preferencesManager = com.agentx.medical.data.local.SettingsPreferencesManager(this)

        val savedCloudConfig = preferencesManager.loadCloudConfig()
        val savedMode = preferencesManager.loadInferenceMode()
        val savedBackend = preferencesManager.loadLocalBackend()
        val localLlama = LlamaCppEngine()
        val cloudEngine = CloudInferenceEngine(config = savedCloudConfig)
        hybridInferenceEngine = HybridInferenceEngine(
            llamaEngine = localLlama,
            cloudEngine = cloudEngine,
            activeLocalBackend = savedBackend
        )
        hybridInferenceEngine.inferenceMode = savedMode
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

        // Automatically scan and register on-device storage models on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val current: List<GGUFModel> = modelRepository.getAllModels().first()
                if (current.isEmpty()) {
                    val candidateDirs = listOfNotNull(
                        File(filesDir, "models"),
                        File(getExternalFilesDir(null), "models"),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    )
                    for (dir in candidateDirs) {
                        if (!dir.exists() || !dir.isDirectory) continue
                        val ggufFiles = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) } ?: emptyList()
                        for (file in ggufFiles) {
                            val newModel = GGUFModel(
                                id = "storage_" + file.nameWithoutExtension.replace("[^a-zA-Z0-9._-]".toRegex(), "_"),
                                name = file.nameWithoutExtension,
                                filePath = file.absolutePath,
                                uriString = Uri.fromFile(file).toString(),
                                sizeBytes = file.length(),
                                architecture = "Llama",
                                contextLength = 2048,
                                tensorCount = 0,
                                format = "GGUF v3",
                                quantization = "Q5_K_M",
                                capabilityProfile = "clinical-consultant",
                                isStaged = true,
                                isActive = true
                            )
                            modelRepository.addModel(newModel)
                            inferenceEngine.loadModel(
                                ModelConfig(
                                    modelId = newModel.id,
                                    modelPath = newModel.filePath,
                                    contextLength = newModel.contextLength,
                                    threads = newModel.threads,
                                    temperature = newModel.temperature,
                                    topP = newModel.topP
                                )
                            )
                            break
                        }
                        if (modelRepository.getAllModels().first().isNotEmpty()) break
                    }
                } else {
                    val active = current.firstOrNull { it.isActive } ?: current.first()
                    inferenceEngine.loadModel(
                        ModelConfig(
                            modelId = active.id,
                            modelPath = active.filePath,
                            contextLength = active.contextLength,
                            threads = active.threads,
                            temperature = active.temperature,
                            topP = active.topP
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
