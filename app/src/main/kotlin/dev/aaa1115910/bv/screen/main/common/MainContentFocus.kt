package dev.aaa1115910.bv.screen.main.common

import dev.aaa1115910.bv.component.TopNavEntryFocusConsumed
import dev.aaa1115910.bv.component.TopNavEntryFocusResolution
import dev.aaa1115910.bv.component.TopNavEntryFocusTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId

enum class MainContentEntryTarget {
    TopEntry,
    LeftEntry
}

const val MainContentFocusComponentId = "main"
const val MainTopNavFocusComponentId = "topNav"
const val MainDrawerFocusComponentId = "drawer"

val MainContentEntryId = WjzFocusEntryId("$MainContentFocusComponentId/content")
val MainContentTopEntryId = WjzFocusEntryId("$MainContentFocusComponentId/top")
val MainContentLeftEntryId = WjzFocusEntryId("$MainContentFocusComponentId/left")

val MainTopNavDefaultEntryId = WjzFocusEntryId("$MainTopNavFocusComponentId/default")
val MainDrawerRightEntryId = WjzFocusEntryId("$MainDrawerFocusComponentId/right")

enum class MainContentEntryState {
    Pending,
    Ready,
    Consumed,
    Rejected
}

data class MainContentEntryTransition(
    val requestId: Long,
    val target: MainContentEntryTarget,
    val state: MainContentEntryState
)

data class MainContentEntryRequest(
    val id: Long,
    val target: MainContentEntryTarget
)

enum class MainContentNavigationExitEntry(
    val entryId: WjzFocusEntryId
) {
    TopNavDefault(MainTopNavDefaultEntryId),
    DrawerRight(MainDrawerRightEntryId)
}

data class MainContentEntryFocusRequest(
    val id: Long,
    val target: MainContentEntryTarget,
    val topNavTarget: TopNavEntryFocusTarget
)

class MainContentEntryAdapter(
    private val entryRequest: MainContentEntryRequest?,
    private val active: Boolean,
    private val onDefaultFocusReady: (() -> Unit)?,
    private val onEntryRequestReady: (Long) -> Unit,
    private val onEntryRequestConsumed: (Long) -> Unit,
    private val onEntryRequestRejected: (Long) -> Unit
) {
    val topNavEntryFocusRequest: MainContentEntryFocusRequest?
        get() = entryRequest?.let { request ->
            MainContentEntryFocusRequest(
                id = request.id,
                target = request.target,
                topNavTarget = request.target.toTopNavEntryFocusTarget()
            )
        }

    val topNavEntryFocusTarget: TopNavEntryFocusTarget
        get() = topNavEntryFocusRequest?.topNavTarget ?: TopNavEntryFocusTarget.DefaultEntry

    fun onDefaultFocusReady(request: MainContentEntryFocusRequest? = topNavEntryFocusRequest) {
        if (!active) return
        onDefaultFocusReady?.invoke()
    }

    fun onTopNavEntryFocusResolution(
        request: MainContentEntryFocusRequest? = topNavEntryFocusRequest,
        resolution: TopNavEntryFocusResolution
    ) {
        if (!active) return
        request ?: return
        if (!request.matchesCurrentEntryRequest()) return

        when (resolution) {
            is TopNavEntryFocusResolution.Ready -> {
                if (resolution.ready.target == request.topNavTarget) {
                    onEntryRequestReady(request.id)
                }
            }

            is TopNavEntryFocusResolution.Reject -> {
                if (resolution.target == request.topNavTarget) {
                    onEntryRequestRejected(request.id)
                }
            }

            is TopNavEntryFocusResolution.Pending -> Unit
        }
    }

    fun onTopNavEntryFocusConsumed(
        request: MainContentEntryFocusRequest? = topNavEntryFocusRequest,
        consumed: TopNavEntryFocusConsumed
    ) {
        if (!active) return
        request ?: return
        if (!request.matchesCurrentEntryRequest()) return
        if (consumed.target == request.topNavTarget) {
            onEntryRequestConsumed(request.id)
        }
    }

    private fun MainContentEntryFocusRequest.matchesCurrentEntryRequest(): Boolean {
        val current = entryRequest ?: return false
        return current.id == id && current.target == target
    }
}

fun mainContentEntryAdapter(
    entryRequest: MainContentEntryRequest?,
    active: Boolean,
    onDefaultFocusReady: (() -> Unit)?,
    onEntryRequestReady: (Long) -> Unit = {},
    onEntryRequestConsumed: (Long) -> Unit,
    onEntryRequestRejected: (Long) -> Unit = {}
): MainContentEntryAdapter {
    return MainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
}

private fun MainContentEntryTarget?.toTopNavEntryFocusTarget(): TopNavEntryFocusTarget {
    return when (this) {
        MainContentEntryTarget.TopEntry -> TopNavEntryFocusTarget.DefaultEntry
        MainContentEntryTarget.LeftEntry -> TopNavEntryFocusTarget.LeftEntry
        null -> TopNavEntryFocusTarget.DefaultEntry
    }
}

/**
 * Content entry protocol for MainScreen.
 *
 * Entries:
 * - TopEntry: enter the top side of the active content page public entry.
 * - LeftEntry: enter the left side of the active content page public entry.
 *
 * State machine:
 * - Pending: a request has been recorded, but the target entry is not confirmed ready.
 * - Ready: the active target page has reported the requested entry can be requested.
 * - Consumed: focus has moved through the target entry and the request may be cleared.
 * - Rejected: the active target page cannot handle the request; keep the previous focus owner.
 *
 * Advancement rules:
 * - Creating a request only enters Pending. It must not commit final active/focused business state.
 * - Ready is driven by the target content entry owner, not by an execution return value.
 * - Consumed is reported only after the target entry owner confirms the matching request id.
 * - Rejected clears no focus by itself and must leave the original focus in place.
 *
 * Pending cleanup:
 * - Clear pending only on Consumed, Rejected, or when replacing it with a newer request.
 * - Drop stale pending when its request id no longer matches the active transition.
 * - If the target is not ready, keep pending and do not proactively clear the old module focus.
 *
 * Constraints:
 * - This protocol is pure data: no closures, Compose UI objects, or ViewModel references.
 * - Active page switching and focus confirmation are separate states, not a single Boolean.
 * - Business state must not be finalized from the request return value.
 */
