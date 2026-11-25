package com.apkupdateross.ui.screen.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apkupdateross.ui.component.LargeTitle
import com.apkupdateross.ui.component.SettingsIcon
import com.apkupdateross.ui.theme.ApkTheme

@Composable
internal fun SettingsGroup(
	@StringRes title: Int,
	content: @Composable () -> Unit
) {
	LargeTitle(stringResource(title), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
	Surface(
		shape = ApkTheme.shapes.md,
		color = ApkTheme.colors.surface,
		border = BorderStroke(1.dp, ApkTheme.colors.divider),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
	) {
		Column { content() }
	}
}

@Composable
internal fun MetricInlineRow(text: String, value: String, @DrawableRes icon: Int) = Row(
	modifier = Modifier
		.fillMaxWidth()
		.padding(horizontal = 16.dp, vertical = 12.dp),
	verticalAlignment = Alignment.CenterVertically
) {
	SettingsIcon(icon, text, Modifier.padding(end = 16.dp))
	Column(Modifier.weight(1f)) {
		Text(text, style = MaterialTheme.typography.bodyLarge, color = ApkTheme.colors.textPrimary)
		Text(value, style = MaterialTheme.typography.bodyMedium, color = ApkTheme.colors.textSecondary)
	}
}
