package dev.aaa1115910.bv.component.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.SettingsBottomIndicator
import dev.aaa1115910.bv.screen.settings.LocalSettingsContentColor
import dev.aaa1115910.bv.ui.theme.C

@Composable
internal fun SettingsNavigationListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    colors: ListItemColors,
    contentColor: Color? = null,
    onFocus: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val itemContentColor = contentColor ?: LocalSettingsContentColor.current

    ListItem(
        modifier = SettingsBottomIndicator(
            modifier = modifier,
            animatedSelected = hasFocus,
            fixedSelected = false,
            color = C.primary
        )
            .onFocusChanged {
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocus()
            },
        selected = false,
        onClick = {},
        colors = colors,
        headlineContent = {
            Text(
                text = title,
                color = itemContentColor
            )
        },
        supportingContent = if (description.isBlank()) {
            null
        } else {
            {
                Text(
                    text = description,
                    color = itemContentColor
                )
            }
        }
    )
}
