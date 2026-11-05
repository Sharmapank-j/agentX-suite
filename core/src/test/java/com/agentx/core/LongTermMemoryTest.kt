package com.agentx.core

import com.agentx.core.memory.DefaultLongTermMemoryManager
import com.agentx.core.memory.MemoryEntry
import com.agentx.core.memory.MemoryQuery
import com.agentx.core.memory.MemoryType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongTermMemoryTest {

    @Test
    fun testStoreAndRetrieveProfileMemory() = runTest {
        val manager = DefaultLongTermMemoryManager()

        val entry = MemoryEntry(
            type = MemoryType.PROFILE,
            key = "user_name",
            content = "The user is Dr. Sharma, an internal medicine specialist.",
            tags = listOf("identity", "medical"),
            importance = 0.95f,
            isPinned = true
        )

        manager.store(entry)

        val retrieved = manager.findByKey("user_name", MemoryType.PROFILE)
        assertNotNull(retrieved)
        assertEquals("The user is Dr. Sharma, an internal medicine specialist.", retrieved?.content)
        assertTrue(retrieved?.isPinned == true)
    }

    @Test
    fun testSemanticKeywordSearch() = runTest {
        val manager = DefaultLongTermMemoryManager()

        manager.store(
            MemoryEntry(
                type = MemoryType.PROFILE,
                key = "tech_pref",
                content = "User strongly prefers Kotlin and Jetpack Compose over Flutter.",
                tags = listOf("programming", "android"),
                importance = 0.8f
            )
        )

        manager.store(
            MemoryEntry(
                type = MemoryType.EPISODIC,
                key = "session_42",
                content = "Discussed atrial fibrillation management and CHA2DS2-VASc scoring.",
                tags = listOf("cardiology"),
                importance = 0.7f
            )
        )

        val results = manager.search(
            MemoryQuery(
                query = "Kotlin Android compose",
                types = setOf(MemoryType.PROFILE),
                limit = 5
            )
        )

        assertEquals(1, results.size)
        assertEquals("tech_pref", results[0].key)
        assertTrue(results[0].content.contains("Kotlin"))
    }

    @Test
    fun testCapacityPruningAndExpiration() = runTest {
        // Manager with capacity of 3 items
        val manager = DefaultLongTermMemoryManager(maxCapacity = 3)

        // 1. Pinned entry
        manager.store(
            MemoryEntry(
                id = "pinned_1",
                type = MemoryType.PROFILE,
                key = "k1",
                content = "Crucial identity",
                importance = 1.0f,
                isPinned = true
            )
        )

        // 2. Low importance entry
        manager.store(
            MemoryEntry(
                id = "low_1",
                type = MemoryType.EPISODIC,
                key = "k2",
                content = "Trivial note",
                importance = 0.1f
            )
        )

        // 3. Expired entry
        manager.store(
            MemoryEntry(
                id = "exp_1",
                type = MemoryType.EPISODIC,
                key = "k3",
                content = "Expired info",
                importance = 0.5f,
                expiresAtMs = System.currentTimeMillis() - 1000 // In the past
            )
        )

        // Expired item should be automatically purged
        val exp = manager.get("exp_1")
        assertNull(exp)

        // 4. Add high importance entry to trigger compaction
        manager.store(
            MemoryEntry(
                id = "high_1",
                type = MemoryType.EPISODIC,
                key = "k4",
                content = "Important clinical review",
                importance = 0.9f
            )
        )

        // Pinned item must always survive
        val pinned = manager.get("pinned_1")
        assertNotNull(pinned)
    }

    @Test
    fun testFactExtractionFromUserStatement() {
        val manager = DefaultLongTermMemoryManager()

        val statement = "Hello! My name is Pankaj and I prefer Kotlin for native Android development."
        val extracted = manager.extractFactsFromUserStatement(statement)

        assertTrue(extracted.isNotEmpty())
        val nameFact = extracted.firstOrNull { it.key == "user_name" }
        assertNotNull(nameFact)
        assertTrue(nameFact?.content?.contains("Pankaj") == true)
        assertTrue(nameFact?.isPinned == true)

        val prefFact = extracted.firstOrNull { it.key.startsWith("preference_") }
        assertNotNull(prefFact)
        assertTrue(prefFact?.content?.contains("Kotlin") == true)
    }

    @Test
    fun testExportAndImportJson() = runTest {
        val manager1 = DefaultLongTermMemoryManager()
        manager1.store(
            MemoryEntry(
                type = MemoryType.PROFILE,
                key = "theme",
                content = "Dark theme enabled",
                importance = 0.5f
            )
        )

        val json = manager1.exportJson()
        assertTrue(json.contains("Dark theme enabled"))

        val manager2 = DefaultLongTermMemoryManager()
        val count = manager2.importJson(json)
        assertEquals(1, count)

        val imported = manager2.findByKey("theme", MemoryType.PROFILE)
        assertNotNull(imported)
        assertEquals("Dark theme enabled", imported?.content)
    }
}
