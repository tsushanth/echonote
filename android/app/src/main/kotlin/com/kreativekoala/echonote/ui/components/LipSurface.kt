package com.kreativekoala.echonote.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A hard-offset slab under a control, used only on the primary round buttons so
 * they read as pressable. [color] defaults to the theme's sunk surface.
 * Leave [depth] of bottom padding on the caller so the slab isn't clipped.
 */
fun Modifier.lip(
    shape: Shape,
    color: Color = Color.Unspecified,
    depth: Dp = 4.dp
): Modifier = composed {
    val resolved = if (color == Color.Unspecified) MaterialTheme.colorScheme.outlineVariant else color
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = depth.toPx()) {
            drawOutline(outline, resolved)
        }
    }
}
