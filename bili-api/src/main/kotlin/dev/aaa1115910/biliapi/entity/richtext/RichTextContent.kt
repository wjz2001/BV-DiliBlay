package dev.aaa1115910.biliapi.entity.richtext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class RichTextContent(
    val parts: List<RichTextPart> = emptyList()
) {
    val plainText: String
        get() = buildString {
            parts.forEach { part ->
                when (part) {
                    is RichTextPart.Text -> append(part.text)
                    is RichTextPart.Mention -> append("@${part.name}")
                    is RichTextPart.Emote -> append(part.alt.ifBlank { part.code })
                    is RichTextPart.Reference -> append(part.reference.displayText)
                }
            }
        }

    companion object {
        fun fromText(text: String): RichTextContent {
            if (text.isEmpty()) return RichTextContent()
            return RichTextContent(parts = listOf(RichTextPart.Text(text)))
        }
    }
}

sealed class RichTextPart {
    data class Text(
        val text: String
    ) : RichTextPart()

    data class Mention(
        val name: String,
        val mid: Long
    ) : RichTextPart()

    data class Emote(
        val code: String,
        val url: String,
        val alt: String
    ) : RichTextPart()

    data class Reference(
        val reference: RichTextReference
    ) : RichTextPart()
}

sealed class RichTextReference {
    abstract val cvid: Long
    abstract val displayText: String

    data class Note(
        override val cvid: Long,
        val url: String = "",
        val title: String = "笔记"
    ) : RichTextReference() {
        override val displayText: String = title
    }

    data class Article(
        override val cvid: Long,
        val title: String,
        val url: String = ""
    ) : RichTextReference() {
        override val displayText: String = title
    }
}

fun parseVideoDescriptionContent(
    description: String,
    descV2: JsonElement?
): RichTextContent {
    val parsedParts = (descV2 as? JsonArray)
        ?.mapNotNull { parseVideoDescriptionPart(it as? JsonObject) }
        .orEmpty()

    if (parsedParts.isEmpty()) {
        return RichTextContent.fromText(description)
    }

    return RichTextContent(parts = mergeAdjacentTextParts(parsedParts))
}

private fun parseVideoDescriptionPart(obj: JsonObject?): RichTextPart? {
    obj ?: return null

    val rawText = obj.string("raw_text")
    val bizId = obj.long("biz_id")
    return when (obj.int("type")) {
        2 -> {
            val name = rawText.removePrefix("@").trim()
            when {
                name.isBlank() -> null
                bizId <= 0L -> RichTextPart.Text(rawText)
                else -> RichTextPart.Mention(name = name, mid = bizId)
            }
        }

        else -> rawText.takeIf { it.isNotEmpty() }?.let(RichTextPart::Text)
    }
}

private fun mergeAdjacentTextParts(parts: List<RichTextPart>): List<RichTextPart> {
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

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.int(key: String): Int {
    return this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
}

private fun JsonObject.long(key: String): Long {
    return this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
}
