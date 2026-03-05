package com.kreativekoala.echonote.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject

class AudioRecorderService @Inject constructor(
    private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L
    private var pausedDurationMs: Long = 0L
    private var pauseStartMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var meteringRunnable: Runnable? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude

    private val _elapsedTimeMs = MutableStateFlow(0L)
    val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs

    private val _meterLevels = MutableStateFlow<List<Float>>(emptyList())
    val meterLevels: StateFlow<List<Float>> = _meterLevels

    private fun getRecordingsDir(): File {
        val dir = File(context.filesDir, "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startRecording(
        fileName: String,
        format: AudioFormat = AudioFormat.COMPRESSED,
        quality: RecordingQuality = RecordingQuality.HIGH,
        isStereo: Boolean = false
    ): String? {
        try {
            val extension = format.extension
            val file = File(getRecordingsDir(), "$fileName.$extension")
            outputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)

            when (format) {
                AudioFormat.COMPRESSED -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(quality.bitRate)
                }
                AudioFormat.UNCOMPRESSED -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
            }

            recorder.setAudioSamplingRate(quality.sampleRate)
            recorder.setAudioChannels(if (isStereo) 2 else 1)
            recorder.setOutputFile(file.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            startTimeMs = System.currentTimeMillis()
            pausedDurationMs = 0L
            _isRecording.value = true
            _isPaused.value = false
            _meterLevels.value = emptyList()

            startMetering()
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            return null
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            try {
                mediaRecorder?.pause()
                pauseStartMs = System.currentTimeMillis()
                _isPaused.value = true
                stopMetering()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
            try {
                mediaRecorder?.resume()
                pausedDurationMs += System.currentTimeMillis() - pauseStartMs
                _isPaused.value = false
                startMetering()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(): RecordingResult? {
        if (!_isRecording.value) return null

        try {
            stopMetering()
            mediaRecorder?.stop()
            mediaRecorder?.release()

            val file = outputFile ?: return null
            val elapsed = if (_isPaused.value) {
                pauseStartMs - startTimeMs - pausedDurationMs
            } else {
                System.currentTimeMillis() - startTimeMs - pausedDurationMs
            }

            _isRecording.value = false
            _isPaused.value = false
            _currentAmplitude.value = 0f
            _elapsedTimeMs.value = 0L
            mediaRecorder = null

            return RecordingResult(
                filePath = file.absolutePath,
                durationMs = elapsed,
                fileSize = file.length()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            return null
        }
    }

    fun cancelRecording() {
        try {
            stopMetering()
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}

        outputFile?.delete()
        cleanup()
    }

    private fun cleanup() {
        mediaRecorder = null
        outputFile = null
        _isRecording.value = false
        _isPaused.value = false
        _currentAmplitude.value = 0f
        _elapsedTimeMs.value = 0L
        _meterLevels.value = emptyList()
    }

    private fun startMetering() {
        meteringRunnable = object : Runnable {
            override fun run() {
                if (_isRecording.value && !_isPaused.value) {
                    try {
                        val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = if (maxAmplitude > 0) {
                            (maxAmplitude.toFloat() / 32767f).coerceIn(0f, 1f)
                        } else 0f

                        _currentAmplitude.value = normalized
                        _meterLevels.value = _meterLevels.value + normalized

                        val elapsed = System.currentTimeMillis() - startTimeMs - pausedDurationMs
                        _elapsedTimeMs.value = elapsed
                    } catch (_: Exception) {}

                    handler.postDelayed(this, 50L)
                }
            }
        }
        handler.post(meteringRunnable!!)
    }

    private fun stopMetering() {
        meteringRunnable?.let { handler.removeCallbacks(it) }
        meteringRunnable = null
    }

    data class RecordingResult(
        val filePath: String,
        val durationMs: Long,
        val fileSize: Long
    )
}
