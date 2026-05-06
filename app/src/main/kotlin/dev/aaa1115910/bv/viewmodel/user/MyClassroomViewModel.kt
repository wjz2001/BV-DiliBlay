package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.cheese.PurchasedCourse
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.CheeseRepository
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import kotlin.coroutines.cancellation.CancellationException

@KoinViewModel
class MyClassroomViewModel(
    private val cheeseRepository: CheeseRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    val courses = mutableStateListOf<PurchasedCourse>()
    var noMore by mutableStateOf(false)
        private set
    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    var lastFailureWasAuth by mutableStateOf(false)
        private set

    private var pageNumber = 1
    private val pageSize = 20
    private var updating = false
    private var updateJob: Job? = null
    @Volatile private var requestGeneration = 0L

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        loadMore()
    }

    fun reloadAll() {
        requestGeneration++
        updateJob?.cancel()
        pageNumber = 1
        updating = false
        noMore = false
        courses.clear()
        lastFailureWasAuth = false
        initialLoadState = LoadState.Loading
        loadMore()
    }

    fun clearData() {
        requestGeneration++
        updateJob?.cancel()
        pageNumber = 1
        updating = false
        noMore = false
        courses.clear()
        lastFailureWasAuth = false
        initialLoadState = LoadState.Idle
    }

    fun loadMore() {
        if (updateJob?.isActive == true) return
        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateData(expectedGeneration)
        }
    }

    private suspend fun updateData(expectedGeneration: Long) {
        if (expectedGeneration != requestGeneration) return
        if (updating || noMore) return

        updating = true
        try {
            lastFailureWasAuth = false
            val page = cheeseRepository.getPurchasedCourses(
                pageNumber = pageNumber,
                pageSize = pageSize
            )

            if (expectedGeneration != requestGeneration) return

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                courses.addAll(page.courses)
                pageNumber++
                noMore = !page.next || courses.size >= page.total
                lastFailureWasAuth = false
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }
        } catch (_: CancellationException) {
            logger.fInfo { "Update purchased courses canceled" }
        } catch (t: Throwable) {
            logger.fInfo { "Update purchased courses failed: ${t.stackTraceToString()}" }
            if (expectedGeneration != requestGeneration) return

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                lastFailureWasAuth = t is AuthFailureException
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Error
                }
                if (lastFailureWasAuth && !BuildConfig.DEBUG) {
                    userRepository.logout()
                }
            }
        } finally {
            if (expectedGeneration == requestGeneration) {
                updating = false
            }
        }
    }
}
