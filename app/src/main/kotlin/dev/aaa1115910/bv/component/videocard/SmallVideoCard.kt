package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.component.buttons.FavoriteDialog
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.theme.BVTheme
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ButtonDefaults
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.swapListWithMainContext
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository


@Composable
fun SmallVideoCard(
    modifier: Modifier = Modifier,
    data: VideoCardData,
    delToView: Boolean = false,
    onClick: () -> Unit,
    onAddWatchLater: (() -> Unit)? = null,
    onGoToDetailPage : (() -> Unit)? = null,
    onGoToUpPage : (() -> Unit)? = null,
    // 1. 为 SmallVideoCard 添加独立的参数，用于分别控制 Cover 和 Info
    coverDensityMultiplier: Float = 1.5f,
    coverFontScaleMultiplier: Float = 1.5f,
    infoDensityMultiplier: Float = 1.35f,
    infoFontScaleMultiplier: Float = 1.35f
) {
    /*
    var showActions by remember { mutableStateOf(false) }
    // 解决长按卡片松开会导致一次按钮触发的问题
    var releaseLongPress by remember { mutableStateOf(false) }
    val firstButtonRequester = remember { FocusRequester() }

    // 判断是否有任何操作按钮
    val hasAnyAction = onAddWatchLater != null || onGoToDetailPage != null || onGoToUpPage != null

    LaunchedEffect(showActions) {
        if (showActions && hasAnyAction) {
            firstButtonRequester.requestFocus()
        } else if (!showActions) {
            releaseLongPress = false // 退出操作态时重置
        }
    }
    */
    val logger = KotlinLogging.logger { }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val favoriteRepository: FavoriteRepository = getKoin().get()
    val videoDetailRepository: VideoDetailRepository = getKoin().get()

    var showActions by remember { mutableStateOf(false) }
    // 解决长按卡片松开会导致一次按钮触发的问题
    var releaseLongPress by remember { mutableStateOf(false) }

    // --- 新增代码：为每个操作按钮定义独立的焦点状态 ---
    var historyFocused by remember { mutableStateOf(false) }
    var favoriteFocused by remember { mutableStateOf(false) }
    var upFocused by remember { mutableStateOf(false) }
    var watchLaterFocused by remember { mutableStateOf(false) }

    // 用于实现：showActions && !focusState.isFocused 时也保留描边
    var cardIsFocused by remember { mutableStateOf(false) }

    // 默认焦点：历史记录按钮
    val historyButtonRequester = remember { FocusRequester() }

    // 历史记录：缓存 cid，避免重复请求
    var historyCid by remember(data.avid) { mutableStateOf<Long?>(null) }

    // 收藏：状态由 SmallVideoCard 自己管理
    var showFavoriteDialog by remember(data.avid) { mutableStateOf(false) }
    var isFavorite by remember(data.avid) { mutableStateOf(false) }

    // 仅在本次 SmallVideoCard 实例生命周期内校准一次收藏状态（更省请求）
    var hasCheckedFavorite by remember(data.avid) { mutableStateOf(false) }
    var checkingFavorite by remember(data.avid) { mutableStateOf(false) }

    val favoriteFolderMetadataList =
        remember(data.avid) { mutableStateListOf<FavoriteFolderMetadata>() }
    val videoInFavoriteFolderIds =
        remember(data.avid) { mutableStateListOf<Long>() }

    // 四按钮永远显示：不可用则置灰 & 不响应点击
    val canWatchLater = onAddWatchLater != null
    val canGoToUpPage = onGoToUpPage != null

    // 收藏：按当前 ApiType 判断需要的凭证；失败只打日志
    val canFavorite = when (Prefs.apiType) {
        ApiType.Web -> Prefs.sessData.isNotEmpty() && Prefs.biliJct.isNotEmpty() && Prefs.uid != 0L
        ApiType.App -> Prefs.accessToken.isNotEmpty() && Prefs.uid != 0L
    }

    // 仅用于“校准收藏图标”的轻量判断：checkVideoFavoured 不需要 biliJct
    val canCheckFavorite = when (Prefs.apiType) {
        ApiType.Web -> Prefs.sessData.isNotEmpty() && Prefs.uid != 0L
        ApiType.App -> Prefs.accessToken.isNotEmpty() && Prefs.uid != 0L
    }

    // 历史上报：当前方案仅走 access_key（/x/v2/history/report）
    val canHistory = Prefs.accessToken.isNotEmpty()

    LaunchedEffect(showActions) {
        if (showActions) {
            // 用项目内已有的“带重试的请求焦点”扩展，确保默认焦点可靠
            historyButtonRequester.requestFocus(scope)

            // 长按弹层显示时：仅校准一次收藏状态（不查在哪个收藏夹）
            if (canCheckFavorite && !hasCheckedFavorite && !checkingFavorite) {
                checkingFavorite = true
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        favoriteRepository.checkVideoFavoured(
                            aid = data.avid,
                            preferApiType = Prefs.apiType
                        )
                    }.onSuccess { favoured ->
                        logger.fInfo { "Check video favoured success: aid=${data.avid}, favoured=$favoured" }
                        withContext(Dispatchers.Main) {
                            isFavorite = favoured
                            hasCheckedFavorite = true
                            checkingFavorite = false
                        }
                    }.onFailure { e ->
                        logger.fWarn { "Check video favoured failed: aid=${data.avid}, error=${e.stackTraceToString()}" }
                        // 失败也标记为已检查：满足“本实例生命周期内只校准一次（更省请求）”
                        withContext(Dispatchers.Main) {
                            hasCheckedFavorite = true
                            checkingFavorite = false
                        }
                    }
                }
            }
        } else {
            releaseLongPress = false // 退出操作态时重置
        }
    }

    // 关闭收藏弹窗后，如果仍处于 actions 状态，尽量把焦点拉回“历史记录”
    LaunchedEffect(showFavoriteDialog) {
        if (!showFavoriteDialog && showActions) {
            historyButtonRequester.requestFocus(scope)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = { if (!showActions) onClick() },
            onLongClick = {
                //if (hasAnyAction) showActions = true
                // 四按钮必须永远显示：长按永远进入 actions
                showActions = true

            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .onFocusChanged { focusState ->
                    //if (!focusState.hasFocus) showActions = false
                    // 记录 Card 本体是否 focused，用于“showActions && !isFocused”手动描边
                    cardIsFocused = focusState.isFocused

                    // Card 及其子树完全失焦则退出 actions
                    // 但当收藏弹窗打开时，焦点会离开 Card，此时不应把 actions 直接关掉（否则用户关闭弹窗回来时不在 actions 里）
                    if (!focusState.hasFocus && !showFavoriteDialog) showActions = false
                },
            shape = CardDefaults.shape(MaterialTheme.shapes.large),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
                    shape = MaterialTheme.shapes.large
                )
            )
        ) {
            if (showActions) {
                // 外层 Box：用于“背景/内容/描边”分层，保证描边不会被 padding 影响
                Box(modifier = Modifier.fillMaxSize()) {
                    // 黑色背景 + 2x2 按钮布局
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    ) {
                        // 上排：左历史、右收藏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                // 左上：历史记录（默认焦点）
                                IconButton(
                                    modifier = Modifier
                                        .focusRequester(historyButtonRequester)
                                        .onFocusChanged { historyFocused = it.isFocused }
                                        .size(60.dp)
                                        .aspectRatio(1f),
                                    shape = ButtonDefaults.shape(shape = CircleShape),
                                    onClick = {
                                        if (!releaseLongPress) {
                                            releaseLongPress = true
                                            return@IconButton
                                        }
                                        if (!canHistory) return@IconButton

                                        scope.launch(Dispatchers.IO) {
                                            runCatching {
                                                val resolvedCid = historyCid
                                                    ?: videoDetailRepository
                                                        .getUgcPages(
                                                            aid = data.avid,
                                                            preferApiType = ApiType.Web
                                                        )
                                                        .firstOrNull()
                                                        ?.cid
                                                        ?.takeIf { it != 0L }

                                                require(resolvedCid != null) { "cid is null" }
                                                if (historyCid != resolvedCid) {
                                                    withContext(Dispatchers.Main) {
                                                        historyCid = resolvedCid
                                                    }
                                                }

                                                logger.fInfo { "Report history: aid=${data.avid}, cid=$resolvedCid, progress=1" }
                                                // /x/v2/history/report
                                                BiliHttpApi.sendHeartbeat(
                                                    avid = data.avid,
                                                    cid = resolvedCid,
                                                    playedTime = 1,
                                                    accessKey = Prefs.accessToken.takeIf { it.isNotEmpty() }
                                                )
                                            }.onSuccess {
                                                // [修改点 3] 成功后显示 Toast
                                                withContext(Dispatchers.Main) {
                                                    "已添加至历史记录".toast(context)
                                                }
                                            }.onFailure {
                                                logger.fWarn {
                                                    "Report history failed: aid=${data.avid}, error=${it.stackTraceToString()}"
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(40.dp),
                                        painter = painterResource(id = R.drawable.add_to_list),
                                        contentDescription = "History",
                                        tint = when {
                                                   historyFocused -> Color.Black
                                                   canHistory -> Color.White
                                                   else -> Color.White.copy(alpha = 0.4f)
                                                  }
                                        )
                                }
                            }

                            // 右上：收藏（原“详情”按钮改为收藏按钮）
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .onFocusChanged { favoriteFocused = it.isFocused }
                                        .size(60.dp)
                                        .aspectRatio(1f),
                                    shape = ButtonDefaults.shape(shape = CircleShape),
                                    onClick = {
                                        if (!releaseLongPress) {
                                            releaseLongPress = true
                                            return@IconButton
                                        }
                                        if (!canFavorite) return@IconButton
                                        if (showFavoriteDialog) return@IconButton

                                        // 为了保证 FavoriteDialog 打开后能自动聚焦到第一个 Chip：
                                        // 这里先拉取收藏夹数据，成功后再 show 对话框
                                        scope.launch(Dispatchers.IO) {
                                            runCatching {
                                                val list =
                                                    favoriteRepository.getAllFavoriteFolderMetadataList(
                                                        mid = Prefs.uid,
                                                        rid = data.avid,
                                                        preferApiType = Prefs.apiType
                                                    )
                                                val selected =
                                                    list.filter { it.videoInThisFav }.map { it.id }

                                                favoriteFolderMetadataList.swapListWithMainContext(
                                                    list
                                                )
                                                videoInFavoriteFolderIds.swapListWithMainContext(
                                                    selected
                                                )

                                                withContext(Dispatchers.Main) {
                                                    isFavorite = selected.isNotEmpty()
                                                    showFavoriteDialog = true
                                                }
                                                logger.fInfo { "Fetch favorite folders success: aid=${data.avid}, selected=$selected" }
                                            }.onFailure {
                                                logger.fWarn {
                                                    "Fetch favorite folders failed: aid=${data.avid}, error=${it.stackTraceToString()}"
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(40.dp),
                                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = when {
                                                   favoriteFocused -> Color.Black
                                                   canFavorite -> Color.White
                                                   else -> Color.White.copy(alpha = 0.4f)
                                                  }
                                        )
                                }
                            }
                        }

                        // 下排：左up空间、右稍后再看
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // 左下：up空间
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .onFocusChanged { upFocused = it.isFocused }
                                        .size(60.dp)
                                        .aspectRatio(1f),
                                    shape = ButtonDefaults.shape(shape = CircleShape),
                                    onClick = {
                                        if (!releaseLongPress) {
                                            releaseLongPress = true
                                            return@IconButton
                                        }
                                        if (!canGoToUpPage) return@IconButton
                                        onGoToUpPage?.invoke()
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(40.dp),
                                        painter = painterResource(id = R.drawable.contact_page_24px),
                                        contentDescription = "Up Page",
                                        tint = when {
                                                   upFocused -> Color.Black
                                                   canGoToUpPage -> Color.White
                                                   else -> Color.White.copy(alpha = 0.4f)
                                                  }
                                        )
                                }
                            }

                            // 右下：稍后再看
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .onFocusChanged { watchLaterFocused = it.isFocused }
                                        .size(60.dp)
                                        .aspectRatio(1f),
                                    shape = ButtonDefaults.shape(shape = CircleShape),
                                    onClick = {
                                        if (!releaseLongPress) {
                                            releaseLongPress = true
                                            return@IconButton
                                        }
                                        if (!canWatchLater) return@IconButton
                                        onAddWatchLater?.invoke()
                                    }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(40.dp),
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Watch later",
                                        tint = when {
                                                   watchLaterFocused -> Color.Black
                                                   canWatchLater -> Color.White
                                                   else -> Color.White.copy(alpha = 0.4f)
                                                  }
                                        )
                                }
                            }
                        }
                    }

                    // Card 本体 focused：使用 CardDefaults.border 的 focusedBorder
                    // Card 本体不 focused 但处于 actions：这里补一层描边
                    if (showActions && !cardIsFocused) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(
                                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
                                    shape = MaterialTheme.shapes.large
                                )
                        )
                    }
                }
            } else {
                CardCover(
                    cover = data.cover,
                    play = data.playString,
                    danmaku = data.danmakuString,
                    time = data.timeString,
                    coverDensityMultiplier = coverDensityMultiplier,
                    coverFontScaleMultiplier = coverFontScaleMultiplier
                )
            }
            /*
            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onAddWatchLater?.let {
                        IconButton(
                            onClick = {
                                if (!releaseLongPress) {
                                    releaseLongPress = true
                                    return@IconButton
                                }
                                it()
                            },
                            modifier = Modifier.focusRequester(firstButtonRequester)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (delToView)
                                        R.drawable.remove_from_list
                                    else
                                        R.drawable.add_to_list
                                ),
                                contentDescription = "Add to/Remove from watch later"
                            )
                        }
                    }

                    onGoToDetailPage?.let {
                        IconButton(onClick = { it() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.info_24px),
                                contentDescription = "Video Detail"
                            )
                        }
                    }

                    onGoToUpPage?.let {
                        IconButton(onClick = { it() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.contact_page_24px),
                                contentDescription = "Up Page"
                            )
                        }
                    }
                }
            } else {
                CardCover(
                    cover = data.cover,
                    play = data.playString,
                    danmaku = data.danmakuString,
                    time = data.timeString,
                    // 2. 将封面相关的参数传递给 CardCover
                    coverDensityMultiplier = coverDensityMultiplier,
                    coverFontScaleMultiplier = coverFontScaleMultiplier
                )
            }
            */
        }

        CardInfo(
            modifier = Modifier.fillMaxWidth(),
            title = data.title,
            upName = data.upName,
            pubTime = data.pubTime,
            // 3. 将信息相关的参数传递给 CardInfo
            infoDensityMultiplier = infoDensityMultiplier,
            infoFontScaleMultiplier = infoFontScaleMultiplier
        )

        FavoriteDialog(
            show = showFavoriteDialog,
            onHideDialog = { showFavoriteDialog = false },
            userFavoriteFolders = favoriteFolderMetadataList,
            favoriteFolderIds = videoInFavoriteFolderIds,
            onUpdateFavoriteFolders = { folderIds ->
                // FavoriteDialog 的回调发生在主线程，切到 IO 做网络请求
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        require(favoriteFolderMetadataList.isNotEmpty()) { "Not found favorite folder" }

                        favoriteRepository.updateVideoToFavoriteFolder(
                            aid = data.avid,
                            addMediaIds = folderIds,
                            delMediaIds = favoriteFolderMetadataList.map { it.id } - folderIds.toSet(),
                            preferApiType = Prefs.apiType
                        )
                    }.onFailure {
                        logger.fWarn {
                            "Update favorite folders failed: aid=${data.avid}, folderIds=$folderIds, error=${it.stackTraceToString()}"
                        }
                    }.onSuccess {
                        videoInFavoriteFolderIds.swapListWithMainContext(folderIds)
                        withContext(Dispatchers.Main) {
                            isFavorite = folderIds.isNotEmpty()
                        }
                        logger.fInfo { "Update favorite folders success: aid=${data.avid}, folderIds=$folderIds" }
                    }
                }
            }
        )
    }
}


