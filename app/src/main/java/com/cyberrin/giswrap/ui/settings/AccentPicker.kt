package com.cyberrin.giswrap.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.ui.common.*
import com.cyberrin.giswrap.ui.theme.*
import java.util.Locale

private const val PICKER_TONE = 50.0

private const val MAX_CHROMA = 120.0

private const val SWATCH_CHROMA = 60.0

private val PRESET_HUES = listOf(25.0, 60.0, 105.0, 145.0, 200.0, 250.0, 300.0, 340.0)

private const val DISABLED_ALPHA = 0.38f

private val SwatchSize = 36.dp
private val TrackHeight = 22.dp
private val ThumbSize = 28.dp

@Composable
fun AccentPicker(
    accent: Color,
    onAccentChange: (Color) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val opening = remember { hctOf(accent) }
    var hue by remember { mutableFloatStateOf(opening.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(opening.chroma.toFloat()) }
    var hex by remember { mutableStateOf(accent.toHex()) }

    var emitted by remember { mutableStateOf(accent) }
    if (accent != emitted) {
        val hct = hctOf(accent)
        hue = hct.hue.toFloat()
        chroma = hct.chroma.toFloat()
        hex = accent.toHex()
        emitted = accent
    }

    fun send(color: Color) {
        emitted = color
        hex = color.toHex()
        onAccentChange(color)
    }

    fun emit(newHue: Float, newChroma: Float) {
        hue = newHue
        chroma = newChroma
        send(hctColor(newHue.toDouble(), newChroma.toDouble(), PICKER_TONE))
    }

    val presets = remember { PRESET_HUES.map { hctColor(it, SWATCH_CHROMA, PICKER_TONE) } }
    val hueTrack = remember { (0..12).map { hctColor(it * 30.0, SWATCH_CHROMA, PICKER_TONE) } }
    val chromaTrack = remember(hue) {
        (0..6).map { hctColor(hue.toDouble(), it * MAX_CHROMA / 6, PICKER_TONE) }
    }

    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PRESET_HUES.forEachIndexed { index, presetHue ->
                Swatch(
                    brush = SolidColor(presets[index]),
                    selected = nearHue(hue.toDouble(), presetHue),
                    label = stringResource(R.string.settings_accent_swatch),
                    enabled = enabled,
                    onClick = { emit(presetHue.toFloat(), SWATCH_CHROMA.toFloat()) },
                )
            }
        }

        SliderRow(
            label = stringResource(R.string.settings_accent_hue),
            fraction = hue / 360f,
            track = hueTrack,
            thumb = accent,
            enabled = enabled,
            onFraction = { emit(it * 360f, chroma) },
        )

        SliderRow(
            label = stringResource(R.string.settings_accent_chroma),
            fraction = chroma / MAX_CHROMA.toFloat(),
            track = chromaTrack,
            thumb = accent,
            enabled = enabled,
            onFraction = { emit(hue, (it * MAX_CHROMA).toFloat()) },
        )

        HexRow(
            hex = hex,
            accent = accent,
            enabled = enabled,
            onHexChange = { typed ->

                hex = typed
                parseHex(typed)?.let { send(it) }
            },
            onReset = { send(DefaultSeed) },
        )
    }
}

@Composable
private fun HexRow(
    hex: String,
    accent: Color,
    enabled: Boolean,
    onHexChange: (String) -> Unit,
    onReset: () -> Unit,
) {
    val valid = parseHex(hex) != null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(accent)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )

        OutlinedTextField(
            value = hex,
            onValueChange = { typed ->

                onHexChange(
                    "#" + typed.removePrefix("#")
                        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        .take(6)
                        .uppercase(Locale.US)
                )
            },
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_accent)) },
            isError = !valid,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                if (enabled) {
                    IconButton(onClick = onReset) {
                        Image(
                            painter = painterResource(R.drawable.ic_widget_refresh),
                            contentDescription = stringResource(R.string.settings_accent_reset),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun Color.toHex(): String =
    String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

internal fun parseHex(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toIntOrNull(16) ?: return null
    return Color(rgb or 0xFF000000.toInt())
}

private fun nearHue(a: Double, b: Double): Boolean {
    val delta = (a - b).mod(360.0)
    return delta < 8.0 || delta > 352.0
}

@Composable
private fun SliderRow(
    label: String,
    fraction: Float,
    track: List<Color>,
    thumb: Color,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientSlider(fraction, track, thumb, enabled, onFraction)
    }
}

@Composable
private fun Swatch(
    brush: Brush,
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(brush)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun GradientSlider(
    fraction: Float,
    track: List<Color>,
    thumb: Color,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    val thumbPx = with(LocalDensity.current) { ThumbSize.roundToPx() }

    val travel = (width - thumbPx).coerceAtLeast(1)
    fun report(x: Float) = onFraction(((x - thumbPx / 2f) / travel).coerceIn(0f, 1f))

    Box(
        Modifier
            .fillMaxWidth()
            .height(ThumbSize)
            .onSizeChanged { width = it.width }

            .pointerInput(travel, enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        report(down.position.x)
                        down.consume()
                        drag(down.id) { change ->
                            report(change.position.x)
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(track)),
        )
        Box(
            Modifier
                .offset { IntOffset((travel * fraction.coerceIn(0f, 1f)).toInt(), 0) }
                .size(ThumbSize)
                .clip(CircleShape)
                .background(thumb)

                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}
