package dev.aaa1115910.bv.component.controllers.playermenu

import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId

internal const val PlayerMenuRootFocusId = "player-menu/root"
internal const val PlayerMenuNavFocusIdPrefix = "player-menu/nav"
internal const val PlayerMenuPictureFocusIdPrefix = "player-menu/picture"
internal const val PlayerMenuPlaySpeedFocusIdPrefix = "player-menu/play-speed"
internal const val PlayerMenuDanmakuFocusIdPrefix = "player-menu/danmaku"
internal const val PlayerMenuClosedCaptionFocusIdPrefix = "player-menu/closed-caption"

internal val PlayerMenuRootNodeId = WjzFocusNodeId(PlayerMenuRootFocusId)

internal enum class PlayerMenuFocusRoot(
    val prefix: String
) {
    Nav(PlayerMenuNavFocusIdPrefix),
    Picture(PlayerMenuPictureFocusIdPrefix),
    PlaySpeed(PlayerMenuPlaySpeedFocusIdPrefix),
    Danmaku(PlayerMenuDanmakuFocusIdPrefix),
    ClosedCaption(PlayerMenuClosedCaptionFocusIdPrefix)
}

internal data class PlayerMenuFocusPrefix(
    val value: String
) {
    fun child(vararg childParts: Any): PlayerMenuFocusPrefix {
        return PlayerMenuFocusPrefix(
            value = (listOf(value) + childParts.map { it.toString() }).joinToString("/")
        )
    }

    fun localId(vararg childParts: Any): WjzFocusLocalId {
        return playerMenuLocalFocusId(value, *childParts)
    }

    fun nodeId(vararg childParts: Any): WjzFocusNodeId {
        return playerMenuNodeId(value, *childParts)
    }

    fun listIds() = PlayerMenuFocusListIds(
        restorerId = "$value/restorer",
        listId = "$value/list"
    )
}

internal data class PlayerMenuFocusListIds(
    val restorerId: String,
    val listId: String
)

internal fun playerMenuFocusPrefix(
    root: PlayerMenuFocusRoot,
    vararg parts: Any
): PlayerMenuFocusPrefix {
    return PlayerMenuFocusPrefix(
        value = (listOf(root.prefix) + parts.map { it.toString() }).joinToString("/")
    )
}

internal fun playerMenuFocusPrefix(prefix: String): PlayerMenuFocusPrefix = PlayerMenuFocusPrefix(prefix)

internal fun playerMenuLocalFocusId(vararg parts: Any): WjzFocusLocalId = wjzFocusLocalId(*parts)

internal fun playerMenuNodeId(vararg parts: Any): WjzFocusNodeId {
    return WjzFocusNodeId(parts.joinToString("/") { it.toString() })
}
