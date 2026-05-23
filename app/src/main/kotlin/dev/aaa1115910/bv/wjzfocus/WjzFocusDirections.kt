package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEvent
import dev.aaa1115910.bv.util.isDpadDown
import dev.aaa1115910.bv.util.isDpadLeft
import dev.aaa1115910.bv.util.isDpadRight
import dev.aaa1115910.bv.util.isDpadUp

/** 只包含左方向的 DSL 常量。 */
val left = WjzFocusDirections(listOf(FocusDirection.Left))
/** 只包含右方向的 DSL 常量。 */
val right = WjzFocusDirections(listOf(FocusDirection.Right))
/** 只包含上方向的 DSL 常量。 */
val up = WjzFocusDirections(listOf(FocusDirection.Up))
/** 只包含下方向的 DSL 常量。 */
val down = WjzFocusDirections(listOf(FocusDirection.Down))

/** 同时表示左和右两个方向的 DSL 常量。 */
val horizontal = WjzFocusDirections(listOf(FocusDirection.Left, FocusDirection.Right))
/** 同时表示上和下两个方向的 DSL 常量。 */
val vertical = WjzFocusDirections(listOf(FocusDirection.Up, FocusDirection.Down))

/** 同时表示上和左两个方向的 DSL 常量。 */
val upleft = WjzFocusDirections(listOf(FocusDirection.Up, FocusDirection.Left))
/** 同时表示上和右两个方向的 DSL 常量。 */
val upright = WjzFocusDirections(listOf(FocusDirection.Up, FocusDirection.Right))
/** 同时表示下和左两个方向的 DSL 常量。 */
val downleft = WjzFocusDirections(listOf(FocusDirection.Down, FocusDirection.Left))
/** 同时表示下和右两个方向的 DSL 常量。 */
val downright = WjzFocusDirections(listOf(FocusDirection.Down, FocusDirection.Right))

/** 同时表示上、左、右三个方向的 DSL 常量。 */
val uphorizontal = WjzFocusDirections(
    listOf(
        FocusDirection.Up,
        FocusDirection.Left,
        FocusDirection.Right
    )
)
/** 同时表示下、左、右三个方向的 DSL 常量。 */
val downhorizontal = WjzFocusDirections(
    listOf(
        FocusDirection.Down,
        FocusDirection.Left,
        FocusDirection.Right
    )
)

/** 同时表示左、上、下三个方向的 DSL 常量。 */
val leftvertical = WjzFocusDirections(
    listOf(
        FocusDirection.Left,
        FocusDirection.Up,
        FocusDirection.Down
    )
)
/** 同时表示右、上、下三个方向的 DSL 常量。 */
val rightvertical = WjzFocusDirections(
    listOf(
        FocusDirection.Right,
        FocusDirection.Up,
        FocusDirection.Down
    )
)

/** 同时表示上、下、左、右四个方向的 DSL 常量。 */
val all = WjzFocusDirections(
    listOf(
        FocusDirection.Up,
        FocusDirection.Down,
        FocusDirection.Left,
        FocusDirection.Right
    )
)
/** `all` 的别名。 */
val inset = all

/**
 * DSL 层使用的方向集合。
 *
 * 单方向集合用于 exits/router 中表达一个方向；双方向集合用于一维/二维 resolver 表达轴顺序。
 * 例如 [horizontal] 的顺序是 Left -> Right，resolver 会把第一个方向解释为 index - 1，
 * 第二个方向解释为 index + 1。
 */
class WjzFocusDirections internal constructor(
    internal val directions: List<FocusDirection>
) {
    /**
     * 返回相反顺序的方向集合。
     *
     * 这主要服务 reversedLayout：`vertical.reversed` 表示 Down -> Up，
     * 因而 Down 对应 index - 1，Up 对应 index + 1。
     */
    val reversed: WjzFocusDirections
        get() = WjzFocusDirections(directions.asReversed())
}

internal fun KeyEvent.wjzFocusDirection(): FocusDirection? {
    return when {
        isDpadUp() -> FocusDirection.Up
        isDpadDown() -> FocusDirection.Down
        isDpadLeft() -> FocusDirection.Left
        isDpadRight() -> FocusDirection.Right
        else -> null
    }
}
