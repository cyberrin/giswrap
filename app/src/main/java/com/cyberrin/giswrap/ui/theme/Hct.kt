package com.cyberrin.giswrap.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign

private object Viewing {
    private val whitePoint = doubleArrayOf(95.047, 100.0, 108.883)

    private val adaptingLuminance = (200.0 / Math.PI) * yFromTone(50.0) / 100.0

    val n: Double = yFromTone(50.0) / whitePoint[1]
    val z: Double = 1.48 + sqrt(n)
    val nbb: Double = 0.725 / n.pow(0.2)
    val ncb: Double = nbb

    const val C: Double = 0.69
    const val NC: Double = 1.0

    val rgbD: DoubleArray
    val fl: Double
    val aw: Double

    init {
        val rw = DoubleArray(3) { i ->
            XYZ_TO_CAM16[i][0] * whitePoint[0] +
                XYZ_TO_CAM16[i][1] * whitePoint[1] +
                XYZ_TO_CAM16[i][2] * whitePoint[2]
        }
        val d = (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0)).coerceIn(0.0, 1.0)
        rgbD = DoubleArray(3) { i -> d * 100.0 / rw[i] + 1.0 - d }

        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        fl = k4 * adaptingLuminance + 0.1 * (1.0 - k4) * (1.0 - k4) *
            cbrt(5.0 * adaptingLuminance)

        val rgbA = DoubleArray(3) { i ->
            val f = ((fl * rgbD[i] * rw[i]) / 100.0).pow(0.42)
            400.0 * f / (f + 27.13)
        }
        aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
    }
}

private val XYZ_TO_CAM16 = arrayOf(
    doubleArrayOf(0.401288, 0.650173, -0.051461),
    doubleArrayOf(-0.250268, 1.204414, 0.045854),
    doubleArrayOf(-0.002079, 0.048952, 0.953127),
)

private fun linearize(channel: Int): Double {
    val c = channel / 255.0
    return 100.0 * if (c <= 0.040449936) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun delinearize(value: Double): Int {
    val n = value / 100.0
    val c = if (n <= 0.0031308) n * 12.92 else 1.055 * n.pow(1.0 / 2.4) - 0.055
    return (c * 255.0).roundToInt().coerceIn(0, 255)
}

internal fun yFromTone(tone: Double): Double {
    val ft = (tone + 16.0) / 116.0
    val ft3 = ft * ft * ft
    return 100.0 * if (ft3 > 216.0 / 24389.0) ft3 else (116.0 * ft - 16.0) / (24389.0 / 27.0)
}

internal fun toneFromY(y: Double): Double {
    val yn = y / 100.0
    return if (yn <= 216.0 / 24389.0) yn * (24389.0 / 27.0) else 116.0 * cbrt(yn) - 16.0
}

data class Hct(val hue: Double, val chroma: Double, val tone: Double)

fun hctOf(color: Color): Hct {
    val argb = color.toArgb()
    val lr = linearize((argb shr 16) and 0xFF)
    val lg = linearize((argb shr 8) and 0xFF)
    val lb = linearize(argb and 0xFF)

    val x = 0.41233895 * lr + 0.35762064 * lg + 0.18051042 * lb
    val y = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
    val z = 0.01932141 * lr + 0.11916382 * lg + 0.95034478 * lb

    val rA = adapt(Viewing.rgbD[0] * (0.401288 * x + 0.650173 * y - 0.051461 * z))
    val gA = adapt(Viewing.rgbD[1] * (-0.250268 * x + 1.204414 * y + 0.045854 * z))
    val bA = adapt(Viewing.rgbD[2] * (-0.002079 * x + 0.048952 * y + 0.953127 * z))

    val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
    val b = (rA + gA - 2.0 * bA) / 9.0
    val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
    val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0

    val hue = Math.toDegrees(atan2(b, a)).mod(360.0)
    val j = 100.0 * (p2 * Viewing.nbb / Viewing.aw).pow(Viewing.C * Viewing.z)

    val huePrime = if (hue < 20.14) hue + 360.0 else hue
    val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
    val t = (50000.0 / 13.0) * eHue * Viewing.NC * Viewing.ncb * hypot(a, b) / (u + 0.305)
    val alpha = t.pow(0.9) * (1.64 - 0.29.pow(Viewing.n)).pow(0.73)

    return Hct(hue, alpha * sqrt(j / 100.0), toneFromY(y))
}

private fun adapt(value: Double): Double {
    val f = ((Viewing.fl * abs(value)) / 100.0).pow(0.42)
    return (400.0 * f / (f + 27.13)).withSign(value)
}

private fun greyOf(tone: Double): Color {
    val v = delinearize(yFromTone(tone))
    return Color(v, v, v)
}

fun hctColor(hue: Double, chroma: Double, tone: Double): Color {
    if (chroma < 1.0 || tone.roundToInt() <= 0 || tone.roundToInt() >= 100) return greyOf(tone)

    val hueRadians = Math.toRadians(hue.mod(360.0))
    val yTarget = yFromTone(tone)
    var low = 0.0
    var high = chroma
    var mid = chroma
    var answer: Color? = null

    while (abs(low - high) >= 0.4) {
        val candidate = solveAtChroma(hueRadians, mid, yTarget)
        if (candidate == null) high = mid else { answer = candidate; low = mid }
        mid = low + (high - low) / 2.0
    }
    return answer ?: greyOf(tone)
}

private fun solveAtChroma(hueRadians: Double, chroma: Double, yTarget: Double): Color? {
    var j = sqrt(yTarget) * 11.0
    val tInner = (1.64 - 0.29.pow(Viewing.n)).pow(0.73)
    val hSin = sin(hueRadians)
    val hCos = cos(hueRadians)
    val eHue = 0.25 * (cos(hueRadians + 2.0) + 3.8)
    val p1 = eHue * (50000.0 / 13.0) * Viewing.NC * Viewing.ncb

    repeat(5) { round ->
        val jNorm = j / 100.0
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(jNorm)
        val t = (alpha / tInner).pow(1.0 / 0.9)
        val p2 = (Viewing.aw * jNorm.pow(1.0 / Viewing.C / Viewing.z)) / Viewing.nbb
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin

        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0

        val r = unadapt(rA) / Viewing.rgbD[0]
        val g = unadapt(gA) / Viewing.rgbD[1]
        val bl = unadapt(bA) / Viewing.rgbD[2]

        val x = 1.86206786 * r - 1.01125463 * g + 0.14918677 * bl
        val y = 0.38752654 * r + 0.62144744 * g - 0.00897398 * bl
        val z = -0.01584150 * r - 0.03412294 * g + 1.04996444 * bl

        val lr = 3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z
        val lg = -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z
        val lb = 0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z

        if (lr < 0.0 || lg < 0.0 || lb < 0.0) return null
        val yGot = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
        if (yGot <= 0.0) return null

        if (round == 4 || abs(yGot - yTarget) < 0.002 * yTarget) {
            if (lr > 100.01 || lg > 100.01 || lb > 100.01) return null
            return Color(delinearize(lr), delinearize(lg), delinearize(lb))
        }
        j -= (yGot - yTarget) * j / (2.0 * yGot)
    }
    return null
}

private fun unadapt(value: Double): Double {
    val base = ((27.13 * abs(value)) / (400.0 - abs(value))).coerceAtLeast(0.0)
    return ((100.0 / Viewing.fl) * base.pow(1.0 / 0.42)) * value.sign
}

class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = HashMap<Int, Color>(24)

    fun tone(tone: Int): Color = cache.getOrPut(tone) { hctColor(hue, chroma, tone.toDouble()) }
}
