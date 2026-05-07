package dev.aaa1115910.bv.viewmodel.pgc

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.season.Timeline
import dev.aaa1115910.biliapi.entity.season.TimelineFilter
import dev.aaa1115910.biliapi.repositories.SeasonRepository
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AnimeTimelineViewModel(
    private val seasonRepository: SeasonRepository
) : ViewModel() {
    val timelines = mutableStateListOf<Timeline>()

    fun loadTimeline(
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                seasonRepository.getTimeline(
                    filter = TimelineFilter.Anime,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    timelines.clear()
                    timelines.addAll(result)
                    onSuccess()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onFailure(it)
                }
            }
        }
    }
}
