package dev.aaa1115910.biliapi.entity.reply

import bilibili.main.community.reply.v1.ReplyInfo
import dev.aaa1115910.biliapi.entity.richtext.RichTextContent
import dev.aaa1115910.biliapi.entity.richtext.RichTextPart
import dev.aaa1115910.biliapi.entity.richtext.RichTextReference
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CommentsData(
    val comments: List<Comment> = emptyList(),
    val nextPage: CommentPage = CommentPage(),
    val hasNext: Boolean
) {
    companion object {
        fun fromMainListReply(
            reply: bilibili.main.community.reply.v1.MainListReply
        ): CommentsData {
            // 置顶：优先使用 MainListReply 的专用字段；其次兼容 reply_control 里的置顶标记
            val rawPinned = buildList {
                if (reply.upTop.id != 0L) add(reply.upTop)
                if (reply.adminTop.id != 0L) add(reply.adminTop)
                if (reply.voteTop.id != 0L) add(reply.voteTop)
                addAll(reply.topRepliesList)
            }.distinctBy { it.id }

            val pinnedComments = rawPinned.map { Comment.fromReplyInfo(it, forcePinned = true) }
            val normalComments = reply.repliesList
                .map { info ->
                    val pinnedByFlag =
                        info.replyControl.isUpTop || info.replyControl.isAdminTop || info.replyControl.isVoteTop
                    Comment.fromReplyInfo(info, forcePinned = pinnedByFlag)
                }
            val mergedComments = (pinnedComments + normalComments)
                .distinctBy { it.rpid }

            return CommentsData(
                comments = mergedComments,
                nextPage = CommentPage(
                    next = reply.cursor.next,
                    prev = reply.cursor.prev
                ),
                hasNext = !reply.cursor.isEnd
            )
        }
    }
}

data class CommentRepliesData(
    val rootComment: Comment,
    val replies: List<Comment>,
    val nextPage: CommentReplyPage = CommentReplyPage(),
    val hasNext: Boolean
) {
    companion object {
        fun fromDetailListReply(
            reply: bilibili.main.community.reply.v1.DetailListReply
        ): CommentRepliesData {
            return CommentRepliesData(
                rootComment = Comment.fromReplyInfo(reply.root, forcePinned = false),
                replies = reply.root.repliesList.map { Comment.fromReplyInfo(it, forcePinned = false) },
                nextPage = CommentReplyPage(
                    next = reply.cursor.next,
                    prev = reply.cursor.prev
                ),
                hasNext = !reply.cursor.isEnd
            )
        }
    }
}

