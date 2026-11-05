package com.kreativekoala.echonote.tts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private fun speedLabel(s: Float) = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')

/**
 * "Listen": reads [request] aloud with an on-device voice and shows the sentence being spoken. The speech itself is
 * owned by [ListenController] (so headset / lock-screen controls work and a screen rotation does not restart it);
 * this sheet only starts it, mirrors it and stops it when the user closes the sheet with Done or by swiping it away.
 *
 * [onBeforeStart] runs just before speech begins, e.g. to pause the recording playback so the two never overlap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenSheet(
    request: ListenRequest,
    onDismiss: () -> Unit,
    onBeforeStart: () -> Unit = {},
) {
    val context = LocalContext.current
    val manager = remember { ReadAloudManager.get(context.applicationContext) }
    val controller = manager.listen
    val ui by controller.ui.collectAsState()
    val voices by controller.engine.voices.collectAsState()
    val preferredVoiceId by manager.preferredVoiceId.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var voiceMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(request.key) {
        val running = controller.ui.value
        val sameRequest = running.request?.key == request.key
        val active = running.phase == ListenPhase.Speaking || running.phase == ListenPhase.Paused || running.phase == ListenPhase.Preparing
        // A re-created sheet (rotation) re-attaches to speech that is already going instead of restarting it.
        if (!(sameRequest && active)) {
            onBeforeStart()
            controller.start(request, manager.preferredVoiceId.first())
        }
    }

    fun close() {
        controller.dismiss()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.listen_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }

            val status = statusText(ui)
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.phase == ListenPhase.Failed || ui.phase == ListenPhase.Unavailable) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (ui.phase == ListenPhase.Preparing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (ui.phase == ListenPhase.Unavailable) {
                UnavailableActions(ui.unavailable, context) { controller.start(request, preferredVoiceId) }
            }
            if (ui.phase == ListenPhase.Failed) {
                Button(onClick = { controller.start(request, preferredVoiceId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.listen_try_again))
                }
            }

            SentenceList(request.units, ui.unit, onTap = { controller.jump(it) })

            if (ui.phase != ListenPhase.Unavailable) {
                Controls(ui, controller)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.listen_speed_label), style = MaterialTheme.typography.labelLarge)
                    SPEEDS.forEach { s ->
                        val chosen = ui.speed == s
                        val label = speedLabel(s) + "×"
                        FilterChip(
                            selected = chosen,
                            onClick = { controller.setSpeed(s) },
                            label = { Text(label) },
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(R.string.listen_speed_chip, speedLabel(s))
                                role = Role.RadioButton
                                selected = chosen
                            },
                        )
                    }
                }
                if (voices.size > 1) {
                    Box {
                        OutlinedButton(onClick = { voiceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                voices.firstOrNull { it.id == preferredVoiceId }?.displayName
                                    ?: stringResource(R.string.listen_voice_picker)
                            )
                        }
                        DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.displayName) },
                                    onClick = {
                                        voiceMenuOpen = false
                                        scope.launch {
                                            manager.setPreferredVoice(voice.id)
                                            controller.start(request.copy(startUnit = ui.unit.coerceAtLeast(0)), voice.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = listOfNotNull(
                    ui.engineLabel?.let { stringResource(R.string.listen_voice_label, it) },
                    stringResource(R.string.listen_privacy_note),
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = ::close, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.listen_done))
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun statusText(ui: ListenUiState): String {
    return when (ui.phase) {
        ListenPhase.Preparing -> stringResource(R.string.listen_preparing)
        ListenPhase.Speaking -> stringResource(R.string.listen_status_speaking)
        ListenPhase.Paused -> stringResource(R.string.listen_status_paused)
        ListenPhase.Finished -> stringResource(R.string.listen_status_finished)
        ListenPhase.Idle -> stringResource(R.string.listen_status_idle)
        ListenPhase.Failed -> stringResource(R.string.listen_failed, ui.failure.orEmpty())
        ListenPhase.Unavailable -> when (val r = ui.unavailable) {
            TtsUnavailable.NoEngine -> stringResource(R.string.listen_no_engine)
            is TtsUnavailable.MissingVoiceData -> stringResource(R.string.listen_missing_voice, languageName(r.languageTag))
            is TtsUnavailable.LanguageNotSupported -> stringResource(R.string.listen_unsupported_language, languageName(r.languageTag))
            TtsUnavailable.InitFailed, null -> stringResource(R.string.listen_init_failed)
        }
    }
}

private fun languageName(tag: String): String = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())

@Composable
private fun UnavailableActions(reason: TtsUnavailable?, context: Context, retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (reason) {
            TtsUnavailable.NoEngine -> OutlinedButton(
                onClick = { openFirst(context, playStoreIntent("com.google.android.tts")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.listen_get_engine)) }
            is TtsUnavailable.MissingVoiceData -> OutlinedButton(
                onClick = {
                    openFirst(
                        context,
                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { reason.enginePackage?.let { setPackage(it) } },
                        ttsSettingsIntent(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.listen_install_voice)) }
            else -> OutlinedButton(
                onClick = { openFirst(context, ttsSettingsIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.listen_open_tts_settings)) }
        }
        Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.listen_try_again)) }
    }
}

private fun playStoreIntent(pkg: String) = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
private fun ttsSettingsIntent() = Intent("com.android.settings.TTS_SETTINGS")

private fun openFirst(context: Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) { }
    }
    try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

@Composable
private fun SentenceList(units: List<String>, current: Int, onTap: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { if (current in units.indices) listState.animateScrollToItem(current) }
    val hint = stringResource(R.string.listen_row_hint)
    val nowReading = stringResource(R.string.listen_row_current)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(units) { index, text ->
            val active = index == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(onClickLabel = hint) { onTap(index) }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        if (active) stateDescription = nowReading
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Not colour alone: the current row is also bold and has a marker bar.
                Box(
                    Modifier.width(4.dp).heightIn(min = 24.dp).background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Controls(ui: ListenUiState, controller: ListenController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.listen_previous))
        }
        val speaking = ui.phase == ListenPhase.Speaking
        val finishedOrStopped = ui.phase == ListenPhase.Finished || ui.phase == ListenPhase.Idle
        FilledIconButton(onClick = { controller.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
            Icon(
                when {
                    speaking -> Icons.Default.Pause
                    finishedOrStopped -> Icons.Default.Replay
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = stringResource(
                    when {
                        speaking -> R.string.listen_pause
                        finishedOrStopped -> R.string.listen_again
                        ui.phase == ListenPhase.Paused -> R.string.listen_resume
                        else -> R.string.listen_play
                    }
                ),
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { controller.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.listen_next))
        }
        IconButton(onClick = { controller.stop() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.listen_stop))
        }
    }
}
