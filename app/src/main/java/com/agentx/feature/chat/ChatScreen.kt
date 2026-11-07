package com.agentx.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentx.core.inference.ModelLifecycleState
import com.agentx.core.inference.ModelStatus
import com.agentx.domain.models.Agent
import com.agentx.domain.models.AgentRole
import com.agentx.domain.models.Conversation
import com.agentx.domain.models.DidYouKnowInsight
import com.agentx.domain.models.DidYouKnowRepository
import com.agentx.domain.models.GGUFModel
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import com.agentx.feature.chat.components.MarkdownText
import com.agentx.ui.theme.AlertRose
import com.agentx.ui.theme.AuroraViolet
import com.agentx.ui.theme.CyberMint
import com.agentx.ui.theme.ElectricCyan
import com.agentx.ui.theme.NeonIndigo
import com.agentx.ui.theme.SpaceDark
import com.agentx.ui.theme.SurfaceBorder
import com.agentx.ui.theme.SurfaceGlass
import com.agentx.ui.theme.SurfaceGlassElevated
import com.agentx.ui.theme.TextMuted
import com.agentx.ui.theme.TextPrimary
import com.agentx.ui.theme.TextSecondary
import com.agentx.ui.theme.VoidDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var conversationToDeleteId by remember { mutableStateOf<String?>(null) }
    var showModelSwitcherSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VoidDark,
                modifier = Modifier.width(320.dp)
            ) {
                // Drawer Header
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "X",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = VoidDark
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AgentX Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Autonomous Multi-Agent AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gradient New Chat Button
                    Surface(
                        onClick = {
                            viewModel.createNewConversation()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Brush.linearGradient(listOf(ElectricCyan, NeonIndigo)), RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceGlassElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Conversation",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "New Conversation",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                )

                // Conversation List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.conversations) { conv ->
                        val isSelected = conv.id == state.currentConversation?.id
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    conv.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) ElectricCyan else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.selectConversation(conv.id)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                IconButton(
                                    onClick = {
                                        conversationToDeleteId = conv.id
                                        showDeleteConfirmDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = if (isSelected) ElectricCyan.copy(alpha = 0.7f) else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF0E2A47),
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }

                // Drawer Bottom Hardware HUD
                Surface(
                    color = SpaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CyberMint)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (state.activeModel != null) "Local Engine Active" else "Local GGUF Runtime",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            "2.0GB Budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CyberTopAppBar(
                    conversationTitle = state.currentConversation?.title ?: "AgentX Chat",
                                        activeModel = state.activeModel,
                    onTitleClick = {
                        renameText = state.currentConversation?.title ?: ""
                        showRenameDialog = true
                    },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onModelBadgeClick = { showModelSwitcherSheet = true },
                    onNavigateToMonitor = onNavigateToMonitor,
                    onRenameClick = {
                        renameText = state.currentConversation?.title ?: ""
                        showRenameDialog = true
                    },
                    onSwitchModelClick = { showModelSwitcherSheet = true },
                    onClearMessagesClick = { viewModel.clearCurrentConversation() }
                )
            },
            bottomBar = {
                Column {
                    // Live Inference Streaming Metrics Bar
                    if (state.liveMetrics.isLive || (state.isStreaming && state.liveMetrics.tokensGenerated > 0)) {
                        LiveMetricsHud(metrics = state.liveMetrics)
                    }

                    // Modern Floating Pill Input Bar
                    ChatInputBar(
                        text = state.inputText,
                        onTextChanged = viewModel::onInputTextChanged,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                        isStreaming = state.isStreaming,
                        activeModelName = state.activeModel?.name ?: state.activeModel?.quantization ?: "Local GGUF (llama.cpp)"
                    )
                }
            },
            containerColor = VoidDark,
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Active Model Status Banner
                ActiveModelStatusBanner(
                    modelStatus = state.modelStatus,
                    activeModel = state.activeModel,
                    onSwitchModelClick = { showModelSwitcherSheet = true }
                )

                if (state.messages.isEmpty()) {
                    // Dynamic "Did You Know?" Welcoming Insight (disappears when messages are present)
                    DidYouKnowWelcomeCard(
                        conversationId = state.currentConversation?.id,
                        onSelectPrompt = { prompt -> viewModel.sendPrompt(prompt) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    // Chat Message Feed
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        reverseLayout = false
                    ) {
                        items(state.messages) { message ->
                            ChatMessageItem(
                                message = message,
                                onRegenerate = { viewModel.regenerateResponse(message.id) },
                                onDelete = { viewModel.deleteMessage(message.id) },
                                isStreamingActive = state.isStreaming
                            )
                        }
                        if (state.isStreaming) {
                            item {
                                StreamingIndicator(agentName = state.streamingAgentName ?: "AgentX")
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Conversation Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SpaceDark,
            title = {
                Text("Rename Conversation", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Conversation Title", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.currentConversation?.let {
                            viewModel.renameConversation(it.id, renameText)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = SpaceDark,
            title = { Text("Delete Conversation?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove this conversation and its multi-agent telemetry from on-device storage.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        conversationToDeleteId?.let { viewModel.deleteConversation(it) }
                        showDeleteConfirmDialog = false
                        conversationToDeleteId = null
                    }
                ) {
                    Text("Delete", color = AlertRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Model Switcher Bottom Sheet
    if (showModelSwitcherSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showModelSwitcherSheet = false },
            sheetState = sheetState,
            containerColor = SpaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Active AI Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Select which local GGUF model executes on-device inference via native llama.cpp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (state.allModels.isEmpty()) {
                    Text(
                        text = "No models imported yet. Go to the Models tab to download or import GGUF models.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertRose,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.allModels) { model ->
                            val isSelected = model.id == state.activeModel?.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF0E2A47) else SurfaceGlass,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) ElectricCyan else SurfaceBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.switchActiveModel(model.id)
                                        showModelSwitcherSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) ElectricCyan else TextPrimary
                                        )
                                        Text(
                                            text = "${model.quantization} • ${model.architecture} • Context: ${model.contextLength}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar with Glassmorphic Obsidian Aesthetics
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyberTopAppBar(
    conversationTitle: String,
    activeModel: GGUFModel?,
    modelStatus: ModelStatus? = null,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onModelBadgeClick: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onRenameClick: () -> Unit,
    onSwitchModelClick: () -> Unit,
    onClearMessagesClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier.clickable { onTitleClick() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "X",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = VoidDark
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = conversationTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AgentX Assistant • Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontSize = 11.sp
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
            }
        },
        actions = {
            // Model Pill Badge
            val isLoadedPill = activeModel != null && (modelStatus?.state == ModelLifecycleState.LOADED || modelStatus?.state == ModelLifecycleState.GENERATING)
            Surface(
                onClick = onModelBadgeClick,
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGlass,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isLoadedPill) CyberMint.copy(alpha = 0.6f) else SurfaceBorder),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isLoadedPill) CyberMint else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isLoadedPill) "${activeModel?.name?.take(10) ?: "Engine"} (RAM)"
                               else (activeModel?.name?.take(10) ?: "llama.cpp"),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SpaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Execution Monitor", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricCyan) },
                        onClick = {
                            showMenu = false
                            onNavigateToMonitor()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Conversation", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ElectricCyan) },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Switch Model", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = NeonIndigo) },
                        onClick = {
                            showMenu = false
                            onSwitchModelClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Messages", color = AlertRose) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRose) },
                        onClick = {
                            showMenu = false
                            onClearMessagesClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SpaceDark
        )
    )
}

// Live Metrics Streaming HUD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LiveMetricsHud(metrics: LiveInferenceMetrics) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceGlassElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CyberMint)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "⚡ ${String.format("%.1f", metrics.tokensPerSecond)} t/s • ${metrics.tokensGenerated} tokens • ${String.format("%.1f", metrics.elapsedSeconds)}s",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (metrics.memoryUsageMb > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "• ${metrics.memoryUsageMb} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Model Status Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ActiveModelStatusBanner(
    modelStatus: ModelStatus,
    activeModel: GGUFModel?,
    onSwitchModelClick: () -> Unit,
    onLoadClick: () -> Unit = {},
    onUnloadClick: () -> Unit = {}
) {
    val isLoaded = modelStatus.state == ModelLifecycleState.LOADED || modelStatus.state == ModelLifecycleState.GENERATING
    val memoryMb = modelStatus.memoryUsageBytes / (1024 * 1024)

    Surface(
        color = SurfaceGlass,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLoaded) CyberMint.copy(alpha = 0.5f) else SurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSwitchModelClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isLoaded) CyberMint else Color(0xFFFF9800))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isLoaded) {
                            "${activeModel?.name ?: "llama.cpp Engine"} • ${memoryMb} MB RAM"
                        } else {
                            "${activeModel?.name ?: "Local GGUF"} • Not in RAM"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isLoaded) {
                            "${activeModel?.threads ?: 4} Threads • ${activeModel?.contextLength ?: 2048} Context Tokens"
                        } else {
                            "Ready for On-Demand Offline Inference"
                        },
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoaded) {
                    TextButton(
                        onClick = onUnloadClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Unload", fontSize = 11.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        onClick = onLoadClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Load", fontSize = 11.sp, color = CyberMint, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Switch ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSwitchModelClick() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic "Did You Know?" Welcoming Insight Card (Empty State)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DidYouKnowWelcomeCard(
    conversationId: String?,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var insight by remember(conversationId) {
        mutableStateOf(DidYouKnowRepository.getRandomInsight())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Hero Icon
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = VoidDark,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "AgentX",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Personal AI Assistant • Local & Cloud Intelligence",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Glassmorphic "Did You Know?" Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceGlassElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Top Tag Row: Badge + Category + Shuffle Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "💡 DID YOU KNOW?",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonIndigo.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${insight.emoji} ${insight.category}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC7D2FE),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            insight = DidYouKnowRepository.getRandomInsight(excludeId = insight.id)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Shuffle Insight",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Headline
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Fact Content
                Text(
                    text = insight.fact,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1-Tap Discussion Starter Button
                Surface(
                    onClick = { onSelectPrompt(insight.suggestedPrompt) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E2A47),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ask: \"${insight.suggestedPrompt.take(45)}...\"",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Related Exploration Chips
                if (insight.relatedChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "EXPLORE TOPICS",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        insight.relatedChips.forEach { chip ->
                            Surface(
                                onClick = { onSelectPrompt("Explain the core technical principles of $chip in detail.") },
                                shape = RoundedCornerShape(8.dp),
                                color = SpaceDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                            ) {
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat Message Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatMessageItem(
    message: Message,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    isStreamingActive: Boolean
) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender Badge & Role Label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (isUser) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NeonIndigo)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AgentX",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonIndigo
                )
                if (message.tokenCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${message.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        // Message Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF0E2A47) else SurfaceGlass,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) ElectricCyan.copy(alpha = 0.3f) else SurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        textColor = TextPrimary
                    )
                }
            }
        }

        // Message Action Toolbar
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    isCopied = true
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    tint = if (isCopied) ElectricCyan else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (!isUser) {
                IconButton(
                    onClick = onRegenerate,
                    enabled = !isStreamingActive,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate",
                        tint = if (!isStreamingActive) TextMuted else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streaming Indicator with Pulsing Neural Cursor
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StreamingIndicator(agentName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E2A47).copy(alpha = alpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$agentName is generating response...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ElectricCyan
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating Pill Input Bar with Glowing Neon Send
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    activeModelName: String = "llama.cpp"
) {
    Surface(
        color = VoidDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SurfaceGlassElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantization Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E2A47),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = activeModelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = { Text("Ask AgentX anything...", color = TextMuted, fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    )
                )

                if (isStreaming) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AlertRose)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (text.isNotBlank()) Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))
                                else Brush.linearGradient(listOf(SurfaceBorder, SurfaceBorder))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank()) VoidDark else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
