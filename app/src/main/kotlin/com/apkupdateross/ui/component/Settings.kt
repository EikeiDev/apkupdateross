package com.apkupdateross.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkupdateross.R

@Composable
fun SettingsIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tintIcon: Boolean = true,
    containerColor: Color? = null,
    iconSize: Dp = 24.dp
) = androidx.compose.material3.Surface(
    shape = androidx.compose.material3.MaterialTheme.shapes.medium,
    color = containerColor ?: androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
    modifier = modifier.size(40.dp)
) {
    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (tintIcon) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
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
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Column(Modifier.weight(1f)) {
        Box(Modifier.fillMaxWidth()) {
            Text(text, Modifier.align(CenterStart), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            Text("${getValue().toInt()}", Modifier.align(CenterEnd), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
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
    enabledItems: List<Boolean> = options.map { true }
) = Box(Modifier.fillMaxWidth()) {
    var position by remember { mutableIntStateOf(getValue()) }
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.getOrElse(position) { options.firstOrNull().orEmpty() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = CenterVertically
    ) {
        SettingsIcon(icon, text, Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            Text(
                selectedText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            Surface(
                modifier = Modifier.widthIn(min = 104.dp, max = 156.dp),
                shape = androidx.compose.material3.MaterialTheme.shapes.small,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = CenterVertically
                ) {
                    Text(
                        selectedText,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 156.dp)
            ) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        enabled = enabledItems.getOrElse(index) { true },
                        onClick = {
                            position = index
                            setValue(position)
                            expanded = false
                        }
                    )
                }
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
) = Row(Modifier.fillMaxWidth().heightIn(min = if (subtitle != null) 88.dp else 72.dp).clickable(enabled = enabled) {
    if (onClick != null) onClick() else {
        val next = !getValue()
        setValue(next)
    }
}.padding(horizontal = 16.dp)) {
    var value by remember { mutableStateOf(getValue()) }
    val alpha = if (enabled) 1f else 0.5f
    SettingsIcon(
        icon = icon,
        contentDescription = text,
        modifier = Modifier.align(CenterVertically).padding(end = 16.dp).alpha(alpha),
        tintIcon = tintIcon,
        containerColor = iconContainerColor,
        iconSize = iconSize
    )
    Column(Modifier.align(CenterVertically).weight(1f).alpha(alpha)) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    
    if (onClick != null) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.align(CenterVertically).padding(end = 8.dp),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Switch(
        checked = value,
        enabled = enabled,
        onCheckedChange = {
            setValue(it)
            value = getValue()
        },
        modifier = Modifier.align(CenterVertically)
    )
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
        .clickable { onClick() }
        .padding(horizontal = 16.dp)
) {
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Text(text, Modifier.align(CenterVertically).weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
}
