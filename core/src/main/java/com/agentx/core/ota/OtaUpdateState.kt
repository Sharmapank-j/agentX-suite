package com.agentx.core.ota

/**
 * Reactive state model for the component-level OTA lifecycle.
 */
sealed interface OtaUpdateState {
    object Idle : OtaUpdateState

    object Checking : OtaUpdateState

    data class UpdateAvailable(
        val availableComponents: List<ComponentManifest>
    ) : OtaUpdateState

    data class Downloading(
        val componentId: String,
        val progressPct: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBps: Long
    ) : OtaUpdateState

    data class Verifying(
        val componentId: String
    ) : OtaUpdateState

    data class Applied(
        val component: InstalledComponent
    ) : OtaUpdateState

    data class Error(
        val componentId: String?,
        val message: String
    ) : OtaUpdateState
}

data class OtaDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPct: Float,
    val speedBps: Long
)
