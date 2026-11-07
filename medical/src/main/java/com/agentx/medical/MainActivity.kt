package com.agentx.medical

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agentx.medical.feature.agents.AgentsViewModel
import com.agentx.medical.feature.chat.ChatViewModel
import com.agentx.medical.feature.models.ModelsViewModel
import com.agentx.medical.feature.settings.SettingsViewModel
import com.agentx.medical.feature.teams.TeamsViewModel
import com.agentx.medical.navigation.AgentXMedicalNavGraph
import com.agentx.medical.ui.theme.AgentXMedicalTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AgentXMedicalApplication

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
                        applicationContext,
                        app.inferenceEngine
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
                        app.hybridInferenceEngine,
                        preferencesManager = app.preferencesManager
                    ) as T
                }
            }
        }

        setContent {
            AgentXMedicalTheme {
                AgentXMedicalNavGraph(
                    chatViewModel = chatViewModel,
                    agentsViewModel = agentsViewModel,
                    teamsViewModel = teamsViewModel,
                    modelsViewModel = modelsViewModel,
                    settingsViewModel = settingsViewModel
                )
                com.agentx.medical.ui.components.PermissionsDisclosureDialog(appName = "AgentX Medical") {}
            }
        }
    }
}
