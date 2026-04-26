package dev.aaa1115910.bv.player.danmaku.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface DanmakuCollection<out T : DanmakuCoreItem> {
    val totalCount: Int

    val totalCountFlow: Flow<Int?>
        get() = flowOf(totalCount)

    fun snapshot(): List<T>

    fun coreSnapshot(): List<DanmakuCoreInfo> {
        return snapshot().map { item -> item.coreInfo }
    }

    fun window(range: DanmakuTimeRange): List<T> {
        return snapshot().filter { item ->
            range.contains(item.timeMs.toLong())
        }
    }

    fun coreWindow(range: DanmakuTimeRange): List<DanmakuCoreInfo> {
        return window(range).map { item -> item.coreInfo }
    }
}

interface MutableDanmakuCollection<T : DanmakuCoreItem> : DanmakuCollection<T> {
    fun replace(items: List<T>)

    fun append(items: List<T>)

    fun trimTo(range: DanmakuTimeRange, maxCount: Int = Int.MAX_VALUE): Int

    fun clear()
}

class InMemoryDanmakuCollection<T : DanmakuCoreItem>(
    items: List<T> = emptyList(),
) : MutableDanmakuCollection<T> {
    private val items = items.sortedByTime().toMutableList()

    override val totalCount: Int
        get() = items.size

    override fun snapshot(): List<T> {
        return items.toList()
    }

    override fun replace(items: List<T>) {
        this.items.clear()
        this.items += items.sortedByTime()
    }

    override fun append(items: List<T>) {
        if (items.isEmpty()) return
        this.items += items
        this.items.sortWith(DanmakuItemTimeComparator)
    }

    override fun trimTo(range: DanmakuTimeRange, maxCount: Int): Int {
        val beforeCount = items.size
        items.removeAll { item -> range.contains(item.timeMs.toLong()).not() }
        val boundedMaxCount = maxCount.coerceAtLeast(0)
        if (items.size > boundedMaxCount) {
            items.subList(0, items.size - boundedMaxCount).clear()
        }
        return beforeCount - items.size
    }

    override fun clear() {
        items.clear()
    }
}

private val DanmakuItemTimeComparator = compareBy<DanmakuCoreItem>(
    { it.timeMs },
    { it.arrivalTimeMs },
    { it.id },
    { it.text },
)

private fun <T : DanmakuCoreItem> List<T>.sortedByTime(): List<T> {
    return sortedWith(DanmakuItemTimeComparator)
}
