package dev.aaa1115910.biliapi.repositories

import bilibili.app.dynamic.v2.DynamicGrpcKt
import bilibili.app.dynamic.v2.Refresh
import bilibili.app.dynamic.v2.dynVideoReq
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.DynamicVideoData
import dev.aaa1115910.biliapi.entity.user.SpaceVideoData
import dev.aaa1115910.biliapi.entity.user.SpaceVideoOrder
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.grpc.utils.handleGrpcException
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.relation.RelationTag
import dev.aaa1115910.biliapi.http.entity.user.FollowAction
import dev.aaa1115910.biliapi.http.entity.user.FollowActionSource
import dev.aaa1115910.biliapi.http.entity.user.Relation
import dev.aaa1115910.biliapi.http.entity.user.RelationData
import dev.aaa1115910.biliapi.http.entity.user.RelationType
import org.koin.core.annotation.Single

@Single
class UserRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository
) {
    private val dynamicStub
        get() = runCatching {
            DynamicGrpcKt.DynamicCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    private fun normalizeFollowTagIds(ids: List<Int>): List<Int> {
        val dedup = ids.distinct().sorted()
        return if (dedup.contains(0) && dedup.size > 1) listOf(0) else dedup
    }

    private suspend fun modifyFollow(
        mid: Long,
        action: FollowAction,
        preferApiType: ApiType = ApiType.Web
    ): Boolean {
        val response = when (preferApiType) {
            ApiType.Web -> {
                BiliHttpApi.modifyFollow(
                    mid = mid,
                    action = action,
                    actionSource = FollowActionSource.Space,
                    csrf = authRepository.biliJct,
                    sessData = authRepository.sessionData
                )
            }

            ApiType.App -> {
                BiliHttpApi.modifyFollow(
                    mid = mid,
                    action = action,
                    actionSource = FollowActionSource.Space,
                    accessKey = authRepository.accessToken
                )
            }
        }
        return response.code == 0
    }

    suspend fun followUser(
        mid: Long,
        preferApiType: ApiType = ApiType.Web
    ): Boolean = modifyFollow(mid, FollowAction.AddFollow, preferApiType)

    suspend fun unfollowUser(
        mid: Long,
        preferApiType: ApiType = ApiType.Web
    ): Boolean = modifyFollow(mid, FollowAction.DelFollow, preferApiType)

    suspend fun getFollowTags(
        preferApiType: ApiType = ApiType.Web
    ): List<RelationTag> {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return emptyList()
        return runCatching {
            when (preferApiType) {
                ApiType.Web -> BiliHttpApi.getRelationTags(
                    sessData = authRepository.sessionData
                ).getResponseData()

                ApiType.App -> BiliHttpApi.getRelationTags(
                    accessKey = authRepository.accessToken
                ).getResponseData()
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(emptyList())
    }

    suspend fun addUserToFollowTags(
        mid: Long,
        tagIds: List<Int>,
        preferApiType: ApiType = ApiType.Web
    ): Boolean {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return false
        if (tagIds.isEmpty()) return true

        val response = when (preferApiType) {
            ApiType.Web -> {
                BiliHttpApi.addUsersToRelationTags(
                    fids = listOf(mid),
                    tagIds = tagIds,
                    csrf = authRepository.biliJct,
                    sessData = authRepository.sessionData
                )
            }

            ApiType.App -> {
                BiliHttpApi.addUsersToRelationTags(
                    fids = listOf(mid),
                    tagIds = tagIds,
                    accessKey = authRepository.accessToken
                )
            }
        }
        return response.code == 0
    }

    suspend fun moveUserToFollowTags(
        mid: Long,
        beforeTagIds: List<Int>,
        afterTagIds: List<Int>,
        preferApiType: ApiType = ApiType.Web
    ): Boolean {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return false

        val normalizedBefore = normalizeFollowTagIds(beforeTagIds).ifEmpty { listOf(0) }
        val normalizedAfter = normalizeFollowTagIds(afterTagIds)
        if (normalizedAfter.isEmpty()) return true

        val response = when (preferApiType) {
            ApiType.Web -> {
                BiliHttpApi.moveUsersToRelationTags(
                    fids = listOf(mid),
                    beforeTagIds = normalizedBefore,
                    afterTagIds = normalizedAfter,
                    csrf = authRepository.biliJct,
                    sessData = authRepository.sessionData
                )
            }

            ApiType.App -> {
                BiliHttpApi.moveUsersToRelationTags(
                    fids = listOf(mid),
                    beforeTagIds = normalizedBefore,
                    afterTagIds = normalizedAfter,
                    accessKey = authRepository.accessToken
                )
            }
        }
        return response.code == 0
    }

    /**
     * 提交“关注 + 关注分组选择”的高层业务编排。
     *
     * 规则：
     * - `afterTagIds` 为空：
     *   - 原本已关注 -> 取关
     *   - 原本未关注 -> 不做任何事
     * - `afterTagIds` 非空：
     *   - 原本未关注 -> 先关注；若目标为默认分组 0，仅关注即可；否则追加到目标分组
     *   - 原本已关注：
     *     - 前后分组一致 -> 不做任何事
     *     - 默认分组 0 -> 其它分组：追加到目标分组
     *     - 其它分组 -> 默认分组 0：调用 addUsers(tagids=0)
     *     - 其它分组 -> 其它分组：调用 moveUsers
     */
    suspend fun submitFollowGroupSelection(
        mid: Long,
        wasFollowing: Boolean,
        beforeTagIds: List<Int>,
        afterTagIds: List<Int>,
        preferApiType: ApiType = ApiType.Web
    ): Boolean {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return false

        val normalizedBefore = if (wasFollowing) {
            normalizeFollowTagIds(beforeTagIds).ifEmpty { listOf(0) }
        } else {
            emptyList()
        }
        val normalizedAfter = normalizeFollowTagIds(afterTagIds)

        return when {
            !wasFollowing && normalizedAfter.isEmpty() -> true

            wasFollowing && normalizedBefore == normalizedAfter -> true

            normalizedAfter.isEmpty() -> {
                unfollowUser(
                    mid = mid,
                    preferApiType = preferApiType
                )
            }

            !wasFollowing -> {
                val followed = followUser(
                    mid = mid,
                    preferApiType = preferApiType
                )
                if (!followed) return false

                if (normalizedAfter == listOf(0)) {
                    true
                } else {
                    addUserToFollowTags(
                        mid = mid,
                        tagIds = normalizedAfter,
                        preferApiType = preferApiType
                    )
                }
            }

            normalizedBefore == listOf(0) -> {
                addUserToFollowTags(
                    mid = mid,
                    tagIds = normalizedAfter,
                    preferApiType = preferApiType
                )
            }

            normalizedAfter == listOf(0) -> {
                addUserToFollowTags(
                    mid = mid,
                    tagIds = listOf(0),
                    preferApiType = preferApiType
                )
            }

            else -> {
                moveUserToFollowTags(
                    mid = mid,
                    beforeTagIds = normalizedBefore,
                    afterTagIds = normalizedAfter,
                    preferApiType = preferApiType
                )
            }
        }
    }

    suspend fun checkIsFollowing(
        mid: Long,
        preferApiType: ApiType = ApiType.Web
    ): Boolean? {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return null
        return runCatching {
            val response = when (preferApiType) {
                ApiType.Web -> {
                    BiliHttpApi.getRelations(
                        mid = mid,
                        sessData = authRepository.sessionData
                    )
                }

                ApiType.App -> {
                    BiliHttpApi.getRelations(
                        mid = mid,
                        //移动端貌似并没有使用这个接口，目前该接口返回-663鉴权失败，直接改用sessdata获取
                        sessData = authRepository.sessionData
                        //accessKey = authRepository.accessToken
                    )
                }
            }.getResponseData()
            listOf(
                RelationType.Followed,
                RelationType.FollowedQuietly,
                RelationType.BothFollowed
            ).contains(response.relation.attribute)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     * 获取“我是否关注了该用户 + 该用户所在关注分组 tagIds”。
     *
     * 规则：
     * - 未关注：返回 (false, emptyList())
     * - 已关注但无分组（默认/未分组）：接口通常返回 tag=null 或空数组，这里统一转为 listOf(0)
     * - 保证 tagid==0 与其它分组互斥：若同时出现，只保留 0
     */
    suspend fun getUpFollowStateAndTagIds(
        mid: Long,
        preferApiType: ApiType = ApiType.Web
    ): Pair<Boolean, List<Int>> {
        if (authRepository.sessionData == null && authRepository.accessToken == null) {
            return false to emptyList()
        }

        val data: RelationData = runCatching {
            when (preferApiType) {
                ApiType.Web -> {
                    BiliHttpApi.getRelations(
                        mid = mid,
                        sessData = authRepository.sessionData
                    )
                }

                ApiType.App -> {
                    BiliHttpApi.getRelations(
                        mid = mid,
                        sessData = authRepository.sessionData
                    )
                }
            }.getResponseData()
        }.getOrElse {
            it.printStackTrace()
            return false to emptyList()
        }

        fun isFollowing(r: Relation): Boolean {
            return listOf(
                RelationType.Followed,
                RelationType.FollowedQuietly,
                RelationType.BothFollowed
            ).contains(r.attribute)
        }

        // 兼容接口字段语义差异：哪个 Relation 显示“我已关注”，就用哪个的 tag
        val followedRel: Relation? = when {
            isFollowing(data.relation) -> data.relation
            isFollowing(data.beRelation) -> data.beRelation
            else -> null
        }

        val isFollowed = followedRel != null
        if (!isFollowed) return false to emptyList()

        val raw = followedRel.tag ?: emptyList()
        val normalized = (raw.ifEmpty { listOf(0) })
            .distinct()
            .sorted()
            .let { ids ->
                if (ids.contains(0) && ids.size > 1) listOf(0) else ids
            }

        return true to normalized
    }

    //TODO 改成返回 关注数，粉丝数，黑名单数
    suspend fun getFollowingUpCount(
        mid: Long,
        preferApiType: ApiType
    ): Int {
        if (authRepository.sessionData == null && authRepository.accessToken == null) return 0
        return runCatching {
            val response = when (preferApiType) {
                ApiType.Web -> {
                    BiliHttpApi.getRelationStat(
                        mid = mid,
                        sessData = authRepository.sessionData
                    )
                }

                ApiType.App -> {
                    BiliHttpApi.getRelationStat(
                        mid = mid,
                        accessKey = authRepository.accessToken
                    )
                }
            }.getResponseData()
            response.following
        }.onFailure {
            it.printStackTrace()
        }.getOrNull() ?: 0
    }

    suspend fun addSeasonFollow(
        seasonId: Int,
        preferApiType: ApiType = ApiType.Web
    ): String {
        return when (preferApiType) {
            ApiType.Web -> BiliHttpApi.addSeasonFollow(
                seasonId = seasonId,
                csrf = authRepository.biliJct!!,
                sessData = authRepository.sessionData!!
            )

            ApiType.App -> BiliHttpApi.addSeasonFollow(
                seasonId = seasonId,
                accessKey = authRepository.accessToken!!
            )
        }.getResponseData().toast
    }

    suspend fun delSeasonFollow(
        seasonId: Int,
        preferApiType: ApiType = ApiType.Web
    ): String {
        return when (preferApiType) {
            ApiType.Web -> BiliHttpApi.delSeasonFollow(
                seasonId = seasonId,
                csrf = authRepository.biliJct!!,
                sessData = authRepository.sessionData!!
            )

            ApiType.App -> BiliHttpApi.delSeasonFollow(
                seasonId = seasonId,
                accessKey = authRepository.accessToken!!
            )
        }.getResponseData().toast
    }

    suspend fun getSpaceVideos(
        mid: Long,
        order: SpaceVideoOrder = SpaceVideoOrder.PubDate,
        page: SpaceVideoPage = SpaceVideoPage(),
        preferApiType: ApiType = ApiType.Web
    ): SpaceVideoData {
        return when (preferApiType) {
            ApiType.Web -> {
                val webSpaceVideoData = BiliHttpApi.getWebUserSpaceVideos(
                    mid = mid,
                    order = order.value,
                    pageNumber = page.nextWebPageNumber,
                    pageSize = page.nextWebPageSize,
                    sessData = authRepository.sessionData ?: "",
                    dedeUserID = authRepository.mid
                ).getResponseData()
                SpaceVideoData.fromWebSpaceVideoData(webSpaceVideoData)
            }

            ApiType.App -> {
                val appSpaceVideoData = BiliHttpApi.getAppUserSpaceVideos(
                    mid = mid,
                    lastAvid = page.lastAvid,
                    order = order.value,
                    ts = System.currentTimeMillis(),
                    accessKey = authRepository.accessToken ?: ""
                ).getResponseData()
                SpaceVideoData.fromAppSpaceVideoData(appSpaceVideoData)
            }
        }
    }

    suspend fun getDynamicVideos(
        page: Int,
        offset: String,
        updateBaseline: String,
        preferApiType: ApiType = ApiType.Web
    ): DynamicVideoData {
        return when (preferApiType) {
            ApiType.Web -> {
                val responseData = BiliHttpApi.getDynamicList(
                    type = "video",
                    page = page,
                    offset = offset,
                    sessData = authRepository.sessionData ?: ""
                ).getResponseData()
                DynamicVideoData.fromDynamicData(responseData)
            }

            ApiType.App -> {
                var result: DynamicVideoData? = null
                runCatching {
                    val dynVideoReply = dynamicStub?.dynVideo(dynVideoReq {
                        this.page = page
                        this.offset = offset
                        this.updateBaseline = updateBaseline
                        localTime = 8
                        refreshType =
                            if (offset == "") Refresh.refresh_new else Refresh.refresh_history
                    })
                    result = DynamicVideoData.fromDynamicData(dynVideoReply!!)
                }.onFailure {
                    handleGrpcException(it)
                }
                result!!
            }
        }
    }
}