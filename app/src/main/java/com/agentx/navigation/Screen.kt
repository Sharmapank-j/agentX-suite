package com.agentx.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    // Bottom Nav Destinations
    data object Chat : Screen("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object Agents : Screen("agents", "Agents", Icons.Default.Psychology)
    data object Models : Screen("models", "Models", Icons.Default.Folder)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Additional Detail & Builder Destinations
    data object AgentBuilder : Screen("agent_builder?agentId={agentId}", "Agent Builder", Icons.Default.Build) {
        fun createRoute(agentId: String? = null) = if (agentId != null) "agent_builder?agentId=$agentId" else "agent_builder"
    }

    data object TeamBuilder : Screen("team_builder?teamId={teamId}", "Team Builder", Icons.Default.Groups) {
        fun createRoute(teamId: String? = null) = if (teamId != null) "team_builder?teamId=$teamId" else "team_builder"
    }

    data object ToolPermissions : Screen("tool_permissions", "Tool Permissions", Icons.Default.Tune)
    data object ExecutionMonitor : Screen("execution_monitor", "Execution Monitor", Icons.Default.Psychology)
    data object ModelConfiguration : Screen("model_config?modelId={modelId}", "Model Configuration", Icons.Default.Settings) {
        fun createRoute(modelId: String) = "model_config?modelId=$modelId"
    }

    companion object {
        val bottomNavItems = listOf(Chat, Models, Settings)
    }
}
