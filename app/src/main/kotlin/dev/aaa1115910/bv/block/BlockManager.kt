package dev.aaa1115910.bv.block

import dev.aaa1115910.bv.relation.RelationGroupSnapshot
import dev.aaa1115910.bv.relation.RelationGroupsDataSource
import dev.aaa1115910.bv.relation.RelationRefreshResult
import dev.aaa1115910.bv.relation.RelationRefreshTrigger
import dev.aaa1115910.bv.util.Prefs

object BlockManager {
    @Volatile
    private var enabledPagesCache: Set<BlockPage> = emptySet()

    @Volatile
    private var blockedMidsCache: Set<Long> = emptySet()

    @Volatile
    private var loaded = false

    fun isPageEnabled(page: BlockPage): Boolean {
        return if (loaded) {
            enabledPagesCache.contains(page)
        } else {
            Prefs.blockEnabledPages.contains(page)
        }
    }

    fun isBlocked(mid: Long?): Boolean {
        if (mid == null) return false
        return blockedMidsCache.contains(mid)
    }

    fun <T> filterList(
        page: BlockPage,
        list: List<T>,
        midSelector: (T) -> Long?
    ): List<T> {
        if (!isPageEnabled(page)) return list
        if (blockedMidsCache.isEmpty()) return list
        return list.filter { item -> !isBlocked(midSelector(item)) }
    }

    fun reloadFromPrefs() {
        enabledPagesCache = Prefs.blockEnabledPages.toSet()
        blockedMidsCache = readBlockedMidsFromCsv()
        loaded = true
    }

    suspend fun updateByUser(): RelationRefreshResult {
        val result = RelationGroupsDataSource.refresh(RelationRefreshTrigger.BlockSettingManual)
        if (result.success && result.snapshot != null) {
            rebuildBlockedMidsFromSnapshot(result.snapshot)
        } else {
            blockedMidsCache = readBlockedMidsFromCsv()
        }
        return result
    }

    fun rebuildBlockedMidsFromSnapshot(
        snapshot: RelationGroupSnapshot? = RelationGroupsDataSource.getSnapshotOrNull()
    ) {
        val resolvedSnapshot = snapshot ?: RelationGroupsDataSource.readSnapshotFromPrefsOrNull()
        if (resolvedSnapshot == null) {
            blockedMidsCache = readBlockedMidsFromCsv()
            return
        }

        val selectedTagIds = Prefs.blockSelectedTagIds.toSet()
        if (selectedTagIds.isEmpty()) {
            Prefs.blockedMidsCsv = ""
            blockedMidsCache = emptySet()
            return
        }

        val midsByGroupId = RelationGroupsDataSource.buildMidsByGroupId(resolvedSnapshot)
        val mids = linkedSetOf<Long>()
        selectedTagIds.forEach { groupId ->
            mids.addAll(midsByGroupId[groupId].orEmpty())
        }

        Prefs.blockedMidsCsv = mids.joinToString(",")
        blockedMidsCache = mids
    }

    private fun readBlockedMidsFromCsv(): Set<Long> {
        return Prefs.blockedMidsCsv.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }
}
