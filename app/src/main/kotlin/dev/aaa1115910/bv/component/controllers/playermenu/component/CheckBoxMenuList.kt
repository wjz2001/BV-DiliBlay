package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.PlayerMenuMainEntryId
import dev.aaa1115910.bv.component.controllers.playermenu.PlayerMenuFocusPrefix
import dev.aaa1115910.bv.component.controllers.playermenu.playerMenuFocusPrefix
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroupRestorerComponent

@Composable
fun CheckBoxMenuList(
    modifier: Modifier = Modifier,
    focusIdPrefix: String,
    items: List<String>,
    selected: List<Int> = listOf(),
    parentFocusEntryId: String = PlayerMenuMainEntryId,
    onItemFocused: () -> Unit = {},
    onSelectedChanged: (indexes: List<Int>) -> Unit,
    onFocusBackToParent: () -> Unit
) {
    CheckBoxMenuList(
        modifier = modifier,
        focusPrefix = playerMenuFocusPrefix(focusIdPrefix),
        items = items,
        selected = selected,
        parentFocusEntryId = parentFocusEntryId,
        onItemFocused = onItemFocused,
        onSelectedChanged = onSelectedChanged,
        onFocusBackToParent = onFocusBackToParent
    )
}

@Composable
internal fun CheckBoxMenuList(
    modifier: Modifier = Modifier,
    focusPrefix: PlayerMenuFocusPrefix,
    items: List<String>,
    selected: List<Int> = listOf(),
    parentFocusEntryId: String = PlayerMenuMainEntryId,
    onItemFocused: () -> Unit = {},
    onSelectedChanged: (indexes: List<Int>) -> Unit,
    onFocusBackToParent: () -> Unit
) {
    val focusScopeId = LocalWjzFocusScopeId.current
    val listRestorer = wjzFocusGroupRestorerComponent(
        componentId = focusPrefix.value,
        layer = WjzFocusLayer.Overlay,
        scopeId = focusScopeId
    )
    val fallbackIndex = selected.firstOrNull { it in items.indices } ?: items.indices.firstOrNull()
    val fallbackLocalFocusId = fallbackIndex?.let { focusPrefix.localId(it) }
    LazyColumn(
        modifier = with(listRestorer) {
            modifier.restorerHost(
                fallbackNodeId = fallbackLocalFocusId?.let { focusScopeId?.resolve(it) }
            )
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, _ -> focusPrefix.child(index).value }
        ) { index, item ->
            MenuListItem(
                modifier = Modifier
                    .fillMaxWidth(),
                localFocusId = focusPrefix.localId(index),
                text = item,
                selected = selected.contains(index),
                exits = {
                    right move parentFocusEntryId
                },
                onFocus = onItemFocused,
                onClick = {
                    val newSelectedIndexes = selected.toMutableList()
                    if (newSelectedIndexes.contains(index)) newSelectedIndexes.remove(index)
                    else newSelectedIndexes.add(index)
                    onSelectedChanged(newSelectedIndexes)
                }
            )
        }
    }
}
