package dev.aaa1115910.bv.player.danmaku.filter

import dev.aaa1115910.bv.player.danmaku.model.DanmakuFilterRule
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType
import java.util.Locale

enum class DanmakuFilterBlockReason {
    TypeNotAllowed,
    LevelTooLow,
    KeywordBlocked,
    UserBlocked,
}

sealed class DanmakuFilterResult {
    data object Accepted : DanmakuFilterResult()

    data class Rejected(
        val reason: DanmakuFilterBlockReason,
        val detail: String,
    ) : DanmakuFilterResult()
}

fun interface DanmakuFilterNode {
    fun test(item: DanmakuItem, rule: DanmakuFilterRule): DanmakuFilterResult
}

class DanmakuFilterChain(
    private val nodes: List<DanmakuFilterNode> = defaultNodes(),
) {
    fun evaluate(item: DanmakuItem, rule: DanmakuFilterRule): DanmakuFilterResult {
        for (node in nodes) {
            val result = node.test(item, rule)
            if (result is DanmakuFilterResult.Rejected) {
                return result
            }
        }
        return DanmakuFilterResult.Accepted
    }

    fun filter(items: List<DanmakuItem>, rule: DanmakuFilterRule): List<DanmakuItem> {
        if (items.isEmpty()) return emptyList()
        return items.filter { evaluate(it, rule) is DanmakuFilterResult.Accepted }
    }

    companion object {
        fun defaultNodes(): List<DanmakuFilterNode> {
            return listOf(
                typeFilterNode(),
                ruleFilterNode(),
            )
        }

        private fun typeFilterNode(): DanmakuFilterNode {
            return DanmakuFilterNode { item, rule ->
                val allowed = when (item.trackType) {
                    DanmakuTrackType.Scroll -> rule.allowScroll
                    DanmakuTrackType.Top -> rule.allowTop
                    DanmakuTrackType.Bottom -> rule.allowBottom
                }
                if (allowed) {
                    DanmakuFilterResult.Accepted
                } else {
                    DanmakuFilterResult.Rejected(
                        reason = DanmakuFilterBlockReason.TypeNotAllowed,
                        detail = "trackType=${item.trackType}",
                    )
                }
            }
        }

        private fun ruleFilterNode(): DanmakuFilterNode {
            return DanmakuFilterNode { item, rule ->
                if (item.level < rule.minLevel) {
                    return@DanmakuFilterNode DanmakuFilterResult.Rejected(
                        reason = DanmakuFilterBlockReason.LevelTooLow,
                        detail = "level=${item.level},minLevel=${rule.minLevel}",
                    )
                }
                val keyword = hitBlockedKeyword(item.text, rule.blockedKeywords)
                if (keyword != null) {
                    return@DanmakuFilterNode DanmakuFilterResult.Rejected(
                        reason = DanmakuFilterBlockReason.KeywordBlocked,
                        detail = "keyword=$keyword",
                    )
                }
                val blockedByUser = item.userId != null && rule.blockedUsers.contains(item.userId)
                if (blockedByUser) {
                    return@DanmakuFilterNode DanmakuFilterResult.Rejected(
                        reason = DanmakuFilterBlockReason.UserBlocked,
                        detail = "userId=${item.userId}",
                    )
                }
                DanmakuFilterResult.Accepted
            }
        }

        private fun hitBlockedKeyword(text: String, blockedKeywords: Set<String>): String? {
            if (blockedKeywords.isEmpty()) return null
            val normalized = text.lowercase(Locale.ROOT)
            return blockedKeywords.firstOrNull { keyword ->
                keyword.isNotBlank() && normalized.contains(keyword.lowercase(Locale.ROOT))
            }
        }
    }
}
