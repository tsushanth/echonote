package com.kreativekoala.echonote.ui.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.ui.components.LiveWaveformView
import com.kreativekoala.echonote.ui.theme.RecordingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onDismiss: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val elapsedTimeMs by viewModel.elapsedTimeMs.collectAsState()
    val meterLevels by viewModel.meterLevels.collectAsState()
    val showSaveDialog by viewModel.showSaveDialog.collectAsState()
    val recordingTitle by viewModel.recordingTitle.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val isStereo by viewModel.isStereo.collectAsState()

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Save Recording") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = recordingTitle,
                        onValueChange = { viewModel.setTitle(it) },
                        label = { Text("Recording Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${selectedFormat.displayName} • ${selectedQuality.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveRecording()
                    onDismiss()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.discardRecording()
                    onDismiss()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isRecording) viewModel.cancelRecording()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Timer display
            val totalSeconds = elapsedTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val timeText = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }

            Text(
                text = timeText,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = if (isRecording && !isPaused) RecordingRed
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live waveform
            if (isRecording) {
                LiveWaveformView(
                    levels = meterLevels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(120.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    // Stop button
                    IconButton(
                        onClick = { viewModel.stopRecording() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Pause/Resume button
                    FilledIconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeRecording()
                            else viewModel.pauseRecording()
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RecordingRed
                        )
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                } else {
                    // Record button
                    FilledIconButton(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RecordingRed
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Format/Quality selectors (only when not recording)
            if (!isRecording) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = selectedFormat == com.kreativekoala.echonote.data.model.AudioFormat.COMPRESSED,
                            onClick = { viewModel.setFormat(com.kreativekoala.echonote.data.model.AudioFormat.COMPRESSED) },
                            label = { Text("M4A") }
                        )
                        FilterChip(
                            selected = selectedFormat == com.kreativekoala.echonote.data.model.AudioFormat.UNCOMPRESSED,
                            onClick = { viewModel.setFormat(com.kreativekoala.echonote.data.model.AudioFormat.UNCOMPRESSED) },
                            label = { Text("WAV") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        com.kreativekoala.echonote.data.model.RecordingQuality.entries.forEach { q ->
                            FilterChip(
                                selected = selectedQuality == q,
                                onClick = { viewModel.setQuality(q) },
                                label = { Text(q.displayName) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = isStereo,
                            onClick = { viewModel.setStereo(!isStereo) },
                            label = { Text("Stereo") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
