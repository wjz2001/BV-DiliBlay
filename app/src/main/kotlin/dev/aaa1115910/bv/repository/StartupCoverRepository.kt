package dev.aaa1115910.bv.repository

object StartupCoverRepository {
    private const val MaxCacheSize = 64

    private val cache = object : LinkedHashMap<Long, String>(MaxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean {
            return size > MaxCacheSize
        }
    }

    @Synchronized
    fun put(aid: Long, cover: String) {
        if (aid <= 0L || cover.isBlank()) return
        cache[aid] = cover
    }

    @Synchronized
    operator fun get(aid: Long): String {
        if (aid <= 0L) return ""
        return cache[aid].orEmpty()
    }

    @Synchronized
    fun clear(aid: Long) {
        if (aid <= 0L) return
        cache.remove(aid)
    }
}
