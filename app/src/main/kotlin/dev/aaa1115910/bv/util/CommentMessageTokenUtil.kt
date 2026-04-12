package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.reply.Comment

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
}

fun buildCommentMessageTokens(comment: Comment): List<CommentMessageToken> {
    val parts = comment.messageParts.ifEmpty {
        listOf(Comment.MessagePart.Text(comment.message))
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
