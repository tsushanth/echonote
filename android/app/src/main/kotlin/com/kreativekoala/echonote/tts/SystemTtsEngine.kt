package com.kreativekoala.echonote.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Wraps Android's built-in [TextToSpeech] with three rules that keep ClearVoice's "audio and text never leave the
 * phone" promise true:
 *  1. The engine is chosen explicitly ([TtsEnginePicker]: Google, then Samsung, then others), never the phone's default.
 *  2. Only installed offline voices are used (network voices and voices whose data is not installed are filtered out).
 *  3. When no engine can speak the language offline, the state becomes [TtsState.Unavailable] so the UI can say why.
 *     There is no server fallback.
 *
 * Speech is queued item by item (a small window ahead), so [spokenIndex] tracks the item being spoken, and pause,
 * resume, skip and speed changes work at item granularity.
 */
class SystemTtsEngine(private val context: Context) : TtsEngine {

    companion object {
        private const val TAG = "SystemTtsEngine"
        private const val INIT_TIMEOUT_MS = 6_000L
        /** How many items are handed to the engine ahead of the one being spoken. */
        private const val WINDOW = 12
        private const val NOT_INSTALLED = "notInstalled"
    }

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    override val voices: StateFlow<List<TtsVoice>> = _voices.asStateFlow()

    private val _spokenIndex = MutableStateFlow(-1)
    override val spokenIndex: StateFlow<Int> = _spokenIndex.asStateFlow()

    private val _engine = MutableStateFlow<TtsEngineInfo?>(null)
    override val engine: StateFlow<TtsEngineInfo?> = _engine.asStateFlow()

    private var tts: TextToSpeech? = null
    private var preparedTag: String? = null
    private var offlineVoices: List<Voice> = emptyList()

    // Queue state. Touched from the main thread and from TTS callback threads, so guarded by [lock].
    private val lock = Any()
    private var items: List<String> = emptyList()
    private var nextToQueue = 0
    private var currentIndex = 0
    private var voiceId: String? = null
    private var speed = 1.0f
    private val session = AtomicInteger(0)

    override suspend fun prepare(locale: Locale) {
        val tag = locale.toLanguageTag()
        if (tts != null && preparedTag == tag) return
        _state.value = TtsState.Preparing
        shutdownTts()

        val packages = installedEnginePackages()
        if (packages.isEmpty()) {
            _state.value = TtsState.Unavailable(TtsUnavailable.NoEngine)
            return
        }

        var problem: TtsUnavailable? = null
        var anyInitialised = false
        for (pkg in TtsEnginePicker.order(packages)) {
            val candidate = initEngine(pkg) ?: continue
            anyInitialised = true
            when (val check = check(candidate, locale, pkg)) {
                is Check.Ok -> {
                    adopt(candidate, pkg, locale, check.voices)
                    return
                }
                is Check.Problem -> {
                    // Report the problem of the most preferred engine that started.
                    if (problem == null) problem = check.reason
                    try { candidate.shutdown() } catch (_: Exception) { }
                }
            }
        }
        _state.value = TtsState.Unavailable(problem ?: if (anyInitialised) TtsUnavailable.LanguageNotSupported(tag) else TtsUnavailable.InitFailed)
    }

    override suspend fun speakQueue(items: List<String>, startIndex: Int, voiceId: String?, speed: Float, locale: Locale) {
        prepare(locale)
        val engine = tts ?: return // prepare() already set Unavailable
        if (items.isEmpty()) return
        synchronized(lock) {
            this.items = items
            this.voiceId = voiceId
            this.speed = speed.coerceIn(0.5f, 2.0f)
            applyVoice(engine)
            engine.setSpeechRate(this.speed)
            startFromLocked(engine, startIndex.coerceIn(0, items.lastIndex))
        }
    }

    override fun pause() {
        val engine = tts ?: return
        synchronized(lock) {
            if (_state.value !is TtsState.Speaking) return
            session.incrementAndGet() // ignore callbacks from the interrupted utterance
            currentIndex = _spokenIndex.value.coerceAtLeast(0)
            engine.stop()
            _state.value = TtsState.Paused
        }
    }

