package com.apkupdateross.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkupdateross.ui.theme.ApkTheme
import java.util.Locale

sealed interface ReleaseNoteLine {
	data class Heading(val text: String, val level: Int) : ReleaseNoteLine
	data class Bullet(val text: String, val indent: Int) : ReleaseNoteLine
	data class Paragraph(val text: String) : ReleaseNoteLine
}

@Composable
fun ReleaseNotesContent(
	markdown: String,
	modifier: Modifier = Modifier,
	stripSelfUpdateHeader: Boolean = false,
	maxCollapsedLines: Int? = null
) {
	val lines = remember(markdown, stripSelfUpdateHeader) {
		markdown.toReleaseNoteLines(stripSelfUpdateHeader = stripSelfUpdateHeader)
	}
	var expanded by remember(markdown) { mutableStateOf(false) }
	val isCollapsible = maxCollapsedLines != null && lines.size > maxCollapsedLines
	val visibleLines = if (isCollapsible && !expanded) {
		lines.take(maxCollapsedLines)
	} else {
		lines
	}

	val containerModifier = if (isCollapsible) {
		modifier
			.fillMaxWidth()
			.tvFocusOutline(ApkTheme.shapes.xs)
			.clickable { expanded = !expanded }
			.animateContentSize()
	} else {
		modifier.fillMaxWidth()
	}

	Column(modifier = containerModifier) {
		visibleLines.forEachIndexed { index, line ->
			val isLastCollapsed = isCollapsible && !expanded && index == visibleLines.lastIndex
			val lineOverflow = if (isLastCollapsed) TextOverflow.Ellipsis else TextOverflow.Clip
			val lineMaxLines = if (isLastCollapsed) 2 else Int.MAX_VALUE
			when (line) {
				is ReleaseNoteLine.Heading -> Text(
					text = line.text,
					style = if (line.level <= 2) {
						MaterialTheme.typography.titleSmall
					} else {
						MaterialTheme.typography.labelLarge
					},
					fontWeight = FontWeight.SemiBold,
					color = ApkTheme.colors.textPrimary,
					maxLines = lineMaxLines,
					overflow = lineOverflow,
					modifier = Modifier.padding(top = if (index == 0) 2.dp else 8.dp, bottom = 2.dp)
				)
				is ReleaseNoteLine.Bullet -> Text(
					text = "\u2022 ${line.text}",
					style = MaterialTheme.typography.bodySmall,
					color = ApkTheme.colors.textSecondary,
					maxLines = lineMaxLines,
					overflow = lineOverflow,
					modifier = Modifier.padding(start = (8 + line.indent * 12).dp, top = 2.dp)
				)
				is ReleaseNoteLine.Paragraph -> Text(
					text = line.text,
					style = MaterialTheme.typography.bodySmall,
					color = ApkTheme.colors.textSecondary,
					maxLines = lineMaxLines,
					overflow = lineOverflow,
					modifier = Modifier.padding(top = 4.dp)
				)
			}
		}
		if (isCollapsible && !expanded) {
			Text(
				text = "…",
				style = MaterialTheme.typography.bodySmall,
				color = ApkTheme.colors.accent,
				modifier = Modifier.padding(top = 2.dp)
			)
		}
	}
}

internal fun String.toReleaseNoteLines(stripSelfUpdateHeader: Boolean = false): List<ReleaseNoteLine> {
	val result = mutableListOf<ReleaseNoteLine>()
	var inCodeFence = false

	lineSequence().forEach { rawLine ->
		val trimmed = rawLine.trim()
		if (trimmed.startsWith("```")) {
			inCodeFence = !inCodeFence
			return@forEach
		}
		if (trimmed.isBlank()) return@forEach

		val heading = markdownHeadingRegex.matchEntire(trimmed)
		if (!inCodeFence && heading != null) {
			val text = heading.groupValues[2].cleanInlineMarkdown()
			if (stripSelfUpdateHeader && result.isEmpty() && text.isSelfUpdateReleaseHeading()) return@forEach
			result += ReleaseNoteLine.Heading(text, heading.groupValues[1].length)
			return@forEach
		}

		val bullet = markdownBulletRegex.matchEntire(trimmed)
		if (!inCodeFence && bullet != null) {
			val indent = (rawLine.takeWhile { it == ' ' || it == '\t' }.length / 2).coerceIn(0, 2)
			result += ReleaseNoteLine.Bullet(bullet.groupValues[1].cleanInlineMarkdown(), indent)
			return@forEach
		}

		val numbered = markdownNumberedRegex.matchEntire(trimmed)
		if (!inCodeFence && numbered != null) {
			val indent = (rawLine.takeWhile { it == ' ' || it == '\t' }.length / 2).coerceIn(0, 2)
			result += ReleaseNoteLine.Bullet(numbered.groupValues[1].cleanInlineMarkdown(), indent)
			return@forEach
		}

		result += ReleaseNoteLine.Paragraph(trimmed.cleanInlineMarkdown())
	}

	return result.ifEmpty { listOf(ReleaseNoteLine.Paragraph(trim().cleanInlineMarkdown())) }
}

private fun String.cleanInlineMarkdown(): String =
	replace(markdownImageRegex, "$1")
		.replace(markdownLinkRegex, "$1")
		.replace(markdownCodeRegex, "$1")
		.replace("**", "")
		.replace("__", "")
		.replace(Regex("""(^|[^\\])~~"""), "$1")
		.trim()

private fun String.isSelfUpdateReleaseHeading(): Boolean {
	val normalized = lowercase(Locale.ROOT)
	return normalized.contains("apkupdater oss") || normalized.contains("apkupdateross")
}

private val markdownHeadingRegex = Regex("""^(#{1,6})\s+(.+)$""")
private val markdownBulletRegex = Regex("""^[-*+]\s+(.+)$""")
private val markdownNumberedRegex = Regex("""^\d+[.)]\s+(.+)$""")
private val markdownImageRegex = Regex("""!\[([^\]]*)]\([^)]+\)""")
private val markdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
private val markdownCodeRegex = Regex("""`([^`]*)`""")
