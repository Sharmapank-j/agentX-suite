package com.agentx.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.agentx.core.inference.ModelLifecycleState
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
    val context = LocalContext.current
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
                            "AgentX operates offline-first with an embedded llama.cpp runtime. When Local GGUF is selected, zero data leaves your device. When Cloud or Custom Gateway is enabled, requests route through your configured endpoint.",
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

            // ── Active Model Engine & Runtime Load Panel ───────────────────
            Text("Active Model Engine & Hardware Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val isModelLoaded = state.modelStatus.state == ModelLifecycleState.LOADED || state.modelStatus.state == ModelLifecycleState.GENERATING
            val memoryMb = state.modelStatus.memoryUsageBytes / (1024 * 1024)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isModelLoaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isModelLoaded) Color(0xFF00E676) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isModelLoaded) "Engine Online in RAM" else "Model Staged (Not in RAM)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isModelLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                if (isModelLoaded) "$memoryMb MB RAM" else "0 MB RAM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.activeModel?.name ?: "No Local GGUF Model Selected",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    state.activeModel?.let { model ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${model.architecture} • ${model.quantization} • ${model.contextLength} Tokens Context • ${model.threads} Threads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Load / Unload Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isModelLoaded) {
                            OutlinedButton(
                                onClick = { viewModel.unloadActiveModelFromRam() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unload from RAM", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.loadActiveModelIntoRam() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load into RAM")
                            }
                        }

                        if (state.allModels.size > 1) {
                            var showModelDropdown by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { showModelDropdown = true }) {
                                    Text("Switch ▾")
                                }
                                DropdownMenu(
                                    expanded = showModelDropdown,
                                    onDismissRequest = { showModelDropdown = false }
                                ) {
                                    state.allModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model.name) },
                                            onClick = {
                                                viewModel.switchActiveModel(model.id)
                                                showModelDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Hardware Telemetry & Safety Diagnostics ──────────────────────
            Text("Hardware Telemetry & Safety Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thermal State", fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Nominal • No Throttling",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RAM Memory Headroom", fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Sufficient • Safe Headroom",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Air-Gapped Isolation", fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "100% Offline Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Multimodal Input & Vision AI Configuration ───────────────────
            Text("Multimodal Input & Vision AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multimodal Attachments", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Enable camera and image picker in chat input bar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = state.multimodalInputEnabled,
                            onCheckedChange = { viewModel.setMultimodalInputEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Image Downsampling & Vision Resolution", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("HIGH" to "1024px", "BALANCED" to "512px", "FAST" to "256px").forEach { (qual, desc) ->
                            FilterChip(
                                selected = state.multimodalQuality == qual,
                                onClick = { viewModel.setMultimodalQuality(qual) },
                                label = { Text("$qual ($desc)") }
                            )
                        }
                    }
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

            // Haptic Feedback
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tactile vibration pulses on streaming tokens, messages & touch interactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.hapticFeedback,
                        onCheckedChange = { isEnabled ->
                            viewModel.setHapticFeedback(isEnabled)
                            if (isEnabled) {
                                com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                    context,
                                    com.agentx.core.device.HapticFeedbackType.SUCCESS
                                )
                            }
                        }
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

            // ── Model Context Protocol (MCP) Subsystem ───────────────────────
            Text("Model Context Protocol (MCP)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            var showAddMcpDialog by remember { mutableStateOf(false) }
            var newMcpName by remember { mutableStateOf("") }
            var newMcpUrl by remember { mutableStateOf("") }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Connected MCP Servers", fontWeight = FontWeight.SemiBold)
                                Text("Standard JSON-RPC 2.0 tool endpoints", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        IconButton(onClick = { showAddMcpDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add MCP Server")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Embedded Standard Server
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("AgentX-Embedded-MCP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("In-Process • Calculator, System Time, System Memory", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.2f)
                            ) {
                                Text("Active", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    state.mcpServers.filter { !it.isLocal }.forEach { srv ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(srv.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(srv.endpointUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (srv.isConnected) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        if (srv.isConnected) "Connected (${srv.toolCount} tools)" else "Offline",
                                        fontSize = 10.sp,
                                        color = if (srv.isConnected) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Context Attention & Long-Term Memory ────────────────────────
            Text("Context Attention & Long-Term Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Durable Profile & Episodic Facts", fontWeight = FontWeight.SemiBold)
                                Text("${state.storedMemories.size} facts currently remembered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (state.storedMemories.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearAllMemories() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Memories", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (state.storedMemories.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No facts stored yet. AgentX automatically extracts your preferences, goals, and technical focus as you chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        state.storedMemories.take(6).forEach { mem ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        mem.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteMemory(mem.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddMcpDialog) {
                AlertDialog(
                    onDismissRequest = { showAddMcpDialog = false },
                    title = { Text("Add External MCP Server") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Connect a local or remote JSON-RPC 2.0 Model Context Protocol server:", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = newMcpName,
                                onValueChange = { newMcpName = it },
                                label = { Text("Server Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newMcpUrl,
                                onValueChange = { newMcpUrl = it },
                                label = { Text("Endpoint URL") },
                                placeholder = { Text("http://192.168.1.100:3000/mcp") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newMcpName.isNotBlank() && newMcpUrl.isNotBlank()) {
                                    viewModel.registerRemoteMcpServer(newMcpName.trim(), newMcpUrl.trim())
                                    newMcpName = ""
                                    newMcpUrl = ""
                                    showAddMcpDialog = false
                                }
                            }
                        ) {
                            Text("Connect")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddMcpDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
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
