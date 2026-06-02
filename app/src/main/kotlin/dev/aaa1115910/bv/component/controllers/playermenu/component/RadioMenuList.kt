package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.PlayerMenuMainEntryId
import dev.aaa1115910.bv.component.controllers.playermenu.playerMenuFocusPrefix
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.wjzFocusRestorerHost

@Composable
fun RadioMenuList(
    modifier: Modifier = Modifier,
    focusIdPrefix: String,
    items: List<String>,
    selected: Int = 0,
    parentFocusEntryId: String = PlayerMenuMainEntryId,
    onItemFocused: () -> Unit = {},
    onSelectedChanged: (index: Int) -> Unit,
    onFocusBackToParent: () -> Unit
) {
    val focusScopeId = LocalWjzFocusScopeId.current
    val focusPrefix = playerMenuFocusPrefix(focusIdPrefix)
    val listIds = focusPrefix.listIds()
    val fallbackIndex = selected.takeIf { it in items.indices } ?: items.indices.firstOrNull()
    val fallbackLocalFocusId = fallbackIndex?.let { focusPrefix.localId(it) }
    LazyColumn(
        modifier = modifier
            .wjzFocusRestorerHost(
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId,
                restorerId = listIds.restorerId,
                listId = listIds.listId,
                fallbackNodeId = fallbackLocalFocusId?.let { focusScopeId?.resolve(it) }
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, _ -> focusPrefix.child(index).value }
        ) { index, item ->
            MenuListItem(
                modifier = Modifier
                    .width(200.dp),
                localFocusId = focusPrefix.localId(index),
                text = item,
                selected = selected == index,
                exits = {
                    right move parentFocusEntryId
                },
                onFocus = onItemFocused,
                onClick = {
                    println("Click menu: $item ($index)")
                    onSelectedChanged(index)
                }
            )
        }
    }
}
