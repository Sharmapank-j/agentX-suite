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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
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

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp-inspired color palette (private to this file)
// ─────────────────────────────────────────────────────────────────────────────
private val WA_BG = Color(0xFF0B141A)
private val WA_SURFACE = Color(0xFF1F2C34)
private val WA_USER_BUBBLE = Color(0xFF005C4B)
private val WA_BOT_BUBBLE = Color(0xFF1F2C34)
private val WA_INPUT_BG = Color(0xFF1F2C34)
private val WA_BORDER = Color(0xFF2A3942)
private val WA_ACCENT = Color(0xFF00A884)
private val WA_TEXT_PRIMARY = Color(0xFFE9EDF0)
private val WA_TEXT_SECONDARY = Color(0xFF8696A0)
private val WA_TIMESTAMP = Color(0xFF8696A0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val isImeVisible = WindowInsets.isImeVisible

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

    // Auto-scroll after keyboard animation settles (300ms delay) when IME becomes visible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && state.messages.isNotEmpty()) {
            delay(300)
            listState.scrollToItem(state.messages.size - 1)
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

    // ── WhatsApp-style Drawer ─────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = WA_SURFACE,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer Header — green gradient avatar + app name
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(WA_ACCENT.copy(alpha = 0.85f), WA_SURFACE)))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(WA_ACCENT, Color(0xFF007A63)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "X",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "AgentX Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Autonomous Multi-Agent AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Chat Button — WhatsApp green
                Surface(
                    onClick = {
                        viewModel.startNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = WA_ACCENT
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Conversation",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "New Conversation",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = WA_BORDER, modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = WA_TEXT_SECONDARY,
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
                                    color = if (isSelected) WA_ACCENT else WA_TEXT_PRIMARY,
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
                                        tint = if (isSelected) WA_ACCENT.copy(alpha = 0.7f) else WA_TEXT_SECONDARY,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = WA_ACCENT.copy(alpha = 0.12f),
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .heightIn(min = 56.dp)
                        )
                    }
                }

                // Drawer Bottom — model name + status row
                HorizontalDivider(color = WA_BORDER, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.activeModel != null) WA_ACCENT else WA_TEXT_SECONDARY)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.activeModel?.name ?: "No model loaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = WA_TEXT_SECONDARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (state.activeModel != null) "Active" else "Idle",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.activeModel != null) WA_ACCENT else WA_TEXT_SECONDARY,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                WhatsAppTopBar(
                    conversationTitle = state.currentConversation?.title ?: "AgentX Chat",
                    activeModel = state.activeModel,
                    modelStatus = state.modelStatus,
                    gatewayHealth = state.gatewayHealth,
                    cloudConfig = state.cloudConfig,
                    isImeVisible = isImeVisible,
                    onTitleClick = {
                        renameText = state.currentConversation?.title ?: ""
                        showRenameDialog = true
                    },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onModelBadgeClick = { showModelSwitcherSheet = true },
                    onNewChatClick = { viewModel.startNewConversation() },
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
                // FIX 1: imePadding here on the bottom bar drives keyboard avoidance
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
                        // Live Inference Streaming Metrics Bar
                        if (state.liveMetrics.isLive || (state.isStreaming && state.liveMetrics.tokensGenerated > 0)) {
                            LiveMetricsHud(metrics = state.liveMetrics)
                        }

                        // WhatsApp-style Input Bar
                        ChatInputBar(
                            text = state.inputText,
                            onTextChanged = viewModel::onInputTextChanged,
                            onSend = viewModel::sendMessage,
                            onStop = viewModel::stopGeneration,
                            isStreaming = state.isStreaming,
                            isMultimodalCapable = state.isMultimodalCapable,
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
                            },
                            onShowModelSwitcher = { showModelSwitcherSheet = true }
                        )
                    }
                }
            },
            // FIX 1: contentWindowInsets = WindowInsets(0) prevents double-padding
            contentWindowInsets = WindowInsets(0),
            containerColor = WA_BG,
            modifier = modifier
        ) { paddingValues ->
            // FIX 1: imePadding on content column ensures list scrolls above keyboard
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                val screenWidth = maxWidth
                // FIX 5: Adaptive layout breakpoints
                val (horizontalPadding, contentMaxWidth) = when {
                    screenWidth < 360.dp -> Pair(12.dp, screenWidth)
                    screenWidth in 360.dp..600.dp -> Pair(8.dp, screenWidth)
                    screenWidth in 600.dp..840.dp -> Pair(72.dp, screenWidth)
                    else -> Pair(0.dp, 680.dp)
                }

                val contentModifier = if (screenWidth > 840.dp) {
                    Modifier.widthIn(max = contentMaxWidth).fillMaxHeight()
                } else if (screenWidth in 600.dp..840.dp) {
                    Modifier.padding(horizontal = horizontalPadding).fillMaxSize()
                } else {
                    Modifier.fillMaxSize()
                }

                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.messages.isEmpty()) {
                        EmptyChatPlaceholder(
                            activeModel = state.activeModel,
                            modelStatus = state.modelStatus,
                            inferenceMode = state.inferenceMode,
                            cloudConfig = state.cloudConfig,
                            isMultimodalCapable = state.isMultimodalCapable,
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
                                .padding(
                                    horizontal = if (screenWidth >= 600.dp) 32.dp else 16.dp,
                                    vertical = 24.dp
                                )
                        )
                    } else {
                        // "Today" date separator header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WA_SURFACE.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WA_TEXT_SECONDARY,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // FIX 4: Smart vertical spacing between messages
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = if (screenWidth >= 600.dp) 24.dp else horizontalPadding),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 16.dp,
                                start = if (screenWidth < 360.dp) 4.dp else 8.dp,
                                end = if (screenWidth < 360.dp) 4.dp else 8.dp
                            ),
                            reverseLayout = false
                        ) {
                            val messages = state.messages
                            items(messages.size) { index ->
                                val message = messages[index]
                                val prevMessage = if (index > 0) messages[index - 1] else null
                                val isSameRoleAsPrev = prevMessage?.role == message.role
                                // Smart grouping: same-role = 3dp, role-change = 10dp
                                val spacingAbove = if (isSameRoleAsPrev) 3.dp else 10.dp
                                if (index > 0) Spacer(modifier = Modifier.height(spacingAbove))
                                ChatMessageItem(
                                    message = message,
                                    onRegenerate = { viewModel.regenerateResponse(message.id) },
                                    onDelete = { viewModel.deleteMessage(message.id) },
                                    isStreamingActive = state.isStreaming
                                )
                            }
                            if (state.isStreaming) {
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    StreamingIndicator(agentName = state.streamingAgentName ?: "AgentX")
                                }
                            }
                        }
                    }
                }

                // Floating Smart Auto-Scroll Pill
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
                        color = WA_SURFACE,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                tint = WA_ACCENT,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isStreaming) "⚡ Live Tokens ▾" else "Scroll to latest ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = WA_ACCENT,
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
            containerColor = WA_SURFACE,
            title = {
                Text("Rename Conversation", color = WA_TEXT_PRIMARY, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Conversation Title", color = WA_TEXT_SECONDARY) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WA_ACCENT,
                        unfocusedBorderColor = WA_BORDER,
                        focusedTextColor = WA_TEXT_PRIMARY,
                        unfocusedTextColor = WA_TEXT_PRIMARY
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
                    Text("Save", color = WA_ACCENT, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = WA_TEXT_SECONDARY)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = WA_SURFACE,
            title = { Text("Delete Conversation?", color = WA_TEXT_PRIMARY, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove this conversation and its multi-agent telemetry from on-device storage.", color = WA_TEXT_SECONDARY) },
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
                    Text("Cancel", color = WA_TEXT_SECONDARY)
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
// WhatsApp-style Top App Bar
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsAppTopBar(
    conversationTitle: String,
    activeModel: GGUFModel?,
    modelStatus: ModelStatus? = null,
    gatewayHealth: com.agentx.core.network.GatewayHeartbeatMonitor.GatewayHealthState? = null,
    cloudConfig: CloudModelConfig? = null,
    isImeVisible: Boolean = false,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onModelBadgeClick: () -> Unit,
    onNewChatClick: () -> Unit = {},
    onNavigateToMonitor: () -> Unit,
    onRenameClick: () -> Unit,
    onSwitchModelClick: () -> Unit,
    onClearMessagesClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isCustomGw = cloudConfig?.provider == CloudProvider.CUSTOM_GATEWAY
    val gwStatus = gatewayHealth?.status
    val statusDotColor = when {
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.CONNECTED -> WA_ACCENT
        isCustomGw && (gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.CONNECTING || gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.DEGRADED) -> Color(0xFFF59E0B)
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.OFFLINE -> Color(0xFFEF4444)
        else -> WA_ACCENT
    }
    val statusLabel = when {
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.CONNECTED -> {
            val lat = gatewayHealth?.latencyMs?.let { " • ${it}ms" } ?: ""
            "Connected$lat"
        }
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.CONNECTING -> "Reconnecting..."
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.DEGRADED -> "High Latency • ${gatewayHealth?.latencyMs}ms"
        isCustomGw && gwStatus == com.agentx.core.network.GatewayHeartbeatMonitor.ConnectionStatus.OFFLINE -> "Offline (Local Mode)"
        else -> activeModel?.name?.take(22) ?: "Online"
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onTitleClick() }
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(WA_ACCENT, Color(0xFF007A63)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "X",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = conversationTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = WA_TEXT_PRIMARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusDotColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = WA_TEXT_PRIMARY)
            }
        },
        actions = {
            // Model Pill Badge
            val isLoadedPill = activeModel != null &&
                (modelStatus?.state == ModelLifecycleState.LOADED || modelStatus?.state == ModelLifecycleState.GENERATING)
            Surface(
                onClick = onModelBadgeClick,
                shape = RoundedCornerShape(12.dp),
                color = WA_BORDER,
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isLoadedPill) WA_ACCENT else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isLoadedPill) "${activeModel?.name?.take(10) ?: "Engine"} (RAM)"
                               else (activeModel?.name?.take(10) ?: "llama.cpp"),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WA_TEXT_PRIMARY
                    )
                }
            }

            // Functional New Chat Quick Action
            IconButton(onClick = onNewChatClick) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation", tint = WA_ACCENT)
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = WA_TEXT_PRIMARY)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(WA_SURFACE)
                ) {
                    DropdownMenuItem(
                        text = { Text("New Conversation", color = WA_ACCENT) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = WA_ACCENT) },
                        onClick = {
                            showMenu = false
                            onNewChatClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Execution Monitor", color = WA_TEXT_PRIMARY) },
                        leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = WA_ACCENT) },
                        onClick = {
                            showMenu = false
                            onNavigateToMonitor()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Conversation", color = WA_TEXT_PRIMARY) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = WA_ACCENT) },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Switch Model", color = WA_TEXT_PRIMARY) },
                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = WA_ACCENT) },
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
            containerColor = WA_SURFACE
        )
    )
}

