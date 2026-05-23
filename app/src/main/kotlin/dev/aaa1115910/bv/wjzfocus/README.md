# WjzFocus 焦点管理系统说明

WjzFocus 是本项目唯一允许的焦点协议。业务层只声明节点、入口、恢复目标、拓扑边界，不能调用 Compose 原生焦点 API，也不直接操作 coordinator 的低层 request/enqueue 细节。

推荐接入顺序是：

1. 普通可聚焦节点优先用 `Modifier.wjzFocusLocal(...)`、`Modifier.wjzFocusExits(...)`、`WjzFocusLocalEntrySurface(...)`
2. 组件内部方向解析优先用 `Modifier.wjzFocusRouter(...)` 或 Lazy/Scroll 封装
3. 跨组件边界优先由页面声明 `WjzFocusTopology`
4. 需要恢复、滚动、跨 layer 时再下沉到 restorer、submit intent、target 等底层 API

## 文件作用

- `WjzDialogFocusHost.kt`：Dialog layer 的宿主封装，负责记录来源焦点、激活临时 layer、关闭时恢复来源。
- `WjzDisabledFocus.kt`：给节点打上“不可聚焦”语义，供 coordinator 在物理路径和严格恢复窗口里识别。
- `WjzFocusable.kt`：最底层的焦点节点注册封装，负责把节点挂进 coordinator，并同步 mounted/placed/focus 状态。
- `WjzFocusClickable.kt`：把点击语义和 WjzFocus 节点绑定在一起，解决遥控确认键、点击回调与节点注册的耦合。
- `WjzFocusCoordinator.kt`：整个焦点系统的核心运行时，负责注册、路由、pending、recent、source、焦点锁、恢复、调试快照。
- `WjzFocusDebug.kt`：日志级别、debug snapshot 结构，、只读调试 overlay（仅 r8test 包有效）。
- `WjzFocusDirections.kt`：方向 DSL 常量，例如 `left`、`up`、`horizontal`、`all`，你能想到的组合里面都有，无需使用多个方向 DSL 常量。
- `WjzFocusExits.kt`：节点级和 Host 级出口 DSL，把方向边界转换成 coordinator 可消费的出口规则。
- `WjzFocusGrid.kt`：静态网格场景的轻量封装，适合不依赖 Lazy 的规则化网格布局。
- `WjzFocusGroup.kt`：Compose 原生 `focusGroup` 的 WjzFocus 语义封装。
- `WjzFocusHost.kt`：稳定宿主区域，负责 scope、layer、按键入口、Host 边界、组合树里提供 coordinator。
- `WjzFocusIds.kt`：所有节点、scope、entry、component、item key 的 id 规则与转换工具。
- `WjzFocusLazyComponents.kt`：LazyColumn/LazyRow/Grid 的推荐接入封装。
- `WjzFocusLazyModel.kt`：Lazy 组件内部的状态模型、入口恢复、滚动恢复、 topology 绑定逻辑。
- `WjzFocusLazyRouter.kt`：Lazy 场景的相邻焦点路由接口与结果模型。
- `WjzFocusLazyRouteTools.kt`：Lazy 行列定位、边界判断、换页、换列等工具函数。
- `WjzFocusModule.kt`：模块级封装，便于把 component、entry、scope、host 组合成可复用的焦点模块。
- `WjzFocusRememberTopologyRegion.kt`：Topology region 的组件侧 binding，统一导出 `nodeExits`、`hostExits`、`initialTarget`。
- `WjzFocusRestorerHost.kt`：group/lazy/entry/layer 各类恢复目标与 restorer host 封装。
- `WjzFocusRouter.kt`：普通非 Lazy 容器的内部方向路由。
- `WjzFocusScrollComponents.kt`：普通 ScrollColumn/ScrollRow 的推荐接入封装。
- `WjzFocusSnapshotResolvers.kt`：线性、网格焦点解析工具，根据当前位置和方向计算下一个目标。
- `WjzFocusStateSaver.kt`：根 coordinator 的保存、恢复数据结构。
- `WjzFocusSubmitIntent.kt`：公开意图模型，区分 `InitialEntry`、`ExternalEntry`、`LayerEntry`、`ContentFallback`。
- `WjzFocusSugar.kt`：推荐语法糖，减少样板代码。例如 local node、local entry、submit intent sugar、debug effect。
- `WjzFocusTarget.kt`：默认 target、具名 entry、entries host、target resolution 的定义和 DSL。
- `WjzFocusTextField.kt`：输入框的节点注册封装。
- `WjzFocusTopology.kt`：页面级拓扑声明，负责 region、boundary、initial target、拓扑导出。
- `WjzFocusTransfer.kt`：node exit、host exit 的运行时表示与消费逻辑。
- `WjzFocusTransitionGuard.kt`：过渡期焦点锁，防止抽屉、动画、切屏过程中误切焦点。

## 推荐接入

以下写法优先于底层 API，业务层默认先从这些开始。

### 1. 最普通的可聚焦节点

```kotlin
Box(
    modifier = Modifier.wjzFocusLocal(
        localId = wjzFocusLocalId("play")
    )
)
```

### 2. 带出口和聚焦回调的节点

```kotlin
var focused by remember { mutableStateOf(false) }

Box(
    modifier = Modifier.wjzFocusLocal(
        localId = wjzFocusLocalId("play"),
        onFocusChanged = { focused = it },
        exits = {
            right move VideoInfoScopeId.localTarget(wjzFocusLocalId("favorite"))
            cancel(down)
        }
    )
)
```

### 3. 暴露本组件自己的 entry

