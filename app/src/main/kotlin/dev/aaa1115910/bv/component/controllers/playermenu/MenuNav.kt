package dev.aaa1115910.bv.component.controllers.playermenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.VideoPlayerMenuNavItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.MenuListItem

@Composable
fun MenuNavList(
    modifier: Modifier = Modifier,
    selectedMenu: VideoPlayerMenuNavItem,
    onSelectedChanged: (VideoPlayerMenuNavItem) -> Unit,
    isFocusing: Boolean
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(VideoPlayerMenuNavItem.entries) { index, item ->
            MenuListItem(
                modifier = Modifier,
                focusId = "$PlayerMenuNavFocusIdPrefix/$index",
                text = item.getDisplayName(context),
                icon = item.icon,
                expanded = isFocusing,
                selected = selectedMenu == item,
                fallback = index == 0,
                onClick = {},
                onFocus = { onSelectedChanged(item) },
            )
        }
    }
}
