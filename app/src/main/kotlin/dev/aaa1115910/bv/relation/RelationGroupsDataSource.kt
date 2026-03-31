package dev.aaa1115910.bv.relation

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.relation.RelationTag
import dev.aaa1115910.biliapi.http.entity.user.RelationStat
import dev.aaa1115910.biliapi.http.entity.user.UserFollowData
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.math.ceil

enum class RelationRefreshTrigger {
    FollowScreen,
    BlockSettingManual
}

data class RelationRefreshResult(
    val success: Boolean,
    val snapshot: RelationGroupSnapshot?,
    val preferredApiType: ApiType,
    val resolvedApiType: ApiType? = null,
    val usedFallback: Boolean = false,
    val error: Throwable? = null
)

object RelationGroupsDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = false
    }

    private const val PAGE_SIZE = 50

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    @Volatile
    private var currentSnapshot: RelationGroupSnapshot? = null

    @Volatile
    private var inFlightRefresh: Deferred<RelationRefreshResult>? = null

    fun decodeSnapshotOrNull(raw: String): RelationGroupSnapshot? {
        if (raw.isBlank()) return null
        return runCatching {
            sanitizeSnapshot(
                json.decodeFromString(RelationGroupSnapshot.serializer(), raw)
            )
        }.getOrNull()
    }

    fun readSnapshotFromPrefsOrNull(): RelationGroupSnapshot? {
        val raw = Prefs.followTagsCacheJson
        if (raw.isBlank()) return null

        val decoded = runCatching {
            json.decodeFromString(RelationGroupSnapshot.serializer(), raw)
        }.getOrNull() ?: return null

        val sanitized = sanitizeSnapshot(decoded)
        if (sanitized != decoded) {
            Prefs.followTagsCacheJson = json.encodeToString(
                RelationGroupSnapshot.serializer(),
                sanitized
            )
        }
        return sanitized
    }

    fun getSnapshotOrNull(): RelationGroupSnapshot? {
        if (currentSnapshot == null) {
            currentSnapshot = readSnapshotFromPrefsOrNull()
        }
        return currentSnapshot
    }

    fun hasUsableSnapshot(snapshot: RelationGroupSnapshot? = getSnapshotOrNull()): Boolean =
        snapshot != null && snapshot.groups.isNotEmpty()

    suspend fun refresh(trigger: RelationRefreshTrigger): RelationRefreshResult {
        val preferredApiType = when (trigger) {
            RelationRefreshTrigger.FollowScreen -> Prefs.apiType
            RelationRefreshTrigger.BlockSettingManual -> Prefs.apiType
        }

        val existingTask = inFlightRefresh?.takeUnless { it.isCompleted }
        val task = existingTask
            ?: refreshMutex.withLock {
                inFlightRefresh?.takeUnless { it.isCompleted } ?: refreshScope.async {
                    performRefresh(preferredApiType)
                }.also { inFlightRefresh = it }
            }

        try {
            return task.await()
        } finally {
            refreshMutex.withLock {
                if (inFlightRefresh === task && task.isCompleted) {
                    inFlightRefresh = null
                }
            }
        }
    }

    fun buildMidsByGroupId(snapshot: RelationGroupSnapshot): Map<Int, Set<Long>> {
        val result = linkedMapOf<Int, LinkedHashSet<Long>>()
        snapshot.users.forEach { user ->
            user.groupIds.forEach { groupId ->
                result.getOrPut(groupId) { LinkedHashSet() }.add(user.mid)
            }
        }
        return result.mapValues { it.value.toSet() }
    }

    private suspend fun performRefresh(preferredApiType: ApiType): RelationRefreshResult {
        val candidates = listOf(preferredApiType, preferredApiType.fallback()).distinct()
        var lastError: Throwable? = null

        candidates.forEachIndexed { index, apiType ->
            val snapshot = runCatching { loadSnapshot(apiType) }
                .onFailure { lastError = it }
                .getOrNull()

            if (snapshot != null) {
                currentSnapshot = snapshot
                Prefs.followingTotalCache = snapshot.followingTotal
                Prefs.followTagsCacheJson = json.encodeToString(
                    RelationGroupSnapshot.serializer(),
                    snapshot
                )
                return RelationRefreshResult(
                    success = true,
                    snapshot = snapshot,
                    preferredApiType = preferredApiType,
                    resolvedApiType = apiType,
                    usedFallback = index > 0
                )
            }
        }

        val staleSnapshot = getSnapshotOrNull()
        return RelationRefreshResult(
            success = false,
            snapshot = staleSnapshot,
            preferredApiType = preferredApiType,
            resolvedApiType = staleSnapshot?.resolvedApiType,
            usedFallback = false,
            error = lastError
        )
    }

    private suspend fun loadSnapshot(apiType: ApiType): RelationGroupSnapshot {
        val uid = Prefs.uid
        require(uid != 0L) { "uid is zero" }

        val credentials = credentialsFor(apiType)
        val stat = fetchRelationStat(uid, apiType, credentials)
        val tags = fetchRelationTags(apiType, credentials)
        val rawUsers = fetchAllFollowings(uid, apiType, credentials)

        return buildSnapshot(
            resolvedApiType = apiType,
            followingTotal = stat.following,
            tags = tags,
            rawUsers = rawUsers
        )
    }

    private fun credentialsFor(apiType: ApiType): Credentials {
        val credentials = when (apiType) {
            ApiType.Web -> Credentials(
                sessData = Prefs.sessData.takeIf { it.isNotBlank() }
            )

            ApiType.App -> Credentials(
                accessKey = Prefs.accessToken.takeIf { it.isNotBlank() }
            )
        }
        require(credentials.sessData != null || credentials.accessKey != null) {
            "missing credentials for $apiType"
        }
        return credentials
    }

    private suspend fun fetchRelationStat(
        uid: Long,
        apiType: ApiType,
        credentials: Credentials
    ): RelationStat {
        return when (apiType) {
            ApiType.Web -> BiliHttpApi.getRelationStat(
                mid = uid,
                sessData = credentials.sessData
            )

            ApiType.App -> BiliHttpApi.getRelationStat(
                mid = uid,
                accessKey = credentials.accessKey
            )
        }.getResponseData()
    }

    private suspend fun fetchRelationTags(
        apiType: ApiType,
        credentials: Credentials
    ): List<RelationTag> {
        return when (apiType) {
            ApiType.Web -> BiliHttpApi.getRelationTags(
                sessData = credentials.sessData
            )

            ApiType.App -> BiliHttpApi.getRelationTags(
                accessKey = credentials.accessKey
            )
        }.getResponseData()
    }

    private suspend fun fetchAllFollowings(
        uid: Long,
        apiType: ApiType,
        credentials: Credentials
    ): List<UserFollowData.FollowedUser> = coroutineScope {
        val firstPage = fetchFollowPage(
            uid = uid,
            pageNumber = 1,
            apiType = apiType,
            credentials = credentials
        )
        val result = firstPage.list.toMutableList()
        val total = firstPage.total
        val pageCount = ceil(total.toFloat() / PAGE_SIZE).toInt()

        if (pageCount >= 2) {
            val remainingPages = (2..pageCount).map { pageNumber ->
                async {
                    fetchFollowPage(
                        uid = uid,
                        pageNumber = pageNumber,
                        apiType = apiType,
                        credentials = credentials
                    ).list
                }
            }.awaitAll()

            remainingPages.forEach { result.addAll(it) }
        }

        result
    }

    private suspend fun fetchFollowPage(
        uid: Long,
        pageNumber: Int,
        apiType: ApiType,
        credentials: Credentials
    ): UserFollowData {
        return when (apiType) {
            ApiType.Web -> BiliHttpApi.getUserFollow(
                mid = uid,
                pageSize = PAGE_SIZE,
                pageNumber = pageNumber,
                sessData = credentials.sessData
            )

            ApiType.App -> BiliHttpApi.getUserFollow(
                mid = uid,
                pageSize = PAGE_SIZE,
                pageNumber = pageNumber,
                accessKey = credentials.accessKey
            )
        }.getResponseData()
    }

    private fun buildSnapshot(
        resolvedApiType: ApiType,
        followingTotal: Int,
        tags: List<RelationTag>,
        rawUsers: List<UserFollowData.FollowedUser>
    ): RelationGroupSnapshot {
        val normalizedUsers = rawUsers.map { normalizeUser(it) }
        val actualCountByGroupId = linkedMapOf<Int, Int>()

        normalizedUsers.forEach { user ->
            user.groupIds.forEach { groupId ->
                actualCountByGroupId[groupId] = (actualCountByGroupId[groupId] ?: 0) + 1
            }
        }

        val groups = buildGroups(tags, actualCountByGroupId)

        return sanitizeSnapshot(
            RelationGroupSnapshot(
                resolvedApiType = resolvedApiType,
                updatedAt = System.currentTimeMillis(),
                followingTotal = followingTotal,
                groups = groups,
                users = normalizedUsers
            )
        )
    }

    private fun normalizeUser(raw: UserFollowData.FollowedUser): RelationGroupUser {
        val rawTagIds = raw.tag.orEmpty()
            .distinct()
            .sorted()
        val isSpecial = raw.special == 1
        val normalizedGroupIds = when {
            rawTagIds.isNotEmpty() && isSpecial -> (rawTagIds + SPECIAL_RELATION_GROUP_ID).distinct()
            rawTagIds.isNotEmpty() -> rawTagIds
            isSpecial -> listOf(SPECIAL_RELATION_GROUP_ID)
            else -> listOf(DEFAULT_RELATION_GROUP_ID)
        }

        return RelationGroupUser(
            mid = raw.mid,
            name = raw.uname,
            avatar = raw.face,
            sign = raw.sign,
            mtime = raw.mtime,
            groupIds = normalizedGroupIds,
            isSpecial = isSpecial
        )
    }

    private fun buildGroups(
        tags: List<RelationTag>,
        actualCountByGroupId: Map<Int, Int>
    ): List<RelationGroup> {
        val groups = mutableListOf<RelationGroup>()
        var nextOrder = 0

        // groups 目录完全以 tags 接口为准
        // 但 tags 本身可能重复返回同一个 tagid，这里必须先按 tagid 去重
        val uniqueSortedTags = tags
            .sortedWith(
                compareBy {
                    when (it.tagid) {
                        DEFAULT_RELATION_GROUP_ID -> 0
                        SPECIAL_RELATION_GROUP_ID -> 1
                        else -> 2
                    }
                }
            )
            .distinctBy { it.tagid }

        val knownTagIds = LinkedHashSet<Int>()
        uniqueSortedTags.forEach { tag ->
            knownTagIds.add(tag.tagid)
            groups += RelationGroup(
                groupId = tag.tagid,
                name = tag.name,
                kind = when (tag.tagid) {
                    DEFAULT_RELATION_GROUP_ID -> RelationGroupKind.DEFAULT
                    SPECIAL_RELATION_GROUP_ID -> RelationGroupKind.SPECIAL
                    else -> RelationGroupKind.NORMAL
                },
                order = nextOrder++,
                countFromTags = tag.count,
                actualCount = actualCountByGroupId[tag.tagid] ?: 0
            )
        }

        // orphan 只兜底普通未知分组：
        // - followings 里出现了某个 groupId
        // - 但 tags 里没有
        // - 且它不是 0 / -1
        actualCountByGroupId.keys
            .filter {
                it != DEFAULT_RELATION_GROUP_ID &&
                        it != SPECIAL_RELATION_GROUP_ID &&
                        it !in knownTagIds
            }
            .sorted()
            .forEach { orphanGroupId ->
                groups += RelationGroup(
                    groupId = orphanGroupId,
                    name = "分组 $orphanGroupId",
                    kind = RelationGroupKind.ORPHAN,
                    order = nextOrder++,
                    countFromTags = actualCountByGroupId[orphanGroupId] ?: 0,
                    actualCount = actualCountByGroupId[orphanGroupId] ?: 0
                )
            }

        return groups
    }

    private fun sanitizeSnapshot(snapshot: RelationGroupSnapshot): RelationGroupSnapshot {
        val dedupedGroups = snapshot.groups
            .sortedBy { it.order }
            .distinctBy { it.groupId }

        val dedupedUsers = snapshot.users.map { user ->
            user.copy(groupIds = user.groupIds.distinct())
        }

        return if (
            dedupedGroups == snapshot.groups &&
            dedupedUsers == snapshot.users
        ) {
            snapshot
        } else {
            snapshot.copy(
                groups = dedupedGroups,
                users = dedupedUsers
            )
        }
    }

    private data class Credentials(
        val sessData: String? = null,
        val accessKey: String? = null
    )
}

private fun ApiType.fallback(): ApiType =
    if (this == ApiType.Web) ApiType.App else ApiType.Web