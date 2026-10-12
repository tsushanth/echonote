package com.kreativekoala.echonote.service

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One downloadable model file, pinned by size and SHA-256 because native code parses it. */
data class ModelFile(val url: String, val fileName: String, val sizeBytes: Long, val sha256: String)

/** A model published as a .tar.bz2: the archive is pinned, and so are the files taken out of it. */
data class ArchiveModel(val url: String, val sizeBytes: Long, val sha256: String, val files: List<ModelFile>)

object SherpaModels {
    private const val HF = "https://huggingface.co/csukuangfj"

    /** Whisper small, int8: roughly 375 MB. Replaces the base model of earlier builds. */
    const val WHISPER_SMALL_DIR = "whisper-small-int8"
    val WHISPER_SMALL = listOf(
        ModelFile(
            "$HF/sherpa-onnx-whisper-small/resolve/main/small-encoder.int8.onnx",
            "encoder.onnx", 112_442_483, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"
        ),
        ModelFile(
            "$HF/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx",
            "decoder.onnx", 262_226_114, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"
        ),
        ModelFile(
            "$HF/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt",
            "tokens.txt", 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
        )
    )

    /** Directory of the Whisper base model shipped by earlier builds; deleted when found. */
    const val LEGACY_WHISPER_BASE_DIR = "whisper-base-int8"

    /**
     * NVIDIA NeMo FastConformer CTC, English/German/Spanish/French, int8. Its Hugging Face repository is empty;
     * the only copy is this archive on the sherpa-onnx releases page. The two files inside are what is used.
     */
    const val NEMO_DIR = "nemo-fastconformer-int8"
    private const val NEMO_PREFIX = "sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8"
    val NEMO_FAST_CONFORMER = ArchiveModel(
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$NEMO_PREFIX.tar.bz2",
        sizeBytes = 102_875_642,
        sha256 = "ea7434ecff117272a70b8a60b70cfc2f04b9b07553aa0ecb91065b69c7b91ec5",
        files = listOf(
            ModelFile("", "model.int8.onnx", 132_445_228, "85451f03e004d607f86f4d6291ca55a8ca984d7e32dedc828cebe7fe61447511"),
            ModelFile("", "tokens.txt", 24_057, "29effa220ddd8fcae96d2d6c78c2e24de4cd9dfaa4ceb86d54dc3d22259c1f93")
        )
    )

    const val SENSE_VOICE_DIR = "sensevoice-int8"
    val SENSE_VOICE = listOf(
        ModelFile(
            "$HF/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main/model.int8.onnx",
            "model.onnx", 237_115_547, "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d"
        ),
        ModelFile(
            "$HF/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main/tokens.txt",
            "tokens.txt", 315_894, "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
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
            if (!(target.exists() && target.length() == file.sizeBytes)) {
                val base = done
                downloadVerified(file.url, target, file.sizeBytes, file.sha256) { onProgress((base + it) / total) }
            }
            done += file.sizeBytes
            onProgress(done / total)
        }
    }

    /**
     * Downloads a .tar.bz2 (verified against its pinned size and SHA-256), takes out only the files in
     * [archive], verifies each, and deletes the archive. Progress is 0..0.9 for the download, the rest extraction.
     */
    fun ensureArchive(dir: File, archive: ArchiveModel, onProgress: (Float) -> Unit) {
        if (isReady(dir, archive.files)) return
        dir.mkdirs()
        val tmp = File(dir, "download.tar.bz2")
        try {
            downloadVerified(archive.url, tmp, archive.sizeBytes, archive.sha256) {
                onProgress(0.9f * it / archive.sizeBytes)
            }
            extractModelFiles(tmp, archive.files, dir)
            onProgress(1f)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Extracts from a .tar.bz2 only the entries whose file name matches one of [files] (anything else, and
     * any directory part of an entry name, is ignored), writing each to [destDir] after checking its size and
     * SHA-256. Throws if a wanted file is missing or fails its check.
     */
    fun extractModelFiles(archiveFile: File, files: List<ModelFile>, destDir: File) {
        val wanted = files.associateBy { it.fileName }
        val found = mutableSetOf<String>()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archiveFile.inputStream()))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (!entry.isFile) continue
                val name = entry.name.substringAfterLast('/')
                val spec = wanted[name] ?: continue
                if (entry.size != spec.sizeBytes) error("Archive entry $name has an unexpected size")
                val part = File(destDir, "$name.part")
                val digest = MessageDigest.getInstance("SHA-256")
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = tar.read(buf)
                    while (read >= 0) {
                        out.write(buf, 0, read); digest.update(buf, 0, read); read = tar.read(buf)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (part.length() != spec.sizeBytes || actual != spec.sha256) {
                    part.delete()
                    error("Extracted $name failed its integrity check")
                }
                val target = File(destDir, name)
                target.delete()
                check(part.renameTo(target)) { "Could not save $name" }
                found += name
            }
        }
        val missing = wanted.keys - found
        if (missing.isNotEmpty()) error("Archive is missing ${missing.joinToString()}")
    }

    /** Streams [url] to [target] via a .part file, then checks size and SHA-256 before moving it into place. */
    private fun downloadVerified(url: String, target: File, sizeBytes: Long, sha256: String, onBytes: (Long) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
                    var got = 0L
                    while (read >= 0) {
                        out.write(buf, 0, read)
                        digest.update(buf, 0, read)
                        got += read
                        onBytes(got)
                        read = input.read(buf)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != sizeBytes || actual != sha256) {
            part.delete()
            error("Downloaded model failed its integrity check")
        }
        target.delete()
        check(part.renameTo(target)) { "Could not save model file" }
    }
}
