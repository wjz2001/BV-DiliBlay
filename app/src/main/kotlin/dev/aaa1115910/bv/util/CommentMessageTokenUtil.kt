package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.reply.Comment

data class CommentMessageContent(
    val message: String = "",
    val messageParts: List<Comment.MessagePart> = emptyList(),
    val attachments: List<Comment.Attachment> = emptyList()
)

sealed class CommentMessageToken {
    data class Text(
        val text: String
    ) : CommentMessageToken()

    data class Emote(
        val code: String,
        val url: String,
        val alt: String
    ) : CommentMessageToken()

    data class Mention(
        val name: String,
        val mid: Long
    ) : CommentMessageToken()

    data class VideoLink(
        val data: VideoLinkToken
    ) : CommentMessageToken()

    data class Attachment(
        val data: Comment.Attachment
    ) : CommentMessageToken()
}

fun buildCommentMessageTokens(comment: Comment): List<CommentMessageToken> {
    return buildCommentMessageTokens(comment.toMessageContent())
}

fun Comment.toMessageContent(): CommentMessageContent {
    return CommentMessageContent(
        message = message,
        messageParts = messageParts,
        attachments = attachments
    )
}

fun buildCommentMessageTokens(content: CommentMessageContent): List<CommentMessageToken> {
    val parts = content.messageParts.ifEmpty {
        listOf(Comment.MessagePart.Text(content.message))
    }

    val tokens = mutableListOf<CommentMessageToken>()
    parts.forEach { part ->
        when (part) {
            is Comment.MessagePart.Mention -> {
                tokens += CommentMessageToken.Mention(
                    name = part.name,
                    mid = part.mid
                )
            }

            is Comment.MessagePart.Text -> {
                tokens += buildCommentMessageTokens(part.text)
            }

            is Comment.MessagePart.Emote -> {
                tokens += CommentMessageToken.Emote(
                    code = part.code,
                    url = part.url,
                    alt = part.alt
                )
            }
        }
    }

    content.attachments.forEach { attachment ->
        tokens += CommentMessageToken.Attachment(attachment)
    }
    return mergeAdjacentTextTokens(tokens)
}

fun buildCommentMessageTokens(text: String): List<CommentMessageToken> {
    if (text.isEmpty()) return emptyList()

    return mergeAdjacentTextTokens(
        splitTextByVideoLink(text).map { token ->
            when (token) {
                is VideoLinkTextToken.Text -> CommentMessageToken.Text(token.text)
                is VideoLinkTextToken.VideoLink -> CommentMessageToken.VideoLink(token.data)
            }
        }
    )
}

private fun mergeAdjacentTextTokens(tokens: List<CommentMessageToken>): List<CommentMessageToken> {
    if (tokens.isEmpty()) return emptyList()

    val merged = mutableListOf<CommentMessageToken>()
    tokens.forEach { token ->
        val last = merged.lastOrNull()
        if (token is CommentMessageToken.Text && last is CommentMessageToken.Text) {
            merged[merged.lastIndex] = last.copy(text = last.text + token.text)
        } else {
            merged += token
        }
    }
    return merged
}
