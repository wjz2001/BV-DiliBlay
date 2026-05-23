package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class WjzFocusCoordinatorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directionKeyRoutesAcrossHostEdge() {
        val coordinator = WjzFocusCoordinator()
        val sourceNodeId = WjzFocusNodeId("test/direction/source")
        val targetNodeId = WjzFocusNodeId("test/direction/target")
        val sourceScopeId = WjzFocusScopeId("scope/source")
        val targetScopeId = WjzFocusScopeId("scope/target")
        val targetEntryId = WjzFocusEntryId("test-direction/target")
        val sourceRequester = FocusRequester()
        val targetRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = targetScopeId
            ) {
                WjzFocusEntriesHost(
                    componentId = targetEntryId.componentId.value,
                    default = { defaultEntry(targetNodeId, WjzFocusLayer.Content, targetScopeId) },
                    entries = {
                        entry(
                            id = targetEntryId.localEntryValue,
                            target = entry(
                                id = targetEntryId.localEntryValue,
                                nodeId = targetNodeId,
                                layer = WjzFocusLayer.Content,
                                scopeId = targetScopeId
                            )
                        )
                    }
                )
                Box(
                    modifier = Modifier
                        .testTag("target")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = targetNodeId,
                            layer = WjzFocusLayer.Content,
                            requester = targetRequester,
                            onFocusChanged = { if (it) focusedNode = "target" }
                        )
                )
            }
            Row {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId,
                    exits = listOf(
                        WjzFocusHostExit(
                            FocusDirection.Right,
                            targetEntryId
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("source")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = sourceNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = sourceRequester,
                                onFocusChanged = { if (it) focusedNode = "source" }
                        )
                    )
                }
            }

            LaunchedEffect(Unit) {
                coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
            }
        }

        composeRule.waitUntil { focusedNode == "source" }
        composeRule.onNodeWithTag("source").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "target" }
    }

    @Test
    fun dpadRoutesAcrossPageBlocksAndWithinContent() {
        val coordinator = WjzFocusCoordinator()
        val navNodeId = WjzFocusNodeId("test/page-block/nav")
        val contentNodeIds = List(4) { WjzFocusNodeId("test/page-block/content/$it") }
        val sourceScopeId = WjzFocusScopeId("scope/nav")
        val targetScopeId = WjzFocusScopeId("scope/content")
        val contentEntryId = WjzFocusEntryId("test-page-block/content")
        val navRequester = FocusRequester()
        val contentRequesters = List(4) { FocusRequester() }
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = targetScopeId
            ) {
                WjzFocusEntriesHost(
                    componentId = contentEntryId.componentId.value,
                    default = {
                        defaultEntry(
                            nodeId = contentNodeIds.first(),
                            layer = WjzFocusLayer.Content,
                            scopeId = targetScopeId
                        )
                    },
                    entries = {
                        entry(
                            id = contentEntryId.localEntryValue,
                            target = entry(
                                id = contentEntryId.localEntryValue,
                                nodeId = contentNodeIds.first(),
                                layer = WjzFocusLayer.Content,
                                scopeId = targetScopeId
                            )
                        )
                    }
                )
                Column {
                    Row {
                        repeat(2) { index ->
                            Box(
                                modifier = Modifier
                                    .testTag("content-$index")
                                    .size(48.dp)
                                    .wjzFocusNode(
                                        nodeId = contentNodeIds[index],
                                        layer = WjzFocusLayer.Content,
                                        requester = contentRequesters[index],
                                        onFocusChanged = {
                                            if (it) focusedNode = "content-$index"
                                        }
                                    )
                            )
                        }
                    }
                    Row {
                        repeat(2) { rowIndex ->
                            val index = rowIndex + 2
                            Box(
                                modifier = Modifier
                                    .testTag("content-$index")
                                    .size(48.dp)
                                    .wjzFocusNode(
                                        nodeId = contentNodeIds[index],
                                        layer = WjzFocusLayer.Content,
                                        requester = contentRequesters[index],
                                        onFocusChanged = {
                                            if (it) focusedNode = "content-$index"
                                        }
                                    )
                            )
                        }
                    }
                }
            }
            Row {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId,
                    exits = listOf(
                        WjzFocusHostExit(
                            FocusDirection.Right,
                            contentEntryId
                        )
                    )
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .testTag("page-nav")
                                .size(48.dp)
                                .wjzFocusNode(
                                    nodeId = navNodeId,
                                    layer = WjzFocusLayer.Content,
                                    requester = navRequester,
                                    onFocusChanged = { if (it) focusedNode = "nav" }
                                )
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                coordinator.requestFocusDetailed(navNodeId, WjzFocusLayer.Content)
            }
        }

        composeRule.waitUntil { focusedNode == "nav" }
        composeRule.onNodeWithTag("page-nav").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "content-0" }
        composeRule.onNodeWithTag("content-0").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "content-1" }
        composeRule.onNodeWithTag("content-1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitUntil { focusedNode == "content-3" }
    }

    @Test
    fun transitionGuardConsumesDpadWhileLockedAndDefersFocusUntilUnlocked() {
        val coordinator = WjzFocusCoordinator()
        val sourceNodeId = WjzFocusNodeId("test/transition/source")
        val targetNodeId = WjzFocusNodeId("test/transition/target")
        val sourceRequester = FocusRequester()
        val targetRequester = FocusRequester()
        val locked = mutableStateOf(false)
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content
            ) {
                WjzFocusTransitionGuard(locked = locked.value)
                Column {
                    Box(
                        modifier = Modifier
                            .testTag("transition-source")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = sourceNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = sourceRequester,
                                onFocusChanged = { if (it) focusedNode = "source" }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .testTag("transition-target")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = targetNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = targetRequester,
                                onFocusChanged = { if (it) focusedNode = "target" }
                            )
                    )
                }

                LaunchedEffect(Unit) {
                    coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
                }
            }
        }

        composeRule.waitUntil { focusedNode == "source" }
        composeRule.runOnIdle {
            locked.value = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transition-source").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            Assert.assertEquals("source", focusedNode)
        }
        composeRule.runOnIdle {
            Assert.assertNotEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(targetNodeId, WjzFocusLayer.Content)
            )
            Assert.assertEquals("source", focusedNode)
            locked.value = false
        }
        composeRule.waitUntil { focusedNode == "target" }
    }

    @Test
    fun lazyRestoreUsesStableItemKeyAfterNeighborRemoval() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/lazy")
        val targetItem = 10_030L
        val targetItemKey = lazyVideoItemKey(targetItem)
        val targetNodeId = lazyVideoNodeId(targetItem)
        val items = mutableStateListOf<Long>()
        items.addAll((10_000L until 10_050L).toList())
        var focusedItem = 0L

        composeRule.setContent {
            val listState = rememberLazyListState()

            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) {
                WjzLazyFocusRestorerHost(
                    layer = WjzFocusLayer.Content,
                    restorerId = "test/lazy",
                    listId = "test/lazy",
                    scopeId = scopeId,
                    scrollToItem = { key ->
                        listState.scrollToItem(items.indexOfFirst { lazyVideoItemKey(it) == key })
                    },
                    isItemVisible = { key ->
                        listState.layoutInfo.visibleItemsInfo.any { it.key == key.value }
                    }
                )
                LazyColumn(
                    modifier = Modifier.size(width = 160.dp, height = 120.dp),
                    state = listState
                ) {
                    items(
                        items = items,
                        key = { lazyVideoItemKey(it).value }
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .testTag("item-$item")
                                .size(48.dp)
                                .wjzFocusNode(
                                    nodeId = lazyVideoNodeId(item),
                                    layer = WjzFocusLayer.Content,
                                    scopeId = scopeId,
                                    onFocusChanged = { if (it) focusedItem = item }
                                )
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            items.remove(targetItem - 1)
            coordinator.enqueueLazyRestore(
                nodeId = targetNodeId,
                itemKey = targetItemKey,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId,
                restorerId = "test/lazy",
                listId = "test/lazy"
            )
        }
        composeRule.waitUntil { focusedItem == targetItem }
    }

    @Test
    fun lazyRestoreRejectsStaleGenerationAndSucceedsAfterRecomposition() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/lazy-generation")
        val targetItem = 10_004L
        val targetItemKey = lazyVideoItemKey(targetItem)
        val targetNodeId = lazyVideoNodeId(targetItem)
        val itemGeneration = mutableIntStateOf(0)
        var shouldRecomposeOnScroll = true
        var focusedItem = 0L

        composeRule.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 4)

            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) {
                WjzLazyFocusRestorerHost(
                    layer = WjzFocusLayer.Content,
                    restorerId = "test/lazy-generation",
                    listId = "test/lazy-generation",
                    scopeId = scopeId,
                    scrollToItem = { key ->
                        if (key == targetItemKey && shouldRecomposeOnScroll) {
                            itemGeneration.intValue += 1
                        }
                        listState.scrollToItem((10_000L until 10_010L).indexOfFirst {
                            lazyVideoItemKey(it) == key
                        })
                    },
                    isItemVisible = { key ->
                        listState.layoutInfo.visibleItemsInfo.any { it.key == key.value }
                    }
                )
                LazyColumn(
                    modifier = Modifier.size(width = 160.dp, height = 120.dp),
                    state = listState
                ) {
                    items(
                        items = (10_000L until 10_010L).toList(),
                        key = { lazyVideoItemKey(it).value }
                    ) { item ->
                        key(itemGeneration.intValue) {
                            Box(
                                modifier = Modifier
                                    .testTag("generation-item-$item")
                                    .size(48.dp)
                                    .wjzFocusNode(
                                        nodeId = lazyVideoNodeId(item),
                                        layer = WjzFocusLayer.Content,
                                        scopeId = scopeId,
                                        onFocusChanged = { if (it) focusedItem = item }
                                    )
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitUntil {
            coordinator.requestFocusDetailed(
                nodeId = targetNodeId,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) == WjzFocusRequestResult.Focused
        }
        composeRule.runOnIdle {
            coordinator.enqueueLazyRestore(
                nodeId = targetNodeId,
                itemKey = targetItemKey,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId,
                restorerId = "test/lazy-generation",
                listId = "test/lazy-generation"
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            Assert.assertEquals(0L, focusedItem)
            shouldRecomposeOnScroll = false
            coordinator.enqueueLazyRestore(
                nodeId = targetNodeId,
                itemKey = targetItemKey,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId,
                restorerId = "test/lazy-generation",
                listId = "test/lazy-generation"
            )
        }
        composeRule.waitUntil { focusedItem == targetItem }
    }

    @Test
    fun requestFocusRequiresMatchingActiveLayer() {
        val coordinator = WjzFocusCoordinator()
        val contentNodeId = WjzFocusNodeId("test/layer/content")
        val dialogNodeId = WjzFocusNodeId("test/layer/dialog")
        val contentRequester = FocusRequester()
        val dialogRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            Column {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("layer-content")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = contentNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = contentRequester,
                                onFocusChanged = { if (it) focusedNode = "content" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Dialog
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("layer-dialog")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = dialogNodeId,
                                layer = WjzFocusLayer.Dialog,
                                requester = dialogRequester,
                                onFocusChanged = { if (it) focusedNode = "dialog" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil { coordinator.isMounted(contentNodeId) && coordinator.isMounted(dialogNodeId) }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(contentNodeId, WjzFocusLayer.Content)
            )
            Assert.assertNotEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(dialogNodeId, WjzFocusLayer.Dialog)
            )
        }
        composeRule.waitUntil { focusedNode == "content" }
        composeRule.runOnIdle {
            coordinator.switchLayer(WjzFocusLayer.Dialog)
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(dialogNodeId, WjzFocusLayer.Dialog)
            )
        }
        composeRule.waitUntil { focusedNode == "dialog" }
    }

    @Test
    fun requestFocusRequiresMatchingScopeWhenScopeIsExplicit() {
        val coordinator = WjzFocusCoordinator()
        val firstScopeId = WjzFocusScopeId("test/scope/first")
        val secondScopeId = WjzFocusScopeId("test/scope/second")
        val firstNodeId = WjzFocusNodeId("test/scope/first-node")
        val secondNodeId = WjzFocusNodeId("test/scope/second-node")
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            Row {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = firstScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("scope-first")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = firstNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = firstRequester,
                                onFocusChanged = { if (it) focusedNode = "first" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = secondScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("scope-second")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = secondNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = secondRequester,
                                onFocusChanged = { if (it) focusedNode = "second" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil { coordinator.isMounted(firstNodeId) && coordinator.isMounted(secondNodeId) }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(
                    nodeId = firstNodeId,
                    layer = WjzFocusLayer.Content,
                    scopeId = firstScopeId
                )
            )
            Assert.assertNotEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(
                    nodeId = secondNodeId,
                    layer = WjzFocusLayer.Content,
                    scopeId = firstScopeId
                )
            )
        }
        composeRule.waitUntil { focusedNode == "first" }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(
                    nodeId = secondNodeId,
                    layer = WjzFocusLayer.Content,
                    scopeId = secondScopeId
                )
            )
        }
        composeRule.waitUntil { focusedNode == "second" }
    }

    @Test
    fun restoreActiveLayerUsesScopeFallbackBeforeGlobalFallback() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/fallback/scope")
        val emptyScopeId = WjzFocusScopeId("test/fallback/empty-scope")
        val scopeFallbackNodeId = WjzFocusNodeId("test/fallback/scope-node")
        val globalFallbackNodeId = WjzFocusNodeId("test/fallback/global-node")
        val scopeFallbackRequester = FocusRequester()
        val globalFallbackRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            Row {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = scopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("scope-fallback")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = scopeFallbackNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = scopeFallbackRequester,
                                fallback = true,
                                onFocusChanged = { if (it) focusedNode = "scope" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("global-fallback")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = globalFallbackNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = globalFallbackRequester,
                                globalFallback = true,
                                onFocusChanged = { if (it) focusedNode = "global" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil {
            coordinator.isMounted(scopeFallbackNodeId) && coordinator.isMounted(globalFallbackNodeId)
        }
        composeRule.runOnIdle {
            Assert.assertTrue(coordinator.restoreActiveLayer(scopeId))
        }
        composeRule.waitUntil { focusedNode == "scope" }
        composeRule.runOnIdle {
            Assert.assertTrue(coordinator.restoreActiveLayer())
        }
        composeRule.waitUntil { focusedNode == "global" }
        composeRule.runOnIdle {
            Assert.assertTrue(coordinator.restoreActiveLayer(emptyScopeId))
        }
        composeRule.waitUntil { focusedNode == "global" }
    }

    @Test
    fun nodeEntryResolutionReadyPendingCancelAndRejectKeepProtocolBoundaries() {
        val coordinator = WjzFocusCoordinator()
        val sourceNodeId = WjzFocusNodeId("test/entry/source")
        val targetNodeId = WjzFocusNodeId("test/entry/target")
        val fallbackNodeId = WjzFocusNodeId("test/entry/fallback")
        val sourceRequester = FocusRequester()
        val targetRequester = FocusRequester()
        val fallbackRequester = FocusRequester()
        val readyEntryId = WjzFocusEntryId("test-entry/target")
        val missingEntryId = WjzFocusEntryId("test-entry/missing")
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content
            ) {
                WjzFocusEntriesHost(
                    componentId = readyEntryId.componentId.value,
                    default = { defaultEntry(fallbackNodeId) },
                    entries = {
                        entry(
                            id = readyEntryId.localEntryValue,
                            target = entry(
                                id = readyEntryId.localEntryValue,
                                nodeId = targetNodeId
                            )
                        )
                    }
                )
                Row {
                    Box(
                        modifier = Modifier
                            .testTag("entry-source")
                            .size(48.dp)
                            .wjzFocusableExits(
                                nodeId = sourceNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = sourceRequester,
                                exits = listOf(WjzFocusNodeExit(FocusDirection.Right, readyEntryId)),
                                onFocusChanged = { if (it) focusedNode = "source" }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .testTag("entry-target")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = targetNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = targetRequester,
                                onFocusChanged = { if (it) focusedNode = "target" }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .testTag("entry-fallback")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = fallbackNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = fallbackRequester,
                                onFocusChanged = { if (it) focusedNode = "fallback" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil {
            coordinator.isMounted(sourceNodeId) &&
                    coordinator.isMounted(targetNodeId) &&
                    coordinator.isMounted(fallbackNodeId)
        }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
            )
        }
        composeRule.waitUntil { focusedNode == "source" }
        composeRule.onNodeWithTag("entry-source").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "target" }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
            )
        }
        composeRule.waitUntil { focusedNode == "source" }
        composeRule.runOnIdle {
            coordinator.updateFocusRouting(
                nodeId = sourceNodeId,
                generation = coordinator.testGenerationOf(sourceNodeId),
                directionHandlers = emptyList(),
                exits = listOf(WjzFocusNodeExit(FocusDirection.Right, missingEntryId))
            )
        }
        composeRule.onNodeWithTag("entry-source").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "fallback" }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
            )
        }
        composeRule.waitUntil { focusedNode == "source" }
        composeRule.runOnIdle {
            coordinator.updateFocusRouting(
                nodeId = sourceNodeId,
                generation = coordinator.testGenerationOf(sourceNodeId),
                directionHandlers = emptyList(),
                exits = listOf(WjzFocusNodeExit.cancel(FocusDirection.Right))
            )
        }
        composeRule.onNodeWithTag("entry-source").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            Assert.assertEquals("source", focusedNode)
        }
    }

    @Test
    fun hostOnExitPassesThroughWhenCoordinatorIsFocusing() {
        val coordinator = WjzFocusCoordinator()
        val sourceScopeId = WjzFocusScopeId("test/host-exit/source-scope")
        val targetScopeId = WjzFocusScopeId("test/host-exit/target-scope")
        val sourceNodeId = WjzFocusNodeId("test/host-exit/source")
        val targetNodeId = WjzFocusNodeId("test/host-exit/target")
        val targetEntryId = WjzFocusEntryId("test-host-exit/target")
        val sourceRequester = FocusRequester()
        val targetRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            Row {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("host-exit-source")
                            .size(48.dp)
                            .wjzFocusableExits(
                                nodeId = sourceNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = sourceRequester,
                                exits = listOf(WjzFocusNodeExit(FocusDirection.Right, targetEntryId)),
                                onFocusChanged = { if (it) focusedNode = "source" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = targetScopeId
                ) {
                    WjzFocusEntriesHost(
                        componentId = targetEntryId.componentId.value,
                        default = {
                            defaultEntry(
                                nodeId = targetNodeId,
                                layer = WjzFocusLayer.Content,
                                scopeId = targetScopeId
                            )
                        },
                        entries = {
                            entry(
                                id = targetEntryId.localEntryValue,
                                target = entry(
                                    id = targetEntryId.localEntryValue,
                                    nodeId = targetNodeId,
                                    layer = WjzFocusLayer.Content,
                                    scopeId = targetScopeId
                                )
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .testTag("host-exit-target")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = targetNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = targetRequester,
                                onFocusChanged = { if (it) focusedNode = "target" }
                            )
                    )
                }
            }

            LaunchedEffect(Unit) {
                coordinator.requestFocusDetailed(sourceNodeId, WjzFocusLayer.Content)
            }
        }

        composeRule.waitUntil { focusedNode == "source" }
        composeRule.onNodeWithTag("host-exit-source").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitUntil { focusedNode == "target" }
        composeRule.runOnIdle {
            Assert.assertEquals("target", focusedNode)
        }
    }

    @Test
    fun dialogFocusHostActivatesDialogLayerAndRestoresSourceOnDispose() {
        val mainCoordinator = WjzFocusCoordinator()
        val dialogCoordinator = WjzFocusCoordinator()
        val mainScopeId = WjzFocusScopeId("test/dialog/main-scope")
        val dialogScopeId = WjzFocusScopeId("test/dialog/dialog-scope")
        val mainNodeId = WjzFocusNodeId("test/dialog/main-node")
        val dialogNodeId = WjzFocusNodeId("test/dialog/dialog-node")
        val mainRequester = FocusRequester()
        val dialogRequester = FocusRequester()
        val showDialog = mutableStateOf(false)
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = mainCoordinator,
                layer = WjzFocusLayer.Content,
                scopeId = mainScopeId
            ) {
                Box(
                    modifier = Modifier
                        .testTag("dialog-main")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = mainNodeId,
                            layer = WjzFocusLayer.Content,
                            requester = mainRequester,
                            onFocusChanged = { if (it) focusedNode = "main" }
                        )
                )
            }
            if (showDialog.value) {
                Dialog(onDismissRequest = {}) {
                    WjzDialogFocusHost(
                        mainCoordinator = mainCoordinator,
                        dialogCoordinator = dialogCoordinator,
                        sourceScopeId = mainScopeId,
                        dialogScopeId = dialogScopeId
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("dialog-content")
                                .size(48.dp)
                                .wjzFocusNode(
                                    nodeId = dialogNodeId,
                                    layer = WjzFocusLayer.Dialog,
                                    requester = dialogRequester,
                                    fallback = true,
                                    onFocusChanged = { if (it) focusedNode = "dialog" }
                                )
                        )
                    }
                }
            }
        }

        composeRule.waitUntil { mainCoordinator.isMounted(mainNodeId) }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                mainCoordinator.requestFocusDetailed(mainNodeId, WjzFocusLayer.Content, mainScopeId)
            )
        }
        composeRule.waitUntil { focusedNode == "main" }
        composeRule.runOnIdle {
            showDialog.value = true
        }
        composeRule.waitUntil { mainCoordinator.activeLayer == WjzFocusLayer.Dialog }
        composeRule.runOnIdle {
            showDialog.value = false
        }
        composeRule.waitUntil {
            mainCoordinator.activeLayer == WjzFocusLayer.Content && focusedNode == "main"
        }
    }

    @Test
    fun focusedSnapshotAllowsContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/snapshot")
        val containerNodeId = WjzFocusNodeId("test/container/snapshot/container")

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container
            )
            coordinator.updateFocus(containerNodeId, true)

            Assert.assertEquals(
                containerNodeId,
                coordinator.focusedNodeId(WjzFocusLayer.Content, scopeId)
            )
        }
    }

    @Test
    fun focusedLeafSnapshotIgnoresContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/leaf-snapshot")
        val containerNodeId = WjzFocusNodeId("test/container/leaf-snapshot/container")

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container
            )
            coordinator.updateFocus(containerNodeId, true)

            Assert.assertNull(coordinator.focusedLeafNodeId(WjzFocusLayer.Content, scopeId))
        }
    }

    @Test
    fun initialEntryIsNotBlockedByFocusedContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/initial")
        val containerNodeId = WjzFocusNodeId("test/container/initial/container")
        val targetNodeId = WjzFocusNodeId("test/container/initial/target")

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container
            )
            coordinator.updateFocus(containerNodeId, true)

            Assert.assertEquals(
                WjzFocusRequestResult.Enqueued,
                coordinator.submitNodeFocusIntent(
                    nodeId = targetNodeId,
                    layer = WjzFocusLayer.Content,
                    scopeId = scopeId,
                    intent = WjzFocusSubmitIntent.InitialEntry("test-container-initial")
                )
            )
        }
    }

    @Test
    fun contentFallbackIsNotBlockedByFocusedContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/content-fallback")
        val containerNodeId = WjzFocusNodeId("test/container/content-fallback/container")
        val targetNodeId = WjzFocusNodeId("test/container/content-fallback/target")

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container
            )
            coordinator.updateFocus(containerNodeId, true)

            Assert.assertEquals(
                WjzFocusRequestResult.Enqueued,
                coordinator.submitNodeFocusIntent(
                    nodeId = targetNodeId,
                    layer = WjzFocusLayer.Content,
                    scopeId = scopeId,
                    intent = WjzFocusSubmitIntent.ContentFallback("test-container-content")
                )
            )
        }
    }

    @Test
    fun fallbackContainerRegistrationFails() {
        val coordinator = WjzFocusCoordinator()

        composeRule.runOnIdle {
            Assert.assertThrows(IllegalArgumentException::class.java) {
                coordinator.registerTestNode(
                    nodeId = WjzFocusNodeId("test/container/fallback"),
                    strategy = WjzFocusRestoreStrategy.Container,
                    fallback = true
                )
            }
        }
    }

    @Test
    fun globalFallbackContainerRegistrationFails() {
        val coordinator = WjzFocusCoordinator()

        composeRule.runOnIdle {
            Assert.assertThrows(IllegalArgumentException::class.java) {
                coordinator.registerTestNode(
                    nodeId = WjzFocusNodeId("test/container/global-fallback"),
                    strategy = WjzFocusRestoreStrategy.Container,
                    globalFallback = true
                )
            }
        }
    }

    @Test
    fun restoreActiveLayerRequestsLeafWhenContainerHasFocus() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/restore")
        val containerNodeId = WjzFocusNodeId("test/container/restore/container")
        val leafNodeId = WjzFocusNodeId("test/container/restore/leaf")
        val containerRequester = FocusRequester()
        val leafRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) {
                Box(
                    modifier = Modifier
                        .testTag("restore-container")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = containerNodeId,
                            layer = WjzFocusLayer.Content,
                            requester = containerRequester,
                            strategy = WjzFocusRestoreStrategy.Container,
                            onFocusChanged = { if (it) focusedNode = "container" }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("restore-leaf")
                            .size(24.dp)
                            .wjzFocusNode(
                                nodeId = leafNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = leafRequester,
                                fallback = true,
                                onFocusChanged = { if (it) focusedNode = "leaf" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil { coordinator.isMounted(containerNodeId) && coordinator.isMounted(leafNodeId) }
        composeRule.runOnIdle {
            coordinator.updateFocus(containerNodeId, true)
            Assert.assertTrue(coordinator.restoreActiveLayer(scopeId))
        }
        composeRule.waitUntil { focusedNode == "leaf" }
    }

    @Test
    fun restoreSourceLayerDoesNotRequestContainerSource() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/source")
        val containerNodeId = WjzFocusNodeId("test/container/source/container")
        val leafNodeId = WjzFocusNodeId("test/container/source/leaf")
        val containerRequester = FocusRequester()
        val leafRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) {
                Box(
                    modifier = Modifier
                        .testTag("source-container")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = containerNodeId,
                            layer = WjzFocusLayer.Content,
                            requester = containerRequester,
                            strategy = WjzFocusRestoreStrategy.Container,
                            onFocusChanged = { if (it) focusedNode = "container" }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("source-leaf")
                            .size(24.dp)
                            .wjzFocusNode(
                                nodeId = leafNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = leafRequester,
                                fallback = true,
                                onFocusChanged = { if (it) focusedNode = "leaf" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil { coordinator.isMounted(containerNodeId) && coordinator.isMounted(leafNodeId) }
        composeRule.runOnIdle {
            coordinator.updateFocus(containerNodeId, true)
            coordinator.activateLayer(WjzFocusLayer.Dialog, recordSource = true)
            Assert.assertTrue(coordinator.restoreSourceLayer())
        }
        composeRule.waitUntil { focusedNode == "leaf" }
    }

    @Test
    fun recentFocusIgnoresContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/recent")
        val containerNodeId = WjzFocusNodeId("test/container/recent/container")
        val containerRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = coordinator,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            ) {
                Box(
                    modifier = Modifier
                        .testTag("recent-container")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = containerNodeId,
                            layer = WjzFocusLayer.Content,
                            requester = containerRequester,
                            strategy = WjzFocusRestoreStrategy.Container,
                            onFocusChanged = { if (it) focusedNode = "container" }
                        )
                )
            }
        }

        composeRule.waitUntil { coordinator.isMounted(containerNodeId) }
        composeRule.runOnIdle {
            coordinator.updateFocus(containerNodeId, true)
            coordinator.updateFocus(containerNodeId, false)
            Assert.assertFalse(coordinator.restoreActiveLayer(scopeId))
        }
        composeRule.runOnIdle {
            Assert.assertNotEquals("container", focusedNode)
        }
    }

    @Test
    fun lastFocusedScopeByLayerIgnoresContainer() {
        val coordinator = WjzFocusCoordinator()
        val leafScopeId = WjzFocusScopeId("test/container/last-scope/leaf")
        val containerScopeId = WjzFocusScopeId("test/container/last-scope/container")
        val unclaimedScopeId = WjzFocusScopeId("test/container/last-scope/unclaimed")
        val leafNodeId = WjzFocusNodeId("test/container/last-scope/leaf/node")
        val containerNodeId = WjzFocusNodeId("test/container/last-scope/container/node")
        val leafRequester = FocusRequester()
        val containerRequester = FocusRequester()
        var focusedNode = ""

        composeRule.setContent {
            Column {
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = leafScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("last-scope-leaf")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = leafNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = leafRequester,
                                fallback = true,
                                onFocusChanged = { if (it) focusedNode = "leaf" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = coordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = containerScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("last-scope-container")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = containerNodeId,
                                layer = WjzFocusLayer.Content,
                                requester = containerRequester,
                                strategy = WjzFocusRestoreStrategy.Container
                            )
                    )
                }
            }
        }

        composeRule.waitUntil { coordinator.isMounted(leafNodeId) && coordinator.isMounted(containerNodeId) }
        composeRule.runOnIdle {
            Assert.assertEquals(
                WjzFocusRequestResult.Focused,
                coordinator.requestFocusDetailed(leafNodeId, WjzFocusLayer.Content, leafScopeId)
            )
        }
        composeRule.waitUntil { focusedNode == "leaf" }
        composeRule.runOnIdle {
            coordinator.updateFocus(leafNodeId, false)
            coordinator.updateFocus(containerNodeId, true)
            Assert.assertFalse(coordinator.restoreHostOnResume(WjzFocusLayer.Content, unclaimedScopeId))
            Assert.assertTrue(coordinator.restoreHostOnResume(WjzFocusLayer.Content, leafScopeId))
        }
        composeRule.waitUntil { focusedNode == "leaf" }
    }

    @Test
    fun focusedLeafSnapshotReplacementSkipsContainer() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/leaf-replacement")
        val firstLeafNodeId = WjzFocusNodeId("test/container/leaf-replacement/leaf-1")
        val containerNodeId = WjzFocusNodeId("test/container/leaf-replacement/container")
        val secondLeafNodeId = WjzFocusNodeId("test/container/leaf-replacement/leaf-2")

        composeRule.runOnIdle {
            coordinator.registerTestNode(firstLeafNodeId, scopeId = scopeId)
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container
            )
            coordinator.registerTestNode(secondLeafNodeId, scopeId = scopeId)

            coordinator.updateFocus(firstLeafNodeId, true)
            coordinator.updateFocus(containerNodeId, true)
            coordinator.updateFocus(secondLeafNodeId, true)
            coordinator.updateFocus(secondLeafNodeId, false)

            Assert.assertEquals(
                firstLeafNodeId,
                coordinator.focusedLeafNodeId(WjzFocusLayer.Content, scopeId)
            )
        }
    }

    @Test
    fun lockedDirectionIntentIsConsumedByLeaf() {
        val coordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/container/locked-direction")
        val containerNodeId = WjzFocusNodeId("test/container/locked-direction/container")
        val leafNodeId = WjzFocusNodeId("test/container/locked-direction/leaf")
        var containerDirectionCount = 0
        var leafDirectionCount = 0
        val token = Any()

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = containerNodeId,
                scopeId = scopeId,
                strategy = WjzFocusRestoreStrategy.Container,
                directionHandlers = listOf(
                    WjzFocusDirectionHandler { _, _ ->
                        containerDirectionCount += 1
                        true
                    }
                )
            )
            coordinator.registerTestNode(
                nodeId = leafNodeId,
                scopeId = scopeId,
                directionHandlers = listOf(
                    WjzFocusDirectionHandler { _, _ ->
                        leafDirectionCount += 1
                        true
                    }
                )
            )

            coordinator.updateFocus(containerNodeId, true)
            coordinator.updateFocus(leafNodeId, true)
            coordinator.lockFocus(token)
            Assert.assertTrue(coordinator.recordLockedDirectionIntent(FocusDirection.Down))
            coordinator.unlockFocus(token)

            Assert.assertEquals(0, containerDirectionCount)
            Assert.assertEquals(1, leafDirectionCount)
        }
    }

    @Test
    fun topologyRegionConvertsEntryAndCancelBoundariesToNodeAndHostExits() {
        val state = WjzFocusTopologyState()
        val entryId = WjzFocusEntryId("test-topology/target")

        WjzFocusTopologyScope(state).region("test/topology/source") {
            onLeft(WjzFocusBoundaryTarget.Entry(entryId))
            onRight(WjzFocusBoundaryTarget.Cancel)
            onDown(WjzFocusBoundaryTarget.Internal)
        }

        val nodeExits = state.nodeExitsFor("test/topology/source")
        val hostExits = state.hostExitsFor("test/topology/source")

        Assert.assertEquals(
            listOf(
                WjzFocusNodeExit(FocusDirection.Left, entryId),
                WjzFocusNodeExit.cancel(FocusDirection.Right)
            ),
            nodeExits
        )
        Assert.assertEquals(
            listOf(
                WjzFocusHostExit(FocusDirection.Left, entryId),
                WjzFocusHostExit.cancel(FocusDirection.Right)
            ),
            hostExits
        )
    }

    @Test
    fun topologyInitialEntryOverridesFallbackForSameComponent() {
        val componentId = "test/topology/component"
        val defaultTarget = defaultEntry(
            nodeId = WjzFocusNodeId("$componentId/default"),
            layer = WjzFocusLayer.Content,
            scopeId = null
        )
        val preferredTarget = WjzFocusTargetEntry(
            id = "preferred",
            nodeId = WjzFocusNodeId("$componentId/preferred"),
            layer = WjzFocusLayer.Content,
            scopeId = null
        )

        val resolved = WjzFocusBoundaryTarget.Entry(
            WjzFocusEntryId("$componentId/preferred")
        ).resolveTopologyInitialTarget(
            componentId = componentId,
            targets = listOf(preferredTarget),
            fallback = { defaultTarget }
        )

        Assert.assertEquals(preferredTarget, resolved)
    }

    @Test
    fun debugOverlaySlotRendersRegisteredSnapshotWhenEnabled() {
        val coordinator = WjzFocusCoordinator()
        val nodeId = WjzFocusNodeId("test/debug-overlay/node")

        WjzFocusDebugOverlayRegistry.installDefault(true)
        composeRule.setContent {
            androidx.tv.material3.MaterialTheme {
                WjzFocusDebugOverlaySlot(coordinator)
            }
        }

        composeRule.runOnIdle {
            coordinator.registerTestNode(nodeId)
        }
        Assert.assertTrue(
            composeRule.onAllNodesWithText("WjzFocus Debug")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("registered: 1", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.runOnIdle {
            WjzFocusDebugOverlayRegistry.clear()
        }
    }

    @Test
    fun saveStateContainsLightweightHistoryOnly() {
        val coordinator = WjzFocusCoordinator()
        val contentScopeId = WjzFocusScopeId("test/state/content")
        val dialogScopeId = WjzFocusScopeId("test/state/dialog")
        val contentNodeId = WjzFocusNodeId("test/state/content/node")
        val dialogNodeId = WjzFocusNodeId("test/state/dialog/node")

        composeRule.runOnIdle {
            coordinator.registerTestNode(
                nodeId = contentNodeId,
                layer = WjzFocusLayer.Content,
                scopeId = contentScopeId
            )
            coordinator.updateFocus(contentNodeId, true)
            val token = coordinator.activateLayer(WjzFocusLayer.Dialog, recordSource = true)
            coordinator.registerTestNode(
                nodeId = dialogNodeId,
                layer = WjzFocusLayer.Dialog,
                scopeId = dialogScopeId
            )
            coordinator.updateFocus(dialogNodeId, true)

            val savedState = coordinator.saveState()

            Assert.assertEquals(WjzFocusLayer.Dialog, savedState.activeLayer)
            Assert.assertEquals(
                listOf(WjzFocusSavedRecentNode(contentNodeId.value, contentScopeId.value)),
                savedState.recentFocus
                    .single { it.layer == WjzFocusLayer.Content }
                    .nodes
            )
            Assert.assertEquals(
                listOf(WjzFocusSavedRecentNode(dialogNodeId.value, dialogScopeId.value)),
                savedState.recentFocus
                    .single { it.layer == WjzFocusLayer.Dialog }
                    .nodes
            )
            Assert.assertEquals(
                WjzFocusSavedSource(
                    layer = WjzFocusLayer.Content,
                    scopeId = contentScopeId.value,
                    nodeId = contentNodeId.value,
                    token = requireNotNull(token).value
                ),
                savedState.sourceStack.single()
            )
            Assert.assertTrue(savedState.lastFocusedScopes.contains(
                WjzFocusSavedLayerScope(WjzFocusLayer.Content, contentScopeId.value)
            ))
            Assert.assertTrue(savedState.lastFocusedScopes.contains(
                WjzFocusSavedLayerScope(WjzFocusLayer.Dialog, dialogScopeId.value)
            ))
        }
    }

    @Test
    fun rememberRootCoordinatorRestoresStableLayerWithoutRequestingFocus() {
        val contentScopeId = WjzFocusScopeId("test/state/root/content")
        val dialogScopeId = WjzFocusScopeId("test/state/root/dialog")
        val contentNodeId = WjzFocusNodeId("test/state/root/content-node")
        val dialogNodeId = WjzFocusNodeId("test/state/root/dialog-node")
        val savedState = WjzFocusSavedState(
            activeLayer = WjzFocusLayer.Dialog,
            recentFocus = listOf(
                WjzFocusSavedRecentLayer(
                    layer = WjzFocusLayer.Content,
                    nodes = listOf(WjzFocusSavedRecentNode(contentNodeId.value, contentScopeId.value))
                )
            ),
            sourceStack = listOf(
                WjzFocusSavedSource(
                    layer = WjzFocusLayer.Content,
                    scopeId = contentScopeId.value,
                    nodeId = contentNodeId.value,
                    token = 1L
                ),
                WjzFocusSavedSource(
                    layer = WjzFocusLayer.Dialog,
                    scopeId = dialogScopeId.value,
                    nodeId = dialogNodeId.value,
                    token = 2L
                )
            )
        )
        var restoredCoordinator: WjzFocusCoordinator? = null

        composeRule.setContent {
            restoredCoordinator = rememberWjzFocusCoordinator(
                initialSavedState = savedState,
                restoreAsRoot = true
            )
        }

        composeRule.runOnIdle {
            val snapshot = requireNotNull(restoredCoordinator).debugSnapshot()
            Assert.assertEquals(WjzFocusLayer.Content, snapshot.activeLayer)
            Assert.assertEquals(
                listOf(
                    WjzFocusDebugSource(
                        layer = WjzFocusLayer.Content,
                        scopeId = contentScopeId.value,
                        nodeId = contentNodeId.value,
                        token = 1L
                    )
                ),
                snapshot.sourceStack
            )
            Assert.assertTrue(snapshot.registeredNodes.isEmpty())
            Assert.assertTrue(snapshot.focusedLeafByLayerScope.isEmpty())
            Assert.assertTrue(snapshot.pendingRequests.isEmpty())
        }
    }

    @Test
    fun restoreStateImportsHistoryWithoutRequestingFocus() {
        val sourceCoordinator = WjzFocusCoordinator()
        val restoredCoordinator = WjzFocusCoordinator()
        val scopeId = WjzFocusScopeId("test/state/restore")
        val nodeId = WjzFocusNodeId("test/state/restore/node")

        composeRule.runOnIdle {
            sourceCoordinator.registerTestNode(
                nodeId = nodeId,
                layer = WjzFocusLayer.Content,
                scopeId = scopeId
            )
            sourceCoordinator.updateFocus(nodeId, true)
            val savedState = sourceCoordinator.saveState()

            restoredCoordinator.restoreState(savedState)
            val restoredSnapshot = restoredCoordinator.debugSnapshot()

            Assert.assertEquals(WjzFocusLayer.Content, restoredSnapshot.activeLayer)
            Assert.assertTrue(restoredSnapshot.registeredNodes.isEmpty())
            Assert.assertTrue(restoredSnapshot.focusedByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.focusedLeafByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.pendingRequests.isEmpty())
            Assert.assertEquals(
                listOf(WjzFocusDebugRecentNode(nodeId.value, scopeId.value)),
                restoredSnapshot.recentFocus
                    .single { it.layer == WjzFocusLayer.Content }
                    .nodes
            )

            Assert.assertFalse(restoredCoordinator.restoreHostOnResume(WjzFocusLayer.Content, scopeId))

            val afterResumeSnapshot = restoredCoordinator.debugSnapshot()
            Assert.assertEquals(1, afterResumeSnapshot.pendingRequests.size)
            Assert.assertEquals(WjzFocusLayer.Content, afterResumeSnapshot.pendingRequests.single().layer)
            Assert.assertEquals(scopeId.value, afterResumeSnapshot.pendingRequests.single().scopeId)
        }
    }

    @Test
    fun restoreStateImportedSourceStackRestoresCurrentSourceLeaf() {
        val restoredCoordinator = WjzFocusCoordinator()
        val sourceScopeId = WjzFocusScopeId("test/state/source/leaf/source")
        val dialogScopeId = WjzFocusScopeId("test/state/source/leaf/dialog")
        val sourceNodeId = WjzFocusNodeId("test/state/source/leaf/source-node")
        val dialogNodeId = WjzFocusNodeId("test/state/source/leaf/dialog-node")
        val savedState = WjzFocusSavedState(
            activeLayer = WjzFocusLayer.Dialog,
            sourceStack = listOf(
                WjzFocusSavedSource(
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId.value,
                    nodeId = sourceNodeId.value,
                    token = 1L
                )
            )
        )
        var focusedNode = ""

        composeRule.setContent {
            Column {
                WjzFocusHost(
                    coordinator = restoredCoordinator,
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("restore-state-source-leaf")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = sourceNodeId,
                                layer = WjzFocusLayer.Content,
                                scopeId = sourceScopeId,
                                onFocusChanged = { if (it) focusedNode = "source" }
                            )
                    )
                }
                WjzFocusHost(
                    coordinator = restoredCoordinator,
                    layer = WjzFocusLayer.Dialog,
                    scopeId = dialogScopeId
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("restore-state-dialog-leaf")
                            .size(48.dp)
                            .wjzFocusNode(
                                nodeId = dialogNodeId,
                                layer = WjzFocusLayer.Dialog,
                                scopeId = dialogScopeId,
                                onFocusChanged = { if (it) focusedNode = "dialog" }
                            )
                    )
                }
            }
        }

        composeRule.waitUntil {
            restoredCoordinator.isMounted(sourceNodeId) &&
                    restoredCoordinator.isMounted(dialogNodeId)
        }
        composeRule.runOnIdle {
            restoredCoordinator.restoreState(savedState)
            val restoredSnapshot = restoredCoordinator.debugSnapshot()

            Assert.assertEquals(WjzFocusLayer.Dialog, restoredSnapshot.activeLayer)
            Assert.assertEquals(
                listOf(
                    WjzFocusDebugSource(
                        layer = WjzFocusLayer.Content,
                        scopeId = sourceScopeId.value,
                        nodeId = sourceNodeId.value,
                        token = 1L
                    )
                ),
                restoredSnapshot.sourceStack
            )
            Assert.assertTrue(restoredSnapshot.focusedByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.focusedLeafByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.pendingRequests.isEmpty())
            Assert.assertEquals("", focusedNode)

            Assert.assertTrue(restoredCoordinator.restoreSourceLayer())
        }
        composeRule.waitUntil { focusedNode == "source" }
        composeRule.runOnIdle {
            val afterRestoreSnapshot = restoredCoordinator.debugSnapshot()
            Assert.assertEquals(WjzFocusLayer.Content, afterRestoreSnapshot.activeLayer)
            Assert.assertTrue(afterRestoreSnapshot.sourceStack.isEmpty())
            Assert.assertTrue(afterRestoreSnapshot.pendingRequests.isEmpty())
        }
    }

    @Test
    fun restoreStateImportedSourceStackFallsBackToSourceScopeWhenSourceNodeMissing() {
        val restoredCoordinator = WjzFocusCoordinator()
        val sourceScopeId = WjzFocusScopeId("test/state/source/fallback/source")
        val missingSourceNodeId = WjzFocusNodeId("test/state/source/fallback/missing")
        val fallbackNodeId = WjzFocusNodeId("test/state/source/fallback/fallback")
        val savedState = WjzFocusSavedState(
            activeLayer = WjzFocusLayer.Dialog,
            sourceStack = listOf(
                WjzFocusSavedSource(
                    layer = WjzFocusLayer.Content,
                    scopeId = sourceScopeId.value,
                    nodeId = missingSourceNodeId.value,
                    token = 2L
                )
            )
        )
        var focusedNode = ""

        composeRule.setContent {
            WjzFocusHost(
                coordinator = restoredCoordinator,
                layer = WjzFocusLayer.Content,
                scopeId = sourceScopeId
            ) {
                Box(
                    modifier = Modifier
                        .testTag("restore-state-source-scope-fallback")
                        .size(48.dp)
                        .wjzFocusNode(
                            nodeId = fallbackNodeId,
                            layer = WjzFocusLayer.Content,
                            scopeId = sourceScopeId,
                            fallback = true,
                            onFocusChanged = { if (it) focusedNode = "fallback" }
                        )
                )
            }
        }

        composeRule.waitUntil { restoredCoordinator.isMounted(fallbackNodeId) }
        composeRule.runOnIdle {
            restoredCoordinator.restoreState(savedState)
            val restoredSnapshot = restoredCoordinator.debugSnapshot()

            Assert.assertEquals(WjzFocusLayer.Dialog, restoredSnapshot.activeLayer)
            Assert.assertEquals(
                listOf(
                    WjzFocusDebugSource(
                        layer = WjzFocusLayer.Content,
                        scopeId = sourceScopeId.value,
                        nodeId = missingSourceNodeId.value,
                        token = 2L
                    )
                ),
                restoredSnapshot.sourceStack
            )
            Assert.assertTrue(restoredSnapshot.focusedByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.focusedLeafByLayerScope.isEmpty())
            Assert.assertTrue(restoredSnapshot.pendingRequests.isEmpty())
            Assert.assertEquals("", focusedNode)

            Assert.assertTrue(restoredCoordinator.restoreSourceLayer())
        }
        composeRule.waitUntil { focusedNode == "fallback" }
        composeRule.runOnIdle {
            val afterRestoreSnapshot = restoredCoordinator.debugSnapshot()
            Assert.assertEquals(WjzFocusLayer.Content, afterRestoreSnapshot.activeLayer)
            Assert.assertTrue(afterRestoreSnapshot.sourceStack.isEmpty())
            Assert.assertTrue(afterRestoreSnapshot.pendingRequests.isEmpty())
        }
    }

    private fun WjzFocusCoordinator.registerTestNode(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer = WjzFocusLayer.Content,
        scopeId: WjzFocusScopeId? = null,
        strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
        fallback: Boolean = false,
        globalFallback: Boolean = false,
        directionHandlers: List<WjzFocusDirectionHandler> = emptyList()
    ): Int {
        val generation = register(
            WjzFocusNode(
                id = nodeId,
                layer = layer,
                requester = FocusRequester(),
                strategy = strategy,
                scopeId = scopeId,
                fallback = fallback,
                globalFallback = globalFallback
            )
        )
        markPlaced(nodeId, generation)
        if (directionHandlers.isNotEmpty()) {
            updateFocusRouting(
                nodeId = nodeId,
                generation = generation,
                directionHandlers = directionHandlers,
                exits = emptyList()
            )
        }
        return generation
    }

    private fun WjzFocusCoordinator.testGenerationOf(nodeId: WjzFocusNodeId): Int {
        return debugSnapshot()
            .registeredNodes
            .single { it.nodeId == nodeId.value }
            .generation
    }

    private fun lazyVideoItemKey(item: Long): WjzFocusItemKey {
        return WjzFocusItemKey("Long:$item")
    }

    private fun lazyVideoNodeId(item: Long): WjzFocusNodeId {
        return WjzFocusNodeId("test/lazy/video/$item")
    }
}
