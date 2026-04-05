package com.kreativekoala.echonote.ui.playback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.ui.components.WaveformView
import com.kreativekoala.echonote.ui.settings.PaywallScreen
import com.kreativekoala.echonote.util.DateFormatting
import com.kreativekoala.echonote.util.TimeFormatting
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val recording by viewModel.currentRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val waveformSamples by viewModel.waveformSamples.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val transcriptionResult by viewModel.transcriptionResult.collectAsState()
    val transcriptionError by viewModel.transcriptionError.collectAsState()
    val transcriptionStatus by viewModel.transcriptionStatus.collectAsState()
    val isTrimming by viewModel.isTrimming.collectAsState()
    val showTrimMode by viewModel.showTrimMode.collectAsState()
    val trimStart by viewModel.trimStart.collectAsState()
    val trimEnd by viewModel.trimEnd.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    var showPaywall by remember { mutableStateOf(false) }

    val rec = recording ?: return

    if (showPaywall) {
        PaywallScreen(onDismiss = {
            showPaywall = false
            viewModel.refreshPremiumStatus()
        })
        return
    }

    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.dismiss()
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.playback_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        try {
                            val file = File(rec.fileUri)
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
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.playback_share))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = rec.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    rec.locationName?.let { append("$it • ") }
                    append(DateFormatting.formatDateTime(rec.dateCreated))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            WaveformView(
                samples = waveformSamples,
                progress = progress,
                onSeek = { p -> viewModel.seekToProgress(p) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            if (showTrimMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.playback_drag_trim),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                RangeSlider(
                    value = trimStart..trimEnd,
                    onValueChange = { range ->
                        viewModel.setTrimRange(range.start, range.endInclusive)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TimeFormatting.formatDuration((trimStart * durationMs).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = TimeFormatting.formatDuration((trimEnd * durationMs).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeFormatting.formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TimeFormatting.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = progress,
                onValueChange = { viewModel.seekToProgress(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipBackward() }) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = stringResource(R.string.playback_skip_back),
                        modifier = Modifier.size(32.dp)
                    )
                }

                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.playback_pause) else stringResource(R.string.playback_play),
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.skipForward() }) {
                    Icon(
                        Icons.Default.Forward30,
                        contentDescription = stringResource(R.string.playback_skip_forward),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Speed controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isPremium) viewModel.decreaseSpeed()
                        else showPaywall = true
                    },
                    enabled = playbackSpeed > 0.5f
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.playback_decrease_speed))
                }
                Text(
                    text = String.format("%.2fx", playbackSpeed),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = {
                        if (isPremium) viewModel.increaseSpeed()
                        else showPaywall = true
                    },
                    enabled = playbackSpeed < 2.0f
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.playback_increase_speed))
                }
                if (!isPremium) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.playback_premium_feature),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Skip Silence toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = skipSilence,
                    onClick = {
                        if (isPremium) viewModel.toggleSkipSilence()
                        else showPaywall = true
                    },
                    label = { Text(stringResource(R.string.playback_skip_silence)) },
                    leadingIcon = if (!isPremium) {
                        { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else if (skipSilence) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons row: Transcribe and Trim
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        if (isPremium) viewModel.transcribe()
                        else showPaywall = true
                    },
                    enabled = !isTranscribing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(transcriptionStatus ?: stringResource(R.string.playback_transcribing))
                    } else {
                        Icon(
                            if (!isPremium) Icons.Default.Lock else Icons.Default.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (transcriptionResult != null) stringResource(R.string.playback_retranscribe) else stringResource(R.string.playback_transcribe))
                    }
                }

                if (showTrimMode) {
                    Button(
                        onClick = { viewModel.performTrim() },
                        enabled = !isTrimming,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTrimming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.playback_trimming))
                        } else {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.playback_apply_trim))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (isPremium) viewModel.toggleTrimMode()
                            else showPaywall = true
                        },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (!isPremium) Icons.Default.Lock else Icons.Default.ContentCut,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.playback_trim))
                    }
                }
            }

            if (showTrimMode) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { viewModel.toggleTrimMode() }) {
                    Text(stringResource(R.string.playback_cancel_trim))
                }
            }

            // Transcription error display
            if (transcriptionError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.playback_transcription_failed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transcriptionError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearTranscriptionError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.playback_dismiss),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Transcript display with Copy button
            if (transcriptionResult != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.playback_transcript),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Transcript", transcriptionResult)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.playback_transcript_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.playback_copy_transcript),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = transcriptionResult!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
