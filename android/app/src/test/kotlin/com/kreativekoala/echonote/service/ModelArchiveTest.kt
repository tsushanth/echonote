package com.kreativekoala.echonote.service

import java.io.File
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun tarBz2(vararg entries: Pair<String, ByteArray>): File {
        val f = tmp.newFile()
        TarArchiveOutputStream(BZip2CompressorOutputStream(f.outputStream())).use { tar ->
            for ((name, data) in entries) {
                tar.putArchiveEntry(TarArchiveEntry(name).also { it.size = data.size.toLong() })
                tar.write(data); tar.closeArchiveEntry()
            }
        }
        return f
    }

    private val model = ByteArray(5000) { (it % 251).toByte() }
    private val tokens = "a\nb\nc\n".toByteArray()
    private val specs = listOf(
        ModelFile("", "model.int8.onnx", model.size.toLong(), sha(model)),
        ModelFile("", "tokens.txt", tokens.size.toLong(), sha(tokens))
    )

    @Test
    fun extractsOnlyTheWantedFilesAndIgnoresEverythingElse() {
        val archive = tarBz2(
            "pkg/model.int8.onnx" to model, "pkg/tokens.txt" to tokens,
            "pkg/test_wavs/a.wav" to ByteArray(100), "../evil.txt" to "x".toByteArray()
        )
        val dest = tmp.newFolder()
        ModelDownloader.extractModelFiles(archive, specs, dest)
        assertEquals(listOf("model.int8.onnx", "tokens.txt"), dest.list()!!.sorted())
        assertTrue(ModelDownloader.isReady(dest, specs))
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test
    fun aTamperedFileIsRejectedAndLeavesNothingBehind() {
        val bad = model.copyOf().also { it[10] = 99 }
        val archive = tarBz2("pkg/model.int8.onnx" to bad, "pkg/tokens.txt" to tokens)
        val dest = tmp.newFolder()
        try { ModelDownloader.extractModelFiles(archive, specs, dest); fail("expected failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("integrity")) }
        assertFalse(File(dest, "model.int8.onnx").exists())
        assertFalse(File(dest, "model.int8.onnx.part").exists())
    }

    @Test
    fun aMissingFileOrWrongSizeFails() {
        val dest = tmp.newFolder()
        try { ModelDownloader.extractModelFiles(tarBz2("pkg/model.int8.onnx" to model), specs, dest); fail("missing") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("missing")) }
        try { ModelDownloader.extractModelFiles(tarBz2("pkg/model.int8.onnx" to model + 1, "pkg/tokens.txt" to tokens), specs, tmp.newFolder()); fail("size") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("size")) }
    }
}
