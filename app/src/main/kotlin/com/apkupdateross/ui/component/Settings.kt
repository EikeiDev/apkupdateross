package com.apkupdateross.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkupdateross.R
import com.apkupdateross.ui.theme.ApkTheme

@Composable
fun SettingsIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tintIcon: Boolean = true,
    containerColor: Color? = null,
    iconSize: Dp = 24.dp
) = androidx.compose.material3.Surface(
    shape = ApkTheme.shapes.sm,
    color = containerColor ?: ApkTheme.colors.surfaceHighlight,
    modifier = modifier.size(42.dp)
) {
    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (tintIcon) ApkTheme.colors.accent else Color.Unspecified
        )
    }
}
@Composable
fun SliderSetting(
    getValue: () -> Float,
    setValue: (Float) -> Unit,
    text: String,
    valueRange: ClosedFloatingPointRange<Float>,
    @DrawableRes icon: Int
) = Row(
    Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
) {
    var position by remember { mutableFloatStateOf(getValue()) }
    val isTv = LocalTvMode.current
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Column(Modifier.weight(1f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text,
                Modifier.weight(1f),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = ApkTheme.colors.textPrimary
            )
            Text(
                "${getValue().toInt()}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ApkTheme.colors.textSecondary
            )
        }
        Slider(
            value = position,
            valueRange = valueRange,
            onValueChange = {
                position = it
                setValue(it)
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(32.dp)
                .onPreviewKeyEvent { event ->
                    if (!isTv || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -1f
                        Key.DirectionRight -> 1f
                        else -> return@onPreviewKeyEvent false
                    }
                    val next = (position + delta).coerceIn(valueRange)
                    if (next != position) {
                        position = next
                        setValue(next)
                    }
                    true
                }
                .tvFocusOutline(ApkTheme.shapes.sm),
            colors = SliderDefaults.colors(
                thumbColor = ApkTheme.colors.accent,
                activeTrackColor = ApkTheme.colors.accent,
                inactiveTrackColor = ApkTheme.colors.surfaceSecondary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedButtonSetting(
    text: String,
    options: List<String>,
    getValue: () -> Int,
    setValue: (Int) -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_system,
    enabledItems: List<Boolean> = options.map { true },
    enabled: Boolean = true
) = Box(Modifier.fillMaxWidth()) {
    var position by remember { mutableIntStateOf(getValue()) }
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.getOrElse(position) { options.firstOrNull().orEmpty() }
    val alpha = if (enabled) 1f else 0.5f
    val largeText = ApkTheme.accessibility.isLargeText

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .tvFocusOutline(ApkTheme.shapes.sm, enabled)
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = CenterVertically
    ) {
        SettingsIcon(icon, text, Modifier.padding(end = 16.dp).alpha(alpha))
        Column(Modifier.weight(1f).alpha(alpha)) {
            Text(
                text,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = ApkTheme.colors.textPrimary
            )
            if (!largeText) {
                Text(
                    selectedText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = ApkTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (largeText) {
                SettingDropdownControl(
                    selectedText = selectedText,
                    options = options,
                    enabledItems = enabledItems,
                    enabled = enabled,
                    expanded = expanded,
                    onDismiss = { expanded = false },
                    onSelect = { index ->
                        position = index
                        setValue(position)
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        if (!largeText) {
            SettingDropdownControl(
                selectedText = selectedText,
                options = options,
                enabledItems = enabledItems,
                enabled = enabled,
                expanded = expanded && enabled,
                onDismiss = { expanded = false },
                onSelect = { index ->
                    position = index
                    setValue(position)
                    expanded = false
                },
                modifier = Modifier.widthIn(min = 104.dp, max = 156.dp).alpha(alpha)
            )
        }
    }
}

@Composable
private fun SettingDropdownControl(
    selectedText: String,
    options: List<String>,
    enabledItems: List<Boolean>,
    enabled: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ApkTheme.shapes.sm,
            color = ApkTheme.colors.surfaceSecondary
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = CenterVertically
            ) {
                Text(
                    selectedText,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = ApkTheme.colors.textPrimary,
                    maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                    tint = ApkTheme.colors.textSecondary
                )
            }
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 156.dp)
        ) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            maxLines = if (ApkTheme.accessibility.isLargeText) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    enabled = enabled && enabledItems.getOrElse(index) { true },
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
fun SwitchSetting(
    getValue: () -> Boolean,
    setValue: (Boolean) -> Unit,
    text: String,
    @DrawableRes icon: Int = R.drawable.ic_system,
    onClick: (() -> Unit)? = null,
    isExpanded: Boolean = false,
    subtitle: String? = null,
    enabled: Boolean = true,
    tintIcon: Boolean = true,
    iconContainerColor: Color? = null,
    iconSize: Dp = 24.dp
) {
    var value by remember { mutableStateOf(getValue()) }
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) 88.dp else 72.dp)
            .tvFocusOutline(ApkTheme.shapes.sm, enabled)
            .toggleable(
                value = value,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { checked ->
                    if (onClick != null) {
                        onClick()
                    } else {
                        setValue(checked)
                        value = getValue()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
    SettingsIcon(
        icon = icon,
        contentDescription = text,
        modifier = Modifier.align(CenterVertically).padding(end = 16.dp).alpha(alpha),
        tintIcon = tintIcon,
        containerColor = iconContainerColor,
        iconSize = iconSize
    )
    Column(Modifier.align(CenterVertically).weight(1f).alpha(alpha)) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = ApkTheme.colors.textPrimary
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = ApkTheme.colors.textSecondary
            )
        }
    }
    
    if (onClick != null) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.align(CenterVertically).padding(end = 8.dp),
            tint = ApkTheme.colors.textSecondary
        )
    }

    Switch(
        checked = value,
        enabled = enabled,
        onCheckedChange = null,
        modifier = Modifier.align(CenterVertically),
        colors = SwitchDefaults.colors(
            checkedThumbColor = ApkTheme.colors.onAccent,
            checkedTrackColor = ApkTheme.colors.accent,
            uncheckedThumbColor = ApkTheme.colors.textTertiary,
            uncheckedTrackColor = ApkTheme.colors.surfaceSecondary,
            uncheckedBorderColor = ApkTheme.colors.divider
        )
    )
    }
}

@Composable
fun ButtonSetting(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int
) = Row(
    Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .tvFocusOutline(ApkTheme.shapes.sm)
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 10.dp)
) {
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Text(
        text,
        Modifier.align(CenterVertically).weight(1f),
        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        color = ApkTheme.colors.textPrimary
    )
}
