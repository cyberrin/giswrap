@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cyberrin.giswrap.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.graphics.shapes.toPath
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.ui.art.CuteEdge
import com.cyberrin.giswrap.ui.art.wobblyPath
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ponytail: fixed 320px short side, measure and cap if a large widget ever looks soft.
private const val PANEL_SHORT_SIDE = 320

private val WidgetEdge = CuteEdge(depth = Dp(7f), lobe = Dp(58f), drift = Dp(2f))

private fun heroPath(w: Int, h: Int): Path {
    val path = MaterialShapes.Cookie12Sided.toPath()
    val bounds = RectF()
    path.computeBounds(bounds, true)

    val side = minOf(w, h).toFloat()
    val scale = side / maxOf(bounds.width(), bounds.height())
    path.transform(
        Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (w - bounds.width() * scale) / 2f - bounds.left * scale,
                (h - bounds.height() * scale) / 2f - bounds.top * scale,
            )
        }
    )
    return path
}

fun widgetPanelBitmap(
    aspect: Float,
    cellShortDp: Float,
    colours: WidgetColours,
): Bitmap {
    val (w, h) = panelPixelSize(aspect)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val short = minOf(w, h)
    val inset = panelInset(short, cellShortDp, colours.border)
    val bw = w - 2 * inset
    val bh = h - 2 * inset

    val path = when {
        colours.hero -> heroPath(bw.toInt(), bh.toInt())
        colours.cute ->

            with(Density(1f)) {
                wobblyPath(Size(bw, bh), WidgetEdge, 5, colours.tuning)
            }.asAndroidPath()
        else -> Path().apply {
            val radius = cornerRadius(short, cellShortDp)
            addRoundRect(RectF(0f, 0f, bw, bh), radius, radius, Path.Direction.CW)
        }
    }
    path.offset(inset, inset)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = colours.panel.toArgb()
    canvas.drawPath(path, paint)

    if (colours.border) {
        paint.color = colours.text.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth(short, cellShortDp)
        canvas.drawPath(path, paint)
    }
    return bitmap
}

internal fun panelPixelSize(aspect: Float): Pair<Int, Int> {
    val safe = if (aspect.isFinite() && aspect > 0f) aspect else 1f
    val ratio = safe.coerceIn(1f / MAX_ASPECT, MAX_ASPECT)
    val long = maxOf(ratio, 1f / ratio)

    val short = minOf(PANEL_SHORT_SIDE.toFloat(), sqrt(MAX_PANEL_PIXELS / long))
        .roundToInt()
        .coerceAtLeast(1)

    return if (ratio >= 1f) {
        (short * ratio).roundToInt() to short
    } else {
        short to (short / ratio).roundToInt()
    }
}

internal fun isSideBySide(aspect: Float): Boolean =
    aspect.isFinite() && aspect >= SIDE_BY_SIDE_ASPECT

private const val SIDE_BY_SIDE_ASPECT = 1.5f

internal fun panelInset(bitmapShort: Int, cellShortDp: Float, border: Boolean): Float =
    if (border) strokeWidth(bitmapShort, cellShortDp) / 2f else 0f

internal fun strokeWidth(bitmapShort: Int, cellShortDp: Float): Float =
    if (cellShortDp <= 0f) STROKE_DP else STROKE_DP * bitmapShort / cellShortDp

internal fun cornerRadius(bitmapShort: Int, cellShortDp: Float): Float =
    if (cellShortDp <= 0f) CORNER_DP else CORNER_DP * bitmapShort / cellShortDp

internal fun stackedFace(heightDp: Float, readingDp: Float): Pair<Float, Float> {
    val slack = (heightDp - readingDp).coerceAtLeast(0f)
    return slack * ICON_SHARE to slack * GAP_SHARE
}

private const val ICON_SHARE = 4f / 7f
private const val GAP_SHARE = 1f / 7f

private const val MAX_ASPECT = 5f

