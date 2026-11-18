package com.agentx.core.ota

import kotlinx.serialization.Serializable

/**
 * Metadata record for a locally installed, verified OTA component.
 */
@Serializable
data class InstalledComponent(
    val id: String,
    val name: String,
    val type: ComponentType,
    val version: String,
    val versionCode: Int,
    val filePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val installedAtEpochMs: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)
