package com.apkupdateross.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkupdateross.ui.theme.ApkTheme

enum class AppTone {
	Neutral,
	Accent,
	Success,
	Warning,
	Error
}

@Composable
fun AppScreen(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.background(ApkTheme.colors.background)
	) {
		content()
	}
}

@Composable
fun AppTopBar(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	navigationIcon: (@Composable () -> Unit)? = null,
	actions: @Composable RowScope.() -> Unit = {}
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.statusBarsPadding()
			.heightIn(min = 64.dp)
			.padding(horizontal = ApkTheme.spacing.md, vertical = ApkTheme.spacing.sm),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (navigationIcon != null) {
			navigationIcon()
			Spacer(modifier = Modifier.width(ApkTheme.spacing.xs))
		}
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.Center
		) {
			Text(
				text = title,
				color = ApkTheme.colors.textOnBackground,
				style = MaterialTheme.typography.titleLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					color = ApkTheme.colors.textOnBackground.copy(alpha = 0.68f),
					style = MaterialTheme.typography.bodySmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
		CompositionLocalProvider(LocalContentColor provides ApkTheme.colors.textOnBackground.copy(alpha = 0.78f)) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(ApkTheme.spacing.xxs),
				verticalAlignment = Alignment.CenterVertically,
				content = actions
			)
		}
	}
}

@Composable
fun AppIconButton(
	@DrawableRes icon: Int,
	onClick: () -> Unit,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	tone: AppTone = AppTone.Neutral
) {
	val colors = ApkTheme.colors
	val container = when (tone) {
		AppTone.Accent -> colors.surfaceHighlight
		AppTone.Success -> colors.success.copy(alpha = 0.16f)
		AppTone.Warning -> colors.warning.copy(alpha = 0.18f)
		AppTone.Error -> colors.error.copy(alpha = 0.16f)
		AppTone.Neutral -> Color.Transparent
	}
	val content = when (tone) {
		AppTone.Accent -> colors.accent
		AppTone.Success -> colors.success
		AppTone.Warning -> colors.warning
		AppTone.Error -> colors.error
		AppTone.Neutral -> colors.textSecondary
	}
	Box(
		modifier = modifier
			.size(48.dp)
			.clip(ApkTheme.shapes.md)
			.background(if (enabled) container else Color.Transparent)
			.clickable(
				enabled = enabled,
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = onClick
			),
		contentAlignment = Alignment.Center
	) {
		Icon(
			painter = painterResource(icon),
			contentDescription = contentDescription,
			modifier = Modifier.size(22.dp),
			tint = if (enabled) content else colors.disabled
		)
	}
}

@Composable
fun AppActionButton(
	@DrawableRes icon: Int,
	onClick: () -> Unit,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	tone: AppTone = AppTone.Neutral,
	size: Dp = 48.dp
) {
	val colors = ApkTheme.colors
	val container = when (tone) {
		AppTone.Accent -> colors.accent
		AppTone.Success -> colors.success.copy(alpha = 0.22f)
		AppTone.Warning -> colors.warning.copy(alpha = 0.22f)
		AppTone.Error -> colors.error.copy(alpha = 0.18f)
		AppTone.Neutral -> colors.surfaceSecondary
	}
	val content = when (tone) {
		AppTone.Accent -> colors.onAccent
		AppTone.Success -> colors.success
		AppTone.Warning -> colors.warning
		AppTone.Error -> colors.error
		AppTone.Neutral -> colors.textPrimary
	}
	Surface(
		modifier = modifier.size(size),
		shape = ApkTheme.shapes.md,
		color = if (enabled) container else colors.surfaceSecondary,
		border = if (tone == AppTone.Neutral) BorderStroke(1.dp, colors.divider) else null
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.clickable(enabled = enabled, onClick = onClick),
			contentAlignment = Alignment.Center
		) {
			Icon(
				painter = painterResource(icon),
				contentDescription = contentDescription,
				modifier = Modifier.size(20.dp),
				tint = if (enabled) content else colors.disabled
			)
		}
	}
}

