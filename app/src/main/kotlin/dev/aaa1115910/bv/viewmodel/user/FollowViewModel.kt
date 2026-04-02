package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.block.BlockManager
import dev.aaa1115910.bv.relation.RelationGroupKind
import dev.aaa1115910.bv.relation.RelationGroupSnapshot
import dev.aaa1115910.bv.relation.RelationGroupUser
import dev.aaa1115910.bv.relation.RelationGroupsDataSource
import dev.aaa1115910.bv.relation.RelationRefreshTrigger
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.biliapi.http.entity.relation.RelationTag
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.Prefs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.text.Collator
import java.util.Locale

@Immutable
data class FollowGroupCardUi(
    val groupId: Int,
    val title: String,
    val kind: RelationGroupKind,
    val count: Int,
    val state: FollowGroupCardState
)

@Immutable
enum class FollowGroupCardState {
    NORMAL,
    EMPTY
}

@Immutable
data class FollowUserUi(
    val groupId: Int,
    val mid: Long,
    val name: String,
    val avatar: String,
    val sign: String
) {
    val stableKey: String = "user-$groupId-$mid"
}

@KoinViewModel
class FollowViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    var groupCards by mutableStateOf<List<FollowGroupCardUi>>(emptyList())
        private set

    var usersByGroupId by mutableStateOf<Map<Int, List<FollowUserUi>>>(emptyMap())
        private set

    var selectedGroupId by mutableStateOf<Int?>(null)
        private set

    var preferredGroupFocusId by mutableStateOf<Int?>(null)
        private set

    var updating by mutableStateOf(true)
        private set

    var totalUsers by mutableIntStateOf(0)
        private set

    private var groupCardById: Map<Int, FollowGroupCardUi> = emptyMap()

    val currentTitle: String
        get() = selectedGroupId?.let { groupCardById[it]?.title }.orEmpty()

    val currentCount: Int
        get() = selectedGroupId?.let { usersByGroupId[it]?.size ?: groupCardById[it]?.count ?: 0 }
            ?: totalUsers

    val currentUsers: List<FollowUserUi>
        get() = selectedGroupId?.let { usersByGroupId[it].orEmpty() } ?: emptyList()

    val preferredDetailUserKey: String?
        get() = currentUsers.firstOrNull()?.stableKey

    private val logger = KotlinLogging.logger { }

    var showFollowGroupDialog by mutableStateOf(false)
        private set

    var followTags by mutableStateOf<List<RelationTag>>(emptyList())
        private set

    var followGroupDialogWasFollowing by mutableStateOf(false)
        private set

    var followGroupDialogInitialSelectedTagIds by mutableStateOf<List<Int>>(emptyList())
        private set

    var followGroupDialogTargetMid by mutableStateOf<Long?>(null)
        private set

    private val _uiEffect = MutableSharedFlow<UiEffect>()
    val uiEvent = _uiEffect.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            initFollowedUsers()
        }
    }

    fun enterGroup(groupId: Int) {
        val group = groupCardById[groupId] ?: return
        if (group.state == FollowGroupCardState.EMPTY) return

        preferredGroupFocusId = groupId
        selectedGroupId = groupId
    }

    fun exitGroupDetail() {
        preferredGroupFocusId = selectedGroupId ?: preferredGroupFocusId
        selectedGroupId = null
    }

    fun onGroupFocused(groupId: Int) {
        if (preferredGroupFocusId != groupId) {
            preferredGroupFocusId = groupId
        }
    }

    fun hideFollowGroupDialog() {
        showFollowGroupDialog = false
    }

    fun openFollowGroupDialog(user: FollowUserUi) {
        if (showFollowGroupDialog) return

        viewModelScope.launch {
            val tagsOk = runCatching { loadFollowTagsIfNeeded() }.getOrDefault(false)
            if (!tagsOk) {
                _uiEffect.emit(UiEffect.ShowToast("未获取到关注分组列表，已取消打开以避免误操作"))
                return@launch
            }

            val tagsSnapshot = followTags
            val (wasFollowing, initialSelected) = runCatching {
                getUpFollowStateAndTagIds(user.mid)
            }.getOrElse {
                true to listOf(user.groupId)
            }

            val presentIds = tagsSnapshot.map { it.tagid }.toSet()
            val normalizedInitial = normalizeTagIds(initialSelected)
            val filteredInitial = normalizedInitial.filter { presentIds.contains(it) }

            val safeInitial = when {
                wasFollowing && filteredInitial.isEmpty() && presentIds.contains(0) -> listOf(0)
                else -> filteredInitial
            }
            if (wasFollowing && safeInitial.isEmpty()) {
                _uiEffect.emit(UiEffect.ShowToast("未能解析当前关注分组，已取消打开以避免误操作"))
                return@launch
            }

            followGroupDialogTargetMid = user.mid
            followGroupDialogWasFollowing = wasFollowing
            followGroupDialogInitialSelectedTagIds = safeInitial
            showFollowGroupDialog = true
        }
    }

    fun submitFollowGroupSelection(selectedTagIds: List<Int>) {
        val upMid = followGroupDialogTargetMid ?: return
        val wasFollowing = followGroupDialogWasFollowing
        val finalSelected = normalizeTagIds(selectedTagIds)
        val initialSelected = normalizeTagIds(followGroupDialogInitialSelectedTagIds)

        if (finalSelected == initialSelected && wasFollowing) return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when {
                    finalSelected.isEmpty() -> {
                        if (wasFollowing) {
                            userRepository.unfollowUser(
                                mid = upMid,
                                preferApiType = Prefs.apiType
                            )
                        }
                    }

                    else -> {
                        if (!wasFollowing) {
                            userRepository.followUser(
                                mid = upMid,
                                preferApiType = Prefs.apiType
                            )
                        }
                        userRepository.addUserToFollowTags(
                            mid = upMid,
                            tagIds = finalSelected,
                            preferApiType = Prefs.apiType
                        )
                    }
                }

                val result = RelationGroupsDataSource.refresh(RelationRefreshTrigger.FollowScreen)
                result.snapshot?.let { refreshedSnapshot ->
                    applySnapshot(refreshedSnapshot)
                    BlockManager.rebuildBlockedMidsFromSnapshot(refreshedSnapshot)
                }
            }.onFailure {
                viewModelScope.launch {
                    _uiEffect.emit(UiEffect.ShowToast("更新关注分组失败"))
                }
            }
        }
    }

    private fun normalizeTagIds(ids: List<Int>): List<Int> {
        val dedup = ids.distinct().sorted()
        return if (dedup.contains(0) && dedup.size > 1) listOf(0) else dedup
    }

    private suspend fun initFollowedUsers() {
        RelationGroupsDataSource.getSnapshotOrNull()?.let { cachedSnapshot ->
            applySnapshot(cachedSnapshot)
            withContext(Dispatchers.Main) {
                updating = false
            }
        }

        val result = RelationGroupsDataSource.refresh(RelationRefreshTrigger.FollowScreen)
        result.snapshot?.let { refreshedSnapshot ->
            applySnapshot(refreshedSnapshot)
            BlockManager.rebuildBlockedMidsFromSnapshot(refreshedSnapshot)
        }

        withContext(Dispatchers.Main) {
            updating = false
        }
    }

    private suspend fun applySnapshot(snapshot: RelationGroupSnapshot) {
        val newUsersByGroupId = buildUsersByGroupId(snapshot)
        val newGroupCards = snapshot.groups.map { group ->
            val count = newUsersByGroupId[group.groupId]?.size ?: 0
            FollowGroupCardUi(
                groupId = group.groupId,
                title = group.name,
                kind = group.kind,
                count = count,
                state = if (count > 0) {
                    FollowGroupCardState.NORMAL
                } else {
                    FollowGroupCardState.EMPTY
                }
            )
        }
        val newGroupCardById = newGroupCards.associateBy { it.groupId }
        val focusableGroupIds = newGroupCards
            .asSequence()
            .filter { it.state == FollowGroupCardState.NORMAL }
            .map { it.groupId }
            .toSet()

        val resolvedSelectedGroupId = selectedGroupId?.takeIf {
            it in newGroupCardById && newUsersByGroupId[it].orEmpty().isNotEmpty()
        }
        val resolvedPreferredGroupFocusId = when {
            preferredGroupFocusId in focusableGroupIds -> preferredGroupFocusId
            resolvedSelectedGroupId in focusableGroupIds -> resolvedSelectedGroupId
            else -> newGroupCards.firstOrNull { it.count > 0 }?.groupId
        }

        withContext(Dispatchers.Main) {
            totalUsers = snapshot.users.size
            groupCardById = newGroupCardById
            groupCards = newGroupCards
            usersByGroupId = newUsersByGroupId
            selectedGroupId = resolvedSelectedGroupId
            preferredGroupFocusId = resolvedPreferredGroupFocusId
        }
    }

    private suspend fun loadFollowTagsIfNeeded(): Boolean {
        if (!Prefs.isLogin) return false
        if (followTags.isNotEmpty()) return true

        val tags = withContext(Dispatchers.IO) {
            userRepository.getFollowTags(preferApiType = Prefs.apiType)
        }
        followTags = tags
        return tags.isNotEmpty()
    }

    private suspend fun getUpFollowStateAndTagIds(upMid: Long): Pair<Boolean, List<Int>> {
        return withContext(Dispatchers.IO) {
            userRepository.getUpFollowStateAndTagIds(
                mid = upMid,
                preferApiType = Prefs.apiType
            )
        }
    }

    private fun buildUsersByGroupId(snapshot: RelationGroupSnapshot): Map<Int, List<FollowUserUi>> {
        val usersByGroupId = linkedMapOf<Int, MutableList<RelationGroupUser>>()
        snapshot.groups.forEach { group ->
            usersByGroupId[group.groupId] = mutableListOf()
        }

        snapshot.users.forEach { user ->
            user.groupIds.forEach { groupId ->
                usersByGroupId.getOrPut(groupId) { mutableListOf() }.add(user)
            }
        }

        return usersByGroupId.mapValues { (groupId, users) ->
            sortUsers(users).map { user ->
                FollowUserUi(
                    groupId = groupId,
                    mid = user.mid,
                    name = user.name,
                    avatar = user.avatar,
                    sign = user.sign
                )
            }
        }
    }

    private fun sortUsers(users: List<RelationGroupUser>): List<RelationGroupUser> {
        val usersStartWithoutChinese =
            users.filter { Regex("^[A-Za-z0-9_-]").containsMatchIn(it.name) }
                .toMutableList()
        val usersStartWithChinese =
            (users - usersStartWithoutChinese.toSet()).toMutableList()

        usersStartWithoutChinese.sortWith { o1, o2 ->
            Collator.getInstance(Locale.CHINA).compare(o1.name, o2.name)
        }
        usersStartWithChinese.sortWith { o1, o2 ->
            Collator.getInstance(Locale.CHINA).compare(o1.name, o2.name)
        }

        return usersStartWithoutChinese + usersStartWithChinese
    }
}