    override fun resume() {
        val engine = tts ?: return
        synchronized(lock) {
            if (_state.value != TtsState.Paused || items.isEmpty()) return
            startFromLocked(engine, currentIndex.coerceIn(0, items.lastIndex))
        }
    }

    override fun skipTo(index: Int) {
        val engine = tts ?: return
        synchronized(lock) {
            if (items.isEmpty()) return
            val i = index.coerceIn(0, items.lastIndex)
            if (_state.value == TtsState.Paused) {
                currentIndex = i
                _spokenIndex.value = i
            } else {
                startFromLocked(engine, i)
            }
        }
    }

    override fun setSpeed(speed: Float) {
        val engine = tts
        synchronized(lock) {
            this.speed = speed.coerceIn(0.5f, 2.0f)
            engine?.setSpeechRate(this.speed)
            if (engine != null && _state.value is TtsState.Speaking && items.isNotEmpty()) {
                startFromLocked(engine, _spokenIndex.value.coerceAtLeast(0))
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            session.incrementAndGet()
            try { tts?.stop() } catch (_: Exception) { }
            _spokenIndex.value = -1
            if (_state.value !is TtsState.Unavailable) _state.value = if (tts != null) TtsState.Idle else _state.value
        }
    }

    override fun release() {
        synchronized(lock) { session.incrementAndGet() }
        shutdownTts()
        _spokenIndex.value = -1
        _state.value = TtsState.Idle
    }

    // MARK: - Engine selection

    private fun installedEnginePackages(): List<String> = try {
        context.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_ALL)
            .mapNotNull { it.serviceInfo?.packageName }
    } catch (t: Throwable) {
        Log.w(TAG, "querying TTS engines failed", t)
        emptyList()
    }

    private suspend fun initEngine(pkg: String): TextToSpeech? {
        var created: TextToSpeech? = null
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont: CancellableContinuation<Boolean> ->
                try {
                    created = TextToSpeech(context.applicationContext, { status ->
                        if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS)
                    }, pkg)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
        if (ok != true) {
            try { created?.shutdown() } catch (_: Exception) { }
            return null
        }
        return created
    }

    private sealed interface Check {
        data class Ok(val voices: List<Voice>) : Check
        data class Problem(val reason: TtsUnavailable) : Check
    }

    private fun check(candidate: TextToSpeech, locale: Locale, pkg: String): Check {
        val tag = locale.toLanguageTag()
        val availability = try { candidate.isLanguageAvailable(locale) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED) return Check.Problem(TtsUnavailable.LanguageNotSupported(tag))
        if (availability == TextToSpeech.LANG_MISSING_DATA) return Check.Problem(TtsUnavailable.MissingVoiceData(pkg, tag))
        val all: Set<Voice>? = try { candidate.voices } catch (_: Exception) { null }
        if (all == null) return Check.Ok(emptyList()) // very old engines do not list voices; trust isLanguageAvailable
        val usable = all.filter {
            it.locale.language.equals(locale.language, ignoreCase = true) &&
                !it.isNetworkConnectionRequired &&
                NOT_INSTALLED !in it.features
        }
        return if (usable.isEmpty()) Check.Problem(TtsUnavailable.MissingVoiceData(pkg, tag)) else Check.Ok(usable)
    }

    private fun adopt(candidate: TextToSpeech, pkg: String, locale: Locale, usable: List<Voice>) {
        tts = candidate
        preparedTag = locale.toLanguageTag()
        offlineVoices = usable.sortedWith(
            compareByDescending<Voice> { it.locale.toLanguageTag().equals(locale.toLanguageTag(), true) }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
        )
        _voices.value = offlineVoices.map { v ->
            TtsVoice(
                id = v.name,
                displayName = humanizeVoiceName(v),
                locale = v.locale.toLanguageTag(),
                isFemale = inferFemale(v),
                isOnDevice = true,
            )
        }
        candidate.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        try { candidate.language = locale } catch (_: Exception) { }
        candidate.setOnUtteranceProgressListener(listener)
        _engine.value = TtsEngineInfo(pkg, engineLabel(pkg))
        _state.value = TtsState.Ready
    }

