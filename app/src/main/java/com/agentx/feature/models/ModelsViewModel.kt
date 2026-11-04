package com.agentx.feature.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.core.device.DeviceHardwareInspector
import com.agentx.core.device.DeviceSpecs
import com.agentx.core.device.RecommendedModel
import com.agentx.core.inference.CuratedMedicalModel
import com.agentx.core.inference.DownloadProgress
import com.agentx.core.inference.GgufMetadataParser
import com.agentx.core.inference.HuggingFaceDownloader
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val curatedModels: List<CuratedMedicalModel> = HuggingFaceDownloader.CURATED_MODELS,
    val deviceSpecs: DeviceSpecs? = null,
    val recommendedModels: List<RecommendedModel> = emptyList()
)

class ModelsViewModel(
    private val modelRepository: ModelRepository,
    private val modelFileManager: ModelFileManager,
    private val huggingFaceDownloader: HuggingFaceDownloader,
    private val context: Context
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
                        _stagingState.value = StagingUiState()
                        _notification.value = "Successfully imported ${newModel.name} (${newModel.quantization})"
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
            _notification.value = "Active inference model updated"
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
