package dev.aaa1115910.bv.wjzfocus

/**
 * 组件和业务层提交给 coordinator 仲裁的公开焦点意图。
 *
 * InitialEntry 是通用组件初始首焦，低优先级。
 * ExternalEntry 是外部明确入口请求。
 * LayerEntry 是 layer 激活后的首焦。
 * ContentFallback 是内容状态变化后的兜底恢复。
 */
sealed interface WjzFocusSubmitIntent {
    val dedupeKey: Any?

    data class InitialEntry(
        override val dedupeKey: Any
    ) : WjzFocusSubmitIntent

    data class ExternalEntry(
        override val dedupeKey: Any? = null,
        val activateLayer: Boolean = false,
        val enqueueUntilLayerActive: Boolean = true,
        val enqueueIfMissing: Boolean = true
    ) : WjzFocusSubmitIntent

    data class LayerEntry(
        override val dedupeKey: Any? = null
    ) : WjzFocusSubmitIntent

    data class ContentFallback(
        override val dedupeKey: Any
    ) : WjzFocusSubmitIntent
}
