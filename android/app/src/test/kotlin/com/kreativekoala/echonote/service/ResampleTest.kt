package com.kreativekoala.echonote.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResampleTest {
    @Test
    fun sameRateIsPassedThrough() {
        val input = shortArrayOf(1, 2, 3)
        val (out, offset) = resampleByAveraging(input, 1.0, 0.0)
        assertEquals(input.toList(), out.toList())
        assertEquals(0.0, offset, 0.0)
    }

    @Test
    fun constantSignalStaysConstantAndLengthMatchesRatio() {
        val input = ShortArray(48_000) { 1000 }
        val (out, _) = resampleByAveraging(input, 3.0, 0.0)
        assertEquals(16_000, out.size)
        assertTrue(out.all { it.toInt() == 1000 })
    }

    @Test
    fun highFrequencyToneIsAttenuatedNotAliased() {
        // Alternating +/-: the highest representable tone at 48 kHz. Plain decimation by 3 would
        // keep full amplitude; averaging must cancel it.
        val input = ShortArray(48_000) { if (it % 2 == 0) 10_000 else -10_000 }
        val (out, _) = resampleByAveraging(input, 3.0, 0.0)
        assertTrue(out.maxOf { kotlin.math.abs(it.toInt()) } <= 3_400)
    }

    @Test
    fun fractionalOffsetCarriesAcrossChunks() {
        val a = ShortArray(1000) { 5 }
        val (_, off1) = resampleByAveraging(a, 2.75, 0.0)
        assertTrue(off1 in 0.0..2.75)
        val whole = resampleByAveraging(ShortArray(2000) { 5 }, 2.75, 0.0).first.size
        val (o1, off) = resampleByAveraging(a, 2.75, 0.0)
        val (o2, _) = resampleByAveraging(a, 2.75, off)
        assertTrue(kotlin.math.abs(o1.size + o2.size - whole) <= 1)
    }
}
