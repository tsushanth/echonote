package com.kreativekoala.echonote.ui.find

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.search.AskPassage
import com.kreativekoala.echonote.search.FindHit
import com.kreativekoala.echonote.search.SearchIndex
import com.kreativekoala.echonote.search.VoiceError
import com.kreativekoala.echonote.search.VoiceListener
import com.kreativekoala.echonote.search.VoiceQueryInput
import com.kreativekoala.echonote.search.VoiceUnavailable
import com.kreativekoala.echonote.tts.ListenRequest
import com.kreativekoala.echonote.tts.ListenSheet
import com.kreativekoala.echonote.tts.ListenSource
import com.kreativekoala.echonote.util.DateFormatting
import com.kreativekoala.echonote.util.TimeFormatting
import java.util.Locale

private sealed interface VoiceUi {
    data object Idle : VoiceUi
    data class Listening(val partial: String) : VoiceUi
    data class Message(val text: String) : VoiceUi
}

private fun stamp(ms: Long?): String? = ms?.let { TimeFormatting.formatDuration(it) }

/**
 * Library-wide Find (exact words, with a jump to the moment in the audio) and Ask (the passages most likely to answer
 * a question). Both run on the phone over the saved transcripts; Ask quotes passages and never writes an answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(
    viewModel: FindViewModel,
    onBack: () -> Unit,
    /** Opens [recordingId] in the player at [startMs] (null when the sentence has no time). */
    onOpen: (recordingId: String, startMs: Long?) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val entitlement by viewModel.entitlement.collectAsState()
    val speechLocale by viewModel.speechLocale.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var voice by remember { mutableStateOf<VoiceUi>(VoiceUi.Idle) }
    var listenPassage by rememberSaveable { mutableIntStateOf(-1) }
    var showPremiumSheet by rememberSaveable { mutableStateOf(false) }
    val voiceInput = remember { VoiceQueryInput(context) }
    DisposableEffect(Unit) { onDispose { voiceInput.cancel() } }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun startVoice() {
        val unavailable = voiceInput.unavailable()
        if (unavailable != null) {
            voice = VoiceUi.Message(
                context.getString(
                    if (unavailable == VoiceUnavailable.NeedsAndroid13) R.string.find_voice_old_android else R.string.find_voice_no_recognizer
                )
            )
            return
        }
        keyboard?.hide()
        voice = VoiceUi.Listening("")
        voiceInput.start(speechLocale, object : VoiceListener {
            override fun onListening() { voice = VoiceUi.Listening("") }
            override fun onPartial(text: String) { voice = VoiceUi.Listening(text) }
            override fun onResult(text: String) { voice = VoiceUi.Idle; viewModel.onVoiceResult(text) }
            override fun onError(error: VoiceError) {
                voice = VoiceUi.Message(
                    when (error) {
                        VoiceError.NoSpeech -> context.getString(R.string.find_voice_no_speech)
                        VoiceError.LanguageUnavailable -> context.getString(R.string.find_voice_language, speechLocale.getDisplayLanguage(Locale.getDefault()))
                        VoiceError.NoPermission -> context.getString(R.string.find_voice_permission)
                        VoiceError.Busy -> context.getString(R.string.find_voice_busy)
                        VoiceError.Other -> context.getString(R.string.find_voice_error)
                    }
                )
            }
        })
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else voice = VoiceUi.Message(context.getString(R.string.find_voice_permission))
    }

    fun onMicClick() {
        if (voice is VoiceUi.Listening) { voiceInput.cancel(); voice = VoiceUi.Idle; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.find_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = ui.mode == FindMode.FIND,
                        onClick = { viewModel.setMode(FindMode.FIND) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.find_mode_find)) }
                    SegmentedButton(
                        selected = ui.mode == FindMode.ASK,
                        onClick = { viewModel.setMode(FindMode.ASK) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.find_mode_ask)) }
                }

                val scopedRecordingId = ui.scope.recordingId
                if (scopedRecordingId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(stringResource(R.string.find_scope_one)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.setScope(FindScope()) },
                            label = { Text(stringResource(R.string.find_scope_all)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                val hint = when {
                    ui.mode == FindMode.FIND -> R.string.find_hint_find
                    ui.scope.recordingId != null -> R.string.find_hint_ask_one
                    else -> R.string.find_hint_ask
                }
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ui.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.find_clear))
                                }
                            }
                            IconButton(onClick = ::onMicClick) {
                                Icon(
                                    if (voice is VoiceUi.Listening) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = stringResource(
                                        if (voice is VoiceUi.Listening) R.string.find_voice_stop else R.string.find_voice_start
                                    ),
                                    tint = if (voice is VoiceUi.Listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.submit(); keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (ui.mode == FindMode.ASK) {
                    Button(
                        onClick = { viewModel.submit(); keyboard?.hide() },
                        enabled = ui.query.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.find_ask_button)) }
                }
                when (val v = voice) {
                    is VoiceUi.Listening -> Text(
                        v.partial.ifBlank { stringResource(R.string.find_voice_listening) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    is VoiceUi.Message -> Text(
                        v.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    VoiceUi.Idle -> Unit
                }
            }

            Spacer(Modifier.size(8.dp))
            when {
                !ui.indexReady -> StatusText(stringResource(R.string.find_building))
                !ui.hasTranscripts -> StatusText(stringResource(R.string.find_no_transcripts))
                ui.mode == FindMode.FIND -> FindResults(ui, onOpen = { hit ->
                    viewModel.resultOpened(); onOpen(hit.recordingId, hit.sentence.startMs)
                })
                else -> AskResults(
                    ui = ui,
                    showPremiumRow = entitlement.billingAvailable,
                    onPremiumRowShown = viewModel::premiumRowShown,
                    onPremiumClick = { viewModel.premiumSheetOpened(); showPremiumSheet = true },
                    onOpen = { p -> viewModel.resultOpened(); onOpen(p.recordingId, p.best.startMs) },
                    onListen = { listenPassage = it },
                )
            }
        }
    }

    if (listenPassage in ui.passages.indices && ui.mode == FindMode.ASK) {
        val p = ui.passages[listenPassage]
        ListenSheet(
            request = ListenRequest(p.sentences.map { it.text }, ListenSource.PASSAGE, speechLocale, startUnit = p.bestIndex.coerceAtLeast(0)),
            onDismiss = { listenPassage = -1 },
        )
    }

    if (showPremiumSheet) {
        PremiumSheet(isSubscriber = entitlement.isPremium, onDismiss = { showPremiumSheet = false })
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun highlighted(text: String, query: String, prefixLast: Boolean, question: Boolean): androidx.compose.ui.text.AnnotatedString {
    val background = MaterialTheme.colorScheme.tertiaryContainer
    val foreground = MaterialTheme.colorScheme.onTertiaryContainer
    return buildAnnotatedString {
    val ranges = SearchIndex.highlights(text, query, prefixLast, question)
    var at = 0
    for (r in ranges) {
        if (r.first < at) continue
        append(text.substring(at, r.first))
        // bold + underline + background: not colour alone
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                background = background,
                color = foreground,
            )
        ) { append(text.substring(r.first, r.last + 1)) }
        at = r.last + 1
    }
    if (at < text.length) append(text.substring(at))
    }
}

