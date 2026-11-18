package com.agentx.core.ota

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates automated, granular component-level OTA updates.
 * Manages manifest discovery, version comparisons, background streaming downloads,
 * cryptographic verification, atomic file placement, and live hot-reloading callbacks.
 */
class OtaUpdateManager(
    val registry: OtaComponentRegistry,
    val downloader: OtaDownloader = OtaDownloader(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val currentAppVersionCode: Int = 100
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<OtaUpdateState>(OtaUpdateState.Idle)
    val state: StateFlow<OtaUpdateState> = _state.asStateFlow()

    private val hotReloadListeners = CopyOnWriteArrayList<Pair<ComponentType, (InstalledComponent) -> Unit>>()
    private var periodicJob: Job? = null

    var isAutoUpdateEnabled: Boolean = true
    var isWifiOnlyForLargeFiles: Boolean = true
    var defaultManifestUrl: String = ""

    val installedComponents: StateFlow<List<InstalledComponent>> = registry.installedComponents

    fun addHotReloadListener(type: ComponentType, listener: (InstalledComponent) -> Unit) {
        hotReloadListeners.add(type to listener)
    }

    fun removeHotReloadListener(listener: (InstalledComponent) -> Unit) {
        hotReloadListeners.removeAll { it.second == listener }
    }

    private fun notifyHotReload(component: InstalledComponent) {
        for ((type, listener) in hotReloadListeners) {
            if (type == component.type) {
                try {
                    listener(component)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Fetches the top-level remote manifest from [manifestUrl].
     */
    suspend fun fetchRemoteManifest(manifestUrl: String): Result<OtaManifest> = withContext(Dispatchers.IO) {
        try {
            val url = URL(manifestUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AgentX-OTA-Client/1.0")
            }
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP ${conn.responseCode} fetching manifest"))
            }
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString<OtaManifest>(content)
            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks for available component updates comparing version codes.
     */
    suspend fun checkForUpdates(manifestUrl: String = defaultManifestUrl): List<ComponentManifest> = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) return@withContext emptyList()
        _state.value = OtaUpdateState.Checking
        val result = fetchRemoteManifest(manifestUrl)
        val manifest = result.getOrNull()
        if (manifest == null) {
            _state.value = OtaUpdateState.Error(null, result.exceptionOrNull()?.message ?: "Failed to fetch manifest")
            return@withContext emptyList()
        }

        val availableUpdates = mutableListOf<ComponentManifest>()
        for (component in manifest.components) {
            // Must be compatible with the current host APK version
            if (currentAppVersionCode < component.minAppVersionCode) continue

            val currentInstalledVersionCode = registry.getActiveVersionCode(component.id)
            if (component.versionCode > currentInstalledVersionCode) {
                availableUpdates.add(component)
            }
        }

        if (availableUpdates.isNotEmpty()) {
            _state.value = OtaUpdateState.UpdateAvailable(availableUpdates)
        } else {
            _state.value = OtaUpdateState.Idle
        }

        availableUpdates
    }

    /**
     * Downloads, cryptographically verifies, and hot-activates a single component.
     */
    suspend fun applyUpdate(manifest: ComponentManifest): Result<InstalledComponent> = withContext(Dispatchers.IO) {
        _state.value = OtaUpdateState.Downloading(
            componentId = manifest.id,
            progressPct = 0f,
            bytesDownloaded = 0L,
            totalBytes = manifest.sizeBytes,
            speedBps = 0L
        )

        val tempDir = File(registry.baseDir, "temp")
        tempDir.mkdirs()
        val stageFileName = if (manifest.targetFileName.isNotBlank()) manifest.targetFileName else "${manifest.id}-${manifest.versionCode}.bin"
        val stageFile = File(tempDir, stageFileName)

        // 1. Download payload
        val dlResult = downloader.downloadToFile(
            urlStr = manifest.downloadUrl,
            targetFile = stageFile,
            expectedSizeBytes = manifest.sizeBytes,
            onProgress = { p ->
                _state.value = OtaUpdateState.Downloading(
                    componentId = manifest.id,
                    progressPct = p.progressPct,
                    bytesDownloaded = p.bytesDownloaded,
                    totalBytes = p.totalBytes,
                    speedBps = p.speedBps
                )
            }
        )

        if (dlResult.isFailure) {
            val err = dlResult.exceptionOrNull()?.message ?: "Download failed"
            _state.value = OtaUpdateState.Error(manifest.id, err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // 2. Cryptographic SHA-256 verification
        _state.value = OtaUpdateState.Verifying(manifest.id)
        if (manifest.sha256.isNotBlank()) {
            val matches = OtaVerifier.verifySha256(stageFile, manifest.sha256)
            if (!matches) {
                stageFile.delete()
                val err = "Cryptographic integrity failure: expected SHA-256 '${manifest.sha256}' does not match download"
                _state.value = OtaUpdateState.Error(manifest.id, err)
                return@withContext Result.failure(SecurityException(err))
            }
        }

        // 3. Atomic registration and directory placement
        val installed = try {
            registry.registerInstallation(manifest, stageFile)
        } catch (e: Exception) {
            stageFile.delete()
            _state.value = OtaUpdateState.Error(manifest.id, "Installation error: ${e.message}")
            return@withContext Result.failure(e)
        }

        _state.value = OtaUpdateState.Applied(installed)

        // 4. Trigger live hot-reload
        notifyHotReload(installed)

        Result.success(installed)
    }

    /**
     * Downloads and applies all available updates sequentially.
     */
    suspend fun applyAllUpdates(manifests: List<ComponentManifest>): List<Result<InstalledComponent>> {
        val results = mutableListOf<Result<InstalledComponent>>()
        for (m in manifests) {
            results.add(applyUpdate(m))
        }
        return results
    }

    /**
     * Starts an automated periodic background check for component updates.
     */
    fun startPeriodicCheck(
        manifestUrl: String = defaultManifestUrl,
        intervalMs: Long = 6 * 60 * 60 * 1000L // 6 hours
    ) {
        periodicJob?.cancel()
        if (manifestUrl.isNotBlank()) {
            defaultManifestUrl = manifestUrl
        }
        periodicJob = scope.launch {
            while (isActive) {
                if (isAutoUpdateEnabled && defaultManifestUrl.isNotBlank()) {
                    try {
                        val updates = checkForUpdates(defaultManifestUrl)
                        if (updates.isNotEmpty()) {
                            // Filter out giant model files (> 50MB) if on mobile data
                            for (up in updates) {
                                val isLargeFile = up.sizeBytes > 50 * 1024 * 1024L
                                if (!isLargeFile || !isWifiOnlyForLargeFiles) {
                                    applyUpdate(up)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPeriodicCheck() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun rollbackComponent(id: String): Boolean {
        val ok = registry.rollback(id)
        if (ok) {
            _state.value = OtaUpdateState.Idle
        }
        return ok
    }
}