// Legacy alias kept for any external callers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyberTopAppBar(
    conversationTitle: String,
    activeModel: GGUFModel?,
    modelStatus: ModelStatus? = null,
    gatewayHealth: com.agentx.core.network.GatewayHeartbeatMonitor.GatewayHealthState? = null,
    cloudConfig: CloudModelConfig? = null,
    onTitleClick: () -> Unit,
    onMenuClick: () -> Unit,
    onModelBadgeClick: () -> Unit,
    onNavigateToMonitor: () -> Unit,
    onRenameClick: () -> Unit,
    onSwitchModelClick: () -> Unit,
    onClearMessagesClick: () -> Unit
) = WhatsAppTopBar(
    conversationTitle = conversationTitle,
    activeModel = activeModel,
    modelStatus = modelStatus,
    gatewayHealth = gatewayHealth,
    cloudConfig = cloudConfig,
    isImeVisible = false,
    onTitleClick = onTitleClick,
    onMenuClick = onMenuClick,
    onModelBadgeClick = onModelBadgeClick,
    onNavigateToMonitor = onNavigateToMonitor,
    onRenameClick = onRenameClick,
    onSwitchModelClick = onSwitchModelClick,
    onClearMessagesClick = onClearMessagesClick
)

// Live Metrics Streaming HUD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LiveMetricsHud(metrics: LiveInferenceMetrics) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = WA_SURFACE,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(WA_ACCENT)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "⚡ ${String.format("%.1f", metrics.tokensPerSecond)} t/s • ${metrics.tokensGenerated} tokens • ${String.format("%.1f", metrics.elapsedSeconds)}s",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = WA_TEXT_PRIMARY
            )
            if (metrics.memoryUsageMb > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "• ${metrics.memoryUsageMb} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = WA_ACCENT
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
        color = WA_SURFACE,
        shape = RoundedCornerShape(10.dp),
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
                        .background(if (isLoaded) WA_ACCENT else Color(0xFFFF9800))
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
                        color = WA_TEXT_PRIMARY
                    )
                    Text(
                        text = if (isLoaded) {
                            "${activeModel?.threads ?: 4} Threads • ${activeModel?.contextLength ?: 2048} Context Tokens"
                        } else {
                            "Ready for On-Demand Offline Inference"
                        },
                        fontSize = 10.sp,
                        color = WA_TEXT_SECONDARY
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
                        Text("Load", fontSize = 11.sp, color = WA_ACCENT, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Switch ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = WA_ACCENT,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSwitchModelClick() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp-inspired Empty State (spacious & calm)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyChatPlaceholder(
    activeModel: GGUFModel?,
    modelStatus: ModelStatus,
    inferenceMode: InferenceMode = InferenceMode.LOCAL_ONLY,
    cloudConfig: CloudModelConfig = CloudModelConfig(),
    isMultimodalCapable: Boolean = false,
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
        Spacer(modifier = Modifier.height(24.dp))
        // Glowing Hero Orbital Emblem
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                WA_ACCENT.copy(alpha = auraAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(WA_ACCENT, Color(0xFF007A63)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "AgentX Intelligence",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = WA_TEXT_PRIMARY
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "What can I help you explore today?",
            style = MaterialTheme.typography.bodyLarge,
            color = WA_TEXT_SECONDARY
        )

        // Capability pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isMultimodalCapable) WA_ACCENT.copy(alpha = 0.12f) else WA_BORDER,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isMultimodalCapable) WA_ACCENT else WA_TEXT_SECONDARY)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMultimodalCapable) "👁️ Multimodal Vision Active • Image reasoning ready" else "💬 Text Reasoning Active • Fast local intelligence",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMultimodalCapable) WA_ACCENT else WA_TEXT_SECONDARY,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Prompt Suggestions
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val quickSuggestions = if (isMultimodalCapable) {
                listOf(
                    "💡 Brainstorm creative ideas" to "Brainstorm 5 innovative and high-impact ideas for my project.",
                    "💻 Write & analyze code" to "Write a clean, performant, and production-ready solution with clear explanations.",
                    "🖼️ Multimodal visual analysis" to "Analyze this image and explain the concepts and details in depth.",
                    "🔍 Deep reasoning & exploration" to "Walk me step-by-step through the core principles and architecture of this topic."
                )
            } else {
                listOf(
                    "💡 Brainstorm creative ideas" to "Brainstorm 5 innovative and high-impact ideas for my project.",
                    "💻 Write & analyze code" to "Write a clean, performant, and production-ready solution with clear explanations.",
                    "📊 Analyze documents & insights" to "Analyze the key insights, findings, and actionable takeaways from this data.",
                    "🔍 Deep reasoning & exploration" to "Walk me step-by-step through the core principles and architecture of this topic."
                )
            }
            quickSuggestions.forEach { (label, prompt) ->
                Surface(
                    onClick = { onQuickPrompt(prompt) },
                    shape = RoundedCornerShape(14.dp),
                    color = WA_SURFACE
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = WA_TEXT_PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = WA_ACCENT.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp-style Chat Message Item
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

    // Format timestamp — show time from message or current time
    val timestamp = remember(message.id) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        try {
            if (message.timestamp > 0) sdf.format(java.util.Date(message.timestamp))
            else sdf.format(java.util.Date())
        } catch (_: Exception) {
            sdf.format(java.util.Date())
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Multimodal Attached Image Display
        if (!message.attachmentUri.isNullOrBlank()) {
            val imgBitmap = rememberImageBitmapFromPath(message.attachmentUri)
            if (imgBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WA_BOT_BUBBLE,
                    modifier = Modifier
                        .fillMaxWidth(if (isUser) 0.85f else 0.90f)
                        .padding(bottom = 4.dp)
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
                                tint = WA_ACCENT,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Multimodal Vision Input",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WA_ACCENT
                            )
                        }
                    }
                }
            }
        }

        // WhatsApp-style Message Bubble — roomy, sleek, zero vertical clutter
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = if (isUser) WA_USER_BUBBLE else WA_BOT_BUBBLE,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.92f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Message content
                if (isUser) {
                    Text(
                        text = message.content,
                        color = WA_TEXT_PRIMARY,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        textColor = WA_TEXT_PRIMARY
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp & Context Actions Footer Row (inside bubble, WhatsApp style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Assistant footer: token stats & quick discrete actions
                    if (!isUser) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (message.tokenCount > 0) {
                                val statsText = buildString {
                                    append("⚡ ${message.tokenCount} tok")
                                    message.tokensPerSecond?.takeIf { it > 0 }?.let {
                                        append(" • %.1f t/s".format(it))
                                    }
                                }
                                Text(
                                    text = statsText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = WA_TIMESTAMP.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Discreet Copy icon
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    isCopied = true
                                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                        context,
                                        com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                                    )
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = if (isCopied) WA_ACCENT else WA_TIMESTAMP.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // Discreet Regenerate icon
                            if (!isStreamingActive) {
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = onRegenerate,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        tint = WA_TIMESTAMP.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Discreet Delete icon
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = WA_TIMESTAMP.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Timestamp and delivery checks
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUser) {
                            // User message quick copy
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    isCopied = true
                                    com.agentx.core.device.HapticFeedbackHelper.performHaptic(
                                        context,
                                        com.agentx.core.device.HapticFeedbackType.LIGHT_TICK
                                    )
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy text",
                                    tint = if (isCopied) WA_ACCENT else WA_TIMESTAMP.copy(alpha = 0.6f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = WA_TIMESTAMP
                        )

                        // Double-tick for user messages
                        if (isUser) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "✓✓",
                                fontSize = 10.sp,
                                color = WA_ACCENT,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streaming Indicator with Pulsing Cursor
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
        color = WA_BOT_BUBBLE.copy(alpha = alpha),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = WA_ACCENT
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$agentName is generating response...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = WA_TEXT_SECONDARY
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsApp-style Input Bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    isMultimodalCapable: Boolean = false,
    activeModelName: String = "llama.cpp",
    attachedImagePath: String? = null,
    onRemoveAttachment: () -> Unit = {},
    onPickGallery: () -> Unit = {},
    onCaptureCamera: () -> Unit = {},
    onShowModelSwitcher: () -> Unit = {}
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        color = WA_BG,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle separator line
            HorizontalDivider(color = WA_BORDER, thickness = 0.5.dp)

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {

                // Attached Media Preview Capsule
                AnimatedVisibility(
                    visible = isMultimodalCapable && attachedImagePath != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (attachedImagePath != null) {
                        val bitmap = rememberImageBitmapFromPath(attachedImagePath)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = WA_SURFACE,
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
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = WA_ACCENT,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Multimodal Vision Input",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WA_ACCENT
                                    )
                                    Text(
                                        text = File(attachedImagePath).name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = WA_TEXT_SECONDARY,
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

                // Main WhatsApp-style pill input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Pill text field container
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = WA_INPUT_BG,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attachment icon (paperclip) on left — always visible
                            Box {
                                IconButton(
                                    onClick = {
                                        if (isMultimodalCapable) showAttachmentMenu = true
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (attachedImagePath != null) Icons.Default.Image else Icons.Default.AttachFile,
                                        contentDescription = "Attach media",
                                        tint = if (attachedImagePath != null) WA_ACCENT
                                               else if (isMultimodalCapable) WA_TEXT_SECONDARY
                                               else WA_TEXT_SECONDARY.copy(alpha = 0.4f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                if (isMultimodalCapable) {
                                    DropdownMenu(
                                        expanded = showAttachmentMenu,
                                        onDismissRequest = { showAttachmentMenu = false },
                                        modifier = Modifier.background(WA_SURFACE)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Photo Library / Gallery", color = WA_TEXT_PRIMARY) },
                                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = WA_ACCENT) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                onPickGallery()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Take Camera Photo", color = WA_TEXT_PRIMARY) },
                                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = WA_ACCENT) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                onCaptureCamera()
                                            }
                                        )
                                    }
                                }
                            }

                            // Text input — center, expands naturally
                            OutlinedTextField(
                                value = text,
                                onValueChange = onTextChanged,
                                placeholder = {
                                    Text(
                                        text = when {
                                            attachedImagePath != null -> "Ask about this image..."
                                            isMultimodalCapable -> "Ask AgentX with text or image..."
                                            else -> "Ask AgentX anything..."
                                        },
                                        color = WA_TEXT_SECONDARY,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                    focusedTextColor = WA_TEXT_PRIMARY,
                                    unfocusedTextColor = WA_TEXT_PRIMARY,
                                    cursorColor = WA_ACCENT
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circular send/stop button (WA_ACCENT green, 48dp)
                    val canSend = text.isNotBlank() || (isMultimodalCapable && attachedImagePath != null)

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
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AlertRose)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Generation",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
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
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (canSend) WA_ACCENT else WA_BORDER)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else WA_TEXT_SECONDARY,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // FIX 6: Vision model info chip — only when not multimodal capable
                if (!isMultimodalCapable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        onClick = onShowModelSwitcher,
                        shape = RoundedCornerShape(12.dp),
                        color = WA_SURFACE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Load a vision model for image analysis",
                                style = MaterialTheme.typography.labelSmall,
                                color = WA_TEXT_SECONDARY,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = WA_ACCENT,
                                modifier = Modifier.size(14.dp)
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