@Composable
private fun FindResults(ui: FindUiState, onOpen: (FindHit) -> Unit) {
    if (ui.query.isBlank()) { StatusText(stringResource(R.string.find_empty_find)); return }
    if (ui.searched && ui.hits.isEmpty()) { StatusText(stringResource(R.string.find_no_results, ui.query.trim())); return }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Text(
                if (ui.hits.size == 1) stringResource(R.string.find_result_count_one) else stringResource(R.string.find_result_count, ui.hits.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        itemsIndexed(ui.hits, key = { _, h -> h.recordingId + ":" + h.sentence.index }) { _, hit ->
            val time = stamp(hit.sentence.startMs)
            val openHint = stringResource(R.string.find_result_open_hint, time ?: stringResource(R.string.find_no_timestamp))
            val description = stringResource(
                R.string.find_result_item, hit.title, DateFormatting.formatRelativeDate(hit.dateCreated),
                time ?: stringResource(R.string.find_no_timestamp), hit.sentence.text
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = openHint) { onOpen(hit) }
                    .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ResultHeader(hit.title, hit.dateCreated, time, hit.sentence.speaker)
                    Text(highlighted(hit.sentence.text, ui.query, prefixLast = true, question = false), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(title: String, date: Long, time: String?, speaker: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            Text(
                DateFormatting.formatRelativeDate(date) + (speaker?.let { "  ·  " + stringResource(R.string.find_speaker, it + 1) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (time != null) {
            Text(
                time,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AskResults(
    ui: FindUiState,
    showPremiumRow: Boolean,
    onPremiumRowShown: () -> Unit,
    onPremiumClick: () -> Unit,
    onOpen: (AskPassage) -> Unit,
    onListen: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ask_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(R.string.ask_label_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        if (!ui.asked && ui.passages.isEmpty()) {
            item { StatusText(stringResource(R.string.find_empty_ask)) }
        } else if (ui.asked && ui.passages.isEmpty()) {
            item { StatusText(stringResource(R.string.ask_no_results, ui.query.trim())) }
        }
        itemsIndexed(ui.passages, key = { _, p -> p.recordingId + ":" + p.best.index }) { index, p ->
            val time = stamp(p.best.startMs)
            val description = stringResource(
                R.string.ask_passage_item, p.title, DateFormatting.formatRelativeDate(p.dateCreated),
                time ?: stringResource(R.string.find_no_timestamp), p.text
            )
            val openHint = stringResource(R.string.ask_open_passage, time ?: stringResource(R.string.find_no_timestamp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = openHint) { onOpen(p) }
                            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ResultHeader(p.title, p.dateCreated, time, p.best.speaker)
                        Text(highlighted(p.text, ui.query, prefixLast = false, question = true), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onListen(index) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.ask_listen_passage))
                        }
                    }
                }
            }
        }
        if (showPremiumRow) {
            item(key = "premium") {
                LaunchedEffect(Unit) { onPremiumRowShown() }
                val rowDescription = stringResource(R.string.premium_row_description)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClick = onPremiumClick)
                        .semantics(mergeDescendants = true) { contentDescription = rowDescription; role = Role.Button },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.premium_row_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.premium_row_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSheet(isSubscriber: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.premium_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.premium_sheet_body), style = MaterialTheme.typography.bodyMedium)
            if (isSubscriber) Text(stringResource(R.string.premium_sheet_subscriber), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.premium_sheet_free_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.premium_sheet_close)) }
            Spacer(Modifier.size(8.dp))
        }
    }
}
