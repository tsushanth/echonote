package com.kreativekoala.echonote.ui.playback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.toJson
import com.kreativekoala.echonote.data.model.toTranscriptSegments
import com.kreativekoala.echonote.data.repository.RecordingRepository
import com.kreativekoala.echonote.data.repository.SettingsRepository
import com.kreativekoala.echonote.service.AudioEditorService
import com.kreativekoala.echonote.service.AudioPlayerService
import com.kreativekoala.ratingkit.RatingKit
import com.kreativekoala.echonote.service.applyParagraphEdit
import com.kreativekoala.echonote.service.transcriptText
import com.kreativekoala.echonote.service.TranscriptionResult
import com.kreativekoala.echonote.service.TranscriptionService
import com.kreativekoala.echonote.service.summary.SummaryManager
import com.kreativekoala.echonote.service.summary.SummaryUiState
import com.kreativekoala.echonote.util.ExportFormat
import com.kreativekoala.echonote.util.buildSubtitleCues
import com.kreativekoala.echonote.util.toSrt
import com.kreativekoala.echonote.util.toVtt
import com.kreativekoala.echonote.worker.ContributionUploadWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerService: AudioPlayerService,
    private val recordingRepository: RecordingRepository,
    private val transcriptionService: TranscriptionService,
    private val audioEditorService: AudioEditorService,
    private val settingsRepository: SettingsRepository,
    private val summaryManager: SummaryManager
) : ViewModel() {

    val isPlaying = playerService.isPlaying
    val currentPositionMs = playerService.currentPositionMs
    val durationMs = playerService.durationMs
    val playbackSpeed = playerService.playbackSpeed
    val skipSilence = playerService.skipSilence

    private val _currentRecording = MutableStateFlow<Recording?>(null)
    val currentRecording: StateFlow<Recording?> = _currentRecording

    private val _waveformSamples = MutableStateFlow(FloatArray(0))
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples

    private val _showPlayback = MutableStateFlow(false)
    val showPlayback: StateFlow<Boolean> = _showPlayback

    // Transcription state
    val isTranscribing = transcriptionService.isTranscribing
    val transcriptionProgress = transcriptionService.progress
    val transcriptionStatus = transcriptionService.statusMessage
    val partialTranscript = transcriptionService.partialTranscript

    private val _transcriptionResult = MutableStateFlow<String?>(null)
    val transcriptionResult: StateFlow<String?> = _transcriptionResult

    private val _transcriptionError = MutableStateFlow<String?>(null)
    val transcriptionError: StateFlow<String?> = _transcriptionError

    private val _transcriptSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val transcriptSegments: StateFlow<List<TranscriptSegment>> = _transcriptSegments

    // Recordings whose transcript the user has corrected by hand; re-transcribing would overwrite it.
    private val editedPrefs = context.getSharedPreferences("transcript_edits", Context.MODE_PRIVATE)
    private val _hasEdits = MutableStateFlow(false)
    val hasEdits: StateFlow<Boolean> = _hasEdits

    val activeSegmentIndex: StateFlow<Int> = currentPositionMs.map { posMs ->
        val segs = _transcriptSegments.value
        if (segs.isEmpty()) return@map -1
        var active = -1
        for (i in segs.indices) {
            if (posMs >= segs[i].startMs) active = i
            else break
        }
        active
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _triggerReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerReview: SharedFlow<Unit> = _triggerReview

    // Trim state
    private val _isTrimming = MutableStateFlow(false)
    val isTrimming: StateFlow<Boolean> = _isTrimming

    private val _trimStart = MutableStateFlow(0f)
    val trimStart: StateFlow<Float> = _trimStart

    private val _trimEnd = MutableStateFlow(1f)
    val trimEnd: StateFlow<Float> = _trimEnd

    private val _showTrimMode = MutableStateFlow(false)
    val showTrimMode: StateFlow<Boolean> = _showTrimMode

    // Summary ("Key points") state; see SummaryManager. Stored beside the app data, not in Room.
    private val _summaryState = MutableStateFlow<SummaryUiState>(SummaryUiState.None)
    val summaryState: StateFlow<SummaryUiState> = _summaryState

    private fun loadStoredSummary(recording: Recording) {
        _summaryState.value = SummaryUiState.None
        viewModelScope.launch {
            val state = summaryManager.loadStored(recording.id, recording.transcript)
            if (_currentRecording.value?.id == recording.id) _summaryState.value = state
        }
    }

    fun summarize() {
        val rec = _currentRecording.value ?: return
        val text = _transcriptionResult.value?.takeIf { it.isNotBlank() } ?: return
        if (_summaryState.value == SummaryUiState.Working) return
        _summaryState.value = SummaryUiState.Working
        val segments = _transcriptSegments.value
        viewModelScope.launch {
            val language = rec.contributionLanguage ?: settingsRepository.transcriptionLanguage.first()
            val state = try {
                summaryManager.generate(rec.id, text, language, segments)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                SummaryUiState.None
            }
            if (_currentRecording.value?.id == rec.id) _summaryState.value = state
        }
    }

    fun loadRecording(recording: Recording) {
        _currentRecording.value = recording
        loadStoredSummary(recording)
        _showPlayback.value = true
        _transcriptionResult.value = recording.transcript
        _transcriptSegments.value = recording.transcriptSegmentsJson?.toTranscriptSegments() ?: emptyList()
        _hasEdits.value = editedPrefs.getStringSet(KEY_EDITED, emptySet())?.contains(recording.id) == true
        _showTrimMode.value = false
        _trimStart.value = 0f
        _trimEnd.value = 1f
        playerService.play(recording.fileUri)

        viewModelScope.launch(Dispatchers.IO) {
            val waveform = playerService.generateWaveformData(recording.fileUri)
            _waveformSamples.value = waveform
        }
    }

    fun togglePlayPause() = playerService.togglePlayPause()

    fun seekTo(positionMs: Long) = playerService.seekTo(positionMs)

    fun seekToProgress(progress: Float) = playerService.seekToProgress(progress)

    fun skipForward() = playerService.skipForward()

    fun skipBackward() = playerService.skipBackward()

    fun increaseSpeed() {
        playerService.setPlaybackSpeed(playerService.playbackSpeed.value + 0.25f)
    }

    fun decreaseSpeed() {
        playerService.setPlaybackSpeed(playerService.playbackSpeed.value - 0.25f)
    }

    fun setSpeed(speed: Float) = playerService.setPlaybackSpeed(speed)

    fun toggleSkipSilence() {
        playerService.setSkipSilence(!playerService.skipSilence.value)
    }

    fun stop() {
        playerService.stop()
        _showPlayback.value = false
    }

    fun dismiss() {
        playerService.release()
        _showPlayback.value = false
        _currentRecording.value = null
        _waveformSamples.value = FloatArray(0)
        _transcriptionResult.value = null
        _transcriptionError.value = null
        _showTrimMode.value = false
        _summaryState.value = SummaryUiState.None
    }

    // Transcription
    fun transcribe() {
        val rec = _currentRecording.value ?: return
        if (transcriptionService.isTranscribing.value) return

        _transcriptionError.value = null

        viewModelScope.launch(Dispatchers.Main) {
            when (val result = transcriptionService.transcribe(rec.fileUri)) {
                is TranscriptionResult.Success -> {
                    _transcriptionResult.value = result.text
                    _transcriptSegments.value = result.segments
                    _transcriptionError.value = null
                    setEdited(rec.id, false)
                    val segJson = if (result.segments.isNotEmpty()) result.segments.toJson() else null
                    // Snapshot the live global transcription language onto the recording NOW,
                    // rather than reading it later at upload time.
                    val languageAtTranscribeTime = settingsRepository.transcriptionLanguage.first()
                    val updatedRec = rec.copy(
                        transcript = result.text,
                        transcriptSegmentsJson = segJson,
                        contributionLanguage = languageAtTranscribeTime
                    )
                    recordingRepository.updateRecording(updatedRec)
                    _currentRecording.value = updatedRec
                    loadStoredSummary(updatedRec) // marks an existing summary as out of date
                    // Magic moment: the user now has their finished transcript. RatingKit decides whether to ask (it
                    // needs several moments and a few days of use first); the screen calls it once this is visible.
                    _triggerReview.tryEmit(Unit)
                    val contributeVoiceData = settingsRepository.contributeVoiceData.first()
                    enqueueContributionUpload(updatedRec.id)
                    // Auto-copy transcript
                    if (settingsRepository.autoCopyTranscript.first()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcript", result.text))
                    }
                    // Auto-delete audio. If the user also opted into voice-data
                    // contribution, do NOT delete synchronously here: the upload is
                    // enqueued on WorkManager and may run well after this call
                    // returns, so deleting now would race the worker off the file
                    // (WorkManager already no-ops silently if the file is gone,
                    // which is exactly how a contribution used to vanish silently).
                    // Instead mark the recording pendingContribution so the worker
                    // performs the deletion itself once the upload has actually
                    // succeeded or is otherwise definitively moot (mirrors the
                    // worker's own upload preconditions).
                    if (settingsRepository.autoDeleteAudioAfterTranscription.first()) {
                        val deferToWorker = contributeVoiceData && result.text.isNotBlank()
                        if (deferToWorker) {
                            val deferredRec = updatedRec.copy(pendingContribution = true)
                            recordingRepository.updateRecording(deferredRec)
                            _currentRecording.value = deferredRec
                        } else {
                            withContext(Dispatchers.IO) {
                                try { File(rec.fileUri).delete() } catch (_: Exception) {}
                            }
                        }
                    }
                }
                is TranscriptionResult.Error -> {
                    RatingKit.reportError() // stay quiet for a while after a visible failure
                    _transcriptionError.value = result.message
                }
            }
            // Transcription releases the player to free the decoder; bring it back, paused,
            // unless the user left this recording or the audio was auto-deleted.
            if (_showPlayback.value && _currentRecording.value?.id == rec.id && File(rec.fileUri).exists()) {
                playerService.play(rec.fileUri, autoPlay = false)
            }
        }
    }

    /**
     * Replaces words [first] until [end] with [text], keeping timings usable, and saves both the word list
     * and the plain transcript. Returns false (and changes nothing) when the text is blank or the range is stale.
     */
    fun editParagraph(first: Int, end: Int, text: String): Boolean {
        val rec = _currentRecording.value ?: return false
        if (transcriptionService.isTranscribing.value) return false
        val edited = applyParagraphEdit(_transcriptSegments.value, first, end, text) ?: return false
        saveEdit(rec, transcriptText(edited), edited)
        return true
    }

    /** Edits the plain text of an older recording that has no word timings. Returns false when blank. */
    fun editPlainTranscript(text: String): Boolean {
        val rec = _currentRecording.value ?: return false
        if (transcriptionService.isTranscribing.value || text.isBlank()) return false
        saveEdit(rec, text.trim(), emptyList())
        return true
    }

    private fun saveEdit(rec: Recording, text: String, segments: List<TranscriptSegment>) {
        _transcriptionResult.value = text
        _transcriptSegments.value = segments
        val updated = rec.copy(
            transcript = text,
            transcriptSegmentsJson = if (segments.isNotEmpty()) segments.toJson() else rec.transcriptSegmentsJson,
            dateModified = System.currentTimeMillis()
        )
        _currentRecording.value = updated
        setEdited(rec.id, true)
        viewModelScope.launch(Dispatchers.IO) { recordingRepository.updateRecording(updated) }
    }

    private fun setEdited(recordingId: String, edited: Boolean) {
        val ids = editedPrefs.getStringSet(KEY_EDITED, emptySet()).orEmpty().toMutableSet()
        if (edited) ids.add(recordingId) else ids.remove(recordingId)
        editedPrefs.edit().putStringSet(KEY_EDITED, ids).apply()
        if (_currentRecording.value?.id == recordingId) _hasEdits.value = edited
    }

    fun clearTranscriptionError() {
        _transcriptionError.value = null
    }

    // Voice-data contribution: enqueue the opt-in upload right after a transcript now
    // exists and a language was captured. The worker itself checks the live
    // contributeVoiceData setting and no-ops if the user hasn't opted in (or opted out
    // since) — Wi-Fi only for now; a cellular toggle is future work.
    private fun enqueueContributionUpload(recordingId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<ContributionUploadWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(ContributionUploadWorker.KEY_RECORDING_ID, recordingId).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun isTranscriptionAvailable(): Boolean = transcriptionService.isAvailable()

    // Trim
    fun toggleTrimMode() {
        _showTrimMode.value = !_showTrimMode.value
        if (_showTrimMode.value) {
            _trimStart.value = 0f
            _trimEnd.value = 1f
        }
    }

    fun setTrimRange(start: Float, end: Float) {
        val safeStart = start.coerceIn(0f, 1f)
        val safeEnd = end.coerceIn(0f, 1f)
        if (safeStart < safeEnd) {
            _trimStart.value = safeStart
            _trimEnd.value = safeEnd
        }
    }

    fun performTrim() {
        val rec = _currentRecording.value ?: return
        val duration = durationMs.value
        if (duration <= 0) return

        _isTrimming.value = true

        viewModelScope.launch {
            val startMs = (_trimStart.value * duration).toLong()
            val endMs = (_trimEnd.value * duration).toLong()

            val inputFile = File(rec.fileUri)
            val ext = inputFile.extension
            val outputFile = File(
                inputFile.parentFile,
                "${inputFile.nameWithoutExtension}_trimmed.$ext"
            )

            // Trim on IO thread
            val success = withContext(Dispatchers.IO) {
                audioEditorService.trimAudio(
                    inputUri = rec.fileUri,
                    outputUri = outputFile.absolutePath,
                    startTimeMs = startMs,
                    endTimeMs = endMs
                )
            }

            if (success) {
                // Stop playback on main thread (ExoPlayer requirement)
                playerService.stop()

                // File operations on IO thread
                withContext(Dispatchers.IO) {
                    inputFile.delete()
                    outputFile.renameTo(inputFile)
                }

                // Update recording metadata
                val newDuration = endMs - startMs
                val newSize = withContext(Dispatchers.IO) { inputFile.length() }
                val updated = rec.copy(
                    duration = newDuration,
                    fileSize = newSize,
                    dateModified = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    recordingRepository.updateRecording(updated)
                }
                _currentRecording.value = updated

                // Reload on main thread, waveform generation on IO
                playerService.play(rec.fileUri)
                val waveform = withContext(Dispatchers.IO) {
                    playerService.generateWaveformData(rec.fileUri)
                }
                _waveformSamples.value = waveform
            }

            _isTrimming.value = false
            _showTrimMode.value = false
        }
    }

    fun exportTranscript(format: ExportFormat, context: Context) {
        val text = _transcriptionResult.value ?: return
        val rec = _currentRecording.value ?: return
        val duration = durationMs.value.takeIf { it > 0 } ?: 1L
        // Recordings transcribed before word timings were stored fall back to even spacing.
        val timedCues = _transcriptSegments.value.takeIf { it.isNotEmpty() }?.let { buildSubtitleCues(it) }

        viewModelScope.launch(Dispatchers.IO) {
            val (fileName, content) = when (format) {
                is ExportFormat.TXT -> Pair("transcript.txt", text)
                is ExportFormat.SRT -> Pair(
                    "transcript.srt",
                    timedCues?.toSrt() ?: buildSrt(text, duration)
                )
                is ExportFormat.VTT -> Pair(
                    "transcript.vtt",
                    timedCues?.toVtt() ?: buildVtt(text, duration)
                )
            }
            val outFile = File(context.cacheDir, fileName)
            outFile.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Export Transcript")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun buildSrt(text: String, durationMs: Long): String {
        val sentences = text.split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return ""
        val segDuration = durationMs / sentences.size
        val sb = StringBuilder()
        sentences.forEachIndexed { i, sentence ->
            val start = i * segDuration
            val end = start + segDuration
            sb.appendLine(i + 1)
            sb.appendLine("${formatSrtTime(start)} --> ${formatSrtTime(end)}")
            sb.appendLine(sentence.trim())
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildVtt(text: String, durationMs: Long): String {
        val sentences = text.split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return "WEBVTT\n"
        val segDuration = durationMs / sentences.size
        val sb = StringBuilder("WEBVTT\n\n")
        sentences.forEachIndexed { i, sentence ->
            val start = i * segDuration
            val end = start + segDuration
            sb.appendLine("${formatVttTime(start)} --> ${formatVttTime(end)}")
            sb.appendLine(sentence.trim())
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun formatSrtTime(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d.%03d".format(h, m, s, millis)
    }

    private companion object {
        const val KEY_EDITED = "edited_ids"
    }

    override fun onCleared() {
        super.onCleared()
        playerService.release()
    }
}
