package com.agentx.admin

import com.agentx.admin.server.LocalModelServerEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalModelServerEngineTest {
    @Test fun curatedCatalogIsDiscoveryOnlyUntilRuntimeExists() {
        val engine = LocalModelServerEngine()
        assertTrue(engine.curatedCatalog.isNotEmpty())
        assertFalse(engine.activeModel.isLoaded)
        assertFalse(engine.activeModel.isServing)
    }

    @Test fun refusesToMarkCatalogEntryAsLoaded() = runTest {
        val engine = LocalModelServerEngine()
        assertFalse(engine.loadModel(engine.curatedCatalog.first().id))
        assertFalse(engine.activeModel.isLoaded)
        assertFalse(engine.activeModel.isServing)
    }

    @Test fun generationFailsInsteadOfReturningMockTokens() = runTest {
        val engine = LocalModelServerEngine()
        try {
            engine.generateCompletionSync("test prompt")
            fail("No real inference runtime is configured")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("real GGUF runtime"))
        }
    }
}
