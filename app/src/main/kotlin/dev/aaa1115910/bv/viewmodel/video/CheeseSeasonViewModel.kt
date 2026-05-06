package dev.aaa1115910.bv.viewmodel.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.cheese.CheeseSeasonDetail
import dev.aaa1115910.biliapi.repositories.CheeseRepository
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.common.LoadState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import kotlin.coroutines.cancellation.CancellationException

@KoinViewModel
class CheeseSeasonViewModel(
    private val cheeseRepository: CheeseRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var seasonId by mutableStateOf(0L)
        private set
    var detail by mutableStateOf<CheeseSeasonDetail?>(null)
        private set
    var loadState by mutableStateOf(LoadState.Idle)
        private set

    private var loadJob: Job? = null

    fun init(seasonId: Long) {
        if (seasonId <= 0 || this.seasonId == seasonId) return
        this.seasonId = seasonId
        load()
    }

    fun load() {
        if (seasonId <= 0) return
        loadJob?.cancel()
        loadState = LoadState.Loading
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = cheeseRepository.getCheeseSeasonDetail(seasonId = seasonId)
                withContext(Dispatchers.Main) {
                    detail = data
                    loadState = LoadState.Success
                }
            } catch (_: CancellationException) {
                logger.fInfo { "Load cheese season canceled" }
            } catch (t: Throwable) {
                logger.fInfo { "Load cheese season failed: ${t.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    loadState = LoadState.Error
                }
            }
        }
    }
}
