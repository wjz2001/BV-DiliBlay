package dev.aaa1115910.bv.network

import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.LogCatcherUtil
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.FileNotFoundException

object HttpServer {
    var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun startServer() {
        server = embeddedServer(CIO, port = 0) {
            homeModule()
            logsUiStaticModule()
            logsApiModule()
        }
        server?.start(wait = false)
    }

    fun stopServer() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        server = null
    }

    private fun Application.homeModule() {
        routing {
            // 打开网页只需要 http://ip:port/ ，直接返回日志管理首页
            get("/") {
                val bytes = readAssetBytesOrNull("logs_ui/index.html")
                    ?: return@get call.respondText(
                        text = "logs_ui/index.html not found in assets",
                        status = HttpStatusCode.NotFound
                    )
                call.respondBytes(bytes, contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
            }
        }
    }

    private fun Application.logsUiStaticModule() {
        routing {
            // 静态资源：/logs_ui/xxx 从 assets/logs_ui/xxx 读取
            get("/logs_ui/{path...}") {
                val segments = call.parameters.getAll("path").orEmpty()
                val relPath = segments.joinToString("/").ifBlank { "index.html" }

                // 简单防穿越：拒绝 .. 和 Windows 分隔符
                if (relPath.contains("..") || relPath.contains("\\")) {
                    return@get call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                }

                val assetPath = "logs_ui/$relPath"
                val bytes = readAssetBytesOrNull(assetPath)
                    ?: return@get call.respondText(
                        text = "not found",
                        status = HttpStatusCode.NotFound
                    )
                call.respondBytes(bytes, contentType = contentTypeFor(relPath))
            }
        }
    }

    private fun Application.logsApiModule() {
        routing {
            // 1) 旧接口保留：下载日志文件，但增加白名单校验（失败 403）
            get("/api/logs/{filename}") {
                val filename =
                    call.parameters["filename"] ?: return@get call.respondText(
                        text = "filename is null",
                        status = HttpStatusCode.NotFound
                    )

                // 拒绝路径穿越
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return@get call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                }

                // 仅允许 logs_manual_*.log / logs_crash_*.log
                val allowedPrefix =
                    filename.startsWith("logs_manual_") || filename.startsWith("logs_crash_")
                val allowedSuffix = filename.endsWith(".log")
                if (!allowedPrefix || !allowedSuffix) {
                    return@get call.respondText("forbidden", status = HttpStatusCode.Forbidden)
                }

                LogCatcherUtil.updateLogFiles()
                val file = (LogCatcherUtil.crashFiles + LogCatcherUtil.manualFiles)
                    .find { it.name == filename } ?: return@get call.respondText(
                    text = "file not found",
                    status = HttpStatusCode.NotFound
                )

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        file.name
                    ).toString()
                )
                call.respondFile(file)
            }

            // 2) 新增：列出日志文件 JSON
            get("/api/logs/list") {
                LogCatcherUtil.updateLogFiles()
                val manual = LogCatcherUtil.manualFiles
                val crash = LogCatcherUtil.crashFiles

                val items = (manual + crash)
                    .sortedByDescending { it.lastModified() }
                    .map { file ->
                        val type = when {
                            file.name.startsWith("logs_manual_") -> "manual"
                            file.name.startsWith("logs_crash_") -> "crash"
                            else -> "unknown"
                        }
                        LogItem(
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            type = type
                        )
                    }

                call.respondText(
                    text = itemsToJson(items),
                    contentType = ContentType.Application.Json
                )
            }

            // 3) 新增：手动保存并立刻下载
            get("/api/logs/create-manual-and-download") {
                val file = LogCatcherUtil.logLogcat(manual = true)
                if (file == null || !file.exists()) {
                    return@get call.respondText(
                        text = "create manual log failed",
                        status = HttpStatusCode.InternalServerError
                    )
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        file.name
                    ).toString()
                )
                call.respondFile(file)
            }
        }
    }

    private data class LogItem(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val type: String
    )

    private fun itemsToJson(items: List<LogItem>): String {
        return buildString {
            append('[')
            for (i in items.indices) {
                val it = items[i]
                if (i != 0) append(',')
                append('{')
                append("\"name\":\"").append(jsonEscape(it.name)).append("\",")
                append("\"size\":").append(it.size).append(',')
                append("\"lastModified\":").append(it.lastModified).append(',')
                append("\"type\":\"").append(jsonEscape(it.type)).append('"')
                append('}')
            }
            append(']')
        }
    }

    private fun jsonEscape(s: String): String {
        return buildString(s.length + 16) {
            for (ch in s) {
                when (ch) {
                    '\\' -> append("\\\\")   // 匹配单反斜杠，追加 "\" 和 "\"
                    '"'  -> append("\\\"")   // 匹配双引号，追加 "\" 和 """
                    '\n' -> append("\\n")    // 匹配换行，追加 "\" 和 "n"
                    '\r' -> append("\\r")    // 匹配回车，追加 "\" 和 "r"
                    '\t' -> append("\\t")    // 匹配制表符，追加 "\" 和 "t"
                    '\b' -> append("\\b")    // 匹配退格符，追加 "\" 和 "b"
                    '\u000C' -> append("\\f") // 匹配换页符
                    else -> append(ch)
                }
            }
        }
    }

    private fun readAssetBytesOrNull(assetPath: String): ByteArray? {
        return runCatching {
            BVApp.context.assets.open(assetPath).use { it.readBytes() }
        }.recoverCatching { e ->
            // assets.open 不存在通常会抛 FileNotFoundException
            if (e is FileNotFoundException) null else throw e
        }.getOrNull()
    }

    private fun contentTypeFor(path: String): ContentType {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> ContentType.Text.Html.withCharset(Charsets.UTF_8)
            "css" -> ContentType.Text.CSS.withCharset(Charsets.UTF_8)
            "js" -> ContentType.Application.JavaScript.withCharset(Charsets.UTF_8)
            "png" -> ContentType.Image.PNG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "svg" -> ContentType.Image.SVG
            "ico" -> ContentType.Image.XIcon
            else -> ContentType.Application.OctetStream
        }
    }
}