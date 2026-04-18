package dev.aaa1115910.bv.component.richtext

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Default
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.richtext.RichTextReference
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.ResolvedVideoLink
import dev.aaa1115910.bv.util.RichTextToken
import dev.aaa1115910.bv.util.VideoLinkToken
import dev.aaa1115910.bv.util.resolveVideoLink
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun RichText(
    modifier: Modifier = Modifier,
    tokens: List<RichTextToken>,
    inlineKeyPrefix: String,
    textStyle: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    enableInteractiveFocus: Boolean,
    interactiveFocusRequesters: List<FocusRequester>,
    onVideoLinkClick: ((ResolvedVideoLink) -> Unit)?,
    onReferenceClick: ((RichTextReference) -> Unit)?,
    onMentionClick: ((Long, String) -> Unit)?,

    // 给父级做“滚动遇到内链停靠”的回调（都有默认值，不影响旧调用）
    onInteractivePositioned: ((index: Int, rectInWindow: Rect) -> Unit)? = null,
    onInteractiveFocused: ((index: Int) -> Unit)? = null,
    onInteractiveNavDown: (() -> Unit)? = null,
    onInteractiveNavUp: (() -> Unit)? = null
) {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    var emoteIndex = 0

    // inlineIndex：保证 InlineTextContent key 唯一（不管可不可聚焦都递增）
    var inlineIndex = 0

    // focusableIndex：只给“真的可聚焦 inline”分配 requester 索引
    var focusableIndex = 0

    val accentColor = C.mentionAndLink
    val fontSize = textStyle.fontSize
    val baseTextColor = textStyle.color

    val text = buildAnnotatedString {
        tokens.forEach { token ->
            when (token) {
                is RichTextToken.Text -> append(token.text)

                is RichTextToken.Mention -> {
                    val clickable = onMentionClick != null
                    val focusEnabled = enableInteractiveFocus && clickable

                    if (focusEnabled) {
                        val id = "${inlineKeyPrefix}_mention_$inlineIndex"
                        inlineIndex += 1

                        val idx = focusableIndex
                        val fr = interactiveFocusRequesters.getOrNull(idx)

                        appendInlineContent(id, "@${token.name}")
                        inlineContent[id] = InlineTextContent(
                            Placeholder(
                                width = (token.name.length.coerceIn(1, 20) + 2).em,
                                height = 1.6.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            MentionInlineItem(
                                mention = token,
                                enableFocus = true,
                                focusRequester = fr,
                                fontSize = fontSize,
                                accentColor = accentColor,
                                onMentionClick = onMentionClick,

                                index = idx,
                                onPositioned = onInteractivePositioned,
                                onFocused = onInteractiveFocused,
                                onNavDown = onInteractiveNavDown,
                                onNavUp = onInteractiveNavUp
                            )
                        }

                        focusableIndex += 1
                    } else {
                        // 不可聚焦：普通富文本样式
                        withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Medium)) {
                            append("@${token.name}")
                        }
                    }
                }

                is RichTextToken.Emote -> {
                    val id = "${inlineKeyPrefix}_emote_$emoteIndex"
                    appendInlineContent(id, token.alt.ifBlank { token.code })
                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = 1.1.em,
                            height = 1.1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = token.url,
                            contentDescription = token.alt,
                            contentScale = ContentScale.Fit
                        )
                    }
                    emoteIndex += 1
                }

                is RichTextToken.VideoLink -> {
                    val clickable = onVideoLinkClick != null
                    val focusEnabled = enableInteractiveFocus && clickable

                    val id = "${inlineKeyPrefix}_video_$inlineIndex"
                    inlineIndex += 1

                    val placeholderText = token.data.videoId.ifBlank { token.data.rawUrl }.ifBlank { token.data.cleanedUrl }
                    val placeholderWidth = estimateInlineWidthEm(
                        text = placeholderText,
                        minEm = 8f,
                        maxEm = 20f,
                        paddingEm = 2.5f
                    )

                    appendInlineContent(id, token.data.videoId.ifBlank { token.data.rawUrl })

                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = placeholderWidth,
                            height = 1.6.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        if (!focusEnabled) {
                            VideoLinkInlineItem(
                                token = token.data,
                                enableFocus = false,
                                focusRequester = null,
                                fontSize = fontSize,
                                accentColor = accentColor,
                                baseTextColor = baseTextColor,
                                onVideoLinkClick = onVideoLinkClick
                            )
                        } else {
                            val idx = focusableIndex
                            val fr = interactiveFocusRequesters.getOrNull(idx)

                            VideoLinkInlineItem(
                                token = token.data,
                                enableFocus = true,
                                focusRequester = fr,
                                fontSize = fontSize,
                                accentColor = accentColor,
                                baseTextColor = baseTextColor,
                                onVideoLinkClick = onVideoLinkClick,

                                index = idx,
                                onPositioned = onInteractivePositioned,
                                onFocused = onInteractiveFocused,
                                onNavDown = onInteractiveNavDown,
                                onNavUp = onInteractiveNavUp
                            )

                            focusableIndex += 1
                        }
                    }
                }

                is RichTextToken.Reference -> {
                    val clickable = onReferenceClick != null
                    val focusEnabled = enableInteractiveFocus && clickable
                    val id = "${inlineKeyPrefix}_reference_$inlineIndex"
                    inlineIndex += 1
                    val displayText = token.reference.displayText
                    val placeholderWidth = estimateInlineWidthEm(
                        text = displayText,
                        minEm = 6f,
                        maxEm = 22f,
                        paddingEm = 2.8f
                    )
                    appendInlineContent(id, token.reference.displayText)
                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = placeholderWidth,
                            height = 1.6.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        if (!focusEnabled) {
                            ReferenceInlineItem(
                                reference = token.reference,
                                enableFocus = false,
                                focusRequester = null,
                                fontSize = fontSize,
                                accentColor = accentColor,
                                onReferenceClick = onReferenceClick
                            )
                        } else {
                            val idx = focusableIndex
                            val fr = interactiveFocusRequesters.getOrNull(idx)

                            ReferenceInlineItem(
                                reference = token.reference,
                                enableFocus = true,
                                focusRequester = fr,
                                fontSize = fontSize,
                                accentColor = accentColor,
                                onReferenceClick = onReferenceClick,

                                index = idx,
                                onPositioned = onInteractivePositioned,
                                onFocused = onInteractiveFocused,
                                onNavDown = onInteractiveNavDown,
                                onNavUp = onInteractiveNavUp
                            )

                            focusableIndex += 1
                        }
                    }
                }
            }
        }
    }

    BasicText(
        modifier = modifier,
        text = text,
        inlineContent = inlineContent,
        style = textStyle,
        maxLines = maxLines
    )
}

