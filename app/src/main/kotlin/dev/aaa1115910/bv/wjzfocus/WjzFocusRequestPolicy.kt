package dev.aaa1115910.bv.wjzfocus

/**
 * WjzFocus 发起焦点请求时传给目标节点的来源信息。
 *
 * 这个来源只覆盖 WjzFocus coordinator 能统一仲裁的请求，例如公开 entry、router、恢复、
 * fallback 和直接 node 请求。Compose 原生几何焦点搜索如果没有被 WjzFocus 的 router/exits
 * 消费，可能不会经过 coordinator，因此也不会产生这里的来源对象。
 *
 * 这里刻意描述“请求从哪里进入 WjzFocus”，而不是描述业务按键。目标节点的
 * [WjzFocusRequestPolicy] 可以据此拒绝某些 WjzFocus 请求，例如只允许指定公开 entry
 * 进入输入框，同时拒绝恢复、fallback 或内部 router 的请求。
 */
sealed interface WjzFocusRequestSource {
    /**
     * 请求来自公开 entry。
     *
     * [entryId] 是调用方请求的公开入口，例如 `searchInput/keyword`。如果请求来自
     * [WjzFocusCoordinator.submitEntryFocusIntent]，则 [intent] 保留原始提交意图；如果来自低层
     * `requestEntryFocusDetailed`，则 [intent] 为 null。
     */
    data class Entry(
        val entryId: WjzFocusEntryId,
        val intent: WjzFocusSubmitIntent? = null
    ) : WjzFocusRequestSource

    /**
     * 请求来自直接 node 请求。
     *
     * 这类请求没有公开 entry 语义，通常由组件内部语法糖、router 或少量低层调用发起。
     */
    data class Node(
        val intent: WjzFocusSubmitIntent? = null
    ) : WjzFocusRequestSource

    /**
     * 请求来自 layer/scope 恢复流程。
     *
     * 这类请求用于 Activity 恢复、Host 重新进入、Back/Escape 从输入态退出等恢复场景。
     */
    data object Restore : WjzFocusRequestSource

    /**
     * 请求来自 fallback 流程。
     *
     * fallback 只表示“没有更精确目标时的兜底落点”，目标节点可以用 policy 拒绝自己成为兜底。
     */
    data object Fallback : WjzFocusRequestSource

    /**
     * 请求来自旧的低层调用路径，调用方没有提供更具体来源。
     *
     * 新代码应优先使用 entry/node submit 语法糖，让来源尽量可追踪。
     */
    data object Direct : WjzFocusRequestSource
}

/**
 * 目标节点的 WjzFocus 请求准入策略。
 *
 * policy 只在 coordinator 准备执行 WjzFocus 请求时生效，用于过滤 WjzFocus 自己发起的请求。
 * 它不会拦截未经过 coordinator 的 Compose 原生几何焦点搜索；如果需要封锁那类路径，仍应在源侧使用 WjzFocus router/exits 消费方向，
 * 或者使用 `wjzDisabledFocus` 让目标整体不参与焦点。
 *
 * 返回 true 表示允许本次 WjzFocus 请求继续执行；返回 false 表示拒绝本次请求，coordinator 会把
 * 结果视为 [WjzFocusRequestResult.Dropped]，不会执行底层 requester，也不会把请求入队等待。
 */
fun interface WjzFocusRequestPolicy {
    fun allow(source: WjzFocusRequestSource): Boolean
}

/** 默认请求策略：不限制任何 WjzFocus 请求，保持历史行为。 */
val WjzFocusAllowAllRequests = WjzFocusRequestPolicy { true }
