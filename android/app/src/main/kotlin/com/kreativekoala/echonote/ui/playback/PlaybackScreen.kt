package com.kreativekoala.echonote.ui.playback

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.kreativekoala.ratingkit.RatingKit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.service.joinWords
import com.kreativekoala.echonote.service.summary.SummaryUiState
import com.kreativekoala.echonote.tts.ReadAloudSheet
import com.kreativekoala.echonote.ui.components.WaveformView
import com.kreativekoala.echonote.ui.theme.EchoTheme
import com.kreativekoala.echonote.ui.theme.TimerTextStyle
import com.kreativekoala.echonote.util.DateFormatting
import com.kreativekoala.echonote.util.ExportFormat
import com.kreativekoala.echonote.util.TimeFormatting
import java.io.File

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"

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
    val transcriptionProgress by viewModel.transcriptionProgress.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val isTrimming by viewModel.isTrimming.collectAsState()
    val showTrimMode by viewModel.showTrimMode.collectAsState()
    val trimStart by viewModel.trimStart.collectAsState()
    val trimEnd by viewModel.trimEnd.collectAsState()
    val transcriptSegments by viewModel.transcriptSegments.collectAsState()
    val activeSegmentIndex by viewModel.activeSegmentIndex.collectAsState()
    val summaryState by viewModel.summaryState.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showReadAloud by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var confirmRetranscribe by remember { mutableStateOf(false) }
    val hasEdits by viewModel.hasEdits.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.triggerReview.collect {
            val activity = context as? Activity ?: return@collect
            RatingKit.trackAction(activity)
        }
    }

    val rec = recording ?: return

    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
    val paragraphs = remember(transcriptSegments) { buildParagraphs(transcriptSegments) }
    val hasText = !transcriptionResult.isNullOrBlank()

    // Items that sit above the transcript in the list; needed to scroll to the right row.
    val leadingItems = 1 + (if (isTranscribing) 1 else 0) + (if (transcriptionError != null) 1 else 0) +
        (if (!hasText && !isTranscribing) 1 else 0) +
        (if (hasText) summaryItemCount(summaryState) else 0)
    val listState = rememberLazyListState()
    val activeParagraph = paragraphIndexOf(paragraphs, activeSegmentIndex)
    // One stable lambda so paragraphs away from the playhead can still skip recomposition.
    val openParagraphEditor = remember(transcriptSegments) {
        { p: TranscriptParagraph ->
            editTarget = EditTarget(
                p.firstWord, p.endWord,
                joinWords(transcriptSegments.subList(p.firstWord, p.endWord).map { it.word })
            )
        }
    }
    val openPlainEditor = remember(transcriptionResult) {
        { editTarget = EditTarget(-1, -1, transcriptionResult.orEmpty()) }
    }
    LaunchedEffect(activeParagraph) {
        if (isPlaying && activeParagraph >= 0) {
            listState.animateScrollToItem(leadingItems + activeParagraph)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (hasText) stringResource(R.string.playback_retranscribe)
                                        else stringResource(R.string.playback_transcribe)
                                    )
                                },
                                enabled = !isTranscribing,
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (hasText && hasEdits) confirmRetranscribe = true else viewModel.transcribe()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playback_trim)) },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleTrimMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playback_skip_silence)) },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                                trailingIcon = {
                                    if (skipSilence) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleSkipSilence()
                                }
                            )
                            if (hasText) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_menu)) },
                                    enabled = !isTranscribing,
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        if (paragraphs.isEmpty()) openPlainEditor()
                                        else openParagraphEditor(paragraphs[maxOf(activeParagraph, 0)])
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.summary_action)) },
                                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                                    enabled = !isTranscribing && summaryState != SummaryUiState.Working,
                                    onClick = {
                                        showMenu = false
                                        viewModel.summarize()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playback_copy_transcript)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", transcriptionResult))
                                        Toast.makeText(context, context.getString(R.string.playback_transcript_copied), Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Read aloud") },
                                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showReadAloud = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as text") },
                                    onClick = { showMenu = false; viewModel.exportTranscript(ExportFormat.TXT, context) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as subtitles (SRT)") },
                                    onClick = { showMenu = false; viewModel.exportTranscript(ExportFormat.SRT, context) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as web subtitles (VTT)") },
                                    onClick = { showMenu = false; viewModel.exportTranscript(ExportFormat.VTT, context) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            PlayerBar(
                waveformSamples = waveformSamples,
                progress = progress,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                showTrimMode = showTrimMode,
                isTrimming = isTrimming,
                trimStart = trimStart,
                trimEnd = trimEnd,
                onSeekProgress = viewModel::seekToProgress,
                onTogglePlay = viewModel::togglePlayPause,
                onSkipBack = viewModel::skipBackward,
                onSkipForward = viewModel::skipForward,
                onSpeed = viewModel::setSpeed,
                onTrimRange = viewModel::setTrimRange,
                onApplyTrim = viewModel::performTrim,
                onCancelTrim = viewModel::toggleTrimMode
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = rec.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(DateFormatting.formatDateTime(rec.dateCreated))
                            rec.locationName?.let { append("  ").append(it) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isTranscribing) {
                item(key = "progress") {
                    TranscribingCard(
                        status = transcriptionStatus ?: "Transcribing…",
                        progress = transcriptionProgress,
                        partial = partialTranscript,
                        onReadAloud = { showReadAloud = true }
                    )
                }
            }

            if (transcriptionError != null) {
                item(key = "error") {
                    ErrorCard(
                        message = transcriptionError!!,
                        onDismiss = viewModel::clearTranscriptionError
                    )
                }
            }

            if (!hasText && !isTranscribing) {
                item(key = "cta") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        Text(
                            text = "No transcript yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Transcribe on this phone to read, search and jump through the recording. The audio never leaves the device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::transcribe, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Transcribe this recording")
                        }
                    }
                }
            }

            if (hasText) {
                summaryItem(summaryState, onRefresh = viewModel::summarize)
                if (paragraphs.isNotEmpty()) {
                    transcriptItems(
                        words = transcriptSegments,
                        paragraphs = paragraphs,
                        activeWord = activeSegmentIndex,
                        onSeek = viewModel::seekTo,
                        onEdit = if (isTranscribing) null else openParagraphEditor
                    )
                } else {
                    plainTranscriptItem(transcriptionResult!!, if (isTranscribing) null else openPlainEditor)
                }
            }
        }
    }

    editTarget?.let { target ->
        TranscriptEditDialog(
            target = target,
            onSave = { text ->
                if (text.trim() != target.text) {
                    if (target.first < 0) viewModel.editPlainTranscript(text)
                    else viewModel.editParagraph(target.first, target.end, text)
                }
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    if (confirmRetranscribe) {
        RetranscribeEditsDialog(
            onConfirm = { confirmRetranscribe = false; viewModel.transcribe() },
            onDismiss = { confirmRetranscribe = false }
        )
    }

    if (showReadAloud) {
        ReadAloudSheet(
            text = if (isTranscribing) partialTranscript else transcriptionResult.orEmpty(),
            onDismiss = { showReadAloud = false }
        )
    }
}

@Composable
private fun TranscribingCard(
    status: String,
    progress: Float,
    partial: String,
    onReadAloud: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = TimerTextStyle.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (partial.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = partial.takeLast(280),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 5
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReadAloud) { Text("Read aloud so far") }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playback_transcription_failed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.playback_dismiss),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PlayerBar(
    waveformSamples: FloatArray,
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    showTrimMode: Boolean,
    isTrimming: Boolean,
    trimStart: Float,
    trimEnd: Float,
    onSeekProgress: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeed: (Float) -> Unit,
    onTrimRange: (Float, Float) -> Unit,
    onApplyTrim: () -> Unit,
    onCancelTrim: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            if (showTrimMode) {
                Text(
                    text = stringResource(R.string.playback_drag_trim),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                RangeSlider(
                    value = trimStart..trimEnd,
                    onValueChange = { range -> onTrimRange(range.start, range.endInclusive) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        TimeFormatting.formatDuration((trimStart * durationMs).toLong()),
                        style = TimerTextStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        TimeFormatting.formatDuration((trimEnd * durationMs).toLong()),
                        style = TimerTextStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelTrim,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.playback_cancel_trim)) }
                    Button(
                        onClick = onApplyTrim,
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
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.playback_trimming))
                        } else {
                            Text(stringResource(R.string.playback_apply_trim))
                        }
                    }
                }
            }

            WaveformView(
                samples = waveformSamples,
                progress = progress,
                onSeek = onSeekProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    TimeFormatting.formatDuration(currentPositionMs),
                    style = TimerTextStyle.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    TimeFormatting.formatDuration(durationMs),
                    style = TimerTextStyle.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(64.dp)) {
                    TextButton(onClick = { showSpeedMenu = true }) {
                        Text(
                            speedLabel(playbackSpeed),
                            style = TimerTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                        SPEEDS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(speed)) },
                                trailingIcon = {
                                    if (speed == playbackSpeed) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    showSpeedMenu = false
                                    onSpeed(speed)
                                }
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSkipBack) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = stringResource(R.string.playback_skip_back),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.playback_pause) else stringResource(R.string.playback_play),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = onSkipForward) {
                        Icon(
                            Icons.Default.Forward30,
                            contentDescription = stringResource(R.string.playback_skip_forward),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(64.dp))
            }
        }
    }
}
