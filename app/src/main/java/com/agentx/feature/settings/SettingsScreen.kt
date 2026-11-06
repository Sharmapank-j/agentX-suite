package com.agentx.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.InferenceMode
import com.agentx.domain.models.ExecutionLog
import com.agentx.domain.models.ExecutionStatus
import com.agentx.domain.models.Tool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showHipaaDialog by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    val providerApiKey = state.cloudConfig.apiKeyFor(state.cloudConfig.provider)
    var apiKeyText by remember(state.cloudConfig.provider, providerApiKey) { mutableStateOf(providerApiKey) }
    var customUrlText by remember(state.cloudConfig.customBaseUrl) { mutableStateOf(state.cloudConfig.customBaseUrl) }

    val detectedCores = remember { Runtime.getRuntime().availableProcessors() }
    val recommendedThreads = remember { (detectedCores - 1).coerceIn(1, 8) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings & Preferences") })
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
            // ── Edge AI & Privacy Safeguard Notice ──────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Privacy Shield",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Autonomous Edge AI Architecture",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "AgentX uses the provider configured in Settings. Local GGUF inference is currently unavailable; requests sent to custom or cloud endpoints leave this device according to that provider's privacy policy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Privacy & Data Isolation ────────────────────────────────────
            Text("Privacy & Data Isolation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHipaaDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Zero-Data-Exfiltration Privacy Safeguard", fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "100% on-device local execution. Zero telemetry, zero external tracking, zero forced cloud logging. Fully operational in air-gapped offline environments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ── System & Performance ────────────────────────────────────────────
            Text("Inference Performance & Hardware", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // CPU Threads & Hardware Detection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Inference Threads", fontWeight = FontWeight.SemiBold)
                                Text("Device detected: $detectedCores CPU cores", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${state.maxCpuThreads} threads",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = state.maxCpuThreads.toFloat(),
                        onValueChange = { viewModel.setCpuThreads(it.toInt()) },
                        valueRange = 1f..detectedCores.coerceAtLeast(4).toFloat(),
                        steps = (detectedCores.coerceAtLeast(4) - 2).coerceAtLeast(0)
                    )
                    Text(
                        "Recommended: $recommendedThreads threads for thermal stability & responsive UI.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Dark Mode
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dark Theme", fontWeight = FontWeight.SemiBold)
                        Text("Cyber-Obsidian dark mode palette", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) }
                    )
                }
            }

            // ── Inbuilt Cloud Models & Hybrid Execution ─────────────────────────
            Text("Inbuilt Cloud Models & Hybrid Execution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inference Execution Strategy", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    InferenceMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setInferenceMode(mode) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.cloudConfig.inferenceMode == mode,
                                onClick = { viewModel.setInferenceMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.displayName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    if (state.cloudConfig.inferenceMode != InferenceMode.LOCAL_ONLY) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("Cloud Provider & Model Tier", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.cloudConfig.provider == CloudProvider.GEMINI,
                                onClick = { viewModel.updateCloudConfig(provider = CloudProvider.GEMINI) },
                                label = { Text("Google Gemini") }
                            )
                            FilterChip(
                                selected = state.cloudConfig.provider == CloudProvider.CUSTOM_GATEWAY,
                                onClick = { viewModel.updateCloudConfig(provider = CloudProvider.CUSTOM_GATEWAY) },
                                label = { Text("Admin Server Hub") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.cloudConfig.provider == CloudProvider.GEMINI) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("gemini-2.5-flash", "gemini-2.5-pro").forEach { modelName ->
                                    FilterChip(
                                        selected = state.cloudConfig.modelName == modelName,
                                        onClick = { viewModel.updateCloudConfig(modelName = modelName) },
                                        label = { Text(if (modelName.contains("flash")) "2.5 Flash" else "2.5 Pro") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = {
                                    apiKeyText = it
                                    viewModel.updateCloudConfig(apiKey = it)
                                },
                                label = { Text("Gemini API Key") },
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = customUrlText,
                                onValueChange = {
                                    customUrlText = it
                                    viewModel.updateCloudConfig(customBaseUrl = it)
                                },
                                label = { Text("AgentX Serve Gateway URL") },
                                placeholder = { Text("http://192.168.1.100:8080") },
                                supportingText = { Text("IP:Port displayed in AgentX Serve app") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Auto-Discovered Wi-Fi Instances (Zero-Configuration mDNS)
                            if (state.discoveredHubs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "✨ Discovered on Local Wi-Fi (Tap to Connect):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.discoveredHubs.forEach { hub ->
                                        FilterChip(
                                            selected = customUrlText == hub.url,
                                            onClick = {
                                                customUrlText = hub.url
                                                viewModel.updateCloudConfig(customBaseUrl = hub.url)
                                                viewModel.testCloudConnection()
                                            },
                                            label = { Text("📡 ${hub.name} (${hub.host})") }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Quick Preset Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = customUrlText == "http://127.0.0.1:8080",
                                    onClick = {
                                        customUrlText = "http://127.0.0.1:8080"
                                        viewModel.updateCloudConfig(customBaseUrl = "http://127.0.0.1:8080")
                                    },
                                    label = { Text("127.0.0.1:8080") }
                                )
                                FilterChip(
                                    selected = customUrlText == "http://10.0.2.2:8080",
                                    onClick = {
                                        customUrlText = "http://10.0.2.2:8080"
                                        viewModel.updateCloudConfig(customBaseUrl = "http://10.0.2.2:8080")
                                    },
                                    label = { Text("Emulator (10.0.2.2)") }
                                )
                                FilterChip(
                                    selected = customUrlText.startsWith("http://192.168."),
                                    onClick = {
                                        if (!customUrlText.startsWith("http://192.168.")) {
                                            customUrlText = "http://192.168.1.100:8080"
                                            viewModel.updateCloudConfig(customBaseUrl = "http://192.168.1.100:8080")
                                        }
                                    },
                                    label = { Text("LAN Subnet") }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = {
                                    apiKeyText = it
                                    viewModel.updateCloudConfig(apiKey = it)
                                },
                                label = { Text("Client API Key (Optional)") },
                                placeholder = { Text("agx-live-...") },
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Dynamic Models Selection Chips (if available)
                            if (state.availableGatewayModels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Models Available on Serve Device:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.availableGatewayModels.forEach { model ->
                                        FilterChip(
                                            selected = state.cloudConfig.modelName == model,
                                            onClick = { viewModel.updateCloudConfig(modelName = model) },
                                            label = { Text(model) }
                                        )
                                    }
                                }
                            }

                            // Connected Server Details Card
                            state.serverDetails?.let { server ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Dns,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "AgentX Serve Device Online",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                "${server.latencyMs} ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Active Model: ${server.activeModel.ifEmpty { "No local model loaded" }}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Mode: ${server.servingMode} • Uptime: ${server.uptimeSeconds}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Test Connection & Fetch Models
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.testCloudConnection() },
                                enabled = !state.isTestingConnection && !state.isFetchingModels,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...")
                                } else {
                                    Text("Test Connection")
                                }
                            }

                            if (state.cloudConfig.provider == CloudProvider.CUSTOM_GATEWAY) {
                                OutlinedButton(
                                    onClick = { viewModel.fetchGatewayModels() },
                                    enabled = !state.isTestingConnection && !state.isFetchingModels
                                ) {
                                    if (state.isFetchingModels) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Models")
                                    }
                                }
                            }
                        }

                        // Status Result Banner
                        state.cloudTestResult?.let { result ->
                            Spacer(modifier = Modifier.height(10.dp))
                            val isSuccess = result.startsWith("Verified") || result.contains("Fetched")
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSuccess)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        result,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Security & Navigation ───────────────────────────────────────────
            Text("Audit & Tool Governance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Tool Permissions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPermissions() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Tool Capabilities & Permissions", fontWeight = FontWeight.SemiBold)
                            Text("Manage risk calculators & offline record access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Execution Monitor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToMonitor() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Agent Execution Monitor", fontWeight = FontWeight.SemiBold)
                            Text("Audit multi-agent tasks, subtask logs & tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }

    // Privacy Explanation Dialog
    if (showHipaaDialog) {
        AlertDialog(
            onDismissRequest = { showHipaaDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Isolation Safeguards")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AgentX provides uncompromising on-device privacy and cryptographic integrity:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Zero Network Egress: Local inference runs entirely on device with zero telemetry or background reporting.", style = MaterialTheme.typography.bodySmall)
                    Text("• Air-Gapped Operation: Fully operational without cellular data or Wi-Fi connectivity.", style = MaterialTheme.typography.bodySmall)
                    Text("• Sandboxed Storage: Chat sessions, agent telemetry, and local models reside strictly in private application storage.", style = MaterialTheme.typography.bodySmall)
                    Text("• Audit Controls: Multi-agent executions record cryptographic task IDs, token counts, and tool verification logs in local Room SQLite.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Status: Fully Verified & Certified Offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHipaaDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPermissionsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tool Permissions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.tools) { tool ->
                val perm = state.permissions.find { it.toolId == tool.id }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tool.name, fontWeight = FontWeight.Bold)
                            Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    tool.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Switch(
                            checked = perm?.granted ?: false,
                            onCheckedChange = { granted ->
                                viewModel.toggleToolPermission(tool.id, granted)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionMonitorScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Execution Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Active Execution Card (if running)
            state.activeExecutionState?.let { active ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Running",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Multi-Agent Workflow Active",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        "Step ${active.currentStep}/${active.totalSteps}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = active.currentActivity,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val progress = if (active.totalSteps > 0) active.currentStep.toFloat() / active.totalSteps else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (state.executions.isEmpty() && state.activeExecutionState == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.height(48.dp).width(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Multi-Agent Workflows Recorded Yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Run a multi-agent prompt in Chat to inspect agent tasks",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(state.executions) { exec ->
                    ExecutionLogItem(exec)
                }
            }
        }
    }
}

@Composable
private fun ExecutionLogItem(exec: ExecutionLog) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (exec.status) {
                        ExecutionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        ExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val statusIcon = if (exec.status == ExecutionStatus.COMPLETED) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Error
                    }
                    Icon(statusIcon, contentDescription = null, tint = statusColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        exec.agentId.replace("agent_", "").replace("_", " ").uppercase(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (exec.status == ExecutionStatus.COMPLETED) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        exec.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Task: ${exec.taskId} • ${exec.executionTimeMs}ms • ${exec.tokenUsage} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (exec.toolCallsJson.isNotBlank() && exec.toolCallsJson != "[]") {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "🔧 Tool Executions Verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = exec.outputResult,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "Show Less" else "Show Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
