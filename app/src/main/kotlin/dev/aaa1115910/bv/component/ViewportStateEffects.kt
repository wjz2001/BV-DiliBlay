package dev.aaa1115910.bv.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.entity.state.ListViewportState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun rememberRestoredLazyGridState(viewport: GridViewportState): LazyGridState {
    return rememberLazyGridState(
        initialFirstVisibleItemIndex = viewport.index,
        initialFirstVisibleItemScrollOffset = viewport.scrollOffset
    )
}

@Composable
fun rememberRestoredLazyListState(viewport: ListViewportState): LazyListState {
    return rememberLazyListState(
        initialFirstVisibleItemIndex = viewport.index,
        initialFirstVisibleItemScrollOffset = viewport.scrollOffset
    )
}

@Composable
fun PersistLazyGridViewportEffect(
    state: LazyGridState,
    onViewportChanged: (index: Int, offset: Int) -> Unit
) {
    LaunchedEffect(state) {
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onViewportChanged(index, offset)
            }
    }
}

@Composable
fun PersistLazyListViewportEffect(
    state: LazyListState,
    onViewportChanged: (index: Int, offset: Int) -> Unit
) {
    LaunchedEffect(state) {
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onViewportChanged(index, offset)
            }
    }
}