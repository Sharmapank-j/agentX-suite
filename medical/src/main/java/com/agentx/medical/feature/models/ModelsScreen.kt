package com.agentx.medical.feature.models

import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentx.core.device.CpuTopology
import com.agentx.core.inference.model.ExtendedModelConfig
import com.agentx.core.inference.model.GenerationProfile
import com.agentx.core.inference.model.OutputFormat
import com.agentx.core.inference.model.ProfilePreset
import com.agentx.medical.core.device.DeviceSpecs
import com.agentx.medical.core.device.RecommendedModel
import com.agentx.medical.core.inference.CuratedMedicalModel
import com.agentx.medical.domain.models.GGUFModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onNavigateToModelConfig: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var modelToDelete by remember { mutableStateOf<GGUFModel?>(null) }
    var customHfUrl by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "imported_model.gguf"
            val fileSize = queryFileSize(context, it)
            viewModel.onGgufFileSelected(it, fileName, fileSize)
        }
    }

    LaunchedEffect(state.notificationMessage) {
        state.notificationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotification()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Hub & Native Engine", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import GGUF")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Storage Capacity Card
            StorageUsageCard(
                usedBytes = state.storageUsedBytes,
                availableBytes = state.storageAvailableBytes
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Active Model Banner with Real-time RAM status and Load/Unload
            ActiveModelBanner(
                activeModel = state.activeModel,
                isLoadedInRam = state.isLoadedInRam,
                loadedMemoryMb = state.loadedMemoryMb,
                onLoadModel = {
                    state.activeModel?.let { viewModel.loadModelIntoRam(it.id) }
                },
                onUnloadModel = {
                    viewModel.unloadModelFromRam()
                },
                onConfigure = {
                    state.activeModel?.let { onNavigateToModelConfig(it.id) }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Download Progress Card (if actively downloading)
            if (state.downloadState.isDownloading) {
                DownloadProgressCard(
                    downloadState = state.downloadState,
                    onCancel = { viewModel.cancelDownload() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tabs: Installed vs Hugging Face vs Specs & Best Fit
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Installed (${state.models.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Hugging Face") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Specs & Best Fit") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Installed Models
                    val filteredModels = when (state.activeFilter) {
                        "INSTALLED" -> state.models.filter { File(it.filePath).exists() }
                        "FAVORITES" -> state.models.filter { state.favoriteModelIds.contains(it.id) }
                        else -> state.models
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        // Toolbar: Scan Phone Storage + Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.scanStorageNow() },
                                enabled = !state.isScanningStorage,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (state.isScanningStorage) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scanning...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan Storage", fontSize = 11.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = state.activeFilter == "ALL",
                                    onClick = { viewModel.setFilter("ALL") },
                                    label = { Text("All", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = state.activeFilter == "INSTALLED",
                                    onClick = { viewModel.setFilter("INSTALLED") },
                                    label = { Text("On Disk", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = state.activeFilter == "FAVORITES",
                                    onClick = { viewModel.setFilter("FAVORITES") },
                                    label = { Text("★ Favs", fontSize = 11.sp) }
                                )
                            }
                        }

                        if (filteredModels.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Models Found", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Tap 'Scan Storage' to detect downloaded .gguf models or '+' to import via file picker.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredModels, key = { it.id }) { model ->
                                    val isActive = model.id == state.activeModel?.id
                                    val isLoadedThis = isActive && state.isLoadedInRam
                                    val isFavorite = state.favoriteModelIds.contains(model.id)
                                    ModelCard(
                                        model = model,
                                        isActive = isActive,
                                        isLoaded = isLoadedThis,
                                        isFavorite = isFavorite,
                                        onToggleFavorite = { viewModel.toggleFavorite(model.id) },
                                        onSelectActive = { viewModel.setActiveModel(model.id) },
                                        onLoad = { viewModel.loadModelIntoRam(model.id) },
                                        onUnload = { viewModel.unloadModelFromRam() },
                                        onConfigure = { onNavigateToModelConfig(model.id) },
                                        onDelete = { modelToDelete = model }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: Hugging Face Hub Custom Downloader & Catalog
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Pull from Custom Hugging Face URL",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Paste any direct resolve/blob GGUF link:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customHfUrl,
                                            onValueChange = { customHfUrl = it },
                                            placeholder = { Text("https://huggingface.co/.../*.gguf", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            enabled = !state.downloadState.isDownloading
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                if (customHfUrl.isNotBlank()) {
                                                    viewModel.startHuggingFaceDownload(customHfUrl)
                                                    customHfUrl = ""
                                                }
                                            },
                                            enabled = customHfUrl.isNotBlank() && !state.downloadState.isDownloading
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = "Pull")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Curated Open-Source GGUF Catalog",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(state.curatedModels) { curated ->
                            val isAlreadyInstalled = state.models.any { it.name.contains(curated.name.substringBefore(" ")) }
                            CuratedModelCard(
                                model = curated,
                                isInstalled = isAlreadyInstalled,
                                isDownloading = state.downloadState.isDownloading && state.downloadState.modelName == curated.name,
                                onDownload = {
                                    viewModel.startHuggingFaceDownload(
                                        url = curated.downloadUrl,
                                        filename = curated.filename,
                                        modelNameHint = curated.name
                                    )
                                }
                            )
                        }
                    }
                }
                2 -> {
                    // TAB 2: Device Hardware Specs & Best-Fit Models
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            state.deviceSpecs?.let { specs ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(specs.deviceModel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    specs.performanceTier.label,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SpecBadge("Total RAM", "${specs.totalRamGb} GB", Modifier.weight(1f))
                                            SpecBadge("Available RAM", "${specs.availableRamGb} GB", Modifier.weight(1f))
                                            SpecBadge("CPU Cores", "${state.cpuTopology.totalCores} Cores (${state.cpuTopology.performanceCores} Fast)", Modifier.weight(1.2f))
                                            specs.batteryPercent?.let {
                                                SpecBadge("Battery", "$it%", Modifier.weight(0.8f))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            specs.performanceTier.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Best-Fit Open-Source Models for Your Phone",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ranked dynamically based on available RAM headroom and CPU core topology (${state.cpuTopology.totalCores} cores, ${state.cpuTopology.performanceCores} performance cores).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        items(state.recommendedModels, key = { it.id }) { model ->
                            val isInstalled = state.models.any { it.name.contains(model.name.substringBefore(" ")) }
                            val isDownloading = state.downloadState.isDownloading && state.downloadState.modelName.contains(model.name.substringBefore(" "))

                            RecommendedModelCard(
                                model = model,
                                isInstalled = isInstalled,
                                isDownloading = isDownloading,
                                onDownload = { viewModel.downloadRecommendedModel(model) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Staging Progress Dialog
    if (state.stagingState.isStaging) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing & Staging Model") },
            text = {
                Column {
                    Text(
                        text = "Staging ${state.stagingState.modelName} to app-managed storage...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.stagingState.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatBytes(state.stagingState.bytesCopied)} / ${formatBytes(state.stagingState.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${(state.stagingState.progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Error Dialog
    state.stagingState.errorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissNotification() },
            title = { Text("GGUF Import Error") },
            text = { Text(errorMsg) },
            confirmButton = {
                Button(onClick = { viewModel.dismissNotification() }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("Delete Model?") },
            text = { Text("Are you sure you want to delete '${model.name}'? This will free ${formatBytes(model.sizeBytes)} of storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModel(model.id)
                        modelToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveModelBanner(
    activeModel: GGUFModel?,
    isLoadedInRam: Boolean,
    loadedMemoryMb: Long,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onConfigure: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLoadedInRam) Color(0xFF064E3B).copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isLoadedInRam) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isLoadedInRam) Color(0xFF10B981) else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLoadedInRam) "🟢 LOADED IN MEMORY (RAM)" else "⚪ OFFLINE ENGINE (NOT IN RAM)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isLoadedInRam) Color(0xFF10B981) else Color(0xFFFF9800)
                    )
                }

                if (isLoadedInRam) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981)
                    ) {
                        Text(
                            text = "${loadedMemoryMb} MB RAM",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeModel?.name ?: "No Model Selected",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = activeModel?.let {
                            "${formatBytes(it.sizeBytes)} • ${it.quantization} • ${it.threads} Threads • ${it.contextLength} Context"
                        } ?: "Select or import a .gguf model to begin offline inference",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onConfigure) {
                        Icon(Icons.Default.Tune, contentDescription = "Configure", tint = MaterialTheme.colorScheme.primary)
                    }

                    if (isLoadedInRam) {
                        OutlinedButton(
                            onClick = onUnloadModel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Unload", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onLoadModel,
                            enabled = activeModel != null,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load into RAM", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: GGUFModel,
    isActive: Boolean,
    isLoaded: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectActive: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectActive() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isActive) "Active" else "Select",
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            if (isLoaded) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Text(
                                        "🟢 IN RAM",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "${model.quantization} • ${formatBytes(model.sizeBytes)} • ${model.threads}T • ${model.contextLength} ctx",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.outline
                        )
                    }

                    if (isLoaded) {
                        OutlinedButton(
                            onClick = onUnload,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Unload", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = onLoad,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Load", fontSize = 11.sp)
                        }
                    }

                    IconButton(onClick = onConfigure, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Configure", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedModelCard(
    model: RecommendedModel,
    isInstalled: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isBestFit) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        if (model.isBestFit) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "⭐ #1 Best Fit",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "${model.parameterSize} • ${model.quantization} • ${model.downloadSizeMb} MB • Req: ${model.requiredRamGb} GB RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (model.matchScorePercent >= 90) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "${model.matchScorePercent}% Match",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(model.capabilitySummary, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(model.speedRating, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

                if (isInstalled) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Installed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = onDownload,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun CuratedModelCard(
    model: CuratedMedicalModel,
    isInstalled: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${model.quantization} • ${model.estimatedSizeBytes / (1024 * 1024)} MB • Req: ${model.ramRequirement}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (isInstalled) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Installed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = onDownload,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StorageUsageCard(usedBytes: Long, availableBytes: Long) {
    val total = usedBytes + availableBytes
    val fraction = if (total > 0) (usedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Model Storage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("${formatBytes(usedBytes)} used • ${formatBytes(availableBytes)} free", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DownloadProgressCard(
    downloadState: DownloadUiState,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Downloading ${downloadState.modelName}...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(progress = { downloadState.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${(downloadState.fraction * 100).toInt()}% • ${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)}", style = MaterialTheme.typography.labelSmall)
                Text("${formatBytes(downloadState.speedBytesPerSec)}/s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    modelId: String,
    viewModel: ModelsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val model = state.models.firstOrNull { it.id == modelId }

    val extendedConfig = remember(model) {
        ExtendedModelConfig.parse(model?.capabilityProfile)
    }

    var selectedPreset by remember(model) { mutableStateOf(extendedConfig.preset) }
    var systemPrompt by remember(model) { mutableStateOf(extendedConfig.systemPrompt ?: "") }
    var outputFormat by remember(model) { mutableStateOf(extendedConfig.outputFormat) }
    var jsonSchema by remember(model) { mutableStateOf(extendedConfig.jsonSchema ?: "") }
    var threads by remember(model) { mutableIntStateOf((model?.threads ?: state.cpuTopology.recommendedInferenceThreads).coerceIn(1, 4)) }
    var contextLength by remember(model) { mutableIntStateOf((model?.contextLength ?: 2048).coerceIn(512, 3840)) }
    var maxTokens by remember(model) { mutableIntStateOf(extendedConfig.maxTokens) }
    var temperature by remember(model) { mutableFloatStateOf(model?.temperature ?: 0.7f) }
    var topP by remember(model) { mutableFloatStateOf(model?.topP ?: 0.9f) }
    var topK by remember(model) { mutableIntStateOf(extendedConfig.topK) }
    var repetitionPenalty by remember(model) { mutableFloatStateOf(extendedConfig.repetitionPenalty) }
    var seed by remember(model) { mutableIntStateOf(extendedConfig.seed) }
    var stopSequencesText by remember(model) { mutableStateOf(extendedConfig.stopSequences.joinToString(", ")) }
    var keepLoadedInRam by remember(model) { mutableStateOf(extendedConfig.keepLoadedInRam) }

    fun applyPreset(p: ProfilePreset) {
        selectedPreset = p
        val profile = GenerationProfile.forPreset(p)
        temperature = profile.temperature
        topP = profile.topP
        topK = profile.topK
        repetitionPenalty = profile.repetitionPenalty
        maxTokens = profile.maxTokens
        if (profile.systemPrompt != null) {
            systemPrompt = profile.systemPrompt ?: ""
        }
    }

    fun saveConfig() {
        val stopSeqs = stopSequencesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val updatedConfig = ExtendedModelConfig(
            preset = selectedPreset,
            maxTokens = maxTokens,
            topK = topK,
            repetitionPenalty = repetitionPenalty,
            seed = seed,
            stopSequences = stopSeqs,
            outputFormat = outputFormat,
            jsonSchema = if (outputFormat == OutputFormat.JSON_SCHEMA) jsonSchema else null,
            systemPrompt = systemPrompt.ifBlank { null },
            keepLoadedInRam = keepLoadedInRam
        )
        viewModel.updateModelConfig(
            modelId = modelId,
            threads = threads,
            contextLength = contextLength,
            temperature = temperature,
            topP = topP,
            capabilityProfile = updatedConfig.serialize()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model?.name ?: "Configure Model", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            model?.let { m ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(m.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatBytes(m.sizeBytes)} • ${m.quantization} • ${m.architecture} • Path: ${m.filePath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Generation Presets
            Text("Generation Presets", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPreset == ProfilePreset.PRECISE,
                    onClick = { applyPreset(ProfilePreset.PRECISE) },
                    label = { Text("🎯 Precise", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedPreset == ProfilePreset.BALANCED,
                    onClick = { applyPreset(ProfilePreset.BALANCED) },
                    label = { Text("⚖️ Balanced", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedPreset == ProfilePreset.CREATIVE,
                    onClick = { applyPreset(ProfilePreset.CREATIVE) },
                    label = { Text("🎨 Creative", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedPreset == ProfilePreset.CUSTOM,
                    onClick = { selectedPreset = ProfilePreset.CUSTOM },
                    label = { Text("🛠️ Custom", fontSize = 11.sp) }
                )
            }

            // System Instructions
            Text("System Instructions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = {
                    systemPrompt = it
                    selectedPreset = ProfilePreset.CUSTOM
                },
                placeholder = { Text("Enter custom system instructions for this model...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // Output Format
            Text("Output Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = outputFormat == OutputFormat.TEXT,
                    onClick = { outputFormat = OutputFormat.TEXT },
                    label = { Text("Standard Text") }
                )
                FilterChip(
                    selected = outputFormat == OutputFormat.JSON,
                    onClick = { outputFormat = OutputFormat.JSON },
                    label = { Text("JSON Object") }
                )
                FilterChip(
                    selected = outputFormat == OutputFormat.JSON_SCHEMA,
                    onClick = { outputFormat = OutputFormat.JSON_SCHEMA },
                    label = { Text("JSON Schema") }
                )
            }

            if (outputFormat == OutputFormat.JSON_SCHEMA) {
                OutlinedTextField(
                    value = jsonSchema,
                    onValueChange = { jsonSchema = it },
                    placeholder = { Text("Enter valid JSON schema definition...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }

            // Hardware & CPU Threading
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Detected Hardware: ${state.cpuTopology.totalCores} CPU Cores (${state.cpuTopology.performanceCores} Performance Cores)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Inference CPU Threads: $threads (Recommended: ${state.cpuTopology.recommendedInferenceThreads})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = threads.toFloat(),
                        onValueChange = {
                            threads = it.toInt()
                            selectedPreset = ProfilePreset.CUSTOM
                        },
                        valueRange = 1f..state.cpuTopology.totalCores.coerceAtLeast(8).toFloat(),
                        steps = state.cpuTopology.totalCores.coerceAtLeast(8) - 2
                    )
                }
            }

            // Context Length & Max Tokens
            Text("Context Length: $contextLength tokens", fontWeight = FontWeight.Bold)
            Slider(
                value = contextLength.toFloat(),
                onValueChange = {
                    contextLength = it.toInt()
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 512f..8192f,
                steps = 14
            )

            Text("Max Generated Tokens: $maxTokens tokens", fontWeight = FontWeight.Bold)
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = {
                    maxTokens = it.toInt()
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 64f..2048f,
                steps = 30
            )

            // Sampling Controls
            Text("Temperature: ${String.format("%.2f", temperature)}", fontWeight = FontWeight.Bold)
            Slider(
                value = temperature,
                onValueChange = {
                    temperature = it
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 0.0f..1.5f
            )

            Text("Top-P: ${String.format("%.2f", topP)}", fontWeight = FontWeight.Bold)
            Slider(
                value = topP,
                onValueChange = {
                    topP = it
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 0.05f..1.0f
            )

            Text("Top-K: $topK", fontWeight = FontWeight.Bold)
            Slider(
                value = topK.toFloat(),
                onValueChange = {
                    topK = it.toInt()
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 1f..100f,
                steps = 98
            )

            Text("Repetition Penalty: ${String.format("%.2f", repetitionPenalty)}", fontWeight = FontWeight.Bold)
            Slider(
                value = repetitionPenalty,
                onValueChange = {
                    repetitionPenalty = it
                    selectedPreset = ProfilePreset.CUSTOM
                },
                valueRange = 1.0f..1.5f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (seed == 0) "" else seed.toString(),
                    onValueChange = { seed = it.toIntOrNull() ?: 0 },
                    label = { Text("Random Seed (0 = Random)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stopSequencesText,
                    onValueChange = { stopSequencesText = it },
                    label = { Text("Stop Sequences (comma sep)") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true
                )
            }

            // Runtime Lifecycle: Keep Loaded in RAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Model Loaded in RAM", fontWeight = FontWeight.Bold)
                    Text(
                        "Maintains model in memory between queries for instant generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = keepLoadedInRam,
                    onCheckedChange = { keepLoadedInRam = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        saveConfig()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Config")
                }

                Button(
                    onClick = {
                        saveConfig()
                        viewModel.loadModelIntoRam(modelId)
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load into RAM")
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format("%.0f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

private fun queryFileName(context: Context, uri: android.net.Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

private fun queryFileSize(context: Context, uri: android.net.Uri): Long {
    var size: Long = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex != -1 && cursor.moveToFirst()) {
            size = cursor.getLong(sizeIndex)
        }
    }
    return size
}
