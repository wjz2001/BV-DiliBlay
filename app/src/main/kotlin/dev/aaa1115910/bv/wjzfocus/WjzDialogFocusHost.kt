package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

private const val DialogOpeningReadyTimeoutMillis = 1_000L

/**
 * Dialog/Popup 独立窗口使用的焦点 Host。
 *
 * Dialog 拥有自己的 window，可能临时接管主屏幕 scope 之外的焦点。
 * 该组件会创建Dialog 内部 coordinator 、scope，激活 Dialog layer，安装兜底焦点容器，
 * 并在销毁时恢复来源 layer。
 *
 * 这个组件解决的是“跨 window 焦点边界”问题，而不是普通页面内的区域跳转问题。
 * 普通内容优先使用 [WjzFocusHost]，只有像 Dialog、Popup 这样拥有独立 window 的场景才能单独使用这个 Host。
 *
 * 这是独立窗口唯一允许的桥接例外。普通内容不准借它绕过 [WjzFocusHost] 、[wjzFocusableExits] 的正常边界。
 *
 * [mainCoordinator] 负责主窗口来源记录与恢复，[dialogCoordinator] 负责 Dialog 窗口内部节点调度。
 * 两者可以是不同实例，只有 Dialog/Popup 这种跨 window 场景才应这样做，普通组件不准套用这套规则。
 *
 * @param mainCoordinator Dialog 背后主屏幕所属的 coordinator。
 * @param dialogCoordinator Dialog 内部使用的 coordinator，通常独立于主屏幕。
 * @param sourceScopeId Dialog 关闭后传给主窗口来源恢复的 scope，可为空；来源记录已有 scope 时优先恢复来源 scope。
 * @param dialogScopeId Dialog Host 和兜底容器使用的 scope。
 * @param containerNodeId Dialog 内部兜底容器节点。
 * @param locked 为 true 时抑制 Dialog 内容内的焦点移动，适用于打开、关闭动画或内容尚未稳定的阶段。
 * @param restoreSourceOnDispose 是否在 Dialog 销毁时恢复主屏幕来源焦点。
 */
@Composable
fun WjzDialogFocusHost(
    modifier: Modifier = Modifier,
    mainCoordinator: WjzFocusCoordinator? = LocalWjzFocusCoordinator.current,
    dialogCoordinator: WjzFocusCoordinator = rememberWjzFocusCoordinator(),
    sourceScopeId: WjzFocusScopeId? = null,
    dialogScopeId: WjzFocusScopeId = rememberDialogFocusScopeId(),
    containerNodeId: WjzFocusNodeId = rememberDialogFocusNodeId(),
    locked: Boolean = false,
    restoreSourceOnDispose: Boolean = true,
    content: @Composable () -> Unit
) {
    // 当 Dialog 子节点都没有更合适目标时，容器节点是最终兜底。
    val containerFocusRequester = remember { FocusRequester() }
    // 首帧锁住焦点，避免打开动画期间焦点泄漏到错误窗口或错误节点。
    var internalOpeningLocked by remember { mutableStateOf(true) }
    val currentRestoreSourceOnDispose by rememberUpdatedState(restoreSourceOnDispose)
    val currentSourceScopeId by rememberUpdatedState(sourceScopeId)

    LaunchedEffect(dialogCoordinator, dialogScopeId, containerNodeId) {
        dialogCoordinator.awaitNodeReady(
            nodeId = containerNodeId,
            expectedGeneration = null,
            timeoutMillis = DialogOpeningReadyTimeoutMillis,
            layer = WjzFocusLayer.Dialog,
            scopeId = dialogScopeId
        )
        internalOpeningLocked = false
    }

    DisposableEffect(Unit) {
        // 切到 Dialog layer 前记录主窗口来源，关闭时才能精确恢复回去。
        val sourceToken = mainCoordinator?.activateLayer(
            layer = WjzFocusLayer.Dialog,
            recordSource = true
        )
        onDispose {
            if (currentRestoreSourceOnDispose) {
                mainCoordinator?.restoreSourceLayer(
                    scopeId = currentSourceScopeId,
                    expectedActiveLayer = WjzFocusLayer.Dialog,
                    token = sourceToken
                )
            }
        }
    }

    DisposableEffect(dialogCoordinator) {
        if (dialogCoordinator === mainCoordinator) {
            return@DisposableEffect onDispose { }
        }

        val sourceToken = dialogCoordinator.activateLayer(
            layer = WjzFocusLayer.Dialog,
            recordSource = true
        )
        onDispose {
            dialogCoordinator.restoreSourceLayer(
                expectedActiveLayer = WjzFocusLayer.Dialog,
                token = sourceToken
            )
        }
    }

    WjzFocusHost(
        modifier = modifier,
        coordinator = dialogCoordinator,
        layer = WjzFocusLayer.Dialog,
        scopeId = dialogScopeId
    ) {
        Box(
            modifier = Modifier.wjzFocusableExits(
                nodeId = containerNodeId,
                layer = WjzFocusLayer.Dialog,
                fallback = true,
                requester = containerFocusRequester
            )
        ) {
            // Dialog 打开、关闭阶段可能处于视觉和交互不同步状态，过渡期内先冻结导航最为安全。
            WjzFocusTransitionGuard(locked = locked || internalOpeningLocked)
            content()
        }
    }
}

/** 调用方未传 Dialog scope 时默认创建 scope id，避免不同独立窗口共用同一作用域。 */
@Composable
private fun rememberDialogFocusScopeId(): WjzFocusScopeId {
    val dialogKey = remember { Any() }
    return remember {
        WjzFocusScopeId("wjz-dialog-focus-host-$dialogKey")
    }
}

/** 创建 Dialog Host 兜底容器节点 id，供本窗口的 fallback 与恢复逻辑复用。 */
@Composable
private fun rememberDialogFocusNodeId(): WjzFocusNodeId {
    val dialogKey = remember { Any() }
    return remember {
        WjzFocusNodeId("wjz-dialog-focus-container-$dialogKey")
    }
}
