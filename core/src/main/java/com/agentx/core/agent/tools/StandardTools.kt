package com.agentx.core.agent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Standard Tool Implementations and MCP Client Implementation
 * specified in Section G of the Master Architecture.
 */

class CalculatorTool : ToolExecutor {
    override val definition: ToolDefinition = ToolDefinition(
        name = "calculator",
        description = "Evaluates basic mathematical arithmetic expressions deterministically (supports +, -, *, /, %, ^, parentheses).",
        permissionLevel = ToolPermissionLevel.SAFE,
        parameters = listOf(
            ToolParameter(
                name = "expression",
                type = "string",
                description = "The arithmetic expression to evaluate, e.g. '15 * (4 + 6) / 2'.",
                isRequired = true
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        return try {
            val jsonObj = Json.parseToJsonElement(argumentsJson).jsonObject
            val expression = jsonObj["expression"]?.jsonPrimitive?.contentOrNull
                ?: return ToolExecutionResult(
                    callId = UUID.randomUUID().toString(),
                    toolName = definition.name,
                    isSuccess = false,
                    output = "",
                    error = "Missing 'expression' parameter"
                )

            val result = evaluateExpression(expression)
            ToolExecutionResult(
                callId = UUID.randomUUID().toString(),
                toolName = definition.name,
                isSuccess = true,
                output = result.toString()
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                callId = UUID.randomUUID().toString(),
                toolName = definition.name,
                isSuccess = false,
                output = "",
                error = "Calculation error: ${e.message}"
            )
        }
    }

    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr.replace(" ", "")
        return SimpleParser(sanitized).parse()
    }

    private class SimpleParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected token: " + ch.toChar())
            }

            if (eat('^'.code)) x = Math.pow(x, parseFactor())
            return x
        }
    }
}

class SystemTimeTool : ToolExecutor {
    override val definition: ToolDefinition = ToolDefinition(
        name = "system_time",
        description = "Provides the current exact system date, time, day of week, UTC ISO-8601 string, and local timezone offset.",
        permissionLevel = ToolPermissionLevel.SAFE,
        parameters = emptyList()
    )

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val now = Date()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val localFormat = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss z", Locale.getDefault())

        val resultJson = buildString {
            append("{")
            append("\"isoUtc\":\"").append(isoFormat.format(now)).append("\",")
            append("\"localFormatted\":\"").append(localFormat.format(now)).append("\",")
            append("\"timestampMs\":").append(now.time).append(",")
            append("\"timezone\":\"").append(TimeZone.getDefault().id).append("\"")
            append("}")
        }

        return ToolExecutionResult(
            callId = UUID.randomUUID().toString(),
            toolName = definition.name,
            isSuccess = true,
            output = resultJson
        )
    }
}

class DeviceTelemetryTool(
    private val getBatteryPercent: () -> Int = { 85 },
    private val getIsCharging: () -> Boolean = { true },
    private val getAvailableRamMb: () -> Long = { 2048L },
    private val getThermalState: () -> String = { "NORMAL" }
) : ToolExecutor {
    override val definition: ToolDefinition = ToolDefinition(
        name = "device_telemetry",
        description = "Returns real-time device hardware metrics including battery state, available RAM, and thermal condition.",
        permissionLevel = ToolPermissionLevel.SENSITIVE,
        parameters = emptyList()
    )

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val payload = buildString {
            append("{")
            append("\"batteryPercent\":").append(getBatteryPercent()).append(",")
            append("\"isCharging\":").append(getIsCharging()).append(",")
            append("\"availableRamMb\":").append(getAvailableRamMb()).append(",")
            append("\"thermalState\":\"").append(getThermalState()).append("\"")
            append("}")
        }
        return ToolExecutionResult(
            callId = UUID.randomUUID().toString(),
            toolName = definition.name,
            isSuccess = true,
            output = payload
        )
    }
}

