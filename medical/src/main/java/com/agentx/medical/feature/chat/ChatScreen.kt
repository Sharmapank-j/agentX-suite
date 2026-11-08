package com.agentx.medical.feature.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.runtime.derivedStateOf
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
import com.agentx.medical.ui.theme.BioEmerald
import com.agentx.medical.ui.theme.ClinicalAlert
import com.agentx.medical.ui.theme.ClinicalTeal
import com.agentx.medical.ui.theme.ClinicalTealBright
import com.agentx.medical.ui.theme.ClinicalTextMuted
import com.agentx.medical.ui.theme.ClinicalTextPrimary
import com.agentx.medical.ui.theme.ClinicalTextSecondary
import com.agentx.medical.ui.theme.CyanCaduceus
import com.agentx.medical.ui.theme.MedicalSurface
import com.agentx.medical.ui.theme.MedicalSurfaceBorder
import com.agentx.medical.ui.theme.MedicalSurfaceElevated
import com.agentx.medical.ui.theme.MedicalSurfaceGlass
import com.agentx.medical.ui.theme.MedicalVoid
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentx.medical.core.inference.ModelLifecycleState
import com.agentx.medical.domain.models.Conversation
import com.agentx.medical.domain.models.MedicalDidYouKnowInsight
import com.agentx.medical.domain.models.MedicalDidYouKnowRepository
import com.agentx.medical.domain.models.GGUFModel
import com.agentx.medical.domain.models.Message
import com.agentx.medical.domain.models.MessageRole
import com.agentx.medical.domain.models.MessageStatus
import com.agentx.medical.feature.chat.components.MarkdownText
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
    val listState = rememberLazyListState()

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = visibleItems.last().index
                val totalItems = layoutInfo.totalItemsCount
                lastVisibleIndex >= totalItems - 2
            }
        }
    }

    // Auto-scroll on new message added
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Auto-scroll on streaming content updates if user is already at the bottom
    LaunchedEffect(state.messages.lastOrNull()?.content) {
        if (isAtBottom && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var conversationToDeleteId by remember { mutableStateOf<String?>(null) }
    var showModelSwitcherSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Clinical Consultations",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Surface(
                    onClick = {
                        viewModel.createNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Consultation",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "New Clinical Consult",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.conversations) { conv ->
                        val isSelected = conv.id == state.currentConversation?.id
                        NavigationDrawerItem(
                            label = { Text(conv.title, maxLines = 1) },
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
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier.clickable {
                                renameText = state.currentConversation?.title ?: ""
                                showRenameDialog = true
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "⚕",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.currentConversation?.title ?: "AgentX Medical",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Clinical Assistant • Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Consultation Drawer")
                        }
                    },
                    actions = {
                        // Model Pill Badge
                        val isLoadedPill = state.activeModel != null && (state.modelStatus.state == ModelLifecycleState.LOADED || state.modelStatus.state == ModelLifecycleState.GENERATING)
                        Surface(
                            onClick = { showModelSwitcherSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
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
                                        .background(if (isLoadedPill) Color(0xFF10B981) else Color(0xFFFF9800))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isLoadedPill) "${state.activeModel?.name?.take(10) ?: "Engine"} (RAM)"
                                           else (state.activeModel?.name?.take(10) ?: "llama.cpp"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reasoning Monitor") },
                                    leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToMonitor()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename Consult") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        renameText = state.currentConversation?.title ?: ""
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Switch Model") },
                                    leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        showModelSwitcherSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Messages") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.clearCurrentConversation()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MedicalSurface
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (state.liveMetrics.isLive || (state.isStreaming && state.liveMetrics.tokensGenerated > 0)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MedicalSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BioEmerald)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "⚡ ${String.format("%.1f", state.liveMetrics.tokensPerSecond)} t/s • ${state.liveMetrics.tokensGenerated} tokens • ${String.format("%.1f", state.liveMetrics.elapsedSeconds)}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ClinicalTextPrimary
                                )
                                if (state.liveMetrics.memoryUsageMb > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "• ${state.liveMetrics.memoryUsageMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ClinicalTealBright
                                    )
                                }
                            }
                        }
                    }
                    ChatInputBar(
                        text = state.inputText,
                        onTextChanged = viewModel::onInputTextChanged,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                        isStreaming = state.isStreaming
                    )
                }
            },
            containerColor = MedicalVoid,
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Active Model Status Banner
                    ActiveModelStatusBanner(
                        modelStatus = state.modelStatus,
                        activeModel = state.activeModel,
                        onSwitchModelClick = { showModelSwitcherSheet = true },
                        onLoadClick = { viewModel.loadActiveModelIntoRam() },
                        onUnloadClick = { viewModel.unloadActiveModelFromRam() }
                    )

                    if (state.messages.isEmpty()) {
                        EmptyMedicalChatPlaceholder(
                            activeModel = state.activeModel,
                            modelStatus = state.modelStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    } else {
                        // Message Thread with Smart Auto-Scroll
                        LazyColumn(
                            state = listState,
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

                // Floating Smart Auto-Scroll Pill for Clinical Consults
                AnimatedVisibility(
                    visible = !isAtBottom && state.messages.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Surface(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(state.messages.size - 1)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MedicalSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.6f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                tint = ClinicalTealBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isStreaming) "⚕️ Streaming Insights ▾" else "Scroll to latest ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = ClinicalTealBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Consultation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Consultation Title") },
                    singleLine = true,
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Consultation?") },
            text = { Text("This will permanently remove this consultation history and all specialist notes from on-device storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        conversationToDeleteId?.let { viewModel.deleteConversation(it) }
                        showDeleteConfirmDialog = false
                        conversationToDeleteId = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Model Switcher Bottom Sheet
    if (showModelSwitcherSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showModelSwitcherSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Active Clinical Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select which local model powers clinical consultations. High-capacity layer streaming ensures fast, memory-safe execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (state.allModels.isEmpty()) {
                    Text(
                        text = "No GGUF models imported yet. Go to the Models tab to download or import clinical models.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.allModels) { model ->
                            val isSelected = model.id == state.activeModel?.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.switchActiveModel(model.id)
                                        showModelSwitcherSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${model.quantization} • ${model.architecture} • Context: ${model.contextLength}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ActiveModelStatusBanner(
    modelStatus: com.agentx.medical.core.inference.ModelStatus,
    activeModel: GGUFModel?,
    onSwitchModelClick: () -> Unit,
    onLoadClick: () -> Unit = {},
    onUnloadClick: () -> Unit = {}
) {
    val isLoaded = modelStatus.state == ModelLifecycleState.LOADED || modelStatus.state == ModelLifecycleState.GENERATING
    val memoryMb = modelStatus.memoryUsageBytes / (1024 * 1024)

    Surface(
        color = if (isLoaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLoaded) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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
                        .background(if (isLoaded) Color(0xFF10B981) else Color(0xFFFF9800))
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
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isLoaded) {
                            "${activeModel?.threads ?: 4} Threads • ${activeModel?.contextLength ?: 2048} Context Tokens"
                        } else {
                            "Ready for On-Demand Offline Inference"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoaded) {
                    TextButton(
                        onClick = onUnloadClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Unload", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        onClick = onLoadClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Load", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Switch ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSwitchModelClick() }
                )
            }
        }
    }
}

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
            .padding(vertical = 5.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Clinical Sender Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (isUser) {
                Text(
                    text = "Clinician",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTealBright
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ClinicalTealBright)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ClinicalTealBright, BioEmerald))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚕", fontSize = 11.sp, color = MedicalVoid, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AgentX Medical",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTealBright
                )
                if (message.tokenCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF042F2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "⚡ ${message.tokenCount} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTealBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Clinical Message Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) Color(0xFF062E3B) else MedicalSurfaceGlass,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) ClinicalTealBright.copy(alpha = 0.4f) else MedicalSurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.94f)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = ClinicalTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        textColor = ClinicalTextPrimary
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
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    tint = if (isCopied) BioEmerald else ClinicalTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (isCopied) {
                Text(
                    text = "Copied",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = BioEmerald,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (!isUser) {
                IconButton(
                    onClick = onRegenerate,
                    enabled = !isStreamingActive,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate",
                        tint = if (!isStreamingActive) ClinicalTextMuted else ClinicalTextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ClinicalTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun StreamingIndicator(agentName: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF042F2E),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.4f)),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = ClinicalTealBright
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$agentName is evaluating clinical literature...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ClinicalTealBright
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean
) {
    Surface(
        color = MedicalVoid,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MedicalSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Caduceus Tag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF042F2E),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "⚕ MediMind",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClinicalTealBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = { Text("Consult with AgentX Medical...", color = ClinicalTextMuted, fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = ClinicalTextPrimary,
                        unfocusedTextColor = ClinicalTextPrimary,
                        cursorColor = ClinicalTealBright
                    )
                )

                if (isStreaming) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ClinicalAlert)
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
                                if (text.isNotBlank()) Brush.linearGradient(listOf(ClinicalTealBright, BioEmerald))
                                else Brush.linearGradient(listOf(MedicalSurfaceBorder, MedicalSurfaceBorder))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank()) MedicalVoid else ClinicalTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Futuristic Clinical-Obsidian Empty State
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyMedicalChatPlaceholder(
    activeModel: com.agentx.medical.domain.models.GGUFModel?,
    modelStatus: com.agentx.medical.core.inference.ModelStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "medicalPulse")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "medAuraAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Hero Orbital Caduceus
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ClinicalTealBright.copy(alpha = auraAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ClinicalTealBright, BioEmerald))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚕",
                    fontSize = 32.sp,
                    color = MedicalVoid,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "AgentX Medical",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = ClinicalTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Evidence-Based Clinical Decision Support • 100% Offline",
            style = MaterialTheme.typography.bodyMedium,
            color = ClinicalTextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Clinical Notice Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF042F2E).copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚕️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clinical Notice: For education, research, and assistive reasoning only. Non-diagnostic. Verify all outputs with licensed physician.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClinicalTealBright,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Status Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MedicalSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isLoaded = modelStatus.state == com.agentx.medical.core.inference.ModelLifecycleState.LOADED
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLoaded) BioEmerald.copy(alpha = 0.15f) else ClinicalAlert.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLoaded) BioEmerald else ClinicalAlert)
                    )
                    Text(
                        text = if (isLoaded) "Biomedical Engine Ready" else "Model Not Staged In RAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoaded) BioEmerald else ClinicalAlert
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = activeModel?.name ?: "No model selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (activeModel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeModel.quantization} • ${activeModel.architecture} • ${activeModel.contextLength} ctx",
                        style = MaterialTheme.typography.labelSmall,
                        color = ClinicalTealBright
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clinical Guarantee Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MedicalSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("HIPAA / GDPR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ClinicalTextMuted)
                            Text("100% On-Device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BioEmerald)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MedicalSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("AIR-GAPPED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ClinicalTextMuted)
                            Text("Zero Hospital Leak", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ClinicalTealBright)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Enter clinical question or case notes below to begin.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClinicalTextSecondary
                )
            }
        }
    }
}
