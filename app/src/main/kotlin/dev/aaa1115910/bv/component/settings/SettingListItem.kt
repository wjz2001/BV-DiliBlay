package dev.aaa1115910.bv.component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.C

@Composable
fun SettingListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    enabled: Boolean = true,
    defaultHasFocus: Boolean = false,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(defaultHasFocus) }
    val textColor = if (enabled) Color.Unspecified else C.disabled

    ListItem(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = { Text(text = title, color = textColor) },
        supportingContent = { Text(text = supportText, color = textColor) },
        trailingContent = { },
        onClick = {
            if (enabled) onClick()
        },
        selected = false
    )
}