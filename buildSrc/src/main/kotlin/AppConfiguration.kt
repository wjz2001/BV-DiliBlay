import java.io.File

object AppConfiguration {
    const val APP_ID = "dev.aaa1115910.bv"
    const val APPLICATION_ID = "dev.frost819.bv" // 由于小米电视屏蔽原包名，此包名仅用于apk打包
    const val COMPILE_SDK = 36
    const val MIN_SDK = 23
    const val TARGET_SDK = 36

    private const val VERSION_MAJOR = 0
    private const val VERSION_MINOR = 3
    private const val VERSION_PATCH = 14
    private const val VERSION_HOTFIX = 0

    @Suppress("KotlinConstantConditions")
    val versionName: String by lazy {
        "$VERSION_MAJOR.$VERSION_MINOR.$VERSION_PATCH${".$VERSION_HOTFIX".takeIf { VERSION_HOTFIX != 0 } ?: ""}" +
                ".r${versionCode}.${"git rev-list HEAD --abbrev-commit --max-count=1".exec()}"
    }
    val versionCode: Int by lazy { "git rev-list --count HEAD".exec().toInt() }
    const val LIB_VLC_VERSION = "3.0.18"
    var googleServicesAvailable = true

    init {
        initConfigurations()
    }

    private fun initConfigurations() {
        val googleServicesJsonPath = File(System.getProperty("user.dir"), "app/google-services.json").absolutePath
        val googleServicesJsonFile = File(googleServicesJsonPath)
        googleServicesAvailable =
            googleServicesJsonFile.exists() && googleServicesJsonFile.readText().let {
                it.contains(APPLICATION_ID) && it.contains("$APPLICATION_ID.r8test") && it.contains("$APPLICATION_ID.debug")
            }
    }
}

fun String.exec(): String {
    val parts = this.trim().split(Regex("\\s+"))
    val process = ProcessBuilder(parts)
        .redirectErrorStream(true)
        .start()
    return process.inputStream.bufferedReader().readText().trim()
}