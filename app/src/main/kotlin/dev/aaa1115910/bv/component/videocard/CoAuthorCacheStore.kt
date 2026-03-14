package dev.aaa1115910.bv.component.videocard

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.CoAuthor
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

enum class CoAuthorFetchSource {
    CACHE,
    NETWORK,
    IN_FLIGHT
}

data class CoAuthorFetchResult(
    val authors: List<CoAuthor>,
    val source: CoAuthorFetchSource,
    val totalCostMs: Long,
    val networkCostMs: Long? = null
)

object CoAuthorCacheStore {
    private const val TTL_MS = 5 * 60 * 1000L

    private data class CacheKey(
        val avid: Long,
        val apiType: ApiType
    )

    private data class CacheEntry(
        val authors: List<CoAuthor>,
        val fetchedAtMs: Long
    )

    private data class FetchPayload(
        val authors: List<CoAuthor>,
        val networkCostMs: Long
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()
    private val activeTasks = ConcurrentHashMap<CacheKey, Deferred<FetchPayload>>()

    suspend fun getOrFetch(
        avid: Long,
        preferApiType: ApiType,
        repository: VideoDetailRepository
    ): CoAuthorFetchResult = coroutineScope {
        val totalStartMs = System.currentTimeMillis()
        val key = CacheKey(avid = avid, apiType = preferApiType)

        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.fetchedAtMs <= TTL_MS }?.let { entry ->
            return@coroutineScope CoAuthorFetchResult(
                authors = entry.authors,
                source = CoAuthorFetchSource.CACHE,
                totalCostMs = System.currentTimeMillis() - totalStartMs
            )
        }

        val existingTask = activeTasks[key]
        val task: Deferred<FetchPayload>
        val source: CoAuthorFetchSource

        if (existingTask != null) {
            task = existingTask
            source = CoAuthorFetchSource.IN_FLIGHT
        } else {
            val newTask = async(Dispatchers.IO) {
                val networkStartMs = System.currentTimeMillis()
                val authors = repository.getCoAuthors(
                    aid = avid,
                    preferApiType = preferApiType
                )
                FetchPayload(
                    authors = authors,
                    networkCostMs = System.currentTimeMillis() - networkStartMs
                )
            }

            val racedTask = activeTasks.putIfAbsent(key, newTask)
            if (racedTask == null) {
                task = newTask
                source = CoAuthorFetchSource.NETWORK
            } else {
                newTask.cancel()
                task = racedTask
                source = CoAuthorFetchSource.IN_FLIGHT
            }
        }

        try {
            val payload = task.await()
            cache[key] = CacheEntry(
                authors = payload.authors,
                fetchedAtMs = System.currentTimeMillis()
            )
            return@coroutineScope CoAuthorFetchResult(
                authors = payload.authors,
                source = source,
                totalCostMs = System.currentTimeMillis() - totalStartMs,
                networkCostMs = payload.networkCostMs
            )
        } catch (e: CancellationException) {
            throw e
        } finally {
            activeTasks.remove(key, task)
        }
    }

    fun cancelInFlight(avid: Long, apiType: ApiType) {
        val key = CacheKey(avid = avid, apiType = apiType)
        activeTasks.remove(key)?.cancel()
    }

    fun invalidate(avid: Long, apiType: ApiType) {
        cache.remove(CacheKey(avid = avid, apiType = apiType))
    }

    fun clear() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        cache.clear()
    }
}