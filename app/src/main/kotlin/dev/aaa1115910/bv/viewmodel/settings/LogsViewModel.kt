package dev.aaa1115910.bv.viewmodel.settings

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.network.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import qrcode.QRCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface

@KoinViewModel
class LogsViewModel : ViewModel() {
    var fileQrImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var serverQrImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var resolvedPort by mutableIntStateOf(0)
        private set

    private var fileQrJob: Job? = null
    private var serverQrJob: Job? = null

    fun clearFileQr() {
        fileQrJob?.cancel()
        fileQrImage = null
    }

    fun generateFileQr(host: String, port: Int, filename: String?) {
        fileQrJob?.cancel()
        fileQrImage = null
        if (filename == null) return
        fileQrJob = viewModelScope.launch(Dispatchers.IO) {
            val image = generateQrImage("http://$host:$port/api/logs/$filename")
            withContext(Dispatchers.Main) {
                fileQrImage = image
            }
        }
    }

    fun waitPortAndGenerateServerQr(host: String) {
        serverQrJob?.cancel()
        serverQrImage = null
        serverQrJob = viewModelScope.launch(Dispatchers.IO) {
            var resolvedHost = host
            var hostRetry = 0
            while ((resolvedHost.isBlank() || resolvedHost == "x.x.x.x") && hostRetry < 50) {
                delay(100)
                resolvedHost = resolveWifiIpAddress()
                hostRetry++
            }

            var port = HttpServer.server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
            var portRetry = 0
            while (port == 0 && portRetry < 50) {
                delay(100)
                port = HttpServer.server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
                portRetry++
            }

            if (port != 0) {
                val image = generateQrImage("http://$resolvedHost:$port/")
                withContext(Dispatchers.Main) {
                    resolvedPort = port
                    serverQrImage = image
                }
            }
        }
    }

    fun cancelServerQr() {
        serverQrJob?.cancel()
        serverQrJob = null
    }

    private fun generateQrImage(url: String): ImageBitmap {
        val output = ByteArrayOutputStream()
        QRCode(url).render().writeImage(output)
        val input = ByteArrayInputStream(output.toByteArray())
        return BitmapFactory.decodeStream(input).asImageBitmap()
    }

    private fun resolveWifiIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true)) {
                    val addresses = intf.inetAddresses
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return@runCatching addr.hostAddress ?: ""
                        }
                    }
                }
            }
            ""
        }.getOrDefault("")
    }
}
