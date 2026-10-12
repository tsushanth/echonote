package com.kreativekoala.echonote.service

/**
 * Downsamples mono [input] by [ratio] (source rate / target rate) by averaging the source
 * samples under each output sample, which filters out most of what would otherwise alias.
 * [startOffset] is the fractional source position carried over from the previous chunk;
 * returns the output samples and the offset to carry into the next chunk.
 */
fun resampleByAveraging(input: ShortArray, ratio: Double, startOffset: Double): Pair<ShortArray, Double> {
    if (ratio == 1.0) return input to 0.0
    val out = ShortArray((input.size / ratio).toInt() + 2)
    var n = 0
    var pos = startOffset
    while (pos < input.size) {
        val a = pos.toInt()
        val b = minOf(input.size, maxOf(a + 1, (pos + ratio).toInt()))
        var sum = 0L
        for (i in a until b) sum += input[i]
        out[n++] = (sum / (b - a)).toShort()
        pos += ratio
    }
    return out.copyOf(n) to (pos - input.size)
}
