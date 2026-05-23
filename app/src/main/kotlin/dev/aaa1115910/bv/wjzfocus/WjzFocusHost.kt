package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.aaa1115910.bv.util.isConfirmKey
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp

/**
 * 焦点 Host，负责提供 coordinator 、scope，并承载区域级进入、退出协议。
 *
 * Host 会把内部节点注册到同一个 scope，处理 Compose 的进入和离开边界，
 * 安装 focusGroup，并在窗口恢复或生命周期恢复时尝试恢复当前 active layer。
 * 它是大多数模块接入焦点协议的边界容器。
 *
 * 可以把 Host 理解成“焦点模块边界”：
 *
 * 1. Host 内部的节点默认共享同一个 [scopeId]。
 * 2. 节点级 [WjzFocusNodeExit] 只表达单个节点自己的离开语义。
 * 3. Host 级 [WjzFocusHostExit] 表达整个区域离开时的边界行为。
 *
 * 业务页面通常会先有一个 [WjzFocusHost]，再在内部放多个 `Modifier.wjzFocusExits(...)` 或 [wjzFocusableExits] 节点。
 * 如果没有 Host：
 *
 * 1. 节点仍可能注册成功，但缺少稳定模块边界。
 * 2. 依赖最近 scope 的出口 、恢复语义会退化。
 * 3. 跨区域恢复、来源记录和 Host 边界出口都无法完整发挥作用。
 *
 * @param coordinator 当前 Host 使用的协调器。
 * @param layer 该 Host 默认工作的焦点层。
 * @param scopeId 该 Host 的 scope，为 null 时自动生成一个稳定默认 scope。
 * @param exits Host 级方向出口。
 * @param content Host 内部内容，其中注册的节点默认共享本 Host 的 scope。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WjzFocusHost(
    modifier: Modifier = Modifier,
    coordinator: WjzFocusCoordinator = rememberWjzFocusCoordinator(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    exits: List<WjzFocusHostExit> = emptyList(),
    content: @Composable () -> Unit
) {
    val hostKeyHash = remember { Any() }
    val defaultScopeId = remember(hostKeyHash) {
        WjzFocusScopeId("wjz-focus-host-$hostKeyHash")
    }
    val resolvedScopeId = scopeId ?: defaultScopeId
    val windowInfo = LocalWindowInfo.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(coordinator, resolvedScopeId) {
        onDispose {
            coordinator.cancelPendingRequests(scopeId = resolvedScopeId)
        }
    }

    SideEffect {
        coordinator.updateHostExits(
            token = hostKeyHash,
            scopeId = resolvedScopeId,
            exits = exits
        )
    }

    DisposableEffect(coordinator, hostKeyHash) {
        onDispose {
            coordinator.unregisterHostExits(hostKeyHash)
        }
    }

    LaunchedEffect(windowInfo.isWindowFocused, resolvedScopeId, layer) {
        if (windowInfo.isWindowFocused) {
            coordinator.restoreHostOnResume(
                layer = layer,
                scopeId = resolvedScopeId
            )
        }
    }

    DisposableEffect(lifecycleOwner, coordinator, resolvedScopeId, layer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coordinator.restoreHostOnResume(
                    layer = layer,
                    scopeId = resolvedScopeId
                )
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(
        LocalWjzFocusCoordinator provides coordinator,
        LocalWjzFocusScopeId provides resolvedScopeId
    ) {
        Box(
            modifier = modifier
                // 焦点锁拦截 dpad 方向键和确认键，让过渡期内的焦点不会丢失。
                .onPreviewKeyEvent { event ->
                    if (!coordinator.isFocusLocked) {
                        return@onPreviewKeyEvent false
                    }

                    if (event.isConfirmKey()) {
                        return@onPreviewKeyEvent true
                    }

                    val direction = event.wjzFocusDirection()
                    when {
                        direction != null && event.isKeyDown() -> {
                            coordinator.recordLockedDirectionIntent(direction)
                            true
                        }
                        direction != null && event.isKeyUp() -> true
                        else -> false
                    }
                }
                .focusProperties focusProperties@{
                    onEnter = {
                        // Host 从 Compose 进入时，coordinator 内部请求触发的级联进入直接放行；外部进入统一交给 coordinator 处理。
                        if (!coordinator.isCoordinatorFocusing()) {
                            when (
                                coordinator.enterFocusRequest(
                                    layer = layer,
                                    scopeId = resolvedScopeId
                                )
                            ) {
                                WjzFocusEnterRequestResult.Focused -> {}
                                WjzFocusEnterRequestResult.NativeSearch -> {
                                    coordinator.enqueueAndTryRestoreLayer(
                                        layer = layer,
                                        scopeId = resolvedScopeId
                                    )
                                    cancelFocusChange()
                                }
                                WjzFocusEnterRequestResult.Cancelled,
                                WjzFocusEnterRequestResult.Failed -> cancelFocusChange()
                            }
                        }
                    }
                    this@focusProperties.onExit = {
                        // Host 从 Compose 离开时，coordinator 内部请求触发的级联离开直接放行；外部离开统一交给 coordinator 处理。
                        if (!coordinator.isCoordinatorFocusing()) {
                            exits.firstOrNull { it.direction == requestedFocusDirection }
                                ?.consume(coordinator)
                            cancelFocusChange()
                        }
                    }
                }
                // 把当前 Box 视为一个 Wjz 焦点组边界，统一收口原生 focusGroup。
                .wjzFocusGroup()
        ) {
            content()
        }
    }
}

@Composable
fun WjzFocusHostExits(
    token: Any,
    scopeId: WjzFocusScopeId? = null,
    exits: List<WjzFocusHostExit>
) {
    val coordinator = LocalWjzFocusCoordinator.current ?: return
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current ?: return
    val currentExits = rememberUpdatedState(exits)

    SideEffect {
        coordinator.updateHostExits(
            token = token,
            scopeId = resolvedScopeId,
            exits = currentExits.value
        )
    }

    DisposableEffect(coordinator, token) {
        onDispose {
            coordinator.unregisterHostExits(token)
        }
    }
}