@Composable
fun CardCover(
    modifier: Modifier = Modifier,
    cover: String,
    play: String,
    danmaku: String,
    time: String,
    // 4. CardCover 使用独立的参数名
    coverDensityMultiplier: Float,
    coverFontScaleMultiplier: Float
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large),
        contentAlignment = Alignment.BottomCenter
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large),
            model = cover.resizedImageUrl(ImageSize.SmallVideoCardCover),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        // 渐变遮罩
        /*
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
        )
         */

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * coverDensityMultiplier,
                fontScale = LocalDensity.current.fontScale * coverFontScaleMultiplier
            )
        ) {
            // 播放数、弹幕数、时间
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            // 使用 colorStops 来精确控制渐变的位置和颜色
                            // 0.0f 代表顶部, 1.0f 代表底部
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.15f to Color.Transparent,
                                0.16f to Color.Black.copy(alpha = 0.7f),
                                1.0f to Color.Black.copy(alpha = 0.7f)
                            )
                            /*
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f) // 可以让底部颜色更深
                            )
                             */
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (play.isNotBlank()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_count),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = play,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (danmaku.isNotBlank()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_danmaku_count),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = danmaku,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CardInfo(
    modifier: Modifier = Modifier,
    title: String,
    upName: String,
    pubTime: String?,
    infoDensityMultiplier: Float,
    infoFontScaleMultiplier: Float
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * infoDensityMultiplier,
            fontScale = LocalDensity.current.fontScale * infoFontScaleMultiplier
        )
    ) {
        Column(
            modifier = modifier
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                // maxLines = 2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UpIcon()
                Text(
                    modifier = Modifier.weight(1f),
                    text = upName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pubTime ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Preview
@Composable
fun SmallVideoCardWithoutFocusPreview() {
    val data = VideoCardData(
        avid = 0,
        title = "震惊！太震惊了！真的是太震惊了！我的天呐！真TMD震惊！",
        cover = "http://i2.hdslb.com/bfs/archive/af17fc07b8f735e822563cc45b7b5607a491dfff.jpg",
        upName = "bishi",
        play = 2333,
        danmaku = 666,
        time = 2333 * 1000,
        pubTime = "1小时前"
    )
    BVTheme {
        Surface(
            modifier = Modifier.width(300.dp)
        ) {
            SmallVideoCard(
                modifier = Modifier.padding(20.dp),
                onClick = {},
                data = data,
            )
        }
    }
}

@Preview
@Composable
fun SmallVideoCardWithFocusPreview() {
    val data = VideoCardData(
        avid = 0,
        title = "震惊！太震惊了！真的是太震惊了！我的天呐！真TMD震惊！",
        cover = "http://i2.hdslb.com/bfs/archive/af17fc07b8f735e822563cc45b7b5607a491dfff.jpg",
        upName = "bishi",
        play = 2333,
        danmaku = 666,
        time = 2333 * 1000,
        pubTime = "1小时前"
    )
    BVTheme {
        Surface(
            modifier = Modifier.width(300.dp)
        ) {
            SmallVideoCard(
                modifier = Modifier.padding(20.dp),
                onClick = {},
                data = data,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun SmallVideoCardsPreview() {
    val data = VideoCardData(
        avid = 0,
        title = "震惊！太震惊了！真的是太震惊了！我的天呐！真TMD震惊！",
        //cover = "http://i2.hdslb.com/bfs/archive/af17fc07b8f735e822563cc45b7b5607a491dfff.jpg",
        cover = "",
        upName = "bishi",
        play = 2333,
        danmaku = 666,
        time = 2333 * 1000,
        pubTime = "1小时前"
    )
    BVTheme {
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(20) {
                item {
                    SmallVideoCard(
                        onClick = {},
                        data = data
                    )
                }
            }
        }
    }
}