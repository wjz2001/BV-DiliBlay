package dev.aaa1115910.bv.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.SettingsBottomIndicator
import dev.aaa1115910.bv.screen.settings.SettingsEntry
import dev.aaa1115910.bv.screen.settings.settingsTransparentListItemColors
import dev.aaa1115910.bv.ui.theme.C

internal fun actionEntry(
    id: String,
    title: String,
    supportText: String,
    actionText: String,
    onClick: () -> Unit
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    detailContent = {
        SettingsActionDetailItem(
            title = actionText,
            supportText = supportText,
            focused = it,
            onClick = onClick
        )
    }
)

@Composable
private fun SettingsActionDetailItem(
    title: String,
    supportText: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = SettingsBottomIndicator(
            modifier = Modifier.fillMaxWidth(),
            animatedSelected = focused,
            fixedSelected = false,
            color = C.primary
        ),
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportText) },
        onClick = onClick,
        selected = focused,
        colors = settingsTransparentListItemColors()
    )
}
