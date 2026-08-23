package com.cyberrin.giswrap.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberrin.giswrap.ui.theme.CuteTuning
import com.cyberrin.giswrap.ui.theme.cuteSeed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.withSign
import kotlin.random.Random

private const val STAR_WAIST = 0.44f

private fun superellipse(t: Float, w: Float, h: Float, squareness: Float): Offset {
    val c = cos(t)
    val s = sin(t)
    val e = 2f / squareness
    return Offset(
        (w / 2f) * abs(c).pow(e).withSign(c),
        (h / 2f) * abs(s).pow(e).withSign(s),
    )
}

private fun Float.pow(e: Float): Float = Math.pow(toDouble(), e.toDouble()).toFloat()

private fun ringNoise(count: Int, seed: Int, passes: Int = 2): FloatArray {
    val rng = Random(seed)
    var values = FloatArray(count) { rng.nextFloat() * 2f - 1f }
    repeat(passes) {
        val next = FloatArray(count)
        for (i in 0 until count) {
            val prev = values[(i - 1 + count) % count]
            val after = values[(i + 1) % count]
            next[i] = (prev + 2f * values[i] + after) / 4f
        }
        values = next
    }
    return values
}

private fun FloatArray.at(u: Float): Float {
    val n = size
    val i = (Math.floor(u.toDouble()).toInt() % n + n) % n
    val frac = u - Math.floor(u.toDouble()).toFloat()
    return this[i] * (1f - frac) + this[(i + 1) % n] * frac
}

fun wobblyPath(
    size: Size,
    squareness: Float = 5f,
    depth: Float = 12f,
    seed: Int = 5,
    lobeLength: Float = 62f,
    drift: Float = 4f,
    unevenness: Float = 0.75f,
    perLobe: Int = 10,
): Path {
    val key = outlineKey(size, squareness, depth, seed, lobeLength, drift, unevenness, perLobe)
    synchronized(outlineCache) { outlineCache[key] }?.let { return it }
    val built = buildWobblyPath(size, squareness, depth, seed, lobeLength, drift, unevenness, perLobe)
    synchronized(outlineCache) { outlineCache[key] = built }
    return built
}

internal data class OutlineKey(
    val w: Int,
    val h: Int,
    val squareness: Float,
    val depth: Float,
    val seed: Int,
    val lobeLength: Float,
    val drift: Float,
    val unevenness: Float,
    val perLobe: Int,
)

internal fun outlineKey(
    size: Size,
    squareness: Float,
    depth: Float,
    seed: Int,
    lobeLength: Float,
    drift: Float,
    unevenness: Float,
    perLobe: Int,
) = OutlineKey(
    w = size.width.roundToInt(),
    h = size.height.roundToInt(),
    squareness = squareness,
    depth = depth,
    seed = seed,
    lobeLength = lobeLength,
    drift = drift,
    unevenness = unevenness,
    perLobe = perLobe,
)

private val outlineCache = object : LinkedHashMap<OutlineKey, Path>(96, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<OutlineKey, Path>) = size > 96
}

private fun buildWobblyPath(
    size: Size,
    squareness: Float,
    depth: Float,
    seed: Int,
    lobeLength: Float,
    drift: Float,
    unevenness: Float,
    perLobe: Int,
): Path {
    val margin = depth + drift + 1f
    val w = (size.width - margin * 2f).coerceAtLeast(1f)
    val h = (size.height - margin * 2f).coerceAtLeast(1f)
    val cx = size.width / 2f
    val cy = size.height / 2f

    val baseN = 180
    val bx = FloatArray(baseN)
    val by = FloatArray(baseN)
    for (i in 0 until baseN) {
        val p = superellipse(2f * PI.toFloat() * i / baseN, w, h, squareness)
        bx[i] = p.x
        by[i] = p.y
    }
    val cumulative = FloatArray(baseN + 1)
    for (i in 0 until baseN) {
        val j = (i + 1) % baseN
        cumulative[i + 1] = cumulative[i] + hypot(bx[j] - bx[i], by[j] - by[i])
    }
    val perimeter = cumulative[baseN].takeIf { it > 0f } ?: 1f

    val lobes = ((perimeter / lobeLength).toInt()).coerceAtLeast(6)
    val depthNoise = ringNoise(lobes, seed)
    val phaseNoise = ringNoise(lobes, seed + 977)
    val driftNoise = ringNoise((lobes / 2).coerceAtLeast(5), seed + 313)

    val total = lobes * perLobe

    val px = FloatArray(total)
    val py = FloatArray(total)

    var lo = 0
    for (j in 0 until total) {
        val distance = perimeter * j / total
        while (lo < baseN - 1 && cumulative[lo + 1] < distance) lo++
        val span = (cumulative[lo + 1] - cumulative[lo]).takeIf { it > 0f } ?: 1f
        val frac = (distance - cumulative[lo]) / span
        val next = (lo + 1) % baseN
        val baseX = bx[lo] + (bx[next] - bx[lo]) * frac
        val baseY = by[lo] + (by[next] - by[lo]) * frac

        val prev = (lo - 1 + baseN) % baseN
        val ahead = (lo + 2) % baseN
        val tx = bx[ahead] - bx[prev]
        val ty = by[ahead] - by[prev]
        val len = hypot(tx, ty).takeIf { it > 0f } ?: 1f
        val nx = ty / len
        val ny = -tx / len

        val u = j.toFloat() / perLobe
        val phase = phaseNoise.at(u) * 0.5f
        val scallop = cos(2f * PI.toFloat() * (u + phase))
        val thisDepth = depth * (1f + unevenness * depthNoise.at(u))
        val wander = drift * driftNoise.at(u * driftNoise.size / lobes)
        val offset = scallop * thisDepth + wander

        px[j] = cx + baseX + nx * offset
        py[j] = cy + baseY + ny * offset
    }

    fun wrap(i: Int) = ((i % total) + total) % total
    return Path().apply {
        moveTo(px[0], py[0])
        for (i in 0 until total) {
            val a = wrap(i - 1)
            val b = wrap(i)
            val c = wrap(i + 1)
            val d = wrap(i + 2)
            cubicTo(
                px[b] + (px[c] - px[a]) / 6f, py[b] + (py[c] - py[a]) / 6f,
                px[c] - (px[d] - px[b]) / 6f, py[c] - (py[d] - py[b]) / 6f,
                px[c], py[c],
            )
        }
        close()
    }
}

