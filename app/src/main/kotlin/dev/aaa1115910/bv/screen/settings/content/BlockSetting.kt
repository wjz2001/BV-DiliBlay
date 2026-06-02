package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.block.BlockManager
import dev.aaa1115910.bv.component.BlockGroupSelectDialog
import dev.aaa1115910.bv.component.BlockPageSelectDialog
import dev.aaa1115910.bv.component.BlockTagItem
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.target
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.relation.RelationGroupSnapshot
import dev.aaa1115910.bv.relation.RelationGroupsDataSource
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.settings.BlockSettingViewModel
import org.koin.androidx.compose.koinViewModel

private val BlockSettingDetailScopeId = WjzFocusScopeId("settings/detail")
private val BlockSettingGroupsLocalFocusId = blockSettingActionLocalFocusId("groups")
private val BlockSettingPagesLocalFocusId = blockSettingActionLocalFocusId("pages")
private val BlockSettingUpdateNowLocalFocusId = blockSettingActionLocalFocusId("update_now")
private const val BlockSettingComponentId = "blockSetting"
private val BlockSettingDefaultEntryId = WjzFocusEntryId.parse(BlockSettingComponentId)
private val BlockSettingGroupsDialogScopeId =
    WjzFocusScopeId("settings/dialog/block_settings/groups")
private val BlockSettingGroupsDialogContainerNodeId =
    WjzFocusNodeId("settings/dialog/block_settings/groups/container")
private val BlockSettingPagesDialogScopeId =
    WjzFocusScopeId("settings/dialog/block_settings/pages")
private val BlockSettingPagesDialogContainerNodeId =
    WjzFocusNodeId("settings/dialog/block_settings/pages/container")

private fun blockSettingGroupDialogItemNodeId(tag: BlockTagItem) =
    WjzFocusNodeId("settings/dialog/block_settings/groups/${tag.tagid}")

private fun blockSettingPageDialogItemNodeId(page: dev.aaa1115910.bv.block.BlockPage) =
    WjzFocusNodeId("settings/dialog/block_settings/pages/${page.name}")

private fun blockSettingActionLocalFocusId(action: String) =
    wjzFocusLocalId("block_settings", "action", action)

