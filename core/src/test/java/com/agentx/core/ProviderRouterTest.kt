package com.agentx.core

import com.agentx.core.inference.model.ExecutionMode
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.model.ProviderHealth
import com.agentx.core.inference.provider.InferenceProvider
import com.agentx.core.inference.provider.ProviderRegistry
import com.agentx.core.inference.routing.ProviderRouter
import com.agentx.core.inference.routing.RoutingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTestProvider(
    override val id: String,
    override val displayName: String,
    override val executionMode: ExecutionMode,
    private val isHealthy: Boolean = true,
    private val latencyMs: Long = 50L,
    private val capabilities: ProviderCapabilities = ProviderCapabilities(),
    private val shouldFailImmediately: Boolean = false,
    private val shouldFailAfterTokens: Boolean = false
) : InferenceProvider {

    override suspend fun getModels(): List<ModelInfo> = listOf(
        ModelInfo(id = "${id}_model", name = "$displayName Model", providerId = id)
    )

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        if (shouldFailImmediately) {
            throw RuntimeException("Simulated immediate engine failure in $id")
        }

        emit(GenerationEvent.Token("Hello"))
        emit(GenerationEvent.Token(" from"))
        emit(GenerationEvent.Token(" $displayName"))

        if (shouldFailAfterTokens) {
            throw RuntimeException("Mid-stream connection broken in $id")
        }

        emit(
            GenerationEvent.Finished(
                totalTokens = 3,
                promptTokens = 1,
                completionTokens = 3,
                durationMs = 150L,
                providerId = id,
                modelId = "${id}_model"
            )
        )
    }

    override suspend fun cancel(requestId: String) {}

    override suspend fun healthCheck(): ProviderHealth = ProviderHealth(
        isHealthy = isHealthy,
        latencyMs = latencyMs
    )

    override suspend fun capabilities(): ProviderCapabilities = capabilities
}

class ProviderRouterTest {

    private lateinit var registry: ProviderRegistry
    private lateinit var router: ProviderRouter

    @Before
    fun setUp() {
        registry = ProviderRegistry()
        router = ProviderRouter(registry)
    }

    @Test
    fun testRegistryLifecycle() {
        val p1 = FakeTestProvider("p1", "Local Provider", ExecutionMode.LOCAL)
        val p2 = FakeTestProvider("p2", "Remote Provider", ExecutionMode.REMOTE)

        registry.register(p1)
        registry.register(p2)

        assertEquals(2, registry.getAll().size)
        assertEquals(p1, registry.get("p1"))
        assertEquals(p2, registry.get("p2"))

        val removed = registry.unregister("p1")
        assertEquals(p1, removed)
        assertNull(registry.get("p1"))
        assertEquals(1, registry.getAll().size)
    }

    @Test
    fun testLocalFirstRoutingPolicy() = runTest {
        val local = FakeTestProvider("local_llama", "Embedded llama.cpp", ExecutionMode.LOCAL, latencyMs = 80L)
        val remote = FakeTestProvider("remote_serve", "AgentX Serve Phone", ExecutionMode.REMOTE, latencyMs = 20L)

        registry.register(local)
        registry.register(remote)

        val resolved = router.resolveProvider(GenerationRequest(prompt = "Test"), RoutingPolicy.LocalFirst)
        assertEquals("local_llama", resolved.id)
    }

    @Test
    fun testRemoteFirstRoutingPolicy() = runTest {
        val local = FakeTestProvider("local_llama", "Embedded llama.cpp", ExecutionMode.LOCAL, latencyMs = 80L)
        val remote = FakeTestProvider("remote_serve", "AgentX Serve Phone", ExecutionMode.REMOTE, latencyMs = 20L)

        registry.register(local)
        registry.register(remote)

        val resolved = router.resolveProvider(GenerationRequest(prompt = "Test"), RoutingPolicy.RemoteFirst)
        assertEquals("remote_serve", resolved.id)
    }

