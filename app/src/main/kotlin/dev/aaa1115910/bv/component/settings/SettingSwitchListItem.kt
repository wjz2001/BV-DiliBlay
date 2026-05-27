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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.SettingsBottomIndicator
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.wjzfocus.wjzDisabledFocus

@Composable
fun SettingSwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    checked: Boolean,
    focused: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    contentColor: Color? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = SettingsBottomIndicator(
            modifier = modifier,
            animatedSelected = focused,
            fixedSelected = false,
            color = C.primary
        ),
        headlineContent = { Text(text = title, color = contentColor ?: Color.Unspecified) },
        supportingContent = { Text(text = supportText, color = contentColor ?: Color.Unspecified) },
        trailingContent = {
            Switch(
                modifier = Modifier.wjzDisabledFocus(),
                checked = checked,
                selected = focused,
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
        selected = focused,
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
