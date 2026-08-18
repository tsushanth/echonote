package com.kreativekoala.echonote.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import javax.inject.Inject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioEditorService @Inject constructor(
    private val context: Context
) {
    suspend fun trimAudio(
        inputUri: String,
        outputUri: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Boolean {
        return try {
            trimWithMuxer(inputUri, outputUri, startTimeMs * 1000L, endTimeMs * 1000L)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun trimWithMuxer(
        inputPath: String,
        outputPath: String,
        startUs: Long,
        endUs: Long
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        // Find the audio track
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in file")
        }

        extractor.selectTrack(audioTrackIndex)

        // Determine output format for muxer
        val outputFormat = when {
            outputPath.endsWith(".m4a") || outputPath.endsWith(".mp4") ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            outputPath.endsWith(".webm") ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }

        val muxer = MediaMuxer(outputPath, outputFormat)
        val muxerTrackIndex = muxer.addTrack(audioFormat)
        muxer.start()

        // Seek to start position
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val bufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            1024 * 1024
        }
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs > endUs) break

            if (sampleTimeUs >= startUs) {
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTimeUs - startUs
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            }

            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
    }

    suspend fun getAudioDuration(fileUri: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(fileUri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getFileSize(fileUri: String): Long {
        return try {
            File(fileUri).length()
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun enhanceAudio(inputUri: String, outputUri: String): Boolean {
        return try {
            val pcmData = decodeAudioToPcm(inputUri) ?: return false
            val sampleRate = getSampleRate(inputUri)
            val enhanced = normalizeAndCompress(pcmData)
            encodePcmToM4a(enhanced, sampleRate, outputUri)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun separateVocals(inputUri: String, outputUri: String): Boolean {
        return try {
            // Isolate vocal frequencies (300Hz-3400Hz) using bandpass filter
            val pcmData = decodeAudioToPcm(inputUri) ?: return false
            val sampleRate = getSampleRate(inputUri)
            val vocalFiltered = applyBandpassFilter(pcmData, sampleRate, 300.0, 3400.0)
            encodePcmToM4a(vocalFiltered, sampleRate, outputUri)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getSampleRate(filePath: String): Int {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                extractor.release()
                return rate
            }
        }
        extractor.release()
        return 44100
    }

    private fun decodeAudioToPcm(filePath: String): ShortArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)

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
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Accumulate as ShortArray chunks (avoids boxing overhead of mutableListOf<Short>)
            val chunks = ArrayList<ShortArray>()
            var totalSamples = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false

            while (true) {
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
                    val chunk = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(chunk)
                    chunks.add(chunk)
                    totalSamples += chunk.size
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            // Combine chunks into single ShortArray
            val result = ShortArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        } catch (e: Exception) {
            extractor.release()
            return null
        }
    }

    private fun normalizeAndCompress(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples

        var peak = 0
        for (sample in samples) {
            val absSample = abs(sample.toInt())
            if (absSample > peak) peak = absSample
        }
        if (peak == 0) return samples

        val targetPeak = (Short.MAX_VALUE * 0.9).toInt()
        val gain = targetPeak.toDouble() / peak
        val threshold = 0.7 * Short.MAX_VALUE
        val ratio = 3.0
        val result = ShortArray(samples.size)

        for (i in samples.indices) {
            var amplified = samples[i].toDouble() * gain
            val absVal = abs(amplified)

            if (absVal > threshold) {
                val excess = absVal - threshold
                val compressed = threshold + excess / ratio
                amplified = if (amplified > 0) compressed else -compressed
            }

            result[i] = amplified.toInt().coerceIn(
                Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()
            ).toShort()
        }

        return result
    }

    private fun applyBandpassFilter(
        samples: ShortArray,
        sampleRate: Int,
        lowCutHz: Double,
        highCutHz: Double
    ): ShortArray {
        if (samples.isEmpty()) return samples

        val lowNorm = lowCutHz / sampleRate
        val highNorm = highCutHz / sampleRate
        val bandwidth = highNorm - lowNorm
        val centerFreq = (lowNorm + highNorm) / 2.0

        val r = 1.0 - 3.0 * bandwidth
        val cosFreq = kotlin.math.cos(2.0 * Math.PI * centerFreq)
        val k = (1.0 - 2.0 * r * cosFreq + r * r) / (2.0 - 2.0 * cosFreq)

        val a0 = 1.0 - k
        val a1 = 2.0 * (k - r) * cosFreq
        val a2 = r * r - k
        val b1 = 2.0 * r * cosFreq
        val b2 = -(r * r)

        val result = ShortArray(samples.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0

        for (i in samples.indices) {
            val x0 = samples[i].toDouble()
            val y0 = a0 * x0 + a1 * x1 + a2 * x2 + b1 * y1 + b2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0

            result[i] = y0.toInt().coerceIn(
                Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()
            ).toShort()
        }

        return result
    }

    private fun encodePcmToM4a(samples: ShortArray, sampleRate: Int, outputPath: String) {
        val mediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1
        )
        mediaFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val pcmBytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            pcmBytes.putShort(sample)
        }
        pcmBytes.flip()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val remaining = pcmBytes.remaining()
                    if (remaining <= 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        val bytesToCopy = minOf(remaining, inputBuffer.capacity())
                        val slice = ByteArray(bytesToCopy)
                        pcmBytes.get(slice)
                        inputBuffer.put(slice)
                        val presentationTimeUs = (pcmBytes.position().toLong() * 1_000_000L) /
                                (sampleRate.toLong() * 2L)
                        codec.queueInputBuffer(
                            inputIndex, 0, bytesToCopy, presentationTimeUs, 0
                        )
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerTrackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                if (muxerStarted && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                break
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }
}
