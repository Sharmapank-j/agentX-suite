package com.agentx.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agentx.admin.ui.AdminDashboardScreen
import com.agentx.admin.ui.AdminViewModel
import com.agentx.admin.ui.theme.AgentXAdminTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AgentXAdminApplication

        val viewModel: AdminViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminViewModel(app) as T
                }
            }
        }

        setContent {
            AgentXAdminTheme {
                AdminDashboardScreen(viewModel = viewModel)
                com.agentx.admin.ui.PermissionsDisclosureDialog(appName = "AgentX Serve") {}
            }
        }
    }
}
