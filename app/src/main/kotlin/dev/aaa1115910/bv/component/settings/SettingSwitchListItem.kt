package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.C

@Composable
fun SettingSwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    checked: Boolean,
    defaultHasFocus: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    onCheckedChange: (Boolean) -> Unit
) {
    var hasFocus by remember { mutableStateOf(defaultHasFocus) }

    ListItem(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportText) },
        trailingContent = {
            Switch(
                    modifier = Modifier
                        .focusable(false)
                        .padding(2.dp),
                    checked = checked,
                    selected = hasFocus,
                    checkedSelectedThumbColor = C.primary,
                    checkedSelectedTrackColor = C.secondary,
                    checkedUnselectedThumbColor = C.secondary,
                    checkedUnselectedTrackColor = C.tertiary,
                    uncheckedThumbColor = C.onSurfaceVariant,
                    uncheckedTrackColor = C.onSurfaceVariant
                )
        },
        onClick = {
            onCheckedChange(!checked)
        },
        selected = hasFocus,
        colors = colors
    )
}

@Composable
private fun Switch(
    checked: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    checkedSelectedThumbColor: Color,
    checkedUnselectedThumbColor: Color,
    uncheckedThumbColor: Color,
    checkedSelectedTrackColor: Color,
    checkedUnselectedTrackColor: Color,
    uncheckedTrackColor: Color
) {
    val thumbSize = 20.dp
    val switchWidth = 34.dp
    val switchHeight = 20.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) switchWidth - thumbSize else 0.dp,
        label = "switchThumbOffset"
    )

    //横杠
    val trackColor = when {
        checked && selected -> checkedSelectedTrackColor
        checked -> checkedUnselectedTrackColor
        else -> uncheckedTrackColor
    }

    //圆点
    val thumbColor = when {
        checked && selected -> checkedSelectedThumbColor
        checked -> checkedUnselectedThumbColor
        else -> uncheckedThumbColor
    }

    Box(
        modifier = modifier
            .size(width = switchWidth, height = switchHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .background(trackColor, RoundedCornerShape(percent = 50))
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = thumbOffset.roundToPx(),
                        y = 0
                    )
                }
                .size(thumbSize)
                .background(thumbColor, CircleShape)
        )
    }
}
