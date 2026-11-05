package com.agentx.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentx.admin.models.ApiKeyRecord
import com.agentx.admin.models.CliEntry
import com.agentx.admin.models.ClientSession
import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.LocalServerModel
import com.agentx.admin.models.ServingMode
import com.agentx.admin.models.TrafficLog
import com.agentx.admin.models.TunnelConfig
import com.agentx.admin.models.TunnelProvider
import com.agentx.admin.models.TunnelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var newKeyClientName by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var portInput by remember(state.selectedPort) { mutableStateOf(state.selectedPort.toString()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importModelFromUri(it, context)
        }
    }

    LaunchedEffect(state.notificationMessage) {
        state.notificationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotification()
        }
    }

    val serverUrl = "http://${state.telemetry.ipAddress}:${state.telemetry.port}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Dns,
                                    contentDescription = "Server Hub",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.telemetry.isRunning) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AgentX Serve", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Master Server Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.telemetry.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (state.telemetry.isRunning) "SERVER ONLINE • ${state.servingMode.label}" else "SERVER STOPPED",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (state.telemetry.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                if (state.telemetry.isRunning) serverUrl else "Tap Start to host endpoints",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleServer(!state.telemetry.isRunning, context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.telemetry.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (state.telemetry.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.telemetry.isRunning) "Stop" else "Start")
                        }
                    }

                    if (state.telemetry.isRunning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(serverUrl)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Base URL", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString("$serverUrl/v1/chat/completions")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Endpoint", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Web Chat Client Access Button
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(serverUrl)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌐 Open Web Chat Client ($serverUrl/) on any Laptop/TV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Adaptive Thermal & Hardware Status HUD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(state.thermalSnapshot.status.badgeColorHex).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(state.thermalSnapshot.status.badgeColorHex).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "🌡️ ${state.thermalSnapshot.temperatureCelsius}°C • ${state.thermalSnapshot.status.label}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(state.thermalSnapshot.status.badgeColorHex),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "🔋 ${state.thermalSnapshot.batteryPct}% ${if (state.thermalSnapshot.isCharging) "⚡" else ""}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "📡 mDNS Ready",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Tabs Header
            val tabs = listOf("Local Models", "CLI Console", "Tunneling", "Telemetry", "Cloud Gateway", "Clients", "API Keys", "Logs", "Quickstart")
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (state.selectedTab) {
                    0 -> LocalModelsTab(
                        state = state,
                        onServingModeChange = { viewModel.setServingMode(it) },
                        onLoadModel = { viewModel.loadLocalModel(it) },
                        onUnloadModel = { viewModel.unloadLocalModel() },
                        onPickModelFromStorage = {
                            try {
                                filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            } catch (_: Exception) {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        },
                        onScanStorage = { viewModel.scanDeviceStorage(context) },
                        onRemoveStorageModel = { viewModel.removeStorageModel(it) }
                    )
                    1 -> CliConsoleTab(
                        cliHistory = state.cliHistory,
                        isExecuting = state.isCliExecuting,
                        onExecuteCommand = { viewModel.executeCliCommand(it) },
                        onClear = { viewModel.clearCliHistory() },
                        onCopy = { clipboard.setText(AnnotatedString(it)) }
                    )
                    2 -> TunnelingTab(
                        tunnelConfig = state.tunnelConfig,
                        serverPort = state.selectedPort,
                        onStartTunnel = { provider, token, domain -> viewModel.startTunnel(provider, token, domain) },
                        onStopTunnel = { viewModel.stopTunnel() },
                        onTestTunnel = { viewModel.testTunnelConnection() },
                        onCopy = { clipboard.setText(AnnotatedString(it)) }
                    )
                    3 -> TelemetryTab(state = state, portInput = portInput, onPortChange = {
                        portInput = it
                        it.toIntOrNull()?.let { p -> viewModel.setPort(p) }
                    })
                    4 -> CloudGatewayTab(
                        state = state,
                        onKeyChange = { viewModel.setGeminiApiKey(it) },
                        onModelChange = { viewModel.setActiveModel(it) },
                        onTest = { viewModel.testCloudConnection() }
                    )
                    5 -> ClientsTab(
                        clients = state.clients,
                        onToggleBlock = { viewModel.toggleBlockClient(it) }
                    )
                    6 -> ApiKeysTab(
                        apiKeys = state.apiKeys,
                        onCreateClick = { showCreateKeyDialog = true },
                        onRevoke = { viewModel.revokeApiKey(it) },
                        onCopy = { clipboard.setText(AnnotatedString(it)) }
                    )
                    7 -> TrafficLogsTab(logs = state.trafficLogs)
                    8 -> QuickstartTab(serverUrl = serverUrl, tunnelUrl = state.tunnelConfig.publicUrl, apiKeys = state.apiKeys)
                }
            }
        }
    }

    if (showCreateKeyDialog) {
        AlertDialog(
            onDismissRequest = { showCreateKeyDialog = false },
            title = { Text("Generate New Client API Key") },
            text = {
                Column {
                    Text("Assign a name to identify this client device or user (e.g. 'ER Tablet', 'Dr. Adams iPad').", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newKeyClientName,
                        onValueChange = { newKeyClientName = it },
                        label = { Text("Client Device Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createApiKey(newKeyClientName)
                        newKeyClientName = ""
                        showCreateKeyDialog = false
                    }
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LocalModelsTab(
    state: AdminUiState,
    onServingModeChange: (ServingMode) -> Unit,
    onLoadModel: (String) -> Unit,
    onUnloadModel: () -> Unit,
    onPickModelFromStorage: () -> Unit,
    onScanStorage: () -> Unit,
    onRemoveStorageModel: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Serving Mode Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Serving Engine Architecture", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (state.servingMode) {
                            ServingMode.LOCAL_MODEL -> Color(0xFF10B981)
                            ServingMode.CLOUD_GATEWAY -> Color(0xFF3B82F6)
                            ServingMode.HYBRID -> Color(0xFF8B5CF6)
                        }
                    ) {
                        Text(
                            when (state.servingMode) {
                                ServingMode.LOCAL_MODEL -> "100% OFFLINE"
                                ServingMode.CLOUD_GATEWAY -> "CLOUD BACKED"
                                ServingMode.HYBRID -> "HYBRID AUTO"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    state.servingMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServingMode.values().forEach { mode ->
                        FilterChip(
                            selected = state.servingMode == mode,
                            onClick = { onServingModeChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ServingMode.LOCAL_MODEL -> "Local AirLLM"
                                        ServingMode.CLOUD_GATEWAY -> "Cloud Gemini"
                                        ServingMode.HYBRID -> "Hybrid"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (state.servingMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        // Section 2: Active Local Model Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ACTIVE SERVING MODEL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            state.activeLocalModel?.name ?: "No Local Model Loaded",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (state.localModelStatus) {
                            LocalModelStatus.LOADED -> Color(0xFF10B981)
                            LocalModelStatus.GENERATING -> Color(0xFF3B82F6)
                            LocalModelStatus.LOADING -> Color(0xFFF59E0B)
                            LocalModelStatus.NOT_LOADED -> Color(0xFF6B7280)
                        }
                    ) {
                        Text(
                            state.localModelStatus.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                state.activeLocalModel?.let { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RAM Footprint", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text(if (model.memoryFootprintMb > 0L) "${model.memoryFootprintMb} MB" else "Not measured", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Architecture", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text("${model.architecture} ${model.parameterCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Quantization", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text(model.quantization, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (state.localModelStatus == LocalModelStatus.LOADED) {
                            OutlinedButton(onClick = onUnloadModel) {
                                Text("Unload From RAM", fontSize = 11.sp)
                            }
                        } else {
                            Button(onClick = { onLoadModel(model.id) }) {
                                Text("Reload Model", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Choose Models from Phone Storage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phone Storage Models (.gguf)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose or scan local models saved in your phone storage, Downloads, or SD Card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickModelFromStorage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Model File", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onScanStorage,
                        enabled = !state.isScanningStorage,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isScanningStorage) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Downloads", fontSize = 11.sp)
                        }
                    }
                }

                if (state.storageModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Imported Storage Models (${state.storageModels.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    state.storageModels.forEach { model ->
                        val isActive = state.activeLocalModel?.id == model.id && state.localModelStatus == LocalModelStatus.LOADED

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "${model.architecture} • ${model.parameterCount} • ${model.quantization} • ${formatFileSize(model.fileSizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        model.filePath?.let { path ->
                                            Text(
                                                path.takeLast(40).let { if (path.length > 40) "...$it" else it },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981)
                                            ) {
                                                Text(
                                                    "Serving",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onLoadModel(model.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Load & Serve", fontSize = 11.sp)
                                            }
                                        }
                                        IconButton(onClick = { onRemoveStorageModel(model.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No custom models selected yet. Tap 'Pick Model File' to select any .gguf or .bin file stored on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Section 4: Curated Models Catalog
        Text("Curated Open-Source Edge Models", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        state.curatedLocalModels.forEach { model ->
            val isActive = state.activeLocalModel?.id == model.id && state.localModelStatus == LocalModelStatus.LOADED

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${model.architecture} • ${model.parameterCount} • ${model.quantization} • ${if (model.memoryFootprintMb > 0L) "${model.memoryFootprintMb} MB RAM" else "RAM unknown"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Serving", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = { onLoadModel(model.id) },
                                enabled = state.localModelStatus != LocalModelStatus.LOADING
                            ) {
                                Text("Load & Serve", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

    }
}

@Composable
private fun CliConsoleTab(
    cliHistory: List<CliEntry>,
    isExecuting: Boolean,
    onExecuteCommand: (String) -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit
) {
    var cmdInput by remember { mutableStateOf("") }
    val listState = rememberScrollState()

    LaunchedEffect(cliHistory.size) {
        listState.animateScrollTo(listState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top HUD & Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            modifier = Modifier.size(10.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10B981), CircleShape))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "CLI TEST & DIAGNOSTIC CONSOLE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                val fullLog = cliHistory.joinToString("\n") { (it.command?.let { c -> "> $c\n" } ?: "") + it.output }
                                onCopy(fullLog)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Console", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Command Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickCommands = listOf("stats", "device", "llm", "bench", "tunnel", "models", "help")
                    quickCommands.forEach { qc ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { onExecuteCommand(qc) }
                        ) {
                            Text(
                                qc,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Terminal Display Area
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F141C),
            border = BorderStroke(1.dp, Color(0xFF1F2937)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(listState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cliHistory.forEach { entry ->
                    if (!entry.command.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "agentx-serve:~$ ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                entry.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF3F4F6)
                            )
                        }
                    }

                    if (entry.output.isNotBlank()) {
                        Text(
                            text = entry.output,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (entry.isError) Color(0xFFEF4444) else Color(0xFF93C5FD),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                if (isExecuting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "executing command...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        // Command Prompt Input
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF131822),
            border = BorderStroke(1.dp, Color(0xFF374151)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "agentx-serve:~$ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )

                OutlinedTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    placeholder = {
                        Text(
                            "Type 'stats', 'device', 'bench', or 'help'...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF3F4F6)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (cmdInput.isNotBlank()) {
                            onExecuteCommand(cmdInput)
                            cmdInput = ""
                        }
                    },
                    enabled = !isExecuting && cmdInput.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = if (cmdInput.isNotBlank()) Color(0xFF10B981) else Color(0xFF4B5563)
                    )
                }
            }
        }
    }
}

@Composable
private fun TunnelingTab(
    tunnelConfig: TunnelConfig,
    serverPort: Int,
    onStartTunnel: (TunnelProvider, String, String) -> Unit,
    onStopTunnel: () -> Unit,
    onTestTunnel: () -> Unit,
    onCopy: (String) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(tunnelConfig.provider) }
    var authTokenInput by remember { mutableStateOf(tunnelConfig.authToken) }
    var customDomainInput by remember { mutableStateOf(tunnelConfig.customDomain) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Tunnel Status HUD Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (tunnelConfig.isEnabled) Color(0xFF064E3B).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (tunnelConfig.isEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GLOBAL TUNNEL ROUTING",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (tunnelConfig.isEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            if (tunnelConfig.isEnabled) "PUBLIC ROUTE ACTIVE" else "TUNNEL INACTIVE",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (tunnelConfig.status) {
                            TunnelStatus.CONNECTED -> Color(0xFF10B981)
                            TunnelStatus.STARTING -> Color(0xFFF59E0B)
                            TunnelStatus.ERROR -> Color(0xFFEF4444)
                            TunnelStatus.STOPPED -> MaterialTheme.colorScheme.outline
                        }
                    ) {
                        Text(
                            tunnelConfig.status.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (tunnelConfig.isEnabled && tunnelConfig.publicUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF111827),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Public Endpoint URL:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                tunnelConfig.publicUrl,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF67E8F9)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Local Target: http://127.0.0.1:$serverPort (Forwarded)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCopy(tunnelConfig.publicUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Base URL", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onCopy("${tunnelConfig.publicUrl}/v1/chat/completions") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Endpoint", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onTestTunnel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Public Route Verification")
                    }

                    tunnelConfig.latencyMs?.let { lat ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "⚡ Verified Route Latency: ${lat}ms (Local Gateway HTTP 200 OK)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                } else {
                    Text(
                        "Expose this phone's AI models securely across the web. Client apps, websites, and external users can connect from anywhere without opening router ports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Section 2: Tunnel Configuration & Provider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tunnel Provider Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))

                // Provider Selectors
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TunnelProvider.values().forEach { provider ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider == provider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selectedProvider == provider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProvider = provider }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (selectedProvider == provider) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            CircleShape
                                        )
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(provider.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        when (provider) {
                                            TunnelProvider.CLOUDFLARE -> "Zero-configuration Cloudflare Quick Tunnel (Recommended, No sign-up required)"
                                            TunnelProvider.NGROK -> "Secure ngrok forwarding with authtoken"
                                            TunnelProvider.PINGGY -> "Instant SSH-based HTTPS forwarding via Pinggy.io"
                                            TunnelProvider.CUSTOM_RELAY -> "Self-hosted corporate relay or custom domain reverse proxy"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Inputs
                OutlinedTextField(
                    value = authTokenInput,
                    onValueChange = { authTokenInput = it },
                    label = { Text("Auth Token (Optional for Cloudflare, required for ngrok)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customDomainInput,
                    onValueChange = { customDomainInput = it },
                    label = { Text("Custom Domain / Subdomain (Optional)") },
                    placeholder = { Text("e.g. my-old-phone-ai or https://ai.mydomain.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tunnelConfig.isEnabled) {
                        Button(
                            onClick = onStopTunnel,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Tunnel")
                        }
                    } else {
                        Button(
                            onClick = { onStartTunnel(selectedProvider, authTokenInput, customDomainInput) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Global HTTPS Tunnel")
                        }
                    }
                }
            }
        }

        // Section 3: Old Phone 24/7 Hosting Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📱 24/7 Old Phone Hosting Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Turn any spare or retired Android phone into a high-performance, private AI micro-server:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))

                val steps = listOf(
                    "1. Connect Continuous Power" to "Plug the old phone into a dedicated 5V/2A wall charger to prevent battery depletion during multi-day inference.",
                    "2. Wi-Fi Power Optimization" to "In Android Wi-Fi settings, set 'Keep Wi-Fi on during sleep' to Always, ensuring network sockets stay open.",
                    "3. CPU WakeLock Active" to "AgentX Serve automatically holds a PARTIAL_WAKE_LOCK while running so layer streaming continues when screen is off.",
                    "4. Connect Client Devices" to "Copy the LAN URL (for devices at home) or the Tunnel URL (for devices anywhere in the world) into your client apps."
                )

                steps.forEach { (title, desc) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024L -> String.format("%.0f KB", bytes.toDouble() / 1024)
        bytes > 0L -> "$bytes B"
        else -> "Storage Model"
    }
}

@Composable
private fun TelemetryTab(
    state: AdminUiState,
    portInput: String,
    onPortChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard("Requests Served", state.telemetry.requestsServed.toString(), Modifier.weight(1f))
            MetricCard("Tokens Emitted", state.telemetry.tokensEmitted.toString(), Modifier.weight(1f))
            MetricCard("Active Clients", state.clients.size.toString(), Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Network Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Local IP: ${state.telemetry.ipAddress}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = { Text("Server Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !state.telemetry.isRunning,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.telemetry.isRunning) {
                    Text("Stop the server to modify the listening port.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun CloudGatewayTab(
    state: AdminUiState,
    onKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onTest: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Inbuilt Google Gemini Cloud Gateway", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This phone acts as the AgentX Serve edge gateway. All active clients/users querying this phone will automatically utilize this cloud model without requiring their own API keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = onKeyChange,
                    label = { Text("Gemini API Key") },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Cloud Model Tier", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("gemini-2.5-flash", "gemini-2.5-pro").forEach { model ->
                        FilterChip(
                            selected = state.activeModel == model,
                            onClick = { onModelChange(model) },
                            label = { Text(if (model.contains("flash")) "2.5 Flash (Ultra-Fast)" else "2.5 Pro (Deep Reasoning)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onTest, enabled = !state.isTestingCloud) {
                        if (state.isTestingCloud) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Test Cloud Connection")
                        }
                    }

                    state.cloudConnectionStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.startsWith("Online")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientsTab(
    clients: List<ClientSession>,
    onToggleBlock: (String) -> Unit
) {
    if (clients.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No connected clients yet.\nClients will appear here when they send requests.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clients, key = { it.ipAddress }) { client ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(client.ipAddress, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${client.deviceName} • ${client.totalRequests} requests • ${client.totalTokens} tokens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Button(
                            onClick = { onToggleBlock(client.ipAddress) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (client.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(if (client.isBlocked) "Unblock" else "Block", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeysTab(
    apiKeys: List<ApiKeyRecord>,
    onCreateClick: () -> Unit,
    onRevoke: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Client API Keys (${apiKeys.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Key")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apiKeys, key = { it.key }) { keyRecord ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (keyRecord.isRevoked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(keyRecord.clientName, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (keyRecord.isRevoked) "REVOKED" else keyRecord.key,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (keyRecord.isRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!keyRecord.isRevoked) {
                            IconButton(onClick = { onCopy(keyRecord.key) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { onRevoke(keyRecord.key) }) {
                                Icon(Icons.Default.Block, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficLogsTab(logs: List<TrafficLog>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No traffic recorded yet.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(logs, key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1E2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${log.method} ${log.path}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF89B4FA),
                                fontSize = 12.sp
                            )
                            Text(
                                "IP: ${log.clientIp} • ${log.latencyMs}ms • ${log.tokensEmitted} tokens",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA6ADC8)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (log.statusCode in 200..299) Color(0xFF10B981) else Color(0xFFEF4444)
                        ) {
                            Text(
                                log.statusCode.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickstartTab(serverUrl: String, tunnelUrl: String? = null, apiKeys: List<ApiKeyRecord>) {
    val activeKey = apiKeys.firstOrNull { !it.isRevoked }?.key ?: "agx_adm_your_key_here"
    val clipboard = LocalClipboardManager.current

    val curlSnippet = """
curl -X POST "$serverUrl/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $activeKey" \
  -d '{
    "messages": [{"role": "user", "content": "Explain how edge AI models stream inference."}],
    "temperature": 0.7
  }'
    """.trimIndent()

    val jsWebSnippet = """
// Connect any Website Chatbot or Web App to this Old Phone!
async function streamChatbot(prompt) {
  const response = await fetch("$serverUrl/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": "Bearer $activeKey"
    },
    body: JSON.stringify({
      messages: [{ role: "user", content: prompt }],
      stream: true,
      temperature: 0.7
    })
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    const chunk = decoder.decode(value);
    console.log("Streamed token:", chunk);
  }
}
    """.trimIndent()

    val pythonSnippet = """
from openai import OpenAI

# Connect directly to this old phone over local Wi-Fi / LAN
client = OpenAI(
    base_url="$serverUrl/v1",
    api_key="$activeKey"
)

# Works directly with the on-device AirLLM layer-streaming engine
response = client.chat.completions.create(
    model="llama-3.2-3b",
    messages=[{"role": "user", "content": "Explain asynchronous programming in Kotlin."}]
)
print(response.choices[0].message.content)
    """.trimIndent()

    val pythonWanSnippet = tunnelUrl?.let { tun ->
        """
from openai import OpenAI

# Connect to this old phone from ANYWHERE in the world over HTTPS Tunnel!
client = OpenAI(
    base_url="$tun/v1",
    api_key="$activeKey"
)

response = client.chat.completions.create(
    model="llama-3.2-3b",
    messages=[{"role": "user", "content": "Hello from outside my home network!"}]
)
print(response.choices[0].message.content)
        """.trimIndent()
    }

    val agentXConnectSnippet = """
1. Open AgentX on your primary device.
2. Go to Settings -> "Inbuilt Cloud Models & Hybrid Execution".
3. Select "Admin Server Hub" as provider.
4. Set Gateway URL: $serverUrl
5. Set API Key: $activeKey (or leave empty if Auth is disabled)
6. Tap "Test Ping" & "Fetch Models" to select your model!
    """.trimIndent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SnippetCard("Website Chatbot Integration (JavaScript / Fetch)", jsWebSnippet) { clipboard.setText(AnnotatedString(jsWebSnippet)) }
        SnippetCard("AgentX & Android App Setup Guide", agentXConnectSnippet) { clipboard.setText(AnnotatedString(agentXConnectSnippet)) }
        SnippetCard("cURL Example (LAN & Terminal)", curlSnippet) { clipboard.setText(AnnotatedString(curlSnippet)) }
        SnippetCard("Python OpenAI SDK Example", pythonSnippet) { clipboard.setText(AnnotatedString(pythonSnippet)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SnippetCard(title: String, code: String, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1E2E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFCDD6F4),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
