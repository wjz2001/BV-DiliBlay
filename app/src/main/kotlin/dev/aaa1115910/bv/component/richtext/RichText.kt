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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

@Composable
fun RichText(
    modifier: Modifier = Modifier,
    tokens: List<RichTextToken>,
    inlineKeyPrefix: String,
    textStyle: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    enableInteractiveFocus: Boolean,
    bodyFocusRequester: FocusRequester?,
    interactiveFocusRequesters: List<FocusRequester>,
    firstPictureFocusRequester: FocusRequester?,
    nextBodyFocusRequester: FocusRequester?,
    onVideoLinkClick: ((ResolvedVideoLink) -> Unit)?,
    onReferenceClick: ((RichTextReference) -> Unit)?,
    onMentionClick: ((Long, String) -> Unit)?
) {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    var emoteIndex = 0
    var interactiveIndex = 0
    val accentColor = C.mentionAndLink
    val fontSize = textStyle.fontSize
    val baseTextColor = textStyle.color

    fun previousUpRequester(
        currentInteractiveIndex: Int,
        currentRequester: FocusRequester?
    ): FocusRequester? {
        return when {
            currentInteractiveIndex > 0 ->
                interactiveFocusRequesters[currentInteractiveIndex - 1]

            bodyFocusRequester != null -> bodyFocusRequester
            else -> currentRequester
        }
    }

    fun nextDownRequester(
        currentInteractiveIndex: Int,
        currentRequester: FocusRequester?
    ): FocusRequester? {
        return when {
            currentInteractiveIndex < interactiveFocusRequesters.lastIndex ->
                interactiveFocusRequesters[currentInteractiveIndex + 1]

            firstPictureFocusRequester != null -> firstPictureFocusRequester
            nextBodyFocusRequester != null -> nextBodyFocusRequester
            else -> currentRequester
        }
    }

    val text = buildAnnotatedString {
        tokens.forEach { token ->
            when (token) {
                is RichTextToken.Text -> append(token.text)

                is RichTextToken.Mention -> {
                    val clickable = onMentionClick != null
                    if (enableInteractiveFocus && clickable) {
                        val id = "${inlineKeyPrefix}_mention_$interactiveIndex"
                        val currentInteractiveIndex = interactiveIndex
                        val currentFr = interactiveFocusRequesters.getOrNull(currentInteractiveIndex)
                        appendInlineContent(id, "@${token.name}")
                        inlineContent[id] = InlineTextContent(
                            Placeholder(
                                width = (token.name.length.coerceIn(1, 16) + 2).em,
                                height = 1.6.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            MentionInlineItem(
                                mention = token,
                                enableFocus = true,
                                focusRequester = currentFr,
                                upRequester = previousUpRequester(currentInteractiveIndex, currentFr),
                                downRequester = nextDownRequester(currentInteractiveIndex, currentFr),
                                fontSize = fontSize,
                                accentColor = accentColor,
                                onMentionClick = onMentionClick
                            )
                        }
                        interactiveIndex += 1
                    } else {
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
                    val id = "${inlineKeyPrefix}_video_$interactiveIndex"
                    val currentInteractiveIndex = interactiveIndex
                    val currentFr = interactiveFocusRequesters.getOrNull(currentInteractiveIndex)

                    appendInlineContent(id, token.data.videoId)

                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = 20.em,
                            height = 1.6.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        VideoLinkInlineItem(
                            token = token.data,
                            enableFocus = enableInteractiveFocus && clickable,
                            focusRequester = currentFr,
                            upRequester = previousUpRequester(currentInteractiveIndex, currentFr),
                            downRequester = nextDownRequester(currentInteractiveIndex, currentFr),
                            fontSize = fontSize,
                            accentColor = accentColor,
                            baseTextColor = baseTextColor,
                            onVideoLinkClick = onVideoLinkClick
                        )
                    }

                    if (clickable) {
                        interactiveIndex += 1
                    }
                }

                is RichTextToken.Reference -> {
                    val clickable = onReferenceClick != null
                    val id = "${inlineKeyPrefix}_reference_$interactiveIndex"
                    val currentInteractiveIndex = interactiveIndex
                    val currentFr = interactiveFocusRequesters.getOrNull(currentInteractiveIndex)
                    val placeholderTextLength = when {
                        token.reference is RichTextReference.Article &&
                            token.reference.cvid == 0L &&
                            "/opus/" in token.reference.url -> token.reference.displayText.length.coerceAtLeast(10)
                        else -> token.reference.displayText.length
                    }

                    append("\n")
                    appendInlineContent(id, token.reference.displayText)

                    inlineContent[id] = InlineTextContent(
                        Placeholder(
                            width = (placeholderTextLength.coerceIn(2, 18) + 5).em,
                            height = 1.6.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        ReferenceInlineItem(
                            reference = token.reference,
                            enableFocus = enableInteractiveFocus && clickable,
                            focusRequester = currentFr,
                            upRequester = previousUpRequester(currentInteractiveIndex, currentFr),
                            downRequester = nextDownRequester(currentInteractiveIndex, currentFr),
                            fontSize = fontSize,
                            accentColor = accentColor,
                            onReferenceClick = onReferenceClick
                        )
                    }

                    if (clickable) {
                        interactiveIndex += 1
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

@Composable
private fun MentionInlineItem(
    mention: RichTextToken.Mention,
    enableFocus: Boolean,
    focusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    onMentionClick: ((Long, String) -> Unit)?
) {
    var focused by remember(mention.mid, mention.name) { mutableStateOf(false) }

    if (!enableFocus) {
        Text(
            text = "@${mention.name}",
            color = accentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    Surface(
        modifier = Modifier
            .focusRequester(focusRequester ?: Default)
            .focusProperties {
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.hasFocus }
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
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoLinkInlineItem(
    token: VideoLinkToken,
    enableFocus: Boolean,
    focusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    baseTextColor: Color,
    onVideoLinkClick: ((ResolvedVideoLink) -> Unit)?
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
    val title = when {
        snapshot != null -> snapshot.title
        loaded -> token.rawUrl
        else -> token.videoId
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
                text = title,
                color = if (showLinkStyle) accentColor else baseTextColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .focusRequester(focusRequester ?: Default)
            .focusProperties {
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.hasFocus }
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
                text = title,
                color = if (showLinkStyle) accentColor else baseTextColor,
                fontSize = fontSize,
                maxLines = 1,
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
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    fontSize: TextUnit,
    accentColor: Color,
    onReferenceClick: ((RichTextReference) -> Unit)?
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
                text = displayText,
                color = accentColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .focusRequester(focusRequester ?: Default)
            .focusProperties {
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.hasFocus }
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
                text = displayText,
                color = accentColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
