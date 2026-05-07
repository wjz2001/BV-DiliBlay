package dev.aaa1115910.bv.viewmodel.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.component.settings.UpdateStatus
import dev.aaa1115910.bv.network.GithubApi
import dev.aaa1115910.bv.network.entity.Release
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.content.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class UpdateViewModel : ViewModel() {
    private val logger = KotlinLogging.logger("UpdateDialog")

    var updateStatus by mutableStateOf(UpdateStatus.UpdatingInfo)
        private set
    var bytesSentTotal: Long by mutableLongStateOf(0L)
        private set
    var contentLength: Long by mutableLongStateOf(0L)
        private set
    var targetProgress by mutableFloatStateOf(0f)
        private set
    var latestReleaseBuild by mutableStateOf<Release?>(null)
        private set

    private var downloadJob: Job? = null

    fun resetStatus() {
        updateStatus = UpdateStatus.UpdatingInfo
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun checkUpdate() {
        updateStatus = UpdateStatus.UpdatingInfo
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val release = GithubApi.getLatestBuild()
                withContext(Dispatchers.Main) {
                    latestReleaseBuild = release
                }
                val revision = release
                    .assets.first { it.name.startsWith("BV") }
                    .name.split("_")[1].toInt()
                if (revision <= BuildConfig.VERSION_CODE) {
                    withContext(Dispatchers.Main) {
                        updateStatus = UpdateStatus.NoAvailableUpdate
                    }
                    return@launch
                }
            }.onFailure {
                logger.fException(it) { "Failed to get latest version" }
                withContext(Dispatchers.Main) {
                    updateStatus = UpdateStatus.CheckError
                }
            }.onSuccess {
                logger.fInfo { "Find latest version ${latestReleaseBuild!!.name}" }
                withContext(Dispatchers.Main) {
                    updateStatus = UpdateStatus.Ready
                }
            }
        }
    }

    fun startUpdate(cacheDir: File, show: Boolean, installUpdate: (File) -> Unit) {
        updateStatus = UpdateStatus.Downloading
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val tempFilename = latestReleaseBuild!!.assets.first { it.name.startsWith("BV") }.name
            val tempDir = File(cacheDir, "update_downloader")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, tempFilename)
            tempFile.createNewFile()
            runCatching {
                GithubApi.downloadUpdate(
                    latestReleaseBuild!!,
                    tempFile,
                    object : ProgressListener {
                        override suspend fun onProgress(downloaded: Long, total: Long?) {
                            withContext(Dispatchers.Main) {
                                bytesSentTotal = downloaded
                                contentLength = total ?: 0
                                targetProgress =
                                    runCatching { bytesSentTotal.toFloat() / contentLength }
                                        .getOrDefault(0f)
                            }
                        }
                    })
                if (show) {
                    withContext(Dispatchers.Main) { installUpdate(tempFile) }
                }
            }.onFailure {
                logger.fException(it) { "Failed to download update" }
                withContext(Dispatchers.Main) {
                    updateStatus = UpdateStatus.DownloadError
                }
            }
        }
    }

    fun setInstallError() {
        updateStatus = UpdateStatus.InstallError
    }

    fun setInstalling() {
        updateStatus = UpdateStatus.Installing
    }
}
