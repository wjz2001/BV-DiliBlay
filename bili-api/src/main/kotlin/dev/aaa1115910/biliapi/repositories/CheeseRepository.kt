package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.cheese.CheeseEpisode
import dev.aaa1115910.biliapi.entity.cheese.CheeseBriefImage
import dev.aaa1115910.biliapi.entity.cheese.CheeseSeasonDetail
import dev.aaa1115910.biliapi.entity.cheese.PurchasedCourse
import dev.aaa1115910.biliapi.entity.cheese.PurchasedCoursePage
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.cheese.CheesePaidData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.annotation.Single

@Single
class CheeseRepository(
    private val authRepository: AuthRepository
) {
    private val purchasedCourseAuthorCache = mutableMapOf<Long, Pair<String, Long?>>()

    private val json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun getPurchasedCourses(
        pageNumber: Int = 1,
        pageSize: Int = 20
    ): PurchasedCoursePage {
        val raw = BiliHttpApi.getPurchasedCoursesRaw(
            pageNumber = pageNumber,
            pageSize = pageSize,
            sessData = authRepository.sessionData.orEmpty()
        )
        val response = json.decodeFromString<BiliResponse<CheesePaidData>>(raw)
        val data = response.getResponseData()
        val courses = data.items.mapNotNull { it.toPurchasedCourse() }
        return PurchasedCoursePage(
            courses = fillPurchasedCourseAuthors(courses),
            next = data.next,
            total = data.total,
            pageNumber = data.pn,
            pageSize = data.ps,
            rawJson = raw
        )
    }

    suspend fun getCheeseSeasonDetail(
        seasonId: Long? = null,
        epId: Long? = null
    ): CheeseSeasonDetail {
        val raw = BiliHttpApi.getCheeseSeasonRaw(
            seasonId = seasonId,
            epId = epId,
            sessData = authRepository.sessionData.orEmpty()
        )
        val response = json.decodeFromString<BiliResponse<JsonObject>>(raw)
        val data = response.getResponseData()
        return data.toCheeseSeasonDetail(raw)
    }

    private suspend fun fillPurchasedCourseAuthors(courses: List<PurchasedCourse>): List<PurchasedCourse> {
        courses
            .map { it.seasonId }
            .distinct()
            .filterNot { purchasedCourseAuthorCache.containsKey(it) }
            .forEach { seasonId ->
                runCatching {
                    getCheeseSeasonDetail(seasonId = seasonId)
                }.getOrNull()
                    ?.let { detail ->
                        purchasedCourseAuthorCache[seasonId] = detail.upName to detail.upMid
                    }
            }

        return courses.map { course ->
            val author = purchasedCourseAuthorCache[course.seasonId]
                ?: return@map course.copy(upName = "", upMid = null)
            val (upName, upMid) = author
            course.copy(
                upName = upName,
                upMid = upMid
            )
        }
    }

    private fun JsonObject.toPurchasedCourse(): PurchasedCourse? {
        val seasonId = longOrNull("season_id")
            ?: longOrNull("seasonId")
            ?: longOrNull("ssid")
            ?: longOrNull("id")
            ?: return null
        val upInfo = obj("up_info") ?: obj("upInfo")
        val epCountText = stringOrNull("release_info2")
            ?: stringOrNull("release_info")
            ?: stringOrNull("update_info")
            ?: intOrNull("ep_count")?.let { "共${it}课时" }
            ?: intOrNull("epCount")?.let { "共${it}课时" }
            ?: ""
        val progress = obj("progress") ?: obj("user_status")?.obj("progress")

        return PurchasedCourse(
            seasonId = seasonId,
            title = stringOrNull("title").orEmpty(),
            cover = stringOrNull("cover").orEmpty(),
            subtitle = stringOrNull("subtitle") ?: stringOrNull("sub_title"),
            upName = upInfo?.stringOrNull("uname")
                ?: upInfo?.stringOrNull("name")
                ?: stringOrNull("up_name")
                ?: stringOrNull("upName")
                ?: "",
            upMid = upInfo?.longOrNull("mid")
                ?: upInfo?.longOrNull("id")
                ?: longOrNull("up_mid")
                ?: longOrNull("upMid"),
            epCountText = epCountText,
            progressText = progress?.stringOrNull("last_ep_index")
        )
    }

    private fun JsonObject.toCheeseSeasonDetail(raw: String): CheeseSeasonDetail {
        val upInfo = obj("up_info")
        val userProgress = obj("user_status")?.obj("progress")
        return CheeseSeasonDetail(
            seasonId = longOrNull("season_id") ?: longOrNull("id") ?: 0L,
            title = stringOrNull("title").orEmpty(),
            cover = stringOrNull("cover").orEmpty(),
            subtitle = stringOrNull("subtitle"),
            courseContent = stringOrNull("course_content"),
            releaseInfo = stringOrNull("release_info"),
            upName = upInfo?.stringOrNull("uname")
                ?: upInfo?.stringOrNull("name")
                ?: "",
            upMid = upInfo?.longOrNull("mid"),
            briefImages = obj("brief")
                ?.array("img")
                ?.mapNotNull { (it as? JsonObject)?.toCheeseBriefImage() }
                .orEmpty(),
            episodes = array("episodes")
                ?.mapNotNull { (it as? JsonObject)?.toCheeseEpisode() }
                .orEmpty(),
            progressText = userProgress?.stringOrNull("last_ep_index"),
            lastEpId = userProgress?.longOrNull("last_ep_id"),
            rawJson = raw
        )
    }

    private fun JsonObject.toCheeseBriefImage(): CheeseBriefImage? {
        val url = stringOrNull("url") ?: return null
        return CheeseBriefImage(
            url = url,
            aspectRatio = floatOrNull("aspect_ratio")
        )
    }

    private fun JsonObject.toCheeseEpisode(): CheeseEpisode? {
        val epId = longOrNull("id") ?: longOrNull("ep_id") ?: return null
        return CheeseEpisode(
            epId = epId,
            aid = longOrNull("aid") ?: 0L,
            cid = longOrNull("cid") ?: 0L,
            index = intOrNull("index") ?: 0,
            title = stringOrNull("title").orEmpty(),
            subtitle = stringOrNull("subtitle"),
            cover = stringOrNull("cover").orEmpty(),
            duration = intOrNull("duration") ?: 0,
            play = longOrNull("play") ?: 0L,
            watched = boolOrNull("watched") ?: false,
            watchedHistory = intOrNull("watchedHistory") ?: 0,
            canView = boolOrNull("episode_can_view")
                ?: boolOrNull("playable")
                ?: ((intOrNull("status") ?: 1) == 1)
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private fun JsonObject.longOrNull(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.floatOrNull(key: String): Float? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
