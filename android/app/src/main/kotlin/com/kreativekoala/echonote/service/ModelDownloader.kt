package com.kreativekoala.echonote.service

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One downloadable model file, pinned by size and SHA-256 because native code parses it. */
data class ModelFile(val url: String, val fileName: String, val sizeBytes: Long, val sha256: String)

object SherpaModels {
    private const val HF = "https://huggingface.co/csukuangfj"

    const val WHISPER_DIR = "whisper-base-int8"
    val WHISPER_BASE = listOf(
        ModelFile(
            "$HF/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx",
            "encoder.onnx", 29_120_534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"
        ),
        ModelFile(
            "$HF/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx",
            "decoder.onnx", 130_672_026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"
        ),
        ModelFile(
            "$HF/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt",
            "tokens.txt", 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
        )
    )

    const val DIARIZATION_DIR = "speaker-diarization"
    val DIARIZATION = listOf(
        ModelFile(
            "$HF/sherpa-onnx-pyannote-segmentation-3-0/resolve/main/model.onnx",
            "segmentation.onnx", 5_992_913, "220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079"
        ),
        // NeMo TitaNet-small: on real-speaker test conversations it found the right number of
        // speakers 16/16 times at threshold 0.8-0.9, where the CAM++ model we first tried got
        // 1/16 at its best. See tools/asr-bench. Licence still to be confirmed before release.
        ModelFile(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/nemo_en_titanet_small.onnx",
            "embedding.onnx", 40_257_283, "ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e"
        )
    )
}

object ModelDownloader {
    fun isReady(dir: File, files: List<ModelFile>): Boolean =
        files.all { File(dir, it.fileName).let { f -> f.exists() && f.length() == it.sizeBytes } }

    /**
     * Downloads any missing file into [dir], checking size and SHA-256 before it is moved into
     * place, so a truncated or tampered download is never loaded. [onProgress] gets 0..1 across
     * all files. Throws on any failure.
     */
    fun ensure(dir: File, files: List<ModelFile>, onProgress: (Float) -> Unit) {
        dir.mkdirs()
        val total = files.sumOf { it.sizeBytes }.toFloat()
        var done = 0L
        for (file in files) {
            val target = File(dir, file.fileName)
            if (target.exists() && target.length() == file.sizeBytes) {
                done += file.sizeBytes
                onProgress(done / total)
                continue
            }
            val part = File(dir, file.fileName + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = (URL(file.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                check(conn.responseCode == 200) { "Download failed (HTTP ${conn.responseCode})" }
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = input.read(buf)
                        var fileDone = 0L
                        while (read >= 0) {
                            out.write(buf, 0, read)
                            digest.update(buf, 0, read)
                            fileDone += read
                            onProgress((done + fileDone) / total)
                            read = input.read(buf)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (part.length() != file.sizeBytes || actual != file.sha256) {
                part.delete()
                error("Downloaded model failed its integrity check")
            }
            check(part.renameTo(target)) { "Could not save model file" }
            done += file.sizeBytes
            onProgress(done / total)
        }
    }
}
