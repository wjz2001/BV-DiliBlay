package dev.aaa1115910.biliapi.entity.reply

import bilibili.main.community.reply.v1.ReplyInfo
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

            val pinnedIds = rawPinned.map { it.id }.toHashSet()

            val pinnedComments = rawPinned.map { Comment.fromReplyInfo(it, forcePinned = true) }
            val normalComments = reply.repliesList
                .asSequence()
                .filter { it.id !in pinnedIds }
                .map { info ->
                    val pinnedByFlag =
                        info.replyControl.isUpTop || info.replyControl.isAdminTop || info.replyControl.isVoteTop
                    Comment.fromReplyInfo(info, forcePinned = pinnedByFlag)
                }
                .toList()

            return CommentsData(
                comments = pinnedComments + normalComments,
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
    val pictures: List<Picture> = emptyList(),
    val member: Member,
    val timeDesc: String,
    val like: Long,
    val repliesCount: Int,
    val isPinned: Boolean
) {
    companion object {
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
                isPinned = forcePinned || pinnedByFlag
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
                    0 -> "今天 ${timePart} 发布"
                    1 -> "昨天 ${timePart} 发布"
                    2 -> "前天 ${timePart} 发布"
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
                    result += MessagePart.Text(" ")
                    result += MessagePart.Mention(
                        name = matchedMention.name,
                        mid = matchedMention.mid
                    )
                    result += MessagePart.Text(" ")
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
