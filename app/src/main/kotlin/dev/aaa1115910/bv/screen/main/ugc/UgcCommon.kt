package dev.aaa1115910.bv.screen.main.ugc

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.biliapi.entity.ugc.region.UgcRegionPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun UgcRegionScaffold(
    modifier: Modifier = Modifier,
    state: UgcScaffoldState,
) {
    val gridState = state.lazyGridState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state.ugcItems.isEmpty()) state.initUgcRegionData() }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.ugcItems.size - 20
            }
            .collect {
                scope.launch(Dispatchers.IO) {
                    state.loadMore()
                }
            }
    }

    LazyVerticalGrid(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = state.ugcItems,
            key = { index, _ -> index }
        ) { _, item ->
            SmallVideoCard(
                data = remember(item) {         // `VideoCardData` 只在 item 变动时重建
                    VideoCardData(
                        avid = item.aid,
                        title = item.title,
                        cover = item.cover,
                        play = item.play.takeIf { it != -1 },
                        danmaku = item.danmaku.takeIf { it != -1 },
                        upName = item.author,
                        time = item.duration * 1000L,
                        pubTime = item.pubTime
                    )
                },
                onClick = { VideoInfoActivity.actionStart(context, item.aid) }
            )
        }

        if (state.updating) {
            item(span = { GridItemSpan(maxLineSpan) }) {    // 网格里占整行
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) { LoadingTip() }
            }
        }
    }

//    LazyColumn(
//        modifier = modifier,
//        state = state.lazyListState
//    ) {
//        gridItems(
//            data = state.ugcItems,
//            columnCount = 4,
//            modifier = Modifier
//                .width(880.dp)
//                .padding(horizontal = 24.dp, vertical = 12.dp),
//            horizontalArrangement = Arrangement.spacedBy(24.dp),
//            itemContent = { index, item ->
//                SmallVideoCard(
//                    data = VideoCardData(
//                        avid = item.aid,
//                        title = item.title,
//                        cover = item.cover,
//                        play = item.play,
//                        danmaku = item.danmaku,
//                        upName = item.author,
//                        time = item.duration * 1000L,
//                        pubTime = item.pubTime
//                    ),
//                    onClick = { VideoInfoActivity.actionStart(context, item.aid) },
//                )
//            }
//        )
//    }
}


data class UgcScaffoldState(
    val context: Context,
    val scope: CoroutineScope,
    val lazyGridState: LazyGridState,
    val ugcType: UgcType,
    private val ugcRepository: UgcRepository
) {
    companion object {
        val logger = KotlinLogging.logger { }
    }
    val ugcItems = mutableStateListOf<UgcItem>()
    var nextPage by mutableStateOf(UgcRegionPage())
    var hasMore by mutableStateOf(true)
    var updating by mutableStateOf(false)

    suspend fun initUgcRegionData() {
        loadUgcRegionData()
        loadMore()
    }

    suspend fun loadUgcRegionData() {
        if (!hasMore && updating) return
        updating = true
        logger.fInfo { "load ugc $ugcType region data" }
        runCatching {
            val data = ugcRepository.getRegionData(ugcType)
            ugcItems.clear()
            ugcItems.addAll(data.items)
            nextPage = data.next
        }.onFailure {
            logger.fInfo { "load $ugcType data failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载 $ugcType 数据失败: ${it.message}".toast(context)
            }
        }
        hasMore = true
        updating = false
    }

    fun reloadAll() {
        logger.fInfo { "reload all $ugcType data" }
        scope.launch(Dispatchers.IO) {
            nextPage = UgcRegionPage()
            hasMore = true
            ugcItems.clear()
            initUgcRegionData()
        }
    }

    suspend fun loadMore() {
        if (!hasMore && updating) return
        updating = true
        runCatching {
            val data = ugcRepository.getRegionMoreData(ugcType)
            ugcItems.addAll(data.items)
            nextPage = data.next
            hasMore = data.items.isNotEmpty()
        }.onFailure {
            logger.fInfo { "load more $ugcType data failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载 $ugcType 更多推荐失败: ${it.message}".toast(context)
            }
        }
        updating = false
    }
}

@Composable
fun rememberUgcScaffoldState(
    context: Context = LocalContext.current,
    scope: CoroutineScope = rememberCoroutineScope(),
    lazyGridState: LazyGridState = rememberLazyGridState(),
    ugcType: UgcType,
    ugcRepository: UgcRepository = koinInject()
): UgcScaffoldState {
    return remember(
        context,
        scope,
        lazyGridState,
        ugcType,
        ugcRepository
    ) {
        UgcScaffoldState(
            context = context,
            scope = scope,
            lazyGridState = lazyGridState,
            ugcType = ugcType,
            ugcRepository = ugcRepository
        )
    }
}