private const val MAX_PANEL_PIXELS = 200_000f

private const val STROKE_DP = 2.6f
private const val CORNER_DP = 24f

fun weatherIconBitmap(context: Context, @DrawableRes art: Int, targetInkPx: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, art)
    if (drawable == null) {
        Log.w(TAG, "no drawable for weather art 0x${art.toString(16)}")
        return null
    }

    val probe = Bitmap.createBitmap(ICON_PROBE_PX, ICON_PROBE_PX, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, ICON_PROBE_PX, ICON_PROBE_PX)
    drawable.draw(Canvas(probe))
    val pixels = IntArray(ICON_PROBE_PX * ICON_PROBE_PX)
    probe.getPixels(pixels, 0, ICON_PROBE_PX, 0, 0, ICON_PROBE_PX, ICON_PROBE_PX)
    val bounds = inkBounds(pixels, ICON_PROBE_PX)
    probe.recycle()

    if (bounds == null) {
        Log.w(TAG, "weather art 0x${art.toString(16)} rasterised to no ink")
        return null
    }

    val (l, t, r, b) = bounds
    val left = (l - 1).coerceAtLeast(0)
    val top = (t - 1).coerceAtLeast(0)
    val right = (r + 1).coerceAtMost(ICON_PROBE_PX - 1)
    val bottom = (b + 1).coerceAtMost(ICON_PROBE_PX - 1)

    val inkWide = (right - left + 1) / ICON_PROBE_PX.toFloat()
    val inkTall = (bottom - top + 1) / ICON_PROBE_PX.toFloat()

    val box = iconRenderSize(targetInkPx, maxOf(inkWide, inkTall))
    val outWide = (inkWide * box).roundToInt().coerceAtLeast(1)
    val outTall = (inkTall * box).roundToInt().coerceAtLeast(1)

    val out = Bitmap.createBitmap(outWide, outTall, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.translate(-left / ICON_PROBE_PX.toFloat() * box, -top / ICON_PROBE_PX.toFloat() * box)
    drawable.setBounds(0, 0, box, box)
    drawable.draw(canvas)
    return out
}

internal fun iconRenderSize(targetInkPx: Int, inkFraction: Float): Int {
    val fraction = if (inkFraction.isFinite() && inkFraction > 0f) inkFraction else 1f
    return (targetInkPx / fraction).roundToInt().coerceIn(ICON_MIN_PX, ICON_MAX_PX)
}

private const val ICON_PROBE_PX = 96

private const val TAG = "GisWrapWidget"

private const val ICON_MIN_PX = 128

private const val ICON_MAX_PX = 1536

internal fun inkBounds(pixels: IntArray, size: Int): List<Int>? {
    var left = size
    var top = size
    var right = -1
    var bottom = -1
    for (y in 0 until size) {
        for (x in 0 until size) {
            if (pixels[y * size + x] ushr 24 == 0) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    return if (right < left || bottom < top) null else listOf(left, top, right, bottom)
}

fun temperatureBitmap(
    context: Context,
    text: String,
    font: AppFont,
    colours: WidgetColours,
): Bitmap? {
    val typeface = widgetTypeface(context, font) ?: return null

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface

        textSize = TEXT_RENDER_PX * colours.textScale
        color = colours.text.toArgb()
    }

    val metrics = paint.fontMetrics
    val width = paint.measureText(text).toInt().coerceAtLeast(1)
    val height = (metrics.bottom - metrics.top).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    Canvas(bitmap).drawText(text, 0f, -metrics.top, paint)
    return bitmap
}

private const val TEXT_RENDER_PX = 96f

private fun widgetTypeface(context: Context, font: AppFont): Typeface? = when (font) {
    AppFont.SYSTEM -> null
    AppFont.SERIF -> Typeface.SERIF
    AppFont.DOODLE ->
        runCatching { ResourcesCompat.getFont(context, R.font.rubik_doodle_shadow) }.getOrNull()
}
