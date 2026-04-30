package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ActionMenuItem(
    modifier: Modifier = Modifier,
    text: String,
    active: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
    onFocus: () -> Unit = {},
    onClick: () -> Unit
) {
    MenuListItem(
        modifier = modifier,
        text = text,
        selected = active,
        textAlign = textAlign,
        onFocus = onFocus,
        onClick = onClick
    )
}
