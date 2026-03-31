package dev.aaa1115910.bv.relation

import dev.aaa1115910.biliapi.entity.ApiType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_RELATION_GROUP_ID = 0
const val SPECIAL_RELATION_GROUP_ID = -1
const val RELATION_GROUP_SNAPSHOT_VERSION = 2

@Serializable
data class RelationGroupSnapshot(
    val version: Int = RELATION_GROUP_SNAPSHOT_VERSION,
    @SerialName("resolved_api_type")
    val resolvedApiType: ApiType,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("following_total")
    val followingTotal: Int,
    val groups: List<RelationGroup> = emptyList(),
    val users: List<RelationGroupUser> = emptyList()
)

@Serializable
data class RelationGroup(
    @SerialName("group_id")
    val groupId: Int,
    val name: String,
    val kind: RelationGroupKind,
    val order: Int,
    @SerialName("count_from_tags")
    val countFromTags: Int = 0,
    @SerialName("actual_count")
    val actualCount: Int = 0
)

@Serializable
enum class RelationGroupKind {
    DEFAULT,
    SPECIAL,
    NORMAL,
    ORPHAN
}

@Serializable
data class RelationGroupUser(
    val mid: Long,
    val name: String,
    val avatar: String,
    val sign: String,
    val mtime: Int,
    @SerialName("group_ids")
    val groupIds: List<Int> = emptyList(),
    @SerialName("is_special")
    val isSpecial: Boolean = false
)