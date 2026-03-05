package com.kreativekoala.echonote.ui.organization

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.data.model.RecordingFolder
import com.kreativekoala.echonote.util.Constants
import com.kreativekoala.echonote.util.TimeFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onFolderClick: (RecordingFolder) -> Unit = {},
    viewModel: FolderViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    if (showPaywall) {
        com.kreativekoala.echonote.ui.settings.PaywallScreen(onDismiss = { showPaywall = false })
        return
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newFolderName = ""
            },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim())
                            newFolderName = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newFolderName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Folders") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (viewModel.canCreateFolder()) showCreateDialog = true
                else showPaywall = true
            }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
            }
        }
    ) { padding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Folders",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create folders to organize your recordings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Folder limit indicator (free users only)
                if (!isPremium) {
                    item {
                        val limit = Constants.Premium.FREE_FOLDER_LIMIT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${folders.size}/$limit folders",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { (folders.size.toFloat() / limit).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
                items(
                    items = folders,
                    key = { it.id }
                ) { folder ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteFolder(folder)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                                label = "bg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onFolderClick(folder) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val folderColor = try {
                                Color(android.graphics.Color.parseColor("#${folder.colorHex}"))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = folderColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                val count by viewModel.getRecordingCount(folder.id).collectAsState()
                                val totalDuration by viewModel.getTotalDuration(folder.id).collectAsState()
                                Text(
                                    text = buildString {
                                        append("$count recording")
                                        if (count != 1) append("s")
                                        if (totalDuration > 0) {
                                            append(" • ")
                                            append(TimeFormatting.formatDuration(totalDuration))
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
    }
}
