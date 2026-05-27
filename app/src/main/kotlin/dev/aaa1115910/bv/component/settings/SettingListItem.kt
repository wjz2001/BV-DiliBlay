package dev.aaa1115910.bv.component.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.SettingsBottomIndicator
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.wjzfocus.wjzDisabledFocus

@Composable
fun SettingListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    enabled: Boolean = true,
    focused: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (enabled) Color.Unspecified else C.disabled

    ListItem(
        modifier = SettingsBottomIndicator(
            modifier = modifier
                .padding(horizontal = 12.dp)
                .wjzDisabledFocus(!enabled),
            animatedSelected = focused,
            fixedSelected = false,
            color = C.primary
        ),
        headlineContent = { Text(text = title, color = textColor) },
        supportingContent = { Text(text = supportText, color = textColor) },
        trailingContent = { },
        onClick = {
            if (enabled) onClick()
        },
        selected = false
    )
}
