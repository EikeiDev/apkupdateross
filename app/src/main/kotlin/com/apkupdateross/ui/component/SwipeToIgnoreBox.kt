package com.apkupdateross.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkupdateross.R
import com.apkupdateross.data.ui.SwipeIgnoreDirection
import com.apkupdateross.ui.theme.ApkTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun SwipeToIgnoreBox(
    confirmLabel: String,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swipeDirection: SwipeIgnoreDirection = SwipeIgnoreDirection.Default,
    dismissOnIgnore: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val enableStartToEnd = swipeDirection.allowsStartToEnd(layoutDirection)
    val enableEndToStart = swipeDirection.allowsEndToStart(layoutDirection)

    val offset = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var crossedThreshold by remember { mutableStateOf(false) }
    var isCommitting by remember { mutableStateOf(false) }

    val minThresholdPx = with(density) { 108.dp.toPx() }
    val maxThresholdPx = with(density) { 156.dp.toPx() }
    val thresholdPx = if (widthPx > 0f) {
        (widthPx * 0.36f).coerceIn(minThresholdPx, maxThresholdPx)
    } else {
        minThresholdPx
    }
    val maxDragPx = if (widthPx > 0f) {
        (widthPx * 0.52f).coerceAtLeast(thresholdPx * 1.25f)
    } else {
        maxThresholdPx * 1.25f
    }

    val minOffset = if (enableEndToStart) -maxDragPx else 0f
    val maxOffset = if (enableStartToEnd) maxDragPx else 0f

    val currentOffset = offset.value
    val progress = (abs(currentOffset) / thresholdPx).coerceIn(0f, 1f)
    val isReady = abs(currentOffset) >= thresholdPx
    val fromStart = currentOffset >= 0f

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isReady -> ApkTheme.colors.error.copy(alpha = 0.22f)
            abs(currentOffset) > 4f -> ApkTheme.colors.error.copy(alpha = 0.08f + 0.08f * progress)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "swipeIgnoreBg"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isReady) 1.16f else (0.78f + 0.22f * progress),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "swipeIgnoreIconScale"
    )
    val contentTint by animateColorAsState(
        targetValue = if (isReady) ApkTheme.colors.error else ApkTheme.colors.textSecondary,
        animationSpec = tween(durationMillis = 120),
        label = "swipeIgnoreTint"
    )

    val draggableState = rememberDraggableState { delta ->
        if (isCommitting) return@rememberDraggableState
        val current = offset.value
        val resistance = if (abs(current) >= thresholdPx && delta.sign == current.sign) {
            0.28f
        } else {
            0.78f
        }
        val next = (current + delta * resistance).coerceIn(minOffset, maxOffset)
        val nowCrossed = abs(next) >= thresholdPx
        if (nowCrossed != crossedThreshold) {
            crossedThreshold = nowCrossed
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        scope.launch {
            offset.snapTo(next)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clip(shape)
    ) {
        if (abs(currentOffset) > 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp)
                    .alpha((progress * 1.4f).coerceIn(0f, 1f)),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fromStart) {
                        Icon(
                            painter = painterResource(R.drawable.ic_visible_off),
                            contentDescription = null,
                            tint = contentTint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = confirmLabel,
                        color = contentTint,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (fromStart) TextAlign.Start else TextAlign.End,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )

                    if (!fromStart) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_visible_off),
                            contentDescription = null,
                            tint = contentTint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .alpha(cardAlpha.value)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !isCommitting,
                    onDragStopped = {
                        val shouldIgnore = abs(offset.value) >= thresholdPx
                        crossedThreshold = false
                        if (shouldIgnore) {
                            isCommitting = true
                            val directionSign = if (offset.value >= 0f) 1f else -1f
                            scope.launch {
                                try {
                                    if (dismissOnIgnore && widthPx > 0f) {
                                        launch {
                                            cardAlpha.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(170, easing = FastOutSlowInEasing)
                                            )
                                        }
                                        offset.animateTo(
                                            targetValue = directionSign * widthPx * 0.72f,
                                            animationSpec = tween(170, easing = FastOutSlowInEasing)
                                        )
                                        onIgnore()
                                    } else {
                                        onIgnore()
                                        offset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                } finally {
                                    offset.snapTo(0f)
                                    cardAlpha.snapTo(1f)
                                    isCommitting = false
                                }
                            }
                        } else {
                            scope.launch {
                                offset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}

private fun SwipeIgnoreDirection.allowsStartToEnd(layoutDirection: LayoutDirection): Boolean =
    when (this) {
        SwipeIgnoreDirection.Both -> true
        SwipeIgnoreDirection.Right -> layoutDirection == LayoutDirection.Ltr
        SwipeIgnoreDirection.Left -> layoutDirection == LayoutDirection.Rtl
    }

private fun SwipeIgnoreDirection.allowsEndToStart(layoutDirection: LayoutDirection): Boolean =
    when (this) {
        SwipeIgnoreDirection.Both -> true
        SwipeIgnoreDirection.Right -> layoutDirection == LayoutDirection.Rtl
        SwipeIgnoreDirection.Left -> layoutDirection == LayoutDirection.Ltr
    }
