package dev.aaa1115910.biliapi.metrics

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.core.annotation.Single
import kotlin.math.min

@Single
class VideoMetricsGlobalConcurrencyLimiter(
    private val totalConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    private val deferredConcurrency: Int = defaultDeferredConcurrency(totalConcurrency),
    private val onPermitEntered: ((VideoMetricsPriority) -> Unit)? = null
) {
    init {
        require(totalConcurrency > 0) { "totalConcurrency must be > 0" }
        require(deferredConcurrency in 0 until totalConcurrency) {
            "deferredConcurrency must be in [0, totalConcurrency)"
        }
    }

    suspend fun <T> withPermit(
        priority: VideoMetricsPriority,
        block: suspend () -> T
    ): T {
        return when (priority) {
            VideoMetricsPriority.VISIBLE -> totalSemaphore.withPermit {
                onPermitEntered?.invoke(priority)
                block()
            }
            VideoMetricsPriority.PREFETCH,
            VideoMetricsPriority.BACKGROUND -> deferredSemaphore.withPermit {
                totalSemaphore.withPermit {
                    onPermitEntered?.invoke(priority)
                    block()
                }
            }
        }
    }

    fun maxConcurrency(): Int = totalConcurrency

    fun maxDeferredConcurrency(): Int = deferredConcurrency

    companion object {
        const val DEFAULT_MAX_CONCURRENCY: Int = 5
        const val RESERVED_VISIBLE_SLOTS: Int = 1

        fun defaultDeferredConcurrency(totalConcurrency: Int): Int {
            return min(totalConcurrency - RESERVED_VISIBLE_SLOTS, totalConcurrency - 1)
        }
    }

    private val totalSemaphore = Semaphore(totalConcurrency)
    private val deferredSemaphore = Semaphore(deferredConcurrency)
}
