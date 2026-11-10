package com.agentx.feature.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.core.device.DeviceHardwareInspector
import com.agentx.core.device.DeviceSpecs
import com.agentx.core.device.RecommendedModel
import com.agentx.core.device.CpuTopology
import com.agentx.core.device.CpuTopologyInspector
import com.agentx.core.inference.CuratedModel
import com.agentx.core.inference.DownloadProgress
import com.agentx.core.device.HardwareProfiler
import com.agentx.core.device.BenchmarkResult
import com.agentx.core.device.MemorySnapshot
import com.agentx.core.device.ThermalSnapshot
import com.agentx.core.inference.InferenceRequest
import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.GgufMetadataParser
import com.agentx.core.inference.HuggingFaceDownloader
import com.agentx.core.inference.InferenceEngine
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import com.agentx.data.local.ModelFileManager
import com.agentx.data.local.StagingProgress
import com.agentx.data.repository.ModelRepository
import com.agentx.domain.models.GGUFModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class StagingUiState(
    val isStaging: Boolean = false,
    val modelName: String = "",
    val progressFraction: Float = 0f,
    val bytesCopied: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

data class DownloadUiState(
    val isDownloading: Boolean = false,
    val modelName: String = "",
    val fraction: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null
)

data class ModelsUiState(
    val models: List<GGUFModel> = emptyList(),
    val activeModel: GGUFModel? = null,
    val stagingState: StagingUiState = StagingUiState(),
    val downloadState: DownloadUiState = DownloadUiState(),
    val storageUsedBytes: Long = 0L,
    val storageAvailableBytes: Long = 0L,
    val notificationMessage: String? = null,
    val curatedModels: List<CuratedModel> = HuggingFaceDownloader.CURATED_MODELS,
    val deviceSpecs: DeviceSpecs? = null,
    val recommendedModels: List<RecommendedModel> = emptyList(),
    val isLoadedInRam: Boolean = false,
    val loadedMemoryMb: Long = 0L,
    val isScanningStorage: Boolean = false,
    val activeFilter: String = "ALL", // "ALL", "INSTALLED", "FAVORITES"
    val favoriteModelIds: Set<String> = emptySet(),
    val cpuTopology: CpuTopology = CpuTopologyInspector.inspect(),
    val isBenchmarking: Boolean = false,
    val latestBenchmark: BenchmarkResult? = null,
    val memorySnapshot: MemorySnapshot = HardwareProfiler.getMemorySnapshot(),
    val thermalSnapshot: ThermalSnapshot = HardwareProfiler.getThermalSnapshot(null)
)

class ModelsViewModel(
    private val modelRepository: ModelRepository,
    private val modelFileManager: ModelFileManager,
    private val huggingFaceDownloader: HuggingFaceDownloader,
    private val context: Context,
    private val inferenceEngine: InferenceEngine? = null
) : ViewModel() {

    private val _stagingState = MutableStateFlow(StagingUiState())
    private val _downloadState = MutableStateFlow(DownloadUiState())
    private val _storageUsed = MutableStateFlow(0L)
    private val _storageAvailable = MutableStateFlow(0L)
    private val _notification = MutableStateFlow<String?>(null)
    private val _deviceSpecs = MutableStateFlow<DeviceSpecs?>(null)
    private val _recommendedModels = MutableStateFlow<List<RecommendedModel>>(emptyList())
    private val _isLoadedInRam = MutableStateFlow(false)
    private val _loadedMemoryMb = MutableStateFlow(0L)
    private val _isScanningStorage = MutableStateFlow(false)
    private val _activeFilter = MutableStateFlow("ALL")
    private val _favoriteModelIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isBenchmarking = MutableStateFlow(false)
    private val _latestBenchmark = MutableStateFlow<BenchmarkResult?>(null)

    private var downloadJob: Job? = null

    private data class RamUiStatus(
        val isLoaded: Boolean,
        val memoryMb: Long,
        val isScanning: Boolean,
        val filter: String,
        val favorites: Set<String>,
        val isBenchmarking: Boolean,
        val latestBenchmark: BenchmarkResult?
    )

    private val progressFlow = combine(_stagingState, _downloadState) { staging, download ->
        Pair(staging, download)
    }

    private val specsFlow = combine(_deviceSpecs, _recommendedModels) { specs, recs ->
        Pair(specs, recs)
    }

    private val benchFlow = combine(_isBenchmarking, _latestBenchmark) { benchmarking, benchResult ->
        Pair(benchmarking, benchResult)
    }

    private val filterFavFlow = combine(_activeFilter, _favoriteModelIds) { filter, favs ->
        Pair(filter, favs)
    }

    private val ramStatusFlow = combine(
        _isLoadedInRam,
        _loadedMemoryMb,
        _isScanningStorage,
        filterFavFlow,
        benchFlow
    ) { loaded, mem, scanning, (filter, favs), (benchmarking, benchResult) ->
        RamUiStatus(loaded, mem, scanning, filter, favs, benchmarking, benchResult)
    }

    val uiState: StateFlow<ModelsUiState> = combine(
        modelRepository.getAllModels(),
        progressFlow,
        combine(_storageUsed, _storageAvailable, _notification) { used, avail, notif ->
            Triple(used, avail, notif)
        },
        specsFlow,
        ramStatusFlow
    ) { models, (staging, download), (used, available, notification), (specs, recs), ramStatus ->
        val active = models.firstOrNull { it.isActive } ?: models.firstOrNull()
        ModelsUiState(
            models = models,
            activeModel = active,
            stagingState = staging,
            downloadState = download,
            storageUsedBytes = used,
            storageAvailableBytes = available,
            notificationMessage = notification,
            curatedModels = HuggingFaceDownloader.CURATED_MODELS,
            deviceSpecs = specs,
            recommendedModels = recs,
            isLoadedInRam = ramStatus.isLoaded,
            loadedMemoryMb = ramStatus.memoryMb,
            isScanningStorage = ramStatus.isScanning,
            activeFilter = ramStatus.filter,
            favoriteModelIds = ramStatus.favorites,
            cpuTopology = CpuTopologyInspector.inspect(),
            isBenchmarking = ramStatus.isBenchmarking,
            latestBenchmark = ramStatus.latestBenchmark,
            memorySnapshot = HardwareProfiler.getMemorySnapshot(),
            thermalSnapshot = HardwareProfiler.getThermalSnapshot(context)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModelsUiState()
    )

    init {
        refreshStorageMetrics()
        refreshDeviceSpecs()
        syncRuntimeStatus()
        scanAndRegisterLocalModels()
    }

    fun syncRuntimeStatus() {
        if (inferenceEngine != null) {
            val status = inferenceEngine.getModelStatus()
            _isLoadedInRam.value = (status.state == ModelLifecycleState.LOADED || status.state == ModelLifecycleState.GENERATING)
            _loadedMemoryMb.value = status.memoryUsageBytes / (1024 * 1024)
        }
    }

    fun refreshDeviceSpecs() {
        try {
            val specs = DeviceHardwareInspector.inspectDevice(context)
            _deviceSpecs.value = specs
            _recommendedModels.value = DeviceHardwareInspector.getRecommendedModels(specs)
        } catch (_: Exception) {}
    }

    fun downloadRecommendedModel(rec: RecommendedModel) {
        startHuggingFaceDownload(
            url = rec.huggingFaceUrl,
            filename = "${rec.id}-${rec.quantization.lowercase()}.gguf",
            modelNameHint = "${rec.name} (${rec.quantization})"
        )
    }

    fun refreshStorageMetrics() {
        _storageUsed.value = modelFileManager.getTotalStorageUsedBytes()
        _storageAvailable.value = modelFileManager.getAvailableStorageBytes()
    }

    fun scanStorageNow() {
        viewModelScope.launch {
            _isScanningStorage.value = true
            _notification.value = "Scanning phone storage for .gguf models..."
            scanAndRegisterLocalModels()
            _isScanningStorage.value = false
            _notification.value = "Phone storage scan complete"
        }
    }

    fun setFilter(filter: String) {
        _activeFilter.value = filter
    }

    fun toggleFavorite(modelId: String) {
        val current = _favoriteModelIds.value.toMutableSet()
        if (current.contains(modelId)) {
            current.remove(modelId)
            _notification.value = "Removed from Favorites"
        } else {
            current.add(modelId)
            _notification.value = "Added to Favorites"
        }
        _favoriteModelIds.value = current
    }

    fun loadModelIntoRam(modelId: String) {
        viewModelScope.launch {
            val model = modelRepository.getModelById(modelId) ?: return@launch
            val file = File(model.filePath)
            if (!file.exists() || !file.isFile) {
                _notification.value = "Cannot load: Model file does not exist at ${model.filePath}"
                return@launch
            }
            modelRepository.setActiveModel(modelId)
            if (inferenceEngine != null) {
                try {
                    _notification.value = "Loading ${model.name} into RAM..."
                    inferenceEngine.loadModel(
                        ModelConfig(
                            modelId = model.id,
                            modelPath = model.filePath,
                            contextLength = model.contextLength.coerceIn(512, 3840),
                            threads = model.threads.coerceIn(1, 4),
                            temperature = model.temperature,
                            topP = model.topP,
                            mmprojPath = model.mmprojPath
                        )
                    )
                    val status = inferenceEngine.getModelStatus()
                    val memMb = status.memoryUsageBytes / (1024 * 1024)
                    _isLoadedInRam.value = (status.state == ModelLifecycleState.LOADED)
                    _loadedMemoryMb.value = memMb
                    val visionTag = if (status.supportsVision) " • 👁️ Vision Active" else ""
                    _notification.value = "Loaded ${model.name} into RAM (${memMb} MB in use, ${model.threads} CPU threads$visionTag)"
                } catch (e: Exception) {
                    _isLoadedInRam.value = false
                    _loadedMemoryMb.value = 0L
                    _notification.value = "Model loading failed: ${e.message}"
                }
            } else {
                _notification.value = "Active model updated (Engine unattached)"
            }
        }
    }

    fun unloadModelFromRam() {
        viewModelScope.launch {
            try {
                inferenceEngine?.unloadModel()
                _isLoadedInRam.value = false
                _loadedMemoryMb.value = 0L
                _notification.value = "Model unloaded from RAM. Memory freed."
            } catch (e: Exception) {
                _notification.value = "Failed to unload model: ${e.message}"
            }
        }
    }

    fun runHardwareBenchmark(modelId: String) {
        viewModelScope.launch {
            val engine = inferenceEngine ?: run {
                _notification.value = "Inference engine is not initialized."
                return@launch
            }
            val model = modelRepository.getModelById(modelId) ?: run {
                _notification.value = "Model not found for benchmark."
                return@launch
            }
            val file = File(model.filePath)
            if (!file.exists() || !file.isFile) {
                _notification.value = "Model file does not exist at ${model.filePath}"
                return@launch
            }

            _isBenchmarking.value = true
            _notification.value = "Benchmarking ${model.name} on ${android.os.Build.MODEL}..."

            try {
                if (engine.getModelStatus().state != ModelLifecycleState.LOADED || engine.getModelStatus().activeModelId != model.id) {
                    engine.loadModel(
                        ModelConfig(
                            modelId = model.id,
                            modelPath = model.filePath,
                            contextLength = 1024,
                            threads = model.threads.coerceIn(1, 4),
                            temperature = 0.0f,
                            mmprojPath = model.mmprojPath
                        )
                    )
                }

                val benchmarkPrompt = "Explain the fundamental principles of artificial intelligence and deep neural networks in concise bullet points."
                val started = System.currentTimeMillis()
                var firstTokenTime = 0L
                var tokenCount = 0

                engine.generate(
                    InferenceRequest(
                        prompt = benchmarkPrompt,
                        maxTokens = 64,
                        temperature = 0.0f
                    )
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis()
                            tokenCount++
                        }
                        is GenerationEvent.Finished -> {
                            val duration = System.currentTimeMillis() - started
                            val ttft = if (firstTokenTime > 0L) firstTokenTime - started else duration
                            val tokPerSec = if (duration > 0 && tokenCount > 0) (tokenCount * 1000.0) / duration else 0.0
                            val bench = BenchmarkResult(
                                modelId = model.id,
                                modelName = model.name,
                                ttftMs = ttft,
                                evalTokensPerSec = tokPerSec,
                                promptTokensPerSec = if (ttft > 0) (benchmarkPrompt.length / 4.0) / (ttft / 1000.0) else 0.0,
                                peakMemoryMb = engine.getModelStatus().memoryUsageBytes / (1024 * 1024),
                                deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
                            )
                            _latestBenchmark.value = bench
                            _notification.value = "Benchmark Complete: %.1f tok/s • TTFT: %d ms • Memory: %d MB".format(
                                bench.evalTokensPerSec, bench.ttftMs, bench.peakMemoryMb
                            )
                        }
                        is GenerationEvent.Error -> {
                            _notification.value = "Benchmark failed: ${event.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                _notification.value = "Benchmark error: ${e.message}"
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun scanAndRegisterLocalModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val candidateDirs = listOfNotNull(
                File(context.filesDir, "models"),
                File(context.getExternalFilesDir(null), "models"),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                File(android.os.Environment.getExternalStorageDirectory(), "Download"),
                File(android.os.Environment.getExternalStorageDirectory(), "Models"),
                File(android.os.Environment.getExternalStorageDirectory(), "Documents")
            )
            val currentModels = modelRepository.getAllModels().first()
            var hasActive = currentModels.any { it.isActive }
            for (dir in candidateDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                val dirFiles = dir.listFiles() ?: continue
                val mmprojFile = dirFiles.firstOrNull { f ->
                    f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.name.contains("mmproj", ignoreCase = true)
                }

                dirFiles.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) && !it.name.contains("mmproj", ignoreCase = true) }.forEach { file ->
                    val existing = currentModels.find { it.filePath == file.absolutePath || it.name == file.nameWithoutExtension }
                    if (existing == null) {
                        val metadata = try {
                            file.inputStream().use { GgufMetadataParser.parse(it) }
                        } catch (_: Exception) { null }

                        val shouldActivate = !hasActive
                        val newModel = GGUFModel(
                            id = "storage_" + file.nameWithoutExtension.replace("[^a-zA-Z0-9._-]".toRegex(), "_"),
                            name = metadata?.name?.ifEmpty { file.nameWithoutExtension } ?: file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            uriString = Uri.fromFile(file).toString(),
                            sizeBytes = file.length(),
                            architecture = metadata?.architecture ?: "Llama",
                            contextLength = metadata?.contextLength ?: 2048,
                            tensorCount = metadata?.tensorCount ?: 0,
                            format = "GGUF v${metadata?.version ?: 3}",
                            quantization = metadata?.quantization ?: "Q5_K_M",
                            capabilityProfile = if (mmprojFile != null) "vision-multimodal" else "general-assistant",
                            isStaged = true,
                            isActive = shouldActivate,
                            mmprojPath = mmprojFile?.absolutePath,
                            supportsVision = mmprojFile != null
                        )
                        modelRepository.addModel(newModel)
                        if (shouldActivate) {
                            hasActive = true
                            modelRepository.setActiveModel(newModel.id)
                            if (inferenceEngine != null) {
                                try {
                                    inferenceEngine.loadModel(
                                        ModelConfig(
                                            modelId = newModel.id,
                                            modelPath = newModel.filePath,
                                            contextLength = newModel.contextLength.coerceIn(512, 3840),
                                            threads = newModel.threads.coerceIn(1, 4),
                                            temperature = newModel.temperature,
                                            topP = newModel.topP,
                                            mmprojPath = newModel.mmprojPath
                                        )
                                    )
                                    syncRuntimeStatus()
                                } catch (_: Exception) {}
                            }
                        }
                    } else if (existing.isActive && inferenceEngine != null && inferenceEngine.getModelStatus().state != ModelLifecycleState.LOADED) {
                        try {
                            inferenceEngine.loadModel(
                                ModelConfig(
                                    modelId = existing.id,
                                    modelPath = existing.filePath,
                                    contextLength = existing.contextLength.coerceIn(512, 3840),
                                    threads = existing.threads.coerceIn(1, 4),
                                    temperature = existing.temperature,
                                    topP = existing.topP,
                                    mmprojPath = existing.mmprojPath
                                )
                            )
                            syncRuntimeStatus()
                        } catch (_: Exception) {}
                    }
                }
            }
            val allModelsNow = modelRepository.getAllModels().first()
            if (allModelsNow.none { it.isActive } && allModelsNow.isNotEmpty()) {
                val firstValid = allModelsNow.firstOrNull { File(it.filePath).exists() } ?: allModelsNow.first()
                modelRepository.setActiveModel(firstValid.id)
                if (inferenceEngine != null && File(firstValid.filePath).exists()) {
                    try {
                        inferenceEngine.loadModel(
                            ModelConfig(
                                modelId = firstValid.id,
                                modelPath = firstValid.filePath,
                                contextLength = firstValid.contextLength,
                                threads = firstValid.threads,
                                temperature = firstValid.temperature,
                                topP = firstValid.topP
                            )
                        )
                        syncRuntimeStatus()
                    } catch (_: Exception) {}
                }
            }
            refreshStorageMetrics()
            syncRuntimeStatus()
        }
    }

    fun dismissNotification() {
        _notification.value = null
        _stagingState.value = _stagingState.value.copy(errorMessage = null)
        _downloadState.value = _downloadState.value.copy(errorMessage = null)
    }

    fun onGgufFileSelected(uri: Uri, fileName: String, fileSize: Long) {
        viewModelScope.launch {
            modelFileManager.takePersistablePermission(uri)

            val metadata = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        GgufMetadataParser.parse(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (metadata == null || !metadata.isValid) {
                val err = metadata?.errorMessage ?: "Invalid or unreadable GGUF file format"
                _stagingState.value = StagingUiState(errorMessage = err)
                return@launch
            }

            val modelId = UUID.randomUUID().toString()
            val cleanName = fileName.removeSuffix(".gguf")

            _stagingState.value = StagingUiState(
                isStaging = true,
                modelName = cleanName,
                totalBytes = fileSize
            )

            modelFileManager.stageModelFile(uri, modelId, fileName, fileSize).collect { progress ->
                when (progress) {
                    is StagingProgress.Progress -> {
                        _stagingState.value = _stagingState.value.copy(
                            progressFraction = progress.percentage,
                            bytesCopied = progress.bytesCopied,
                            totalBytes = progress.totalBytes
                        )
                    }
                    is StagingProgress.Success -> {
                        val destinationFile = progress.stagedFile
                        val newModel = GGUFModel(
                            id = modelId,
                            name = metadata.name.ifEmpty { cleanName },
                            filePath = destinationFile.absolutePath,
                            uriString = uri.toString(),
                            sizeBytes = destinationFile.length(),
                            architecture = metadata.architecture,
                            contextLength = metadata.contextLength,
                            tensorCount = metadata.tensorCount,
                            format = "GGUF v${metadata.version}",
                            quantization = metadata.quantization,
                            capabilityProfile = "general-assistant",
                            isStaged = true,
                            isActive = uiState.value.models.isEmpty()
                        )

                        modelRepository.addModel(newModel)
                        if (newModel.isActive && inferenceEngine != null && newModel.filePath.isNotEmpty()) {
                            try {
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
                                syncRuntimeStatus()
                            } catch (_: Exception) {}
                        }
                        _stagingState.value = StagingUiState()
                        _notification.value = "Successfully imported & staged ${newModel.name} (${newModel.quantization})"
                        refreshStorageMetrics()
                    }
                    is StagingProgress.Error -> {
                        _stagingState.value = StagingUiState(
                            isStaging = false,
                            errorMessage = progress.message
                        )
                    }
                }
            }
        }
    }

    fun startHuggingFaceDownload(url: String, filename: String? = null, modelNameHint: String? = null) {
        if (url.isBlank()) return
        val displayName = modelNameHint ?: HuggingFaceDownloader.extractFilenameFromUrl(url).removeSuffix(".gguf")

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _downloadState.value = DownloadUiState(
                isDownloading = true,
                modelName = displayName
            )

            huggingFaceDownloader.downloadModel(url, filename).collect { progress ->
                when (progress) {
                    is DownloadProgress.Downloading -> {
                        _downloadState.value = _downloadState.value.copy(
                            fraction = progress.fraction,
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                            speedBytesPerSec = progress.speedBytesPerSec,
                            etaSeconds = progress.etaSeconds
                        )
                    }
                    is DownloadProgress.Success -> {
                        val file = progress.file
                        val metadata = progress.metadata
                        val newModel = GGUFModel(
                            id = UUID.randomUUID().toString(),
                            name = metadata.name.ifEmpty { displayName },
                            filePath = file.absolutePath,
                            uriString = url,
                            sizeBytes = file.length(),
                            architecture = metadata.architecture,
                            contextLength = metadata.contextLength,
                            tensorCount = metadata.tensorCount,
                            format = "GGUF v${metadata.version}",
                            quantization = metadata.quantization,
                            capabilityProfile = "general-assistant",
                            isStaged = true,
                            isActive = uiState.value.models.isEmpty()
                        )

                        modelRepository.addModel(newModel)
                        _downloadState.value = DownloadUiState()
                        _notification.value = "Downloaded & verified ${newModel.name} from Hugging Face"
                        refreshStorageMetrics()
                    }
                    is DownloadProgress.Error -> {
                        _downloadState.value = DownloadUiState(
                            isDownloading = false,
                            errorMessage = progress.message
                        )
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadUiState()
        _notification.value = "Download cancelled"
    }

    fun setActiveModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.setActiveModel(modelId)
            val model = modelRepository.getModelById(modelId)
            if (model != null && model.filePath.isNotEmpty() && inferenceEngine != null) {
                try {
                    inferenceEngine.loadModel(
                        ModelConfig(
                            modelId = model.id,
                            modelPath = model.filePath,
                            contextLength = model.contextLength.coerceIn(512, 3840),
                            threads = model.threads.coerceIn(1, 4),
                            temperature = model.temperature,
                            topP = model.topP
                        )
                    )
                    syncRuntimeStatus()
                    _notification.value = "Active model loaded into native engine: ${model.name}"
                } catch (e: Exception) {
                    _notification.value = "Model set active. Loading warning: ${e.message}"
                }
            } else {
                _notification.value = "Active inference model updated"
            }
        }
    }

    fun updateModelConfig(
        modelId: String,
        threads: Int,
        contextLength: Int,
        temperature: Float,
        topP: Float,
        capabilityProfile: String
    ) {
        viewModelScope.launch {
            val existing = modelRepository.getModelById(modelId) ?: return@launch
            val updated = existing.copy(
                threads = threads,
                contextLength = contextLength,
                temperature = temperature,
                topP = topP,
                capabilityProfile = capabilityProfile
            )
            modelRepository.updateModel(updated)
            if (updated.isActive && _isLoadedInRam.value && inferenceEngine != null) {
                try {
                    inferenceEngine.loadModel(
                        ModelConfig(
                            modelId = updated.id,
                            modelPath = updated.filePath,
                            contextLength = updated.contextLength,
                            threads = updated.threads,
                            temperature = updated.temperature,
                            topP = updated.topP
                        )
                    )
                    syncRuntimeStatus()
                } catch (_: Exception) {}
            }
            _notification.value = "Model configuration saved"
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = modelRepository.getModelById(modelId)
            if (model != null) {
                if (model.id == uiState.value.activeModel?.id && _isLoadedInRam.value) {
                    unloadModelFromRam()
                }
                if (model.isStaged && model.filePath.isNotEmpty()) {
                    modelFileManager.deleteStagedFile(model.filePath)
                }
                modelRepository.deleteModel(modelId)
                _notification.value = "Model ${model.name} removed"
                refreshStorageMetrics()
            }
        }
    }
}
