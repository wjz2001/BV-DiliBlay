package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ToggleMenuItem(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    textAlign: TextAlign = TextAlign.Center,
    onFocus: () -> Unit = {},
    onCheckedChange: (Boolean) -> Unit
) {
    MenuListItem(
        modifier = modifier,
        text = text,
        selected = checked,
        textAlign = textAlign,
        onFocus = onFocus,
        onClick = { onCheckedChange(!checked) }
    )
}