private fun estimateInlineWidthEm(
    text: String,
    minEm: Float,
    maxEm: Float,
    paddingEm: Float = 2f
): TextUnit {
    val s = text.trim()
    if (s.isEmpty()) return minEm.em

    // 简易视觉长度：CJK/全角按 2，其它按 1（再做上限）
    val visualLen = s.sumOf { ch ->
        if (ch.code in 0x2E80..0x9FFF) 2 else 1
    }.coerceAtMost(40)

    val estimated = paddingEm + visualLen * 0.75f
    return estimated.coerceIn(minEm, maxEm).em
}

@Composable
private fun MentionInlineItem(
    mention: RichTextToken.Mention,
    enableFocus: Boolean,
    focusRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    onMentionClick: ((Long, String) -> Unit)?,

    // 给父级做定位/导航
    index: Int = -1,
    onPositioned: ((Int, Rect) -> Unit)? = null,
    onFocused: ((Int) -> Unit)? = null,
    onNavDown: (() -> Unit)? = null,
    onNavUp: (() -> Unit)? = null
) {
    var focused by remember(mention.mid, mention.name) { mutableStateOf(false) }

    if (!enableFocus) {
        Text(
            text = "@${mention.name}",
            color = accentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    Surface(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                if (index >= 0) onPositioned?.invoke(index, coords.boundsInWindow())
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> {
                        if (onNavDown != null) {
                            onNavDown()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (onNavUp != null) {
                            onNavUp()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester ?: Default)
            .onFocusChanged { fs ->
                focused = fs.hasFocus
                if (fs.isFocused && index >= 0) onFocused?.invoke(index)
            }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) accentColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
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
        onClick = { onMentionClick?.invoke(mention.mid, mention.name) }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 4.dp),
            text = "@${mention.name}",
            color = accentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoLinkInlineItem(
    token: VideoLinkToken,
    enableFocus: Boolean,
    focusRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    baseTextColor: Color,
    onVideoLinkClick: ((ResolvedVideoLink) -> Unit)?,

    // 给父级做定位/导航
    index: Int = -1,
    onPositioned: ((Int, Rect) -> Unit)? = null,
    onFocused: ((Int) -> Unit)? = null,
    onNavDown: (() -> Unit)? = null,
    onNavUp: (() -> Unit)? = null
) {
    var resolved by remember(token.cleanedUrl) { mutableStateOf<ResolvedVideoLink?>(null) }
    var loaded by remember(token.cleanedUrl) { mutableStateOf(false) }
    var focused by remember(token.cleanedUrl) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token.cleanedUrl) {
        if (loaded) return@LaunchedEffect
        loaded = true
        resolved = resolveVideoLink(token)
    }

    val snapshot = resolved
    val showLinkStyle = !loaded || snapshot != null
    val fallbackAsPlainText = loaded && snapshot == null
    val title = when {
        snapshot != null -> snapshot.title
        loaded -> token.rawUrl
        else -> token.videoId
    }

    if (fallbackAsPlainText && !enableFocus) {
        Text(
            text = token.rawUrl,
            color = baseTextColor,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    if (!enableFocus) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showLinkStyle) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = accentColor
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = if (showLinkStyle) accentColor else baseTextColor,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                if (index >= 0) onPositioned?.invoke(index, coords.boundsInWindow())
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> {
                        if (onNavDown != null) {
                            onNavDown()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (onNavUp != null) {
                            onNavUp()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester ?: Default)
            .onFocusChanged { fs ->
                focused = fs.hasFocus
                if (fs.isFocused && index >= 0) onFocused?.invoke(index)
            }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused && showLinkStyle) accentColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
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
        onClick = {
            scope.launch {
                val link = resolved ?: resolveVideoLink(token)
                if (link != null) {
                    resolved = link
                    onVideoLinkClick?.invoke(link)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showLinkStyle) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    tint = accentColor
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                color = if (showLinkStyle) accentColor else baseTextColor,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReferenceInlineItem(
    reference: RichTextReference,
    enableFocus: Boolean,
    focusRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    onReferenceClick: ((RichTextReference) -> Unit)?,

    // 给父级做定位/导航
    index: Int = -1,
    onPositioned: ((Int, Rect) -> Unit)? = null,
    onFocused: ((Int) -> Unit)? = null,
    onNavDown: (() -> Unit)? = null,
    onNavUp: (() -> Unit)? = null
) {
    var focused by remember(reference) { mutableStateOf(false) }
    val icon = when (reference) {
        is RichTextReference.Article -> Icons.Rounded.Description
        is RichTextReference.Note -> Icons.Rounded.NoteAlt
    }
    var resolvedTitle by remember(reference) { mutableStateOf<String?>(null) }

    if (reference is RichTextReference.Article && reference.cvid == 0L && "/opus/" in reference.url) {
        LaunchedEffect(reference.url) {
            runCatching {
                val opusId = Regex("""/opus/(\d+)""").find(reference.url)?.groupValues?.getOrNull(1)
                if (!opusId.isNullOrBlank()) {
                    val data = BiliHttpApi.getOpusDetail(opusId).getResponseData()
                    val item = data["item"] as? JsonObject
                    val basic = item?.get("basic") as? JsonObject
                    resolvedTitle = basic?.get("title")?.jsonPrimitive?.contentOrNull
                }
            }
        }
    }

    val displayText = resolvedTitle ?: reference.displayText

    if (!enableFocus) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor
            )
            Text(
                modifier = Modifier.weight(1f),
                text = displayText,
                color = accentColor,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                if (index >= 0) onPositioned?.invoke(index, coords.boundsInWindow())
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> {
                        if (onNavDown != null) {
                            onNavDown()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (onNavUp != null) {
                            onNavUp()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester ?: Default)
            .onFocusChanged { fs ->
                focused = fs.hasFocus
                if (fs.isFocused && index >= 0) onFocused?.invoke(index)
            }
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) accentColor else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
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
        onClick = { onReferenceClick?.invoke(reference) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor
            )
            Text(
                modifier = Modifier.weight(1f),
                text = displayText,
                color = accentColor,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
