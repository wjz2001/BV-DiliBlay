package dev.aaa1115910.bv.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.block.BlockManager
import dev.aaa1115910.bv.relation.RelationRefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BlockSettingViewModel : ViewModel() {
    private var updateJob: Job? = null

    fun updateByUser(
        onStart: () -> Unit,
        onResult: (RelationRefreshResult) -> Unit,
        onFailure: (Throwable) -> Unit,
        onFinish: () -> Unit
    ) {
        onStart()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = BlockManager.updateByUser()
                withContext(Dispatchers.Main) { onResult(result) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onFailure(t) }
            } finally {
                withContext(Dispatchers.Main) {
                    updateJob = null
                    onFinish()
                }
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
    }
}
