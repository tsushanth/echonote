package com.kreativekoala.echonote.service

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/** Finds who spoke when, on-device, using pyannote segmentation plus speaker embeddings. */
class SpeakerDiarizer(private val modelDir: File) {

    fun diarize(samples: ShortArray, count: Int, onProgress: (Float) -> Unit): List<SpeakerSpan> {
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = File(modelDir, "segmentation.onnx").absolutePath
                ),
                numThreads = 2
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = File(modelDir, "embedding.onnx").absolutePath,
                numThreads = 2
            ),
            clustering = FastClusteringConfig(numClusters = -1, threshold = CLUSTER_THRESHOLD),
            minDurationOn = 0.3f,
            minDurationOff = 0.5f
        )
        val diarization = OfflineSpeakerDiarization(null, config)
        try {
            check(diarization.sampleRate() == 16000) { "Unexpected diarization sample rate" }
            val floats = FloatArray(count) { samples[it] / 32768f }
            // Not processWithCallback: its native side looks up a specific `invoke` signature on the
            // callback object, which a Kotlin lambda does not provide, and the process aborts.
            onProgress(0f)
            val segments = diarization.process(floats)
            return segments.map {
                SpeakerSpan((it.start * 1000).toLong(), (it.end * 1000).toLong(), it.speaker)
            }
        } finally {
            diarization.release()
        }
    }

    private companion object {
        // Higher merges more voices into one; lower splits one voice into several. With TitaNet-small
        // the right speaker count held across roughly 0.8-0.9 and broke down by 0.7 and 1.0.
        const val CLUSTER_THRESHOLD = 0.85f
    }
}
