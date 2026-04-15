package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.richtext.RichTextContent
import dev.aaa1115910.biliapi.entity.richtext.RichTextPart
import dev.aaa1115910.biliapi.entity.richtext.RichTextReference

sealed class RichTextToken {
    data class Text(
        val text: String
    ) : RichTextToken()

    data class Emote(
        val code: String,
        val url: String,
        val alt: String
    ) : RichTextToken()

    data class Mention(
        val name: String,
        val mid: Long
    ) : RichTextToken()

    data class VideoLink(
        val data: VideoLinkToken
    ) : RichTextToken()

    data class Reference(
        val reference: RichTextReference
    ) : RichTextToken()
}

fun buildRichTextTokens(content: RichTextContent): List<RichTextToken> {
    val tokens = mutableListOf<RichTextToken>()

    // key -> reference（优先使用 content.parts 里显式给出的 reference）
    val explicitReferences = mutableMapOf<String, RichTextReference>()
    content.parts.forEach { part ->
        if (part is RichTextPart.Reference) {
            part.reference.referenceMatchKey()?.let { key ->

                explicitReferences.getOrPut(key) { part.reference }
            }
        }
    }

    // 记录哪些 key 已经在文本里被 inline 过了，避免重复输出 Reference token
    val inlinedKeys = mutableSetOf<String>()

    content.parts.forEach { part ->
        when (part) {
            is RichTextPart.Text -> {
                splitTextByVideoLink(part.text).forEach { videoToken ->
                    when (videoToken) {
                        is VideoLinkTextToken.Text -> {
                            tokens += splitTextByReference(
                                text = videoToken.text,
                                explicitReferences = explicitReferences,
                                inlinedKeys = inlinedKeys
                            )
                        }

                        is VideoLinkTextToken.VideoLink -> {
                            tokens += RichTextToken.VideoLink(videoToken.data)
                        }
                    }
                }
            }

            is RichTextPart.Mention -> {
                tokens += RichTextToken.Mention(
                    name = part.name,
                    mid = part.mid
                )
            }

            is RichTextPart.Emote -> {
                tokens += RichTextToken.Emote(
                    code = part.code,
                    url = part.url,
                    alt = part.alt
                )
            }

            is RichTextPart.Reference -> {
                val key = part.reference.referenceMatchKey()
                if (key == null || key !in inlinedKeys) {
                    tokens += RichTextToken.Reference(part.reference)
                }
            }
        }
    }

    // 最终统一规范化：合并相邻 Text + 去空行但保留必要换行
    return normalizeTokens(tokens)
}

fun countRichTextInteractiveTokens(
    tokens: List<RichTextToken>,
    includeVideoLinks: Boolean = true,
    includeReferences: Boolean = true,
    includeMentions: Boolean = true
): Int {
    return tokens.count { token ->
        when (token) {
            is RichTextToken.VideoLink -> includeVideoLinks
            is RichTextToken.Reference -> includeReferences
            is RichTextToken.Mention -> includeMentions
            else -> false
        }
    }
}

private sealed class ReferenceTextToken {
    data class Text(
        val text: String
    ) : ReferenceTextToken()

    data class Reference(
        val reference: RichTextReference
    ) : ReferenceTextToken()
}

private data class MatchedReference(
    val range: IntRange,
    val reference: RichTextReference
)

private val NoteQueryReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/note-app/view[^\s#]*?[?&]cvid=(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val NotePathReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/x/note/(?:[^/\s?#]+/)?(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val ArticleReadReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/read/cv(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val ArticleMobileReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/read/mobile[^\s#]*?[?&]id=(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val ArticlePathReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/article/(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val PureCvReferenceRegex = Regex(
    pattern = """(?<![A-Za-z0-9])cv(\d+)(?![A-Za-z0-9])""",
    options = setOf(RegexOption.IGNORE_CASE)
)
private val OpusReferenceRegex = Regex(
    pattern = """(?:https?://)?(?:[a-zA-Z0-9-]+\.)*bilibili\.com/opus/(\d+)[^\s#]*""",
    options = setOf(RegexOption.IGNORE_CASE)
)

