package com.agentx.core

import com.agentx.core.protocol.asp.AspComputeBackend
import com.agentx.core.protocol.asp.AspDeviceProfile
import com.agentx.core.protocol.asp.AspModelDescriptor
import com.agentx.core.protocol.asp.AspNodeSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AspProtocolAndRouterTest {

    private val selector = AspNodeSelector()

    @Test
    fun testEmptyNodesReturnsNoSuitableNode() {
        val res = selector.selectOptimalNode(emptyList(), "medimind-v2-q5_k_m")
        assertTrue(res is AspNodeSelector.NodeSelectionResult.NoSuitableNode)
    }

    @Test
    fun testDisqualifyLowBatteryWithoutCharging() {
        val lowBatteryNode = AspDeviceProfile(
            nodeId = "phone_old",
            nodeName = "Old Redmi Phone",
            osVersion = "Android 10",
            totalRamMb = 4096,
            availableRamMb = 2048,
            batteryPercent = 12,
            isCharging = false, // Critical low battery
            activeEndpoints = listOf("http://192.168.1.50:8080")
        )

        val res = selector.selectOptimalNode(listOf(lowBatteryNode), null, 1024)
        assertTrue(res is AspNodeSelector.NodeSelectionResult.NoSuitableNode)
    }

    @Test
    fun testDisqualifyInsufficientRam() {
        val lowRamNode = AspDeviceProfile(
            nodeId = "phone_low_ram",
            nodeName = "Low RAM Phone",
            osVersion = "Android 12",
            totalRamMb = 2048,
            availableRamMb = 512, // Only 512 MB available
            batteryPercent = 90,
            isCharging = true,
            activeEndpoints = listOf("http://192.168.1.51:8080")
        )

        // Requires 1024 MB
        val res = selector.selectOptimalNode(listOf(lowRamNode), null, 1024)
        assertTrue(res is AspNodeSelector.NodeSelectionResult.NoSuitableNode)
    }

    @Test
    fun testSelectsBestAcceleratedAndLoadedNode() {
        val cpuNode = AspDeviceProfile(
            nodeId = "node_cpu",
            nodeName = "Host A (Generic CPU)",
            osVersion = "Android 13",
            totalRamMb = 6144,
            availableRamMb = 2500,
            computeBackend = AspComputeBackend.CPU_NEON,
            batteryPercent = 60,
            isCharging = false,
            isThermalThrottling = false,
            activeEndpoints = listOf("http://192.168.1.10:8080"),
            availableModels = listOf(
                AspModelDescriptor(
                    modelId = "medimind-v2-q5_k_m",
                    displayName = "MediMind 0.5B Q5_K_M",
                    parameterSize = "0.5B",
                    quantization = "Q5_K_M",
                    isLoaded = false
                )
            )
        )

        val acceleratedNode = AspDeviceProfile(
            nodeId = "node_vulkan",
            nodeName = "Host B (Vulkan Acceleration)",
            osVersion = "Android 14",
            totalRamMb = 8192,
            availableRamMb = 4000,
            computeBackend = AspComputeBackend.VULKAN,
            batteryPercent = 100,
            isCharging = true,
            isThermalThrottling = false,
            activeEndpoints = listOf("http://192.168.1.20:8080"),
            availableModels = listOf(
                AspModelDescriptor(
                    modelId = "medimind-v2-q5_k_m",
                    displayName = "MediMind 0.5B Q5_K_M",
                    parameterSize = "0.5B",
                    quantization = "Q5_K_M",
                    isLoaded = true, // Already loaded!
                    tokensPerSecond = 45.0f
                )
            )
        )

        val res = selector.selectOptimalNode(listOf(cpuNode, acceleratedNode), "medimind-v2-q5_k_m", 1024)
        assertTrue(res is AspNodeSelector.NodeSelectionResult.Selected)
        val selected = res as AspNodeSelector.NodeSelectionResult.Selected
        assertEquals("node_vulkan", selected.profile.nodeId)
        assertEquals("http://192.168.1.20:8080", selected.targetEndpoint)
    }

    @Test
    fun testThermalThrottledNodePenalized() {
        val hotThrottledNode = AspDeviceProfile(
            nodeId = "node_hot",
            nodeName = "Hot Throttled Phone",
            osVersion = "Android 13",
            totalRamMb = 6144,
            availableRamMb = 3000,
            computeBackend = AspComputeBackend.CPU_NEON,
            batteryPercent = 95,
            isCharging = true,
            isThermalThrottling = true, // High temperature throttle!
            activeEndpoints = listOf("http://192.168.1.30:8080"),
            availableModels = listOf(
                AspModelDescriptor(
                    modelId = "general-1b",
                    displayName = "General 1B",
                    parameterSize = "1B",
                    quantization = "Q4_0",
                    isLoaded = true
                )
            )
        )

        val coolNode = AspDeviceProfile(
            nodeId = "node_cool",
            nodeName = "Cool Normal Phone",
            osVersion = "Android 13",
            totalRamMb = 6144,
            availableRamMb = 3000,
            computeBackend = AspComputeBackend.CPU_NEON,
            batteryPercent = 85,
            isCharging = true,
            isThermalThrottling = false, // Cool and stable
            activeEndpoints = listOf("http://192.168.1.31:8080"),
            availableModels = listOf(
                AspModelDescriptor(
                    modelId = "general-1b",
                    displayName = "General 1B",
                    parameterSize = "1B",
                    quantization = "Q4_0",
                    isLoaded = true
                )
            )
        )

        val res = selector.selectOptimalNode(listOf(hotThrottledNode, coolNode), "general-1b", 1024)
        assertTrue(res is AspNodeSelector.NodeSelectionResult.Selected)
        val selected = res as AspNodeSelector.NodeSelectionResult.Selected
        assertEquals("node_cool", selected.profile.nodeId)
    }
}
