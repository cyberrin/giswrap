@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.cyberrin.giswrap.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.ui.art.*
import com.cyberrin.giswrap.ui.theme.*
import java.time.LocalDate
import kotlin.math.roundToInt

internal val BarEdgePadding = 16.dp
internal val BarButtonSize = 44.dp
internal val BarGap = 12.dp

internal val SearchFieldInset = BarEdgePadding + BarButtonSize + BarGap
internal val BarCorner = 22.dp

internal val TabBarClearance = 96.dp

internal val HeroIconSize = 168.dp
internal val DayIconSize = 60.dp
internal val HourIconSize = 44.dp

internal val HeroTextInset = 40.dp

internal val HeroTextMargin = 48.dp

@Composable
internal fun DismissScrim(onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap,
            )
    )
}

@Composable
internal fun RoundIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(BarButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun SoftCard(
    onClick: (() -> Unit)? = null,
    identity: Any? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (LocalCuteTheme.current) {
        CutePanel(onClick = onClick, identity = identity, content = content)
        return
    }
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = MaterialTheme.shapes.largeIncreased
    val box = Modifier.fillMaxWidth()
    if (onClick == null) {
        Card(shape = shape, colors = colors, modifier = box) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), content = content)
        }
    } else {
        Card(onClick = onClick, shape = shape, colors = colors, modifier = box) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), content = content)
        }
    }
}

@Composable
internal fun CutePanel(
    onClick: (() -> Unit)? = null,
    identity: Any? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tuning = LocalCuteTuning.current
    val colour = cuteColourFor(identity, tuning.weights)
    val fill = MaterialTheme.colorScheme.cute(colour)
    val ink = MaterialTheme.colorScheme.onCute(colour)

    val outline = MaterialTheme.colorScheme.outlineVariant
    val seed = remember(identity) { cuteSeed(identity) }
    Column(
        Modifier
            .fillMaxWidth()

            .drawWithCache {
                val path = wobblyPath(size, CuteCard, seed, tuning)
                onDrawBehind { drawWobblyPanel(path, fill, outline, CuteStroke.toPx()) }
            }
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink, content = { content() })
    }
}

private const val LabelAlpha = 0.72f

internal val CuteStroke = 2.6.dp

internal val CuteCard = CuteEdge(depth = 4.5.dp, lobe = 40.dp, drift = 1.6.dp, squareness = 6.5f)
internal val CuteBar = CuteEdge(depth = 2.2.dp, lobe = 26.dp, drift = 0.8.dp)
internal val CuteChip = CuteEdge(depth = 1.6.dp, lobe = 20.dp, drift = 0.6.dp, squareness = 4.2f)

@Composable
internal fun Loading(contained: Boolean = true, size: Dp? = null) {
    val indicator = @Composable {
        val modifier = if (size == null) Modifier else Modifier.size(size)
        if (contained) {
            ContainedLoadingIndicator(modifier = modifier, polygons = LoadingShapes)
        } else {
            LoadingIndicator(modifier = modifier, polygons = LoadingShapes)
        }
    }
    if (size != null) {
        indicator()
    } else {
        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.TopCenter) {
            indicator()
        }
    }
}

private val LoadingShapes: List<RoundedPolygon> = listOf(
    0.95f to 0.5f,
    0.75f to 0.35f,
    0.55f to 0.25f,
    0.75f to 0.35f,
).map { (innerRadius, rounding) ->
    RoundedPolygon.star(
        numVerticesPerRadius = 8,
        innerRadius = innerRadius,
        rounding = CornerRounding(rounding),
        innerRounding = CornerRounding(rounding),
    )
}

@Composable
internal fun Notice(text: String, error: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
internal fun TabBar(
    slots: Int,
    label: @Composable (Int) -> String,
    highlighted: () -> Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillColor = MaterialTheme.colorScheme.secondaryContainer

    val pillOutline = MaterialTheme.colorScheme.primary
    val cute = LocalCuteTheme.current
    val barTuning = LocalCuteTuning.current

    val barShape: Shape = if (cute) RectangleShape else CircleShape
    val barFill = if (cute) {
        MaterialTheme.colorScheme.cute(cuteColourFor("range-bar", barTuning.weights))
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val barOutline = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = barShape,
        color = if (cute) Color.Transparent else barFill,
        shadowElevation = if (cute) 0.dp else 2.dp,
        modifier = modifier.then(
            if (!cute) Modifier else Modifier.drawWithCache {
                val path = wobblyPath(size, CuteBar, cuteSeed("range-bar"), barTuning)
                onDrawBehind { drawWobblyPanel(path, barFill, barOutline, CuteStroke.toPx()) }
            }
        ),
    ) {
        Row(
            Modifier
                .padding(if (cute) 8.dp else 6.dp)

                .drawWithCache {
                    val slotWidth = size.width / slots

                    val chip = if (!cute) null else {
                        wobblyPath(
                            Size(slotWidth, size.height), CuteChip,
                            cuteSeed("range-chip"), barTuning,
                        )
                    }
                    onDrawBehind {
                    val slot = position().coerceIn(0f, (slots - 1).toFloat())
                    if (chip == null) {
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(slot * slotWidth, 0f),
                            size = Size(slotWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    } else {
                        translate(left = slot * slotWidth) {
                            drawPath(chip, pillColor)
                            drawPath(
                                chip,
                                pillOutline,
                                style = Stroke(width = CuteStroke.toPx()),
                            )
                        }
                    }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(slots) { slot ->
                TabChip(
                    label = label(slot),
                    active = { highlighted() == slot },
                    onClick = { onSelect(slot) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun TabChip(
    label: String,
    active: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content by animateColorAsState(
        targetValue = if (active()) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 280, easing = LinearEasing),
        label = "chipContent",
    )

    Box(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            color = content,
        )
    }
}

@Composable
internal fun SlotBar(
    labels: List<String>,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlighted = remember(labels.size) {
        derivedStateOf { position().roundToInt().coerceIn(0, labels.lastIndex) }
    }
    TabBar(
        slots = labels.size,
        label = { labels[it] },
        highlighted = { highlighted.value },
        position = position,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = LabelAlpha),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = LocalContentColor.current,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FieldGrid(fields: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, value) ->
                    Field(label, value, Modifier.weight(1f))
                }

                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun DateHeading(date: LocalDate) {
    val locale = appLocale()
    Text(
        text = remember(date, locale) { dateHeading(date, locale) },
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    expanded: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (expanded) {
        RoundedCornerShape(
            topStart = BarCorner,
            topEnd = BarCorner,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
    } else {
        CircleShape
    }
    Row(
        modifier
            .height(BarButtonSize)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(17.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )
    }
}

@Composable
internal fun SuggestionRow(city: City, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(city.name, style = MaterialTheme.typography.bodyLarge)
        val where = city.where
        if (where.isNotBlank()) {
            Text(
                where,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SuggestionNote(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

internal const val SUGGESTION_LIMIT = 5
