package com.kreativekoala.echonote.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.kreativekoala.echonote.service.Funnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What is being read: only these fixed names ever reach analytics, never any text. */
enum class ListenSource(val key: String) { SUMMARY("summary"), SEGMENT("segment"), TRANSCRIPT("transcript"), PASSAGE("passage") }

/** [units] are the rows the user sees (key points, sentences, passages); each is spoken and highlighted as one. */
data class ListenRequest(
    val units: List<String>,
    val source: ListenSource,
    val locale: Locale,
    /** Where the highlight starts, e.g. the first sentence of a tapped paragraph. */
    val startUnit: Int = 0,
) {
    val key: String get() = source.key + ":" + units.hashCode() + ":" + locale.toLanguageTag()
}

enum class ListenPhase { Idle, Preparing, Speaking, Paused, Finished, Unavailable, Failed }

data class ListenUiState(
    val request: ListenRequest? = null,
    val phase: ListenPhase = ListenPhase.Idle,
    /** Index into [ListenRequest.units] currently spoken (or paused on), -1 when none. */
    val unit: Int = -1,
    val speed: Float = 1.0f,
    val unavailable: TtsUnavailable? = null,
    val failure: String? = null,
    val engineLabel: String? = null,
)

/**
 * Process-wide owner of "Listen". It drives [TtsEngine], follows audio focus (pauses for calls and other media),
 * and publishes a media session + notification so headset buttons and lock-screen / notification controls work.
 * It lives outside any screen, so speech keeps going if the sheet is closed or the screen turns off.
 */