    @Test
    fun testLowestLatencyRoutingPolicy() = runTest {
        val slow = FakeTestProvider("slow_provider", "Slow Cloud", ExecutionMode.REMOTE, latencyMs = 450L)
        val fast = FakeTestProvider("fast_serve", "Fast Local Serve", ExecutionMode.REMOTE, latencyMs = 15L)

        registry.register(slow)
        registry.register(fast)

        val resolved = router.resolveProvider(GenerationRequest(prompt = "Test"), RoutingPolicy.LowestLatency)
        assertEquals("fast_serve", resolved.id)
    }

    @Test
    fun testCapabilityBasedRoutingPolicy() = runTest {
        val textOnly = FakeTestProvider(
            "text_only",
            "Text Model",
            ExecutionMode.LOCAL,
            capabilities = ProviderCapabilities(supportsVision = false)
        )
        val visionCapable = FakeTestProvider(
            "vision_model",
            "Vision Model",
            ExecutionMode.REMOTE,
            capabilities = ProviderCapabilities(supportsVision = true)
        )

        registry.register(textOnly)
        registry.register(visionCapable)

        val resolved = router.resolveProvider(
            GenerationRequest(prompt = "Describe this image"),
            RoutingPolicy.CapabilityBased { it.supportsVision }
        )
        assertEquals("vision_model", resolved.id)
    }

    @Test
    fun testZeroStreamMixingFallbackWhenPrimaryFailsImmediately() = runTest {
        val failingPrimary = FakeTestProvider(
            "failing_cloud",
            "Failing Cloud Gateway",
            ExecutionMode.REMOTE,
            shouldFailImmediately = true
        )
        val backupLocal = FakeTestProvider(
            "backup_local",
            "Backup Embedded llama.cpp",
            ExecutionMode.LOCAL
        )

        registry.register(failingPrimary)
        registry.register(backupLocal)

        val results = router.routeAndGenerate(
            GenerationRequest(prompt = "Explain blood pressure regulation"),
            RoutingPolicy.RemoteFirst
        ).toList()

        val tokens = results.mapNotNull { (event, _) ->
            (event as? GenerationEvent.Token)?.text
        }
        assertTrue("Tokens should be emitted by backup provider", tokens.contains(" Backup Embedded llama.cpp"))

        val finishedPair = results.firstOrNull { it.first is GenerationEvent.Finished }
        assertNotNull("Finished event should be delivered", finishedPair)
        val provenance = finishedPair?.second
        assertNotNull("Provenance must be present", provenance)
        assertEquals("backup_local", provenance?.providerId)
        assertEquals(ExecutionMode.LOCAL, provenance?.executionMode)
    }

    @Test
    fun testNoFallbackAfterPartialStreamPreservesStreamIntegrity() = runTest {
        val midStreamFailing = FakeTestProvider(
            "mid_stream_fail",
            "Mid Stream Failing Provider",
            ExecutionMode.REMOTE,
            shouldFailAfterTokens = true
        )
        val backup = FakeTestProvider(
            "backup_provider",
            "Backup Provider",
            ExecutionMode.LOCAL
        )

        registry.register(midStreamFailing)
        registry.register(backup)

        val results = router.routeAndGenerate(
            GenerationRequest(prompt = "Summarize clinical trial"),
            RoutingPolicy.RemoteFirst
        ).toList()

        val tokens = results.mapNotNull { (event, _) -> (event as? GenerationEvent.Token)?.text }
        // Verify tokens were emitted before error
        assertTrue(tokens.isNotEmpty())

        // Verify that NO tokens from backup provider were appended
        assertFalse(
            "Backup provider tokens must NEVER be concatenated mid-stream into a failed primary stream",
            tokens.any { it.contains("Backup Provider") }
        )

        val hasError = results.any { it.first is GenerationEvent.Error }
        assertTrue("Error event must be emitted to notify UI cleanly", hasError)
    }
}
