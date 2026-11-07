package com.agentx.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val PREFS_NAME = "agentx_permissions_prefs"
private const val KEY_DISCLOSURE_ACCEPTED = "permissions_disclosure_accepted"

fun hasAcceptedPermissionsDisclosure(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
}

fun setPermissionsDisclosureAccepted(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, true).apply()
}

@Composable
fun PermissionsDisclosureDialog(
    appName: String = "AgentX",
    onAccepted: () -> Unit
) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(!hasAcceptedPermissionsDisclosure(context)) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        setPermissionsDisclosureAccepted(context)
        isVisible = false
        onAccepted()
    }

    if (!isVisible) return

    Dialog(
        onDismissRequest = { /* Must be explicitly confirmed once after install */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Welcome to $appName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Permissions & System Capabilities",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Text(
                    text = "$appName executes genuine on-device LLMs completely offline. To ensure unrestricted inference and model serving, please grant the following permissions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CapabilityItem(
                    icon = Icons.Default.Memory,
                    title = "Unrestricted RAM & CPU Execution",
                    description = "Allocates native RAM memory to load complete GGUF model weights into device memory for low-latency offline inference."
                )

                CapabilityItem(
                    icon = Icons.Default.Storage,
                    title = "Model File & Storage Access",
                    description = "Reads, stages, and caches model weights (.gguf) from Downloads, Documents, and app sandboxes without size restrictions."
                )

                CapabilityItem(
                    icon = Icons.Default.Notifications,
                    title = "Background Tasks & Live Sessions",
                    description = "Keeps the local model loaded in memory across app screens and delivers status notifications during generation."
                )

                CapabilityItem(
                    icon = Icons.Default.Wifi,
                    title = "Local Network Serving (Optional)",
                    description = "Enables communication with local AgentX Serve instances across your Wi-Fi network with zero cloud transmission."
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val toRequest = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                            toRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        if (toRequest.isNotEmpty()) {
                            permissionsLauncher.launch(toRequest.toTypedArray())
                        } else {
                            setPermissionsDisclosureAccepted(context)
                            isVisible = false
                            onAccepted()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Permissions & Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CapabilityItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