class ListenController internal constructor(context: Context, val engine: TtsEngine) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = appContext.getSharedPreferences("listen_prefs", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(ListenUiState(speed = prefs.getFloat(KEY_SPEED, 1.0f)))
    val ui: StateFlow<ListenUiState> = _ui.asStateFlow()

    private var plan: ListenPlan? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var startedLogged = false
    private val media = ListenMediaSession(
        appContext,
        onPlay = { resume() }, onPause = { pause() }, onStop = { stop() },
        onNext = { next() }, onPrevious = { previous() },
    )

    init {
        scope.launch { engine.state.collect { onEngineState(it) } }
        scope.launch { engine.spokenIndex.collect { onSpokenIndex(it) } }
        scope.launch { engine.engine.collect { e -> _ui.value = _ui.value.copy(engineLabel = e?.label) } }
    }

    fun start(request: ListenRequest, voiceId: String? = null) {
        val newPlan = ListenPlan.of(request.units.filter { it.isNotBlank() }.ifEmpty { return })
        engine.stop()
        plan = newPlan
        startedLogged = false
        _ui.value = _ui.value.copy(
            request = request.copy(units = request.units.filter { it.isNotBlank() }),
            phase = ListenPhase.Preparing, unit = -1, unavailable = null, failure = null,
        )
        if (!requestFocus()) {
            _ui.value = _ui.value.copy(phase = ListenPhase.Failed, failure = "Another app is using the audio right now.")
            return
        }
        media.show(ListenPhase.Preparing, request.source)
        val startPiece = newPlan.firstPieceOf(request.startUnit.coerceAtLeast(0)).coerceAtLeast(0)
        scope.launch {
            engine.speakQueue(newPlan.pieces, startPiece, voiceId, _ui.value.speed, request.locale)
        }
    }

    fun pause() {
        engine.pause()
    }

    fun resume() {
        if (_ui.value.phase == ListenPhase.Paused) {
            if (requestFocus()) engine.resume()
        } else if (_ui.value.phase == ListenPhase.Finished || _ui.value.phase == ListenPhase.Idle) {
            _ui.value.request?.let { start(it.copy(startUnit = 0)) }
        }
    }

    fun togglePlayPause() {
        when (_ui.value.phase) {
            ListenPhase.Speaking -> pause()
            else -> resume()
        }
    }

    fun next() = jump(_ui.value.unit + 1)
    fun previous() = jump(_ui.value.unit - 1)

    /** Speak from [unit] (a row the user tapped, or the next/previous one). */
    fun jump(unit: Int) {
        val p = plan ?: return
        val req = _ui.value.request ?: return
        if (unit !in req.units.indices) return
        if (_ui.value.phase == ListenPhase.Paused || _ui.value.phase == ListenPhase.Speaking) {
            engine.skipTo(p.firstPieceOf(unit))
            _ui.value = _ui.value.copy(unit = unit)
        } else {
            start(req.copy(startUnit = unit))
        }
    }

    fun setSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        _ui.value = _ui.value.copy(speed = speed)
        engine.setSpeed(speed)
    }

    /** Stops speaking. The request is kept so the sheet can offer "Listen again". */
    fun stop() {
        engine.stop()
        abandonFocus()
        media.hide()
        if (_ui.value.phase != ListenPhase.Unavailable) {
            _ui.value = _ui.value.copy(phase = ListenPhase.Idle, unit = -1)
        }
    }

    /** Stops and forgets the request (sheet closed). */
    fun dismiss() {
        stop()
        _ui.value = ListenUiState(speed = _ui.value.speed, engineLabel = _ui.value.engineLabel)
        plan = null
    }

    private fun onEngineState(s: TtsState) {
        val cur = _ui.value
        val req = cur.request
        when (s) {
            is TtsState.Speaking -> {
                if (cur.phase != ListenPhase.Speaking) {
                    _ui.value = cur.copy(phase = ListenPhase.Speaking, failure = null, unavailable = null)
                    if (!startedLogged && req != null) { startedLogged = true; Funnel.listenStarted(req.source.key) }
                    if (req != null) media.show(ListenPhase.Speaking, req.source)
                }
            }
            TtsState.Paused -> if (req != null && cur.phase != ListenPhase.Idle) {
                _ui.value = cur.copy(phase = ListenPhase.Paused)
                media.show(ListenPhase.Paused, req.source)
            }
            TtsState.Finished -> if (req != null && cur.phase != ListenPhase.Finished) {
                _ui.value = cur.copy(phase = ListenPhase.Finished, unit = -1)
                Funnel.listenCompleted(req.source.key)
                abandonFocus()
                media.hide()
            }
            TtsState.Preparing -> if (req != null) _ui.value = cur.copy(phase = ListenPhase.Preparing)
            is TtsState.Unavailable -> if (req != null) {
                _ui.value = cur.copy(phase = ListenPhase.Unavailable, unavailable = s.reason)
                abandonFocus(); media.hide()
            }
            is TtsState.Failed -> if (req != null) {
                _ui.value = cur.copy(phase = ListenPhase.Failed, failure = s.message)
                abandonFocus(); media.hide()
            }
            TtsState.Idle, TtsState.Ready -> Unit
        }
    }

    private fun onSpokenIndex(piece: Int) {
        val p = plan ?: return
        if (piece < 0) return
        val unit = p.unitAt(piece)
        if (unit >= 0 && unit != _ui.value.unit) _ui.value = _ui.value.copy(unit = unit)
    }

    // MARK: - Audio focus

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocusGain = false; scope.launch { pause() } }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeOnFocusGain = _ui.value.phase == ListenPhase.Speaking
                        scope.launch { pause() }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        scope.launch { if (_ui.value.phase == ListenPhase.Paused) engine.resume() }
                    }
                }
            }
            .build().also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        resumeOnFocusGain = false
    }

    companion object {
        private const val KEY_SPEED = "speed"
        @Volatile private var INSTANCE: ListenController? = null

        fun get(context: Context): ListenController = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ListenController(context.applicationContext, SystemTtsEngine(context.applicationContext)).also { INSTANCE = it }
        }

        /** Stops speech if the controller exists. Used when a recording starts so the voice never leaks into it. */
        fun stopIfRunning() { INSTANCE?.stop() }
    }
}
