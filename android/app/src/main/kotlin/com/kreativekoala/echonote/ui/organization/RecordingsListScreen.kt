package com.kreativekoala.echonote.ui.organization

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.RecordingFolder
import com.kreativekoala.echonote.ui.components.RecordingRowItem
import com.kreativekoala.echonote.ui.settings.PaywallScreen
import com.kreativekoala.echonote.ui.theme.RecordingRed
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordingsListScreen(
    onRecordClick: () -> Unit,
    onRecordingClick: (Recording) -> Unit,
    folders: List<RecordingFolder> = emptyList(),
    onMoveToFolder: (Recording, String?) -> Unit = { _, _ -> },
    onRecycleBinClick: () -> Unit = {},
    viewModel: RecordingsListViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var recordingToMove by remember { mutableStateOf<Recording?>(null) }
    var recordingToRename by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showPaywall by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isSelectionMode = selectedIds.isNotEmpty()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFile(context, it) }
    }

    if (showPaywall) {
        PaywallScreen(onDismiss = { showPaywall = false })
        return
    }

    // Rename dialog
    if (recordingToRename != null) {
        AlertDialog(
            onDismissRequest = {
                recordingToRename = null
                renameText = ""
            },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            recordingToRename?.let { viewModel.renameRecording(it, renameText.trim()) }
                            recordingToRename = null
                            renameText = ""
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(stringResource(R.string.rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    recordingToRename = null
                    renameText = ""
                }) {
                    Text(stringResource(R.string.rename_cancel))
                }
            }
        )
    }

    // Move-to-folder bottom sheet
    if (showMoveSheet && recordingToMove != null) {
        MoveToFolderSheet(
            folders = folders,
            currentFolderId = recordingToMove?.folderId,
            onFolderSelected = { folderId ->
                recordingToMove?.let { onMoveToFolder(it, folderId) }
                recordingToMove = null
            },
            onDismiss = {
                showMoveSheet = false
                recordingToMove = null
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.recordings_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.recordings_cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.recordings_delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.recordings_title)) },
                    actions = {
                        IconButton(onClick = onRecycleBinClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Recycle Bin")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.recordings_sort))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (option) {
                                                    SortOption.DATE -> stringResource(R.string.sort_date)
                                                    SortOption.NAME -> stringResource(R.string.sort_name)
                                                    SortOption.DURATION -> stringResource(R.string.sort_duration)
                                                    SortOption.SIZE -> stringResource(R.string.sort_size)
                                                }
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (sortOption == option) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import audio/video")
                    }
                    FloatingActionButton(
                        onClick = {
                            if (viewModel.canCreateRecording()) onRecordClick()
                            else showPaywall = true
                        },
                        containerColor = RecordingRed,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.recordings_record))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            if (!isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.recordings_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.recordings_clear))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (recordings.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) stringResource(R.string.recordings_no_results)
                            else stringResource(R.string.recordings_no_recordings),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) stringResource(R.string.recordings_try_different_search)
                            else stringResource(R.string.recordings_tap_to_start),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = recordings,
                        key = { it.id }
                    ) { recording ->
                        var showContextMenu by remember { mutableStateOf(false) }
                        val isSelected = selectedIds.contains(recording.id)

                        if (isSelectionMode) {
                            // Selection mode row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .combinedClickable(
                                        onClick = { viewModel.toggleSelection(recording.id) }
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelection(recording.id) }
                                )
                                RecordingRowItem(
                                    recording = recording,
                                    onTap = { viewModel.toggleSelection(recording.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(recording) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteRecording(recording)
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
                                            contentDescription = stringResource(R.string.recordings_delete),
                                            tint = Color.White
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                Box {
                                    RecordingRowItem(
                                        recording = recording,
                                        onTap = { onRecordingClick(recording) },
                                        onLongPress = { showContextMenu = true },
                                        onFavoriteToggle = { viewModel.toggleFavorite(recording) },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    )
                                    DropdownMenu(
                                        expanded = showContextMenu,
                                        onDismissRequest = { showContextMenu = false },
                                        offset = DpOffset(16.dp, 0.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.context_rename)) },
                                            onClick = {
                                                showContextMenu = false
                                                renameText = recording.title
                                                recordingToRename = recording
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.context_share)) },
                                            onClick = {
                                                showContextMenu = false
                                                try {
                                                    val file = File(recording.fileUri)
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "audio/*"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.playback_share_recording)))
                                                } catch (_: Exception) { }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Share, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.context_move_to_folder)) },
                                            onClick = {
                                                showContextMenu = false
                                                recordingToMove = recording
                                                showMoveSheet = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Folder, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.context_select)) },
                                            onClick = {
                                                showContextMenu = false
                                                viewModel.toggleSelection(recording.id)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.CheckBox, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.context_delete)) },
                                            onClick = {
                                                showContextMenu = false
                                                viewModel.deleteRecording(recording)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}
