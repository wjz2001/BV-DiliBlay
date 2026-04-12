package dev.aaa1115910.bv.component.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text

@Composable
fun <T> SettingCycleListItem(
    modifier: Modifier = Modifier,
    title: String,
    options: List<T>,
    checked: T,
    defaultHasFocus: Boolean = false,
    supportText: (T) -> String = { "" },
    trailingText: (T) -> String = { "" },
    onCheckedChange: (T) -> Unit
) {
    if (options.isEmpty()) return

    var hasFocus by remember { mutableStateOf(defaultHasFocus) }

    val currentIndex = options.indexOf(checked).takeIf { it >= 0 } ?: 0
    val currentOption = options[currentIndex]
    val currentSupportText = supportText(currentOption)
    val currentTrailingText = trailingText(currentOption)

    ListItem(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = { Text(text = title) },
        supportingContent = {
            if (currentSupportText.isNotBlank()) {
                Text(text = currentSupportText)
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier.widthIn(min = 80.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (currentTrailingText.isNotBlank()) {
                    Text(text = currentTrailingText)
                }
            }
        },
        onClick = {
            val nextIndex = (currentIndex + 1) % options.size
            onCheckedChange(options[nextIndex])
        },
        selected = hasFocus
    )
}