private fun splitTextByReference(
    text: String,
    explicitReferences: Map<String, RichTextReference> = emptyMap(),
    inlinedKeys: MutableSet<String>? = null
): List<RichTextToken> {
    if (text.isEmpty()) return emptyList()

    val parts = mutableListOf<ReferenceTextToken>()
    var cursor = 0
    while (cursor < text.length) {
        val match = findNextReference(text, cursor) ?: break
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (start > cursor) {
            parts += ReferenceTextToken.Text(text.substring(cursor, start))
        }

        val key = match.reference.referenceMatchKey()
        val existingRef = key?.let { explicitReferences[it] }
        if (existingRef != null) {
            parts += ReferenceTextToken.Reference(existingRef)
            inlinedKeys?.add(key)
        } else {
            parts += ReferenceTextToken.Reference(match.reference)
        }
        cursor = endExclusive
    }

    if (cursor < text.length) {
        parts += ReferenceTextToken.Text(text.substring(cursor))
    }

    return parts.map { part ->
        when (part) {
            is ReferenceTextToken.Text -> RichTextToken.Text(part.text)
            is ReferenceTextToken.Reference -> RichTextToken.Reference(part.reference)
        }
    }
}

private fun findNextReference(text: String, startIndex: Int): MatchedReference? {
    return listOfNotNull(
        NoteQueryReferenceRegex.find(text, startIndex)?.toNoteReference(),
        NotePathReferenceRegex.find(text, startIndex)?.toNoteReference(),
        ArticleReadReferenceRegex.find(text, startIndex)?.toArticleReference(),
        ArticleMobileReferenceRegex.find(text, startIndex)?.toArticleReference(),
        ArticlePathReferenceRegex.find(text, startIndex)?.toArticleReference(),
        PureCvReferenceRegex.find(text, startIndex)?.toArticleReference(),
        OpusReferenceRegex.find(text, startIndex)?.toOpusReference()
    ).minWithOrNull(
        compareBy<MatchedReference> { it.range.first }
            .thenByDescending { it.range.last - it.range.first }
    )
}

private fun MatchResult.toNoteReference(): MatchedReference? {
    val cvid = groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    return MatchedReference(
        range = range,
        reference = RichTextReference.Note(
            cvid = cvid,
            url = value,
            title = "笔记 cvid=$cvid"
        )
    )
}

private fun MatchResult.toArticleReference(): MatchedReference? {
    val cvid = groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    return MatchedReference(
        range = range,
        reference = RichTextReference.Article(
            cvid = cvid,
            title = "专栏 cv$cvid",
            url = value
        )
    )
}

private fun MatchResult.toOpusReference(): MatchedReference? {
    groupValues.getOrNull(1)?.toLongOrNull() ?: return null
    return MatchedReference(
        range = range,
        reference = RichTextReference.Article(
            cvid = 0L,
            title = "图文",
            url = value
        )
    )
}

private fun RichTextReference.referenceMatchKey(): String? {
    if (cvid > 0L) {
        return when (this) {
            is RichTextReference.Note -> "cvid:$cvid"
            is RichTextReference.Article -> "cvid:$cvid"
        }
    }
    val url = when (this) {
        is RichTextReference.Note -> this.url
        is RichTextReference.Article -> this.url
    }
    return url.takeIf { it.isNotBlank() }?.let { "url:$it" }
}

private val BlankLinesRegex = Regex("""\n[ \t]*\n+""")
private val TrailingSpacesBeforeNewlineRegex = Regex("""[ \t]+\n""")

/**
 * 不保留空行，但保留必要换行：
 * - 统一 \r\n/\r 为 \n
 * - 去掉行尾空白
 * - 把任何“空行”（>=2 个换行，中间可夹空白）压成单个 \n
 */
private fun normalizeText(s: String): String {
    return s
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(TrailingSpacesBeforeNewlineRegex, "\n")
        .replace(BlankLinesRegex, "\n")
}

/**
 * 最终 token 规范化：
 * 合并相邻 Text（用 StringBuilder 缓冲）
 * 对 Text 做 normalizeText（去空行保留换行）
 */
private fun normalizeTokens(tokens: List<RichTextToken>): List<RichTextToken> {
    if (tokens.isEmpty()) return emptyList()

    val out = mutableListOf<RichTextToken>()
    val buf = StringBuilder()

    fun flushText() {
        if (buf.isEmpty()) return
        val normalized = normalizeText(buf.toString())
        buf.clear()

        if (normalized.isEmpty()) return

        val last = out.lastOrNull()
        if (last is RichTextToken.Text) {
            out[out.lastIndex] = RichTextToken.Text(last.text + normalized)
        } else {
            out += RichTextToken.Text(normalized)
        }
    }

    for (t in tokens) {
        when (t) {
            is RichTextToken.Text -> buf.append(t.text)
            else -> {
                flushText()
                out += t
            }
        }
    }
    flushText()

    return out
}