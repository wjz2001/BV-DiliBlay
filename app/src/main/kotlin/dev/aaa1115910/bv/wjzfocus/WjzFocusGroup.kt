package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier

/**
 * WjzFocus 底层分组边界封装。
 *
 * 业务/UI 层需要 Compose 焦点组语义时通过本函数接入，避免直接依赖原生 focusGroup。
 *
 * 它只声明“这里是一个原生焦点搜索分组”，不注册 WjzFocus 节点，也不记录组内最近焦点。
 * 如果需要离开后恢复到组内最近节点，请使用 [Modifier.wjzFocusRestorerHost]。
 *
 * @param enabled 为 false 时保持原 modifier，不创建焦点组边界。
 */
fun Modifier.wjzFocusGroup(
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    return focusGroup()
}
