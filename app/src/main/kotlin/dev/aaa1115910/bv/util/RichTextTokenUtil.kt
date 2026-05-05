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
private val SpacesAroundNewlineRegex = Regex("""[ \t]*\n[ \t]*""")

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
        .replace(SpacesAroundNewlineRegex, "\n")
        .replace(BlankLinesRegex, "\n")
}

/**
 * 最终 token 规范化：
 * 合并相邻 Text（用 StringBuilder 缓冲）
 * 对 Text 做 normalizeText（去空行保留换行）
 * 强制前面和后面只有一个空格（解决长短不一的空格问题），并对标点符号做智能适配
 */
private fun normalizeTokens(tokens: List<RichTextToken>): List<RichTextToken> {
    if (tokens.isEmpty()) return emptyList()

    val out = mutableListOf<RichTextToken>()
    var needSpaceBeforeNextText = false

    fun appendText(raw: String) {
        if (raw.isEmpty()) return
        var s = normalizeText(raw)

        if (needSpaceBeforeNextText) {
            // 去除 API 文本自带的行内前导空格，避免与强制加的空格叠加
            s = s.trimStart(' ', '\t')

            // 如果去掉空格后为空，直接返回，同时保留 needSpaceBeforeNextText=true 供下个 token 使用
            if (s.isEmpty()) return

            val prevTextEndsWithNewline =
                (out.lastOrNull() as? RichTextToken.Text)?.text?.endsWith("\n") == true

            // 如果下一段文本一上来就是换行，或上一段以换行结尾，则不插空格
            if (!s.startsWith("\n") && !prevTextEndsWithNewline) {
                // 如果 Mention 后面紧跟着中文或英文的标点符号，就不加空格，排版更好看
                val isPunctuation = s.firstOrNull()?.let { it in "，。！？、：；”’)]}》,.!?:;)" } == true
                if (!isPunctuation) {
                    s = " $s"
                }
            }
            needSpaceBeforeNextText = false
        }

        if (s.isEmpty()) return

        val last = out.lastOrNull()
        if (last is RichTextToken.Text) {
            out[out.lastIndex] = RichTextToken.Text(last.text + s)
        } else {
            out += RichTextToken.Text(s)
        }
    }

    fun ensureSingleSpaceBeforeInteractive() {
        if (out.isEmpty()) return

        val last = out.lastOrNull()
        when (last) {
            is RichTextToken.Text -> {
                val t = last.text
                val trimmed = t.trimEnd(' ', '\t')
                // 先做 trim，再判断是否换行结尾。
                // 否则如果原文是 "\n  "，原逻辑会误判并强制在行首加一个空格。
                if (trimmed.isNotEmpty() && !trimmed.endsWith("\n")) {
                    out[out.lastIndex] = RichTextToken.Text(trimmed + " ")
                } else {
                    out[out.lastIndex] = RichTextToken.Text(trimmed)
                }
            }
            else -> {
                // 连续两个非 Text token，中间补一个空格
                out += RichTextToken.Text(" ")
            }
        }
    }

    for (t in tokens) {
        when (t) {
            is RichTextToken.Text -> appendText(t.text)
            else -> {
                ensureSingleSpaceBeforeInteractive()
                out += t
                needSpaceBeforeNextText = true
            }
        }
    }

    // 去掉整体首尾的空格（避免首空格/尾空格）
    if (out.firstOrNull() is RichTextToken.Text) {
        val first = out.first() as RichTextToken.Text
        val trimmed = first.text.trimStart(' ', '\t')
        if (trimmed.isEmpty()) out.removeAt(0) else out[0] = RichTextToken.Text(trimmed)
    }
    if (out.lastOrNull() is RichTextToken.Text) {
        val last = out.last() as RichTextToken.Text
        val trimmed = last.text.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) out.removeAt(out.lastIndex) else out[out.lastIndex] = RichTextToken.Text(trimmed)
    }

    // 最后再合并一次所有的 Text，由于前置逻辑已经处理好了全部的空格，此处不要再粗暴调用 normalizeText，直接合并内容即可。
    val finalOut = mutableListOf<RichTextToken>()
    for (t in out) {
        if (t is RichTextToken.Text) {
            if (t.text.isEmpty()) continue
            val last = finalOut.lastOrNull()
            if (last is RichTextToken.Text) {
                finalOut[finalOut.lastIndex] = RichTextToken.Text(last.text + t.text)
            } else {
                finalOut += t
            }
        } else {
            finalOut += t
        }
    }
    return finalOut
}