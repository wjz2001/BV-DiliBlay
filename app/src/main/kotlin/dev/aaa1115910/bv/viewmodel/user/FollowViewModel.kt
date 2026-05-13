package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.relation.RelationTag
import dev.aaa1115910.biliapi.repositories.UserRepository as BiliUserRepository
import dev.aaa1115910.bv.block.BlockManager
import dev.aaa1115910.bv.relation.RelationGroupKind
import dev.aaa1115910.bv.relation.RelationGroupSnapshot
import dev.aaa1115910.bv.relation.RelationGroupUser
import dev.aaa1115910.bv.relation.RelationGroupsDataSource
import dev.aaa1115910.bv.relation.RelationRefreshTrigger
import dev.aaa1115910.bv.relation.SPECIAL_RELATION_GROUP_ID
import dev.aaa1115910.bv.repository.UserRepository as AppUserRepository
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.accountSessionKey
import io.github.oshai.kotlinlogging.KotlinLogging
import java.text.Collator
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

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
    val sign: String,
    val isSelfEntry: Boolean = false
) {
    val stableKey: String = if (isSelfEntry) {
        "self-$groupId-$mid"
    } else {
        "user-$groupId-$mid"
    }
}

@KoinViewModel
class FollowViewModel(
    private val biliUserRepository: BiliUserRepository,
    private val appUserRepository: AppUserRepository
) : ViewModel() {
    private val _groupCards = MutableStateFlow(persistentListOf<FollowGroupCardUi>())
    val groupCards: StateFlow<ImmutableList<FollowGroupCardUi>> = _groupCards.asStateFlow()

    private val _usersByGroupId =
        MutableStateFlow(persistentHashMapOf<Int, ImmutableList<FollowUserUi>>())
    val usersByGroupId: StateFlow<ImmutableMap<Int, ImmutableList<FollowUserUi>>> =
        _usersByGroupId.asStateFlow()

    var updating by mutableStateOf(true)
        private set

    var totalUsers by mutableIntStateOf(0)
        private set

    private var groupCardById: Map<Int, FollowGroupCardUi> = emptyMap()
    private var selfEntryAvailable by mutableStateOf(false)
    private var selfName by mutableStateOf("")
    private var selfAvatar by mutableStateOf("")
    private var selfSign by mutableStateOf("")

    private val groupActivation = DebouncedActivationController<Int?>(
        initial = null,
        scope = viewModelScope
    )

    val focusedGroupId get() = groupActivation.focused
    val activeGroupId get() = groupActivation.active

    val currentGroupId: Int?
        get() = activeGroupId ?: _groupCards.value.firstOrNull()?.groupId

    val preferredGroupFocusId: Int?
        get() = focusedGroupId ?: currentGroupId

    val currentTitle: String
        get() = currentGroupId?.let { groupCardById[it]?.title }.orEmpty()

    val currentCount: Int
        get() = currentUsers.size

    val currentUsers: List<FollowUserUi>
        get() = currentGroupId?.let { _usersByGroupId.value[it].orEmpty() } ?: emptyList()

    val preferredDetailUserKey: String?
        get() = currentUsers.firstOrNull()?.stableKey

    private val logger = KotlinLogging.logger { }
    private var refreshJob: Job? = null
    private var loadedAccountSessionKey = appUserRepository.accountSessionKey()

    var showFollowGroupDialog by mutableStateOf(false)
        private set

    private val _followTags = MutableStateFlow(persistentListOf<RelationTag>())
    val followTags: StateFlow<ImmutableList<RelationTag>> = _followTags.asStateFlow()

    var followGroupDialogWasFollowing by mutableStateOf(false)
        private set

    private val _followGroupDialogInitialSelectedTagIds = MutableStateFlow(persistentListOf<Int>())
    val followGroupDialogInitialSelectedTagIds: StateFlow<ImmutableList<Int>> =
        _followGroupDialogInitialSelectedTagIds.asStateFlow()

    var followGroupDialogTargetMid by mutableStateOf<Long?>(null)
        private set

    private val uiEffect = MutableSharedFlow<UiEffect>()
    val uiEvent = uiEffect.asSharedFlow()

    fun onGroupFocused(groupId: Int) {
        groupActivation.onFocused(groupId)
    }

    fun onGroupClicked(groupId: Int) {
        groupActivation.onClicked(groupId)
    }

    fun syncGroupActivationToCurrent() {
        currentGroupId?.let(groupActivation::onClicked)
    }

    fun activateFollowScreen() {
        ensureAccountStateFresh()
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            initFollowedUsers()
        }
    }

    fun freezeFollowScreen() {
        refreshJob?.cancel()
        refreshJob = null

        viewModelScope.launch(Dispatchers.IO) {
            RelationGroupsDataSource.cancelRefresh(RelationRefreshTrigger.FollowScreen)
        }
    }

    private fun clearData() {
        refreshJob?.cancel()
        refreshJob = null
        _groupCards.value = persistentListOf()
        _usersByGroupId.value = persistentHashMapOf()
        updating = true
        totalUsers = 0
        groupCardById = emptyMap()
        selfEntryAvailable = false
        selfName = ""
        selfAvatar = ""
        selfSign = ""
        showFollowGroupDialog = false
        _followTags.value = persistentListOf()
        followGroupDialogWasFollowing = false
        _followGroupDialogInitialSelectedTagIds.value = persistentListOf()
        followGroupDialogTargetMid = null
        RelationGroupsDataSource.clearSnapshotCache(clearPrefs = true)
        loadedAccountSessionKey = appUserRepository.accountSessionKey()
    }

    private fun ensureAccountStateFresh() {
        val currentAccountSessionKey = appUserRepository.accountSessionKey()
        if (loadedAccountSessionKey == currentAccountSessionKey) return
        clearData()
        loadedAccountSessionKey = currentAccountSessionKey
    }

    fun hideFollowGroupDialog() {
        showFollowGroupDialog = false
    }

    fun openFollowGroupDialog(user: FollowUserUi) {
        if (showFollowGroupDialog || user.isSelfEntry) return

        viewModelScope.launch {
            val tagsOk = runCatching { loadFollowTagsIfNeeded() }.getOrDefault(false)
            if (!tagsOk) {
                uiEffect.emit(UiEffect.ShowToast("未获取到关注分组列表，已取消打开以避免误操作"))
                return@launch
            }

            val tagsSnapshot = _followTags.value
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
                uiEffect.emit(UiEffect.ShowToast("未能解析当前关注分组，已取消打开以避免误操作"))
                return@launch
            }

            followGroupDialogTargetMid = user.mid
            followGroupDialogWasFollowing = wasFollowing
            _followGroupDialogInitialSelectedTagIds.value = safeInitial.toPersistentList()
            showFollowGroupDialog = true
        }
    }

    fun submitFollowGroupSelection(selectedTagIds: List<Int>) {
        val upMid = followGroupDialogTargetMid ?: return
        val wasFollowing = followGroupDialogWasFollowing
        val finalSelected = normalizeTagIds(selectedTagIds)
        val initialSelected = normalizeTagIds(_followGroupDialogInitialSelectedTagIds.value)

        if (finalSelected == initialSelected && wasFollowing) return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val success = biliUserRepository.submitFollowGroupSelection(
                    mid = upMid,
                    wasFollowing = wasFollowing,
                    beforeTagIds = initialSelected,
                    afterTagIds = finalSelected,
                    preferApiType = Prefs.apiType
                )
                check(success) { "submit follow group selection failed" }

                val refreshResult = RelationGroupsDataSource.refresh(RelationRefreshTrigger.FollowScreen)
                refreshResult.snapshot?.let { refreshedSnapshot ->
                    applySnapshot(refreshedSnapshot)
                    BlockManager.rebuildBlockedMidsFromSnapshot(refreshedSnapshot)
                }
            }.onFailure {
                viewModelScope.launch {
                    uiEffect.emit(UiEffect.ShowToast("更新关注分组失败"))
                }
            }
        }
    }

    private fun normalizeTagIds(ids: List<Int>): List<Int> {
        val dedup = ids.distinct().sorted()
        return if (dedup.contains(0) && dedup.size > 1) listOf(0) else dedup
    }

    private suspend fun initFollowedUsers() {
        val cachedSnapshot = RelationGroupsDataSource.getSnapshotOrNull()
        cachedSnapshot?.let {
            applySnapshot(cachedSnapshot)
            withContext(Dispatchers.Main) {
                updating = false
            }
        }

        refreshSelfEntryInfo()
        cachedSnapshot?.let { applySnapshot(it) }

        val result = RelationGroupsDataSource.refresh(RelationRefreshTrigger.FollowScreen)
        result.snapshot?.let { refreshedSnapshot ->
            refreshSelfEntryInfo()
            applySnapshot(refreshedSnapshot)
            BlockManager.rebuildBlockedMidsFromSnapshot(refreshedSnapshot)
        }

        withContext(Dispatchers.Main) {
            updating = false
        }
    }

    private suspend fun refreshSelfEntryInfo() {
        if (!Prefs.isLogin || Prefs.uid == 0L) {
            withContext(Dispatchers.Main) {
                selfEntryAvailable = false
                selfName = ""
                selfAvatar = ""
                selfSign = ""
            }
            return
        }

        val selfInfo = runCatching {
            BiliHttpApi.getUserSelfInfo(sessData = Prefs.sessData).getResponseData()
        }.getOrNull()

        val hasVideos = checkSelfHasVideos()

        withContext(Dispatchers.Main) {
            selfEntryAvailable = hasVideos
            selfName = selfInfo?.name ?: appUserRepository.username
            selfAvatar = selfInfo?.face ?: appUserRepository.avatar
            selfSign = selfInfo?.sign.orEmpty()
        }
    }

    private suspend fun applySnapshot(snapshot: RelationGroupSnapshot) {
        val displayGroups = snapshot.groups.sortedWith(
            compareBy<dev.aaa1115910.bv.relation.RelationGroup> {
                specialGroupPriority(it.groupId, it.kind, it.name)
            }.thenBy {
                when (it.groupId) {
                    0 -> 1
                    else -> 2
                }
            }.thenBy { it.order }
        )
        val newUsersByGroupId = buildUsersByGroupId(snapshot, displayGroups.map { it.groupId })
        val newGroupCards = displayGroups.map { group ->
            val count = newUsersByGroupId[group.groupId]?.size ?: 0
            FollowGroupCardUi(
                groupId = group.groupId,
                title = group.name,
                kind = group.kind,
                count = count,
                state = if (count > 0) FollowGroupCardState.NORMAL else FollowGroupCardState.EMPTY
            )
        }
        val newGroupCardById = newGroupCards.associateBy { it.groupId }
        val availableGroupIds = newGroupCards.map { it.groupId }.toSet()
        val resolvedFocusedGroupId = when {
            focusedGroupId in availableGroupIds -> focusedGroupId
            activeGroupId in availableGroupIds -> activeGroupId
            else -> newGroupCards.firstOrNull()?.groupId
        }
        val resolvedActiveGroupId = when {
            activeGroupId in availableGroupIds -> activeGroupId
            resolvedFocusedGroupId in availableGroupIds -> resolvedFocusedGroupId
            else -> newGroupCards.firstOrNull()?.groupId
        }

        withContext(Dispatchers.Main) {
            totalUsers = snapshot.users.size
            groupCardById = newGroupCardById
            _groupCards.value = newGroupCards.toPersistentList()
            _usersByGroupId.value = newUsersByGroupId
                .mapValues { it.value.toPersistentList() }
                .toPersistentMap()

            resolvedFocusedGroupId?.let(groupActivation::onFocused)
            (resolvedActiveGroupId ?: resolvedFocusedGroupId)?.let(groupActivation::onClicked)
        }
    }

    private suspend fun checkSelfHasVideos(): Boolean {
        val apiCandidates = listOf(Prefs.apiType, Prefs.apiType.fallback()).distinct()
        apiCandidates.forEach { apiType ->
            val hasVideos = runCatching {
                val data = biliUserRepository.getSpaceVideos(
                    mid = Prefs.uid,
                    page = SpaceVideoPage(nextWebPageSize = 1),
                    preferApiType = apiType
                )
                (data.totalCount ?: data.videos.size) > 0
            }.getOrNull()
            if (hasVideos == true) return true
        }
        return false
    }

    private suspend fun loadFollowTagsIfNeeded(): Boolean {
        if (!Prefs.isLogin) return false
        if (_followTags.value.isNotEmpty()) return true

        val tags = withContext(Dispatchers.IO) {
            biliUserRepository.getFollowTags(preferApiType = Prefs.apiType)
        }
        _followTags.value = tags.toPersistentList()
        return tags.isNotEmpty()
    }

    private suspend fun getUpFollowStateAndTagIds(upMid: Long): Pair<Boolean, List<Int>> {
        return withContext(Dispatchers.IO) {
            biliUserRepository.getUpFollowStateAndTagIds(
                mid = upMid,
                preferApiType = Prefs.apiType
            )
        }
    }

    private fun buildUsersByGroupId(
        snapshot: RelationGroupSnapshot,
        orderedGroupIds: List<Int>
    ): Map<Int, List<FollowUserUi>> {
        val usersByGroupId = linkedMapOf<Int, MutableList<RelationGroupUser>>()
        orderedGroupIds.forEach { groupId ->
            usersByGroupId[groupId] = mutableListOf()
        }

        snapshot.users.forEach { user ->
            user.groupIds.forEach { groupId ->
                usersByGroupId.getOrPut(groupId) { mutableListOf() }.add(user)
            }
        }

        val result = linkedMapOf<Int, List<FollowUserUi>>()
        orderedGroupIds.forEach { groupId ->
            val users = sortUsers(usersByGroupId[groupId].orEmpty()).map { user ->
                FollowUserUi(
                    groupId = groupId,
                    mid = user.mid,
                    name = user.name,
                    avatar = user.avatar,
                    sign = user.sign
                )
            }.toMutableList()

            val groupMeta = snapshot.groups.firstOrNull { it.groupId == groupId }
            val isSpecial = groupMeta?.let {
                specialGroupPriority(it.groupId, it.kind, it.name) == 0
            } == true
            if (isSpecial && selfEntryAvailable) {
                users.add(
                    0,
                    FollowUserUi(
                        groupId = groupId,
                        mid = Prefs.uid,
                        name = selfName.ifBlank { appUserRepository.username },
                        avatar = selfAvatar.ifBlank { appUserRepository.avatar },
                        sign = selfSign,
                        isSelfEntry = true
                    )
                )
            }

            result[groupId] = users
        }

        return result
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

    private fun specialGroupPriority(
        groupId: Int,
        kind: RelationGroupKind,
        title: String
    ): Int {
        return when {
            groupId == SPECIAL_RELATION_GROUP_ID -> 0
            kind == RelationGroupKind.SPECIAL -> 0
            title.contains("特别关注") -> 0
            else -> 1
        }
    }

    private fun ApiType.fallback(): ApiType =
        if (this == ApiType.Web) ApiType.App else ApiType.Web

    override fun onCleared() {
        refreshJob?.cancel()
        groupActivation.cancel()
        super.onCleared()
    }
}
