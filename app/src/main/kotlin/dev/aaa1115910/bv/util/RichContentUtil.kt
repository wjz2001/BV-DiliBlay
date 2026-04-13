package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.http.BiliHttpApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class RichContentDocument(
    val title: String,
    val body: CommentMessageContent,
    val pictures: List<Comment.Picture>,
    val centerTitle: Boolean
)

private data class ParsedRichContent(
    val text: String,
    val pictures: List<Comment.Picture>
)

private val richContentJson = Json {
    ignoreUnknownKeys = true
}

private val HtmlImageRegex = Regex(
    pattern = """<img\b[^>]*\bsrc\s*=\s*(["']?)([^"'\s>]+)\1[^>]*>""",
    option = RegexOption.IGNORE_CASE
)
private val HtmlBlockSeparatorRegex = Regex(
    pattern = """</(?:h[1-6]|p|li|blockquote|div|section|article)>""",
    option = RegexOption.IGNORE_CASE
)
private val HtmlLineBreakRegex = Regex(
    pattern = """<br\s*/?>""",
    option = RegexOption.IGNORE_CASE
)
private val HtmlTagRegex = Regex("""<[^>]+>""")

suspend fun loadRichContentDocument(
    attachment: Comment.Attachment,
    sessData: String? = Prefs.sessData.takeIf { it.isNotBlank() }
): RichContentDocument {
    return when (attachment) {
        is Comment.Attachment.Note -> {
            runCatching {
                loadPublishedNoteDocument(
                    cvid = attachment.cvid,
                    fallbackTitle = attachment.displayText,
                    sessData = sessData
                )
            }.recoverCatching {
                loadArticleDocument(
                    cvid = attachment.cvid,
                    fallbackTitle = attachment.displayText,
                    sessData = sessData
                )
            }.getOrThrow()
        }

        is Comment.Attachment.Article -> {
            runCatching {
                loadArticleDocument(
                    cvid = attachment.cvid,
                    fallbackTitle = attachment.title,
                    sessData = sessData
                )
            }.recoverCatching {
                loadPublishedNoteDocument(
                    cvid = attachment.cvid,
                    fallbackTitle = attachment.title,
                    sessData = sessData
                )
            }.getOrThrow()
        }
    }
}

private suspend fun loadPublishedNoteDocument(
    cvid: Long,
    fallbackTitle: String,
    sessData: String?
): RichContentDocument {
    val data = BiliHttpApi.getPublishedNoteInfo(
        cvid = cvid,
        sessData = sessData
    ).getResponseData()
    val title = data.string("title").ifBlank { fallbackTitle }
    val parsed = parseNoteContent(data.string("content"))
    return RichContentDocument(
        title = title,
        body = CommentMessageContent(message = parsed.text),
        pictures = parsed.pictures,
        centerTitle = false
    )
}

private suspend fun loadArticleDocument(
    cvid: Long,
    fallbackTitle: String,
    sessData: String?
): RichContentDocument {
    val data = BiliHttpApi.getArticleView(
        cvid = cvid,
        sessData = sessData
    ).getResponseData()
    val title = data.string("title").ifBlank { fallbackTitle }
    val parsed = parseArticleContent(data["content"])
    val fallbackPictures = if (parsed.pictures.isNotEmpty()) {
        parsed.pictures
    } else {
        data.pictureArray("image_urls")
    }
    return RichContentDocument(
        title = title,
        body = CommentMessageContent(message = parsed.text),
        pictures = fallbackPictures,
        centerTitle = true
    )
}

private fun parseNoteContent(raw: String): ParsedRichContent {
    if (raw.isBlank()) return ParsedRichContent(text = "", pictures = emptyList())
    val root = runCatching { richContentJson.parseToJsonElement(raw) }.getOrNull()
    return parseOpsRoot(root, imageKeys = setOf("imageUpload"))
}

private fun parseArticleContent(content: JsonElement?): ParsedRichContent {
    content ?: return ParsedRichContent(text = "", pictures = emptyList())

    return when (content) {
        is JsonObject -> parseOpsRoot(content, imageKeys = setOf("native-image"))
        is JsonArray -> parseOpsArray(content, imageKeys = setOf("native-image"))
        is JsonPrimitive -> {
            val raw = content.contentOrNull.orEmpty()
            when {
                raw.isBlank() -> ParsedRichContent(text = "", pictures = emptyList())
                raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[") ->
                    parseOpsRoot(
                        runCatching { richContentJson.parseToJsonElement(raw) }.getOrNull(),
                        imageKeys = setOf("native-image")
                    )

                else -> parseHtmlContent(raw)
            }
        }
    }
}

private fun parseOpsRoot(
    root: JsonElement?,
    imageKeys: Set<String>
): ParsedRichContent {
    root ?: return ParsedRichContent(text = "", pictures = emptyList())
    return when (root) {
        is JsonArray -> parseOpsArray(root, imageKeys)
        is JsonObject -> {
            val ops = root["ops"] as? JsonArray
            if (ops != null) parseOpsArray(ops, imageKeys)
            else ParsedRichContent(text = "", pictures = emptyList())
        }

        else -> ParsedRichContent(text = "", pictures = emptyList())
    }
}

private fun parseOpsArray(
    ops: JsonArray,
    imageKeys: Set<String>
): ParsedRichContent {
    val text = StringBuilder()
    val pictures = mutableListOf<Comment.Picture>()

    ops.forEach { item ->
        val obj = item as? JsonObject ?: return@forEach
        val insert = obj["insert"] ?: return@forEach
        when (insert) {
            is JsonPrimitive -> {
                text.append(insert.contentOrNull.orEmpty())
            }

            is JsonObject -> {
                imageKeys.firstNotNullOfOrNull { imageKey ->
                    insert[imageKey] as? JsonObject
                }?.let { image ->
                    val imgSrc = image.string("url")
                    if (imgSrc.isNotBlank()) {
                        pictures += Comment.Picture(
                            imgSrc = imgSrc,
                            imgWidth = image.double("width"),
                            imgHeight = image.double("height"),
                            imgSize = image.double("size")
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    return ParsedRichContent(
        text = text.toString().trim('\n'),
        pictures = pictures.distinctBy { it.imgSrc }
    )
}

private fun parseHtmlContent(html: String): ParsedRichContent {
    val pictures = HtmlImageRegex.findAll(html)
        .mapNotNull { it.groupValues.getOrNull(2)?.takeIf(String::isNotBlank) }
        .distinct()
        .map {
            Comment.Picture(
                imgSrc = it,
                imgWidth = 0.0,
                imgHeight = 0.0,
                imgSize = 0.0
            )
        }
        .toList()

    val text = html
        .replace(HtmlLineBreakRegex, "\n")
        .replace(HtmlBlockSeparatorRegex, "\n")
        .replace(HtmlTagRegex, "")
        .unescapeHtml()
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    return ParsedRichContent(
        text = text,
        pictures = pictures
    )
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.double(key: String): Double {
    return this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
}

private fun JsonObject.pictureArray(key: String): List<Comment.Picture> {
    return (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        ?.distinct()
        ?.map {
            Comment.Picture(
                imgSrc = it,
                imgWidth = 0.0,
                imgHeight = 0.0,
                imgSize = 0.0
            )
        }
        ?.toList()
        .orEmpty()
}

private fun String.unescapeHtml(): String {
    return replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}
