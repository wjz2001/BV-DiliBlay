package dev.aaa1115910.bv.component.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
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
import androidx.tv.material3.SurfaceDefaults
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
import dev.aaa1115910.bv.wjzfocus.WjzDialogFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.component.richtext.RichText
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.buildRichTextTokens
import dev.aaa1115910.bv.util.countRichTextInteractiveTokens
import dev.aaa1115910.bv.util.loadRichContentDocument
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.launchPlayerActivity
import dev.aaa1115910.bv.util.RichContentDocument
import dev.aaa1115910.bv.util.ResolvedVideoLink
import dev.aaa1115910.bv.util.isConfirmKey
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.ceil

private enum class Page { Main, Replies, RichContent }

private val CommentsDialogScopeId = WjzFocusScopeId("comments/dialog")
private const val CommentsDialogFocusComponentId = "comments-dialog"
private const val CommentsDialogRootKey = "comments/dialog/root"
private const val CommentsDialogRootLocalId = "root"
private fun mainCommentFocusKey(rpid: Long) = "comments/dialog/main/$rpid"
private fun replyCommentFocusKey(rpid: Long) = "comments/dialog/reply/$rpid"
private fun commentInlineFocusKey(rpid: Long, index: Int) =
    "comments/dialog/inline/comment/$rpid/$index"
private fun richInlineFocusKey(stableDocKey: String, index: Int) =
    "comments/dialog/inline/rich/$stableDocKey/$index"
private fun commentPictureFocusKey(keyPrefix: String, index: Int) =
    "comments/dialog/picture/$keyPrefix/$index"
private fun richContentStableDocKey(document: RichContentDocument): String =
    "${document.title.hashCode()}-${document.body.plainText.hashCode()}"

private fun commentsDialogLocalFocusId(key: String): String =
    key.removePrefix("${CommentsDialogScopeId.value}/")

private fun commentsDialogEntryLocalId(nodeId: String): String =
    nodeId.replace('/', ':')

private fun commentsDialogEntryId(nodeId: String): WjzFocusEntryId =
    WjzFocusEntryId("$CommentsDialogFocusComponentId/${commentsDialogEntryLocalId(nodeId)}")

private fun requestDialogNodeFocus(
    coordinator: WjzFocusCoordinator?,
    nodeId: String
): Boolean {
    coordinator ?: return false
    coordinator.switchLayer(WjzFocusLayer.Dialog)
    return coordinator.requestEntryFocus(commentsDialogEntryId(nodeId))
}

