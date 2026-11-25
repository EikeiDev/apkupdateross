package com.apkupdateross.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkupdateross.BuildConfig
import com.apkupdateross.R
import com.apkupdateross.ui.component.LoadingImageApp
import com.apkupdateross.ui.component.tvFocusOutline
import com.apkupdateross.ui.theme.ApkTheme

@Composable
internal fun About() = Column(
	modifier = Modifier
		.fillMaxSize()
		.verticalScroll(rememberScrollState())
) {
	Spacer(Modifier.height(8.dp))
	AboutIdentity()

	SettingsGroup(R.string.about_project) {
		AboutLinkRow(
			icon = R.drawable.ic_github,
			title = stringResource(R.string.github),
			subtitle = stringResource(R.string.about_github_description),
			url = stringResource(R.string.github_url)
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		AboutLinkRow(
			imageVector = Icons.Default.Favorite,
			title = stringResource(R.string.donate),
			subtitle = stringResource(R.string.about_donate_description),
			url = stringResource(R.string.donate_url)
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		AboutLinkRow(
			icon = R.drawable.ic_safe,
			title = stringResource(R.string.about_license),
			subtitle = stringResource(R.string.about_license_name),
			url = stringResource(R.string.license_url)
		)
	}

	SettingsGroup(R.string.about_credits) {
		MetricInlineRow(
			text = stringResource(R.string.about_maintained_by),
			value = stringResource(R.string.about_maintainer_name),
			icon = R.drawable.ic_info
		)
		Divider(Modifier.padding(horizontal = 16.dp), color = ApkTheme.colors.divider)
		MetricInlineRow(
			text = stringResource(R.string.about_based_on),
			value = stringResource(R.string.about_original_project),
			icon = R.drawable.ic_github
		)
	}

	Spacer(Modifier.height(24.dp))
}

@Composable
private fun AboutIdentity() {
	val largeText = ApkTheme.accessibility.isLargeText
	if (largeText) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			AboutLogo(88.dp)
			Spacer(Modifier.height(16.dp))
			AboutIdentityText(centered = true)
		}
	} else {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			AboutLogo(88.dp)
			Spacer(Modifier.width(20.dp))
			AboutIdentityText(modifier = Modifier.weight(1f))
		}
	}
}

@Composable
private fun AboutLogo(size: Dp) = Surface(
	modifier = Modifier.size(size),
	shape = ApkTheme.shapes.lg,
	color = ApkTheme.colors.surfaceSecondary,
	border = BorderStroke(1.dp, ApkTheme.colors.divider)
) {
	Box(contentAlignment = Alignment.Center) {
		LoadingImageApp(BuildConfig.APPLICATION_ID, Modifier.size(size * 0.72f))
	}
}

@Composable
private fun AboutIdentityText(
	modifier: Modifier = Modifier,
	centered: Boolean = false
) = Column(
	modifier = modifier,
	horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
) {
	Text(
		text = stringResource(R.string.app_name),
		style = MaterialTheme.typography.headlineMedium,
		fontWeight = FontWeight.Bold,
		color = ApkTheme.colors.textOnBackground
	)
	Spacer(Modifier.height(6.dp))
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Text(
			text = stringResource(R.string.about_version),
			style = MaterialTheme.typography.bodyMedium,
			color = ApkTheme.colors.textSecondary
		)
		Text(
			text = BuildConfig.VERSION_NAME,
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.SemiBold,
			color = ApkTheme.colors.accent
		)
	}
}

@Composable
private fun AboutLinkRow(
	@DrawableRes icon: Int? = null,
	imageVector: ImageVector? = null,
	title: String,
	subtitle: String,
	url: String
) {
	val context = LocalContext.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 76.dp)
			.tvFocusOutline(ApkTheme.shapes.sm)
			.clickable {
				runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
			}
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Surface(
			modifier = Modifier.size(42.dp),
			shape = ApkTheme.shapes.sm,
			color = ApkTheme.colors.surfaceHighlight
		) {
			Box(contentAlignment = Alignment.Center) {
				when {
					icon != null -> Icon(
						painter = painterResource(icon),
						contentDescription = null,
						modifier = Modifier.size(24.dp),
						tint = ApkTheme.colors.accent
					)
					imageVector != null -> Icon(
						imageVector = imageVector,
						contentDescription = null,
						modifier = Modifier.size(24.dp),
						tint = ApkTheme.colors.accent
					)
				}
			}
		}
		Spacer(Modifier.width(16.dp))
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.bodyLarge, color = ApkTheme.colors.textPrimary)
			Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
		}
		Icon(
			painter = painterResource(R.drawable.ic_open_in_new),
			contentDescription = null,
			modifier = Modifier.padding(start = 12.dp).size(20.dp),
			tint = ApkTheme.colors.textTertiary
		)
	}
}
