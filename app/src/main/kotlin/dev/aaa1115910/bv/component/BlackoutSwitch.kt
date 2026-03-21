package dev.aaa1115910.bv.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * 单树切换：过渡期间不会同时存在“两棵不同页面的内容子树”（仍会因遮罩 alpha 变化触发外层重组）。
 *
 * - targetState 变化时：先黑场淡入 -> 切换 displayedState -> 黑场淡出
 * - 设计目标：替代 AnimatedContent 的“双树转场”，在低性能 TV 盒子上减少掉帧
 *
 * 重要：
 * - 使用 Animatable 串行动画，尽量把时序收敛到一个协程里：alpha=1.0 -> 切状态 -> alpha=0.0
 * - 默认安全策略：displayedState 用 remember（不会因为 T 不可保存而在运行时报错）
 * - 主线建议：除非你明确要做“进程重建恢复当前页”，否则不要用 Saveable（TV 盒子上这一点通常收益很小，但出错面更大）。
 *   - 如你确实需要 Saveable 版本：请看文末附录（把 Saveable 单独放附录，避免误用导致 cannot be saved）。
 *
 * 说明（别误解“严格时序/逐次必达”）：
 * - 实现顺序是：黑场淡入 -> 切状态 -> 黑场淡出；（但不保证“用户一定看到一帧纯黑”，严格保证属于 Step4 的 Activity 级黑场）
 * - 快速连切时：中间 targetState 可能被跳过（最终一致）；只保证“最后一次 targetState”会落地，且不会出现 AnimatedContent 那种双树过渡；
 * - 边界兜底：快速连切/协程取消时，以“把遮罩可靠地退回 0”优先，可能出现“没有切换但遮罩淡出”的恢复动画（只发生在 alpha>0 的异常残留场景）。
 * - switchDelayMillis 默认 0：主线不建议调大，它会**直接延长黑屏时间**，TV 上很容易被用户当成“卡死/罚站”。
 * - onSwitched 必须是 O(1) 的轻操作（只做“同步 displayed 状态”或发轻量事件）：禁止在里面做 IO/解析/同步等待/大计算/页面初始化，否则会把黑场拖长成“罚站”。
 *   如果你确实需要“逐次必达/每次切页都必须执行副作用”：不要用这个 eventually-consistent 的切换器；改用队列/Mutex 串行执行，或把副作用绑定到 requestedDrawerItem（并自行处理取消/幂等）。
 */
@Composable
fun <T> BlackoutSwitch(
    targetState: T,
    modifier: Modifier = Modifier,
    blackoutColor: Color = Color.Black,
    fadeInMillis: Int = 90,
    fadeOutMillis: Int = 90,
    switchDelayMillis: Long = 0L,
    onSwitched: (T) -> Unit = {},
    content: @Composable (T) -> Unit
) {
    var displayedState by remember { mutableStateOf(targetState) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(targetState) {
        // 兜底：快速连切/取消可能导致 alpha 停在半黑或全黑；即使状态没变，也要把遮罩淡出
        if (targetState == displayedState) {
            if (alphaAnim.value > 0f) {
                if (fadeOutMillis <= 0) alphaAnim.snapTo(0f)
                else alphaAnim.animateTo(0f, animationSpec = tween(durationMillis = fadeOutMillis))
            }
            return@LaunchedEffect
        }

        // 特判：fade=0 且无额外 delay 时，直接切状态，不走“黑一下”的路径（Step2 的 TopNav 扫焦点场景尤其需要）
        if (fadeInMillis <= 0 && fadeOutMillis <= 0 && switchDelayMillis <= 0L) {
            if (alphaAnim.value != 0f) alphaAnim.snapTo(0f)
            displayedState = targetState
            onSwitched(targetState)
            return@LaunchedEffect
        }

        // 加固：低端盒子 fade=0ms 时直接 snap，避免 0ms 动画仍触发一次调度/挂起
        if (fadeInMillis <= 0) alphaAnim.snapTo(1f)
        else alphaAnim.animateTo(1f, animationSpec = tween(durationMillis = fadeInMillis))
        displayedState = targetState
        onSwitched(targetState)
        if (switchDelayMillis > 0) delay(switchDelayMillis)
        if (fadeOutMillis <= 0) alphaAnim.snapTo(0f)
        else alphaAnim.animateTo(0f, animationSpec = tween(durationMillis = fadeOutMillis))
    }

    Box(modifier = modifier) {
        content(displayedState)
        val a = alphaAnim.value
        if (a > 0f) {
            // 用带 alpha 的背景色，避免 `.alpha(...)` 触发额外图层带来的开销
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(blackoutColor.copy(alpha = a))
            )
        }
    }
}