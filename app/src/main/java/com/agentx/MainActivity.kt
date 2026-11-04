package com.agentx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agentx.feature.agents.AgentsViewModel
import com.agentx.feature.chat.ChatViewModel
import com.agentx.feature.models.ModelsViewModel
import com.agentx.feature.settings.SettingsViewModel
import com.agentx.feature.teams.TeamsViewModel
import com.agentx.navigation.AgentXNavGraph
import com.agentx.ui.theme.AgentXTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AgentXApplication

        val chatViewModel: ChatViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        app.conversationRepository,
                        app.agentRepository,
                        app.modelRepository,
                        app.inferenceEngine,
                        app.clinicalSupervisor,
                        app.executionQueue
                    ) as T
                }
            }
        }

        val agentsViewModel: AgentsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AgentsViewModel(app.agentRepository) as T
                }
            }
        }

        val teamsViewModel: TeamsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TeamsViewModel(app.agentRepository) as T
                }
            }
        }

        val modelsViewModel: ModelsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ModelsViewModel(
                        app.modelRepository,
                        app.modelFileManager,
                        app.huggingFaceDownloader,
                        applicationContext
                    ) as T
                }
            }
        }

        val settingsViewModel: SettingsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        app.toolRepository,
                        app.executionRepository,
                        app.executionQueue,
                        app.hybridInferenceEngine
                    ) as T
                }
            }
        }

        setContent {
            AgentXTheme {
                AgentXNavGraph(
                    chatViewModel = chatViewModel,
                    agentsViewModel = agentsViewModel,
                    teamsViewModel = teamsViewModel,
                    modelsViewModel = modelsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
