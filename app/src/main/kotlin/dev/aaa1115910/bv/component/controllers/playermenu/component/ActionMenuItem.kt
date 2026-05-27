package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ActionMenuItem(
    modifier: Modifier = Modifier,
    focusId: String,
    text: String,
    active: Boolean = false,
    focusEnabled: Boolean = true,
    textAlign: TextAlign = TextAlign.Center,
    onFocus: () -> Unit = {},
    onClick: () -> Unit
) {
    MenuListItem(
        modifier = modifier,
        focusId = focusId,
        text = text,
        selected = active,
        focusEnabled = focusEnabled,
        textAlign = textAlign,
        onFocus = onFocus,
        onClick = onClick
    )
}
