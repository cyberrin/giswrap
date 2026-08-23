package com.cyberrin.giswrap.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RefreshArrow(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
    ) {
        AnimatedContent(
            targetState = isRefreshing,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "refreshMark",
        ) { refreshing ->
            if (refreshing) {
                Loading(contained = false, size = MarkSize)
            } else {
                PullingArrow(state = state, motion = motion)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullingArrow(
    state: PullToRefreshState,
    motion: androidx.compose.material3.MotionScheme,
) {
    val pull = state.distanceFraction.coerceIn(0f, 1f)

    val scale by animateFloatAsState(
        targetValue = if (pull >= 1f) 1f else pull,
        animationSpec = motion.defaultSpatialSpec(),
        label = "arrowScale",
    )
    val alpha by animateFloatAsState(
        targetValue = pull,
        animationSpec = motion.fastEffectsSpec(),
        label = "arrowAlpha",
    )

    val tint = PullToRefreshDefaults.loadingIndicatorColor
    Canvas(
        Modifier
            .size(MarkSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        val w = size.width
        val h = size.height
        val stroke = w * StrokeFraction
        val head = w * HeadFraction

        val x = w / 2f
        val reach = h * ReachFraction
        val tipY = h / 2f + reach
        val tailY = h / 2f - reach

        drawLine(
            color = tint,
            start = Offset(x, tailY),
            end = Offset(x, tipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        drawPath(
            Path().apply {
                moveTo(x - head, tipY - head)
                lineTo(x, tipY)
                lineTo(x + head, tipY - head)
            },
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private val MarkSize = 24.dp

private const val StrokeFraction = 0.10f
private const val HeadFraction = 0.22f
private const val ReachFraction = 0.30f
