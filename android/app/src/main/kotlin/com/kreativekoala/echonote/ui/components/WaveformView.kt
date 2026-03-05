package com.kreativekoala.echonote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.ui.theme.WaveformBlue
import com.kreativekoala.echonote.ui.theme.WaveformGray

@Composable
fun WaveformView(
    samples: FloatArray,
    progress: Float = 0f,
    onSeek: ((Float) -> Unit)? = null,
    activeColor: Color = WaveformBlue,
    inactiveColor: Color = WaveformGray,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(seekProgress)
                        }
                    }
                } else Modifier
            )
    ) {
        if (samples.isEmpty()) return@Canvas

        val bwPx = barWidth.toPx()
        val spPx = spacing.toPx()
        val totalBarWidth = bwPx + spPx
        val visibleBars = (size.width / totalBarWidth).toInt().coerceAtLeast(1)
        val cornerRadius = CornerRadius(bwPx / 2f)
        val centerY = size.height / 2f

        val displaySamples = if (samples.size > visibleBars) {
            FloatArray(visibleBars) { i ->
                val start = (i.toFloat() / visibleBars * samples.size).toInt()
                val end = ((i + 1).toFloat() / visibleBars * samples.size).toInt()
                    .coerceAtMost(samples.size)
                var sum = 0f
                for (j in start until end) sum += samples[j]
                sum / (end - start).coerceAtLeast(1)
            }
        } else {
            samples
        }

        val progressIndex = (progress * displaySamples.size).toInt()

        for (i in displaySamples.indices) {
            val amplitude = displaySamples[i].coerceIn(0f, 1f)
            val barHeight = (amplitude * size.height).coerceAtLeast(2f)
            val x = i * totalBarWidth
            val y = centerY - barHeight / 2f
            val color = if (i < progressIndex) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(bwPx, barHeight),
                cornerRadius = cornerRadius
            )
        }

        if (progress > 0f && progress < 1f) {
            val playheadX = progress * size.width
            drawLine(
                color = activeColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun LiveWaveformView(
    levels: List<Float>,
    color: Color = Color(0xFFFF3B30),
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val bwPx = barWidth.toPx()
        val spPx = spacing.toPx()
        val totalBarWidth = bwPx + spPx
        val visibleBars = (size.width / totalBarWidth).toInt().coerceAtLeast(1)
        val cornerRadius = CornerRadius(bwPx / 2f)
        val centerY = size.height / 2f

        val displayLevels = if (levels.size > visibleBars) {
            levels.takeLast(visibleBars)
        } else {
            levels
        }

        val startX = size.width - displayLevels.size * totalBarWidth

        for (i in displayLevels.indices) {
            val amplitude = displayLevels[i].coerceIn(0f, 1f)
            val barHeight = (amplitude * size.height).coerceAtLeast(2f)
            val x = startX + i * totalBarWidth
            val y = centerY - barHeight / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(bwPx, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
