package com.kreativekoala.echonote.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.ui.theme.Lip

/**
 * A thick-border, hard-offset-shadow depth cue for dark surfaces — the one
 * borrowed idea from the competitive bar (Echonote's black-border-plus-hard-
 * shadow cards), adapted to a dark theme as a lighter slab instead of a black
 * outline. Leave [depth] of bottom padding on the caller so the slab isn't
 * clipped.
 */
fun Modifier.lip(shape: Shape, color: Color = Lip, depth: Dp = 4.dp): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = depth.toPx()) {
            drawOutline(outline, color)
        }
    }
