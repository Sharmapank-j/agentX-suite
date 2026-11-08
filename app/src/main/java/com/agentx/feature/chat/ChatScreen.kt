package com.agentx.feature.chat

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.InferenceMode
import com.agentx.core.multimodal.ImageUtils
import com.agentx.core.multimodal.rememberImageBitmapFromPath
import java.io.File
import java.io.FileOutputStream
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    val context = LocalContext.current

    // Auto-scroll on new message added
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Auto-scroll and tactile pulse on streaming content updates
    LaunchedEffect(state.messages.lastOrNull()?.content) {
        if (state.isStreaming) {
            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                context,
                com.agentx.core.device.HapticFeedbackType.STREAMING_TOKEN
            )
        }
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
    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var geminiKeyInput by remember(state.cloudConfig.geminiApiKey) { mutableStateOf(state.cloudConfig.geminiApiKey) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val localPath = ImageUtils.persistImageToStorage(context, it)
                    val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
                    viewModel.attachImage(localPath, mimeType)
                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                        context,
                        com.agentx.core.device.HapticFeedbackType.SUCCESS
                    )
                } catch (_: Exception) {}
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            scope.launch {
                try {
                    val attachmentsDir = File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
                    val photoFile = File(attachmentsDir, "camera_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    viewModel.attachImage(photoFile.absolutePath, "image/jpeg")
                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                        context,
                        com.agentx.core.device.HapticFeedbackType.SUCCESS
                    )
                } catch (_: Exception) {}
            }
        }
    }

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
                    modelStatus = state.modelStatus,
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
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Live Inference Streaming Metrics Bar
                    if (state.liveMetrics.isLive || (state.isStreaming && state.liveMetrics.tokensGenerated > 0)) {
                        LiveMetricsHud(metrics = state.liveMetrics)
                    }

                    // Modern Floating Pill Input Bar with Multimodal & Auto-Adjust
                    ChatInputBar(
                        text = state.inputText,
                        onTextChanged = viewModel::onInputTextChanged,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                        isStreaming = state.isStreaming,
                        activeModelName = when (state.inferenceMode) {
                            InferenceMode.CLOUD_ONLY -> "Cloud: ${state.cloudConfig.modelName}"
                            InferenceMode.HYBRID_AUTO -> "Hybrid: ${state.cloudConfig.modelName}"
                            InferenceMode.LOCAL_ONLY -> state.activeModel?.name ?: state.activeModel?.quantization ?: "Local GGUF (llama.cpp)"
                        },
                        attachedImagePath = state.attachedImagePath,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onPickGallery = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCaptureCamera = {
                            cameraLauncher.launch(null)
                        }
                    )
                }
            },
            containerColor = VoidDark,
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
                        EmptyChatPlaceholder(
                            activeModel = state.activeModel,
                            modelStatus = state.modelStatus,
                            inferenceMode = state.inferenceMode,
                            cloudConfig = state.cloudConfig,
                            onSwitchModelClick = { showModelSwitcherSheet = true },
                            onPickImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCaptureCamera = {
                                cameraLauncher.launch(null)
                            },
                            onQuickPrompt = { prompt ->
                                viewModel.onInputTextChanged(prompt)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    } else {
                        // Chat Message Feed with Smart Auto-Scroll
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

                // Floating Smart Auto-Scroll Pill (Never hijack user scrolling)
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
                        color = SurfaceGlassElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isStreaming) "⚡ Live Tokens ▾" else "Scroll to latest ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
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
        ModelSwitcherBottomSheet(
            state = state,
            onDismiss = { showModelSwitcherSheet = false },
            onSelectLocalModel = { modelId -> viewModel.switchActiveModel(modelId) },
            onSetInferenceMode = { mode -> viewModel.setInferenceMode(mode) },
            onUpdateCloudConfig = { config -> viewModel.updateCloudConfig(config) }
        )
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
// Futuristic Cyber-Obsidian Empty State
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyChatPlaceholder(
    activeModel: GGUFModel?,
    modelStatus: ModelStatus,
    inferenceMode: InferenceMode = InferenceMode.LOCAL_ONLY,
    cloudConfig: CloudModelConfig = CloudModelConfig(),
    onSwitchModelClick: () -> Unit,
    onPickImage: () -> Unit = {},
    onCaptureCamera: () -> Unit = {},
    onQuickPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Hero Orbital Emblem
        Box(contentAlignment = Alignment.Center) {
            // Outer Aura
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ElectricCyan.copy(alpha = auraAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Core Orb
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = VoidDark,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "AgentX Multimodal AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (inferenceMode == InferenceMode.CLOUD_ONLY) "Cloud Generative AI • Google Gemini 3.8 Flash Vision"
            else if (inferenceMode == InferenceMode.HYBRID_AUTO) "Hybrid Multimodal Reasoning • Cloud Vision + Offline GGUF"
            else "100% Offline On-Device LLM Inference • Upstream llama.cpp",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Hardware & Architecture Diagnostics Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceGlassElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Engine Status Pill
                val isLoaded = modelStatus.state == ModelLifecycleState.LOADED
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLoaded) CyberMint.copy(alpha = 0.15f) else AlertRose.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLoaded) CyberMint else AlertRose)
                    )
                    Text(
                        text = if (isLoaded) "Native Engine Online & Ready" else "Model Not Staged In RAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoaded) CyberMint else AlertRose
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (inferenceMode == InferenceMode.CLOUD_ONLY) "Google Gemini 3.8 Flash" else (activeModel?.name ?: "No model selected"),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (activeModel != null && inferenceMode == InferenceMode.LOCAL_ONLY) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeModel.quantization} • ${activeModel.architecture} • ${activeModel.contextLength} Tokens Context",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hardware Topology Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SpaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("MULTIMODAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Text("Vision & Photos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ElectricCyan)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SpaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SOVEREIGNTY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Text("Air-Gapped Ready", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyberMint)
                        }
                    }
                }
            }
        }

        // Multimodal Action Shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onPickImage,
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGlass,
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Image", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            Surface(
                onClick = onCaptureCamera,
                shape = RoundedCornerShape(12.dp),
                color = SurfaceGlass,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberMint.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CyberMint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Snap Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        // Generative AI Quick Prompts
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickPrompts = listOf(
                "🔍 Explain image" to "Analyze and describe key insights from this visual data in detail.",
                "📝 Extract text" to "Extract and transcribe all text, labels, and numbers from this document.",
                "💻 Code solution" to "Write clean, production-grade code that implements this feature or design."
            )
            quickPrompts.forEach { (label, prompt) ->
                Surface(
                    onClick = { onQuickPrompt(prompt) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0E2A47),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Elevated Chat Message Item
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
    val context = LocalContext.current
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
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = VoidDark,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AgentX",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonIndigo
                )
                if (message.tokenCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0E2A47),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "⚡ ${message.tokenCount} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Multimodal Attached Image Display
        if (!message.attachmentUri.isNullOrBlank()) {
            val imgBitmap = rememberImageBitmapFromPath(message.attachmentUri)
            if (imgBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF091424),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.88f else 0.94f)
                        .padding(bottom = 6.dp)
                ) {
                    Column {
                        Image(
                            bitmap = imgBitmap,
                            contentDescription = "Attached Multimodal Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Multimodal Vision Input",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }

        // Message Bubble with Asymmetric Geometry
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) Color(0xFF0F2B48) else SurfaceGlass,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) ElectricCyan.copy(alpha = 0.35f) else SurfaceBorder
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.94f)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
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
                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                        context,
                        com.agentx.core.device.HapticFeedbackType.SUCCESS
                    )
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    tint = if (isCopied) CyberMint else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (isCopied) {
                Text(
                    text = "Copied",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = CyberMint,
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
                        tint = if (!isStreamingActive) TextMuted else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                        context,
                        com.agentx.core.device.HapticFeedbackType.WARNING
                    )
                    onDelete()
                },
                modifier = Modifier.size(28.dp)
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
// Floating Pill Input Bar with Multimodal Attachment Capsule & Glowing Neon Send
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    activeModelName: String = "llama.cpp",
    attachedImagePath: String? = null,
    onRemoveAttachment: () -> Unit = {},
    onPickGallery: () -> Unit = {},
    onCaptureCamera: () -> Unit = {}
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        color = VoidDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Attached Media Preview Capsule
            AnimatedVisibility(
                visible = attachedImagePath != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (attachedImagePath != null) {
                    val bitmap = rememberImageBitmapFromPath(attachedImagePath)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0C243B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Attached thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multimodal Vision Input",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = File(attachedImagePath).name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                        context,
                                        com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                                    )
                                    onRemoveAttachment()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove attachment",
                                    tint = AlertRose,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Input Pill
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceGlassElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Picker Button with Dropdown Menu
                    Box {
                        IconButton(
                            onClick = { showAttachmentMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (attachedImagePath != null) Icons.Default.Image else Icons.Default.AttachFile,
                                contentDescription = "Attach media",
                                tint = if (attachedImagePath != null) ElectricCyan else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false },
                            modifier = Modifier.background(SpaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Photo Library / Gallery", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = ElectricCyan) },
                                onClick = {
                                    showAttachmentMenu = false
                                    onPickGallery()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Take Camera Photo", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CyberMint) },
                                onClick = {
                                    showAttachmentMenu = false
                                    onCaptureCamera()
                                }
                            )
                        }
                    }

                    // Quantization / Model Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0E2A47),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = activeModelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Text Input
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        placeholder = {
                            Text(
                                if (attachedImagePath != null) "Ask about this image..." else "Ask AgentX anything...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
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

                    val canSend = text.isNotBlank() || attachedImagePath != null

                    if (isStreaming) {
                        IconButton(
                            onClick = {
                                com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                    context,
                                    com.agentx.core.device.HapticFeedbackType.WARNING
                                )
                                onStop()
                            },
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
                            onClick = {
                                com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                    context,
                                    com.agentx.core.device.HapticFeedbackType.CLICK
                                )
                                onSend()
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))
                                    else Brush.linearGradient(listOf(SurfaceBorder, SurfaceBorder))
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) VoidDark else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Futuristic Model & Multimodal Provider Hub Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSwitcherBottomSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onSelectLocalModel: (String) -> Unit,
    onSetInferenceMode: (InferenceMode) -> Unit,
    onUpdateCloudConfig: (CloudModelConfig) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableStateOf(
            when {
                state.inferenceMode == InferenceMode.HYBRID_AUTO -> "hybrid"
                state.inferenceMode == InferenceMode.CLOUD_ONLY && state.cloudConfig.provider == CloudProvider.CUSTOM_GATEWAY -> "gateway"
                state.inferenceMode == InferenceMode.CLOUD_ONLY -> "gemini"
                else -> "local"
            }
        )
    }

    var geminiKeyInput by remember(state.cloudConfig.geminiApiKey) {
        mutableStateOf(state.cloudConfig.geminiApiKey)
    }
    var geminiModelSelected by remember(state.cloudConfig.modelName) {
        mutableStateOf(if (state.cloudConfig.modelName.startsWith("gemini")) state.cloudConfig.modelName else "gemini-3.8-flash")
    }
    var showGeminiKey by remember { mutableStateOf(false) }

    var gatewayUrlInput by remember(state.cloudConfig.customBaseUrl) {
        mutableStateOf(state.cloudConfig.customBaseUrl)
    }
    var gatewayModelInput by remember(state.cloudConfig.modelName) {
        mutableStateOf(if (!state.cloudConfig.modelName.startsWith("gemini")) state.cloudConfig.modelName else "llama-3-8b")
    }
    var gatewayKeyInput by remember(state.cloudConfig.gatewayApiKey) {
        mutableStateOf(state.cloudConfig.gatewayApiKey)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpaceDark,
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = VoidDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Inference & Provider Hub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Switch between offline GGUF, Gemini Multimodal & LAN Gateway",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Tabs (Horizontal Scroll)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    ProviderTabChip(
                        title = "Local GGUF",
                        subtitle = "Offline llama.cpp",
                        isSelected = selectedTab == "local",
                        onClick = {
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                            )
                            selectedTab = "local"
                        }
                    )
                }
                item {
                    ProviderTabChip(
                        title = "Gemini Cloud",
                        subtitle = "Multimodal Vision",
                        isSelected = selectedTab == "gemini",
                        onClick = {
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                            )
                            selectedTab = "gemini"
                        }
                    )
                }
                item {
                    ProviderTabChip(
                        title = "Hybrid Auto",
                        subtitle = "Failover to Edge",
                        isSelected = selectedTab == "hybrid",
                        onClick = {
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                            )
                            selectedTab = "hybrid"
                        }
                    )
                }
                item {
                    ProviderTabChip(
                        title = "LAN Gateway",
                        subtitle = "Server Gateway",
                        isSelected = selectedTab == "gateway",
                        onClick = {
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                            )
                            selectedTab = "gateway"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Tab Panels
            when (selectedTab) {
                "local" -> {
                    Text(
                        text = "Select Local GGUF Model",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Executes 100% privately on device CPU/NPU without internet connection.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.allModels.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AlertRose.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRose.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "No local models imported yet. Go to the Models tab to download or import GGUF models.",
                                color = AlertRose,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(state.allModels) { model ->
                                val isSelected = state.inferenceMode == InferenceMode.LOCAL_ONLY && model.id == state.activeModel?.id
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
                                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                                context,
                                                com.agentx.core.device.HapticFeedbackType.SUCCESS
                                            )
                                            onSetInferenceMode(InferenceMode.LOCAL_ONLY)
                                            onSelectLocalModel(model.id)
                                            onDismiss()
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
                }

                "gemini" -> {
                    Text(
                        text = "Google Gemini Multimodal Vision",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "State-of-the-art vision reasoning, document OCR, chart analysis & coding.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Model selection
                    Text(
                        text = "Choose Multimodal Model:",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    CloudModelConfig.AVAILABLE_GEMINI_MODELS.forEach { (modelId, desc) ->
                        val isChosen = geminiModelSelected == modelId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChosen) Color(0xFF0C2742) else SurfaceGlass,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChosen) ElectricCyan else SurfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { geminiModelSelected = modelId }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isChosen) Icons.Default.Check else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isChosen) ElectricCyan else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = modelId,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isChosen) ElectricCyan else TextPrimary
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key Field
                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        label = { Text("Gemini API Key (AI Studio)", color = TextMuted) },
                        placeholder = { Text("AIzaSy...", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.Close else Icons.Default.Check,
                                    contentDescription = "Toggle key display",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Activate Button
                    Surface(
                        onClick = {
                            val updated = state.cloudConfig.copy(
                                provider = CloudProvider.GEMINI,
                                geminiApiKey = geminiKeyInput.trim(),
                                apiKey = geminiKeyInput.trim(),
                                modelName = geminiModelSelected,
                                isEnabled = true
                            )
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.SUCCESS
                            )
                            onUpdateCloudConfig(updated)
                            onSetInferenceMode(InferenceMode.CLOUD_ONLY)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricCyan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = VoidDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Activate Gemini Multimodal",
                                fontWeight = FontWeight.Bold,
                                color = VoidDark,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                "hybrid" -> {
                    Text(
                        text = "Hybrid Auto-Failover Engine",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Combines cloud reasoning power with 100% offline edge privacy. When an internet connection is available, queries utilize Gemini Multimodal. If connectivity drops or latency spikes, AgentX automatically fails over to your local GGUF model without interrupting your conversation.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        onClick = {
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.SUCCESS
                            )
                            onSetInferenceMode(InferenceMode.HYBRID_AUTO)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = CyberMint,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = VoidDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Activate Hybrid Auto-Failover",
                                fontWeight = FontWeight.Bold,
                                color = VoidDark,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                "gateway" -> {
                    Text(
                        text = "AgentX LAN Server / OpenAI Gateway",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Connect to your local workstation, desktop LLM server (vLLM, Ollama, llama.cpp server, AgentX Serve).",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = gatewayUrlInput,
                        onValueChange = { gatewayUrlInput = it },
                        label = { Text("Gateway Base URL", color = TextMuted) },
                        placeholder = { Text("http://192.168.1.100:8080/v1", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = gatewayModelInput,
                        onValueChange = { gatewayModelInput = it },
                        label = { Text("Model Identifier", color = TextMuted) },
                        placeholder = { Text("llama-3-8b-instruct", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = gatewayKeyInput,
                        onValueChange = { gatewayKeyInput = it },
                        label = { Text("Bearer Token / API Key (Optional)", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        onClick = {
                            val updated = state.cloudConfig.copy(
                                provider = CloudProvider.CUSTOM_GATEWAY,
                                customBaseUrl = gatewayUrlInput.trim(),
                                modelName = gatewayModelInput.trim(),
                                gatewayApiKey = gatewayKeyInput.trim(),
                                apiKey = gatewayKeyInput.trim(),
                                isEnabled = true
                            )
                            com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                context,
                                com.agentx.core.device.HapticFeedbackType.SUCCESS
                            )
                            onUpdateCloudConfig(updated)
                            onSetInferenceMode(InferenceMode.CLOUD_ONLY)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = NeonIndigo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Connect to LAN Gateway",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProviderTabChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF0F2E4F) else SurfaceGlass,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ElectricCyan else SurfaceBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isSelected) ElectricCyan else TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}
