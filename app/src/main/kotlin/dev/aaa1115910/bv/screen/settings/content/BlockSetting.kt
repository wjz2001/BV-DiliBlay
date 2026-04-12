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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.relation.RelationGroupSnapshot
import dev.aaa1115910.bv.relation.RelationGroupsDataSource
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BlockSetting(
    modifier: Modifier = Modifier,
    contentActive: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var updating by remember { mutableStateOf(false) }
    var updateJob by remember { mutableStateOf<Job?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }

    val updateFocusRequester = remember { FocusRequester() }
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

    val disabledModifier = Modifier
        .alpha(0.5f)
        .focusProperties { canFocus = false }

    DisposableEffect(Unit) {
        onDispose {
            updateJob?.cancel()
            updateJob = null
            updating = false
        }
    }

    LaunchedEffect(contentActive) {
        if (contentActive) {
            updateFocusRequester.requestFocus(scope)
        }
    }

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

        SettingListItem(
            enabled = !updating && hasSnapshot,
                title = stringResource(R.string.block_setting_groups_title),
            supportText = when {
                !hasSnapshot -> "未获取分组，请先点击立即更新"
                else -> "已选择 $groupSelectedCount / ${tags.size} 个分组"
            },
            onClick = { showGroupDialog = true }
        )

        SettingListItem(
            enabled = !updating && hasSnapshot,
            title = stringResource(R.string.block_setting_pages_title),
            supportText = when {
                !hasSnapshot -> "需先点击立即更新"
                pagesSelected.isEmpty() -> stringResource(R.string.block_setting_pages_support_empty)
                else -> context.getString(
                    R.string.block_setting_pages_support,
                    pagesSelected.joinToString(",") { it.displayName }
                )
            },
            onClick = { showPageDialog = true }
        )

        SettingListItem(
            modifier = Modifier.focusRequester(updateFocusRequester),
            enabled = !updating,
            title = stringResource(R.string.block_setting_update_now_title),
            supportText = when {
                updating -> stringResource(R.string.block_setting_update_now_support_updating)
                !hasSnapshot -> "当前无快照，点击开始拉取"
                else -> "当前快照 ${tags.size} 个分组 / ${snapshot?.users?.size ?: 0} 个用户"
            },
            onClick = {
                val hasAuth = Prefs.uid != 0L &&
                        (Prefs.sessData.isNotBlank() || Prefs.accessToken.isNotBlank())
                if (!hasAuth) {
                    context.getString(R.string.block_setting_update_now_need_login).toast(context)
                    return@SettingListItem
                }

                updating = true
                updateJob = scope.launch(Dispatchers.IO) {
                    try {
                        val result = BlockManager.updateByUser()

                        withContext(Dispatchers.Main) {
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
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        withContext(Dispatchers.Main) {
                            ("更新失败：" + (t.localizedMessage ?: t.javaClass.simpleName)).toast(context)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            updating = false
                            updateJob = null
                        }
                    }
                }
            }
        )
    }

    BlockGroupSelectDialog(
        show = showGroupDialog,
        title = stringResource(R.string.block_setting_groups_dialog_title),
        tags = tags,
        initialSelectedTagIds = effectiveSelectedTagIds,
        onHideDialog = { showGroupDialog = false },
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
        onHideDialog = { showPageDialog = false },
        onSubmit = { pages ->
            if (Prefs.blockEnabledPages == pages) return@BlockPageSelectDialog
            Prefs.blockEnabledPages = pages
            BlockManager.reloadFromPrefs()
            "已保存，仅对之后加载的内容生效".toast(context)
        }
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
