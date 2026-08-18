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
        _progress.value = 0f
        _statusMessage.value = null
        _partialTranscript.value = ""

        // Release ExoPlayer before opening MediaCodec — ExoPlayer must be released on main thread
        withContext(Dispatchers.Main) {
            audioPlayerService.release()
        }

        try {
            val language = settingsRepository.transcriptionLanguage.first()

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
     * Decode audio and feed directly to Vosk in streaming fashion.
     * Never holds the full PCM in memory — avoids OOM on large files.
     */
    private fun decodeAndRecognize(fileUri: String, model: Model, onPartial: (String) -> Unit = {}): TranscriptionOutput? {
        val extractor = MediaExtractor()
        val recognizer = Recognizer(model, 16000.0f)
        recognizer.setWords(true)
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
            if (audioTrackIndex < 0) return null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            android.util.Log.d("TranscriptionService", "Audio: mime=$mime sampleRate=$sampleRate channels=$channelCount")
            val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L

            // Prefer a software-only decoder — hardware decoders may be exhausted by ExoPlayer
            val codec = findSoftwareDecoder(mime) ?: MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            // Streaming resampler: srcOffset = fractional position in current source chunk
            // Always >= 0 at start of each chunk; carry-over from previous chunk
            var srcOffset = 0.0
            val ratio = if (sampleRate != 16000) sampleRate.toDouble() / 16000.0 else 1.0
            // Reusable output chunk buffer (bytes for Vosk)
            val voskChunk = ByteArray(8192)
            var voskChunkPos = 0

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
                                if (totalDurationUs > 0) {
                                    val pct = (sampleTimeUs.toFloat() / totalDurationUs)
                                    _progress.value = 0.3f + pct * 0.65f
                                }
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

                        // Resample and pipe to Vosk chunk buffer
                        // srcOffset tracks where in current mono chunk to read next output sample
                        if (ratio == 1.0) {
                            for (s in mono) {
                                voskChunk[voskChunkPos++] = (s.toInt() and 0xFF).toByte()
                                voskChunk[voskChunkPos++] = ((s.toInt() shr 8) and 0xFF).toByte()
                                if (voskChunkPos >= voskChunk.size) {
                                    if (recognizer.acceptWaveForm(voskChunk, voskChunkPos)) {
                                        val sentence = JSONObject(recognizer.result).optString("text", "")
                                        if (sentence.isNotBlank()) onPartial(sentence)
                                    }
                                    voskChunkPos = 0
                                }
                            }
                        } else {
                            while (srcOffset < mono.size) {
                                val s = mono[srcOffset.toInt()]
                                voskChunk[voskChunkPos++] = (s.toInt() and 0xFF).toByte()
                                voskChunk[voskChunkPos++] = ((s.toInt() shr 8) and 0xFF).toByte()
                                if (voskChunkPos >= voskChunk.size) {
                                    if (recognizer.acceptWaveForm(voskChunk, voskChunkPos)) {
                                        val sentence = JSONObject(recognizer.result).optString("text", "")
                                        if (sentence.isNotBlank()) onPartial(sentence)
                                    }
                                    voskChunkPos = 0
                                }
                                srcOffset += ratio
                            }
                            srcOffset -= mono.size  // carry fractional offset into next chunk
                        }

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

            // Flush remaining bytes
            if (voskChunkPos > 0) {
                if (recognizer.acceptWaveForm(voskChunk, voskChunkPos)) {
                    val sentence = JSONObject(recognizer.result).optString("text", "")
                    if (sentence.isNotBlank()) onPartial(sentence)
                }
            }

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
            extractor.release()
            recognizer.close()
        }
    }
}
