package com.agentx.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agentx.feature.agents.AgentBuilderScreen
import com.agentx.feature.agents.AgentsScreen
import com.agentx.feature.agents.AgentsViewModel
import com.agentx.feature.chat.ChatScreen
import com.agentx.feature.chat.ChatViewModel
import com.agentx.feature.models.ModelConfigScreen
import com.agentx.feature.models.ModelsScreen
import com.agentx.feature.models.ModelsViewModel
import com.agentx.feature.settings.ExecutionMonitorScreen
import com.agentx.feature.settings.SettingsScreen
import com.agentx.feature.settings.SettingsViewModel
import com.agentx.feature.settings.ToolPermissionsScreen
import com.agentx.feature.teams.TeamBuilderScreen
import com.agentx.feature.teams.TeamsScreen
import com.agentx.feature.teams.TeamsViewModel

@Composable
fun AgentXNavGraph(
    chatViewModel: ChatViewModel,
    agentsViewModel: AgentsViewModel,
    teamsViewModel: TeamsViewModel,
    modelsViewModel: ModelsViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isBottomBarVisible = Screen.bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(imageVector = it, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // 1. Chat
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToMonitor = { navController.navigate(Screen.ExecutionMonitor.route) }
                )
            }

            // 2. Agents
            composable(Screen.Agents.route) {
                AgentsScreen(
                    viewModel = agentsViewModel,
                    onNavigateToAgentBuilder = { agentId ->
                        navController.navigate(Screen.AgentBuilder.createRoute(agentId))
                    },
                    onNavigateToTeamBuilder = {
                        navController.navigate(Screen.TeamBuilder.createRoute(null))
                    }
                )
            }

            // 3. Models
            composable(Screen.Models.route) {
                ModelsScreen(
                    viewModel = modelsViewModel,
                    onNavigateToModelConfig = { modelId ->
                        navController.navigate(Screen.ModelConfiguration.createRoute(modelId))
                    }
                )
            }

            // 4. Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToPermissions = { navController.navigate(Screen.ToolPermissions.route) },
                    onNavigateToMonitor = { navController.navigate(Screen.ExecutionMonitor.route) }
                )
            }

            // Detail / Builder Screens
            composable(
                route = Screen.AgentBuilder.route,
                arguments = listOf(navArgument("agentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId")
                AgentBuilderScreen(
                    agentId = agentId,
                    viewModel = agentsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.TeamBuilder.route,
                arguments = listOf(navArgument("teamId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val teamId = backStackEntry.arguments?.getString("teamId")
                TeamBuilderScreen(
                    teamId = teamId,
                    viewModel = teamsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ToolPermissions.route) {
                ToolPermissionsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ExecutionMonitor.route) {
                ExecutionMonitorScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ModelConfiguration.route,
                arguments = listOf(navArgument("modelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                ModelConfigScreen(
                    modelId = modelId,
                    viewModel = modelsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