@Composable
fun AppStatusBadge(
	text: String,
	modifier: Modifier = Modifier,
	tone: AppTone = AppTone.Neutral
) {
	val colors = ApkTheme.colors
	val container = when (tone) {
		AppTone.Accent -> colors.accent.copy(alpha = 0.18f)
		AppTone.Success -> colors.success.copy(alpha = 0.2f)
		AppTone.Warning -> colors.warning.copy(alpha = 0.22f)
		AppTone.Error -> colors.error.copy(alpha = 0.2f)
		AppTone.Neutral -> colors.surfaceSecondary
	}
	val content = when (tone) {
		AppTone.Accent -> colors.accent
		AppTone.Success -> colors.success
		AppTone.Warning -> colors.warning
		AppTone.Error -> colors.error
		AppTone.Neutral -> colors.textSecondary
	}
	Surface(
		modifier = modifier,
		shape = ApkTheme.shapes.xs,
		color = container,
		border = BorderStroke(1.dp, content.copy(alpha = 0.28f))
	) {
		Text(
			text = text,
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
			color = content,
			style = MaterialTheme.typography.labelSmall,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1
		)
	}
}

@Composable
fun AppListSurface(
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null,
	content: @Composable () -> Unit
) {
	val clickableModifier = if (onClick != null) {
		Modifier
			.clip(ApkTheme.shapes.md)
			.clickable(onClick = onClick)
	} else {
		Modifier
	}
	Surface(
		modifier = modifier.then(clickableModifier),
		shape = ApkTheme.shapes.md,
		color = ApkTheme.colors.surface,
		border = BorderStroke(1.dp, ApkTheme.colors.divider)
	) {
		content()
	}
}

@Composable
fun AppStateMessage(
	title: String,
	modifier: Modifier = Modifier,
	message: String? = null,
	@DrawableRes icon: Int? = null,
	tone: AppTone = AppTone.Neutral,
	loading: Boolean = false
) {
	Box(
		modifier = modifier
			.fillMaxSize()
			.padding(ApkTheme.spacing.xxl),
		contentAlignment = Alignment.Center
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(ApkTheme.spacing.sm)
		) {
			Surface(
				shape = ApkTheme.shapes.lg,
				color = toneContainer(tone)
			) {
				Box(
					modifier = Modifier.size(64.dp),
					contentAlignment = Alignment.Center
				) {
					if (loading) {
						CircularProgressIndicator(
							modifier = Modifier.size(26.dp),
							color = ApkTheme.colors.accent,
							strokeWidth = 2.5.dp
						)
					} else if (icon != null) {
						Icon(
							painter = painterResource(icon),
							contentDescription = null,
							modifier = Modifier.size(28.dp),
							tint = toneContent(tone)
						)
					}
				}
			}
			Text(
				text = title,
				color = ApkTheme.colors.textOnBackground,
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center
			)
			if (message != null) {
				Text(
					text = message,
					color = ApkTheme.colors.textOnBackground.copy(alpha = 0.68f),
					style = MaterialTheme.typography.bodyMedium,
					textAlign = TextAlign.Center
				)
			}
		}
	}
}

@Composable
private fun toneContainer(tone: AppTone): Color {
	val colors = ApkTheme.colors
	return when (tone) {
		AppTone.Accent -> colors.accent.copy(alpha = 0.18f)
		AppTone.Success -> colors.success.copy(alpha = 0.2f)
		AppTone.Warning -> colors.warning.copy(alpha = 0.22f)
		AppTone.Error -> colors.error.copy(alpha = 0.18f)
		AppTone.Neutral -> colors.surfaceSecondary
	}
}

@Composable
private fun toneContent(tone: AppTone): Color {
	val colors = ApkTheme.colors
	return when (tone) {
		AppTone.Accent -> colors.accent
		AppTone.Success -> colors.success
		AppTone.Warning -> colors.warning
		AppTone.Error -> colors.error
		AppTone.Neutral -> colors.textSecondary
	}
}