```kotlin
WjzFocusLocalEntrySurface(
    componentId = "videoInfoActions",
    defaultLocalId = wjzFocusLocalId("play")
) {
    ActionsRow()
}
```

### 4. 推荐的 submit intent 语法糖

```kotlin
coordinator.submitInitialEntryFocus(
    entryId = VideoInfoEntryId,
    dedupeKey = VideoInfoEntryId
)

coordinator.submitExternalEntryFocus(
    entryId = DrawerEntryId,
    layerActivation = WjzFocusLayerActivation.ActivateLayer,
    dedupeKey = "open-drawer"
)

coordinator.submitContentFallbackNodeFocus(
    nodeId = currentNodeId,
    dedupeKey = "list-refresh"
)
```

### 5. 页面声明 topology，组件绑定 region

```kotlin
private const val MainContentRegion = "main/content"

WjzFocusTopology {
    region(
        id = MainContentRegion,
        scopeId = MainFocusScopeId
    ) {
        onLeft(WjzFocusBoundaryTarget.Entry(MainDrawerRightEntryId))
        onUp(WjzFocusBoundaryTarget.Entry(MainTopNavDefaultEntryId))
    }

    HomeContent(
        topologyRegion = wjzFocusTopologyRegion(MainContentRegion)
    )
}
```

### 6. 组件内部消费 `wjzFocusRememberTopologyRegion`

```kotlin
@Composable
fun HomeContent(
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
) {
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)

    Box(
        modifier = Modifier.wjzFocusExits(
            localId = wjzFocusLocalId("list"),
            exits = { addAll(topology.nodeExits) }
        )
    )
}
```

### 7. 只在激活时绑定 topology

```kotlin
val contentRegion = wjzFocusTopologyRegion(MainContentRegion)

HomeContent(
    topologyRegion = contentRegion.enabledIf(active)
)
```

### 8. Lazy/Scroll 场景优先用现成封装

```kotlin
WjzFocusLazyColumn(
    items = videos,
    itemKey = { it.id },
    componentId = "homeFeed",
    topologyRegion = wjzFocusTopologyRegion(MainContentRegion)
) { video ->
    VideoCard(video = video)
}
```

## 底层 API

只有在推荐接入不够用时，才调用这些 API。

### 1. 最底层注册 leaf 节点

```kotlin
Box(
    modifier = Modifier.wjzFocusableExits(
        nodeId = scopeId.resolve(wjzFocusLocalId("play")),
        layer = WjzFocusLayer.Content,
        scopeId = scopeId,
        fallback = true
    )
)
```

### 2. 注册 Container 节点

```kotlin
Column(
    modifier = Modifier.wjzFocusNode(
        nodeId = scopeId.resolve(wjzFocusLocalId("panel")),
        layer = WjzFocusLayer.Content,
        scopeId = scopeId,
        strategy = WjzFocusRestoreStrategy.Container
    )
)
```

### 3. 普通 Router

```kotlin
Modifier.wjzFocusRouter(
    currentEntryId = currentEntryId,
    resolver = WjzFocusRouteResolver { direction ->
        when (direction) {
            FocusDirection.Left -> WjzFocusRouteResult.Found(leftTarget)
            FocusDirection.Right -> WjzFocusRouteResult.Found(rightTarget)
            else -> WjzFocusRouteResult.Missing
        }
    }
)
```

### 4. Lazy Router

```kotlin
val resolver = wjzLazyGridRouteResolver(
    itemCount = items.size,
    columnCount = 5
)
```

### 5. entry surface 和 entries host

```kotlin
WjzFocusEntrySurface(
    componentId = "searchResult",
    default = { defaultEntry(nodeId = resultNodeId) },
    entries = {
        entry("top") move SearchScopeId.target(wjzFocusLocalId("top"))
        entry("list") move SearchScopeId.target(wjzFocusLocalId("list"))
    }
)
```

### 6. restorer target 和恢复

```kotlin
val target = wjzFocusLayerRestoreTarget(
    layer = WjzFocusLayer.Content,
    scopeId = VideoInfoScopeId
)

target.restoreFocus(coordinator)
```

### 7. Dialog layer 来源恢复

```kotlin
val token = coordinator.activateLayer(
    layer = WjzFocusLayer.Dialog,
    recordSource = true
)

coordinator.restoreSourceLayer(
    expectedActiveLayer = WjzFocusLayer.Dialog,
    token = token
)
```

### 8. topology 底层 binding 细节

```kotlin
val topology = wjzFocusRememberTopologyRegion(
    wjzFocusTopologyRegion(MainContentRegion)
)

val initialTarget = topology.resolveInitialTarget(
    componentId = "homeFeed",
    targets = targets
) {
    defaultResolvedTarget
}

val hostExits = topology.hostExits
```

## 规则

- 页面负责声明跨组件边界，组件不要反向硬编码页面结构。
- `WjzFocusTopologyRegionRef.Standalone` 表示组件保留自己的独立 fallback，不从页面 topology 读取边界。
- `WjzFocusBoundaryTarget.Internal`、`Region`、`Wrap` 不会导出成可执行 node/host exit；`Entry` 和 `Cancel` 才会导出。
- Lazy/Scroll 精确恢复读 leaf snapshot，不把 Container path 当恢复目标。
- 业务和通用组件不要直接触 coordinator 的低层 request/enqueue；优先走 submit intent 或 restorer target。
- `androidx.compose.ui.focus.*` 在业务/UI 层禁用，`FocusDirection` 仅作为 WjzFocus DSL 方向枚举例外。
