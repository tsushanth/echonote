package com.kreativekoala.echonote.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import com.kreativekoala.echonote.data.model.TranscriptionEngine
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.data.model.TranscriptionOutput
import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.repository.SettingsRepository
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

sealed class TranscriptionResult {
    data class Success(val text: String, val segments: List<TranscriptSegment> = emptyList()) : TranscriptionResult()
    data class Error(val message: String) : TranscriptionResult()
}

class TranscriptionService @Inject constructor(
    private val context: Context,
    private val audioPlayerService: AudioPlayerService,
    private val settingsRepository: SettingsRepository
) {
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript

    // Cache models per language
    private val modelCache = mutableMapOf<TranscriptionLanguage, Model>()

    private fun modelDir(language: TranscriptionLanguage): File =
        File(context.filesDir, language.modelDirName)

    fun isAvailable(): Boolean = true

    suspend fun transcribe(fileUri: String): TranscriptionResult = withContext(Dispatchers.IO) {
        val file = File(fileUri)
        if (!file.exists()) {
            return@withContext TranscriptionResult.Error("Recording file not found")
        }

        _isTranscribing.value = true
        val startedAtMs = android.os.SystemClock.elapsedRealtime()
        _progress.value = 0f
        _statusMessage.value = null
        _partialTranscript.value = ""

        // Release ExoPlayer before opening MediaCodec — ExoPlayer must be released on main thread
        withContext(Dispatchers.Main) {
            audioPlayerService.release()
        }

        try {
            val language = settingsRepository.transcriptionLanguage.first()

            // Accurate picks the best engine for the language; where that is Vosk it is the same as Fast.
            val engine = EnginePlan.forLanguage(language, DeviceMemory.totalBytes(context))
            if (settingsRepository.transcriptionEngine.first() == TranscriptionEngine.ACCURATE && engine != AsrEngine.VOSK) {
                return@withContext transcribeWithSherpa(fileUri, language, engine)
            }

            // Ensure model is ready
            val model = getOrDownloadModel(language)
                ?: return@withContext TranscriptionResult.Error(
                    "Failed to load speech recognition model. Check your internet connection and try again."
                )

            _statusMessage.value = "Transcribing..."
            _progress.value = 0.3f

            // Decode and transcribe in one streaming pass — no full PCM buffer in memory
            val output = decodeAndRecognize(fileUri, model) { partial ->
                _partialTranscript.value = partial
            }

            _progress.value = 1f

            if (output == null || output.text.isBlank()) {
                TranscriptionResult.Error("No speech detected in this recording")
            } else {
                TranscriptionResult.Success(output.text, output.segments)
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("Transcription failed: ${e.message ?: "Unknown error"}")
        } finally {
            android.util.Log.i("TranscriptionService", "transcribe finished in ${android.os.SystemClock.elapsedRealtime() - startedAtMs} ms")
            _isTranscribing.value = false
            _progress.value = 0f
            _statusMessage.value = null
            _partialTranscript.value = ""
        }
    }

    private suspend fun getOrDownloadModel(language: TranscriptionLanguage): Model? {
        // Return cached model if available
        modelCache[language]?.let { return it }

        val dir = modelDir(language)
        // Check if model is already on disk
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            return try {
                _statusMessage.value = "Loading speech model..."
                val model = Model(dir.absolutePath)
                modelCache[language] = model
                model
            } catch (e: Exception) {
                // Model files corrupted, re-download
                dir.deleteRecursively()
                downloadAndExtractModel(language)
            }
        }

        return downloadAndExtractModel(language)
    }

    private suspend fun downloadAndExtractModel(language: TranscriptionLanguage): Model? = withContext(Dispatchers.IO) {
        _statusMessage.value = "Downloading ${language.displayName} speech model..."
        _progress.value = 0.05f

        val modelUrl = language.modelUrl
        val tempZip = File(context.cacheDir, "vosk-model-${language.name.lowercase()}.zip")
        val dir = modelDir(language)

        try {
            // Download
            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val downloadProgress = downloadedBytes.toFloat() / totalBytes
                            _progress.value = 0.05f + downloadProgress * 0.2f
                            _statusMessage.value = "Downloading speech model (${(downloadProgress * 100).toInt()}%)..."
                        }
                    }
                }
            }

            // Extract
            _statusMessage.value = "Extracting speech model..."
            _progress.value = 0.25f

            dir.mkdirs()
            ZipInputStream(tempZip.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // Strip the top-level directory from zip paths
                    val name = entry.name
                    val strippedName = if (name.contains("/")) {
                        name.substringAfter("/")
                    } else {
                        name
                    }

                    if (strippedName.isNotEmpty()) {
                        val outFile = File(dir, strippedName)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zip.copyTo(fos)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Clean up zip
            tempZip.delete()

            // Load model
            _statusMessage.value = "Loading speech model..."
            _progress.value = 0.28f
            val model = Model(dir.absolutePath)
            modelCache[language] = model
            model
        } catch (e: Exception) {
            tempZip.delete()
            dir.deleteRecursively()
            null
        }
    }

    /**
     * Find a software-only decoder for the given MIME type.
     * Software decoders don't share the hardware codec pool with ExoPlayer.
     */
    private fun findSoftwareDecoder(mime: String): MediaCodec? {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            if (!info.isSoftwareOnly) continue
            if (info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) {
                return try { MediaCodec.createByCodecName(info.name) } catch (_: Exception) { null }
            }
        }
        return null
    }

    /**
     * Accurate path: download the language's model once, decode the recording into 16 kHz mono chunks,
     * and optionally label speakers afterwards. Memory is held to one 28 s window plus, when speakers are
     * requested, the audio itself (recordings up to 30 minutes).
     */
    private suspend fun transcribeWithSherpa(fileUri: String, language: TranscriptionLanguage, engine: AsrEngine): TranscriptionResult {
        val dirName = when (engine) {
            AsrEngine.WHISPER_SMALL -> SherpaModels.WHISPER_SMALL_DIR
            AsrEngine.SENSE_VOICE -> SherpaModels.SENSE_VOICE_DIR
            AsrEngine.NEMO_FAST_CONFORMER -> SherpaModels.NEMO_DIR
            AsrEngine.VOSK -> return TranscriptionResult.Error("Fast engine selected")
        }
        val files = when (engine) {
            AsrEngine.WHISPER_SMALL -> SherpaModels.WHISPER_SMALL
            AsrEngine.SENSE_VOICE -> SherpaModels.SENSE_VOICE
            else -> SherpaModels.NEMO_FAST_CONFORMER.files
        }
        val modelDir = File(context.filesDir, dirName)
        val wantSpeakers = settingsRepository.identifySpeakers.first()
        val diarizationDir = File(context.filesDir, SherpaModels.DIARIZATION_DIR)
        // The Whisper base model of earlier builds is no longer used; free its ~160 MB.
        File(context.filesDir, SherpaModels.LEGACY_WHISPER_BASE_DIR).takeIf { it.exists() }?.deleteRecursively()

        try {
            if (!ModelDownloader.isReady(modelDir, files)) {
                _statusMessage.value = "Downloading ${language.displayName} speech model (${engine.downloadMb} MB)..."
                if (engine == AsrEngine.NEMO_FAST_CONFORMER) {
                    ModelDownloader.ensureArchive(modelDir, SherpaModels.NEMO_FAST_CONFORMER) { _progress.value = 0.02f + it * 0.2f }
                } else {
                    ModelDownloader.ensure(modelDir, files) { _progress.value = 0.02f + it * 0.2f }
                }
            }
            if (wantSpeakers && !ModelDownloader.isReady(diarizationDir, SherpaModels.DIARIZATION)) {
                _statusMessage.value = "Downloading speaker model (46 MB)..."
                ModelDownloader.ensure(diarizationDir, SherpaModels.DIARIZATION) { _progress.value = 0.22f + it * 0.03f }
            }
        } catch (e: Exception) {
            return TranscriptionResult.Error("Could not download the accurate speech model. Check your internet connection and try again. (${e.message})")
        }

        _statusMessage.value = "Transcribing..."
        _progress.value = 0.25f
        val languageCode = EnginePlan.languageCode(language)

        var finalWords: List<TranscriptSegment>? = null
        var keptAudio: Pair<ShortArray, Int>? = null
        try {
            SherpaTranscriber(engine, modelDir, languageCode, keepAudio = wantSpeakers) { _partialTranscript.value = it }.use { transcriber ->
                val hasAudio = decodeAudio(fileUri, onProgress = { _progress.value = 0.25f + it * 0.6f }) { samples, count ->
                    transcriber.accept(samples, count)
                }
                if (!hasAudio) return TranscriptionResult.Error("No audio found in this recording")
                transcriber.finish()

                var words = transcriber.words.toList()
                if (words.isEmpty()) return TranscriptionResult.Error("No speech detected in this recording")

                if (wantSpeakers) {
                    val kept = transcriber.keptAudio()
                    if (kept == null) {
                        _statusMessage.value = "Speaker labels skipped: recording is longer than 30 minutes"
                    } else {
                        _statusMessage.value = "Identifying speakers..."
                        try {
                            val spans = SpeakerDiarizer(diarizationDir).diarize(kept.first, kept.second) {
                                _progress.value = 0.85f + it * 0.13f
                            }
                            words = smoothSpeakersBySentence(assignSpeakers(words, spans))
                        } catch (e: Exception) {
                            android.util.Log.e("TranscriptionService", "diarization failed", e)
                        }
                    }
                }
                finalWords = words
                keptAudio = transcriber.keptAudio()
            }
        } catch (e: OutOfMemoryError) {
            return TranscriptionResult.Error("Not enough memory for the accurate model on this phone. Switch Transcription Quality to Fast.")
        }

        // The main model is closed now, so its memory is free for the Vosk pass.
        var words = finalWords ?: return TranscriptionResult.Error("No speech detected in this recording")
        if (settingsRepository.markUncertainWords.first() && UncertainWords.supports(language, engine)) {
            words = markUncertainWords(fileUri, language, words, keptAudio)
        }
        _progress.value = 1f
        return TranscriptionResult.Success(joinWords(words.map { it.word }), words)
    }

    /**
     * Second opinion for "Mark uncertain words": runs Vosk over the same recording and marks the words it does
     * not agree with. Never throws and never fails the transcription; on any problem (model cannot be fetched,
     * out of memory, bad audio) the words come back unmarked and the reason goes to the log only.
     * Uses the audio kept for speaker labels when there is one, otherwise decodes the file a second time.
     */
    private suspend fun markUncertainWords(
        fileUri: String,
        language: TranscriptionLanguage,
        words: List<TranscriptSegment>,
        kept: Pair<ShortArray, Int>?
    ): List<TranscriptSegment> {
        val startedAtMs = android.os.SystemClock.elapsedRealtime()
        return try {
            _statusMessage.value = "Checking for uncertain words..."
            _progress.value = 0.85f
            val model = getOrDownloadModel(language)
            if (model == null) {
                android.util.Log.w("TranscriptionService", "uncertain words skipped: Vosk model unavailable")
                return words
            }
            _statusMessage.value = "Checking for uncertain words..."
            val second = recognizeVoskWords(fileUri, model, kept) { _progress.value = 0.88f + it * 0.1f }
            if (second == null) {
                android.util.Log.w("TranscriptionService", "uncertain words skipped: Vosk pass produced nothing")
                return words
            }
            val marked = UncertainWords.mark(words, second, UncertainWords.comparesCharacters(language))
            android.util.Log.i(
                "TranscriptionService",
                "uncertain words: ${marked.count { it.uncertain }} of ${marked.size} marked in " +
                    "${android.os.SystemClock.elapsedRealtime() - startedAtMs} ms (${if (kept != null) "reused audio" else "decoded twice"})"
            )
            marked
        } catch (e: Exception) {
            android.util.Log.w("TranscriptionService", "uncertain words skipped", e)
            words
        } catch (e: OutOfMemoryError) {
            android.util.Log.w("TranscriptionService", "uncertain words skipped: out of memory")
            words
        }
    }

    /** Every word Vosk heard in the recording (all sentences, not only the last), or null when it heard none. */
    private fun recognizeVoskWords(
        fileUri: String,
        model: Model,
        kept: Pair<ShortArray, Int>?,
        onProgress: (Float) -> Unit
    ): List<String>? {
        val recognizer = Recognizer(model, 16000.0f)
        recognizer.setWords(true)
        try {
            val out = ArrayList<String>()
            fun collect(json: String) {
                val arr = JSONObject(json).optJSONArray("result") ?: return
                for (i in 0 until arr.length()) arr.getJSONObject(i).optString("word").takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
            val chunk = ByteArray(8192)
            var pos = 0
            fun feed() {
                if (recognizer.acceptWaveForm(chunk, pos)) collect(recognizer.result)
                pos = 0
            }
            fun accept(samples: ShortArray, count: Int) {
                for (i in 0 until count) {
                    val v = samples[i].toInt()
                    chunk[pos++] = (v and 0xFF).toByte()
                    chunk[pos++] = ((v shr 8) and 0xFF).toByte()
                    if (pos >= chunk.size) feed()
                }
            }
            if (kept != null) {
                accept(kept.first, kept.second)
            } else if (!decodeAudio(fileUri, onProgress = onProgress) { samples, count -> accept(samples, count) }) {
                return null
            }
            if (pos > 0) feed()
            collect(recognizer.finalResult)
            return out.takeIf { it.isNotEmpty() }
        } finally {
            recognizer.close()
        }
    }

    /**
     * Decodes [fileUri] to 16 kHz mono PCM and hands it to [onChunk] as it is produced, so the
     * whole recording is never held in memory. Returns false when the file has no audio track.
     */
    private fun decodeAudio(
        fileUri: String,
        onProgress: (Float) -> Unit = {},
        onChunk: (ShortArray, Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(fileUri)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return false

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            android.util.Log.d("TranscriptionService", "Audio: mime=$mime sampleRate=$sampleRate channels=$channelCount")
            val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L

            // Prefer a software-only decoder — hardware decoders may be exhausted by ExoPlayer
            val codec = findSoftwareDecoder(mime) ?: MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var srcOffset = 0.0
            val ratio = sampleRate.toDouble() / 16000.0

            try {
                while (true) {
                    if (!isEos) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEos = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                                if (totalDurationUs > 0) onProgress((sampleTimeUs.toFloat() / totalDurationUs).coerceIn(0f, 1f))
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val decoded = ShortArray(shortBuf.remaining())
                        shortBuf.get(decoded)

                        // Mix to mono
                        val frames = decoded.size / channelCount
                        val mono = if (channelCount > 1) {
                            ShortArray(frames) { i ->
                                var sum = 0L
                                for (ch in 0 until channelCount) sum += decoded[i * channelCount + ch]
                                (sum / channelCount).toShort()
                            }
                        } else decoded

                        val (resampled, nextOffset) = resampleByAveraging(mono, ratio, srcOffset)
                        srcOffset = nextOffset
                        if (resampled.isNotEmpty()) onChunk(resampled, resampled.size)

                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isEos) {
                        break
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionService", "decodeAudio failed", e)
            return false
        } finally {
            extractor.release()
        }
    }

    /** Vosk path: stream the decoded audio into a Vosk recognizer. */
    private fun decodeAndRecognize(fileUri: String, model: Model, onPartial: (String) -> Unit = {}): TranscriptionOutput? {
        val recognizer = Recognizer(model, 16000.0f)
        recognizer.setWords(true)
        try {
            val voskChunk = ByteArray(8192)
            var voskChunkPos = 0

            fun feed() {
                if (recognizer.acceptWaveForm(voskChunk, voskChunkPos)) {
                    val sentence = JSONObject(recognizer.result).optString("text", "")
                    if (sentence.isNotBlank()) onPartial(sentence)
                }
                voskChunkPos = 0
            }

            val hasAudio = decodeAudio(
                fileUri,
                onProgress = { _progress.value = 0.3f + it * 0.65f }
            ) { samples, count ->
                for (i in 0 until count) {
                    val s = samples[i].toInt()
                    voskChunk[voskChunkPos++] = (s and 0xFF).toByte()
                    voskChunk[voskChunkPos++] = ((s shr 8) and 0xFF).toByte()
                    if (voskChunkPos >= voskChunk.size) feed()
                }
            }
            if (!hasAudio) return null
            if (voskChunkPos > 0) feed()

            val finalResult = recognizer.finalResult
            android.util.Log.d("TranscriptionService", "Vosk finalResult: $finalResult")
            val json = JSONObject(finalResult)
            val text = json.optString("text", "").ifBlank { null } ?: return null
            val segments = mutableListOf<TranscriptSegment>()
            val wordsArr = json.optJSONArray("result")
            if (wordsArr != null) {
                for (i in 0 until wordsArr.length()) {
                    val w = wordsArr.getJSONObject(i)
                    segments.add(
                        TranscriptSegment(
                            word = w.optString("word"),
                            startMs = (w.optDouble("start", 0.0) * 1000).toLong(),
                            endMs = (w.optDouble("end", 0.0) * 1000).toLong()
                        )
                    )
                }
            }
            return TranscriptionOutput(text, segments)
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionService", "decodeAndRecognize failed", e)
            return null
        } finally {
            recognizer.close()
        }
    }
}
