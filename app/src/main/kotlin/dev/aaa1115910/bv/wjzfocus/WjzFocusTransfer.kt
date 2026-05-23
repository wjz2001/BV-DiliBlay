package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.focus.FocusDirection

/**
 * 节点和 Host 离开当前边界时的语义目标。
 *
 * `Entry` 表示把焦点交给另一个公开 entry，由 [WjzFocusEntriesHost] 再解析成真实节点；
 * `Cancel` 表示当前方向在该边界被消费，不继续向外传播。内部移动不应该通过这里表达，
 * 内部拓扑应使用 [WjzFocusRouteResolver] 或 [WjzLazyFocusRouteResolver]。
 */
sealed interface WjzFocusNodeExitTarget {
    /** 跳转到另一个组件暴露出来的公开 entry。 */
    data class Entry(
        val entryId: WjzFocusEntryId
    ) : WjzFocusNodeExitTarget

    /** 当前方向被边界消费，不触发外部 entry 跳转。 */
    data object Cancel : WjzFocusNodeExitTarget
}

/**
 * 单个焦点节点的方向出口。
 *
 * 它只描述“当前节点在某方向离开自己时怎样交给外部协议”，不参与同一组件内部的相邻项计算。
 */
data class WjzFocusNodeExit(
    val direction: FocusDirection,
    val target: WjzFocusNodeExitTarget
) {
    /** 兼容常用写法：直接把目标 entry 包装成 [WjzFocusNodeExitTarget.Entry]。 */
    constructor(
        direction: FocusDirection,
        targetEntryId: WjzFocusEntryId
    ) : this(
        direction = direction,
        target = WjzFocusNodeExitTarget.Entry(targetEntryId)
    )

    companion object {
        /** 构建消费指定方向的节点出口。 */
        fun cancel(direction: FocusDirection): WjzFocusNodeExit {
            return WjzFocusNodeExit(
                direction = direction,
                target = WjzFocusNodeExitTarget.Cancel
            )
        }
    }
}

/**
 * Host 级方向出口。
 *
 * Host 出口表示整个 scope/模块边界在某方向离开时的下一跳，优先级低于节点自己的 exits。
 */
data class WjzFocusHostExit(
    val direction: FocusDirection,
    val target: WjzFocusNodeExitTarget
) {
    /** 兼容常用写法：直接把目标 entry 包装成 [WjzFocusNodeExitTarget.Entry]。 */
    constructor(
        direction: FocusDirection,
        targetEntryId: WjzFocusEntryId
    ) : this(
        direction = direction,
        target = WjzFocusNodeExitTarget.Entry(targetEntryId)
    )

    companion object {
        /** 构建消费指定方向的 Host 出口。 */
        fun cancel(direction: FocusDirection): WjzFocusHostExit {
            return WjzFocusHostExit(
                direction = direction,
                target = WjzFocusNodeExitTarget.Cancel
            )
        }
    }
}

/** 消费节点级出口：出口链路可直接走 coordinator 底层 entry 请求。 */
internal fun WjzFocusNodeExit.consume(
    coordinator: WjzFocusCoordinator?
): Boolean {
    return when (val resolvedTarget = target) {
        WjzFocusNodeExitTarget.Cancel -> true
        is WjzFocusNodeExitTarget.Entry -> when (coordinator?.requestEntryFocusDetailed(resolvedTarget.entryId)) {
            WjzFocusRequestResult.Focused,
            WjzFocusRequestResult.Enqueued -> true
            WjzFocusRequestResult.Dropped,
            WjzFocusRequestResult.Failed,
            null -> false
        }
    }
}

/** 消费 Host 级出口：出口链路可直接走 coordinator 底层 entry 请求。 */
internal fun WjzFocusHostExit.consume(
    coordinator: WjzFocusCoordinator?
): Boolean {
    return when (val resolvedTarget = target) {
        WjzFocusNodeExitTarget.Cancel -> true
        is WjzFocusNodeExitTarget.Entry -> when (coordinator?.requestEntryFocusDetailed(resolvedTarget.entryId)) {
            WjzFocusRequestResult.Focused,
            WjzFocusRequestResult.Enqueued -> true
            WjzFocusRequestResult.Dropped,
            WjzFocusRequestResult.Failed,
            null -> false
        }
    }
}

