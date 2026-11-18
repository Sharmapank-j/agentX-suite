package com.agentx.core.ota

import kotlinx.serialization.Serializable

/**
 * Manifest description for an independently updatable component.
 */
@Serializable
data class ComponentManifest(
    val id: String,
    val name: String,
    val type: ComponentType,
    val version: String,
    val versionCode: Int,
    val minAppVersionCode: Int = 1,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val isMandatory: Boolean = false,
    val releaseNotes: String = "",
    val targetFileName: String = "",
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Top-level manifest served by the update distribution hub or AgentX Serve.
 */
@Serializable
data class OtaManifest(
    val manifestVersion: Int = 1,
    val publishedAtEpochMs: Long = System.currentTimeMillis(),
    val serverVersion: String = "1.0.0",
    val channel: String = "stable",
    val components: List<ComponentManifest> = emptyList()
)
