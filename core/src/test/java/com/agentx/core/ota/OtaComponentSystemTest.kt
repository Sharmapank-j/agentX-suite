package com.agentx.core.ota

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OtaComponentSystemTest {

    private lateinit var testBaseDir: File
    private lateinit var registry: OtaComponentRegistry

    @Before
    fun setUp() {
        testBaseDir = File(System.getProperty("java.io.tmpdir"), "agentx_ota_test_${System.nanoTime()}")
        testBaseDir.mkdirs()
        registry = OtaComponentRegistry(testBaseDir)
    }

    @After
    fun tearDown() {
        testBaseDir.deleteRecursively()
    }

    @Test
    fun testComponentManifestSerialization() {
        val manifest = ComponentManifest(
            id = "medical-intelligence-pack",
            name = "MediMind Clinical Guidelines",
            type = ComponentType.INTELLIGENCE_PACK,
            version = "1.2.0",
            versionCode = 10200,
            minAppVersionCode = 100,
            downloadUrl = "https://agentx.local/ota/download/medical-intelligence-pack",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sizeBytes = 2048L,
            releaseNotes = "Added qSOFA and Updated Sepsis-3 guidelines"
        )

        val json = Json { prettyPrint = true }
        val encoded = json.encodeToString(manifest)
        val decoded = json.decodeFromString<ComponentManifest>(encoded)

        assertEquals("medical-intelligence-pack", decoded.id)
        assertEquals(ComponentType.INTELLIGENCE_PACK, decoded.type)
        assertEquals(10200, decoded.versionCode)
        assertEquals("1.2.0", decoded.version)
    }

    @Test
    fun testOtaVerifierComputesAccurateSha256() {
        val testFile = File(testBaseDir, "test_payload.txt")
        testFile.writeText("AgentX Medical OTA Component Payload 2026")

        val sha256 = OtaVerifier.computeSha256(testFile)
        assertTrue(sha256.isNotBlank())
        assertEquals(64, sha256.length)

        assertTrue(OtaVerifier.verifySha256(testFile, sha256))
        assertFalse(OtaVerifier.verifySha256(testFile, "wrong_hash_1234567890abcdef"))
    }

    @Test
    fun testRegistryAtomicInstallationAndRollback() {
        val payloadFile = File(testBaseDir, "staged_pack.json")
        payloadFile.writeText("{\"packId\":\"test-pack\",\"version\":\"2.0.0\"}")

        val manifest = ComponentManifest(
            id = "test-pack",
            name = "Test Knowledge Pack",
            type = ComponentType.INTELLIGENCE_PACK,
            version = "2.0.0",
            versionCode = 200,
            minAppVersionCode = 1,
            downloadUrl = "http://localhost/test",
            sha256 = OtaVerifier.computeSha256(payloadFile),
            sizeBytes = payloadFile.length(),
            targetFileName = "test_pack.json"
        )

        val installed = registry.registerInstallation(manifest, payloadFile)
        assertEquals("test-pack", installed.id)
        assertEquals("2.0.0", installed.version)
        assertEquals(200, installed.versionCode)
        assertTrue(File(installed.filePath).exists())

        // Verify query methods
        assertEquals("2.0.0", registry.getActiveVersion("test-pack"))
        assertEquals(200, registry.getActiveVersionCode("test-pack"))
        assertNotNull(registry.getActiveFile("test-pack"))

        // Rollback test
        val rolledBack = registry.rollback("test-pack")
        assertTrue(rolledBack)
        assertEquals(null, registry.getActiveVersion("test-pack"))
        assertEquals(0, registry.getActiveVersionCode("test-pack"))
        assertFalse(File(installed.filePath).exists())
    }

    @Test
    fun testOtaUpdateManagerDiscoversNewerComponentsAndTriggersHotReload() = runTest {
        var hotReloadTriggered = false
        var reloadedVersion = ""

        val manager = OtaUpdateManager(
            registry = registry,
            currentAppVersionCode = 100
        )

        manager.addHotReloadListener(ComponentType.INTELLIGENCE_PACK) { comp ->
            hotReloadTriggered = true
            reloadedVersion = comp.version
        }

        val dummyFile = File(testBaseDir, "ota_payload.json")
        dummyFile.writeText("{\"status\":\"active\"}")
        val dummySha = OtaVerifier.computeSha256(dummyFile)

        val updateManifest = ComponentManifest(
            id = "clinical-guidelines",
            name = "Emergency Guidelines Pack",
            type = ComponentType.INTELLIGENCE_PACK,
            version = "1.5.0",
            versionCode = 150,
            minAppVersionCode = 100,
            downloadUrl = dummyFile.toURI().toURL().toString(),
            sha256 = dummySha,
            sizeBytes = dummyFile.length(),
            targetFileName = "guidelines.json"
        )

        // Simulate local application
        val applied = manager.applyUpdate(updateManifest)
        assertTrue(applied.isSuccess)
        val installed = applied.getOrThrow()

        assertEquals("clinical-guidelines", installed.id)
        assertEquals("1.5.0", installed.version)
        assertTrue(hotReloadTriggered)
        assertEquals("1.5.0", reloadedVersion)
    }
}
