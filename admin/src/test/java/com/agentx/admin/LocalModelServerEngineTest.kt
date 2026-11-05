package com.agentx.admin

import com.agentx.admin.server.LocalModelServerEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalModelServerEngineTest {
    @Test fun curatedCatalogIsDiscoveryOnlyUntilUserImportsWeights() {
        val engine = LocalModelServerEngine()
        assertTrue(engine.curatedCatalog.isNotEmpty())
        assertTrue(engine.curatedCatalog.none { it.isLoaded || it.isServing })
        assertFalse(engine.activeModel.isLoaded)
        assertFalse(engine.activeModel.isServing)
        assertEquals(com.agentx.admin.models.LocalModelStatus.NOT_LOADED, engine.status)
        assertEquals(0L, engine.activeModel.memoryFootprintMb)
    }

    @Test fun refusesToMarkCatalogEntryAsLoaded() = runTest {
        val engine = LocalModelServerEngine()
        assertFalse(engine.loadModel(engine.curatedCatalog.first().id))
        assertFalse(engine.activeModel.isLoaded)
        assertFalse(engine.activeModel.isServing)
        assertEquals(com.agentx.admin.models.LocalModelStatus.NOT_LOADED, engine.status)
    }

    @Test fun unknownModelMetadataIsNotGuessedFromFileSize() {
        assertEquals("Unknown", LocalModelServerEngine.detectParameters("custom-model.gguf", 7L * 1024 * 1024 * 1024))
        assertEquals("Unknown", LocalModelServerEngine.detectQuantization("custom-model.gguf"))
        assertEquals(0L, LocalModelServerEngine.computeLayerStreamingRamMb(7L * 1024 * 1024 * 1024))
    }

    @Test fun generationFailsInsteadOfReturningMockTokens() = runTest {
        val engine = LocalModelServerEngine()
        try {
            engine.generateCompletionSync("test prompt")
            fail("No real inference runtime is configured")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Load a local GGUF model"))
        }
    }
}
