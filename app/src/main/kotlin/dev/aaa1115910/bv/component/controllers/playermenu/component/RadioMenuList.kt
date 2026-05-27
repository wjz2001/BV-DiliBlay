package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.playermenu.playerMenuFocusNodeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.wjzFocusRestorerHost

@Composable
fun RadioMenuList(
    modifier: Modifier = Modifier,
    focusIdPrefix: String,
    items: List<String>,
    selected: Int = 0,
    onSelectedChanged: (index: Int) -> Unit,
    onFocusBackToParent: () -> Unit
) {
    val focusScopeId = LocalWjzFocusScopeId.current
    val fallbackIndex = selected.takeIf { it in items.indices } ?: items.indices.firstOrNull()
    val fallbackFocusId = fallbackIndex?.let { "$focusIdPrefix/$it" }
    LazyColumn(
        modifier = modifier
            .onPreviewKeyEvent {
                println(it)
                if (it.type == KeyEventType.KeyUp) {
                    if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                        return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                val result = it.key == Key.DirectionRight
                if (result) onFocusBackToParent()
                result
            }
            .wjzFocusRestorerHost(
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId,
                restorerId = "$focusIdPrefix/restorer",
                listId = "$focusIdPrefix/list",
                fallbackNodeId = fallbackFocusId?.let { playerMenuFocusNodeId(focusScopeId, it) }
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, _ -> "$focusIdPrefix/$index" }
        ) { index, item ->
            MenuListItem(
                modifier = Modifier
                    .width(200.dp),
                focusId = "$focusIdPrefix/$index",
                text = item,
                selected = selected == index,
                onClick = {
                    println("Click menu: $item ($index)")
                    onSelectedChanged(index)
                }
            )
        }
    }
}
