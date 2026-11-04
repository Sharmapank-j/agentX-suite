package com.agentx.feature.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentx.domain.models.AgentRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentBuilderScreen(
    agentId: String?,
    viewModel: AgentsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(AgentRole.CODER) }
    var systemPrompt by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topP by remember { mutableFloatStateOf(0.9f) }
    var maxTokens by remember { mutableStateOf("1024") }
    var roleDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (agentId == null) "Create Agent" else "Edit Agent") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.saveAgent(
                                    id = agentId,
                                    name = name,
                                    role = selectedRole,
                                    systemPrompt = systemPrompt,
                                    temperature = temperature,
                                    topP = topP,
                                    maxTokens = maxTokens.toIntOrNull() ?: 1024
                                )
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Agent Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Role Dropdown
            ExposedDropdownMenuBox(
                expanded = roleDropdownExpanded,
                onExpandedChange = { roleDropdownExpanded = !roleDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedRole.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = roleDropdownExpanded,
                    onDismissRequest = { roleDropdownExpanded = false }
                ) {
                    AgentRole.entries.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.title) },
                            onClick = {
                                selectedRole = role
                                roleDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Instructions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                maxLines = 8
            )

            // Temperature Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Temperature", style = MaterialTheme.typography.labelMedium)
                    Text(String.format("%.2f", temperature), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..1.5f
                )
            }

            // Top-P Slider
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Top P", style = MaterialTheme.typography.labelMedium)
                    Text(String.format("%.2f", topP), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.1f..1.0f
                )
            }

            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Max Tokens") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveAgent(
                            id = agentId,
                            name = name,
                            role = selectedRole,
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            topP = topP,
                            maxTokens = maxTokens.toIntOrNull() ?: 1024
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Agent Configuration")
            }
        }
    }
}
