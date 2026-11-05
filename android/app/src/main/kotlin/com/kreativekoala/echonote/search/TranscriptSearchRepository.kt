package com.kreativekoala.echonote.search

import com.kreativekoala.echonote.data.local.RecordingDao
import com.kreativekoala.echonote.data.local.TranscriptIndexRow
import com.kreativekoala.echonote.data.model.toTranscriptSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** State of the in-memory index; [Building] shows a spinner only the first time. */
sealed interface IndexState {
    data object NotStarted : IndexState
    data object Building : IndexState
    data class Ready(val index: SearchIndex) : IndexState
}

/**
 * Keeps one [SearchIndex] over all non-deleted, transcribed recordings, rebuilt whenever a transcript is added,
 * edited or deleted. Everything stays in memory in this process: nothing is written to disk or sent anywhere. Parsed
 * sentences are cached per recording and reused when only other recordings changed.
 */
@Singleton
class TranscriptSearchRepository @Inject constructor(private val dao: RecordingDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<IndexState>(IndexState.NotStarted)
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private class Cached(val key: String, val recording: IndexedRecording)
    private val cache = HashMap<String, Cached>()
    @Volatile private var started = false

    /** Starts watching the database the first time search is opened. Cheap to call again. */
    fun ensureStarted() {
        if (started) return
        started = true
        _state.value = IndexState.Building
        scope.launch {
            dao.transcriptIndexRows().collectLatest { rows ->
                _state.value = IndexState.Ready(build(rows))
            }
        }
    }

    private fun build(rows: List<TranscriptIndexRow>): SearchIndex {
        val live = HashSet<String>()
        val list = rows.map { row ->
            live += row.id
            val key = "${row.title}|${row.transcript.hashCode()}|${row.transcriptSegmentsJson?.hashCode()}"
            val hit = cache[row.id]
            if (hit != null && hit.key == key) hit.recording
            else toIndexed(row).also { cache[row.id] = Cached(key, it) }
        }
        cache.keys.retainAll(live)
        return SearchIndex(list)
    }

    internal companion object {
        fun toIndexed(row: TranscriptIndexRow): IndexedRecording {
            val words = row.transcriptSegmentsJson?.toTranscriptSegments().orEmpty()
            val sentences = if (words.isNotEmpty()) Sentences.fromWords(words) else Sentences.fromPlainText(row.transcript.orEmpty())
            return IndexedRecording(row.id, row.title, row.dateCreated, sentences)
        }
    }
}
