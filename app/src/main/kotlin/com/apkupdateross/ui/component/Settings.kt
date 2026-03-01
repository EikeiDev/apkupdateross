package com.apkupdateross.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apkupdateross.R

@Composable
fun SettingsIcon(@DrawableRes icon: Int, contentDescription: String, modifier: Modifier = Modifier) = androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.CircleShape,
    color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier.size(40.dp)
) {
    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
@Composable
fun SliderSetting(
    getValue: () -> Float,
    setValue: (Float) -> Unit,
    text: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
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
            steps = steps,
            onValueChange = {
                position = it
                setValue(it)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
) = Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
    var position by remember { mutableIntStateOf(getValue()) }
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Column(Modifier.weight(1f)) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp).fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = {
                        position = index
                        setValue(position)
                    },
                    selected = index == position,
                    enabled = enabledItems.getOrElse(index) { true },
                    icon = {}
                ) {
                    Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    @DrawableRes icon: Int = R.drawable.ic_system
) = Row(Modifier.fillMaxWidth().height(72.dp).clickable {
    val next = !getValue()
    setValue(next)
}.padding(horizontal = 16.dp)) {
    var value by remember { mutableStateOf(getValue()) }
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Text(text, Modifier.align(CenterVertically).weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    Switch(
        checked = value,
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
    @DrawableRes icon: Int,
    @DrawableRes iconButton: Int
) = Row(
    Modifier
        .fillMaxWidth()
        .height(72.dp)
        .clickable { onClick() }
        .padding(horizontal = 16.dp)
) {
    SettingsIcon(icon, text, Modifier.align(CenterVertically).padding(end = 16.dp))
    Text(text, Modifier.align(CenterVertically).weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    IconButton(onClick = onClick, modifier = Modifier.align(CenterVertically)) {
        Icon(painterResource(iconButton), stringResource(R.string.copy_to_clipboard))
    }
}