    private fun engineLabel(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun shutdownTts() {
        try { tts?.stop(); tts?.shutdown() } catch (t: Throwable) { Log.w(TAG, "shutdown threw", t) }
        tts = null
        preparedTag = null
        offlineVoices = emptyList()
        _voices.value = emptyList()
        _engine.value = null
    }

    // MARK: - Speaking

    private fun applyVoice(engine: TextToSpeech) {
        val wanted = voiceId?.let { id -> offlineVoices.firstOrNull { it.name == id } } ?: offlineVoices.firstOrNull()
        if (wanted != null) try { engine.voice = wanted } catch (_: Exception) { }
    }

    /** Must hold [lock]. Interrupts whatever is queued and speaks from [index]. */
    private fun startFromLocked(engine: TextToSpeech, index: Int) {
        val sid = session.incrementAndGet()
        engine.stop()
        currentIndex = index
        _spokenIndex.value = index
        nextToQueue = index
        var first = true
        while (nextToQueue < items.size && nextToQueue < index + WINDOW) {
            if (!enqueueLocked(engine, sid, nextToQueue, flush = first)) return
            first = false
            nextToQueue++
        }
        _state.value = TtsState.Speaking(progress(index))
    }

    private fun enqueueLocked(engine: TextToSpeech, sid: Int, index: Int, flush: Boolean): Boolean {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "$sid:$index")
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(items[index], mode, params, "$sid:$index")
        if (result != TextToSpeech.SUCCESS) {
            _state.value = TtsState.Failed("Text-to-speech rejected the text (code $result)")
            return false
        }
        return true
    }

    private fun progress(index: Int) = if (items.isEmpty()) 0f else (index.toFloat() / items.size).coerceIn(0f, 1f)

    private fun parse(id: String?): Pair<Int, Int>? {
        val p = id?.split(':') ?: return null
        if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (sid, idx) = parse(utteranceId) ?: return
            synchronized(lock) {
                if (sid != session.get()) return
                currentIndex = idx
                _spokenIndex.value = idx
                _state.value = TtsState.Speaking(progress(idx))
            }
        }

        override fun onDone(utteranceId: String?) {
            val (sid, idx) = parse(utteranceId) ?: return
            synchronized(lock) {
                if (sid != session.get()) return
                if (idx >= items.lastIndex) {
                    _spokenIndex.value = -1
                    _state.value = TtsState.Finished
                } else if (nextToQueue < items.size) {
                    val engine = tts ?: return
                    if (enqueueLocked(engine, sid, nextToQueue, flush = false)) nextToQueue++
                }
            }
        }

        @Deprecated("API < 21")
        override fun onError(utteranceId: String?) {
            handleError(utteranceId, -1)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handleError(utteranceId, errorCode)
        }

        private fun handleError(utteranceId: String?, code: Int) {
            val (sid, _) = parse(utteranceId) ?: return
            synchronized(lock) {
                if (sid != session.get()) return
                _state.value = TtsState.Failed("The voice stopped with an error (code $code)")
            }
        }
    }

    // MARK: - Voice names

    private fun humanizeVoiceName(voice: Voice): String {
        val gender = inferFemale(voice)?.let { if (it) "F" else "M" } ?: ""
        val suffix = if (gender.isNotEmpty()) " ($gender)" else ""
        return "${voice.locale.displayName}$suffix"
    }

    private fun inferFemale(voice: Voice): Boolean? {
        val name = voice.name.lowercase()
        return when {
            name.contains("-tpf-") || name.contains("female") -> true
            name.contains("-tpm-") || name.contains("male") -> false
            else -> null
        }
    }
}