fun starPath(cx: Float, cy: Float, r: Float, seed: Int): Path {
    val rng = Random(seed)
    val tilt = (rng.nextFloat() - 0.5f) * 0.44f
    return Path().apply {
        for (i in 0 until 10) {
            val base = -PI.toFloat() / 2f + PI.toFloat() * i / 5f + tilt
            val radius = (if (i % 2 == 0) r else r * STAR_WAIST) *
                (0.84f + rng.nextFloat() * 0.32f)
            val angle = base + (rng.nextFloat() - 0.5f) * 0.20f
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private data class Speck(val x: Float, val y: Float, val r: Float, val seed: Int, val kind: Int)

private fun starField(seed: Int = 19): List<Speck> {
    val rng = Random(seed)
    val cols = 4
    val rows = 8
    val out = mutableListOf<Speck>()
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val keep = 0.88f - 0.5f * (row.toFloat() / rows)
            if (rng.nextFloat() > keep) continue
            val x = (col + 0.5f) / cols + (rng.nextFloat() - 0.5f) * 0.16f
            val y = (row + 0.5f) / rows + (rng.nextFloat() - 0.5f) * 0.08f
            if (x !in 0.02f..0.98f || y !in 0.02f..0.98f) continue
            if (x in 0.28f..0.72f && y in 0.24f..0.42f) continue
            val roll = rng.nextFloat()
            val spin = rng.nextInt()

            out += when {
                roll < 0.42f -> Speck(x, y, 15f + rng.nextFloat() * 8f, spin, 0)
                roll < 0.62f -> Speck(x, y, 9f + rng.nextFloat() * 4f, spin, 1)
                else -> Speck(x, y, 3f + rng.nextFloat() * 2f, spin, 2)
            }
        }
    }
    return out
}

private val StarFieldCache = starField()

class Star internal constructor(internal val path: Path, internal val kind: Int)

fun Density.buildSky(size: Size, scale: Float = 1f): List<Star> = StarFieldCache.map { speck ->
    val cx = speck.x * size.width
    val cy = speck.y * size.height
    val r = speck.r.dp.toPx() * scale
    val path = if (speck.kind == 2) {
        Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    } else {
        starPath(cx, cy, r, speck.seed)
    }
    Star(path, speck.kind)
}

fun DrawScope.drawSky(sky: List<Star>, bright: Color, warm: Color, dim: Color) {
    sky.forEach { star ->
        drawPath(
            star.path,
            when (star.kind) {
                0 -> bright
                1 -> warm
                else -> dim
            },
        )
    }
}

val CuteNightSky = Color(0xFF1E1E2E)

data class CuteEdge(
    val depth: Dp,
    val lobe: Dp,
    val drift: Dp,
    val squareness: Float = 5f,
)

fun Density.wobblyPath(
    size: Size,
    edge: CuteEdge,
    seed: Int,
    tuning: CuteTuning = CuteTuning(),
): Path = wobblyPath(
    size = size,
    squareness = edge.squareness * (2.2f - 1.2f * tuning.morph.coerceIn(0f, 1f)),
    depth = edge.depth.toPx() * tuning.wobble,
    seed = seed,
    lobeLength = edge.lobe.toPx() / tuning.frequency.coerceAtLeast(0.2f),
    drift = edge.drift.toPx() * tuning.wobble,
)

fun DrawScope.drawWobblyPanel(
    path: Path,
    fill: Color,
    outline: Color,
    strokeWidth: Float,
) {
    drawPath(path, fill)

    drawPath(path, outline, style = Stroke(width = strokeWidth))
}
