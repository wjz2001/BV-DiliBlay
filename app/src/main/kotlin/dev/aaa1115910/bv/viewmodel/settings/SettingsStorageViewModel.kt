package dev.aaa1115910.bv.viewmodel.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.util.LogCatcherUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class SettingsStorageViewModel : ViewModel() {
    private val logger = KotlinLogging.logger { }

    var loading by mutableStateOf(false)
        private set
    var imageCacheSize by mutableLongStateOf(0L)
        private set
    var updateCacheSize by mutableLongStateOf(0L)
        private set
    var crashLogsSize by mutableLongStateOf(0L)
        private set

    fun refresh(cacheDir: File, filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { loading = true }
            refreshSizes(cacheDir, filesDir)
        }
    }

    fun clearImageCaches(cacheDir: File, filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            logger.info { "clearImageCaches" }
            File(cacheDir, "image_cache").deleteRecursively()
            refreshSizes(cacheDir, filesDir)
        }
    }

    fun clearOthersCaches(cacheDir: File, filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            logger.info { "clearOthersCaches" }
            File(cacheDir, "update_downloader").deleteRecursively()
            refreshSizes(cacheDir, filesDir)
        }
    }

    fun clearCrashLogs(cacheDir: File, filesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            logger.info { "clearCrashLogs" }
            File(filesDir, LogCatcherUtil.LOG_DIR).deleteRecursively()
            refreshSizes(cacheDir, filesDir)
        }
    }

    private suspend fun refreshSizes(cacheDir: File, filesDir: File) {
        val imageSize = getFolderSize(File(cacheDir, "image_cache"))
        val updateSize = getFolderSize(File(cacheDir, "update_downloader"))
        val logsSize = getFolderSize(File(filesDir, LogCatcherUtil.LOG_DIR))
        withContext(Dispatchers.Main) {
            imageCacheSize = imageSize
            updateCacheSize = updateSize
            crashLogsSize = logsSize
            loading = false
        }
    }

    private fun getFolderSize(f: File): Long {
        if (!f.exists()) return 0L
        if (!f.isDirectory) return f.length()
        return f.listFiles()?.sumOf { getFolderSize(it) } ?: 0L
    }
}
