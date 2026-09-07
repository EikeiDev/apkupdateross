package com.apkupdateross.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkupdateross.data.ui.SwipeIgnoreDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToIgnoreBox(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swipeDirection: SwipeIgnoreDirection = SwipeIgnoreDirection.Default,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    var showIgnoreConfirmation by rememberSaveable { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current
    val enableStartToEnd = swipeDirection.allowsStartToEnd(layoutDirection)
    val enableEndToStart = swipeDirection.allowsEndToStart(layoutDirection)
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.62f },
        confirmValueChange = { value ->
            val shouldConfirm = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> enableStartToEnd
                SwipeToDismissBoxValue.EndToStart -> enableEndToStart
                SwipeToDismissBoxValue.Settled -> false
            }
            if (shouldConfirm) {
                showIgnoreConfirmation = true
            }
            false
        }
    )

    LaunchedEffect(showIgnoreConfirmation) {
        if (!showIgnoreConfirmation) {
            dismissState.reset()
        }
    }

    if (showIgnoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showIgnoreConfirmation = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showIgnoreConfirmation = false
                        onIgnore()
                    }
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIgnoreConfirmation = false }) {
                    Text(cancelLabel)
                }
            },
            title = { Text(title) },
            text = { Text(message) }
        )
    }

    val direction = dismissState.dismissDirection
    val isSwipeActive = direction != SwipeToDismissBoxValue.Settled ||
        dismissState.targetValue != SwipeToDismissBoxValue.Settled
    val backgroundColor by animateColorAsState(
        targetValue = if (isSwipeActive) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
        label = "swipeIgnoreBackground"
    )
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    val fromStart = direction == SwipeToDismissBoxValue.StartToEnd

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = enableStartToEnd,
        enableDismissFromEndToStart = enableEndToStart,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fromStart) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = confirmLabel,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!fromStart) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                }
            }
        },
        content = { content() }
    )
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
