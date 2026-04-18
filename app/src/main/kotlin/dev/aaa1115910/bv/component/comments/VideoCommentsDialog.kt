package dev.aaa1115910.bv.component.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.richtext.RichTextContent
import dev.aaa1115910.biliapi.entity.richtext.RichTextReference
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentPage
import dev.aaa1115910.biliapi.entity.reply.CommentReplyPage
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.biliapi.repositories.CommentRepository
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.richtext.RichText
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.buildRichTextTokens
import dev.aaa1115910.bv.util.countRichTextInteractiveTokens
import dev.aaa1115910.bv.util.loadRichContentDocument
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.launchPlayerActivity
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.RichContentDocument
import dev.aaa1115910.bv.util.ResolvedVideoLink
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class Page { Main, Replies, RichContent }
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCommentsDialog(
    show: Boolean,
    aid: Long,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val commentRepository: CommentRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentDensity = LocalDensity.current.density
    val currentFontScale = LocalConfiguration.current.fontScale
    val dialogDensity = remember(currentDensity, currentFontScale) {
        Density(
            density = currentDensity * 1.25f,
            fontScale = currentFontScale * 1.25f,
        )
    }

    val dialogBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val targetPosition = containerSize * 0.3f
                return offset - targetPosition
            }
        }
    }

    var gatePassed by remember(aid) { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.Main) }
    var richContentParentPage by remember { mutableStateOf(Page.Main) }

    // --- 主评论 ---
    val comments = remember { mutableStateListOf<Comment>() }
    var commentPage by remember { mutableStateOf(CommentPage()) }
    var commentsHasNext by remember { mutableStateOf(true) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf<String?>(null) }
    val commentsListState = rememberLazyListState()

    // 记录“主列表最后一次聚焦的评论 rpid”，用于从回复页返回时恢复焦点
    var lastMainFocusedRpid by remember { mutableStateOf<Long?>(null) }
    // 为每个主评论 item 维护 FocusRequester（rpid -> requester）
    val mainItemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    // --- 回复页 ---
    var rootComment by remember { mutableStateOf<Comment?>(null) }
    val replies = remember { mutableStateListOf<Comment>() }
    var replyPage by remember { mutableStateOf(CommentReplyPage()) }
    var repliesHasNext by remember { mutableStateOf(true) }
    var repliesLoading by remember { mutableStateOf(false) }
    var repliesError by remember { mutableStateOf<String?>(null) }
    val repliesListState = rememberLazyListState()

    // 回复列表 item 焦点请求器（rpid -> requester）
    val replyItemFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var lastReplyFocusedRpid by remember { mutableStateOf<Long?>(null) }

    var richContent by remember { mutableStateOf<RichContentDocument?>(null) }
    var richContentLoading by remember { mutableStateOf(false) }
    var richContentError by remember { mutableStateOf<String?>(null) }
    val richContentStack = remember { mutableStateListOf<RichContentDocument>() }
    val noteFullTexts = remember { mutableStateMapOf<Long, String>() }

    // 图片全屏预览：仅保存“当前评论图片集”
    var previewPictures by remember { mutableStateOf<List<Comment.Picture>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }

    fun openPreview(pictures: List<Comment.Picture>, index: Int) {
        if (pictures.isEmpty()) return
        previewPictures = pictures
        previewIndex = index.coerceIn(0, pictures.lastIndex)
    }

    fun closePreview() {
        previewPictures = emptyList()
        previewIndex = 0
    }

    fun switchPreview(delta: Int) {
        if (previewPictures.isEmpty()) return
        val size = previewPictures.size
        previewIndex = (previewIndex + delta + size) % size
    }

    // 根容器兜底焦点（无数据/异常时）
    val rootFocusRequester = remember { FocusRequester() }

    fun requestMainFocusRestore() {
        scope.launch {
            delay(40)
            val target = lastMainFocusedRpid
            val requester = target?.let { mainItemFocusRequesters[it] }
            if (requester != null) {
                runCatching { requester.requestFocus() }
                    .onFailure { runCatching { rootFocusRequester.requestFocus() } }
            } else {
                // 没记录到就兜底：列表第一个（如果有）
                val first = comments.firstOrNull()?.rpid
                val firstReq = first?.let { mainItemFocusRequesters[it] }
                if (firstReq != null) runCatching { firstReq.requestFocus() }
                else runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    fun requestReplyFocusRestore() {
        scope.launch {
            delay(40)
            val target = lastReplyFocusedRpid
            val requester = target?.let { replyItemFocusRequesters[it] }
            if (requester != null) {
                runCatching { requester.requestFocus() }
                    .onFailure { runCatching { rootFocusRequester.requestFocus() } }
            } else {
                val first = replies.firstOrNull()?.rpid
                val firstReq = first?.let { replyItemFocusRequesters[it] }
                if (firstReq != null) runCatching { firstReq.requestFocus() }
                else runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    fun openRichContent(
        reference: RichTextReference,
        parentPage: Page
    ) {
        richContentParentPage = parentPage
        richContentStack.clear()
        richContent = null
        richContentError = null
        richContentLoading = true
        page = Page.RichContent

        scope.launch {
            runCatching {
                loadRichContentDocument(reference)
            }.onSuccess {
                richContent = it
            }.onFailure {
                richContentError = it.message ?: "加载失败"
            }
            richContentLoading = false
        }
    }

    fun goBackLayer(): Boolean {
        if (previewPictures.isNotEmpty()) {
            closePreview()
            return true
        }

        return when (page) {
            Page.RichContent -> {
                if (richContentStack.isNotEmpty()) {
                    richContentStack.removeAt(richContentStack.lastIndex)
                    return true
                }
                page = richContentParentPage
                richContentStack.clear()
                richContent = null
                richContentLoading = false
                richContentError = null

                if (richContentParentPage == Page.Replies) {
                    requestReplyFocusRestore()
                } else {
                    requestMainFocusRestore()
                }
                true
            }

            Page.Replies -> {
                page = Page.Main
                rootComment = null

                replies.clear()
                replyPage = CommentReplyPage()
                repliesHasNext = true
                repliesError = null
                replyItemFocusRequesters.clear()
                lastReplyFocusedRpid = null

                requestMainFocusRestore()
                true
            }

            Page.Main -> {
                onDismissRequest()
                true
            }
        }
    }

    fun loadComments(reset: Boolean) {
        scope.launch {
            if (commentsLoading) return@launch
            commentsLoading = true
            commentsError = null
            try {
                val pageToUse = if (reset) CommentPage() else commentPage
                val data = commentRepository.getVideoComments(
                    aid = aid,
                    sort = CommentSort.Hot,
                    preferApiType = Prefs.apiType,
                    page = pageToUse
                )
                if (reset) comments.clear()
                val existingRpids = comments.asSequence().map { it.rpid }.toHashSet()
                comments.addAll(data.comments.filterNot { it.rpid in existingRpids })
                commentPage = data.nextPage
                commentsHasNext = data.hasNext
            } catch (e: Throwable) {
                commentsError = e.message ?: "加载失败"
            } finally {
                commentsLoading = false
            }
        }
    }

    fun loadReplies(reset: Boolean) {
        val root = rootComment ?: return
        scope.launch {
            if (repliesLoading) return@launch
            repliesLoading = true
            repliesError = null
            try {
                val pageToUse = if (reset) CommentReplyPage() else replyPage
                val data = commentRepository.getVideoCommentReplies(
                    aid = aid,
                    rootRpid = root.rpid,
                    sort = CommentSort.Hot,
                    preferApiType = Prefs.apiType,
                    page = pageToUse
                )
                rootComment = rootComment ?: data.rootComment
                if (reset) replies.clear()
                val existingRpids = replies.asSequence().map { it.rpid }.toHashSet()
                replies.addAll(data.replies.filterNot { it.rpid in existingRpids })
                replyPage = data.nextPage
                repliesHasNext = data.hasNext
            } catch (e: Throwable) {
                repliesError = e.message ?: "加载失败"
            } finally {
                repliesLoading = false
            }
        }
    }

    // 打开时：先 gate（无评论/网络错误就 toast 并关闭），通过后再初始化 UI 状态
    LaunchedEffect(aid) {
        gatePassed = false

        page = Page.Main
        rootComment = null

        comments.clear()
        commentPage = CommentPage()
        commentsHasNext = true
        commentsLoading = false
        commentsError = null

        replies.clear()
        replyPage = CommentReplyPage()
        repliesHasNext = true
        repliesLoading = false
        repliesError = null
        replyItemFocusRequesters.clear()
        lastReplyFocusedRpid = null

        richContentParentPage = Page.Main
        richContentStack.clear()
        richContent = null
        richContentLoading = false
        richContentError = null
        noteFullTexts.clear()

        closePreview()

        val firstPage = runCatching {
            commentRepository.getVideoComments(
                aid = aid,
                sort = CommentSort.Hot,
                preferApiType = Prefs.apiType,
                page = CommentPage()
            )
        }.getOrElse {
            "网络错误，请稍后重试".toast(context)
            onDismissRequest()
            return@LaunchedEffect
        }

        if (firstPage.comments.isEmpty()) {
            "暂无评论".toast(context)
            onDismissRequest()
            return@LaunchedEffect
        }

        comments.addAll(firstPage.comments)
        commentPage = firstPage.nextPage
        commentsHasNext = firstPage.hasNext

        gatePassed = true

        // 兜底给根容器焦点（等列表 item 创建后再把焦点交给 item）
        delay(40)
        runCatching { rootFocusRequester.requestFocus() }
    }

    // 主列表有数据时：如果没有记录过 lastMainFocusedRpid，就默认记录第一条，并尝试聚焦它
    LaunchedEffect(page, comments.size) {
        if (page == Page.Main && comments.isNotEmpty()) {
            if (lastMainFocusedRpid == null) {
                lastMainFocusedRpid = comments.first().rpid
            }
            requestMainFocusRestore()
        }
    }

    LaunchedEffect(comments.size) {
        comments
            .filter { it.isNoteComment && it.rpid !in noteFullTexts }
            .forEach { comment ->
                runCatching {
                    var cvid = comment.noteCvid
                    if (cvid <= 0L) {
                        val detail = commentRepository.getVideoCommentReplies(
                            aid = aid,
                            rootRpid = comment.rpid,
                            sort = CommentSort.Hot,
                            preferApiType = Prefs.apiType,
                            page = CommentReplyPage()
                        )
                        cvid = detail.rootComment.noteCvid
                    }
                    if (cvid > 0L) {
                        val doc = loadRichContentDocument(RichTextReference.Note(cvid = cvid))
                        noteFullTexts[comment.rpid] = doc.body.plainText
                    }
                }
            }
    }

    // 回复列表有数据后，聚焦第一个回复正文
    LaunchedEffect(page, replies.size) {
        if (page == Page.Replies && replies.isNotEmpty()) {
            if (lastReplyFocusedRpid == null) {
                lastReplyFocusedRpid = replies.first().rpid
            }
            requestReplyFocusRestore()
        }
    }

    LaunchedEffect(replies.size, rootComment?.rpid) {
        val rootRpid = rootComment?.rpid ?: return@LaunchedEffect
        var resolvedRootNoteCvid = rootComment?.noteCvid ?: 0L
        replies
            .filter { it.isNoteComment && it.rpid !in noteFullTexts }
            .forEach { reply ->
                runCatching {
                    var cvid = reply.noteCvid
                    if (cvid <= 0L) {
                        if (resolvedRootNoteCvid <= 0L) {
                            val detail = commentRepository.getVideoCommentReplies(
                                aid = aid,
                                rootRpid = rootRpid,
                                sort = CommentSort.Hot,
                                preferApiType = Prefs.apiType,
                                page = CommentReplyPage()
                            )
                            resolvedRootNoteCvid = detail.rootComment.noteCvid
                        }
                        cvid = resolvedRootNoteCvid
                    }
                    if (cvid > 0L) {
                        val doc = loadRichContentDocument(RichTextReference.Note(cvid = cvid))
                        noteFullTexts[reply.rpid] = doc.body.plainText
                    }
                }
            }
    }

    // 懒加载：接近底部加载下一页
    val commentsNearBottom by remember {
        derivedStateOf {
            val lastVisible = commentsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (comments.size - 3).coerceAtLeast(0)
        }
    }
    LaunchedEffect(commentsNearBottom, commentsHasNext, commentsLoading, page) {
        if (page == Page.Main && commentsNearBottom && commentsHasNext && !commentsLoading && comments.isNotEmpty()) {
            loadComments(reset = false)
        }
    }

    val repliesNearBottom by remember {
        derivedStateOf {
            val lastVisible = repliesListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (replies.size - 3).coerceAtLeast(0)
        }
    }
    LaunchedEffect(repliesNearBottom, repliesHasNext, repliesLoading, page) {
        if (page == Page.Replies && repliesNearBottom && repliesHasNext && !repliesLoading && replies.isNotEmpty()) {
            loadReplies(reset = false)
        }
    }

    if (gatePassed) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            CompositionLocalProvider(
                LocalDensity provides dialogDensity,
                LocalBringIntoViewSpec provides dialogBringIntoViewSpec
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(rootFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Back) {
                                goBackLayer()
                                true
                            } else false
                        },
                    shape = RoundedCornerShape(0.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = C.commentsBackground,
                        contentColor = AppBlack
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (page) {
                            Page.Main -> {
                                if (commentsError != null) {
                                    InlineErrorText(text = commentsError ?: "加载失败")
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = commentsListState,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(
                                        comments,
                                        key = { _, c -> c.rpid }
                                    ) { index, comment ->
                                        // 这个就是“item 入口”FocusRequester，直接挂在评论正文上
                                        val itemFr = mainItemFocusRequesters.getOrPut(comment.rpid) { FocusRequester() }

                                        val prevItemFr = comments
                                            .getOrNull(index - 1)
                                            ?.let { prev -> mainItemFocusRequesters.getOrPut(prev.rpid) { FocusRequester() } }

                                        val nextItemFr = comments
                                            .getOrNull(index + 1)
                                            ?.let { next -> mainItemFocusRequesters.getOrPut(next.rpid) { FocusRequester() } }

                                        LightCommentItem(
                                            modifier = Modifier.onFocusChanged {
                                                if (it.hasFocus) { // hasFocus 这里用于“组内任一子焦点获得焦点”也算这条评论被选中
                                                    lastMainFocusedRpid = comment.rpid
                                                }
                                            },
                                            bodyFocusRequester = itemFr,
                                            previousBodyFocusRequester = prevItemFr,
                                            nextBodyFocusRequester = nextItemFr,
                                            comment = comment,
                                            noteFullText = noteFullTexts[comment.rpid],
                                            showRepliesHint = comment.repliesCount > 0,
                                            onClick = {
                                                if (comment.repliesCount <= 0) return@LightCommentItem

                                                lastMainFocusedRpid = comment.rpid

                                                rootComment = comment
                                                page = Page.Replies

                                                replies.clear()
                                                replyPage = CommentReplyPage()
                                                repliesHasNext = true
                                                repliesError = null
                                                replyItemFocusRequesters.clear()
                                                lastReplyFocusedRpid = null
                                                loadReplies(reset = true)
                                            },
                                            onImageClick = { imgIndex ->
                                                openPreview(comment.pictures, imgIndex)
                                            },
                                            onVideoLinkClick = { link ->
                                                if (Prefs.showVideoInfo) {
                                                    VideoInfoActivity.actionStart(context, link.aid)
                                                } else {
                                                    launchPlayerActivity(
                                                        context = context,
                                                        avid = link.aid,
                                                        cid = link.cid,
                                                        title = link.title,
                                                        partTitle = "",
                                                        played = 0,
                                                        fromSeason = false
                                                    )
                                                }
                                            },
                                            onReferenceClick = { reference ->
                                                openRichContent(reference, Page.Main)
                                            },
                                            onMentionClick = { mid, name ->
                                                UpInfoActivity.actionStart(context, mid = mid, name = name)
                                            }
                                        )
                                    }

                                    item {
                                        BottomStateLight(
                                            loading = commentsLoading,
                                            hasNext = commentsHasNext,
                                            empty = comments.isEmpty(),
                                            emptyText = "暂无评论"
                                        )
                                    }
                                }
                            }

                            Page.Replies -> {
                                val root = rootComment
                                if (root == null) {
                                    Text(
                                        text = "未选择根评论",
                                        color = AppBlack.copy(alpha = 0.70f),
                                        fontSize = 20.sp,
                                        modifier = Modifier.padding(
                                            horizontal = 2.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RootCommentHeader(comment = root)

                                        if (repliesError != null) {
                                            InlineErrorText(text = repliesError ?: "加载失败")
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            state = repliesListState,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            itemsIndexed(
                                                replies,
                                                key = { _, c -> c.rpid }
                                            ) { index, reply ->
                                                val itemFr = replyItemFocusRequesters.getOrPut(reply.rpid) { FocusRequester() }

                                                val prevItemFr = replies
                                                    .getOrNull(index - 1)
                                                    ?.let { prev -> replyItemFocusRequesters.getOrPut(prev.rpid) { FocusRequester() } }

                                                val nextItemFr = replies
                                                    .getOrNull(index + 1)
                                                    ?.let { next -> replyItemFocusRequesters.getOrPut(next.rpid) { FocusRequester() } }

                                                LightCommentItem(
                                                    modifier = Modifier.onFocusChanged {
                                                        if (it.hasFocus) {
                                                            lastReplyFocusedRpid = reply.rpid
                                                        }
                                                    },
                                                    bodyFocusRequester = itemFr,
                                                    previousBodyFocusRequester = prevItemFr,
                                                    nextBodyFocusRequester = nextItemFr,
                                                    comment = reply,
                                                    noteFullText = noteFullTexts[reply.rpid],
                                                    showRepliesHint = false,
                                                    onClick = {},
                                                    onImageClick = { imgIndex ->
                                                        openPreview(reply.pictures, imgIndex)
                                                    },
                                                    onVideoLinkClick = { link ->
                                                        if (Prefs.showVideoInfo) {
                                                            VideoInfoActivity.actionStart(context, link.aid)
                                                        } else {
                                                            launchPlayerActivity(
                                                                context = context,
                                                                avid = link.aid,
                                                                cid = link.cid,
                                                                title = link.title,
                                                                partTitle = "",
                                                                played = 0,
                                                                fromSeason = false
                                                            )
                                                        }
                                                    },
                                                    onReferenceClick = { reference ->
                                                        lastReplyFocusedRpid = reply.rpid
                                                        openRichContent(reference, Page.Replies)
                                                    },
                                                    onMentionClick = { mid, name ->
                                                        UpInfoActivity.actionStart(context, mid = mid, name = name)
                                                    }
                                                )
                                            }

                                            item {
                                                BottomStateLight(
                                                    loading = repliesLoading,
                                                    hasNext = repliesHasNext,
                                                    empty = replies.isEmpty(),
                                                    emptyText = "暂无回复"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Page.RichContent -> {
                                RichContentPage(
                                    document = richContentStack.lastOrNull() ?: richContent,
                                    loading = richContentLoading,
                                    error = richContentError,
                                    onImageClick = { pictures, index ->
                                        openPreview(pictures, index)
                                    },
                                    onVideoLinkClick = { link ->
                                        if (Prefs.showVideoInfo) {
                                            VideoInfoActivity.actionStart(context, link.aid)
                                        } else {
                                            launchPlayerActivity(
                                                context = context,
                                                avid = link.aid,
                                                cid = link.cid,
                                                title = link.title,
                                                partTitle = "",
                                                played = 0,
                                                fromSeason = false
                                            )
                                        }
                                    },
                                    onReferenceClick = { reference ->
                                        scope.launch {
                                            richContentError = null
                                            richContentLoading = true
                                            runCatching {
                                                loadRichContentDocument(reference)
                                            }.onSuccess { doc ->
                                                richContentStack.add(doc)
                                            }.onFailure {
                                                richContentError = it.message ?: "加载失败"
                                            }
                                            richContentLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (previewPictures.isNotEmpty()) {
                    CommentImagePreviewDialog(
                        pictures = previewPictures,
                        currentIndex = previewIndex,
                        onDismissRequest = { closePreview() },
                        onSwitch = { delta -> switchPreview(delta) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RootCommentHeader(comment: Comment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.member.name,
                    color = C.bilibili,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (comment.isPinned) {
                    Spacer(modifier = Modifier.width(8.dp))
                    PinnedBadgeLight()
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = comment.timeDesc,
                    color = AppBlack.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "赞 ${comment.like}",
                    color = AppBlack.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            CommentMessageText(
                content = comment.toRichTextContent(),
                maxLines = 5,
                enableLinkFocus = false,
                interactiveFocusRequesters = emptyList(),
                onVideoLinkClick = null,
                onReferenceClick = null,
                onMentionClick = null
            )

            if (comment.pictures.isNotEmpty()) {
                RootCommentPictures(pictures = comment.pictures)
            }
        }
    }
}

private sealed interface CommentFocusNode {
    data object Body : CommentFocusNode
    data class Inline(val index: Int) : CommentFocusNode
    data class Picture(val index: Int) : CommentFocusNode
}

private fun firstReachableForwardNodeInCurrentItem(
    inlineCount: Int,
    pictureCount: Int
): CommentFocusNode? = when {
    inlineCount > 0 -> CommentFocusNode.Inline(0)
    pictureCount > 0 -> CommentFocusNode.Picture(0)
    else -> null
}

private fun nextNodeFromInline(
    index: Int,
    inlineCount: Int,
    pictureCount: Int
): CommentFocusNode? = when {
    index + 1 < inlineCount -> CommentFocusNode.Inline(index + 1)
    pictureCount > 0 -> CommentFocusNode.Picture(0)
    else -> null
}

private fun prevNodeFromInline(index: Int): CommentFocusNode =
    if (index > 0) CommentFocusNode.Inline(index - 1) else CommentFocusNode.Body

private fun nextNodeFromPicture(index: Int, pictureCount: Int): CommentFocusNode? =
    if (index + 1 < pictureCount) CommentFocusNode.Picture(index + 1) else null

private fun prevNodeFromPicture(index: Int, inlineCount: Int): CommentFocusNode = when {
    index > 0 -> CommentFocusNode.Picture(index - 1)
    inlineCount > 0 -> CommentFocusNode.Inline(inlineCount - 1)
    else -> CommentFocusNode.Body
}

@Composable
private fun LightCommentItem(
    modifier: Modifier = Modifier,
    bodyFocusRequester: FocusRequester,
    previousBodyFocusRequester: FocusRequester? = null,
    nextBodyFocusRequester: FocusRequester? = null,
    comment: Comment,
    noteFullText: String? = null,
    showRepliesHint: Boolean,
    onClick: () -> Unit,
    onImageClick: (Int) -> Unit,
    onVideoLinkClick: (ResolvedVideoLink) -> Unit,
    onReferenceClick: (RichTextReference) -> Unit,
    onMentionClick: (Long, String) -> Unit
) {
    val pictures = comment.pictures
    val displayComment = if (comment.isNoteComment && noteFullText != null) {
        comment.copy(message = noteFullText, messageParts = emptyList())
    } else {
        comment
    }

    val messageContent = remember(
        displayComment.rpid,
        displayComment.messageParts,
        displayComment.message,
        displayComment.attachments
    ) { displayComment.toRichTextContent() }

    val tokens = remember(messageContent) { buildRichTextTokens(messageContent) }

    // 只统计“真的可聚焦”的 inline 数量（回调不为 null 才算）
    val interactiveCount = remember(tokens) {
        countRichTextInteractiveTokens(
            tokens = tokens,
            includeVideoLinks = true,
            includeReferences = true,
            includeMentions = true
        )
    }

    val interactiveFocusRequesters = remember(comment.rpid, interactiveCount) {
        List(interactiveCount) { FocusRequester() }
    }
    val pictureFocusRequesters = remember(comment.rpid, pictures.size) {
        List(pictures.size) { FocusRequester() }
    }

    // 文本区滚动
    val textScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var activeNode by remember(comment.rpid) { mutableStateOf<CommentFocusNode>(CommentFocusNode.Body) }
    var shouldSyncFocus by remember(comment.rpid) { mutableStateOf(false) }
    var skipCurrentItemSync by remember(comment.rpid) { mutableStateOf(false) }

    fun moveToNode(node: CommentFocusNode) {
        activeNode = node
        shouldSyncFocus = true
    }

    fun moveToCurrentBody() {
        activeNode = CommentFocusNode.Body
        shouldSyncFocus = false
        skipCurrentItemSync = false
        runCatching { bodyFocusRequester.requestFocus() }
            .onFailure { bodyFocusRequester.requestFocus(coroutineScope) }
    }

    fun moveToAdjacentBody(targetRequester: FocusRequester?) {
        activeNode = CommentFocusNode.Body
        if (targetRequester != null) {
            skipCurrentItemSync = true
            shouldSyncFocus = true
            targetRequester.requestFocus(coroutineScope)
        } else {
            moveToCurrentBody()
        }
    }

    suspend fun handleBodyNavDown() {
        if (textScrollState.value < textScrollState.maxValue) {
            textScrollState.animateScrollBy(60f)
            return
        }

        val nextNode = firstReachableForwardNodeInCurrentItem(
            inlineCount = interactiveCount,
            pictureCount = pictures.size
        )
        if (nextNode != null) {
            moveToNode(nextNode)
        } else {
            moveToAdjacentBody(nextBodyFocusRequester)
        }
    }

    suspend fun handleBodyNavUp() {
        if (textScrollState.value > 0) {
            textScrollState.animateScrollBy(-60f)
            return
        }
        moveToAdjacentBody(previousBodyFocusRequester)
    }

    fun handleInlineNavDown() {
        val index = (activeNode as? CommentFocusNode.Inline)?.index ?: return
        val nextNode = nextNodeFromInline(
            index = index,
            inlineCount = interactiveCount,
            pictureCount = pictures.size
        )
        if (nextNode != null) {
            moveToNode(nextNode)
        } else {
            moveToAdjacentBody(nextBodyFocusRequester)
        }
    }

    fun handleInlineNavUp() {
        val index = (activeNode as? CommentFocusNode.Inline)?.index ?: return
        moveToNode(prevNodeFromInline(index))
    }

    fun handlePictureNavDown(index: Int) {
        val nextNode = nextNodeFromPicture(index = index, pictureCount = pictures.size)
        if (nextNode != null) {
            moveToNode(nextNode)
        } else {
            moveToAdjacentBody(nextBodyFocusRequester)
        }
    }

    fun handlePictureNavUp(index: Int) {
        moveToNode(prevNodeFromPicture(index = index, inlineCount = interactiveCount))
    }

    LaunchedEffect(activeNode, shouldSyncFocus, skipCurrentItemSync, interactiveCount, pictures.size) {
        if (!shouldSyncFocus) return@LaunchedEffect

        if (skipCurrentItemSync) {
            skipCurrentItemSync = false
            shouldSyncFocus = false
            return@LaunchedEffect
        }

        when (val node = activeNode) {
            CommentFocusNode.Body -> {
                bodyFocusRequester.requestFocus(coroutineScope)
                shouldSyncFocus = false
            }

            is CommentFocusNode.Inline -> {
                val requester = interactiveFocusRequesters.getOrNull(node.index)
                if (requester != null) {
                    requester.requestFocus(coroutineScope)
                    shouldSyncFocus = false
                } else {
                    activeNode = CommentFocusNode.Body
                }
            }

            is CommentFocusNode.Picture -> {
                if (pictureFocusRequesters.isEmpty()) {
                    activeNode = CommentFocusNode.Body
                } else {
                    val targetIndex = if (node.index in pictureFocusRequesters.indices) {
                        node.index
                    } else {
                        pictureFocusRequesters.lastIndex
                    }
                    if (targetIndex != node.index) {
                        activeNode = CommentFocusNode.Picture(targetIndex)
                    } else {
                        pictureFocusRequesters[targetIndex].requestFocus(coroutineScope)
                        shouldSyncFocus = false
                    }
                }
            }
        }
    }

    // ====== item 容器 ======
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var bodyIsFocused by remember(comment.rpid) { mutableStateOf(false) }
        var bodyHasFocus by remember(comment.rpid) { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(bodyFocusRequester)
                .focusProperties {
                    up = previousBodyFocusRequester ?: bodyFocusRequester
                    // down 不再依赖默认焦点系统；我们自己在 onPreviewKeyEvent 里控制
                }
                .onFocusChanged { fs ->
                    bodyIsFocused = fs.isFocused
                    bodyHasFocus = fs.hasFocus
                    if (fs.isFocused) {
                        activeNode = CommentFocusNode.Body
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (!bodyIsFocused || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            coroutineScope.launch { handleBodyNavDown() }
                            true
                        }
                        Key.DirectionUp -> {
                            coroutineScope.launch { handleBodyNavUp() }
                            true
                        }
                        else -> false
                    }
                }
                .border(
                    width = 3.dp,
                    color = if (bodyHasFocus) C.bilibili else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                ),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1f,
                pressedScale = 1f
            ),
            enabled = true,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.06f)),
                    model = comment.member.avatar,
                    contentDescription = null
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = comment.member.name,
                                color = C.bilibili,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (comment.isPinned) {
                                PinnedBadgeLight()
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = comment.timeDesc,
                            color = AppBlack.copy(alpha = 0.70f),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "赞 ${comment.like}",
                            color = AppBlack.copy(alpha = 0.70f),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    CommentMessageText(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(textScrollState),
                        content = messageContent,
                        maxLines = Int.MAX_VALUE,
                        enableLinkFocus = true,
                        interactiveFocusRequesters = interactiveFocusRequesters,
                        onVideoLinkClick = onVideoLinkClick,
                        onReferenceClick = onReferenceClick,
                        onMentionClick = onMentionClick,
                        onInteractiveFocused = { idx ->
                            activeNode = CommentFocusNode.Inline(idx)
                        },
                        onInteractiveNavDown = {
                            handleInlineNavDown()
                        },
                        onInteractiveNavUp = {
                            handleInlineNavUp()
                        }
                    )

                    if (showRepliesHint) {
                        Text(
                            text = "${comment.repliesCount} 条回复 >>",
                            color = AppBlack.copy(alpha = 0.85f),
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        FocusableCommentPictures(
            pictures = pictures,
            keyPrefix = comment.rpid.toString(),
            pictureFocusRequesters = pictureFocusRequesters,
            onImageClick = onImageClick,
            onPictureFocused = { index ->
                activeNode = CommentFocusNode.Picture(index)
            },
            onPictureNavUp = ::handlePictureNavUp,
            onPictureNavDown = ::handlePictureNavDown
        )
    }
}

@Composable
private fun CommentMessageText(
    modifier: Modifier = Modifier,
    content: RichTextContent,
    maxLines: Int,
    enableLinkFocus: Boolean,
    interactiveFocusRequesters: List<FocusRequester>,
    onVideoLinkClick: ((ResolvedVideoLink) -> Unit)?,
    onReferenceClick: ((RichTextReference) -> Unit)?,
    onMentionClick: ((Long, String) -> Unit)?,
    onInteractiveFocused: ((index: Int) -> Unit)? = null,
    onInteractiveNavDown: (() -> Unit)? = null,
    onInteractiveNavUp: (() -> Unit)? = null
) {
    val tokens = remember(content) { buildRichTextTokens(content) }
    val basicFontSize = 24.sp

    RichText(
        modifier = modifier,
        tokens = tokens,
        inlineKeyPrefix = "comment",
        textStyle = TextStyle(
            color = AppBlack,
            fontSize = basicFontSize,
            lineHeight = 29.sp
        ),
        maxLines = maxLines,
        enableInteractiveFocus = enableLinkFocus,
        interactiveFocusRequesters = interactiveFocusRequesters,
        onVideoLinkClick = onVideoLinkClick,
        onReferenceClick = onReferenceClick,
        onMentionClick = onMentionClick,
        onInteractiveFocused = onInteractiveFocused,
        onInteractiveNavDown = onInteractiveNavDown,
        onInteractiveNavUp = onInteractiveNavUp
    )
}

@Composable
private fun RichContentPage(
    document: RichContentDocument?,
    loading: Boolean,
    error: String?,
    onImageClick: (List<Comment.Picture>, Int) -> Unit,
    onVideoLinkClick: (ResolvedVideoLink) -> Unit,
    onReferenceClick: (RichTextReference) -> Unit
) {
    when {
        loading -> {
            Text(
                text = "加载中……",
                color = AppBlack.copy(alpha = 0.70f),
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        error != null -> {
            InlineErrorText(text = error)
        }

        document != null -> {
            val tokens = remember(document.body) {
                buildRichTextTokens(document.body)
            }
            val interactiveCount = remember(tokens) {
                countRichTextInteractiveTokens(
                    tokens = tokens,
                    includeVideoLinks = true,
                    includeReferences = true,
                    includeMentions = false
                )
            }
            val bodyFocusRequester = remember(document.title) { FocusRequester() }
            val interactiveFocusRequesters = remember(document.title, interactiveCount) {
                List(interactiveCount) { FocusRequester() }
            }
            val pictureFocusRequesters = remember(document.title, document.pictures.size) {
                List(document.pictures.size) { FocusRequester() }
            }
            var bodyIsFocused by remember(document.title) { mutableStateOf(false) }
            var bodyHasFocus by remember(document.title) { mutableStateOf(false) }
            val textScrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            var activeNode by remember(document.title) {
                mutableStateOf<CommentFocusNode>(CommentFocusNode.Body)
            }
            var shouldSyncFocus by remember(document.title) { mutableStateOf(false) }

            fun moveToNode(node: CommentFocusNode) {
                activeNode = node
                shouldSyncFocus = true
            }

            fun moveToCurrentBody() {
                activeNode = CommentFocusNode.Body
                shouldSyncFocus = false
                runCatching { bodyFocusRequester.requestFocus() }
                    .onFailure { bodyFocusRequester.requestFocus(coroutineScope) }
            }

            suspend fun handleBodyNavDown() {
                if (textScrollState.value < textScrollState.maxValue) {
                    textScrollState.animateScrollBy(60f)
                    return
                }

                moveToNode(
                    firstReachableForwardNodeInCurrentItem(
                        inlineCount = interactiveCount,
                        pictureCount = document.pictures.size
                    ) ?: return moveToCurrentBody()
                )
            }

            suspend fun handleBodyNavUp() {
                if (textScrollState.value > 0) {
                    textScrollState.animateScrollBy(-60f)
                    return
                }
                moveToCurrentBody()
            }

            fun handleInlineNavDown() {
                val index = (activeNode as? CommentFocusNode.Inline)?.index ?: return
                moveToNode(
                    nextNodeFromInline(
                        index = index,
                        inlineCount = interactiveCount,
                        pictureCount = document.pictures.size
                    ) ?: return moveToCurrentBody()
                )
            }

            fun handleInlineNavUp() {
                val index = (activeNode as? CommentFocusNode.Inline)?.index ?: return
                moveToNode(prevNodeFromInline(index))
            }

            fun handlePictureNavDown(index: Int) {
                moveToNode(
                    nextNodeFromPicture(index = index, pictureCount = document.pictures.size)
                        ?: return moveToCurrentBody()
                )
            }

            fun handlePictureNavUp(index: Int) {
                moveToNode(prevNodeFromPicture(index = index, inlineCount = interactiveCount))
            }

            LaunchedEffect(document.title) {
                shouldSyncFocus = true
            }

            LaunchedEffect(activeNode, shouldSyncFocus, interactiveCount, document.pictures.size) {
                if (!shouldSyncFocus) return@LaunchedEffect

                when (val node = activeNode) {
                    CommentFocusNode.Body -> {
                        bodyFocusRequester.requestFocus(coroutineScope)
                        shouldSyncFocus = false
                    }

                    is CommentFocusNode.Inline -> {
                        val requester = interactiveFocusRequesters.getOrNull(node.index)
                        if (requester != null) {
                            requester.requestFocus(coroutineScope)
                            shouldSyncFocus = false
                        } else {
                            activeNode = CommentFocusNode.Body
                        }
                    }

                    is CommentFocusNode.Picture -> {
                        if (pictureFocusRequesters.isEmpty()) {
                            activeNode = CommentFocusNode.Body
                        } else {
                            val targetIndex = if (node.index in pictureFocusRequesters.indices) {
                                node.index
                            } else {
                                pictureFocusRequesters.lastIndex
                            }
                            if (targetIndex != node.index) {
                                activeNode = CommentFocusNode.Picture(targetIndex)
                            } else {
                                pictureFocusRequesters[targetIndex].requestFocus(coroutineScope)
                                shouldSyncFocus = false
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (document.title.isNotBlank()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = document.title,
                        color = AppBlack,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = if (document.centerTitle) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(bodyFocusRequester)
                        .onFocusChanged { fs ->
                            bodyIsFocused = fs.isFocused
                            bodyHasFocus = fs.hasFocus
                            if (fs.isFocused) {
                                activeNode = CommentFocusNode.Body
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (bodyIsFocused && event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        coroutineScope.launch { handleBodyNavDown() }
                                        return@onPreviewKeyEvent true
                                    }

                                    Key.DirectionUp -> {
                                        coroutineScope.launch { handleBodyNavUp() }
                                        return@onPreviewKeyEvent true
                                    }

                                    else -> Unit
                                }
                            }
                            false
                        }
                        .border(
                            width = 3.dp,
                            color = if (bodyHasFocus) C.bilibili else Color.Transparent,
                            shape = MaterialTheme.shapes.medium
                        ),
                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        pressedContainerColor = Color.Transparent
                    ),
                    scale = ClickableSurfaceDefaults.scale(
                        focusedScale = 1f,
                        pressedScale = 1f
                    ),
                    enabled = true,
                    onClick = {}
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        CommentMessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .verticalScroll(textScrollState),
                            content = document.body,
                            maxLines = Int.MAX_VALUE,
                            enableLinkFocus = true,
                            interactiveFocusRequesters = interactiveFocusRequesters,
                            onVideoLinkClick = onVideoLinkClick,
                            onReferenceClick = onReferenceClick,
                            onMentionClick = null,
                            onInteractiveFocused = { idx ->
                                activeNode = CommentFocusNode.Inline(idx)
                            },
                            onInteractiveNavDown = {
                                handleInlineNavDown()
                            },
                            onInteractiveNavUp = {
                                handleInlineNavUp()
                            }
                        )
                    }
                }

                FocusableCommentPictures(
                    pictures = document.pictures,
                    keyPrefix = "rich-${document.title}",
                    pictureFocusRequesters = pictureFocusRequesters,
                    onImageClick = { index ->
                        onImageClick(document.pictures, index)
                    },
                    onPictureFocused = { index ->
                        activeNode = CommentFocusNode.Picture(index)
                    },
                    onPictureNavUp = ::handlePictureNavUp,
                    onPictureNavDown = ::handlePictureNavDown
                )
            }
        }
    }
}

@Composable
private fun FocusableCommentPictures(
    pictures: List<Comment.Picture>,
    keyPrefix: String,
    pictureFocusRequesters: List<FocusRequester>,
    onImageClick: (Int) -> Unit,
    onPictureFocused: (Int) -> Unit,
    onPictureNavUp: (Int) -> Unit,
    onPictureNavDown: (Int) -> Unit
) {
    if (pictures.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(pictures, key = { index, p -> "$keyPrefix-${p.imgSrc}-$index" }) { index, picture ->
            val fr = pictureFocusRequesters[index]
            var pictureHasFocus by remember(keyPrefix, index) { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .width(184.dp)
                    .height(112.dp)
                    .focusRequester(fr)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> {
                                onPictureNavUp(index)
                                true
                            }

                            Key.DirectionDown -> {
                                onPictureNavDown(index)
                                true
                            }

                            else -> false
                        }
                    }
                    .onFocusChanged {
                        pictureHasFocus = it.isFocused
                        if (it.isFocused) {
                            onPictureFocused(index)
                        }
                    }
                    .border(
                        width = if (pictureHasFocus) 3.dp else 0.dp,
                        color = if (pictureHasFocus) C.bilibili else Color.Transparent,
                        shape = MaterialTheme.shapes.small
                    ),
                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = C.commentsBackground,
                    focusedContainerColor = C.commentsBackground,
                    pressedContainerColor = C.commentsBackground
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1f,
                    pressedScale = 1f
                ),
                enabled = true,
                onClick = { onImageClick(index) }
            ) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(C.commentsBackground),
                    model = picture.imgSrc,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            }
        }
    }
}

@Composable
private fun RootCommentPictures(pictures: List<Comment.Picture>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(pictures, key = { index, p -> "root-${p.imgSrc}-$index" }) { _, picture ->
            Box(
                modifier = Modifier
                    .width(184.dp)
                    .height(112.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(C.commentsBackground)
            ) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(C.commentsBackground),
                    model = picture.imgSrc,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            }
        }
    }
}

@Composable
private fun CommentImagePreviewDialog(
    pictures: List<Comment.Picture>,
    currentIndex: Int,
    onDismissRequest: () -> Unit,
    onSwitch: (delta: Int) -> Unit
) {
    if (pictures.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(0, pictures.lastIndex)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(pictures, safeIndex) {
        delay(20)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.Back -> {
                            onDismissRequest()
                            true
                        }

                        Key.DirectionLeft -> {
                            onSwitch(-1)
                            true
                        }

                        Key.DirectionRight -> {
                            onSwitch(1)
                            true
                        }

                        else -> false
                    }
                },
            shape = RoundedCornerShape(0.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = C.commentsBackground,
                contentColor = AppBlack
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(C.commentsBackground)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = pictures[safeIndex].imgSrc,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
            }
        }
    }
}

@Composable
private fun PinnedBadgeLight() {
    Text(
        text = "置顶",
        color = C.bilibili,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .border(
                width = 1.dp,                    // 边框粗细
                color = C.bilibili,           // 边框颜色与文字一致
                shape = RoundedCornerShape(3.dp) // 图片中的小圆角
            )
            .padding(horizontal = 4.dp, vertical = 1.dp) // 文字与边框之间的间距
    )
}

@Composable
private fun InlineErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontSize = 20.sp,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    )
}

@Composable
private fun BottomStateLight(
    loading: Boolean,
    hasNext: Boolean,
    empty: Boolean,
    emptyText: String
) {
    val t = when {
        loading -> "加载中……"
        empty -> emptyText
        hasNext -> "继续下滑加载更多……"
        else -> "没有了"
    }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        text = t,
        color = AppBlack.copy(alpha = 0.60f),
        fontSize = 18.sp
    )
}
