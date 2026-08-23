package com.cyberrin.giswrap.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cyberrin.giswrap.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
        drawCircle(color, radius = s * 0.28f, center = Offset(s * 0.42f, s * 0.42f), style = stroke)
        drawLine(
            color = color,
            start = Offset(s * 0.63f, s * 0.63f),
            end = Offset(s * 0.85f, s * 0.85f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun LightModeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        val centre = Offset(s * 0.5f, s * 0.5f)
        drawCircle(color, radius = s * 0.17f, center = centre, style = stroke)
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = color,
                start = centre + Offset(dx * s * 0.27f, dy * s * 0.27f),
                end = centre + Offset(dx * s * 0.38f, dy * s * 0.38f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun DarkModeGlyph(color: Color, modifier: Modifier = Modifier) {
    Spacer(modifier.drawWithCache {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        val centre = Offset(s * 0.5f, s * 0.52f)
        val radius = s * 0.27f
        val disc = Path().apply { addOval(Rect(center = centre, radius = radius)) }
        val bite = Path().apply {
            addOval(
                Rect(
                    center = centre + Offset(radius * 0.60f, -radius * 0.45f),
                    radius = radius * 0.92f,
                )
            )
        }
        val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
        onDrawBehind { drawPath(crescent, color, style = stroke) }
    })
}

@Composable
fun SystemModeGlyph(color: Color, modifier: Modifier = Modifier) {
    Spacer(modifier.drawWithCache {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        val centre = Offset(s * 0.5f, s * 0.5f)
        val radius = s * 0.27f

        val half = Path().apply {
            addArc(Rect(center = centre, radius = radius), 90f, 180f)
            close()
        }
        onDrawBehind {
            drawCircle(color, radius = radius, center = centre, style = stroke)
            drawPath(half, color)
        }
    })
}
