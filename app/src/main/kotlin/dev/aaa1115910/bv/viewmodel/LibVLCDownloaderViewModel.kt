package dev.aaa1115910.bv.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.network.VlcLibsApi
import dev.aaa1115910.bv.player.BuildConfig
import io.ktor.client.content.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@KoinViewModel
class LibVLCDownloaderViewModel : ViewModel() {
    var processing by mutableStateOf(false)
        private set
    var text by mutableStateOf("等待操作中...")
        private set

    fun startInstall(
        cacheDir: File,
        filesDir: File,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        processing = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                setText("正在获取下载地址")
                val release =
                    VlcLibsApi.getRelease(BuildConfig.LIB_VLC_VERSION)
                        ?: throw IllegalStateException("Release not found")
                val tempFilename = "${UUID.randomUUID()}.zip"
                val tempDir = File(cacheDir, "libvlc_downloader")
                if (!tempDir.exists()) tempDir.mkdirs()
                val tempFile = File(tempDir, tempFilename)
                tempFile.createNewFile()

                VlcLibsApi.downloadFile(
                    release,
                    tempFile,
                    object : ProgressListener {
                        override suspend fun onProgress(downloaded: Long, total: Long?) {
                            setText("正在下载(${downloaded / (total?.toFloat() ?: 0f) * 100}%)")
                        }
                    })

                setText("正在解压")
                unZipLibs(filesDir, tempFile)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    text = "安装完成"
                    processing = false
                    onSuccess()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    text = "安装失败"
                    processing = false
                    onFailure(it)
                }
                it.printStackTrace()
            }
        }
    }

    private suspend fun setText(value: String) {
        withContext(Dispatchers.Main) { text = value }
    }

    private fun unZipLibs(filesDir: File, zipFile: File) {
        val vlcLibsDir = File(filesDir, "vlc_libs")
        vlcLibsDir.mkdir()
        vlcLibsDir.listFiles()?.forEach { file ->
            file.delete()
        }

        ZipInputStream(zipFile.inputStream())
            .use { zipInputStream ->
                generateSequence { zipInputStream.nextEntry }
                    .map {
                        UnzippedFile(
                            filename = it.name,
                            content = zipInputStream.readBytes()
                        )
                    }.toList()
            }
            .forEach {
                println("Extracting ${it.filename}")
                val file = File(vlcLibsDir, it.filename)
                file.createNewFile()
                file.writeBytes(it.content)
            }
    }

    private data class UnzippedFile(
        val filename: String,
        val content: ByteArray
    )
}
