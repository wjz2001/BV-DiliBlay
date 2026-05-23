package dev.aaa1115910.bv.wjzfocus

import androidx.compose.animation.core.Transition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * 在短暂过渡期内锁住当前 [WjzFocusCoordinator]。
 *
 * 这里的“锁住”是指：Host 会吞掉按键，协调器不会立刻执行新的焦点跳转，
 * 而是把 pending 请求延后到解锁后统一处理。
 *
 * `locked = true` 时会向 coordinator 注册一个锁 token；`locked = false` 或离开
 * 组合时释放 token。用于动画、页面切换、Dialog 打开这类 UI 还未稳定的阶段，
 * 避免焦点提前进入尚未 ready 的节点。
 *
 * @param locked 是否处于需要锁焦点的阶段。
 * @param token 锁的身份。默认每个调用点 remember 一个稳定 token，用来保证同一调用点
 * 的加锁和解锁能准确配对。
 * @param coordinator 要锁定的焦点协调器；默认使用当前 CompositionLocal。
 */
@Composable
fun WjzFocusTransitionGuard(
    locked: Boolean,
    token: Any = remember { Any() },
    coordinator: WjzFocusCoordinator? = LocalWjzFocusCoordinator.current
) {
    DisposableEffect(coordinator, token, locked) {
        if (coordinator == null || !locked) {
            return@DisposableEffect onDispose { }
        }

        coordinator.lockFocus(token)
        onDispose {
            coordinator.unlockFocus(token)
        }
    }
}

/**
 * 使用 Compose [Transition] 的运行状态自动锁焦点。
 *
 * 当 [transition] 正在运行时等价于 `WjzFocusTransitionGuard(locked = true)`；
 * 动画结束后自动释放锁。
 *
 * 适合直接绑定到 `AnimatedContent`、`updateTransition` 等已有 [Transition] 的场景，
 * 避免手动维护布尔锁状态。
 */
@Composable
fun WjzFocusTransitionGuard(
    transition: Transition<*>,
    token: Any = remember { Any() },
    coordinator: WjzFocusCoordinator? = LocalWjzFocusCoordinator.current
) {
    WjzFocusTransitionGuard(
        locked = transition.isRunning,
        token = token,
        coordinator = coordinator
    )
}
