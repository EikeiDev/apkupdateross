package com.apkupdateross.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.apkupdateross.prefs.Prefs
import androidx.compose.ui.graphics.Color
import com.apkupdateross.ui.theme.ApkTheme
import org.koin.compose.koinInject


@Composable
fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = ApkTheme.colors.textTertiary) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = color,
    maxLines = 1,
    modifier = modifier,
    overflow = TextOverflow.Ellipsis
)

@Composable
fun MediumText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    color: Color = ApkTheme.colors.textSecondary
) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    maxLines = maxLines,
    modifier = modifier,
    overflow = TextOverflow.Ellipsis
)

@Composable
fun MediumTitle(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = ApkTheme.colors.textPrimary,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
)

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = ApkTheme.colors.textPrimary,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
)

@Composable
fun ScrollableText(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val state = rememberScrollState()
    val inner = remember { mutableStateOf(IntSize.Zero) }
    val outer = remember { mutableStateOf(IntSize.Zero) }

    if (koinInject<Prefs>().playTextAnimations.get()) {
        val effect: suspend (ScrollState) -> Unit = {
            state.scrollTo(0)
            val scroll = (inner.value.width - outer.value.width)
            if (scroll > 0) {
                while(true) {
                    state.animateScrollTo(
                        scroll,
                        tween(delayMillis = 1000, durationMillis = scroll * 10, easing = LinearEasing)
                    )
                    state.animateScrollTo(
                        0,
                        tween(delayMillis = 1000, durationMillis = scroll * 10, easing = LinearEasing)
                    )
                }

            }
        }
        LaunchedEffect(outer.value) { effect(state) }
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { outer.value = it }
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(state)
                .onSizeChanged { inner.value = it },
            content = content
        )
    }
}

@Composable
fun BadgeText(number: String) {
    if (number.isNotEmpty()) {
        Badge(
            containerColor = ApkTheme.colors.error,
            contentColor = Color.White
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ExpandingAnnotatedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val displayText = if (text.length > 0 && text.text.last() == '\n') {
        text.subSequence(0, text.length - 1)
    } else {
        text
    }
    Text(
        text = displayText,
        maxLines = if (isExpanded) Int.MAX_VALUE else minLines,
        style = style,
        color = ApkTheme.colors.textSecondary,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable(true) { isExpanded = !isExpanded }
            .animateContentSize(),
    )
}
