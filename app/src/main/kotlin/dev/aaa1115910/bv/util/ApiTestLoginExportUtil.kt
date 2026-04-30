package dev.aaa1115910.bv.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.entity.AuthData
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class ApiTestLoginExportPayload(
    val authData: AuthData,
    val rawResponseJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ApiTestLoginExportUtil {
    private const val FILE_PREFIX = "扫码登录测试用全量数据"
    private const val MIME_TYPE_JSON = "application/json"
    private val json = Json { prettyPrint = true }

    fun requiresLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P

    fun exportToDownloads(
        context: Context,
        payload: ApiTestLoginExportPayload
    ): Result<String> = runCatching {
        val filename = buildFilename(payload.timestamp)
        val content = json.encodeToString(payload.toExportFile())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, filename, content)
            "Download/$filename"
        } else {
            writeToLegacyDownloads(filename, content)
        }
    }

    private fun buildFilename(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return "${FILE_PREFIX}_${formatter.format(Date(timestamp))}.json"
    }

    fun copyFileToDownloads(
        context: Context,
        sourceFile: File,
        subDir: String? = null
    ): Result<String> = runCatching {
        require(sourceFile.exists()) { "源文件不存在: ${sourceFile.absolutePath}" }
        val relativePath = buildDownloadsRelativePath(subDir)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileWithMediaStore(
                context = context,
                sourceFile = sourceFile,
                relativePath = relativePath
            )
            "$relativePath/${sourceFile.name}"
        } else {
            copyFileToLegacyDownloads(
                sourceFile = sourceFile,
                subDir = subDir
            )
        }
    }

    private fun ApiTestLoginExportPayload.toExportFile(): ApiTestLoginExportFile {
        val rawResponse = runCatching { json.decodeFromString<JsonElement>(rawResponseJson) }
            .getOrElse { JsonPrimitive(rawResponseJson) }
        return ApiTestLoginExportFile(
            purpose = "测试",
            description = "扫码登录测试用全量数据，仅供接口测试使用",
            buildType = BuildConfig.BUILD_TYPE,
            flavorName = BuildConfig.FLAVOR,
            applicationId = BuildConfig.APPLICATION_ID,
            exportTimestamp = timestamp,
            exportTime = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date(timestamp)),
            authData = authData,
            rawResponse = rawResponse
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(
        context: Context,
        filename: String,
        content: String
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_JSON)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建下载文件")
        resolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
            checkNotNull(writer) { "无法打开下载文件输出流" }
            writer.write(content)
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyFileWithMediaStore(
        context: Context,
        sourceFile: File,
        relativePath: String
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建下载文件")
        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法打开下载文件输出流")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    @Suppress("DEPRECATION")
    private fun writeToLegacyDownloads(filename: String, content: String): String {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, filename)
        file.writeText(content)
        return file.absolutePath
    }

    @Suppress("DEPRECATION")
    private fun copyFileToLegacyDownloads(sourceFile: File, subDir: String?): String {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (subDir.isNullOrBlank()) downloadsDir else File(downloadsDir, subDir)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, sourceFile.name)
        sourceFile.copyTo(targetFile, overwrite = true)
        return targetFile.absolutePath
    }

    private fun buildDownloadsRelativePath(subDir: String?): String {
        return if (subDir.isNullOrBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
        }
    }
}

@Serializable
private data class ApiTestLoginExportFile(
    val purpose: String,
    val description: String,
    val buildType: String,
    val flavorName: String,
    val applicationId: String,
    val exportTimestamp: Long,
    val exportTime: String,
    val authData: AuthData,
    val rawResponse: JsonElement
)