data class Comment(
    val rpid: Long,
    val mid: Long,
    val oid: Long,
    val type: Long,
    val parent: Long,
    val message: String,
    val messageParts: List<MessagePart> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val pictures: List<Picture> = emptyList(),
    val member: Member,
    val timeDesc: String,
    val like: Long,
    val repliesCount: Int,
    val isPinned: Boolean,
    val isNoteComment: Boolean = false,
    val noteCvid: Long = 0L,
    val maxPreviewLines: Int = 0
) {
    fun toRichTextContent(): RichTextContent {
        val parts = mutableListOf<RichTextPart>()

        val sourceParts = messageParts.ifEmpty {
            listOf(MessagePart.Text(message))
        }
        sourceParts.forEach { part ->
            when (part) {
                is MessagePart.Text -> parts += RichTextPart.Text(part.text)
                is MessagePart.Mention -> {
                    parts += RichTextPart.Mention(
                        name = part.name,
                        mid = part.mid
                    )
                }

                is MessagePart.Emote -> {
                    parts += RichTextPart.Emote(
                        code = part.code,
                        url = part.url,
                        alt = part.alt
                    )
                }
            }
        }

        attachments.forEach { attachment ->
            parts += RichTextPart.Reference(attachment.toRichTextReference())
        }
        return RichTextContent(parts = mergeAdjacentRichTextParts(parts))
    }

    companion object {
        private val CvidRegexes = listOf(
            Regex("""(?<![A-Za-z0-9])cv(\d+)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
            Regex("""[?&]cvid=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/read/cv(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/read/mobile[^\s#]*?[?&]id=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/article/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/x/note/(?:[^/\s?#]+/)?(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/note-app/view[^\s#]*?[?&]cvid=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:[a-zA-Z0-9-]+\.)*b23\.tv/(?:cv)?(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""bilibili://article/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""bilibili://article[^\s#]*?[?&]id=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""bilibili://note[^\s#]*?[?&](?:cvid|id)=(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""bilibili://note/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""bilibili://[^?]+\?[^#]*\b(?:cvid|id)=(\d+)""", RegexOption.IGNORE_CASE)
        )

        fun fromReplyInfo(info: ReplyInfo, forcePinned: Boolean): Comment {
            val pinnedByFlag =
                info.replyControl.isUpTop || info.replyControl.isAdminTop || info.replyControl.isVoteTop

            val emoteMap = info.content.emoteMap.mapValues { (_, emote) ->
                EmoteResource(
                    url = emote.url,
                    text = emote.text
                )
            }
            val mentionMap = info.content.atNameToMidMap
                .filter { (name, mid) -> name.isNotBlank() && mid > 0L }
            val messageParts = parseMessageParts(
                message = info.content.message,
                emoteMap = emoteMap,
                mentionMap = mentionMap
            )
            val attachments = buildAttachments(info)
            val pictures = info.content.picturesList.map {
                Picture(
                    imgSrc = it.imgSrc,
                    imgWidth = it.imgWidth,
                    imgHeight = it.imgHeight,
                    imgSize = it.imgSize
                )
            }

            return Comment(
                rpid = info.id,
                mid = info.mid,
                oid = info.oid,
                type = info.type,
                parent = info.parent,
                message = info.content.message,
                messageParts = messageParts,
                attachments = attachments,
                pictures = pictures,
                member = Member(
                    mid = info.mid,
                    avatar = info.member.face,
                    name = info.member.name
                ),
                timeDesc = formatCommentTimeDescWithSeconds(
                    ctime = info.ctime,
                    fallback = info.replyControl.timeDesc
                ),
                like = info.like,
                repliesCount = info.count.toInt(),
                isPinned = forcePinned || pinnedByFlag,
                isNoteComment = info.replyControl.isNote,
                noteCvid = extractNoteCvid(info),
                maxPreviewLines = info.replyControl.maxLine.toInt()
            )
        }

        private fun formatCommentTimeDescWithSeconds(
            ctime: Long,
            fallback: String
        ): String {
            if (ctime <= 0L) return fallback

            return runCatching {
                // 兼容秒/毫秒时间戳
                val millis = if (ctime < 10_000_000_000L) ctime * 1000L else ctime
                val date = Date(millis)

                val nowCal = Calendar.getInstance()
                val targetCal = Calendar.getInstance().apply { timeInMillis = millis }

                fun Calendar.toStartOfDay(): Calendar = (clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val todayStart = nowCal.toStartOfDay()
                val targetStart = targetCal.toStartOfDay()
                val diffDays = ((todayStart.timeInMillis - targetStart.timeInMillis) / 86_400_000L).toInt()

                val timePart = SimpleDateFormat("HH:mm:ss", Locale.CHINESE).format(date)

                when (diffDays) {
                    0 -> "今天 $timePart 发布"
                    1 -> "昨天 $timePart 发布"
                    2 -> "前天 $timePart 发布"
                    else -> {
                        // 这里沿用 formatPubTimeString 的“今年不带年、跨年带年”逻辑，只是补上秒
                        val year = targetCal.get(Calendar.YEAR)
                        val currentYear = nowCal.get(Calendar.YEAR)
                        val pattern = if (year == currentYear) {
                            "MM月dd日HH:mm:ss"
                        } else {
                            "yyyy年MM月dd日HH:mm:ss"
                        }
                        "${SimpleDateFormat(pattern, Locale.CHINESE).format(date)} 发布"
                    }
                }
            }.getOrElse {
                fallback
            }
        }

        private fun parseMessageParts(
            message: String,
            emoteMap: Map<String, EmoteResource>,
            mentionMap: Map<String, Long>
        ): List<MessagePart> {
            if (message.isEmpty()) return emptyList()
            val mentionResources = mentionMap
                .asSequence()
                .map { (name, mid) ->
                    MentionResource(
                        name = name,
                        mid = mid,
                        rawText = "@$name"
                    )
                }
                .sortedByDescending { it.rawText.length }
                .toList()
            if (emoteMap.isEmpty() && mentionResources.isEmpty()) {
                return listOf(MessagePart.Text(message))
            }

            val emoteCodes = emoteMap.keys
                .asSequence()
                .filter { it.isNotEmpty() }
                .sortedByDescending { it.length }
                .toList()

            val result = mutableListOf<MessagePart>()
            val textBuffer = StringBuilder()
            var cursor = 0

            fun flushTextBuffer() {
                if (textBuffer.isEmpty()) return
                result += MessagePart.Text(textBuffer.toString())
                textBuffer.clear()
            }

            while (cursor < message.length) {
                val matchedCode = emoteCodes.firstOrNull { code ->
                    message.startsWith(code, startIndex = cursor)
                }
                if (matchedCode != null) {
                    val emote = emoteMap[matchedCode]
                    if (emote != null && emote.url.isNotBlank()) {
                        flushTextBuffer()
                        result += MessagePart.Emote(
                            code = matchedCode,
                            url = emote.url,
                            alt = emote.text.ifBlank { matchedCode }
                        )
                        cursor += matchedCode.length
                        continue
                    }
                }

                val matchedMention = mentionResources.firstOrNull { mention ->
                    message.startsWith(mention.rawText, startIndex = cursor)
                }
                if (matchedMention != null) {
                    if (textBuffer.lastOrNull() == ' ') {
                        textBuffer.deleteCharAt(textBuffer.lastIndex)
                    }
                    flushTextBuffer()
                    result += MessagePart.Mention(
                        name = matchedMention.name,
                        mid = matchedMention.mid
                    )
                    cursor += matchedMention.rawText.length
                    if (cursor < message.length && message[cursor] == ' ') {
                        cursor += 1
                    }
                    continue
                }

                textBuffer.append(message[cursor])
                cursor += 1
            }

            flushTextBuffer()
            return mergeAdjacentTextParts(result)
        }

        private fun buildAttachments(info: ReplyInfo): List<Attachment> {
            val result = mutableListOf<Attachment>()

            info.content.urlMap.entries
                .asSequence()
                .mapNotNull { (rawText, url) ->
                    val candidates = listOf(url.pcUrl, url.appUrlSchema, rawText)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val cvid = candidates.firstNotNullOfOrNull(::parseCvid)
                    // opus URL 无法解析出 cvid，但仍需保留为附件（用 cvid=0 + URL 标记）
                    if (cvid == null) {
                        val opusCandidate = candidates.firstOrNull { "bilibili.com/opus/" in it }
                        if (opusCandidate != null) {
                            val title = url.title.ifBlank { "图文" }
                            result += Attachment.Article(cvid = 0L, title = title, url = opusCandidate)
                        }
                        return@mapNotNull null
                    }
                    val displayUrl = candidates.firstOrNull(::isJumpUrl) ?: candidates.first()

                    if (displayUrl.let(::isNoteUrl) || url.title.contains("笔记")) {
                        Attachment.Note(
                            cvid = cvid,
                            clickUrl = displayUrl
                        )
                    } else {
                        val title = url.title.ifBlank {
                            rawText.takeIf { it.isNotBlank() } ?: "专栏 cv$cvid"
                        }
                        Attachment.Article(
                            cvid = cvid,
                            title = title,
                            url = displayUrl
                        )
                    }
                }
                .forEach { result += it }

            return result
                .groupBy { attachment ->
                    when {
                        attachment.cvid > 0L -> "cvid:${attachment.cvid}"
                        attachment is Attachment.Note ->
                            "note:${attachment.clickUrl}"
                        attachment is Attachment.Article ->
                            "article:${attachment.url.ifBlank { attachment.title }}"
                        else -> "other:${attachment.displayText}"
                    }
                }
                .values
                .map { group ->
                    group.firstOrNull { it is Attachment.Note } ?: group.first()
                }
        }

        private fun extractNoteCvid(info: ReplyInfo): Long {
            if (!info.content.hasRichText()) return 0L
            val richText = info.content.richText
            if (richText.hasNoteCard() && richText.noteCard.cvid > 0L) {
                return richText.noteCard.cvid
            }
            if (richText.hasNote()) {
                val cvid = parseCvid(richText.note.clickUrl)
                if (cvid != null) return cvid
            }
            return 0L
        }

        private fun parseCvid(raw: String): Long? {
            if (raw.isBlank()) return null
            val candidates = buildList {
                add(raw)
                decodeUrl(raw)?.let { add(it) }
            }.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            return candidates.mapNotNull { candidate ->
                candidate.toLongOrNull()?.takeIf { it > 0L } ?: CvidRegexes.asSequence()
                    .mapNotNull { regex ->
                        regex.find(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    }
                    .firstOrNull()
            }.firstOrNull()
        }

        private fun decodeUrl(raw: String): String? {
            return runCatching {
                URLDecoder.decode(raw, Charsets.UTF_8.name())
            }.getOrNull()
                ?.takeIf { it != raw }
        }

        private fun isNoteUrl(raw: String): Boolean {
            if (raw.isBlank()) return false
            val url = raw.lowercase()
            return "note-app/view" in url || "/x/note/" in url
        }

        private fun isJumpUrl(raw: String): Boolean {
            if (raw.isBlank()) return false
            return raw.startsWith("https://", ignoreCase = true) ||
                raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("bilibili://", ignoreCase = true)
        }

        private fun mergeAdjacentTextParts(parts: List<MessagePart>): List<MessagePart> {
            if (parts.isEmpty()) return emptyList()
            val merged = mutableListOf<MessagePart>()
            parts.forEach { part ->
                val last = merged.lastOrNull()
                if (part is MessagePart.Text && last is MessagePart.Text) {
                    merged[merged.lastIndex] = MessagePart.Text(last.text + part.text)
                } else {
                    merged += part
                }
            }
            return merged
        }
    }

    data class Member(
        val mid: Long,
        val avatar: String,
        val name: String
    )

    data class Picture(
        val imgSrc: String,
        val imgWidth: Double,
        val imgHeight: Double,
        val imgSize: Double
    )

    sealed class Attachment {
        abstract val cvid: Long
        abstract val displayText: String

        data class Note(
            override val cvid: Long,
            val clickUrl: String
        ) : Attachment() {
            override val displayText: String = "笔记"
        }

        data class Article(
            override val cvid: Long,
            val title: String,
            val url: String
        ) : Attachment() {
            override val displayText: String = title
        }
    }

    sealed class MessagePart {
        data class Text(
            val text: String
        ) : MessagePart()

        data class Mention(
            val name: String,
            val mid: Long
        ) : MessagePart()

        data class Emote(
            val code: String,
            val url: String,
            val alt: String
        ) : MessagePart()
    }

    private data class EmoteResource(
        val url: String,
        val text: String
    )

    private data class MentionResource(
        val name: String,
        val mid: Long,
        val rawText: String
    )
}

private fun Comment.Attachment.toRichTextReference(): RichTextReference {
    return when (this) {
        is Comment.Attachment.Note -> RichTextReference.Note(
            cvid = cvid,
            url = clickUrl
        )

        is Comment.Attachment.Article -> RichTextReference.Article(
            cvid = cvid,
            title = title,
            url = url
        )
    }
}

private fun mergeAdjacentRichTextParts(parts: List<RichTextPart>): List<RichTextPart> {
    if (parts.isEmpty()) return emptyList()

    val merged = mutableListOf<RichTextPart>()
    parts.forEach { part ->
        val last = merged.lastOrNull()
        if (part is RichTextPart.Text && last is RichTextPart.Text) {
            merged[merged.lastIndex] = RichTextPart.Text(last.text + part.text)
        } else {
            merged += part
        }
    }
    return merged
}
