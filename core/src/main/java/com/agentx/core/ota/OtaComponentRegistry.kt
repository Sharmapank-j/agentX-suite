package com.agentx.core.ota

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks and persists installed, active OTA components in app-sandboxed storage.
 * Provides atomic installation, version querying, rollback support, and reactive state flows.
 */
class OtaComponentRegistry(
    val baseDir: File
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val indexFile = File(baseDir, "installed_components.json")
    private val registryMap = ConcurrentHashMap<String, InstalledComponent>()

    private val _installedComponents = MutableStateFlow<List<InstalledComponent>>(emptyList())
    val installedComponents: StateFlow<List<InstalledComponent>> = _installedComponents.asStateFlow()

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        loadRegistry()
    }

    @Synchronized
    private fun loadRegistry() {
        if (indexFile.exists() && indexFile.isFile) {
            try {
                val content = indexFile.readText()
                val list = json.decodeFromString<List<InstalledComponent>>(content)
                registryMap.clear()
                list.forEach { comp ->
                    // Verify that the component payload file still exists on disk
                    val file = File(comp.filePath)
                    if (file.exists() && file.isFile) {
                        registryMap[comp.id] = comp
                    }
                }
                _installedComponents.value = registryMap.values.toList().sortedBy { it.id }
            } catch (_: Exception) {
                // If corrupted, initialize empty
                registryMap.clear()
                _installedComponents.value = emptyList()
            }
        } else {
            registryMap.clear()
            _installedComponents.value = emptyList()
        }
    }

    @Synchronized
    private fun persistRegistry() {
        try {
            val list = registryMap.values.toList()
            val content = json.encodeToString(list)
            val tempFile = File(baseDir, "installed_components.tmp")
            tempFile.writeText(content)
            if (tempFile.renameTo(indexFile)) {
                _installedComponents.value = list.sortedBy { it.id }
            } else {
                indexFile.writeText(content)
                tempFile.delete()
                _installedComponents.value = list.sortedBy { it.id }
            }
        } catch (_: Exception) {}
    }

    fun getInstalled(id: String): InstalledComponent? {
        return registryMap[id]
    }

    fun getActiveFile(id: String): File? {
        val comp = registryMap[id] ?: return null
        val file = File(comp.filePath)
        return if (file.exists() && file.isFile) file else null
    }

    fun getActiveVersion(id: String): String? {
        return registryMap[id]?.version
    }

    fun getActiveVersionCode(id: String): Int {
        return registryMap[id]?.versionCode ?: 0
    }

    fun listInstalled(): List<InstalledComponent> {
        return registryMap.values.toList().sortedBy { it.id }
    }

    /**
     * Atomically registers a newly downloaded and verified component payload.
     * Moves [stagedFile] into the component's versioned directory:
     * `baseDir/components/{componentId}/{versionCode}/{fileName}`
     */
    @Synchronized
    fun registerInstallation(manifest: ComponentManifest, stagedFile: File): InstalledComponent {
        require(stagedFile.exists() && stagedFile.isFile) { "Staged file does not exist: ${stagedFile.absolutePath}" }

        val targetDir = File(baseDir, "components/${manifest.id}/${manifest.versionCode}")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val destName = if (manifest.targetFileName.isNotBlank()) {
            manifest.targetFileName
        } else {
            stagedFile.name
        }
        val destFile = File(targetDir, destName)

        // Ensure safe sandboxing
        require(OtaVerifier.isSafePath(baseDir, destFile)) { "Target destination escaped base storage path: ${destFile.absolutePath}" }

        if (destFile.exists()) {
            destFile.delete()
        }

        val moved = stagedFile.renameTo(destFile)
        if (!moved) {
            // Fallback: copy and delete
            stagedFile.copyTo(destFile, overwrite = true)
            stagedFile.delete()
        }

        val sha256 = OtaVerifier.computeSha256(destFile)
        val installed = InstalledComponent(
            id = manifest.id,
            name = manifest.name,
            type = manifest.type,
            version = manifest.version,
            versionCode = manifest.versionCode,
            filePath = destFile.absolutePath,
            sha256 = sha256,
            sizeBytes = destFile.length(),
            installedAtEpochMs = System.currentTimeMillis(),
            isActive = true,
            metadata = manifest.metadata
        )

        registryMap[manifest.id] = installed
        persistRegistry()
        return installed
    }

    /**
     * Deactivates or removes a component, reverting to application built-in defaults.
     */
    @Synchronized
    fun rollback(id: String): Boolean {
        val existing = registryMap.remove(id) ?: return false
        try {
            val file = File(existing.filePath)
            if (file.exists()) {
                file.delete()
            }
            file.parentFile?.deleteRecursively()
        } catch (_: Exception) {}

        persistRegistry()
        return true
    }

    @Synchronized
    fun clearAll() {
        registryMap.clear()
        try {
            File(baseDir, "components").deleteRecursively()
            indexFile.delete()
        } catch (_: Exception) {}
        _installedComponents.value = emptyList()
    }
}
