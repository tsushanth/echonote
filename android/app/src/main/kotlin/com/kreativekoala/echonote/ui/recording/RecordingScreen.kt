package com.kreativekoala.echonote.ui.recording

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.ui.components.LiveWaveformView
import com.kreativekoala.echonote.ui.components.lip
import com.kreativekoala.echonote.ui.theme.*
import com.kreativekoala.ratingkit.RatingKit
import android.app.Activity

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
    val showPermissionError by viewModel.showPermissionError.collectAsState()
    val showBatteryOptPrompt by viewModel.showBatteryOptPrompt.collectAsState()
    val showReviewCard by viewModel.showReviewCard.collectAsState()
    val gain by viewModel.gain.collectAsState()
    val soundEffectsEnabled by viewModel.soundEffectsEnabled.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun buzz() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    fun beep(start: Boolean) {
        try {
            val tone = if (start) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(tone, 180)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 300)
        } catch (_: Exception) {}
    }

    // Battery optimization dialog (Xiaomi/MIUI)
    if (showBatteryOptPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBatteryOptPrompt() },
            title = { Text(stringResource(R.string.battery_opt_title)) },
            text = { Text(stringResource(R.string.battery_opt_message)) },
            confirmButton = {
                Button(onClick = { viewModel.openBatteryOptSettings() }) {
                    Text(stringResource(R.string.battery_opt_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBatteryOptPrompt() }) {
                    Text(stringResource(R.string.battery_opt_dismiss))
                }
            }
        )
    }

    // Permission error dialog
    if (showPermissionError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionError() },
            title = { Text(stringResource(R.string.permission_microphone_rationale)) },
            text = { Text(stringResource(R.string.recording_error_permission)) },
            confirmButton = {
                Button(onClick = { viewModel.dismissPermissionError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.recording_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = recordingTitle,
                        onValueChange = { viewModel.setTitle(it) },
                        label = { Text(stringResource(R.string.recording_name_label)) },
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
                    (context as? Activity)?.let { RatingKit.trackAction(it) }
                    onDismiss()
                }) {
                    Text(stringResource(R.string.recording_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.discardRecording()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.recording_discard), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recording_title_record)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isRecording) viewModel.cancelRecording()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.recording_close))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isRecording && !isPaused) Coral else TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Live waveform
                if (isRecording) {
                    LiveWaveformView(
                        levels = meterLevels,
                        isStereo = isStereo,
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
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SurfaceRaised)
                                .clickable {
                                    if (hapticFeedbackEnabled) buzz()
                                    if (soundEffectsEnabled) beep(false)
                                    viewModel.stopRecording()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = stringResource(R.string.recording_stop),
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Pause/Resume button
                        Box(modifier = Modifier.padding(bottom = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .lip(CircleShape, color = CoralDim)
                                    .clip(CircleShape)
                                    .background(Coral)
                                    .clickable {
                                        if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isPaused) stringResource(R.string.recording_resume) else stringResource(R.string.recording_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    } else {
                        // Record button
                        Box(modifier = Modifier.padding(bottom = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .lip(CircleShape, color = CoralDim)
                                    .clip(CircleShape)
                                    .background(Coral)
                                    .clickable {
                                        if (hapticFeedbackEnabled) buzz()
                                        if (soundEffectsEnabled) beep(true)
                                        viewModel.startRecording()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Format/Quality selectors (only when not recording)
                if (!isRecording) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceRaised,
                        labelColor = TextSecondary,
                        selectedContainerColor = Electric,
                        selectedLabelColor = Color.White
                    )
                    val chipBorder = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilterChip(
                                selected = selectedFormat == com.kreativekoala.echonote.data.model.AudioFormat.COMPRESSED,
                                onClick = { viewModel.setFormat(com.kreativekoala.echonote.data.model.AudioFormat.COMPRESSED) },
                                label = { Text(stringResource(R.string.recording_format_m4a)) },
                                shape = RoundedCornerShape(50),
                                colors = chipColors,
                                border = chipBorder
                            )
                            FilterChip(
                                selected = selectedFormat == com.kreativekoala.echonote.data.model.AudioFormat.UNCOMPRESSED,
                                onClick = { viewModel.setFormat(com.kreativekoala.echonote.data.model.AudioFormat.UNCOMPRESSED) },
                                label = { Text(stringResource(R.string.recording_format_wav)) },
                                shape = RoundedCornerShape(50),
                                colors = chipColors,
                                border = chipBorder
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
                                    label = { Text(q.displayName) },
                                    shape = RoundedCornerShape(50),
                                    colors = chipColors,
                                    border = chipBorder
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
                                label = { Text(stringResource(R.string.recording_stereo)) },
                                shape = RoundedCornerShape(50),
                                colors = chipColors,
                                border = chipBorder
                            )
                        }

                        // Mic Gain slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Mic Gain", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(
                                    text = "%.1f×".format(gain),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Slider(
                                value = gain,
                                onValueChange = { viewModel.setGain(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = Electric,
                                    activeTrackColor = Electric,
                                    inactiveTrackColor = SurfaceRaised
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Review prompt card — slides up from bottom
            AnimatedVisibility(
                visible = showReviewCard,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                ReviewPromptCard(
                    onYes = {
                        viewModel.dismissReviewCard()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kreativekoala.echonote")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.kreativekoala.echonote")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    },
                    onNo = {
                        viewModel.dismissReviewCard()
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@kreativekoala.com?subject=ClearVoice%20Feedback")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                )
            }
        }
    }
}

@Composable
private fun ReviewPromptCard(
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.review_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.review_prompt_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onYes,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("\uD83D\uDC4D ${stringResource(R.string.review_prompt_yes)}")
                }
                OutlinedButton(
                    onClick = onNo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("\uD83D\uDC4E ${stringResource(R.string.review_prompt_no)}")
                }
            }
        }
    }
}
