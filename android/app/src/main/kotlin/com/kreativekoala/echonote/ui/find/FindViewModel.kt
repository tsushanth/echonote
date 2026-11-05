package com.kreativekoala.echonote.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.billing.EntitlementState
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.search.AskPassage
import com.kreativekoala.echonote.search.FindHit
import com.kreativekoala.echonote.search.IndexState
import com.kreativekoala.echonote.search.SearchIndex
import com.kreativekoala.echonote.search.TranscriptSearchRepository
import com.kreativekoala.echonote.service.Funnel
import com.kreativekoala.echonote.service.PremiumManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class FindMode { FIND, ASK }

/** Limits a search to one recording; both null = all recordings. */
data class FindScope(val recordingId: String? = null, val title: String? = null) {
    val key: String get() = if (recordingId == null) "all" else "recording"
}

data class FindUiState(
    val mode: FindMode = FindMode.FIND,
    val scope: FindScope = FindScope(),
    val query: String = "",
    /** How the current query was entered: "typed" or "voice". */
    val input: String = "typed",
    val indexReady: Boolean = false,
    /** True once the index is ready and holds at least one transcript. */
    val hasTranscripts: Boolean = false,
    val hits: List<FindHit> = emptyList(),
    val passages: List<AskPassage> = emptyList(),
    /** Ask runs on submit, not per keystroke; false until the first submit for the current text. */
    val asked: Boolean = false,
    val searched: Boolean = false,
)

@HiltViewModel
class FindViewModel @Inject constructor(
    private val repository: TranscriptSearchRepository,
    private val premiumManager: PremiumManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FindUiState())
    val ui: StateFlow<FindUiState> = _ui.asStateFlow()

    /** Billing availability decides whether the "AI answer (Premium)" row exists at all. */
    val entitlement: StateFlow<EntitlementState> = premiumManager.state

    val speechLocale: StateFlow<Locale> = settingsRepository.transcriptionLanguage
        .map { Locale.forLanguageTag(it.bcp47Tag) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Locale.getDefault())

    private var searchJob: Job? = null
    private var logJob: Job? = null
    private var premiumRowLogged = false
    private var index: SearchIndex? = null

    init {
        viewModelScope.launch {
            repository.state.collect { s ->
                when (s) {
                    is IndexState.Ready -> {
                        index = s.index
                        _ui.value = _ui.value.copy(indexReady = true, hasTranscripts = s.index.hasContent())
                        recompute(delayMs = 0)
                    }
                    else -> _ui.value = _ui.value.copy(indexReady = false)
                }
            }
        }
    }

    /** Called each time the screen is opened. Keeps the last query when coming back from a result, resets otherwise. */
    fun open(scope: FindScope, mode: FindMode, fresh: Boolean) {
        repository.ensureStarted()
        viewModelScope.launch { premiumManager.refresh() }
        if (fresh) {
            premiumRowLogged = false
            _ui.value = _ui.value.copy(
                scope = scope, mode = mode, query = "", input = "typed", hits = emptyList(), passages = emptyList(),
                asked = false, searched = false,
            )
        }
    }

    fun setMode(mode: FindMode) {
        if (_ui.value.mode == mode) return
        _ui.value = _ui.value.copy(mode = mode, hits = emptyList(), passages = emptyList(), asked = false, searched = false)
        if (mode == FindMode.FIND) recompute(delayMs = 0)
        else if (_ui.value.query.isNotBlank()) submitAsk(_ui.value.input)
    }

    fun setScope(scope: FindScope) {
        _ui.value = _ui.value.copy(scope = scope, asked = false)
        if (_ui.value.mode == FindMode.FIND) recompute(delayMs = 0)
        else if (_ui.value.query.isNotBlank()) submitAsk(_ui.value.input)
    }

    fun setQuery(text: String) {
        _ui.value = _ui.value.copy(query = text, input = "typed", asked = false)
        if (_ui.value.mode == FindMode.FIND) recompute(delayMs = TYPING_DEBOUNCE_MS)
    }

    /** IME "search" action or the Ask button. */
    fun submit() {
        if (_ui.value.mode == FindMode.ASK) submitAsk(_ui.value.input) else recompute(delayMs = 0)
    }

    /** A voice query was recognised on the device. */
    fun onVoiceResult(text: String) {
        _ui.value = _ui.value.copy(query = text, input = "voice", asked = false)
        if (_ui.value.mode == FindMode.ASK) submitAsk("voice")
        else {
            recompute(delayMs = 0)
            logFindAfterSearch("voice", immediate = true)
        }
    }

    private fun recompute(delayMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            if (delayMs > 0) delay(delayMs)
            val state = _ui.value
            if (state.mode != FindMode.FIND) return@launch
            val idx = index
            val hits = if (idx == null || state.query.isBlank()) emptyList()
            else idx.find(state.query, prefixLast = true, recordingId = state.scope.recordingId)
            _ui.value = _ui.value.copy(hits = hits, searched = state.query.isNotBlank())
            if (state.input == "typed" && state.query.trim().length >= 2) logFindAfterSearch("typed", immediate = false)
        }
    }

    /** Typed searches are logged once the user stops typing for a second; the count is bucketed, the text is never sent. */
    private fun logFindAfterSearch(input: String, immediate: Boolean) {
        logJob?.cancel()
        logJob = viewModelScope.launch {
            if (!immediate) delay(LOG_IDLE_MS) else delay(250) // let the voice search finish first
            val s = _ui.value
            if (s.mode == FindMode.FIND && s.query.isNotBlank()) Funnel.findQuery(input, s.hits.size)
        }
    }

    private fun submitAsk(input: String) {
        val state = _ui.value
        if (state.query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val passages = index?.ask(state.query, state.scope.recordingId).orEmpty()
            _ui.value = _ui.value.copy(passages = passages, asked = true)
            Funnel.askQuery(input, passages.size)
        }
    }

    fun resultOpened() {
        val s = _ui.value
        if (s.mode == FindMode.ASK) Funnel.askResultOpened(s.scope.key) else Funnel.findResultOpened(s.scope.key)
    }

    fun premiumRowShown() {
        if (premiumRowLogged) return
        premiumRowLogged = true
        Funnel.premiumRowViewed()
    }

    fun premiumSheetOpened() = Funnel.premiumSheetOpened()

    private companion object {
        const val TYPING_DEBOUNCE_MS = 120L
        const val LOG_IDLE_MS = 1_000L
    }
}
