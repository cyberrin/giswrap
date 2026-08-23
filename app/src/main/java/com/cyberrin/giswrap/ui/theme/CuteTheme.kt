package com.cyberrin.giswrap.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.cyberrin.giswrap.domain.model.CuteColour

val LocalCuteTheme = staticCompositionLocalOf { false }

// @Immutable is a promise, not a deduction: the Map alone makes this unstable, so every
// drawWithCache lambda capturing it would be dropped and each panel outline rebuilt per frame.
@Immutable
data class CuteTuning(
    val wobble: Float = 1f,
    val stars: Float = 1f,
    val weights: Map<CuteColour, Float> = emptyMap(),
    val frequency: Float = 1f,
    val morph: Float = 1f,
)

val LocalCuteTuning = compositionLocalOf { CuteTuning() }

fun cuteSeed(identity: Any?): Int {
    val h = identity?.hashCode() ?: 0
    var x = h * -0x61c88647
    x = x xor (x ushr 15)
    x *= 0x2545f491
    return x xor (x ushr 13)
}
