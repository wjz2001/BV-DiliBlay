package dev.aaa1115910.bv.component.controllers

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId

internal const val PlayerControlsFocusComponentId = "playerControls"
internal val PlayerControlsFirstActionFocusLocalId =
    videoPlayerOverlayActionFocusLocalId("danmaku")

internal fun videoPlayerOverlayActionFocusLocalId(id: String): WjzFocusLocalId =
    wjzFocusLocalId("actions", id)

internal sealed interface VideoPlayerOverlayAction {
    val id: String
    val description: String
    val onClick: () -> Unit
    val focusLocalId: WjzFocusLocalId
        get() = videoPlayerOverlayActionFocusLocalId(id)

    data class Resource(
        override val id: String,
        @param:DrawableRes val iconRes: Int,
        override val description: String,
        override val onClick: () -> Unit
    ) : VideoPlayerOverlayAction

    data class Vector(
        override val id: String,
        val imageVector: ImageVector,
        override val description: String,
        override val onClick: () -> Unit
    ) : VideoPlayerOverlayAction
}
