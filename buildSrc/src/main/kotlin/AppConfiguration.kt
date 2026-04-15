@file:Suppress("NewApi")
import java.io.File
import java.util.Properties
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object AppConfiguration {
    const val APP_ID = "dev.aaa1115910.bv"
    const val APPLICATION_ID = "dev.frost819.bv" // 由于小米电视屏蔽原包名，此包名仅用于apk打包
    const val COMPILE_SDK = 36
    const val MIN_SDK = 23
    const val TARGET_SDK = 36

    const val LIB_VLC_VERSION = "3.0.18"
    var googleServicesAvailable = true

    private val rootDir: File by lazy { File(System.getProperty("user.dir")) }
    private val versionPropsFile: File by lazy { File(rootDir, "version.properties") }

    private fun loadProps(): Properties =
        Properties().apply { versionPropsFile.inputStream().use { load(it) } }

    private fun storeProps(p: Properties) {
        versionPropsFile.writer().use { p.store(it, "Updated by build") }
    }

    private val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai") // 北京时间（中国标准时间）
    private fun timeCandidate(p: Properties, now: Instant = Instant.now()): Int {
        val baseCode = p.getProperty("BASE_VERSION_CODE").toInt()
        val baseInstant = Instant.parse(p.getProperty("BASE_INSTANT_UTC"))

        // 按北京时间口径计算“相差小时数”（每小时 +1）
        val hours = ChronoUnit.HOURS.between(
            baseInstant.atZone(BEIJING_ZONE),
            now.atZone(BEIJING_ZONE)
        )

        // 按北京时间取分钟，并按 6 分钟一档分桶：0..9
        val minuteBucket = now.atZone(BEIJING_ZONE).minute / 6

        val candidate = baseCode + hours * 10 + minuteBucket
        if (candidate > 2_100_000_000L) error("versionCode overflow: $candidate")
        return candidate.toInt()
    }

    /** 只读：不落盘（适合 debug/日常构建） */
    fun peekVersionCode(): Int {
        val p = loadProps()
        val last = p.getProperty("LAST_VERSION_CODE").toInt()
        val candidate = timeCandidate(p)
        return maxOf(candidate, last + 1)
    }

    /**
     * 预留一个版本号：只增不减，并写回 LAST_VERSION_CODE（适合 release 打包前调用）
     * 这样就算系统时间回拨，也不会出现变小。
     */
    fun reserveVersionCode(): Int {
        val p = loadProps()
        val last = p.getProperty("LAST_VERSION_CODE").toInt()
        val candidate = timeCandidate(p)

        val chosen = maxOf(candidate, last + 1)

        p.setProperty("LAST_VERSION_CODE", chosen.toString())
        storeProps(p)
        return chosen
    }

    /** versionName 直接使用外部传入的 vc，保证同一次构建绝对一致 */
    private const val HOTFIX_SUFFIX: String = "" // 例如 "", "a", "b", "c"
    private fun hotfixSuffix(): String = HOTFIX_SUFFIX.trim().takeIf { it.isNotEmpty() } ?: ""
    fun buildVersionName(vc: Int): String {
        val dt = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        val datePart = dt.format(DateTimeFormatter.ofPattern("yy.MM.dd")) // 例如 26.04.13
        val gitHash = "git rev-parse --short HEAD".exec().trim()

        return "v$datePart${hotfixSuffix()}.r$vc.$gitHash"
    }

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