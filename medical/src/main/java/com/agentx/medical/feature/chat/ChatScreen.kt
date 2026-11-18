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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.agentx.medical.intelligence.AnswerDepth
import com.agentx.medical.intelligence.ResponseStyle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Slider
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
                        viewModel.startNewConversation()
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
                        // Quick Action: New Clinical Consult
                        IconButton(onClick = { viewModel.startNewConversation() }) {
                            Icon(Icons.Default.Add, contentDescription = "New Consultation", tint = ClinicalTealBright)
                        }

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
                                    text = { Text("New Consult", color = ClinicalTealBright) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = ClinicalTealBright) },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.startNewConversation()
                                    }
                                )
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
                                    text = { Text("Clear Messages", color = Color(0xFFFF5252)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
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
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val isWideScreen = maxWidth >= 640.dp
                    Column(
                        modifier = if (isWideScreen) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth()
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
                            isStreaming = state.isStreaming,
                            currentStyle = state.currentResponseStyle,
                            currentDepth = state.currentAnswerDepth,
                            onStyleSelected = viewModel::updateCurrentConversationStyle,
                            onDepthSelected = viewModel::updateCurrentConversationDepth
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
            containerColor = MedicalVoid,
            modifier = modifier
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWideScreen = maxWidth >= 640.dp
                val contentModifier = if (isWideScreen) {
                    Modifier.widthIn(max = 760.dp).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                }

                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.messages.isEmpty()) {
                        EmptyMedicalChatPlaceholder(
                            activeModel = state.activeModel,
                            modelStatus = state.modelStatus,
                            currentStyle = state.currentResponseStyle,
                            currentDepth = state.currentAnswerDepth,
                            onQuickPrompt = { prompt -> viewModel.onInputTextChanged(prompt) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = if (isWideScreen) 32.dp else 16.dp, vertical = 12.dp)
                        )
                    } else {
                        // Roomy, Calm Message Thread with Smart Auto-Scroll
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = if (isWideScreen) 24.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
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

        // Clinical Message Bubble with Serene, Roomy Geometry
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 20.dp else 4.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            color = if (isUser) Color(0xFF063342) else Color(0xFF041924),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) ClinicalTealBright.copy(alpha = 0.45f) else MedicalSurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.94f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = ClinicalTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 23.sp
                    )
                } else {
                    Column {
                        val rawContent = message.content
                        val hasThinking = rawContent.contains("<think>")
                        val thinkingText = if (hasThinking) {
                            val afterThink = rawContent.substringAfter("<think>")
                            if (afterThink.contains("</think>")) {
                                afterThink.substringBefore("</think>").trim()
                            } else {
                                afterThink.trim()
                            }
                        } else null

                        val mainContent = if (hasThinking) {
                            if (rawContent.contains("</think>")) {
                                rawContent.substringAfter("</think>").trim()
                            } else {
                                ""
                            }
                        } else {
                            rawContent
                        }

                        if (!thinkingText.isNullOrBlank()) {
                            ClinicalReasoningCard(
                                thinkingText = thinkingText,
                                isStreaming = message.status == MessageStatus.STREAMING && !rawContent.contains("</think>")
                            )
                        }

                        if (mainContent.isNotBlank()) {
                            MarkdownText(
                                markdown = mainContent,
                                textColor = ClinicalTextPrimary
                            )
                        } else if (message.status == MessageStatus.STREAMING && rawContent.contains("</think>")) {
                            StreamingIndicator("MediMind")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Inline Discreet Footer Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    isCopied = true
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = if (isCopied) BioEmerald else ClinicalTextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (!isStreamingActive) {
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = onRegenerate,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        tint = ClinicalTextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = ClinicalTextMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                isCopied = true
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = if (isCopied) BioEmerald else ClinicalTextMuted.copy(alpha = 0.6f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    if (isCopied) {
                        Text(
                            text = "Copied",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = BioEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicalReasoningCard(
    thinkingText: String,
    isStreaming: Boolean
) {
    var isExpanded by remember { mutableStateOf(isStreaming) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) isExpanded = true
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF021722),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ClinicalTealBright.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧠", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreaming) "Clinical Diagnostic Reasoning..." else "Clinical Diagnostic Reasoning",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTealBright,
                    modifier = Modifier.weight(1f)
                )
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = ClinicalTealBright
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ClinicalTealBright.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = ClinicalTextSecondary
                        )
                    )
                }
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
    isStreaming: Boolean,
    currentStyle: ResponseStyle = ResponseStyle.DEFAULT,
    currentDepth: AnswerDepth = AnswerDepth.DEFAULT,
    onStyleSelected: (ResponseStyle) -> Unit = {},
    onDepthSelected: (AnswerDepth) -> Unit = {}
) {
    var showStyleDialog by remember { mutableStateOf(false) }
    var showDepthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Clinical Intelligence Control Bar: Response Style & Answer Depth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Style Selector Pill
            Surface(
                onClick = { showStyleDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = MedicalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎓 Style: ${currentStyle.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ClinicalTealBright,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Style",
                        tint = ClinicalTealBright.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Depth Selector Pill
            Surface(
                onClick = { showDepthDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = MedicalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Depth: ${currentDepth.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BioEmerald,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Depth",
                        tint = BioEmerald.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Input Surface & Text Field
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MedicalSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF042F2E),
                    modifier = Modifier.padding(end = 4.dp)
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

    // Response Style Picker Dialog
    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = {
                Text(
                    "Select Response Style",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResponseStyle.entries.forEach { style ->
                        val isSelected = style == currentStyle
                        Surface(
                            onClick = {
                                onStyleSelected(style)
                                showStyleDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF063342) else MedicalSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ClinicalTealBright else MedicalSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) ClinicalTealBright else ClinicalTextPrimary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ClinicalTealBright,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = style.shortDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ClinicalTextSecondary,
                                    fontSize = 11.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Audience: ${style.targetAudience}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ClinicalTextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStyleDialog = false }) {
                    Text("Close", color = ClinicalTealBright)
                }
            },
            containerColor = MedicalSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Answer Depth Picker Dialog
    if (showDepthDialog) {
        AlertDialog(
            onDismissRequest = { showDepthDialog = false },
            title = {
                Text(
                    "Select Answer Depth",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnswerDepth.entries.forEach { depth ->
                        val isSelected = depth == currentDepth
                        Surface(
                            onClick = {
                                onDepthSelected(depth)
                                showDepthDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF042F2E) else MedicalSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BioEmerald else MedicalSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = depth.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) BioEmerald else ClinicalTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF063342)
                                    ) {
                                        Text(
                                            text = "${depth.tokenBudget} tokens",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = ClinicalTealBright,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = BioEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = depth.shortDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ClinicalTextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDepthDialog = false }) {
                    Text("Close", color = ClinicalTealBright)
                }
            },
            containerColor = MedicalSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Futuristic Clinical-Obsidian Empty State
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyMedicalChatPlaceholder(
    activeModel: com.agentx.medical.domain.models.GGUFModel?,
    modelStatus: com.agentx.medical.core.inference.ModelStatus,
    currentStyle: ResponseStyle = ResponseStyle.DEFAULT,
    currentDepth: AnswerDepth = AnswerDepth.DEFAULT,
    onQuickPrompt: (String) -> Unit = {},
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

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "AgentX Medical",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = ClinicalTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Evidence-Based Clinical Decision Support",
            style = MaterialTheme.typography.bodyMedium,
            color = ClinicalTextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Medimind Status Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF042F2E),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalTealBright.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (modelStatus.state == ModelLifecycleState.LOADED) BioEmerald else ClinicalAlert)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MediMind v2 • Q5_K_M • ${if (modelStatus.state == ModelLifecycleState.LOADED) "Engine Loaded" else "Ready"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTealBright,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Preference Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MedicalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder)
            ) {
                Text(
                    "🎓 ${currentStyle.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClinicalTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MedicalSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder)
            ) {
                Text(
                    "📊 ${currentDepth.title} (${currentDepth.tokenBudget}t)",
                    style = MaterialTheme.typography.labelSmall,
                    color = BioEmerald,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Open Spacious Clinical Suggestion Cards (Core Clinical Benchmarks)
        Column(
            modifier = Modifier.fillMaxWidth(0.94f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "🫁 Acute COPD Exacerbation Management" to "Detail the comprehensive stepped management of acute exacerbation of COPD with respiratory acidosis, including oxygen titration, bronchodilators, systemic steroids, antibiotics, and indications for non-invasive ventilation (NIV).",
                "🩸 Diabetic Ketoacidosis (DKA) Protocol" to "Provide the acute resuscitation protocol for adult diabetic ketoacidosis (DKA): fluid resuscitation regimen, fixed-rate IV insulin infusion titration, potassium replacement thresholds, and criteria for resolution.",
                "🩺 Acute Kidney Injury (AKI) Workup & Staging" to "Outline the KDIGO staging criteria, diagnostic workup, and acute management of Acute Kidney Injury with hyperkalemia.",
                "👶 Postpartum Haemorrhage (PPH) Algorithm" to "Detail the emergency resuscitation and 4T stepwise pharmacological (oxytocin, ergometrine, carboprost, misoprostol) and mechanical management of primary postpartum haemorrhage.",
                "🔪 Acute Appendicitis Clinical Workup" to "Explain the Alvarado scoring system, differential diagnosis by age/sex, and surgical workup for suspected acute appendicitis.",
                "💊 ACE Inhibitors: Mechanisms & Adverse Effects" to "Explain the mechanism of action, hemodynamic renal effects, clinical indications, adverse effects (dry cough, angioedema), and contraindications of ACE inhibitors."
            )
            suggestions.forEach { (label, prompt) ->
                Surface(
                    onClick = { onQuickPrompt(prompt) },
                    shape = RoundedCornerShape(12.dp),
                    color = MedicalSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MedicalSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ClinicalTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ClinicalTealBright.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