@Composable
fun BlockSetting(
    modifier: Modifier = Modifier,
    contentActive: Boolean = false,
    blockSettingViewModel: BlockSettingViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val focusCoordinator = LocalWjzFocusCoordinator.current

    var updating by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    val dismissGroupDialog = rememberBlockSettingDialogDismiss(
        onDismiss = { showGroupDialog = false }
    )
    val dismissPageDialog = rememberBlockSettingDialogDismiss(
        onDismiss = { showPageDialog = false }
    )

    val snapshot = rememberRelationGroupSnapshot()
    val hasSnapshot = RelationGroupsDataSource.hasUsableSnapshot(snapshot)
    val tags = snapshot?.groups.orEmpty().map { group ->
        BlockTagItem(
            tagid = group.groupId,
            name = group.name,
            count = group.actualCount
        )
    }
    val presentTagIds = tags.map { it.tagid }.toSet()
    val effectiveSelectedTagIds = Prefs.blockSelectedTagIds.filter { it in presentTagIds }
    val groupSelectedCount = effectiveSelectedTagIds.size
    val pagesSelected = Prefs.blockEnabledPages

    DisposableEffect(Unit) {
        onDispose {
            blockSettingViewModel.cancelUpdate()
            updating = false
        }
    }

    LaunchedEffect(contentActive, focusCoordinator) {
        if (contentActive) {
            focusCoordinator?.enqueueRestoreLayer(
                layer = WjzFocusLayer.Content,
                scopeId = BlockSettingDetailScopeId
            )
            focusCoordinator?.requestEntryFocus(BlockSettingDefaultEntryId)
        }
    }

    WjzFocusEntrySurface(
        componentId = BlockSettingComponentId,
        default = {
            BlockSettingDetailScopeId.target(BlockSettingUpdateNowLocalFocusId)
        },
        entries = {
            entry("groups") {
                if (!updating && hasSnapshot) {
                    BlockSettingDetailScopeId.target(BlockSettingGroupsLocalFocusId)
                } else {
                    BlockSettingDetailScopeId.target(BlockSettingUpdateNowLocalFocusId)
                }
            }
            entry("pages") {
                if (!updating && hasSnapshot) {
                    BlockSettingDetailScopeId.target(BlockSettingPagesLocalFocusId)
                } else {
                    BlockSettingDetailScopeId.target(BlockSettingUpdateNowLocalFocusId)
                }
            }
            entry("update") move BlockSettingDetailScopeId.target(BlockSettingUpdateNowLocalFocusId)
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_item_block),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        BlockSettingActionListItem(
            localFocusId = BlockSettingGroupsLocalFocusId,
            enabled = !updating && hasSnapshot,
            title = stringResource(R.string.block_setting_groups_title),
            supportText = when {
                !hasSnapshot -> "未获取分组，请先点击立即更新"
                else -> "已选择 $groupSelectedCount / ${tags.size} 个分组"
            },
            onClick = { showGroupDialog = true }
        )

        BlockSettingActionListItem(
            localFocusId = BlockSettingPagesLocalFocusId,
            enabled = !updating && hasSnapshot,
            title = stringResource(R.string.block_setting_pages_title),
            supportText = when {
                !hasSnapshot -> "需先点击立即更新"
                pagesSelected.isEmpty() -> stringResource(R.string.block_setting_pages_support_empty)
                else -> stringResource(
                    R.string.block_setting_pages_support,
                    pagesSelected.joinToString(",") { it.displayName }
                )
            },
            onClick = { showPageDialog = true }
        )

        val needLoginToastText = stringResource(R.string.block_setting_update_now_need_login)
        BlockSettingActionListItem(
            localFocusId = BlockSettingUpdateNowLocalFocusId,
            enabled = true, // 更新中也保持可聚焦，避免焦点跳走
            title = stringResource(R.string.block_setting_update_now_title),
            supportText = when {
                updating -> stringResource(R.string.block_setting_update_now_support_updating)
                !hasSnapshot -> "当前无快照，点击开始拉取"
                else -> "当前快照 ${tags.size} 个分组 / ${snapshot?.users?.size ?: 0} 个用户"
            },
            onClick = {
                if (updating) return@BlockSettingActionListItem // 防止更新中重复点击

                val hasAuth = Prefs.uid != 0L &&
                        (Prefs.sessData.isNotBlank() || Prefs.accessToken.isNotBlank())
                if (!hasAuth) {
                    needLoginToastText.toast(context)
                    return@BlockSettingActionListItem
                }

                blockSettingViewModel.updateByUser(
                    onStart = { updating = true },
                    onResult = { result ->
                        if (result.success && result.snapshot != null) {
                            val refreshedSnapshot = result.snapshot
                            val fallbackSuffix = if (result.usedFallback && result.resolvedApiType != null) {
                                "，已自动切换到 ${result.resolvedApiType.name}"
                            } else {
                                ""
                            }
                            "更新完成：${refreshedSnapshot.groups.size} 个分组，${refreshedSnapshot.users.size} 个用户$fallbackSuffix"
                                .toast(context)
                        } else {
                            val message = result.error?.localizedMessage
                                ?: result.error?.javaClass?.simpleName
                                ?: "未知错误"
                            if (result.snapshot != null) {
                                "更新失败，已保留旧快照：$message".toast(context)
                            } else {
                                "更新失败：$message".toast(context)
                            }
                        }
                    },
                    onFailure = {
                        ("更新失败：" + (it.localizedMessage ?: it.javaClass.simpleName)).toast(context)
                    },
                    onFinish = { updating = false }
                )
            }
        )
    }

    BlockGroupSelectDialog(
        show = showGroupDialog,
        title = stringResource(R.string.block_setting_groups_dialog_title),
        tags = tags,
        initialSelectedTagIds = effectiveSelectedTagIds,
        onHideDialog = dismissGroupDialog,
        sourceScopeId = BlockSettingDetailScopeId,
        dialogScopeId = BlockSettingGroupsDialogScopeId,
        containerNodeId = BlockSettingGroupsDialogContainerNodeId,
        itemNodeId = { blockSettingGroupDialogItemNodeId(it) },
        onSubmit = { finalSelectedTagIds ->
            val sanitizedTagIds = finalSelectedTagIds
                .filter { it in presentTagIds }
                .distinct()
                .sorted()
            if (Prefs.blockSelectedTagIds == sanitizedTagIds) return@BlockGroupSelectDialog
            Prefs.blockSelectedTagIds = sanitizedTagIds
            BlockManager.rebuildBlockedMidsFromSnapshot(snapshot)
            "已保存，仅对之后加载的内容生效".toast(context)
        }
    )

    BlockPageSelectDialog(
        show = showPageDialog,
        title = stringResource(R.string.block_setting_pages_dialog_title),
        initialSelectedPages = Prefs.blockEnabledPages,
        onHideDialog = dismissPageDialog,
        sourceScopeId = BlockSettingDetailScopeId,
        dialogScopeId = BlockSettingPagesDialogScopeId,
        containerNodeId = BlockSettingPagesDialogContainerNodeId,
        itemNodeId = { blockSettingPageDialogItemNodeId(it) },
        onSubmit = { pages ->
            if (Prefs.blockEnabledPages == pages) return@BlockPageSelectDialog
            Prefs.blockEnabledPages = pages
            BlockManager.reloadFromPrefs()
            "已保存，仅对之后加载的内容生效".toast(context)
        }
    )
}

@Composable
private fun rememberBlockSettingDialogDismiss(
    onDismiss: () -> Unit
): () -> Unit {
    return remember(onDismiss) { onDismiss }
}

@Composable
private fun BlockSettingActionListItem(
    localFocusId: WjzFocusLocalId,
    title: String,
    supportText: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    SettingListItem(
        modifier = modifier
            .wjzFocusExits(
                localId = localFocusId,
                layer = WjzFocusLayer.Content,
                enabled = enabled,
                onFocusChanged = { focused = it }
            ),
        enabled = enabled,
        focused = focused,
        title = title,
        supportText = supportText,
        onClick = onClick
    )
}

@Composable
private fun rememberRelationGroupSnapshot(): RelationGroupSnapshot? {
    val raw = Prefs.followTagsCacheJson
    return remember(raw) {
        RelationGroupsDataSource.decodeSnapshotOrNull(raw)
            ?: RelationGroupsDataSource.getSnapshotOrNull()
    }
}
