package com.kreativekoala.echonote.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

class AudioPlayerService @Inject constructor(
    private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionRunnable: Runnable? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence

    fun play(filePath: String) {
        release()

        val player = ExoPlayer.Builder(context).build()
        val file = File(filePath)
        val mediaItem = MediaItem.fromUri(file.toURI().toString())

        player.setMediaItem(mediaItem)
        player.playbackParameters = PlaybackParameters(_playbackSpeed.value)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _durationMs.value = player.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        _currentPositionMs.value = 0L
                        stopPositionUpdates()
                        player.seekTo(0)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }
        })

        player.prepare()
        player.play()
        exoPlayer = player
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.seekTo(0)
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        stopPositionUpdates()
    }

    fun release() {
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        exoPlayer?.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun seekToProgress(progress: Float) {
        val positionMs = (progress * _durationMs.value).toLong()
        seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        exoPlayer?.playbackParameters = PlaybackParameters(clamped)
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        exoPlayer?.skipSilenceEnabled = enabled
    }

    fun skipForward(ms: Long = 15_000L) {
        val newPos = (_currentPositionMs.value + ms).coerceAtMost(_durationMs.value)
        seekTo(newPos)
    }

    fun skipBackward(ms: Long = 15_000L) {
        val newPos = (_currentPositionMs.value - ms).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun generateWaveformData(filePath: String, samplesCount: Int = 200): FloatArray {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return FloatArray(samplesCount) { 0f }
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            val codec = MediaCodec.createDecoderByType(
                format.getString(MediaFormat.KEY_MIME)!!
            )
            codec.configure(format, null, null, 0)
            codec.start()

            // Get total duration to estimate total sample count for bucket sizing
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val estimatedTotal = if (durationUs > 0)
                (durationUs / 1_000_000.0 * sourceSampleRate).toLong() else Long.MAX_VALUE

            // Online bucketing — no accumulation of all samples
            val bucketSumSq = DoubleArray(samplesCount)
            val bucketCount = LongArray(samplesCount)
            var totalSamplesDecoded = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shortBuffer.hasRemaining()) {
                        val v = shortBuffer.get().toFloat() / 32768f
                        // Assign to bucket based on position in estimated total
                        val bucketIdx = if (estimatedTotal > 0)
                            ((totalSamplesDecoded * samplesCount) / estimatedTotal)
                                .toInt().coerceIn(0, samplesCount - 1)
                        else (totalSamplesDecoded % samplesCount).toInt()
                        bucketSumSq[bucketIdx] += v * v
                        bucketCount[bucketIdx]++
                        totalSamplesDecoded++
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            if (totalSamplesDecoded == 0L) return FloatArray(samplesCount) { 0f }

            val waveform = FloatArray(samplesCount)
            var maxVal = 0f
            for (i in 0 until samplesCount) {
                val count = bucketCount[i].coerceAtLeast(1)
                val rms = sqrt(bucketSumSq[i] / count).toFloat()
                waveform[i] = rms
                if (rms > maxVal) maxVal = rms
            }

            if (maxVal > 0f) {
                for (i in waveform.indices) waveform[i] = waveform[i] / maxVal
            }

            waveform
        } catch (e: Exception) {
            e.printStackTrace()
            FloatArray(samplesCount) { 0f }
        }
    }

    private fun startPositionUpdates() {
        positionRunnable = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPositionMs.value = player.currentPosition
                    }
                }
                handler.postDelayed(this, 50L)
            }
        }
        handler.post(positionRunnable!!)
    }

    private fun stopPositionUpdates() {
        positionRunnable?.let { handler.removeCallbacks(it) }
        positionRunnable = null
    }
}
