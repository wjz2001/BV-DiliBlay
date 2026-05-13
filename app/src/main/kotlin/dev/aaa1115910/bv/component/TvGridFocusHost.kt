package dev.aaa1115910.bv.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class TvGridFocusController(
    private val columnCount: Int
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    var enabled: Boolean = true
    var itemCount: Int = 0
    var entryFocusRequester: FocusRequester? = null
    var upFocusRequester: FocusRequester? = null
    var enableHorizontalLinks: Boolean = true
    var onEntryFocusReady: (() -> Unit)? = null
    var gridState: LazyGridState? = null
    var scope: CoroutineScope? = null

    private fun requesterFor(index: Int): FocusRequester {
        if (index == 0) {
            entryFocusRequester?.let { return it }
        }
        return requesters.getOrPut(index) { FocusRequester() }
    }

    private fun sameColumnTarget(index: Int, rowOffset: Int): Int? {
        val target = index + columnCount * rowOffset
        return target.takeIf { it in 0..itemCount }
    }

    private fun moveFocusTo(index: Int) {
        val state = gridState ?: return
        val coroutineScope = scope ?: return
        coroutineScope.launch {
            state.scrollToItem(index)
            requesterFor(index).requestFocus()
        }
    }

    fun Modifier.modifierFor(index: Int): Modifier {
        if (!enabled || itemCount <= 0 || index !in 0..itemCount) return this

        val rowStart = (index / columnCount) * columnCount
        val rowEnd = minOf(rowStart + columnCount - 1, itemCount - 1)
        val upTarget = sameColumnTarget(index, -1)
        val downTarget = sameColumnTarget(index, 1)

        var modifier = this
            .focusRequester(requesterFor(index))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionUp -> {
                        val target = upTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    Key.DirectionDown -> {
                        val target = downTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    else -> false
                }
            }
            .focusProperties {
                if (enableHorizontalLinks && rowStart != rowEnd) {
                    left = if (index == rowStart) {
                        requesterFor(rowEnd)
                    } else {
                        requesterFor(index - 1)
                    }

                    right = if (index == rowEnd) {
                        requesterFor(rowStart)
                    } else {
                        requesterFor(index + 1)
                    }
                }
                if (rowStart == 0) {
                    up = upFocusRequester ?: FocusRequester.Default
                } else {
                    upTarget?.let { up = requesterFor(it) }
                }
                downTarget?.let { down = requesterFor(it) }
            }
        if (index == 0) {
            modifier = modifier.onGloballyPositioned {
                onEntryFocusReady?.invoke()
            }
        }
        return modifier
    }
}

private val LocalTvGridFocusController =
    compositionLocalOf<TvGridFocusController?> { null }

@Composable
fun rememberTvGridFocusModifier(index: Int): Modifier {
    val controller = LocalTvGridFocusController.current
    return if (controller != null) {
        with(controller) { Modifier.modifierFor(index) }
    } else {
        Modifier
    }
}

@Composable
fun TvGridFocusHost(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    enableRowHorizontalWrap: Boolean = true,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onEntryFocusReady: (() -> Unit)? = null,
    focusItemCount: Int = 0,
    focusColumnCount: Int = 4,
    content: LazyGridScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusController = remember(focusColumnCount) {
        TvGridFocusController(focusColumnCount)
    }.apply {
        enabled = focusItemCount > 0 &&
                focusColumnCount > 0 &&
                (enableRowHorizontalWrap || entryFocusRequester != null)
        itemCount = focusItemCount
        this.entryFocusRequester = entryFocusRequester
        this.upFocusRequester = upFocusRequester
        this.enableHorizontalLinks = enableRowHorizontalWrap
        this.onEntryFocusReady = onEntryFocusReady
        this.gridState = state
        this.scope = scope
    }

    CompositionLocalProvider(
        LocalTvGridFocusController provides focusController
    ) {
        val gridModifier = if (focusItemCount <= 0 && entryFocusRequester != null) {
            modifier
                .focusRequester(entryFocusRequester)
                .focusProperties {
                    up = upFocusRequester ?: FocusRequester.Default
                }
                .focusable()
                .onGloballyPositioned {
                    onEntryFocusReady?.invoke()
                }
        } else {
            modifier
        }

        TvLazyVerticalGrid(
            columns = columns,
            modifier = gridModifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
    }
}