private fun requestDialogFocus(
    coordinator: WjzFocusCoordinator?,
    key: String
): Boolean {
    val nodeId = if (key == CommentsDialogRootKey) {
        CommentsDialogRootLocalId
    } else {
        key
    }
    return requestDialogNodeFocus(coordinator, nodeId)
}
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

    // --- 回复页 ---
    var rootComment by remember { mutableStateOf<Comment?>(null) }
    val replies = remember { mutableStateListOf<Comment>() }
    var replyPage by remember { mutableStateOf(CommentReplyPage()) }
    var repliesHasNext by remember { mutableStateOf(true) }
    var repliesLoading by remember { mutableStateOf(false) }
    var repliesError by remember { mutableStateOf<String?>(null) }
    val repliesListState = rememberLazyListState()

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

    var pendingFocusRestoreKey by remember { mutableStateOf<String?>(null) }

    fun requestMainFocusRestore() {
        val target = lastMainFocusedRpid
        val targetRpid = target?.takeIf { rpid -> comments.any { it.rpid == rpid } }
            ?: comments.firstOrNull()?.rpid
        pendingFocusRestoreKey = targetRpid?.let(::mainCommentFocusKey)
            ?: CommentsDialogRootKey
    }

    fun requestReplyFocusRestore() {
        val target = lastReplyFocusedRpid
        val targetRpid = target?.takeIf { rpid -> replies.any { it.rpid == rpid } }
            ?: replies.firstOrNull()?.rpid
        pendingFocusRestoreKey = targetRpid?.let(::replyCommentFocusKey)
            ?: CommentsDialogRootKey
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
        pendingFocusRestoreKey = CommentsDialogRootKey
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
            WjzDialogFocusHost(dialogScopeId = CommentsDialogScopeId) {
                val dialogFocusCoordinator = LocalWjzFocusCoordinator.current
                val restoreKey = pendingFocusRestoreKey

                LaunchedEffect(dialogFocusCoordinator, restoreKey) {
                    val coordinator = dialogFocusCoordinator ?: return@LaunchedEffect
                    val key = restoreKey ?: return@LaunchedEffect
                    coordinator.switchLayer(WjzFocusLayer.Dialog)
                    val restored = requestDialogNodeFocus(
                        coordinator = coordinator,
                        nodeId = commentsDialogLocalFocusId(key)
                    )
                    if (!restored && key != CommentsDialogRootKey) {
                        requestDialogNodeFocus(coordinator, CommentsDialogRootLocalId)
                    }
                    pendingFocusRestoreKey = null
                }

                WjzFocusEntrySurface(
                    componentId = CommentsDialogFocusComponentId,
                    default = {
                        defaultEntry(
                            nodeId = WjzFocusNodeId(CommentsDialogRootLocalId),
                            layer = WjzFocusLayer.Dialog,
                            scopeId = CommentsDialogScopeId
                        )
                    },
                    entries = {
                    fun registerEntry(nodeId: String, targetNodeId: String = nodeId) {
                        entry(commentsDialogEntryLocalId(nodeId)) move defaultEntry(
                            nodeId = WjzFocusNodeId(targetNodeId),
                            layer = WjzFocusLayer.Dialog,
                            scopeId = CommentsDialogScopeId
                        )
                    }

                    registerEntry(CommentsDialogRootLocalId)

                    when (page) {
                        Page.Main -> {
                            comments.forEach { comment ->
                                registerEntry(
                                    commentsDialogLocalFocusId(mainCommentFocusKey(comment.rpid))
                                )

                                val interactiveCount = countRichTextInteractiveTokens(
                                    tokens = buildRichTextTokens(comment.toRichTextContent()),
                                    includeVideoLinks = true,
                                    includeReferences = true,
                                    includeMentions = true
                                )
                                repeat(interactiveCount) { index ->
                                    registerEntry(commentInlineFocusKey(comment.rpid, index))
                                }

                                comment.pictures.forEachIndexed { index, _ ->
                                    registerEntry(commentPictureFocusKey(comment.rpid.toString(), index))
                                }
                            }
                        }

                        Page.Replies -> {
                            replies.forEach { reply ->
                                registerEntry(
                                    commentsDialogLocalFocusId(replyCommentFocusKey(reply.rpid))
                                )

                                val interactiveCount = countRichTextInteractiveTokens(
                                    tokens = buildRichTextTokens(reply.toRichTextContent()),
                                    includeVideoLinks = true,
                                    includeReferences = true,
                                    includeMentions = true
                                )
                                repeat(interactiveCount) { index ->
                                    registerEntry(commentInlineFocusKey(reply.rpid, index))
                                }

                                reply.pictures.forEachIndexed { index, _ ->
                                    registerEntry(commentPictureFocusKey(reply.rpid.toString(), index))
                                }
                            }
                        }

                        Page.RichContent -> {
                            val document = richContentStack.lastOrNull() ?: richContent
                            if (document != null) {
                                val stableDocKey = richContentStableDocKey(document)
                                val interactiveCount = countRichTextInteractiveTokens(
                                    tokens = buildRichTextTokens(document.body),
                                    includeVideoLinks = true,
                                    includeReferences = true,
                                    includeMentions = false
                                )
                                repeat(interactiveCount) { index ->
                                    registerEntry(richInlineFocusKey(stableDocKey, index))
                                }

                                document.pictures.forEachIndexed { index, _ ->
                                    registerEntry(commentPictureFocusKey("rich-$stableDocKey", index))
                                }
                            }
                        }
                    }
                    }
                )

                CompositionLocalProvider(
                    LocalDensity provides dialogDensity,
                    LocalBringIntoViewSpec provides dialogBringIntoViewSpec
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .wjzFocusExits(
                                id = "root",
                                layer = WjzFocusLayer.Dialog
                            )
                            .onKeyEvent {
                                if (it.isKeyDown() && it.key == Key.Back) {
                                    goBackLayer()
                                    true
                                } else false
                            },
                        shape = RoundedCornerShape(0.dp),
                        colors = SurfaceDefaults.colors(
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
                                        val prevItemKey = comments
                                            .getOrNull(index - 1)
                                            ?.let { prev -> mainCommentFocusKey(prev.rpid) }

                                        val nextItemKey = comments
                                            .getOrNull(index + 1)
                                            ?.let { next -> mainCommentFocusKey(next.rpid) }

                                        LightCommentItem(
                                            modifier = Modifier,
                                            onItemFocusChanged = { hasFocus ->
                                                if (hasFocus) {
                                                    lastMainFocusedRpid = comment.rpid
                                                }
                                            },
                                            bodyNodeKey = mainCommentFocusKey(comment.rpid),
                                            previousBodyKey = prevItemKey,
                                            nextBodyKey = nextItemKey,
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
                                                        played = 0L,
                                                        source = VideoSource.Ugc
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
                                                val prevItemKey = replies
                                                    .getOrNull(index - 1)
                                                    ?.let { prev -> replyCommentFocusKey(prev.rpid) }

                                                val nextItemKey = replies
                                                    .getOrNull(index + 1)
                                                    ?.let { next -> replyCommentFocusKey(next.rpid) }

                                                LightCommentItem(
                                                    modifier = Modifier,
                                                    onItemFocusChanged = { hasFocus ->
                                                        if (hasFocus) {
                                                            lastReplyFocusedRpid = reply.rpid
                                                        }
                                                    },
                                                    bodyNodeKey = replyCommentFocusKey(reply.rpid),
                                                    previousBodyKey = prevItemKey,
                                                    nextBodyKey = nextItemKey,
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
                                                                played = 0L,
                                                                source = VideoSource.Ugc
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
                                                played = 0L,
                                                source = VideoSource.Ugc
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
    bodyNodeKey: String,
    previousBodyKey: String? = null,
    nextBodyKey: String? = null,
    comment: Comment,
    noteFullText: String? = null,
    showRepliesHint: Boolean,
    onClick: () -> Unit,
    onImageClick: (Int) -> Unit,
    onVideoLinkClick: (ResolvedVideoLink) -> Unit,
    onReferenceClick: (RichTextReference) -> Unit,
    onMentionClick: (Long, String) -> Unit,
    onItemFocusChanged: (Boolean) -> Unit = {}
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

    val inlineFocusKeys = remember(comment.rpid, interactiveCount) {
        List(interactiveCount) { index -> commentInlineFocusKey(comment.rpid, index) }
    }

    // 文本区滚动
    val textScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var activeNode by remember(comment.rpid) { mutableStateOf<CommentFocusNode>(CommentFocusNode.Body) }
    var shouldSyncFocus by remember(comment.rpid) { mutableStateOf(false) }
    var skipCurrentItemSync by remember(comment.rpid) { mutableStateOf(false) }
    val focusCoordinator = LocalWjzFocusCoordinator.current

    fun moveToNode(node: CommentFocusNode) {
        activeNode = node
        shouldSyncFocus = true
    }

    fun moveToCurrentBody() {
        activeNode = CommentFocusNode.Body
        shouldSyncFocus = false
        skipCurrentItemSync = false
        requestDialogNodeFocus(focusCoordinator, commentsDialogLocalFocusId(bodyNodeKey))
    }

    fun moveToAdjacentBody(targetNodeKey: String?) {
        activeNode = CommentFocusNode.Body
        if (targetNodeKey != null) {
            skipCurrentItemSync = true
            shouldSyncFocus = true
            requestDialogNodeFocus(focusCoordinator, commentsDialogLocalFocusId(targetNodeKey))
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
            moveToAdjacentBody(nextBodyKey)
        }
    }

    suspend fun handleBodyNavUp() {
        if (textScrollState.value > 0) {
            textScrollState.animateScrollBy(-60f)
            return
        }
        moveToAdjacentBody(previousBodyKey)
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
            moveToAdjacentBody(nextBodyKey)
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
            moveToAdjacentBody(nextBodyKey)
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
                requestDialogNodeFocus(focusCoordinator, commentsDialogLocalFocusId(bodyNodeKey))
                shouldSyncFocus = false
            }

            is CommentFocusNode.Inline -> {
                if (node.index in inlineFocusKeys.indices) {
                    requestDialogFocus(focusCoordinator, inlineFocusKeys[node.index])
                    shouldSyncFocus = false
                } else {
                    activeNode = CommentFocusNode.Body
                }
            }

            is CommentFocusNode.Picture -> {
                if (pictures.isEmpty()) {
                    activeNode = CommentFocusNode.Body
                } else {
                    val targetIndex = if (node.index in pictures.indices) {
                        node.index
                    } else {
                        pictures.lastIndex
                    }
                    if (targetIndex != node.index) {
                        activeNode = CommentFocusNode.Picture(targetIndex)
                    } else {
                        requestDialogNodeFocus(
                            coordinator = focusCoordinator,
                            nodeId = commentPictureFocusKey(comment.rpid.toString(), targetIndex)
                        )
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
                .wjzFocusExits(
                    id = commentsDialogLocalFocusId(bodyNodeKey),
                    layer = WjzFocusLayer.Dialog
                ) { hasFocus ->
                    bodyIsFocused = hasFocus
                    bodyHasFocus = hasFocus
                    onItemFocusChanged(hasFocus)
                    if (hasFocus) {
                        activeNode = CommentFocusNode.Body
                    }
                }
                .onKeyEvent { event ->
                    if (!bodyIsFocused || event.type != KeyEventType.KeyDown) return@onKeyEvent false
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
                        interactiveNodeKey = { index -> commentInlineFocusKey(comment.rpid, index) },
                        onVideoLinkClick = onVideoLinkClick,
                        onReferenceClick = onReferenceClick,
                        onMentionClick = onMentionClick,
                        onInteractiveFocused = { idx ->
                            onItemFocusChanged(true)
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
            onImageClick = onImageClick,
            onPictureFocused = { index ->
                onItemFocusChanged(true)
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
    interactiveNodeKey: ((index: Int) -> String)? = null,
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
        interactiveFocusEnabled = enableLinkFocus,
        interactiveNodeKey = interactiveNodeKey,
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
            val stableDocKey = remember(document) {
                richContentStableDocKey(document)
            }
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
            val inlineFocusKeys = remember(stableDocKey, interactiveCount) {
                List(interactiveCount) { index -> richInlineFocusKey(stableDocKey, index) }
            }
            var bodyIsFocused by remember(document.title) { mutableStateOf(false) }
            var bodyHasFocus by remember(document.title) { mutableStateOf(false) }
            val textScrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            var activeNode by remember(document.title) {
                mutableStateOf<CommentFocusNode>(CommentFocusNode.Body)
            }
            var shouldSyncFocus by remember(document.title) { mutableStateOf(false) }
            val focusCoordinator = LocalWjzFocusCoordinator.current

            fun moveToNode(node: CommentFocusNode) {
                activeNode = node
                shouldSyncFocus = true
            }

            fun moveToCurrentBody() {
                activeNode = CommentFocusNode.Body
                shouldSyncFocus = false
                requestDialogFocus(focusCoordinator, CommentsDialogRootKey)
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
                        requestDialogFocus(focusCoordinator, CommentsDialogRootKey)
                        shouldSyncFocus = false
                    }

                    is CommentFocusNode.Inline -> {
                        if (node.index in inlineFocusKeys.indices) {
                            requestDialogFocus(focusCoordinator, inlineFocusKeys[node.index])
                            shouldSyncFocus = false
                        } else {
                            activeNode = CommentFocusNode.Body
                        }
                    }

                    is CommentFocusNode.Picture -> {
                        if (document.pictures.isEmpty()) {
                            activeNode = CommentFocusNode.Body
                        } else {
                            val targetIndex = if (node.index in document.pictures.indices) {
                                node.index
                            } else {
                                document.pictures.lastIndex
                            }
                            if (targetIndex != node.index) {
                                activeNode = CommentFocusNode.Picture(targetIndex)
                            } else {
                                requestDialogFocus(
                                    focusCoordinator,
                                    commentPictureFocusKey("rich-$stableDocKey", targetIndex)
                                )
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
                        .wjzFocusExits(
                            id = CommentsDialogRootLocalId,
                            layer = WjzFocusLayer.Dialog,
                            onFocusChanged = { hasFocus ->
                                bodyIsFocused = hasFocus
                                bodyHasFocus = hasFocus
                                if (hasFocus) {
                                    activeNode = CommentFocusNode.Body
                                }
                            }
                        )
                        .onKeyEvent { event ->
                            if (bodyIsFocused && event.isKeyDown()) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        coroutineScope.launch { handleBodyNavDown() }
                                        return@onKeyEvent true
                                    }

                                    Key.DirectionUp -> {
                                        coroutineScope.launch { handleBodyNavUp() }
                                        return@onKeyEvent true
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
                            interactiveNodeKey = { index -> richInlineFocusKey(stableDocKey, index) },
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
                    keyPrefix = "rich-$stableDocKey",
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
            var pictureHasFocus by remember(keyPrefix, index) { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .width(184.dp)
                    .height(112.dp)
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
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
                    .wjzFocusExits(
                        id = commentPictureFocusKey(keyPrefix, index),
                        layer = WjzFocusLayer.Dialog,
                        onFocusChanged = {
                            pictureHasFocus = it
                            if (it) {
                                onPictureFocused(index)
                            }
                        }
                    )
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stepPx = with(density) { 60.dp.toPx() }
    val imageScrollState = rememberScrollState()

    var isWidthFitMode by remember { mutableStateOf(false) }
    val picture = pictures[safeIndex]
    var resolvedW by remember(picture.imgSrc) { mutableIntStateOf(0) }
    var resolvedH by remember(picture.imgSrc) { mutableIntStateOf(0) }
    val canWidthFit = resolvedW > 0 && resolvedH > 0
    val widthFitEnabled = isWidthFitMode && canWidthFit

    // 切图 / 切换模式：偏移归零
    LaunchedEffect(safeIndex, isWidthFitMode) {
        imageScrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        WjzDialogFocusHost {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = C.commentsBackground,
                    contentColor = AppBlack
                )
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(C.commentsBackground)
                        .clipToBounds()
                        .wjzFocusExits(
                            id = "comments/dialog/image-preview",
                            layer = WjzFocusLayer.Dialog
                        )
                        .onKeyEvent { e ->
                            when {
                                e.key == Key.Back -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    onDismissRequest()
                                    return@onKeyEvent true
                                }

                                e.isConfirmKey() -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    isWidthFitMode = !isWidthFitMode
                                    return@onKeyEvent true
                                }

                                e.key == Key.DirectionLeft -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    onSwitch(-1)
                                    return@onKeyEvent true
                                }

                                e.key == Key.DirectionRight -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    onSwitch(1)
                                    return@onKeyEvent true
                                }

                                e.key == Key.DirectionUp -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    if (widthFitEnabled) {
                                        coroutineScope.launch {
                                            imageScrollState.animateScrollBy(-stepPx)
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                e.key == Key.DirectionDown -> {
                                    if (e.isKeyUp()) return@onKeyEvent true
                                    if (widthFitEnabled) {
                                        coroutineScope.launch {
                                            imageScrollState.animateScrollBy(stepPx)
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                else -> false
                            }
                        }
                ) {
                    val viewportWidthPx = constraints.maxWidth
                    val viewportHeightPx = constraints.maxHeight
                    val widthFitHeightPx = if (canWidthFit) {
                        ceil((viewportWidthPx.toFloat() * resolvedH.toFloat()) / resolvedW.toFloat()).toInt()
                    } else {
                        viewportHeightPx
                    }
                    val widthFitHeightDp = with(density) { widthFitHeightPx.toDp() }

                    val imageModifier =
                        if (widthFitEnabled) {
                            Modifier
                                .fillMaxWidth()
                                .requiredHeight(widthFitHeightDp)
                        } else {
                            Modifier.fillMaxSize()
                        }

                    if (widthFitEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(imageScrollState)
                        ) {
                            AsyncImage(
                                modifier = imageModifier,
                                model = picture.imgSrc,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                alignment = Alignment.TopCenter,
                                onSuccess = { success ->
                                    val d = success.result.drawable
                                    val w = d.intrinsicWidth
                                    val h = d.intrinsicHeight
                                    if (w > 0) resolvedW = w
                                    if (h > 0) resolvedH = h
                                }
                            )
                        }
                    } else {
                        AsyncImage(
                            modifier = imageModifier,
                            model = picture.imgSrc,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            onSuccess = { success ->
                                val d = success.result.drawable
                                val w = d.intrinsicWidth
                                val h = d.intrinsicHeight
                                if (w > 0) resolvedW = w
                                if (h > 0) resolvedH = h
                            }
                        )
                    }
                }
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
