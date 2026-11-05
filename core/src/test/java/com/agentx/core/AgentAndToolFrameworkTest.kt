package com.agentx.core

import com.agentx.core.agent.framework.AgentRole
import com.agentx.core.agent.framework.AgentTask
import com.agentx.core.agent.framework.TaskDependencyGraph
import com.agentx.core.agent.framework.TaskStatus
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolExecutionRequest
import com.agentx.core.agent.tools.ToolExecutionResult
import com.agentx.core.agent.tools.ToolExecutor
import com.agentx.core.agent.tools.ToolPermissionLevel
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.core.rag.SlidingWindowChunker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAndToolFrameworkTest {

    @Test
    fun testTaskDependencyGraphExecutionOrder() {
        val task1 = AgentTask(id = "t1", title = "Extract facts", description = "", assignedRole = AgentRole.RESEARCHER)
        val task2 = AgentTask(id = "t2", title = "Write code", description = "", assignedRole = AgentRole.SPECIALIST, dependencies = listOf("t1"))
        val task3 = AgentTask(id = "t3", title = "Review", description = "", assignedRole = AgentRole.REVIEWER, dependencies = listOf("t2"))

        var graph = TaskDependencyGraph(goal = "Build feature", tasks = listOf(task1, task2, task3))

        // Initially, only task1 has 0 dependencies and should be executable
        var executable = graph.getExecutableTasks()
        assertEquals(1, executable.size)
        assertEquals("t1", executable[0].id)
        assertFalse(graph.isFinished())

        // Mark task1 completed
        graph = graph.copy(tasks = graph.tasks.map { if (it.id == "t1") it.copy(status = TaskStatus.COMPLETED) else it })
        executable = graph.getExecutableTasks()
        assertEquals(1, executable.size)
        assertEquals("t2", executable[0].id)

        // Mark task2 completed
        graph = graph.copy(tasks = graph.tasks.map { if (it.id == "t2") it.copy(status = TaskStatus.COMPLETED) else it })
        executable = graph.getExecutableTasks()
        assertEquals(1, executable.size)
        assertEquals("t3", executable[0].id)

        // Mark task3 completed
        graph = graph.copy(tasks = graph.tasks.map { if (it.id == "t3") it.copy(status = TaskStatus.COMPLETED) else it })
        assertTrue("Graph must report finished when all tasks are complete", graph.isFinished())
    }

    @Test
    fun testToolRegistrySafeExecution() = runBlocking {
        val registry = ToolRegistry()
        val safeCalculator = object : ToolExecutor {
            override val definition = ToolDefinition(
                name = "calculator",
                description = "Basic math",
                permissionLevel = ToolPermissionLevel.SAFE
            )
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                return ToolExecutionResult(callId = "c1", toolName = "calculator", isSuccess = true, output = "42")
            }
        }
        registry.register(safeCalculator)

        val req = ToolExecutionRequest(callId = "c1", toolName = "calculator", argumentsJson = "{\"expr\":\"6*7\"}")
        val res = registry.executeCall(req)
        assertTrue(res.isSuccess)
        assertEquals("42", res.output)
    }

    @Test
    fun testDangerousToolBlockedWithoutApproval() = runBlocking {
        val registry = ToolRegistry()
        val fileWriter = object : ToolExecutor {
            override val definition = ToolDefinition(
                name = "file_writer",
                description = "Writes file to disk",
                permissionLevel = ToolPermissionLevel.DANGEROUS
            )
            override suspend fun execute(argumentsJson: String): ToolExecutionResult {
                return ToolExecutionResult(callId = "c2", toolName = "file_writer", isSuccess = true, output = "File written")
            }
        }
        registry.register(fileWriter)

        // Call without approval -> MUST be blocked
        val unapprovedReq = ToolExecutionRequest(callId = "c2", toolName = "file_writer", argumentsJson = "{\"path\":\"/test\"}", isApproved = false)
        val blockedRes = registry.executeCall(unapprovedReq)
        assertFalse(blockedRes.isSuccess)
        assertTrue(blockedRes.error?.contains("requires explicit user approval") == true)

        // Call with approval -> Allowed
        val approvedReq = unapprovedReq.copy(isApproved = true)
        val successRes = registry.executeCall(approvedReq)
        assertTrue(successRes.isSuccess)
        assertEquals("File written", successRes.output)
    }

    @Test
    fun testSlidingWindowChunker() {
        val chunker = SlidingWindowChunker()
        val text = "Word1 Word2 Word3 Word4 Word5 Word6 Word7 Word8 Word9 Word10"
        val chunks = chunker.chunk(text = text, documentId = "doc1", targetChunkSize = 4, overlap = 2)

        assertTrue(chunks.isNotEmpty())
        assertEquals("doc1", chunks[0].documentId)
        assertEquals(0, chunks[0].chunkIndex)
        assertTrue(chunks[0].text.contains("Word1"))
    }
}
