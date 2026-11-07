package com.agentx.medical.feature.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.medical.core.device.DeviceHardwareInspector
import com.agentx.medical.core.device.DeviceSpecs
import com.agentx.medical.core.device.RecommendedModel
import com.agentx.medical.core.inference.CuratedMedicalModel
import com.agentx.medical.core.inference.DownloadProgress
import com.agentx.medical.core.inference.GgufMetadataParser
import com.agentx.medical.core.inference.HuggingFaceDownloader
import com.agentx.medical.core.inference.InferenceEngine
import com.agentx.medical.core.inference.ModelConfig
import com.agentx.medical.data.local.ModelFileManager
import com.agentx.medical.data.local.StagingProgress
import com.agentx.medical.data.repository.ModelRepository
import com.agentx.medical.domain.models.GGUFModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File
import kotlinx.coroutines.flow.first

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
    val curatedModels: List<CuratedMedicalModel> = HuggingFaceDownloader.CURATED_MODELS,
    val deviceSpecs: DeviceSpecs? = null,
    val recommendedModels: List<RecommendedModel> = emptyList()
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

    private var downloadJob: Job? = null

    private val progressFlow = combine(_stagingState, _downloadState) { staging, download ->
        Pair(staging, download)
    }

    private val specsFlow = combine(_deviceSpecs, _recommendedModels) { specs, recs ->
        Pair(specs, recs)
    }

    val uiState: StateFlow<ModelsUiState> = combine(
        modelRepository.getAllModels(),
        progressFlow,
        combine(_storageUsed, _storageAvailable, _notification) { used, avail, notif ->
            Triple(used, avail, notif)
        },
        specsFlow
    ) { models, (staging, download), (used, available, notification), (specs, recs) ->
        ModelsUiState(
            models = models,
            activeModel = models.firstOrNull { it.isActive } ?: models.firstOrNull(),
            stagingState = staging,
            downloadState = download,
            storageUsedBytes = used,
            storageAvailableBytes = available,
            notificationMessage = notification,
            deviceSpecs = specs,
            recommendedModels = recs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModelsUiState()
    )

    init {
        refreshStorageMetrics()
        refreshDeviceSpecs()
        scanAndRegisterLocalModels()
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

    fun scanAndRegisterLocalModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val candidateDirs = listOfNotNull(
                File(context.filesDir, "models"),
                File(context.getExternalFilesDir(null), "models"),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            )
            val currentModels = modelRepository.getAllModels().first()
            for (dir in candidateDirs) {
                if (!dir.exists() || !dir.isDirectory) continue
                dir.listFiles()?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }?.forEach { file ->
                    val existing = currentModels.find { it.filePath == file.absolutePath || it.name == file.nameWithoutExtension }
                    if (existing == null) {
                        val metadata = try {
                            file.inputStream().use { GgufMetadataParser.parse(it) }
                        } catch (_: Exception) { null }

                        val isFirst = currentModels.isEmpty()
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
                            capabilityProfile = "general-assistant",
                            isStaged = true,
                            isActive = isFirst
                        )
                        modelRepository.addModel(newModel)
                        if (isFirst && inferenceEngine != null) {
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
                            } catch (_: Exception) {}
                        }
                    } else if (existing.isActive && inferenceEngine != null && inferenceEngine.getModelStatus().state != com.agentx.medical.core.inference.ModelLifecycleState.LOADED) {
                        try {
                            inferenceEngine.loadModel(
                                ModelConfig(
                                    modelId = existing.id,
                                    modelPath = existing.filePath,
                                    contextLength = existing.contextLength,
                                    threads = existing.threads,
                                    temperature = existing.temperature,
                                    topP = existing.topP
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            refreshStorageMetrics()
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
                            capabilityProfile = "medical-clinical",
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
                            } catch (_: Exception) {}
                        }
                        _stagingState.value = StagingUiState()
                        _notification.value = "Successfully imported & loaded ${newModel.name} (${newModel.quantization})"
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
                            capabilityProfile = "medical-clinical",
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
                            contextLength = model.contextLength,
                            threads = model.threads,
                            temperature = model.temperature,
                            topP = model.topP
                        )
                    )
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
            _notification.value = "Model configuration saved"
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = modelRepository.getModelById(modelId)
            if (model != null) {
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