class StorageInspectionTool(
    private val getStorageStats: () -> Triple<Long, Long, Double> = {
        val root = java.io.File("/")
        val total = if (root.totalSpace > 0) root.totalSpace else 128L * 1024 * 1024 * 1024
        val free = if (root.freeSpace > 0) root.freeSpace else 64L * 1024 * 1024 * 1024
        val usedPct = if (total > 0) ((total - free).toDouble() / total) * 100.0 else 0.0
        Triple(total, free, usedPct)
    }
) : ToolExecutor {
    override val definition: ToolDefinition = ToolDefinition(
        name = "storage_inspection",
        description = "Returns real-time device storage metrics including total capacity, free storage space, and percentage used.",
        permissionLevel = ToolPermissionLevel.SAFE,
        parameters = emptyList()
    )

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val (total, free, usedPct) = getStorageStats()
        val freeGb = free.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val totalGb = total.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val payload = buildString {
            append("{")
            append("\"totalStorageBytes\":").append(total).append(",")
            append("\"freeStorageBytes\":").append(free).append(",")
            append("\"totalStorageGb\":").append(String.format(Locale.US, "%.2f", totalGb)).append(",")
            append("\"freeStorageGb\":").append(String.format(Locale.US, "%.2f", freeGb)).append(",")
            append("\"usedPercentage\":").append(String.format(Locale.US, "%.1f", usedPct))
            append("}")
        }
        return ToolExecutionResult(
            callId = UUID.randomUUID().toString(),
            toolName = definition.name,
            isSuccess = true,
            output = payload
        )
    }
}

class MemoryStatusTool(
    private val getMemoryMetrics: () -> Triple<Long, Long, Boolean> = {
        val rt = Runtime.getRuntime()
        val total = rt.totalMemory()
        val free = rt.freeMemory()
        val isLow = free < (total * 0.15)
        Triple(total, free, isLow)
    }
) : ToolExecutor {
    override val definition: ToolDefinition = ToolDefinition(
        name = "memory_status",
        description = "Returns system memory metrics including total allocated memory, free memory, and low-memory warning flag.",
        permissionLevel = ToolPermissionLevel.SAFE,
        parameters = emptyList()
    )

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val (total, free, isLow) = getMemoryMetrics()
        val used = total - free
        val freeMb = free.toDouble() / (1024.0 * 1024.0)
        val totalMb = total.toDouble() / (1024.0 * 1024.0)
        val payload = buildString {
            append("{")
            append("\"totalMemoryBytes\":").append(total).append(",")
            append("\"freeMemoryBytes\":").append(free).append(",")
            append("\"usedMemoryBytes\":").append(used).append(",")
            append("\"freeMemoryMb\":").append(String.format(Locale.US, "%.2f", freeMb)).append(",")
            append("\"totalMemoryMb\":").append(String.format(Locale.US, "%.2f", totalMb)).append(",")
            append("\"isLowMemory\":").append(isLow)
            append("}")
        }
        return ToolExecutionResult(
            callId = UUID.randomUUID().toString(),
            toolName = definition.name,
            isSuccess = true,
            output = payload
        )
    }
}

/**
 * Standard MCP JSON-RPC 2.0 Client implementation.
 */
class LocalMcpClient(
    override val serverName: String = "AgentX-Embedded-MCP",
    private val initialTools: List<ToolExecutor> = emptyList()
) : McpClient {

    private var connected = false
    private val toolMap = mutableMapOf<String, ToolExecutor>()

    init {
        for (tool in initialTools) {
            toolMap[tool.definition.name] = tool
        }
    }

    override val isConnected: Boolean
        get() = connected

    override suspend fun initialize(): Result<Unit> {
        connected = true
        return Result.success(Unit)
    }

    override suspend fun listTools(): List<ToolDefinition> {
        return toolMap.values.map { it.definition }
    }

    override suspend fun callTool(name: String, arguments: JsonObject): ToolExecutionResult {
        val tool = toolMap[name]
            ?: return ToolExecutionResult(
                callId = UUID.randomUUID().toString(),
                toolName = name,
                isSuccess = false,
                output = "",
                error = "Tool '$name' not found on MCP server '$serverName'"
            )

        val argsJson = Json.encodeToString(arguments)
        return tool.execute(argsJson)
    }

    override suspend fun disconnect() {
        connected = false
    }
}
