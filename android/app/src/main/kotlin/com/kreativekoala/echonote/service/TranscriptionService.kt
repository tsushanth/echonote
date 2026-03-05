package com.kreativekoala.echonote.service

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.json.JSONObject

sealed class TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult()
    data class Error(val message: String) : TranscriptionResult()
}

class TranscriptionService @Inject constructor(
    private val context: Context
) {
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private var voskModel: Model? = null

    private val modelDir: File
        get() = File(context.filesDir, "vosk-model")

    fun isAvailable(): Boolean = true

    suspend fun transcribe(fileUri: String): TranscriptionResult = withContext(Dispatchers.IO) {
        val file = File(fileUri)
        if (!file.exists()) {
            return@withContext TranscriptionResult.Error("Recording file not found")
        }

        _isTranscribing.value = true
        _progress.value = 0f
        _statusMessage.value = null

        try {
            // Ensure model is ready
            val model = getOrDownloadModel()
                ?: return@withContext TranscriptionResult.Error(
                    "Failed to load speech recognition model. Check your internet connection and try again."
                )

            _statusMessage.value = "Decoding audio..."
            _progress.value = 0.3f

            // Decode audio to 16kHz mono PCM
            val pcmData = decodeAudioToPcm(fileUri)
                ?: return@withContext TranscriptionResult.Error(
                    "Failed to decode audio file. The format may not be supported."
                )

            _statusMessage.value = "Transcribing..."
            _progress.value = 0.5f

            // Run Vosk recognition
            val result = recognizeWithVosk(model, pcmData)

            _progress.value = 1f

            if (result.isNullOrBlank()) {
                TranscriptionResult.Error("No speech detected in this recording")
            } else {
                TranscriptionResult.Success(result)
            }
        } catch (e: Exception) {
            TranscriptionResult.Error("Transcription failed: ${e.message ?: "Unknown error"}")
        } finally {
            _isTranscribing.value = false
            _progress.value = 0f
            _statusMessage.value = null
        }
    }

    private suspend fun getOrDownloadModel(): Model? {
        // Return cached model if available
        voskModel?.let { return it }

        // Check if model is already on disk
        if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            return try {
                _statusMessage.value = "Loading speech model..."
                val model = Model(modelDir.absolutePath)
                voskModel = model
                model
            } catch (e: Exception) {
                // Model files corrupted, re-download
                modelDir.deleteRecursively()
                downloadAndExtractModel()
            }
        }

        return downloadAndExtractModel()
    }

    private suspend fun downloadAndExtractModel(): Model? = withContext(Dispatchers.IO) {
        _statusMessage.value = "Downloading speech model..."
        _progress.value = 0.05f

        val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        val tempZip = File(context.cacheDir, "vosk-model.zip")

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

            modelDir.mkdirs()
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
                        val outFile = File(modelDir, strippedName)
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
            val model = Model(modelDir.absolutePath)
            voskModel = model
            model
        } catch (e: Exception) {
            tempZip.delete()
            modelDir.deleteRecursively()
            null
        }
    }

    private fun decodeAudioToPcm(fileUri: String): ByteArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(fileUri)

            // Find audio track
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

            // Configure decoder
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false

            while (true) {
                // Feed input
                if (!isEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Read output
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val pcmBytes = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmBytes)
                    outputStream.write(pcmBytes)
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isEos) {
                    break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val rawPcm = outputStream.toByteArray()

            // Resample to 16kHz mono if needed
            return resamplePcm(rawPcm, sampleRate, channelCount, 16000)
        } catch (e: Exception) {
            extractor.release()
            return null
        }
    }

    private fun resamplePcm(
        input: ByteArray,
        inputRate: Int,
        inputChannels: Int,
        targetRate: Int
    ): ByteArray {
        // Convert bytes to short samples (16-bit PCM)
        val shortBuffer = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        val samples = ShortArray(totalSamples)
        shortBuffer.get(samples)

        // Mix to mono if stereo
        val monoSamples = if (inputChannels > 1) {
            val framesCount = totalSamples / inputChannels
            ShortArray(framesCount) { i ->
                var sum = 0L
                for (ch in 0 until inputChannels) {
                    sum += samples[i * inputChannels + ch]
                }
                (sum / inputChannels).toInt().toShort()
            }
        } else {
            samples
        }

        // Resample if rate differs
        val resampled = if (inputRate != targetRate) {
            val ratio = inputRate.toDouble() / targetRate
            val outputLength = (monoSamples.size / ratio).toInt()
            ShortArray(outputLength) { i ->
                val srcIndex = (i * ratio).toInt().coerceAtMost(monoSamples.size - 1)
                monoSamples[srcIndex]
            }
        } else {
            monoSamples
        }

        // Convert back to bytes
        val outputBuffer = ByteBuffer.allocate(resampled.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        resampled.forEach { outputBuffer.putShort(it) }
        return outputBuffer.array()
    }

    private fun recognizeWithVosk(model: Model, pcmData: ByteArray): String? {
        val recognizer = Recognizer(model, 16000.0f)
        try {
            val chunkSize = 4096
            var offset = 0
            val totalSize = pcmData.size

            while (offset < totalSize) {
                val end = minOf(offset + chunkSize, totalSize)
                val chunk = pcmData.copyOfRange(offset, end)
                recognizer.acceptWaveForm(chunk, chunk.size)

                // Update progress (0.5 to 0.95 range during recognition)
                val recognitionProgress = offset.toFloat() / totalSize
                _progress.value = 0.5f + recognitionProgress * 0.45f

                offset = end
            }

            val finalResult = recognizer.finalResult
            val json = JSONObject(finalResult)
            return json.optString("text", "").ifBlank { null }
        } finally {
            recognizer.close()
        }
    }
}
