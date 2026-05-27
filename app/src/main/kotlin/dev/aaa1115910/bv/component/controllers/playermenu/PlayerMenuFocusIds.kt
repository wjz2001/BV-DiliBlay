package dev.aaa1115910.bv.component.controllers.playermenu

import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId

internal const val PlayerMenuRootFocusId = "player-menu/root"
internal const val PlayerMenuNavFocusIdPrefix = "player-menu/nav"
internal const val PlayerMenuPictureFocusIdPrefix = "player-menu/picture"
internal const val PlayerMenuPlaySpeedFocusIdPrefix = "player-menu/play-speed"
internal const val PlayerMenuDanmakuFocusIdPrefix = "player-menu/danmaku"
internal const val PlayerMenuClosedCaptionFocusIdPrefix = "player-menu/closed-caption"

internal fun playerMenuFocusNodeId(scopeId: WjzFocusScopeId?, focusId: String): WjzFocusNodeId {
    val resolvedScopeId = scopeId?.value ?: "__wjz_focus_sugar__"
    return WjzFocusNodeId("$resolvedScopeId/$focusId")
}
