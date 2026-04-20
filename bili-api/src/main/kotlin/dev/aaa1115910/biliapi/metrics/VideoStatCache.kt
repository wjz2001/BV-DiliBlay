package dev.aaa1115910.biliapi.metrics

import java.util.concurrent.ConcurrentHashMap

object VideoStatCache {
    const val DEFAULT_TTL_MS: Long = 12 * 60 * 60 * 1000L

    private sealed interface CacheKey {
        data class Aid(val value: Long) : CacheKey
        data class Bvid(val value: String) : CacheKey
    }

    private data class CacheEntry(
        val envelope: StatEnvelope,
        val cachedAt: Long,
        val ttlMs: Long
    ) {
        val expireAt: Long = cachedAt + ttlMs

        fun isFresh(nowMs: Long): Boolean = nowMs < expireAt
    }

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()
    private val bvidAliasToAid = ConcurrentHashMap<String, Long>()

    fun getFreshOrNull(
        aid: Long? = null,
        bvid: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): StatEnvelope? {
        val entry = resolveEntry(aid = aid, bvid = bvid) ?: return null
        return entry.takeIf { it.isFresh(nowMs) }?.toEnvelope(
            cacheStatus = CanonicalCacheStatus.HIT
        )
    }

    fun getStaleOk(
        aid: Long? = null,
        bvid: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): StatEnvelope? {
        val entry = resolveEntry(aid = aid, bvid = bvid) ?: return null
        val cacheStatus = if (entry.isFresh(nowMs)) {
            CanonicalCacheStatus.HIT
        } else {
            CanonicalCacheStatus.STALE
        }
        return entry.toEnvelope(cacheStatus = cacheStatus)
    }

    fun put(
        envelope: StatEnvelope,
        ttlMs: Long = DEFAULT_TTL_MS,
        nowMs: Long = System.currentTimeMillis()
    ) {
        require(ttlMs >= 0L) { "ttlMs must be >= 0" }

        val normalizedBvid = envelope.bvid.normalizeBvidOrNull()
        val stat = envelope.stat.copy(
            source = CanonicalSource.CACHE,
            cacheStatus = CanonicalCacheStatus.HIT,
            ttlMs = ttlMs,
            expireAt = nowMs + ttlMs
        )
        val cachedEnvelope = envelope.copy(
            bvid = normalizedBvid ?: envelope.bvid,
            stat = stat
        )
        val entry = CacheEntry(
            envelope = cachedEnvelope,
            cachedAt = nowMs,
            ttlMs = ttlMs
        )

        val currentEntry = cache[CacheKey.Aid(envelope.aid)]
        val entryToStore = if (shouldKeepExisting(currentEntry, entry)) {
            currentEntry!!
        } else {
            entry
        }

        cache[CacheKey.Aid(envelope.aid)] = entryToStore
        if (normalizedBvid != null) {
            cache[CacheKey.Bvid(normalizedBvid)] = entryToStore
            bvidAliasToAid[normalizedBvid] = envelope.aid
        }
    }

    fun invalidate(
        aid: Long? = null,
        bvid: String? = null
    ) {
        val normalizedBvid = bvid.normalizeBvidOrNull()
        val resolvedAid = aid ?: normalizedBvid?.let { bvidAliasToAid[it] }

        if (resolvedAid != null) {
            cache.remove(CacheKey.Aid(resolvedAid))?.let { entry ->
                entry.envelope.bvid.normalizeBvidOrNull()?.let { cachedBvid ->
                    cache.remove(CacheKey.Bvid(cachedBvid))
                    bvidAliasToAid.remove(cachedBvid, resolvedAid)
                }
            }
        }

        if (normalizedBvid != null) {
            cache.remove(CacheKey.Bvid(normalizedBvid))
            bvidAliasToAid.remove(normalizedBvid)?.let { aliasedAid ->
                cache.remove(CacheKey.Aid(aliasedAid))
            }
        }
    }

    fun clear() {
        cache.clear()
        bvidAliasToAid.clear()
    }

    private fun resolveEntry(
        aid: Long? = null,
        bvid: String? = null
    ): CacheEntry? {
        require(aid != null || !bvid.isNullOrBlank()) {
            "aid and bvid cannot both be empty"
        }

        aid?.let { directAid ->
            cache[CacheKey.Aid(directAid)]?.let { return it }
        }

        val normalizedBvid = bvid.normalizeBvidOrNull()
        if (normalizedBvid != null) {
            cache[CacheKey.Bvid(normalizedBvid)]?.let { return it }
            bvidAliasToAid[normalizedBvid]?.let { aliasedAid ->
                cache[CacheKey.Aid(aliasedAid)]?.let { return it }
            }
        }

        return null
    }

    private fun CacheEntry.toEnvelope(
        cacheStatus: CanonicalCacheStatus
    ): StatEnvelope {
        return envelope.copy(
            stat = envelope.stat.copy(
                cacheStatus = cacheStatus,
                ttlMs = ttlMs,
                expireAt = expireAt
            )
        )
    }

    private fun shouldKeepExisting(
        existing: CacheEntry?,
        incoming: CacheEntry
    ): Boolean {
        if (existing == null) return false
        return existing.envelope.stat.precision == CanonicalPrecision.EXACT &&
            incoming.envelope.stat.precision == CanonicalPrecision.APPROX
    }

    private fun String?.normalizeBvidOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    }
}
